package org.example.ui.product;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Visuel neutre quand aucune photo produit n’est disponible (évite les icônes système / emoji).
 */
public final class ProductImagePlaceholder {

    private ProductImagePlaceholder() {
    }

    /**
     * @param width  largeur du cadre image
     * @param height hauteur du cadre image
     */
    public static StackPane create(double width, double height) {
        StackPane pane = new StackPane();
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        double r = Math.min(12, Math.min(width, height) * 0.05);
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#f1f5f9"));
        gc.fillRoundRect(0, 0, width, height, r, r);
        gc.setStroke(Color.web("#e2e8f0"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(0.5, 0.5, width - 1, height - 1, r, r);
        double hBase = height * 0.55;
        gc.setFill(Color.web("#cbd5e1"));
        double[] x1 = {0, width * 0.42, width * 0.72, width};
        double[] y1 = {height, hBase + height * 0.14, hBase, height};
        gc.fillPolygon(x1, y1, 4);
        gc.setFill(Color.web("#e2e8f0"));
        double[] x2 = {0, width * 0.32, width * 0.88};
        double[] y2 = {height, hBase + height * 0.04, height};
        gc.fillPolygon(x2, y2, 3);
        double sunR = Math.min(width, height) * 0.07;
        gc.setFill(Color.web("#e2e8f0"));
        gc.fillOval(width * 0.66 - sunR / 2, height * 0.12 - sunR / 2, sunR, sunR);
        pane.getChildren().add(canvas);
        return pane;
    }
}
