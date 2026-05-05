package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.MainApp;
import org.example.models.ModuleContent;
import org.example.models.Ressource;
import org.example.services.ModuleService;
import org.example.services.RessourceService;
import org.example.services.YouTubeService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;

public class AdminRessourceAddController {

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
    @FXML private VBox youtubeBox;
    @FXML private TextField youtubeSearchField;
    @FXML private FlowPane youtubeResultsFlow;
    @FXML private Label youtubeHint;

    private final RessourceService ressourceService = new RessourceService();
    private final ModuleService moduleService = new ModuleService();
    private final YouTubeService youTubeService = new YouTubeService();

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        typeCombo.getItems().addAll(TYPES);
        typeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String value) {
                if (value == null || value.isBlank()) {
                    return "";
                }
                return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        });
        typeCombo.valueProperty().addListener((obs, old, val) -> updateVideoBoxVisibility());
        updateVideoBoxVisibility();
        loadModules();
    }

    private void loadModules() {
        try {
            List<ModuleContent> modules = moduleService.findAll();
            moduleCombo.getItems().setAll(modules);
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", "Impossible de charger les modules.");
        }
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
    public void onSearchYoutubeVideos() {
        String q = youtubeSearchField != null && youtubeSearchField.getText() != null
                ? youtubeSearchField.getText().trim()
                : "";
        if (q.isBlank()) {
            alert(Alert.AlertType.WARNING, "YouTube", "Saisissez des mots-clés pour rechercher une vidéo.");
            return;
        }
        if (youtubeHint != null) {
            youtubeHint.setText("Recherche YouTube en cours...");
        }
        if (youtubeResultsFlow != null) {
            youtubeResultsFlow.getChildren().clear();
        }
        Thread worker = new Thread(() -> {
            try {
                List<YouTubeService.YouTubeVideo> videos = youTubeService.searchVideos(q, 8);
                javafx.application.Platform.runLater(() -> fillYoutubeResults(videos));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    if (youtubeHint != null) {
                        youtubeHint.setText("Erreur de recherche YouTube.");
                    }
                    alert(Alert.AlertType.ERROR, "YouTube",
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                });
            }
        }, "youtube-search-resource-add");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    public void onSaveRessource() {
        String titre = trim(titreField);
        if (titre.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Champs requis", "Indiquez un titre.");
            return;
        }
        if (titre.length() > 255) {
            alert(Alert.AlertType.WARNING, "Titre", "Le titre ne doit pas dépasser 255 caractères.");
            return;
        }
        
        // Vérification de l'unicité du titre
        try {
            if (ressourceService.existsByTitre(titre, null)) {
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
        if ("video".equalsIgnoreCase(type) && (contenu == null || contenu.isBlank())) {
            alert(Alert.AlertType.WARNING, "Vidéo requise", "Choisissez une vidéo YouTube ou renseignez une URL vidéo.");
            return;
        }

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

        Ressource r = new Ressource();
        r.setTitre(titre);
        r.setTypeRessource(type);
        r.setContenu(contenu);
        r.setOrdre(ordre);
        r.setActive(activeCheck.isSelected());
        r.setModuleId(selectedModule.getId());

        try {
            ressourceService.add(r);
            alert(Alert.AlertType.INFORMATION, "Ressource créée", "La ressource a été enregistrée.");
            onBackToModules();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur base de données",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @FXML
    public void onCancel() {
        onBackToModules();
    }

    @FXML
    public void onBackToModules() {
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

    private void updateVideoBoxVisibility() {
        boolean show = typeCombo != null && "video".equalsIgnoreCase(typeCombo.getValue());
        if (youtubeBox != null) {
            youtubeBox.setManaged(show);
            youtubeBox.setVisible(show);
        }
    }

    private void fillYoutubeResults(List<YouTubeService.YouTubeVideo> videos) {
        if (youtubeResultsFlow == null) {
            return;
        }
        youtubeResultsFlow.getChildren().clear();
        if (videos == null || videos.isEmpty()) {
            if (youtubeHint != null) {
                youtubeHint.setText("Aucune vidéo trouvée. Essayez d'autres mots-clés.");
            }
            return;
        }
        for (YouTubeService.YouTubeVideo v : videos) {
            youtubeResultsFlow.getChildren().add(buildYouTubeCard(v));
        }
        if (youtubeHint != null) {
            youtubeHint.setText("Cliquez sur \"Utiliser\" pour ajouter la vidéo comme ressource.");
        }
    }

    private VBox buildYouTubeCard(YouTubeService.YouTubeVideo v) {
        ImageView thumb = new ImageView();
        if (v.thumbnailUrl() != null && !v.thumbnailUrl().isBlank()) {
            thumb.setImage(new Image(v.thumbnailUrl(), 220, 124, true, true, true));
        }
        thumb.setFitWidth(220);
        thumb.setFitHeight(124);
        thumb.getStyleClass().add("res-yt-thumb");

        Label title = new Label(v.title() != null ? v.title() : "Vidéo YouTube");
        title.setWrapText(true);
        title.getStyleClass().add("res-yt-title");
        title.setMaxWidth(220);

        Label channel = new Label(v.channel() != null ? v.channel() : "");
        channel.getStyleClass().add("res-yt-channel");
        channel.setMaxWidth(220);

        javafx.scene.control.Button openBtn = new javafx.scene.control.Button("Ouvrir");
        openBtn.getStyleClass().add("res-yt-open-btn");
        openBtn.setOnAction(e -> openBrowser(v.watchUrl()));

        javafx.scene.control.Button useBtn = new javafx.scene.control.Button("Utiliser");
        useBtn.getStyleClass().add("res-yt-use-btn");
        useBtn.setOnAction(e -> selectYouTubeVideo(v));

        HBox actions = new HBox(8, openBtn, useBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, thumb, title, channel, actions);
        card.getStyleClass().add("res-yt-card");
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) {
                selectYouTubeVideo(v);
            }
        });
        return card;
    }

    private void selectYouTubeVideo(YouTubeService.YouTubeVideo v) {
        if (v == null) {
            return;
        }
        if (contenuField != null) {
            contenuField.setText(v.watchUrl());
        }
        if ((titreField == null || titreField.getText() == null || titreField.getText().trim().isBlank())
                && v.title() != null && !v.title().isBlank()) {
            titreField.setText(v.title());
        }
        if (youtubeHint != null) {
            youtubeHint.setText("Vidéo sélectionnée: " + (v.title() != null ? v.title() : "YouTube"));
        }
    }

    private void openBrowser(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // no-op
        }
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
