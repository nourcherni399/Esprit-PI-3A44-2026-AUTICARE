package org.example.utils;

import org.example.models.ModuleContent;
import org.example.models.Ressource;
import org.example.models.User;
import org.example.models.AppLanguage;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppState {
    private static AppLanguage currentLanguage = resolveDefaultLanguage();
    private static User currentUser;
    /** Utilisateur affiché sur l’écran admin « détail » (œil dans la liste). */
    private static User adminDetailUser;
    /** Utilisateur en cours d’édition (formulaire Modifier). */
    private static User adminEditUser;
    /** Si vrai, Retour / après enregistrement renvoie vers la fiche détail au lieu de la liste. */
    private static boolean adminEditReturnToDetail;
    /** Utilisateur ciblé par l’écran « Supprimer » (icône poubelle ou bouton sur la fiche). */
    private static User adminDeleteUser;
    /** Si vrai, « Annuler » sur l’écran suppression renvoie vers la fiche détail. */
    private static boolean adminDeleteReturnToDetail;
    /** Module en cours d’édition (écran « Modifier le module »). */
    private static ModuleContent adminEditModule;
    private static Ressource adminEditRessource;
    /** Section à ouvrir automatiquement dans l'espace admin utilisateurs (events/thematiques). */
    private static String pendingAdminUsersSection;
    /** Panier invité (clé = productId, valeur = quantity). */
    private static final Map<Integer, Integer> guestCart = new LinkedHashMap<>();
    /** Historique léger de comportement catalogue (clics/consultations panier) pour suggestions. */
    private static final Map<Integer, Integer> productInterestCounts = new LinkedHashMap<>();
    /** Listeners UI déclenchés après mise à jour du panier invité. */
    private static final List<Runnable> cartChangeListeners = new ArrayList<>();
    /** Brouillon de produit alimenté par l’assistant chat avant ouverture du formulaire admin. */
    private static AdminProductDraft pendingAdminProductDraft;
    /** Si vrai, ouvrir directement le formulaire produit admin (mode prérempli) au chargement. */
    private static boolean pendingOpenAdminProductEditor;

    public static final class AdminProductDraft {
        private final String nom;
        private final String description;
        private final String categorie;
        private final String prixText;
        private final String stockHint;
        private final String imagePath;

        public AdminProductDraft(String nom, String description, String categorie, String prixText, String stockHint, String imagePath) {
            this.nom = nom;
            this.description = description;
            this.categorie = categorie;
            this.prixText = prixText;
            this.stockHint = stockHint;
            this.imagePath = imagePath;
        }

        public String getNom() {
            return nom;
        }

        public String getDescription() {
            return description;
        }

        public String getCategorie() {
            return categorie;
        }

        public String getPrixText() {
            return prixText;
        }

        public String getStockHint() {
            return stockHint;
        }

        public String getImagePath() {
            return imagePath;
        }
    }

    private AppState() {
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User currentUser) {
        AppState.currentUser = currentUser;
    }

    public static void clear() {
        currentUser = null;
        currentLanguage = AppLanguage.FR;
        adminDetailUser = null;
        clearAdminEditContext();
        clearAdminDeleteContext();
        clearAdminModuleEditContext();
        clearAdminRessourceEditContext();
        clearPendingAdminUsersSection();
        clearPendingPublicRdvBooking();
        clearPendingPublicEventDetailId();
        clearPendingAdminProductDraft();
        clearPendingOpenAdminProductEditor();
        guestCartClear();
        clearProductInterest();
    }

    public static void setPendingAdminProductDraft(AdminProductDraft draft) {
        pendingAdminProductDraft = draft;
    }

    public static AdminProductDraft consumePendingAdminProductDraft() {
        AdminProductDraft draft = pendingAdminProductDraft;
        pendingAdminProductDraft = null;
        return draft;
    }

    public static void clearPendingAdminProductDraft() {
        pendingAdminProductDraft = null;
    }

    public static void requestOpenAdminProductEditor() {
        pendingOpenAdminProductEditor = true;
    }

    public static boolean consumePendingOpenAdminProductEditor() {
        boolean v = pendingOpenAdminProductEditor;
        pendingOpenAdminProductEditor = false;
        return v;
    }

    public static void clearPendingOpenAdminProductEditor() {
        pendingOpenAdminProductEditor = false;
    }

    public static AppLanguage getCurrentLanguage() {
        return currentLanguage != null ? currentLanguage : AppLanguage.FR;
    }

    public static void setCurrentLanguage(AppLanguage language) {
        currentLanguage = language != null ? language : AppLanguage.FR;
    }

    private static AppLanguage resolveDefaultLanguage() {
        String configured = LocalAiPropertiesFile.readProperty("app.default.language");
        return AppLanguage.fromCode(configured);
    }

    public static User getAdminDetailUser() {
        return adminDetailUser;
    }

    public static void setAdminDetailUser(User user) {
        adminDetailUser = user;
    }

    public static void clearAdminDetailUser() {
        adminDetailUser = null;
    }

    public static void beginAdminEdit(User user, boolean returnToDetailOnClose) {
        adminEditUser = user;
        adminEditReturnToDetail = returnToDetailOnClose;
    }

    public static User getAdminEditUser() {
        return adminEditUser;
    }

    public static boolean isAdminEditReturnToDetail() {
        return adminEditReturnToDetail;
    }

    public static void clearAdminEditContext() {
        adminEditUser = null;
        adminEditReturnToDetail = false;
    }

    public static void beginAdminDelete(User user, boolean returnToDetailOnCancel) {
        adminDeleteUser = user;
        adminDeleteReturnToDetail = returnToDetailOnCancel;
    }

    public static User getAdminDeleteUser() {
        return adminDeleteUser;
    }

    public static boolean isAdminDeleteReturnToDetail() {
        return adminDeleteReturnToDetail;
    }

    public static void clearAdminDeleteContext() {
        adminDeleteUser = null;
        adminDeleteReturnToDetail = false;
    }

    public static void beginAdminModuleEdit(ModuleContent module) {
        adminEditModule = module;
    }

    public static ModuleContent getAdminEditModule() {
        return adminEditModule;
    }

    public static void clearAdminModuleEditContext() {
        adminEditModule = null;
    }

    public static void beginAdminRessourceEdit(Ressource ressource) {
        adminEditRessource = ressource;
    }

    public static Ressource getAdminEditRessource() {
        return adminEditRessource;
    }

    public static void clearAdminRessourceEditContext() {
        adminEditRessource = null;
    }

    public static void setPendingAdminUsersSection(String sectionKey) {
        if (sectionKey == null || sectionKey.isBlank()) {
            pendingAdminUsersSection = null;
            return;
        }
        pendingAdminUsersSection = sectionKey.trim().toLowerCase();
    }

    public static String consumePendingAdminUsersSection() {
        String section = pendingAdminUsersSection;
        pendingAdminUsersSection = null;
        return section;
    }

    public static void clearPendingAdminUsersSection() {
        pendingAdminUsersSection = null;
    }

    private static String pendingPublicRdvDoctorName;
    private static int pendingPublicRdvDoctorId = -1;
    /** Créneau choisi à l’étape 1 (réaffichage si retour depuis l’étape 2). */
    private static int pendingPublicRdvAvailabilityId = -1;
    private static String pendingPublicRdvConsultTypeLabel;
    private static String pendingPublicRdvMotif;

    /** > 0 : ouvrir la fiche événement public après chargement de {@code page-event-detail.fxml}. */
    private static int pendingPublicEventDetailId = -1;

    public static void setPendingPublicEventDetailId(int eventId) {
        pendingPublicEventDetailId = eventId > 0 ? eventId : -1;
    }

    public static int getPendingPublicEventDetailId() {
        return pendingPublicEventDetailId;
    }

    public static int consumePendingPublicEventDetailId() {
        int id = pendingPublicEventDetailId;
        pendingPublicEventDetailId = -1;
        return id;
    }

    public static void clearPendingPublicEventDetailId() {
        pendingPublicEventDetailId = -1;
    }

    public static void beginPublicRdvBooking(int doctorId, String displayName) {
        pendingPublicRdvDoctorId = doctorId;
        pendingPublicRdvDoctorName = displayName != null ? displayName : "";
        pendingPublicRdvAvailabilityId = -1;
        pendingPublicRdvConsultTypeLabel = null;
        pendingPublicRdvMotif = null;
    }

    public static int getPendingPublicRdvDoctorId() {
        return pendingPublicRdvDoctorId;
    }

    public static String getPendingPublicRdvDoctorName() {
        return pendingPublicRdvDoctorName != null ? pendingPublicRdvDoctorName : "";
    }

    public static void clearPendingPublicRdvBooking() {
        pendingPublicRdvDoctorId = -1;
        pendingPublicRdvDoctorName = null;
        pendingPublicRdvAvailabilityId = -1;
        pendingPublicRdvConsultTypeLabel = null;
        pendingPublicRdvMotif = null;
    }

    public static Map<Integer, Integer> guestCartSnapshot() {
        return new LinkedHashMap<>(guestCart);
    }

    public static int guestCartGetQty(int productId) {
        return guestCart.getOrDefault(productId, 0);
    }

    public static void guestCartAdd(int productId, int qty) {
        if (qty <= 0) return;
        guestCart.put(productId, guestCartGetQty(productId) + qty);
    }

    public static void guestCartSetQty(int productId, int qty) {
        if (qty <= 0) {
            guestCart.remove(productId);
            return;
        }
        guestCart.put(productId, qty);
    }

    public static void guestCartRemove(int productId) {
        guestCart.remove(productId);
    }

    public static void guestCartClear() {
        guestCart.clear();
    }

    public static void addCartChangeListener(Runnable listener) {
        if (listener != null) {
            cartChangeListeners.add(listener);
        }
    }

    public static void removeCartChangeListener(Runnable listener) {
        cartChangeListeners.remove(listener);
    }

    public static void notifyCartChanged() {
        for (Runnable listener : List.copyOf(cartChangeListeners)) {
            try {
                listener.run();
            } catch (Exception ignored) {
                // Do not block cart updates if a listener fails.
            }
        }
    }

    public static void markProductInterest(int productId) {
        if (productId <= 0) {
            return;
        }
        productInterestCounts.put(productId, productInterestCounts.getOrDefault(productId, 0) + 1);
    }

    public static Map<Integer, Integer> productInterestSnapshot() {
        return new LinkedHashMap<>(productInterestCounts);
    }

    public static void clearProductInterest() {
        productInterestCounts.clear();
    }

    public static int getPendingPublicRdvAvailabilityId() {
        return pendingPublicRdvAvailabilityId;
    }

    public static void setPendingPublicRdvAvailabilityId(int availabilityId) {
        pendingPublicRdvAvailabilityId = availabilityId;
    }

    public static String getPendingPublicRdvConsultTypeLabel() {
        return pendingPublicRdvConsultTypeLabel != null ? pendingPublicRdvConsultTypeLabel : "";
    }

    public static void setPendingPublicRdvConsultTypeLabel(String label) {
        pendingPublicRdvConsultTypeLabel = label;
    }

    public static String getPendingPublicRdvMotif() {
        return pendingPublicRdvMotif != null ? pendingPublicRdvMotif : "";
    }

    public static void setPendingPublicRdvMotif(String motif) {
        pendingPublicRdvMotif = motif;
    }
}
