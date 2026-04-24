package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.models.User;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.UserAvatarGraphic;
import org.example.utils.UserImageStorage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Profil médecin : affichage lecture seule, puis édition après « Modifier ».
 * Le tarif est enregistré en base ({@code tarif_consultation}) ; le téléphone du cabinet reste en préférences locales.
 */
public class MedecinMyProfileController {

    private static final String PREF_NODE = "org.example.medecin.profile";

    @FXML
    private TextField prenomField;
    @FXML
    private TextField nomField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField telephoneField;
    @FXML
    private TextField specialiteField;
    @FXML
    private TextField cabinetNameField;
    @FXML
    private TextField cabinetTelephoneField;
    @FXML
    private TextField tarifField;
    @FXML
    private TextField adresseCabinetField;
    @FXML
    private Label photoPathLabel;
    @FXML
    private StackPane avatarHost;
    @FXML
    private Button photoFabButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button modifyButton;

    private final UserService userService = new UserService();
    private Stage dialogStage;
    private User editingUser;
    private File chosenPhoto;
    private boolean readOnly = true;
    private Runnable onProfileSaved;

    public void setStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setOnProfileSaved(Runnable onProfileSaved) {
        this.onProfileSaved = onProfileSaved;
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node(PREF_NODE);
    }

    private String prefKey(String suffix) {
        return "u" + editingUser.getId() + "." + suffix;
    }

    @FXML
    public void initialize() {
        User session = AppState.getCurrentUser();
        if (session == null) {
            Platform.runLater(this::safeClose);
            return;
        }
        try {
            Optional<User> fresh = userService.findById(session.getId());
            if (fresh.isEmpty()) {
                Platform.runLater(this::safeClose);
                return;
            }
            editingUser = fresh.get();
            bindForm(editingUser);
            enterViewMode();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
            Platform.runLater(this::safeClose);
        }
    }

    private void safeClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void bindForm(User u) {
        prenomField.setText(u.getPrenom() != null ? u.getPrenom() : "");
        nomField.setText(u.getNom() != null ? u.getNom() : "");
        emailField.setText(u.getEmail() != null ? u.getEmail() : "");
        telephoneField.setText(u.getTelephone() != null ? u.getTelephone() : "");
        specialiteField.setText(u.getSpecialite() != null ? u.getSpecialite() : "");
        cabinetNameField.setText(u.getCabinet() != null ? u.getCabinet() : "");
        adresseCabinetField.setText(u.getAdresse() != null ? u.getAdresse() : "");

        Preferences p = prefs();
        cabinetTelephoneField.setText(p.get(prefKey("telCabinet"), ""));
        String tarifDb = u.getTarifConsultation();
        if (tarifDb != null && !tarifDb.isBlank()) {
            tarifField.setText(tarifDb.trim());
        } else {
            tarifField.setText(p.get(prefKey("tarif"), ""));
        }

        chosenPhoto = null;
        String img = u.getImage();
        if (img != null && !img.isBlank()) {
            int sl = Math.max(img.lastIndexOf('/'), img.lastIndexOf('\\'));
            photoPathLabel.setText(sl >= 0 ? img.substring(sl + 1) : img);
        } else {
            photoPathLabel.setText("Aucune photo");
        }
        refreshAvatarPreview(u);
    }

    private void refreshAvatarPreview(User u) {
        if (avatarHost == null) {
            return;
        }
        avatarHost.getChildren().setAll(
                UserAvatarGraphic.build(u, 120, UserAvatarGraphic.initialsFor(u), "med-profile-avatar-initials"));
    }

    private void savePrefsExtras() {
        Preferences p = prefs();
        p.put(prefKey("telCabinet"), trimOrEmpty(cabinetTelephoneField));
        p.remove(prefKey("tarif"));
    }

    private void enterViewMode() {
        readOnly = true;
        applyReadOnlyUi();
    }

    private void enterEditMode() {
        readOnly = false;
        applyReadOnlyUi();
    }

    private void applyReadOnlyUi() {
        prenomField.setEditable(!readOnly);
        nomField.setEditable(!readOnly);
        emailField.setEditable(!readOnly);
        telephoneField.setEditable(!readOnly);
        specialiteField.setEditable(!readOnly);
        cabinetNameField.setEditable(!readOnly);
        cabinetTelephoneField.setEditable(!readOnly);
        tarifField.setEditable(!readOnly);
        adresseCabinetField.setEditable(!readOnly);
        if (photoFabButton != null) {
            photoFabButton.setDisable(readOnly);
        }
        if (modifyButton != null) {
            modifyButton.setManaged(readOnly);
            modifyButton.setVisible(readOnly);
        }
        if (saveButton != null) {
            saveButton.setManaged(!readOnly);
            saveButton.setVisible(!readOnly);
        }
    }

    @FXML
    public void onModify() {
        enterEditMode();
    }

    @FXML
    public void onChoosePhoto() {
        if (readOnly) {
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle("Photo de profil");
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        Stage st = photoFabButton != null && photoFabButton.getScene() != null
                ? (Stage) photoFabButton.getScene().getWindow()
                : dialogStage;
        File f = ch.showOpenDialog(st);
        if (f != null) {
            long max = 5L * 1024 * 1024;
            if (f.length() > max) {
                alert(Alert.AlertType.WARNING, "Photo", "Le fichier dépasse 5 Mo.");
                return;
            }
            chosenPhoto = f;
            photoPathLabel.setText(f.getName());
        }
    }

    @FXML
    public void onSave() {
        if (editingUser == null || readOnly) {
            return;
        }
        String prenom = trimOrEmpty(prenomField);
        String nom = trimOrEmpty(nomField);
        String email = trimOrEmpty(emailField);
        String tel = trimOrEmpty(telephoneField);

        if (nom.isEmpty() || prenom.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Indiquez le prénom et le nom.");
            return;
        }
        if (email.isEmpty() || !email.contains("@")) {
            alert(Alert.AlertType.WARNING, "Email", "Indiquez une adresse email valide.");
            return;
        }
        if (tel.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Téléphone", "Indiquez un numéro de téléphone.");
            return;
        }
        if (trimOrEmpty(specialiteField).isEmpty()) {
            alert(Alert.AlertType.WARNING, "Cabinet & activité", "La spécialité est obligatoire.");
            return;
        }
        if (trimOrEmpty(cabinetNameField).isEmpty()) {
            alert(Alert.AlertType.WARNING, "Cabinet & activité", "Le nom du cabinet est obligatoire.");
            return;
        }
        if (trimOrEmpty(cabinetTelephoneField).isEmpty()) {
            alert(Alert.AlertType.WARNING, "Cabinet & activité", "Le téléphone du cabinet est obligatoire.");
            return;
        }
        String tarifRaw = trimOrEmpty(tarifField);
        if (tarifRaw.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Cabinet & activité", "Le tarif de consultation est obligatoire.");
            return;
        }
        try {
            Double.parseDouble(tarifRaw.replace(',', '.'));
        } catch (NumberFormatException e) {
            alert(Alert.AlertType.WARNING, "Tarif", "Indiquez un nombre valide pour le tarif (ex. 80 ou 80,5).");
            return;
        }
        if (trimOrEmpty(adresseCabinetField).isEmpty()) {
            alert(Alert.AlertType.WARNING, "Cabinet & activité", "L'adresse du cabinet est obligatoire.");
            return;
        }

        String emailNorm = email.trim().toLowerCase(Locale.FRENCH);
        String original = editingUser.getEmail() != null ? editingUser.getEmail().trim().toLowerCase(Locale.FRENCH) : "";
        if (!emailNorm.equals(original)) {
            try {
                Optional<User> other = userService.findByEmail(email);
                if (other.isPresent() && other.get().getId() != editingUser.getId()) {
                    alert(Alert.AlertType.WARNING, "Email", "Cette adresse est déjà utilisée.");
                    return;
                }
            } catch (SQLException e) {
                alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
                return;
            }
        }

        try {
            Optional<User> baseOpt = userService.findById(editingUser.getId());
            if (baseOpt.isEmpty()) {
                alert(Alert.AlertType.WARNING, "Session", "Compte introuvable.");
                safeClose();
                return;
            }
            User u = baseOpt.get();
            u.setPrenom(prenom);
            u.setNom(nom);
            u.setEmail(email.trim());
            u.setTelephone(tel);
            u.setSpecialite(trimOrEmpty(specialiteField));
            u.setCabinet(trimOrEmpty(cabinetNameField));
            u.setAdresse(trimOrEmpty(adresseCabinetField));
            u.setTarifConsultation(trimOrEmpty(tarifField));
            if (chosenPhoto != null) {
                u.setImage(UserImageStorage.copyUserImage(chosenPhoto));
            }
            userService.update(u);
            savePrefsExtras();
            AppState.setCurrentUser(u);
            editingUser = u;
            chosenPhoto = null;
            if (onProfileSaved != null) {
                onProfileSaved.run();
            }
            alert(Alert.AlertType.INFORMATION, "Enregistré", "Votre profil a été mis à jour.");
            safeClose();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Photo",
                    "Impossible d'enregistrer l'image : " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("Duplicate") || msg.contains("duplicate") || "23000".equals(e.getSQLState())) {
                alert(Alert.AlertType.WARNING, "Conflit", "Cet email existe déjà en base.");
            } else {
                alert(Alert.AlertType.ERROR, "Erreur base de données", msg);
            }
        }
    }

    @FXML
    public void onBack() {
        safeClose();
    }

    private static String trimOrEmpty(TextField f) {
        if (f == null || f.getText() == null) {
            return "";
        }
        return f.getText().trim();
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        if (dialogStage != null) {
            a.initOwner(dialogStage);
        }
        a.showAndWait();
    }
}
