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
        /* Idempotent : crée les tables ajoutées dans une nouvelle version sans obliger à redémarrer la JVM. */
        ensureMysqlPidbTables(connection);
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

    /**
     * Crée les tables attendues par les services si elles manquent sur MySQL {@code pidb}
     * (le {@code schema.sql} du dépôt est SQLite).
     */
    private static void ensureMysqlPidbTables(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS evenements (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        titre VARCHAR(180) NOT NULL,
                        description TEXT,
                        date_debut DATETIME NOT NULL,
                        date_fin DATETIME NOT NULL,
                        lieu VARCHAR(180),
                        thematique VARCHAR(120) NULL,
                        mode_evenement VARCHAR(40) NULL,
                        lien_google_maps VARCHAR(512) NULL,
                        lien_zoom_visio VARCHAR(512) NULL,
                        latitude DOUBLE NULL,
                        longitude DOUBLE NULL,
                        places_max INT NOT NULL DEFAULT 0,
                        statut VARCHAR(32) NOT NULL DEFAULT 'BROUILLON'
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            try {
                st.execute("ALTER TABLE evenements ADD COLUMN thematique VARCHAR(120) NULL AFTER lieu");
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1060) {
                    throw ex;
                }
            }
            try {
                st.execute("ALTER TABLE evenements ADD COLUMN mode_evenement VARCHAR(40) NULL AFTER lieu");
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1060) {
                    throw ex;
                }
            }
            try {
                st.execute("ALTER TABLE evenements ADD COLUMN lien_google_maps VARCHAR(512) NULL AFTER mode_evenement");
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1060) {
                    throw ex;
                }
            }
            try {
                st.execute("ALTER TABLE evenements ADD COLUMN lien_zoom_visio VARCHAR(512) NULL AFTER lien_google_maps");
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1060) {
                    throw ex;
                }
            }
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS inscriptions_evenement (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        evenement_id INT NOT NULL,
                        utilisateur_id INT NOT NULL,
                        statut VARCHAR(32) NOT NULL DEFAULT 'EN_ATTENTE',
                        date_inscription DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_inscription_evt_user (evenement_id, utilisateur_id),
                        CONSTRAINT fk_insc_evenement FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
                        CONSTRAINT fk_insc_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES `user`(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS evenement_messages (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        evenement_id INT NOT NULL,
                        expediteur_user_id INT NOT NULL,
                        destinataire_user_id INT NULL,
                        corps TEXT NOT NULL,
                        date_envoi DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
                        FOREIGN KEY (expediteur_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                        FOREIGN KEY (destinataire_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                        INDEX idx_evmsg_evt_user (evenement_id, expediteur_user_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            try {
                st.execute("ALTER TABLE evenement_messages ADD COLUMN destinataire_user_id INT NULL AFTER expediteur_user_id");
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1060) {
                    throw ex;
                }
            }
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS notifications_admin (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        type_code VARCHAR(48) NOT NULL,
                        evenement_id INT NULL,
                        expediteur_user_id INT NOT NULL,
                        resume VARCHAR(512) NOT NULL,
                        lu TINYINT(1) NOT NULL DEFAULT 0,
                        date_creation DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_notif_evenement FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
                        CONSTRAINT fk_notif_user FOREIGN KEY (expediteur_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
                        INDEX idx_notif_lu (lu),
                        INDEX idx_notif_date (date_creation)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS notifications_user (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        utilisateur_id INT NOT NULL,
                        type_code VARCHAR(64) NOT NULL,
                        evenement_id INT NULL,
                        resume VARCHAR(512) NOT NULL,
                        lu TINYINT(1) NOT NULL DEFAULT 0,
                        date_creation DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (utilisateur_id) REFERENCES `user`(id) ON DELETE CASCADE,
                        FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
                        INDEX idx_nu_user_lu (utilisateur_id, lu),
                        INDEX idx_nu_user_date (utilisateur_id, date_creation)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS modules (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        titre VARCHAR(180) NOT NULL,
                        description TEXT,
                        categorie VARCHAR(120),
                        date_creation DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                        ressources_lien VARCHAR(255)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `blog` (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        titre VARCHAR(500) NOT NULL,
                        contenu MEDIUMTEXT,
                        auteur_id INT NOT NULL,
                        date_publication DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
                        categorie VARCHAR(120),
                        slug VARCHAR(180),
                        module_id INT NULL,
                        user_id INT NULL,
                        CONSTRAINT fk_blog_auteur FOREIGN KEY (auteur_id) REFERENCES `user`(id) ON DELETE CASCADE,
                        CONSTRAINT fk_blog_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            try {
                st.execute("ALTER TABLE `blog` ADD COLUMN date_publication DATETIME NULL DEFAULT CURRENT_TIMESTAMP");
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1060) {
                    throw ex;
                }
            }
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS thematiques (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        nom VARCHAR(180) NOT NULL,
                        code VARCHAR(32) NOT NULL,
                        description TEXT,
                        couleur VARCHAR(16),
                        sous_titre VARCHAR(180),
                        image_chemin VARCHAR(512),
                        ordre_affichage INT NOT NULL DEFAULT 0,
                        visible_site TINYINT(1) NOT NULL DEFAULT 1,
                        public_cible VARCHAR(80) NOT NULL,
                        niveau_difficulte VARCHAR(80) NOT NULL,
                        UNIQUE KEY uq_thematique_code (code)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        }
    }
}
