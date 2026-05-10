# Document Management System for АО "ОМЕГА"

A console-based document management system written in Python 3 with SQLite storage. The project is structured as a small real application for an **Information Systems and Programming** diploma project.

## Project structure

```text
main.py       - application entry point and menu navigation
auth.py       - user registration, login, and SHA-256 password hashing
documents.py  - document creation, viewing, search, deletion, and status workflow
database.py   - SQLite connection and automatic schema creation
utils.py      - shared input validation and console output helpers
```

## Features

- User registration and login.
- Password hashing with SHA-256 and per-user salt.
- SQLite database created automatically on startup.
- Create, list, view, delete, and search documents.
- Update document status through a fixed workflow:
  - `created`
  - `in progress`
  - `approved`
  - `rejected`
- Secure SQL execution with parameterized queries for user values.
- Console input validation for empty fields, document IDs, and menu choices.

## Database tables

### users

| Column | Description |
| --- | --- |
| `id` | User identifier |
| `username` | Unique username |
| `password` | SHA-256 password hash |

### documents

| Column | Description |
| --- | --- |
| `id` | Document identifier |
| `title` | Document title |
| `content` | Document content |
| `author` | Username of the creator |
| `created_at` | Creation timestamp |
| `status` | Current workflow status |

## How to run

```bash
python main.py
```

The database file `dms_omega.db` is created automatically in the project directory.
