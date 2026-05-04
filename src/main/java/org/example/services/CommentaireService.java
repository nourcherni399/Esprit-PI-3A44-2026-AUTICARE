package org.example.services;

import org.example.models.Commentaire;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommentaireService {

    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `commentaire` WHERE user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }

    public List<Commentaire> findByBlogId(int blogId) throws SQLException {
        List<Commentaire> list = new ArrayList<>();
        String sql = "SELECT * FROM `commentaire` WHERE blog_id = ? ORDER BY date_creation DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, blogId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public void create(Commentaire c) throws SQLException {
        String sql = "INSERT INTO `commentaire` (contenu, media, is_published, date_creation, user_id, blog_id) "
                   + "VALUES (?, ?, 1, NOW(), ?, ?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, c.getContenu());
            if (c.getMedia() != null && !c.getMedia().isBlank()) {
                ps.setString(2, c.getMedia());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            if (c.getUserId() != null) {
                ps.setInt(3, c.getUserId());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setInt(4, c.getBlogId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM `commentaire` WHERE id = ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Commentaire map(ResultSet rs) throws SQLException {
        Commentaire c = new Commentaire();
        c.setId(rs.getInt("id"));
        c.setContenu(rs.getString("contenu"));
        c.setMedia(rs.getString("media"));
        c.setPublished(rs.getBoolean("is_published"));
        Timestamp dateCreation = rs.getTimestamp("date_creation");
        if (dateCreation != null) c.setDateCreation(dateCreation.toLocalDateTime());
        Timestamp dateModif = rs.getTimestamp("date_modif");
        if (dateModif != null) c.setDateModif(dateModif.toLocalDateTime());
        int userId = rs.getInt("user_id");
        c.setUserId(rs.wasNull() ? null : userId);
        c.setBlogId(rs.getInt("blog_id"));
        return c;
    }
}
