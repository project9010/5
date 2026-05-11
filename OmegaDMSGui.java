import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java-графическая оболочка для СЭД АО "ОМЕГА".
 *
 * <p>Этот файл является клиентской частью приложения. Он не работает с SQLite напрямую:
 * база данных, проверка логина и пароля, создание документов и поиск выполняются
 * Python-бэкендом {@code backend_api.py}. Java-клиент только рисует окно, собирает
 * данные из полей формы и отправляет HTTP-запросы на локальный адрес API.</p>
 *
 * <p>Перед запуском этого окна нужно запустить Python-сервер командой
 * {@code python backend_api.py}. Затем Java-клиент можно скомпилировать и запустить
 * командами {@code javac OmegaDMSGui.java} и {@code java OmegaDMSGui}.</p>
 */
public class OmegaDMSGui extends JFrame {
    // Базовый адрес Python API. Все запросы Java-клиента начинаются с этого URL.
    private static final String API_URL = "http://127.0.0.1:8000/api";

    // Список статусов должен совпадать со списком VALID_STATUSES в Python-файле utils.py.
    private static final String[] STATUSES = {"created", "in progress", "approved", "rejected"};

    // CardLayout позволяет переключаться между двумя экранами: авторизацией и документами.
    private CardLayout cardLayout;
    private JPanel rootPanel;

    // Поля экрана авторизации. JPasswordField скрывает пароль символами-масками.
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;

    // currentUser хранит имя пользователя после успешного входа.
    private JLabel currentUserLabel;
    private String currentUser;

    // Элементы основного экрана: таблица, форма документа, статус и поиск.
    private JTable documentsTable;
    private DefaultTableModel documentsModel;
    private JTextField titleField;
    private JTextArea contentArea;
    private JComboBox<String> statusBox;
    private JComboBox<String> searchFieldBox;
    private JTextField searchTextField;

    /**
     * Точка входа Java-приложения.
     *
     * <p>Swing-компоненты нужно создавать в специальном потоке обработки событий
     * Event Dispatch Thread. Метод {@code SwingUtilities.invokeLater} как раз
     * передаёт создание окна в этот поток.</p>
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OmegaDMSGui app = new OmegaDMSGui();
            app.setVisible(true);
        });
    }

    /**
     * Конструктор создаёт главное окно, настраивает размер и добавляет два экрана.
     */
    public OmegaDMSGui() {
        setTitle("СЭД АО ОМЕГА — Java GUI + Python Backend");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        // null означает центрирование окна относительно экрана.
        setLocationRelativeTo(null);

        // В rootPanel лежат оба экрана; CardLayout показывает только один из них.
        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);
        rootPanel.add(createAuthPanel(), "auth");
        rootPanel.add(createDocumentsPanel(), "documents");
        add(rootPanel);

        cardLayout.show(rootPanel, "auth");
    }

    /**
     * Создать экран авторизации.
     *
     * <p>На этом экране пользователь может зарегистрироваться или войти. При нажатии
     * кнопок вызываются методы {@link #register()} и {@link #login()}.</p>
     */
    private JPanel createAuthPanel() {
        // GridBagLayout удобен для форм: элементы можно размещать по строкам и колонкам.
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Система электронного документооборота АО \"ОМЕГА\"", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 20));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(new JLabel("Имя пользователя:"), gbc);
        gbc.gridx = 1;
        usernameField = new JTextField(24);
        panel.add(usernameField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(new JLabel("Пароль:"), gbc);
        gbc.gridx = 1;
        passwordField = new JPasswordField(24);
        panel.add(passwordField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(new JLabel("Повтор пароля:"), gbc);
        gbc.gridx = 1;
        confirmPasswordField = new JPasswordField(24);
        panel.add(confirmPasswordField, gbc);

        // Кнопки используют слушатели событий: при клике выполняется нужный метод.
        JPanel buttons = new JPanel(new FlowLayout());
        JButton loginButton = new JButton("Войти");
        JButton registerButton = new JButton("Зарегистрироваться");
        loginButton.addActionListener(event -> login());
        registerButton.addActionListener(event -> register());
        buttons.add(loginButton);
        buttons.add(registerButton);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(buttons, gbc);

        JLabel hint = new JLabel("Сначала запустите Python-бэкенд: python backend_api.py", SwingConstants.CENTER);
        gbc.gridy++;
        panel.add(hint, gbc);

        return panel;
    }

    /**
     * Создать основной экран после входа пользователя.
     *
     * <p>Экран состоит из верхней панели с именем пользователя, формы документа,
     * таблицы документов и панели поиска.</p>
     */
    private JPanel createDocumentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel topPanel = new JPanel(new BorderLayout());
        currentUserLabel = new JLabel("Пользователь: ");
        currentUserLabel.setFont(new Font("Arial", Font.BOLD, 14));
        JButton logoutButton = new JButton("Выйти");
        logoutButton.addActionListener(event -> logout());
        topPanel.add(currentUserLabel, BorderLayout.WEST);
        topPanel.add(logoutButton, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

        // JSplitPane делит окно на левую часть с формой и правую часть с таблицей.
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createDocumentForm(), createTablePanel());
        splitPane.setDividerLocation(330);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(createSearchPanel(), BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Создать левую форму для ввода заголовка, текста и статуса документа.
     */
    private JPanel createDocumentForm() {
        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.setBorder(BorderFactory.createTitledBorder("Документ"));

        JPanel fields = new JPanel(new BorderLayout(6, 6));
        titleField = new JTextField();
        contentArea = new JTextArea(12, 24);
        // Перенос строк делает длинный текст документа читаемым внутри поля.
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.add(new JLabel("Заголовок:"), BorderLayout.NORTH);
        titlePanel.add(titleField, BorderLayout.CENTER);
        fields.add(titlePanel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(new JLabel("Содержание:"), BorderLayout.NORTH);
        contentPanel.add(new JScrollPane(contentArea), BorderLayout.CENTER);
        fields.add(contentPanel, BorderLayout.CENTER);

        form.add(fields, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton createButton = new JButton("Создать документ");
        JButton clearButton = new JButton("Очистить форму");
        JButton updateStatusButton = new JButton("Обновить статус");
        JButton deleteButton = new JButton("Удалить выбранный");

        // Выпадающий список ограничивает выбор только допустимыми статусами.
        statusBox = new JComboBox<>(STATUSES);

        // Каждая кнопка вызывает отдельный метод, чтобы код было проще читать и менять.
        createButton.addActionListener(event -> createDocument());
        clearButton.addActionListener(event -> clearForm());
        updateStatusButton.addActionListener(event -> updateStatus());
        deleteButton.addActionListener(event -> deleteSelectedDocument());

        buttons.add(createButton);
        buttons.add(clearButton);
        buttons.add(new JLabel("Статус выбранного документа:"));
        buttons.add(statusBox);
        buttons.add(updateStatusButton);
        buttons.add(deleteButton);
        form.add(buttons, BorderLayout.SOUTH);

        return form;
    }

    /**
     * Создать таблицу документов.
     *
     * <p>Таблица показывает краткую информацию. Полное содержание документа
     * загружается отдельно при выборе строки.</p>
     */
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Список документов"));

        // DefaultTableModel хранит строки таблицы. Второй аргумент 0 означает, что строк пока нет.
        documentsModel = new DefaultTableModel(new Object[]{"ID", "Заголовок", "Автор", "Создан", "Статус"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Пользователь не редактирует таблицу напрямую: изменения идут через кнопки и API.
                return false;
            }
        };
        documentsTable = new JTable(documentsModel);
        // Разрешаем выбирать только один документ за раз.
        documentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documentsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                fillFormFromSelectedRow();
            }
        });

        panel.add(new JScrollPane(documentsTable), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Создать нижнюю панель поиска.
     *
     * <p>Пользователь выбирает поле поиска: title, content или author. Эти имена
     * соответствуют разрешённым полям в Python-функции search_document_records.</p>
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Поиск"));

        searchFieldBox = new JComboBox<>(new String[]{"title", "content", "author"});
        searchTextField = new JTextField();
        JButton searchButton = new JButton("Поиск");
        JButton refreshButton = new JButton("Показать все");

        searchButton.addActionListener(event -> searchDocuments());
        refreshButton.addActionListener(event -> loadDocuments());

        panel.add(searchFieldBox, BorderLayout.WEST);
        panel.add(searchTextField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(searchButton);
        buttons.add(refreshButton);
        panel.add(buttons, BorderLayout.EAST);

        return panel;
    }

    /**
     * Зарегистрировать пользователя через Python API.
     *
     * <p>Java не хеширует пароль сама. Она отправляет логин и пароль в Python,
     * а Python уже создаёт соль, считает SHA-256 и сохраняет пользователя в SQLite.</p>
     */
    private void register() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmation = new String(confirmPasswordField.getPassword());

        // Проверку совпадения паролей удобно сделать сразу на клиенте.
        if (!password.equals(confirmation)) {
            showError("Пароли не совпадают.");
            return;
        }

        try {
            // Отправляем POST /api/register с JSON-телом {"username": ..., "password": ...}.
            ApiResponse response = sendRequest("POST", "/register", jsonObject(
                    "username", username,
                    "password", password
            ));
            if (response.isOk()) {
                JOptionPane.showMessageDialog(this, "Пользователь зарегистрирован.");
                passwordField.setText("");
                confirmPasswordField.setText("");
            } else {
                showError(response.errorMessage());
            }
        } catch (Exception error) {
            showError("Не удалось зарегистрироваться: " + error.getMessage());
        }
    }

    /**
     * Выполнить вход через Python API и перейти на экран документов при успехе.
     */
    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        try {
            ApiResponse response = sendRequest("POST", "/login", jsonObject(
                    "username", username,
                    "password", password
            ));
            if (response.isOk()) {
                // Python возвращает имя пользователя, если логин и пароль правильные.
                currentUser = extractString(response.body, "username");
                currentUserLabel.setText("Пользователь: " + currentUser);
                cardLayout.show(rootPanel, "documents");
                loadDocuments();
            } else {
                showError(response.errorMessage());
            }
        } catch (Exception error) {
            showError("Не удалось войти. Проверьте, что Python-бэкенд запущен: " + error.getMessage());
        }
    }

    /**
     * Выйти из учётной записи и вернуться на экран авторизации.
     */
    private void logout() {
        currentUser = null;
        clearForm();
        documentsModel.setRowCount(0);
        cardLayout.show(rootPanel, "auth");
    }

    /**
     * Создать новый документ через Python API.
     */
    private void createDocument() {
        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();

        // Пустые документы не отправляем на сервер, чтобы пользователь сразу видел ошибку.
        if (title.isEmpty() || content.isEmpty()) {
            showError("Заполните заголовок и содержание документа.");
            return;
        }

        try {
            ApiResponse response = sendRequest("POST", "/documents", jsonObject(
                    "title", title,
                    "content", content,
                    "author", currentUser
            ));
            if (response.isOk()) {
                JOptionPane.showMessageDialog(this, "Документ создан.");
                clearForm();
                loadDocuments();
            } else {
                showError(response.errorMessage());
            }
        } catch (Exception error) {
            showError("Не удалось создать документ: " + error.getMessage());
        }
    }

    /**
     * Загрузить полный список документов из Python API и обновить таблицу.
     */
    private void loadDocuments() {
        try {
            ApiResponse response = sendRequest("GET", "/documents", null);
            if (response.isOk()) {
                fillTable(parseDocuments(response.body));
            } else {
                showError(response.errorMessage());
            }
        } catch (Exception error) {
            showError("Не удалось загрузить документы: " + error.getMessage());
        }
    }

    /**
     * Найти документы по выбранному полю и поисковой строке.
     */
    private void searchDocuments() {
        String field = (String) searchFieldBox.getSelectedItem();
        String keyword = searchTextField.getText().trim();

        if (keyword.isEmpty()) {
            showError("Введите текст для поиска.");
            return;
        }

        try {
            // Значения в URL нужно кодировать, чтобы пробелы и русские буквы передавались корректно.
            String path = "/documents/search?field=" + encode(field) + "&keyword=" + encode(keyword);
            ApiResponse response = sendRequest("GET", path, null);
            if (response.isOk()) {
                fillTable(parseDocuments(response.body));
            } else {
                showError(response.errorMessage());
            }
        } catch (Exception error) {
            showError("Ошибка поиска: " + error.getMessage());
        }
    }

    /**
     * Обновить статус выбранного документа.
     */
    private void updateStatus() {
        Integer documentId = selectedDocumentId();
        if (documentId == null) {
            showError("Сначала выберите документ в таблице.");
            return;
        }

        String status = (String) statusBox.getSelectedItem();
        try {
            ApiResponse response = sendRequest("PUT", "/documents/" + documentId + "/status", jsonObject("status", status));
            if (response.isOk()) {
                JOptionPane.showMessageDialog(this, "Статус обновлён.");
                loadDocuments();
            } else {
                showError(response.errorMessage());
            }
        } catch (Exception error) {
            showError("Не удалось обновить статус: " + error.getMessage());
        }
    }

    /**
     * Удалить выбранный документ после подтверждения пользователя.
     */
    private void deleteSelectedDocument() {
        Integer documentId = selectedDocumentId();
        if (documentId == null) {
            showError("Сначала выберите документ в таблице.");
            return;
        }

        // Удаление необратимо, поэтому перед запросом DELETE спрашиваем подтверждение.
        int answer = JOptionPane.showConfirmDialog(this, "Удалить документ ID " + documentId + "?", "Подтверждение", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            ApiResponse response = sendRequest("DELETE", "/documents/" + documentId, null);
            if (response.isOk()) {
                JOptionPane.showMessageDialog(this, "Документ удалён.");
                clearForm();
                loadDocuments();
            } else {
                showError(response.errorMessage());
            }
        } catch (Exception error) {
            showError("Не удалось удалить документ: " + error.getMessage());
        }
    }

    /**
     * Заполнить форму данными выбранной строки таблицы.
     *
     * <p>В таблице нет полного текста документа, поэтому содержание дополнительно
     * запрашивается у Python-бэкенда по ID документа.</p>
     */
    private void fillFormFromSelectedRow() {
        int row = documentsTable.getSelectedRow();
        if (row < 0) {
            return;
        }

        titleField.setText(String.valueOf(documentsModel.getValueAt(row, 1)));
        statusBox.setSelectedItem(String.valueOf(documentsModel.getValueAt(row, 4)));

        Integer documentId = selectedDocumentId();
        if (documentId == null) {
            return;
        }

        try {
            ApiResponse response = sendRequest("GET", "/documents/" + documentId, null);
            if (response.isOk()) {
                contentArea.setText(extractString(response.body, "content"));
            }
        } catch (Exception ignored) {
            // Если полный текст не загрузился, таблица всё равно остаётся доступной.
        }
    }

    /**
     * Очистить форму документа и снять выбор в таблице.
     */
    private void clearForm() {
        titleField.setText("");
        contentArea.setText("");
        statusBox.setSelectedItem(STATUSES[0]);
        documentsTable.clearSelection();
    }

    /**
     * Получить ID выбранного документа или null, если строка не выбрана.
     */
    private Integer selectedDocumentId() {
        int row = documentsTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return Integer.parseInt(String.valueOf(documentsModel.getValueAt(row, 0)));
    }

    /**
     * Перерисовать таблицу на основе списка документов, полученного от Python.
     */
    private void fillTable(List<DocumentRow> documents) {
        documentsModel.setRowCount(0);
        for (DocumentRow document : documents) {
            documentsModel.addRow(new Object[]{
                    document.id,
                    document.title,
                    document.author,
                    document.createdAt,
                    document.status
            });
        }
    }

    /**
     * Отправить HTTP-запрос в Python API.
     *
     * @param method HTTP-метод: GET, POST, PUT или DELETE
     * @param path путь после /api, например /documents
     * @param body JSON-тело запроса или null для запросов без тела
     */
    private ApiResponse sendRequest(String method, String path, String body) throws Exception {
        URI uri = URI.create(API_URL + path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        // Для POST/PUT передаём JSON в тело запроса. Для GET/DELETE body обычно null.
        if (body != null) {
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        // Если сервер вернул ошибку, тело ответа читается из errorStream.
        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = readStream(stream);
        return new ApiResponse(statusCode, responseBody);
    }

    /**
     * Прочитать ответ сервера в строку UTF-8.
     */
    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    /**
     * Собрать простой JSON-объект из пар ключ-значение.
     *
     * <p>В проект не добавлялись сторонние библиотеки, поэтому JSON формируется вручную.
     * Метод подходит для простых строковых полей, которые используются в этом клиенте.</p>
     */
    private String jsonObject(String... pairs) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(pairs[i])).append('"').append(':');
            builder.append('"').append(escapeJson(pairs[i + 1])).append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    /**
     * Экранировать спецсимволы перед вставкой строки в JSON.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Закодировать значение для безопасной передачи в URL-параметрах.
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Достать строковое поле из JSON-ответа.
     *
     * <p>Это упрощённый разбор JSON без сторонних библиотек. Для учебного проекта
     * достаточно, потому что Python API возвращает предсказуемую структуру.</p>
     */
    private String extractString(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return "";
    }

    /**
     * Достать целочисленное поле из JSON-ответа.
     */
    private int extractInt(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    /**
     * Преобразовать JSON escape-последовательности обратно в обычные символы.
     */
    private String unescapeJson(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (escaping) {
                switch (character) {
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case '\\': result.append('\\'); break;
                    case '"': result.append('"'); break;
                    default: result.append(character); break;
                }
                escaping = false;
            } else if (character == '\\') {
                escaping = true;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    /**
     * Разобрать список документов из JSON-ответа Python API.
     */
    private List<DocumentRow> parseDocuments(String json) {
        List<DocumentRow> documents = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher matcher = objectPattern.matcher(json);
        while (matcher.find()) {
            String object = matcher.group();
            if (!object.contains("\"title\"")) {
                continue;
            }
            documents.add(new DocumentRow(
                    extractInt(object, "id"),
                    extractString(object, "title"),
                    extractString(object, "content"),
                    extractString(object, "author"),
                    extractString(object, "created_at"),
                    extractString(object, "status")
            ));
        }
        return documents;
    }

    /**
     * Показать сообщение об ошибке в стандартном диалоговом окне Swing.
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Небольшая обёртка над HTTP-ответом.
     *
     * <p>Хранит статус ответа и JSON-текст, а также умеет проверить успешность
     * операции и достать сообщение об ошибке.</p>
     */
    private class ApiResponse {
        private final int statusCode;
        private final String body;

        private ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        /**
         * Ответ считается успешным, если HTTP-статус 2xx и Python вернул "ok": true.
         */
        private boolean isOk() {
            return statusCode >= 200 && statusCode < 300 && body.contains("\"ok\": true");
        }

        /**
         * Вернуть понятный текст ошибки из JSON или технический ответ сервера.
         */
        private String errorMessage() {
            String error = extractString(body, "error");
            if (error.isEmpty()) {
                return "HTTP " + statusCode + ": " + body;
            }
            return error;
        }
    }

    /**
     * Простая модель одной строки документа для отображения в JTable.
     */
    private static class DocumentRow {
        private final int id;
        private final String title;
        private final String content;
        private final String author;
        private final String createdAt;
        private final String status;

        private DocumentRow(int id, String title, String content, String author, String createdAt, String status) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.author = author;
            this.createdAt = createdAt;
            this.status = status;
        }
    }
}
