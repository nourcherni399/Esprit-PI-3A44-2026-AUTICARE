package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.Map;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.MainApp;
import org.example.models.AdminUser;
import org.example.models.Medecin;
import org.example.models.Role;
import org.example.models.User;
import org.example.models.UserFactory;
import org.example.services.FaceBiometricException;
import org.example.services.FaceBiometricService;
import org.example.services.FaceIdClientService;
import org.example.services.FaceIdConfig;
import org.example.services.GoogleOAuthService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.PasswordUtil;

import java.io.IOException;
import java.sql.SQLException;
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
    private ImageView biometricPreviewImage;
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
    private final FaceIdConfig faceIdConfig = new FaceIdConfig();
    private final FaceBiometricService faceBiometricService = new FaceIdClientService(faceIdConfig);
    private File biometricFile;

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
        biometricFile = null;
        biometricPathLabel.setText("Aucun fichier choisi");
        if (biometricPreviewImage != null) {
            biometricPreviewImage.setImage(null);
            biometricPreviewImage.setVisible(false);
            biometricPreviewImage.setManaged(false);
        }
        if (passwordField != null) {
            passwordField.textProperty().addListener((obs, o, n) -> updatePasswordRules(n));
            updatePasswordRules(passwordField.getText());
        }
    }

    @FXML
    public void onChooseBiometricFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Photo visage");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        Window w = chooseFileBtn.getScene().getWindow();
        java.io.File f = chooser.showOpenDialog(w);
        if (f != null) {
            biometricFile = f;
            Image img = new Image(f.toURI().toString(), 84, 84, true, true);
            if (img.isError()) {
                biometricPathLabel.setText("Image invalide");
                biometricFile = null;
                if (biometricPreviewImage != null) {
                    biometricPreviewImage.setImage(null);
                    biometricPreviewImage.setVisible(false);
                    biometricPreviewImage.setManaged(false);
                }
                return;
            }
            if (biometricPreviewImage != null) {
                biometricPreviewImage.setImage(img);
                biometricPreviewImage.setVisible(true);
                biometricPreviewImage.setManaged(true);
            }
            biometricPathLabel.setText("Image sélectionnée");
        }
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
            User u = UserFactory.createByRole(role);
            u.setPrenom(prenom);
            u.setNom(nom);
            u.setEmail(email);
            u.setTelephone(tel);
            u.setMotDePasseHash(PasswordUtil.hash(pwd));
            u.setRole(role);
            if (faceIdConfig.isEnabled()) {
                if (biometricFile == null) {
                    alert(Alert.AlertType.WARNING, "Face ID",
                            "Veuillez choisir une image visage pour activer la connexion Face ID.");
                    return;
                }
                try {
                    String templateJson = faceBiometricService.enrollFromImage(biometricFile);
                    u.setDataFaceApi(templateJson);
                } catch (FaceBiometricException ex) {
                    alert(Alert.AlertType.WARNING, "Face ID", faceMessageForCode(ex));
                    return;
                }
            }
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
            u.setActif(true);
            userService.add(u);
            alert(Alert.AlertType.INFORMATION, "Compte créé",
                    "Vous pouvez maintenant vous connecter avec votre email.");
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
            if (u instanceof AdminUser) {
                MainApp.showAdminUsers();
            } else if (u instanceof Medecin) {
                MainApp.showMedecinDashboard();
            } else {
                MainApp.showHome();
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Google OAuth",
                    "Echec connexion Google: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                            + "\nredirectUri utilisee: " + google.getConfiguredRedirectUri()
                            + "\nclientId: " + google.getConfiguredClientIdMasked());
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

    private static String trim(TextField f) {
        return f.getText() == null ? "" : f.getText().trim();
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
