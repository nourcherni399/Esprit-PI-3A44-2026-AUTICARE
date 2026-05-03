package org.example.services;

import org.example.models.Product;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductService implements IService<Product> {
    @Override
    public void add(Product p) throws SQLException {
        String sql = "INSERT INTO `produit`(nom,description,prix,categorie,stock,image_path,publie,note_moyenne) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, p, false);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Product p) throws SQLException {
        String sql = "UPDATE `produit` SET nom=?,description=?,prix=?,categorie=?,stock=?,image_path=?,publie=?,note_moyenne=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, p, true);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `produit` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Product> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `produit` WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        }
    }

    @Override
    public List<Product> findAll() throws SQLException {
        List<Product> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `produit` ORDER BY id DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /** Nombre de lignes {@code produit} dont {@code user_id} pointe vers l’utilisateur (bandeau profil admin). */
    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `produit` WHERE user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    public List<Product> searchByCategoryAndPrice(String category, double minPrice, double maxPrice) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM `produit` WHERE (?='' OR categorie=?) AND prix BETWEEN ? AND ? ORDER BY prix ASC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, category);
            ps.setDouble(3, minPrice);
            ps.setDouble(4, maxPrice);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private void fill(PreparedStatement ps, Product p, boolean withId) throws SQLException {
        ps.setString(1, p.getNom());
        ps.setString(2, p.getDescription());
        ps.setDouble(3, p.getPrix());
        ps.setString(4, p.getCategorie());
        ps.setInt(5, p.getStock());
        ps.setString(6, p.getImagePath());
        ps.setBoolean(7, p.isPublie());
        if (p.getNoteMoyenne() == null) ps.setNull(8, Types.DOUBLE); else ps.setDouble(8, p.getNoteMoyenne());
        if (withId) ps.setInt(9, p.getId());
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setNom(rs.getString("nom"));
        p.setDescription(rs.getString("description"));
        p.setPrix(rs.getDouble("prix"));
        p.setCategorie(rs.getString("categorie"));
        p.setStock(rs.getInt("stock"));
        p.setImagePath(rs.getString("image_path"));
        p.setPublie(rs.getBoolean("publie"));
        double note = rs.getDouble("note_moyenne");
        if (!rs.wasNull()) p.setNoteMoyenne(note);
        return p;
    }
}
