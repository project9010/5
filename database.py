"""SQLite database setup and connection helpers for the DMS."""

import sqlite3
from pathlib import Path

DB_NAME = "dms_omega.db"
DB_PATH = Path(__file__).resolve().parent / DB_NAME


CREATE_USERS_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password TEXT NOT NULL
)
"""

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

CREATE_DOCUMENTS_AUTHOR_INDEX_SQL = """
CREATE INDEX IF NOT EXISTS idx_documents_author ON documents(author)
"""

CREATE_DOCUMENTS_STATUS_INDEX_SQL = """
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status)
"""


def get_connection():
    """Return a SQLite connection with rows accessible by column name."""
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def initialize_database():
    """Create the database schema required by the application."""
    with get_connection() as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute(CREATE_USERS_TABLE_SQL)
        connection.execute(CREATE_DOCUMENTS_TABLE_SQL)
        connection.execute(CREATE_DOCUMENTS_AUTHOR_INDEX_SQL)
        connection.execute(CREATE_DOCUMENTS_STATUS_INDEX_SQL)
        connection.commit()
