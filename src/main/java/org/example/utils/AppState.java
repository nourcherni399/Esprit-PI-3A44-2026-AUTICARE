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
}
