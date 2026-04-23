package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.MainApp;
import org.example.models.AdminUser;
import org.example.models.Medecin;
import org.example.models.User;
import org.example.services.FaceBiometricException;
import org.example.services.FaceBiometricService;
import org.example.services.FaceIdClientService;
import org.example.services.FaceIdConfig;
import org.example.services.GoogleOAuthService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.PasswordRecoveryState;
import org.example.utils.PasswordUtil;

import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.List;

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
    private final FaceIdConfig faceIdConfig = new FaceIdConfig();
    private final FaceBiometricService faceBiometricService = new FaceIdClientService(faceIdConfig);
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
            var u = cand;
            AppState.setCurrentUser(u);
            if (u instanceof AdminUser) {
                MainApp.showAdminUsers();
            } else if (u instanceof Medecin) {
                MainApp.showMedecinDashboard();
            } else {
                if (AppState.getPendingPublicEventDetailId() > 0) {
                    MainApp.showPublicPage("event-detail");
                    return;
                }
                MainApp.showHome();
            }
        } catch (Exception e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
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
            AppState.setCurrentUser(u);
            if (u instanceof AdminUser) {
                MainApp.showAdminUsers();
            } else if (u instanceof Medecin) {
                MainApp.showMedecinDashboard();
            } else {
                MainApp.showHome();
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
        if (!faceIdConfig.isEnabled()) {
            show(Alert.AlertType.INFORMATION, "Face ID", "Face ID est désactivé.");
            return;
        }
        if (faceIdConfig.isRequireHealthy() && faceBiometricService instanceof FaceIdClientService client && !client.isHealthy()) {
            show(Alert.AlertType.WARNING, "Face ID", "Service Face ID indisponible.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Image de vérification Face ID");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        File probe = chooser.showOpenDialog(emailField != null && emailField.getScene() != null
                ? emailField.getScene().getWindow()
                : null);
        if (probe == null) {
            return;
        }
        try {
            List<User> users = userService.findAll().stream()
                    .filter(User::isActif)
                    .filter(u -> u.getDataFaceApi() != null && !u.getDataFaceApi().isBlank())
                    .toList();
            if (users.isEmpty()) {
                show(Alert.AlertType.INFORMATION, "Face ID", "Aucun utilisateur enrôlé Face ID.");
                return;
            }
            User bestUser = null;
            double bestScore = -1.0;
            for (User user : users) {
                try {
                    double score = faceBiometricService.similarity(user.getDataFaceApi(), probe);
                    if (score > bestScore) {
                        bestScore = score;
                        bestUser = user;
                    }
                } catch (FaceBiometricException ex) {
                    String code = ex.getCode() != null ? ex.getCode().trim().toUpperCase() : "";
                    if ("NO_FACE".equals(code) || "MULTIPLE_FACES".equals(code) || "INVALID_IMAGE".equals(code)) {
                        show(Alert.AlertType.WARNING, "Face ID", faceMessageForCode(ex));
                        return;
                    }
                }
            }
            if (bestUser == null || bestScore < faceIdConfig.threshold()) {
                show(Alert.AlertType.INFORMATION, "Face ID", "Aucun visage reconnu.");
                return;
            }
            AppState.setCurrentUser(bestUser);
            if (bestUser instanceof AdminUser) {
                MainApp.showAdminUsers();
            } else if (bestUser instanceof Medecin) {
                MainApp.showMedecinDashboard();
            } else {
                MainApp.showHome();
            }
        } catch (FaceBiometricException ex) {
            show(Alert.AlertType.WARNING, "Face ID", faceMessageForCode(ex));
        } catch (SQLException e) {
            show(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        } catch (Exception e) {
            show(Alert.AlertType.ERROR, "Face ID", "Erreur lors de la connexion Face ID.");
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

    private static String faceMessageForCode(FaceBiometricException ex) {
        if (ex == null) {
            return "Erreur Face ID.";
        }
        String code = ex.getCode() != null ? ex.getCode().trim().toUpperCase() : "";
        return switch (code) {
            case "NO_FACE" -> "Aucun visage détecté. Utilisez une photo nette du visage.";
            case "MULTIPLE_FACES" -> "Plusieurs visages détectés. Utilisez une image avec un seul visage.";
            case "INVALID_IMAGE" -> "Image invalide. Veuillez choisir un fichier image valide.";
            case "SERVICE_UNAVAILABLE" -> "Service Face ID indisponible. Réessayez plus tard.";
            default -> (ex.getMessage() != null && !ex.getMessage().isBlank())
                    ? ex.getMessage()
                    : "Erreur Face ID.";
        };
    }
}
