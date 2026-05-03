package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.MainApp;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.BlogService;
import org.example.services.CommentaireService;
import org.example.services.ProductService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.UserAvatarGraphic;
import org.example.utils.UserImageStorage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public class AdminMyProfileController {

    @FXML
    private StackPane sidebarAvatarHost;
    @FXML
    private Label sidebarNameLabel;
    @FXML
    private Label sidebarRoleLabel;
    @FXML
    private Label statProduitsValue;
    @FXML
    private Label statCommentairesValue;
    @FXML
    private Label statArticlesValue;
    @FXML
    private ProgressBar profileProgressBar;
    @FXML
    private Label profileProgressLabel;
    @FXML
    private TextField prenomField;
    @FXML
    private TextField nomField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField telephoneField;
    @FXML
    private Label photoPathLabel;
    @FXML
    private Button choosePhotoButton;

    private final UserService userService = new UserService();
    private final ProductService productService = new ProductService();
    private final BlogService blogService = new BlogService();
    private final CommentaireService commentaireService = new CommentaireService();
    private Stage dialogStage;
    private User editingUser;
    private File chosenPhoto;

    public void setStage(Stage stage) {
        this.dialogStage = stage;
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
            bindSidebar(editingUser);
            loadStats(editingUser);
            updateCompletion(editingUser);
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
        chosenPhoto = null;
        String img = u.getImage();
        if (img != null && !img.isBlank()) {
            int sl = Math.max(img.lastIndexOf('/'), img.lastIndexOf('\\'));
            photoPathLabel.setText(sl >= 0 ? img.substring(sl + 1) : img);
        } else {
            photoPathLabel.setText("Aucun fichier choisi");
        }
    }

    private void bindSidebar(User u) {
        String prenom = u.getPrenom() != null ? u.getPrenom() : "";
        String nom = u.getNom() != null ? u.getNom() : "";
        sidebarNameLabel.setText(((prenom + " " + nom).trim()).isEmpty() ? "—" : (prenom + " " + nom).trim());
        sidebarRoleLabel.setText(roleDisplay(u.getRole()));

        if (sidebarAvatarHost != null) {
            sidebarAvatarHost.getChildren().clear();
            Node avatar = UserAvatarGraphic.build(u, 96, UserAvatarGraphic.initialsFor(u), "admin-profile-sidebar-avatar");
            StackPane wrap = new StackPane();
            wrap.getChildren().add(avatar);
            Label cam = new Label("📷");
            cam.setMouseTransparent(true);
            cam.getStyleClass().add("admin-profile-camera-badge");
            StackPane.setAlignment(cam, Pos.BOTTOM_RIGHT);
            wrap.getChildren().add(cam);
            sidebarAvatarHost.getChildren().add(wrap);
        }
    }

    private void loadStats(User current) throws SQLException {
        int uid = current.getId();
        statProduitsValue.setText(String.valueOf(productService.countByUserId(uid)));
        statCommentairesValue.setText(String.valueOf(commentaireService.countByUserId(uid)));
        statArticlesValue.setText(String.valueOf(blogService.countByUserId(uid)));
    }

    private void updateCompletion(User u) {
        int ok = 0;
        int total = 5;
        if (u.getPrenom() != null && !u.getPrenom().isBlank()) {
            ok++;
        }
        if (u.getNom() != null && !u.getNom().isBlank()) {
            ok++;
        }
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            ok++;
        }
        if (u.getTelephone() != null && !u.getTelephone().isBlank()) {
            ok++;
        }
        if (u.getImage() != null && !u.getImage().isBlank()) {
            ok++;
        }
        double p = total == 0 ? 0 : ok / (double) total;
        profileProgressBar.setProgress(p);
        profileProgressLabel.setText("Profil complété — " + Math.round(p * 100) + " %");
    }

    private static String roleDisplay(Role r) {
        if (r == null) {
            return "";
        }
        return switch (r) {
            case ADMIN -> "ADMIN";
            case MEDECIN -> "MÉDECIN";
            case PARENT -> "PARENT";
            case PATIENT -> "PATIENT";
            case USER -> "USER";
        };
    }

    @FXML
    public void onBackdropClick() {
        safeClose();
    }

    @FXML
    public void onClose() {
        safeClose();
    }

    @FXML
    public void onChoosePhoto() {
        FileChooser ch = new FileChooser();
        ch.setTitle("Photo de profil");
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        Stage st = choosePhotoButton != null && choosePhotoButton.getScene() != null
                ? (Stage) choosePhotoButton.getScene().getWindow()
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
        if (editingUser == null) {
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
            if (chosenPhoto != null) {
                u.setImage(UserImageStorage.copyUserImage(chosenPhoto));
            }
            userService.update(u);
            AppState.setCurrentUser(u);
            editingUser = u;
            bindSidebar(u);
            updateCompletion(u);
            chosenPhoto = null;
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

    @FXML
    public void onLogout() {
        AppState.clear();
        safeClose();
        try {
            MainApp.showLogin();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
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
        a.initOwner(dialogStage);
        a.showAndWait();
    }
}
