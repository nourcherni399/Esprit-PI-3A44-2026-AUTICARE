package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.MainApp;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;
import org.example.services.ModuleService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.ModuleActionHistory;
import org.example.utils.ModuleCategorieStringConverter;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class AdminModuleAddController {

    private static final int MIN_DESC = 10;
    private static final int MAX_DESC = 255;
    private static final int MIN_CONTENU = 20;
    private static final int MAX_IMAGE = 255;

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private TextField titreField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private TextArea contenuField;
    @FXML
    private ComboBox<ModuleNiveau> niveauCombo;
    @FXML
    private ComboBox<ModuleCategorie> categorieCombo;
    @FXML
    private TextField imageField;
    @FXML
    private CheckBox publishedCheck;

    private final ModuleService moduleService = new ModuleService();

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        niveauCombo.getItems().setAll(ModuleNiveau.values());
        categorieCombo.getItems().setAll(ModuleCategorie.values());
        categorieCombo.setConverter(ModuleCategorieStringConverter.INSTANCE);
        niveauCombo.getSelectionModel().select(ModuleNiveau.moyen);
        categorieCombo.getSelectionModel().select(ModuleCategorie.COMPRENDRE_TSA);
    }

    @FXML
    public void onChooseImageFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                "Images (JPG, PNG, GIF, WebP)",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"
            )
        );
        Stage st = (Stage) titreField.getScene().getWindow();
        File file = chooser.showOpenDialog(st);
        if (file != null) {
            String path = file.getAbsolutePath();
            if (path.length() > MAX_IMAGE) {
                alert(Alert.AlertType.WARNING, "Image", "Le chemin est trop long (max " + MAX_IMAGE + " caractères). Utilisez une URL plus courte ou déplacez le fichier.");
                return;
            }
            imageField.setText(path);
            System.out.println("Image sélectionnée : " + file.getAbsolutePath());
        }
    }

    @FXML
    public void onBackToModules() {
        try {
            MainApp.showAdminModules();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        onBackToModules();
    }

    @FXML
    public void onSaveModule() {
        String titre = trim(titreField);
        String desc = descriptionField.getText() != null ? descriptionField.getText().trim() : "";
        if (titre.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Indiquez un titre.");
            return;
        }
        
        // Vérification de l'unicité du titre
        try {
            if (moduleService.existsByTitre(titre, null)) {
                alert(Alert.AlertType.WARNING, "Titre existant", "Ce titre existe déjà. Veuillez choisir un titre unique.");
                return;
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", "Impossible de vérifier l'unicité du titre : " + e.getMessage());
            return;
        }
        if (desc.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Indiquez une description.");
            return;
        }
        if (desc.length() < MIN_DESC) {
            alert(Alert.AlertType.WARNING, "Description",
                    "La description doit contenir au moins " + MIN_DESC + " caractères.");
            return;
        }
        if (desc.length() > MAX_DESC) {
            alert(Alert.AlertType.WARNING, "Description", "La description ne doit pas dépasser " + MAX_DESC + " caractères.");
            return;
        }
        String cont = contenuField.getText() != null ? contenuField.getText().trim() : "";
        if (cont.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Contenu", "Indiquez le contenu détaillé du module.");
            return;
        }
        if (cont.length() < MIN_CONTENU) {
            alert(Alert.AlertType.WARNING, "Contenu",
                    "Le contenu doit contenir au moins " + MIN_CONTENU + " caractères.");
            return;
        }
        ModuleNiveau niv = niveauCombo.getSelectionModel().getSelectedItem();
        if (niv == null) {
            alert(Alert.AlertType.WARNING, "Niveau", "Choisissez un niveau.");
            return;
        }
        ModuleCategorie cat = categorieCombo.getSelectionModel().getSelectedItem();
        if (cat == null) {
            alert(Alert.AlertType.WARNING, "Catégorie", "Choisissez une catégorie.");
            return;
        }
        String img = trim(imageField);
        if (img.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Image", "Indiquez une URL, un chemin, ou choisissez un fichier.");
            return;
        }
        if (img.length() > MAX_IMAGE) {
            alert(Alert.AlertType.WARNING, "Image", "La valeur image ne doit pas dépasser " + MAX_IMAGE + " caractères.");
            return;
        }

        ModuleContent m = new ModuleContent();
        m.setTitre(titre);
        m.setDescription(desc);
        m.setContenu(cont);
        m.setNiveau(niv);
        m.setCategorieEnum(cat);
        m.setImage(img);
        m.setPublished(publishedCheck.isSelected());
        m.setAdminId(null);

        try {
            moduleService.add(m);
            ModuleActionHistory.record("Création", m.getTitre(), -1);
            alert(Alert.AlertType.INFORMATION, "Module créé", "Le module a été enregistré.");
            onBackToModules();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur base de données", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static String trim(TextField f) {
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
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavStocks() {
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavOrders() {
        try {
            MainApp.showDashboard(1);
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
