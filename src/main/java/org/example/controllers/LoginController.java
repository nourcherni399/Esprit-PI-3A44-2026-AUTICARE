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
import org.example.services.GoogleOAuthService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.MyDatabase;
import org.example.utils.PasswordRecoveryState;
import org.example.utils.PasswordUtil;

import java.io.IOException;

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
                show(Alert.AlertType.INFORMATION, "Echec", "Email ou mot de passe invalide.");
                return;
            }
            var cand = account.get();
            if (!cand.isActif()) {
                show(Alert.AlertType.WARNING, "Compte desactive",
                        "Ce compte n'est pas active (is_active = 0 en base). Activez-le dans MySQL ou utilisez un autre utilisateur.");
                return;
            }
            if (!PasswordUtil.matches(pwd, cand.getMotDePasseHash())) {
                show(Alert.AlertType.INFORMATION, "Echec", "Email ou mot de passe invalide.");
                return;
            }
            var u = cand;
            AppState.setCurrentUser(u);
            if (u.getRole() == Role.ADMIN) {
                MainApp.showAdminUsers();
            } else if (u.getRole() == Role.MEDECIN) {
                MainApp.showMedecinDashboard();
            } else {
                handlePostPatientLoginRedirect();
            }
        } catch (Exception e) {
            show(Alert.AlertType.ERROR, "Erreur", humanizeLoginError(e));
        }
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
            show(Alert.AlertType.WARNING, "PIN", "Entrez un code a 6 chiffres.");
            return;
        }
        show(Alert.AlertType.INFORMATION, "Verification",
                "Code accepte (demonstration - aucune API reelle).");
        PasswordRecoveryState.clear();
        onBackToLogin();
    }

    @FXML
    public void onForgotResendPin() {
        show(Alert.AlertType.INFORMATION, "Code renvoye", "Un nouveau code a ete envoye (demonstration).");
    }

    private static void setPaneVisible(VBox pane, boolean visible) {
        if (pane != null) {
            pane.setVisible(visible);
            pane.setManaged(visible);
        }
    }

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
            AppState.setCurrentUser(u);
            if (u.getRole() == Role.ADMIN) {
                MainApp.showAdminUsers();
            } else if (u.getRole() == Role.MEDECIN) {
                MainApp.showMedecinDashboard();
            } else {
                handlePostPatientLoginRedirect();
            }
        } catch (Exception e) {
            show(Alert.AlertType.ERROR, "Google OAuth",
                    "Echec connexion Google: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                            + "\nredirectUri utilisee: " + google.getConfiguredRedirectUri()
                            + "\nclientId: " + google.getConfiguredClientIdMasked());
        }
    }

    @FXML
    public void onFaceIdSignIn() {
        show(Alert.AlertType.INFORMATION, "Face ID", "Face ID non disponible sur cette plateforme.");
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
        show(Alert.AlertType.INFORMATION, "Accessibilite",
                "AutiCare s'engage a ameliorer l'accessibilite de cette application.");
    }

    @FXML
    public void onFooterLegal() {
        show(Alert.AlertType.INFORMATION, "Mentions legales",
                "Informations legales a completer selon votre structure.");
    }

    @FXML
    public void onFooterPrivacy() {
        show(Alert.AlertType.INFORMATION, "Politique de confidentialite",
                "Traitement des donnees personnelles : texte a adapter a votre politique.");
    }

    @FXML
    public void onFooterCgv() {
        show(Alert.AlertType.INFORMATION, "CGV",
                "Conditions generales de vente : texte a adapter.");
    }

    private void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void handlePostPatientLoginRedirect() throws IOException {
        if (AppState.getPendingPublicEventDetailId() > 0) {
            MainApp.showPublicPage("event-detail");
            return;
        }
        if (AppState.getPendingPublicRdvDoctorId() > 0 && AppState.getPendingPublicRdvAvailabilityId() > 0) {
            MainApp.showPublicPage("rdv-type");
            return;
        }
        MainApp.showHome();
    }

    private String humanizeLoginError(Throwable error) {
        if (error == null) {
            return "Erreur inconnue.";
        }
        String message = safeLower(error.getMessage());
        Throwable cursor = error.getCause();
        while (cursor != null) {
            message += " " + safeLower(cursor.getMessage());
            cursor = cursor.getCause();
        }
        if (message.contains("communications link failure")
                || message.contains("connection refused")
                || message.contains("connect timed out")
                || message.contains("host is down")) {
            return "Connexion a MySQL impossible.\n"
                    + "Verifiez que le serveur MySQL est demarre et accessible.\n"
                    + "URL actuelle: " + MyDatabase.getJdbcUrl() + "\n"
                    + "Controlez aussi db.user/db.password dans application.properties.";
        }
        if (message.contains("access denied for user")) {
            return "Identifiants MySQL invalides (db.user / db.password).";
        }
        if (message.contains("unknown database")) {
            return "La base MySQL n'existe pas. Creez la base 'pidb'.";
        }
        return error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}

