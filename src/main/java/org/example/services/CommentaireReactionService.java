package org.example.services;

import org.example.models.CommentaireReaction;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommentaireReactionService {

    /**
     * Retourne le type de réaction de l'utilisateur sur un commentaire,
     * ou null s'il n'a pas encore réagi.
     */
    public String getUserReaction(int commentaireId, int userId) throws SQLException {
        String sql = "SELECT type FROM `commentaire_reaction` WHERE commentaire_id = ? AND user_id = ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, commentaireId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("type");
            return null;
        }
    }

    /**
     * Ajoute ou met à jour la réaction d'un utilisateur sur un commentaire.
     * Si l'utilisateur a déjà réagi avec le même type → supprime (toggle off).
     * Si l'utilisateur a réagi avec un type différent → met à jour.
     * Sinon → insère.
     */
    public void addOrUpdate(int commentaireId, int userId, String type) throws SQLException {
        String existing = getUserReaction(commentaireId, userId);
        if (type.equals(existing)) {
            // Toggle off : retirer la réaction
            remove(commentaireId, userId);
        } else if (existing != null) {
            // Changer de réaction
            String sql = "UPDATE `commentaire_reaction` SET type = ?, created_at = NOW() "
                       + "WHERE commentaire_id = ? AND user_id = ?";
            try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setInt(2, commentaireId);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }
        } else {
            // Nouvelle réaction
            String sql = "INSERT INTO `commentaire_reaction` (type, created_at, user_id, commentaire_id) "
                       + "VALUES (?, NOW(), ?, ?)";
            try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setInt(2, userId);
                ps.setInt(3, commentaireId);
                ps.executeUpdate();
            }
        }
    }

    /** Supprime la réaction d'un utilisateur sur un commentaire. */
    public void remove(int commentaireId, int userId) throws SQLException {
        String sql = "DELETE FROM `commentaire_reaction` WHERE commentaire_id = ? AND user_id = ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, commentaireId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Retourne le nombre de réactions par type pour un commentaire.
     * Exemple : {"star": 3, "heart": 1}
     */
    public Map<String, Integer> countByType(int commentaireId) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String sql = "SELECT type, COUNT(*) AS cnt FROM `commentaire_reaction` "
                   + "WHERE commentaire_id = ? GROUP BY type";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, commentaireId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                counts.put(rs.getString("type"), rs.getInt("cnt"));
            }
        }
        return counts;
    }

    /** Nombre total de réactions sur un commentaire. */
    public int totalCount(int commentaireId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `commentaire_reaction` WHERE commentaire_id = ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, commentaireId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }
}
