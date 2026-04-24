package org.example.services;

import org.example.models.UserNotificationItem;
import org.example.utils.MyDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class UserNotificationService {

    public static final String TYPE_EVENT_REGISTRATION_ACCEPTED = "EVENT_REGISTRATION_ACCEPTED";
    public static final String TYPE_EVENT_REGISTRATION_REFUSED = "EVENT_REGISTRATION_REFUSED";
    /** Inscription enregistrée, en attente de validation par l’admin. */
    public static final String TYPE_EVENT_REGISTRATION_PENDING = "EVENT_REGISTRATION_PENDING";
    public static final String TYPE_EVENT_MESSAGE_REPLY = "EVENT_MESSAGE_REPLY";
    /** Demande de RDV acceptée par le médecin (passage en planifié). */
    public static final String TYPE_RDV_ACCEPTED = "RDV_ACCEPTED";
    /** Demande de RDV refusée / annulée par le médecin (refus de la demande). */
    public static final String TYPE_RDV_REFUSED = "RDV_REFUSED";
    /** Rendez-vous déjà planifié annulé par le médecin. */
    public static final String TYPE_RDV_CANCELLED = "RDV_CANCELLED";

    public void addNotification(int userId, String typeCode, Integer eventId, String summary) throws SQLException {
        String sql = "INSERT INTO notifications_user(utilisateur_id, type_code, evenement_id, resume, lu, date_creation) "
                + "VALUES(?,?,?,?,0,NOW())";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, typeCode);
            if (eventId == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, eventId);
            }
            ps.setString(4, summary != null ? summary : "");
            ps.executeUpdate();
        }
    }

    public int countUnreadForUser(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM notifications_user WHERE utilisateur_id=? AND lu=0";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        }
    }

    public List<UserNotificationItem> listLatestForUser(int userId, int limit) throws SQLException {
        int capped = Math.max(1, Math.min(50, limit));
        String sql = "SELECT id, utilisateur_id, type_code, evenement_id, resume, lu, date_creation "
                + "FROM notifications_user WHERE utilisateur_id=? ORDER BY lu ASC, date_creation DESC LIMIT ?";
        List<UserNotificationItem> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, capped);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<UserNotificationItem> listAllForUser(int userId) throws SQLException {
        String sql = "SELECT id, utilisateur_id, type_code, evenement_id, resume, lu, date_creation "
                + "FROM notifications_user WHERE utilisateur_id=? ORDER BY date_creation DESC";
        List<UserNotificationItem> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public void markAsRead(int notificationId) throws SQLException {
        String sql = "UPDATE notifications_user SET lu=1 WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.executeUpdate();
        }
    }

    private static UserNotificationItem map(ResultSet rs) throws SQLException {
        UserNotificationItem item = new UserNotificationItem();
        item.setId(rs.getInt("id"));
        item.setUtilisateurId(rs.getInt("utilisateur_id"));
        item.setTypeCode(rs.getString("type_code"));
        int evId = rs.getInt("evenement_id");
        item.setEvenementId(rs.wasNull() ? null : evId);
        item.setResume(rs.getString("resume"));
        item.setLu(rs.getBoolean("lu"));
        Timestamp t = rs.getTimestamp("date_creation");
        item.setDateCreation(t != null ? t.toLocalDateTime() : null);
        return item;
    }
}
