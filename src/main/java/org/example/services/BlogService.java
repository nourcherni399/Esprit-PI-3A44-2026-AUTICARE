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
        String sql = "INSERT INTO `blog`(titre,contenu,auteur_id,date_publication,categorie,slug,module_id) VALUES(?,?,?,NOW(),?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, a, false);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(BlogArticle a) throws SQLException {
        String sql = "UPDATE `blog` SET titre=?,contenu=?,auteur_id=?,categorie=?,slug=?,module_id=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, a, true);
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
             ResultSet rs = st.executeQuery("SELECT * FROM `blog` ORDER BY date_publication DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<BlogArticle> findByModule(int moduleId) throws SQLException {
        return query("SELECT * FROM `blog` WHERE module_id=? ORDER BY date_publication DESC", moduleId);
    }

    public List<BlogArticle> findArticlesByCategory(String category) throws SQLException {
        return query("SELECT * FROM `blog` WHERE categorie=? ORDER BY date_publication DESC", category);
    }

    /** Nombre d’articles {@code blog} dont {@code user_id} pointe vers l’utilisateur (bandeau profil admin). */
    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `blog` WHERE user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
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

    private void fill(PreparedStatement ps, BlogArticle a, boolean withId) throws SQLException {
        ps.setString(1, a.getTitre());
        ps.setString(2, a.getContenu());
        ps.setInt(3, a.getAuteurId());
        ps.setString(4, a.getCategorie());
        ps.setString(5, a.getSlug());
        if (a.getModuleId() == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, a.getModuleId());
        if (withId) ps.setInt(7, a.getId());
    }

    private BlogArticle map(ResultSet rs) throws SQLException {
        BlogArticle a = new BlogArticle();
        a.setId(rs.getInt("id"));
        a.setTitre(rs.getString("titre"));
        a.setContenu(rs.getString("contenu"));
        a.setAuteurId(rs.getInt("auteur_id"));
        a.setDatePublication(rs.getTimestamp("date_publication").toLocalDateTime());
        a.setCategorie(rs.getString("categorie"));
        a.setSlug(rs.getString("slug"));
        int module = rs.getInt("module_id");
        if (!rs.wasNull()) a.setModuleId(module);
        return a;
    }
}
