"""Точка входа в систему электронного документооборота АО "ОМЕГА"."""

import sqlite3

from auth import login_user, register_user
from database import initialize_database
from documents import (
    create_document,
    delete_document,
    search_documents,
    update_document_status,
    view_all_documents,
    view_document_by_id,
)
from utils import print_separator

# Название организации выводится в заголовке главного меню.
ORGANIZATION_NAME = 'АО "ОМЕГА"'


def show_start_menu():
    """Показать стартовое меню для пользователя, который ещё не вошёл в систему."""
    print_separator()
    print(f"Document Management System - {ORGANIZATION_NAME}")
    print_separator()
    print("1. Register")
    print("2. Login")
    print("0. Exit")


def show_document_menu(username):
    """Показать меню работы с документами для авторизованного пользователя."""
    print_separator()
    print(f"Logged in as: {username}")
    print("1. Create document")
    print("2. View all documents")
    print("3. View document by ID")
    print("4. Search documents")
    print("5. Update document status")
    print("6. Delete document")
    print("0. Logout")


def document_menu(username):
    """Обрабатывать действия пользователя после успешного входа в систему."""
    # Словарь связывает пункт меню с функцией, которую нужно выполнить.
    # Такой подход проще расширять: для нового пункта достаточно добавить строку.
    actions = {
        "1": lambda: create_document(username),
        "2": view_all_documents,
        "3": view_document_by_id,
        "4": search_documents,
        "5": update_document_status,
        "6": delete_document,
    }

    while True:
        show_document_menu(username)
        choice = input("Choose an option: ").strip()

        # 0 используется для выхода из текущего меню обратно на стартовый экран.
        if choice == "0":
            print("Logged out.")
            break

        action = actions.get(choice)
        if action is None:
            print("Invalid option. Please try again.")
            continue

        action()


def main():
    """Запустить приложение и основной цикл меню."""
    try:
        # При старте приложения создаём базу данных и таблицы, если их ещё нет.
        initialize_database()
    except sqlite3.Error as error:
        print(f"Failed to initialize database: {error}")
        return

    while True:
        show_start_menu()
        choice = input("Choose an option: ").strip()

        if choice == "1":
            register_user()
        elif choice == "2":
            username = login_user()
            if username:
                document_menu(username)
        elif choice == "0":
            print("Goodbye!")
            break
        else:
            print("Invalid option. Please try again.")


# Этот блок позволяет запускать программу командой: python main.py
if __name__ == "__main__":
    main()
