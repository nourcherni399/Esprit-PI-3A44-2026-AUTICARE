package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Map;
import javafx.stage.Window;
import org.example.MainApp;
import org.example.models.Role;
import org.example.models.User;
import jakarta.mail.MessagingException;
import org.example.services.EmailVerificationCallbackServer;
import org.example.services.EmailVerificationEmailService;
import org.example.services.FaceBiometricException;
import org.example.services.FaceIdClientService;
import org.example.services.FaceIdConfig;
import org.example.services.GoogleOAuthService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.FaceCameraCapture;
import org.example.utils.SqlConnectivityErrors;
import org.example.utils.PasswordUtil;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

public class SignupController implements PublicShellAware {

    private PublicShellController shell;

    @FXML
    private BorderPane root;
    @FXML
    private VBox signupPageRoot;

    private static final Map<String, Role> PROFILE_TO_ROLE = Map.of(
            "Personne concernée (patient)", Role.PATIENT,
            "Parent / Proche", Role.PARENT
    );

    private static final Pattern HAS_UPPER = Pattern.compile("[A-ZÀÂÄÉÈÊËÏÎÔÙÛÜÇ]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    @FXML
    private TextField prenomField;
    @FXML
    private TextField nomField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField telephoneField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField repeatPasswordField;
    @FXML
    private Label ruleLenLabel;
    @FXML
    private Label ruleUpperLabel;
    @FXML
    private Label ruleDigitLabel;
    @FXML
    private ComboBox<String> profileCombo;
    @FXML
    private ComboBox<String> langCombo;
    @FXML
    private Label biometricPathLabel;
    @FXML
    private Button chooseFileBtn;
    @FXML
    private VBox patientFieldsBox;
    @FXML
    private DatePicker dateNaissancePicker;
    @FXML
    private TextField adresseField;
    @FXML
    private ComboBox<String> sexeCombo;
    @FXML
    private VBox parentFieldsBox;
    @FXML
    private TextField relationParentField;

    private final UserService userService = new UserService();

    /** Fichier image issu de la dernière capture webcam (facultatif), enregistré après création du compte. */
    private File pendingBiometricImage;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    public void initialize() {
        Region styleHost = signupPageRoot != null ? signupPageRoot : root;
        if (styleHost != null) {
            var signupCss = getClass().getResource("/styles/signup.css");
            if (signupCss != null) {
                String ext = signupCss.toExternalForm();
                if (!styleHost.getStylesheets().contains(ext)) {
                    styleHost.getStylesheets().add(ext);
                }
            }
        }
        boolean embedded = signupPageRoot != null;
        if (!embedded && langCombo != null) {
            langCombo.getItems().addAll("FR", "EN");
            langCombo.getSelectionModel().selectFirst();
        }
        if (!embedded) {
            AuthPageController.attachAnimatedAuthBackground(langCombo != null ? langCombo : root);
        }
        profileCombo.setPromptText("Sélectionnez votre profil");
        profileCombo.getItems().setAll(
                "Personne concernée (patient)",
                "Parent / Proche"
        );
        profileCombo.getSelectionModel().clearSelection();
        profileCombo.valueProperty().addListener((obs, oldV, newV) -> updateProfileSpecificFields(newV));
        updateProfileSpecificFields(null);

        if (sexeCombo != null) {
            sexeCombo.getItems().setAll("Choisir", "Femme", "Homme");
            sexeCombo.getSelectionModel().selectFirst();
        }
        if (biometricPathLabel != null) {
            biometricPathLabel.setText("Aucune capture enregistrée");
        }
        if (passwordField != null) {
            passwordField.textProperty().addListener((obs, o, n) -> updatePasswordRules(n));
            updatePasswordRules(passwordField.getText());
        }
    }

    @FXML
    public void onCaptureBiometricFace() {
        Window owner = signupOwnerWindow();
        Optional<File> shot;
        try {
            shot = FaceCameraCapture.capture(owner);
        } catch (FaceCameraCapture.FaceCameraException e) {
            alert(Alert.AlertType.WARNING, "Caméra", e.getMessage());
            return;
        }
        if (shot.isEmpty()) {
            return;
        }
        pendingBiometricImage = shot.get();
        if (biometricPathLabel != null) {
            biometricPathLabel.setText("Capture enregistrée — validez à la fin du formulaire.");
        }
    }

    private Window signupOwnerWindow() {
        if (chooseFileBtn != null && chooseFileBtn.getScene() != null && chooseFileBtn.getScene().getWindow() != null) {
            return chooseFileBtn.getScene().getWindow();
        }
        if (signupPageRoot != null && signupPageRoot.getScene() != null && signupPageRoot.getScene().getWindow() != null) {
            return signupPageRoot.getScene().getWindow();
        }
        return MainApp.getPrimaryStage();
    }

    @FXML
    public void onCreateAccount() {
        String prenom = trim(prenomField);
        String nom = trim(nomField);
        String email = trim(emailField);
        String tel = trim(telephoneField);
        String pwd = passwordField.getText();
        String pwd2 = repeatPasswordField.getText();

        if (prenom.isEmpty() || nom.isEmpty() || email.isEmpty() || tel.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Veuillez remplir tous les champs obligatoires.");
            return;
        }
        if (pwd.length() < 8) {
            alert(Alert.AlertType.WARNING, "Mot de passe", "Au moins 8 caractères.");
            return;
        }
        if (!HAS_UPPER.matcher(pwd).find()) {
            alert(Alert.AlertType.WARNING, "Mot de passe", "Au moins une lettre majuscule.");
            return;
        }
        if (!HAS_DIGIT.matcher(pwd).find()) {
            alert(Alert.AlertType.WARNING, "Mot de passe", "Au moins un chiffre.");
            return;
        }
        if (!pwd.equals(pwd2)) {
            alert(Alert.AlertType.WARNING, "Mot de passe", "Les deux mots de passe ne correspondent pas.");
            return;
        }
        String profileLabel = profileCombo.getValue();
        if (profileLabel == null || !PROFILE_TO_ROLE.containsKey(profileLabel)) {
            alert(Alert.AlertType.WARNING, "Profil", "Sélectionnez votre profil dans la liste.");
            return;
        }
        Role role = PROFILE_TO_ROLE.get(profileLabel);
        if (role == Role.PATIENT) {
            if (dateNaissancePicker.getValue() == null || trim(adresseField).isEmpty()
                    || sexeCombo.getValue() == null || "Choisir".equalsIgnoreCase(sexeCombo.getValue())) {
                alert(Alert.AlertType.WARNING, "Profil patient",
                        "Veuillez remplir Date de naissance, Adresse et Sexe.");
                return;
            }
        }
        if (role == Role.PARENT && trim(relationParentField).isEmpty()) {
            alert(Alert.AlertType.WARNING, "Profil parent",
                    "Veuillez préciser la relation avec le patient.");
            return;
        }

        try {
            if (userService.findByEmail(email).isPresent()) {
                alert(Alert.AlertType.WARNING, "Email", "Un compte existe déjà avec cet email.");
                return;
            }
            User u = new User();
            u.setPrenom(prenom);
            u.setNom(nom);
            u.setEmail(email);
            u.setTelephone(tel);
            u.setMotDePasseHash(PasswordUtil.hash(pwd));
            u.setRole(role);
            if (role == Role.PATIENT) {
                u.setDateNaissance(dateNaissancePicker.getValue());
                u.setAdresse(trim(adresseField));
                u.setSexe(sexeCombo.getValue());
                u.setRelationParent(null);
            } else if (role == Role.PARENT) {
                u.setRelationParent(trim(relationParentField));
                u.setDateNaissance(null);
                u.setAdresse(null);
                u.setSexe(null);
            }
            Optional<User> createdOpt;
            boolean verifyByEmail = EmailVerificationCallbackServer.isEmailVerificationEnabled();
            if (verifyByEmail) {
                byte[] raw = new byte[32];
                new SecureRandom().nextBytes(raw);
                String token = HexFormat.of().formatHex(raw);
                LocalDateTime expires = LocalDateTime.now().plusHours(48);
                u.setActif(false);
                userService.addWithEmailVerification(u, token, expires);
                createdOpt = userService.findByEmail(email);
                String verifyUrl = EmailVerificationCallbackServer.buildVerifyUrl(token);
                try {
                    new EmailVerificationEmailService().sendVerificationEmail(email, verifyUrl);
                } catch (MessagingException me) {
                    alert(Alert.AlertType.ERROR, "Email",
                            "Compte créé mais inactif : l’envoi du mail d’activation a échoué.\n"
                                    + (me.getMessage() != null ? me.getMessage() : me.getClass().getSimpleName())
                                    + "\n\nVérifiez la configuration SMTP ou contactez un administrateur.");
                    pendingBiometricImage = null;
                    if (biometricPathLabel != null) {
                        biometricPathLabel.setText("Aucune capture enregistrée");
                    }
                    goToLoginPage();
                    return;
                }
            } else {
                u.setActif(true);
                userService.add(u);
                createdOpt = userService.findByEmail(email);
            }
            File capture = pendingBiometricImage;
            if (createdOpt.isPresent() && capture != null && capture.isFile()) {
                enrollFaceAfterSignup(createdOpt.get(), capture);
            }
            pendingBiometricImage = null;
            if (biometricPathLabel != null) {
                biometricPathLabel.setText("Aucune capture enregistrée");
            }
            if (verifyByEmail) {
                alert(Alert.AlertType.INFORMATION, "Vérifiez votre e-mail",
                        "Un lien d’activation a été envoyé à " + email + ".\n\n"
                                + "Ouvrez le lien pendant que l’application AutiCare est lancée sur cet ordinateur "
                                + "(le lien utilise l’adresse locale indiquée dans la configuration). "
                                + "Ensuite vous pourrez vous connecter.");
            } else {
                alert(Alert.AlertType.INFORMATION, "Compte créé",
                        "Vous pouvez maintenant vous connecter avec votre email.");
            }
            goToLoginPage();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void goToLoginPage() throws IOException {
        if (shell != null) {
            shell.loadPage("login");
        } else {
            MainApp.showLogin();
        }
    }

    @FXML
    public void onBackToLogin() {
        try {
            goToLoginPage();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onGoogleSignIn() {
        GoogleOAuthService google = new GoogleOAuthService();
        try {
            User u = google.signInWithGoogle();
            AppState.setCurrentUser(u);
            if (u.getRole() == Role.ADMIN) {
                MainApp.showAdminUsers();
            } else if (u.getRole() == Role.MEDECIN) {
                MainApp.showMedecinDashboard();
            } else {
                MainApp.showHome();
            }
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
            alert(Alert.AlertType.ERROR, title, msg.toString());
        }
    }

    @FXML
    public void onNavConnexion() {
        try {
            goToLoginPage();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onRegister() {
        if (prenomField != null) {
            prenomField.requestFocus();
        }
    }

    @FXML
    public void onToggleTheme() {
        MainApp.toggleTheme();
    }

    @FXML
    public void onNavAccueil(MouseEvent e) {
        try {
            MainApp.showHome();
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", ex.getMessage());
        }
    }

    @FXML
    public void onNavProduits(MouseEvent e) {
        navigateToPublicPage("produits");
    }

    @FXML
    public void onNavRdv(MouseEvent e) {
        navigateToPublicPage("rdv");
    }

    @FXML
    public void onNavEvents(MouseEvent e) {
        navigateToPublicPage("events");
    }

    @FXML
    public void onNavBlog(MouseEvent e) {
        navigateToPublicPage("blog");
    }

    private void navigateToPublicPage(String pageId) {
        try {
            if (shell != null) {
                shell.loadPage(pageId);
            } else {
                MainApp.showPublicPage(pageId);
            }
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", ex.getMessage());
        }
    }

    @FXML
    public void onFooterNavAccueil() {
        try {
            MainApp.showHome();
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", ex.getMessage());
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
        alert(Alert.AlertType.INFORMATION, "FAQ",
                "Une section FAQ sera disponible prochainement.");
    }

    @FXML
    public void onFooterContact() {
        try {
            MainApp.showHomeScrollTo("contact");
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", ex.getMessage());
        }
    }

    @FXML
    public void onFooterAccessibility() {
        alert(Alert.AlertType.INFORMATION, "Accessibilité",
                "AutiCare s'engage à améliorer l'accessibilité de cette application.");
    }

    @FXML
    public void onFooterLegal() {
        alert(Alert.AlertType.INFORMATION, "Mentions légales",
                "Informations légales à compléter selon votre structure.");
    }

    @FXML
    public void onFooterPrivacy() {
        alert(Alert.AlertType.INFORMATION, "Politique de confidentialité",
                "Traitement des données personnelles : texte à adapter à votre politique.");
    }

    @FXML
    public void onFooterCgv() {
        alert(Alert.AlertType.INFORMATION, "CGV",
                "Conditions générales de vente : texte à adapter.");
    }

    private void enrollFaceAfterSignup(User created, File imageFile) {
        FaceIdConfig cfg = new FaceIdConfig();
        if (!cfg.isEnabled()) {
            return;
        }
        if (cfg.isRequireHealthy()) {
            FaceIdClientService probe = new FaceIdClientService(cfg);
            if (!probe.isHealthy()) {
                alert(Alert.AlertType.WARNING, "Face ID",
                        "Compte créé, mais le service visage ne répond pas (" + cfg.serviceUrl()
                                + "). Vous pourrez enregistrer votre visage plus tard depuis votre profil.");
                return;
            }
        }
        try {
            FaceIdClientService face = new FaceIdClientService(cfg);
            String template = face.enrollFromImage(imageFile);
            userService.updateDataFaceApi(created.getId(), template);
        } catch (FaceBiometricException e) {
            alert(Alert.AlertType.WARNING, "Face ID",
                    "Compte créé, mais l’enregistrement du visage a échoué : " + e.getMessage());
        } catch (SQLException e) {
            alert(Alert.AlertType.WARNING, "Face ID",
                    "Compte créé, mais la sauvegarde du modèle visage a échoué : " + e.getMessage());
        }
    }

    private static String trim(TextField f) {
        return f.getText() == null ? "" : f.getText().trim();
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void updatePasswordRules(String pwd) {
        String value = pwd != null ? pwd : "";
        setRuleState(ruleLenLabel, value.length() >= 8);
        setRuleState(ruleUpperLabel, HAS_UPPER.matcher(value).find());
        setRuleState(ruleDigitLabel, HAS_DIGIT.matcher(value).find());
    }

    private static void setRuleState(Label label, boolean ok) {
        if (label == null) {
            return;
        }
        label.getStyleClass().removeAll("pwd-rule-valid", "pwd-rule-invalid");
        label.getStyleClass().add(ok ? "pwd-rule-valid" : "pwd-rule-invalid");
    }

    private void updateProfileSpecificFields(String profileLabel) {
        boolean isPatient = "Personne concernée (patient)".equals(profileLabel);
        boolean isParent = "Parent / Proche".equals(profileLabel);
        setVisibleManaged(patientFieldsBox, isPatient);
        setVisibleManaged(parentFieldsBox, isParent);
        if (!isPatient) {
            if (dateNaissancePicker != null) {
                dateNaissancePicker.setValue(null);
            }
            if (adresseField != null) {
                adresseField.clear();
            }
            if (sexeCombo != null && !sexeCombo.getItems().isEmpty()) {
                sexeCombo.getSelectionModel().selectFirst();
            }
        }
        if (!isParent && relationParentField != null) {
            relationParentField.clear();
        }
    }

    private static void setVisibleManaged(VBox box, boolean show) {
        if (box == null) {
            return;
        }
        box.setVisible(show);
        box.setManaged(show);
    }
}
