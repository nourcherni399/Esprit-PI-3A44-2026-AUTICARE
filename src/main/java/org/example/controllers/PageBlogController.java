package org.example.controllers;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.example.models.BlogArticle;
import org.example.models.AppLanguage;
import org.example.models.Commentaire;
import org.example.models.CommentaireReaction;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;
import org.example.models.User;
import org.example.models.ArticleAiSuggestion;
import org.example.services.ArticleAiGenerationService;
import org.example.services.BlogService;
import org.example.services.CommentaireReactionService;
import org.example.services.CommentaireService;
import org.example.services.LanguageToolService;
import org.example.services.LanguageToolService.SpellMatch;
import org.example.services.LibreTranslateService;
import org.example.services.ModuleService;
import org.example.services.ModuleQuizService;
import org.example.services.PexelsService;
import org.example.services.SightengineService;
import org.example.services.TTSService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.PexelsThumbGrid;

import java.awt.Desktop;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.util.Duration;

public class PageBlogController implements PublicShellAware {
    private boolean uiInitialized = false;

    private static final double CAT_CARD_W = 330;
    private static final double CAT_CARD_H = 200;
    private static final double MOD_CARD_W = 300;
    /** Décodage image détail module : bandeau 300×150 px. */
    private static final double MODULE_DETAIL_IMG_DECODE_W = 360;
    private static final double MODULE_DETAIL_IMG_H = 190;

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
    private final ArticleAiGenerationService articleAiGenerationService = new ArticleAiGenerationService();
    private final PexelsService pexelsService = new PexelsService();
    private final ModuleQuizService moduleQuizService = new ModuleQuizService();
    private final LibreTranslateService libreTranslateService = new LibreTranslateService();
    private final LanguageToolService languageToolService = new LanguageToolService();
    private final org.example.services.RessourceService ressourceService = new org.example.services.RessourceService();
    private final TTSService ttsService = new TTSService();
    private final CommentaireService commentaireService = new CommentaireService();
    private final CommentaireReactionService reactionService = new CommentaireReactionService();
    private final SightengineService sightengineService = new SightengineService();
    private final org.example.services.WikipediaService wikipediaService = new org.example.services.WikipediaService();
    private Button currentArticleTtsBtn = null;
    private final Map<Node, String> baseLabeledText = new ConcurrentHashMap<>();
    private final Map<TextInputControl, String> basePromptText = new ConcurrentHashMap<>();
    private final ExecutorService translationExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread t = new Thread(runnable, "blog-i18n-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger translationSessionSeq = new AtomicInteger(0);
    private final AtomicInteger pendingTranslations = new AtomicInteger(0);
    private final AtomicInteger succeededTranslations = new AtomicInteger(0);
    private final AtomicInteger failedTranslations = new AtomicInteger(0);
    private final AtomicReference<String> firstFailureProvider = new AtomicReference<>("");
    private final AtomicReference<String> firstFailureError = new AtomicReference<>("");
    private volatile int activeTranslationSession = 0;
    private volatile AppLanguage activeLanguage = AppState.getCurrentLanguage();
    private ModuleCategorie currentCategory;
    private ModuleContent currentModule;
    private String selectedImagePath;
    private List<BlogArticle> currentArticles;
    private boolean isApplyingLanguage = false;
    private final Map<Integer, String> userEmailCache = new HashMap<>();
    private final Map<String, String> dictionaryDefinitionCache = new ConcurrentHashMap<>();
    private final Map<TextInputControl, PauseTransition> dictionaryDebounceByControl = new ConcurrentHashMap<>();
    private final Map<TextInputControl, double[]> dictionaryLastAnchor = new ConcurrentHashMap<>();
    private final Popup dictionaryPopup = new Popup();
    private final VBox dictionaryPopupBox = new VBox(4);
    private final Label dictionaryPopupWord = new Label();
    private final Label dictionaryPopupDefinition = new Label();

    /* ── FXML: shared ── */
    @FXML private StackPane heroBand;
    @FXML private StackPane blogContentHost;
    @FXML private Label translationStatusLabel;
    @FXML private Label heroTitleLabel;
    @FXML private Label heroSubtitleLabel;

    /* ── FXML: vue 1 — catégories ── */
    @FXML private VBox categoriesView;
    @FXML private FlowPane categoryCardsPane;
    @FXML private Label categoriesHeadingLabel;

    /* ── FXML: vue 2 — liste modules d'une catégorie ── */
    @FXML private VBox categoryDetailView;
    @FXML private FlowPane moduleCardsPane;
    @FXML private ImageView detailHeaderImg;
    @FXML private Label detailCatTitle;
    @FXML private Label detailCatDesc;
    @FXML private Button backToCategoriesBtn;

    /* ── FXML: vue 3 — détail d'un module ── */
    @FXML private VBox moduleDetailView;
    @FXML private VBox moduleQuizView;
    @FXML private Label mdTitle;
    @FXML private Label mdNiveauBadge;
    @FXML private Label mdDate;
    @FXML private Button mdTtsBtn;
    @FXML private Button mdSummaryBtn;
    @FXML private TextArea mdDescription;
    @FXML private StackPane mdImageWrap;
    @FXML private ImageView mdImage;
    @FXML private TextArea mdContenu;
    @FXML private Label mdRessources;
    @FXML private VBox mdRessourcesContainer;
    @FXML private VBox mdArticlesContainer;
    @FXML private Label mdArticlesPlaceholder;
    @FXML private TextField articleSearchField;
    @FXML private HBox loginPromptBox;
    @FXML private Hyperlink loginLink;
    @FXML private HBox socialIconsBar;
    @FXML private Button mdPassQuizBtn;
    @FXML private Label quizModuleTitleLabel;
    @FXML private VBox quizQuestionsContainer;
    @FXML private Label quizStatusLabel;
    @FXML private Button quizSubmitBtn;
    @FXML private Hyperlink wikipediaLink;

    /* ── FXML: formulaire ajout article ── */
    @FXML private VBox articleFormContainer;
    @FXML private TextArea aiPromptField;
    @FXML private ComboBox<String> aiTypeCombo;
    @FXML private TextField articleTitreField;
    @FXML private ComboBox<String> articleTypeCombo;
    @FXML private TextArea articleContenuField;
    @FXML private RadioButton rbUploadFile;
    @FXML private RadioButton rbPexels;
    @FXML private VBox articleImageUploadPanel;
    @FXML private VBox articlePexelsPanel;
    @FXML private TextField articlePexelsSearchField;
    @FXML private FlowPane articlePexelsFlow;
    @FXML private Label articlePexelsHint;
    @FXML private Label articleImageLabel;
    @FXML private ImageView articleImagePreview;
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
    @FXML private VBox editArticleImageUploadPanel;
    @FXML private VBox editArticlePexelsPanel;
    @FXML private TextField editArticlePexelsSearchField;
    @FXML private FlowPane editArticlePexelsFlow;
    @FXML private Label editArticlePexelsHint;
    @FXML private Label editImageLabel;
    @FXML private ImageView editArticleImagePreview;
    @FXML private CheckBox editCbPublished;
    @FXML private CheckBox editCbUrgent;
    @FXML private CheckBox editCbVisible;
    
    /* ── FXML: vue bibliothèque ── */
    @FXML private VBox libraryView;
    @FXML private FlowPane libraryContentPane;
    @FXML private Label librarySectionTitle;
    @FXML private ImageView libraryIconImg;
    @FXML private ImageView libraryGirlImg;
    @FXML private Button libFilterTous, libFilterSaved, libFilterFavs, libFilterLib;
    
    private BlogArticle editingArticle;
    private String editSelectedImagePath;
    private final Map<Integer, Boolean> moduleAccessById = new HashMap<>();
    private final Map<Integer, String> moduleLockReasonById = new HashMap<>();
    private ModuleQuizService.QuizData activeQuizData;
    private int activeQuizUserId = -1;
    private final List<ToggleGroup> activeQuizGroups = new ArrayList<>();

    @FXML
    private void initialize() {
        if (uiInitialized) {
            return;
        }
        uiInitialized = true;
        try {
            loadBlogFragments();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger les vues blog.", e);
        }
        Platform.runLater(() -> {
            ensureBlogCssLoaded();
            buildCategoryCards();
            switchView(categoriesView);
            applyLanguageNow();
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
        setupArticleImageModePanels();
        setupEditArticleImageModePanels();
        ensureEditArticleTypeChoices();
        initDictionaryPopup();
        setupDictionarySupport(mdDescription);
        setupDictionarySupport(mdContenu);
        enableAutoGrowTextArea(mdDescription, 2);
        enableAutoGrowTextArea(mdContenu, 10);
        if (blogContentHost != null) {
            blogContentHost.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, evt -> {
                Object target = evt.getTarget();
                if (!(target instanceof TextInputControl)) {
                    hideDictionaryPopup();
                }
            });
        }
    }

    private void setupArticleImageModePanels() {
        if (rbUploadFile == null || rbUploadFile.getToggleGroup() == null) {
            return;
        }
        rbUploadFile.getToggleGroup().selectedToggleProperty().addListener((obs, o, n) -> updateArticleImagePanels());
        updateArticleImagePanels();
    }

    private void updateArticleImagePanels() {
        if (articleImageUploadPanel == null || articlePexelsPanel == null) {
            return;
        }
        boolean upload = rbUploadFile == null || rbUploadFile.isSelected();
        articleImageUploadPanel.setVisible(upload);
        articleImageUploadPanel.setManaged(upload);
        articlePexelsPanel.setVisible(!upload);
        articlePexelsPanel.setManaged(!upload);
    }

    private void setupEditArticleImageModePanels() {
        if (editRbUploadFile == null || editRbUploadFile.getToggleGroup() == null) {
            return;
        }
        editRbUploadFile.getToggleGroup().selectedToggleProperty().addListener((obs, o, n) -> updateEditArticleImagePanels());
        updateEditArticleImagePanels();
    }

    private void updateEditArticleImagePanels() {
        if (editArticleImageUploadPanel == null || editArticlePexelsPanel == null) {
            return;
        }
        boolean upload = editRbUploadFile == null || editRbUploadFile.isSelected();
        editArticleImageUploadPanel.setVisible(upload);
        editArticleImageUploadPanel.setManaged(upload);
        editArticlePexelsPanel.setVisible(!upload);
        editArticlePexelsPanel.setManaged(!upload);
    }

    @FXML
    private void onArticlePexelsSearch() {
        String q = articlePexelsSearchField != null ? articlePexelsSearchField.getText().trim() : "";
        if (q.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Pexels", "Saisissez des mots-clés (ex. autisme, famille…).");
            return;
        }
        if (articlePexelsFlow != null) {
            articlePexelsFlow.getChildren().clear();
        }
        Thread worker = new Thread(() -> {
            try {
                List<PexelsService.PexelsPhoto> list = pexelsService.search(q, 6);
                Platform.runLater(() -> {
                    if (articlePexelsFlow != null) {
                        PexelsThumbGrid.fill(articlePexelsFlow, list, this::runArticlePexelsPick);
                    }
                    if (articlePexelsHint != null) {
                        articlePexelsHint.setText(list.isEmpty()
                                ? "Aucun résultat. Essayez d'autres mots-clés."
                                : "Cliquez sur une image pour la télécharger et l'utiliser.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Pexels",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }, "blog-pexels-search-article");
        worker.setDaemon(true);
        worker.start();
    }

    private void runArticlePexelsPick(PexelsService.PexelsPhoto photo) {
        if (articlePexelsHint != null) {
            articlePexelsHint.setText("Téléchargement de l'image sélectionnée...");
        }
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
                        Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Image",
                                "Chemin/URL trop long pour la base (" + MAX_IMAGE + " caractères max)."));
                        return;
                    }
                    path = fallback;
                    usedFallbackUrl = true;
                }
                final String selectedPath = path;
                final boolean fallbackUsed = usedFallbackUrl;
                Platform.runLater(() -> {
                    selectedImagePath = selectedPath;
                    if (articleImageLabel != null) {
                        String name = selectedPath;
                        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                        if (sep >= 0) {
                            name = name.substring(sep + 1);
                        }
                        articleImageLabel.setText("Pexels — " + name);
                    }
                    if (articlePexelsHint != null) {
                        articlePexelsHint.setText(fallbackUsed
                                ? "Image sélectionnée (URL Pexels): "
                                    + (photo.photographer() == null ? "Pexels" : photo.photographer())
                                    + ". Vous pouvez publier l'article."
                                : "Image sélectionnée: " + (photo.photographer() == null ? "Pexels" : photo.photographer()) + ". Vous pouvez publier l'article.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Pexels",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }, "blog-pexels-download-article");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void onEditArticlePexelsSearch() {
        String q = editArticlePexelsSearchField != null ? editArticlePexelsSearchField.getText().trim() : "";
        if (q.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Pexels", "Saisissez des mots-clés (ex. autisme, famille…).");
            return;
        }
        if (editArticlePexelsFlow != null) {
            editArticlePexelsFlow.getChildren().clear();
        }
        Thread worker = new Thread(() -> {
            try {
                List<PexelsService.PexelsPhoto> list = pexelsService.search(q, 6);
                Platform.runLater(() -> {
                    if (editArticlePexelsFlow != null) {
                        PexelsThumbGrid.fill(editArticlePexelsFlow, list, this::runEditArticlePexelsPick);
                    }
                    if (editArticlePexelsHint != null) {
                        editArticlePexelsHint.setText(list.isEmpty()
                                ? "Aucun résultat. Essayez d'autres mots-clés."
                                : "Cliquez sur une image pour la télécharger et l'utiliser.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Pexels",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }, "blog-pexels-search-edit");
        worker.setDaemon(true);
        worker.start();
    }

    private void runEditArticlePexelsPick(PexelsService.PexelsPhoto photo) {
        if (editArticlePexelsHint != null) {
            editArticlePexelsHint.setText("Téléchargement de l'image sélectionnée...");
        }
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
                        Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Image",
                                "Chemin/URL trop long pour la base (" + MAX_IMAGE + " caractères max)."));
                        return;
                    }
                    path = fallback;
                    usedFallbackUrl = true;
                }
                final String selectedPath = path;
                final boolean fallbackUsed = usedFallbackUrl;
                Platform.runLater(() -> {
                    editSelectedImagePath = selectedPath;
                    if (editImageLabel != null) {
                        String name = selectedPath;
                        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                        if (sep >= 0) {
                            name = name.substring(sep + 1);
                        }
                        editImageLabel.setText("Pexels — " + name);
                    }
                    if (editArticlePexelsHint != null) {
                        editArticlePexelsHint.setText(fallbackUsed
                                ? "Image sélectionnée (URL Pexels): "
                                    + (photo.photographer() == null ? "Pexels" : photo.photographer())
                                    + ". Vous pouvez enregistrer l'article."
                                : "Image sélectionnée: " + (photo.photographer() == null ? "Pexels" : photo.photographer()) + ". Vous pouvez enregistrer l'article.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Pexels",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }, "blog-pexels-download-edit");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void onOpenPromptDictation() {
        startWindowsDictation(aiPromptField);
    }

    @FXML
    private void onOpenEditPromptDictation() {
        startWindowsDictation(editAiPromptField);
    }

    @FXML
    private void onDictateArticlePrompt() {
        startWindowsDictation(aiPromptField);
    }

    @FXML
    private void onDictateArticleTitle() {
        startWindowsDictation(articleTitreField);
    }

    @FXML
    private void onDictateArticleContent() {
        startWindowsDictation(articleContenuField);
    }

    @FXML
    private void onDictateArticlePexelsSearch() {
        startWindowsDictation(articlePexelsSearchField);
    }

    @FXML
    private void onDictateEditArticlePrompt() {
        startWindowsDictation(editAiPromptField);
    }

    @FXML
    private void onDictateEditArticleTitle() {
        startWindowsDictation(editTitreField);
    }

    @FXML
    private void onDictateEditArticleContent() {
        startWindowsDictation(editContenuField);
    }

    @FXML
    private void onDictateEditArticlePexelsSearch() {
        startWindowsDictation(editArticlePexelsSearchField);
    }

    /**
     * Approche desktop fiable: focus du champ puis raccourci Windows Win+H.
     */
    private void startWindowsDictation(TextInputControl target) {
        if (target == null) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            showAlert(Alert.AlertType.INFORMATION, "Dictée",
                    "La dictée rapide est prévue pour Windows. Placez le curseur dans le champ puis utilisez le raccourci système de dictée.");
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
                Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Dictée",
                        "Impossible d'ouvrir la dictée Windows automatiquement. Utilisez Win + H après avoir cliqué dans le champ."));
            }
        }, "windows-dictation-shortcut");
        worker.setDaemon(true);
        worker.start();
    }

    /* ── Text-to-Speech (module) ── */

    @FXML
    private void onToggleModuleTts() {
        if (ttsService.isSpeaking()) {
            ttsService.stop();
            updateTtsBtnState(false);
            resetArticleTtsBtn();
        } else {
            resetArticleTtsBtn();
            updateTtsBtnState(true);
            String texte = buildModuleTtsText();
            speakTranslated(texte, () -> updateTtsBtnState(false), () -> updateTtsBtnState(false));
        }
    }

    private String buildModuleTtsText() {
        if (currentModule == null) return "";
        StringBuilder sb = new StringBuilder();
        if (currentModule.getTitre() != null) sb.append(currentModule.getTitre()).append(". ");
        if (currentModule.getDescription() != null) sb.append(currentModule.getDescription());
        return sb.toString();
    }

    private void updateTtsBtnState(boolean playing) {
        if (mdTtsBtn == null) return;
        if (playing) {
            mdTtsBtn.setText("■");
            mdTtsBtn.getStyleClass().removeAll("blog-md-tts-btn");
            if (!mdTtsBtn.getStyleClass().contains("blog-md-tts-btn-active")) {
                mdTtsBtn.getStyleClass().add("blog-md-tts-btn-active");
            }
        } else {
            mdTtsBtn.setText("▶");
            mdTtsBtn.getStyleClass().removeAll("blog-md-tts-btn-active");
            if (!mdTtsBtn.getStyleClass().contains("blog-md-tts-btn")) {
                mdTtsBtn.getStyleClass().add("blog-md-tts-btn");
            }
        }
    }

    /* ── Text-to-Speech (articles) ── */

    private void onToggleArticleTts(Button btn, BlogArticle article) {
        boolean wasThisOne = (currentArticleTtsBtn == btn) && ttsService.isSpeaking();

        // Arrêter toute lecture en cours (module ou article)
        ttsService.stop();
        updateTtsBtnState(false);
        resetArticleTtsBtn();

        if (wasThisOne) {
            // Deuxième clic sur le même bouton → simple arrêt
            return;
        }

        // Lancer la lecture de cet article
        currentArticleTtsBtn = btn;
        updateArticleTtsBtnState(btn, true);
        String texte = buildArticleTtsText(article);
        speakTranslated(texte,
            () -> { updateArticleTtsBtnState(btn, false); if (currentArticleTtsBtn == btn) currentArticleTtsBtn = null; },
            () -> { updateArticleTtsBtnState(btn, false); if (currentArticleTtsBtn == btn) currentArticleTtsBtn = null; }
        );
    }

    private String buildArticleTtsText(BlogArticle article) {
        StringBuilder sb = new StringBuilder();
        if (article.getTitre() != null) sb.append(article.getTitre()).append(". ");
        if (article.getContenu() != null) sb.append(article.getContenu());
        return sb.toString();
    }

    /**
     * Traduit le texte dans la langue active (si différente du français),
     * puis le lit avec la voix Windows correspondante.
     *
     * @param text        Texte source (en français)
     * @param onFinished  Callback appelé quand la lecture se termine normalement
     * @param onError     Callback appelé en cas d'erreur de traduction
     */
    private void speakTranslated(String text, Runnable onFinished, Runnable onError) {
        if (text == null || text.isBlank()) {
            if (onFinished != null) Platform.runLater(onFinished);
            return;
        }
        AppLanguage lang = activeLanguage != null ? activeLanguage : AppLanguage.FR;
        String langCode = lang.code();

        if ("fr".equalsIgnoreCase(langCode)) {
            // Pas de traduction nécessaire
            ttsService.speak(text, langCode);
            startTtsWatcher(onFinished);
            return;
        }

        // Traduire d'abord, puis lire
        translationExecutor.submit(() -> {
            try {
                String translated = libreTranslateService.translateFromFrench(text, lang);
                String finalText = (translated != null && !translated.isBlank()) ? translated : text;
                Platform.runLater(() -> {
                    ttsService.speak(finalText, langCode);
                    startTtsWatcher(onFinished);
                });
            } catch (Exception ex) {
                // En cas d'erreur de traduction, lire quand même en français
                Platform.runLater(() -> {
                    ttsService.speak(text, null);
                    startTtsWatcher(onFinished);
                });
            }
        });
    }

    /** Démarre un thread qui attend la fin du processus TTS puis appelle le callback. */
    private void startTtsWatcher(Runnable onFinished) {
        if (onFinished == null) return;
        Thread watcher = new Thread(() -> {
            try {
                Process p = ttsService.getCurrentProcess();
                if (p != null) p.waitFor();
            } catch (Exception ignored) {}
            Platform.runLater(onFinished);
        }, "tts-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void updateArticleTtsBtnState(Button btn, boolean playing) {
        if (btn == null) return;
        if (playing) {
            btn.setText("■");
            btn.getStyleClass().removeAll("soft-icon-btn--green", "blog-art-icon-btn-tts");
            if (!btn.getStyleClass().contains("soft-icon-btn--playing")) {
                btn.getStyleClass().add("soft-icon-btn--playing");
            }
        } else {
            btn.setText("▶");
            btn.getStyleClass().removeAll("soft-icon-btn--playing", "blog-art-icon-btn-tts-active");
            if (!btn.getStyleClass().contains("soft-icon-btn--green")) {
                btn.getStyleClass().add("soft-icon-btn--green");
            }
        }
    }

    private void resetArticleTtsBtn() {
        if (currentArticleTtsBtn != null) {
            updateArticleTtsBtnState(currentArticleTtsBtn, false);
            currentArticleTtsBtn = null;
        }
    }

    /* ── Résumé IA du module (bouton ✦) ── */

    @FXML
    private void onOpenModuleSummary() {
        if (currentModule == null) return;
        if (mdSummaryBtn != null) {
            mdSummaryBtn.setDisable(true);
            mdSummaryBtn.setText("…");
        }
        AppLanguage lang = activeLanguage != null ? activeLanguage : AppLanguage.FR;

        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "groq-summary-worker");
            t.setDaemon(true);
            return t;
        });
        exec.submit(() -> {
            try {
                // 1) Générer le résumé (toujours en français depuis l'IA)
                String summaryFr = articleAiGenerationService.generateModuleSummary(
                        currentModule.getTitre(),
                        currentModule.getDescription(),
                        currentModule.getContenu()
                );

                // 2) Traduire dans la langue active si nécessaire
                String summaryDisplay = summaryFr;
                if (!"fr".equalsIgnoreCase(lang.code())) {
                    try {
                        String translated = libreTranslateService.translateFromFrench(summaryFr, lang);
                        if (translated != null && !translated.isBlank()) {
                            summaryDisplay = translated;
                        }
                    } catch (Exception ignored) {
                        // fallback : résumé en français
                    }
                }

                final String finalSummary = summaryDisplay;
                Platform.runLater(() -> {
                    if (mdSummaryBtn != null) {
                        mdSummaryBtn.setDisable(false);
                        mdSummaryBtn.setText("✦");
                    }
                    showSummaryPopup(finalSummary, lang);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (mdSummaryBtn != null) {
                        mdSummaryBtn.setDisable(false);
                        mdSummaryBtn.setText("✦");
                    }
                    showAlert(Alert.AlertType.ERROR, "Résumé IA",
                            "Impossible de générer le résumé : " + e.getMessage());
                });
            }
        });
        exec.shutdown();
    }

    private void showSummaryPopup(String summary, AppLanguage lang) {
        ttsService.stop();
        updateTtsBtnState(false);
        resetArticleTtsBtn();

        String langCode = lang != null ? lang.code() : "fr";

        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(moduleDetailView.getScene().getWindow());
        popup.setTitle("Résumé IA");
        popup.setResizable(false);

        // Barre de titre
        HBox header = new HBox();
        header.getStyleClass().add("blog-summary-header");
        Label titleLbl = new Label("Résumé IA");
        titleLbl.getStyleClass().add("blog-summary-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("Fermer");
        closeBtn.getStyleClass().add("blog-summary-close-btn");
        closeBtn.setOnAction(e -> {
            ttsService.stop();
            popup.close();
        });
        header.getChildren().addAll(titleLbl, spacer, closeBtn);

        // Bouton play TTS — le summary est DÉJÀ traduit, on lit directement dans la bonne langue
        Button playBtn = new Button("▶");
        playBtn.getStyleClass().add("blog-summary-play-btn");
        Label playLbl = new Label("Écouter le résumé");
        playLbl.getStyleClass().add("blog-summary-play-label");
        HBox playRow = new HBox(10, playBtn, playLbl);
        playRow.setAlignment(Pos.CENTER_LEFT);
        playRow.getStyleClass().add("blog-summary-play-row");

        playBtn.setOnAction(e -> {
            if (ttsService.isSpeaking()) {
                ttsService.stop();
                playBtn.setText("▶");
                playBtn.getStyleClass().removeAll("blog-art-icon-btn-tts-active");
                if (!playBtn.getStyleClass().contains("blog-summary-play-btn")) {
                    playBtn.getStyleClass().add("blog-summary-play-btn");
                }
            } else {
                playBtn.setText("■");
                playBtn.getStyleClass().removeAll("blog-summary-play-btn");
                if (!playBtn.getStyleClass().contains("blog-art-icon-btn-tts-active")) {
                    playBtn.getStyleClass().add("blog-art-icon-btn-tts-active");
                }
                // Le texte est déjà traduit → lire directement dans la langue cible
                ttsService.speak(summary, langCode);
                startTtsWatcher(() -> {
                    playBtn.setText("▶");
                    playBtn.getStyleClass().removeAll("blog-art-icon-btn-tts-active");
                    if (!playBtn.getStyleClass().contains("blog-summary-play-btn")) {
                        playBtn.getStyleClass().add("blog-summary-play-btn");
                    }
                });
            }
        });

        // Texte du résumé (déjà traduit)
        Label summaryLbl = new Label(summary);
        summaryLbl.getStyleClass().add("blog-summary-text");
        summaryLbl.setWrapText(true);
        summaryLbl.setMaxWidth(560);

        // Layout global
        VBox root = new VBox(16, header, playRow, summaryLbl);
        root.getStyleClass().add("blog-summary-popup");
        root.setPadding(new Insets(20));
        root.setPrefWidth(620);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/styles/page-blog.css").toExternalForm()
        );
        popup.setScene(scene);
        popup.setOnHiding(e -> ttsService.stop());
        popup.showAndWait();
    }

    private void loadBlogFragments() throws IOException {
        if (heroBand != null) {
            heroBand.getChildren().setAll(loadFragment("/front/blog/hero-band.fxml"));
        }
        categoriesView = (VBox) loadFragment("/front/blog/categories-view.fxml");
        categoryDetailView = (VBox) loadFragment("/front/blog/category-detail-view.fxml");
        moduleDetailView = (VBox) loadFragment("/front/blog/module-detail-view.fxml");
        moduleQuizView = (VBox) loadFragment("/front/blog/module-quiz-view.fxml");
        editArticleView = (VBox) loadFragment("/front/blog/edit-article-view.fxml");
        libraryView = (VBox) loadFragment("/front/blog/library-view.fxml");

        if (blogContentHost != null) {
            blogContentHost.getChildren().setAll(categoriesView, categoryDetailView, moduleDetailView, moduleQuizView, editArticleView, libraryView);
        }
        applyLanguageNow();
    }

    private Parent loadFragment(String resourcePath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resourcePath));
        loader.setController(this);
        return loader.load();
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
        moduleAccessById.clear();
        moduleLockReasonById.clear();
        try {
            List<ModuleContent> modules = moduleService.findAll().stream()
                    .filter(m -> m.getCategorieEnum() == cat)
                    .collect(Collectors.toList());
            computeModuleAccessForCategory(cat);
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
            applyLanguageNow();
        } catch (Exception e) {
            Label err = new Label("Erreur de chargement des modules.");
            err.getStyleClass().add("blog-empty-label");
            moduleCardsPane.getChildren().add(err);
            applyLanguageNow();
        }
    }

    private VBox buildModuleCard(ModuleContent mod) {
        VBox card = new VBox(0);
        card.getStyleClass().add("blog-mod-card");
        card.setPrefWidth(MOD_CARD_W);
        card.setMaxWidth(MOD_CARD_W);
        boolean locked = isModuleLocked(mod);
        String lockReason = moduleLockReasonById.getOrDefault(mod.getId(), "Terminez le niveau précédent");

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
        if (locked) {
            VBox overlay = new VBox(4);
            overlay.getStyleClass().add("blog-mod-locked-overlay");
            Label lockTitle = new Label("🔒  " + lockReason);
            lockTitle.getStyleClass().add("blog-mod-locked-title");
            overlay.getChildren().add(lockTitle);
            StackPane.setAlignment(overlay, Pos.CENTER);
            StackPane.setMargin(overlay, new Insets(0, 12, 0, 12));
            imgWrap.getChildren().add(overlay);
        }

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
        Button readBtn = new Button(locked ? "Verrouillé" : "Lire  >");
        readBtn.getStyleClass().add("blog-mod-read-btn");
        if (locked) {
            readBtn.setDisable(true);
            readBtn.getStyleClass().add("blog-mod-read-btn-locked");
        } else {
            readBtn.setOnAction(e -> showModuleDetail(mod));
        }

        Label bookmark = new Label("\uD83D\uDD16");
        bookmark.getStyleClass().add("blog-mod-bookmark");
        bookmark.setOnMouseClicked(e -> showLibrary());
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
        if (isModuleLocked(mod)) {
            showAlert(Alert.AlertType.WARNING, "Module verrouillé",
                    moduleLockReasonById.getOrDefault(mod.getId(), "Terminez le niveau précédent pour accéder à ce module."));
            return;
        }
        currentModule = mod;
        ttsService.stop();
        updateTtsBtnState(false);
        resetArticleTtsBtn();
        switchView(moduleDetailView);

        mdTitle.setText(mod.getTitre() != null ? mod.getTitre() : "");
        baseLabeledText.put(mdTitle, mdTitle.getText());

        mdNiveauBadge.setText(formatNiveau(mod.getNiveau()));
        mdNiveauBadge.getStyleClass().removeAll("blog-mod-badge-facile", "blog-mod-badge-moyen", "blog-mod-badge-difficile");
        mdNiveauBadge.getStyleClass().add(niveauBadgeClass(mod.getNiveau()));

        if (mod.getDateCreation() != null) {
            mdDate.setText("Publié le " + mod.getDateCreation().format(DATE_FMT));
        } else {
            mdDate.setText("");
        }

        mdDescription.setText(mod.getDescription() != null ? mod.getDescription() : "");
        baseLabeledText.put(mdDescription, mdDescription.getText());
        updateAutoGrowTextAreaHeight(mdDescription, 2);
        mdDescription.positionCaret(0);
        mdDescription.setScrollTop(0);
        translateDynamicTextInput(mdDescription, mod.getDescription());

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
        baseLabeledText.put(mdContenu, mdContenu.getText());
        updateAutoGrowTextAreaHeight(mdContenu, 10);
        mdContenu.positionCaret(0);
        mdContenu.setScrollTop(0);
        translateDynamicTextInput(mdContenu, mod.getContenu());
        
        // Charger et afficher les ressources
        loadRessourcesForModule(mod.getId());

        if (articleSearchField != null) articleSearchField.clear();
        loadArticlesForModule(mod.getId());
        setupArticleForm();
        refreshQuizButtonState(mod);
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
            applyLanguageNow();
        } catch (Exception e) {
            Label errorLabel = new Label("Erreur de chargement des ressources.");
            errorLabel.getStyleClass().add("blog-md-ressources-text");
            mdRessourcesContainer.getChildren().add(errorLabel);
            applyLanguageNow();
        }
    }

    private VBox buildRessourceCard(org.example.models.Ressource res) {
        VBox card = new VBox(12);
        card.getStyleClass().add("blog-ressource-card");
        card.setPadding(new Insets(14));
        card.setMaxWidth(360); // Ajusté pour correspondre à la largeur du contenu du module

        // Header : Titre + Bouton Ouvrir + Badge Type
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(res.getTitre() != null ? res.getTitre() : "Ressource sans titre");
        titleLabel.getStyleClass().add("blog-ressource-name");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button openBtn = new Button("↓");
        if ("video".equalsIgnoreCase(res.getTypeRessource())) openBtn.setText("▶");
        openBtn.getStyleClass().add("blog-ressource-download-btn");
        
        Label typeBadge = new Label(res.getTypeRessource() != null ? res.getTypeRessource().toUpperCase() : "DOC");
        typeBadge.getStyleClass().addAll("blog-ressource-type-badge", "blog-ressource-badge-" + (res.getTypeRessource() != null ? res.getTypeRessource().toLowerCase() : "doc"));

        header.getChildren().addAll(titleLabel, openBtn, typeBadge);

        // Preview Section
        StackPane previewArea = new StackPane();
        previewArea.getStyleClass().add("blog-ressource-preview-area");
        previewArea.setPrefHeight(170);
        // On utilise un rectangle pour arrondir les coins de l'image (largeur 332 = 360 - 28 de padding)
        Rectangle clip = new Rectangle(332, 170);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        previewArea.setClip(clip);

        ImageView previewImg = new ImageView();
        previewImg.setFitWidth(332);
        previewImg.setFitHeight(170);
        previewImg.setPreserveRatio(false);
        previewImg.setSmooth(true);

        String contenu = res.getContenu() != null ? res.getContenu().trim() : "";
        boolean isYoutube = contenu.contains("youtube.com") || contenu.contains("youtu.be");

        if (isYoutube) {
            String videoId = extractYoutubeId(contenu);
            if (videoId != null) {
                previewImg.setImage(new Image("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg", true));
                // Overlay bouton play YouTube (plus élégant et centré)
                Label playOverlay = new Label("▶");
                playOverlay.setStyle("-fx-font-size: 32; -fx-text-fill: white; -fx-background-color: rgba(255,0,0,0.85); -fx-padding: 8 16; -fx-background-radius: 12;");
                previewArea.getChildren().add(playOverlay);
            }
        } else if ("video".equalsIgnoreCase(res.getTypeRessource())) {
            previewImg.setImage(new Image("https://placehold.co/400x200?text=VIDEO", true));
        } else {
            previewImg.setImage(new Image("https://placehold.co/400x200?text=" + (res.getTypeRessource() != null ? res.getTypeRessource().toUpperCase() : "DOC"), true));
        }

        previewArea.getChildren().add(0, previewImg);

        // Action d'ouverture (Restreinte aux utilisateurs connectés)
        Runnable openAction = () -> {
            if (AppState.getCurrentUser() == null) {
                showAlert(Alert.AlertType.WARNING, "Connexion requise", "Veuillez vous connecter pour accéder aux ressources du module.");
                return;
            }
            if (contenu.isEmpty()) return;
            try {
                if (contenu.startsWith("http")) {
                    java.awt.Desktop.getDesktop().browse(new URI(contenu));
                } else {
                    java.io.File file = new java.io.File(contenu);
                    if (file.exists()) {
                        java.awt.Desktop.getDesktop().open(file);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erreur", "Fichier introuvable : " + contenu);
                    }
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'ouvrir la ressource : " + ex.getMessage());
            }
        };

        openBtn.setOnAction(e -> openAction.run());
        previewArea.setOnMouseClicked(e -> openAction.run());
        previewArea.setCursor(javafx.scene.Cursor.HAND);

        card.getChildren().addAll(header, previewArea);
        return card;
    }

    private String extractYoutubeId(String url) {
        if (url == null || url.isBlank()) return null;
        // Regex simplifiée utilisant un groupe de capture pour l'ID (compatible Java)
        String pattern = "^.*(?:youtu.be\\/|v\\/|u\\/\\w\\/|embed\\/|watch\\?v=|&v=)([^#&?]{11}).*";
        java.util.regex.Pattern compiledPattern = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = compiledPattern.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private void computeModuleAccessForCategory(ModuleCategorie categorie) {
        if (categorie == null) {
            return;
        }
        User user = AppState.getCurrentUser();
        if (user == null) {
            return;
        }
        try {
            ModuleQuizService.LevelProgressResult facileProgress =
                    moduleQuizService.getLevelProgress(user.getId(), categorie, ModuleNiveau.facile);
            ModuleQuizService.LevelProgressResult moyenProgress =
                    moduleQuizService.getLevelProgress(user.getId(), categorie, ModuleNiveau.moyen);

            moduleLockReasonById.put(-1, "Terminez le niveau précédent");
            moduleAccessById.put(-1, true);

            for (ModuleContent module : moduleService.findAll()) {
                if (module.getCategorieEnum() != categorie) {
                    continue;
                }
                boolean unlocked = true;
                String reason = "";
                if (module.getNiveau() == ModuleNiveau.moyen) {
                    unlocked = facileProgress.complete();
                    if (!unlocked) {
                        reason = "Terminez le niveau facile (" + facileProgress.passedModules() + "/" + facileProgress.totalModules() + ")";
                    }
                } else if (module.getNiveau() == ModuleNiveau.difficile) {
                    unlocked = moyenProgress.complete();
                    if (!unlocked) {
                        reason = "Terminez le niveau moyen (" + moyenProgress.passedModules() + "/" + moyenProgress.totalModules() + ")";
                    }
                }
                moduleAccessById.put(module.getId(), unlocked);
                if (!reason.isBlank()) {
                    moduleLockReasonById.put(module.getId(), reason);
                }
            }
        } catch (Exception e) {
            // Fallback sécurisé : en cas d'erreur, ne pas bloquer la vue.
            moduleAccessById.clear();
            moduleLockReasonById.clear();
        }
    }

    private boolean isModuleLocked(ModuleContent module) {
        if (module == null) {
            return false;
        }
        if (module.getNiveau() == ModuleNiveau.facile) {
            return false;
        }
        User user = AppState.getCurrentUser();
        if (user == null) {
            moduleLockReasonById.put(module.getId(), "Connectez-vous et terminez le niveau précédent");
            return true;
        }
        Boolean unlocked = moduleAccessById.get(module.getId());
        if (unlocked == null) {
            return false;
        }
        return !unlocked;
    }

    private void refreshQuizButtonState(ModuleContent module) {
        if (mdPassQuizBtn == null || module == null) {
            return;
        }
        User user = AppState.getCurrentUser();
        if (user == null) {
            mdPassQuizBtn.setText("🎓 Passer le quiz (connexion requise)");
            mdPassQuizBtn.setDisable(false);
            return;
        }
        try {
            double best = moduleQuizService.getBestScorePercent(user.getId(), module.getId());
            if (best >= 80.0) {
                mdPassQuizBtn.setText("✅ Quiz validé (" + Math.round(best) + "%)");
            } else if (best >= 0) {
                mdPassQuizBtn.setText("🎓 Repasser le quiz (meilleur score " + Math.round(best) + "%)");
            } else {
                mdPassQuizBtn.setText("🎓 Passer le quiz");
            }
            mdPassQuizBtn.setDisable(false);
        } catch (SQLException e) {
            mdPassQuizBtn.setText("🎓 Passer le quiz");
            mdPassQuizBtn.setDisable(false);
        }
    }

    @FXML
    private void onPassModuleQuiz() {
        if (currentModule == null) {
            return;
        }
        User user = AppState.getCurrentUser();
        if (user == null) {
            showAlert(Alert.AlertType.WARNING, "Connexion requise",
                    "Connectez-vous pour passer le quiz de validation du module.");
            return;
        }
        if (mdPassQuizBtn != null) {
            mdPassQuizBtn.setDisable(true);
            mdPassQuizBtn.setText("⏳ Génération du quiz...");
        }
        Thread worker = new Thread(() -> {
            try {
                ModuleQuizService.QuizData quiz = moduleQuizService.getOrCreateQuizForModule(currentModule, activeLanguage);
                Platform.runLater(() -> {
                    if (mdPassQuizBtn != null) {
                        mdPassQuizBtn.setDisable(false);
                    }
                    showQuizPage(quiz, user.getId(), currentModule);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    refreshQuizButtonState(currentModule);
                    showAlert(Alert.AlertType.ERROR, "Quiz",
                            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                });
            }
        }, "module-quiz-generate");
        worker.setDaemon(true);
        worker.start();
    }

    private void showQuizPage(ModuleQuizService.QuizData quiz, int userId, ModuleContent module) {
        if (quiz == null || quiz.questions() == null || quiz.questions().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Quiz", "Quiz indisponible.");
            return;
        }
        activeQuizData = quiz;
        activeQuizUserId = userId;
        activeQuizGroups.clear();

        AppLanguage lang = activeLanguage != null ? activeLanguage : AppLanguage.FR;

        if (quizModuleTitleLabel != null) {
            quizModuleTitleLabel.setText("Module : " + (module != null ? module.getTitre() : "—"));
        }
        if (quizStatusLabel != null) {
            quizStatusLabel.setText("");
        }
        if (quizQuestionsContainer != null) {
            quizQuestionsContainer.getChildren().clear();
            List<ModuleQuizService.QuizQuestion> questions = quiz.questions();
            for (int i = 0; i < questions.size(); i++) {
                final int idx = i;
                ModuleQuizService.QuizQuestion q = questions.get(i);
                VBox qBox = new VBox(8);
                qBox.getStyleClass().add("blog-md-quiz-question-box");

                // Afficher la question (en FR d'abord, puis traduite de façon async)
                Label qLabel = new Label((idx + 1) + ". " + q.question());
                qLabel.setWrapText(true);
                qLabel.getStyleClass().add("blog-md-quiz-question");
                qBox.getChildren().add(qLabel);

                ToggleGroup group = new ToggleGroup();
                activeQuizGroups.add(group);
                List<RadioButton> optButtons = new ArrayList<>();
                for (int c = 0; c < q.options().size(); c++) {
                    RadioButton opt = new RadioButton(q.options().get(c));
                    opt.setWrapText(true);
                    opt.setUserData(c);
                    opt.setToggleGroup(group);
                    opt.getStyleClass().add("blog-md-quiz-option");
                    qBox.getChildren().add(opt);
                    optButtons.add(opt);
                }
                quizQuestionsContainer.getChildren().add(qBox);

                // Traduction asynchrone de la question et des options
                if (!"fr".equalsIgnoreCase(lang.code())) {
                    final String questionFr = q.question();
                    final List<String> optionsFr = new ArrayList<>(q.options());
                    translationExecutor.submit(() -> {
                        try {
                            String qTr = libreTranslateService.translateFromFrench(questionFr, lang);
                            List<String> optTr = new ArrayList<>();
                            for (String opt : optionsFr) {
                                String tr = libreTranslateService.translateFromFrench(opt, lang);
                                optTr.add(tr != null && !tr.isBlank() ? tr : opt);
                            }
                            Platform.runLater(() -> {
                                if (qTr != null && !qTr.isBlank()) {
                                    qLabel.setText((idx + 1) + ". " + qTr);
                                }
                                for (int c = 0; c < optButtons.size() && c < optTr.size(); c++) {
                                    optButtons.get(c).setText(optTr.get(c));
                                }
                            });
                        } catch (Exception ignored) {}
                    });
                }
            }
        }
        switchView(moduleQuizView);
    }

    @FXML
    private void onSubmitQuizFromPage() {
        if (activeQuizData == null || currentModule == null || activeQuizUserId <= 0) {
            showAlert(Alert.AlertType.WARNING, "Quiz", "Quiz invalide. Relancez le quiz.");
            return;
        }
        boolean allAnswered = activeQuizGroups.stream().allMatch(g -> g.getSelectedToggle() != null);
        if (!allAnswered) {
            showAlert(Alert.AlertType.WARNING, "Quiz incomplet", "Veuillez répondre à toutes les questions.");
            return;
        }
        List<Integer> answers = new ArrayList<>();
        for (ToggleGroup group : activeQuizGroups) {
            answers.add((Integer) group.getSelectedToggle().getUserData());
        }

        if (quizSubmitBtn != null) {
            quizSubmitBtn.setDisable(true);
        }
        if (quizStatusLabel != null) {
            quizStatusLabel.setText("Validation du quiz...");
        }
        Thread worker = new Thread(() -> {
            try {
                ModuleQuizService.QuizAttemptResult attempt =
                        moduleQuizService.submitAttempt(activeQuizUserId, currentModule.getId(), activeQuizData.quizId(), answers, activeQuizData.questions());
                Platform.runLater(() -> {
                    if (quizSubmitBtn != null) {
                        quizSubmitBtn.setDisable(false);
                    }
                    String scoreText = String.format(java.util.Locale.US, "%.2f", attempt.scorePercent());
                    if (attempt.passed()) {
                        showAlert(Alert.AlertType.INFORMATION, "Quiz validé",
                                "Bravo ! Vous avez obtenu " + scoreText + "%.\nLe module est validé.");
                    } else {
                        showAlert(Alert.AlertType.WARNING, "Quiz non validé",
                                "Score: " + scoreText + "%.\nVous devez atteindre au moins 80% pour valider ce module.");
                    }
                    if (quizStatusLabel != null) {
                        quizStatusLabel.setText("Score: " + scoreText + "%");
                    }
                    refreshQuizButtonState(currentModule);
                    if (currentCategory != null) {
                        loadModulesForCategory(currentCategory);
                    }
                    showModuleDetail(currentModule);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (quizSubmitBtn != null) {
                        quizSubmitBtn.setDisable(false);
                    }
                    if (quizStatusLabel != null) {
                        quizStatusLabel.setText("Échec validation quiz.");
                    }
                    showAlert(Alert.AlertType.ERROR, "Quiz",
                            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                });
            }
        }, "module-quiz-submit");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void onBackFromQuizToModule() {
        if (currentModule != null) {
            showModuleDetail(currentModule);
            return;
        }
        if (currentCategory != null) {
            showCategoryDetail(currentCategory);
        } else {
            switchView(categoriesView);
        }
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

    private void ensureEditArticleTypeChoices() {
        if (editTypeCombo != null) {
            editTypeCombo.setItems(FXCollections.observableArrayList(ARTICLE_TYPES));
        }
        if (editAiTypeCombo != null) {
            editAiTypeCombo.setItems(FXCollections.observableArrayList(ARTICLE_TYPES));
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
        if (articleImageLabel != null) {
            articleImageLabel.setText("Aucun fichier choisi");
            articleImageLabel.setVisible(true);
            articleImageLabel.setManaged(true);
        }
        if (articleImagePreview != null) {
            articleImagePreview.setImage(null);
            articleImagePreview.setVisible(false);
            articleImagePreview.setManaged(false);
        }
        if (articlePexelsFlow != null) articlePexelsFlow.getChildren().clear();
        if (articlePexelsSearchField != null) articlePexelsSearchField.clear();
        if (rbUploadFile != null) rbUploadFile.setSelected(true);
        updateArticleImagePanels();
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
            if (articleImageLabel != null) articleImageLabel.setText(file.getName());
            if (articleImagePreview != null) {
                try {
                    Image img = new Image(file.toURI().toString());
                    articleImagePreview.setImage(img);
                    articleImagePreview.setVisible(true);
                    articleImagePreview.setManaged(true);
                    if (articleImageLabel != null) {
                        articleImageLabel.setVisible(false);
                        articleImageLabel.setManaged(false);
                    }
                } catch (Exception e) {
                    articleImagePreview.setVisible(false);
                    articleImagePreview.setManaged(false);
                }
            }
            if (rbUploadFile != null) {
                rbUploadFile.setSelected(true);
            }
            updateArticleImagePanels();
            System.out.println("Image sélectionnée : " + file.getAbsolutePath());
        }
    }

    @FXML
    private void onGenerateAI() {
        String prompt = aiPromptField != null ? aiPromptField.getText() : "";
        String type = aiTypeCombo != null ? aiTypeCombo.getValue() : null;
        if (prompt.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "IA", "Décrivez votre sujet et le contexte (pourquoi) dans le champ, avec un lien clair aux TSA / autisme.");
            return;
        }
        if (type == null) {
            showAlert(Alert.AlertType.WARNING, "IA", "Veuillez sélectionner un type d'article.");
            return;
        }
        runArticleAiGeneration(prompt, type, false);
    }

    @FXML
    private void onGenerateHashtags() {
        String titre = articleTitreField != null ? articleTitreField.getText() : "";
        String contenu = articleContenuField != null ? articleContenuField.getText() : "";
        String type = articleTypeCombo != null ? articleTypeCombo.getValue() : null;
        if (titre.isBlank() && contenu.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Hashtags", "Renseignez au moins un titre ou du contenu.");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                String tags = articleAiGenerationService.generateHashtags(titre, contenu, type);
                
                // Traduire les hashtags si nécessaire
                AppLanguage lang = activeLanguage != null ? activeLanguage : AppLanguage.FR;
                String finalTags = tags;
                if (!"fr".equalsIgnoreCase(lang.code())) {
                    try {
                        String trTags = libreTranslateService.translateFromFrench(tags, lang);
                        if (trTags != null && !trTags.isBlank()) finalTags = trTags;
                    } catch (Exception ignored) {}
                }
                
                final String dispTags = finalTags;
                Platform.runLater(() -> {
                    if (articleContenuField != null) {
                        String base = articleContenuField.getText() != null ? articleContenuField.getText().trim() : "";
                        articleContenuField.setText((base + "\n\n" + dispTags).trim());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Hashtags",
                        "Échec génération hashtags: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            }
        }, "article-ai-hashtags-add");
        worker.setDaemon(true);
        worker.start();
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

        if (currentModule == null) {
            performPersistNewArticle(titre, contenu, type, user);
            return;
        }

        final String modTitre = currentModule.getTitre();
        final String modDesc = currentModule.getDescription();
        final String modContenu = currentModule.getContenu();
        final String modCategorie = currentModule.getCategorie();

        Thread worker = new Thread(() -> {
            try {
                ArticleAiGenerationService.ModuleArticleAlignmentResult modCheck =
                        articleAiGenerationService.validateArticleAlignsWithModule(
                                titre, contenu, type, modTitre, modDesc, modContenu, modCategorie);
                if (!modCheck.aligned()) {
                    String msg = modCheck.message() != null && !modCheck.message().isBlank()
                            ? modCheck.message()
                            : "L'article ne correspond pas assez au sujet du du module.";
                    Platform.runLater(() -> showAlert(Alert.AlertType.WARNING,
                            "Cohérence avec le module", msg));
                    return;
                }
                Platform.runLater(() -> performPersistNewArticle(titre, contenu, type, user));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Vérification module",
                        "Échec de la vérification IA : " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            }
        }, "article-publish-module-align");
        worker.setDaemon(true);
        worker.start();
    }

    /** Enregistre l'article après validations locales (et Groq module si applicable). À appeler sur le thread JavaFX. */
    private void performPersistNewArticle(String titre, String contenu, String type, User user) {
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
            
            // Masquer le formulaire après publication
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
            applyLanguageNow();
        } catch (Exception e) {
            currentArticles = List.of();
            Label err = new Label("Erreur de chargement des articles.");
            err.getStyleClass().add("blog-empty-label");
            mdArticlesContainer.getChildren().add(err);
            applyLanguageNow();
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
        applyLanguageNow();
    }

    private VBox buildArticleItem(BlogArticle article) {
        VBox card = new VBox(8);
        card.getStyleClass().add("blog-art-card");
        card.setMaxWidth(Double.MAX_VALUE);

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

        // Traduction dynamique du titre
        translateDynamicLabeled(titleLbl, article.getTitre());

        HBox actionIcons = new HBox(6);
        actionIcons.getStyleClass().add("soft-actions");
        actionIcons.setAlignment(Pos.CENTER_RIGHT);
        Button btnTts = new Button("▶");
        btnTts.getStyleClass().addAll("soft-icon-btn", "soft-icon-btn--green");
        Button btnView = new Button("✓");
        btnView.getStyleClass().addAll("soft-icon-btn", "soft-icon-btn--green");
        Button btnEdit = new Button("✎");
        btnEdit.getStyleClass().addAll("soft-icon-btn", "soft-icon-btn--blue");
        Button btnDelete = new Button("🗑");
        btnDelete.getStyleClass().addAll("soft-icon-btn", "soft-icon-btn--red");

        User currentUser = AppState.getCurrentUser();
        boolean isOwner = currentUser != null && article.getUserId() != null
                && currentUser.getId() == article.getUserId();
        btnEdit.setVisible(isOwner);
        btnEdit.setManaged(isOwner);
        btnDelete.setVisible(isOwner);
        btnDelete.setManaged(isOwner);
        btnView.setVisible(isOwner);
        btnView.setManaged(isOwner);

        btnTts.setOnAction(e -> onToggleArticleTts(btnTts, article));
        btnEdit.setOnAction(e -> showEditArticle(article));
        btnDelete.setOnAction(e -> onDeleteArticle(article));
        btnView.setOnAction(e -> openSpellCheckForArticle(article));

        actionIcons.getChildren().addAll(btnTts, btnView, btnEdit, btnDelete);
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
        TextArea contentArea = new TextArea(fullContent);
        contentArea.getStyleClass().addAll("blog-art-content", "blog-art-content-area");
        contentArea.setWrapText(true);
        contentArea.setEditable(false);
        contentArea.setFocusTraversable(true);
        contentArea.setMaxWidth(Double.MAX_VALUE);
        contentArea.setMinHeight(Region.USE_PREF_SIZE);
        contentArea.setPrefRowCount(1);
        /* Largeur = carte moins padding horizontal (16+16) */
        contentArea.prefWidthProperty().bind(card.widthProperty().subtract(32));
        enableAutoGrowTextArea(contentArea, 1);

        // Traduction dynamique du contenu
        translateDynamicTextInput(contentArea, article.getContenu());
        setupDictionarySupport(contentArea);

        /* ── Row 4: social icons ── */
        HBox row4 = new HBox(10);
        row4.setAlignment(Pos.CENTER_LEFT);

        Button commentIcon = new Button("💬");
        commentIcon.getStyleClass().add("blog-art-social");
        Label shareIcon = new Label("➜");
        shareIcon.getStyleClass().add("blog-art-social");
        row4.getChildren().addAll(commentIcon, shareIcon);

        /* ── Section commentaires (masquée par défaut) ── */
        VBox commentsListBox = new VBox(0);
        commentsListBox.getStyleClass().add("blog-comments-list");

        // Champ de saisie
        TextField commentField = new TextField();
        commentField.setPromptText("Laissez une réponse...");
        commentField.getStyleClass().add("blog-comment-input");
        HBox.setHgrow(commentField, Priority.ALWAYS);

        // Bouton image
        Button imgBtn = new Button("📷");
        imgBtn.getStyleClass().add("blog-comment-img-btn");
        imgBtn.setFocusTraversable(false);

        // Bouton soumettre ✓
        Button sendBtn = new Button("✓");
        sendBtn.getStyleClass().add("blog-comment-send-btn");
        sendBtn.setFocusTraversable(false);

        String[] imagePathHolder = {null};

        // Ligne de prévisualisation (cachée au départ)
        ImageView previewThumb = new ImageView();
        previewThumb.setFitWidth(80);
        previewThumb.setFitHeight(50);
        previewThumb.setPreserveRatio(true);
        previewThumb.setSmooth(true);
        Button clearImgBtn = new Button("×");
        clearImgBtn.getStyleClass().add("blog-comment-clear-img-btn");
        HBox previewRow = new HBox(6, previewThumb, clearImgBtn);
        previewRow.setAlignment(Pos.CENTER_LEFT);
        previewRow.setPadding(new Insets(4, 0, 0, 0));
        previewRow.setVisible(false);
        previewRow.setManaged(false);

        clearImgBtn.setOnAction(ev -> {
            imagePathHolder[0] = null;
            previewThumb.setImage(null);
            previewRow.setVisible(false);
            previewRow.setManaged(false);
            imgBtn.setText("📷");
            ev.consume();
        });

        imgBtn.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choisir une image");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
            );
            File f = fc.showOpenDialog(card.getScene() != null ? card.getScene().getWindow() : null);
            if (f != null) {
                imagePathHolder[0] = f.getAbsolutePath();
                try {
                    previewThumb.setImage(new Image("file:" + f.getAbsolutePath(), 80, 50, true, true));
                    previewRow.setVisible(true);
                    previewRow.setManaged(true);
                } catch (Exception ignored) {}
                imgBtn.setText("📷 ✓");
            }
            ev.consume();
        });

        HBox inputRow = new HBox(8, commentField, imgBtn, sendBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(8, 0, 0, 0));

        VBox commentSection = new VBox(4, commentsListBox, inputRow, previewRow);
        commentSection.getStyleClass().add("blog-comment-section");
        commentSection.setVisible(false);
        commentSection.setManaged(false);

        // Soumettre avec Entrée (consommé pour bloquer la propagation)
        commentField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                submitComment(commentField, imagePathHolder, imgBtn, previewRow, previewThumb,
                        article, commentsListBox);
            }
        });

        // Soumettre avec le bouton ✓
        sendBtn.setOnAction(e -> {
            e.consume();
            submitComment(commentField, imagePathHolder, imgBtn, previewRow, previewThumb,
                    article, commentsListBox);
        });

        // Toggle section commentaires
        commentIcon.setOnAction(e -> toggleCommentSection(article, commentSection, commentsListBox));

        card.getChildren().addAll(row1, row2, contentArea, row4, commentSection);
        return card;
    }

    /* ── Commentaires ── */

    private void toggleCommentSection(BlogArticle article, VBox section, VBox commentsListBox) {
        if (section.isVisible()) {
            section.setVisible(false);
            section.setManaged(false);
        } else {
            refreshComments(article, commentsListBox);
            section.setVisible(true);
            section.setManaged(true);
        }
    }

    private void refreshComments(BlogArticle article, VBox commentsListBox) {
        commentsListBox.getChildren().clear();
        try {
            List<Commentaire> comments = commentaireService.findByBlogId(article.getId());
            for (Commentaire c : comments) {
                commentsListBox.getChildren().add(buildCommentRow(c, article, commentsListBox));
            }
        } catch (Exception e) {
            Label err = new Label("Impossible de charger les commentaires.");
            err.getStyleClass().add("blog-comment-content");
            commentsListBox.getChildren().add(err);
        }
    }

    private HBox buildCommentRow(Commentaire c, BlogArticle article, VBox commentsListBox) {
        // Avatar avec initiales
        String initials = resolveInitials(c.getUserId());
        Label avatar = new Label(initials);
        avatar.getStyleClass().add("blog-comment-avatar");

        // Nom + date
        String authorName = resolveUserEmail(c.getUserId());
        Label authorLbl = new Label(authorName);
        authorLbl.getStyleClass().add("blog-comment-author");

        String dateStr = c.getDateCreation() != null
                ? c.getDateCreation().format(DATETIME_FMT)
                : "";
        Label dateLbl = new Label(dateStr);
        dateLbl.getStyleClass().add("blog-comment-date");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox headerRow = new HBox(8, authorLbl, dateLbl, spacer);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Bouton supprimer (seulement pour l'auteur du commentaire)
        User currentUser = AppState.getCurrentUser();
        boolean isOwner = currentUser != null && c.getUserId() != null
                && currentUser.getId() == c.getUserId();
        if (isOwner) {
            Button deleteBtn = new Button("🗑");
            deleteBtn.getStyleClass().add("blog-comment-delete-btn");
            deleteBtn.setOnAction(e -> {
                try {
                    commentaireService.delete(c.getId());
                    refreshComments(article, commentsListBox);
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de supprimer le commentaire.");
                }
            });
            headerRow.getChildren().add(deleteBtn);
        }

        // Contenu texte
        Label contentLbl = new Label(c.getContenu() != null ? c.getContenu() : "");
        contentLbl.getStyleClass().add("blog-comment-content");
        contentLbl.setWrapText(true);
        contentLbl.setMaxWidth(Double.MAX_VALUE);

        VBox textBox = new VBox(4, headerRow, contentLbl);

        // Image optionnelle
        if (c.getMedia() != null && !c.getMedia().isBlank()) {
            try {
                ImageView imgView = new ImageView(new Image("file:" + c.getMedia(), 200, 120, true, true));
                imgView.getStyleClass().add("blog-comment-img-preview");
                textBox.getChildren().add(imgView);
            } catch (Exception ignored) {}
        }

        // Barre de réactions
        HBox reactionsBar = buildReactionsBar(c);
        textBox.getChildren().add(reactionsBar);

        HBox.setHgrow(textBox, Priority.ALWAYS);
        HBox row = new HBox(10, avatar, textBox);
        row.getStyleClass().add("blog-comment-row");
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }

    /** Construit la barre de réactions d'un commentaire. */
    private HBox buildReactionsBar(Commentaire c) {
        // Données réactions : type → (emoji, label)
        String[][] REACTIONS = {
            {"star",  "⭐", "J'aime"},
            {"heart", "❤️", "J'adore"},
            {"haha",  "😄", "Haha"},
            {"wow",   "😲", "Wow"},
            {"sad",   "😢", "Triste"},
            {"angry", "😠", "Grrr"}
        };

        User currentUser = AppState.getCurrentUser();

        // Bouton principal "J'aime" (ou réaction active)
        Button mainBtn = new Button();
        mainBtn.getStyleClass().add("blog-reaction-main-btn");
        mainBtn.setFocusTraversable(false);

        // Compteurs affichés à droite du bouton principal
        HBox countersBox = new HBox(4);
        countersBox.setAlignment(Pos.CENTER_LEFT);

        // Référence mutable pour mise à jour
        HBox reactionsBar = new HBox(6);
        reactionsBar.setAlignment(Pos.CENTER_LEFT);
        reactionsBar.setPadding(new Insets(4, 0, 0, 0));

        // Charger l'état initial
        refreshReactionBar(c, mainBtn, countersBox, currentUser, REACTIONS);

        // Popup picker
        Popup picker = new Popup();
        picker.setAutoHide(true);
        HBox pickerBox = new HBox(6);
        pickerBox.getStyleClass().add("blog-reaction-picker");
        try {
            java.net.URL cssUrl = getClass().getResource("/styles/page-blog.css");
            if (cssUrl != null) {
                pickerBox.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {}
        pickerBox.setStyle("-fx-background-color: white; -fx-background-radius: 50; -fx-border-color: #e0d9f5; -fx-border-radius: 50; -fx-border-width: 1;");
        pickerBox.setPadding(new Insets(8));

        for (String[] r : REACTIONS) {
            String rType = r[0];
            String rEmoji = r[1];
            String rLabel = r[2];

            VBox pickerItem = new VBox(2);
            pickerItem.setAlignment(Pos.CENTER);
            pickerItem.getStyleClass().add("blog-reaction-picker-item");

            // Essayer de charger l'image PNG, sinon fallback emoji
            ImageView iv = loadReactionImage(rType, 32);
            Label emojiLbl = new Label(rEmoji);
            emojiLbl.setStyle("-fx-font-size:22px;");
            if (iv != null) {
                pickerItem.getChildren().addAll(iv);
            } else {
                pickerItem.getChildren().add(emojiLbl);
            }
            Label nameLbl = new Label(rLabel);
            nameLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#555;");
            pickerItem.getChildren().add(nameLbl);

            pickerItem.setOnMouseClicked(ev -> {
                picker.hide();
                if (currentUser == null) {
                    showAlert(Alert.AlertType.WARNING, "Connexion requise",
                            "Connectez-vous pour réagir.");
                    return;
                }
                try {
                    reactionService.addOrUpdate(c.getId(), currentUser.getId(), rType);
                    refreshReactionBar(c, mainBtn, countersBox, currentUser, REACTIONS);
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'enregistrer la réaction.");
                }
                ev.consume();
            });
            pickerBox.getChildren().add(pickerItem);
        }
        picker.getContent().add(pickerBox);

        // Afficher / cacher le picker au clic sur le bouton principal
        mainBtn.setOnAction(ev -> {
            if (picker.isShowing()) {
                picker.hide();
            } else {
                javafx.geometry.Bounds b = mainBtn.localToScreen(mainBtn.getBoundsInLocal());
                if (b != null) picker.show(mainBtn, b.getMinX(), b.getMinY() - 80);
            }
            ev.consume();
        });

        reactionsBar.getChildren().addAll(mainBtn, countersBox);
        return reactionsBar;
    }

    /** Recharge l'état du bouton principal et les compteurs. */
    private void refreshReactionBar(Commentaire c, Button mainBtn, HBox countersBox,
                                     User currentUser, String[][] REACTIONS) {
        countersBox.getChildren().clear();
        try {
            String userReaction = currentUser != null
                    ? reactionService.getUserReaction(c.getId(), currentUser.getId()) : null;
            Map<String, Integer> counts = reactionService.countByType(c.getId());

            // Bouton principal : afficher la réaction active ou "⭐ J'aime"
            if (userReaction != null) {
                String activeEmoji = emojiForType(userReaction, REACTIONS);
                ImageView iv = loadReactionImage(userReaction, 18);
                if (iv != null) {
                    mainBtn.setGraphic(iv);
                    mainBtn.setText(" " + labelForType(userReaction, REACTIONS));
                } else {
                    mainBtn.setGraphic(null);
                    mainBtn.setText(activeEmoji + " " + labelForType(userReaction, REACTIONS));
                }
                mainBtn.getStyleClass().removeAll("blog-reaction-main-btn-active");
                mainBtn.getStyleClass().add("blog-reaction-main-btn-active");
            } else {
                ImageView iv = loadReactionImage("star", 18);
                if (iv != null) {
                    mainBtn.setGraphic(iv);
                    mainBtn.setText(" J'aime");
                } else {
                    mainBtn.setGraphic(null);
                    mainBtn.setText("⭐ J'aime");
                }
                mainBtn.getStyleClass().removeAll("blog-reaction-main-btn-active");
            }

            // Compteurs par type
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String t = entry.getKey();
                int cnt = entry.getValue();
                if (cnt <= 0) continue;
                String emoji = emojiForType(t, REACTIONS);
                ImageView iv = loadReactionImage(t, 16);
                Label badge = new Label();
                if (iv != null) {
                    badge.setGraphic(iv);
                    badge.setText(" " + cnt);
                } else {
                    badge.setText(emoji + " " + cnt);
                }
                badge.getStyleClass().add("blog-reaction-count-badge");
                countersBox.getChildren().add(badge);
            }
        } catch (Exception ignored) {}
    }

    private ImageView loadReactionImage(String type, double size) {
        try {
            java.io.InputStream is = getClass().getResourceAsStream(
                    "/images/reactions/" + type + ".png");
            if (is == null) return null;
            Image img = new Image(is, size, size, true, true);
            return new ImageView(img);
        } catch (Exception e) {
            return null;
        }
    }

    private String emojiForType(String type, String[][] reactions) {
        for (String[] r : reactions) if (r[0].equals(type)) return r[1];
        return "⭐";
    }

    private String labelForType(String type, String[][] reactions) {
        for (String[] r : reactions) if (r[0].equals(type)) return r[2];
        return "J'aime";
    }

    private void submitComment(TextField commentField, String[] imagePathHolder, Button imgBtn,
                                HBox previewRow, ImageView previewThumb,
                                BlogArticle article, VBox commentsListBox) {
        String texte = commentField.getText().trim();
        if (texte.isBlank()) return;

        User user = AppState.getCurrentUser();
        if (user == null) {
            showAlert(Alert.AlertType.WARNING, "Connexion requise",
                    "Vous devez être connecté pour laisser un commentaire.");
            return;
        }

        Commentaire c = new Commentaire();
        c.setContenu(texte);
        c.setMedia(imagePathHolder[0]);
        c.setUserId(user.getId());
        c.setBlogId(article.getId());

        // Modération de contenu avec Sightengine
        String langCode = activeLanguage != null ? activeLanguage.code() : "fr";
        if (sightengineService.isOffensive(texte, langCode)) {
            showAlert(Alert.AlertType.WARNING, "Modération", 
                "Votre commentaire contient des propos jugés offensants ou inappropriés. Veuillez reformuler votre message.");
            return;
        }

        try {
            commentaireService.create(c);
            // Réinitialiser le champ et l'image
            commentField.clear();
            imagePathHolder[0] = null;
            imgBtn.setText("📷");
            previewThumb.setImage(null);
            previewRow.setVisible(false);
            previewRow.setManaged(false);
            refreshComments(article, commentsListBox);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'enregistrer le commentaire : " + e.getMessage());
        }
    }

    private String resolveInitials(Integer userId) {
        if (userId == null) return "?";
        String email = resolveUserEmail(userId);
        if (email == null || email.isBlank() || "inconnu".equals(email)) return "?";
        // Prendre les 2 premières lettres avant le @
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String[] parts = local.split("[._\\-]");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return local.substring(0, Math.min(2, local.length())).toUpperCase();
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
        ensureEditArticleTypeChoices();

        // Traduction dynamique des champs pour l'édition
        if (activeLanguage != null && !"fr".equalsIgnoreCase(activeLanguage.code())) {
            int sessionId = activeTranslationSession;
            translationExecutor.submit(() -> {
                String tit = article.getTitre();
                String con = article.getContenu();
                String titT = libreTranslateService.translateAuto(tit, activeLanguage);
                String conT = libreTranslateService.translateAuto(con, activeLanguage);
                Platform.runLater(() -> {
                    if (sessionId == activeTranslationSession) {
                        if (editTitreField != null) editTitreField.setText(titT != null ? titT : (tit != null ? tit : ""));
                        if (editContenuField != null) editContenuField.setText(conT != null ? conT : (con != null ? con : ""));
                    }
                });
            });
        } else {
            if (editTitreField != null) editTitreField.setText(article.getTitre() != null ? article.getTitre() : "");
            if (editContenuField != null) editContenuField.setText(article.getContenu() != null ? article.getContenu() : "");
        }
        if (editCbPublished != null) editCbPublished.setSelected(article.isPublished());
        if (editCbUrgent != null) editCbUrgent.setSelected(article.isUrgent());
        if (editCbVisible != null) editCbVisible.setSelected(article.isVisible());
        if (editTypeCombo != null) editTypeCombo.setValue(article.getType());
        if (editAiTypeCombo != null) editAiTypeCombo.getSelectionModel().clearSelection();
        if (editAiPromptField != null) editAiPromptField.clear();
        if (editArticlePexelsFlow != null) editArticlePexelsFlow.getChildren().clear();
        if (editArticlePexelsSearchField != null) editArticlePexelsSearchField.clear();

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
        updateEditArticleImagePanels();
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
            if (editArticleImagePreview != null) {
                try {
                    Image img = new Image(file.toURI().toString());
                    editArticleImagePreview.setImage(img);
                    editArticleImagePreview.setVisible(true);
                    editArticleImagePreview.setManaged(true);
                    if (editImageLabel != null) {
                        editImageLabel.setVisible(false);
                        editImageLabel.setManaged(false);
                    }
                } catch (Exception e) {
                    editArticleImagePreview.setVisible(false);
                    editArticleImagePreview.setManaged(false);
                }
            }
            if (editRbUploadFile != null) {
                editRbUploadFile.setSelected(true);
            }
            updateEditArticleImagePanels();
            System.out.println("Image sélectionnée : " + file.getAbsolutePath());
        }
    }

    @FXML
    private void onEditGenerateAI() {
        String prompt = editAiPromptField != null ? editAiPromptField.getText() : "";
        String type = editAiTypeCombo != null ? editAiTypeCombo.getValue() : null;
        if (prompt.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "IA", "Décrivez votre sujet et le contexte (pourquoi) dans le champ, avec un lien clair aux TSA / autisme.");
            return;
        }
        if (type == null) {
            showAlert(Alert.AlertType.WARNING, "IA", "Veuillez sélectionner un type d'article.");
            return;
        }
        runArticleAiGeneration(prompt, type, true);
    }

    private void runArticleAiGeneration(String prompt, String type, boolean editMode) {
        String promptTrim = prompt != null ? prompt.trim() : "";
        Thread worker = new Thread(() -> {
            try {
                ArticleAiGenerationService.TsaRelevanceResult tsa =
                        articleAiGenerationService.validatePromptRelevantToAutisme(promptTrim);
                if (!tsa.relevant()) {
                    String msg = tsa.message() != null && !tsa.message().isBlank()
                            ? tsa.message()
                            : "Le texte doit être lié à l'autisme ou aux TSA pour utiliser la génération IA.";
                    Platform.runLater(() -> showAlert(Alert.AlertType.WARNING,
                            "Lien TSA / autisme requis", msg));
                    return;
                }
                ArticleAiGenerationService.PromptAlignmentResult alignment =
                        articleAiGenerationService.validatePromptForType(promptTrim, type);
                if (!alignment.aligned()) {
                    String msg = alignment.message() != null && !alignment.message().isBlank()
                            ? alignment.message()
                            : "Le prompt ne semble pas aligné avec le type choisi.";
                    Platform.runLater(() -> showAlert(Alert.AlertType.WARNING,
                            "Type d'article non aligné", msg));
                    return;
                }
                ArticleAiSuggestion generated = articleAiGenerationService.generate(promptTrim, type);

                // Traduction vers la langue active si nécessaire
                AppLanguage lang = activeLanguage != null ? activeLanguage : AppLanguage.FR;
                String finalTitre = generated.getTitre();
                String finalContenu = generated.getContenu();

                if (!"fr".equalsIgnoreCase(lang.code())) {
                    try {
                        String trTitre = libreTranslateService.translateFromFrench(finalTitre, lang);
                        if (trTitre != null && !trTitre.isBlank()) finalTitre = trTitre;

                        String trContenu = libreTranslateService.translateFromFrench(finalContenu, lang);
                        if (trContenu != null && !trContenu.isBlank()) finalContenu = trContenu;
                    } catch (Exception ignored) {}
                }

                final String dispTitre = finalTitre;
                final String dispContenu = finalContenu;

                Platform.runLater(() -> {
                    if (editMode) {
                        if (editTitreField != null) editTitreField.setText(dispTitre);
                        if (editContenuField != null) editContenuField.setText(dispContenu);
                        if (editTypeCombo != null) editTypeCombo.setValue(generated.getType());
                    } else {
                        if (articleTitreField != null) articleTitreField.setText(dispTitre);
                        if (articleContenuField != null) articleContenuField.setText(dispContenu);
                        if (articleTypeCombo != null) articleTypeCombo.setValue(generated.getType());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "IA",
                        "Échec de génération IA: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            }
        }, editMode ? "article-ai-generate-edit" : "article-ai-generate-add");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void onEditGenerateHashtags() {
        String titre = editTitreField != null ? editTitreField.getText() : "";
        String contenu = editContenuField != null ? editContenuField.getText() : "";
        String type = editTypeCombo != null ? editTypeCombo.getValue() : null;
        if (titre.isBlank() && contenu.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Hashtags", "Renseignez au moins un titre ou du contenu.");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                String tags = articleAiGenerationService.generateHashtags(titre, contenu, type);

                // Traduire les hashtags si nécessaire
                AppLanguage lang = activeLanguage != null ? activeLanguage : AppLanguage.FR;
                String finalTags = tags;
                if (!"fr".equalsIgnoreCase(lang.code())) {
                    try {
                        String trTags = libreTranslateService.translateFromFrench(tags, lang);
                        if (trTags != null && !trTags.isBlank()) finalTags = trTags;
                    } catch (Exception ignored) {}
                }

                final String dispTags = finalTags;
                Platform.runLater(() -> {
                    if (editContenuField != null) {
                        String base = editContenuField.getText() != null ? editContenuField.getText().trim() : "";
                        editContenuField.setText((base + "\n\n" + dispTags).trim());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Hashtags",
                        "Échec génération hashtags: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            }
        }, "article-ai-hashtags-edit");
        worker.setDaemon(true);
        worker.start();
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
        ttsService.stop();
        updateTtsBtnState(false);
        resetArticleTtsBtn();
        if (currentCategory != null) {
            showCategoryDetail(currentCategory);
        } else {
            switchView(categoriesView);
        }
    }

    /* ═══════ Navigation entre vues ═══════ */

    private void switchView(VBox target) {
        for (VBox v : new VBox[]{categoriesView, categoryDetailView, moduleDetailView, moduleQuizView, editArticleView}) {
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
        applyLanguageNow();
    }

    /* ═══════ Spell Check ═══════ */

    /** Opens spell check dialog for an article card — checks the content text. */
    private void openSpellCheckForArticle(BlogArticle article) {
        String text = article.getContenu();
        if (text == null || text.isBlank()) {
            showAlert(Alert.AlertType.INFORMATION, "Vérification orthographique",
                    "Le contenu de l'article est vide.");
            return;
        }
        openSpellCheckDialog(
                text,
                "Appliquer et modifier l'article",
                corrected -> {
                    article.setContenu(corrected);
                    try {
                        blogService.update(article);
                        if (currentModule != null) {
                            loadArticlesForModule(currentModule.getId());
                        }
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Erreur",
                                "Impossible de sauvegarder la correction : " + ex.getMessage());
                    }
                }
        );
    }

    /** Opens spell check for the article title field in the form. */
    @FXML
    private void onSpellCheckArticleTitre() {
        String text = articleTitreField != null ? articleTitreField.getText() : "";
        if (text == null || text.isBlank()) {
            showAlert(Alert.AlertType.INFORMATION, "Vérification orthographique",
                    "Le champ Titre est vide.");
            return;
        }
        openSpellCheckDialog(
                text,
                "Appliquer la correction",
                corrected -> { if (articleTitreField != null) articleTitreField.setText(corrected); }
        );
    }

    /** Opens spell check for the article content field in the form. */
    @FXML
    private void onSpellCheckArticleContenu() {
        String text = articleContenuField != null ? articleContenuField.getText() : "";
        if (text == null || text.isBlank()) {
            showAlert(Alert.AlertType.INFORMATION, "Vérification orthographique",
                    "Le champ Contenu est vide.");
            return;
        }
        openSpellCheckDialog(
                text,
                "Appliquer la correction",
                corrected -> { if (articleContenuField != null) articleContenuField.setText(corrected); }
        );
    }

    /**
     * Core helper: loads the spell-check dialog FXML, shows it as a popup Stage,
     * runs the LanguageTool API call in background and updates the dialog.
     */
    private void openSpellCheckDialog(String text, String applyLabel, Consumer<String> onApply) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/spell-check-dialog.fxml"));
            javafx.scene.layout.BorderPane dialogContent = loader.load();
            SpellCheckDialogController ctrl = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.setResizable(true);
            dialogStage.setTitle("Vérification orthographique");

            Scene scene = new Scene(dialogContent, 560, 500);
            dialogStage.setScene(scene);
            dialogStage.setMinWidth(480);
            dialogStage.setMinHeight(420);

            ctrl.setStage(dialogStage);
            ctrl.setApplyButtonLabel(applyLabel);
            ctrl.setOnApplyCallback(onApply);
            ctrl.showLoading();

            dialogStage.show();

            String langCode = (activeLanguage != null) ? activeLanguage.code() : "fr";

            Thread checkThread = new Thread(() -> {
                try {
                    List<SpellMatch> matches = languageToolService.check(text, langCode);
                    Platform.runLater(() -> ctrl.showResults(text, matches));
                } catch (Exception ex) {
                    Platform.runLater(() -> ctrl.showError(ex.getMessage()));
                }
            }, "spell-check-worker");
            checkThread.setDaemon(true);
            checkThread.start();

        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Impossible d'ouvrir le vérificateur : " + ex.getMessage());
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

    private void clearTranslationMemory() {
        baseLabeledText.clear();
        basePromptText.clear();
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

    @Override
    public void onShellLanguageChanged(AppLanguage language) {
        activeLanguage = language != null ? language : AppLanguage.FR;
        activeTranslationSession = translationSessionSeq.incrementAndGet();
        pendingTranslations.set(0);
        succeededTranslations.set(0);
        failedTranslations.set(0);
        firstFailureProvider.set("");
        firstFailureError.set("");
        if (activeLanguage == AppLanguage.FR) {
            setTranslationStatus("✅ Français appliqué.", "success");
        } else {
            setTranslationStatus("⏳ Traduction en cours...", "loading");
        }
        Platform.runLater(this::refreshCurrentViewLanguage);
    }

    private void refreshCurrentViewLanguage() {
        clearTranslationMemory();
        if (categoriesHeadingLabel != null) {
            categoriesHeadingLabel.setText("Catégories");
        }
        if (heroTitleLabel != null) {
            heroTitleLabel.setText("Modules & Témoignages");
        }
        if (heroSubtitleLabel != null) {
            heroSubtitleLabel.setText("Explorez nos modules d'apprentissage par catégorie. Partagez vos expériences, découvrez des conseils pratiques et rejoignez notre communauté.");
        }
        if (backToCategoriesBtn != null) {
            backToCategoriesBtn.setText("← Retour aux catégories");
        }

        if (moduleQuizView != null && moduleQuizView.isVisible() && activeQuizData != null && currentModule != null) {
            showQuizPage(activeQuizData, activeQuizUserId, currentModule);
            return;
        }
        if (moduleDetailView != null && moduleDetailView.isVisible() && currentModule != null) {
            showModuleDetail(currentModule);
            return;
        }
        if (categoryDetailView != null && categoryDetailView.isVisible() && currentCategory != null) {
            showCategoryDetail(currentCategory);
            return;
        }
        buildCategoryCards();
        switchView(categoriesView);
    }
    private void applyLanguageNow() {
        if (isApplyingLanguage) return;
        isApplyingLanguage = true;
        try {
            if (heroBand != null) {
                applyOrientation(heroBand);
                translateNodeTree(heroBand);
            }
            if (blogContentHost != null) {
                applyOrientation(blogContentHost);
                translateNodeTree(blogContentHost);
            }
            // Rafraîchir les articles pour appliquer la traduction dynamique
            if (moduleDetailView != null && moduleDetailView.isVisible() && currentArticles != null) {
                renderArticles(currentArticles);
            }
        } finally {
            isApplyingLanguage = false;
        }
    }

    private void translateDynamicLabeled(Label label, String originalText) {
        if (originalText == null || originalText.isBlank()) return;
        if (activeLanguage == null || "fr".equalsIgnoreCase(activeLanguage.code())) {
            label.setText(originalText);
            return;
        }
        int sessionId = activeTranslationSession;
        pendingTranslations.incrementAndGet();
        AppLanguage targetLanguage = activeLanguage;
        String expectedLang = targetLanguage.code();
        
        translationExecutor.submit(() -> {
            LibreTranslateService.TranslationOutcome outcome =
                    libreTranslateService.translateAutoDetailed(originalText, targetLanguage);
            Platform.runLater(() -> {
                if (sessionId == activeTranslationSession
                        && outcome.success()
                        && outcome.translatedText() != null
                        && activeLanguage != null
                        && expectedLang.equalsIgnoreCase(activeLanguage.code())) {
                    label.setText(outcome.translatedText());
                }
                onTranslationTaskDone(sessionId, outcome.success(), outcome.provider(), outcome.error());
            });
        });
    }

    private void translateDynamicTextInput(TextInputControl input, String originalText) {
        if (input == null || originalText == null || originalText.isBlank()) return;
        if (activeLanguage == null || "fr".equalsIgnoreCase(activeLanguage.code())) {
            input.setText(originalText);
            return;
        }
        int sessionId = activeTranslationSession;
        pendingTranslations.incrementAndGet();
        AppLanguage targetLanguage = activeLanguage;
        String expectedLang = targetLanguage.code();

        translationExecutor.submit(() -> {
            LibreTranslateService.TranslationOutcome outcome =
                    libreTranslateService.translateAutoDetailed(originalText, targetLanguage);
            Platform.runLater(() -> {
                if (sessionId == activeTranslationSession
                        && outcome.success()
                        && outcome.translatedText() != null
                        && activeLanguage != null
                        && expectedLang.equalsIgnoreCase(activeLanguage.code())) {
                    input.setText(outcome.translatedText());
                    if (input instanceof TextArea area) {
                        area.positionCaret(0);
                        area.setScrollTop(0);
                    }
                }
                onTranslationTaskDone(sessionId, outcome.success(), outcome.provider(), outcome.error());
            });
        });
    }

    private void applyOrientation(Parent root) {
        if (root == null) {
            return;
        }
        root.setNodeOrientation(activeLanguage != null && activeLanguage.rtl()
                ? NodeOrientation.RIGHT_TO_LEFT
                : NodeOrientation.LEFT_TO_RIGHT);
    }

    private void translateNodeTree(Node root) {
        if (root == null) {
            return;
        }
        if (root instanceof Labeled labeled) {
            translateLabeled(labeled);
        }
        if (root instanceof TextInputControl input) {
            translatePrompt(input);
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                translateNodeTree(child);
            }
        }
    }

    private void translateLabeled(Labeled labeled) {
        String current = labeled.getText();
        if (current == null || current.isBlank()) {
            return;
        }
        baseLabeledText.putIfAbsent(labeled, current);
        String source = baseLabeledText.get(labeled);
        if (activeLanguage == null || "fr".equalsIgnoreCase(activeLanguage.code())) {
            labeled.setText(source);
            return;
        }
        int sessionId = activeTranslationSession;
        pendingTranslations.incrementAndGet();
        AppLanguage targetLanguage = activeLanguage;
        String expectedLang = targetLanguage.code();
        translationExecutor.submit(() -> {
            LibreTranslateService.TranslationOutcome outcome =
                    libreTranslateService.translateFromFrenchDetailed(source, targetLanguage);
            Platform.runLater(() -> {
                boolean applied = false;
                String translated = outcome.translatedText();
                if (sessionId == activeTranslationSession
                        && translated != null && !translated.isBlank()
                        && activeLanguage != null
                        && expectedLang.equalsIgnoreCase(activeLanguage.code())) {
                    labeled.setText(translated);
                    applied = outcome.success();
                }
                onTranslationTaskDone(sessionId, applied, outcome.provider(), outcome.error());
            });
        });
    }

    private void translatePrompt(TextInputControl input) {
        String prompt = input.getPromptText();
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        basePromptText.putIfAbsent(input, prompt);
        String source = basePromptText.get(input);
        if (activeLanguage == null || "fr".equalsIgnoreCase(activeLanguage.code())) {
            input.setPromptText(source);
            return;
        }
        int sessionId = activeTranslationSession;
        pendingTranslations.incrementAndGet();
        AppLanguage targetLanguage = activeLanguage;
        String expectedLang = targetLanguage.code();
        translationExecutor.submit(() -> {
            LibreTranslateService.TranslationOutcome outcome =
                    libreTranslateService.translateFromFrenchDetailed(source, targetLanguage);
            Platform.runLater(() -> {
                boolean applied = false;
                String translated = outcome.translatedText();
                if (sessionId == activeTranslationSession
                        && translated != null && !translated.isBlank()
                        && activeLanguage != null
                        && expectedLang.equalsIgnoreCase(activeLanguage.code())) {
                    input.setPromptText(translated);
                    applied = outcome.success();
                }
                onTranslationTaskDone(sessionId, applied, outcome.provider(), outcome.error());
            });
        });
    }

    private void onTranslationTaskDone(int sessionId, boolean success, String provider, String error) {
        if (sessionId != activeTranslationSession) {
            return;
        }
        if (success) {
            succeededTranslations.incrementAndGet();
        } else {
            failedTranslations.incrementAndGet();
            if (provider != null && !provider.isBlank()) {
                firstFailureProvider.compareAndSet("", provider);
            }
            if (error != null && !error.isBlank()) {
                firstFailureError.compareAndSet("", error);
            }
        }
        int remaining = pendingTranslations.decrementAndGet();
        if (remaining > 0) {
            return;
        }
        int ok = succeededTranslations.get();
        int ko = failedTranslations.get();
        if (ok > 0 && ko == 0) {
            setTranslationStatus("✅ Traduction réussie (" + ok + " éléments).", "success");
        } else if (ok > 0) {
            setTranslationStatus("⚠ Traduction partielle (" + ok + " ok, " + ko + " échecs).", "fail");
        } else {
            String p = firstFailureProvider.get();
            if (p == null || p.isBlank()) {
                p = "LibreTranslate";
            }
            String err = firstFailureError.get();
            if (err != null && !err.isBlank()) {
                setTranslationStatus("❌ Traduction échouée (" + p + "): " + shorten(err, 70), "fail");
            } else {
                setTranslationStatus("❌ Traduction échouée (" + p + " indisponible).", "fail");
            }
        }
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max - 3) + "...";
    }

    private void setTranslationStatus(String message, String kind) {
        if (translationStatusLabel == null) {
            return;
        }
        translationStatusLabel.setText(message != null ? message : "");
        translationStatusLabel.setVisible(message != null && !message.isBlank());
        translationStatusLabel.setManaged(message != null && !message.isBlank());
        translationStatusLabel.getStyleClass().removeAll(
                "blog-translation-status-loading",
                "blog-translation-status-success",
                "blog-translation-status-fail"
        );
        if ("success".equals(kind)) {
            translationStatusLabel.getStyleClass().add("blog-translation-status-success");
        } else if ("fail".equals(kind)) {
            translationStatusLabel.getStyleClass().add("blog-translation-status-fail");
        } else {
            translationStatusLabel.getStyleClass().add("blog-translation-status-loading");
        }
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

    /* ═══════════════════ VUE : BIBLIOTHÈQUE ═══════════════════ */

    public void showLibrary() {
        switchView(libraryView);
        
        // Charger les images (Magnifying glass et Girl reading)
        try {
            if (libraryIconImg != null) {
                libraryIconImg.setImage(new Image(getClass().getResourceAsStream("/images/blog/search_heart.png")));
            }
            if (libraryGirlImg != null) {
                libraryGirlImg.setImage(new Image(getClass().getResourceAsStream("/images/blog/girl_reading.png")));
            }
        } catch (Exception e) {
            System.err.println("Erreur chargement images bibliothèque: " + e.getMessage());
        }
        
        onLibraryFilterTous();
    }

    @FXML
    private void onLibraryFilterTous() {
        setActiveFilter(libFilterTous);
        if (librarySectionTitle != null) librarySectionTitle.setText("Tous les contenus");
        loadLibraryContent("tous");
    }

    @FXML
    private void onLibraryFilterSaved() {
        setActiveFilter(libFilterSaved);
        if (librarySectionTitle != null) librarySectionTitle.setText("Contenus enregistrés");
        loadLibraryContent("enregistre");
    }

    @FXML
    private void onLibraryFilterFavs() {
        setActiveFilter(libFilterFavs);
        if (librarySectionTitle != null) librarySectionTitle.setText("Mes Favoris");
        loadLibraryContent("favoris");
    }

    @FXML
    private void onLibraryFilterLib() {
        setActiveFilter(libFilterLib);
        if (librarySectionTitle != null) librarySectionTitle.setText("Ma Bibliothèque");
        loadLibraryContent("bibliotheque");
    }

    private void setActiveFilter(Button activeBtn) {
        List<Button> btns = Arrays.asList(libFilterTous, libFilterSaved, libFilterFavs, libFilterLib);
        if (btns.contains(null)) return;
        for (Button b : btns) {
            b.getStyleClass().remove("blog-lib-filter-active");
        }
        if (activeBtn != null) {
            activeBtn.getStyleClass().add("blog-lib-filter-active");
        }
    }

    private void loadLibraryContent(String type) {
        if (libraryContentPane == null) return;
        libraryContentPane.getChildren().clear();
        
        // On affiche tous les modules en guise de démonstration
        try {
            List<ModuleContent> all = moduleService.findAll();
            for (ModuleContent m : all) {
                VBox card = buildModuleCard(m);
                libraryContentPane.getChildren().add(card);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            libraryContentPane.getChildren().add(new Label("Erreur de chargement de la bibliothèque."));
        }
    }

    private void initDictionaryPopup() {
        dictionaryPopupWord.getStyleClass().add("blog-art-dictionary-word");
        dictionaryPopupDefinition.getStyleClass().add("blog-art-dictionary-definition");
        dictionaryPopupDefinition.setWrapText(true);
        dictionaryPopupDefinition.setMaxWidth(300);
        dictionaryPopupBox.getStyleClass().add("blog-art-dictionary-box");
        dictionaryPopupBox.setPadding(new Insets(8, 10, 8, 10));
        // Force a pastel framed look even if popup CSS is not inherited.
        dictionaryPopupBox.setStyle(
                "-fx-background-color: #f7ecff;"
                        + "-fx-background-radius: 14;"
                        + "-fx-border-color: #d7b8f5;"
                        + "-fx-border-radius: 14;"
                        + "-fx-border-width: 1.4;"
                        + "-fx-effect: dropshadow(gaussian, rgba(109,40,217,0.18), 12, 0.18, 0, 2);"
                        + "-fx-padding: 10 12 10 12;"
        );
        dictionaryPopupWord.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #5b3e8d;");
        dictionaryPopupDefinition.setStyle("-fx-font-size: 12px; -fx-text-fill: #3f3a57;");
        dictionaryPopupBox.getChildren().setAll(dictionaryPopupWord, dictionaryPopupDefinition);
        dictionaryPopup.getContent().setAll(dictionaryPopupBox);
        dictionaryPopup.setAutoHide(false);
    }

    private void setupDictionarySupport(TextInputControl area) {
        if (area == null) {
            return;
        }
        area.selectedTextProperty().addListener((obs, oldSel, newSel) -> {
            String selectedWord = normalizeSelectedWord(newSel);
            PauseTransition debounce = dictionaryDebounceByControl.computeIfAbsent(area, k -> new PauseTransition(Duration.millis(360)));
            debounce.setOnFinished(event -> {
                if (selectedWord.isBlank()) {
                    hideDictionaryPopup();
                    return;
                }
                loadDefinitionForSelection(area, selectedWord);
            });
            debounce.playFromStart();
        });
        area.setOnMouseReleased(evt ->
                dictionaryLastAnchor.put(area, new double[]{evt.getScreenX(), evt.getScreenY()}));
        area.setOnKeyReleased(evt -> {
            var bounds = area.localToScreen(area.getBoundsInLocal());
            if (bounds != null) {
                dictionaryLastAnchor.put(area, new double[]{
                        bounds.getMinX() + (bounds.getWidth() / 2.0),
                        bounds.getMinY() + 22
                });
            }
        });
    }

    private String normalizeSelectedWord(String rawSelection) {
        if (rawSelection == null) return "";
        String value = rawSelection.trim();
        if (value.isBlank() || value.length() > 40) return "";
        value = value.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
        if (value.isBlank() || value.length() < 2 || value.contains(" ")) return "";
        return value;
    }

    private void loadDefinitionForSelection(TextInputControl area, String word) {
        AppLanguage lang = activeLanguage != null ? activeLanguage : AppLanguage.FR;
        String langCode = lang.code();
        String cacheKey = langCode + ":" + word.toLowerCase(Locale.ROOT);
        String cached = dictionaryDefinitionCache.get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            showDictionaryPopup(area, word, cached);
            return;
        }

        showDictionaryPopup(area, word, "Chargement...");
        translationExecutor.submit(() -> {
            String definition = fetchDefinitionWithFallback(word, langCode);
            if (definition == null || definition.isBlank()) {
                definition = "Aucune définition trouvée.";
            } else {
                definition = simplifyDefinitionFiveWords(definition);
                dictionaryDefinitionCache.put(cacheKey, definition);
            }
            String finalDefinition = definition;
            Platform.runLater(() -> showDictionaryPopup(area, word, finalDefinition));
        });
    }

    private String fetchDefinitionWithFallback(String word, String targetLanguage) {
        // 1) Prefer real dictionary definition in English, then translate.
        try {
            String wordForEn = word;
            if (!"en".equalsIgnoreCase(targetLanguage)) {
                LibreTranslateService.TranslationOutcome toEn =
                        libreTranslateService.translateAutoDetailed(word, AppLanguage.EN);
                if (toEn.success() && toEn.translatedText() != null && !toEn.translatedText().isBlank()) {
                    wordForEn = toEn.translatedText().trim();
                }
            }
            String enDef = wikipediaService.getEnglishDictionaryDefinition(wordForEn);
            if (enDef != null && !enDef.isBlank()) {
                if (!"en".equalsIgnoreCase(targetLanguage)) {
                    AppLanguage target = AppLanguage.fromCode(targetLanguage);
                    LibreTranslateService.TranslationOutcome tr =
                            libreTranslateService.translateAutoDetailed(enDef, target);
                    if (tr.success() && tr.translatedText() != null && !tr.translatedText().isBlank()) {
                        return tr.translatedText();
                    }
                }
                return enDef;
            }
        } catch (Exception ignored) {
        }

        // 2) Fallback to Wikipedia summary.
        String[] langs = new String[]{targetLanguage, "fr", "en"};
        for (String candidate : langs) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                String summary = wikipediaService.getSummaryExtract(word, candidate);
                if (summary == null || summary.isBlank()) continue;
                if (!candidate.equalsIgnoreCase(targetLanguage)) {
                    AppLanguage target = AppLanguage.fromCode(targetLanguage);
                    LibreTranslateService.TranslationOutcome tr =
                            libreTranslateService.translateAutoDetailed(summary, target);
                    if (tr.success() && tr.translatedText() != null && !tr.translatedText().isBlank()) {
                        return tr.translatedText();
                    }
                }
                return summary;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private void showDictionaryPopup(TextInputControl area, String word, String definition) {
        if (area == null || area.getScene() == null || area.getScene().getWindow() == null) {
            return;
        }
        if (word == null || word.isBlank()) {
            hideDictionaryPopup();
            return;
        }
        dictionaryPopupWord.setText("Mot : " + word);
        dictionaryPopupDefinition.setText(definition != null ? definition : "");

        double[] anchor = dictionaryLastAnchor.get(area);
        double anchorX;
        double anchorY;
        if (anchor != null) {
            anchorX = anchor[0];
            anchorY = anchor[1];
        } else {
            var b = area.localToScreen(area.getBoundsInLocal());
            anchorX = b != null ? b.getMinX() + b.getWidth() / 2.0 : 0;
            anchorY = b != null ? b.getMinY() + 30 : 0;
        }

        if (!dictionaryPopup.isShowing()) {
            dictionaryPopup.show(area.getScene().getWindow());
        }
        dictionaryPopupBox.applyCss();
        dictionaryPopupBox.layout();
        double w = dictionaryPopupBox.prefWidth(-1);
        double h = dictionaryPopupBox.prefHeight(-1);
        var win = area.getScene().getWindow();
        double minX = win.getX() + 8;
        double maxX = win.getX() + Math.max(8, win.getWidth() - w - 8);
        double x = Math.max(minX, Math.min(anchorX - (w / 2.0), maxX));

        // Prefer above selected word, fallback below if not enough space.
        double yAbove = anchorY - h - 12;
        double minY = win.getY() + 8;
        double maxY = win.getY() + Math.max(8, win.getHeight() - h - 8);
        double y = yAbove >= minY ? yAbove : (anchorY + 14);
        y = Math.max(minY, Math.min(y, maxY));

        dictionaryPopup.setX(x);
        dictionaryPopup.setY(y);
    }

    private void hideDictionaryPopup() {
        if (dictionaryPopup.isShowing()) {
            dictionaryPopup.hide();
        }
    }

    private String simplifyDefinitionFiveWords(String definition) {
        if (definition == null || definition.isBlank()) {
            return "";
        }
        String cleaned = definition
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s'-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "";
        }

        // Keep the most meaningful clause when possible.
        String lower = cleaned.toLowerCase(Locale.ROOT);
        int idx = -1;
        String[] markers = {" is ", " are ", " means ", " est ", " sont ", " signifie ", " c est ", " c'est "};
        for (String marker : markers) {
            int found = lower.indexOf(marker);
            if (found > 0) {
                idx = found + marker.length();
                break;
            }
        }
        String core = (idx > 0 && idx < cleaned.length()) ? cleaned.substring(idx).trim() : cleaned;
        core = core.replaceAll("^(a|an|the|un|une|des|le|la|les)\\s+", "");
        if (core.isBlank()) {
            core = cleaned;
        }

        String[] words = core.split(" ");
        int take = Math.min(5, words.length);
        return String.join(" ", Arrays.copyOf(words, take));
    }

    private void enableAutoGrowTextArea(TextArea area, int minRows) {
        if (area == null) return;
        area.setPrefRowCount(minRows);
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(Region.USE_PREF_SIZE);
        area.textProperty().addListener((obs, oldV, newV) -> updateAutoGrowTextAreaHeight(area, minRows));
        area.widthProperty().addListener((obs, oldV, newV) -> updateAutoGrowTextAreaHeight(area, minRows));
        Platform.runLater(() -> updateAutoGrowTextAreaHeight(area, minRows));
    }

    private void updateAutoGrowTextAreaHeight(TextArea area, int minRows) {
        if (area == null) return;
        double width = area.getWidth() > 0 ? area.getWidth() : area.prefWidth(-1);
        if (width <= 0) return;

        Text helper = new Text(area.getText() == null || area.getText().isBlank() ? " " : area.getText());
        helper.setFont(area.getFont());
        // Approximates TextArea inner text width accounting for insets + scrollbar gutter.
        helper.setWrappingWidth(Math.max(80, width - 24));

        double fontSize = area.getFont() != null ? area.getFont().getSize() : 13;
        double lineHeight = Math.max(16, fontSize * 1.35);
        double minHeight = (lineHeight * minRows) + 18;
        // Extra buffer prevents residual internal scrolling on long wrapped paragraphs.
        double targetHeight = Math.max(minHeight, helper.getLayoutBounds().getHeight() + 34);

        area.setPrefHeight(targetHeight);
        area.setMinHeight(targetHeight);
        area.setMaxHeight(targetHeight);
    }

    @FXML
    private void onOpenWikipedia() {
        if (currentModule == null || wikipediaLink == null) return;
        
        // On ajoute systématiquement "autisme" pour contextualiser la recherche Wikipedia
        String moduleTitle = currentModule.getTitre();
        String query = moduleTitle + " autisme";
        
        wikipediaLink.setCursor(javafx.scene.Cursor.WAIT);
        
        Thread thread = new Thread(() -> {
            try {
                String url = wikipediaService.getWikipediaUrl(query);
                Platform.runLater(() -> {
                    wikipediaLink.setCursor(javafx.scene.Cursor.HAND);
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(url));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> wikipediaLink.setCursor(javafx.scene.Cursor.HAND));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
