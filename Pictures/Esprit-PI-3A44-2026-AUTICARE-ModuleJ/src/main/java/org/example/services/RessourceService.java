package org.example.services;

import org.example.models.Ressource;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RessourceService implements IService<Ressource> {

    @Override
    public void add(Ressource r) throws SQLException {
        String sql = "INSERT INTO `ressource`(titre, type_ressource, contenu, date_creation, datemodif, ordre, is_active, module_id) "
                   + "VALUES(?,?,?,NOW(),NOW(),?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, r.getTitre());
            ps.setString(2, r.getTypeRessource());
            ps.setString(3, r.getContenu());
            if (r.getOrdre() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, r.getOrdre());
            ps.setBoolean(5, r.isActive());
            ps.setInt(6, r.getModuleId());
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Ressource r) throws SQLException {
        String sql = "UPDATE `ressource` SET titre=?, type_ressource=?, contenu=?, datemodif=NOW(), ordre=?, is_active=?, module_id=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, r.getTitre());
            ps.setString(2, r.getTypeRessource());
            ps.setString(3, r.getContenu());
            if (r.getOrdre() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, r.getOrdre());
            ps.setBoolean(5, r.isActive());
            ps.setInt(6, r.getModuleId());
            ps.setInt(7, r.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `ressource` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Ressource> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `ressource` WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        }
    }

    @Override
    public List<Ressource> findAll() throws SQLException {
        List<Ressource> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `ressource` ORDER BY module_id, ordre")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Ressource> findByModule(int moduleId) throws SQLException {
        List<Ressource> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(
                "SELECT * FROM `ressource` WHERE module_id=? ORDER BY ordre")) {
            ps.setInt(1, moduleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public int countByModule(int moduleId) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM `ressource` WHERE module_id=?")) {
            ps.setInt(1, moduleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }

    /**
     * Vérifie si un titre existe déjà dans la base de données.
     * @param titre Le titre à vérifier
     * @param excludeId ID de la ressource à exclure de la recherche (pour l'édition), null pour l'ajout
     * @return true si le titre existe déjà, false sinon
     */
    public boolean existsByTitre(String titre, Integer excludeId) throws SQLException {
        String sql = excludeId == null 
            ? "SELECT COUNT(*) FROM `ressource` WHERE LOWER(titre) = LOWER(?)"
            : "SELECT COUNT(*) FROM `ressource` WHERE LOWER(titre) = LOWER(?) AND id != ?";
        
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

    private Ressource map(ResultSet rs) throws SQLException {
        Ressource r = new Ressource();
        r.setId(rs.getInt("id"));
        r.setTitre(rs.getString("titre"));
        r.setTypeRessource(rs.getString("type_ressource"));
        r.setContenu(rs.getString("contenu"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) r.setDateCreation(dc.toLocalDateTime());
        Timestamp dm = rs.getTimestamp("datemodif");
        if (dm != null) r.setDateModif(dm.toLocalDateTime());
        int ordre = rs.getInt("ordre");
        if (!rs.wasNull()) r.setOrdre(ordre);
        r.setActive(rs.getBoolean("is_active"));
        r.setModuleId(rs.getInt("module_id"));
        return r;
    }
}
