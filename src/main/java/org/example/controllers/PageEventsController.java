package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.CacheHint;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.example.models.Event;
import org.example.models.Thematique;
import org.example.services.EventService;
import org.example.services.ThematiqueService;
import org.example.utils.AppState;
import org.example.utils.UserPublicAssets;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Liste publique des événements : filtres, affichage grille / liste, regroupement par thématique.
 */
public class PageEventsController implements PublicShellAware {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
    private static final DateTimeFormatter DATE_US = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);

    /** Grille : largeur fixe ; hauteur suivant le contenu (plus de grande zone vide). */
    private static final double THEME_CARD_W = 320;
    /** Bandeau visuel type « cover » (recadrage centré, sans étirement). */
    private static final double THEME_IMG_H = 182;

    /** Compare les libellés thématique (événement) et nom affiché (carte), tolère espaces / casse / accents. */
    private static String normalizeThemeKey(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.FRENCH).replaceAll("\\s+", " ");
    }

    private static boolean themeTitlesMatch(String themeTitle, String eventThematiqueField) {
        return normalizeThemeKey(themeTitle).equals(normalizeThemeKey(eventThematiqueField));
    }

    /**
     * Filtre + cartes : égalité stricte, ou chaîne événement qui contient le libellé thème
     * (ex. thème « lili » et événement enregistré « lili — atelier »).
     */
    private static boolean sameThematiqueAsThemeTitle(String themeTitle, String eventThematique) {
        if (themeTitlesMatch(themeTitle, eventThematique)) {
            return true;
        }
        String a = normalizeThemeKey(themeTitle);
        String b = normalizeThemeKey(eventThematique);
        if (a.length() < 2 || b.isEmpty()) {
            return false;
        }
        return b.contains(a);
    }

    /**
     * Affiche l’image en « cover » dans une zone à dimensions fixes. L’{@link ImageView} est
     * {@code managed=false} pour qu’il ne gonfle pas la largeur préférée de la carte (bug d’overflow).
     */
    private static void installCoverImage(StackPane host, String imageUrl, double boxW, double boxH) {
        host.getChildren().clear();
        host.setMinWidth(boxW);
        host.setPrefWidth(boxW);
        host.setMaxWidth(boxW);
        host.setMinHeight(boxH);
        host.setPrefHeight(boxH);
        host.setMaxHeight(boxH);

        Rectangle clip = new Rectangle(boxW, boxH);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        clip.widthProperty().bind(host.widthProperty());
        clip.heightProperty().bind(host.heightProperty());
        host.setClip(clip);

        ImageView iv = new ImageView();
        iv.setSmooth(true);
        iv.setCache(true);
        iv.setManaged(false);
        try {
            Image img = new Image(imageUrl, true);
            Runnable layout = () -> {
                double W = host.getWidth() > 0 ? host.getWidth() : boxW;
                double H = boxH;
                if (W <= 2 || img.isError()) {
                    return;
                }
                double iw = img.getWidth();
                double ih = img.getHeight();
                if (iw <= 0 || ih <= 0) {
                    return;
                }
                double scale = Math.max(W / iw, H / ih);
                double vw = iw * scale;
                double vh = ih * scale;
                iv.setPreserveRatio(false);
                iv.setFitWidth(vw);
                iv.setFitHeight(vh);
                iv.setTranslateX((W - vw) / 2);
                iv.setTranslateY((H - vh) / 2);
            };
            iv.setImage(img);
            img.progressProperty().addListener((o, a, p) -> {
                if (p.doubleValue() >= 1.0) {
                    Platform.runLater(layout);
                }
            });
            img.errorProperty().addListener((o, a, err) -> {
                if (!err) {
                    Platform.runLater(layout);
                }
            });
            host.widthProperty().addListener((o, a, b) -> layout.run());
            host.getChildren().add(iv);
            if (img.getProgress() >= 1.0 && !img.isError()) {
                Platform.runLater(layout);
            }
        } catch (Exception e) {
            host.setClip(null);
            host.getChildren().add(new Label("📷"));
        }
    }

    private record ThemeDef(String title, String subtitle, String imageUrl) {}

    /** Ancien jeu par défaut si aucune thématique « visible site » en base. */
    private static final List<ThemeDef> FALLBACK_THEMES = List.of(
            new ThemeDef("Famille & Loisirs", "Des moments à partager",
                    "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=720&q=80"),
            new ThemeDef("Bien-être & santé", "Prendre soin de soi",
                    "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=720&q=80"),
            new ThemeDef("Professionnels & formation", "Réseau et expertise",
                    "https://images.unsplash.com/photo-1524178232363-1fb2b075b655?w=720&q=80")
    );

    private static final String DEFAULT_CARD_IMAGE =
            "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=720&q=80";

    private PublicShellController shell;
    private final EventService eventService = new EventService();
    private final ThematiqueService thematiqueService = new ThematiqueService();
    /** Thématiques publiques chargées depuis la BDD (vide = on utilisera {@link #FALLBACK_THEMES}). */
    private List<ThemeDef> publicThemesFromDb = List.of();
    private List<Event> sourceEvents = List.of();
    private boolean gridMode = true;

    @FXML
    private DatePicker dateDebutPicker;
    @FXML
    private DatePicker dateFinPicker;
    @FXML
    private TextField lieuField;
    @FXML
    private ComboBox<String> thematiqueCombo;
    @FXML
    private Button searchBtn;
    @FXML
    private Button resetBtn;
    @FXML
    private ToggleButton gridToggle;
    @FXML
    private ToggleButton listToggle;
    @FXML
    private VBox themesHost;
    @FXML
    private VBox otherSection;
    @FXML
    private Label otherCountLabel;
    @FXML
    private VBox otherEventsHost;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    public void initialize() {
        if (thematiqueCombo != null) {
            thematiqueCombo.setEditable(false);
            thematiqueCombo.setVisibleRowCount(12);
            Tooltip tip = new Tooltip(
                    "Toutes : affiche tous les événements (selon dates et lieu).\n"
                            + "Choisissez une thématique pour ne voir que ces événements.\n"
                            + "Autres : événements sans thématique reconnue ou non classés.");
            thematiqueCombo.setTooltip(tip);
        }
        if (dateDebutPicker != null) {
            dateDebutPicker.setOnAction(e -> onSearch());
            dateDebutPicker.valueProperty().addListener((obs, o, n) -> onSearch());
        }
        if (dateFinPicker != null) {
            dateFinPicker.setOnAction(e -> onSearch());
            dateFinPicker.valueProperty().addListener((obs, o, n) -> onSearch());
        }
        if (lieuField != null) {
            lieuField.setOnAction(e -> onSearch());
            lieuField.textProperty().addListener((obs, o, n) -> onSearch());
        }
        if (thematiqueCombo != null) {
            thematiqueCombo.valueProperty().addListener((obs, o, n) -> onSearch());
        }
        if (searchBtn != null) {
            searchBtn.setOnAction(e -> onSearch());
        }
        if (resetBtn != null) {
            resetBtn.setOnAction(e -> onReset());
        }
        /* Les boutons utilisent onAction dans le FXML (lien direct contrôleur) — ne pas remplacer par setOnAction ici. */
        if (gridToggle != null) {
            gridToggle.setSelected(true);
        }
    }

    @Override
    public void onShellReady() {
        /* Recharge à chaque affichage (retour depuis la fiche détail, etc.). */
        reloadFromDb();
    }

    @FXML
    public void onSearch() {
        rebuild();
    }

    @FXML
    public void onReset() {
        if (dateDebutPicker != null) {
            dateDebutPicker.setValue(null);
        }
        if (dateFinPicker != null) {
            dateFinPicker.setValue(null);
        }
        if (lieuField != null) {
            lieuField.clear();
        }
        if (thematiqueCombo != null) {
            thematiqueCombo.getSelectionModel().selectFirst();
        }
        rebuild();
    }

    @FXML
    public void onViewModeChanged() {
        boolean g = gridToggle != null && gridToggle.isSelected();
        gridMode = g;
        rebuild();
    }

    private void reloadFromDb() {
        loadPublicThemesFromDb();
        try {
            sourceEvents = eventService.findPublishedPublic();
        } catch (Exception e) {
            sourceEvents = List.of();
        }
        refillThematiqueCombo();
        rebuild();
    }

    private void loadPublicThemesFromDb() {
        try {
            List<ThemeDef> built = new ArrayList<>();
            for (Thematique t : thematiqueService.findVisibleOnSiteOrdered()) {
                built.add(themeDefFromThematique(t));
            }
            publicThemesFromDb = List.copyOf(built);
        } catch (SQLException e) {
            publicThemesFromDb = List.of();
        }
    }

    private ThemeDef themeDefFromThematique(Thematique t) {
        String sub = subtitleForThematique(t);
        String url = resolveThematiqueImageUrl(t);
        if (url == null) {
            url = DEFAULT_CARD_IMAGE;
        }
        return new ThemeDef(safeNom(t), sub, url);
    }

    private static String safeNom(Thematique t) {
        String n = t.getNom();
        return n != null && !n.isBlank() ? n.trim() : "(Sans nom)";
    }

    private static String subtitleForThematique(Thematique t) {
        if (t.getSousTitre() != null && !t.getSousTitre().isBlank()) {
            return t.getSousTitre().trim();
        }
        String d = t.getDescription();
        if (d == null || d.isBlank()) {
            return "";
        }
        d = d.trim();
        int nl = d.indexOf('\n');
        return nl < 0 ? d : d.substring(0, nl).trim();
    }

    private String resolveThematiqueImageUrl(Thematique t) {
        Path p = UserPublicAssets.resolvePublicRelative(t.getImageChemin());
        if (p == null) {
            return null;
        }
        File f = p.toFile();
        if (!f.isFile()) {
            return null;
        }
        return f.toURI().toString();
    }

    /** Thématiques affichées : celles de la BDD (visible site) ou repli sur l’ancien trio par défaut. */
    private List<ThemeDef> themesForDisplay() {
        if (!publicThemesFromDb.isEmpty()) {
            return publicThemesFromDb;
        }
        return FALLBACK_THEMES;
    }

    private void refillThematiqueCombo() {
        if (thematiqueCombo == null) {
            return;
        }
        String current = getThematiqueFromCombo();
        List<String> items = new ArrayList<>();
        items.add("Toutes");
        for (ThemeDef t : themesForDisplay()) {
            items.add(t.title());
        }
        items.add("Autres");
        thematiqueCombo.getItems().setAll(items);
        if (current != null && items.contains(current)) {
            thematiqueCombo.getSelectionModel().select(current);
        } else {
            thematiqueCombo.getSelectionModel().selectFirst();
        }
    }

    /** Libellé thématique : valeur du ComboBox puis repli sur l’index. */
    private String getThematiqueFromCombo() {
        if (thematiqueCombo == null) {
            return "Toutes";
        }
        String val = thematiqueCombo.getValue();
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        var items = thematiqueCombo.getItems();
        int idx = thematiqueCombo.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= items.size()) {
            return "Toutes";
        }
        String v = items.get(idx);
        return v != null && !v.isBlank() ? v.trim() : "Toutes";
    }

    /** Lieu + liens : recherche insensible à la casse et aux accents. */
    private static boolean lieuOuLiensContient(Event ev, String queryRaw) {
        String q = normalizeThemeKey(queryRaw);
        if (q.isEmpty()) {
            return true;
        }
        String l = normalizeThemeKey(ev.getLieu());
        if (l.contains(q)) {
            return true;
        }
        String maps = normalizeThemeKey(ev.getLienGoogleMaps());
        if (maps.contains(q)) {
            return true;
        }
        String zoom = normalizeThemeKey(ev.getLienZoomVisio());
        return zoom.contains(q);
    }

    private List<Event> applyFilters(List<Event> in) {
        LocalDate raw0 = readPickerDate(dateDebutPicker);
        LocalDate raw1 = readPickerDate(dateFinPicker);
        final LocalDate rangeStart;
        final LocalDate rangeEnd;
        if (raw0 != null && raw1 != null && raw0.isAfter(raw1)) {
            rangeStart = raw1;
            rangeEnd = raw0;
        } else {
            rangeStart = raw0;
            rangeEnd = raw1;
        }
        String lieuQ = lieuField != null && lieuField.getText() != null ? lieuField.getText().trim() : "";
        String themeSel = getThematiqueFromCombo();

        return in.stream().filter(ev -> {
            if (ev.getDateDebut() == null) {
                return false;
            }
            LocalDate day = ev.getDateDebut().toLocalDate();
            if (rangeStart != null && rangeEnd == null) {
                if (!day.isEqual(rangeStart)) {
                    return false;
                }
            } else if (rangeStart == null && rangeEnd != null) {
                if (!day.isEqual(rangeEnd)) {
                    return false;
                }
            } else {
                if (rangeStart != null && day.isBefore(rangeStart)) {
                    return false;
                }
                if (rangeEnd != null && day.isAfter(rangeEnd)) {
                    return false;
                }
            }
            if (!lieuQ.isEmpty() && !lieuOuLiensContient(ev, lieuQ)) {
                return false;
            }
            if (!"Toutes".equals(themeSel)) {
                if ("Autres".equals(themeSel)) {
                    return !isListedPublicTheme(ev.getThematiqueNom());
                }
                if (!isListedPublicTheme(ev.getThematiqueNom())) {
                    return true;
                }
                return sameThematiqueAsThemeTitle(themeSel, ev.getThematiqueNom());
            }
            return true;
        }).collect(Collectors.toList());
    }

    private static LocalDate readPickerDate(DatePicker picker) {
        if (picker == null) {
            return null;
        }
        LocalDate value = picker.getValue();
        if (value != null) {
            return value;
        }
        String typed = picker.getEditor() != null ? picker.getEditor().getText() : null;
        if (typed == null || typed.isBlank()) {
            return null;
        }
        String s = typed.trim();
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ignored) {
            /* try localized formats */
        }
        try {
            return LocalDate.parse(s, DATE_FR);
        } catch (DateTimeParseException ignored) {
            /* try us format */
        }
        try {
            return LocalDate.parse(s, DATE_US);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean isListedPublicTheme(String thematique) {
        if (normalizeThemeKey(thematique).isEmpty()) {
            return false;
        }
        for (ThemeDef def : themesForDisplay()) {
            if (sameThematiqueAsThemeTitle(def.title(), thematique)) {
                return true;
            }
        }
        return false;
    }

    private VBox resolveVBoxById(String cssId) {
        Node anchor = lieuField != null ? lieuField : thematiqueCombo;
        if (anchor == null || anchor.getScene() == null) {
            return null;
        }
        Node n = anchor.getScene().lookup("#" + cssId);
        return n instanceof VBox v ? v : null;
    }

    private VBox themesHostResolved() {
        return themesHost != null ? themesHost : resolveVBoxById("themesHost");
    }

    private VBox otherEventsHostResolved() {
        return otherEventsHost != null ? otherEventsHost : resolveVBoxById("otherEventsHost");
    }

    private void rebuild() {
        VBox th = themesHostResolved();
        VBox otherHost = otherEventsHostResolved();
        if (th == null) {
            return;
        }
        th.getChildren().clear();
        if (otherHost != null) {
            otherHost.getChildren().clear();
        }

        List<Event> filtered = applyFilters(sourceEvents);
        String themeSel = getThematiqueFromCombo();
        boolean showingOnlyAutres = "Autres".equals(themeSel);
        boolean hasSpecificTheme = themeSel != null && !"Toutes".equals(themeSel) && !showingOnlyAutres;
        boolean hasDateFilter = readPickerDate(dateDebutPicker) != null || readPickerDate(dateFinPicker) != null;
        boolean hasLieuFilter = lieuField != null && lieuField.getText() != null && !lieuField.getText().isBlank();
        boolean hasAnyFilter = hasSpecificTheme || showingOnlyAutres || hasDateFilter || hasLieuFilter;

        List<ThemeDef> visibleThemes;
        if (hasSpecificTheme) {
            visibleThemes = themesForDisplay().stream()
                    .filter(t -> sameThematiqueAsThemeTitle(themeSel, t.title()))
                    .collect(Collectors.toList());
        } else if (showingOnlyAutres) {
            visibleThemes = List.of();
        } else {
            visibleThemes = themesForDisplay();
        }

        if (gridMode && !visibleThemes.isEmpty()) {
            FlowPane flow = new FlowPane();
            flow.setAlignment(Pos.TOP_LEFT);
            flow.setColumnHalignment(HPos.LEFT);
            flow.setRowValignment(VPos.TOP);
            flow.setHgap(20);
            flow.setVgap(20);
            flow.setPrefWrapLength(3 * THEME_CARD_W + 2 * 20 + 8);
            flow.setMaxWidth(Double.MAX_VALUE);
            flow.getStyleClass().add("events-theme-flow");
            for (ThemeDef theme : visibleThemes) {
                List<Event> forTheme = filtered.stream()
                        .filter(ev -> sameThematiqueAsThemeTitle(theme.title(), ev.getThematiqueNom()))
                        .collect(Collectors.toList());
                if (hasAnyFilter && forTheme.isEmpty() && !hasSpecificTheme) {
                    continue;
                }
                flow.getChildren().add(buildThemeCardGrid(theme, forTheme));
            }
            th.getChildren().add(flow);
        } else if (!visibleThemes.isEmpty()) {
            VBox stack = new VBox(14);
            stack.setAlignment(Pos.TOP_LEFT);
            stack.setMaxWidth(Double.MAX_VALUE);
            stack.getStyleClass().add("events-list-stack");
            for (ThemeDef theme : visibleThemes) {
                List<Event> forTheme = filtered.stream()
                        .filter(ev -> sameThematiqueAsThemeTitle(theme.title(), ev.getThematiqueNom()))
                        .collect(Collectors.toList());
                if (hasAnyFilter && forTheme.isEmpty() && !hasSpecificTheme) {
                    continue;
                }
                stack.getChildren().add(buildThemeCardList(theme, forTheme));
            }
            th.getChildren().add(stack);
        }

        List<Event> autres = filtered.stream()
                .filter(ev -> !isListedPublicTheme(ev.getThematiqueNom()))
                .collect(Collectors.toList());

        int n = autres.size();
        if (otherCountLabel != null) {
            otherCountLabel.setText(n <= 1 ? n + " événement" : n + " événements");
        }
        if (otherHost != null) {
            for (Event ev : autres) {
                otherHost.getChildren().add(buildOtherEventRow(ev));
            }
        }
        if (otherSection != null) {
            boolean showOthers = n > 0;
            otherSection.setVisible(showOthers);
            otherSection.setManaged(showOthers);
        }
        optimizeEventListRender(th, otherHost);
    }

    private void optimizeEventListRender(VBox themesContainer, VBox othersContainer) {
        if (themesContainer != null) {
            themesContainer.setCache(true);
            themesContainer.setCacheHint(CacheHint.SPEED);
        }
        if (othersContainer != null) {
            othersContainer.setCache(true);
            othersContainer.setCacheHint(CacheHint.SPEED);
        }
    }

    private VBox buildThemeCardGrid(ThemeDef theme, List<Event> events) {
        VBox card = new VBox(8);
        card.getStyleClass().add("events-theme-card");
        card.setMinWidth(THEME_CARD_W);
        card.setPrefWidth(THEME_CARD_W);
        card.setMaxWidth(THEME_CARD_W);
        card.setFillWidth(true);

        StackPane imgHost = new StackPane();
        imgHost.getStyleClass().add("events-theme-image-wrap");
        installCoverImage(imgHost, theme.imageUrl(), THEME_CARD_W, THEME_IMG_H);

        VBox meta = new VBox(4);
        meta.getStyleClass().add("events-theme-meta-block");
        meta.setMaxWidth(THEME_CARD_W);
        double textMax = THEME_CARD_W - 24;
        Label title = new Label(theme.title());
        title.getStyleClass().add("events-theme-card-title");
        title.setWrapText(true);
        title.setMaxWidth(textMax);
        Label sub = new Label(theme.subtitle());
        sub.getStyleClass().add("events-theme-card-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(textMax);
        Label count = new Label(events.size() + " événement" + (events.size() > 1 ? "s" : ""));
        count.getStyleClass().add("events-theme-count");
        count.setMaxWidth(textMax);
        if (!events.isEmpty()) {
            count.getStyleClass().add("events-theme-count-has-events");
        }
        meta.getChildren().addAll(title, sub, count);

        card.getChildren().addAll(imgHost, meta);

        if (!events.isEmpty()) {
            VBox inner = new VBox(8);
            inner.getStyleClass().add("events-theme-inner");
            inner.setMaxWidth(THEME_CARD_W);
            VBox list = new VBox(8);
            list.getStyleClass().add("events-theme-events-stack");
            list.setFillWidth(true);
            for (Event ev : events) {
                list.getChildren().add(buildMiniEventRow(ev));
            }
            if (events.size() <= 3) {
                inner.getChildren().add(list);
            } else {
                ScrollPane sp = new ScrollPane();
                sp.setFitToWidth(true);
                sp.setPrefViewportHeight(196);
                sp.setMinViewportHeight(160);
                sp.setMaxHeight(220);
                sp.getStyleClass().add("events-theme-mini-scroll");
                sp.setContent(list);
                inner.getChildren().add(sp);
            }
            card.getChildren().add(inner);
        }

        return card;
    }

    private HBox buildThemeCardList(ThemeDef theme, List<Event> events) {
        HBox row = new HBox(18);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("events-theme-row-list");
        row.setPadding(new Insets(12, 16, 12, 16));

        StackPane thumb = new StackPane();
        thumb.setPrefSize(120, 120);
        thumb.setMinSize(120, 120);
        thumb.setMaxSize(120, 120);
        thumb.getStyleClass().add("events-theme-thumb");
        installCoverImage(thumb, theme.imageUrl(), 120, 120);

        VBox text = new VBox(6);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label title = new Label(theme.title());
        title.getStyleClass().add("events-theme-card-title");
        Label sub = new Label(theme.subtitle());
        sub.getStyleClass().add("events-theme-card-sub");
        Label count = new Label(events.size() + " événement" + (events.size() > 1 ? "s" : ""));
        count.getStyleClass().add("events-theme-count");
        if (!events.isEmpty()) {
            count.getStyleClass().add("events-theme-count-has-events");
        }

        text.getChildren().addAll(title, sub, count);
        if (!events.isEmpty()) {
            VBox inner = new VBox(8);
            inner.getStyleClass().add("events-theme-inner-wide");
            for (Event ev : events) {
                inner.getChildren().add(buildMiniEventRow(ev));
            }
            text.getChildren().add(inner);
        }
        row.getChildren().addAll(thumb, text);
        return row;
    }

    private HBox buildMiniEventRow(Event ev) {
        HBox line = new HBox(12);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("events-mini-row");
        line.setMaxWidth(THEME_CARD_W - 16);
        StackPane icon = new StackPane(new Label("📅"));
        icon.getStyleClass().add("events-mini-cal");
        icon.setMinSize(44, 44);
        icon.setPrefSize(44, 44);
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        info.setMaxWidth(Double.MAX_VALUE);
        Label t = new Label(Objects.toString(ev.getTitre(), ""));
        t.getStyleClass().add("events-mini-title");
        t.setWrapText(true);
        String whenWhere = DT.format(ev.getDateDebut());
        if (ev.getLieu() != null && !ev.getLieu().isBlank()) {
            whenWhere += "  ·  " + ev.getLieu();
        }
        Label meta = new Label(whenWhere);
        meta.getStyleClass().add("events-mini-meta");
        meta.setWrapText(true);
        info.getChildren().addAll(t, meta);
        Button voir = new Button("Voir →");
        voir.getStyleClass().add("events-btn-voir");
        voir.setMinWidth(Region.USE_PREF_SIZE);
        voir.setOnAction(e -> openDetail(ev.getId()));
        line.getChildren().addAll(icon, info, voir);
        return line;
    }

    private HBox buildOtherEventRow(Event ev) {
        return buildMiniEventRow(ev);
    }

    private void openDetail(int eventId) {
        AppState.setPendingPublicEventDetailId(eventId);
        try {
            if (shell != null) {
                shell.loadPage("event-detail");
            }
        } catch (Exception ex) {
            // ignoré — shell affiche déjà une alerte
        }
    }
}
