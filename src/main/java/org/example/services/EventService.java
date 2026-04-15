package org.example.services;

import org.example.models.Event;
import org.example.models.EventStatus;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventService implements IService<Event> {
    @Override
    public void add(Event e) throws SQLException {
        String sql = "INSERT INTO evenements(titre,description,date_debut,date_fin,lieu,latitude,longitude,places_max,statut) VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, e, false);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Event e) throws SQLException {
        String sql = "UPDATE evenements SET titre=?,description=?,date_debut=?,date_fin=?,lieu=?,latitude=?,longitude=?,places_max=?,statut=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, e, true);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM evenements WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Event> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM evenements WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        }
    }

    @Override
    public List<Event> findAll() throws SQLException {
        List<Event> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM evenements ORDER BY date_debut DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private void fill(PreparedStatement ps, Event e, boolean withId) throws SQLException {
        ps.setString(1, e.getTitre());
        ps.setString(2, e.getDescription());
        ps.setTimestamp(3, Timestamp.valueOf(e.getDateDebut()));
        ps.setTimestamp(4, Timestamp.valueOf(e.getDateFin()));
        ps.setString(5, e.getLieu());
        if (e.getLatitude() == null) ps.setNull(6, Types.DOUBLE); else ps.setDouble(6, e.getLatitude());
        if (e.getLongitude() == null) ps.setNull(7, Types.DOUBLE); else ps.setDouble(7, e.getLongitude());
        ps.setInt(8, e.getPlacesMax());
        ps.setString(9, e.getStatut().name());
        if (withId) ps.setInt(10, e.getId());
    }

    private Event map(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getInt("id"));
        e.setTitre(rs.getString("titre"));
        e.setDescription(rs.getString("description"));
        e.setDateDebut(rs.getTimestamp("date_debut").toLocalDateTime());
        e.setDateFin(rs.getTimestamp("date_fin").toLocalDateTime());
        e.setLieu(rs.getString("lieu"));
        double lat = rs.getDouble("latitude");
        if (!rs.wasNull()) e.setLatitude(lat);
        double lng = rs.getDouble("longitude");
        if (!rs.wasNull()) e.setLongitude(lng);
        e.setPlacesMax(rs.getInt("places_max"));
        e.setStatut(EventStatus.valueOf(rs.getString("statut")));
        return e;
    }
}
