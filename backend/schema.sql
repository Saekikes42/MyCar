CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
    birth_date TEXT NOT NULL,
    password_hash TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS cars (
    id INTEGER PRIMARY KEY,
    brand TEXT NOT NULL,
    model TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0)
);
CREATE TABLE IF NOT EXISTS configurations (
    id INTEGER PRIMARY KEY,
    car_id INTEGER NOT NULL REFERENCES cars(id),
    year INTEGER NOT NULL,
    color TEXT NOT NULL,
    trim TEXT NOT NULL,
    price_cents INTEGER NOT NULL CHECK (price_cents > 0),
    description TEXT NOT NULL,
    image_url TEXT NOT NULL,
    UNIQUE(car_id, year, color, trim)
);
CREATE TABLE IF NOT EXISTS cart_items (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    configuration_id INTEGER NOT NULL REFERENCES configurations(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    PRIMARY KEY(user_id, configuration_id)
);
CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    item_json TEXT NOT NULL,
    client_name TEXT NOT NULL,
    client_email TEXT NOT NULL,
    delivery_method TEXT NOT NULL,
    salon_location TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    request_date TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending', 'Approved', 'Cancelled')),
    notes TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS orders_user_id ON orders(user_id, id DESC);
PRAGMA user_version = 1;
