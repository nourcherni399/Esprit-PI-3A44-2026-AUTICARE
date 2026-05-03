package org.example.services;

import org.example.models.EventRegistration;
import org.example.models.RegistrationStatus;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventRegistrationService implements IService<EventRegistration> {
    @Override
    public void add(EventRegistration r) throws SQLException {
        String sql = "INSERT INTO inscriptions_evenement(evenement_id,utilisateur_id,statut,date_inscription) VALUES(?,?,?,NOW())";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, r.getEvenementId());
            ps.setInt(2, r.getUtilisateurId());
            ps.setString(3, r.getStatut().name());
            ps.executeUpdate();
        }
    }

    @Override
    public void update(EventRegistration r) throws SQLException {
        String sql = "UPDATE inscriptions_evenement SET evenement_id=?,utilisateur_id=?,statut=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, r.getEvenementId());
            ps.setInt(2, r.getUtilisateurId());
            ps.setString(3, r.getStatut().name());
            ps.setInt(4, r.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM inscriptions_evenement WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<EventRegistration> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM inscriptions_evenement WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        }
    }

    @Override
    public List<EventRegistration> findAll() throws SQLException {
        List<EventRegistration> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM inscriptions_evenement ORDER BY date_inscription DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public boolean isUserRegistered(int eventId, int userId) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(
                "SELECT 1 FROM inscriptions_evenement WHERE evenement_id=? AND utilisateur_id=? LIMIT 1")) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    public Optional<EventRegistration> findByEventAndUser(int eventId, int userId) throws SQLException {
        String sql = "SELECT * FROM inscriptions_evenement WHERE evenement_id=? AND utilisateur_id=? LIMIT 1";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    public List<EventRegistration> listParticipants(int eventId) throws SQLException {
        List<EventRegistration> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM inscriptions_evenement WHERE evenement_id=?")) {
            ps.setInt(1, eventId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<EventRegistration> findByUserId(int userId) throws SQLException {
        List<EventRegistration> list = new ArrayList<>();
        String sql = "SELECT * FROM inscriptions_evenement WHERE utilisateur_id=? ORDER BY date_inscription DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public void setStatus(int registrationId, RegistrationStatus status) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("UPDATE inscriptions_evenement SET statut=? WHERE id=?")) {
            ps.setString(1, status.name());
            ps.setInt(2, registrationId);
            ps.executeUpdate();
        }
    }

    private EventRegistration map(ResultSet rs) throws SQLException {
        EventRegistration r = new EventRegistration();
        r.setId(rs.getInt("id"));
        r.setEvenementId(rs.getInt("evenement_id"));
        r.setUtilisateurId(rs.getInt("utilisateur_id"));
        r.setStatut(RegistrationStatus.valueOf(rs.getString("statut")));
        r.setDateInscription(rs.getTimestamp("date_inscription").toLocalDateTime());
        return r;
    }
}
