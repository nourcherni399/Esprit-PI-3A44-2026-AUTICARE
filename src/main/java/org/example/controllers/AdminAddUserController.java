package org.example.controllers;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.example.MainApp;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.UserService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.PasswordUtil;
import org.example.utils.UserImageStorage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

public class AdminAddUserController {

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private Label telephoneLabel;
    @FXML
    private TextField nomField;
    @FXML
    private TextField prenomField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField telephoneField;
    @FXML
    private Label photoPathLabel;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private CheckBox actifCheckBox;
    @FXML
    private ComboBox<Role> roleCombo;
    @FXML
    private VBox parentAttrsBox;
    @FXML
    private TextField relationField;
    @FXML
    private VBox patientAttrsBox;
    @FXML
    private DatePicker dateNaissancePicker;
    @FXML
    private ComboBox<String> sexeCombo;
    @FXML
    private TextField patientAdresseField;
    @FXML
    private VBox medecinAttrsBox;
    @FXML
    private TextField specialiteField;
    @FXML
    private TextField cabinetNameField;
    @FXML
    private TextField cabinetAdresseField;
    @FXML
    private TextField cabinetTelField;
    @FXML
    private TextField tarifField;

    private final UserService userService = new UserService();
    private File chosenPhoto;
    private ChangeListener<Role> roleListener;

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);

        roleCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Role r) {
                return r == null ? "" : roleLabelFr(r);
            }

            @Override
            public Role fromString(String s) {
                if (s == null) {
                    return null;
                }
                for (Role r : Role.values()) {
                    if (roleLabelFr(r).equals(s)) {
                        return r;
                    }
                }
                return null;
            }
        });
        roleCombo.getItems().setAll(
                Role.ADMIN, Role.PARENT, Role.PATIENT, Role.MEDECIN, Role.USER);
        roleCombo.getSelectionModel().clearSelection();

        sexeCombo.getItems().setAll("Femme", "Homme");

        roleListener = (obs, o, n) -> refreshRoleSections(n);
        roleCombo.getSelectionModel().selectedItemProperty().addListener(roleListener);
        refreshRoleSections(roleCombo.getSelectionModel().getSelectedItem());
    }

    private void refreshRoleSections(Role r) {
        AdminUserRoleFormHelper.updateSectionVisibility(
                r, parentAttrsBox, patientAttrsBox, medecinAttrsBox, telephoneLabel, telephoneField);
    }

    private static String roleLabelFr(Role r) {
        return switch (r) {
            case ADMIN -> "Administrateur";
            case MEDECIN -> "Médecin";
            case PARENT -> "Parent";
            case PATIENT -> "Patient";
            case USER -> "Utilisateur";
        };
    }

    @FXML
    public void onChoosePhoto() {
        FileChooser ch = new FileChooser();
        ch.setTitle("Photo de profil");
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        Stage st = (Stage) nomField.getScene().getWindow();
        File f = ch.showOpenDialog(st);
        if (f != null) {
            chosenPhoto = f;
            photoPathLabel.setText(f.getName());
        }
    }

    @FXML
    public void onBackToUsers() {
        try {
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        onBackToUsers();
    }

    @FXML
    public void onSaveUser() {
        String nom = trimOrEmpty(nomField);
        String prenom = trimOrEmpty(prenomField);
        String email = trimOrEmpty(emailField);
        String tel = trimOrEmpty(telephoneField);
        String pwd = passwordField.getText() != null ? passwordField.getText() : "";
        String pwd2 = confirmPasswordField.getText() != null ? confirmPasswordField.getText() : "";

        if (nom.isEmpty() || prenom.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Indiquez le nom et le prénom.");
            return;
        }
        if (email.isEmpty() || !email.contains("@")) {
            alert(Alert.AlertType.WARNING, "Email", "Indiquez une adresse email valide.");
            return;
        }
        Role role = roleCombo.getSelectionModel().getSelectedItem();
        if (role == null) {
            alert(Alert.AlertType.WARNING, "Rôle", "Choisissez un rôle.");
            return;
        }
        if (role == Role.MEDECIN) {
            if (AdminUserRoleFormHelper.trim(cabinetTelField).isEmpty()) {
                alert(Alert.AlertType.WARNING, "Téléphone", "Indiquez le téléphone du cabinet.");
                return;
            }
        } else if (tel.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Téléphone", "Indiquez un numéro de téléphone.");
            return;
        }
        if (pwd.length() < 6) {
            alert(Alert.AlertType.WARNING, "Mot de passe", "Le mot de passe doit contenir au moins 6 caractères.");
            return;
        }
        if (!pwd.equals(pwd2)) {
            alert(Alert.AlertType.WARNING, "Mot de passe", "La confirmation ne correspond pas.");
            return;
        }
        try {
            Optional<User> existing = userService.findByEmail(email);
            if (existing.isPresent()) {
                alert(Alert.AlertType.WARNING, "Email", "Cette adresse est déjà utilisée.");
                return;
            }

            User u = new User();
            u.setNom(nom);
            u.setPrenom(prenom);
            u.setEmail(email);
            u.setTelephone(tel);
            u.setMotDePasseHash(PasswordUtil.hashBcrypt(pwd));
            u.setRole(role);
            u.setActif(actifCheckBox.isSelected());
            AdminUserRoleFormHelper.applyRoleFields(
                    u,
                    role,
                    relationField,
                    dateNaissancePicker,
                    sexeCombo,
                    patientAdresseField,
                    specialiteField,
                    cabinetNameField,
                    cabinetAdresseField,
                    cabinetTelField,
                    telephoneField);

            if (chosenPhoto != null) {
                try {
                    u.setImage(UserImageStorage.copyUserImage(chosenPhoto));
                } catch (IOException ex) {
                    alert(Alert.AlertType.ERROR, "Photo",
                            "Impossible d'enregistrer l'image : " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
                    return;
                }
            }

            userService.add(u);
            alert(Alert.AlertType.INFORMATION, "Utilisateur créé", "L'utilisateur a été enregistré.");
            onBackToUsers();
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("Duplicate") || msg.contains("duplicate") || "23000".equals(e.getSQLState())) {
                alert(Alert.AlertType.WARNING, "Conflit", "Cet email existe déjà en base.");
            } else {
                alert(Alert.AlertType.ERROR, "Erreur base de données", msg);
            }
        }
    }

    private static String trimOrEmpty(TextField f) {
        if (f == null || f.getText() == null) {
            return "";
        }
        return f.getText().trim();
    }

    @FXML
    public void onOpenMyProfile() {
        try {
            MainApp.openAdminMyProfile(topbarAvatarHost, userNameLabel, userEmailLabel);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onLogout() {
        AppState.clear();
        try {
            MainApp.showLogin();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavDashboard() {
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavUsers() {
        try {
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavProducts() {
        try {
            MainApp.showAdminProducts();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavStocks() {
        try {
            MainApp.showAdminStocks();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavOrders() {
        try {
            MainApp.showAdminOrders();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavEvents() {
        try {
            MainApp.showDashboard(4);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavTopics() {
        try {
            MainApp.showDashboard(7);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavModules() {
        try {
            MainApp.showAdminModules();
            MainApp.showDashboard(6);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavSettings() {
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavAdminHome() {
        try {
            MainApp.showHome();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
