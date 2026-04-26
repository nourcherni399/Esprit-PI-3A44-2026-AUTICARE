package org.example.services;

import org.example.models.MedecinPatientNote;
import org.example.utils.MyDatabase;
import org.example.utils.NoteHtmlUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Accès aux notes médecin–patient. Table {@code note} (Symfony) ou {@code medecin_patient_note}.
 * Les noms de colonnes sont détectés (ex. {@code content} vs {@code contenu}, {@code date_creation} vs {@code created_at}).
 */
public class MedecinPatientNoteService {

    private static volatile String cachedNoteTableRef;
    /** Nom physique sans backticks, pour les métadonnées JDBC. */
    private static volatile String cachedNoteTablePlain;
    private static volatile NoteTableSchema cachedSchema;

    public static void clearNoteTableNameCache() {
        synchronized (MedecinPatientNoteService.class) {
            cachedNoteTableRef = null;
            cachedNoteTablePlain = null;
            cachedSchema = null;
        }
    }

    private static boolean metaTableExists(Connection c, String table) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), null, table, null)) {
            return rs.next();
        }
    }

    private static void resolveNoteTable(Connection c) throws SQLException {
        if (cachedNoteTableRef != null && cachedNoteTablePlain != null) {
            return;
        }
        if (metaTableExists(c, "note")) {
            cachedNoteTableRef = "`note`";
            cachedNoteTablePlain = "note";
        } else if (metaTableExists(c, "medecin_patient_note")) {
            cachedNoteTableRef = "medecin_patient_note";
            cachedNoteTablePlain = "medecin_patient_note";
        } else {
            cachedNoteTableRef = null;
            cachedNoteTablePlain = null;
        }
    }

    /**
     * Référence SQL de la table physique : en priorité {@code note}, sinon {@code medecin_patient_note}.
     */
    private static String noteTableRef() throws SQLException {
        if (cachedNoteTableRef != null) {
            return cachedNoteTableRef;
        }
        synchronized (MedecinPatientNoteService.class) {
            if (cachedNoteTableRef != null) {
                return cachedNoteTableRef;
            }
            resolveNoteTable(MyDatabase.getConnection());
            return cachedNoteTableRef;
        }
    }

    private static String requireNoteTableRef() throws SQLException {
        String t = noteTableRef();
        if (t == null) {
            throw new SQLException("Table « note » introuvable. Relancez l'application après migration.");
        }
        return t;
    }

    private static Map<String, String> readColumnsLowerToActual(Connection c, String plainTable) throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        DatabaseMetaData md = c.getMetaData();
        String catalog = c.getCatalog();
        try (ResultSet crs = md.getColumns(catalog, null, plainTable, "%")) {
            while (crs.next()) {
                String name = crs.getString("COLUMN_NAME");
                if (name != null) {
                    map.put(name.toLowerCase(Locale.ROOT), name);
                }
            }
        }
        if (map.isEmpty() && catalog != null) {
            try (ResultSet crs = md.getColumns(null, null, plainTable, "%")) {
                while (crs.next()) {
                    String name = crs.getString("COLUMN_NAME");
                    if (name != null) {
                        map.put(name.toLowerCase(Locale.ROOT), name);
                    }
                }
            }
        }
        return map;
    }

    private static String pick(Map<String, String> cols, String... candidates) {
        for (String cand : candidates) {
            String actual = cols.get(cand.toLowerCase(Locale.ROOT));
            if (actual != null) {
                return actual;
            }
        }
        return null;
    }

    private static NoteTableSchema loadSchema(Connection c) throws SQLException {
        if (cachedSchema != null) {
            return cachedSchema;
        }
        synchronized (MedecinPatientNoteService.class) {
            if (cachedSchema != null) {
                return cachedSchema;
            }
            resolveNoteTable(c);
            if (cachedNoteTablePlain == null) {
                throw new SQLException("Table note introuvable.");
            }
            Map<String, String> cols = readColumnsLowerToActual(c, cachedNoteTablePlain);
            String idCol = pick(cols, "id");
            String medecinCol = pick(cols, "medecin_id", "doctor_id", "id_medecin");
            String patientCol = pick(cols, "patient_id", "id_patient");
            String contenuCol = pick(cols, "contenu", "content", "texte", "message", "body", "description", "text");
            /* Symfony : souvent date_creation / updated_at sans DEFAULT — les remplir à l’INSERT. */
            String[] onInsertTsCandidates = {
                    "date_creation",
                    "created_at",
                    "created",
                    "updated_at",
                    "updated",
                    "date_modification",
                    "modified_at",
                    "datetime",
                    "createdat",
                    "date"
            };
            List<String> insertTimestampCols = new ArrayList<>();
            LinkedHashSet<String> insertTsSeen = new LinkedHashSet<>();
            for (String cand : onInsertTsCandidates) {
                String actual = cols.get(cand.toLowerCase(Locale.ROOT));
                if (actual != null && insertTsSeen.add(actual)) {
                    insertTimestampCols.add(actual);
                }
            }
            String[] onUpdateTsCandidates = {
                    "updated_at",
                    "updated",
                    "date_modification",
                    "modified_at",
                    "date_maj"
            };
            List<String> updateTimestampCols = new ArrayList<>();
            for (String cand : onUpdateTsCandidates) {
                String actual = cols.get(cand.toLowerCase(Locale.ROOT));
                if (actual != null && !updateTimestampCols.contains(actual)) {
                    updateTimestampCols.add(actual);
                }
            }
            /* Tri / affichage : priorité schémas Symfony. */
            String dateCol = pick(cols,
                    "date_creation",
                    "created_at",
                    "created",
                    "date",
                    "updated_at",
                    "updated",
                    "datetime",
                    "createdat");
            if (idCol == null || medecinCol == null || patientCol == null || contenuCol == null) {
                throw new SQLException(
                        "Table « " + cachedNoteTablePlain + " » : colonnes attendues introuvables "
                                + "(id, médecin, patient, texte). Colonnes trouvées : " + cols.keySet());
            }
            cachedSchema = new NoteTableSchema(
                    idCol, medecinCol, patientCol, contenuCol, dateCol,
                    List.copyOf(insertTimestampCols), List.copyOf(updateTimestampCols));
            return cachedSchema;
        }
    }

    private static NoteTableSchema schema() throws SQLException {
        return loadSchema(MyDatabase.getConnection());
    }

    public void add(int medecinId, int patientId, String contenu) throws SQLException {
        if (contenu == null || contenu.isBlank() || NoteHtmlUtil.isEffectivelyEmpty(contenu)) {
            throw new SQLException("Contenu vide.");
        }
        String t = requireNoteTableRef();
        NoteTableSchema s = schema();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(t).append(" (`")
                .append(s.medecinCol).append("`,`").append(s.patientCol).append("`,`").append(s.contenuCol).append("`");
        for (String col : s.insertTimestampCols) {
            sql.append(",`").append(col).append("`");
        }
        sql.append(") VALUES(?,?,?");
        for (int i = 0; i < s.insertTimestampCols.size(); i++) {
            sql.append(",?");
        }
        sql.append(")");
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql.toString())) {
            int i = 1;
            ps.setInt(i++, medecinId);
            ps.setInt(i++, patientId);
            ps.setString(i++, contenu.trim());
            for (int k = 0; k < s.insertTimestampCols.size(); k++) {
                ps.setTimestamp(i++, now);
            }
            ps.executeUpdate();
        }
    }

    public void update(int noteId, int medecinId, String contenu) throws SQLException {
        if (contenu == null || contenu.isBlank() || NoteHtmlUtil.isEffectivelyEmpty(contenu)) {
            throw new SQLException("Contenu vide.");
        }
        String t = requireNoteTableRef();
        NoteTableSchema s = schema();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        StringBuilder sql = new StringBuilder("UPDATE ").append(t).append(" SET `").append(s.contenuCol).append("`=?");
        for (String uc : s.updateTimestampCols) {
            sql.append(",`").append(uc).append("`=?");
        }
        sql.append(" WHERE `").append(s.idCol).append("`=? AND `").append(s.medecinCol).append("`=?");
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql.toString())) {
            int i = 1;
            ps.setString(i++, contenu.trim());
            for (int k = 0; k < s.updateTimestampCols.size(); k++) {
                ps.setTimestamp(i++, now);
            }
            ps.setInt(i++, noteId);
            ps.setInt(i, medecinId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Note introuvable ou vous n'avez pas le droit de la modifier.");
            }
        }
    }

    public void delete(int noteId, int medecinId) throws SQLException {
        String t = requireNoteTableRef();
        NoteTableSchema s = schema();
        String sql = "DELETE FROM " + t + " WHERE `" + s.idCol + "`=? AND `" + s.medecinCol + "`=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, noteId);
            ps.setInt(2, medecinId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Note introuvable ou vous n'avez pas le droit de la supprimer.");
            }
        }
    }

    /** Supprime toutes les lignes de la table {@code note} pour ce médecin. */
    public int deleteAllForMedecin(int medecinId) throws SQLException {
        String t = requireNoteTableRef();
        NoteTableSchema s = schema();
        String sql = "DELETE FROM " + t + " WHERE `" + s.medecinCol + "`=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            return ps.executeUpdate();
        }
    }

    public List<MedecinPatientNote> findByMedecin(int medecinId) throws SQLException {
        List<MedecinPatientNote> list = new ArrayList<>();
        String t = requireNoteTableRef();
        NoteTableSchema s = schema();
        StringBuilder sql = new StringBuilder("SELECT `")
                .append(s.idCol).append("`,`").append(s.medecinCol).append("`,`")
                .append(s.patientCol).append("`,`").append(s.contenuCol).append("`");
        if (s.dateCol != null) {
            sql.append(",`").append(s.dateCol).append("`");
        }
        sql.append(" FROM ").append(t).append(" WHERE `").append(s.medecinCol).append("`=?");
        if (s.dateCol != null) {
            sql.append(" ORDER BY `").append(s.dateCol).append("` DESC");
        } else {
            sql.append(" ORDER BY `").append(s.idCol).append("` DESC");
        }
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql.toString())) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs, s));
                }
            }
        }
        return list;
    }

    private static MedecinPatientNote map(ResultSet rs, NoteTableSchema s) throws SQLException {
        MedecinPatientNote n = new MedecinPatientNote();
        n.setId(rs.getInt(s.idCol));
        n.setMedecinId(rs.getInt(s.medecinCol));
        n.setPatientId(rs.getInt(s.patientCol));
        n.setContenu(rs.getString(s.contenuCol));
        if (s.dateCol != null) {
            Timestamp ts = rs.getTimestamp(s.dateCol);
            if (ts != null) {
                n.setCreatedAt(ts.toLocalDateTime());
            }
        }
        return n;
    }

    /** Vérifie si une table de notes existe ({@code note} ou ancienne {@code medecin_patient_note}). */
    public static boolean tableExists() throws SQLException {
        return noteTableRef() != null;
    }

    private static final class NoteTableSchema {
        final String idCol;
        final String medecinCol;
        final String patientCol;
        final String contenuCol;
        /** Peut être {@code null} (tri par {@link #idCol}). */
        final String dateCol;
        /** Colonnes date à renseigner à l’INSERT (ex. {@code date_creation}, {@code updated_at}). */
        final List<String> insertTimestampCols;
        /** Colonnes date à mettre à jour lors d’un UPDATE. */
        final List<String> updateTimestampCols;

        NoteTableSchema(
                String idCol,
                String medecinCol,
                String patientCol,
                String contenuCol,
                String dateCol,
                List<String> insertTimestampCols,
                List<String> updateTimestampCols) {
            this.idCol = idCol;
            this.medecinCol = medecinCol;
            this.patientCol = patientCol;
            this.contenuCol = contenuCol;
            this.dateCol = dateCol;
            this.insertTimestampCols = insertTimestampCols;
            this.updateTimestampCols = updateTimestampCols;
        }
    }
}
