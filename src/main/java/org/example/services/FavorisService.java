package org.example.services;

import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Favoris produits ({@code favoris} : user_id, produit_id).
 */
public class FavorisService {

    public boolean isFavorite(int userId, int produitId) throws SQLException {
        String sql = "SELECT 1 FROM `favoris` WHERE user_id=? AND produit_id=? LIMIT 1";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, produitId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void add(int userId, int produitId) throws SQLException {
        if (isFavorite(userId, produitId)) {
            return;
        }
        String sql = "INSERT INTO `favoris`(created_at, user_id, produit_id) VALUES(?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, userId);
            ps.setInt(3, produitId);
            ps.executeUpdate();
        }
    }

    public void remove(int userId, int produitId) throws SQLException {
        String sql = "DELETE FROM `favoris` WHERE user_id=? AND produit_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, produitId);
            ps.executeUpdate();
        }
    }

    public Set<Integer> favoriteProductIdsForUser(int userId) throws SQLException {
        Set<Integer> set = new HashSet<>();
        String sql = "SELECT produit_id FROM `favoris` WHERE user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getInt(1));
                }
            }
        }
        return set;
    }

    public List<Integer> listProductIdsOrdered(int userId) throws SQLException {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT produit_id FROM `favoris` WHERE user_id=? ORDER BY created_at DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt(1));
                }
            }
        }
        return list;
    }
}
