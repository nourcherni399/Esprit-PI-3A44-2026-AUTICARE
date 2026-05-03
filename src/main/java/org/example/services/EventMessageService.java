package org.example.services;

import org.example.models.EventMessage;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventMessageService {

    public List<EventMessage> listForUserAndEvent(int evenementId, int userId) throws SQLException {
        String sql = "SELECT id, evenement_id, expediteur_user_id, destinataire_user_id, corps, date_envoi FROM evenement_messages "
                + "WHERE evenement_id=? AND (expediteur_user_id=? OR destinataire_user_id=?) ORDER BY date_envoi ASC";
        List<EventMessage> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, evenementId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public void addMessage(int evenementId, int expediteurUserId, String corps) throws SQLException {
        addMessage(evenementId, expediteurUserId, null, corps);
    }

    public void addMessage(int evenementId, int expediteurUserId, Integer destinataireUserId, String corps) throws SQLException {
        String sql = "INSERT INTO evenement_messages(evenement_id, expediteur_user_id, destinataire_user_id, corps, date_envoi) "
                + "VALUES(?,?,?,?,NOW())";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, evenementId);
            ps.setInt(2, expediteurUserId);
            if (destinataireUserId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, destinataireUserId);
            }
            ps.setString(4, corps);
            ps.executeUpdate();
        }
    }

    public List<Integer> listParticipantIdsForEvent(int evenementId) throws SQLException {
        String sql = "SELECT DISTINCT expediteur_user_id AS participant_id FROM evenement_messages "
                + "WHERE evenement_id=? AND destinataire_user_id IS NULL "
                + "UNION "
                + "SELECT DISTINCT destinataire_user_id AS participant_id FROM evenement_messages "
                + "WHERE evenement_id=? AND destinataire_user_id IS NOT NULL";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, evenementId);
            ps.setInt(2, evenementId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getInt("participant_id"));
            }
        }
        return ids;
    }

    public List<EventMessage> listConversationForEventAndParticipant(int evenementId, int participantUserId) throws SQLException {
        String sql = "SELECT id, evenement_id, expediteur_user_id, destinataire_user_id, corps, date_envoi "
                + "FROM evenement_messages "
                + "WHERE evenement_id=? AND (expediteur_user_id=? OR destinataire_user_id=?) "
                + "ORDER BY date_envoi ASC, id ASC";
        List<EventMessage> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, evenementId);
            ps.setInt(2, participantUserId);
            ps.setInt(3, participantUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public Map<Integer, Integer> countByEventIds(List<Integer> eventIds) throws SQLException {
        Map<Integer, Integer> counts = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return counts;
        }
        StringBuilder inSql = new StringBuilder();
        for (int i = 0; i < eventIds.size(); i++) {
            if (i > 0) {
                inSql.append(",");
            }
            inSql.append("?");
        }
        String sql = "SELECT evenement_id, COUNT(*) AS total FROM evenement_messages "
                + "WHERE evenement_id IN (" + inSql + ") GROUP BY evenement_id";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < eventIds.size(); i++) {
                ps.setInt(i + 1, eventIds.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                counts.put(rs.getInt("evenement_id"), rs.getInt("total"));
            }
        }
        return counts;
    }

    public boolean deleteOwnMessage(int messageId, int senderUserId) throws SQLException {
        String sql = "DELETE FROM evenement_messages WHERE id=? AND expediteur_user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, messageId);
            ps.setInt(2, senderUserId);
            return ps.executeUpdate() > 0;
        }
    }

    private static EventMessage map(ResultSet rs) throws SQLException {
        EventMessage m = new EventMessage();
        m.setId(rs.getInt("id"));
        m.setEvenementId(rs.getInt("evenement_id"));
        m.setExpediteurUserId(rs.getInt("expediteur_user_id"));
        int dest = rs.getInt("destinataire_user_id");
        m.setDestinataireUserId(rs.wasNull() ? null : dest);
        m.setCorps(rs.getString("corps"));
        Timestamp t = rs.getTimestamp("date_envoi");
        m.setDateEnvoi(t != null ? t.toLocalDateTime() : null);
        return m;
    }
}
