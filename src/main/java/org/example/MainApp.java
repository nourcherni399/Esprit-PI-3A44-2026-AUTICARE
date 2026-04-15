package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.controllers.AdminMyProfileController;
import org.example.models.Role;
import org.example.models.User;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;

import java.io.IOException;

public class MainApp extends Application {

    private static Stage primaryStage;
    private static final String DARK_THEME_STYLESHEET = MainApp.class.getResource("/styles/dark.css").toExternalForm();
    private static boolean darkModeEnabled = false;
    /** Onglet à afficher au chargement de {@code dashboard.fxml} (voir ordre des &lt;Tab&gt;). */
    private static int pendingDashboardTabIndex = 0;
    /** Section d’accueil à défiler après chargement (voir {@link org.example.controllers.HomeController}). */
    private static String pendingHomeScroll = null;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        showHome();
        primaryStage.setTitle("AutiCare Desktop");
        primaryStage.show();
    }

    /** Page d'accueil (défilement, sections, fond animé). */
    public static void showHome() throws IOException {
        Parent root = FXMLLoader.load(MainApp.class.getResource("/fxml/home.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /**
     * Ouvre l’accueil et demande un défilement vers une section après chargement
     * (clés : {@code produits}, {@code rdv}, {@code events}, {@code blog}, {@code contact}, {@code mission}, {@code community}).
     */
    public static void showHomeScrollTo(String sectionKey) throws IOException {
        pendingHomeScroll = sectionKey;
        showHome();
    }

    /** Utilisé par {@link org.example.controllers.HomeController} une fois le FXML initialisé. */
    public static String consumePendingHomeScroll() {
        String s = pendingHomeScroll;
        pendingHomeScroll = null;
        return s;
    }

    public static void showLogin() throws IOException {
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/user/login.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /** Tableau de bord (onglets CRUD) — premier onglet sélectionné. */
    public static void showDashboard() throws IOException {
        showDashboard(0);
    }

    /**
     * Tableau de bord avec onglet initial (0 = Utilisateurs, 1 = Produits, 4 = Événements, …).
     */
    public static void showDashboard(int tabIndex) throws IOException {
        pendingDashboardTabIndex = Math.max(0, tabIndex);
        Parent root = FXMLLoader.load(MainApp.class.getResource("/fxml/dashboard.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 960, 640);
    }

    /** Utilisé par {@link org.example.controllers.MainController} après chargement du FXML. */
    public static int getPendingDashboardTabIndex() {
        return pendingDashboardTabIndex;
    }

    /** Inscription (remplace l’usage de main.fxml comme écran d’inscription). */
    public static void showSignup() throws IOException {
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/user/signup.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /** Étape 1 : saisie email (mot de passe oublié). */
    public static void showForgotPassword() throws IOException {
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/user/forgotpassword.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /** Étape 2 : message envoyé, prochaine étape PIN. */
    public static void showResetPassword() throws IOException {
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/user/resetpassword.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /** Étape 3 : vérification du code PIN. */
    public static void showPin() throws IOException {
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/user/pin.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /** Étape 4 : saisie du nouveau mot de passe après PIN valide. */
    public static void showSetNewPassword() throws IOException {
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/user/setnewpassword.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /** Espace administrateur — gestion des utilisateurs. */
    public static void showAdminUsers() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-users.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Formulaire création d’utilisateur (admin). */
    public static void showAdminUserAdd() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-user-add.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Fiche détail utilisateur (admin). {@link AppState#getAdminDetailUser()} doit être renseigné. */
    public static void showAdminUserDetail() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        if (AppState.getAdminDetailUser() == null) {
            showAdminUsers();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-user-detail.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Formulaire modification d’utilisateur (admin). {@link AppState#getAdminEditUser()} doit être renseigné. */
    public static void showAdminUserEdit() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        if (AppState.getAdminEditUser() == null) {
            showAdminUsers();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-user-edit.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Confirmation suppression utilisateur. {@link AppState#getAdminDeleteUser()} doit être renseigné. */
    public static void showAdminUserDelete() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        if (AppState.getAdminDeleteUser() == null) {
            showAdminUsers();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-user-delete.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void toggleTheme() {
        darkModeEnabled = !darkModeEnabled;
        if (primaryStage != null) {
            applyThemeToScene(primaryStage.getScene());
        }
    }

    public static boolean isDarkModeEnabled() {
        return darkModeEnabled;
    }

    /**
     * Modale « Modifier mon profil » (clic sur le bloc profil de la barre admin).
     * Ne montre pas l’identifiant numérique de l’utilisateur.
     */
    public static void openAdminMyProfile(StackPane topbarAvatarHost, Label userNameLabel, Label userEmailLabel) throws IOException {
        User session = AppState.getCurrentUser();
        if (session == null) {
            return;
        }
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/front/admin/user/admin-my-profile.fxml"));
        Parent root = loader.load();
        AdminMyProfileController ctrl = loader.getController();
        Stage dialog = new Stage();
        ctrl.setStage(dialog);
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        if (primaryStage != null) {
            dialog.setWidth(primaryStage.getWidth());
            dialog.setHeight(primaryStage.getHeight());
            dialog.setX(primaryStage.getX());
            dialog.setY(primaryStage.getY());
        }
        dialog.showAndWait();
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
    }

    /** @deprecated utiliser {@link #showDashboard()} */
    @Deprecated
    public static void showMain() throws IOException {
        showDashboard();
    }

    private static void applyScenePreservingWindowState(
            Parent root,
            double sceneWidth,
            double sceneHeight,
            double minWidth,
            double minHeight
    ) {
        if (primaryStage == null) {
            return;
        }
        boolean wasFullScreen = primaryStage.isFullScreen();
        boolean wasMaximized = primaryStage.isMaximized();
        double currentX = primaryStage.getX();
        double currentY = primaryStage.getY();
        double currentWidth = primaryStage.getWidth();
        double currentHeight = primaryStage.getHeight();

        primaryStage.setScene(new Scene(root, sceneWidth, sceneHeight));
        applyThemeToScene(primaryStage.getScene());
        primaryStage.setMinWidth(minWidth);
        primaryStage.setMinHeight(minHeight);

        if (!wasFullScreen && !wasMaximized && currentWidth > 0 && currentHeight > 0) {
            if (!Double.isNaN(currentX)) {
                primaryStage.setX(currentX);
            }
            if (!Double.isNaN(currentY)) {
                primaryStage.setY(currentY);
            }
            primaryStage.setWidth(currentWidth);
            primaryStage.setHeight(currentHeight);
        }

        // Re-apply on next pulse too: some platforms reset state right after setScene.
        Runnable restore = () -> {
            primaryStage.setMaximized(wasMaximized);
            primaryStage.setFullScreen(wasFullScreen);
            if (!wasFullScreen && !wasMaximized && currentWidth > 0 && currentHeight > 0) {
                if (!Double.isNaN(currentX)) {
                    primaryStage.setX(currentX);
                }
                if (!Double.isNaN(currentY)) {
                    primaryStage.setY(currentY);
                }
                primaryStage.setWidth(currentWidth);
                primaryStage.setHeight(currentHeight);
            }
        };
        restore.run();
        Platform.runLater(restore);
    }

    private static void applyThemeToScene(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().remove(DARK_THEME_STYLESHEET);
        if (darkModeEnabled) {
            scene.getStylesheets().add(DARK_THEME_STYLESHEET);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
