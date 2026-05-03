package org.example.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.models.BlogArticle;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;
import org.example.models.User;
import org.example.services.BlogService;
import org.example.services.ModuleService;
import org.example.services.UserService;
import org.example.utils.AppState;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PageBlogController implements PublicShellAware {

    private static final double CAT_CARD_W = 330;
    private static final double CAT_CARD_H = 200;
    private static final double MOD_CARD_W = 300;
    /** Décodage image détail module : bandeau 300×150 px. */
    private static final double MODULE_DETAIL_IMG_DECODE_W = 300;
    private static final double MODULE_DETAIL_IMG_H = 150;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private static final String[] ARTICLE_TYPES = {"recommandation", "plainte", "question", "experience"};

    private static final int MIN_TITRE = 3;
    private static final int MAX_TITRE = 255;
    private static final int MIN_CONTENU = 10;
    private static final int MAX_IMAGE = 255;

    private static final Map<ModuleCategorie, String> CAT_DESCRIPTIONS = Map.of(
            ModuleCategorie.COMPRENDRE_TSA, "Découvrez tous les modules d'apprentissage de la catégorie Comprendre le TSA.",
            ModuleCategorie.AUTONOMIE, "Modules pour développer l'autonomie au quotidien.",
            ModuleCategorie.COMMUNICATION, "Outils et stratégies pour améliorer la communication.",
            ModuleCategorie.EMOTIONS, "Apprendre à identifier et gérer les émotions.",
            ModuleCategorie.VIE_QUOTIDIENNE, "Activités et conseils pour la vie de tous les jours.",
            ModuleCategorie.ACCOMPAGNEMENT, "Ressources pour les accompagnants et les familles."
    );

    private PublicShellController shell;
    private final ModuleService moduleService = new ModuleService();
    private final BlogService blogService = new BlogService();
    private final UserService userService = new UserService();
    private final org.example.services.RessourceService ressourceService = new org.example.services.RessourceService();
    private ModuleCategorie currentCategory;
    private ModuleContent currentModule;
    private String selectedImagePath;
    private List<BlogArticle> currentArticles;
    private final Map<Integer, String> userEmailCache = new HashMap<>();

    /* ── FXML: shared ── */
    @FXML private StackPane heroBand;
    @FXML private StackPane blogContentHost;

    /* ── FXML: vue 1 — catégories ── */
    @FXML private VBox categoriesView;
    @FXML private FlowPane categoryCardsPane;

    /* ── FXML: vue 2 — liste modules d'une catégorie ── */
    @FXML private VBox categoryDetailView;
    @FXML private FlowPane moduleCardsPane;
    @FXML private ImageView detailHeaderImg;
    @FXML private Label detailCatTitle;
    @FXML private Label detailCatDesc;

    /* ── FXML: vue 3 — détail d'un module ── */
    @FXML private VBox moduleDetailView;
    @FXML private Label mdTitle;
    @FXML private Label mdNiveauBadge;
    @FXML private Label mdDate;
    @FXML private Label mdDescription;
    @FXML private StackPane mdImageWrap;
    @FXML private ImageView mdImage;
    @FXML private Label mdContenu;
    @FXML private Label mdRessources;
    @FXML private VBox mdRessourcesContainer;
    @FXML private VBox mdArticlesContainer;
    @FXML private Label mdArticlesPlaceholder;
    @FXML private TextField articleSearchField;
    @FXML private HBox loginPromptBox;
    @FXML private Hyperlink loginLink;
    @FXML private HBox socialIconsBar;

    /* ── FXML: formulaire ajout article ── */
    @FXML private VBox articleFormContainer;
    @FXML private TextArea aiPromptField;
    @FXML private ComboBox<String> aiTypeCombo;
    @FXML private TextField articleTitreField;
    @FXML private ComboBox<String> articleTypeCombo;
    @FXML private TextArea articleContenuField;
    @FXML private RadioButton rbUploadFile;
    @FXML private RadioButton rbPexels;
    @FXML private Label articleImageLabel;
    @FXML private CheckBox cbPublished;
    @FXML private CheckBox cbUrgent;
    @FXML private CheckBox cbVisible;

    /* ── FXML: vue 4 — modifier article ── */
    @FXML private VBox editArticleView;
    @FXML private TextArea editAiPromptField;
    @FXML private ComboBox<String> editAiTypeCombo;
    @FXML private TextField editTitreField;
    @FXML private ComboBox<String> editTypeCombo;
    @FXML private TextArea editContenuField;
    @FXML private RadioButton editRbUploadFile;
    @FXML private RadioButton editRbPexels;
    @FXML private Label editImageLabel;
    @FXML private CheckBox editCbPublished;
    @FXML private CheckBox editCbUrgent;
    @FXML private CheckBox editCbVisible;
    private BlogArticle editingArticle;
    private String editSelectedImagePath;

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            ensureBlogCssLoaded();
            buildCategoryCards();
        });
        if (articleSearchField != null) {
            articleSearchField.textProperty().addListener((obs, o, n) -> filterArticles(n));
        }
        if (mdImage != null && mdImageWrap != null) {
            mdImage.setPreserveRatio(true);
            mdImage.setSmooth(true);
            mdImage.fitWidthProperty().bind(mdImageWrap.widthProperty());
            mdImage.fitHeightProperty().bind(mdImageWrap.heightProperty());
            Rectangle imgClip = new Rectangle();
            imgClip.setArcWidth(8);
            imgClip.setArcHeight(8);
            imgClip.widthProperty().bind(mdImageWrap.widthProperty());
            imgClip.heightProperty().bind(mdImageWrap.heightProperty());
            mdImageWrap.setClip(imgClip);
        }
    }

    private void ensureBlogCssLoaded() {
        URL css = getClass().getResource("/styles/page-blog.css");
        if (css == null) return;
        String cssUrl = css.toExternalForm();
        if (blogContentHost != null && blogContentHost.getScene() != null) {
            Parent root = blogContentHost.getScene().getRoot();
            if (root != null && !root.getStylesheets().contains(cssUrl)) {
                root.getStylesheets().add(cssUrl);
            }
        }
    }

    /* ═══════ VUE 1 : catégories ═══════ */

    private void buildCategoryCards() {
        if (categoryCardsPane == null) return;
        categoryCardsPane.getChildren().clear();
        for (ModuleCategorie cat : ModuleCategorie.values()) {
            if (cat == ModuleCategorie.NON_DEFINI) continue;
            categoryCardsPane.getChildren().add(buildCatCard(cat));
        }
    }

    private StackPane buildCatCard(ModuleCategorie cat) {
        StackPane card = new StackPane();
        card.getStyleClass().add("blog-cat-card");
        card.setPrefSize(CAT_CARD_W, CAT_CARD_H);
        card.setMaxSize(CAT_CARD_W, CAT_CARD_H);
        card.setMinSize(CAT_CARD_W, CAT_CARD_H);

        ImageView img = new ImageView();
        img.setFitWidth(CAT_CARD_W);
        img.setFitHeight(CAT_CARD_H);
        img.setPreserveRatio(false);
        img.setSmooth(true);
        Image catImage = loadCatImage(cat);
        if (catImage != null) img.setImage(catImage);
        Rectangle clip = new Rectangle(CAT_CARD_W, CAT_CARD_H);
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        img.setClip(clip);

        Region gradient = new Region();
        gradient.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        gradient.getStyleClass().add("blog-cat-card-gradient");

        VBox overlay = new VBox(8);
        overlay.setAlignment(Pos.BOTTOM_LEFT);
        overlay.setPadding(new Insets(16, 16, 14, 16));

        Label title = new Label(cat.getLibelle());
        title.getStyleClass().add("blog-cat-card-title");
        title.setWrapText(true);

        Button btn = new Button("Voir les modules  >");
        btn.getStyleClass().add("blog-cat-card-btn");
        btn.setOnAction(e -> showCategoryDetail(cat));

        overlay.getChildren().addAll(title, btn);
        card.getChildren().addAll(img, gradient, overlay);
        card.setOnMouseClicked(e -> showCategoryDetail(cat));
        return card;
    }

    private Image loadCatImage(ModuleCategorie cat) {
        String filename = switch (cat) {
            case COMPRENDRE_TSA -> "cat-comprendre-tsa.png";
            case AUTONOMIE -> "cat-autonomie.png";
            case COMMUNICATION -> "cat-communication.png";
            case EMOTIONS -> "cat-emotions.png";
            case VIE_QUOTIDIENNE -> "cat-vie-quotidienne.png";
            case ACCOMPAGNEMENT -> "cat-accompagnement.png";
            default -> null;
        };
        if (filename == null) return null;
        URL u = getClass().getResource("/images/blog/" + filename);
        return u != null ? new Image(u.toExternalForm(), CAT_CARD_W, CAT_CARD_H, false, true) : null;
    }

    /* ═══════ VUE 2 : modules d'une catégorie ═══════ */

    private void showCategoryDetail(ModuleCategorie cat) {
        currentCategory = cat;
        switchView(categoryDetailView);

        detailCatTitle.setText(cat.getLibelle());
        detailCatDesc.setText(CAT_DESCRIPTIONS.getOrDefault(cat, ""));

        Image headerImg = loadCatImage(cat);
        if (headerImg != null) {
            detailHeaderImg.setImage(headerImg);
            Rectangle clip = new Rectangle(120, 120);
            clip.setArcWidth(20);
            clip.setArcHeight(20);
            detailHeaderImg.setClip(clip);
        }
        loadModulesForCategory(cat);
    }

    @FXML
    private void onBackToCategories() {
        switchView(categoriesView);
    }

    private void loadModulesForCategory(ModuleCategorie cat) {
        if (moduleCardsPane == null) return;
        moduleCardsPane.getChildren().clear();
        try {
            List<ModuleContent> modules = moduleService.findAll().stream()
                    .filter(m -> m.getCategorieEnum() == cat)
                    .collect(Collectors.toList());
            if (modules.isEmpty()) {
                Label empty = new Label("Aucun module dans cette catégorie pour l'instant.");
                empty.getStyleClass().add("blog-empty-label");
                empty.setWrapText(true);
                moduleCardsPane.getChildren().add(empty);
                return;
            }
            for (ModuleContent mod : modules) {
                moduleCardsPane.getChildren().add(buildModuleCard(mod));
            }
        } catch (Exception e) {
            Label err = new Label("Erreur de chargement des modules.");
            err.getStyleClass().add("blog-empty-label");
            moduleCardsPane.getChildren().add(err);
        }
    }

    private VBox buildModuleCard(ModuleContent mod) {
        VBox card = new VBox(0);
        card.getStyleClass().add("blog-mod-card");
        card.setPrefWidth(MOD_CARD_W);
        card.setMaxWidth(MOD_CARD_W);

        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("blog-mod-card-img-wrap");
        imgWrap.setPrefHeight(200);
        imgWrap.setMinHeight(200);

        ImageView img = new ImageView();
        img.setFitWidth(MOD_CARD_W);
        img.setFitHeight(200);
        img.setPreserveRatio(false);
        img.setSmooth(true);
        Image modImg = resolveModuleImage(mod.getImage(), MOD_CARD_W, 200);
        if (modImg != null) img.setImage(modImg);
        Rectangle imgClip = new Rectangle(MOD_CARD_W, 200);
        imgClip.setArcWidth(28);
        imgClip.setArcHeight(28);
        img.setClip(imgClip);

        Label niveauBadge = new Label(formatNiveau(mod.getNiveau()));
        niveauBadge.getStyleClass().addAll("blog-mod-badge", niveauBadgeClass(mod.getNiveau()));
        StackPane.setAlignment(niveauBadge, Pos.TOP_LEFT);
        StackPane.setMargin(niveauBadge, new Insets(12, 0, 0, 12));
        imgWrap.getChildren().addAll(img, niveauBadge);

        VBox info = new VBox(6);
        info.getStyleClass().add("blog-mod-card-info");
        info.setPadding(new Insets(14, 16, 16, 16));

        Label catLabel = new Label(mod.getCategorieEnum().getLibelle().toUpperCase());
        catLabel.getStyleClass().add("blog-mod-card-cat");

        Label titleLabel = new Label(mod.getTitre() != null ? mod.getTitre() : "");
        titleLabel.getStyleClass().add("blog-mod-card-title");
        titleLabel.setWrapText(true);

        String descText = mod.getDescription() != null ? mod.getDescription() : "";
        if (descText.length() > 80) descText = descText.substring(0, 77) + "...";
        Label descLabel = new Label(descText);
        descLabel.getStyleClass().add("blog-mod-card-desc");
        descLabel.setWrapText(true);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button readBtn = new Button("Lire  >");
        readBtn.getStyleClass().add("blog-mod-read-btn");
        readBtn.setOnAction(e -> showModuleDetail(mod));

        Label bookmark = new Label("\uD83D\uDD16");
        bookmark.getStyleClass().add("blog-mod-bookmark");
        HBox spacer = new HBox();
        spacer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().addAll(readBtn, spacer, bookmark);

        info.getChildren().addAll(catLabel, titleLabel, descLabel, actions);
        card.getChildren().addAll(imgWrap, info);
        return card;
    }

    /* ═══════ VUE 3 : détail d'un module ═══════ */

    private void showModuleDetail(ModuleContent mod) {
        currentModule = mod;
        switchView(moduleDetailView);

        mdTitle.setText(mod.getTitre() != null ? mod.getTitre() : "");

        mdNiveauBadge.setText(formatNiveau(mod.getNiveau()));
        mdNiveauBadge.getStyleClass().removeAll("blog-mod-badge-facile", "blog-mod-badge-moyen", "blog-mod-badge-difficile");
        mdNiveauBadge.getStyleClass().add(niveauBadgeClass(mod.getNiveau()));

        if (mod.getDateCreation() != null) {
            mdDate.setText("Publié le " + mod.getDateCreation().format(DATE_FMT));
        } else {
            mdDate.setText("");
        }

        mdDescription.setText(mod.getDescription() != null ? mod.getDescription() : "");

        Image modImg = resolveModuleImage(mod.getImage(), MODULE_DETAIL_IMG_DECODE_W, MODULE_DETAIL_IMG_H);
        if (modImg != null) {
            mdImage.setImage(modImg);
            mdImage.setVisible(true);
            mdImage.setManaged(true);
            if (mdImageWrap != null) {
                mdImageWrap.setVisible(true);
                mdImageWrap.setManaged(true);
            }
        } else {
            mdImage.setImage(null);
            mdImage.setVisible(false);
            mdImage.setManaged(false);
            if (mdImageWrap != null) {
                mdImageWrap.setVisible(false);
                mdImageWrap.setManaged(false);
            }
        }

        mdContenu.setText(mod.getContenu() != null ? mod.getContenu() : "");
        
        // Charger et afficher les ressources
        loadRessourcesForModule(mod.getId());

        if (articleSearchField != null) articleSearchField.clear();
        loadArticlesForModule(mod.getId());
        setupArticleForm();
    }

    private void loadRessourcesForModule(int moduleId) {
        if (mdRessourcesContainer == null) return;
        
        // Nettoyer le conteneur
        mdRessourcesContainer.getChildren().clear();
        
        try {
            java.util.List<org.example.models.Ressource> ressources = ressourceService.findByModule(moduleId);
            
            if (ressources == null || ressources.isEmpty()) {
                // Afficher le message par défaut
                Label emptyLabel = new Label("Aucune ressource active pour ce module.");
                emptyLabel.getStyleClass().add("blog-md-ressources-text");
                emptyLabel.setWrapText(true);
                mdRessourcesContainer.getChildren().add(emptyLabel);
            } else {
                // Afficher chaque ressource dans une carte
                for (org.example.models.Ressource res : ressources) {
                    if (res.isActive()) {
                        mdRessourcesContainer.getChildren().add(buildRessourceCard(res));
                    }
                }
            }
        } catch (Exception e) {
            Label errorLabel = new Label("Erreur de chargement des ressources.");
            errorLabel.getStyleClass().add("blog-md-ressources-text");
            mdRessourcesContainer.getChildren().add(errorLabel);
        }
    }

    private VBox buildRessourceCard(org.example.models.Ressource res) {
        VBox card = new VBox(10);
        card.getStyleClass().add("blog-ressource-card");
        card.setPadding(new Insets(14, 16, 14, 16));

        // Première ligne : nom + badge type
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(res.getTitre() != null ? res.getTitre() : "");
        nameLabel.getStyleClass().add("blog-ressource-name");
        nameLabel.setWrapText(true);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label typeBadge = new Label(res.getTypeRessource() != null ? res.getTypeRessource().toUpperCase() : "");
        typeBadge.getStyleClass().add("blog-ressource-type-badge");

        topRow.getChildren().addAll(nameLabel, typeBadge);

        // Deuxième ligne : bouton pour ouvrir/télécharger
        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        // Pour les vidéos, afficher un bouton de lecture
        if ("video".equalsIgnoreCase(res.getTypeRessource())) {
            Button playBtn = new Button("▶");
            playBtn.getStyleClass().add("blog-ressource-download-btn");
            playBtn.setOnAction(e -> {
                if (res.getContenu() != null && !res.getContenu().isEmpty()) {
                    try {
                        java.io.File videoFile = new java.io.File(res.getContenu());
                        if (videoFile.exists()) {
                            java.awt.Desktop.getDesktop().open(videoFile);
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Erreur", "Fichier vidéo introuvable.");
                        }
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'ouvrir la vidéo.");
                    }
                }
            });
            bottomRow.getChildren().add(playBtn);
        } else {
            // Pour les autres types, bouton de téléchargement
            Button downloadBtn = new Button("⬇");
            downloadBtn.getStyleClass().add("blog-ressource-download-btn");
            downloadBtn.setOnAction(e -> {
                if (res.getContenu() != null && !res.getContenu().isEmpty()) {
                    try {
                        java.io.File file = new java.io.File(res.getContenu());
                        if (file.exists()) {
                            java.awt.Desktop.getDesktop().open(file);
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Erreur", "Fichier introuvable.");
                        }
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'ouvrir la ressource.");
                    }
                }
            });
            bottomRow.getChildren().add(downloadBtn);
        }

        card.getChildren().addAll(topRow, bottomRow);

        return card;
    }

    private void setupArticleForm() {
        User user = AppState.getCurrentUser();
        boolean loggedIn = user != null;

        // Masquer le formulaire par défaut
        if (articleFormContainer != null) {
            articleFormContainer.setVisible(false);
            articleFormContainer.setManaged(false);
        }

        // Afficher les icônes sociales SEULEMENT si connecté
        if (socialIconsBar != null) {
            socialIconsBar.setVisible(loggedIn);
            socialIconsBar.setManaged(loggedIn);
        }

        // Afficher le message de connexion SEULEMENT si NON connecté
        if (loginPromptBox != null) {
            loginPromptBox.setVisible(!loggedIn);
            loginPromptBox.setManaged(!loggedIn);
        }

        if (loggedIn && articleTypeCombo != null) {
            articleTypeCombo.setItems(FXCollections.observableArrayList(ARTICLE_TYPES));
            aiTypeCombo.setItems(FXCollections.observableArrayList(ARTICLE_TYPES));
            resetArticleForm();
        }
    }

    @FXML
    private void onShowLogin() {
        try {
            org.example.MainApp.showLogin();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void onToggleArticleForm() {
        User user = AppState.getCurrentUser();
        if (user == null) {
            // Si non connecté, afficher un message
            showAlert(Alert.AlertType.WARNING, "Connexion requise", 
                "Vous devez être connecté pour écrire un article.");
            return;
        }

        if (articleFormContainer != null) {
            // Toggle : afficher/masquer le formulaire
            boolean isVisible = articleFormContainer.isVisible();
            articleFormContainer.setVisible(!isVisible);
            articleFormContainer.setManaged(!isVisible);
            
            // Si on affiche le formulaire, réinitialiser les champs
            if (!isVisible) {
                resetArticleForm();
            }
        }
    }

    private void resetArticleForm() {
        if (articleTitreField != null) articleTitreField.clear();
        if (articleContenuField != null) articleContenuField.clear();
        if (articleTypeCombo != null) articleTypeCombo.getSelectionModel().clearSelection();
        if (aiPromptField != null) aiPromptField.clear();
        if (aiTypeCombo != null) aiTypeCombo.getSelectionModel().clearSelection();
        if (cbPublished != null) cbPublished.setSelected(false);
        if (cbUrgent != null) cbUrgent.setSelected(false);
        if (cbVisible != null) cbVisible.setSelected(true);
        if (articleImageLabel != null) articleImageLabel.setText("Aucun fichier choisi");
        if (rbUploadFile != null) rbUploadFile.setSelected(true);
        selectedImagePath = null;
    }

    @FXML
    private void onChooseArticleImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                "Images (JPG, PNG, GIF, WebP)",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"
            )
        );
        Stage st = (Stage) blogContentHost.getScene().getWindow();
        File file = chooser.showOpenDialog(st);
        if (file != null) {
            String path = file.getAbsolutePath();
            if (path.length() > MAX_IMAGE) {
                showAlert(Alert.AlertType.WARNING, "Image",
                        "Le chemin est trop long (max " + MAX_IMAGE + " caractères). Déplacez le fichier ou utilisez un chemin plus court.");
                return;
            }
            selectedImagePath = path;
            articleImageLabel.setText(file.getName());
            System.out.println("Image sélectionnée : " + file.getAbsolutePath());
        }
    }

    @FXML
    private void onGenerateAI() {
        String prompt = aiPromptField != null ? aiPromptField.getText() : "";
        String type = aiTypeCombo != null ? aiTypeCombo.getValue() : null;
        if (prompt.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "IA", "Veuillez saisir un prompt / sujet.");
            return;
        }
        if (type == null) {
            showAlert(Alert.AlertType.WARNING, "IA", "Veuillez sélectionner un type d'article.");
            return;
        }
        String generatedTitle = "Article : " + prompt.substring(0, Math.min(prompt.length(), 60));
        String generatedContent = "Contenu généré automatiquement pour le sujet : « " + prompt + " ».\n\n"
                + "Type d'article : " + type + ".\n\n"
                + "(Intégrez ici votre service d'IA pour une génération réelle.)";
        if (articleTitreField != null) articleTitreField.setText(generatedTitle);
        if (articleContenuField != null) articleContenuField.setText(generatedContent);
        if (articleTypeCombo != null) articleTypeCombo.setValue(type);
    }

    @FXML
    private void onGenerateHashtags() {
        String titre = articleTitreField != null ? articleTitreField.getText() : "";
        if (titre.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Hashtags", "Veuillez d'abord renseigner un titre.");
            return;
        }
        String[] words = titre.split("\\s+");
        StringBuilder hashtags = new StringBuilder("\n\n");
        for (String w : words) {
            String clean = w.replaceAll("[^a-zA-ZÀ-ÿ0-9]", "");
            if (clean.length() > 2) hashtags.append("#").append(clean).append(" ");
        }
        if (articleContenuField != null) {
            articleContenuField.setText(articleContenuField.getText() + hashtags.toString().trim());
        }
    }

    @FXML
    private void onPublishArticle() {
        String titre = articleTitreField != null ? articleTitreField.getText().trim() : "";
        String contenu = articleContenuField != null ? articleContenuField.getText().trim() : "";
        String type = articleTypeCombo != null ? articleTypeCombo.getValue() : null;

        if (titre.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champs requis", "Indiquez un titre pour l'article.");
            return;
        }
        if (titre.length() < MIN_TITRE) {
            showAlert(Alert.AlertType.WARNING, "Titre", "Le titre doit contenir au moins " + MIN_TITRE + " caractères.");
            return;
        }
        if (titre.length() > MAX_TITRE) {
            showAlert(Alert.AlertType.WARNING, "Titre", "Le titre ne doit pas dépasser " + MAX_TITRE + " caractères.");
            return;
        }
        
        // Vérification de l'unicité du titre pour un nouvel article
        try {
            if (blogService.existsByTitre(titre, null)) {
                showAlert(Alert.AlertType.WARNING, "Titre existant", "Ce titre existe déjà. Veuillez choisir un titre unique.");
                return;
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de vérifier l'unicité du titre : " + e.getMessage());
            return;
        }
        
        if (type == null || type.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Champs requis", "Veuillez choisir un type d'article.");
            return;
        }
        if (contenu.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champs requis", "Indiquez le contenu de l'article.");
            return;
        }
        if (contenu.length() < MIN_CONTENU) {
            showAlert(Alert.AlertType.WARNING, "Contenu", "Le contenu doit contenir au moins " + MIN_CONTENU + " caractères.");
            return;
        }
        if (selectedImagePath == null || selectedImagePath.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Image obligatoire", "Veuillez choisir une image pour l'article.");
            return;
        }
        if (selectedImagePath.length() > MAX_IMAGE) {
            showAlert(Alert.AlertType.WARNING, "Image", "Le chemin de l'image ne doit pas dépasser " + MAX_IMAGE + " caractères.");
            return;
        }

        User user = AppState.getCurrentUser();
        if (user == null) {
            showAlert(Alert.AlertType.WARNING, "Connexion requise", "Vous devez être connecté pour publier un article.");
            return;
        }

        BlogArticle article = new BlogArticle();
        article.setTitre(titre);
        article.setType(type);
        article.setContenu(contenu);
        article.setImage(selectedImagePath);
        article.setPublished(cbPublished != null && cbPublished.isSelected());
        article.setUrgent(cbUrgent != null && cbUrgent.isSelected());
        article.setVisible(cbVisible != null && cbVisible.isSelected());
        article.setModuleId(currentModule != null ? currentModule.getId() : null);
        article.setUserId(user.getId());

        try {
            blogService.add(article);
            showAlert(Alert.AlertType.INFORMATION, "Succès", "L'article a été publié avec succès !");
            resetArticleForm();
            if (articleFormContainer != null) {
                articleFormContainer.setVisible(false);
                articleFormContainer.setManaged(false);
            }
            if (currentModule != null) {
                loadArticlesForModule(currentModule.getId());
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de publier l'article : " + e.getMessage());
        }
    }

    @FXML
    private void onCancelArticle() {
        resetArticleForm();
        
        // Masquer le formulaire lors de l'annulation
        if (articleFormContainer != null) {
            articleFormContainer.setVisible(false);
            articleFormContainer.setManaged(false);
        }
    }

    /* ═══════ Articles ═══════ */

    private void loadArticlesForModule(int moduleId) {
        if (mdArticlesContainer == null) return;
        mdArticlesContainer.getChildren().clear();

        try {
            currentArticles = blogService.findByModule(moduleId);
            renderArticles(currentArticles);
        } catch (Exception e) {
            currentArticles = List.of();
            Label err = new Label("Erreur de chargement des articles.");
            err.getStyleClass().add("blog-empty-label");
            mdArticlesContainer.getChildren().add(err);
        }
    }

    private void filterArticles(String query) {
        if (currentArticles == null || mdArticlesContainer == null) return;
        String q = query != null ? query.trim().toLowerCase() : "";
        if (q.isEmpty()) {
            renderArticles(currentArticles);
        } else {
            List<BlogArticle> filtered = currentArticles.stream()
                    .filter(a -> a.getTitre() != null && a.getTitre().toLowerCase().contains(q))
                    .collect(Collectors.toList());
            renderArticles(filtered);
        }
    }

    private void renderArticles(List<BlogArticle> articles) {
        mdArticlesContainer.getChildren().clear();
        if (articles.isEmpty()) {
            Label empty = new Label("Aucun article publié pour ce module.");
            empty.getStyleClass().add("blog-md-articles-empty");
            empty.setWrapText(true);
            empty.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            Label hint = new Label("Soyez le premier à écrire un article !");
            hint.getStyleClass().add("blog-md-articles-hint");
            hint.setWrapText(true);
            hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            mdArticlesContainer.getChildren().addAll(empty, hint);
        } else {
            for (BlogArticle article : articles) {
                mdArticlesContainer.getChildren().add(buildArticleItem(article));
            }
        }
    }

    private VBox buildArticleItem(BlogArticle article) {
        VBox card = new VBox(8);
        card.getStyleClass().add("blog-art-card");
        card.setPadding(new Insets(14, 16, 14, 16));

        /* ── Row 1: image + titre + actions ── */
        HBox row1 = new HBox(12);
        row1.setAlignment(Pos.CENTER_LEFT);

        ImageView thumb = new ImageView();
        thumb.setFitWidth(50);
        thumb.setFitHeight(50);
        thumb.setPreserveRatio(false);
        thumb.setSmooth(true);
        Image artImg = resolveModuleImage(article.getImage(), 50, 50);
        if (artImg != null) thumb.setImage(artImg);
        Rectangle thumbClip = new Rectangle(50, 50);
        thumbClip.setArcWidth(12);
        thumbClip.setArcHeight(12);
        thumb.setClip(thumbClip);

        Label titleLbl = new Label(article.getTitre() != null ? article.getTitre() : "Sans titre");
        titleLbl.getStyleClass().add("blog-art-card-title");
        titleLbl.setWrapText(true);
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        HBox actionIcons = new HBox(6);
        actionIcons.setAlignment(Pos.CENTER_RIGHT);
        Button btnDownload = new Button("⬇");
        btnDownload.getStyleClass().add("blog-art-icon-btn-green");
        Button btnView = new Button("👁");
        btnView.getStyleClass().add("blog-art-icon-btn-green");
        Button btnEdit = new Button("✏");
        btnEdit.getStyleClass().add("blog-art-icon-btn-green");
        Button btnDelete = new Button("🗑");
        btnDelete.getStyleClass().add("blog-art-icon-btn-red");

        User currentUser = AppState.getCurrentUser();
        boolean isOwner = currentUser != null && article.getUserId() != null
                && currentUser.getId() == article.getUserId();
        btnEdit.setVisible(isOwner);
        btnEdit.setManaged(isOwner);
        btnDelete.setVisible(isOwner);
        btnDelete.setManaged(isOwner);

        btnEdit.setOnAction(e -> showEditArticle(article));
        btnDelete.setOnAction(e -> onDeleteArticle(article));

        actionIcons.getChildren().addAll(btnDownload, btnView, btnEdit, btnDelete);
        row1.getChildren().addAll(thumb, titleLbl, actionIcons);

        /* ── Row 2: type badge + author + date ── */
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(capitalize(article.getType()));
        typeBadge.getStyleClass().add("blog-art-type-badge");
        if ("plainte".equalsIgnoreCase(article.getType())) {
            typeBadge.getStyleClass().add("blog-art-type-plainte");
        } else if ("recommandation".equalsIgnoreCase(article.getType())) {
            typeBadge.getStyleClass().add("blog-art-type-recommandation");
        } else if ("question".equalsIgnoreCase(article.getType())) {
            typeBadge.getStyleClass().add("blog-art-type-question");
        } else {
            typeBadge.getStyleClass().add("blog-art-type-experience");
        }

        String email = resolveUserEmail(article.getUserId());
        Label authorLbl = new Label("Par " + email);
        authorLbl.getStyleClass().add("blog-art-author");

        String dateStr = "";
        if (article.getDateCreation() != null) {
            dateStr = "Le " + article.getDateCreation().format(DATETIME_FMT);
        }
        Label dateLbl = new Label(dateStr);
        dateLbl.getStyleClass().add("blog-art-date");

        row2.getChildren().addAll(typeBadge, authorLbl, dateLbl);

        /* ── Row 3 : texte intégral du contenu (retour à la ligne dans la carte) ── */
        String fullContent = article.getContenu() != null ? article.getContenu() : "";
        Label contentLbl = new Label(fullContent);
        contentLbl.getStyleClass().add("blog-art-content");
        contentLbl.setWrapText(true);
        /* Largeur = carte moins padding horizontal (14+16 d’Insets + cohérent avec la mise en page) */
        contentLbl.maxWidthProperty().bind(card.widthProperty().subtract(32));

        /* ── Row 4: social icons ── */
        HBox row4 = new HBox(10);
        row4.setAlignment(Pos.CENTER_LEFT);
        Label commentIcon = new Label("💬");
        commentIcon.getStyleClass().add("blog-art-social");
        Label shareIcon = new Label("➜");
        shareIcon.getStyleClass().add("blog-art-social");
        row4.getChildren().addAll(commentIcon, shareIcon);

        card.getChildren().addAll(row1, row2, contentLbl, row4);
        return card;
    }

    private void onDeleteArticle(BlogArticle article) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Supprimer l'article « " + article.getTitre() + " » ?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                blogService.delete(article.getId());
                if (currentModule != null) loadArticlesForModule(currentModule.getId());
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de supprimer : " + e.getMessage());
            }
        }
    }

    private String resolveUserEmail(Integer userId) {
        if (userId == null) return "inconnu";
        if (userEmailCache.containsKey(userId)) return userEmailCache.get(userId);
        try {
            Optional<User> opt = userService.findById(userId);
            String email = opt.map(User::getEmail).orElse("inconnu");
            userEmailCache.put(userId, email);
            return email;
        } catch (Exception e) {
            return "inconnu";
        }
    }

    /* ═══════ VUE 4 : modifier un article ═══════ */

    private void showEditArticle(BlogArticle article) {
        editingArticle = article;
        switchView(editArticleView);

        if (editTypeCombo != null) editTypeCombo.setItems(FXCollections.observableArrayList(ARTICLE_TYPES));
        if (editAiTypeCombo != null) editAiTypeCombo.setItems(FXCollections.observableArrayList(ARTICLE_TYPES));

        if (editTitreField != null) editTitreField.setText(article.getTitre() != null ? article.getTitre() : "");
        if (editTypeCombo != null) editTypeCombo.setValue(article.getType());
        if (editContenuField != null) editContenuField.setText(article.getContenu() != null ? article.getContenu() : "");
        if (editCbPublished != null) editCbPublished.setSelected(article.isPublished());
        if (editCbUrgent != null) editCbUrgent.setSelected(article.isUrgent());
        if (editCbVisible != null) editCbVisible.setSelected(article.isVisible());
        if (editAiPromptField != null) editAiPromptField.clear();

        editSelectedImagePath = article.getImage();
        if (editImageLabel != null) {
            if (article.getImage() != null && !article.getImage().isBlank()) {
                String name = article.getImage();
                int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                if (sep >= 0) name = name.substring(sep + 1);
                editImageLabel.setText(name);
            } else {
                editImageLabel.setText("Aucun fichier choisi");
            }
        }
        if (editRbUploadFile != null) editRbUploadFile.setSelected(true);
    }

    @FXML
    private void onBackFromEdit() {
        if (currentModule != null) {
            showModuleDetail(currentModule);
        } else {
            switchView(categoriesView);
        }
    }

    @FXML
    private void onChooseEditImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                "Images (JPG, PNG, GIF, WebP)",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"
            )
        );
        Stage st = (Stage) blogContentHost.getScene().getWindow();
        File file = chooser.showOpenDialog(st);
        if (file != null) {
            String path = file.getAbsolutePath();
            if (path.length() > MAX_IMAGE) {
                showAlert(Alert.AlertType.WARNING, "Image",
                        "Le chemin est trop long (max " + MAX_IMAGE + " caractères).");
                return;
            }
            editSelectedImagePath = path;
            if (editImageLabel != null) editImageLabel.setText(file.getName());
            System.out.println("Image sélectionnée : " + file.getAbsolutePath());
        }
    }

    @FXML
    private void onEditGenerateAI() {
        String prompt = editAiPromptField != null ? editAiPromptField.getText() : "";
        String type = editAiTypeCombo != null ? editAiTypeCombo.getValue() : null;
        if (prompt.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "IA", "Veuillez saisir un prompt / sujet.");
            return;
        }
        if (type == null) {
            showAlert(Alert.AlertType.WARNING, "IA", "Veuillez sélectionner un type d'article.");
            return;
        }
        String generatedTitle = "Article : " + prompt.substring(0, Math.min(prompt.length(), 60));
        String generatedContent = "Contenu généré automatiquement pour le sujet : « " + prompt + " ».\n\n"
                + "Type d'article : " + type + ".\n\n"
                + "(Intégrez ici votre service d'IA pour une génération réelle.)";
        if (editTitreField != null) editTitreField.setText(generatedTitle);
        if (editContenuField != null) editContenuField.setText(generatedContent);
        if (editTypeCombo != null) editTypeCombo.setValue(type);
    }

    @FXML
    private void onEditGenerateHashtags() {
        String titre = editTitreField != null ? editTitreField.getText() : "";
        if (titre.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Hashtags", "Veuillez d'abord renseigner un titre.");
            return;
        }
        String[] words = titre.split("\\s+");
        StringBuilder hashtags = new StringBuilder("\n\n");
        for (String w : words) {
            String clean = w.replaceAll("[^a-zA-ZÀ-ÿ0-9]", "");
            if (clean.length() > 2) hashtags.append("#").append(clean).append(" ");
        }
        if (editContenuField != null) {
            editContenuField.setText(editContenuField.getText() + hashtags.toString().trim());
        }
    }

    @FXML
    private void onSaveEditArticle() {
        if (editingArticle == null) return;

        String titre = editTitreField != null ? editTitreField.getText().trim() : "";
        String contenu = editContenuField != null ? editContenuField.getText().trim() : "";
        String type = editTypeCombo != null ? editTypeCombo.getValue() : null;

        if (titre.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champs requis", "Indiquez un titre pour l'article.");
            return;
        }
        if (titre.length() < MIN_TITRE) {
            showAlert(Alert.AlertType.WARNING, "Titre", "Le titre doit contenir au moins " + MIN_TITRE + " caractères.");
            return;
        }
        if (titre.length() > MAX_TITRE) {
            showAlert(Alert.AlertType.WARNING, "Titre", "Le titre ne doit pas dépasser " + MAX_TITRE + " caractères.");
            return;
        }
        
        // Vérification de l'unicité du titre lors de l'édition (exclure l'article en cours)
        try {
            if (blogService.existsByTitre(titre, editingArticle.getId())) {
                showAlert(Alert.AlertType.WARNING, "Titre existant", "Ce titre existe déjà. Veuillez choisir un titre unique.");
                return;
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de vérifier l'unicité du titre : " + e.getMessage());
            return;
        }
        
        if (type == null || type.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Champs requis", "Veuillez choisir un type d'article.");
            return;
        }
        if (contenu.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champs requis", "Indiquez le contenu de l'article.");
            return;
        }
        if (contenu.length() < MIN_CONTENU) {
            showAlert(Alert.AlertType.WARNING, "Contenu", "Le contenu doit contenir au moins " + MIN_CONTENU + " caractères.");
            return;
        }
        if (editSelectedImagePath == null || editSelectedImagePath.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Image obligatoire", "Veuillez choisir une image pour l'article.");
            return;
        }
        if (editSelectedImagePath.length() > MAX_IMAGE) {
            showAlert(Alert.AlertType.WARNING, "Image", "Le chemin de l'image ne doit pas dépasser " + MAX_IMAGE + " caractères.");
            return;
        }

        editingArticle.setTitre(titre);
        editingArticle.setType(type);
        editingArticle.setContenu(contenu);
        editingArticle.setImage(editSelectedImagePath);
        editingArticle.setPublished(editCbPublished != null && editCbPublished.isSelected());
        editingArticle.setUrgent(editCbUrgent != null && editCbUrgent.isSelected());
        editingArticle.setVisible(editCbVisible != null && editCbVisible.isSelected());

        try {
            blogService.update(editingArticle);
            showAlert(Alert.AlertType.INFORMATION, "Succès", "L'article a été modifié avec succès !");
            if (currentModule != null) {
                showModuleDetail(currentModule);
            } else {
                switchView(categoriesView);
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de modifier l'article : " + e.getMessage());
        }
    }

    @FXML
    private void onBackToCategoryFromModule() {
        if (currentCategory != null) {
            showCategoryDetail(currentCategory);
        } else {
            switchView(categoriesView);
        }
    }

    /* ═══════ Navigation entre vues ═══════ */

    private void switchView(VBox target) {
        for (VBox v : new VBox[]{categoriesView, categoryDetailView, moduleDetailView, editArticleView}) {
            if (v != null) {
                boolean show = v == target;
                v.setVisible(show);
                v.setManaged(show);
            }
        }
        if (heroBand != null) {
            boolean showHero = target == categoriesView;
            heroBand.setVisible(showHero);
            heroBand.setManaged(showHero);
        }
    }

    /* ═══════ Utilitaires ═══════ */

    private Image resolveModuleImage(String path, double w, double h) {
        if (path == null || path.isBlank()) return null;
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                return new Image(path, w, h, false, true, true);
            }
            java.io.File f = new java.io.File(path);
            if (f.isFile()) {
                return new Image(f.toURI().toString(), w, h, false, true);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String formatNiveau(ModuleNiveau n) {
        if (n == null) return "Moyen";
        return switch (n) {
            case facile -> "Facile";
            case moyen -> "Moyen";
            case difficile -> "Difficile";
        };
    }

    private String niveauBadgeClass(ModuleNiveau n) {
        if (n == null) return "blog-mod-badge-moyen";
        return switch (n) {
            case facile -> "blog-mod-badge-facile";
            case moyen -> "blog-mod-badge-moyen";
            case difficile -> "blog-mod-badge-difficile";
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void onVoirModules() {
        if (shell == null) return;
        try { shell.loadPage("produits"); } catch (IOException ignored) {}
    }

    @FXML
    private void onNavigateLogin() {
        if (shell == null) return;
        try { shell.loadPage("login"); } catch (IOException ignored) {}
    }
}
