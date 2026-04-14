package org.example.services;

import org.example.models.Event;
import org.example.models.EventStatus;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès table {@code evenement} (dump pidb) : {@code title}, {@code date_event}, {@code heure_debut}, etc.
 * Les champs UI {@code placesMax} / {@code statut} ne sont pas persistés en base (absents du schéma).
 */
public class EventService implements IService<Event> {

    private static final String INSERT_SQL = "INSERT INTO `evenement`(`title`,`description`,`date_event`,`heure_debut`,`heure_fin`,`lieu`,`latitude`,`longitude`) "
        + "VALUES(?,?,?,?,?,?,?,?)";

    private static final String UPDATE_SQL = "UPDATE `evenement` SET `title`=?,`description`=?,`date_event`=?,`heure_debut`=?,`heure_fin`=?,`lieu`=?,`latitude`=?,`longitude`=? WHERE `id`=?";

    @Override
    public void add(Event e) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(INSERT_SQL)) {
            fill(ps, e, false);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Event e) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(UPDATE_SQL)) {
            fill(ps, e, true);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `evenement` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Event> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `evenement` WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Event> findAll() throws SQLException {
        List<Event> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `evenement` ORDER BY `date_event` DESC, `heure_debut` DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private void fill(PreparedStatement ps, Event e, boolean withId) throws SQLException {
        ps.setString(1, e.getTitre());
        ps.setString(2, e.getDescription());
        LocalDate d = e.getDateDebut().toLocalDate();
        ps.setDate(3, Date.valueOf(d));
        ps.setTime(4, Time.valueOf(e.getDateDebut().toLocalTime()));
        ps.setTime(5, Time.valueOf(e.getDateFin().toLocalTime()));
        if (e.getLieu() == null || e.getLieu().isBlank()) {
            ps.setNull(6, Types.VARCHAR);
        } else {
            ps.setString(6, e.getLieu());
        }
        if (e.getLatitude() == null) {
            ps.setNull(7, Types.DOUBLE);
        } else {
            ps.setDouble(7, e.getLatitude());
        }
        if (e.getLongitude() == null) {
            ps.setNull(8, Types.DOUBLE);
        } else {
            ps.setDouble(8, e.getLongitude());
        }
        if (withId) {
            ps.setInt(9, e.getId());
        }
    }

    private Event map(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getInt("id"));
        e.setTitre(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        Date de = rs.getDate("date_event");
        Time hb = rs.getTime("heure_debut");
        Time hf = rs.getTime("heure_fin");
        LocalDate day = de != null ? de.toLocalDate() : LocalDate.now();
        LocalTime start = hb != null ? hb.toLocalTime() : LocalTime.MIDNIGHT;
        LocalTime end = hf != null ? hf.toLocalTime() : start;
        e.setDateDebut(LocalDateTime.of(day, start));
        e.setDateFin(LocalDateTime.of(day, end));
        e.setLieu(rs.getString("lieu"));
        double lat = rs.getDouble("latitude");
        if (!rs.wasNull()) {
            e.setLatitude(lat);
        }
        double lng = rs.getDouble("longitude");
        if (!rs.wasNull()) {
            e.setLongitude(lng);
        }
        e.setPlacesMax(0);
        e.setStatut(EventStatus.PUBLIE);
        return e;
    }
}
