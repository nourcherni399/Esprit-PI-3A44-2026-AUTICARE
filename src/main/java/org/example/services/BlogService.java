package org.example.services;

import org.example.models.BlogArticle;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Table MySQL {@code blog} (pidb) : {@code user_id}, {@code date_creation}, {@code type}, etc. — pas {@code auteur_id} ni {@code date_publication}.
 */
public class BlogService implements IService<BlogArticle> {

    private static final Set<String> BLOG_TYPES = Set.of("recommandation", "plainte", "question", "experience");

    private static String categorieToType(String cat) {
        if (cat == null || cat.isBlank()) {
            return "experience";
        }
        String c = cat.trim().toLowerCase();
        return BLOG_TYPES.contains(c) ? c : "experience";
    }

    @Override
    public void add(BlogArticle a) throws SQLException {
        String sql = "INSERT INTO `blog`(`titre`,`type`,`is_published`,`image`,`is_urgent`,`is_visible`,`date_creation`,`date_modif`,`contenu`,`module_id`,`user_id`) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, a.getTitre());
            ps.setString(2, categorieToType(a.getCategorie()));
            ps.setInt(3, 1);
            ps.setString(4, "/images/placeholder.png");
            ps.setNull(5, Types.TINYINT);
            ps.setInt(6, 1);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            ps.setString(9, a.getContenu());
            int modId = a.getModuleId() != null ? a.getModuleId() : 1;
            ps.setInt(10, modId);
            if (a.getAuteurId() > 0) {
                ps.setInt(11, a.getAuteurId());
            } else {
                ps.setNull(11, Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void update(BlogArticle a) throws SQLException {
        String sql = "UPDATE `blog` SET `titre`=?,`type`=?,`contenu`=?,`user_id`=?,`module_id`=?,`date_modif`=? WHERE `id`=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, a.getTitre());
            ps.setString(2, categorieToType(a.getCategorie()));
            ps.setString(3, a.getContenu());
            if (a.getAuteurId() > 0) {
                ps.setInt(4, a.getAuteurId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            int modId = a.getModuleId() != null ? a.getModuleId() : 1;
            ps.setInt(5, modId);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(7, a.getId());
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
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<BlogArticle> findAll() throws SQLException {
        List<BlogArticle> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `blog` ORDER BY `date_creation` DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<BlogArticle> findByModule(int moduleId) throws SQLException {
        return query("SELECT * FROM `blog` WHERE module_id=? ORDER BY `date_creation` DESC", moduleId);
    }

    public List<BlogArticle> findArticlesByCategory(String category) throws SQLException {
        return query("SELECT * FROM `blog` WHERE `type`=? ORDER BY `date_creation` DESC", categorieToType(category));
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
            if (value instanceof Integer v) {
                ps.setInt(1, v);
            }
            if (value instanceof String v) {
                ps.setString(1, v);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private BlogArticle map(ResultSet rs) throws SQLException {
        BlogArticle a = new BlogArticle();
        a.setId(rs.getInt("id"));
        a.setTitre(rs.getString("titre"));
        a.setContenu(rs.getString("contenu"));
        Integer uid = rs.getObject("user_id", Integer.class);
        a.setAuteurId(uid != null ? uid : 0);
        Timestamp dc = rs.getTimestamp("date_creation");
        a.setDatePublication(dc != null ? dc.toLocalDateTime() : LocalDateTime.now());
        a.setCategorie(rs.getString("type"));
        a.setSlug("");
        int module = rs.getInt("module_id");
        if (!rs.wasNull()) {
            a.setModuleId(module);
        }
        return a;
    }
}
