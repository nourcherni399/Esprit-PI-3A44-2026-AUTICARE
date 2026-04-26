package org.example.services;

import org.example.models.Product;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ProductService implements IService<Product> {

    private static final String INSERT_SQL = "INSERT INTO `produit`(`nom`,`description`,`categorie`,`prix`,`disponibilite`,`image`,`sku`,"
        + "`statut_publication`,`note_moyenne`,`quantite`,`genere_par_ia`,`valide`,`user_id`,`stock_id`,`seuil_alerte`) "
        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_SQL = "UPDATE `produit` SET `nom`=?,`description`=?,`categorie`=?,`prix`=?,`disponibilite`=?,`image`=?,`sku`=?,"
        + "`statut_publication`=?,`note_moyenne`=?,`quantite`=?,`genere_par_ia`=?,`valide`=?,`user_id`=?,`stock_id`=?,`seuil_alerte`=? WHERE `id`=?";

    @Override
    public void add(Product p) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(INSERT_SQL)) {
            fill(ps, p, false);
            ps.executeUpdate();
        }
    }

    public int addReturningId(Product p) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            fill(ps, p, false);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Insertion produit impossible.");
    }

    @Override
    public void update(Product p) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(UPDATE_SQL)) {
            fill(ps, p, true);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        Connection conn = MyDatabase.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            deleteFromChildTables(conn, id);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM `produit` WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Supprime les lignes qui référencent {@code produit} sans {@code ON DELETE CASCADE}
     * ({@code ligne_commande}, {@code cart_item}, {@code order_item}).
     */
    private static void deleteFromChildTables(Connection conn, int produitId) throws SQLException {
        String[] sqls = {
            "DELETE FROM `ligne_commande` WHERE produit_id=?",
            "DELETE FROM `cart_item` WHERE produit_id=?",
            "DELETE FROM `order_item` WHERE produit_id=?"
        };
        for (String sql : sqls) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, produitId);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public Optional<Product> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM `produit` WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Product> findAll() throws SQLException {
        List<Product> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `produit` ORDER BY id DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
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
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<Product> search(String keyword) throws SQLException {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return findAll();
        }
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM `produit` WHERE nom LIKE ? OR description LIKE ? OR categorie LIKE ? ORDER BY id DESC";
        String like = "%" + q + "%";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public List<Product> findPublishedCatalog() throws SQLException {
        List<Product> all = findAll();
        return all.stream().filter(ProductService::isVisibleOnPublicCatalog).toList();
    }

    /**
     * Produit affiché dans le catalogue public (parent, patient, invité) : statut « publié » uniquement.
     * La disponibilité ({@code disponibilite} / stock) n’empêche pas l’affichage : la carte reste visible,
     * l’ajout au panier est bloqué si plus de stock (voir panier / {@code CartService}).
     */
    public static boolean isVisibleOnPublicCatalog(Product p) {
        return p != null && p.isPublie();
    }

    /** Reconnaît les variantes courantes de statut en base (Symfony / imports). */
    public static boolean isPublicationStatusPublic(String statutPublication) {
        if (statutPublication == null || statutPublication.isBlank()) {
            return false;
        }
        String s = statutPublication.trim().toLowerCase(Locale.FRENCH);
        return "publie".equals(s) || "publié".equals(s) || "published".equals(s);
    }

    private static int resolveStockId(Product p) throws SQLException {
        if (p != null && p.getStockId() > 0) {
            return p.getStockId();
        }
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM `stock` ORDER BY id ASC LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Aucun emplacement dans la table stock. Créez un stock ou associez stock_id au produit.");
    }

    private static String statutPublicationFromProduct(Product p) {
        if (p.getStatutPublication() != null && !p.getStatutPublication().isBlank()) {
            return p.getStatutPublication().trim();
        }
        return p.isPublie() ? "publie" : "brouillon";
    }

    private void fill(PreparedStatement ps, Product p, boolean withId) throws SQLException {
        int stockId = resolveStockId(p);
        ps.setString(1, p.getNom());
        ps.setString(2, p.getDescription());
        ps.setString(3, p.getCategorie());
        ps.setDouble(4, p.getPrix());
        ps.setInt(5, p.isDisponible() ? 1 : 0);
        if (p.getImagePath() == null || p.getImagePath().isBlank()) {
            ps.setNull(6, Types.VARCHAR);
        } else {
            ps.setString(6, p.getImagePath().trim());
        }
        if (p.getSku() == null || p.getSku().isBlank()) {
            ps.setNull(7, Types.VARCHAR);
        } else {
            ps.setString(7, p.getSku().trim());
        }
        ps.setString(8, statutPublicationFromProduct(p));
        if (p.getNoteMoyenne() == null) {
            ps.setDouble(9, 0);
        } else {
            ps.setDouble(9, p.getNoteMoyenne());
        }
        ps.setInt(10, p.getStock());
        ps.setInt(11, p.isGenereParIa() ? 1 : 0);
        ps.setInt(12, p.isValide() ? 1 : 0);
        if (p.getUserId() == null) {
            ps.setNull(13, Types.INTEGER);
        } else {
            ps.setInt(13, p.getUserId());
        }
        ps.setInt(14, stockId);
        if (p.getSeuilAlerte() == null) {
            ps.setNull(15, Types.INTEGER);
        } else {
            ps.setInt(15, p.getSeuilAlerte());
        }
        if (withId) {
            ps.setInt(16, p.getId());
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setNom(rs.getString("nom"));
        p.setDescription(rs.getString("description"));
        p.setPrix(rs.getDouble("prix"));
        p.setCategorie(rs.getString("categorie"));
        p.setDisponible(rs.getInt("disponibilite") != 0);
        String img = rs.getString("image");
        p.setImagePath(rs.wasNull() ? null : img);
        String sku = rs.getString("sku");
        p.setSku(rs.wasNull() ? null : sku);
        String sp = rs.getString("statut_publication");
        p.setStatutPublication(sp);
        p.setPublie(isPublicationStatusPublic(sp));
        double note = rs.getDouble("note_moyenne");
        if (!rs.wasNull()) {
            p.setNoteMoyenne(note);
        }
        p.setStock(rs.getInt("quantite"));
        p.setGenereParIa(rs.getInt("genere_par_ia") != 0);
        p.setValide(rs.getInt("valide") != 0);
        int uid = rs.getInt("user_id");
        if (rs.wasNull()) {
            p.setUserId(null);
        } else {
            p.setUserId(uid);
        }
        p.setStockId(rs.getInt("stock_id"));
        int seuil = rs.getInt("seuil_alerte");
        if (rs.wasNull()) {
            p.setSeuilAlerte(null);
        } else {
            p.setSeuilAlerte(seuil);
        }
        return p;
    }
}
