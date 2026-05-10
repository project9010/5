"""Настройка SQLite базы данных и функции подключения к ней."""

import sqlite3
from pathlib import Path

# База данных хранится рядом с файлами проекта.
DB_NAME = "dms_omega.db"
DB_PATH = Path(__file__).resolve().parent / DB_NAME


# SQL-команда создаёт таблицу пользователей, если она ещё не существует.
CREATE_USERS_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password TEXT NOT NULL
)
"""

# SQL-команда создаёт таблицу документов и ограничивает возможные статусы.
CREATE_DOCUMENTS_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    author TEXT NOT NULL,
    created_at TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN (
        'created',
        'in progress',
        'approved',
        'rejected'
    ))
)
"""

# Индекс ускоряет поиск и фильтрацию документов по автору.
CREATE_DOCUMENTS_AUTHOR_INDEX_SQL = """
CREATE INDEX IF NOT EXISTS idx_documents_author ON documents(author)
"""

# Индекс ускоряет операции, связанные со статусом документа.
CREATE_DOCUMENTS_STATUS_INDEX_SQL = """
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status)
"""


def get_connection():
    """Вернуть подключение к SQLite с доступом к полям строк по имени колонки."""
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def initialize_database():
    """Создать таблицы и индексы, необходимые для работы приложения."""
    with get_connection() as connection:
        # Включаем поддержку внешних ключей на случай расширения схемы в будущем.
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute(CREATE_USERS_TABLE_SQL)
        connection.execute(CREATE_DOCUMENTS_TABLE_SQL)
        connection.execute(CREATE_DOCUMENTS_AUTHOR_INDEX_SQL)
        connection.execute(CREATE_DOCUMENTS_STATUS_INDEX_SQL)
        connection.commit()
