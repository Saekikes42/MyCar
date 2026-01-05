"""Validation and transactional operations, independent of HTTP handling."""

import json
import re
import secrets
import time
from datetime import datetime, timezone

from .security import hash_password, token_hash, verify_password


class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def text(data, key, minimum=1, maximum=200):
    value = data.get(key)
    if not isinstance(value, str) or not minimum <= len(value.strip()) <= maximum:
        raise ApiError(400, f"Некорректное поле: {key}")
    return value.strip()


def integer(data, key, minimum=1, maximum=2_147_483_647):
    value = data.get(key)
    if type(value) is not int or not minimum <= value <= maximum:
        raise ApiError(400, f"Некорректное поле: {key}")
    return value


def profile_fields(data):
    name = text(data, "name", 2, 20)
    email = text(data, "email", 6, 60).lower()
    birth_date = text(data, "birthDate", maximum=10)
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise ApiError(400, "Введите корректный email")
    for date_format in ("%d.%m.%Y", "%Y-%m-%d"):
        try:
            parsed = datetime.strptime(birth_date, date_format).date()
            if parsed > datetime.now(timezone.utc).date():
                raise ApiError(400, "Дата рождения не может быть в будущем")
            break
        except ValueError:
            continue
    else:
        raise ApiError(400, "Дата рождения: ДД.ММ.ГГГГ или ГГГГ-ММ-ДД")
    return name, email, birth_date


def password_field(data):
    password = data.get("password")
    if (not isinstance(password, str) or not 8 <= len(password) <= 64
            or not any(c.isupper() for c in password)
            or not any(c.islower() for c in password)
            or not any(c.isdigit() for c in password)
            or not any(c in "!@#$%^&*()_+-=[]{}|;:,.<>?" for c in password)):
        raise ApiError(400, "Пароль: 8–64 символа, заглавная и строчная буквы, цифра и спецсимвол")
    return password


def public_user(row):
    return {"id": row["id"], "name": row["name"], "email": row["email"], "birthDate": row["birth_date"]}


def auth_response(row, message="", **extra):
    return {"success": True, "message": message, "user": public_user(row), **extra}


def require_owner(actor, user_id):
    if actor != user_id:
        raise ApiError(403, "Доступ к данным другого пользователя запрещён")


class Service:
    def __init__(self, db):
        self.db = db

    def authenticate(self, header):
        if not header.startswith("Bearer "):
            raise ApiError(401, "Войдите в аккаунт")
        row = self.db.execute(
            "SELECT user_id FROM sessions WHERE token_hash = ? AND expires_at > ?",
            (token_hash(header[7:]), int(time.time())),
        ).fetchone()
        if row is None:
            raise ApiError(401, "Сессия истекла. Войдите заново")
        return row["user_id"]

    def register(self, data):
        fields = profile_fields(data)
        encoded = hash_password(password_field(data))
        cursor = self.db.execute(
            "INSERT INTO users(name, email, birth_date, password_hash) VALUES (?, ?, ?, ?)",
            (*fields, encoded),
        )
        return auth_response(self.user(cursor.lastrowid), "Регистрация завершена")

    def user(self, user_id):
        row = self.db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
        if row is None:
            raise ApiError(404, "Пользователь не найден")
        return row

    def login(self, data):
        email = text(data, "email", maximum=60).lower()
        password = data.get("password")
        if not isinstance(password, str) or not 1 <= len(password) <= 64:
            raise ApiError(400, "Введите пароль")
        row = self.db.execute("SELECT * FROM users WHERE email = ?", (email,)).fetchone()
        if row is None or not verify_password(password, row["password_hash"]):
            raise ApiError(401, "Неверный email или пароль")
        token = secrets.token_urlsafe(32)
        now = int(time.time())
        self.db.execute("DELETE FROM sessions WHERE expires_at <= ?", (now,))
        self.db.execute("INSERT INTO sessions VALUES (?, ?, ?)",
                        (token_hash(token), row["id"], now + 7 * 24 * 3600))
        return auth_response(row, "Вход выполнен", token=token)

    def update_profile(self, actor, user_id, data):
        require_owner(actor, user_id)
        fields = profile_fields(data)
        self.db.execute("UPDATE users SET name = ?, email = ?, birth_date = ? WHERE id = ?",
                        (*fields, user_id))
        if data.get("password"):
            self.db.execute("UPDATE users SET password_hash = ? WHERE id = ?",
                            (hash_password(password_field(data)), user_id))
            # Changing a password invalidates all sessions; the client must log in again.
            self.db.execute("DELETE FROM sessions WHERE user_id = ?", (user_id,))
        return auth_response(self.user(user_id), "Профиль обновлён")

    def cars(self, car_id=None):
        rows = self.db.execute("SELECT * FROM cars ORDER BY id").fetchall() if car_id is None else \
            self.db.execute("SELECT * FROM cars WHERE id = ?", (car_id,)).fetchall()
        result = []
        for row in rows:
            variants = {}
            for config in self.db.execute(
                "SELECT * FROM configurations WHERE car_id = ? ORDER BY year DESC, price_cents",
                (row["id"],),
            ):
                colors = variants.setdefault(config["year"], {})
                color = colors.setdefault(config["color"], {
                    "name": config["color"], "images": [config["image_url"]], "complectations": [],
                })
                color["complectations"].append({
                    "id": config["id"], "name": config["trim"], "price": config["price_cents"] / 100,
                    "description": config["description"], "imageUrl": config["image_url"],
                })
            result.append({**dict(row), "variants": [
                {"year": year, "colors": list(colors.values())} for year, colors in variants.items()
            ]})
        if car_id is not None:
            if not result:
                raise ApiError(404, "Автомобиль не найден")
            return result[0]
        return result

    def cart_rows(self, user_id, item_id=None):
        query = """SELECT configurations.*, cars.brand, cars.model, cart_items.quantity
                   FROM cart_items JOIN configurations ON configurations.id = configuration_id
                   JOIN cars ON cars.id = car_id WHERE user_id = ?"""
        params = [user_id]
        if item_id is not None:
            query += " AND configuration_id = ?"
            params.append(item_id)
        return self.db.execute(query + " ORDER BY configuration_id", params).fetchall()

    @staticmethod
    def cart_item(row):
        return {"id": row["id"], "carName": f"{row['brand']} {row['model']}",
                "variantDesc": f"{row['year']} год, {row['color']}, {row['trim']}",
                "imageUrl": row["image_url"], "price": row["price_cents"] / 100,
                "quantity": row["quantity"]}

    def cart(self, actor, user_id):
        require_owner(actor, user_id)
        rows = self.cart_rows(user_id)
        return {"items": [self.cart_item(row) for row in rows],
                "totalQuantity": sum(row["quantity"] for row in rows),
                "totalPrice": sum(row["price_cents"] * row["quantity"] for row in rows) / 100}

    def change_cart(self, actor, data, add=False):
        user_id = integer(data, "userId")
        require_owner(actor, user_id)
        item = data.get("cartItem")
        if not isinstance(item, dict):
            raise ApiError(400, "Не указан товар")
        config_id = integer(item, "id")
        quantity = integer(item, "quantity", minimum=1 if add else 0, maximum=99)
        if self.db.execute("SELECT id FROM configurations WHERE id = ?", (config_id,)).fetchone() is None:
            raise ApiError(404, "Комплектация не найдена")
        if quantity == 0:
            self.db.execute("DELETE FROM cart_items WHERE user_id = ? AND configuration_id = ?",
                            (user_id, config_id))
        elif add:
            self.db.execute("""INSERT INTO cart_items VALUES (?, ?, ?)
                ON CONFLICT(user_id, configuration_id) DO UPDATE SET quantity = quantity + excluded.quantity""",
                            (user_id, config_id, quantity))
        else:
            cursor = self.db.execute("UPDATE cart_items SET quantity = ? WHERE user_id = ? AND configuration_id = ?",
                                     (quantity, user_id, config_id))
            if cursor.rowcount == 0:
                raise ApiError(404, "Товар отсутствует в корзине")
        return {"success": True}

    def create_order(self, actor, data):
        user_id = integer(data, "userId")
        require_owner(actor, user_id)
        item_id = integer(data, "itemId")
        # Serialize checkout: two requests cannot buy the same cart entry twice.
        self.db.execute("BEGIN IMMEDIATE")
        rows = self.cart_rows(user_id, item_id)
        if not rows:
            raise ApiError(404, "Товар отсутствует в корзине")
        delivery = text(data, "deliveryMethod")
        payment = text(data, "paymentMethod")
        if delivery not in ("Салон", "Доставка") or payment not in ("Кредит", "Наличные"):
            raise ApiError(400, "Некорректный способ доставки или оплаты")
        user = self.user(user_id)
        location = text(data, "salonLocation", maximum=500)
        notes = text(data, "notes", minimum=0, maximum=2000)
        cursor = self.db.execute("""INSERT INTO orders
            (user_id, item_json, client_name, client_email, delivery_method, salon_location,
             payment_method, request_date, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (user_id, json.dumps(self.cart_item(rows[0]), ensure_ascii=False), user["name"], user["email"],
             delivery, location, payment, datetime.now(timezone.utc).isoformat(), notes))
        self.db.execute("DELETE FROM cart_items WHERE user_id = ? AND configuration_id = ?", (user_id, item_id))
        return {"success": True, "orderId": cursor.lastrowid}

    def orders(self, actor, user_id):
        require_owner(actor, user_id)
        return [{"id": row["id"], "userId": row["user_id"], "item": json.loads(row["item_json"]),
                 "clientName": row["client_name"], "clientEmail": row["client_email"],
                 "deliveryMethod": row["delivery_method"], "salonLocation": row["salon_location"],
                 "paymentMethod": row["payment_method"], "requestDate": row["request_date"],
                 "status": row["status"], "notes": row["notes"]}
                for row in self.db.execute("SELECT * FROM orders WHERE user_id = ? ORDER BY id DESC", (user_id,))]
