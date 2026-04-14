package org.example.controllers;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.CacheHint;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Side;
import javafx.util.Duration;
import org.example.MainApp;
import org.example.models.UserNotificationItem;
import org.example.services.UserNotificationService;
import org.example.utils.AppState;
import org.example.utils.UserAvatarGraphic;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coque publique partagée (même apparence que l’accueil) : nav, fond, hero, ticker, zone de page.
 */
public class PublicShellController {

    private static final String[] HOME_SHARED_PHOTO_FILES = {
            "home-shared-1.png",
            "home-shared-2.png"
    };

    private static final String[] HOME_PHOTO_FALLBACK_URLS = {
            "https://images.unsplash.com/photo-1516627145497-ae6968895b74?w=1600&q=85",
            "https://images.unsplash.com/photo-1544776193-7d62c3841120?w=1600&q=85"
    };

    private static final String GLOBAL_BG_FALLBACK_URL = HOME_PHOTO_FALLBACK_URLS[0];
    private static final double GLOBAL_BG_MAX_OPACITY = 0.5;

    private static final Duration HERO_SLIDE_INTERVAL = Duration.seconds(7);
    private static final Duration HERO_CROSSFADE_DURATION = Duration.millis(1600);

    private static final List<String> NEWS_TICKER_HEADLINES = List.of(
            "Move Your Body 1.0 : Quand sport et créativité s'unissent",
            "Hackathon H12 Innovation : innover pour l'inclusion",
            "Ateliers sensoriels : découverte des outils d'apaisement");

    private static final double NEWS_TICKER_PIXELS_PER_SEC = 44;

    private static final String SHELL_LISTING_MODE_CLASS = "public-shell--listing-mode";
    private static final String SHELL_AUTH_MODE_CLASS = "public-shell--auth-mode";

    @FXML
    private ScrollPane shellScrollPane;
    @FXML
    private VBox shellScrollContentVBox;
    @FXML
    private StackPane pageContentHost;
    @FXML
    private StackPane shellHeroLayer;
    @FXML
    private StackPane shellHeroScrollSpacer;
    @FXML
    private ImageView globalBgA;
    @FXML
    private ImageView globalBgB;
    @FXML
    private Region globalVeilRegion;
    @FXML
    private ImageView heroBgImageA;
    @FXML
    private ImageView heroBgImageB;
    @FXML
    private ComboBox<String> langCombo;
    @FXML
    private HBox guestNavActions;
    @FXML
    private HBox loggedShortcutIcons;
    @FXML
    private Button userNotifMenuButton;
    @FXML
    private Label userNotifBadgeLabel;
    @FXML
    private HBox loggedNavActions;
    @FXML
    private StackPane loggedAvatarHost;
    @FXML
    private Label loggedUserNameLabel;
    @FXML
    private Label loggedUserCodeLabel;
    @FXML
    private StackPane newsTickerViewport;
    @FXML
    private HBox shellNavAccueil;
    @FXML
    private HBox shellNavProduits;
    @FXML
    private HBox shellNavRdv;
    @FXML
    private HBox shellNavEvents;
    @FXML
    private HBox shellNavBlog;
    @FXML
    private Hyperlink shellConnexionLink;
    @FXML
    private Button shellSignupBtn;

    /** Dernière page chargée (pour surbrillance nav). */
    private String currentShellPageKey = "produits";

    private HBox newsTickerTrack;
    private HBox newsTickerSeg1;
    private Timeline newsTickerTimeline;
    private double newsTickerLastSegmentWidth = -1;

    private final List<Image> heroSlides = new ArrayList<>();
    private int heroSlideIndex;
    private boolean heroShowingA = true;
    private final UserNotificationService userNotificationService = new UserNotificationService();
    private final ContextMenu userNotifContextMenu = new ContextMenu();
    private static final DateTimeFormatter USER_NOTIF_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRENCH);
    private static final int PAGE_CACHE_LIMIT = 6;
    private final Map<String, CachedShellPage> pageCache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedShellPage> eldest) {
            return size() > PAGE_CACHE_LIMIT;
        }
    };

    private record CachedShellPage(Parent pageRoot, Object controller) {
    }

    @FXML
    private void initialize() {
        if (langCombo != null) {
            langCombo.getItems().addAll("FR", "EN");
            langCombo.getSelectionModel().selectFirst();
        }
        Platform.runLater(() -> {
            refreshTopNavState();
            configureNotificationsMenu();
            optimizeShellScrolling();
            initGlobalBackground();
            initHeroBackground();
            initNewsTicker();
            try {
                loadPage(MainApp.consumePendingPublicPage());
            } catch (IOException e) {
                alert(Alert.AlertType.ERROR, "Page", e.getMessage());
            }
            if (shellScrollPane != null) {
                shellScrollPane.setVvalue(0);
            }
        });
    }

    private void optimizeShellScrolling() {
        if (shellScrollPane != null) {
            shellScrollPane.setPannable(true);
            shellScrollPane.setCache(true);
            shellScrollPane.setCacheHint(CacheHint.SPEED);
        }
        if (shellScrollContentVBox != null) {
            shellScrollContentVBox.setCache(true);
            shellScrollContentVBox.setCacheHint(CacheHint.SPEED);
        }
        if (pageContentHost != null) {
            pageContentHost.setCache(true);
            pageContentHost.setCacheHint(CacheHint.SPEED);
        }
    }

    private void initGlobalBackground() {
        if (globalBgA == null || globalBgB == null) {
            return;
        }
        List<Image> shared = loadHomeSharedImages();
        Image chosen = shared.isEmpty() ? new Image(GLOBAL_BG_FALLBACK_URL, true) : shared.get(0);

        StackPane wrap = (StackPane) globalBgA.getParent();
        for (ImageView iv : new ImageView[]{globalBgA, globalBgB}) {
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setTranslateX(0);
            iv.fitWidthProperty().bind(wrap.widthProperty());
            iv.fitHeightProperty().bind(wrap.heightProperty());
        }

        globalBgA.setImage(chosen);
        globalBgA.setOpacity(GLOBAL_BG_MAX_OPACITY);
        globalBgB.setImage(null);
        globalBgB.setOpacity(0);
        globalBgB.setVisible(false);
    }

    private void initHeroBackground() {
        if (heroBgImageA == null || heroBgImageB == null) {
            return;
        }
        heroSlides.clear();
        heroSlides.addAll(slidesForHomeDiaporamas());
        if (heroSlides.isEmpty()) {
            return;
        }

        StackPane wrap = (StackPane) heroBgImageA.getParent();
        for (ImageView iv : new ImageView[]{heroBgImageA, heroBgImageB}) {
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setTranslateX(0);
            iv.fitWidthProperty().bind(wrap.widthProperty());
            iv.fitHeightProperty().bind(wrap.heightProperty());
        }

        if (heroSlides.size() < 2) {
            heroBgImageA.setImage(heroSlides.get(0));
            heroBgImageA.setOpacity(1);
            heroBgImageB.setImage(null);
            heroBgImageB.setOpacity(0);
            heroBgImageB.setVisible(false);
            return;
        }

        heroSlideIndex = 0;
        heroShowingA = true;
        heroBgImageA.setImage(heroSlides.get(0));
        heroBgImageA.setOpacity(1);
        heroBgImageA.setVisible(true);
        heroBgImageB.setImage(heroSlides.get(1));
        heroBgImageB.setOpacity(0);
        heroBgImageB.setVisible(true);

        PauseTransition pause = new PauseTransition(HERO_SLIDE_INTERVAL);
        pause.setOnFinished(e -> crossfadeHeroNext(pause));
        pause.play();
    }

    private void crossfadeHeroNext(PauseTransition pause) {
        if (heroSlides.size() < 2 || pause == null) {
            return;
        }

        ImageView visible = heroShowingA ? heroBgImageA : heroBgImageB;
        ImageView hidden = heroShowingA ? heroBgImageB : heroBgImageA;

        int nextIdx = (heroSlideIndex + 1) % heroSlides.size();
        hidden.setImage(heroSlides.get(nextIdx));
        hidden.setOpacity(0);
        hidden.setVisible(true);

        FadeTransition fadeOut = new FadeTransition(HERO_CROSSFADE_DURATION, visible);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        FadeTransition fadeIn = new FadeTransition(HERO_CROSSFADE_DURATION, hidden);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ParallelTransition crossfade = new ParallelTransition(fadeOut, fadeIn);
        crossfade.setOnFinished(e -> {
            visible.setOpacity(0);
            visible.setVisible(false);
            heroSlideIndex = nextIdx;
            heroShowingA = !heroShowingA;
            pause.playFromStart();
        });
        crossfade.play();
    }

    private List<Image> loadHomeSharedImages() {
        List<Image> out = new ArrayList<>();
        for (String name : HOME_SHARED_PHOTO_FILES) {
            URL u = getClass().getResource("/images/home/" + name);
            if (u != null) {
                out.add(new Image(u.toExternalForm(), true));
            }
        }
        return out;
    }

    private List<Image> slidesForHomeDiaporamas() {
        List<Image> slides = loadHomeSharedImages();
        if (slides.size() >= 2) {
            return slides;
        }
        if (slides.size() == 1) {
            slides.add(slides.get(0));
            return slides;
        }
        slides.clear();
        for (String url : HOME_PHOTO_FALLBACK_URLS) {
            slides.add(new Image(url, true));
        }
        return slides;
    }

    private void initNewsTicker() {
        if (newsTickerViewport == null) {
            return;
        }
        newsTickerTrack = new HBox(0);
        newsTickerTrack.setAlignment(Pos.CENTER_LEFT);
        newsTickerTrack.getStyleClass().add("home-news-ticker-track");
        newsTickerSeg1 = buildNewsTickerSegment(NEWS_TICKER_HEADLINES);
        HBox seg2 = buildNewsTickerSegment(NEWS_TICKER_HEADLINES);
        newsTickerTrack.getChildren().setAll(newsTickerSeg1, seg2);
        newsTickerViewport.getChildren().setAll(newsTickerTrack);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(newsTickerViewport.widthProperty());
        clip.heightProperty().bind(newsTickerViewport.heightProperty());
        newsTickerViewport.setClip(clip);

        newsTickerSeg1.layoutBoundsProperty().addListener((obs, prev, cur) -> Platform.runLater(this::restartNewsTickerIfReady));
        Platform.runLater(this::restartNewsTickerIfReady);
    }

    private static HBox buildNewsTickerSegment(List<String> headlines) {
        HBox seg = new HBox(8);
        seg.setAlignment(Pos.CENTER_LEFT);
        seg.getStyleClass().add("home-news-ticker-seg");
        for (int i = 0; i < headlines.size(); i++) {
            if (i > 0) {
                Label dot = new Label("•");
                dot.getStyleClass().add("home-news-ticker-sep");
                seg.getChildren().add(dot);
            }
            Label chev = new Label(">");
            chev.getStyleClass().add("home-news-ticker-chev");
            Label line = new Label(headlines.get(i));
            line.getStyleClass().add("home-news-ticker-item");
            seg.getChildren().addAll(chev, line);
        }
        return seg;
    }

    private void restartNewsTickerIfReady() {
        if (newsTickerTrack == null || newsTickerSeg1 == null || newsTickerViewport == null) {
            return;
        }
        newsTickerTrack.applyCss();
        newsTickerTrack.layout();
        double segmentW = newsTickerSeg1.getBoundsInLocal().getWidth();
        if (segmentW < 1) {
            return;
        }
        if (Math.abs(segmentW - newsTickerLastSegmentWidth) < 0.5 && newsTickerTimeline != null
                && newsTickerTimeline.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        newsTickerLastSegmentWidth = segmentW;
        if (newsTickerTimeline != null) {
            newsTickerTimeline.stop();
        }
        newsTickerTrack.setTranslateX(0);
        double durationSec = clamp(segmentW / NEWS_TICKER_PIXELS_PER_SEC, 16, 90);
        newsTickerTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(newsTickerTrack.translateXProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(durationSec),
                        new KeyValue(newsTickerTrack.translateXProperty(), -segmentW, Interpolator.LINEAR)));
        newsTickerTimeline.setCycleCount(Timeline.INDEFINITE);
        newsTickerTimeline.play();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void refreshTopNavState() {
        var u = AppState.getCurrentUser();
        boolean logged = u != null;
        if (guestNavActions != null) {
            guestNavActions.setVisible(!logged);
            guestNavActions.setManaged(!logged);
        }
        if (loggedNavActions != null) {
            loggedNavActions.setVisible(logged);
            loggedNavActions.setManaged(logged);
        }
        if (loggedShortcutIcons != null) {
            loggedShortcutIcons.setVisible(logged);
            loggedShortcutIcons.setManaged(logged);
        }
        if (logged) {
            String name = ((u.getPrenom() != null ? u.getPrenom().trim() : "") + " "
                    + (u.getNom() != null ? u.getNom().trim() : "")).trim();
            if (name.isBlank()) {
                name = u.getEmail();
            }
            if (loggedUserNameLabel != null) {
                loggedUserNameLabel.setText(name);
            }
            if (loggedUserCodeLabel != null) {
                loggedUserCodeLabel.setText(String.format("%04d", Math.max(0, u.getId())));
            }
            if (loggedAvatarHost != null) {
                loggedAvatarHost.getChildren().setAll(
                        UserAvatarGraphic.build(u, 42, UserAvatarGraphic.initialsFor(u), "home-topbar-avatar"));
            }
            refreshUserNotificationBadge();
        }
    }

    private void configureNotificationsMenu() {
        if (userNotifMenuButton != null) {
            userNotifMenuButton.setOnAction(e -> toggleUserNotificationsMenu());
        }
    }

    private void toggleUserNotificationsMenu() {
        if (userNotifMenuButton == null) {
            return;
        }
        if (userNotifContextMenu.isShowing()) {
            userNotifContextMenu.hide();
            return;
        }
        rebuildUserNotificationsMenuItems();
        userNotifContextMenu.show(userNotifMenuButton, Side.BOTTOM, 0, 6);
    }

    private void refreshUserNotificationBadge() {
        if (userNotifBadgeLabel == null) {
            return;
        }
        var user = AppState.getCurrentUser();
        if (user == null) {
            userNotifBadgeLabel.setVisible(false);
            userNotifBadgeLabel.setManaged(false);
            return;
        }
        try {
            int unread = userNotificationService.countUnreadForUser(user.getId());
            userNotifBadgeLabel.setText(String.valueOf(unread));
            userNotifBadgeLabel.setVisible(unread > 0);
            userNotifBadgeLabel.setManaged(unread > 0);
        } catch (Exception ignored) {
            userNotifBadgeLabel.setVisible(false);
            userNotifBadgeLabel.setManaged(false);
        }
    }

    private void rebuildUserNotificationsMenuItems() {
        if (userNotifMenuButton == null) {
            return;
        }
        userNotifContextMenu.getItems().clear();
        var user = AppState.getCurrentUser();
        if (user == null) {
            return;
        }
        try {
            MenuItem header = new MenuItem("Notifications");
            header.setDisable(true);
            userNotifContextMenu.getItems().add(header);
            List<UserNotificationItem> notifications = userNotificationService.listLatestForUser(user.getId(), 5);
            if (notifications.isEmpty()) {
                MenuItem emptyItem = new MenuItem("Aucune notification.");
                emptyItem.setDisable(true);
                userNotifContextMenu.getItems().add(emptyItem);
            } else {
                for (UserNotificationItem item : notifications) {
                    HBox card = new HBox(10);
                    card.setPrefWidth(320);
                    card.getStyleClass().add("public-notif-item");
                    card.getStyleClass().add(notificationTypeStyleClass(item));
                    Label icon = new Label(notificationIcon(item));
                    icon.getStyleClass().add("public-notif-item-icon");
                    Label title = new Label(item.getResume() != null ? item.getResume() : "Notification");
                    title.setWrapText(true);
                    title.getStyleClass().add("public-notif-item-title");
                    String ts = item.getDateCreation() != null ? USER_NOTIF_TIME_FMT.format(item.getDateCreation()) : "";
                    Label meta = new Label(ts);
                    meta.getStyleClass().add("public-notif-item-meta");
                    VBox textCol = new VBox(2, title, meta);
                    card.getChildren().addAll(icon, textCol);
                    CustomMenuItem menuItem = new CustomMenuItem(card, true);
                    menuItem.setOnAction(e -> onUserNotificationClick(item));
                    userNotifContextMenu.getItems().add(menuItem);
                }
            }
            Label allLabel = new Label("Voir\u00A0toutes\u00A0les\u00A0notifications");
            allLabel.getStyleClass().add("public-notif-see-all");
            allLabel.setWrapText(false);
            allLabel.setMinWidth(Region.USE_PREF_SIZE);
            HBox allWrap = new HBox(allLabel);
            allWrap.setAlignment(Pos.CENTER);
            allWrap.setPrefWidth(320);
            allWrap.getStyleClass().add("public-notif-see-all-wrap");
            CustomMenuItem allItem = new CustomMenuItem(allWrap, true);
            allItem.setOnAction(e -> openInShell("notifications"));
            userNotifContextMenu.getItems().add(allItem);
            refreshUserNotificationBadge();
        } catch (Exception ex) {
            MenuItem errorItem = new MenuItem("Impossible de charger les notifications.");
            errorItem.setDisable(true);
            userNotifContextMenu.getItems().add(errorItem);
        }
    }

    private static String notificationTypeStyleClass(UserNotificationItem item) {
        String code = item != null && item.getTypeCode() != null ? item.getTypeCode() : "";
        return switch (code) {
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "public-notif-item-msg";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED -> "public-notif-item-refused";
            default -> "public-notif-item-accepted";
        };
    }

    private static String notificationIcon(UserNotificationItem item) {
        String code = item != null && item.getTypeCode() != null ? item.getTypeCode() : "";
        return switch (code) {
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "\u2709";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED -> "\u2716";
            default -> "\u2713";
        };
    }

    private void onUserNotificationClick(UserNotificationItem item) {
        if (item == null) {
            return;
        }
        try {
            userNotificationService.markAsRead(item.getId());
            if (item.getEvenementId() != null && item.getEvenementId() > 0) {
                AppState.setPendingPublicEventDetailId(item.getEvenementId());
                openInShell("event-detail");
            } else {
                openInShell("notifications");
            }
            userNotifContextMenu.hide();
            refreshUserNotificationBadge();
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Notifications", ex.getMessage());
        }
    }

    /** Charge une page dans la zone centrale (clés : produits, rdv, events, blog, login, signup). */
    public void loadPage(String pageId) throws IOException {
        if (pageContentHost == null) {
            return;
        }
        currentShellPageKey = pageId != null && !pageId.isBlank() ? pageId.trim() : "produits";
        String path = resolvePagePath(currentShellPageKey);
        CachedShellPage cached = pageCache.get(path);
        Parent page;
        Object ctrl;
        if (cached != null) {
            page = cached.pageRoot();
            ctrl = cached.controller();
        } else {
            URL url = getClass().getResource(path);
            if (url == null) {
                throw new IOException("Page introuvable : " + path);
            }
            FXMLLoader loader = new FXMLLoader(url);
            page = loader.load();
            ctrl = loader.getController();
            pageCache.put(path, new CachedShellPage(page, ctrl));
        }
        pageContentHost.getChildren().setAll(page);
        StackPane.setAlignment(page, Pos.TOP_CENTER);
        /* Après attache au graphe : évite états bizarres (contrôles, styles) avant init données. */
        if (ctrl instanceof PublicShellAware aware) {
            aware.setPublicShell(this);
            aware.onShellReady();
        }
        applyShellHeroForPage(currentShellPageKey);
        refreshTopNavState();
        updatePublicNavHighlight();
        if (shellScrollPane != null) {
            shellScrollPane.setVvalue(0);
        }
    }

    private void clearPublicNavHighlight() {
        HBox[] navBoxes = {shellNavAccueil, shellNavProduits, shellNavRdv, shellNavEvents, shellNavBlog};
        for (HBox box : navBoxes) {
            if (box != null) {
                box.getStyleClass().remove("nav-item-active");
            }
        }
        if (shellConnexionLink != null) {
            shellConnexionLink.getStyleClass().remove("nav-auth-active");
        }
        if (shellSignupBtn != null) {
            shellSignupBtn.getStyleClass().remove("nav-auth-active");
        }
    }

    private void updatePublicNavHighlight() {
        clearPublicNavHighlight();
        String id = normalizeNavPageKey(currentShellPageKey);
        switch (id) {
            case "produits" -> addNavItemActive(shellNavProduits);
            case "rdv" -> addNavItemActive(shellNavRdv);
            case "events" -> addNavItemActive(shellNavEvents);
            case "blog" -> addNavItemActive(shellNavBlog);
            case "login" -> {
                if (shellConnexionLink != null) {
                    shellConnexionLink.getStyleClass().add("nav-auth-active");
                }
            }
            case "signup" -> {
                if (shellSignupBtn != null) {
                    shellSignupBtn.getStyleClass().add("nav-auth-active");
                }
            }
            default -> {
                /* pages non listées : pas de surbrillance centrale */
            }
        }
    }

    private static void addNavItemActive(HBox box) {
        if (box != null && !box.getStyleClass().contains("nav-item-active")) {
            box.getStyleClass().add("nav-item-active");
        }
    }

    private static String normalizeNavPageKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "produits";
        }
        String x = raw.toLowerCase(Locale.ROOT).trim();
        return switch (x) {
            case "rendez-vous", "rendezvous", "rdv-booking", "rdv-creneau", "rdv-type", "rdv-info" -> "rdv";
            case "événements", "evenements" -> "events";
            case "event-detail", "evenement", "événement" -> "events";
            case "connexion", "login" -> "login";
            case "signup", "inscription", "register" -> "signup";
            case "products" -> "produits";
            default -> x;
        };
    }

    /**
     * Listes et formulaires (RDV, événements, etc.) : pas de grand visuel hero ni d’espace réservé au-dessus du contenu.
     */
    private void applyShellHeroForPage(String pageId) {
        boolean compact = isCompactPublicPage(pageId);
        boolean authPage = isAuthPublicPage(pageId);
        applyCompactShellBackdrop();
        boolean showHero = !compact;
        if (shellHeroLayer != null) {
            shellHeroLayer.setVisible(showHero);
            shellHeroLayer.setManaged(showHero);
        }
        if (shellHeroScrollSpacer != null) {
            if (showHero) {
                shellHeroScrollSpacer.setMinHeight(320);
                shellHeroScrollSpacer.setPrefHeight(Region.USE_COMPUTED_SIZE);
                shellHeroScrollSpacer.setMaxHeight(Double.MAX_VALUE);
            } else {
                shellHeroScrollSpacer.setMinHeight(0);
                shellHeroScrollSpacer.setPrefHeight(0);
                shellHeroScrollSpacer.setMaxHeight(0);
            }
        }
        if (shellScrollContentVBox != null) {
            if (compact) {
                if (!shellScrollContentVBox.getStyleClass().contains(SHELL_LISTING_MODE_CLASS)) {
                    shellScrollContentVBox.getStyleClass().add(SHELL_LISTING_MODE_CLASS);
                }
            } else {
                shellScrollContentVBox.getStyleClass().remove(SHELL_LISTING_MODE_CLASS);
            }
            if (authPage) {
                if (!shellScrollContentVBox.getStyleClass().contains(SHELL_AUTH_MODE_CLASS)) {
                    shellScrollContentVBox.getStyleClass().add(SHELL_AUTH_MODE_CLASS);
                }
            } else {
                shellScrollContentVBox.getStyleClass().remove(SHELL_AUTH_MODE_CLASS);
            }
        }
    }

    /**
     * Rétablit le fond photo d’accueil et le voile semi-transparent (l’ancien voile opaque est retiré).
     */
    private void applyCompactShellBackdrop() {
        if (globalBgA != null) {
            globalBgA.setOpacity(GLOBAL_BG_MAX_OPACITY);
        }
        if (globalBgB != null) {
            globalBgB.setOpacity(0);
        }
        if (globalVeilRegion != null) {
            globalVeilRegion.getStyleClass().remove("home-global-veil-compact");
        }
    }

    /** Pages publiques sans hero : contenu remonte sous la barre de navigation. */
    private static boolean isCompactPublicPage(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return false;
        }
        return switch (pageId.trim().toLowerCase(Locale.ROOT)) {
            case "rdv", "rendez-vous", "rendezvous", "rdv-booking", "rdv-creneau", "rdv-type", "rdv-info",
                    "events", "evenements", "événements",
                    "event-detail", "evenement", "événement",
                    "login", "connexion", "signup", "inscription", "register",
                    "notifications", "notifs" -> true;
            default -> false;
        };
    }

    private static boolean isAuthPublicPage(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return false;
        }
        return switch (pageId.trim().toLowerCase(Locale.ROOT)) {
            case "login", "connexion", "signup", "inscription", "register" -> true;
            default -> false;
        };
    }

    private static String resolvePagePath(String pageId) {
        if (pageId == null) {
            return "/fxml/pages/page-produits.fxml";
        }
        return switch (pageId.toLowerCase(Locale.ROOT)) {
            case "produits", "products" -> "/fxml/pages/page-produits.fxml";
            case "rdv", "rendez-vous", "rendezvous" -> "/fxml/pages/page-rdv.fxml";
            case "rdv-booking", "rdv-creneau" -> "/fxml/pages/page-rdv-booking.fxml";
            case "rdv-type" -> "/fxml/pages/page-rdv-type.fxml";
            case "rdv-info" -> "/fxml/pages/page-rdv-info.fxml";
            case "events", "evenements", "événements" -> "/fxml/pages/page-events.fxml";
            case "event-detail", "evenement", "événement" -> "/fxml/pages/page-event-detail.fxml";
            case "blog" -> "/fxml/pages/page-blog.fxml";
            case "login", "connexion" -> "/fxml/pages/page-login.fxml";
            case "signup", "inscription", "register" -> "/fxml/pages/page-signup.fxml";
            case "notifications", "notifs" -> "/fxml/pages/page-notifications.fxml";
            default -> "/fxml/pages/page-produits.fxml";
        };
    }

    private void openInShell(String pageId) {
        try {
            loadPage(pageId);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    @FXML
    private void onNavAccueil(MouseEvent e) {
        goHome();
    }

    private void goHome() {
        try {
            MainApp.showHome();
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Accueil", ex.getMessage());
        }
    }

    @FXML
    private void onNavProduits(MouseEvent e) {
        openInShell("produits");
    }

    @FXML
    private void onNavRdv(MouseEvent e) {
        openInShell("rdv");
    }

    @FXML
    private void onNavEvents(MouseEvent e) {
        openInShell("events");
    }

    @FXML
    private void onNavBlog(MouseEvent e) {
        openInShell("blog");
    }

    @FXML
    private void onToggleTheme() {
        MainApp.toggleTheme();
    }

    @FXML
    private void onUserShortcutBook() {
        openInShell("blog");
    }

    @FXML
    private void onUserShortcutBag() {
        openInShell("produits");
    }

    @FXML
    private void onUserShortcutNotifications() {
        toggleUserNotificationsMenu();
    }

    @FXML
    private void onNavConnexion() {
        openInShell("login");
    }

    @FXML
    private void onRegister() {
        openInShell("signup");
    }

    @FXML
    private void onLogout() throws IOException {
        AppState.clear();
        MainApp.showLogin();
    }

    @FXML
    private void onLogoutLinkClick(MouseEvent event) {
        if (event != null) {
            event.consume();
        }
    }

    @FXML
    private void onOpenMyProfile(MouseEvent event) {
        if (AppState.getCurrentUser() == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/front/admin/user/admin-my-profile.fxml"));
            Parent root = loader.load();
            AdminMyProfileController ctrl = loader.getController();
            Stage dialog = new Stage();
            ctrl.setStage(dialog);
            dialog.initOwner(MainApp.getPrimaryStage());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            dialog.setScene(scene);
            Stage owner = MainApp.getPrimaryStage();
            if (owner != null) {
                dialog.setWidth(owner.getWidth());
                dialog.setHeight(owner.getHeight());
                dialog.setX(owner.getX());
                dialog.setY(owner.getY());
            }
            dialog.showAndWait();
            refreshTopNavState();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Profil", e.getMessage());
        }
    }

    @FXML
    private void onShellFooterAccueil() {
        goHome();
    }

    @FXML
    private void onShellFooterProduits() {
        openInShell("produits");
    }

    @FXML
    private void onShellFooterRdv() {
        openInShell("rdv");
    }

    @FXML
    private void onShellFooterEvents() {
        openInShell("events");
    }

    @FXML
    private void onShellFooterBlog() {
        openInShell("blog");
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg != null ? msg : "");
        a.showAndWait();
    }
}
