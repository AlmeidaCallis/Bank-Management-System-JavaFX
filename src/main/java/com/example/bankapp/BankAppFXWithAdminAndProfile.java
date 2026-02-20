package com.example.bankapp;
/* BankAppFXWithAdminAndProfile.java 
JavaFX Bank App + MySQL + Google OAuth 2.0 + Admin Panel + Profile Picture (circular top-right) - Requires JDK 17+ (works with JDK21) - JavaFX SDK on module-path - MySQL Connector/J jar on classpath - Set CLIENT_ID and CLIENT_SECRET for Google OAuth Desktop app 
*/ 
import javafx.application.Application; 
import javafx.application.Platform; 
import javafx.collections.FXCollections; 
import javafx.concurrent.Task; 
import javafx.geometry.Insets; 
import javafx.geometry.Pos; 
import javafx.scene.Scene; 
import javafx.scene.control.*; 
import javafx.scene.image.*; 
import javafx.scene.layout.*; 
import javafx.stage.Stage; 
import com.sun.net.httpserver.HttpServer; 
import java.awt.Desktop; 
import java.io.*; 
import java.net.*; 
import java.nio.charset.StandardCharsets; 
import java.sql.*; 
import java.util.*; 
import java.util.stream.Collectors; 
public class BankAppFXWithAdminAndProfile extends Application { 
// ----------------- EDIT THESE ----------------- 
private static final String DB_URL = 
"jdbc:mysql://localhost:3306/bankdb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"; 
private static final String DB_USER = "bankuser"; 
private static final String DB_PASS = "bankpass"; 
// Google OAuth desktop credentials (set these) 
private static final String CLIENT_ID = 
"665423830918-ghutn56n63vja8ik1q8l0c2mavib5epf.apps.googleusercontent.com"; 
private static final String CLIENT_SECRET = "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"; 
private static final int OAUTH_PORT = 54321; 
private static final String REDIRECT_URI = "http://127.0.0.1:" + OAUTH_PORT + "/"; 
// Admin creds (change if you want) 
private static final String ADMIN_USER = "admin"; 
private static final String ADMIN_PASS = "admin123"; 
// ---------------------------------------------- 
private String currentAccount = null; 
private Label balanceLabel = new Label(); 
    private Label nameLabel = new Label(); 
    private ImageView profileImageView = new ImageView(); 
    private ListView<String> historyList = new ListView<>(); 
 
    // Top-right user info 
    private Label topRightNameLabel = new Label(); 
    private ImageView topRightProfile = new ImageView(); 
 
    private Scene splashScene, loginScene, mainScene; 
 
    @Override 
    public void start(Stage stage) { 
        stage.setTitle("Bank Management System (JavaFX + MySQL)"); 
 
        splashScene = buildSplashScene(); 
        loginScene = buildLoginScene(stage); 
        mainScene = buildMainScene(stage); 
 
        stage.setScene(splashScene); 
        stage.show(); 
 
        // init DB in background 
        Task<Void> init = new Task<>() { 
            @Override protected Void call() throws Exception { 
                DB.initSchema(); Thread.sleep(500); return null; 
            } 
        }; 
        init.setOnSucceeded(e -> stage.setScene(loginScene)); 
        init.setOnFailed(e -> { 
            showError("DB initialization failed: " + init.getException().getMessage()); 
            stage.setScene(loginScene); 
        }); 
        new Thread(init).start(); 
    } 
 
    // ---------- Splash ---------- 
    private Scene buildSplashScene() { 
        VBox card = new VBox(12, new Label("Connecting to Database..."), new ProgressIndicator()); 
        card.setAlignment(Pos.CENTER); 
        card.setPadding(new Insets(20)); 
        card.getStyleClass().add("glass-card"); 
        StackPane root = new StackPane(card); 
        root.getStyleClass().add("background"); 
        Scene s = new Scene(root, 860, 540); 
        try { s.getStylesheets().add(getClass().getResource("techy.css").toExternalForm()); } catch (Exception ignored) {} 
        return s; 
    } 
 
    // ---------- Login ---------- 
    private Scene buildLoginScene(Stage stage) { 
        PasswordField apiKeyField = new PasswordField(); apiKeyField.setPromptText("API Key (optional)"); 
        TextField accField = new TextField(); accField.setPromptText("Account Number / Email"); 
        PasswordField pinField = new PasswordField(); pinField.setPromptText("PIN (4 digits)"); 
 
        CheckBox adminCheck = new CheckBox("Login as Admin"); 
 
        Button loginBtn = new Button("Login"); 
        Button createBtn = new Button("Create Account"); 
        Button googleBtn = new Button("Login with Google"); 
 
        Label msg = new Label(); 
 
        loginBtn.getStyleClass().add("primary-btn"); 
        createBtn.getStyleClass().add("secondary-btn"); 
        googleBtn.getStyleClass().add("primary-btn"); 
 
        HBox hbox = new HBox(10, loginBtn, createBtn); 
        hbox.setAlignment(Pos.CENTER); 
 
        VBox card = new VBox(12, 
                new Label("Secure Bank Login"), 
                apiKeyField, accField, pinField, 
                adminCheck, 
                hbox, 
                new Separator(), 
                googleBtn, 
                msg 
        ); 
        card.setAlignment(Pos.CENTER); 
        card.setPadding(new Insets(20)); 
        card.getStyleClass().add("glass-card"); 
 
        StackPane root = new StackPane(card); root.getStyleClass().add("background"); 
        Scene s = new Scene(root, 860, 540); 
        try { s.getStylesheets().add(getClass().getResource("techy.css").toExternalForm()); } catch (Exception ignored) {} 
 
        // Login logic 
        loginBtn.setOnAction(e -> { 
            String api = apiKeyField.getText().trim(); 
            String acc = accField.getText().trim(); 
            String pin = pinField.getText().trim(); 
 
            if (adminCheck.isSelected()) { 
                // Admin login check 
                if (acc.equals(ADMIN_USER) && pin.equals(ADMIN_PASS)) { 
                    Scene adminScene = buildAdminScene(stage); 
                    stage.setScene(adminScene); 
                } else { 
                    msg.setStyle("-fx-text-fill:red;"); msg.setText("Invalid admin credentials"); 
                } 
                return; 
            } 
 
            if (acc.isEmpty() || pin.isEmpty()) { 
                msg.setStyle("-fx-text-fill: yellow;"); msg.setText("Enter account and PIN"); 
                return; 
            } 
 
            runAsync(() -> DB.validateLogin(api, acc, pin), 
                    ok -> { 
                        if (ok) { 
                            currentAccount = acc; 
                            refreshDashboard(); 
                            stage.setScene(mainScene); 
                        } else { 
                            msg.setStyle("-fx-text-fill: red;"); msg.setText("Invalid credentials"); 
                        } 
                    }, 
                    ex -> showError("Login failed: " + ex.getMessage())); 
        }); 
 
        // Create account 
        createBtn.setOnAction(e -> { 
            String api = apiKeyField.getText().trim(); 
            String acc = accField.getText().trim(); 
            String pin = pinField.getText().trim(); 
            if (acc.isEmpty() || pin.isEmpty()) { msg.setStyle("-fx-text-fill: yellow;"); msg.setText("Enter account & PIN"); return; } 
            if (!pin.matches("\\d{4}")) { msg.setStyle("-fx-text-fill: yellow;"); msg.setText("PIN must be 4 digits"); return; 
} 
 
            runAsync(() -> { DB.createAccount(acc, api.isEmpty() ? "LOCAL" : api, pin, 1000.0); return null; }, 
                    ok -> { msg.setStyle("-fx-text-fill:#00ff90;"); msg.setText("Account created with ₹1000"); }, 
                    ex -> showError("Account creation failed: " + ex.getMessage())); 
        }); 
 
        // Google OAuth 
        googleBtn.setOnAction(e -> { 
            runAsync(() -> googleOAuthLogin(), 
                    user -> { 
                        if (user == null || user.get("email") == null) { showError("Google login failed or cancelled."); return; } 
                        String email = user.get("email"); 
                        String name = user.getOrDefault("name", ""); 
                        String picture = user.getOrDefault("picture", ""); 
 
                        runAsync(() -> { 
                            if (!DB.hasAccount(email)) { 
                                DB.createOAuthAccount(email, "GOOGLE", "OAUTH", 1000.0, name, picture); 
                            } else { 
                                // optionally update name/picture if changed 
                                DB.updateProfile(email, name, picture); 
                            } 
                            return email; 
                        }, acc -> { 
                            currentAccount = acc; 
                            refreshDashboard(); 
                            stage.setScene(mainScene); 
                        }, ex -> showError("DB error after OAuth: " + ex.getMessage())); 
                    }, 
                    ex -> showError("OAuth flow failed: " + ex.getMessage())); 
        }); 
 
        return s; 
    } 
 
    // Apply circular clip to ImageView 
    private void applyCircularClip(ImageView imgView, double size) { 
        javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(size / 2); 
        clip.setCenterX(size / 2); 
        clip.setCenterY(size / 2); 
        imgView.setClip(clip); 
    } 
 
    // ---------- Main / Dashboard ---------- 
    private Scene buildMainScene(Stage stage) { 
        // --- Top Right User Info Bar --- 
        topRightProfile.setFitWidth(40); 
        topRightProfile.setFitHeight(40); 
        topRightProfile.setPreserveRatio(true); 
        applyCircularClip(topRightProfile, 40); 
        topRightNameLabel.getStyleClass().add("top-right-name"); 
        HBox topBar = new HBox(); 
        Region spacer = new Region(); 
        HBox.setHgrow(spacer, Priority.ALWAYS); 
        topBar.getChildren().addAll(spacer, topRightNameLabel, topRightProfile); 
        topBar.setAlignment(Pos.CENTER_RIGHT); 
        topBar.setPadding(new Insets(10)); 
 
        // Profile area (name + picture) 
        profileImageView.setFitWidth(80); 
        profileImageView.setFitHeight(80); 
        profileImageView.setPreserveRatio(true); 
        applyCircularClip(profileImageView, 80); 
        VBox profileBox = new VBox(8, profileImageView, nameLabel); 
        profileBox.setAlignment(Pos.CENTER); 
 
        balanceLabel.getStyleClass().add("heading"); 
 
        VBox dash = new VBox(8, profileBox, new Label("Balance"), balanceLabel); 
        dash.setAlignment(Pos.CENTER); 
        dash.getStyleClass().add("glass-card"); 
 
        // Deposit 
        TextField depField = new TextField(); depField.setPromptText("Amount"); 
        Button depBtn = new Button("Deposit"); depBtn.getStyleClass().add("primary-btn"); 
        Label depMsg = new Label(); 
        depBtn.setOnAction(e -> { 
            try { 
                double amt = Double.parseDouble(depField.getText()); 
                if (amt <= 0) throw new NumberFormatException(); 
                runAsync(() -> { DB.changeBalance(currentAccount, +amt); DB.insertTxn(currentAccount, "DEPOSIT", amt); return null; }, 
                        ok -> { depMsg.setStyle("-fx-text-fill:#99ff99;"); depMsg.setText("Deposited ₹" + amt); depField.clear(); refreshDashboard(); }, 
                        ex -> showError("Deposit failed: " + ex.getMessage())); 
            } catch (NumberFormatException ex) { depMsg.setStyle("-fx-text-fill: yellow;"); depMsg.setText("Enter valid amount"); } 
        }); 
 
        VBox depBox = new VBox(8, depField, depBtn, depMsg); depBox.setAlignment(Pos.CENTER); 
 
        // Withdraw 
        TextField witField = new TextField(); witField.setPromptText("Amount"); 
        Button witBtn = new Button("Withdraw"); witBtn.getStyleClass().add("danger-btn"); 
        Label witMsg = new Label(); 
        witBtn.setOnAction(e -> { 
            try { 
                double amt = Double.parseDouble(witField.getText()); 
                if (amt <= 0) throw new NumberFormatException(); 
                runAsync(() -> { 
                    double bal = DB.getBalance(currentAccount); 
                    if (amt > bal) throw new IllegalStateException("Insufficient balance (₹" + bal + ")"); 
                    DB.changeBalance(currentAccount, -amt); 
                    DB.insertTxn(currentAccount, "WITHDRAW", amt); 
                    return null; 
                }, ok -> { witMsg.setStyle("-fx-text-fill:#99ff99;"); witMsg.setText("Withdrawn ₹" + amt); witField.clear(); refreshDashboard(); }, 
                        ex -> { witMsg.setStyle("-fx-text-fill:red;"); witMsg.setText("❌" + ex.getMessage()); }); 
            } catch (NumberFormatException ex) { witMsg.setStyle("-fx-text-fill: yellow;"); witMsg.setText("Enter validamount"); } 
        }); 
 
        VBox witBox = new VBox(8, witField, witBtn, witMsg); witBox.setAlignment(Pos.CENTER); 
 
        // History 
        historyList.setPrefHeight(300); 
        VBox histBox = new VBox(8, new Label("Transactions"), historyList); 
        histBox.setAlignment(Pos.CENTER); 
        histBox.getStyleClass().add("glass-card"); 
 
        Button logout = new Button("Logout"); logout.getStyleClass().add("secondary-btn"); 
        logout.setOnAction(e -> { currentAccount = null; Stage s = (Stage)logout.getScene().getWindow(); s.setScene(buildLoginScene(stage)); }); 
 
        TabPane tabs = new TabPane( 
                new Tab("Dashboard", dash), 
                new Tab("Deposit", depBox), 
                new Tab("Withdraw", witBox), 
                new Tab("History", histBox) 
        ); 
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); 
 
        VBox root = new VBox(10, topBar, tabs, logout); 
        root.setPadding(new Insets(20)); root.setAlignment(Pos.CENTER); 
        root.getStyleClass().add("background"); 
 
        Scene s = new Scene(root, 900, 600); 
        try { s.getStylesheets().add(getClass().getResource("techy.css").toExternalForm()); } catch (Exception ignored) {} 
        return s; 
    } 
 
    // ---------- Admin Scene ---------- 
    private Scene buildAdminScene(Stage stage) { 
        TableView<Map<String,String>> table = new TableView<>(); 
 
        TableColumn<Map<String,String>, String> colTs = new TableColumn<>("Date"); 
        colTs.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("ts"))); 
        TableColumn<Map<String,String>, String> colAcc = new TableColumn<>("Account"); 
        colAcc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("account"))); 
        TableColumn<Map<String,String>, String> colName = new TableColumn<>("Name"); 
        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("name"))); 
        TableColumn<Map<String,String>, String> colType = new TableColumn<>("Type"); 
        colType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("type"))); 
        TableColumn<Map<String,String>, String> colAmt = new TableColumn<>("Amount"); 
        colAmt.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("amount"))); 
 
        table.getColumns().addAll(colTs, colAcc, colName, colType, colAmt); 
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); 
 
        runAsync(() -> DB.getAllTransactions(), list -> { 
            table.setItems(FXCollections.observableArrayList(list)); 
        }, ex -> showError("Failed to load transactions: " + ex.getMessage())); 
 
        Button refresh = new Button("Refresh"); 
        refresh.setOnAction(e -> runAsync(() -> DB.getAllTransactions(), list -> table.setItems(FXCollections.observableArrayList(list)), ex -> showError("Refresh failed: " + ex.getMessage()))); 
 
        Button logout = new Button("Logout"); logout.getStyleClass().add("secondary-btn"); 
        logout.setOnAction(e -> stage.setScene(buildLoginScene(stage))); 
 
        HBox controls = new HBox(10, refresh, logout); controls.setAlignment(Pos.CENTER); 
 
        VBox root = new VBox(12, new Label("Admin — All Transactions"), table, controls); 
        root.setPadding(new Insets(12)); root.setAlignment(Pos.CENTER); 
        root.getStyleClass().add("background"); 
 
        Scene s = new Scene(root, 1000, 600); 
        try { s.getStylesheets().add(getClass().getResource("techy.css").toExternalForm()); } catch (Exception ignored) {} 
        return s; 
    } 
 
    // ---------- Refresh dashboard with profile + balance + txns ---------- 
    private void refreshDashboard() { 
        if (currentAccount == null) return; 
        runAsync(() -> { 
            Map<String,String> info = DB.getAccountInfo(currentAccount); 
            double bal = DB.getBalance(currentAccount); 
            List<String> lines = new ArrayList<>(); 
            for (Txn t : DB.getTxns(currentAccount)) lines.add(t.ts + " — " + t.type + " ₹" + t.amount); 
            return new Object[]{info, bal, lines}; 
        }, data -> { 
            @SuppressWarnings("unchecked") 
            Map<String,String> info = (Map<String,String>)((Object[])data)[0]; 
            double bal = (double)((Object[])data)[1]; 
            @SuppressWarnings("unchecked") 
            List<String> lines = (List<String>)((Object[])data)[2]; 
 
            // Update main area 
            balanceLabel.setText("₹" + bal); 
            nameLabel.setText(info.getOrDefault("name", currentAccount)); 
            String pic = info.get("picture"); 
            if (pic != null && !pic.isBlank()) { 
                try { 
                    Image img = new Image(pic, 80, 80, true, true, true); 
                    profileImageView.setImage(img); 
                    applyCircularClip(profileImageView, 80); 
                } catch (Exception ex) { 
                    profileImageView.setImage(null); 
                } 
            } else { 
                profileImageView.setImage(null); 
            } 
            historyList.setItems(FXCollections.observableArrayList(lines)); 
 
            // --- Update top-right user info --- 
            String displayName = info.getOrDefault("name", currentAccount); 
            topRightNameLabel.setText(displayName + " (" + currentAccount + ")"); 
            if (pic != null && !pic.isBlank()) { 
                try { 
                    Image imgSmall = new Image(pic, 40, 40, true, true, true); 
                    topRightProfile.setImage(imgSmall); 
                    applyCircularClip(topRightProfile, 40); 
                } catch (Exception ex) { 
                    topRightProfile.setImage(null); 
                } 
            } else { 
                topRightProfile.setImage(null); 
            } 
 
        }, ex -> showError("Refresh failed: " + ex.getMessage())); 
    } 
 
    // ---------- GOOGLE OAUTH flow (desktop loopback) ---------- 
    private Map<String,String> googleOAuthLogin() throws Exception { 
        if (CLIENT_ID.startsWith("YOUR_")) throw new IllegalStateException("Set CLIENT_ID and CLIENT_SECRET in code."); 
 
        String scope = URLEncoder.encode("openid email profile", StandardCharsets.UTF_8); 
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" 
                + "?response_type=code" 
                + "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) 
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) 
                + "&scope=" + scope 
                + "&access_type=offline" 
                + "&prompt=select_account"; 
 
        String code = startLocalOAuthServer(authUrl); 
        if (code == null) return null; 
 
        String tokenJson = postForm("https://oauth2.googleapis.com/token", Map.of( 
                "code", code, 
                "client_id", CLIENT_ID, 
                "client_secret", CLIENT_SECRET, 
                "redirect_uri", REDIRECT_URI, 
                "grant_type", "authorization_code" 
        )); 
 
        String accessToken = extractJsonValue(tokenJson, "access_token"); 
        if (accessToken == null) throw new IllegalStateException("No access_token in token response: " + tokenJson); 
 
        String userJson = getWithBearer("https://www.googleapis.com/oauth2/v3/userinfo", accessToken); 
        Map<String,String> out = new HashMap<>(); 
        out.put("email", extractJsonValue(userJson, "email")); 
        out.put("name", extractJsonValue(userJson, "name")); 
        out.put("picture", extractJsonValue(userJson, "picture")); 
        out.put("access_token", accessToken); 
        return out; 
    } 
 
    private String startLocalOAuthServer(String authUrl) throws Exception { 
        final String[] codeHolder = {null}; 
 
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", OAUTH_PORT), 0); 
        server.createContext("/", exchange -> { 
            String q = exchange.getRequestURI().getQuery(); 
            Map<String,String> params = parseQuery(q); 
            String html; 
            if (params.containsKey("code")) { 
                codeHolder[0] = params.get("code"); 
                html = "<html><body><h2>Login successful</h2><p>You may close this tab and return to the application.</p></body></html>"; 
            } else { 
                html = "<html><body><h2>Login failed or cancelled</h2></body></html>"; 
            } 
            exchange.getResponseHeaders().set("Content-Type", "text/html"); 
            byte[] resp = html.getBytes(StandardCharsets.UTF_8); 
            exchange.sendResponseHeaders(200, resp.length); 
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); } 
            new Thread(() -> { try { Thread.sleep(200); } catch (InterruptedException ignored) {} server.stop(0); }).start(); 
        }); 
        server.start(); 
 
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(authUrl)); 
        else System.out.println("Open this URL: " + authUrl); 
 
        int waited = 0; 
        while (codeHolder[0] == null && waited < 180) { Thread.sleep(500); waited++; } 
        server.stop(0); 
        return codeHolder[0]; 
    } 
 
    // ---------- HTTP helpers ---------- 
    private String postForm(String urlStr, Map<String,String> form) throws IOException { 
        URL url = new URL(urlStr); 
        String body = form.entrySet().stream() 
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)) 
                .collect(Collectors.joining("&")); 
        byte[] post = body.getBytes(StandardCharsets.UTF_8); 
 
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(); 
        conn.setRequestMethod("POST"); conn.setDoOutput(true); 
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
        conn.setRequestProperty("Accept", "application/json"); 
        try (OutputStream os = conn.getOutputStream()) { os.write(post); } 
 
        try (InputStream is = conn.getInputStream(); BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) { 
            return br.lines().collect(Collectors.joining()); 
        } catch (IOException ex) { 
            InputStream es = conn.getErrorStream(); 
            if (es != null) try (BufferedReader br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) { throw new IOException("HTTP error: " + br.lines().collect(Collectors.joining())); } 
            throw ex; 
        } 
    } 
 
    private String getWithBearer(String urlStr, String bearer) throws IOException { 
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection(); 
        conn.setRequestProperty("Authorization", "Bearer " + bearer); 
        conn.setRequestProperty("Accept", "application/json"); 
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { 
            return br.lines().collect(Collectors.joining()); 
        } 
    } 
 
    private Map<String,String> parseQuery(String q) { 
        Map<String,String> map = new HashMap<>(); 
        if (q == null) return map; 
        for (String part : q.split("&")) { 
            int i = part.indexOf('='); 
            if (i >= 0) map.put(URLDecoder.decode(part.substring(0,i), StandardCharsets.UTF_8), 
                    URLDecoder.decode(part.substring(i+1), StandardCharsets.UTF_8)); 
        } 
        return map; 
    } 
 
    // ---------- FIXED JSON extractor ---------- 
    private String extractJsonValue(String json, String key) { 
        if (json == null) return null; 
        String normalized = json.replace("\n","").replace("\r",""); 
        String regex = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\""; 
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex); 
        java.util.regex.Matcher m = p.matcher(normalized); 
        if (m.find()) return m.group(1); 
        return null; 
    } 
 
    // ---------- UI helpers ---------- 
    private void showError(String msg) { Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }); } 
 
    private <T> void runAsync(ThrowingSupplier<T> supplier, java.util.function.Consumer<T> onSuccess, java.util.function.Consumer<Throwable> onError) { 
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return supplier.get(); } }; 
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue())); 
        task.setOnFailed(e -> onError.accept(task.getException())); 
        new Thread(task).start(); 
    } 
    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; } 
 
    // ---------- DB layer ---------- 
    static class DB { 
        static Connection get() throws SQLException { return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); } 
 
        static void initSchema() throws SQLException { 
            try (Connection c = get(); Statement s = c.createStatement()) { 
                s.executeUpdate(""" 
                    CREATE TABLE IF NOT EXISTS accounts ( 
                      accountNo VARCHAR(255) PRIMARY KEY, 
                      apiKey VARCHAR(255), 
                      pin VARCHAR(64), 
                      balance DOUBLE DEFAULT 0, 
                      name VARCHAR(255), 
                      picture VARCHAR(1000), 
                      provider VARCHAR(50) 
                    ) 
                """); 
                s.executeUpdate(""" 
                    CREATE TABLE IF NOT EXISTS transactions ( 
                      id BIGINT PRIMARY KEY AUTO_INCREMENT, 
                      accountNo VARCHAR(255), 
                      type VARCHAR(50), 
                      amount DOUBLE, 
                      ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
                      FOREIGN KEY (accountNo) REFERENCES accounts(accountNo) 
                    ) 
                """); 
            } 
        } 
 
        static boolean validateLogin(String apiKey, String accNo, String pin) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM accounts WHERE accountNo=? AND apiKey=? AND pin=?")) { 
                ps.setString(1, accNo); ps.setString(2, apiKey); ps.setString(3, pin); 
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); } 
            } 
        } 
 
        static boolean hasAccount(String accNo) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM accounts WHERE accountNo=?")) { 
                ps.setString(1, accNo); try (ResultSet rs = ps.executeQuery()) { return rs.next(); } 
            } 
        } 
 
        static void createAccount(String accNo, String apiKey, String pin, double balance) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("INSERT INTO accounts(accountNo,apiKey,pin,balance) VALUES(?,?,?,?)")) { 
                ps.setString(1, accNo); ps.setString(2, apiKey); ps.setString(3, pin); ps.setDouble(4, balance); ps.executeUpdate(); 
            } 
            insertTxn(accNo, "OPEN", balance); 
        } 
 
        static void createOAuthAccount(String accNo, String apiKey, String pin, double balance, String name, String picture) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("INSERT INTO accounts(accountNo,apiKey,pin,balance,name,picture,provider) VALUES(?,?,?,?,?,?,?)")) { 
                ps.setString(1, accNo); ps.setString(2, apiKey); ps.setString(3, pin); ps.setDouble(4, balance); ps.setString(5, name); ps.setString(6, picture); ps.setString(7, "google"); ps.executeUpdate(); 
            } 
            insertTxn(accNo, "OPEN", balance); 
        } 
 
        static void updateProfile(String accNo, String name, String picture) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("UPDATE accounts SET name=?, picture=? WHERE accountNo=?")) { 
                ps.setString(1, name); ps.setString(2, picture); ps.setString(3, accNo); ps.executeUpdate(); 
            } 
        } 
 
        static void changeBalance(String accNo, double delta) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE accountNo=?")) { 
                ps.setDouble(1, delta); ps.setString(2, accNo); ps.executeUpdate(); 
            } 
        } 
 
        static double getBalance(String accNo) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("SELECT balance FROM accounts WHERE accountNo=?")) { 
                ps.setString(1, accNo); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getDouble(1); } 
            } 
            throw new SQLException("Account not found"); 
        } 
 
        static Map<String,String> getAccountInfo(String accNo) throws SQLException { 
            Map<String,String> map = new HashMap<>(); 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("SELECT name,picture FROM accounts WHERE accountNo=?")) { 
                ps.setString(1, accNo); 
                try (ResultSet rs = ps.executeQuery()) { 
                    if (rs.next()) { 
                        map.put("name", rs.getString("name") == null ? "" : rs.getString("name")); 
                        map.put("picture", rs.getString("picture") == null ? "" : rs.getString("picture")); 
                        return map; 
                    } 
                } 
            } 
            map.put("name", ""); 
            map.put("picture", ""); 
            return map; 
        } 
 
        static void insertTxn(String accNo, String type, double amount) throws SQLException { 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("INSERT INTO transactions(accountNo,type,amount) VALUES(?,?,?)")) { 
                ps.setString(1, accNo); ps.setString(2, type); ps.setDouble(3, amount); ps.executeUpdate(); 
            } 
        } 
 
        static List<Txn> getTxns(String accNo) throws SQLException { 
            List<Txn> list = new ArrayList<>(); 
            try (Connection c = get(); PreparedStatement ps = c.prepareStatement("SELECT id,accountNo,type,amount,ts FROM transactions WHERE accountNo=? ORDER BY ts DESC")) { 
                ps.setString(1, accNo); 
                try (ResultSet rs = ps.executeQuery()) { 
                    while (rs.next()) list.add(new Txn(rs.getLong("id"), rs.getString("accountNo"), rs.getString("type"), rs.getDouble("amount"), rs.getTimestamp("ts"))); 
                } 
            } 
            return list; 
        } 
 
        static List<Map<String,String>> getAllTransactions() throws SQLException { List<Map<String,String>> list = new ArrayList<>(); try (Connection c = get(); PreparedStatement ps = c.prepareStatement( "SELECT t.ts, t.accountNo, a.name, t.type, t.amount FROM transactions t LEFT JOIN accounts a ON t.accountNo = a.accountNo ORDER BY t.ts DESC")) { 
        try (ResultSet rs = ps.executeQuery()) { 
        while (rs.next()) { 
Map<String,String> row = new HashMap<>(); 
Timestamp ts = rs.getTimestamp("ts"); 
row.put("ts", ts == null ? "" : ts.toString()); 
row.put("account", rs.getString("accountNo")); 
row.put("name", rs.getString("name") == null ? "" : rs.getString("name")); 
row.put("type", rs.getString("type")); 
row.put("amount", String.valueOf(rs.getDouble("amount"))); 
list.add(row); 
} 
} 
} 
return list; 
} 
} 
// ---------- Simple Txn POJO ---------- 
static class Txn { long id; String account; String type; double amount; Timestamp ts; Txn(long id,String a,String 
t,double amt,Timestamp ts){ this.id=id;this.account=a;this.type=t;this.amount=amt;this.ts=ts; } } 
// ---------- main ---------- 
public static void main(String[] args) { 
try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException e) { 
System.err.println("MySQL JDBC driver not found"); } 
launch(args); 
} 
} 

