package com.ecommerce.dashboard.view.components;

import javafx.scene.Node;
import javafx.scene.layout.VBox;

public class AnimatedChart extends VBox {
    public AnimatedChart(String title, Node chartNode) {
        getStyleClass().add("panel-card");
        getChildren().add(chartNode);
    }
}
