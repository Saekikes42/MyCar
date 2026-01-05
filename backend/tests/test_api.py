"""HTTP integration tests against a temporary SQLite database."""

import json
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from backend.database import Database
from backend.server import DemoServer, Handler


class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.database = Database(Path(cls.temp.name) / "test.db")
        cls.database.initialize()
        cls.server = DemoServer(("127.0.0.1", 0), cls.database)
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"
        # Keep test output concise; request bodies and tokens are never logged.
        cls.original_log = Handler.log_message
        Handler.log_message = lambda *args: None
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)
        Handler.log_message = cls.original_log
        cls.temp.cleanup()

    def request(self, path, method="GET", data=None, token=None, raw=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        body = raw if raw is not None else json.dumps(data).encode() if data is not None else None
        request = Request(self.base + path, data=body, headers=headers, method=method)
        try:
            response = urlopen(request, timeout=10)
        except HTTPError as error:
            response = error
        with response:
            content = response.read()
            value = json.loads(content) if "application/json" in response.headers["Content-Type"] else content
            return response.status, value

    def setUp(self):
        email = f"{self._testMethodName}@example.com"
        # UI limits email length to 60 characters.
        self.user_data = {"name": "Иван", "email": email, "birthDate": "2000-01-15", "password": "Study2025!"}
        status, result = self.request("/api/Auth/Register", "POST", self.user_data)
        self.assertEqual(status, 200, result)
        self.user_id = result["user"]["id"]
        status, result = self.request("/api/Auth/Login", "POST", self.user_data)
        self.assertEqual(status, 200, result)
        self.token = result["token"]

    def add_item(self, config_id=101, quantity=1):
        return self.request("/api/Cart/AddToCart", "POST", {
            "userId": self.user_id, "cartItem": {"id": config_id, "quantity": quantity, "price": 0.01},
        }, self.token)

    def checkout(self, config_id=101):
        return self.request("/api/Order/CreateOrder", "POST", {
            "userId": self.user_id, "itemId": config_id,
            "clientName": "Подменённое имя", "clientEmail": "fake@example.com",
            "deliveryMethod": "Салон", "salonLocation": "Учебный салон",
            "paymentMethod": "Наличные", "notes": "",
        }, self.token)

    def test_registration_hashes_password_and_login(self):
        with self.database.connect() as db:
            row = db.execute("SELECT * FROM users WHERE id = ?", (self.user_id,)).fetchone()
            self.assertNotEqual(row["password_hash"], self.user_data["password"])
            self.assertTrue(row["password_hash"].startswith("pbkdf2_sha256$"))
            stored = db.execute("SELECT token_hash FROM sessions WHERE user_id = ?", (self.user_id,)).fetchone()[0]
            self.assertNotEqual(stored, self.token)
        status, result = self.request(f"/api/Auth/GetProfile/{self.user_id}", token=self.token)
        self.assertEqual(status, 200)
        self.assertNotIn("password", result["user"])
        self.assertNotIn("password_hash", result["user"])

    def test_duplicate_email_is_case_insensitive(self):
        status, _ = self.request("/api/Auth/Register", "POST", {**self.user_data, "email": self.user_data["email"].upper()})
        self.assertEqual(status, 409)

    def test_invalid_password_and_birth_date(self):
        for changes in ({"password": "123"}, {"birthDate": "2000-99-99"}, {"birthDate": "2999-01-01"}):
            status, _ = self.request("/api/Auth/Register", "POST", {**self.user_data, **changes})
            self.assertEqual(status, 400)

    def test_wrong_password(self):
        status, _ = self.request("/api/Auth/Login", "POST", {**self.user_data, "password": "Wrong123!"})
        self.assertEqual(status, 401)

    def test_logout_invalidates_token(self):
        status, _ = self.request("/api/Auth/Logout", "POST", {}, self.token)
        self.assertEqual(status, 200)
        status, _ = self.request(f"/api/Auth/GetProfile/{self.user_id}", token=self.token)
        self.assertEqual(status, 401)

    def test_auth_and_user_isolation(self):
        for path in (f"/api/Cart/GetCart/{self.user_id}", f"/api/Order/GetOrders/{self.user_id}"):
            status, _ = self.request(path)
            self.assertEqual(status, 401)
        for prefix in ("Auth/GetProfile", "Cart/GetCart", "Order/GetOrders"):
            status, _ = self.request(f"/api/{prefix}/{self.user_id + 1}", token=self.token)
            self.assertEqual(status, 403)
        status, _ = self.request("/api/Cart/AddToCart", "POST", {
            "userId": self.user_id + 1, "cartItem": {"id": 101, "quantity": 1},
        }, self.token)
        self.assertEqual(status, 403)

    def test_expired_session(self):
        with self.database.connect() as db:
            db.execute("UPDATE sessions SET expires_at = 0 WHERE user_id = ?", (self.user_id,))
        status, _ = self.request(f"/api/Cart/GetCart/{self.user_id}", token=self.token)
        self.assertEqual(status, 401)

    def test_catalogue_images_and_missing_car(self):
        status, cars = self.request("/api/car")
        self.assertEqual(status, 200)
        self.assertEqual(len(cars), 3)
        self.assertEqual(len(cars[0]["variants"]), 2)
        image = cars[0]["variants"][0]["colors"][0]["images"][0]
        status, content = self.request(image)
        self.assertEqual(status, 200)
        self.assertTrue(content.startswith(b"\x89PNG"))
        status, _ = self.request("/api/car/999")
        self.assertEqual(status, 404)
        status, _ = self.request("/images/../schema.sql")
        self.assertEqual(status, 404)

    def test_cart_uses_database_price(self):
        self.assertEqual(self.add_item(quantity=2)[0], 200)
        self.assertEqual(self.add_item()[0], 200)
        status, cart = self.request(f"/api/Cart/GetCart/{self.user_id}", token=self.token)
        self.assertEqual(status, 200)
        self.assertEqual(cart["items"][0]["price"], 1500000.0)
        self.assertEqual(cart["totalQuantity"], 3)
        self.assertEqual(cart["totalPrice"], 4500000.0)

    def test_quantity_update_remove_and_limits(self):
        self.assertEqual(self.add_item(quantity=-1)[0], 400)
        self.assertEqual(self.add_item(quantity=99)[0], 200)
        self.assertEqual(self.add_item()[0], 409)
        for quantity in (2, 0):
            status, _ = self.request("/api/Cart/UpdateQuantity", "PUT", {
                "userId": self.user_id, "cartItem": {"id": 101, "quantity": quantity},
            }, self.token)
            self.assertEqual(status, 200)
        _, cart = self.request(f"/api/Cart/GetCart/{self.user_id}", token=self.token)
        self.assertEqual(cart["items"], [])

    def test_checkout_only_selected_item_and_history(self):
        self.add_item()
        self.add_item(202)
        self.assertEqual(self.checkout()[0], 200)
        _, cart = self.request(f"/api/Cart/GetCart/{self.user_id}", token=self.token)
        self.assertEqual([item["id"] for item in cart["items"]], [202])
        _, orders = self.request(f"/api/Order/GetOrders/{self.user_id}", token=self.token)
        self.assertEqual(orders[0]["item"]["price"], 1500000.0)
        self.assertEqual(orders[0]["clientName"], self.user_data["name"])
        self.assertEqual(orders[0]["status"], "Pending")
        self.assertEqual(self.checkout()[0], 404)

    def test_concurrent_checkout_creates_one_order(self):
        self.add_item()
        with ThreadPoolExecutor(max_workers=2) as pool:
            statuses = list(pool.map(lambda _: self.checkout()[0], range(2)))
        self.assertEqual(sorted(statuses), [200, 404])
        _, orders = self.request(f"/api/Order/GetOrders/{self.user_id}", token=self.token)
        self.assertEqual(len(orders), 1)

    def test_profile_update_and_persistence(self):
        status, result = self.request(f"/api/Auth/UpdateProfile/{self.user_id}", "PUT", {
            **self.user_data, "name": "Пётр", "password": "",
        }, self.token)
        self.assertEqual(status, 200)
        self.assertEqual(result["user"]["name"], "Пётр")
        # Reopening/initializing the file preserves users and doesn't duplicate the catalogue.
        reopened = Database(self.database.path)
        reopened.initialize()
        with reopened.connect() as db:
            self.assertEqual(db.execute("SELECT name FROM users WHERE id = ?", (self.user_id,)).fetchone()[0], "Пётр")
            self.assertEqual(db.execute("SELECT count(*) FROM configurations").fetchone()[0], 18)

    def test_malformed_json_and_invalid_identifiers(self):
        status, _ = self.request("/api/Auth/Login", "POST", raw=b"{")
        self.assertEqual(status, 400)
        status, _ = self.request("/api/Auth/Login", "POST", raw=b"[]")
        self.assertEqual(status, 400)
        status, _ = self.request("/api/car/abc")
        self.assertEqual(status, 400)


if __name__ == "__main__":
    unittest.main()
