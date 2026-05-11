"""HTTP API-бэкенд для Java-графической оболочки СЭД АО "ОМЕГА".

Python остаётся основной серверной частью проекта: он работает с SQLite,
проверяет пользователей, хеширует пароли и выполняет операции с документами.
Java-приложение подключается к этому файлу по HTTP на адрес http://localhost:8000.
"""

import json
import sqlite3
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

from auth import authenticate_user, register_user_record, validate_credentials
from database import initialize_database
from documents import (
    create_document_record,
    delete_document_record,
    get_document_by_id,
    list_documents,
    search_document_records,
    update_document_status_record,
)

HOST = "127.0.0.1"
PORT = 8000


# Эти поля можно отдавать клиенту. Пароли в ответах API никогда не возвращаются.
DOCUMENT_RESPONSE_FIELDS = ("id", "title", "content", "author", "created_at", "status")


def row_to_document(row):
    """Преобразовать sqlite3.Row в обычный словарь для JSON-ответа."""
    return {field: row[field] for field in DOCUMENT_RESPONSE_FIELDS}


class DMSRequestHandler(BaseHTTPRequestHandler):
    """Обработчик HTTP-запросов от Java-клиента."""

    def log_message(self, format, *args):
        """Уменьшить лишний технический вывод сервера в консоль."""
        return

    def send_json(self, status_code, payload):
        """Отправить клиенту JSON-ответ с указанным HTTP-статусом."""
        response = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def read_json_body(self):
        """Прочитать JSON из тела POST/PUT/DELETE-запроса."""
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length == 0:
            return {}

        raw_body = self.rfile.read(content_length).decode("utf-8")
        try:
            return json.loads(raw_body)
        except json.JSONDecodeError as error:
            raise ValueError("Некорректный JSON в запросе.") from error

    def do_GET(self):
        """Обработать GET-запросы: проверка сервера, список и поиск документов."""
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        query = parse_qs(parsed_url.query)

        try:
            if path == "/api/health":
                self.send_json(200, {"ok": True, "message": "Python backend is running"})
            elif path == "/api/documents":
                documents = [row_to_document(row) for row in list_documents()]
                self.send_json(200, {"ok": True, "documents": documents})
            elif path == "/api/documents/search":
                field = query.get("field", [""])[0]
                keyword = query.get("keyword", [""])[0]
                if not field or not keyword:
                    self.send_json(400, {"ok": False, "error": "Укажите поле и текст поиска."})
                    return

                documents = [
                    row_to_document(row) for row in search_document_records(field, keyword)
                ]
                self.send_json(200, {"ok": True, "documents": documents})
            elif path.startswith("/api/documents/"):
                document_id = int(path.rsplit("/", 1)[1])
                document = get_document_by_id(document_id)
                if document is None:
                    self.send_json(404, {"ok": False, "error": "Документ не найден."})
                    return

                self.send_json(200, {"ok": True, "document": row_to_document(document)})
            else:
                self.send_json(404, {"ok": False, "error": "Маршрут не найден."})
        except (sqlite3.Error, ValueError) as error:
            self.send_json(400, {"ok": False, "error": str(error)})

    def do_POST(self):
        """Обработать POST-запросы: регистрация, вход и создание документов."""
        parsed_url = urlparse(self.path)
        path = parsed_url.path

        try:
            data = self.read_json_body()

            if path == "/api/register":
                username = str(data.get("username", "")).strip()
                password = str(data.get("password", ""))
                is_valid, message = validate_credentials(username, password)
                if not is_valid:
                    self.send_json(400, {"ok": False, "error": message})
                    return

                registered_username = register_user_record(username, password)
                self.send_json(201, {"ok": True, "username": registered_username})
            elif path == "/api/login":
                username = str(data.get("username", "")).strip()
                password = str(data.get("password", ""))
                authenticated_username = authenticate_user(username, password)
                self.send_json(200, {"ok": True, "username": authenticated_username})
            elif path == "/api/documents":
                title = str(data.get("title", "")).strip()
                content = str(data.get("content", "")).strip()
                author = str(data.get("author", "")).strip()

                if not title or not content or not author:
                    self.send_json(
                        400,
                        {"ok": False, "error": "Заполните заголовок, содержание и автора."},
                    )
                    return

                document_id = create_document_record(title, content, author)
                document = get_document_by_id(document_id)
                self.send_json(201, {"ok": True, "document": row_to_document(document)})
            else:
                self.send_json(404, {"ok": False, "error": "Маршрут не найден."})
        except ValueError as error:
            self.send_json(400, {"ok": False, "error": str(error)})
        except sqlite3.Error as error:
            self.send_json(500, {"ok": False, "error": f"Ошибка базы данных: {error}"})

    def do_PUT(self):
        """Обработать PUT-запросы: изменение статуса документа."""
        parsed_url = urlparse(self.path)
        path = parsed_url.path

        try:
            if not path.startswith("/api/documents/") or not path.endswith("/status"):
                self.send_json(404, {"ok": False, "error": "Маршрут не найден."})
                return

            document_id = int(path.split("/")[3])
            data = self.read_json_body()
            status = str(data.get("status", "")).strip()

            updated = update_document_status_record(document_id, status)
            if not updated:
                self.send_json(404, {"ok": False, "error": "Документ не найден."})
                return

            document = get_document_by_id(document_id)
            self.send_json(200, {"ok": True, "document": row_to_document(document)})
        except (ValueError, sqlite3.Error) as error:
            self.send_json(400, {"ok": False, "error": str(error)})

    def do_DELETE(self):
        """Обработать DELETE-запросы: удаление документа."""
        parsed_url = urlparse(self.path)
        path = parsed_url.path

        try:
            if not path.startswith("/api/documents/"):
                self.send_json(404, {"ok": False, "error": "Маршрут не найден."})
                return

            document_id = int(path.rsplit("/", 1)[1])
            deleted = delete_document_record(document_id)
            if not deleted:
                self.send_json(404, {"ok": False, "error": "Документ не найден."})
                return

            self.send_json(200, {"ok": True})
        except (ValueError, sqlite3.Error) as error:
            self.send_json(400, {"ok": False, "error": str(error)})


def run_server():
    """Инициализировать базу данных и запустить HTTP-сервер."""
    initialize_database()
    server = HTTPServer((HOST, PORT), DMSRequestHandler)
    print(f"Python backend started: http://{HOST}:{PORT}")
    print("Для остановки нажмите Ctrl+C")
    server.serve_forever()


if __name__ == "__main__":
    run_server()
