package org.example.services;

import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Avis 1–5 sur un produit ({@code avis_produit}), mise à jour de {@code produit.note_moyenne}.
 */
public class AvisProduitService {

    public void saveOrUpdateNote(int userId, int produitId, int note) throws SQLException {
        if (note < 1 || note > 5) {
            throw new SQLException("La note doit être entre 1 et 5.");
        }
        Connection conn = MyDatabase.getConnection();
        String upsert = "INSERT INTO `avis_produit`(note, created_at, produit_id, user_id) VALUES(?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE note=VALUES(note), created_at=VALUES(created_at)";
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setInt(1, note);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(3, produitId);
            ps.setInt(4, userId);
            ps.executeUpdate();
        }
        refreshAverage(conn, produitId);
    }

    public Integer findNoteByUserAndProduct(int userId, int produitId) throws SQLException {
        String sql = "SELECT note FROM `avis_produit` WHERE user_id=? AND produit_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, produitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return null;
    }

    private static void refreshAverage(Connection conn, int produitId) throws SQLException {
        Double avg;
        String q = "SELECT AVG(note) FROM `avis_produit` WHERE produit_id=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, produitId);
            try (ResultSet rs = ps.executeQuery()) {
                avg = rs.next() ? rs.getDouble(1) : null;
                if (rs.wasNull()) {
                    avg = null;
                }
            }
        }
        String up = "UPDATE `produit` SET note_moyenne=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(up)) {
            if (avg == null) {
                ps.setNull(1, java.sql.Types.DOUBLE);
            } else {
                ps.setDouble(1, Math.round(avg * 100.0) / 100.0);
            }
            ps.setInt(2, produitId);
            ps.executeUpdate();
        }
    }
}
