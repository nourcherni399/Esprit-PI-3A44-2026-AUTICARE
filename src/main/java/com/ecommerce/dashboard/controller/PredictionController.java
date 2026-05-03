package com.ecommerce.dashboard.controller;

import com.ecommerce.dashboard.model.PredictionPoint;
import com.ecommerce.dashboard.model.PredictionReport;
import com.ecommerce.dashboard.model.Product;
import com.ecommerce.dashboard.model.ProductPrediction;
import com.ecommerce.dashboard.service.AIAnalysisService;
import com.ecommerce.dashboard.service.DataService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.List;

public class PredictionController {
    private final AIAnalysisService aiService;
    private final DataService dataService;

    private final LineChart<String, Number> predictionChart;
    private final Label growthLabel = new Label("📈 Croissance prévue: --");
    private final Label precisionLabel = new Label("🎯 Précision modèle: --");
    private final Label peakLabel = new Label("⚡ Pic prévu: --");
    private final Label recoLabel = new Label("💡 Recommandation: --");
    private final ProgressBar growthBar = new ProgressBar(0);
    private final ProgressBar precisionBar = new ProgressBar(0);
    private final ProgressBar peakBar = new ProgressBar(0);
    private final ProgressBar recoBar = new ProgressBar(0);
    private final TableView<ProductPrediction> table = new TableView<>();
    private final TextArea actionsArea = new TextArea();
    private final Label statusLabel = new Label("Prêt");

    public PredictionController(AIAnalysisService aiService, DataService dataService) {
        this.aiService = aiService;
        this.dataService = dataService;
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        x.setLabel("Mois");
        y.setLabel("Volume de ventes");
        predictionChart = new LineChart<>(x, y);
        predictionChart.setAnimated(false);
        predictionChart.setCreateSymbols(true);
        predictionChart.setLegendVisible(true);
        predictionChart.setTitle("Historique vs Prédiction IA + Confiance");
    }

    public VBox buildPredictionSection() {
        Label sectionTitle = new Label("🤖 Prédictions IA - Prochains 3 Mois");
        sectionTitle.getStyleClass().add("panel-title");

        VBox left = new VBox(8, predictionChart, statusLabel);
        left.getStyleClass().add("panel-card");
        VBox.setVgrow(predictionChart, Priority.ALWAYS);

        Button generate = new Button("Générer Rapport IA");
        generate.getStyleClass().add("accent-btn");
        generate.setOnAction(e -> refreshPrediction(generate));

        VBox right = new VBox(
            8,
            title("Analyse Prédictive"),
            metric(growthLabel, growthBar),
            metric(precisionLabel, precisionBar),
            metric(peakLabel, peakBar),
            metric(recoLabel, recoBar),
            generate
        );
        right.getStyleClass().add("panel-card");
        right.setPrefWidth(380);

        HBox row = new HBox(14, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);

        buildPredictionTable();
        actionsArea.setEditable(false);
        actionsArea.setWrapText(true);
        actionsArea.setPrefRowCount(4);
        actionsArea.getStyleClass().add("analysis-area");
        VBox tableCard = new VBox(8, title("Données structurées des prédictions"), table);
        tableCard.getStyleClass().add("panel-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox actionsCard = new VBox(8, title("Recommandations concrètes"), actionsArea);
        actionsCard.getStyleClass().add("panel-card");

        VBox root = new VBox(10, sectionTitle, row, tableCard, actionsCard);
        refreshPrediction(null);
        return root;
    }

    private void refreshPrediction(Button generateBtn) {
        if (generateBtn != null) {
            generateBtn.setDisable(true);
            generateBtn.setText("Génération...");
        }
        statusLabel.setText("Analyse IA en cours...");
        new Thread(() -> {
            List<Product> products = dataService.topRevenueProducts();
            PredictionReport report = aiService.buildPredictionReport(products, dataService.salesHistory());
            Platform.runLater(() -> {
                renderReport(report);
                statusLabel.setText("Analyse prête");
                if (generateBtn != null) {
                    generateBtn.setDisable(false);
                    generateBtn.setText("Générer Rapport IA");
                }
            });
        }, "prediction-ia-thread").start();
    }

    private void renderReport(PredictionReport report) {
        growthLabel.setText(String.format("📈 Croissance prévue: +%.1f%%", report.expectedGrowthPct()));
        precisionLabel.setText(String.format("🎯 Précision modèle: %.1f%%", report.modelPrecisionPct()));
        peakLabel.setText("⚡ Pic prévu: " + report.peakPeriod());
        recoLabel.setText("💡 Recommandation: " + report.keyRecommendation());

        growthBar.setProgress(Math.min(1.0, Math.max(0.0, report.expectedGrowthPct() / 30.0)));
        precisionBar.setProgress(Math.min(1.0, Math.max(0.0, report.modelPrecisionPct() / 100.0)));
        peakBar.setProgress(0.85);
        recoBar.setProgress(0.90);

        predictionChart.getData().clear();
        XYChart.Series<String, Number> hist = new XYChart.Series<>();
        hist.setName("● Historique");
        XYChart.Series<String, Number> pred = new XYChart.Series<>();
        pred.setName("◌ Prédiction IA");
        XYChart.Series<String, Number> low = new XYChart.Series<>();
        low.setName("▨ Borne basse");
        XYChart.Series<String, Number> high = new XYChart.Series<>();
        high.setName("▨ Borne haute");

        for (PredictionPoint p : report.curvePoints()) {
            if (p.predictedPeriod()) {
                pred.getData().add(new XYChart.Data<>(p.periodLabel(), p.predictedValue()));
                low.getData().add(new XYChart.Data<>(p.periodLabel(), p.lowerBound()));
                high.getData().add(new XYChart.Data<>(p.periodLabel(), p.upperBound()));
            } else {
                hist.getData().add(new XYChart.Data<>(p.periodLabel(), p.historicalValue()));
                low.getData().add(new XYChart.Data<>(p.periodLabel(), p.historicalValue()));
                high.getData().add(new XYChart.Data<>(p.periodLabel(), p.historicalValue()));
            }
        }
        predictionChart.getData().addAll(hist, pred, low, high);
        styleSeries(hist, "#58A6FF", false);
        styleSeries(pred, "#8B5CF6", true);
        styleSeries(low, "#F0883E", true);
        styleSeries(high, "#3FB950", true);

        table.getItems().setAll(report.productRows());
        actionsArea.setText(String.join("\n• ", prependBullet(report.actions())));
    }

    private static List<String> prependBullet(List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of("Aucune recommandation.");
        }
        return actions;
    }

    private void styleSeries(XYChart.Series<String, Number> series, String color, boolean dashed) {
        Platform.runLater(() -> {
            if (series.getNode() != null) {
                String dash = dashed ? " -fx-stroke-dash-array: 8 6;" : "";
                series.getNode().setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2.4;" + dash);
            }
            for (XYChart.Data<String, Number> d : series.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle("-fx-background-color: " + color + ", #0D1117;");
                }
            }
        });
    }

    private void buildPredictionTable() {
        TableColumn<ProductPrediction, String> c1 = new TableColumn<>("Produit");
        c1.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().productName()));
        TableColumn<ProductPrediction, String> c2 = new TableColumn<>("Période");
        c2.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().period()));
        TableColumn<ProductPrediction, Integer> c3 = new TableColumn<>("Qté prévue");
        c3.setCellValueFactory(v -> new SimpleIntegerProperty(v.getValue().predictedQuantity()).asObject());
        TableColumn<ProductPrediction, Double> c4 = new TableColumn<>("Confiance");
        c4.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().confidence()).asObject());
        TableColumn<ProductPrediction, Integer> c5 = new TableColumn<>("Stock recommandé");
        c5.setCellValueFactory(v -> new SimpleIntegerProperty(v.getValue().recommendedStock()).asObject());
        TableColumn<ProductPrediction, String> c6 = new TableColumn<>("Action");
        c6.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().action()));
        table.getColumns().setAll(c1, c2, c3, c4, c5, c6);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-title");
        return l;
    }

    private VBox metric(Label label, ProgressBar bar) {
        label.getStyleClass().add("meta-row");
        bar.setPrefWidth(320);
        return new VBox(4, label, bar);
    }
}
