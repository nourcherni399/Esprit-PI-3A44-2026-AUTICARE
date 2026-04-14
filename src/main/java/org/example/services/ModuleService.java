package org.example.services;

import org.example.models.ModuleContent;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ModuleService implements IService<ModuleContent> {
    @Override
    public void add(ModuleContent m) throws SQLException {
        String sql = "INSERT INTO modules(titre,description,categorie,ressources_lien,date_creation) VALUES(?,?,?,?,NOW())";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, m.getTitre());
            ps.setString(2, m.getDescription());
            ps.setString(3, m.getCategorie());
            ps.setString(4, m.getRessourcesLien());
            ps.executeUpdate();
        }
    }

    @Override
    public void update(ModuleContent m) throws SQLException {
        String sql = "UPDATE modules SET titre=?,description=?,categorie=?,ressources_lien=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, m.getTitre());
            ps.setString(2, m.getDescription());
            ps.setString(3, m.getCategorie());
            ps.setString(4, m.getRessourcesLien());
            ps.setInt(5, m.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM modules WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<ModuleContent> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM modules WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        }
    }

    @Override
    public List<ModuleContent> findAll() throws SQLException {
        List<ModuleContent> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM modules ORDER BY date_creation DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private ModuleContent map(ResultSet rs) throws SQLException {
        ModuleContent m = new ModuleContent();
        m.setId(rs.getInt("id"));
        m.setTitre(rs.getString("titre"));
        m.setDescription(rs.getString("description"));
        m.setCategorie(rs.getString("categorie"));
        m.setRessourcesLien(rs.getString("ressources_lien"));
        m.setDateCreation(rs.getTimestamp("date_creation").toLocalDateTime());
        return m;
    }
}
