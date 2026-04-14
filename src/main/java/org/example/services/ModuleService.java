package org.example.services;

import org.example.models.ModuleContent;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Table MySQL {@code module} (pidb), pas {@code modules}.
 */
public class ModuleService implements IService<ModuleContent> {

    private static final Set<String> CATEGORIES_PIDB = Set.of(
        "", "COMPRENDRE_TSA", "AUTONOMIE", "COMMUNICATION", "EMOTIONS", "VIE_QUOTIDIENNE", "ACCOMPAGNEMENT");

    private static String normalizeCategorie(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim();
        if (CATEGORIES_PIDB.contains(t)) {
            return t;
        }
        return "";
    }

    @Override
    public void add(ModuleContent m) throws SQLException {
        String sql = "INSERT INTO `module`(`titre`,`description`,`contenu`,`niveau`,`image`,`is_published`,`date_creation`,`date_modif`,`categorie`,`admin_id`) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?)";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, m.getTitre());
            ps.setString(2, m.getDescription() == null ? "" : m.getDescription());
            ps.setString(3, m.getRessourcesLien() == null ? "" : m.getRessourcesLien());
            ps.setString(4, "moyen");
            ps.setString(5, "/images/placeholder.png");
            ps.setInt(6, 1);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            ps.setString(9, normalizeCategorie(m.getCategorie()));
            ps.setNull(10, Types.INTEGER);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(ModuleContent m) throws SQLException {
        String sql = "UPDATE `module` SET `titre`=?,`description`=?,`contenu`=?,`categorie`=?,`date_modif`=? WHERE `id`=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, m.getTitre());
            ps.setString(2, m.getDescription() == null ? "" : m.getDescription());
            ps.setString(3, m.getRessourcesLien() == null ? "" : m.getRessourcesLien());
            ps.setString(4, normalizeCategorie(m.getCategorie()));
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(6, m.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `module` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<ModuleContent> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `module` WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<ModuleContent> findAll() throws SQLException {
        List<ModuleContent> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `module` ORDER BY `date_creation` DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private ModuleContent map(ResultSet rs) throws SQLException {
        ModuleContent m = new ModuleContent();
        m.setId(rs.getInt("id"));
        m.setTitre(rs.getString("titre"));
        m.setDescription(rs.getString("description"));
        m.setCategorie(rs.getString("categorie"));
        m.setRessourcesLien(rs.getString("contenu"));
        m.setDateCreation(rs.getTimestamp("date_creation").toLocalDateTime());
        return m;
    }
}
