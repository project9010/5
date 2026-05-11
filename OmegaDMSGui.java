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
 * Важно: бизнес-логика и база данных остаются на Python.
 * Этот Java-клиент отправляет HTTP-запросы в backend_api.py.
 */
public class OmegaDMSGui extends JFrame {
    private static final String API_URL = "http://127.0.0.1:8000/api";
    private static final String[] STATUSES = {"created", "in progress", "approved", "rejected"};

    private CardLayout cardLayout;
    private JPanel rootPanel;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;

    private JLabel currentUserLabel;
    private String currentUser;

    private JTable documentsTable;
    private DefaultTableModel documentsModel;
    private JTextField titleField;
    private JTextArea contentArea;
    private JComboBox<String> statusBox;
    private JComboBox<String> searchFieldBox;
    private JTextField searchTextField;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OmegaDMSGui app = new OmegaDMSGui();
            app.setVisible(true);
        });
    }

    public OmegaDMSGui() {
        setTitle("СЭД АО ОМЕГА — Java GUI + Python Backend");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);
        rootPanel.add(createAuthPanel(), "auth");
        rootPanel.add(createDocumentsPanel(), "documents");
        add(rootPanel);

        cardLayout.show(rootPanel, "auth");
    }

    private JPanel createAuthPanel() {
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

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createDocumentForm(), createTablePanel());
        splitPane.setDividerLocation(330);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(createSearchPanel(), BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createDocumentForm() {
        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.setBorder(BorderFactory.createTitledBorder("Документ"));

        JPanel fields = new JPanel(new BorderLayout(6, 6));
        titleField = new JTextField();
        contentArea = new JTextArea(12, 24);
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

        statusBox = new JComboBox<>(STATUSES);
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

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Список документов"));

        documentsModel = new DefaultTableModel(new Object[]{"ID", "Заголовок", "Автор", "Создан", "Статус"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        documentsTable = new JTable(documentsModel);
        documentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documentsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                fillFormFromSelectedRow();
            }
        });

        panel.add(new JScrollPane(documentsTable), BorderLayout.CENTER);
        return panel;
    }

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

    private void register() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmation = new String(confirmPasswordField.getPassword());

        if (!password.equals(confirmation)) {
            showError("Пароли не совпадают.");
            return;
        }

        try {
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

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        try {
            ApiResponse response = sendRequest("POST", "/login", jsonObject(
                    "username", username,
                    "password", password
            ));
            if (response.isOk()) {
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

    private void logout() {
        currentUser = null;
        clearForm();
        documentsModel.setRowCount(0);
        cardLayout.show(rootPanel, "auth");
    }

    private void createDocument() {
        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();

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

    private void searchDocuments() {
        String field = (String) searchFieldBox.getSelectedItem();
        String keyword = searchTextField.getText().trim();

        if (keyword.isEmpty()) {
            showError("Введите текст для поиска.");
            return;
        }

        try {
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

    private void deleteSelectedDocument() {
        Integer documentId = selectedDocumentId();
        if (documentId == null) {
            showError("Сначала выберите документ в таблице.");
            return;
        }

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

    private void clearForm() {
        titleField.setText("");
        contentArea.setText("");
        statusBox.setSelectedItem(STATUSES[0]);
        documentsTable.clearSelection();
    }

    private Integer selectedDocumentId() {
        int row = documentsTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return Integer.parseInt(String.valueOf(documentsModel.getValueAt(row, 0)));
    }

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

    private ApiResponse sendRequest(String method, String path, String body) throws Exception {
        URI uri = URI.create(API_URL + path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        if (body != null) {
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = readStream(stream);
        return new ApiResponse(statusCode, responseBody);
    }

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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String extractString(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return "";
    }

    private int extractInt(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

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

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private class ApiResponse {
        private final int statusCode;
        private final String body;

        private ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        private boolean isOk() {
            return statusCode >= 200 && statusCode < 300 && body.contains("\"ok\": true");
        }

        private String errorMessage() {
            String error = extractString(body, "error");
            if (error.isEmpty()) {
                return "HTTP " + statusCode + ": " + body;
            }
            return error;
        }
    }

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
