package org.example.services;

import org.example.models.CartLineView;
import org.example.models.Product;
import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Panier persisté ({@code cart} + {@code cart_item}), aligné sur le projet Symfony
 * ({@code CartController}, {@code CartSessionService}).
 */
public class CartService {

    private final ProductService productService = new ProductService();
    private final StockService stockService = new StockService();

    /**
     * Quantité maximale au panier : unités « catalogue » ({@code produit.quantite}),
     * plafonnées par la réserve à l’emplacement ({@code stock.quantite}) si elle existe.
     * Si le catalogue est à 0 mais qu’il reste de la quantité à l’emplacement lié
     * ({@code produit.stock_id}), on peut vendre depuis cette réserve (cas où les unités
     * ne sont pas encore reflétées sur la fiche produit).
     */
    public int getAvailableQuantityForCart(Product p) {
        if (p == null) {
            return 0;
        }
        int catalog = Math.max(0, p.getStock());
        int sid = p.getStockId();
        if (sid <= 0) {
            return catalog;
        }
        int physical = Math.max(0, stockService.getQuantityOrZero(sid));
        if (catalog == 0) {
            return physical;
        }
        return Math.min(catalog, physical);
    }

    /**
     * Limite une quantité souhaitée à la disponibilité actuelle (panier invité, UI).
     *
     * @return quantité entre 0 et le max autorisé ; 0 si produit introuvable ou rien de disponible
     */
    public int clampDesiredQuantityForCart(int productId, int desiredQty) throws SQLException {
        if (desiredQty < 1) {
            return 0;
        }
        Optional<Product> opt = productService.findById(productId);
        if (opt.isEmpty()) {
            return 0;
        }
        int max = getAvailableQuantityForCart(opt.get());
        return Math.min(desiredQty, max);
    }

    public int getOrCreateCartId(int userId) throws SQLException {
        Connection conn = MyDatabase.getConnection();
        try (PreparedStatement sel = conn.prepareStatement("SELECT id FROM `cart` WHERE user_id=? LIMIT 1")) {
            sel.setInt(1, userId);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try (PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO `cart`(created_at, updated_at, user_id) VALUES(?,?,?)",
            Statement.RETURN_GENERATED_KEYS)) {
            ins.setTimestamp(1, now);
            ins.setTimestamp(2, now);
            ins.setInt(3, userId);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Impossible de créer le panier.");
    }

    private void touchCart(Connection conn, int cartId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE `cart` SET updated_at=? WHERE id=?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, cartId);
            ps.executeUpdate();
        }
    }

    /**
     * Exporte le panier DB en {@code produit_id -> quantite} (pour copie invité à la déconnexion).
     */
    public Map<Integer, Integer> exportCartAsMap(int userId) throws SQLException {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        Connection conn = MyDatabase.getConnection();
        Integer cartId = findCartId(conn, userId);
        if (cartId == null) {
            return map;
        }
        String sql = "SELECT produit_id, quantite FROM `cart_item` WHERE cart_id=? ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("produit_id"), rs.getInt("quantite"));
                }
            }
        }
        return map;
    }

    public int countTotalItems(int userId) throws SQLException {
        int sum = 0;
        for (int q : exportCartAsMap(userId).values()) {
            sum += q;
        }
        return sum;
    }

    private Integer findCartId(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM `cart` WHERE user_id=? LIMIT 1")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return null;
    }

    /**
     * Fusionne le panier invité dans le panier DB (connexion), puis la session invité doit être vidée par l’appelant.
     */
    public void mergeGuestIntoUserCart(int userId, Map<Integer, Integer> guestLines) throws SQLException {
        if (guestLines == null || guestLines.isEmpty()) {
            return;
        }
        Connection conn = MyDatabase.getConnection();
        boolean prev = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            int cartId = getOrCreateCartIdInTransaction(conn, userId);
            for (Map.Entry<Integer, Integer> e : guestLines.entrySet()) {
                int pid = e.getKey();
                int qtyGuest = e.getValue();
                if (qtyGuest < 1) {
                    continue;
                }
                Optional<Product> opt = productService.findById(pid);
                if (opt.isEmpty()) {
                    continue;
                }
                Product p = opt.get();
                int stock = getAvailableQuantityForCart(p);
                int existing = findItemQty(conn, cartId, pid);
                int merged = Math.min(stock, existing + qtyGuest);
                if (merged < 1) {
                    continue;
                }
                upsertLine(conn, cartId, pid, merged, p.getPrix());
            }
            touchCart(conn, cartId);
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    private int getOrCreateCartIdInTransaction(Connection conn, int userId) throws SQLException {
        Integer id = findCartId(conn, userId);
        if (id != null) {
            return id;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try (PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO `cart`(created_at, updated_at, user_id) VALUES(?,?,?)",
            Statement.RETURN_GENERATED_KEYS)) {
            ins.setTimestamp(1, now);
            ins.setTimestamp(2, now);
            ins.setInt(3, userId);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Création panier impossible.");
    }

    private int findItemQty(Connection conn, int cartId, int productId) throws SQLException {
        String sql = "SELECT id, quantite FROM `cart_item` WHERE cart_id=? AND produit_id=? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            ps.setInt(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantite");
                }
            }
        }
        return 0;
    }

    private void upsertLine(Connection conn, int cartId, int productId, int qty, double prix) throws SQLException {
        String sel = "SELECT id FROM `cart_item` WHERE cart_id=? AND produit_id=? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sel)) {
            ps.setInt(1, cartId);
            ps.setInt(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int itemId = rs.getInt(1);
                    try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE `cart_item` SET quantite=?, prix=? WHERE id=?")) {
                        up.setInt(1, qty);
                        up.setDouble(2, prix);
                        up.setInt(3, itemId);
                        up.executeUpdate();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO `cart_item`(quantite, prix, cart_id, produit_id) VALUES(?,?,?,?)")) {
            ins.setInt(1, qty);
            ins.setDouble(2, prix);
            ins.setInt(3, cartId);
            ins.setInt(4, productId);
            ins.executeUpdate();
        }
    }

    public List<CartLineView> loadCartLinesForUser(int userId) throws SQLException {
        List<CartLineView> out = new ArrayList<>();
        Connection conn = MyDatabase.getConnection();
        Integer cartId = findCartId(conn, userId);
        if (cartId == null) {
            return out;
        }
        String sql = "SELECT id, produit_id, quantite, prix FROM `cart_item` WHERE cart_id=? ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("id");
                    int pid = rs.getInt("produit_id");
                    int qty = rs.getInt("quantite");
                    double prix = rs.getDouble("prix");
                    Optional<Product> opt = productService.findById(pid);
                    if (opt.isPresent()) {
                        out.add(new CartLineView(itemId, opt.get(), qty, prix));
                    }
                }
            }
        }
        return out;
    }

    public List<CartLineView> loadCartLinesForGuest(Map<Integer, Integer> guest) throws SQLException {
        List<CartLineView> out = new ArrayList<>();
        if (guest == null || guest.isEmpty()) {
            return out;
        }
        for (Map.Entry<Integer, Integer> e : guest.entrySet()) {
            int pid = e.getKey();
            int qty = e.getValue();
            if (qty < 1) {
                continue;
            }
            Optional<Product> opt = productService.findById(pid);
            if (opt.isPresent()) {
                Product p = opt.get();
                out.add(new CartLineView(0, p, qty, p.getPrix()));
            }
        }
        return out;
    }

    public void addOne(int userId, int productId) throws SQLException {
        Optional<Product> opt = productService.findById(productId);
        if (opt.isEmpty()) {
            throw new SQLException("Produit introuvable.");
        }
        Product p = opt.get();
        int stock = getAvailableQuantityForCart(p);
        if (stock <= 0) {
            throw new SQLException("STOCK_OUT");
        }
        Connection conn = MyDatabase.getConnection();
        boolean prev = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            int cartId = getOrCreateCartIdInTransaction(conn, userId);
            int existing = findItemQty(conn, cartId, productId);
            int next = existing + 1;
            if (next > stock) {
                conn.rollback();
                throw new SQLException("STOCK_OUT");
            }
            upsertLine(conn, cartId, productId, next, p.getPrix());
            touchCart(conn, cartId);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    public void setQuantity(int userId, int productId, int quantity) throws SQLException {
        if (quantity < 1) {
            removeProduct(userId, productId);
            return;
        }
        Optional<Product> opt = productService.findById(productId);
        if (opt.isEmpty()) {
            return;
        }
        Product p = opt.get();
        int stock = getAvailableQuantityForCart(p);
        if (quantity > stock) {
            throw new SQLException("STOCK_OUT");
        }
        Connection conn = MyDatabase.getConnection();
        Integer cartId = findCartId(conn, userId);
        if (cartId == null) {
            return;
        }
        boolean prev = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            upsertLine(conn, cartId, productId, quantity, p.getPrix());
            touchCart(conn, cartId);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    public void removeProduct(int userId, int productId) throws SQLException {
        Connection conn = MyDatabase.getConnection();
        Integer cartId = findCartId(conn, userId);
        if (cartId == null) {
            return;
        }
        try (PreparedStatement del = conn.prepareStatement(
            "DELETE FROM `cart_item` WHERE cart_id=? AND produit_id=?")) {
            del.setInt(1, cartId);
            del.setInt(2, productId);
            del.executeUpdate();
        }
        touchCart(conn, cartId);
    }

    public void clearCart(int userId) throws SQLException {
        Connection conn = MyDatabase.getConnection();
        Integer cartId = findCartId(conn, userId);
        if (cartId == null) {
            return;
        }
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM `cart_item` WHERE cart_id=?")) {
            del.setInt(1, cartId);
            del.executeUpdate();
        }
        touchCart(conn, cartId);
    }

    public static double totalPrice(List<CartLineView> lines) {
        double t = 0;
        for (CartLineView l : lines) {
            t += l.lineTotal();
        }
        return t;
    }

    public static int totalItems(List<CartLineView> lines) {
        int n = 0;
        for (CartLineView l : lines) {
            n += l.quantity();
        }
        return n;
    }
}
