"""SQLite setup and repeatable demo catalogue initialization."""

import sqlite3
from contextlib import contextmanager
from pathlib import Path


class Database:
    def __init__(self, path):
        self.path = Path(path)

    @contextmanager
    def connect(self):
        connection = sqlite3.connect(self.path, timeout=10)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def initialize(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, 1):
                raise RuntimeError(f"Unsupported database version: {version}")
            db.execute("PRAGMA journal_mode = WAL")
            db.executescript(Path(__file__).with_name("schema.sql").read_text(encoding="utf-8"))
            # Stable IDs keep initialization repeatable and preserve user data.
            cars = [
                (1, "LADA", "Vesta", 10, 150000000, "/images/car_image.png"),
                (2, "LADA", "Granta", 12, 100000000, "/images/image_carousel.png"),
                (3, "LADA", "Aura", 5, 260000000, "/images/aura.png"),
            ]
            for car_id, brand, model, quantity, price, image in cars:
                db.execute("INSERT OR IGNORE INTO cars VALUES (?, ?, ?, ?)",
                           (car_id, brand, model, quantity))
                for year_index, year in enumerate((2025, 2024)):
                    for trim_index, trim in enumerate(("Стандарт", "Комфорт", "Люкс")):
                        config_id = car_id * 100 + year_index * 10 + trim_index + 1
                        db.execute(
                            "INSERT OR IGNORE INTO configurations VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            (config_id, car_id, year, "Белый", trim,
                             price + trim_index * 20000000 - year_index * 10000000,
                             f"Учебная комплектация {trim}", image),
                        )
