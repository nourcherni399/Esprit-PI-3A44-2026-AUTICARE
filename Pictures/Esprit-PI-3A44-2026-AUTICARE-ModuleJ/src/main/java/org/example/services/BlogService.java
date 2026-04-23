package org.example.services;

import org.example.models.BlogArticle;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlogService implements IService<BlogArticle> {
    @Override
    public void add(BlogArticle a) throws SQLException {
        String sql = "INSERT INTO `blog`(titre, type, is_published, image, is_urgent, is_visible, "
                   + "date_creation, date_modif, contenu, module_id, user_id) "
                   + "VALUES(?,?,?,?,?,?,NOW(),NOW(),?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, a.getTitre());
            ps.setString(2, a.getType());
            ps.setBoolean(3, a.isPublished());
            ps.setString(4, a.getImage());
            ps.setBoolean(5, a.isUrgent());
            ps.setBoolean(6, a.isVisible());
            ps.setString(7, a.getContenu());
            if (a.getModuleId() == null) ps.setNull(8, Types.INTEGER); else ps.setInt(8, a.getModuleId());
            if (a.getUserId() == null) ps.setNull(9, Types.INTEGER); else ps.setInt(9, a.getUserId());
            ps.executeUpdate();
        }
    }

    @Override
    public void update(BlogArticle a) throws SQLException {
        String sql = "UPDATE `blog` SET titre=?, type=?, is_published=?, image=?, is_urgent=?, "
                   + "is_visible=?, date_modif=NOW(), contenu=?, module_id=?, user_id=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, a.getTitre());
            ps.setString(2, a.getType());
            ps.setBoolean(3, a.isPublished());
            ps.setString(4, a.getImage());
            ps.setBoolean(5, a.isUrgent());
            ps.setBoolean(6, a.isVisible());
            ps.setString(7, a.getContenu());
            if (a.getModuleId() == null) ps.setNull(8, Types.INTEGER); else ps.setInt(8, a.getModuleId());
            if (a.getUserId() == null) ps.setNull(9, Types.INTEGER); else ps.setInt(9, a.getUserId());
            ps.setInt(10, a.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `blog` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<BlogArticle> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `blog` WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        }
    }

    @Override
    public List<BlogArticle> findAll() throws SQLException {
        List<BlogArticle> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `blog` ORDER BY date_creation DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<BlogArticle> findByModule(int moduleId) throws SQLException {
        return query("SELECT * FROM `blog` WHERE module_id=? ORDER BY date_creation DESC", moduleId);
    }

    public List<BlogArticle> findByType(String type) throws SQLException {
        return query("SELECT * FROM `blog` WHERE type=? ORDER BY date_creation DESC", type);
    }

    public int countByModuleId(int moduleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `blog` WHERE module_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }

    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `blog` WHERE user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }

    /**
     * Vérifie si un titre existe déjà dans la base de données.
     * @param titre Le titre à vérifier
     * @param excludeId ID de l'article à exclure de la recherche (pour l'édition), null pour l'ajout
     * @return true si le titre existe déjà, false sinon
     */
    public boolean existsByTitre(String titre, Integer excludeId) throws SQLException {
        String sql = excludeId == null 
            ? "SELECT COUNT(*) FROM `blog` WHERE LOWER(titre) = LOWER(?)"
            : "SELECT COUNT(*) FROM `blog` WHERE LOWER(titre) = LOWER(?) AND id != ?";
        
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, titre);
            if (excludeId != null) {
                ps.setInt(2, excludeId);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }

    private List<BlogArticle> query(String sql, Object value) throws SQLException {
        List<BlogArticle> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            if (value instanceof Integer v) ps.setInt(1, v);
            if (value instanceof String v) ps.setString(1, v);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private BlogArticle map(ResultSet rs) throws SQLException {
        BlogArticle a = new BlogArticle();
        a.setId(rs.getInt("id"));
        a.setTitre(rs.getString("titre"));
        a.setType(rs.getString("type"));
        a.setPublished(rs.getBoolean("is_published"));
        a.setImage(rs.getString("image"));
        a.setUrgent(rs.getBoolean("is_urgent"));
        a.setVisible(rs.getBoolean("is_visible"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) a.setDateCreation(dc.toLocalDateTime());
        Timestamp dm = rs.getTimestamp("date_modif");
        if (dm != null) a.setDateModif(dm.toLocalDateTime());
        a.setContenu(rs.getString("contenu"));
        int module = rs.getInt("module_id");
        if (!rs.wasNull()) a.setModuleId(module);
        int user = rs.getInt("user_id");
        if (!rs.wasNull()) a.setUserId(user);
        return a;
    }
}
