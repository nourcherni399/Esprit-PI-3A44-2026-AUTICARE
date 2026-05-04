package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.util.Duration;
import org.example.MainApp;

import java.io.IOException;
import java.util.List;

/**
 * Navigation et pied de page communs aux écrans login / mot de passe oublié.
 */
public abstract class AuthPageController {
    private static final List<String> AUTH_BG_URLS = List.of(
            // Enfant / apprentissage
            "https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=2200&q=85",
            // Médicaments / accompagnement
            "https://images.unsplash.com/photo-1607619056574-7b8d3ee536b2?w=2200&q=85",
            // Groupe d'enfants / inclusion
            "https://images.unsplash.com/photo-1529156069898-49953e39b3ac?w=2200&q=85",
            // Variante inclusion
            "https://images.unsplash.com/photo-1523240795612-9a054b0db644?w=2200&q=85"
    );
    private static final Duration AUTH_BG_CYCLE = Duration.seconds(60);
    private static final String AUTH_BG_TIMELINE_KEY = "authBgTimeline";
    private static final String AUTH_BG_LAYER_KEY = "authBgLayer";

    @FXML
    protected ComboBox<String> langCombo;

    @FXML
    public void initializeAuth() {
        if (langCombo != null) {
            langCombo.getItems().addAll("FR", "EN");
            langCombo.getSelectionModel().selectFirst();
        }
        attachAnimatedAuthBackground(langCombo);
    }

    public static void attachAnimatedAuthBackground(Node anchor) {
        if (anchor == null) {
            return;
        }
        Platform.runLater(() -> {
            Scene scene = anchor.getScene();
            if (scene == null) {
                return;
            }
            if (!(scene.getRoot() instanceof Region region) || !(scene.getRoot() instanceof Pane pane)) {
                return;
            }
            Object old = region.getProperties().get(AUTH_BG_TIMELINE_KEY);
            if (old instanceof Timeline oldTimeline) {
                oldTimeline.stop();
            }
            Object oldLayer = region.getProperties().get(AUTH_BG_LAYER_KEY);
            if (oldLayer instanceof StackPane layer) {
                pane.getChildren().remove(layer);
            }

            StackPane bgLayer = new StackPane();
            bgLayer.getStyleClass().add("auth-bg-layer");
            bgLayer.setManaged(true);
            bgLayer.setMouseTransparent(true);
            bgLayer.minWidthProperty().bind(region.widthProperty());
            bgLayer.prefWidthProperty().bind(region.widthProperty());
            bgLayer.maxWidthProperty().bind(region.widthProperty());
            bgLayer.minHeightProperty().bind(region.heightProperty());
            bgLayer.prefHeightProperty().bind(region.heightProperty());
            bgLayer.maxHeightProperty().bind(region.heightProperty());

            NumberBinding tileWidth = Bindings.max(560, Bindings.min(900, region.widthProperty().multiply(0.72)));

            HBox stripA = new HBox();
            stripA.setAlignment(Pos.CENTER_LEFT);
            stripA.setSpacing(0);
            HBox stripB = new HBox();
            stripB.setAlignment(Pos.CENTER_LEFT);
            stripB.setSpacing(0);
            for (String url : AUTH_BG_URLS) {
                stripA.getChildren().add(createAuthTile(url, region, tileWidth));
                stripB.getChildren().add(createAuthTile(url, region, tileWidth));
            }

            HBox track = new HBox(stripA, stripB);
            track.setAlignment(Pos.CENTER_LEFT);
            track.setSpacing(0);

            Region overlay = new Region();
            overlay.getStyleClass().add("auth-bg-overlay");
            overlay.minWidthProperty().bind(region.widthProperty());
            overlay.prefWidthProperty().bind(region.widthProperty());
            overlay.maxWidthProperty().bind(region.widthProperty());
            overlay.minHeightProperty().bind(region.heightProperty());
            overlay.prefHeightProperty().bind(region.heightProperty());
            overlay.maxHeightProperty().bind(region.heightProperty());

            bgLayer.getChildren().setAll(track, overlay);
            pane.getChildren().add(0, bgLayer);
            region.getProperties().put(AUTH_BG_LAYER_KEY, bgLayer);
            stripA.widthProperty().addListener((obs, oldW, newW) ->
                    Platform.runLater(() -> startAuthTrackAnimation(region, track, stripA)));
            Platform.runLater(() -> startAuthTrackAnimation(region, track, stripA));
        });
    }

    private static StackPane createAuthTile(String imageUrl, Region root, NumberBinding tileWidth) {
        ImageView image = new ImageView(new Image(imageUrl, true));
        image.setPreserveRatio(false);
        image.setSmooth(true);
        image.fitHeightProperty().bind(root.heightProperty());
        image.fitWidthProperty().bind(tileWidth);

        Region tint = new Region();
        tint.getStyleClass().add("auth-bg-tile-tint");
        tint.prefWidthProperty().bind(tileWidth);
        tint.prefHeightProperty().bind(root.heightProperty());

        StackPane tile = new StackPane(image, tint);
        tile.setMinWidth(Region.USE_PREF_SIZE);
        tile.setPrefWidth(Region.USE_COMPUTED_SIZE);
        tile.setMaxWidth(Region.USE_PREF_SIZE);
        return tile;
    }

    private static void startAuthTrackAnimation(Region region, HBox track, HBox stripA) {
        double distance = stripA.getLayoutBounds().getWidth();
        if (distance <= 1) {
            return;
        }
        Object old = region.getProperties().get(AUTH_BG_TIMELINE_KEY);
        if (old instanceof Timeline oldTimeline) {
            oldTimeline.stop();
        }
        track.setTranslateX(0);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(track.translateXProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(AUTH_BG_CYCLE, new KeyValue(track.translateXProperty(), -distance, Interpolator.LINEAR))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        region.getProperties().put(AUTH_BG_TIMELINE_KEY, timeline);

    }

    protected void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    public void onToggleTheme() {
        MainApp.toggleTheme();
    }

    @FXML
    public void onRegister() {
        try {
            MainApp.showSignup();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavConnexion() {
        try {
            MainApp.showLogin();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavAccueil(MouseEvent event) {
        try {
            MainApp.showHome();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavProduits(MouseEvent event) {
        try {
            MainApp.showHomeScrollTo("produits");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavRdv(MouseEvent event) {
        try {
            MainApp.showHomeScrollTo("rdv");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavEvents(MouseEvent event) {
        try {
            MainApp.showHomeScrollTo("events");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavBlog(MouseEvent event) {
        try {
            MainApp.showHomeScrollTo("blog");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterNavAccueil() {
        try {
            MainApp.showHome();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterNavProduits() {
        try {
            MainApp.showHomeScrollTo("produits");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterNavRdv() {
        try {
            MainApp.showHomeScrollTo("rdv");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterNavEvents() {
        try {
            MainApp.showHomeScrollTo("events");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterNavBlog() {
        try {
            MainApp.showHomeScrollTo("blog");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterFaq() {
        showAlert(Alert.AlertType.INFORMATION, "FAQ", "Une section FAQ sera disponible prochainement.");
    }

    @FXML
    public void onFooterContact() {
        try {
            MainApp.showHomeScrollTo("contact");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onFooterAccessibility() {
        showAlert(Alert.AlertType.INFORMATION, "Accessibilité",
                "AutiCare s'engage à améliorer l'accessibilité de cette application.");
    }

    @FXML
    public void onFooterLegal() {
        showAlert(Alert.AlertType.INFORMATION, "Mentions légales",
                "Informations légales à compléter selon votre structure.");
    }

    @FXML
    public void onFooterPrivacy() {
        showAlert(Alert.AlertType.INFORMATION, "Politique de confidentialité",
                "Traitement des données personnelles : texte à adapter à votre politique.");
    }

    @FXML
    public void onFooterCgv() {
        showAlert(Alert.AlertType.INFORMATION, "CGV", "Conditions générales de vente : texte à adapter.");
    }
}
