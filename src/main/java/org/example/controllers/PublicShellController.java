package org.example.controllers;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.example.MainApp;
import org.example.models.AppLanguage;
import org.example.models.Appointment;
import org.example.models.Role;
import org.example.models.User;
import org.example.models.UserNotificationItem;
import org.example.services.AppointmentService;
import org.example.services.EventService;
import org.example.services.NotificationService;
import org.example.services.UserNotificationService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.CombinedPublicNotifications;
import org.example.utils.HomeHeroTicker;
import org.example.utils.NewsTickerHeadlines;
import org.example.utils.UserAvatarGraphic;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
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

    private static final double HERO_TICKER_PIXELS_PER_SEC = 40;

    private static final double NEWS_TICKER_PIXELS_PER_SEC = 44;
    private static final double NEWS_TICKER_BAR_HEIGHT = 56;

    @FXML
    private ScrollPane shellScrollPane;
    private static final String SHELL_LISTING_MODE_CLASS = "public-shell--listing-mode";
    private static final String SHELL_AUTH_MODE_CLASS = "public-shell--auth-mode";

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
    private ComboBox<AppLanguage> langCombo;
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
    private Hyperlink dashboardQuickLink;
    @FXML
    private HBox publicNewsTickerBar;
    @FXML
    private StackPane newsTickerViewport;
    @FXML
    private HBox shellNavAccueil;
    @FXML
    private HBox shellNavProduits;
    @FXML
    private HBox shellNavMesCommandes;
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
    @FXML
    private StackPane patientNotifBellHost;
    @FXML
    private Label patientNotifBadge;

    private final AppointmentService shellAppointmentService = new AppointmentService();
    private final EventService shellEventService = new EventService();
    private final UserService shellUserService = new UserService();

    /** Dernière page chargée (pour surbrillance nav). */
    private String currentShellPageKey = "produits";
    private Object currentPageController;

    private HBox newsTickerTrack;
    private HBox newsTickerSeg1;
    private Timeline newsTickerTimeline;
    private double newsTickerLastSegmentWidth = -1;

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
            langCombo.getItems().setAll(AppLanguage.supported());
            langCombo.setVisibleRowCount(5);
            langCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(AppLanguage language) {
                    return language != null ? language.shortLabel() : "FR";
                }

                @Override
                public AppLanguage fromString(String string) {
                    return AppLanguage.fromCode(string);
                }
            });
            langCombo.setCellFactory(cb -> new ListCell<>() {
                @Override
                protected void updateItem(AppLanguage item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.label());
                    }
                    setGraphic(null);
                }
            });
            langCombo.setButtonCell(new ListCell<>() {
                private final Label icon = new Label("🈯");

                {
                    icon.getStyleClass().add("lang-combo-icon");
                }

                @Override
                protected void updateItem(AppLanguage item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("FR");
                        setGraphic(icon);
                    } else {
                        setText(item.shortLabel());
                        setGraphic(icon);
                    }
                }
            });
            AppLanguage current = AppState.getCurrentLanguage();
            langCombo.getSelectionModel().select(current);
            langCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV == null || newV == oldV) {
                    return;
                }
                AppState.setCurrentLanguage(newV);
                notifyCurrentPageLanguageChanged();
            });
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
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String detail = e.getMessage();
                if (detail == null || detail.isBlank()) {
                    detail = cause.getMessage();
                }
                alert(Alert.AlertType.ERROR, "Chargement de page",
                        "Impossible d’afficher la page publique.\n\n"
                                + (detail != null && !detail.isBlank() ? detail + "\n\n" : "")
                                + "Astuce : faites `mvn clean compile` puis relancez ; vérifiez les chemins CSS/FXML sous src/main/resources.");
                cause.printStackTrace();
            }
            if (shellScrollPane != null) {
                shellScrollPane.setVvalue(0);
            }
            ensureTopNavPinned(0);
        });
    }

    private void ensureTopNavPinned(int attempt) {
        if (shellScrollPane == null) {
            return;
        }
        Scene sc = shellScrollPane.getScene();
        if (sc == null && attempt < 16) {
            Platform.runLater(() -> ensureTopNavPinned(attempt + 1));
            return;
        }
        if (sc == null) {
            return;
        }
        Node nav = sc.getRoot().lookup(".top-nav");
        if (nav instanceof Region r) {
            r.setMinHeight(88);
            r.setPrefHeight(88);
            r.setMaxHeight(120);
            r.setVisible(true);
            r.setManaged(true);
        }
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
        StackPane wrap = (StackPane) heroBgImageA.getParent();
        List<Image> slides = resolveHeroBackgroundImages();
        if (slides.isEmpty()) {
            return;
        }
        HomeHeroTicker.install(wrap, heroBgImageA, heroBgImageB, slides, HERO_TICKER_PIXELS_PER_SEC);
        HomeHeroTicker.bindPhotoWrapFullViewportBelowNav(wrap);
        clipShellHeroLayerToBounds();
    }

    /** Évite que vignette / images du hero débordent et recouvrent le bandeau « Actualités » (BorderPane bottom). */
    private void clipShellHeroLayerToBounds() {
        if (shellHeroLayer == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(shellHeroLayer.widthProperty());
        clip.heightProperty().bind(shellHeroLayer.heightProperty());
        shellHeroLayer.setClip(clip);
    }

    private List<Image> resolveHeroBackgroundImages() {
        List<Image> ticker = HomeHeroTicker.loadFromResources(getClass());
        if (ticker.size() >= 2) {
            return ticker;
        }
        return slidesForHomeDiaporamas();
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
        List<String> headlines = NewsTickerHeadlines.loadFromDatabase(shellEventService);
        newsTickerSeg1 = buildNewsTickerSegment(headlines);
        HBox seg2 = buildNewsTickerSegment(headlines);
        newsTickerTrack.getChildren().setAll(newsTickerSeg1, seg2);
        newsTickerViewport.getChildren().setAll(newsTickerTrack);
        newsTickerViewport.setMinHeight(NEWS_TICKER_BAR_HEIGHT);
        newsTickerViewport.setPrefHeight(NEWS_TICKER_BAR_HEIGHT);

        Rectangle clip = new Rectangle();
        clip.setHeight(NEWS_TICKER_BAR_HEIGHT);
        clip.widthProperty().bind(newsTickerViewport.widthProperty());
        newsTickerViewport.setClip(clip);

        newsTickerSeg1.layoutBoundsProperty().addListener((obs, prev, cur) -> Platform.runLater(this::restartNewsTickerIfReady));
        newsTickerViewport.widthProperty().addListener((o, prev, cur) -> {
            if (cur != null && cur.doubleValue() > 8) {
                Platform.runLater(this::restartNewsTickerIfReady);
            }
        });
        if (publicNewsTickerBar != null) {
            publicNewsTickerBar.setVisible(true);
            publicNewsTickerBar.setManaged(true);
            publicNewsTickerBar.setMinHeight(NEWS_TICKER_BAR_HEIGHT);
            publicNewsTickerBar.setPrefHeight(NEWS_TICKER_BAR_HEIGHT);
        }
        Platform.runLater(this::restartNewsTickerIfReady);
        Platform.runLater(() -> Platform.runLater(this::restartNewsTickerIfReady));
    }

    private static HBox buildNewsTickerSegment(List<String> headlines) {
        List<String> lines = headlines == null || headlines.isEmpty()
                ? List.of("Bienvenue sur AutiCare")
                : headlines;
        HBox seg = new HBox(8);
        seg.setAlignment(Pos.CENTER_LEFT);
        seg.getStyleClass().add("home-news-ticker-seg");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                Label dot = new Label("•");
                dot.getStyleClass().add("home-news-ticker-sep");
                seg.getChildren().add(dot);
            }
            Label chev = new Label(">");
            chev.getStyleClass().add("home-news-ticker-chev");
            Label line = new Label(lines.get(i));
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
        if (loggedUserCodeLabel != null) {
            loggedUserCodeLabel.setVisible(false);
            loggedUserCodeLabel.setManaged(false);
        }
        /* Une seule cloche : RDV + événements fusionnés dans userNotifMenuButton (pas de doublon patient). */
        if (patientNotifBellHost != null) {
            patientNotifBellHost.setVisible(false);
            patientNotifBellHost.setManaged(false);
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
        if (userNotifContextMenu != null) {
            userNotifContextMenu.getStyleClass().add("public-notif-context-menu");
        }
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
            int unread = CombinedPublicNotifications.totalUnread(user, userNotificationService, shellAppointmentService);
            String badge = unread > 99 ? "99+" : String.valueOf(Math.max(0, unread));
            userNotifBadgeLabel.setText(badge);
            userNotifBadgeLabel.setVisible(unread > 0);
            userNotifBadgeLabel.setManaged(unread > 0);
        } catch (Exception ignored) {
            userNotifBadgeLabel.setVisible(false);
            userNotifBadgeLabel.setManaged(false);
        }
    }

    public void refreshNotificationsBadge() {
        refreshUserNotificationBadge();
    }

    /** Rafraîchit les pastilles de la barre de navigation (notifications, RDV patient, etc.). */
    public void refreshPublicNavBadges() {
        refreshUserNotificationBadge();
        refreshPatientNotifBadge();
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
            Label headLbl = new Label("Notifications");
            headLbl.getStyleClass().add("public-notif-popup-header");
            CustomMenuItem header = new CustomMenuItem(headLbl, false);
            header.setHideOnClick(false);
            header.setDisable(true);
            header.getStyleClass().add("public-notif-menu-header");
            userNotifContextMenu.getItems().add(header);
            List<CombinedPublicNotifications.MergedPreview> merged =
                    CombinedPublicNotifications.buildMenuPreview(user, userNotificationService, shellAppointmentService);
            if (merged.isEmpty()) {
                MenuItem emptyItem = new MenuItem("Aucune notification.");
                emptyItem.setDisable(true);
                userNotifContextMenu.getItems().add(emptyItem);
            } else {
                for (CombinedPublicNotifications.MergedPreview row : merged) {
                    if (row.kind() == CombinedPublicNotifications.MergedKind.EVENT) {
                        UserNotificationItem item = row.eventItem();
                        if (item == null) {
                            continue;
                        }
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
                    } else {
                        Appointment ap = row.rdv();
                        if (ap == null) {
                            continue;
                        }
                        HBox card = new HBox(10);
                        card.setPrefWidth(320);
                        card.getStyleClass().add("public-notif-item");
                        card.getStyleClass().add("public-notif-item-rdv");
                        Label icon = new Label("📅");
                        icon.getStyleClass().add("public-notif-item-icon");
                        String summary = PatientRdvNotificationsDialog.summaryForMenu(ap, shellUserService);
                        Label title = new Label(summary);
                        title.setWrapText(true);
                        title.getStyleClass().add("public-notif-item-title");
                        String ts = ap.getDateHeure() != null ? USER_NOTIF_TIME_FMT.format(ap.getDateHeure()) : "";
                        Label meta = new Label("Rendez-vous · " + ts);
                        meta.getStyleClass().add("public-notif-item-meta");
                        VBox textCol = new VBox(2, title, meta);
                        card.getChildren().addAll(icon, textCol);
                        CustomMenuItem menuItem = new CustomMenuItem(card, true);
                        menuItem.setOnAction(e -> onMergedRdvNotificationClick(ap));
                        userNotifContextMenu.getItems().add(menuItem);
                    }
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
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED,
                 UserNotificationService.TYPE_RDV_REFUSED,
                 UserNotificationService.TYPE_RDV_CANCELLED -> "public-notif-item-refused";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_PENDING -> "public-notif-item-pending";
            case UserNotificationService.TYPE_RDV_ACCEPTED -> "public-notif-item-accepted";
            default -> "public-notif-item-accepted";
        };
    }

    private static String notificationIcon(UserNotificationItem item) {
        String code = item != null && item.getTypeCode() != null ? item.getTypeCode() : "";
        return switch (code) {
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "\u2709";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED,
                 UserNotificationService.TYPE_RDV_REFUSED,
                 UserNotificationService.TYPE_RDV_CANCELLED -> "\u2716";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_PENDING -> "\u23F3";
            case UserNotificationService.TYPE_RDV_ACCEPTED -> "\u2713";
            default -> "\u2713";
        };
    }

    private void onUserNotificationClick(UserNotificationItem item) {
        if (item == null) {
            return;
        }
        try {
            userNotificationService.markAsRead(item.getId());
            String c = item.getTypeCode() != null ? item.getTypeCode() : "";
            if (UserNotificationService.TYPE_RDV_ACCEPTED.equals(c)
                    || UserNotificationService.TYPE_RDV_REFUSED.equals(c)
                    || UserNotificationService.TYPE_RDV_CANCELLED.equals(c)) {
                openInShell("rdv");
            } else if (item.getEvenementId() != null && item.getEvenementId() > 0) {
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

    private void onMergedRdvNotificationClick(Appointment ap) {
        User u = AppState.getCurrentUser();
        if (ap == null || u == null) {
            return;
        }
        try {
            shellAppointmentService.markPatientDecisionRead(ap.getId(), u.getId());
            openInShell("rdv");
            userNotifContextMenu.hide();
            refreshUserNotificationBadge();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Notifications",
                    ex.getMessage() != null ? ex.getMessage() : "Impossible de mettre à jour la notification.");
        }
    }

    private void refreshPatientNotifBadge() {
        if (patientNotifBadge == null) {
            return;
        }
        User u = AppState.getCurrentUser();
        if (u == null || (u.getRole() != Role.PATIENT && u.getRole() != Role.PARENT)) {
            patientNotifBadge.setVisible(false);
            patientNotifBadge.setManaged(false);
            return;
        }
        try {
            int c = shellAppointmentService.countUnreadPatientDecisions(u.getId());
            patientNotifBadge.setText(c > 9 ? "9+" : String.valueOf(Math.max(0, c)));
            boolean show = c > 0;
            patientNotifBadge.setVisible(show);
            patientNotifBadge.setManaged(show);
        } catch (SQLException e) {
            patientNotifBadge.setVisible(false);
            patientNotifBadge.setManaged(false);
        }
    }

    @FXML
    private void onPatientNotifBell(MouseEvent event) {
        if (event != null) {
            event.consume();
        }
        User u = AppState.getCurrentUser();
        if (u == null || (u.getRole() != Role.PATIENT && u.getRole() != Role.PARENT)) {
            return;
        }
        try {
            Window owner = null;
            if (patientNotifBellHost != null && patientNotifBellHost.getScene() != null) {
                owner = patientNotifBellHost.getScene().getWindow();
            }
            if (owner == null && event != null && event.getSource() instanceof Node n && n.getScene() != null) {
                owner = n.getScene().getWindow();
            }
            PatientRdvNotificationsDialog.show(owner, u.getId(), shellAppointmentService, shellUserService);
            refreshPatientNotifBadge();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Notifications",
                    ex.getMessage() != null ? ex.getMessage() : "Impossible de charger les notifications.");
        }
    }

    /** Charge une page dans la zone centrale (clés : produits, rdv, events, blog, login, signup). */
    public void loadPage(String pageId) throws IOException {
        if (pageContentHost == null) {
            return;
        }
        currentShellPageKey = pageId != null && !pageId.isBlank() ? pageId.trim() : "produits";
        String path = resolvePagePath(currentShellPageKey);
        URL url = getClass().getResource(path);
        if (url == null) {
            throw new IOException("Page introuvable : " + path);
        }
        FXMLLoader loader = new FXMLLoader(url);
        Parent page = loader.load();
        Object ctrl = loader.getController();
        currentPageController = ctrl;
        pageContentHost.getChildren().setAll(page);
        StackPane.setAlignment(page, Pos.TOP_CENTER);
        /* Référence coque + chargement données (liste événements, fiche détail, etc.) une fois la page dans la scène. */
        if (ctrl instanceof PublicShellAware aware) {
            aware.setPublicShell(this);
            aware.onShellReady();
            aware.onShellLanguageChanged(AppState.getCurrentLanguage());
        }
        if (ctrl instanceof PublicShellAware aware) {
            aware.setPublicShell(this);
        }
        pageContentHost.getChildren().setAll(page);
        StackPane.setAlignment(page, Pos.TOP_CENTER);
        applyShellHeroForPage(currentShellPageKey);
        refreshTopNavState();
        updatePublicNavHighlight();
        if (shellScrollPane != null) {
            shellScrollPane.setVvalue(0);
        }
    }

    private void notifyCurrentPageLanguageChanged() {
        if (!(currentPageController instanceof PublicShellAware aware)) {
            return;
        }
        aware.onShellLanguageChanged(AppState.getCurrentLanguage());
    }

    private void clearPublicNavHighlight() {
        HBox[] navBoxes = {shellNavAccueil, shellNavProduits, shellNavMesCommandes, shellNavRdv, shellNavEvents, shellNavBlog};
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
            case "mes-commandes" -> addNavItemActive(shellNavMesCommandes);
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
            case "orders", "commandes", "panier", "cart", "checkout", "mes-commandes" -> "mes-commandes";
            default -> x;
        };
    }

    /**
     * Ajuste le bandeau hero, le spacer et le fond global selon la page.
     * Connexion / inscription : même bandeau photo que l’accueil ; RDV : fond global dédié.
     */
    private void applyShellHeroForPage(String pageId) {
        boolean compact = isCompactPublicPage(pageId);
        boolean authPage = isAuthPublicPage(pageId);
        boolean rdvPage = isRdvPageId(pageId);

        applyCompactShellBackdrop();

        if (globalBgA != null) {
            globalBgA.setVisible(rdvPage);
            globalBgA.setManaged(rdvPage);
            if (!rdvPage) {
                globalBgA.setOpacity(0);
            } else if (globalBgA.getOpacity() <= 0) {
                globalBgA.setOpacity(GLOBAL_BG_MAX_OPACITY);
            }
        }
        if (globalBgB != null) {
            globalBgB.setVisible(rdvPage);
            globalBgB.setManaged(rdvPage);
            if (!rdvPage) {
                globalBgB.setOpacity(0);
            }
        }

        /* Connexion / inscription : même bandeau photo que l’accueil (hero visible derrière la carte) */
        boolean showHero = !compact || authPage;
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

    private static boolean isRdvPageId(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return false;
        }
        return switch (pageId.trim().toLowerCase(Locale.ROOT)) {
            case "rdv", "rendez-vous", "rendezvous", "rdv-booking", "rdv-creneau", "rdv-type", "rdv-info" -> true;
            default -> false;
        };
    }

    private static String resolvePagePath(String pageId) {
        if (pageId == null) {
            return "/fxml/pages/page-produits.fxml";
        }
        return switch (pageId.toLowerCase(Locale.ROOT)) {
            case "produits", "products" -> "/fxml/pages/page-produits.fxml";
            case "panier", "cart" -> "/fxml/pages/page-panier.fxml";
            case "mes-commandes", "orders", "commandes" -> "/fxml/pages/page-mes-commandes.fxml";
            case "checkout", "paiement" -> "/fxml/pages/page-checkout.fxml";
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
    private void onNavMesCommandes(MouseEvent e) {
        openInShell("mes-commandes");
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
    private void onNavDashboard() {
        try {
            MainApp.openManagementSpace();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Gestion", e.getMessage());
        }
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
    private void onNavAvatar() {
        if (AppState.getCurrentUser() == null) {
            onNavConnexion();
        } else {
            onOpenMyProfile(null);
        }
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
    private void onClientOrderNotifications(MouseEvent event) {
        if (event != null) {
            event.consume();
        }
        openInShell("mes-commandes");
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
            MainApp.applyThemeToScene(scene);
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