package org.example.services;

import org.example.models.Availability;
import org.example.utils.MyDatabase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AvailabilityService implements IService<Availability> {
    private static final String TABLE_NAME = "disponibilite";
    private AvailabilitySchema cachedSchema;

    private enum AvailabilitySchema {
        MODERN_DEBUT_FIN,
        LEGACY_DATE_HEURES
    }

    @Override
    public void add(Availability a) throws SQLException {
        String sql;
        AvailabilitySchema schema = resolveSchema();
        if (schema == AvailabilitySchema.MODERN_DEBUT_FIN) {
            sql = "INSERT INTO " + TABLE_NAME + "(medecin_id,debut,fin) VALUES(?,?,?)";
        } else {
            sql = "INSERT INTO " + TABLE_NAME + "(medecin_id,date,heure_debut,heure_fin,duree,est_dispo) VALUES(?,?,?,?,?,1)";
        }
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            bindInsert(ps, a, schema);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Availability a) throws SQLException {
        String sql;
        AvailabilitySchema schema = resolveSchema();
        if (schema == AvailabilitySchema.MODERN_DEBUT_FIN) {
            sql = "UPDATE " + TABLE_NAME + " SET medecin_id=?,debut=?,fin=? WHERE id=?";
        } else {
            sql = "UPDATE " + TABLE_NAME + " SET medecin_id=?,date=?,heure_debut=?,heure_fin=?,duree=? WHERE id=?";
        }
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            bindUpdate(ps, a, schema);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Availability> findById(int id) throws SQLException {
        String sql = selectSql("WHERE id=?");
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        }
    }

    @Override
    public List<Availability> findAll() throws SQLException {
        List<Availability> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(selectSql("ORDER BY debut"))) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Availability> findByDoctor(int doctorId) throws SQLException {
        List<Availability> list = new ArrayList<>();
        String sql = selectSql("WHERE medecin_id=? ORDER BY debut");
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private Availability map(ResultSet rs) throws SQLException {
        Availability a = new Availability();
        a.setId(rs.getInt("id"));
        a.setMedecinId(rs.getInt("medecin_id"));
        a.setDebut(rs.getTimestamp("debut").toLocalDateTime());
        a.setFin(rs.getTimestamp("fin").toLocalDateTime());
        return a;
    }

    private String selectSql(String tail) throws SQLException {
        AvailabilitySchema schema = resolveSchema();
        if (schema == AvailabilitySchema.MODERN_DEBUT_FIN) {
            return "SELECT id, medecin_id, debut, fin FROM " + TABLE_NAME + " " + tail;
        }
        return "SELECT id, medecin_id, TIMESTAMP(`date`, heure_debut) AS debut, TIMESTAMP(`date`, heure_fin) AS fin "
                + "FROM " + TABLE_NAME + " " + tail;
    }

    private void bindInsert(PreparedStatement ps, Availability a, AvailabilitySchema schema) throws SQLException {
        if (schema == AvailabilitySchema.MODERN_DEBUT_FIN) {
            ps.setInt(1, a.getMedecinId());
            ps.setTimestamp(2, Timestamp.valueOf(a.getDebut()));
            ps.setTimestamp(3, Timestamp.valueOf(a.getFin()));
            return;
        }
        LocalDateTime debut = a.getDebut();
        LocalDateTime fin = a.getFin();
        LocalDate date = debut.toLocalDate();
        LocalTime heureDebut = debut.toLocalTime();
        LocalTime heureFin = fin.toLocalTime();
        int duree = Math.max(0, (int) ChronoUnit.MINUTES.between(debut, fin));

        ps.setInt(1, a.getMedecinId());
        ps.setDate(2, Date.valueOf(date));
        ps.setTime(3, Time.valueOf(heureDebut));
        ps.setTime(4, Time.valueOf(heureFin));
        ps.setInt(5, duree);
    }

    private void bindUpdate(PreparedStatement ps, Availability a, AvailabilitySchema schema) throws SQLException {
        if (schema == AvailabilitySchema.MODERN_DEBUT_FIN) {
            ps.setInt(1, a.getMedecinId());
            ps.setTimestamp(2, Timestamp.valueOf(a.getDebut()));
            ps.setTimestamp(3, Timestamp.valueOf(a.getFin()));
            ps.setInt(4, a.getId());
            return;
        }
        LocalDateTime debut = a.getDebut();
        LocalDateTime fin = a.getFin();
        LocalDate date = debut.toLocalDate();
        LocalTime heureDebut = debut.toLocalTime();
        LocalTime heureFin = fin.toLocalTime();
        int duree = Math.max(0, (int) ChronoUnit.MINUTES.between(debut, fin));

        ps.setInt(1, a.getMedecinId());
        ps.setDate(2, Date.valueOf(date));
        ps.setTime(3, Time.valueOf(heureDebut));
        ps.setTime(4, Time.valueOf(heureFin));
        ps.setInt(5, duree);
        ps.setInt(6, a.getId());
    }

    private AvailabilitySchema resolveSchema() throws SQLException {
        if (cachedSchema != null) {
            return cachedSchema;
        }
        Connection c = MyDatabase.getConnection();
        boolean hasDebut = hasColumn(c, TABLE_NAME, "debut");
        boolean hasFin = hasColumn(c, TABLE_NAME, "fin");
        if (hasDebut && hasFin) {
            cachedSchema = AvailabilitySchema.MODERN_DEBUT_FIN;
            return cachedSchema;
        }
        cachedSchema = AvailabilitySchema.LEGACY_DATE_HEURES;
        return cachedSchema;
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getColumns(c.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = md.getColumns(c.getCatalog(), null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }
}