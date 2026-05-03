package org.example.services;

import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Avis patients sur les médecins (table {@code medecin_rating}).
 */
public class MedecinRatingService {

    private static final DateTimeFormatter AFFICHE =
            DateTimeFormatter.ofPattern("d MMM yyyy à HH:mm", Locale.FRENCH);
    private static volatile String patientFkColumn;
    private static volatile String starsColumn;
    private static volatile String commentColumn;
    /** 0 = pas encore chargé, 1 = chargé (voir {@link #ratingHasCreatedAt} / {@link #ratingHasUpdatedAt}). */
    private static volatile byte ratingTsLoaded;
    private static boolean ratingHasCreatedAt;
    private static boolean ratingHasUpdatedAt;

    public record AvgRating(double average, int count) {
        public String labelFr() {
            if (count <= 0) {
                return "";
            }
            return String.format(Locale.FRENCH, "%.1f/5 · %d avis", average, count);
        }
    }

    public record PatientReview(int stars, String comment) {}

    /** Ligne affichée dans la liste « tous les avis ». */
    public record PublicReview(int stars, String commentaire, String auteurAffiche, String dateAffiche) {}

    public AvgRating averageForMedecin(int medecinId) throws SQLException {
        String starCol = resolveStarsColumn();
        String sql = "SELECT AVG(" + starCol + ") AS moy, COUNT(*) AS cnt FROM medecin_rating WHERE medecin_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new AvgRating(0, 0);
                }
                double avg = rs.getDouble("moy");
                if (rs.wasNull()) {
                    avg = 0;
                }
                int cnt = rs.getInt("cnt");
                return new AvgRating(avg, cnt);
            }
        }
    }

    public Optional<PatientReview> findPatientReview(int medecinId, int patientId) throws SQLException {
        String fk = resolvePatientFkColumn();
        String starCol = resolveStarsColumn();
        String comCol = resolveCommentColumn();
        String selectComment = comCol != null ? comCol + " AS commentaire" : "NULL AS commentaire";
        String sql = "SELECT " + starCol + " AS stars, " + selectComment
                + " FROM medecin_rating WHERE medecin_id=? AND " + fk + "=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.setInt(2, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int s = rs.getInt("stars");
                String c = rs.getString("commentaire");
                return Optional.of(new PatientReview(s, c));
            }
        }
    }

    public void upsertReview(int medecinId, int patientId, int stars, String commentaire) throws SQLException {
        if (stars < 1 || stars > 5) {
            throw new SQLException("La note doit être entre 1 et 5.");
        }
        Connection conn = MyDatabase.getConnection();
        String fk = resolvePatientFkColumn();
        String starCol = resolveStarsColumn();
        String comCol = resolveCommentColumn();
        ensureRatingTimestampsLoaded();
        String dupTail = buildUpsertDuplicateTail(starCol, comCol);
        String insertTsCols = buildInsertTimestampColumns();
        String insertTsVals = buildInsertTimestampValues();
        String sql;
        if (comCol != null) {
            sql = "INSERT INTO medecin_rating (medecin_id, " + fk + ", " + starCol + ", " + comCol + insertTsCols + ") VALUES (?,?,?,?" + insertTsVals + ") "
                    + "ON DUPLICATE KEY UPDATE " + dupTail;
        } else {
            sql = "INSERT INTO medecin_rating (medecin_id, " + fk + ", " + starCol + insertTsCols + ") VALUES (?,?,?" + insertTsVals + ") "
                    + "ON DUPLICATE KEY UPDATE " + dupTail;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.setInt(2, patientId);
            ps.setInt(3, stars);
            if (comCol != null) {
                ps.setString(4, commentaire);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Tous les avis enregistrés pour ce praticien (notes + textes), du plus récent au plus ancien.
     */
    public List<PublicReview> listReviewsForMedecin(int medecinId) throws SQLException {
        String fk = resolvePatientFkColumn();
        String starCol = resolveStarsColumn();
        String comCol = resolveCommentColumn();
        ensureRatingTimestampsLoaded();
        String selectComment = comCol != null ? "r." + comCol + " AS commentaire" : "NULL AS commentaire";
        String dateCols = buildSelectTimestampAliases();
        String orderBy = buildOrderByTimestamps();
        String sql = "SELECT r." + starCol + " AS stars, " + selectComment + dateCols + ", u.prenom, u.nom "
                + "FROM medecin_rating r "
                + "LEFT JOIN `user` u ON u.id = r." + fk + " "
                + "WHERE r.medecin_id = ? "
                + orderBy;
        List<PublicReview> out = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int stars = rs.getInt("stars");
                    String com = rs.getString("commentaire");
                    String prenom = rs.getString("prenom");
                    String nom = rs.getString("nom");
                    LocalDateTime dt = readDateTime(rs, "updated_at", "created_at");
                    out.add(new PublicReview(
                            stars,
                            com != null ? com : "",
                            formatAuteur(prenom, nom),
                            dt != null ? dt.format(AFFICHE) : "—"));
                }
            }
        }
        return out;
    }

    private static String resolvePatientFkColumn() throws SQLException {
        String cached = patientFkColumn;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        synchronized (MedecinRatingService.class) {
            if (patientFkColumn != null && !patientFkColumn.isBlank()) {
                return patientFkColumn;
            }
            Connection c = MyDatabase.getConnection();
            String sql = "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'medecin_rating' "
                    + "AND column_name IN ('patient_id','user_id')";
            boolean hasPatientId = false;
            boolean hasUserId = false;
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    if ("patient_id".equalsIgnoreCase(col)) {
                        hasPatientId = true;
                    } else if ("user_id".equalsIgnoreCase(col)) {
                        hasUserId = true;
                    }
                }
            }
            if (hasPatientId) {
                patientFkColumn = "patient_id";
            } else if (hasUserId) {
                patientFkColumn = "user_id";
            } else {
                patientFkColumn = "patient_id";
            }
            return patientFkColumn;
        }
    }

    private static String resolveStarsColumn() throws SQLException {
        String cached = starsColumn;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        synchronized (MedecinRatingService.class) {
            if (starsColumn != null && !starsColumn.isBlank()) {
                return starsColumn;
            }
            List<String> candidates = List.of("stars", "rating", "note", "etoiles");
            starsColumn = firstExistingColumn(candidates).orElse("stars");
            return starsColumn;
        }
    }

    private static String resolveCommentColumn() throws SQLException {
        String cached = commentColumn;
        if (cached != null) {
            return cached;
        }
        synchronized (MedecinRatingService.class) {
            if (commentColumn != null) {
                return commentColumn;
            }
            List<String> candidates = List.of("commentaire", "comment", "comments", "avis", "contenu", "description");
            commentColumn = firstExistingColumn(candidates).orElse(null);
            return commentColumn;
        }
    }

    private static void ensureRatingTimestampsLoaded() throws SQLException {
        if (ratingTsLoaded == 1) {
            return;
        }
        synchronized (MedecinRatingService.class) {
            if (ratingTsLoaded == 1) {
                return;
            }
            Connection conn = MyDatabase.getConnection();
            boolean hasC = false;
            boolean hasU = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'medecin_rating' "
                            + "AND column_name IN ('created_at','updated_at')");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String n = rs.getString("column_name");
                    if ("created_at".equalsIgnoreCase(n)) {
                        hasC = true;
                    }
                    if ("updated_at".equalsIgnoreCase(n)) {
                        hasU = true;
                    }
                }
            }
            ratingHasCreatedAt = hasC;
            ratingHasUpdatedAt = hasU;
            ratingTsLoaded = 1;
        }
    }

    private static String buildUpsertDuplicateTail(String starCol, String comCol) {
        StringBuilder b = new StringBuilder();
        b.append(starCol).append("=VALUES(").append(starCol).append(")");
        if (comCol != null) {
            b.append(", ").append(comCol).append("=VALUES(").append(comCol).append(")");
        }
        if (ratingHasUpdatedAt) {
            b.append(", updated_at=CURRENT_TIMESTAMP");
        }
        return b.toString();
    }

    private static String buildInsertTimestampColumns() {
        StringBuilder b = new StringBuilder();
        if (ratingHasCreatedAt) {
            b.append(", created_at");
        }
        if (ratingHasUpdatedAt) {
            b.append(", updated_at");
        }
        return b.toString();
    }

    private static String buildInsertTimestampValues() {
        StringBuilder b = new StringBuilder();
        if (ratingHasCreatedAt) {
            b.append(", CURRENT_TIMESTAMP");
        }
        if (ratingHasUpdatedAt) {
            b.append(", CURRENT_TIMESTAMP");
        }
        return b.toString();
    }

    private static String buildSelectTimestampAliases() {
        if (ratingHasCreatedAt && ratingHasUpdatedAt) {
            return ", r.created_at, r.updated_at";
        }
        if (ratingHasCreatedAt) {
            return ", r.created_at, NULL AS updated_at";
        }
        if (ratingHasUpdatedAt) {
            return ", NULL AS created_at, r.updated_at";
        }
        return ", NULL AS created_at, NULL AS updated_at";
    }

    private static String buildOrderByTimestamps() {
        if (ratingHasCreatedAt && ratingHasUpdatedAt) {
            return "ORDER BY COALESCE(r.updated_at, r.created_at) DESC, r.id DESC";
        }
        if (ratingHasCreatedAt) {
            return "ORDER BY r.created_at DESC, r.id DESC";
        }
        if (ratingHasUpdatedAt) {
            return "ORDER BY r.updated_at DESC, r.id DESC";
        }
        return "ORDER BY r.id DESC";
    }

    private static Optional<String> firstExistingColumn(List<String> candidates) throws SQLException {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Connection c = MyDatabase.getConnection();
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                in.append(',');
            }
            in.append('?');
        }
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'medecin_rating' "
                + "AND column_name IN (" + in + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < candidates.size(); i++) {
                ps.setString(i + 1, candidates.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<String> found = new ArrayList<>();
                while (rs.next()) {
                    found.add(rs.getString("column_name"));
                }
                for (String cnd : candidates) {
                    for (String f : found) {
                        if (cnd.equalsIgnoreCase(f)) {
                            return Optional.of(cnd);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static LocalDateTime readDateTime(ResultSet rs, String colUpdated, String colCreated) throws SQLException {
        Timestamp tu = rs.getTimestamp(colUpdated);
        if (tu != null && !rs.wasNull()) {
            return tu.toLocalDateTime();
        }
        Timestamp tc = rs.getTimestamp(colCreated);
        if (tc != null && !rs.wasNull()) {
            return tc.toLocalDateTime();
        }
        return null;
    }

    private static String formatAuteur(String prenom, String nom) {
        String p = prenom != null ? prenom.trim() : "";
        String n = nom != null ? nom.trim() : "";
        if (p.isEmpty() && n.isEmpty()) {
            return "Patient";
        }
        if (n.isEmpty()) {
            return p;
        }
        if (p.isEmpty()) {
            return n.charAt(0) + ".";
        }
        return p + " " + Character.toUpperCase(n.charAt(0)) + ".";
    }
}
