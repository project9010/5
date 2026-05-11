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


def validate_credentials(username, password):
    """Проверить логин и пароль перед регистрацией или входом."""
    username = normalize_username(username)

    if len(username) < MIN_USERNAME_LENGTH:
        return False, f"Имя пользователя должно содержать минимум {MIN_USERNAME_LENGTH} символа."

    if len(password) < MIN_PASSWORD_LENGTH:
        return False, f"Пароль должен содержать минимум {MIN_PASSWORD_LENGTH} символов."

    return True, ""


def register_user_record(username, password):
    """Создать пользователя через программный интерфейс и вернуть нормализованное имя."""
    username = normalize_username(username)
    is_valid, message = validate_credentials(username, password)
    if not is_valid:
        raise ValueError(message)

    try:
        with get_connection() as connection:
            # Знак ? в SQL-запросе защищает от SQL-инъекций.
            connection.execute(
                "INSERT INTO users (username, password) VALUES (?, ?)",
                (username, hash_password(password)),
            )
            connection.commit()
    except sqlite3.IntegrityError as error:
        raise ValueError("Пользователь с таким именем уже существует.") from error

    return username


def authenticate_user(username, password):
    """Проверить пользователя через программный интерфейс и вернуть имя при успехе."""
    username = normalize_username(username)

    with get_connection() as connection:
        user = connection.execute(
            "SELECT id, username, password FROM users WHERE username = ?",
            (username,),
        ).fetchone()

    # Сначала проверяем, найден ли пользователь, затем сравниваем пароль с хешем.
    if user is None or not verify_password(password, user["password"]):
        raise ValueError("Неверное имя пользователя или пароль.")

    return user["username"]


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
        registered_username = register_user_record(username, password)
    except ValueError as error:
        print(error)
        return None
    except sqlite3.Error as error:
        print(f"Database error during registration: {error}")
        return None

    print("Registration completed successfully. You can now log in.")
    return registered_username


def login_user():
    """Проверить логин и пароль, вернуть имя пользователя при успешном входе."""
    print("\nUser login")
    username = normalize_username(prompt_non_empty("Username: "))
    password = prompt_non_empty("Password: ")

    try:
        authenticated_username = authenticate_user(username, password)
    except ValueError:
        print("Invalid username or password.")
        return None
    except sqlite3.Error as error:
        print(f"Database error during login: {error}")
        return None

    print(f"Welcome, {authenticated_username}!")
    return authenticated_username
