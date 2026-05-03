package org.example.controllers;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
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
import org.example.MainApp;
import org.example.controllers.AdminMyProfileController;
import org.example.models.Product;
import org.example.models.Role;
import org.example.models.User;
import org.example.models.UserNotificationItem;
import org.example.services.EventService;
import org.example.services.AiRecommendationService;
import org.example.services.EventRegistrationService;
import org.example.services.CustomerOrderService;
import org.example.services.ProductService;
import org.example.services.UserNotificationService;
import org.example.services.AppointmentService;
import org.example.services.ProductService;
import org.example.services.UserService;
import org.example.models.Appointment;
import org.example.utils.AppState;
import org.example.utils.CombinedPublicNotifications;
import org.example.utils.UserAvatarGraphic;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.utils.ProductImageLoader;
import org.example.utils.HomeHeroTicker;
import org.example.utils.NewsTickerHeadlines;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HomeController {

    private static final java.time.format.DateTimeFormatter HOME_NOTIF_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm", java.util.Locale.FRENCH);

    /**
     * Images locales (fournies par l'utilisateur) affichées en slider sur l'accueil.
     * Ordre = ordre de défilement.
     */
    private static final String[] HOME_USER_SLIDER_IMAGE_PATHS = {
            "C:/Users/Administrator/.cursor/projects/c-Users-Administrator-Desktop-eya-rendez-vous-Validation-java-Jeudi/assets/c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_3937bc5d49a2c57ca8078ee8dc26d5b4_images_662339393_1682515246087110_420077270349823399_n-e207dc03-87fb-4139-8d21-038ad1f846c6.png",
            "C:/Users/Administrator/.cursor/projects/c-Users-Administrator-Desktop-eya-rendez-vous-Validation-java-Jeudi/assets/c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_3937bc5d49a2c57ca8078ee8dc26d5b4_images_649825682_1755182145458570_3568991923210223264_n__1_-266746d5-5656-4891-adfe-817f00b2af5c.png",
            "C:/Users/Administrator/.cursor/projects/c-Users-Administrator-Desktop-eya-rendez-vous-Validation-java-Jeudi/assets/c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_3937bc5d49a2c57ca8078ee8dc26d5b4_images_flat_750x_075_f-pad_750x1000_f8f8f8.u2-ec79f937-b43b-433b-8925-1e3e658add9e.png",
            "C:/Users/Administrator/.cursor/projects/c-Users-Administrator-Desktop-eya-rendez-vous-Validation-java-Jeudi/assets/c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_3937bc5d49a2c57ca8078ee8dc26d5b4_images_Capture-6826b853-01a0-4fa0-b738-d9377822d142.png",
            "C:/Users/Administrator/.cursor/projects/c-Users-Administrator-Desktop-eya-rendez-vous-Validation-java-Jeudi/assets/c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_3937bc5d49a2c57ca8078ee8dc26d5b4_images_65114438_s-a7c0e0a3-5497-4a8f-86c9-096bedef71e8.png"
    };
    /**
     * Désactivé temporairement : certains fichiers importés génèrent un rendu visuel non souhaité
     * (artefacts carrés au centre). Réactivez à true après remplacement par des visuels validés.
     */
    private static final boolean USE_USER_PROVIDED_HERO_SLIDES = false;

    /**
     * Fallback local (repo) si les images utilisateur ne sont pas trouvées.
     */
    private static final String[] HOME_SHARED_PHOTO_FILES = {
            "home-shared-1.png",
            "home-shared-2.png"
    };

    /** Si les fichiers locaux manquent : deux URL pour les diaporamas (hero, mission, témoignages). */
    private static final String[] HOME_PHOTO_FALLBACK_URLS = {
            "https://images.unsplash.com/photo-1516627145497-ae6968895b74?w=1600&q=85",
            "https://images.unsplash.com/photo-1544776193-7d62c3841120?w=1600&q=85"
    };

    /** Fond global statique si aucun fichier {@link #HOME_SHARED_PHOTO_FILES}. */
    private static final String GLOBAL_BG_FALLBACK_URL = HOME_PHOTO_FALLBACK_URLS[0];

    /** Opacité max d’un calque photo (le voile CSS complète la lisibilité). */
    private static final double GLOBAL_BG_MAX_OPACITY = 0.5;

    /** Défilement hero droite → gauche (images côte à côte), comme le bandeau actualités. */
    private static final double HERO_TICKER_PIXELS_PER_SEC = 40;

    private static final double NEWS_TICKER_PIXELS_PER_SEC = 44;
    /** Hauteur fixe du bandeau / clip (évite hauteur 0 au 1er layout ou après maximisation). */
    private static final double NEWS_TICKER_BAR_HEIGHT = 56;

    /** Dernier recours cascade : styles CTA hero sur la Scene (voir {@link #ensureHeroCtaStylesOnScene()}). */
    private static final String HERO_CTA_FORCE_CSS = urlToExternalForm(
            HomeController.class.getResource("/styles/home-hero-cta-force.css"));

    private static String urlToExternalForm(URL url) {
        return url != null ? url.toExternalForm() : null;
    }

    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox scrollContent;
    @FXML
    private ImageView globalBgA;
    @FXML
    private ImageView globalBgB;
    @FXML
    private ImageView heroBgImageA;
    @FXML
    private ImageView heroBgImageB;
    @FXML
    private ImageView missionImageView;
    @FXML
    private Button heroBtnProducts;
    @FXML
    private Button heroBtnEvents;
    @FXML
    private VBox aiProductsSection;
    @FXML
    private FlowPane aiProductsFlow;
    @FXML
    private VBox ctaGuest;
    @FXML
    private VBox ctaLoggedIn;
    @FXML
    private ComboBox<String> langCombo;
    @FXML
    private HBox guestNavActions;
    @FXML
    private HBox loggedShortcutIcons;
    private StackPane patientNotifBellHost;
    @FXML
    private Label patientNotifBadge;
    @FXML
    private HBox loggedNavActions;
    @FXML
    private StackPane loggedAvatarHost;
    @FXML
    private Label loggedUserNameLabel;
    @FXML
    private Label loggedUserCodeLabel;
    @FXML
    private Label homeNotifBadgeLabel;
    @FXML
    private Button homeNotifMenuButton;
    @FXML
    private VBox homeFooterSection;
    @FXML
    private VBox sectionAnchorMission;
    @FXML
    private VBox sectionAnchorProduits;
    @FXML
    private VBox sectionAnchorEvents;
    @FXML
    private VBox sectionAnchorBlog;
    @FXML
    private VBox sectionAnchorCommunity;
    @FXML
    private StackPane newsTickerViewport;
    private HBox newsTickerTrack;
    private HBox newsTickerSeg1;
    private Timeline newsTickerTimeline;
    private double newsTickerLastSegmentWidth = -1;
    private final ProductService productService = new ProductService();
    private final UserNotificationService userNotificationService = new UserNotificationService();
    private final AppointmentService appointmentService = new AppointmentService();
    private final EventService eventService = new EventService();
    private final EventRegistrationService eventRegistrationService = new EventRegistrationService();
    private final CustomerOrderService customerOrderService = new CustomerOrderService();
    private final AiRecommendationService aiRecommendationService = new AiRecommendationService();
    private final UserService userService = new UserService();
    private final ContextMenu homeNotifContextMenu = new ContextMenu();

    @FXML
    public void initialize() {
        if (langCombo != null) {
            langCombo.getItems().addAll("FR", "EN");
            langCombo.getSelectionModel().selectFirst();
        }
        Platform.runLater(() -> {
            refreshTopNavState();
            configureHomeNotificationsMenu();
            initGlobalBackground();
            initHeroBackground();
            applyMissionImageClip();
            populateAiProducts();
            refreshCtaState();
            setupHeroPulse();
            ensureHeroCtaStylesOnScene();
            setupScrollAnimations();
            initNewsTicker();
            applyPendingHomeScroll();
            ensureTopNavPinned(0);
        });
    }

    /** Après fond hero / ticker : garantit hauteur et visibilité de la barre (VBox + secours layout). */
    private void ensureTopNavPinned(int attempt) {
        if (scrollPane == null) {
            return;
        }
        Scene sc = scrollPane.getScene();
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

    private void initNewsTicker() {
        if (newsTickerViewport == null) {
            return;
        }
        Integer currentUserId = AppState.getCurrentUser() != null ? AppState.getCurrentUser().getId() : null;
        List<String> headlines = NewsTickerHeadlines.loadFromDatabase(
                eventService,
                productService,
                eventRegistrationService,
                customerOrderService,
                aiRecommendationService,
                currentUserId);
        newsTickerTrack = new HBox(0);
        newsTickerTrack.setAlignment(Pos.CENTER_LEFT);
        newsTickerTrack.getStyleClass().add("home-news-ticker-track");
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

    /** Après layout : défilement demandé depuis login / inscription / mot de passe oublié. */
    private void applyPendingHomeScroll() {
        String key = MainApp.consumePendingHomeScroll();
        if (key == null || key.isBlank()) {
            return;
        }
        Platform.runLater(() -> navigateHomeScroll(key.trim()));
    }

    private boolean isLoggedIn() {
        return AppState.getCurrentUser() != null;
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
            refreshHomeNotificationBadge();
        }
    }

    private void refreshHomeNotificationBadge() {
        if (homeNotifBadgeLabel == null) {
            return;
        }
        var user = AppState.getCurrentUser();
        if (user == null) {
            homeNotifBadgeLabel.setVisible(false);
            homeNotifBadgeLabel.setManaged(false);
            return;
        }
        try {
            int unread = CombinedPublicNotifications.totalUnread(user, userNotificationService, appointmentService);
            String badge = unread > 99 ? "99+" : String.valueOf(Math.max(0, unread));
            homeNotifBadgeLabel.setText(badge);
            homeNotifBadgeLabel.setVisible(unread > 0);
            homeNotifBadgeLabel.setManaged(unread > 0);
        } catch (SQLException ignored) {
            homeNotifBadgeLabel.setVisible(false);
            homeNotifBadgeLabel.setManaged(false);
        }
    }

    private void configureHomeNotificationsMenu() {
        if (homeNotifContextMenu != null) {
            homeNotifContextMenu.getStyleClass().add("public-notif-context-menu");
        }
        if (homeNotifMenuButton != null) {
            homeNotifMenuButton.setOnAction(e -> toggleHomeNotificationsMenu());
        }
    }

    private void toggleHomeNotificationsMenu() {
        if (homeNotifMenuButton == null) {
            return;
        }
        if (homeNotifContextMenu.isShowing()) {
            homeNotifContextMenu.hide();
            return;
        }
        rebuildHomeNotificationsMenuItems();
        homeNotifContextMenu.show(homeNotifMenuButton, Side.BOTTOM, 0, 6);
    }

    private void rebuildHomeNotificationsMenuItems() {
        if (homeNotifMenuButton == null) {
            return;
        }
        homeNotifContextMenu.getItems().clear();
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
            homeNotifContextMenu.getItems().add(header);
            List<CombinedPublicNotifications.MergedPreview> merged =
                    CombinedPublicNotifications.buildMenuPreview(user, userNotificationService, appointmentService);
            if (merged.isEmpty()) {
                MenuItem emptyItem = new MenuItem("Aucune notification.");
                emptyItem.setDisable(true);
                homeNotifContextMenu.getItems().add(emptyItem);
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
                        String ts = item.getDateCreation() != null ? HOME_NOTIF_TIME_FMT.format(item.getDateCreation()) : "";
                        Label meta = new Label(ts);
                        meta.getStyleClass().add("public-notif-item-meta");
                        VBox textCol = new VBox(2, title, meta);
                        card.getChildren().addAll(icon, textCol);
                        CustomMenuItem menuItem = new CustomMenuItem(card, true);
                        menuItem.setOnAction(e -> onHomeNotificationClick(item));
                        homeNotifContextMenu.getItems().add(menuItem);
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
                        String summary = PatientRdvNotificationsDialog.summaryForMenu(ap, userService);
                        Label title = new Label(summary);
                        title.setWrapText(true);
                        title.getStyleClass().add("public-notif-item-title");
                        String ts = ap.getDateHeure() != null ? HOME_NOTIF_TIME_FMT.format(ap.getDateHeure()) : "";
                        Label meta = new Label("Rendez-vous · " + ts);
                        meta.getStyleClass().add("public-notif-item-meta");
                        VBox textCol = new VBox(2, title, meta);
                        card.getChildren().addAll(icon, textCol);
                        CustomMenuItem menuItem = new CustomMenuItem(card, true);
                        menuItem.setOnAction(e -> onMergedRdvHomeNotificationClick(ap));
                        homeNotifContextMenu.getItems().add(menuItem);
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
            allItem.setOnAction(e -> onOpenAllNotificationsPage());
            homeNotifContextMenu.getItems().add(allItem);
            refreshHomeNotificationBadge();
        } catch (SQLException e) {
            MenuItem errorItem = new MenuItem("Impossible de charger les notifications.");
            errorItem.setDisable(true);
            homeNotifContextMenu.getItems().add(errorItem);
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

    private void onHomeNotificationClick(UserNotificationItem item) {
        if (item == null) {
            return;
        }
        try {
            userNotificationService.markAsRead(item.getId());
            String c = item.getTypeCode() != null ? item.getTypeCode() : "";
            if (UserNotificationService.TYPE_RDV_ACCEPTED.equals(c)
                    || UserNotificationService.TYPE_RDV_REFUSED.equals(c)
                    || UserNotificationService.TYPE_RDV_CANCELLED.equals(c)) {
                MainApp.showPublicPage("rdv");
            } else if (item.getEvenementId() != null && item.getEvenementId() > 0) {
                AppState.setPendingPublicEventDetailId(item.getEvenementId());
                MainApp.showPublicPage("event-detail");
            } else {
                MainApp.showPublicPage("notifications");
            }
            homeNotifContextMenu.hide();
            refreshHomeNotificationBadge();
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Notifications", e.getMessage());
        }
    }

    private void onMergedRdvHomeNotificationClick(Appointment ap) {
        User u = AppState.getCurrentUser();
        if (ap == null || u == null) {
            return;
        }
        try {
            appointmentService.markPatientDecisionRead(ap.getId(), u.getId());
            MainApp.showPublicPage("rdv");
            homeNotifContextMenu.hide();
            refreshHomeNotificationBadge();
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Notifications",
                    ex.getMessage() != null ? ex.getMessage() : "Impossible de mettre à jour la notification.");
        }
    }

    private void onOpenAllNotificationsPage() {
        try {
            MainApp.showPublicPage("notifications");
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Notifications", e.getMessage());
        }
    }

    private void openDashboardTab(int tabIndex) {
        try {
            User u = AppState.getCurrentUser();
            if (u == null) {
                MainApp.showLogin();
                return;
            }
            if (u.getRole() == Role.MEDECIN) {
                MainApp.showMedecinDashboard();
                return;
            }
            if (u.getRole() == Role.ADMIN) {
                MainApp.showAdminUsers();
                return;
            }
            MainApp.showDashboard(tabIndex);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    /**
     * Défile vers une section (clés alignées sur {@link MainApp#showHomeScrollTo(String)}).
     */
    private void navigateHomeScroll(String key) {
        switch (key.toLowerCase()) {
            case "produits" -> {
                try {
                    MainApp.showPublicPage("produits");
                } catch (IOException e) {
                    alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
                }
            }
            case "rdv" -> {
                try {
                    MainApp.showPublicPage("rdv");
                } catch (IOException e) {
                    alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
                }
            }
            case "events" -> {
                try {
                    MainApp.showPublicPage("events");
                } catch (IOException e) {
                    alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
                }
            }
            case "blog" -> {
                try {
                    MainApp.showPublicPage("blog");
                } catch (IOException e) {
                    alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
                }
            }
            case "contact" -> scrollToSection(homeFooterSection);
            case "mission" -> scrollToSection(sectionAnchorMission);
            case "community" -> scrollToSection(sectionAnchorCommunity);
            default -> scrollToTop();
        }
    }

    private void navigateToProducts() {
        try {
            MainApp.showPublicPage("produits");
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    private void navigateToRdv() {
        try {
            MainApp.showPublicPage("rdv");
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    private void navigateToEvents() {
        try {
            MainApp.showPublicPage("events");
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    private void navigateToBlog() {
        try {
            MainApp.showPublicPage("blog");
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    private void scrollToSection(Node section) {
        if (scrollPane == null || scrollContent == null || section == null) {
            return;
        }
        Platform.runLater(() -> {
            scrollContent.applyCss();
            scrollContent.layout();
            double contentH = scrollContent.getBoundsInLocal().getHeight();
            double viewportH = scrollPane.getViewportBounds().getHeight();
            double y = section.getBoundsInParent().getMinY();
            double maxScroll = Math.max(0.001, contentH - viewportH);
            double v = y / maxScroll;
            scrollPane.setVvalue(Math.min(1, Math.max(0, v)));
        });
    }

    /** Produits publiés (équivalent « validés » côté web) — masque la section si vide. */
    private void populateAiProducts() {
        if (aiProductsFlow == null || aiProductsSection == null) {
            return;
        }
        aiProductsFlow.getChildren().clear();
        try {
            List<Product> published = productService.findAll().stream()
                    .filter(Product::isPublie)
                    .limit(4)
                    .collect(Collectors.toList());
            if (published.isEmpty()) {
                aiProductsSection.setVisible(false);
                aiProductsSection.setManaged(false);
                return;
            }
            aiProductsSection.setVisible(true);
            aiProductsSection.setManaged(true);
            for (Product p : published) {
                aiProductsFlow.getChildren().add(buildAiProductCard(p));
            }
        } catch (SQLException ex) {
            aiProductsSection.setVisible(false);
            aiProductsSection.setManaged(false);
        }
    }

    private VBox buildAiProductCard(Product p) {
        VBox card = new VBox();
        card.getStyleClass().addAll("home-ai-card", "home-hover-lift");
        card.setSpacing(0);

        StackPane imgFrame = new StackPane();
        imgFrame.getStyleClass().add("home-ai-card-img");
        imgFrame.setMinHeight(120);
        imgFrame.setPrefHeight(120);
        ImageView imgView = new ImageView();
        imgView.setFitHeight(120);
        imgView.setFitWidth(260);
        imgView.setPreserveRatio(false);
        imgView.setSmooth(true);
        String path = p.getImagePath();
        if (path != null && !path.isBlank()) {
            Image loaded = ProductImageLoader.loadForDisplay(path, 260, 120);
            if (loaded == null && !path.trim().startsWith("http://") && !path.trim().startsWith("https://")) {
                URL u = getClass().getResource(path.startsWith("/") ? path : "/" + path);
                if (u == null) {
                    u = getClass().getResource("/images/" + path);
                }
                if (u != null) {
                    loaded = new Image(u.toExternalForm(), true);
                }
            }
            if (loaded != null && !loaded.isError()) {
                imgView.setImage(loaded);
            }
        }
        if (imgView.getImage() == null) {
            imgFrame.getChildren().add(ProductImagePlaceholder.create(260, 120));
        } else {
            imgFrame.getChildren().add(imgView);
        }

        VBox body = new VBox(6);
        body.getStyleClass().add("home-ai-card-body");
        if (p.getCategorie() != null && !p.getCategorie().isBlank()) {
            Label cat = new Label(p.getCategorie());
            cat.setStyle("-fx-background-color: #cdb4db; -fx-text-fill: white; -fx-padding: 4 10 4 10; -fx-background-radius: 12; -fx-font-size: 11px;");
            body.getChildren().add(cat);
        }
        Label title = new Label(p.getNom());
        title.getStyleClass().add("home-ai-card-title");
        title.setWrapText(true);
        body.getChildren().add(title);
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            Label desc = new Label(p.getDescription().length() > 120
                    ? p.getDescription().substring(0, 117) + "…"
                    : p.getDescription());
            desc.getStyleClass().add("home-ai-card-desc");
            desc.setWrapText(true);
            body.getChildren().add(desc);
        }
        Label price = new Label(String.format("%.2f dt", p.getPrix()));
        price.getStyleClass().add("home-ai-card-price");
        body.getChildren().add(price);

        Hyperlink see = new Hyperlink("Voir →");
        see.getStyleClass().add("home-feature-link");
        see.setStyle("-fx-text-fill: #cdb4db; -fx-font-size: 12px;");
        see.setOnAction(e -> onAiProductClick(p.getId()));
        body.getChildren().add(see);

        card.getChildren().addAll(imgFrame, body);
        return card;
    }

    private void refreshCtaState() {
        boolean logged = AppState.getCurrentUser() != null;
        if (ctaGuest != null) {
            ctaGuest.setVisible(!logged);
            ctaGuest.setManaged(!logged);
        }
        if (ctaLoggedIn != null) {
            ctaLoggedIn.setVisible(logged);
            ctaLoggedIn.setManaged(logged);
        }
    }

    private void onAiProductClick(int productId) {
        navigateToProducts();
    }

    @FXML
    public void onCtaProfile() {
        try {
            MainApp.openManagementSpace();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onCtaBrowseProducts() {
        navigateToProducts();
    }

    @FXML
    public void onCtaBrowseEvents() {
        navigateToEvents();
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

    private List<Image> loadHomeUserSliderImages() {
        List<Image> out = new ArrayList<>();
        for (String rawPath : HOME_USER_SLIDER_IMAGE_PATHS) {
            if (rawPath == null || rawPath.isBlank()) {
                continue;
            }
            try {
                Path p = Path.of(rawPath);
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                out.add(new Image(p.toUri().toString(), true));
            } catch (Exception ignored) {
                // Chemin invalide ou inaccessible : on continue avec les autres images.
            }
        }
        return out;
    }

    /** Deux images pour hero / mission / témoignages (locales ou secours URL). */
    private List<Image> slidesForHomeDiaporamas() {
        if (USE_USER_PROVIDED_HERO_SLIDES) {
            List<Image> slides = loadHomeUserSliderImages();
            if (slides.size() >= 2) {
                return slides;
            }
        }
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
    }

    /** Arrondi réel de la photo mission via clip (le CSS seul n'arrondit pas les pixels d'une ImageView). */
    private void applyMissionImageClip() {
        if (missionImageView == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.setArcWidth(26);
        clip.setArcHeight(26);
        clip.widthProperty().bind(missionImageView.fitWidthProperty());
        clip.heightProperty().bind(missionImageView.fitHeightProperty());
        missionImageView.setClip(clip);
    }

    /** PNG {@code hero-ticker-0x} dans /images/home/, sinon repli diaporama (URLs). */
    private List<Image> resolveHeroBackgroundImages() {
        List<Image> ticker = HomeHeroTicker.loadFromResources(getClass());
        if (ticker.size() >= 2) {
            return ticker;
        }
        return slidesForHomeDiaporamas();
    }

    /** Pulsation légère en boucle (2e bouton décalé comme animation-delay: 0.4s) ; pause au survol. */
    private void setupHeroPulse() {
        if (heroBtnProducts != null) {
            heroBtnProducts.setDefaultButton(false);
            attachPulseStopOnHover(heroBtnProducts, Duration.ZERO);
        }
        if (heroBtnEvents != null) {
            heroBtnEvents.setDefaultButton(false);
            attachPulseStopOnHover(heroBtnEvents, Duration.millis(400));
        }
    }

    /**
     * Ajoute une feuille sur la {@link Scene} après le premier affichage : sur certaines configs,
     * les styles du BorderPane ne suffisent pas à remplacer le dégradé Modena des {@link Button}.
     */
    private void ensureHeroCtaStylesOnScene() {
        if (HERO_CTA_FORCE_CSS == null || heroBtnProducts == null) {
            return;
        }
        Platform.runLater(() -> attachHeroCtaCssIfNeeded(0));
    }

    private void attachHeroCtaCssIfNeeded(int attempt) {
        Scene sc = heroBtnProducts.getScene();
        if (sc != null) {
            if (!sc.getStylesheets().contains(HERO_CTA_FORCE_CSS)) {
                sc.getStylesheets().add(HERO_CTA_FORCE_CSS);
            }
            return;
        }
        if (attempt < 12) {
            Platform.runLater(() -> attachHeroCtaCssIfNeeded(attempt + 1));
        }
    }

    /** Pulsation type hero-cta-pulse (~2,2 s) ; pause au survol. */
    private void attachPulseStopOnHover(Button btn, Duration delay) {
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(btn.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(btn.scaleYProperty(), 1, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1100),
                        new KeyValue(btn.scaleXProperty(), 1.02, Interpolator.EASE_BOTH),
                        new KeyValue(btn.scaleYProperty(), 1.02, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(2200),
                        new KeyValue(btn.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(btn.scaleYProperty(), 1, Interpolator.EASE_BOTH)));
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.setDelay(delay);
        btn.setOnMouseEntered(e -> {
            pulse.stop();
            btn.setScaleX(1);
            btn.setScaleY(1);
        });
        btn.setOnMouseExited(e -> pulse.playFromStart());
        pulse.play();
    }

    private void setupScrollAnimations() {
        if (scrollContent == null) {
            return;
        }
        for (Node n : scrollContent.lookupAll(".home-hover-lift")) {
            if (!(n instanceof VBox box)) {
                continue;
            }
            if (box.getStyleClass().contains("home-feature-card")) {
                setupFeatureCardInteractions(box);
            } else if (box.getStyleClass().contains("home-ai-card")) {
                installLiftHover(box, 1.02);
            } else if (box.getStyleClass().contains("home-stat-card")
                    || box.getStyleClass().contains("home-testi-card")) {
                installLiftHover(box, 1.02);
            }
        }
        for (Node n : scrollContent.lookupAll(".home-feature-link")) {
            if (n instanceof Hyperlink link) {
                installLinkArrowShift(link);
            }
        }
        for (Node n : scrollContent.lookupAll(".home-cta-lift")) {
            installLiftHover(n, 1.02);
        }
    }

    private void installLiftHover(Node node, double toScale) {
        ScaleTransition in = new ScaleTransition(Duration.millis(220), node);
        in.setToX(toScale);
        in.setToY(toScale);
        in.setInterpolator(Interpolator.EASE_BOTH);
        ScaleTransition out = new ScaleTransition(Duration.millis(220), node);
        out.setToX(1);
        out.setToY(1);
        out.setInterpolator(Interpolator.EASE_BOTH);
        node.setOnMouseEntered(e -> {
            out.stop();
            in.playFromStart();
        });
        node.setOnMouseExited(e -> {
            in.stop();
            out.playFromStart();
        });
    }

    private void setupFeatureCardInteractions(VBox card) {
        installLiftHover(card, 1.018);
        if (card.getChildren().isEmpty()) {
            return;
        }
        Node first = card.getChildren().get(0);
        if (!(first instanceof StackPane frame)
                || !frame.getStyleClass().contains("home-feature-img-frame")) {
            return;
        }
        if (frame.getChildren().isEmpty()) {
            return;
        }
        Node inner = frame.getChildren().get(0);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        frame.setClip(clip);
        Node zoomTarget = inner;
        if (!(inner instanceof ImageView) && !(inner instanceof HBox)) {
            return;
        }
        if (inner instanceof HBox h && !h.getStyleClass().contains("home-feature-img-track")) {
            return;
        }
        ScaleTransition zoomIn = new ScaleTransition(Duration.millis(300), zoomTarget);
        zoomIn.setToX(1.07);
        zoomIn.setToY(1.07);
        zoomIn.setInterpolator(Interpolator.EASE_BOTH);
        ScaleTransition zoomOut = new ScaleTransition(Duration.millis(300), zoomTarget);
        zoomOut.setToX(1);
        zoomOut.setToY(1);
        zoomOut.setInterpolator(Interpolator.EASE_BOTH);
        frame.setOnMouseEntered(e -> {
            zoomOut.stop();
            zoomIn.playFromStart();
        });
        frame.setOnMouseExited(e -> {
            zoomIn.stop();
            zoomOut.playFromStart();
        });
    }

    private void installLinkArrowShift(Hyperlink link) {
        TranslateTransition txIn = new TranslateTransition(Duration.millis(200), link);
        txIn.setToX(6);
        txIn.setInterpolator(Interpolator.EASE_BOTH);
        TranslateTransition txOut = new TranslateTransition(Duration.millis(200), link);
        txOut.setToX(0);
        txOut.setInterpolator(Interpolator.EASE_BOTH);
        link.setOnMouseEntered(e -> {
            txOut.stop();
            txIn.playFromStart();
        });
        link.setOnMouseExited(e -> {
            txIn.stop();
            txOut.playFromStart();
        });
    }

    @FXML
    public void onNavConnexion() throws IOException {
        MainApp.showLogin();
    }

    @FXML
    public void onRegister() throws IOException {
        MainApp.showSignup();
    }

    @FXML
    public void onLogout() throws IOException {
        AppState.clear();
        MainApp.showLogin();
    }

    @FXML
    public void onLogoutLinkClick(MouseEvent event) {
        if (event != null) {
            event.consume();
        }
    }

    @FXML
    public void onOpenMyProfile(MouseEvent event) {
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
    public void onNavAccueil(MouseEvent e) {
        scrollToTop();
    }

    private void scrollToTop() {
        if (scrollPane != null) {
            scrollPane.setVvalue(0);
        }
    }

    /** Fait défiler jusqu’au pied de page (coordonnées). */
    private void scrollToContact() {
        scrollToSection(homeFooterSection);
    }

    @FXML
    public void onToggleTheme() {
        MainApp.toggleTheme();
    }

    @FXML
    public void onUserShortcutBookFromHome() {
        navigateToBlog();
    }

    @FXML
    public void onUserShortcutBagFromHome() {
        navigateToProducts();
    }

    @FXML
    public void onUserShortcutNotificationsFromHome() {
        toggleHomeNotificationsMenu();
    }

    @FXML
    public void onPatientNotifBell(MouseEvent event) {
        if (event != null) {
            event.consume();
        }
        toggleHomeNotificationsMenu();
    }

    @FXML
    public void onNavProduits(MouseEvent e) {
        navigateToProducts();
    }

    @FXML
    public void onNavRdv(MouseEvent e) {
        navigateToRdv();
    }

    @FXML
    public void onNavEvents(MouseEvent e) {
        navigateToEvents();
    }

    @FXML
    public void onNavBlog(MouseEvent e) {
        navigateToBlog();
    }

    @FXML
    public void onHeroProducts() {
        navigateToProducts();
    }

    @FXML
    public void onHeroEvents() {
        navigateToEvents();
    }

    @FXML
    public void onMissionMore() {
        scrollToSection(sectionAnchorMission);
    }

    @FXML
    public void onFeatureProducts() {
        navigateToProducts();
    }

    @FXML
    public void onFeatureEvents() {
        navigateToEvents();
    }

    @FXML
    public void onFeatureBlog() {
        navigateToBlog();
    }

    @FXML
    public void onFeatureCommunity() {
        scrollToSection(sectionAnchorCommunity);
    }

    @FXML
    public void onJoinSignup() throws IOException {
        MainApp.showSignup();
    }

    @FXML
    public void onJoinLogin() throws IOException {
        MainApp.showLogin();
    }

    @FXML
    public void onJoinContact() {
        scrollToContact();
    }

    @FXML
    public void onFooterNavAccueil() {
        scrollToTop();
    }

    @FXML
    public void onFooterNavProduits() {
        navigateToProducts();
    }

    @FXML
    public void onFooterNavRdv() {
        navigateToRdv();
    }

    @FXML
    public void onFooterNavEvents() {
        navigateToEvents();
    }

    @FXML
    public void onFooterNavBlog() {
        navigateToBlog();
    }

    @FXML
    public void onFooterFaq() {
        alert(Alert.AlertType.INFORMATION, "FAQ", "Une section FAQ sera disponible prochainement.");
    }

    @FXML
    public void onFooterContact() {
        onJoinContact();
    }

    @FXML
    public void onFooterAccessibility() {
        alert(Alert.AlertType.INFORMATION, "Accessibilité",
                "AutiCare s'engage à améliorer l'accessibilité de cette application.");
    }

    @FXML
    public void onFooterLegal() {
        alert(Alert.AlertType.INFORMATION, "Mentions légales",
                "Informations légales à compléter selon votre structure.");
    }

    @FXML
    public void onFooterPrivacy() {
        alert(Alert.AlertType.INFORMATION, "Politique de confidentialité",
                "Traitement des données personnelles : texte à adapter à votre politique.");
    }

    @FXML
    public void onFooterCgv() {
        alert(Alert.AlertType.INFORMATION, "CGV", "Conditions générales de vente : texte à adapter.");
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
