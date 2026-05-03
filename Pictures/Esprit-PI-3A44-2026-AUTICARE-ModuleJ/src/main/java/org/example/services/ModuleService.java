package org.example.services;

import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;
import org.example.utils.AppState;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès à la table MySQL {@code module} (base {@code pidb}).
 */
public class ModuleService implements IService<ModuleContent> {

    private static final int MAX_DESCRIPTION_LEN = 255;

    @Override
    public void add(ModuleContent m) throws SQLException {
        String sql = "INSERT INTO `module` (titre, description, contenu, niveau, image, is_published, date_creation, date_modif, categorie, admin_id) "
                + "VALUES (?,?,?,?,?,?,NOW(),NOW(),?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fillInsertUpdate(ps, m);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(ModuleContent m) throws SQLException {
        String sql = "UPDATE `module` SET titre=?, description=?, contenu=?, niveau=?, image=?, is_published=?, date_modif=NOW(), categorie=?, admin_id=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fillInsertUpdate(ps, m);
            ps.setInt(9, m.getId());
            ps.executeUpdate();
        }
    }

    private void fillInsertUpdate(PreparedStatement ps, ModuleContent m) throws SQLException {
        ps.setString(1, nullToEmpty(m.getTitre()));
        ps.setString(2, truncate(nullToEmpty(m.getDescription()), MAX_DESCRIPTION_LEN));
        if (m.getContenu() == null || m.getContenu().isBlank()) {
            ps.setNull(3, Types.LONGVARCHAR);
        } else {
            ps.setString(3, m.getContenu());
        }
        ps.setString(4, m.getNiveau() != null ? m.getNiveau().name() : ModuleNiveau.moyen.name());
        ps.setString(5, nullToEmpty(m.getImage()));
        ps.setInt(6, m.isPublished() ? 1 : 0);
        ps.setString(7, m.getCategorie());
        Integer adminId = m.getAdminId();
        if (adminId == null && AppState.getCurrentUser() != null) {
            adminId = AppState.getCurrentUser().getId();
        }
        if (adminId == null) {
            ps.setNull(8, Types.INTEGER);
        } else {
            ps.setInt(8, adminId);
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
             ResultSet rs = st.executeQuery("SELECT * FROM `module` ORDER BY date_creation DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Vérifie si un titre existe déjà dans la base de données.
     * @param titre Le titre à vérifier
     * @param excludeId ID du module à exclure de la recherche (pour l'édition), null pour l'ajout
     * @return true si le titre existe déjà, false sinon
     */
    public boolean existsByTitre(String titre, Integer excludeId) throws SQLException {
        String sql = excludeId == null 
            ? "SELECT COUNT(*) FROM `module` WHERE LOWER(titre) = LOWER(?)"
            : "SELECT COUNT(*) FROM `module` WHERE LOWER(titre) = LOWER(?) AND id != ?";
        
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

    private ModuleContent map(ResultSet rs) throws SQLException {
        ModuleContent m = new ModuleContent();
        m.setId(rs.getInt("id"));
        m.setTitre(rs.getString("titre"));
        m.setDescription(rs.getString("description"));
        String contenu = rs.getString("contenu");
        if (!rs.wasNull()) {
            m.setContenu(contenu);
        }
        m.setNiveau(ModuleNiveau.fromDb(rs.getString("niveau")));
        m.setImage(rs.getString("image"));
        m.setPublished(rs.getInt("is_published") != 0);
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) {
            m.setDateCreation(dc.toLocalDateTime());
        }
        Timestamp dm = rs.getTimestamp("date_modif");
        if (dm != null) {
            m.setDateModif(dm.toLocalDateTime());
        }
        m.setCategorie(rs.getString("categorie"));
        int aid = rs.getInt("admin_id");
        if (!rs.wasNull()) {
            m.setAdminId(aid);
        }
        return m;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
