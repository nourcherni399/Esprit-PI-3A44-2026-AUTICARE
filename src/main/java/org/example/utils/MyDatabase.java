package org.example.utils;

import java.util.Locale;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.example.services.AppointmentService;
import org.example.services.MedecinPatientNoteService;

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
                ensureRendezVousOptionalColumns(connection);
                ensureRendezVousMotifColumnWide(connection);
                ensureRendezVousPatientReponseLueColumn(connection);
                ensureRendezVousMedecinDemandeLueColumn(connection);
                ensureRendezVousDisponibiliteIdColumn(connection);
                AppointmentService.clearRendezVousSchemaCache();
                ensureNoteTable(connection);
                ensureMedecinRatingTable(connection);
                ensureMedecinRatingPatientColumnCompat(connection);
                ensureMedecinRatingStarsColumnCompat(connection);
                ensureMedecinRatingCommentColumnCompat(connection);
                ensureMedecinRatingTimestampColumnCompat(connection);
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

    /**
     * Bases MySQL plus anciennes : la table {@code rendez_vous} peut exister sans {@code statut} / {@code notes},
     * ce qui provoque « Column 'statut' not found » dans {@link org.example.services.AppointmentService}.
     */
    private static void ensureRendezVousOptionalColumns(Connection c) {
        try (Statement st = c.createStatement()) {
            try {
                st.execute("ALTER TABLE rendez_vous ADD COLUMN statut VARCHAR(40) NOT NULL DEFAULT 'PLANIFIE'");
            } catch (SQLException e) {
                if (!isDuplicateColumnError(e)) {
                    System.err.println("Migration rendez_vous.statut : " + e.getMessage());
                }
            }
            try {
                st.execute("ALTER TABLE rendez_vous ADD COLUMN notes TEXT");
            } catch (SQLException e) {
                if (!isDuplicateColumnError(e)) {
                    System.err.println("Migration rendez_vous.notes : " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Migration rendez_vous : " + e.getMessage());
        }
    }

    /**
     * Évite « Data truncated for column 'motif' » : VARCHAR trop court ou ancien type ENUM incompatible
     * avec les libellés saisis (ex. type de consultation).
     */
    private static void ensureRendezVousMotifColumnWide(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE rendez_vous MODIFY COLUMN motif VARCHAR(255) NULL");
        } catch (SQLException e) {
            System.err.println("Migration rendez_vous.motif : " + e.getMessage());
        }
    }

    /** Colonne pour savoir si le patient a vu l’acceptation / le refus du médecin. */
    private static void ensureRendezVousPatientReponseLueColumn(Connection c) {
        try (Statement st = c.createStatement()) {
            try {
                st.execute("ALTER TABLE rendez_vous ADD COLUMN patient_reponse_lue TINYINT(1) NOT NULL DEFAULT 1");
            } catch (SQLException e) {
                if (!isDuplicateColumnError(e)) {
                    System.err.println("Migration rendez_vous.patient_reponse_lue : " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Migration patient_reponse_lue : " + e.getMessage());
        }
    }

    /** Lien optionnel vers le créneau {@code disponibilite} (prise de RDV publique). */
    private static void ensureRendezVousDisponibiliteIdColumn(Connection c) {
        try (Statement st = c.createStatement()) {
            try {
                st.execute("ALTER TABLE rendez_vous ADD COLUMN disponibilite_id INT NULL DEFAULT NULL");
            } catch (SQLException e) {
                if (!isDuplicateColumnError(e)) {
                    System.err.println("Migration rendez_vous.disponibilite_id : " + e.getMessage());
                }
            }
            try {
                st.execute("ALTER TABLE rendez_vous ADD KEY idx_rdv_disponibilite (disponibilite_id)");
            } catch (SQLException e) {
                if (!isDuplicateKeyError(e)) {
                    System.err.println("Migration rendez_vous idx_rdv_disponibilite : " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Migration disponibilite_id : " + e.getMessage());
        }
    }

    private static boolean isDuplicateKeyError(SQLException e) {
        if (e.getErrorCode() == 1061) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase(Locale.ROOT).contains("duplicate key name");
    }

    /** Demande EN_ATTENTE vue par le médecin (cloche / liste notifications). */
    private static void ensureRendezVousMedecinDemandeLueColumn(Connection c) {
        try (Statement st = c.createStatement()) {
            try {
                st.execute("ALTER TABLE rendez_vous ADD COLUMN medecin_demande_lue TINYINT(1) NOT NULL DEFAULT 1");
            } catch (SQLException e) {
                if (!isDuplicateColumnError(e)) {
                    System.err.println("Migration rendez_vous.medecin_demande_lue : " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Migration medecin_demande_lue : " + e.getMessage());
        }
    }

    /** Avis / notes sur les médecins (patients), pour la page Rendez-vous publique. */
    private static void ensureMedecinRatingTable(Connection c) {
        String sql = "CREATE TABLE IF NOT EXISTS `medecin_rating` ("
                + "`id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "`medecin_id` INT NOT NULL,"
                + "`patient_id` INT NOT NULL,"
                + "`stars` TINYINT NOT NULL,"
                + "`commentaire` VARCHAR(2000) NULL,"
                + "`created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,"
                + "`updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY `uq_medecin_rating_patient` (`medecin_id`,`patient_id`),"
                + "KEY `idx_medecin_rating_medecin` (`medecin_id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            System.err.println("Table medecin_rating : " + e.getMessage());
        }
    }

    /**
     * Compat schémas historiques : certaines bases utilisent {@code user_id} au lieu de {@code patient_id}
     * dans {@code medecin_rating}. On normalise vers {@code patient_id}.
     */
    private static void ensureMedecinRatingPatientColumnCompat(Connection c) {
        try (Statement st = c.createStatement()) {
            boolean hasPatientId;
            boolean hasUserId;
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='patient_id'")) {
                rs.next();
                hasPatientId = rs.getInt("c") > 0;
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='user_id'")) {
                rs.next();
                hasUserId = rs.getInt("c") > 0;
            }

            if (!hasPatientId && hasUserId) {
                st.execute("ALTER TABLE medecin_rating CHANGE COLUMN user_id patient_id INT NOT NULL");
            }
        } catch (SQLException e) {
            System.err.println("Migration medecin_rating patient_id/user_id : " + e.getMessage());
        }
    }

    /**
     * Compat schémas historiques : normalise la colonne de note vers {@code stars}.
     */
    private static void ensureMedecinRatingStarsColumnCompat(Connection c) {
        try (Statement st = c.createStatement()) {
            boolean hasStars;
            boolean hasRating;
            boolean hasNote;
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='stars'")) {
                rs.next();
                hasStars = rs.getInt("c") > 0;
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='rating'")) {
                rs.next();
                hasRating = rs.getInt("c") > 0;
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='note'")) {
                rs.next();
                hasNote = rs.getInt("c") > 0;
            }

            if (!hasStars && hasRating) {
                st.execute("ALTER TABLE medecin_rating CHANGE COLUMN rating stars TINYINT NOT NULL");
                hasStars = true;
            } else if (!hasStars && hasNote) {
                st.execute("ALTER TABLE medecin_rating CHANGE COLUMN note stars TINYINT NOT NULL");
                hasStars = true;
            }
            if (!hasStars) {
                st.execute("ALTER TABLE medecin_rating ADD COLUMN stars TINYINT NOT NULL DEFAULT 0");
            }
        } catch (SQLException e) {
            System.err.println("Migration medecin_rating stars : " + e.getMessage());
        }
    }

    /**
     * Compat schémas historiques : normalise la colonne commentaire vers {@code commentaire}.
     */
    private static void ensureMedecinRatingCommentColumnCompat(Connection c) {
        try (Statement st = c.createStatement()) {
            boolean hasCommentaire;
            boolean hasComment;
            boolean hasAvis;
            boolean hasContenu;
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='commentaire'")) {
                rs.next();
                hasCommentaire = rs.getInt("c") > 0;
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='comment'")) {
                rs.next();
                hasComment = rs.getInt("c") > 0;
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='avis'")) {
                rs.next();
                hasAvis = rs.getInt("c") > 0;
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='contenu'")) {
                rs.next();
                hasContenu = rs.getInt("c") > 0;
            }

            if (!hasCommentaire && hasComment) {
                st.execute("ALTER TABLE medecin_rating CHANGE COLUMN comment commentaire VARCHAR(2000) NULL");
                hasCommentaire = true;
            } else if (!hasCommentaire && hasAvis) {
                st.execute("ALTER TABLE medecin_rating CHANGE COLUMN avis commentaire VARCHAR(2000) NULL");
                hasCommentaire = true;
            } else if (!hasCommentaire && hasContenu) {
                st.execute("ALTER TABLE medecin_rating CHANGE COLUMN contenu commentaire VARCHAR(2000) NULL");
                hasCommentaire = true;
            }
            if (!hasCommentaire) {
                st.execute("ALTER TABLE medecin_rating ADD COLUMN commentaire VARCHAR(2000) NULL");
            }
        } catch (SQLException e) {
            System.err.println("Migration medecin_rating commentaire : " + e.getMessage());
        }
    }

    /** Schémas anciens : ajoute {@code created_at} / {@code updated_at} si absents (requêtes avis). */
    private static void ensureMedecinRatingTimestampColumnCompat(Connection c) {
        try (Statement st = c.createStatement()) {
            boolean hasCreated;
            boolean hasUpdated;
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='created_at'")) {
                rs.next();
                hasCreated = rs.getInt("c") > 0;
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) AS c FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name='medecin_rating' AND column_name='updated_at'")) {
                rs.next();
                hasUpdated = rs.getInt("c") > 0;
            }
            if (!hasCreated) {
                st.execute("ALTER TABLE medecin_rating ADD COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
            } else {
                st.execute("ALTER TABLE medecin_rating MODIFY COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
            }
            if (!hasUpdated) {
                st.execute("ALTER TABLE medecin_rating ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP "
                        + "ON UPDATE CURRENT_TIMESTAMP");
            } else {
                st.execute("ALTER TABLE medecin_rating MODIFY COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP "
                        + "ON UPDATE CURRENT_TIMESTAMP");
            }
        } catch (SQLException e) {
            System.err.println("Migration medecin_rating created_at/updated_at : " + e.getMessage());
        }
    }

    /** Table {@code note} (alignée Symfony / schéma métier) ; même structure que l’ancienne {@code medecin_patient_note}. */
    private static void ensureNoteTable(Connection c) {
        String sql = "CREATE TABLE IF NOT EXISTS `note` ("
                + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "medecin_id INT NOT NULL,"
                + "patient_id INT NOT NULL,"
                + "contenu TEXT NOT NULL,"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "KEY idx_note_med (medecin_id),"
                + "KEY idx_note_pat (patient_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            System.err.println("Table note : " + e.getMessage());
        }
        MedecinPatientNoteService.clearNoteTableNameCache();
    }

    private static boolean isDuplicateColumnError(SQLException e) {
        if (e.getErrorCode() == 1060) {
            return true;
        }
        String state = e.getSQLState();
        if ("42S21".equals(state)) {
            return true;
        }
        String m = e.getMessage();
        if (m == null) {
            return false;
        }
        String lower = m.toLowerCase();
        return lower.contains("duplicate column")
                || lower.contains("already exists")
                || lower.contains("déjà exist"); /* MySQL FR */
    }
}
