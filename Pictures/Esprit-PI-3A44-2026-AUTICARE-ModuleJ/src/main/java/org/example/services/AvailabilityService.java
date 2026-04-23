package org.example.services;

import org.example.models.Availability;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AvailabilityService implements IService<Availability> {

    private static final String[] DEBUT_COLUMN_CANDIDATES = {
            "debut",
            "date_debut",
            "heure_debut",
            "start_at",
            "date_heure_debut",
            "datetime_debut"
    };

    private static final String[] FIN_COLUMN_CANDIDATES = {
            "fin",
            "date_fin",
            "heure_fin",
            "end_at",
            "date_heure_fin",
            "datetime_fin"
    };

    /**
     * Colonne jour seul (schémas Symfony / MySQL avec {@code date} + heures séparées) : obligatoire côté BDD mais absent du modèle Java.
     */
    private static final String[] DATE_ONLY_COLUMN_CANDIDATES = {
            "date",
            "jour",
            "slot_date",
            "date_jour",
            "dispo_date"
    };

    private static volatile String cachedDebutColumn;
    private static volatile String cachedFinColumn;
    /** Nom réel de la colonne « jour seul », ou {@code null} si la table n’en a pas. */
    private static volatile String cachedDateOnlyColumn;
    private static volatile int cachedDebutSqlType = Types.TIMESTAMP;
    private static volatile int cachedFinSqlType = Types.TIMESTAMP;

    private static final String MSG_DUPLICATE =
            "Une disponibilité existe déjà à la même date et la même heure de début. "
                    + "Modifiez l’heure de début ou supprimez l’ancien créneau.";

    @Override
    public void add(Availability a) throws SQLException {
        if (hasExistingSlotSameStart(a.getMedecinId(), a.getDebut(), null)) {
            throw new SQLException(MSG_DUPLICATE);
        }
        String d = debutColumn();
        String f = finColumn();
        String dayCol = dateOnlyColumn();
        int td = debutSqlType();
        int tf = finSqlType();
        LocalDate day = a.getDebut() != null ? a.getDebut().toLocalDate() : null;
        String sql;
        if (dayCol != null) {
            sql = "INSERT INTO disponibilite(medecin_id," + q(dayCol) + "," + q(d) + "," + q(f) + ") VALUES(?,?,?,?)";
        } else {
            sql = "INSERT INTO disponibilite(medecin_id," + q(d) + "," + q(f) + ") VALUES(?,?,?)";
        }
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, a.getMedecinId());
            if (dayCol != null) {
                bindDateOnly(ps, 2, day);
                bindEndpoint(ps, 3, a.getDebut(), td);
                bindEndpoint(ps, 4, a.getFin(), tf);
            } else {
                bindEndpoint(ps, 2, a.getDebut(), td);
                bindEndpoint(ps, 3, a.getFin(), tf);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Availability a) throws SQLException {
        if (hasExistingSlotSameStart(a.getMedecinId(), a.getDebut(), a.getId())) {
            throw new SQLException(MSG_DUPLICATE);
        }
        String d = debutColumn();
        String f = finColumn();
        String dayCol = dateOnlyColumn();
        int td = debutSqlType();
        int tf = finSqlType();
        LocalDate day = a.getDebut() != null ? a.getDebut().toLocalDate() : null;
        String sql;
        if (dayCol != null) {
            sql = "UPDATE disponibilite SET medecin_id=?," + q(dayCol) + "=?," + q(d) + "=?," + q(f) + "=? WHERE id=?";
        } else {
            sql = "UPDATE disponibilite SET medecin_id=?," + q(d) + "=?," + q(f) + "=? WHERE id=?";
        }
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, a.getMedecinId());
            if (dayCol != null) {
                bindDateOnly(ps, 2, day);
                bindEndpoint(ps, 3, a.getDebut(), td);
                bindEndpoint(ps, 4, a.getFin(), tf);
                ps.setInt(5, a.getId());
            } else {
                bindEndpoint(ps, 2, a.getDebut(), td);
                bindEndpoint(ps, 3, a.getFin(), tf);
                ps.setInt(4, a.getId());
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM disponibilite WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Availability> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM disponibilite WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Availability> findAll() throws SQLException {
        String d = debutColumn();
        List<Availability> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM disponibilite ORDER BY " + q(d))) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<Availability> findByDoctor(int doctorId) throws SQLException {
        String d = debutColumn();
        List<Availability> list = new ArrayList<>();
        String sql = "SELECT * FROM disponibilite WHERE medecin_id=? ORDER BY " + q(d);
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Indique si une disponibilité du médecin a déjà le même instant de début ({@code debut} : date + heure).
     * Évite plusieurs cartes « 09:00 » le même jour côté prise de RDV. Un 09:00 un autre jour n’est pas un doublon.
     *
     * @param excludeAvailabilityId identifiant à exclure (ligne en cours de modification), ou {@code null}
     */
    public boolean hasExistingSlotSameStart(int medecinId, LocalDateTime debut, Integer excludeAvailabilityId)
            throws SQLException {
        if (debut == null) {
            return false;
        }
        for (Availability other : findByDoctor(medecinId)) {
            if (excludeAvailabilityId != null && other.getId() == excludeAvailabilityId) {
                continue;
            }
            if (other.getDebut() != null && debut.equals(other.getDebut())) {
                return true;
            }
        }
        return false;
    }

    private Availability map(ResultSet rs) throws SQLException {
        String d = debutColumn();
        String f = finColumn();
        String dayCol = dateOnlyColumn();
        int td = debutSqlType();
        int tf = finSqlType();
        Availability a = new Availability();
        a.setId(rs.getInt("id"));
        a.setMedecinId(rs.getInt("medecin_id"));
        LocalDate partDate = null;
        if (dayCol != null) {
            partDate = readSqlLocalDate(rs, dayCol);
        }
        LocalDateTime debutLdt = readEndpoint(rs, d, partDate, td);
        LocalDateTime finLdt = readEndpoint(rs, f, partDate, tf);
        if (debutLdt != null) {
            a.setDebut(debutLdt);
        }
        if (finLdt != null) {
            a.setFin(finLdt);
        }
        return a;
    }

    private static LocalDate readSqlLocalDate(ResultSet rs, String columnLabel) throws SQLException {
        LocalDate d = rs.getObject(columnLabel, LocalDate.class);
        if (d != null) {
            return d;
        }
        Date sqlDay = rs.getDate(columnLabel);
        return sqlDay != null ? sqlDay.toLocalDate() : null;
    }

    /**
     * Lit début ou fin : si la colonne est de type {@code TIME} (ou équivalent), fusionne avec {@code partDate}
     * quand la table a une colonne {@code date} séparée — sinon le calendrier ne voit pas le bon jour.
     * Si {@code partDate} est fourni et diffère de la date lue dans un {@code TIMESTAMP/DATETIME},
     * on retient la colonne {@code date} (jour canonique côté Symfony) + l’heure lue (évite décalages fuseau).
     */
    private static LocalDateTime readEndpoint(ResultSet rs, String col, LocalDate partDate, int sqlType) throws SQLException {
        if (col == null) {
            return null;
        }
        if (sqlType == Types.TIME) {
            Time t = rs.getTime(col);
            if (t == null || partDate == null) {
                return null;
            }
            return LocalDateTime.of(partDate, t.toLocalTime());
        }
        LocalDateTime ldt = rs.getObject(col, LocalDateTime.class);
        if (ldt == null) {
            Timestamp ts = rs.getTimestamp(col);
            if (ts == null) {
                return null;
            }
            ldt = ts.toLocalDateTime();
        }
        if (partDate != null) {
            if (isTimeOnlyPlaceholderDate(ldt.toLocalDate()) || !partDate.equals(ldt.toLocalDate())) {
                return LocalDateTime.of(partDate, ldt.toLocalTime());
            }
        }
        return ldt;
    }

    /** Date « factice » souvent renvoyée par le driver JDBC pour une colonne MySQL {@code TIME} lue en {@code Timestamp}. */
    private static boolean isTimeOnlyPlaceholderDate(LocalDate date) {
        return LocalDate.of(1970, 1, 1).equals(date);
    }

    private static void bindEndpoint(PreparedStatement ps, int index, LocalDateTime ldt, int sqlType) throws SQLException {
        if (ldt == null) {
            ps.setNull(index, sqlType == Types.TIME ? Types.TIME : Types.TIMESTAMP);
            return;
        }
        if (sqlType == Types.TIME) {
            ps.setTime(index, Time.valueOf(ldt.toLocalTime()));
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(ldt));
        }
    }

    private static String debutColumn() throws SQLException {
        ensureDisponibiliteColumns();
        return cachedDebutColumn;
    }

    private static String finColumn() throws SQLException {
        ensureDisponibiliteColumns();
        return cachedFinColumn;
    }

    private static void ensureDisponibiliteColumns() throws SQLException {
        if (cachedDebutColumn != null && cachedFinColumn != null) {
            return;
        }
        synchronized (AvailabilityService.class) {
            if (cachedDebutColumn != null && cachedFinColumn != null) {
                return;
            }
            Connection conn = MyDatabase.getConnection();
            Map<String, String> lowerToActual = new HashMap<>();
            DatabaseMetaData md = conn.getMetaData();
            String catalog = conn.getCatalog();
            readColumnNames(md, catalog, "disponibilite", lowerToActual);
            if (lowerToActual.isEmpty() && catalog != null) {
                readColumnNames(md, null, "disponibilite", lowerToActual);
            }
            Set<String> lower = lowerToActual.keySet();
            String debut = pickActual(lowerToActual, DEBUT_COLUMN_CANDIDATES);
            String fin = pickActual(lowerToActual, FIN_COLUMN_CANDIDATES);
            if (debut == null || fin == null) {
                throw new SQLException(
                        "Table disponibilite : colonnes début/fin introuvables (attendu parmi "
                                + String.join(", ", DEBUT_COLUMN_CANDIDATES)
                                + " / "
                                + String.join(", ", FIN_COLUMN_CANDIDATES)
                                + "). Colonnes trouvées : "
                                + lower);
            }
            cachedDebutColumn = debut;
            cachedFinColumn = fin;
            cachedDateOnlyColumn = pickDateOnlyActual(lowerToActual, debut, fin);
            cachedDebutSqlType = readColumnDataType(md, catalog, "disponibilite", debut);
            cachedFinSqlType = readColumnDataType(md, catalog, "disponibilite", fin);
            if (cachedDebutSqlType == Types.NULL) {
                cachedDebutSqlType = Types.TIMESTAMP;
            }
            if (cachedFinSqlType == Types.NULL) {
                cachedFinSqlType = Types.TIMESTAMP;
            }
        }
    }

    private static int readColumnDataType(DatabaseMetaData md, String catalog, String table, String columnActual)
            throws SQLException {
        int t = readColumnDataTypeOnce(md, catalog, table, columnActual);
        if (t != Types.NULL) {
            return t;
        }
        return readColumnDataTypeOnce(md, null, table, columnActual);
    }

    private static int readColumnDataTypeOnce(DatabaseMetaData md, String catalog, String table, String columnActual)
            throws SQLException {
        try (ResultSet crs = md.getColumns(catalog, null, table, "%")) {
            while (crs.next()) {
                String n = crs.getString("COLUMN_NAME");
                if (n != null && n.equalsIgnoreCase(columnActual)) {
                    return crs.getInt("DATA_TYPE");
                }
            }
        }
        return Types.NULL;
    }

    private static int debutSqlType() throws SQLException {
        ensureDisponibiliteColumns();
        return cachedDebutSqlType;
    }

    private static int finSqlType() throws SQLException {
        ensureDisponibiliteColumns();
        return cachedFinSqlType;
    }

    private static String pickDateOnlyActual(Map<String, String> lowerToActual, String debutCol, String finCol) {
        for (String cand : DATE_ONLY_COLUMN_CANDIDATES) {
            String key = cand.toLowerCase(Locale.ROOT);
            if (!lowerToActual.containsKey(key)) {
                continue;
            }
            String actual = lowerToActual.get(key);
            if (actual.equalsIgnoreCase(debutCol) || actual.equalsIgnoreCase(finCol)) {
                continue;
            }
            return actual;
        }
        return null;
    }

    private static String dateOnlyColumn() throws SQLException {
        ensureDisponibiliteColumns();
        return cachedDateOnlyColumn;
    }

    private static void bindDateOnly(PreparedStatement ps, int index, LocalDate day) throws SQLException {
        if (day == null) {
            ps.setNull(index, Types.DATE);
            return;
        }
        ps.setDate(index, Date.valueOf(day));
    }

    private static String pickActual(Map<String, String> lowerToActual, String[] candidates) {
        for (String cand : candidates) {
            String key = cand.toLowerCase(Locale.ROOT);
            if (lowerToActual.containsKey(key)) {
                return lowerToActual.get(key);
            }
        }
        return null;
    }

    private static void readColumnNames(DatabaseMetaData md, String catalog, String table,
                                        Map<String, String> lowerToActual) throws SQLException {
        try (ResultSet crs = md.getColumns(catalog, null, table, "%")) {
            while (crs.next()) {
                String name = crs.getString("COLUMN_NAME");
                if (name != null) {
                    lowerToActual.put(name.toLowerCase(Locale.ROOT), name);
                }
            }
        }
    }

    private static String q(String ident) {
        if (ident == null || ident.isEmpty()) {
            return "``";
        }
        return "`" + ident.replace("`", "") + "`";
    }
}
