package org.example.services;

import jakarta.mail.MessagingException;
import org.example.models.CartLineView;
import org.example.models.CheckoutFormData;
import org.example.models.Product;
import org.example.models.Role;
import org.example.models.User;
import org.example.utils.AppState;
import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Checkout panier → commande + lignes + notification admin (validation) + notification client « en attente ».
 * Le débit de stock est effectué lors de la première validation admin ({@link CustomerOrderService#advanceCommandeStatut}).
 */
public class OrderCheckoutService {

    private final CartService cartService = new CartService();
    private final UserService userService = new UserService();
    private final OrderConfirmationEmailService orderConfirmationEmailService = new OrderConfirmationEmailService();
    private final NotificationService notificationService = new NotificationService();

    public PlaceOrderResult placeOrder(User currentUser, CheckoutFormData form) throws SQLException {
        if (currentUser == null) {
            throw new SQLException("Utilisateur non connecté.");
        }
        List<CartLineView> cartLines = cartService.loadCartLinesForUser(currentUser.getId());
        if (cartLines.isEmpty()) {
            throw new SQLException("Votre panier est vide.");
        }
        for (CartLineView line : cartLines) {
            int avail = cartService.getAvailableQuantityForCart(line.product());
            if (line.quantity() > avail) {
                String nom = line.product().getNom() != null ? line.product().getNom() : "Produit";
                throw new SQLException(
                    "Stock insuffisant pour « " + nom + " ». Mettez à jour les quantités dans votre panier."
                );
            }
        }
        Connection conn = MyDatabase.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            int commandeId = insertCommande(conn, currentUser.getId(), form, cartLines);
            insertLignesCommande(conn, commandeId, cartLines);
            /* Stock débité uniquement après validation admin (voir CustomerOrderService.advanceCommandeStatut). */
            insertAdminNotifications(conn, commandeId);
            cartService.clearCart(currentUser.getId());
            conn.commit();
            try {
                notificationService.insertClientOrderNotification(
                    currentUser.getId(),
                    NotificationService.TYPE_COMMANDE_EN_ATTENTE_ADMIN,
                    commandeId
                );
            } catch (SQLException ex) {
                System.err.println("[OrderCheckout] Notification client en attente admin : " + ex.getMessage());
            }
            String emailError = sendOrderConfirmationEmailSafe(form, cartLines);
            return new PlaceOrderResult(commandeId, emailError == null, emailError);
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    private static int insertCommande(Connection conn, int userId, CheckoutFormData form, List<CartLineView> lines) throws SQLException {
        String sql = "INSERT INTO `commande`(nom, email, telephone, adresse, code_postal, ville, total, statut, mode_payment, date_creation, stripe_payment_intent, user_id) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, form.nom());
            ps.setString(2, form.email());
            ps.setString(3, form.telephone());
            ps.setString(4, form.adresse());
            ps.setString(5, form.codePostal());
            ps.setString(6, form.ville());
            ps.setDouble(7, CartService.totalPrice(lines));
            ps.setString(8, "en_attente");
            ps.setString(9, form.modePayment());
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(11, form.stripePaymentIntent());
            ps.setInt(12, userId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Impossible de créer la commande.");
    }

    private static void insertLignesCommande(Connection conn, int commandeId, List<CartLineView> lines) throws SQLException {
        String sql = "INSERT INTO `ligne_commande`(quantite, prix, sous_total, commande_id, produit_id) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CartLineView line : lines) {
                ps.setInt(1, line.quantity());
                ps.setDouble(2, line.unitPrice());
                ps.setDouble(3, line.lineTotal());
                ps.setInt(4, commandeId);
                ps.setInt(5, line.product().getId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Charge les lignes d’une commande pour appliquer la même logique de stock que le panier.
     */
    public static List<CartLineView> loadCommandeLinesAsCartLineViews(Connection conn, int commandeId, ProductService productService)
        throws SQLException {
        String sql = "SELECT lc.id, lc.quantite, lc.prix, lc.produit_id FROM ligne_commande lc WHERE lc.commande_id=? ORDER BY lc.id";
        List<CartLineView> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int lineId = rs.getInt("id");
                    int qty = rs.getInt("quantite");
                    double prix = rs.getDouble("prix");
                    int pid = rs.getInt("produit_id");
                    Product p = productService.findById(pid).orElseThrow(
                        () -> new SQLException("Produit introuvable pour la ligne de commande (id=" + pid + ").")
                    );
                    out.add(new CartLineView(lineId, p, qty, prix));
                }
            }
        }
        return out;
    }

    /**
     * Décrémente le stock catalogue / emplacement pour une commande déjà validée par l’admin (même règles que l’ancien checkout).
     */
    public static void applyInventoryDeductionForApprovedCommande(Connection conn, int commandeId, ProductService productService)
        throws SQLException {
        List<CartLineView> lines = loadCommandeLinesAsCartLineViews(conn, commandeId, productService);
        decrementInventoryForOrder(conn, lines);
    }

    /**
     * Décrémente d’abord {@code produit.quantite} ; si elle est insuffisante (ex. 0 en catalogue
     * mais vente autorisée depuis {@code stock.quantite}), décrémente l’emplacement lié.
     */
    private static void decrementInventoryForOrder(Connection conn, List<CartLineView> lines) throws SQLException {
        String decProd = "UPDATE `produit` SET quantite = quantite - ? WHERE id = ? AND quantite >= ?";
        String decStock = "UPDATE `stock` SET quantite = quantite - ? WHERE id = ? AND quantite >= ?";
        String sel = "SELECT stock_id FROM `produit` WHERE id=?";
        for (CartLineView line : lines) {
            int pid = line.product().getId();
            int q = line.quantity();
            if (q < 1) {
                continue;
            }
            try (PreparedStatement ps = conn.prepareStatement(decProd)) {
                ps.setInt(1, q);
                ps.setInt(2, pid);
                ps.setInt(3, q);
                if (ps.executeUpdate() == 1) {
                    continue;
                }
            }
            int stockId = 0;
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setInt(1, pid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stockId = rs.getInt("stock_id");
                    }
                }
            }
            if (stockId <= 0) {
                throw new SQLException(
                    "Stock insuffisant pour le produit id=" + pid + " (commande annulée)."
                );
            }
            try (PreparedStatement ps = conn.prepareStatement(decStock)) {
                ps.setInt(1, q);
                ps.setInt(2, stockId);
                ps.setInt(3, q);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException(
                        "Stock insuffisant pour le produit id=" + pid + " (commande annulée)."
                    );
                }
            }
        }
    }

    private void insertAdminNotifications(Connection conn, int commandeId) throws SQLException {
        User current = AppState.getCurrentUser();
        if (current == null || !isCustomerRoleForAdminNotif(current)) {
            return;
        }
        List<User> admins = userService.findByRole(Role.ADMIN);
        if (admins.isEmpty()) {
            return;
        }
        List<Integer> adminIds = new ArrayList<>();
        for (User admin : admins) {
            adminIds.add(admin.getId());
        }
        String sql = "INSERT INTO `notification`(type, lu, created_at, destinataire_id, commande_id) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Integer adminId : adminIds) {
                ps.setString(1, "nouvelle_commande");
                ps.setBoolean(2, false);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.setInt(4, adminId);
                ps.setInt(5, commandeId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static boolean isCustomerRoleForAdminNotif(User user) {
        Role role = user.getRole();
        if (role == null) {
            return false;
        }
        return switch (role) {
            case PARENT, PATIENT, USER -> true;
            case ADMIN, MEDECIN -> false;
        };
    }

    private String sendOrderConfirmationEmailSafe(CheckoutFormData form, List<CartLineView> cartLines) {
        try {
            orderConfirmationEmailService.sendOrderConfirmation(form, cartLines, CartService.totalPrice(cartLines));
            return null;
        } catch (MessagingException ex) {
            // L'envoi mail ne doit pas annuler une commande déjà validée en base.
            // On loggue l'erreur pour faciliter le diagnostic SMTP.
            System.err.println("[OrderEmail] Echec envoi mail commande vers " + form.email() + " : " + ex.getMessage());
            return ex.getMessage();
        }
    }

    public record PlaceOrderResult(int commandeId, boolean emailSent, String emailErrorMessage) {
    }
}
