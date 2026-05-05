package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.controllers.AdminMyProfileController;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.EmailVerificationCallbackServer;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.WindowsFirewallPortOpener;

import java.io.IOException;
import java.net.URL;

public class MainApp extends Application {

    private static Stage primaryStage;
    /** Écoute les clics sur le lien d’activation email (ex. {@code http://127.0.0.1:8899/verify-email?token=...}). */
    private EmailVerificationCallbackServer emailVerificationCallbackServer;
    private static final String DARK_THEME_STYLESHEET = MainApp.class.getResource("/styles/dark.css").toExternalForm();
    /** Curseur « main » sur boutons, onglets, listes, etc. (voir {@code /styles/cursor-pointer.css}). */
    private static final String CURSOR_POINTER_STYLESHEET =
            MainApp.class.getResource("/styles/cursor-pointer.css").toExternalForm();
    private static boolean darkModeEnabled = false;
    /** Onglet à afficher au chargement de {@code dashboard.fxml} (voir ordre des &lt;Tab&gt;). */
    private static int pendingDashboardTabIndex = 0;
    /** Section d’accueil à défiler après chargement (voir {@link org.example.controllers.HomeController}). */
    private static String pendingHomeScroll = null;

    /** Page initiale pour {@link org.example.controllers.PublicShellController} (produits, rdv, events, blog). */
    private static String pendingPublicPage = "produits";
    /**
     * Fenêtre agrandie comme le bouton « plein cadre » Windows : {@code setMaximized(true)} (échelle / rendu natifs).
     * Le bandeau bas est gardé lisible via le layout (hero + {@code .home-root} dans le CSS), pas via un redimensionnement manuel.
     */
    private static final boolean FORCE_MAXIMIZED_WINDOW = true;
    private static boolean maximizedGuardInstalled = false;
    /**
     * Gabarit de secours si la fenêtre n’est pas maximisée (ex. désactivation du flag ci-dessus).
     */
    private static final double DEFAULT_WINDOW_WIDTH = 1360;
    private static final double DEFAULT_WINDOW_HEIGHT = 760;

    @Override
    public void start(Stage stage) throws IOException {
        WindowsFirewallPortOpener.ensureCheckinPortOpen();
        primaryStage = stage;
        installMaximizedGuard();
        try {
            emailVerificationCallbackServer = new EmailVerificationCallbackServer();
            emailVerificationCallbackServer.start();
        } catch (Exception e) {
            System.err.println("AutiCare: serveur d'activation email non démarré (port 8899 occupé ou refusé ?) — "
                    + "les liens dans les mails ne fonctionneront pas tant que l'app ne peut pas écouter ce port. "
                    + e.getMessage());
        }
        showHome();
        primaryStage.setTitle("AutiCare Desktop");
        primaryStage.show();
        scheduleMaximizedEnforcement();
    }

    @Override
    public void stop() throws Exception {
        if (emailVerificationCallbackServer != null) {
            emailVerificationCallbackServer.close();
            emailVerificationCallbackServer = null;
        }
        super.stop();
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

    /**
     * Affiche la coque publique (même habillage que l’accueil) avec la page indiquée.
     *
     * @param pageId {@code produits}, {@code rdv}, {@code events}, {@code blog} (insensible à la casse)
     */
    public static void showPublicPage(String pageId) throws IOException {
        pendingPublicPage = pageId != null && !pageId.isBlank() ? pageId.trim() : "produits";
        Parent root = FXMLLoader.load(MainApp.class.getResource("/fxml/public-shell.fxml"));
        applyScenePreservingWindowState(root, 1200, 720, 960, 640);
    }

    /** Utilisé par {@link org.example.controllers.PublicShellController} au chargement du FXML. */
    public static String consumePendingPublicPage() {
        String p = pendingPublicPage;
        pendingPublicPage = "produits";
        return p;
    }

    public static void showLogin() throws IOException {
        showPublicPage("login");
    }

    /** Tableau de bord (onglets CRUD) — premier onglet sélectionné. */
    public static void showDashboard() throws IOException {
        showDashboard(0);
    }

    /**
     * Tableau de bord avec onglet initial (0 = Utilisateurs, 1 = Produits, 4 = Événements, …).
     */
    public static void showDashboard(int tabIndex) throws IOException {
        User session = AppState.getCurrentUser();
        if (session != null && session.getRole() == Role.MEDECIN) {
            showMedecinDashboard();
            return;
        }
        pendingDashboardTabIndex = Math.max(0, tabIndex);
        URL dashboardUrl = MainApp.class.getResource("/fxml/dashboard.fxml");
        if (dashboardUrl == null) {
            throw new IOException("Ressource introuvable : /fxml/dashboard.fxml (vérifiez le JAR ou mvn compile).");
        }
        Parent root = FXMLLoader.load(dashboardUrl);
        applyScenePreservingWindowState(root, 1280, 760, 960, 640);
    }

    /**
     * Portail médecin (rôle {@link Role#MEDECIN} uniquement).
     */
    public static void showMedecinDashboard() throws IOException {
        User u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.MEDECIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/fxml/medecin-dashboard.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /**
     * Lien « Gestion » / profil depuis le site : admin → gestion utilisateurs,
     * médecin → portail médecin, autres rôles → tableau de bord CRUD.
     */
    public static void openManagementSpace() throws IOException {
        User u = AppState.getCurrentUser();
        if (u == null) {
            showLogin();
            return;
        }
        switch (u.getRole()) {
            case ADMIN -> showAdminUsers();
            case MEDECIN -> showMedecinDashboard();
            default -> showDashboard();
        }
    }

    /** Utilisé par {@link org.example.controllers.MainController} après chargement du FXML. */
    public static int getPendingDashboardTabIndex() {
        return pendingDashboardTabIndex;
    }

    /**
     * À appeler <strong>avant</strong> le premier {@code FXMLLoader.load(dashboard.fxml)} lorsque le tableau de bord
     * est embarqué dans l’admin (ex. barre latérale) pour sélectionner le bon onglet au {@code initialize()}.
     */
    public static void prepareEmbeddedDashboardTab(int tabIndex) {
        pendingDashboardTabIndex = Math.max(0, tabIndex);
    }

    /** Inscription : même coque publique que l'accueil (nav, fond). */
    public static void showSignup() throws IOException {
        showPublicPage("signup");
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

    /** Espace administrateur — gestion des modules (CRUD). */
    public static void showAdminModules() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-modules.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Espace administrateur — gestion des produits. */
    public static void showAdminProducts() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-products.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Espace administrateur — gestion du stock. */
    public static void showAdminStocks() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-stocks.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Espace administrateur — gestion des commandes. */
    public static void showAdminOrders() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-orders.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Espace administrateur — validation des demandes produit. */
    public static void showAdminDemandesProduit() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-demandes-produit.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Formulaire création d’un module (admin). */
    public static void showAdminModuleAdd() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        AppState.clearAdminModuleEditContext();
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-module-add.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Formulaire creation d'une ressource (admin). */
    public static void showAdminRessourceAdd() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-ressource-add.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Formulaire modification d'une ressource (admin). */
    public static void showAdminRessourceEdit() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        if (AppState.getAdminEditRessource() == null) {
            showAdminModules();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-ressource-edit.fxml"));
        applyScenePreservingWindowState(root, 1280, 760, 1024, 640);
    }

    /** Formulaire modification d’un module (admin). {@link AppState#getAdminEditModule()} doit être renseigné. */
    public static void showAdminModuleEdit() throws IOException {
        var u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.ADMIN) {
            showLogin();
            return;
        }
        if (AppState.getAdminEditModule() == null) {
            showAdminModules();
            return;
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("/front/admin/user/admin-module-edit.fxml"));
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
        applyThemeToScene(scene);
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
        if (FORCE_MAXIMIZED_WINDOW) {
            installMaximizedGuard();
            double currentWidth = primaryStage.getWidth();
            double currentHeight = primaryStage.getHeight();
            double appliedWidth = hasReliableWindowBounds(currentWidth, currentHeight) ? currentWidth : sceneWidth;
            double appliedHeight = hasReliableWindowBounds(currentWidth, currentHeight) ? currentHeight : sceneHeight;
            // Conserve le rendu actuel de l'accueil tout en gardant l'état "agrandi".
            primaryStage.setScene(new Scene(root, appliedWidth, appliedHeight));
            applyThemeToScene(primaryStage.getScene());
            primaryStage.setMinWidth(minWidth);
            primaryStage.setMinHeight(minHeight);
            scheduleMaximizedEnforcement();
            return;
        }
        boolean wasFullScreen = primaryStage.isFullScreen();
        boolean wasMaximized = primaryStage.isMaximized();
        double currentX = primaryStage.getX();
        double currentY = primaryStage.getY();
        double currentWidth = primaryStage.getWidth();
        double currentHeight = primaryStage.getHeight();
        boolean boundsReliable = hasReliableWindowBounds(currentWidth, currentHeight);

        primaryStage.setScene(new Scene(root, sceneWidth, sceneHeight));
        applyThemeToScene(primaryStage.getScene());
        primaryStage.setMinWidth(minWidth);
        primaryStage.setMinHeight(minHeight);

        if (!wasFullScreen && !wasMaximized) {
            if (boundsReliable) {
                if (!Double.isNaN(currentX)) {
                    primaryStage.setX(currentX);
                }
                if (!Double.isNaN(currentY)) {
                    primaryStage.setY(currentY);
                }
                primaryStage.setWidth(currentWidth);
                primaryStage.setHeight(currentHeight);
            } else {
                applyDefaultWindowBounds();
            }
        }

        // Re-apply on next pulse too: some platforms reset state right after setScene.
        Runnable restore = () -> {
            primaryStage.setMaximized(wasMaximized);
            primaryStage.setFullScreen(wasFullScreen);
            if (!wasFullScreen && !wasMaximized) {
                if (boundsReliable) {
                    if (!Double.isNaN(currentX)) {
                        primaryStage.setX(currentX);
                    }
                    if (!Double.isNaN(currentY)) {
                        primaryStage.setY(currentY);
                    }
                    primaryStage.setWidth(currentWidth);
                    primaryStage.setHeight(currentHeight);
                } else {
                    applyDefaultWindowBounds();
                }
            }
        };
        restore.run();
        Platform.runLater(restore);
    }

    private static boolean hasReliableWindowBounds(double width, double height) {
        if (Double.isNaN(width) || Double.isNaN(height)) {
            return false;
        }
        return width > 1 && height > 1;
    }

    /** Centre la fenêtre avec un gabarit fixe, sans dépasser les bords visibles de l’écran. */
    private static void applyDefaultWindowBounds() {
        if (primaryStage == null) {
            return;
        }
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double w = Math.min(DEFAULT_WINDOW_WIDTH, vb.getWidth());
        double h = Math.min(DEFAULT_WINDOW_HEIGHT, vb.getHeight());
        primaryStage.setWidth(w);
        primaryStage.setHeight(h);
        primaryStage.setX(vb.getMinX() + (vb.getWidth() - w) / 2.0);
        primaryStage.setY(vb.getMinY() + (vb.getHeight() - h) / 2.0);
    }

    private static void installMaximizedGuard() {
        if (!FORCE_MAXIMIZED_WINDOW || primaryStage == null || maximizedGuardInstalled) {
            return;
        }
        maximizedGuardInstalled = true;
        primaryStage.maximizedProperty().addListener((obs, was, isNow) -> {
            if (!Boolean.TRUE.equals(isNow)) {
                scheduleMaximizedEnforcement();
            }
        });
        primaryStage.iconifiedProperty().addListener((obs, was, isNow) -> {
            if (!Boolean.TRUE.equals(isNow)) {
                scheduleMaximizedEnforcement();
            }
        });
    }

    private static void forceMaximizedStage() {
        if (primaryStage == null || !FORCE_MAXIMIZED_WINDOW) {
            return;
        }
        primaryStage.setFullScreen(false);
        primaryStage.setMaximized(true);
    }

    private static void scheduleMaximizedEnforcement() {
        if (primaryStage == null || !FORCE_MAXIMIZED_WINDOW) {
            return;
        }
        forceMaximizedStage();
        Platform.runLater(MainApp::forceMaximizedStage);
    }

    /**
     * Applique le thème sombre sur la racine de la scène (dernière feuille du {@link Parent} racine),
     * pas seulement sur la {@link Scene} : en JavaFX, les feuilles des parents plus profonds
     * (ex. VBox login embarquée) s’appliquent après celles de la scène et écrasaient {@code dark.css}.
     */
    /** Réapplique le thème (ex. modales créées hors {@link #applyScenePreservingWindowState}). */
    public static void applyThemeToScene(Scene scene) {
        if (scene == null) {
            return;
        }
        if (!scene.getStylesheets().contains(CURSOR_POINTER_STYLESHEET)) {
            scene.getStylesheets().add(0, CURSOR_POINTER_STYLESHEET);
        }
        scene.getStylesheets().remove(DARK_THEME_STYLESHEET);
        Node root = scene.getRoot();
        if (root instanceof Parent parent) {
            parent.getStylesheets().remove(DARK_THEME_STYLESHEET);
            if (darkModeEnabled) {
                parent.getStylesheets().add(DARK_THEME_STYLESHEET);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
