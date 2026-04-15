package org.example.services;

import org.example.utils.MyDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Agrégats sur la table Symfony {@code commentaire} (FK {@code user_id} → {@code user.id}). */
public class CommentaireService {

    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `commentaire` WHERE user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}
