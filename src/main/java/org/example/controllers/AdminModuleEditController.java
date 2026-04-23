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

public class AdminModuleEditController {

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
    private ModuleContent editingModule;
    private Integer editingAdminId;

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        niveauCombo.getItems().setAll(ModuleNiveau.values());
        categorieCombo.getItems().setAll(ModuleCategorie.values());
        categorieCombo.setConverter(ModuleCategorieStringConverter.INSTANCE);

        editingModule = AppState.getAdminEditModule();
        if (editingModule == null) {
            navigateToModulesList();
            return;
        }
        try {
            var fresh = moduleService.findById(editingModule.getId());
            if (fresh.isPresent()) {
                editingModule = fresh.get();
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
            navigateToModulesList();
            return;
        }

        titreField.setText(editingModule.getTitre());
        descriptionField.setText(editingModule.getDescription());
        if (editingModule.getContenu() != null) {
            contenuField.setText(editingModule.getContenu());
        }
        niveauCombo.getSelectionModel().select(
                editingModule.getNiveau() != null ? editingModule.getNiveau() : ModuleNiveau.moyen);
        categorieCombo.getSelectionModel().select(editingModule.getCategorieEnum());
        imageField.setText(editingModule.getImage());
        publishedCheck.setSelected(editingModule.isPublished());
        editingAdminId = editingModule.getAdminId();
    }

    private void navigateToModulesList() {
        AppState.clearAdminModuleEditContext();
        try {
            MainApp.showAdminModules();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
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
                alert(Alert.AlertType.WARNING, "Image", "Le chemin est trop long (max " + MAX_IMAGE + " caractères).");
                return;
            }
            imageField.setText(path);
            System.out.println("Image sélectionnée : " + file.getAbsolutePath());
        }
    }

    @FXML
    public void onBackToModules() {
        navigateToModulesList();
    }

    @FXML
    public void onCancel() {
        navigateToModulesList();
    }

    @FXML
    public void onSaveModule() {
        if (editingModule == null) {
            navigateToModulesList();
            return;
        }
        String titre = trim(titreField);
        String desc = descriptionField.getText() != null ? descriptionField.getText().trim() : "";
        if (titre.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Indiquez un titre.");
            return;
        }
        
        // Vérification de l'unicité du titre (exclure le module en cours d'édition)
        try {
            if (moduleService.existsByTitre(titre, editingModule.getId())) {
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
        m.setId(editingModule.getId());
        m.setTitre(titre);
        m.setDescription(desc);
        m.setContenu(cont);
        m.setNiveau(niv);
        m.setCategorieEnum(cat);
        m.setImage(img);
        m.setPublished(publishedCheck.isSelected());
        m.setAdminId(editingAdminId);

        try {
            moduleService.update(m);
            ModuleActionHistory.record("Modification", m.getTitre(), m.getId());
            alert(Alert.AlertType.INFORMATION, "Module mis à jour", "Les modifications ont été enregistrées.");
            AppState.clearAdminModuleEditContext();
            MainApp.showAdminModules();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur base de données", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
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
            AppState.clearAdminModuleEditContext();
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
