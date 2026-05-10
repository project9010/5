"""Регистрация пользователей, вход в систему и хеширование паролей."""

import hashlib
import hmac
import secrets
import sqlite3

from database import get_connection
from utils import prompt_non_empty

# Минимальные ограничения делают ввод чуть безопаснее и удобнее для проверки.
MIN_USERNAME_LENGTH = 3
MIN_PASSWORD_LENGTH = 6

# Количество байтов для соли. Соль делает одинаковые пароли разными в базе.
SALT_BYTES = 16
HASH_SEPARATOR = "$"


def normalize_username(username):
    """Вернуть имя пользователя без лишних пробелов в начале и конце."""
    return username.strip()


def hash_password(password, salt=None):
    """Вернуть SHA-256 хеш пароля в формате salt$hash."""
    if salt is None:
        # secrets подходит для генерации случайных значений, связанных с безопасностью.
        salt = secrets.token_hex(SALT_BYTES)

    # В базе хранится не сам пароль, а результат хеширования соли и пароля.
    password_hash = hashlib.sha256(f"{salt}{password}".encode("utf-8")).hexdigest()
    return f"{salt}{HASH_SEPARATOR}{password_hash}"


def verify_password(password, stored_password):
    """Проверить введённый пароль по сохранённому хешу."""
    if HASH_SEPARATOR in stored_password:
        # Новый формат хранения: соль и хеш лежат в одной строке через разделитель.
        salt, expected_hash = stored_password.split(HASH_SEPARATOR, 1)
        actual_hash = hashlib.sha256(f"{salt}{password}".encode("utf-8")).hexdigest()
        return hmac.compare_digest(actual_hash, expected_hash)

    # Поддержка старого варианта, если в базе уже есть простые SHA-256 хеши без соли.
    legacy_hash = hashlib.sha256(password.encode("utf-8")).hexdigest()
    return hmac.compare_digest(legacy_hash, stored_password)


def register_user():
    """Зарегистрировать нового пользователя и сохранить только хеш пароля."""
    print("\nUser registration")
    username = normalize_username(
        prompt_non_empty("Username: ", min_length=MIN_USERNAME_LENGTH)
    )
    password = prompt_non_empty("Password: ", min_length=MIN_PASSWORD_LENGTH)
    password_confirmation = prompt_non_empty(
        "Confirm password: ", min_length=MIN_PASSWORD_LENGTH
    )

    if password != password_confirmation:
        print("Passwords do not match. Registration cancelled.")
        return None

    try:
        with get_connection() as connection:
            # Знак ? в SQL-запросе защищает от SQL-инъекций.
            connection.execute(
                "INSERT INTO users (username, password) VALUES (?, ?)",
                (username, hash_password(password)),
            )
            connection.commit()
    except sqlite3.IntegrityError:
        print("A user with this username already exists.")
        return None
    except sqlite3.Error as error:
        print(f"Database error during registration: {error}")
        return None

    print("Registration completed successfully. You can now log in.")
    return username


def login_user():
    """Проверить логин и пароль, вернуть имя пользователя при успешном входе."""
    print("\nUser login")
    username = normalize_username(prompt_non_empty("Username: "))
    password = prompt_non_empty("Password: ")

    try:
        with get_connection() as connection:
            user = connection.execute(
                "SELECT id, username, password FROM users WHERE username = ?",
                (username,),
            ).fetchone()
    except sqlite3.Error as error:
        print(f"Database error during login: {error}")
        return None

    # Сначала проверяем, найден ли пользователь, затем сравниваем пароль с хешем.
    if user is None or not verify_password(password, user["password"]):
        print("Invalid username or password.")
        return None

    print(f"Welcome, {user['username']}!")
    return user["username"]
