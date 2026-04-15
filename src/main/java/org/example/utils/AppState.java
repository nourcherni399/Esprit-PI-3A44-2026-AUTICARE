package org.example.utils;

import org.example.models.User;

public class AppState {
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
        adminDetailUser = null;
        clearAdminEditContext();
        clearAdminDeleteContext();
        clearPendingPublicRdvBooking();
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

    private static String pendingPublicRdvDoctorName;
    private static int pendingPublicRdvDoctorId = -1;
    /** Créneau choisi à l’étape 1 (réaffichage si retour depuis l’étape 2). */
    private static int pendingPublicRdvAvailabilityId = -1;
    private static String pendingPublicRdvConsultTypeLabel;
    private static String pendingPublicRdvMotif;

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
