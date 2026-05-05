package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.RadioButton;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.MainApp;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;
import org.example.services.ModuleService;
import org.example.services.PexelsService;
import org.example.utils.PexelsThumbGrid;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.ModuleActionHistory;
import org.example.utils.ModuleCategorieStringConverter;

import java.io.File;
import java.io.IOException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.util.List;

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
    private RadioButton moduleRbUpload;
    @FXML
    private RadioButton moduleRbPexels;
    @FXML
    private VBox moduleUploadImagePanel;
    @FXML
    private VBox modulePexelsPanel;
    @FXML
    private TextField modulePexelsSearchField;
    @FXML
    private FlowPane modulePexelsFlow;
    @FXML
    private Label modulePexelsHint;
    @FXML
    private Label moduleImageFileLabel;
    @FXML
    private ImageView moduleImagePreview;
    @FXML
    private CheckBox publishedCheck;

    private final ModuleService moduleService = new ModuleService();
    private final PexelsService pexelsService = new PexelsService();
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
        if (moduleImageFileLabel != null && editingModule.getImage() != null && !editingModule.getImage().isBlank()) {
            String name = editingModule.getImage();
            int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            if (sep >= 0) {
                name = name.substring(sep + 1);
            }
            if (name.length() > 80) {
                name = name.substring(0, 77) + "…";
            }
            moduleImageFileLabel.setText(name);
            
            // Preview de l'image existante
            if (moduleImagePreview != null) {
                try {
                    String imgUrl = editingModule.getImage();
                    if (!imgUrl.startsWith("http") && !imgUrl.startsWith("file")) {
                        imgUrl = new File(imgUrl).toURI().toString();
                    }
                    moduleImagePreview.setImage(new Image(imgUrl, true));
                    moduleImagePreview.setVisible(true);
                    moduleImagePreview.setManaged(true);
                    moduleImageFileLabel.setVisible(false);
                    moduleImageFileLabel.setManaged(false);
                } catch (Exception e) {
                    moduleImagePreview.setVisible(false);
                    moduleImagePreview.setManaged(false);
                    moduleImageFileLabel.setVisible(true);
                    moduleImageFileLabel.setManaged(true);
                }
            }
        } else if (moduleImageFileLabel != null) {
            moduleImageFileLabel.setText("Aucun fichier choisi");
            moduleImageFileLabel.setVisible(true);
            moduleImageFileLabel.setManaged(true);
            if (moduleImagePreview != null) {
                moduleImagePreview.setImage(null);
                moduleImagePreview.setVisible(false);
                moduleImagePreview.setManaged(false);
            }
        }
        publishedCheck.setSelected(editingModule.isPublished());
        editingAdminId = editingModule.getAdminId();
        setupModuleImagePanels();
    }

    private void setupModuleImagePanels() {
        if (moduleRbUpload == null || moduleRbUpload.getToggleGroup() == null) {
            return;
        }
        moduleRbUpload.getToggleGroup().selectedToggleProperty().addListener((obs, o, n) -> updateModuleImagePanels());
        updateModuleImagePanels();
    }

    private void updateModuleImagePanels() {
        if (moduleUploadImagePanel == null || modulePexelsPanel == null) {
            return;
        }
        boolean upload = moduleRbUpload == null || moduleRbUpload.isSelected();
        moduleUploadImagePanel.setVisible(upload);
        moduleUploadImagePanel.setManaged(upload);
        modulePexelsPanel.setVisible(!upload);
        modulePexelsPanel.setManaged(!upload);
    }

    @FXML
    public void onDictateModuleTitre() {
        startWindowsDictation(titreField);
    }

    @FXML
    public void onDictateModuleDescription() {
        startWindowsDictation(descriptionField);
    }

    @FXML
    public void onDictateModuleContenu() {
        startWindowsDictation(contenuField);
    }

    @FXML
    public void onDictateModuleImageField() {
        startWindowsDictation(imageField);
    }

    @FXML
    public void onDictateModulePexelsSearch() {
        startWindowsDictation(modulePexelsSearchField);
    }

    private void startWindowsDictation(TextInputControl target) {
        if (target == null) return;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            alert(Alert.AlertType.INFORMATION, "Dictée",
                    "Fonction prévue pour Windows. Cliquez dans le champ puis utilisez le raccourci système de dictée.");
            return;
        }
        target.requestFocus();
        target.positionCaret(target.getLength());
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(120);
                Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_WINDOWS);
                robot.keyPress(KeyEvent.VK_H);
                robot.keyRelease(KeyEvent.VK_H);
                robot.keyRelease(KeyEvent.VK_WINDOWS);
            } catch (Exception e) {
                Platform.runLater(() -> alert(Alert.AlertType.WARNING, "Dictée",
                        "Impossible d'ouvrir la dictée automatiquement. Utilisez Win + H."));
            }
        }, "windows-dictation-admin-edit");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    public void onModulePexelsSearch() {
        String q = modulePexelsSearchField != null ? modulePexelsSearchField.getText().trim() : "";
        if (q.isBlank()) {
            alert(Alert.AlertType.WARNING, "Pexels", "Saisissez des mots-clés (ex. autisme, famille…).");
            return;
        }
        if (modulePexelsFlow != null) {
            modulePexelsFlow.getChildren().clear();
        }
        Thread worker = new Thread(() -> {
            try {
                List<PexelsService.PexelsPhoto> list = pexelsService.search(q, 18);
                Platform.runLater(() -> {
                    if (modulePexelsFlow != null) {
                        PexelsThumbGrid.fill(modulePexelsFlow, list, this::runModulePexelsPick);
                    }
                    if (modulePexelsHint != null) {
                        modulePexelsHint.setText(list.isEmpty()
                                ? "Aucun résultat. Essayez d'autres mots-clés."
                                : "Cliquez sur une image pour la télécharger et remplir le champ image.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Pexels",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }, "admin-module-edit-pexels-search");
        worker.setDaemon(true);
        worker.start();
    }

    private void runModulePexelsPick(PexelsService.PexelsPhoto photo) {
        Thread worker = new Thread(() -> {
            try {
                String path;
                boolean usedFallbackUrl = false;
                try {
                    path = pexelsService.downloadToLocalFile(photo.downloadUrl(), photo.id());
                } catch (Exception ex) {
                    String fallback = photo.downloadUrl();
                    if (fallback == null || fallback.isBlank() || fallback.length() > MAX_IMAGE) {
                        throw ex;
                    }
                    path = fallback;
                    usedFallbackUrl = true;
                }
                if (path.length() > MAX_IMAGE) {
                    String fallback = photo.downloadUrl();
                    if (fallback == null || fallback.isBlank() || fallback.length() > MAX_IMAGE) {
                        Platform.runLater(() -> alert(Alert.AlertType.WARNING, "Image",
                                "Chemin/URL trop long (max " + MAX_IMAGE + " caractères)."));
                        return;
                    }
                    path = fallback;
                    usedFallbackUrl = true;
                }
                final String selectedPath = path;
                final boolean fallbackUsed = usedFallbackUrl;
                Platform.runLater(() -> {
                    if (imageField != null) {
                        imageField.setText(selectedPath);
                    }
                    if (moduleImageFileLabel != null) {
                        String name = selectedPath;
                        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                        if (sep >= 0) {
                            name = name.substring(sep + 1);
                        }
                        moduleImageFileLabel.setText(fallbackUsed ? "Pexels (URL) — " + name : "Pexels — " + name);
                    }
                    if (moduleImagePreview != null) {
                        try {
                            String imgUrl = selectedPath;
                            if (!imgUrl.startsWith("http") && !imgUrl.startsWith("file")) {
                                imgUrl = new File(imgUrl).toURI().toString();
                            }
                            moduleImagePreview.setImage(new Image(imgUrl, true));
                            moduleImagePreview.setVisible(true);
                            moduleImagePreview.setManaged(true);
                            if (moduleImageFileLabel != null) {
                                moduleImageFileLabel.setVisible(false);
                                moduleImageFileLabel.setManaged(false);
                            }
                        } catch (Exception e) {
                            moduleImagePreview.setVisible(false);
                            moduleImagePreview.setManaged(false);
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Pexels",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }, "admin-module-edit-pexels-download");
        worker.setDaemon(true);
        worker.start();
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
            if (moduleImageFileLabel != null) {
                moduleImageFileLabel.setText(file.getName());
            }
            if (moduleImagePreview != null) {
                try {
                    Image img = new Image(file.toURI().toString());
                    moduleImagePreview.setImage(img);
                    moduleImagePreview.setVisible(true);
                    moduleImagePreview.setManaged(true);
                    if (moduleImageFileLabel != null) {
                        moduleImageFileLabel.setVisible(false);
                        moduleImageFileLabel.setManaged(false);
                    }
                } catch (Exception e) {
                    moduleImagePreview.setVisible(false);
                    moduleImagePreview.setManaged(false);
                }
            }
            if (moduleRbUpload != null) {
                moduleRbUpload.setSelected(true);
            }
            updateModuleImagePanels();
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
        try {
            ModuleService.LevelProgressionValidation progression =
                    moduleService.validateNiveauProgression(cat, niv, editingModule != null ? editingModule.getId() : null);
            if (!progression.allowed()) {
                alert(Alert.AlertType.WARNING, "Progression des niveaux", progression.message());
                return;
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", "Impossible de vérifier la progression des niveaux : " + e.getMessage());
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
