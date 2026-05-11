"""Создание, просмотр, поиск, удаление документов и изменение их статуса."""

import sqlite3
from datetime import datetime

from database import get_connection
from utils import (
    VALID_STATUSES,
    choose_status,
    confirm_action,
    print_document,
    print_document_summary,
    prompt_non_empty,
    prompt_positive_int,
)

# Новый документ всегда начинает работу со статуса created.
DEFAULT_STATUS = "created"

# Единый список колонок уменьшает дублирование в SELECT-запросах.
DOCUMENT_COLUMNS = "id, title, content, author, created_at, status"

# Пользователь выбирает пункт меню, а программа получает безопасное имя поля.
SEARCH_OPTIONS = {
    "1": ("title", "Title keyword: "),
    "2": ("content", "Content keyword: "),
    "3": ("author", "Author keyword: "),
}


def create_document_record(title, content, author):
    """Добавить документ в базу данных и вернуть его новый ID."""
    created_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    with get_connection() as connection:
        cursor = connection.execute(
            """
            INSERT INTO documents (title, content, author, created_at, status)
            VALUES (?, ?, ?, ?, ?)
            """,
            (title, content, author, created_at, DEFAULT_STATUS),
        )
        connection.commit()
        return cursor.lastrowid


def list_documents():
    """Вернуть все документы, отсортированные от новых к старым."""
    with get_connection() as connection:
        return connection.execute(
            f"SELECT {DOCUMENT_COLUMNS} FROM documents ORDER BY id DESC"
        ).fetchall()


def get_document_by_id(document_id):
    """Вернуть документ по ID или None, если документ не найден."""
    with get_connection() as connection:
        return connection.execute(
            f"SELECT {DOCUMENT_COLUMNS} FROM documents WHERE id = ?",
            (document_id,),
        ).fetchone()


def delete_document_record(document_id):
    """Удалить документ и вернуть True, если строка была удалена."""
    with get_connection() as connection:
        cursor = connection.execute("DELETE FROM documents WHERE id = ?", (document_id,))
        connection.commit()
        return cursor.rowcount > 0


def update_document_status_record(document_id, new_status):
    """Обновить статус документа и вернуть True, если запись была изменена."""
    if new_status not in VALID_STATUSES:
        raise ValueError("Invalid document status.")

    with get_connection() as connection:
        cursor = connection.execute(
            "UPDATE documents SET status = ? WHERE id = ?",
            (new_status, document_id),
        )
        connection.commit()
        return cursor.rowcount > 0


def search_document_records(field, keyword):
    """Найти документы по разрешенному полю и вернуть совпадающие строки."""
    # Разрешаем искать только по полям из SEARCH_OPTIONS, чтобы не подставлять в SQL что угодно.
    allowed_fields = {option[0] for option in SEARCH_OPTIONS.values()}
    if field not in allowed_fields:
        raise ValueError("Invalid search field.")

    search_pattern = f"%{keyword}%"
    query = (
        f"SELECT {DOCUMENT_COLUMNS} FROM documents "
        f"WHERE {field} LIKE ? COLLATE NOCASE ORDER BY id DESC"
    )

    with get_connection() as connection:
        return connection.execute(query, (search_pattern,)).fetchall()


def create_document(author):
    """Запросить данные у пользователя и создать новый документ."""
    print("\nCreate document")
    title = prompt_non_empty("Title: ")
    content = prompt_non_empty("Content: ")

    try:
        document_id = create_document_record(title, content, author)
        print(f"Document created successfully with ID {document_id}.")
    except sqlite3.Error as error:
        print(f"Database error while creating document: {error}")


def view_all_documents():
    """Показать все документы в кратком виде."""
    print("\nAll documents")
    try:
        documents = list_documents()
    except sqlite3.Error as error:
        print(f"Database error while loading documents: {error}")
        return

    if not documents:
        print("No documents found.")
        return

    for document in documents:
        print_document_summary(document)


def view_document_by_id():
    """Запросить ID и показать полный текст выбранного документа."""
    print("\nView document by ID")
    document_id = prompt_positive_int("Document ID: ")

    try:
        document = get_document_by_id(document_id)
    except sqlite3.Error as error:
        print(f"Database error while loading document: {error}")
        return

    if document is None:
        print("Document not found.")
        return

    print_document(document)


def delete_document():
    """Удалить документ по ID после подтверждения пользователя."""
    print("\nDelete document")
    document_id = prompt_positive_int("Document ID: ")

    try:
        document = get_document_by_id(document_id)
        if document is None:
            print("Document not found.")
            return

        # Перед удалением показываем документ, чтобы пользователь не ошибся с ID.
        print_document(document)
        if not confirm_action(f"Delete document {document_id} permanently?"):
            print("Deletion cancelled.")
            return

        if delete_document_record(document_id):
            print("Document deleted successfully.")
        else:
            print("Document was not deleted because it no longer exists.")
    except sqlite3.Error as error:
        print(f"Database error while deleting document: {error}")


def update_document_status():
    """Изменить статус документа с использованием заранее заданных статусов."""
    print("\nUpdate document status")
    document_id = prompt_positive_int("Document ID: ")

    try:
        document = get_document_by_id(document_id)
        if document is None:
            print("Document not found.")
            return

        print_document_summary(document)
        print(f"Current status: {document['status']}")
        new_status = choose_status()

        if update_document_status_record(document_id, new_status):
            print("Document status updated successfully.")
        else:
            print("Document status was not updated because the document no longer exists.")
    except (sqlite3.Error, ValueError) as error:
        print(f"Error while updating status: {error}")


def search_documents():
    """Искать документы по заголовку, содержанию или автору."""
    print("\nSearch documents")
    print("1. Search by title")
    print("2. Search by content")
    print("3. Search by author")

    choice = input("Choose search option: ").strip()
    if choice not in SEARCH_OPTIONS:
        print("Invalid search option.")
        return

    field, prompt = SEARCH_OPTIONS[choice]
    keyword = prompt_non_empty(prompt)

    try:
        documents = search_document_records(field, keyword)
    except (sqlite3.Error, ValueError) as error:
        print(f"Error while searching documents: {error}")
        return

    if not documents:
        print("No matching documents found.")
        return

    print(f"Found documents: {len(documents)}")
    for document in documents:
        print_document_summary(document)
