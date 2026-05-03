package org.example.services;

import org.example.models.CustomerOrder;
import org.example.models.CustomerOrderLine;
import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lecture des commandes côté client (propriétaire {@code user_id}), aligné sur Symfony.
 */
public class CustomerOrderService {

    private final NotificationService notificationService = new NotificationService();

    public Optional<CustomerOrder> findByIdForUser(int commandeId, int userId) throws SQLException {
        String sql = "SELECT id, nom, email, telephone, adresse, code_postal, ville, total, statut, mode_payment, "
            + "date_creation, stripe_payment_intent, user_id FROM `commande` WHERE id=? AND user_id=?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapOrder(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<CustomerOrderLine> findLinesForUserOrder(int commandeId, int userId) throws SQLException {
        String sql = "SELECT lc.quantite, lc.prix, lc.sous_total, lc.produit_id, p.nom AS produit_nom, p.image AS produit_image "
            + "FROM ligne_commande lc "
            + "JOIN produit p ON p.id = lc.produit_id "
            + "JOIN commande c ON c.id = lc.commande_id "
            + "WHERE lc.commande_id=? AND c.user_id=? "
            + "ORDER BY lc.id ASC";
        List<CustomerOrderLine> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CustomerOrderLine(
                        rs.getInt("quantite"),
                        rs.getDouble("prix"),
                        rs.getDouble("sous_total"),
                        rs.getInt("produit_id"),
                        rs.getString("produit_nom"),
                        rs.getString("produit_image")
                    ));
                }
            }
        }
        return list;
    }

    public List<CustomerOrder> findAllForUser(int userId) throws SQLException {
        String sql = "SELECT id, nom, email, telephone, adresse, code_postal, ville, total, statut, mode_payment, "
            + "date_creation, stripe_payment_intent, user_id FROM `commande` WHERE user_id=? ORDER BY date_creation DESC";
        List<CustomerOrder> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapOrder(rs));
                }
            }
        }
        return list;
    }

    public Set<Integer> findPurchasedProductIdsForUser(int userId) throws SQLException {
        String sql = "SELECT DISTINCT lc.produit_id "
            + "FROM commande c "
            + "JOIN ligne_commande lc ON lc.commande_id = c.id "
            + "WHERE c.user_id=? AND lc.produit_id IS NOT NULL";
        Set<Integer> ids = new LinkedHashSet<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("produit_id"));
                }
            }
        }
        return ids;
    }

    public Map<String, Integer> findTopPurchasedCategoriesForUser(int userId, int limit) throws SQLException {
        int safeLimit = Math.max(1, limit);
        String sql = "SELECT p.categorie AS categorie, COUNT(*) AS cnt "
            + "FROM commande c "
            + "JOIN ligne_commande lc ON lc.commande_id = c.id "
            + "JOIN produit p ON p.id = lc.produit_id "
            + "WHERE c.user_id=? AND p.categorie IS NOT NULL AND TRIM(p.categorie) <> '' "
            + "GROUP BY p.categorie "
            + "ORDER BY cnt DESC";
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && out.size() < safeLimit) {
                    out.put(rs.getString("categorie"), rs.getInt("cnt"));
                }
            }
        }
        return out;
    }

    /**
     * Toutes les commandes (admin) : les plus récentes en premier.
     */
    public List<CustomerOrder> findAllOrders() throws SQLException {
        String sql = "SELECT id, nom, email, telephone, adresse, code_postal, ville, total, statut, mode_payment, "
            + "date_creation, stripe_payment_intent, user_id FROM `commande` ORDER BY date_creation DESC";
        List<CustomerOrder> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapOrder(rs));
            }
        }
        return list;
    }

    /**
     * Anciennes commandes (schéma Symfony / table {@code order}), pour l’admin.
     */
    public List<CustomerOrder> findAllOrdersFromLegacyOrderTable() throws SQLException {
        String sql = "SELECT id, total_price, status, payment_method, first_name, last_name, email, phone, "
            + "address, postal_code, city, created_at, user_id FROM `order` ORDER BY created_at DESC";
        List<CustomerOrder> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapLegacyOrder(rs));
            }
        }
        return list;
    }

    /**
     * Lignes d’une commande (admin, sans filtre propriétaire).
     */
    public List<CustomerOrderLine> findLinesForOrder(int commandeId) throws SQLException {
        String sql = "SELECT lc.quantite, lc.prix, lc.sous_total, lc.produit_id, p.nom AS produit_nom, p.image AS produit_image "
            + "FROM ligne_commande lc "
            + "JOIN produit p ON p.id = lc.produit_id "
            + "WHERE lc.commande_id=? "
            + "ORDER BY lc.id ASC";
        List<CustomerOrderLine> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CustomerOrderLine(
                        rs.getInt("quantite"),
                        rs.getDouble("prix"),
                        rs.getDouble("sous_total"),
                        rs.getInt("produit_id"),
                        rs.getString("produit_nom"),
                        rs.getString("produit_image")
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Lignes d’une commande issue de la table legacy {@code order} / {@code order_item}.
     */
    public List<CustomerOrderLine> findLinesForLegacyOrder(int legacyOrderId) throws SQLException {
        String sql = "SELECT oi.quantite, oi.prix, (oi.quantite * oi.prix) AS sous_total, oi.produit_id, "
            + "p.nom AS produit_nom, p.image AS produit_image "
            + "FROM order_item oi "
            + "JOIN produit p ON p.id = oi.produit_id "
            + "WHERE oi.order_id=? "
            + "ORDER BY oi.id ASC";
        List<CustomerOrderLine> list = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, legacyOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CustomerOrderLine(
                        rs.getInt("quantite"),
                        rs.getDouble("prix"),
                        rs.getDouble("sous_total"),
                        rs.getInt("produit_id"),
                        rs.getString("produit_nom"),
                        rs.getString("produit_image")
                    ));
                }
            }
        }
        return list;
    }

    private static CustomerOrder mapOrder(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("date_creation");
        LocalDateTime dt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        String stripe = rs.getString("stripe_payment_intent");
        return new CustomerOrder(
            rs.getInt("id"),
            rs.getString("nom"),
            rs.getString("email"),
            rs.getString("telephone"),
            rs.getString("adresse"),
            rs.getString("code_postal"),
            rs.getString("ville"),
            rs.getDouble("total"),
            rs.getString("statut"),
            rs.getString("mode_payment"),
            dt,
            stripe,
            rs.getInt("user_id")
        );
    }

    private static CustomerOrder mapLegacyOrder(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime dt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        String fn = rs.getString("first_name");
        String ln = rs.getString("last_name");
        String nom = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
        if (nom.isEmpty()) {
            nom = "—";
        }
        return new CustomerOrder(
            rs.getInt("id"),
            nom,
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getString("postal_code"),
            rs.getString("city"),
            rs.getDouble("total_price"),
            rs.getString("status"),
            rs.getString("payment_method"),
            dt,
            null,
            rs.getInt("user_id")
        );
    }

    /**
     * Chaîne de statuts table {@code commande} (alignée sur « Mes commandes ») :
     * en attente → en livraison → livrée.
     */
    public static Optional<String> nextStatutForCommande(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String k = raw.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "en_attente" -> Optional.of("livraison");
            case "livraison" -> Optional.of("livrée");
            case "livrée", "livree" -> Optional.empty();
            case "confirmer" -> Optional.of("livraison"); // compat anciens enregistrements
            case "payée", "payee" -> Optional.of("livrée");
            case "expédiée", "expediee" -> Optional.of("livrée");
            case "annulée", "annulee" -> Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * Chaîne pour la table legacy {@code order} (statuts souvent en anglais).
     */
    public static Optional<String> nextStatutForLegacyOrder(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String k = raw.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "pending" -> Optional.of("confirmed");
            case "confirmed" -> Optional.of("shipped");
            case "shipped" -> Optional.of("delivered");
            case "delivered" -> Optional.empty();
            case "cancelled", "canceled" -> Optional.empty();
            default -> Optional.empty();
        };
    }

    public boolean canAdvanceStatut(boolean legacyOrder, String currentStatut) {
        return legacyOrder
            ? nextStatutForLegacyOrder(currentStatut).isPresent()
            : nextStatutForCommande(currentStatut).isPresent();
    }

    /** Commande boutique encore annulable (pas livrée ni déjà annulée). */
    public static boolean isCommandeCancellableByAdmin(String statut) {
        if (statut == null || statut.isBlank()) {
            return false;
        }
        String k = statut.trim().toLowerCase(Locale.ROOT);
        return !"annulée".equals(k) && !"annulee".equals(k) && !"livrée".equals(k) && !"livree".equals(k);
    }

    public static boolean isEnAttenteCommande(String statut) {
        return statut != null && "en_attente".equalsIgnoreCase(statut.trim());
    }

    /**
     * Annule la commande (statut {@code annulée}) et notifie le client.
     */
    public void annulerCommandeParAdmin(int commandeId) throws SQLException {
        String cur = fetchStatutCommande(commandeId).orElseThrow(
            () -> new SQLException("Commande introuvable.")
        );
        if (!isCommandeCancellableByAdmin(cur)) {
            throw new SQLException("Impossible d’annuler une commande livrée ou déjà annulée.");
        }
        int uid = fetchUserIdForCommande(commandeId);
        String sql = "UPDATE `commande` SET statut=? WHERE id=? AND statut=?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "annulée");
            ps.setInt(2, commandeId);
            ps.setString(3, cur);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Impossible d’annuler la commande.");
            }
        }
        if (uid > 0) {
            notificationService.insertClientOrderNotification(uid, NotificationService.TYPE_COMMANDE_ANNULEE, commandeId);
        }
        notificationService.deleteAdminNotificationsForCommande(commandeId);
    }

    /**
     * Passe la commande à l’étape suivante (admin). Échoue si aucune transition n’est définie.
     */
    public void advanceOrderStatut(boolean legacyOrder, int orderId) throws SQLException {
        if (legacyOrder) {
            advanceLegacyOrderStatus(orderId);
        } else {
            advanceCommandeStatut(orderId);
        }
    }

    private void advanceCommandeStatut(int commandeId) throws SQLException {
        String cur = fetchStatutCommande(commandeId).orElseThrow(
            () -> new SQLException("Commande introuvable.")
        );
        String next = nextStatutForCommande(cur).orElseThrow(
            () -> new SQLException("Cette commande est déjà au dernier statut ou le statut n’est pas géré.")
        );
        int uid = fetchUserIdForCommande(commandeId);
        String sql = "UPDATE `commande` SET statut=? WHERE id=? AND statut=?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, next);
            ps.setInt(2, commandeId);
            ps.setString(3, cur);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Impossible de mettre à jour le statut (commande modifiée entre-temps).");
            }
        }
        notifyClientAfterCommandeAdvance(uid, next, commandeId);
    }

    private void notifyClientAfterCommandeAdvance(int clientUserId, String nextStatut, int commandeId) throws SQLException {
        if (clientUserId <= 0 || nextStatut == null) {
            return;
        }
        String n = nextStatut.trim().toLowerCase(Locale.ROOT);
        if ("livraison".equals(n) || "confirmer".equals(n)) {
            notificationService.insertClientOrderNotification(
                clientUserId, NotificationService.TYPE_COMMANDE_VALIDEE, commandeId
            );
        } else if ("livrée".equals(n) || "livree".equals(n)) {
            notificationService.insertClientOrderNotification(
                clientUserId, NotificationService.TYPE_COMMANDE_LIVREE, commandeId
            );
        }
    }

    private static int fetchUserIdForCommande(int commandeId) throws SQLException {
        String sql = "SELECT user_id FROM `commande` WHERE id=?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getObject("user_id") != null) {
                    return rs.getInt("user_id");
                }
            }
        }
        return 0;
    }

    private void advanceLegacyOrderStatus(int orderId) throws SQLException {
        String cur = fetchStatutLegacyOrder(orderId).orElseThrow(
            () -> new SQLException("Commande (ancienne table) introuvable.")
        );
        String next = nextStatutForLegacyOrder(cur).orElseThrow(
            () -> new SQLException("Cette commande est déjà au dernier statut ou le statut n’est pas géré.")
        );
        String sql = "UPDATE `order` SET status=? WHERE id=? AND status=?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, next);
            ps.setInt(2, orderId);
            ps.setString(3, cur);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Impossible de mettre à jour le statut (commande modifiée entre-temps).");
            }
        }
    }

    private static Optional<String> fetchStatutCommande(int commandeId) throws SQLException {
        String sql = "SELECT statut FROM `commande` WHERE id=?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("statut"));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> fetchStatutLegacyOrder(int orderId) throws SQLException {
        String sql = "SELECT status FROM `order` WHERE id=?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("status"));
                }
            }
        }
        return Optional.empty();
    }
}
