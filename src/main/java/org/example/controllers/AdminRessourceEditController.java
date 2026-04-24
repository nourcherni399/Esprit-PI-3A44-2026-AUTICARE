package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.MainApp;
import org.example.models.ModuleContent;
import org.example.models.Ressource;
import org.example.services.ModuleService;
import org.example.services.RessourceService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

public class AdminRessourceEditController {

    private static final String[] TYPES = {"url", "video", "audio", "pdf", "image", "document"};

    @FXML private StackPane topbarAvatarHost;
    @FXML private Label userNameLabel;
    @FXML private Label userEmailLabel;
    @FXML private TextField topSearchField;

    @FXML private TextField titreField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField contenuField;
    @FXML private Label fileLabel;
    @FXML private ComboBox<ModuleContent> moduleCombo;
    @FXML private TextField ordreField;
    @FXML private CheckBox activeCheck;

    private final RessourceService ressourceService = new RessourceService();
    private final ModuleService moduleService = new ModuleService();
    private Ressource editing;

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        typeCombo.getItems().addAll(TYPES);

        Ressource src = AppState.getAdminEditRessource();
        if (src == null) {
            Platform.runLater(() -> {
                alert(Alert.AlertType.WARNING, "Ressource", "Aucune ressource à modifier.");
                navigateBack();
            });
            return;
        }
        try {
            editing = ressourceService.findById(src.getId()).orElse(src);
        } catch (SQLException e) {
            editing = src;
        }
        loadModules();
        fillForm(editing);
    }

    private void loadModules() {
        try {
            List<ModuleContent> modules = moduleService.findAll();
            moduleCombo.getItems().setAll(modules);
            for (ModuleContent m : modules) {
                if (m.getId() == editing.getModuleId()) {
                    moduleCombo.getSelectionModel().select(m);
                    break;
                }
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", "Impossible de charger les modules.");
        }
    }

    private void fillForm(Ressource r) {
        titreField.setText(r.getTitre() != null ? r.getTitre() : "");
        typeCombo.setValue(r.getTypeRessource());
        String c = r.getContenu();
        contenuField.setText(c != null ? c : "");
        if (c != null && !c.isBlank()) {
            try {
                Path p = Paths.get(c);
                if (p.getFileName() != null) {
                    fileLabel.setText(p.getFileName().toString());
                } else {
                    fileLabel.setText("—");
                }
            } catch (Exception e) {
                fileLabel.setText("—");
            }
        } else {
            fileLabel.setText("Aucun fichier choisi");
        }
        ordreField.setText(r.getOrdre() != null ? String.valueOf(r.getOrdre()) : "");
        activeCheck.setSelected(r.isActive());
    }

    @FXML
    public void onChooseFile() {
        FileChooser ch = new FileChooser();
        ch.setTitle("Fichier vidéo/audio");
        ch.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Médias", "*.mp4", "*.mp3", "*.wav", "*.avi", "*.mkv", "*.ogg", "*.webm"),
                new FileChooser.ExtensionFilter("Tous", "*.*"));
        Stage st = (Stage) titreField.getScene().getWindow();
        File f = ch.showOpenDialog(st);
        if (f != null) {
            contenuField.setText(f.getAbsolutePath());
            fileLabel.setText(f.getName());
        }
    }

    @FXML
    public void onSaveEdits() {
        if (editing == null) {
            return;
        }
        String titre = trim(titreField);
        if (titre.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Indiquez un titre.");
            return;
        }
        if (titre.length() > 255) {
            alert(Alert.AlertType.WARNING, "Titre", "Le titre ne doit pas dépasser 255 caractères.");
            return;
        }
        
        // Vérification de l'unicité du titre (exclure la ressource en cours d'édition)
        try {
            if (ressourceService.existsByTitre(titre, editing.getId())) {
                alert(Alert.AlertType.WARNING, "Titre existant", "Ce titre existe déjà. Veuillez choisir un titre unique.");
                return;
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", "Impossible de vérifier l'unicité du titre : " + e.getMessage());
            return;
        }
        String type = typeCombo.getValue();
        if (type == null || type.isBlank()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Choisissez un type de ressource.");
            return;
        }
        ModuleContent selectedModule = moduleCombo.getValue();
        if (selectedModule == null) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Choisissez un module associé.");
            return;
        }
        String contenu = contenuField.getText() != null ? contenuField.getText().trim() : null;

        Integer ordre = null;
        String ordreText = trim(ordreField);
        if (!ordreText.isEmpty()) {
            try {
                ordre = Integer.parseInt(ordreText);
                if (ordre < 0) {
                    alert(Alert.AlertType.WARNING, "Ordre", "L'ordre doit être un nombre positif.");
                    return;
                }
            } catch (NumberFormatException e) {
                alert(Alert.AlertType.WARNING, "Ordre", "L'ordre doit être un nombre entier valide.");
                return;
            }
        }

        editing.setTitre(titre);
        editing.setTypeRessource(type);
        editing.setContenu(contenu);
        editing.setOrdre(ordre);
        editing.setActive(activeCheck.isSelected());
        editing.setModuleId(selectedModule.getId());

        try {
            ressourceService.update(editing);
            alert(Alert.AlertType.INFORMATION, "Ressource mise à jour", "Les modifications ont été enregistrées.");
            AppState.clearAdminRessourceEditContext();
            MainApp.showAdminModules();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur base de données",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        navigateBack();
    }

    @FXML
    public void onBackToModules() {
        navigateBack();
    }

    private void navigateBack() {
        AppState.clearAdminRessourceEditContext();
        try {
            MainApp.showAdminModules();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private static String trim(TextField f) {
        if (f == null || f.getText() == null) return "";
        return f.getText().trim();
    }

    @FXML public void onOpenMyProfile() {
        try { MainApp.openAdminMyProfile(topbarAvatarHost, userNameLabel, userEmailLabel); }
        catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); }
    }
    @FXML public void onLogout() {
        AppState.clear();
        try { MainApp.showLogin(); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); }
    }
    @FXML public void onNavDashboard() { try { MainApp.showDashboard(0); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavUsers() { try { MainApp.showAdminUsers(); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavProducts() { try { MainApp.showAdminProducts(); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavStocks() { try { MainApp.showAdminStocks(); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavOrders() { try { MainApp.showAdminOrders(); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavEvents() { try { MainApp.showDashboard(4); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavTopics() { try { MainApp.showDashboard(7); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavModules() { try { MainApp.showAdminModules(); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavSettings() { try { MainApp.showDashboard(0); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }
    @FXML public void onNavAdminHome() { try { MainApp.showHome(); } catch (IOException e) { alert(Alert.AlertType.ERROR, "Erreur", e.getMessage()); } }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
