package org.example.services;

import org.example.models.Availability;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AvailabilityService implements IService<Availability> {
    @Override
    public void add(Availability a) throws SQLException {
        String sql = "INSERT INTO disponibilites(medecin_id,debut,fin) VALUES(?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, a.getMedecinId());
            ps.setTimestamp(2, Timestamp.valueOf(a.getDebut()));
            ps.setTimestamp(3, Timestamp.valueOf(a.getFin()));
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Availability a) throws SQLException {
        String sql = "UPDATE disponibilites SET medecin_id=?,debut=?,fin=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, a.getMedecinId());
            ps.setTimestamp(2, Timestamp.valueOf(a.getDebut()));
            ps.setTimestamp(3, Timestamp.valueOf(a.getFin()));
            ps.setInt(4, a.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM disponibilites WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Availability> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM disponibilites WHERE id=?")) {
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
             ResultSet rs = st.executeQuery("SELECT * FROM disponibilites ORDER BY debut")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Availability> findByDoctor(int doctorId) throws SQLException {
        List<Availability> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM disponibilites WHERE medecin_id=? ORDER BY debut")) {
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
}
