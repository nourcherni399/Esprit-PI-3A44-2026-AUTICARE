package org.example.services;

import org.example.models.Stock;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StockService {

    public List<Stock> findAll() throws SQLException {
        List<Stock> list = new ArrayList<>();
        String sql = "SELECT id, nom, quantite FROM `stock` ORDER BY id DESC";
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Recherche texte (id, nom, quantité) — même logique que le projet ppp.
     */
    public List<Stock> search(String rawTerm) throws SQLException {
        String t = rawTerm == null ? "" : rawTerm.trim();
        if (t.isEmpty()) {
            return findAll();
        }
        String pattern = "%" + t + "%";
        String sql = "SELECT id, nom, quantite FROM `stock` WHERE CAST(id AS CHAR) LIKE ? OR `nom` LIKE ? OR CAST(quantite AS CHAR) LIKE ? ORDER BY id DESC";
        List<Stock> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public Optional<Stock> findById(int id) throws SQLException {
        String sql = "SELECT id, nom, quantite FROM `stock` WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Quantité à l’emplacement (0 si inconnu / erreur). */
    public int getQuantityOrZero(int stockId) {
        if (stockId <= 0) {
            return 0;
        }
        try {
            return findById(stockId).map(Stock::getQuantite).orElse(0);
        } catch (SQLException e) {
            return 0;
        }
    }

    public void insert(String nom, int quantite) throws SQLException {
        String sql = "INSERT INTO `stock` (nom, quantite) VALUES (?, ?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nom);
            ps.setInt(2, quantite);
            ps.executeUpdate();
        }
    }

    public void update(int id, String nom, int quantite) throws SQLException {
        String sql = "UPDATE `stock` SET nom=?, quantite=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, nom);
            ps.setInt(2, quantite);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public int countProduitsByStockId(int stockId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `produit` WHERE stock_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public void deleteById(int id) throws SQLException {
        if (countProduitsByStockId(id) > 0) {
            throw new SQLException("Impossible de supprimer : des produits utilisent encore cet emplacement.");
        }
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `stock` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static Stock map(ResultSet rs) throws SQLException {
        Stock s = new Stock();
        s.setId(rs.getInt("id"));
        s.setNom(rs.getString("nom"));
        s.setQuantite(rs.getInt("quantite"));
        return s;
    }
}
