package org.example.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import javafx.util.Duration;
import org.example.MainApp;
import org.example.controllers.AdminMyProfileController;
import org.example.models.Product;
import org.example.services.ProductService;
import org.example.utils.AppState;
import org.example.utils.UserAvatarGraphic;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HomeController {

    /** Fichiers dans src/main/resources/images/home/ (prioritaires si présents). */
    private static final String[] HERO_SLIDE_NAMES = {
            "hero-slide-1.png",
            "hero-slide-2.png",
            "hero-slide-3.jpg",
            "hero-slide-4.jpg"
    };

    /** Diaporama hero si aucun fichier local. */
    private static final String[] HERO_FALLBACK_URLS = {
            "https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=1600&q=85",
            "https://images.unsplash.com/photo-1580582932707-520edc937b0e?w=1600&q=85",
            "https://images.unsplash.com/photo-1503454537195-1dcabb73ffb9?w=1600&q=85",
            "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=1600&q=85"
    };

    private static final String[] TESTI_BG_URLS = {
            "https://images.unsplash.com/photo-1503454537195-1dcabb73ffb9?w=1600&q=85",
            "https://images.unsplash.com/photo-1587854692152-cbe660dbde88?w=1600&q=85",
            "https://images.unsplash.com/photo-1503919545889-aef636e10ad4?w=1600&q=85"
    };

    /** Fond mission (diaporama léger derrière le voile). */
    private static final String[] MISSION_BG_URLS = {
            "https://images.unsplash.com/photo-1607619056574-7b8d3ee536b2?w=1600&q=85",
            "https://images.unsplash.com/photo-1544776193-7d62c3841120?w=1600&q=85",
            "https://images.unsplash.com/photo-1516627145497-ae6968895b74?w=1600&q=85",
            "https://images.unsplash.com/photo-1503454537195-1dcabb73ffb9?w=1600&q=85"
    };

    /** Fond global animé sur toute la page (style maquette). */
    private static final String[] GLOBAL_BG_URLS = {
            "https://images.unsplash.com/photo-1516627145497-ae6968895b74?w=2000&q=85",
            "https://images.unsplash.com/photo-1544776193-7d62c3841120?w=2000&q=85",
            "https://images.unsplash.com/photo-1607619056574-7b8d3ee536b2?w=2000&q=85",
            "https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=2000&q=85"
    };

    private static final Duration HERO_SLIDE_INTERVAL = Duration.seconds(7);
    private static final Duration HERO_CROSSFADE_DURATION = Duration.millis(1600);
    private static final Duration MISSION_SLIDE_INTERVAL = Duration.seconds(10);
    private static final Duration MISSION_CROSSFADE_DURATION = Duration.millis(2200);
    private static final Duration GLOBAL_BG_SLIDE_INTERVAL = Duration.seconds(11);
    private static final Duration GLOBAL_BG_CROSSFADE_DURATION = Duration.millis(2600);

    private static final Duration AMBIENT_GRADIENT_INTERVAL = Duration.seconds(9);
    private static final Duration AMBIENT_GRADIENT_FADE = Duration.millis(2800);

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
    private ImageView missionBgA;
    @FXML
    private ImageView missionBgB;
    @FXML
    private ImageView testiBgA;
    @FXML
    private ImageView testiBgB;
    @FXML
    private Region needBgA;
    @FXML
    private Region needBgB;
    @FXML
    private Region joinBgA;
    @FXML
    private Region joinBgB;
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
    private HBox loggedNavActions;
    @FXML
    private StackPane loggedAvatarHost;
    @FXML
    private Label loggedUserNameLabel;
    @FXML
    private Label loggedUserCodeLabel;
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
    private List<Image> heroSlides = new ArrayList<>();
    private int heroSlideIndex = 0;
    private boolean heroShowingA = true;

    private List<Image> globalSlides = new ArrayList<>();
    private int globalSlideIndex = 0;
    private boolean globalShowingA = true;

    private List<Image> missionSlides = new ArrayList<>();
    private int missionSlideIndex = 0;
    private boolean missionShowingA = true;

    private List<Image> testiSlides = new ArrayList<>();
    private int testiSlideIndex = 0;
    private boolean testiShowingA = true;

    private boolean needShowingA = true;
    private boolean joinShowingA = true;

    private final ProductService productService = new ProductService();

    @FXML
    public void initialize() {
        if (langCombo != null) {
            langCombo.getItems().addAll("FR", "EN");
            langCombo.getSelectionModel().selectFirst();
        }
        Platform.runLater(() -> {
            refreshTopNavState();
            initGlobalBackground();
            initHeroBackground();
            initMissionBackground();
            initTestimonialBackground();
            initAmbientGradients();
            populateAiProducts();
            refreshCtaState();
            setupHeroPulse();
            setupScrollAnimations();
            applyPendingHomeScroll();
        });
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
        }
    }

    private void openDashboardTab(int tabIndex) {
        try {
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
                if (isLoggedIn()) {
                    openDashboardTab(1);
                } else {
                    scrollToSection(productScrollTarget());
                }
            }
            case "rdv" -> {
                if (isLoggedIn()) {
                    openDashboardTab(2);
                } else {
                    scrollToSection(homeFooterSection);
                }
            }
            case "events" -> {
                if (isLoggedIn()) {
                    openDashboardTab(4);
                } else {
                    scrollToSection(sectionAnchorEvents);
                }
            }
            case "blog" -> {
                if (isLoggedIn()) {
                    openDashboardTab(7);
                } else {
                    scrollToSection(sectionAnchorBlog);
                }
            }
            case "contact" -> scrollToSection(homeFooterSection);
            case "mission" -> scrollToSection(sectionAnchorMission);
            case "community" -> scrollToSection(sectionAnchorCommunity);
            default -> scrollToTop();
        }
    }

    private Node productScrollTarget() {
        if (aiProductsSection != null && aiProductsSection.isVisible()) {
            return aiProductsSection;
        }
        return sectionAnchorProduits != null ? sectionAnchorProduits : sectionAnchorEvents;
    }

    private void navigateToProducts() {
        if (isLoggedIn()) {
            openDashboardTab(1);
        } else {
            scrollToSection(productScrollTarget());
        }
    }

    private void navigateToRdv() {
        if (isLoggedIn()) {
            openDashboardTab(2);
        } else {
            scrollToSection(homeFooterSection);
        }
    }

    private void navigateToEvents() {
        if (isLoggedIn()) {
            openDashboardTab(4);
        } else {
            scrollToSection(sectionAnchorEvents);
        }
    }

    private void navigateToBlog() {
        if (isLoggedIn()) {
            openDashboardTab(7);
        } else {
            scrollToSection(sectionAnchorBlog);
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
            if (path.startsWith("http://") || path.startsWith("https://")) {
                imgView.setImage(new Image(path, true));
            } else {
                URL u = getClass().getResource(path.startsWith("/") ? path : "/" + path);
                if (u == null) {
                    u = getClass().getResource("/images/" + path);
                }
                if (u != null) {
                    imgView.setImage(new Image(u.toExternalForm(), true));
                }
            }
        }
        if (imgView.getImage() == null) {
            Region ph = new Region();
            ph.setMinHeight(120);
            ph.setStyle("-fx-background-color: #cdb4db;");
            imgFrame.getChildren().add(ph);
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
        if (isLoggedIn()) {
            openDashboardTab(1);
        } else {
            navigateToProducts();
        }
    }

    @FXML
    public void onAiSeeAllProducts() {
        navigateToProducts();
    }

    @FXML
    public void onCtaProfile() {
        try {
            MainApp.showDashboard();
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

    private void initGlobalBackground() {
        if (globalBgA == null || globalBgB == null) {
            return;
        }
        globalSlides = new ArrayList<>();
        for (String url : GLOBAL_BG_URLS) {
            globalSlides.add(new Image(url, true));
        }
        StackPane wrap = (StackPane) globalBgA.getParent();
        for (ImageView iv : new ImageView[]{globalBgA, globalBgB}) {
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.fitWidthProperty().bind(wrap.widthProperty());
            iv.fitHeightProperty().bind(wrap.heightProperty());
        }
        if (globalSlides.size() < 2) {
            if (globalSlides.size() == 1) {
                globalBgA.setImage(globalSlides.get(0));
                globalBgA.setOpacity(1);
                globalBgB.setOpacity(0);
                globalBgB.setVisible(false);
            }
            return;
        }
        globalSlideIndex = 0;
        globalShowingA = true;
        globalBgA.setImage(globalSlides.get(0));
        globalBgA.setOpacity(1);
        globalBgB.setImage(globalSlides.get(1 % globalSlides.size()));
        globalBgB.setOpacity(0);
        PauseTransition pause = new PauseTransition(GLOBAL_BG_SLIDE_INTERVAL);
        pause.setOnFinished(e -> crossfadeGlobalBgNext());
        pause.play();
    }

    private void crossfadeGlobalBgNext() {
        if (globalSlides.size() < 2 || globalBgA == null || globalBgB == null) {
            return;
        }
        int nextIdx = (globalSlideIndex + 1) % globalSlides.size();
        ImageView from = globalShowingA ? globalBgA : globalBgB;
        ImageView to = globalShowingA ? globalBgB : globalBgA;
        to.setImage(globalSlides.get(nextIdx));
        to.setOpacity(0);
        from.setOpacity(1);
        FadeTransition fadeOut = new FadeTransition(GLOBAL_BG_CROSSFADE_DURATION, from);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        FadeTransition fadeIn = new FadeTransition(GLOBAL_BG_CROSSFADE_DURATION, to);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ParallelTransition cross = new ParallelTransition(fadeOut, fadeIn);
        cross.setOnFinished(e -> {
            globalSlideIndex = nextIdx;
            globalShowingA = !globalShowingA;
            ImageView hidden = globalShowingA ? globalBgB : globalBgA;
            hidden.setOpacity(0);
            hidden.setImage(globalSlides.get((globalSlideIndex + 1) % globalSlides.size()));
            PauseTransition pause = new PauseTransition(GLOBAL_BG_SLIDE_INTERVAL);
            pause.setOnFinished(ev -> crossfadeGlobalBgNext());
            pause.play();
        });
        cross.play();
    }

    private void initHeroBackground() {
        if (heroBgImageA == null || heroBgImageB == null) {
            return;
        }
        heroSlides = new ArrayList<>();
        for (String name : HERO_SLIDE_NAMES) {
            URL u = getClass().getResource("/images/home/" + name);
            if (u != null) {
                heroSlides.add(new Image(u.toExternalForm(), true));
            }
        }
        if (heroSlides.isEmpty()) {
            for (String url : HERO_FALLBACK_URLS) {
                heroSlides.add(new Image(url, true));
            }
        }
        StackPane wrap = (StackPane) heroBgImageA.getParent();
        for (ImageView iv : new ImageView[]{heroBgImageA, heroBgImageB}) {
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.fitWidthProperty().bind(wrap.widthProperty());
            iv.fitHeightProperty().bind(wrap.heightProperty());
        }
        if (heroSlides.size() < 2) {
            if (heroSlides.size() == 1) {
                heroBgImageA.setImage(heroSlides.get(0));
                heroBgImageA.setOpacity(1);
                heroBgImageB.setOpacity(0);
                heroBgImageB.setVisible(false);
            }
            return;
        }
        heroSlideIndex = 0;
        heroShowingA = true;
        heroBgImageA.setImage(heroSlides.get(0));
        heroBgImageA.setOpacity(1);
        heroBgImageB.setImage(heroSlides.get(1 % heroSlides.size()));
        heroBgImageB.setOpacity(0);
        PauseTransition pause = new PauseTransition(HERO_SLIDE_INTERVAL);
        pause.setOnFinished(e -> crossfadeHeroNext());
        pause.play();
    }

    private void crossfadeHeroNext() {
        if (heroSlides.size() < 2 || heroBgImageA == null || heroBgImageB == null) {
            return;
        }
        int nextIdx = (heroSlideIndex + 1) % heroSlides.size();
        ImageView from = heroShowingA ? heroBgImageA : heroBgImageB;
        ImageView to = heroShowingA ? heroBgImageB : heroBgImageA;
        to.setImage(heroSlides.get(nextIdx));
        to.setOpacity(0);
        from.setOpacity(1);
        FadeTransition fadeOut = new FadeTransition(HERO_CROSSFADE_DURATION, from);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        FadeTransition fadeIn = new FadeTransition(HERO_CROSSFADE_DURATION, to);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ParallelTransition cross = new ParallelTransition(fadeOut, fadeIn);
        cross.setOnFinished(e -> {
            heroSlideIndex = nextIdx;
            heroShowingA = !heroShowingA;
            ImageView hidden = heroShowingA ? heroBgImageB : heroBgImageA;
            hidden.setOpacity(0);
            hidden.setImage(heroSlides.get((heroSlideIndex + 1) % heroSlides.size()));
            PauseTransition pause = new PauseTransition(HERO_SLIDE_INTERVAL);
            pause.setOnFinished(ev -> crossfadeHeroNext());
            pause.play();
        });
        cross.play();
    }

    private void initMissionBackground() {
        if (missionBgA == null || missionBgB == null) {
            return;
        }
        missionSlides = new ArrayList<>();
        for (String url : MISSION_BG_URLS) {
            missionSlides.add(new Image(url, true));
        }
        StackPane wrap = (StackPane) missionBgA.getParent();
        for (ImageView iv : new ImageView[]{missionBgA, missionBgB}) {
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.fitWidthProperty().bind(wrap.widthProperty());
            iv.fitHeightProperty().bind(wrap.heightProperty());
        }
        if (missionSlides.size() < 2) {
            if (missionSlides.size() == 1) {
                missionBgA.setImage(missionSlides.get(0));
                missionBgA.setOpacity(1);
                missionBgB.setOpacity(0);
                missionBgB.setVisible(false);
            }
            return;
        }
        missionSlideIndex = 0;
        missionShowingA = true;
        missionBgA.setImage(missionSlides.get(0));
        missionBgA.setOpacity(1);
        missionBgB.setImage(missionSlides.get(1 % missionSlides.size()));
        missionBgB.setOpacity(0);
        PauseTransition pause = new PauseTransition(MISSION_SLIDE_INTERVAL);
        pause.setOnFinished(e -> crossfadeMissionNext());
        pause.play();
    }

    private void crossfadeMissionNext() {
        if (missionSlides.size() < 2 || missionBgA == null || missionBgB == null) {
            return;
        }
        int nextIdx = (missionSlideIndex + 1) % missionSlides.size();
        ImageView from = missionShowingA ? missionBgA : missionBgB;
        ImageView to = missionShowingA ? missionBgB : missionBgA;
        to.setImage(missionSlides.get(nextIdx));
        to.setOpacity(0);
        from.setOpacity(1);
        FadeTransition fadeOut = new FadeTransition(MISSION_CROSSFADE_DURATION, from);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        FadeTransition fadeIn = new FadeTransition(MISSION_CROSSFADE_DURATION, to);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ParallelTransition cross = new ParallelTransition(fadeOut, fadeIn);
        cross.setOnFinished(e -> {
            missionSlideIndex = nextIdx;
            missionShowingA = !missionShowingA;
            ImageView hidden = missionShowingA ? missionBgB : missionBgA;
            hidden.setOpacity(0);
            hidden.setImage(missionSlides.get((missionSlideIndex + 1) % missionSlides.size()));
            PauseTransition pause = new PauseTransition(MISSION_SLIDE_INTERVAL);
            pause.setOnFinished(ev -> crossfadeMissionNext());
            pause.play();
        });
        cross.play();
    }

    private void initTestimonialBackground() {
        if (testiBgA == null || testiBgB == null) {
            return;
        }
        testiSlides = new ArrayList<>();
        for (String url : TESTI_BG_URLS) {
            testiSlides.add(new Image(url, true));
        }
        if (testiSlides.size() < 2) {
            StackPane wrapSingle = (StackPane) testiBgA.getParent();
            for (ImageView iv : new ImageView[]{testiBgA, testiBgB}) {
                iv.setEffect(new GaussianBlur(8));
                iv.setPreserveRatio(false);
                iv.setSmooth(true);
                iv.fitWidthProperty().bind(wrapSingle.widthProperty());
                iv.fitHeightProperty().bind(wrapSingle.heightProperty());
            }
            if (testiSlides.size() == 1) {
                testiBgA.setImage(testiSlides.get(0));
                testiBgA.setOpacity(1);
                testiBgB.setOpacity(0);
                testiBgB.setVisible(false);
            }
            return;
        }
        StackPane wrap = (StackPane) testiBgA.getParent();
        for (ImageView iv : new ImageView[]{testiBgA, testiBgB}) {
            iv.setEffect(new GaussianBlur(8));
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.fitWidthProperty().bind(wrap.widthProperty());
            iv.fitHeightProperty().bind(wrap.heightProperty());
        }
        testiSlideIndex = 0;
        testiShowingA = true;
        testiBgA.setImage(testiSlides.get(0));
        testiBgA.setOpacity(1);
        testiBgB.setImage(testiSlides.get(1 % testiSlides.size()));
        testiBgB.setOpacity(0);
        PauseTransition pause = new PauseTransition(Duration.seconds(8));
        pause.setOnFinished(e -> crossfadeTestiNext());
        pause.play();
    }

    private void crossfadeTestiNext() {
        if (testiSlides.size() < 2 || testiBgA == null || testiBgB == null) {
            return;
        }
        int nextIdx = (testiSlideIndex + 1) % testiSlides.size();
        ImageView from = testiShowingA ? testiBgA : testiBgB;
        ImageView to = testiShowingA ? testiBgB : testiBgA;
        to.setImage(testiSlides.get(nextIdx));
        to.setOpacity(0);
        from.setOpacity(1);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(2200), from);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(2200), to);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ParallelTransition cross = new ParallelTransition(fadeOut, fadeIn);
        cross.setOnFinished(e -> {
            testiSlideIndex = nextIdx;
            testiShowingA = !testiShowingA;
            ImageView hidden = testiShowingA ? testiBgB : testiBgA;
            hidden.setOpacity(0);
            hidden.setImage(testiSlides.get((testiSlideIndex + 1) % testiSlides.size()));
            PauseTransition pause = new PauseTransition(Duration.seconds(8));
            pause.setOnFinished(ev -> crossfadeTestiNext());
            pause.play();
        });
        cross.play();
    }

    private void initAmbientGradients() {
        if (needBgA != null && needBgB != null) {
            needBgB.setOpacity(0);
            PauseTransition pause = new PauseTransition(AMBIENT_GRADIENT_INTERVAL);
            pause.setOnFinished(e -> crossfadeNeedGradient());
            pause.play();
        }
        if (joinBgA != null && joinBgB != null) {
            joinBgB.setOpacity(0);
            PauseTransition pause = new PauseTransition(AMBIENT_GRADIENT_INTERVAL);
            pause.setOnFinished(e -> crossfadeJoinGradient());
            pause.play();
        }
    }

    private void crossfadeNeedGradient() {
        if (needBgA == null || needBgB == null) {
            return;
        }
        Region from = needShowingA ? needBgA : needBgB;
        Region to = needShowingA ? needBgB : needBgA;
        FadeTransition fadeOut = new FadeTransition(AMBIENT_GRADIENT_FADE, from);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        FadeTransition fadeIn = new FadeTransition(AMBIENT_GRADIENT_FADE, to);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ParallelTransition cross = new ParallelTransition(fadeOut, fadeIn);
        cross.setOnFinished(e -> {
            needShowingA = !needShowingA;
            PauseTransition pause = new PauseTransition(AMBIENT_GRADIENT_INTERVAL);
            pause.setOnFinished(ev -> crossfadeNeedGradient());
            pause.play();
        });
        cross.play();
    }

    private void crossfadeJoinGradient() {
        if (joinBgA == null || joinBgB == null) {
            return;
        }
        Region from = joinShowingA ? joinBgA : joinBgB;
        Region to = joinShowingA ? joinBgB : joinBgA;
        FadeTransition fadeOut = new FadeTransition(AMBIENT_GRADIENT_FADE, from);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        FadeTransition fadeIn = new FadeTransition(AMBIENT_GRADIENT_FADE, to);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ParallelTransition cross = new ParallelTransition(fadeOut, fadeIn);
        cross.setOnFinished(e -> {
            joinShowingA = !joinShowingA;
            PauseTransition pause = new PauseTransition(AMBIENT_GRADIENT_INTERVAL);
            pause.setOnFinished(ev -> crossfadeJoinGradient());
            pause.play();
        });
        cross.play();
    }

    /** Pulsation légère en boucle (2e bouton décalé comme animation-delay: 0.4s) ; pause au survol. */
    private void setupHeroPulse() {
        if (heroBtnProducts != null) {
            attachPulseStopOnHover(heroBtnProducts, Duration.ZERO);
        }
        if (heroBtnEvents != null) {
            attachPulseStopOnHover(heroBtnEvents, Duration.millis(400));
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
        if (!(first instanceof StackPane frame)) {
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
        ScaleTransition zoomIn = new ScaleTransition(Duration.millis(300), inner);
        zoomIn.setToX(1.07);
        zoomIn.setToY(1.07);
        zoomIn.setInterpolator(Interpolator.EASE_BOTH);
        ScaleTransition zoomOut = new ScaleTransition(Duration.millis(300), inner);
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
