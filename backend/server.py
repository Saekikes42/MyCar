"""Run from the repository root: python -m backend.server"""

import argparse
import json
import logging
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

from .database import Database
from .service import ApiError, Service, auth_response, require_owner
from .security import token_hash

ROOT = Path(__file__).resolve().parent.parent
IMAGES = {name: ROOT / "app/src/main/res/drawable" / name
          for name in ("car_image.png", "image_carousel.png", "aura.png")}


class DemoServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, database):
        self.database = database
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    server_version = "MyCarDemo/1.0"

    def do_GET(self):
        self.handle_request()

    def do_POST(self):
        self.handle_request()

    def do_PUT(self):
        self.handle_request()

    def reply(self, status, value, content_type="application/json; charset=utf-8"):
        body = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        if self.headers.get_content_type() != "application/json":
            raise ApiError(415, "Ожидается application/json")
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 1 <= length <= 65536:
                raise ApiError(413, "Размер запроса должен быть от 1 до 65536 байт")
            value = json.loads(self.rfile.read(length))
            if not isinstance(value, dict):
                raise ApiError(400, "Ожидается JSON-объект")
            return value
        except (ValueError, UnicodeDecodeError):
            raise ApiError(400, "Некорректный JSON") from None

    def handle_request(self):
        self.connection.settimeout(10)
        try:
            path = urlsplit(self.path).path.rstrip("/")
            if self.command == "GET" and path.startswith("/images/"):
                image = IMAGES.get(path.removeprefix("/images/"))
                if image is None or not image.is_file():
                    raise ApiError(404, "Изображение не найдено")
                self.reply(200, image.read_bytes(), "image/png")
                return
            data = self.read_json() if self.command in ("POST", "PUT") else {}
            with self.server.database.connect() as db:
                result = self.route(Service(db), path, data)
            self.reply(200, result)
        except ApiError as error:
            self.reply(error.status, {"success": False, "message": error.message})
        except sqlite3.IntegrityError as error:
            message = "Email уже зарегистрирован" if "users.email" in str(error) else "Недопустимое количество товара"
            self.reply(409, {"success": False, "message": message})
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass
        except Exception:
            logging.exception("Request failed")
            self.reply(500, {"success": False, "message": "Внутренняя ошибка сервера"})

    def route(self, service, path, data):
        method = self.command
        if method == "GET" and path == "/health":
            return {"status": "ok", "databaseVersion": service.db.execute("PRAGMA user_version").fetchone()[0]}
        if method == "POST" and path == "/api/Auth/Register":
            return service.register(data)
        if method == "POST" and path == "/api/Auth/Login":
            return service.login(data)
        if method == "GET" and path == "/api/car":
            return service.cars()
        parts = path.strip("/").split("/")
        if method == "GET" and len(parts) == 3 and parts[:2] == ["api", "car"]:
            return service.cars(self.path_id(parts[-1]))
        actor = service.authenticate(self.headers.get("Authorization", ""))
        if method == "POST" and path == "/api/Auth/Logout":
            service.db.execute("DELETE FROM sessions WHERE token_hash = ?",
                               (token_hash(self.headers["Authorization"][7:]),))
            return {"success": True}
        if len(parts) == 4 and parts[0] == "api":
            user_id = self.path_id(parts[-1])
            if method == "GET" and parts[1:3] == ["Auth", "GetProfile"]:
                require_owner(actor, user_id)
                return auth_response(service.user(user_id))
            if method == "PUT" and parts[1:3] == ["Auth", "UpdateProfile"]:
                return service.update_profile(actor, user_id, data)
            if method == "GET" and parts[1:3] == ["Cart", "GetCart"]:
                return service.cart(actor, user_id)
            if method == "GET" and parts[1:3] == ["Order", "GetOrders"]:
                return service.orders(actor, user_id)
        if method == "POST" and path == "/api/Cart/AddToCart":
            return service.change_cart(actor, data, add=True)
        if method == "PUT" and path == "/api/Cart/UpdateQuantity":
            return service.change_cart(actor, data)
        if method == "POST" and path == "/api/Order/CreateOrder":
            return service.create_order(actor, data)
        raise ApiError(404, "Маршрут не найден")

    @staticmethod
    def path_id(value):
        if not value.isascii() or not value.isdigit() or not 0 < int(value) <= 2_147_483_647:
            raise ApiError(400, "Некорректный идентификатор")
        return int(value)


def main():
    parser = argparse.ArgumentParser(description="Local MyCarDemo API with SQLite")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5114)
    parser.add_argument("--db", type=Path, default=ROOT / "backend/data/mycar.db")
    args = parser.parse_args()
    database = Database(args.db)
    database.initialize()
    with DemoServer((args.host, args.port), database) as server:
        print(f"MyCarDemo API: http://{args.host}:{args.port} | SQLite: {database.path}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
