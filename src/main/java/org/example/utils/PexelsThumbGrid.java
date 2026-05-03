package org.example.utils;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import org.example.services.PexelsService;

import java.util.List;
import java.util.function.Consumer;

/** Mini-grille de vignettes pour les résultats Pexels. */
public final class PexelsThumbGrid {

    private PexelsThumbGrid() {}

    public static void fill(FlowPane pane, List<PexelsService.PexelsPhoto> photos, Consumer<PexelsService.PexelsPhoto> onPick) {
        if (pane == null) {
            return;
        }
        pane.getChildren().clear();
        if (photos == null || photos.isEmpty()) {
            return;
        }
        for (PexelsService.PexelsPhoto ph : photos) {
            Image img = new Image(ph.thumbUrl(), 116, 86, false, true, false);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(116);
            iv.setFitHeight(86);
            iv.setPreserveRatio(true);
            iv.setPickOnBounds(true);
            StackPane cell = new StackPane(iv);
            cell.setAlignment(Pos.CENTER);
            cell.setMinSize(116, 86);
            cell.setPrefSize(116, 86);
            cell.setMaxSize(116, 86);
            if (img.isError()) {
                cell.getChildren().setAll(new Label("Image indisponible"));
            }
            cell.getStyleClass().add("pexels-thumb-cell");
            cell.setCursor(Cursor.HAND);
            Runnable pickAction = () -> onPick.accept(ph);
            cell.setOnMouseClicked(e -> {
                if (e.getClickCount() >= 1) {
                    pickAction.run();
                }
            });
            iv.setOnMouseClicked(e -> pickAction.run());
            pane.getChildren().add(cell);
        }
    }
}
