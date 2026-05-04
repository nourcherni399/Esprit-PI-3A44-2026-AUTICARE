package org.example.services;

import org.example.models.AdminNotificationItem;
import org.example.utils.MyDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Notifications destinées à l’équipe admin (inscriptions, messages événement).
 * L’interface admin pourra les lire / marquer comme lues plus tard.
 */
public class AdminNotificationService {

    public static final String TYPE_INSCRIPTION_DEMANDE = "INSCRIPTION_DEMANDE";
    public static final String TYPE_MESSAGE_EVENEMENT = "MESSAGE_EVENEMENT";

    public void addNotification(String typeCode, Integer evenementId, int expediteurUserId, String resume)
            throws SQLException {
        String sql = "INSERT INTO notifications_admin(type_code, evenement_id, expediteur_user_id, resume, lu, date_creation) "
                + "VALUES(?,?,?,?,0,NOW())";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, typeCode);
            if (evenementId != null) {
                ps.setInt(2, evenementId);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setInt(3, expediteurUserId);
            ps.setString(4, truncate(resume, 500));
            ps.executeUpdate();
        }
    }

    public int countUnread() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM notifications_admin WHERE lu = 0";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
        }
        return 0;
    }

    public List<AdminNotificationItem> listLatest(int limit) throws SQLException {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        String sql = "SELECT n.id, n.type_code, n.evenement_id, n.expediteur_user_id, n.resume, n.lu, n.date_creation, "
                + "u.prenom, u.nom, e.titre AS evenement_titre "
                + "FROM notifications_admin n "
                + "LEFT JOIN `user` u ON u.id = n.expediteur_user_id "
                + "LEFT JOIN evenements e ON e.id = n.evenement_id "
                + "ORDER BY n.lu ASC, n.date_creation DESC "
                + "LIMIT ?";
        List<AdminNotificationItem> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, cappedLimit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public void markAsRead(int notificationId) throws SQLException {
        String sql = "UPDATE notifications_admin SET lu = 1 WHERE id = ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.executeUpdate();
        }
    }

    private static AdminNotificationItem map(ResultSet rs) throws SQLException {
        AdminNotificationItem item = new AdminNotificationItem();
        item.setId(rs.getInt("id"));
        item.setTypeCode(rs.getString("type_code"));
        int evId = rs.getInt("evenement_id");
        item.setEvenementId(rs.wasNull() ? null : evId);
        item.setExpediteurUserId(rs.getInt("expediteur_user_id"));
        item.setResume(rs.getString("resume"));
        item.setLu(rs.getBoolean("lu"));
        Timestamp ts = rs.getTimestamp("date_creation");
        item.setDateCreation(ts != null ? ts.toLocalDateTime() : null);
        String prenom = rs.getString("prenom");
        String nom = rs.getString("nom");
        String fullName = ((prenom != null ? prenom : "") + " " + (nom != null ? nom : "")).trim();
        item.setExpediteurNom(fullName.isBlank() ? "Utilisateur" : fullName);
        item.setEvenementTitre(rs.getString("evenement_titre"));
        return item;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
