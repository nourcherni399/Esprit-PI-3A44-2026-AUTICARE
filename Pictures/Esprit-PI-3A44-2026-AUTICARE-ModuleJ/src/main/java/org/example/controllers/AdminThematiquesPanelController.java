package org.example.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.MainApp;
import org.example.models.Event;
import org.example.models.EventStatus;
import org.example.models.Thematique;
import org.example.services.EventService;
import org.example.services.ThematiqueService;
import org.example.utils.AppState;
import org.example.utils.FxInputConstraints;
import org.example.utils.ThematiqueImageStorage;
import org.example.utils.UserPublicAssets;

import javafx.fxml.FXML;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Liste des thématiques, statistiques et formulaire création / édition (superposition).
 */
public class AdminThematiquesPanelController {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9_]{2,32}");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    /** Aligné sur la colonne {@code VARCHAR(180)}. */
    private static final int NOM_MIN = 2;
    private static final int NOM_MAX = 180;
    private static final int DESC_MIN = 5;
    private static final int DESC_MAX = 10_000;
    private static final int SOUS_TITRE_MIN = 2;
    private static final int SOUS_TITRE_MAX = 180;
    private static final int ORDRE_MIN = 0;
    private static final int ORDRE_MAX = 999_999;
    private static final long IMAGE_MAX_BYTES = 15L * 1024 * 1024;

    /** Couleurs des camemberts (alignées sur la maquette : vert = avec événements, gris = sans). */
    private static final String PIE_GREEN_AVEC = "#22c55e";
    private static final String PIE_GRAY_SANS = "#9ca3af";

    /** Pastilles pour « Événements par thématique » : rotation comme les autres cartes KPI. */
    private static final String[] STAT_LIST_BADGE_VARIANTS = {
            "admin-stat-num-badge-blue",
            "admin-stat-num-badge-green",
            "admin-stat-num-badge-orange",
            "admin-stat-num-badge-purple",
            "admin-stat-num-badge-grey",
            "admin-stat-num-badge-red",
    };
    private static final String PIE_GRAY_EMPTY = "#d1d5db";
    /** Palette pour « événements par thématique » (slices distinctes). */
    private static final String[] PIE_EVENT_PALETTE = {
            "#22c55e", "#3b82f6", "#a855f7", "#f97316", "#14b8a6",
            "#ec4899", "#eab308", "#6366f1", "#84cc16", "#f43f5e"
    };

    /** Icônes Material (viewBox 0 0 24 24), rendues via {@link SVGPath}. */
    private static final String SVG_VISIBILITY =
            "M12 6.5c-5.79 0-10.28 3.35-12 8 1.72 4.65 6.21 8 12 8s10.28-3.35 12-8c-1.72-4.65-6.21-8-12-8zm0 12c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4zm0-6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z";
    private static final String SVG_EDIT =
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
    private static final String SVG_DELETE =
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";

    private static final String[] SUGGESTED_HEX = {
            "#9333ea", "#db2777", "#ec4899", "#fb923c", "#facc15", "#fda4af",
            "#f43f5e", "#14b8a6", "#86efac", "#A7C7E7"
    };

    private final ThematiqueService thematiqueService = new ThematiqueService();
    private final EventService eventService = new EventService();

    private enum VisibleLayer {
        LIST, DETAIL, FORM
    }

    @FXML
    private VBox listLayer;
    @FXML
    private VBox detailLayer;
    @FXML
    private ScrollPane thematiquesScrollRoot;
    @FXML
    private StackPane thematiquesStackPane;
    @FXML
    private AnchorPane formLayerHost;
    @FXML
    private VBox formLayer;
    @FXML
    private FlowPane cardsFlow;
    @FXML
    private TextField searchField;
    @FXML
    private PieChart pieThematiquesChart;
    @FXML
    private PieChart pieEventsParThematiqueChart;
    @FXML
    private VBox statListEventsBox;
    @FXML
    private VBox statThematiquesTypeBox;
    @FXML
    private Label statBigTotalThem;
    @FXML
    private Label statBigAvecThem;
    @FXML
    private Label statBigTotalEvtKpi;

    @FXML
    private Label formPageTitle;
    @FXML
    private TextField formNom;
    @FXML
    private TextField formCode;
    @FXML
    private TextArea formDescription;
    @FXML
    private TextField formCouleur;
    @FXML
    private TextField formSousTitre;
    @FXML
    private FlowPane colorSwatches;
    @FXML
    private TextField formOrdre;
    @FXML
    private CheckBox formVisible;
    @FXML
    private ComboBox<String> formPublicCible;
    @FXML
    private ComboBox<String> formNiveau;

    @FXML
    private StackPane detailHeroStack;
    @FXML
    private ImageView detailHeroImage;
    @FXML
    private Region detailColorSwatch;
    @FXML
    private Region detailColorDot;
    @FXML
    private Label detailTitleLabel;
    @FXML
    private Label detailMetaLabel;
    @FXML
    private Label detailSousTitreLabel;
    @FXML
    private Label detailDescriptionLabel;
    @FXML
    private Label detailPublicLabel;
    @FXML
    private Label detailNiveauLabel;
    @FXML
    private Label detailColorHexLabel;
    @FXML
    private TableView<Event> detailEventsTable;

    /** Thématique affichée dans la fiche détail (caps 3–4). */
    private Thematique detailShownThematique;
    /** Injecté par {@link org.example.controllers.AdminUsersController} : bascule vers Événements + formulaire. */
    private Consumer<String> navigateNewEventForThematique;

    private List<Thematique> allThematiques = new ArrayList<>();
    /** Filtre la liste pendant la frappe (évite d’avoir à cliquer sur « Rechercher »). */
    private Timeline searchDebounceTimeline;
    private int editingId = -1;
    private File pendingImageFile;
    private String existingImageRelative;

    @FXML
    public void initialize() {
        if (formPublicCible != null) {
            formPublicCible.setPromptText("Choisir un public");
            formPublicCible.setItems(FXCollections.observableArrayList(
                    "Enfant", "Parent", "Médecin", "Éducateur", "Aidant", "Autre"));
        }
        if (formNiveau != null) {
            formNiveau.setPromptText("Choisir un niveau");
            formNiveau.setItems(FXCollections.observableArrayList(
                    "Débutant", "Intermédiaire", "Avancé"));
        }
        buildColorSwatches();
        setupDetailEventsTable();
        setupDetailHeroHost();
        clearStatPlaceholders();
        setupStatsPieCharts();
        if (searchField != null) {
            searchField.setOnAction(e -> onApplySearch());
            searchDebounceTimeline = new Timeline(new KeyFrame(Duration.millis(200), ev -> onApplySearch()));
            searchDebounceTimeline.setCycleCount(1);
            searchField.textProperty().addListener((obs, prev, cur) -> {
                searchDebounceTimeline.stop();
                searchDebounceTimeline.playFromStart();
            });
        }
        setupThematiqueFormInputConstraints();
        wireScrollContentFullWidth();
    }

    private void wireScrollContentFullWidth() {
        if (thematiquesScrollRoot != null && thematiquesStackPane != null) {
            thematiquesStackPane.minWidthProperty().bind(thematiquesScrollRoot.widthProperty());
            thematiquesStackPane.prefWidthProperty().bind(thematiquesScrollRoot.widthProperty());
        }
        if (formLayerHost != null && thematiquesStackPane != null) {
            formLayerHost.minWidthProperty().bind(thematiquesStackPane.widthProperty());
            formLayerHost.prefWidthProperty().bind(thematiquesStackPane.widthProperty());
        }
        if (formLayer != null && formLayerHost != null) {
            final double formMax = 1320.0;
            final double margin = 48.0;
            var formW = Bindings.createDoubleBinding(
                    () -> {
                        double hw = formLayerHost.getWidth();
                        if (hw <= 0) {
                            return 680.0;
                        }
                        return Math.max(680.0, Math.min(formMax, hw - margin));
                    },
                    formLayerHost.widthProperty());
            formLayer.prefWidthProperty().bind(formW);
            formLayer.maxWidthProperty().bind(formW);
            formLayer.setMinWidth(680);
        }
    }

    /** Limites de saisie alignées sur {@link #validateForm()}. */
    private void setupThematiqueFormInputConstraints() {
        if (formNom != null) {
            formNom.setTextFormatter(FxInputConstraints.maxLength(NOM_MAX));
        }
        if (formCode != null) {
            formCode.setTextFormatter(FxInputConstraints.asciiIdentifierMax(32));
        }
        if (formDescription != null) {
            formDescription.setTextFormatter(FxInputConstraints.maxLength(DESC_MAX));
        }
        if (formCouleur != null) {
            formCouleur.setTextFormatter(FxInputConstraints.maxLength(8));
        }
        if (formSousTitre != null) {
            formSousTitre.setTextFormatter(FxInputConstraints.maxLength(SOUS_TITRE_MAX));
        }
        if (formOrdre != null) {
            formOrdre.setTextFormatter(FxInputConstraints.unsignedIntDigits(6));
        }
    }

    private void setupDetailHeroHost() {
        if (detailHeroStack == null || detailHeroImage == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        clip.widthProperty().bind(detailHeroStack.widthProperty());
        clip.heightProperty().bind(detailHeroStack.heightProperty());
        detailHeroStack.setClip(clip);
        detailHeroImage.fitWidthProperty().bind(detailHeroStack.widthProperty());
        detailHeroImage.setPreserveRatio(true);
        detailHeroImage.setSmooth(true);
        StackPane.setAlignment(detailHeroImage, Pos.CENTER);
    }

    public void setNavigateToNewEventForThematique(Consumer<String> callback) {
        this.navigateNewEventForThematique = callback;
    }

    private void setupStatsPieCharts() {
        if (pieThematiquesChart != null) {
            pieThematiquesChart.setAnimated(false);
            pieThematiquesChart.setClockwise(true);
            pieThematiquesChart.setStartAngle(90);
            /* Pas de texte sur les parts (évite chevauchement / halo bleu sur fond coloré). Légende uniquement. */
            pieThematiquesChart.setLabelsVisible(false);
            pieThematiquesChart.setTitle("");
            pieThematiquesChart.setMinWidth(200);
            pieThematiquesChart.setMinHeight(190);
            pieThematiquesChart.setPrefHeight(210);
            pieThematiquesChart.getStyleClass().add("admin-thematiques-pie-stats");
        }
        if (pieEventsParThematiqueChart != null) {
            pieEventsParThematiqueChart.setAnimated(false);
            pieEventsParThematiqueChart.setClockwise(true);
            pieEventsParThematiqueChart.setStartAngle(90);
            pieEventsParThematiqueChart.setLabelsVisible(false);
            pieEventsParThematiqueChart.setTitle("");
            /* Même taille minimale que le 1er graphique — sinon le disque peut ne pas s’afficher. */
            pieEventsParThematiqueChart.setMinWidth(200);
            pieEventsParThematiqueChart.setMinHeight(190);
            pieEventsParThematiqueChart.setPrefHeight(210);
            pieEventsParThematiqueChart.getStyleClass().add("admin-thematiques-pie-events");
        }
        Platform.runLater(() -> {
            centerPieChartLegend(pieThematiquesChart);
            centerPieChartLegend(pieEventsParThematiqueChart);
        });
    }

    /** Même rendu que le 1er camembert : légende centrée en bas (évite l’alignement à droite avec une seule ligne). */
    private static void centerPieChartLegend(PieChart chart) {
        if (chart == null) {
            return;
        }
        Node legendNode = chart.lookup(".chart-legend");
        if (!(legendNode instanceof TilePane legend)) {
            return;
        }
        legend.setAlignment(Pos.CENTER);
        legend.setMaxWidth(Double.MAX_VALUE);
        /* Pas d’applyCss() : sinon les pastilles de légende repassent à l’orange Modena
         * et ne correspondent plus aux couleurs des parts (voir EventStatsPieCharts.syncLegendColors). */
        chart.layout();
        double w = chart.getWidth();
        if (w > 0) {
            legend.setPrefWidth(w);
        }
        chart.requestLayout();
    }

    private void clearStatPlaceholders() {
        if (pieThematiquesChart != null) {
            pieThematiquesChart.setData(FXCollections.observableArrayList());
        }
        if (pieEventsParThematiqueChart != null) {
            pieEventsParThematiqueChart.setData(FXCollections.observableArrayList());
        }
        if (statThematiquesTypeBox != null) {
            statThematiquesTypeBox.getChildren().clear();
        }
        if (statBigTotalThem != null) {
            statBigTotalThem.setText("");
        }
        if (statBigAvecThem != null) {
            statBigAvecThem.setText("");
        }
        if (statBigTotalEvtKpi != null) {
            statBigTotalEvtKpi.setText("");
        }
    }

    private void setupDetailEventsTable() {
        if (detailEventsTable == null) {
            return;
        }
        TableColumn<Event, String> colTitre = new TableColumn<>("Titre");
        colTitre.setPrefWidth(260);
        colTitre.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getTitre()));
        TableColumn<Event, String> colDate = new TableColumn<>("Date");
        colDate.setPrefWidth(150);
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
        colDate.setCellValueFactory(c -> {
            Event ev = c.getValue();
            if (ev.getDateDebut() == null) {
                return new ReadOnlyObjectWrapper<>("");
            }
            return new ReadOnlyObjectWrapper<>(df.format(ev.getDateDebut()));
        });
        TableColumn<Event, String> colLieu = new TableColumn<>("Lieu");
        colLieu.setPrefWidth(200);
        colLieu.setCellValueFactory(c -> {
            String l = c.getValue().getLieu();
            return new ReadOnlyObjectWrapper<>(l != null ? l : "");
        });
        applyCenterAlignedCells(colTitre);
        applyCenterAlignedCells(colDate);
        applyCenterAlignedCells(colLieu);
        detailEventsTable.getColumns().setAll(colTitre, colDate, colLieu);
        detailEventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        detailEventsTable.setPlaceholder(new Label("Aucun événement pour cette thématique."));
    }

    private static void applyCenterAlignedCells(TableColumn<Event, String> column) {
        column.setCellFactory(col -> new TableCell<Event, String>() {
            {
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });
    }

    private void buildColorSwatches() {
        if (colorSwatches == null) {
            return;
        }
        colorSwatches.getChildren().clear();
        for (String hex : SUGGESTED_HEX) {
            Circle c = new Circle(14);
            c.setStyle("-fx-fill: " + hex + ";");
            c.getStyleClass().add("admin-thematiques-swatch");
            c.setOnMouseClicked(e -> {
                if (formCouleur != null) {
                    formCouleur.setText(hex);
                }
            });
            colorSwatches.getChildren().add(c);
        }
    }

    /** Appelé depuis {@link AdminUsersController} après chargement du panneau. */
    public void refreshFromDb() {
        try {
            allThematiques = thematiqueService.findAll();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Base de données", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            allThematiques = new ArrayList<>();
        }
        applySearch();
        updateStats();
        if (detailShownThematique != null && detailLayer != null && detailLayer.isVisible()) {
            try {
                int id = detailShownThematique.getId();
                Optional<Thematique> refreshed = thematiqueService.findById(id);
                if (refreshed.isEmpty()) {
                    detailShownThematique = null;
                    showLayer(VisibleLayer.LIST);
                } else {
                    detailShownThematique = refreshed.get();
                    populateDetailView(detailShownThematique);
                }
            } catch (SQLException e) {
                alert(Alert.AlertType.ERROR, "Base de données", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }
    }

    /** Affiche explicitement la vue liste (utilisé par le menu latéral « Thématiques »). */
    public void showListView() {
        detailShownThematique = null;
        editingId = -1;
        pendingImageFile = null;
        showLayer(VisibleLayer.LIST);
    }

    @FXML
    public void onApplySearch() {
        applySearch();
        updateStats();
    }

    private void applySearch() {
        if (cardsFlow == null) {
            return;
        }
        String q = currentSearchQueryNormalized();
        cardsFlow.getChildren().clear();
        for (Thematique t : allThematiques) {
            if (!matchesFilter(t, q)) {
                continue;
            }
            cardsFlow.getChildren().add(buildCard(t));
        }
    }

    private String currentSearchQueryNormalized() {
        if (searchField == null || searchField.getText() == null) {
            return "";
        }
        return normalizeForSearch(searchField.getText());
    }

    /**
     * Filtre sur le nom affiché et le code uniquement (pas la description : trop de faux positifs).
     */
    private static boolean matchesFilter(Thematique t, String q) {
        if (q.isEmpty()) {
            return true;
        }
        return contains(t.getNom(), q) || contains(t.getCode(), q);
    }

    /** Compare insensible à la casse et aux accents (utile pour le français). */
    private static boolean contains(String s, String qNormalized) {
        if (s == null) {
            return false;
        }
        return normalizeForSearch(s).contains(qNormalized);
    }

    private static String normalizeForSearch(String s) {
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.FRENCH);
    }

    private int eventCount(Thematique t) {
        try {
            return thematiqueService.countEvenementsForNomThematique(t.getNom());
        } catch (SQLException e) {
            return 0;
        }
    }

    private Region buildCard(Thematique t) {
        VBox card = new VBox(8);
        card.getStyleClass().add("admin-thematiques-card");
        String accent = normalizeHex(t.getCouleur());
        card.setStyle("-fx-border-width: 0 0 0 4; -fx-border-color: " + accent + ";");

        StackPane thumbWrap = new StackPane();
        thumbWrap.setMinSize(260, 130);
        thumbWrap.setPrefSize(260, 130);
        thumbWrap.setMaxSize(260, 130);
        thumbWrap.getStyleClass().add("admin-thematiques-thumb-wrap");
        Rectangle thumbClip = new Rectangle(260, 130);
        thumbClip.setArcWidth(10);
        thumbClip.setArcHeight(10);
        thumbWrap.setClip(thumbClip);
        ImageView iv = new ImageView();
        iv.setPreserveRatio(true);
        iv.fitWidthProperty().bind(thumbWrap.widthProperty());
        iv.setSmooth(true);
        StackPane.setAlignment(iv, Pos.CENTER);
        loadThematiqueImageCard(iv, t.getImageChemin());
        thumbWrap.getChildren().add(iv);

        Label title = new Label(t.getNom());
        title.getStyleClass().add("admin-thematiques-card-title");

        // Pastille = ordre d’affichage (champ « Ordre » du formulaire)
        Label badge = new Label(String.valueOf(t.getOrdreAffichage()));
        badge.getStyleClass().add("admin-thematiques-badge");
        javafx.scene.control.Tooltip.install(badge, new javafx.scene.control.Tooltip("Ordre d'affichage"));
        HBox titleRow = new HBox(8);
        titleRow.getStyleClass().add("admin-thematiques-card-title-row");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        titleRow.getChildren().addAll(title, spacer, badge);

        Label code = new Label("Code : " + (t.getCode() != null ? t.getCode() : ""));
        code.getStyleClass().add("admin-thematiques-card-code");
        Label desc = new Label(t.getDescription() != null ? t.getDescription() : "");
        desc.getStyleClass().add("admin-thematiques-card-desc");
        desc.setWrapText(true);
        desc.setMaxWidth(260);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        Button edit = iconButtonSvg(SVG_EDIT, "Modifier", "admin-thematiques-action-edit");
        Button del = iconButtonSvg(SVG_DELETE, "Supprimer", "admin-thematiques-action-delete");
        edit.setOnAction(e -> openFormForEdit(t));
        del.setOnAction(e -> confirmDeleteThematique(t));
        actions.getChildren().addAll(edit, del);

        card.getChildren().addAll(thumbWrap, titleRow, code, desc, actions);

        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            Node n = e.getPickResult().getIntersectedNode();
            while (n != null) {
                if (n instanceof Button) {
                    return;
                }
                n = n.getParent();
            }
            openThematiqueDetail(t);
        });

        return card;
    }

    private static Button iconButtonSvg(String pathD, String tip, String... extraStyles) {
        SVGPath svg = new SVGPath();
        svg.setContent(pathD);
        svg.getStyleClass().add("admin-thematiques-svg");
        svg.setScaleX(20.0 / 24.0);
        svg.setScaleY(20.0 / 24.0);
        StackPane g = new StackPane(svg);
        g.setAlignment(Pos.CENTER);
        g.setMinSize(40, 34);
        g.setPrefSize(40, 34);
        g.setMaxSize(40, 34);
        Button b = new Button();
        b.setGraphic(g);
        b.setMinSize(44, 38);
        b.setPrefSize(44, 38);
        b.getStyleClass().add("admin-thematiques-icon-btn");
        b.getStyleClass().addAll(extraStyles);
        b.setTooltip(new javafx.scene.control.Tooltip(tip));
        return b;
    }

    private void loadThematiqueImageCard(ImageView iv, String relative) {
        Path p = UserPublicAssets.resolvePublicRelative(relative);
        if (p != null) {
            File f = p.toFile();
            if (f.isFile()) {
                try {
                    iv.setImage(new Image(f.toURI().toString(), true));
                    iv.getStyleClass().remove("admin-thematiques-card-img-placeholder");
                    return;
                } catch (Exception ignored) {
                    // placeholder
                }
            }
        }
        iv.setImage(null);
        iv.getStyleClass().remove("admin-thematiques-card-img-placeholder");
        iv.getStyleClass().add("admin-thematiques-card-img-placeholder");
    }

    /**
     * Ouvre la fiche publique d’un événement lié à cette thématique (même rendu que pour un visiteur).
     * Choisit de préférence le prochain événement publié ; sinon le plus récent.
     */
    private void openPublicPreviewForThematique(Thematique t) {
        if (t == null || t.getNom() == null || t.getNom().isBlank()) {
            return;
        }
        try {
            String themeName = t.getNom().trim();
            List<Event> published = eventService.findPublishedPublic().stream()
                    .filter(ev -> thematiqueMatchesForPreview(themeName, ev.getThematique()))
                    .filter(ev -> ev.getStatut() == EventStatus.PUBLIE)
                    .toList();
            if (published.isEmpty()) {
                alert(Alert.AlertType.INFORMATION, "Aperçu public",
                        "Aucun événement publié n’est associé à la thématique « " + themeName
                                + " ». Publiez un événement et associez-lui ce nom de thématique.");
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            Optional<Event> upcoming = published.stream()
                    .filter(ev -> ev.getDateDebut() != null && !ev.getDateDebut().isBefore(now))
                    .min(Comparator.comparing(Event::getDateDebut));
            Event pick = upcoming.orElseGet(() -> published.stream()
                    .max(Comparator.comparing(Event::getDateDebut))
                    .orElse(published.get(0)));
            AppState.setPendingPublicEventDetailId(pick.getId());
            MainApp.showPublicPage("event-detail");
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Aperçu public",
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private static boolean thematiqueMatchesForPreview(String themeTitle, String eventThematique) {
        String a = normalizeForSearch(themeTitle);
        String b = normalizeForSearch(eventThematique != null ? eventThematique : "");
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b) || b.contains(a);
    }

    private void openThematiqueDetail(Thematique t) {
        detailShownThematique = t;
        populateDetailView(t);
        showLayer(VisibleLayer.DETAIL);
    }

    private void populateDetailView(Thematique t) {
        if (detailTitleLabel != null) {
            detailTitleLabel.setText(t.getNom() != null ? t.getNom() : "");
        }
        if (detailMetaLabel != null) {
            String code = t.getCode() != null ? t.getCode() : "—";
            detailMetaLabel.setText("Code : " + code + " · Ordre : " + t.getOrdreAffichage());
        }
        if (detailSousTitreLabel != null) {
            detailSousTitreLabel.setText(t.getSousTitre() != null ? t.getSousTitre() : "");
        }
        if (detailDescriptionLabel != null) {
            detailDescriptionLabel.setText(t.getDescription() != null ? t.getDescription() : "—");
        }
        if (detailPublicLabel != null) {
            detailPublicLabel.setText("Public cible : " + (t.getPublicCible() != null ? t.getPublicCible() : "—"));
        }
        if (detailNiveauLabel != null) {
            detailNiveauLabel.setText("Niveau : " + (t.getNiveauDifficulte() != null ? t.getNiveauDifficulte() : "—"));
        }
        String hex = normalizeHex(t.getCouleur() != null ? t.getCouleur() : "#A7C7E7");
        if (detailColorHexLabel != null) {
            detailColorHexLabel.setText(hex);
        }
        String style = "-fx-background-color: " + hex + "; -fx-background-radius: 6;";
        if (detailColorSwatch != null) {
            detailColorSwatch.setStyle(style);
        }
        if (detailColorDot != null) {
            detailColorDot.setStyle(style + " -fx-min-width: 16; -fx-min-height: 16; -fx-max-width: 16; -fx-max-height: 16;");
        }
        loadDetailHeroImage(t.getImageChemin());
        loadDetailEvents(t);
    }

    private void loadDetailHeroImage(String relative) {
        if (detailHeroImage == null) {
            return;
        }
        if (detailHeroStack != null) {
            detailHeroStack.getStyleClass().remove("admin-thematiques-detail-hero-empty");
        }
        Path p = UserPublicAssets.resolvePublicRelative(relative);
        if (p != null) {
            File f = p.toFile();
            if (f.isFile()) {
                try {
                    detailHeroImage.setImage(new Image(f.toURI().toString(), true));
                    detailHeroImage.getStyleClass().remove("admin-thematiques-card-img-placeholder");
                    return;
                } catch (Exception ignored) {
                    // placeholder
                }
            }
        }
        detailHeroImage.setImage(null);
        detailHeroImage.getStyleClass().remove("admin-thematiques-card-img-placeholder");
        detailHeroImage.getStyleClass().add("admin-thematiques-card-img-placeholder");
        if (detailHeroStack != null) {
            detailHeroStack.getStyleClass().add("admin-thematiques-detail-hero-empty");
        }
    }

    private void loadDetailEvents(Thematique t) {
        if (detailEventsTable == null) {
            return;
        }
        try {
            List<Event> evs = eventService.findByThematiqueNom(t.getNom());
            ObservableList<Event> obs = FXCollections.observableArrayList(evs);
            detailEventsTable.setItems(obs);
        } catch (SQLException e) {
            detailEventsTable.setItems(FXCollections.observableArrayList());
            alert(Alert.AlertType.ERROR, "Événements", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @FXML
    public void onDetailBack() {
        detailShownThematique = null;
        showLayer(VisibleLayer.LIST);
    }

    @FXML
    public void onDetailEdit() {
        if (detailShownThematique != null) {
            openFormForEdit(detailShownThematique);
        }
    }

    @FXML
    public void onDetailDelete() {
        if (detailShownThematique != null) {
            confirmDeleteThematique(detailShownThematique);
        }
    }

    @FXML
    public void onDetailNouvelEvenement() {
        if (navigateNewEventForThematique != null && detailShownThematique != null) {
            navigateNewEventForThematique.accept(detailShownThematique.getNom());
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private void confirmDeleteThematique(Thematique t) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Confirmer");
        c.setHeaderText("Supprimer cette thématique ?");
        c.setContentText(t.getNom());
        c.showAndWait().ifPresent(btn -> {
            if (btn == javafx.scene.control.ButtonType.OK) {
                try {
                    int idDeleted = t.getId();
                    thematiqueService.delete(idDeleted);
                    if (detailShownThematique != null && detailShownThematique.getId() == idDeleted) {
                        detailShownThematique = null;
                        showLayer(VisibleLayer.LIST);
                    }
                    refreshFromDb();
                } catch (SQLException e) {
                    alert(Alert.AlertType.ERROR, "Suppression", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }
        });
    }

    private void updateStats() {
        String q = currentSearchQueryNormalized();
        List<Thematique> visible = allThematiques.stream().filter(t -> matchesFilter(t, q)).toList();

        int avec = 0;
        int sans = 0;
        for (Thematique t : visible) {
            int c = eventCount(t);
            if (c > 0) {
                avec++;
            } else {
                sans++;
            }
        }

        if (statBigTotalThem != null) {
            statBigTotalThem.setText(String.valueOf(visible.size()));
        }
        if (statBigAvecThem != null) {
            statBigAvecThem.setText(String.valueOf(avec));
        }
        int totalEvtDb;
        try {
            totalEvtDb = thematiqueService.countTotalEvenements();
        } catch (SQLException e) {
            totalEvtDb = 0;
        }
        if (statBigTotalEvtKpi != null) {
            statBigTotalEvtKpi.setText(String.valueOf(totalEvtDb));
        }

        if (statThematiquesTypeBox != null) {
            statThematiquesTypeBox.getChildren().clear();
            statThematiquesTypeBox.getChildren().addAll(
                    buildStatRowWithBadge("Avec événements", avec, "admin-stat-num-badge", "admin-stat-num-badge-green"),
                    buildStatRowWithBadge("Sans événement", sans, "admin-stat-num-badge", "admin-stat-num-badge-grey"));
        }

        rebuildThematiquesPie(avec, sans);
        rebuildEventsPie(visible);

        if (statListEventsBox != null) {
            statListEventsBox.getChildren().clear();
            int rowIdx = 0;
            for (Thematique t : visible) {
                int c = eventCount(t);
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(shorten(t.getNom(), 28));
                name.getStyleClass().add("admin-events-stat-line");
                Label b = new Label(String.valueOf(c));
                String variant = STAT_LIST_BADGE_VARIANTS[rowIdx % STAT_LIST_BADGE_VARIANTS.length];
                b.getStyleClass().addAll("admin-stat-num-badge", variant);
                rowIdx++;
                Region sp = new Region();
                HBox.setHgrow(sp, javafx.scene.layout.Priority.ALWAYS);
                row.getStyleClass().add("admin-stat-row-badge");
                row.getChildren().addAll(name, sp, b);
                statListEventsBox.getChildren().add(row);
            }
        }
    }

    private static HBox buildStatRowWithBadge(String leftLabel, int value, String... badgeStyleClasses) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("admin-stat-row-badge");
        Label left = new Label(leftLabel);
        left.getStyleClass().add("admin-events-stat-line");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label badge = new Label(String.valueOf(value));
        badge.getStyleClass().addAll(badgeStyleClasses);
        row.getChildren().addAll(left, sp, badge);
        return row;
    }

    /** Camembert « avec / sans » événements (vert / gris). */
    private void rebuildThematiquesPie(int avec, int sans) {
        if (pieThematiquesChart == null) {
            return;
        }
        int total = avec + sans;
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        if (total <= 0) {
            data.add(new PieChart.Data("Aucune thématique", 1));
            pieThematiquesChart.setData(data);
            Platform.runLater(() -> {
                EventStatsPieCharts.applyIndexedPieChartColorsCss(pieThematiquesChart, PIE_GRAY_EMPTY);
                applyPieColors(pieThematiquesChart, PIE_GRAY_EMPTY);
                Map<String, String> leg = new HashMap<>();
                leg.put("Aucune thématique", PIE_GRAY_EMPTY);
                centerPieChartLegend(pieThematiquesChart);
                EventStatsPieCharts.syncLegendColors(pieThematiquesChart, leg);
            });
            return;
        }
        data.add(new PieChart.Data("Avec événements", avec));
        data.add(new PieChart.Data("Sans événement", sans));
        pieThematiquesChart.setData(data);
        Platform.runLater(() -> {
            EventStatsPieCharts.applyIndexedPieChartColorsCss(pieThematiquesChart, PIE_GREEN_AVEC, PIE_GRAY_SANS);
            pieThematiquesChart.applyCss();
            pieThematiquesChart.layout();
            List<PieChart.Data> slice = pieThematiquesChart.getData();
            if (slice.size() >= 2) {
                applyPieSliceColor(slice.get(0), PIE_GREEN_AVEC);
                applyPieSliceColor(slice.get(1), PIE_GRAY_SANS);
            }
            Map<String, String> leg = new HashMap<>();
            leg.put("Avec événements", PIE_GREEN_AVEC);
            leg.put("Sans événement", PIE_GRAY_SANS);
            centerPieChartLegend(pieThematiquesChart);
            EventStatsPieCharts.syncLegendColors(pieThematiquesChart, leg);
            Platform.runLater(() -> {
                EventStatsPieCharts.applyIndexedPieChartColorsCss(pieThematiquesChart, PIE_GREEN_AVEC, PIE_GRAY_SANS);
                if (slice.size() >= 2) {
                    applyPieSliceColor(slice.get(0), PIE_GREEN_AVEC);
                    applyPieSliceColor(slice.get(1), PIE_GRAY_SANS);
                }
                centerPieChartLegend(pieThematiquesChart);
                EventStatsPieCharts.syncLegendColors(pieThematiquesChart, leg);
            });
        });
    }

    /** Camembert : part d’événements par thématique (couleurs distinctes). */
    private void rebuildEventsPie(List<Thematique> visible) {
        if (pieEventsParThematiqueChart == null) {
            return;
        }
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        int sum = 0;
        for (Thematique t : visible) {
            int c = eventCount(t);
            sum += c;
            if (c > 0) {
                data.add(new PieChart.Data(shorten(t.getNom(), 22), c));
            }
        }
        if (sum <= 0) {
            if (visible.isEmpty()) {
                data.setAll(new PieChart.Data("—", 1));
            } else {
                data.setAll(new PieChart.Data("Aucun événement", 1));
            }
            pieEventsParThematiqueChart.setData(data);
            Platform.runLater(() -> {
                EventStatsPieCharts.applyIndexedPieChartColorsCss(pieEventsParThematiqueChart, PIE_GRAY_EMPTY);
                applyPieColors(pieEventsParThematiqueChart, PIE_GRAY_EMPTY);
                Map<String, String> leg = new HashMap<>();
                leg.put("—", PIE_GRAY_EMPTY);
                leg.put("Aucun événement", PIE_GRAY_EMPTY);
                centerPieChartLegend(pieEventsParThematiqueChart);
                EventStatsPieCharts.syncLegendColors(pieEventsParThematiqueChart, leg);
            });
            return;
        }
        pieEventsParThematiqueChart.setData(data);
        Platform.runLater(() -> {
            List<PieChart.Data> slices = pieEventsParThematiqueChart.getData();
            String[] hexByIndex = new String[slices.size()];
            Map<String, String> leg = new HashMap<>();
            for (int i = 0; i < slices.size(); i++) {
                String hex = PIE_EVENT_PALETTE[i % PIE_EVENT_PALETTE.length];
                hexByIndex[i] = hex;
                leg.put(slices.get(i).getName(), hex);
            }
            EventStatsPieCharts.applyIndexedPieChartColorsCss(pieEventsParThematiqueChart, hexByIndex);
            pieEventsParThematiqueChart.applyCss();
            pieEventsParThematiqueChart.layout();
            for (int i = 0; i < slices.size(); i++) {
                applyPieSliceColor(slices.get(i), hexByIndex[i]);
            }
            centerPieChartLegend(pieEventsParThematiqueChart);
            EventStatsPieCharts.syncLegendColors(pieEventsParThematiqueChart, leg);
            Platform.runLater(() -> {
                EventStatsPieCharts.applyIndexedPieChartColorsCss(pieEventsParThematiqueChart, hexByIndex);
                for (int i = 0; i < slices.size(); i++) {
                    applyPieSliceColor(slices.get(i), hexByIndex[i]);
                }
                centerPieChartLegend(pieEventsParThematiqueChart);
                EventStatsPieCharts.syncLegendColors(pieEventsParThematiqueChart, leg);
            });
        });
    }

    private static void applyPieSliceColor(PieChart.Data d, String hex) {
        Node n = d.getNode();
        if (n != null) {
            n.setStyle("-fx-pie-color: " + hex + ";");
        }
    }

    private static void applyPieColors(PieChart chart, String singleHex) {
        chart.applyCss();
        chart.layout();
        for (PieChart.Data d : chart.getData()) {
            applyPieSliceColor(d, singleHex);
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @FXML
    public void onNewThematique() {
        editingId = -1;
        pendingImageFile = null;
        existingImageRelative = null;
        if (formPageTitle != null) {
            formPageTitle.setText("Nouvelle thématique");
        }
        clearForm();
        showLayer(VisibleLayer.FORM);
    }

    private void openFormForEdit(Thematique t) {
        editingId = t.getId();
        pendingImageFile = null;
        existingImageRelative = t.getImageChemin();
        if (formPageTitle != null) {
            formPageTitle.setText("Modifier la thématique");
        }
        if (formNom != null) {
            formNom.setText(t.getNom());
        }
        if (formCode != null) {
            formCode.setText(t.getCode());
        }
        if (formDescription != null) {
            formDescription.setText(t.getDescription());
        }
        if (formCouleur != null) {
            formCouleur.setText(t.getCouleur());
        }
        if (formSousTitre != null) {
            formSousTitre.setText(t.getSousTitre());
        }
        if (formOrdre != null) {
            formOrdre.setText(String.valueOf(t.getOrdreAffichage()));
        }
        if (formVisible != null) {
            formVisible.setSelected(t.isVisibleSite());
        }
        if (formPublicCible != null) {
            selectPublicCibleForEdit(t.getPublicCible());
        }
        if (formNiveau != null) {
            String niv = t.getNiveauDifficulte();
            if (niv != null && formNiveau.getItems().contains(niv)) {
                formNiveau.getSelectionModel().select(niv);
            }
        }
        showLayer(VisibleLayer.FORM);
    }

    /** Sélectionne la valeur issue de la BDD, ou une correspondance avec l’ancienne liste (Enfants → Enfant, etc.). */
    private void selectPublicCibleForEdit(String saved) {
        if (formPublicCible == null || saved == null) {
            return;
        }
        if (formPublicCible.getItems().contains(saved)) {
            formPublicCible.getSelectionModel().select(saved);
            return;
        }
        String mapped = mapLegacyPublicCible(saved);
        if (mapped != null && formPublicCible.getItems().contains(mapped)) {
            formPublicCible.getSelectionModel().select(mapped);
        }
    }

    private static String mapLegacyPublicCible(String saved) {
        return switch (saved) {
            case "Enfants" -> "Enfant";
            case "Parents" -> "Parent";
            case "Familles", "Grand public" -> "Autre";
            case "Professionnels" -> "Médecin";
            default -> null;
        };
    }

    private void clearForm() {
        if (formNom != null) {
            formNom.clear();
        }
        if (formCode != null) {
            formCode.clear();
        }
        if (formDescription != null) {
            formDescription.clear();
        }
        if (formCouleur != null) {
            formCouleur.setText("#A7C7E7");
        }
        if (formSousTitre != null) {
            formSousTitre.clear();
        }
        if (formOrdre != null) {
            formOrdre.setText("0");
        }
        if (formVisible != null) {
            formVisible.setSelected(true);
        }
        if (formPublicCible != null) {
            formPublicCible.getSelectionModel().clearSelection();
        }
        if (formNiveau != null) {
            formNiveau.getSelectionModel().clearSelection();
        }
    }

    private void showLayer(VisibleLayer layer) {
        boolean list = layer == VisibleLayer.LIST;
        boolean det = layer == VisibleLayer.DETAIL;
        boolean frm = layer == VisibleLayer.FORM;
        if (listLayer != null) {
            listLayer.setVisible(list);
            listLayer.setManaged(list);
        }
        if (detailLayer != null) {
            detailLayer.setVisible(det);
            detailLayer.setManaged(det);
        }
        if (formLayerHost != null) {
            formLayerHost.setVisible(frm);
            formLayerHost.setManaged(frm);
            if (frm) {
                formLayerHost.toFront();
            }
        }
    }

    @FXML
    public void onCloseForm() {
        detailShownThematique = null;
        showLayer(VisibleLayer.LIST);
    }

    @FXML
    public void onPickImage() {
        Window w = formLayer != null ? formLayer.getScene().getWindow() : null;
        FileChooser ch = new FileChooser();
        ch.setTitle("Image de la thématique");
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        File f = w != null ? ch.showOpenDialog(w) : ch.showOpenDialog(null);
        if (f != null) {
            pendingImageFile = f;
        }
    }

    @FXML
    public void onSaveThematique() {
        if (!validateForm()) {
            return;
        }
        Thematique t = new Thematique();
        t.setNom(trim(formNom));
        t.setCode(trim(formCode).toUpperCase(Locale.FRENCH));
        t.setDescription(trim(formDescription));
        t.setCouleur(normalizeHex(trim(formCouleur)));
        t.setSousTitre(trim(formSousTitre));
        t.setOrdreAffichage(parseOrdre());
        t.setVisibleSite(formVisible != null && formVisible.isSelected());
        t.setPublicCible(formPublicCible.getValue());
        t.setNiveauDifficulte(formNiveau.getValue());

        try {
            if (pendingImageFile != null) {
                t.setImageChemin(ThematiqueImageStorage.copyThematiqueImage(pendingImageFile));
            } else if (editingId > 0) {
                t.setImageChemin(existingImageRelative);
            } else {
                t.setImageChemin(null);
            }

            if (editingId <= 0) {
                thematiqueService.add(t);
            } else {
                t.setId(editingId);
                thematiqueService.update(t);
            }
            pendingImageFile = null;
            refreshFromDb();
            onCloseForm();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                alert(Alert.AlertType.WARNING, "Validation", "Ce code thématique existe déjà.");
            } else {
                alert(Alert.AlertType.ERROR, "Enregistrement", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Image", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private int parseOrdre() {
        if (formOrdre == null || formOrdre.getText() == null || formOrdre.getText().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(formOrdre.getText().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean validateForm() {
        String nom = trim(formNom);
        if (nom.length() < NOM_MIN) {
            validationAlert("Le nom est obligatoire ou trop court : de " + NOM_MIN + " à " + NOM_MAX + " caractères.");
            return false;
        }
        if (nom.length() > NOM_MAX) {
            validationAlert("Le nom ne doit pas dépasser " + NOM_MAX + " caractères.");
            return false;
        }

        String code = trim(formCode);
        if (!CODE_PATTERN.matcher(code).matches()) {
            validationAlert("Le code est obligatoire ou invalide : de 2 à 32 caractères, lettres sans accent, chiffres "
                    + "et tiret bas, et le code doit rester unique en base.");
            return false;
        }

        String desc = trim(formDescription);
        if (desc.length() < DESC_MIN) {
            validationAlert("La description est obligatoire ou trop courte : de " + DESC_MIN + " à " + DESC_MAX
                    + " caractères.");
            return false;
        }
        if (desc.length() > DESC_MAX) {
            validationAlert("La description ne doit pas dépasser " + DESC_MAX + " caractères.");
            return false;
        }

        String couleurRaw = formCouleur != null && formCouleur.getText() != null ? formCouleur.getText().trim() : "";
        if (couleurRaw.isEmpty()) {
            validationAlert("La couleur est obligatoire : saisissez le symbole # suivi de six caractères hexadécimaux.");
            return false;
        }
        String couleurNorm = normalizeHex(couleurRaw);
        if (!HEX_COLOR.matcher(couleurNorm).matches()) {
            validationAlert("La couleur est invalide : six chiffres hexadécimaux doivent suivre le #.");
            return false;
        }

        String sousTitre = trim(formSousTitre);
        if (sousTitre.length() < SOUS_TITRE_MIN) {
            validationAlert("Le sous-titre est obligatoire ou trop court : de " + SOUS_TITRE_MIN + " à " + SOUS_TITRE_MAX
                    + " caractères.");
            return false;
        }
        if (sousTitre.length() > SOUS_TITRE_MAX) {
            validationAlert("Le sous-titre ne doit pas dépasser " + SOUS_TITRE_MAX + " caractères.");
            return false;
        }

        if (!validateOrdreAffichage()) {
            return false;
        }

        if (formPublicCible == null || formPublicCible.getValue() == null || formPublicCible.getValue().isBlank()) {
            validationAlert("Le public cible est obligatoire : sélectionnez une valeur dans la liste déroulante.");
            return false;
        }
        if (!formPublicCible.getItems().contains(formPublicCible.getValue())) {
            validationAlert("Le public cible est invalide : choisissez une des valeurs proposées dans la liste.");
            return false;
        }

        if (formNiveau == null || formNiveau.getValue() == null || formNiveau.getValue().isBlank()) {
            validationAlert("Le niveau de difficulté est obligatoire : sélectionnez une valeur dans la liste.");
            return false;
        }
        if (!formNiveau.getItems().contains(formNiveau.getValue())) {
            validationAlert("Le niveau de difficulté est invalide : choisissez une des valeurs proposées dans la liste.");
            return false;
        }

        if (!validateImageRules()) {
            return false;
        }

        return true;
    }

    private void validationAlert(String message) {
        alert(Alert.AlertType.WARNING, "Contrôle de saisie", message);
    }

    /** Champ ordre : vide = 0 ; sinon entier dans {@link #ORDRE_MIN}..{@link #ORDRE_MAX}. */
    private boolean validateOrdreAffichage() {
        if (formOrdre == null) {
            return true;
        }
        String s = formOrdre.getText();
        if (s == null || s.isBlank()) {
            return true;
        }
        try {
            int v = Integer.parseInt(s.trim());
            if (v < ORDRE_MIN || v > ORDRE_MAX) {
                validationAlert("L'ordre d'affichage doit être un entier entre " + ORDRE_MIN + " et " + ORDRE_MAX
                        + ", ou laissez le champ vide pour utiliser 0.");
                return false;
            }
        } catch (NumberFormatException e) {
            validationAlert("L'ordre d'affichage doit être un nombre entier ou un champ vide pour 0.");
            return false;
        }
        return true;
    }

    private boolean validateImageRules() {
        if (editingId > 0) {
            if (pendingImageFile != null) {
                if (!pendingImageFile.isFile()) {
                    validationAlert("Le fichier image sélectionné est introuvable.");
                    return false;
                }
                return validateImageFileConstraints(pendingImageFile);
            }
            if (existingImageRelative == null || existingImageRelative.isBlank()) {
                validationAlert("L'image est obligatoire pour cette fiche : fichier JPG, PNG, GIF ou WebP jusqu'à 15 Mo.");
                return false;
            }
            if (!UserPublicAssets.imageFileExists(existingImageRelative)) {
                validationAlert("L'image enregistrée est introuvable : ajoutez un fichier JPG, PNG, GIF ou WebP jusqu'à 15 Mo.");
                return false;
            }
            return true;
        }

        if (pendingImageFile == null || !pendingImageFile.isFile()) {
            validationAlert("L'image est obligatoire pour une nouvelle thématique : JPG, PNG, GIF ou WebP jusqu'à 15 Mo.");
            return false;
        }
        return validateImageFileConstraints(pendingImageFile);
    }

    private boolean validateImageFileConstraints(File file) {
        String name = file.getName();
        if (!isAllowedImageExtension(name)) {
            validationAlert("Format d'image non autorisé : utilisez une extension .jpg, .jpeg, .png, .gif ou .webp.");
            return false;
        }
        long len = file.length();
        if (len <= 0) {
            validationAlert("Le fichier image est vide ou illisible.");
            return false;
        }
        if (len > IMAGE_MAX_BYTES) {
            validationAlert("L'image est trop volumineuse : la taille doit rester sous 15 Mo.");
            return false;
        }
        return true;
    }

    private static boolean isAllowedImageExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private static String trim(TextField f) {
        if (f == null || f.getText() == null) {
            return "";
        }
        return f.getText().trim();
    }

    private static String trim(TextArea f) {
        if (f == null || f.getText() == null) {
            return "";
        }
        return f.getText().trim();
    }

    private static String normalizeHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return "#A7C7E7";
        }
        String h = hex.trim();
        if (!h.startsWith("#")) {
            h = "#" + h;
        }
        return h;
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
