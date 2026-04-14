package org.example.utils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Singleton — connexion MySQL à la base {@code pidb} (schéma : {@code /sql/pidb-1.sql}).
 * <p>
 * Priorité de configuration : propriétés système {@code -Ddb.*} &gt; variables d’environnement
 * {@code PIDB_JDBC_URL}, {@code PIDB_DB_USER}, {@code PIDB_DB_PASSWORD} &gt; {@code application.properties}
 * &gt; valeurs par défaut (localhost, root, mot de passe vide).
 */
public class MyDatabase {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/pidb";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private static final Properties FILE_CONFIG = loadFileConfig();

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;
    private static MyDatabase instance;

    private MyDatabase() {
        this.url = readConfig("db.url", "PIDB_JDBC_URL", DEFAULT_URL);
        this.user = readConfig("db.user", "PIDB_DB_USER", DEFAULT_USER);
        this.password = readConfig("db.password", "PIDB_DB_PASSWORD", DEFAULT_PASSWORD);
    }

    private static String readConfig(String propKey, String envKey, String fallback) {
        String fromProp = System.getProperty(propKey);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = FILE_CONFIG.getProperty(propKey);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = MyDatabase.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // défauts JDBC
        }
        return p;
    }

    public static synchronized MyDatabase getInstance() {
        if (instance == null) {
            instance = new MyDatabase();
        }
        return instance;
    }

    /**
     * Connexion JDBC (création lazy). Affiche « Connected » en console si succès.
     */
    public Connection openConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                connection = DriverManager.getConnection(url, user, password);
                try (Statement st = connection.createStatement()) {
                    st.execute("SET NAMES utf8mb4");
                }
                System.out.println("Connected");
            } catch (SQLException e) {
                System.err.println(e.getMessage());
                throw e;
            }
        }
        return connection;
    }

    /** Utilisé par les services ({@code MyDatabase.getConnection()}). */
    public static Connection getConnection() throws SQLException {
        return getInstance().openConnection();
    }

    /** URL JDBC effective (logs / debug). */
    public static String getJdbcUrl() {
        return getInstance().url;
    }
}
