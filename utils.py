"""Общие вспомогательные функции для консольного приложения."""

# Допустимые статусы документа. Другие значения в программе не используются.
VALID_STATUSES = ("created", "in progress", "approved", "rejected")


def print_separator():
    """Напечатать разделитель для меню и карточек документов."""
    print("-" * 70)


def prompt_non_empty(message, *, min_length=1):
    """Запрашивать ввод, пока пользователь не введёт непустое значение."""
    while True:
        value = input(message).strip()
        if len(value) >= min_length:
            return value

        if min_length <= 1:
            print("Input cannot be empty. Please try again.")
        else:
            print(f"Input must contain at least {min_length} characters.")


def prompt_positive_int(message):
    """Запрашивать ввод, пока пользователь не введёт положительное целое число."""
    while True:
        value = input(message).strip()
        try:
            number = int(value)
            if number > 0:
                return number
            print("Please enter a number greater than zero.")
        except ValueError:
            print("Please enter a valid number.")


def confirm_action(message):
    """Вернуть True только при явном подтверждении действия пользователем."""
    answer = input(f"{message} (y/n): ").strip().lower()
    return answer in {"y", "yes"}


def print_document(document):
    """Напечатать один документ в подробном и удобном для чтения виде."""
    print_separator()
    print(f"ID:         {document['id']}")
    print(f"Title:      {document['title']}")
    print(f"Author:     {document['author']}")
    print(f"Created at: {document['created_at']}")
    print(f"Status:     {document['status']}")
    print("Content:")
    print(document["content"])
    print_separator()


def print_document_summary(document):
    """Напечатать одну краткую строку документа для списков и результатов поиска."""
    print(
        f"#{document['id']} | {document['title']} | "
        f"author: {document['author']} | status: {document['status']} | "
        f"created: {document['created_at']}"
    )


def choose_status():
    """Предложить пользователю выбрать один из допустимых статусов документа."""
    print("Available statuses:")
    for index, status in enumerate(VALID_STATUSES, start=1):
        print(f"{index}. {status}")

    while True:
        choice = input("Choose status number: ").strip()
        try:
            index = int(choice)
            if 1 <= index <= len(VALID_STATUSES):
                return VALID_STATUSES[index - 1]
        except ValueError:
            pass

        print("Invalid status choice. Please try again.")
