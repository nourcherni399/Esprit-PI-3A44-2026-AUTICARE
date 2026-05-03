package org.example.utils;

import java.sql.SQLException;

/**
 * Détecte les échecs de connexion JDBC (souvent confondus avec d'autres étapes du flux, ex. OAuth).
 */
public final class SqlConnectivityErrors {

    private SqlConnectivityErrors() {
    }

    public static boolean isLikelyDbConnectivity(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException) {
                return true;
            }
            String m = c.getMessage();
            if (m == null) {
                continue;
            }
            String u = m.toLowerCase();
            if (u.contains("communications link failure")
                    || u.contains("communications exception")
                    || u.contains("could not create connection")
                    || u.contains("connection refused")
                    || u.contains("unknown host")
                    || u.contains("the driver has not received any packets")) {
                return true;
            }
        }
        return false;
    }
}
