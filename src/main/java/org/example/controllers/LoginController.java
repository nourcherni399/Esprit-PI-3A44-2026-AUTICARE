package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.example.MainApp;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.FaceBiometricException;
import org.example.services.FaceIdClientService;
import org.example.services.FaceIdConfig;
import org.example.services.FaceIdentifyMatch;
import org.example.services.GoogleOAuthService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.FaceCameraCapture;
import org.example.utils.SqlConnectivityErrors;
import org.example.utils.PasswordRecoveryState;
import org.example.utils.PasswordUtil;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class LoginController implements PublicShellAware {

    private PublicShellController shell;

    @FXML
    private VBox loginPageRoot;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordVisibleField;
    @FXML
    private Label pwToggleLabel;
    @FXML
    private ComboBox<String> langCombo;
    @FXML
    private VBox loginPane;
    @FXML
    private VBox forgotPane1;
    @FXML
    private VBox forgotPane2;
    @FXML
    private VBox forgotPane3;
    @FXML
    private TextField forgotEmailField;
    @FXML
    private TextField forgotVerifyEmailField;
    @FXML
    private TextField pinCodeField;

    private final UserService userService = new UserService();
    private boolean passwordVisible;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    public void initialize() {
        boolean embedded = loginPageRoot != null;
        if (!embedded && langCombo != null) {
            langCombo.getItems().addAll("FR", "EN");
            langCombo.getSelectionModel().selectFirst();
        }
        if (!embedded) {
            AuthPageController.attachAnimatedAuthBackground(langCombo);
        }
        if (passwordVisibleField != null) {
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
        }
    }

    private void navigateToPublicPage(String pageId) {
        try {
            if (shell != null) {
                shell.loadPage(pageId);
            } else {
                MainApp.showPublicPage(pageId);
            }
        } catch (IOException e) {
            show(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    @FXML
    public void onLogin() {
        try {
            String email = emailField.getText() != null ? emailField.getText().trim() : "";
            String pwd = passwordVisible ? passwordVisibleField.getText() : passwordField.getText();
            var account = userService.findByEmail(email);
            if (account.isEmpty()) {
                show(Alert.AlertType.INFORMATION, "Échec", "Email ou mot de passe invalide.");
                return;
            }
            var cand = account.get();
            if (!cand.isActif()) {
                show(Alert.AlertType.WARNING, "Compte désactivé",
                        "Ce compte n'est pas activé (is_active = 0 en base). Activez-le dans MySQL ou utilisez un autre utilisateur.");
                return;
            }
            if (!PasswordUtil.matches(pwd, cand.getMotDePasseHash())) {
                show(Alert.AlertType.INFORMATION, "Échec", "Email ou mot de passe invalide.");
                return;
            }
            routeAfterSuccessfulLogin(cand);
        } catch (Exception e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void routeAfterSuccessfulLogin(User u) throws IOException {
        AppState.setCurrentUser(u);
        if (u.getRole() == Role.ADMIN) {
            MainApp.showAdminUsers();
        } else if (u.getRole() == Role.MEDECIN) {
            MainApp.showMedecinDashboard();
        } else {
            if (AppState.getPendingPublicEventDetailId() > 0) {
                MainApp.showPublicPage("event-detail");
                return;
            }
            MainApp.showHome();
        }
    }

    private Window loginOwnerWindow() {
        if (loginPageRoot != null && loginPageRoot.getScene() != null && loginPageRoot.getScene().getWindow() != null) {
            return loginPageRoot.getScene().getWindow();
        }
        if (emailField != null && emailField.getScene() != null && emailField.getScene().getWindow() != null) {
            return emailField.getScene().getWindow();
        }
        return MainApp.getPrimaryStage();
    }

    @FXML
    public void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            passwordVisibleField.setText(passwordField.getText());
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            if (pwToggleLabel != null) {
                pwToggleLabel.setText("🙈");
            }
        } else {
            passwordField.setText(passwordVisibleField.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            if (pwToggleLabel != null) {
                pwToggleLabel.setText("👁");
            }
        }
    }

    /** Affiche le flux « mot de passe oublié » intégré dans la carte de connexion. */
    @FXML
    public void onForgotPassword() {
        PasswordRecoveryState.clear();
        try {
            MainApp.showForgotPassword();
        } catch (IOException e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onForgotResetPassword() {
        String email = forgotEmailField != null && forgotEmailField.getText() != null
                ? forgotEmailField.getText().trim()
                : "";
        if (email.isEmpty()) {
            show(Alert.AlertType.WARNING, "Champ requis", "Indiquez votre email ou nom d'utilisateur.");
            return;
        }
        PasswordRecoveryState.setEmailOrUsername(email);
        setPaneVisible(forgotPane1, false);
        setPaneVisible(forgotPane2, true);
        setPaneVisible(forgotPane3, false);
    }

    @FXML
    public void onForgotGoToPin() {
        setPaneVisible(forgotPane2, false);
        setPaneVisible(forgotPane3, true);
        if (forgotVerifyEmailField != null) {
            forgotVerifyEmailField.setText(PasswordRecoveryState.getEmailOrUsername());
        }
    }

    @FXML
    public void onForgotVerifyPin() {
        String pin = pinCodeField != null && pinCodeField.getText() != null
                ? pinCodeField.getText().trim().replaceAll("\\s+", "")
                : "";
        if (pin.length() != 6 || !pin.chars().allMatch(Character::isDigit)) {
            show(Alert.AlertType.WARNING, "PIN", "Entrez un code à 6 chiffres.");
            return;
        }
        show(Alert.AlertType.INFORMATION, "Vérification",
                "Code accepté (démonstration — aucune API réelle).");
        PasswordRecoveryState.clear();
        onBackToLogin();
    }

    @FXML
    public void onForgotResendPin() {
        show(Alert.AlertType.INFORMATION, "Code renvoyé", "Un nouveau code a été envoyé (démonstration).");
    }

    private static void setPaneVisible(VBox pane, boolean visible) {
        if (pane != null) {
            pane.setVisible(visible);
            pane.setManaged(visible);
        }
    }

    /** Retour au formulaire principal de connexion (écrans « mot de passe oublié » intégrés). */
    @FXML
    public void onBackToLogin() {
        PasswordRecoveryState.clear();
        if (loginPane != null) {
            loginPane.setVisible(true);
            loginPane.setManaged(true);
        }
        if (forgotPane1 != null) {
            forgotPane1.setVisible(false);
            forgotPane1.setManaged(false);
        }
        if (forgotPane2 != null) {
            forgotPane2.setVisible(false);
            forgotPane2.setManaged(false);
        }
        if (forgotPane3 != null) {
            forgotPane3.setVisible(false);
            forgotPane3.setManaged(false);
        }
    }

    @FXML
    public void onGoogleSignIn() {
        GoogleOAuthService google = new GoogleOAuthService();
        try {
            var u = google.signInWithGoogle();
            routeAfterSuccessfulLogin(u);
        } catch (Exception e) {
            boolean db = SqlConnectivityErrors.isLikelyDbConnectivity(e);
            String title = db ? "Base de données (MySQL)" : "Google OAuth";
            StringBuilder msg = new StringBuilder(256);
            if (db) {
                msg.append("L’authentification Google a probablement réussi, mais l’application ne peut pas joindre MySQL ")
                        .append("(recherche ou création du compte).\n\n")
                        .append("Démarrez le serveur MySQL et vérifiez jdbc.url / utilisateur dans application.properties ")
                        .append("(ou votre fichier local équivalent).\n\n");
            } else {
                msg.append("Échec connexion Google : ");
            }
            msg.append(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            if (!db) {
                msg.append("\nredirectUri utilisee: ").append(google.getConfiguredRedirectUri())
                        .append("\nclientId: ").append(google.getConfiguredClientIdMasked());
            }
            show(Alert.AlertType.ERROR, title, msg.toString());
        }
    }

    @FXML
    public void onFaceIdSignIn() {
        FaceIdConfig faceCfg = new FaceIdConfig();
        if (!faceCfg.isEnabled()) {
            show(Alert.AlertType.INFORMATION, "Face ID",
                    "La connexion par visage est désactivée (faceid.enabled=false dans application.properties).");
            return;
        }
        FaceIdClientService faceClient = new FaceIdClientService(faceCfg);
        if (faceCfg.isRequireHealthy() && !faceClient.isHealthy()) {
            show(Alert.AlertType.WARNING, "Service Face ID",
                    "Le service visage ne répond pas sur "
                            + faceCfg.serviceUrl()
                            + "\n\nDémarrez le service Python (face-service) sur ce port, ou mettez faceid.requireHealthy=false pour tenter quand même.");
            return;
        }
        List<User> candidates;
        try {
            candidates = userService.findUsersWithFaceEnrollment();
        } catch (SQLException e) {
            boolean db = SqlConnectivityErrors.isLikelyDbConnectivity(e);
            show(Alert.AlertType.ERROR, db ? "Base de données (MySQL)" : "Erreur",
                    db ? "Impossible de charger les comptes avec visage enregistré.\n\n" + e.getMessage()
                            : (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return;
        }
        if (candidates.isEmpty()) {
            show(Alert.AlertType.INFORMATION, "Face ID",
                    "Aucun compte n’a encore enrôlé un visage. Connectez-vous par email puis enregistrez votre visage depuis votre profil.");
            return;
        }
        Optional<File> probe;
        try {
            probe = FaceCameraCapture.capture(loginOwnerWindow());
        } catch (FaceCameraCapture.FaceCameraException e) {
            show(Alert.AlertType.WARNING, "Caméra", e.getMessage());
            return;
        }
        if (probe.isEmpty()) {
            return;
        }
        File image = probe.get();
        try {
            FaceIdentifyMatch match = faceClient.identifyBestAmongUsers(candidates, image);
            double minScore = faceCfg.threshold();
            if (!match.hasMatch() || match.similarity() < minScore) {
                show(Alert.AlertType.INFORMATION, "Face ID",
                        "Visage non reconnu ou score insuffisant (seuil " + String.format(Locale.FRENCH, "%.2f", minScore)
                                + "). Réessayez ou utilisez l’email et le mot de passe.");
                return;
            }
            Optional<User> found = userService.findById(match.userId());
            if (found.isEmpty()) {
                show(Alert.AlertType.ERROR, "Face ID", "Utilisateur introuvable après identification.");
                return;
            }
            User u = found.get();
            if (!u.isActif()) {
                show(Alert.AlertType.WARNING, "Compte désactivé",
                        "Ce compte n’est pas activé. Activez-le en base ou utilisez un autre utilisateur.");
                return;
            }
            routeAfterSuccessfulLogin(u);
        } catch (FaceBiometricException e) {
            show(Alert.AlertType.ERROR, "Face ID", e.getMessage());
        } catch (IOException e) {
            show(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        } catch (SQLException e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onRegister() {
        navigateToPublicPage("signup");
    }

    @FXML
    public void onToggleTheme() {
        MainApp.toggleTheme();
    }

    @FXML
    public void onNavConnexion() {
        emailField.requestFocus();
    }

    @FXML
    public void onNavAccueil(MouseEvent event) {
        try {
            MainApp.showHome();
        } catch (IOException e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavProduits(MouseEvent event) {
        navigateToPublicPage("produits");
    }

    @FXML
    public void onNavRdv(MouseEvent event) {
        navigateToPublicPage("rdv");
    }

    @FXML
    public void onNavEvents(MouseEvent event) {
        navigateToPublicPage("events");
    }

    @FXML
    public void onNavBlog(MouseEvent event) {
        navigateToPublicPage("blog");
    }

    @FXML
    public void onFooterNavAccueil() {
        try {
            MainApp.showHome();
        } catch (IOException e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterNavProduits() {
        navigateToPublicPage("produits");
    }

    @FXML
    public void onFooterNavRdv() {
        navigateToPublicPage("rdv");
    }

    @FXML
    public void onFooterNavEvents() {
        navigateToPublicPage("events");
    }

    @FXML
    public void onFooterNavBlog() {
        navigateToPublicPage("blog");
    }

    @FXML
    public void onFooterFaq() {
        show(Alert.AlertType.INFORMATION, "FAQ",
                "Une section FAQ sera disponible prochainement.");
    }

    @FXML
    public void onFooterContact() {
        try {
            MainApp.showHomeScrollTo("contact");
        } catch (IOException e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterAccessibility() {
        show(Alert.AlertType.INFORMATION, "Accessibilité",
                "AutiCare s’engage à améliorer l’accessibilité de cette application.");
    }

    @FXML
    public void onFooterLegal() {
        show(Alert.AlertType.INFORMATION, "Mentions légales",
                "Informations légales à compléter selon votre structure.");
    }

    @FXML
    public void onFooterPrivacy() {
        show(Alert.AlertType.INFORMATION, "Politique de confidentialité",
                "Traitement des données personnelles : texte à adapter à votre politique.");
    }

    @FXML
    public void onFooterCgv() {
        show(Alert.AlertType.INFORMATION, "CGV",
                "Conditions générales de vente : texte à adapter.");
    }

    private void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
