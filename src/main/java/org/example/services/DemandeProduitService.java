package org.example.services;

import org.example.models.DemandeProduit;
import org.example.models.Product;
import org.example.models.Role;
import org.example.models.User;
import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Demandes de fiches produit ({@code demande_produit}), aligné Symfony.
 */
public class DemandeProduitService {

    public static final String STATUT_EN_ATTENTE = "en_attente";
    public static final String STATUT_APPROUVE = "approuve";
    public static final String STATUT_REJETE = "rejete";

    public List<DemandeProduit> findAll() throws SQLException {
        List<DemandeProduit> list = new ArrayList<>();
        String sql = "SELECT id, demande_client, nom, description, categorie, prix_estime, budget_client, caracteristiques, "
            + "donnees_externes, statut, created_at, validated_at, demandeur_id, validated_by_id, produit_id "
            + "FROM `demande_produit` ORDER BY created_at DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public Optional<DemandeProduit> findById(int id) throws SQLException {
        String sql = "SELECT id, demande_client, nom, description, categorie, prix_estime, budget_client, caracteristiques, "
            + "donnees_externes, statut, created_at, validated_at, demandeur_id, validated_by_id, produit_id "
            + "FROM `demande_produit` WHERE id=?";
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

    public int insertDemandeSimple(
        String demandeClient,
        String nomSuggere,
        String descriptionSuggeree,
        String categorieDb,
        double prixEstime,
        Double budgetClient,
        Integer demandeurId
    ) throws SQLException {
        return insertDemandeSimple(demandeClient, nomSuggere, descriptionSuggeree, categorieDb, prixEstime, budgetClient, demandeurId, null);
    }

    /**
     * @param donneesExternesJson JSON optionnel (ex. {@code {"imageUrl":"https://..."}}) pour aperçu admin.
     */
    public int insertDemandeSimple(
        String demandeClient,
        String nomSuggere,
        String descriptionSuggeree,
        String categorieDb,
        double prixEstime,
        Double budgetClient,
        Integer demandeurId,
        String donneesExternesJson
    ) throws SQLException {
        String sql = "INSERT INTO `demande_produit`(demande_client, nom, description, categorie, prix_estime, budget_client, "
            + "caracteristiques, donnees_externes, statut, created_at, validated_at, demandeur_id, validated_by_id, produit_id) "
            + "VALUES(?,?,?,?,?,?,NULL,?,?,NOW(),NULL,?,NULL,NULL)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, demandeClient);
            ps.setString(2, nomSuggere);
            ps.setString(3, descriptionSuggeree);
            ps.setString(4, categorieDb);
            ps.setDouble(5, prixEstime);
            if (budgetClient != null) {
                ps.setDouble(6, budgetClient);
            } else {
                ps.setNull(6, Types.DOUBLE);
            }
            if (donneesExternesJson != null && !donneesExternesJson.isBlank()) {
                ps.setString(7, donneesExternesJson.trim());
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.setString(8, STATUT_EN_ATTENTE);
            if (demandeurId != null) {
                ps.setInt(9, demandeurId);
            } else {
                ps.setNull(9, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int newId = keys.getInt(1);
                    insertAdminNotificationsForDemande(newId);
                    return newId;
                }
            }
        }
        throw new SQLException("Insertion demande impossible.");
    }

    /** Une notification par administrateur (type {@code nouvelle_demande_produit}). */
    private static void insertAdminNotificationsForDemande(int demandeId) throws SQLException {
        UserService userService = new UserService();
        List<User> admins = userService.findByRole(Role.ADMIN);
        if (admins.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO `notification`(type, lu, created_at, destinataire_id, commande_id, demande_produit_id) "
            + "VALUES(?,?,?,?,NULL,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            for (User admin : admins) {
                ps.setString(1, "nouvelle_demande_produit");
                ps.setBoolean(2, false);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.setInt(4, admin.getId());
                ps.setInt(5, demandeId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void approuver(int id, int adminUserId) throws SQLException {
        Optional<DemandeProduit> before = findById(id);
        String sql = "UPDATE `demande_produit` SET statut=?, validated_at=NOW(), validated_by_id=? WHERE id=? AND statut=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, STATUT_APPROUVE);
            ps.setInt(2, adminUserId);
            ps.setInt(3, id);
            ps.setString(4, STATUT_EN_ATTENTE);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Demande introuvable ou déjà traitée.");
            }
        }
        if (before.isPresent()) {
            DemandeProduit d = before.get();
            new NotificationService().markAdminDemandeProduitNotificationsRead(id);
            notifyClientDemandeOutcome(d, true);
        }
    }

    public void rejeter(int id, int adminUserId) throws SQLException {
        Optional<DemandeProduit> before = findById(id);
        String sql = "UPDATE `demande_produit` SET statut=?, validated_at=NOW(), validated_by_id=? WHERE id=? AND statut=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, STATUT_REJETE);
            ps.setInt(2, adminUserId);
            ps.setInt(3, id);
            ps.setString(4, STATUT_EN_ATTENTE);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Demande introuvable ou déjà traitée.");
            }
        }
        if (before.isPresent()) {
            DemandeProduit d = before.get();
            new NotificationService().markAdminDemandeProduitNotificationsRead(id);
            notifyClientDemandeOutcome(d, false);
        }
    }

    private static void notifyClientDemandeOutcome(DemandeProduit d, boolean accepted) throws SQLException {
        Integer uid = d.demandeurId();
        if (uid == null || uid <= 0) {
            return;
        }
        String nom = d.nom() == null || d.nom().isBlank() ? "votre demande" : d.nom().trim();
        UserNotificationService uns = new UserNotificationService();
        if (accepted) {
            uns.addNotification(
                uid,
                UserNotificationService.TYPE_DEMANDE_PRODUIT_ACCEPTEE,
                null,
                "Bonne nouvelle : votre demande de produit « " + nom + " » a été acceptée. "
                    + "Elle sera ajoutée au catalogue dès qu’un administrateur aura créé la fiche produit."
            );
        } else {
            uns.addNotification(
                uid,
                UserNotificationService.TYPE_DEMANDE_PRODUIT_REFUSEE,
                null,
                "Votre demande de produit « " + nom + " » n’a pas été retenue par l’équipe AutiCare."
            );
        }
    }

    private static void notifyClientProduitPublie(DemandeProduit d, int produitId) throws SQLException {
        Integer uid = d.demandeurId();
        if (uid == null || uid <= 0 || produitId <= 0) {
            return;
        }
        String nom = d.nom() == null || d.nom().isBlank() ? "Votre produit" : d.nom().trim();
        new UserNotificationService().addNotification(
            uid,
            UserNotificationService.TYPE_DEMANDE_PRODUIT_PUBLIEE,
            null,
            "Le produit « " + nom + " » issu de votre demande est disponible dans la boutique."
        );
    }

    /**
     * Crée un {@link Product} depuis une demande approuvée et lie {@code produit_id}.
     */
    public int creerProduitDepuisDemande(int demandeId, int stockId, int quantiteCatalogue, int adminUserId) throws SQLException {
        DemandeProduit d = findById(demandeId).orElseThrow(() -> new SQLException("Demande introuvable."));
        if (!STATUT_APPROUVE.equals(d.statut())) {
            throw new SQLException("La demande doit être au statut « approuvé ».");
        }
        if (d.produitId() != null && d.produitId() > 0) {
            throw new SQLException("Un produit est déjà associé à cette demande.");
        }
        Product p = new Product();
        p.setNom(d.nom());
        p.setDescription(d.description() == null ? "" : d.description());
        p.setPrix(d.prixEstime());
        p.setCategorie(d.categorie());
        p.setStock(Math.max(1, quantiteCatalogue));
        p.setStockId(stockId);
        p.setImagePath(null);
        p.setDisponible(true);
        p.setPublie(true);
        p.setValide(true);
        p.setGenereParIa(true);
        p.setUserId(d.demandeurId() != null ? d.demandeurId() : adminUserId);
        p.setStatutPublication("publie");

        ProductService psvc = new ProductService();
        int newPid = psvc.addReturningId(p);

        String up = "UPDATE `demande_produit` SET produit_id=? WHERE id=?";
        try (PreparedStatement u = MyDatabase.getConnection().prepareStatement(up)) {
            u.setInt(1, newPid);
            u.setInt(2, demandeId);
            u.executeUpdate();
        }
        notifyClientProduitPublie(d, newPid);
        return newPid;
    }

    /**
     * Variante formulaire admin : création produit avec champs édités (incluant image),
     * puis liaison à la demande.
     */
    public int creerProduitDepuisDemandeAvecFormulaire(
        int demandeId,
        int stockId,
        int quantiteCatalogue,
        int adminUserId,
        String nom,
        String description,
        String categorie,
        double prix,
        String imagePath
    ) throws SQLException {
        DemandeProduit d = findById(demandeId).orElseThrow(() -> new SQLException("Demande introuvable."));
        if (!STATUT_APPROUVE.equals(d.statut())) {
            throw new SQLException("La demande doit être au statut « approuvé ».");
        }
        if (d.produitId() != null && d.produitId() > 0) {
            throw new SQLException("Un produit est déjà associé à cette demande.");
        }

        String nomFinal = (nom == null || nom.isBlank()) ? (d.nom() == null ? "Produit" : d.nom()) : nom.trim();
        String descFinal = description == null ? "" : description.trim();
        String catFinal = (categorie == null || categorie.isBlank()) ? d.categorie() : categorie.trim();
        String imgFinal = imagePath == null || imagePath.isBlank() ? null : imagePath.trim();
        double prixFinal = prix > 0 ? prix : d.prixEstime();

        Product p = new Product();
        p.setNom(nomFinal);
        p.setDescription(descFinal);
        p.setPrix(prixFinal);
        p.setCategorie(catFinal);
        p.setStock(Math.max(1, quantiteCatalogue));
        p.setStockId(stockId);
        p.setImagePath(imgFinal);
        p.setDisponible(true);
        p.setPublie(true);
        p.setValide(true);
        p.setGenereParIa(true);
        p.setUserId(d.demandeurId() != null ? d.demandeurId() : adminUserId);
        p.setStatutPublication("publie");

        ProductService psvc = new ProductService();
        int newPid = psvc.addReturningId(p);

        String up = "UPDATE `demande_produit` SET produit_id=? WHERE id=?";
        try (PreparedStatement u = MyDatabase.getConnection().prepareStatement(up)) {
            u.setInt(1, newPid);
            u.setInt(2, demandeId);
            u.executeUpdate();
        }
        notifyClientProduitPublie(d, newPid);
        return newPid;
    }

    private static DemandeProduit map(ResultSet rs) throws SQLException {
        return new DemandeProduit(
            rs.getInt("id"),
            rs.getString("demande_client"),
            rs.getString("nom"),
            rs.getString("description"),
            rs.getString("categorie"),
            rs.getDouble("prix_estime"),
            rs.getObject("budget_client") != null ? rs.getDouble("budget_client") : null,
            rs.getString("caracteristiques"),
            rs.getString("donnees_externes"),
            rs.getString("statut"),
            tsToLocal(rs.getTimestamp("created_at")),
            rs.getTimestamp("validated_at") != null ? tsToLocal(rs.getTimestamp("validated_at")) : null,
            rs.getObject("demandeur_id") != null ? rs.getInt("demandeur_id") : null,
            rs.getObject("validated_by_id") != null ? rs.getInt("validated_by_id") : null,
            rs.getObject("produit_id") != null ? rs.getInt("produit_id") : null
        );
    }

    private static LocalDateTime tsToLocal(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
