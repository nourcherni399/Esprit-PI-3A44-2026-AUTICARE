package org.example.utils;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Fond hero : plusieurs images côte à côte, défilement continu droite → gauche (principe bandeau actualités).
 */
public final class HomeHeroTicker {

    public static final String[] RESOURCE_FILENAMES = {
            "hero-ticker-01.png",
            "hero-ticker-02.png",
            "hero-ticker-03.png",
            "hero-ticker-04.png"
    };

    public static final String TRACK_STYLE_CLASS = "home-hero-ticker-track";
    private static final String SEG_STYLE_CLASS = "home-hero-ticker-seg";
    private static final String PROP_TIMELINE = "homeHeroTickerTimeline";
    private static final String PROP_LAST_W = "homeHeroTickerLastSegmentW";

    /** Espace entre deux images dans le bandeau (0 = collage bord à bord). */
    public static final double HERO_TICKER_IMAGE_GAP_PX = 0.0;
    /**
     * Largeur de chaque vignette = fraction de la largeur du hero (ex. 0.52 ≈ 2 images visibles, vignettes « HD »).
     */
    public static final double HERO_TICKER_IMAGE_WIDTH_FRACTION = 0.52;
    private static final double HERO_TICKER_MIN_CELL_WIDTH_PX = 360.0;

    /** Chargement mémoire proche 1080p pour un rendu plus net à l’écran. */
    private static final double HERO_IMAGE_DECODE_W = 1920.0;
    private static final double HERO_IMAGE_DECODE_H = 1080.0;

    private HomeHeroTicker() {
    }

    public static List<Image> loadFromResources(Class<?> clazz) {
        List<Image> out = new ArrayList<>();
        for (String name : RESOURCE_FILENAMES) {
            URL u = clazz.getResource("/images/home/" + name);
            if (u != null) {
                /* decode vers ~Full HD pour limiter le flou quand les vignettes sont larges */
                out.add(new Image(u.toExternalForm(), HERO_IMAGE_DECODE_W, HERO_IMAGE_DECODE_H, false, true, true));
            }
        }
        return out;
    }

    public static void install(
            StackPane wrap,
            ImageView hideA,
            ImageView hideB,
            List<Image> slides,
            double pixelsPerSec) {
        Object oldTl = wrap.getProperties().get(PROP_TIMELINE);
        if (oldTl instanceof Timeline tl) {
            tl.stop();
        }
        wrap.getChildren().removeIf(n -> n instanceof HBox hb && hb.getStyleClass().contains(TRACK_STYLE_CLASS));
        if (hideA != null) {
            hideA.setManaged(false);
            hideA.setVisible(false);
        }
        if (hideB != null) {
            hideB.setManaged(false);
            hideB.setVisible(false);
        }

        List<Image> imgs = new ArrayList<>();
        for (Image img : slides) {
            if (img != null && !img.isError()) {
                imgs.add(img);
            }
        }
        if (imgs.isEmpty()) {
            return;
        }
        if (imgs.size() == 1) {
            imgs.add(imgs.get(0));
        }

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(wrap.widthProperty());
        clip.heightProperty().bind(wrap.heightProperty());
        wrap.setClip(clip);

        HBox seg1 = buildSegment(wrap, imgs);
        seg1.getStyleClass().add(SEG_STYLE_CLASS);
        HBox seg2 = buildSegment(wrap, imgs);
        seg2.getStyleClass().add(SEG_STYLE_CLASS);

        HBox track = new HBox(0);
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMinSize(0, 0);
        track.getStyleClass().add(TRACK_STYLE_CLASS);
        track.getChildren().addAll(seg1, seg2);
        StackPane.setAlignment(track, Pos.CENTER_LEFT);
        wrap.getChildren().add(0, track);

        Runnable restart = () -> restartIfReady(wrap, track, seg1, pixelsPerSec);
        seg1.layoutBoundsProperty().addListener((obs, p, c) -> Platform.runLater(restart));
        wrap.widthProperty().addListener((obs, p, c) -> Platform.runLater(restart));
        Platform.runLater(restart);
    }

    private static HBox buildSegment(StackPane wrap, List<Image> imgs) {
        HBox seg = new HBox(HERO_TICKER_IMAGE_GAP_PX);
        seg.setAlignment(Pos.CENTER_LEFT);
        seg.setFillHeight(true);
        DoubleBinding cellWidth = Bindings.createDoubleBinding(
                () -> {
                    double w = wrap.getWidth();
                    if (w <= 1.0) {
                        return HERO_TICKER_MIN_CELL_WIDTH_PX;
                    }
                    return Math.max(HERO_TICKER_MIN_CELL_WIDTH_PX, w * HERO_TICKER_IMAGE_WIDTH_FRACTION);
                },
                wrap.widthProperty());
        for (Image img : imgs) {
            ImageView iv = new ImageView(img);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setCache(true);
            iv.getStyleClass().add("home-hero-ticker-img");
            iv.fitWidthProperty().bind(cellWidth);
            iv.fitHeightProperty().bind(wrap.heightProperty());
            seg.getChildren().add(iv);
        }
        return seg;
    }

    /** Même hauteur que la nav dans {@code home.fxml} / {@code public-shell.fxml}. */
    public static final double DEFAULT_TOP_NAV_HEIGHT_PX = 88.0;
    /**
     * Bande « Actualités » ({@code home-news-ticker}) en bas du {@code BorderPane} : doit être réservée sinon le
     * calque hero (hauteur ≈ scène − nav) déborde et se peint par-dessus le ticker.
     */
    public static final double DEFAULT_BOTTOM_NEWS_TICKER_PX = 56.0;

    /**
     * Hauteur du bandeau hero ≈ fenêtre moins la barre du haut et le bandeau actualités du bas.
     */
    public static void bindPhotoWrapFullViewportBelowNav(StackPane heroPhotoWrap) {
        bindPhotoWrapFullViewportBelowNav(heroPhotoWrap, DEFAULT_TOP_NAV_HEIGHT_PX, DEFAULT_BOTTOM_NEWS_TICKER_PX);
    }

    /**
     * @param topReservedPx    espace réservé en haut (barre nav)
     * @param bottomReservedPx espace réservé en bas (bande actualités)
     */
    public static void bindPhotoWrapFullViewportBelowNav(
            StackPane heroPhotoWrap,
            double topReservedPx,
            double bottomReservedPx) {
        Runnable apply = () -> {
            Scene sc = heroPhotoWrap.getScene();
            if (sc == null) {
                return;
            }
            heroPhotoWrap.minHeightProperty().unbind();
            heroPhotoWrap.prefHeightProperty().unbind();
            /* Pas de plafond artificiel : largeur/hauteur max = illimité (équivalent à l’ancien Infinity en FXML/CSS). */
            heroPhotoWrap.setMaxWidth(Double.MAX_VALUE);
            heroPhotoWrap.setMaxHeight(Double.MAX_VALUE);
            var h = Bindings.createDoubleBinding(
                    () -> Math.max(400.0, sc.getHeight() - topReservedPx - bottomReservedPx),
                    sc.heightProperty());
            heroPhotoWrap.minHeightProperty().bind(h);
            heroPhotoWrap.prefHeightProperty().bind(h);
        };
        heroPhotoWrap.sceneProperty().addListener((o, a, b) -> Platform.runLater(apply));
        Platform.runLater(apply);
    }

    private static void restartIfReady(StackPane wrap, HBox track, HBox seg1, double pixelsPerSec) {
        track.applyCss();
        track.layout();
        double segmentW = seg1.getBoundsInLocal().getWidth();
        if (segmentW < 1) {
            return;
        }
        Object lastObj = wrap.getProperties().get(PROP_LAST_W);
        double lastW = lastObj instanceof Number num ? num.doubleValue() : -1;
        Object tlObj = wrap.getProperties().get(PROP_TIMELINE);
        if (Math.abs(segmentW - lastW) < 0.5 && tlObj instanceof Timeline t
                && t.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        wrap.getProperties().put(PROP_LAST_W, segmentW);
        if (tlObj instanceof Timeline old) {
            old.stop();
        }
        track.setTranslateX(0);
        double durationSec = clamp(segmentW / pixelsPerSec, 16, 90);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(track.translateXProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(durationSec),
                        new KeyValue(track.translateXProperty(), -segmentW, Interpolator.LINEAR)));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        wrap.getProperties().put(PROP_TIMELINE, timeline);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
