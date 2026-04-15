package org.example.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Singleton — connexion MySQL à la base {@code pidb}.
 */
public class MyDatabase {

    private final String url = "jdbc:mysql://localhost:3306/pidb";
    private final String user = "root";
    private final String password = "";
    private Connection connection;
    private static MyDatabase instance;

    private MyDatabase() {
    }

    public static synchronized MyDatabase getInstance() {
        if (instance == null) {
            instance = new MyDatabase();
        }
        return instance;
    }

    /**
     * Connexion JDBC (création lazy). Affiche « Connected » en console si succès.
     * Équivalent pratique de l’ancien constructeur qui ouvrait tout de suite la connexion.
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

    /** URL JDBC (logs / debug). */
    public static String getJdbcUrl() {
        return getInstance().url;
    }
}
