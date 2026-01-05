"""Password hashing and opaque session tokens, using the Python standard library."""

import hashlib
import hmac
import secrets

ITERATIONS = 600_000


def hash_password(password):
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), ITERATIONS)
    return f"pbkdf2_sha256${ITERATIONS}${salt}${digest.hex()}"


def verify_password(password, encoded):
    algorithm, iterations, salt, expected = encoded.split("$")
    if algorithm != "pbkdf2_sha256":
        return False
    actual = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), int(iterations))
    return hmac.compare_digest(actual.hex(), expected)


def token_hash(token):
    return hashlib.sha256(token.encode()).hexdigest()
