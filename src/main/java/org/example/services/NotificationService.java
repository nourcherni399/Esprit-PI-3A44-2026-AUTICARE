package org.example.services;

import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Notifications admin (nouvelle commande, demande produit) et client (suivi commande).
 */
public class NotificationService {

    public static final String TYPE_COMMANDE_VALIDEE = "commande_validee";
    public static final String TYPE_COMMANDE_LIVREE = "commande_livree";
    public static final String TYPE_COMMANDE_ANNULEE = "commande_annulee";
    /** Commande enregistrée : en attente de validation par un administrateur (stock non débité). */
    public static final String TYPE_COMMANDE_EN_ATTENTE_ADMIN = "commande_en_attente_admin";

    public List<OrderNotificationView> findUnreadOrderNotificationsForAdmin(int adminUserId) throws SQLException {
        String sql = "SELECT n.id AS notif_id, n.created_at, c.id AS commande_id, c.nom AS client_nom, c.total AS total, "
            + "c.statut AS statut_c, c.user_id AS client_uid, c.email AS c_email, c.telephone AS c_tel, "
            + "c.adresse AS c_adr, c.code_postal AS c_cp, c.ville AS c_ville, c.mode_payment AS c_pay "
            + "FROM `notification` n "
            + "JOIN `commande` c ON c.id = n.commande_id "
            + "WHERE n.destinataire_id = ? AND n.lu = 0 AND n.type = 'nouvelle_commande' "
            + "ORDER BY n.created_at DESC";
        List<OrderNotificationView> out = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, adminUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getObject("client_uid") != null ? rs.getInt("client_uid") : 0;
                    out.add(new OrderNotificationView(
                        rs.getInt("notif_id"),
                        rs.getInt("commande_id"),
                        rs.getString("client_nom"),
                        rs.getDouble("total"),
                        rs.getString("statut_c"),
                        uid,
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                        rs.getString("c_email"),
                        rs.getString("c_tel"),
                        rs.getString("c_adr"),
                        rs.getString("c_cp"),
                        rs.getString("c_ville"),
                        rs.getString("c_pay")
                    ));
                }
            }
        }
        return out;
    }

    public List<DemandeNotificationView> findUnreadDemandeNotificationsForAdmin(int adminUserId) throws SQLException {
        String sql = "SELECT n.id AS notif_id, n.created_at, d.id AS demande_id, d.nom AS nom_produit, d.demande_client AS demande_txt, d.statut AS statut_demande "
            + "FROM `notification` n "
            + "JOIN `demande_produit` d ON d.id = n.demande_produit_id "
            + "WHERE n.destinataire_id = ? AND n.lu = 0 AND n.type = 'nouvelle_demande_produit' "
            + "ORDER BY n.created_at DESC";
        List<DemandeNotificationView> out = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, adminUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DemandeNotificationView(
                        rs.getInt("notif_id"),
                        rs.getInt("demande_id"),
                        rs.getString("nom_produit"),
                        rs.getString("demande_txt"),
                        rs.getString("statut_demande"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null
                    ));
                }
            }
        }
        return out;
    }

    /**
     * Notification pour le client (commande approuvée, livrée ou annulée par l’admin).
     */
    public void insertClientOrderNotification(int clientUserId, String type, int commandeId) throws SQLException {
        if (clientUserId <= 0 || commandeId <= 0) {
            return;
        }
        String sql = "INSERT INTO `notification`(type, lu, created_at, destinataire_id, commande_id) VALUES(?,?,?,?,?)";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setBoolean(2, false);
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(4, clientUserId);
            ps.setInt(5, commandeId);
            ps.executeUpdate();
        }
    }

    public int countUnreadClientOrderNotifications(int userId) throws SQLException {
        if (userId <= 0) {
            return 0;
        }
        String sql = "SELECT COUNT(*) AS c FROM `notification` WHERE destinataire_id=? AND lu=0 AND type IN (?,?,?)";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, TYPE_COMMANDE_VALIDEE);
            ps.setString(3, TYPE_COMMANDE_LIVREE);
            ps.setString(4, TYPE_COMMANDE_ANNULEE);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("c");
                }
            }
        }
        return 0;
    }

    public List<ClientOrderNotificationView> findUnreadClientOrderNotifications(int userId) throws SQLException {
        List<ClientOrderNotificationView> out = new ArrayList<>();
        if (userId <= 0) {
            return out;
        }
        String sql = "SELECT n.id, n.type, n.created_at, n.commande_id, c.nom AS order_nom "
            + "FROM `notification` n "
            + "LEFT JOIN `commande` c ON c.id = n.commande_id "
            + "WHERE n.destinataire_id=? AND n.lu=0 AND n.type IN (?,?,?) ORDER BY n.created_at DESC";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, TYPE_COMMANDE_VALIDEE);
            ps.setString(3, TYPE_COMMANDE_LIVREE);
            ps.setString(4, TYPE_COMMANDE_ANNULEE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ClientOrderNotificationView(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getInt("commande_id"),
                        rs.getString("order_nom"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null
                    ));
                }
            }
        }
        return out;
    }

    /**
     * Texte affiché au client (sans numéro de commande : uniquement le nom de livraison).
     */
    public static String messageForClientOrderType(String type, String orderNomLivraison) {
        if (type == null) {
            return "";
        }
        String nom = (orderNomLivraison != null && !orderNomLivraison.isBlank())
            ? orderNomLivraison.trim()
            : "votre commande";
        return switch (type) {
            case TYPE_COMMANDE_VALIDEE -> "La commande « " + nom + " » a été validée et sera préparée.";
            case TYPE_COMMANDE_LIVREE -> "Bonne nouvelle : la commande « " + nom + " » a été livrée.";
            case TYPE_COMMANDE_ANNULEE -> "La commande « " + nom + " » a été annulée par l’administration.";
            default -> "Mise à jour pour la commande « " + nom + " ».";
        };
    }

    public void deleteAdminNotificationsForCommande(int commandeId) throws SQLException {
        String sql = "DELETE FROM `notification` WHERE commande_id=? AND type='nouvelle_commande'";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            ps.executeUpdate();
        }
    }

    public void markNotificationsAsRead(List<Integer> notifIds) throws SQLException {
        if (notifIds == null || notifIds.isEmpty()) {
            return;
        }
        String sql = "UPDATE `notification` SET lu = 1 WHERE id = ?";
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Integer id : notifIds) {
                if (id == null) {
                    continue;
                }
                ps.setInt(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public record OrderNotificationView(
        int notificationId,
        int commandeId,
        String clientNom,
        double total,
        String statutCommande,
        int clientUserId,
        LocalDateTime createdAt,
        String email,
        String telephone,
        String adresse,
        String codePostal,
        String ville,
        String modePayment
    ) {}

    public record ClientOrderNotificationView(
        int notificationId,
        String type,
        int commandeId,
        String orderNomLivraison,
        LocalDateTime createdAt
    ) {}

    public record DemandeNotificationView(
        int notificationId,
        int demandeId,
        String nomProduit,
        String demandeClientText,
        String statutDemande,
        LocalDateTime createdAt
    ) {}

    /**
     * Détail d’une commande pour l’admin : lignes, prix, reste à vendre sur la fiche après la vente,
     * emplacement logistique (informatif — la réserve {@code stock.quantite} n’est pas modifiée à la vente).
     */
    public record OrderLineDetailRow(
        String productName,
        int quantityOrdered,
        double unitPrice,
        double lineTotal,
        int remainingSellableQuantity,
        String stockLocationName
    ) {}

    public List<OrderLineDetailRow> findOrderLineDetails(int commandeId) throws SQLException {
        String sql = "SELECT p.nom AS produit_nom, lc.quantite AS qty, lc.prix AS prix_u, lc.sous_total AS sous, "
            + "p.quantite AS reste_vendre, s.nom AS stock_nom "
            + "FROM `ligne_commande` lc "
            + "JOIN `produit` p ON p.id = lc.produit_id "
            + "LEFT JOIN `stock` s ON s.id = p.stock_id "
            + "WHERE lc.commande_id=? ORDER BY lc.id ASC";
        List<OrderLineDetailRow> out = new ArrayList<>();
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commandeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OrderLineDetailRow(
                        rs.getString("produit_nom"),
                        rs.getInt("qty"),
                        rs.getDouble("prix_u"),
                        rs.getDouble("sous"),
                        rs.getInt("reste_vendre"),
                        rs.getString("stock_nom")
                    ));
                }
            }
        }
        return out;
    }
}
