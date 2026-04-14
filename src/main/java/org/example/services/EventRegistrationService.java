package org.example.services;

import org.example.models.EventRegistration;
import org.example.models.RegistrationStatus;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès table {@code inscrit_events} (pidb) : {@code user_id}, {@code date_inscrit}, {@code statut}, etc.
 */
public class EventRegistrationService implements IService<EventRegistration> {

    private static final String INSERT_SQL = "INSERT INTO `inscrit_events`(`date_inscrit`,`est_inscrit`,`statut`,`user_id`,`evenement_id`) VALUES(?,?,?,?,?)";

    private static final String UPDATE_SQL = "UPDATE `inscrit_events` SET `evenement_id`=?,`user_id`=?,`statut`=?,`est_inscrit`=? WHERE `id`=?";

    @Override
    public void add(EventRegistration r) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(INSERT_SQL)) {
            ps.setDate(1, Date.valueOf(LocalDate.now()));
            ps.setInt(2, estInscritValue(r.getStatut()));
            ps.setString(3, statutToDb(r.getStatut()));
            ps.setInt(4, r.getUtilisateurId());
            ps.setInt(5, r.getEvenementId());
            ps.executeUpdate();
        }
    }

    @Override
    public void update(EventRegistration r) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(UPDATE_SQL)) {
            ps.setInt(1, r.getEvenementId());
            ps.setInt(2, r.getUtilisateurId());
            ps.setString(3, statutToDb(r.getStatut()));
            ps.setInt(4, estInscritValue(r.getStatut()));
            ps.setInt(5, r.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `inscrit_events` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<EventRegistration> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `inscrit_events` WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<EventRegistration> findAll() throws SQLException {
        List<EventRegistration> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `inscrit_events` ORDER BY `date_inscrit` DESC, `id` DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<EventRegistration> listParticipants(int eventId) throws SQLException {
        List<EventRegistration> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `inscrit_events` WHERE evenement_id=?")) {
            ps.setInt(1, eventId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public void setStatus(int registrationId, RegistrationStatus status) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(
            "UPDATE `inscrit_events` SET `statut`=?, `est_inscrit`=? WHERE `id`=?")) {
            ps.setString(1, statutToDb(status));
            ps.setInt(2, estInscritValue(status));
            ps.setInt(3, registrationId);
            ps.executeUpdate();
        }
    }

    private static int estInscritValue(RegistrationStatus s) {
        return s == RegistrationStatus.REFUSE ? 0 : 1;
    }

    private static String statutToDb(RegistrationStatus s) {
        return switch (s) {
            case EN_ATTENTE -> "en_attente";
            case ACCEPTE -> "accepte";
            case REFUSE -> "refuse";
        };
    }

    private static RegistrationStatus statutFromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return RegistrationStatus.EN_ATTENTE;
        }
        switch (raw.trim().toLowerCase()) {
            case "en_attente":
                return RegistrationStatus.EN_ATTENTE;
            case "accepte":
                return RegistrationStatus.ACCEPTE;
            case "refuse":
                return RegistrationStatus.REFUSE;
            default:
                try {
                    return RegistrationStatus.valueOf(raw.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return RegistrationStatus.EN_ATTENTE;
                }
        }
    }

    private EventRegistration map(ResultSet rs) throws SQLException {
        EventRegistration r = new EventRegistration();
        r.setId(rs.getInt("id"));
        r.setEvenementId(rs.getInt("evenement_id"));
        r.setUtilisateurId(rs.getInt("user_id"));
        r.setStatut(statutFromDb(rs.getString("statut")));
        Date di = rs.getDate("date_inscrit");
        r.setDateInscription(di != null ? di.toLocalDate().atStartOfDay() : LocalDateTime.now());
        return r;
    }
}
