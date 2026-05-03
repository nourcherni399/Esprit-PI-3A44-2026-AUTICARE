package com.ecommerce.dashboard.view.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class KPICard extends VBox {
    private final Label valueLabel = new Label("0");

    public KPICard(String icon, String label, int targetValue, String suffix, String trend, String trendStyle) {
        getStyleClass().add("kpi-card");
        setPadding(new Insets(14));
        setSpacing(8);
        setAlignment(Pos.TOP_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("kpi-icon");

        valueLabel.getStyleClass().add("kpi-value");
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("kpi-label");

        Label trendLabel = new Label(trend);
        trendLabel.getStyleClass().addAll("kpi-trend", trendStyle);

        getChildren().addAll(iconLabel, valueLabel, nameLabel, trendLabel);
        animateCounter(targetValue, suffix == null ? "" : suffix);
    }

    private void animateCounter(int target, String suffix) {
        Timeline timeline = new Timeline();
        int steps = 24;
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(25L * i), e -> {
                int value = (int) Math.round((target * step) / (double) steps);
                valueLabel.setText(value + suffix);
            }));
        }
        timeline.play();
    }
}
