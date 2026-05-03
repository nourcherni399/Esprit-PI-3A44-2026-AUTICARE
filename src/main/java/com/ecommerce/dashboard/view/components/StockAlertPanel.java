package com.ecommerce.dashboard.view.components;

import com.ecommerce.dashboard.model.StockAlert;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class StockAlertPanel extends VBox {
    public StockAlertPanel(List<StockAlert> alerts) {
        getStyleClass().add("panel-card");
        setSpacing(10);
        setPadding(new Insets(12));

        Label title = new Label("⚠️ Alertes de Stock Critique");
        title.getStyleClass().add("panel-title");
        getChildren().add(title);

        for (StockAlert alert : alerts) {
            VBox row = new VBox(4);
            row.getStyleClass().add("stock-row");
            Label name = new Label(alert.productName());
            name.getStyleClass().add("stock-name");
            double ratio = Math.min(1.0, alert.stock() / (double) Math.max(1, alert.threshold()));
            ProgressBar bar = new ProgressBar(ratio);
            bar.getStyleClass().add(alert.critical() ? "stock-critical" : "stock-warning");
            Label meta = new Label("Stock: " + alert.stock() + " / Seuil: " + alert.threshold());
            String badgeText = alert.critical() ? "CRITIQUE" : "ATTENTION";
            Label badge = new Label(badgeText);
            badge.getStyleClass().add(alert.critical() ? "badge-critical" : "badge-warning");
            Button orderBtn = new Button("Commander");
            orderBtn.getStyleClass().add("small-btn");
            HBox last = new HBox(8, meta, badge, orderBtn);
            HBox.setHgrow(meta, Priority.ALWAYS);
            row.getChildren().addAll(name, bar, last);
            getChildren().add(row);
        }
    }
}
