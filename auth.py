diff --git a/auth.py b/auth.py
new file mode 100644
index 0000000000000000000000000000000000000000..13ca4ddbb1561894fb175a7b95ea844c8c8c562c
--- /dev/null
+++ b/auth.py
@@ -0,0 +1,96 @@
+"""User registration, login, and password hashing logic."""
+
+import hashlib
+import hmac
+import secrets
+import sqlite3
+
+from database import get_connection
+from utils import prompt_non_empty
+
+MIN_USERNAME_LENGTH = 3
+MIN_PASSWORD_LENGTH = 6
+SALT_BYTES = 16
+HASH_SEPARATOR = "$"
+
+
+def normalize_username(username):
+    """Return a normalized username for consistent storage and lookup."""
+    return username.strip()
+
+
+def hash_password(password, salt=None):
+    """Return a salted SHA-256 password hash in the format salt$hash."""
+    if salt is None:
+        salt = secrets.token_hex(SALT_BYTES)
+
+    password_hash = hashlib.sha256(f"{salt}{password}".encode("utf-8")).hexdigest()
+    return f"{salt}{HASH_SEPARATOR}{password_hash}"
+
+
+def verify_password(password, stored_password):
+    """Verify a password against a stored salted or legacy SHA-256 hash."""
+    if HASH_SEPARATOR in stored_password:
+        salt, expected_hash = stored_password.split(HASH_SEPARATOR, 1)
+        actual_hash = hashlib.sha256(f"{salt}{password}".encode("utf-8")).hexdigest()
+        return hmac.compare_digest(actual_hash, expected_hash)
+
+    legacy_hash = hashlib.sha256(password.encode("utf-8")).hexdigest()
+    return hmac.compare_digest(legacy_hash, stored_password)
+
+
def register_user():
    """Register a new user and store only the hashed password."""
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
    """Authenticate a user and return their username on success."""
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

    if user is None or not verify_password(password, user["password"]):
        print("Invalid username or password.")
        return None

+    print(f"Welcome, {user['username']}!")
+    return user["username"]
