package org.example.ui.product;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.scene.control.Tooltip;
import org.example.models.Product;
import org.example.services.AdminProductPredictionService;
import org.example.services.AdminProductPredictionService.PredictionSnapshot;
import org.example.services.AdminProductPredictionService.ProductSalesPoint;
import org.example.services.AdminProductPredictionService.StockRiskPoint;
import org.example.services.ProductService;
import org.example.utils.ProductImageLoader;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdminCatalogPredictionWindow {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-catalog-prediction");
        t.setDaemon(true);
        return t;
    });

    private AdminCatalogPredictionWindow() {
    }

    public static void show(Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Prédictions ventes & ruptures");

        Label status = new Label("Chargement des prédictions…");
        status.setStyle("-fx-text-fill: #64748b;");
        Label title = new Label("Prédiction produits et ruptures");
        title.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        VBox content = new VBox(8, new Label("…"));
        VBox.setVgrow(content, Priority.ALWAYS);

        Button close = new Button("Fermer");
        close.setOnAction(e -> stage.close());
        close.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-padding: 8 16;");

        HBox foot = new HBox(10, status, close);
        foot.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.ALWAYS);

        VBox root = new VBox(12, title, content, foot);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8fafc, #f1f5f9);");

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #faf9f7; -fx-background-color: #faf9f7;");

        stage.setScene(new Scene(sp, 1060, 740));
        stage.show();

        EXEC.submit(() -> {
            try {
                PredictionSnapshot snap = new AdminProductPredictionService().buildSnapshot();
                Platform.runLater(() -> {
                    content.getChildren().setAll(buildSinglePage(snap));
                    status.setText(snap.note());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Erreur prévision : " + ex.getMessage()));
            }
        });
    }

    private static VBox buildSinglePage(PredictionSnapshot snap) {
        Label h1 = new Label("Courbe historique — produits les plus/moins vendus (90 jours)");
        h1.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        BarChart<String, Number> hist = buildHistoryChart(snap.topSold(), snap.leastSold());

        Label h2 = new Label("Courbe prédictive — prochains plus/moins vendus");
        h2.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        BarChart<String, Number> pred = buildPredictionChart(snap.predictedTop(), snap.predictedLeast());

        Label h = new Label("Stocks qui risquent d'être en rupture prochainement");
        h.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label sub = new Label("Projection = stock actuel - demande prédite (30 jours).");
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        FlowPane riskCards = buildRiskCards(snap.riskStocks());
        ScrollPane riskScroll = new ScrollPane(riskCards);
        riskScroll.setFitToWidth(true);
        riskScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        riskScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        riskScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        riskScroll.setMinViewportHeight(360);

        return new VBox(14, h1, hist, h2, pred, h, sub, riskScroll);
    }

    private static FlowPane buildRiskCards(List<StockRiskPoint> risks) {
        FlowPane flow = new FlowPane(12, 12);
        flow.setPadding(new Insets(4));
        flow.setPrefWrapLength(980);

        Map<Integer, Product> byId = new HashMap<>();
        try {
            for (Product p : new ProductService().findAll()) {
                byId.put(p.getId(), p);
            }
        } catch (SQLException ignored) {
            // fallback sans image
        }

        if (risks == null || risks.isEmpty()) {
            Label empty = new Label("Aucun produit à risque pour le moment.");
            empty.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            flow.getChildren().add(empty);
            return flow;
        }

        for (StockRiskPoint r : risks) {
            Product p = byId.get(r.productId());
            flow.getChildren().add(buildRiskCard(r, p));
        }
        return flow;
    }

    private static VBox buildRiskCard(StockRiskPoint r, Product p) {
        VBox card = new VBox(8);
        card.setPrefWidth(235);
        card.setMinWidth(235);
        card.setPadding(new Insets(10));
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-radius: 12; "
                + "-fx-border-color: #d1d5db; -fx-border-width: 1.5; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.12), 8, 0.2, 0, 2);"
        );

        Label name = new Label(r.productName());
        name.setWrapText(true);
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #111827;");

        String badgeText = r.projectedStock() < 0 ? "RUPTURE IMMINENTE" : "STOCK CRITIQUE";
        String badgeColor = r.projectedStock() < 0 ? "#dc2626" : "#d97706";
        Label badge = new Label(badgeText);
        badge.setStyle(
            "-fx-background-color: " + badgeColor + "; -fx-text-fill: white; -fx-font-size: 10px; "
                + "-fx-font-weight: 700; -fx-padding: 4 8; -fx-background-radius: 999;"
        );
        HBox top = new HBox(8, name, badge);
        HBox.setHgrow(name, Priority.ALWAYS);
        top.setAlignment(Pos.TOP_LEFT);

        StackPane imageHost = new StackPane();
        imageHost.setPrefSize(95, 80);
        imageHost.setMinSize(95, 80);
        imageHost.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e5e7eb; -fx-border-radius: 10;");
        if (p != null && p.getPrimaryImagePath() != null && !p.getPrimaryImagePath().isBlank()) {
            Image im = ProductImageLoader.loadForDisplay(p.getPrimaryImagePath(), 90, 74);
            if (im != null && !im.isError()) {
                ImageView iv = new ImageView(im);
                iv.setFitWidth(90);
                iv.setFitHeight(74);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                imageHost.getChildren().add(iv);
            } else {
                imageHost.getChildren().add(ProductImagePlaceholder.create(90, 74));
            }
        } else {
            imageHost.getChildren().add(ProductImagePlaceholder.create(90, 74));
        }

        Label projected = new Label(String.valueOf(r.projectedStock()));
        projected.setStyle(
            "-fx-font-size: 42px; -fx-font-weight: 800; -fx-text-fill: "
                + (r.projectedStock() < 0 ? "#dc2626" : "#d97706") + ";"
        );
        HBox mid = new HBox(12, imageHost, projected);
        mid.setAlignment(Pos.CENTER_LEFT);

        Label metrics = new Label(
            "Stock actuel  " + r.currentStock()
                + "   |   Seuil  " + r.threshold()
                + "   |   Demande prévue  " + r.predictedDemand()
        );
        metrics.setWrapText(true);
        metrics.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Label foot = new Label("Stockout estimé : 30 jours");
        foot.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        card.getChildren().addAll(top, mid, metrics, foot);
        return card;
    }

    private static BarChart<String, Number> buildHistoryChart(List<ProductSalesPoint> top, List<ProductSalesPoint> least) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Quantité vendue");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setMinHeight(280);
        chart.setTitle("Historique ventes");
        x.setTickLabelRotation(-12);
        x.setTickLabelGap(8);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);
        chart.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-width: 1; -fx-padding: 8;");

        XYChart.Series<String, Number> sTop = new XYChart.Series<>();
        sTop.setName("Plus vendus");
        for (ProductSalesPoint p : top) {
            sTop.getData().add(new XYChart.Data<>(p.productName(), p.soldQty()));
        }

        XYChart.Series<String, Number> sLeast = new XYChart.Series<>();
        sLeast.setName("Moins vendus");
        for (ProductSalesPoint p : least) {
            sLeast.getData().add(new XYChart.Data<>(p.productName(), p.soldQty()));
        }

        chart.getData().addAll(sTop, sLeast);
        styleBars(sTop, "#2563eb");
        styleBars(sLeast, "#dc2626");
        return chart;
    }

    private static BarChart<String, Number> buildPredictionChart(List<ProductSalesPoint> top, List<ProductSalesPoint> least) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Demande prévue");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setMinHeight(280);
        chart.setTitle("Prédiction prochains produits");
        x.setTickLabelRotation(-12);
        x.setTickLabelGap(8);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);
        chart.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-width: 1; -fx-padding: 8;");

        XYChart.Series<String, Number> sTop = new XYChart.Series<>();
        sTop.setName("Vont être les plus vendus");
        for (ProductSalesPoint p : top) {
            sTop.getData().add(new XYChart.Data<>(p.productName(), p.predictedQty()));
        }

        XYChart.Series<String, Number> sLeast = new XYChart.Series<>();
        sLeast.setName("Vont être les moins vendus");
        for (ProductSalesPoint p : least) {
            sLeast.getData().add(new XYChart.Data<>(p.productName(), p.predictedQty()));
        }

        chart.getData().addAll(sTop, sLeast);
        styleBars(sTop, "#2563eb");
        styleBars(sLeast, "#dc2626");
        return chart;
    }

    private static void styleBars(XYChart.Series<String, Number> series, String color) {
        for (XYChart.Data<String, Number> d : series.getData()) {
            d.nodeProperty().addListener((obs, oldNode, node) -> {
                if (node != null) {
                    node.setStyle("-fx-bar-fill: " + color + ";");
                    Tooltip.install(node, new Tooltip("Quantité: " + d.getYValue()));
                }
            });
        }
    }
}
