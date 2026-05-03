package com.ecommerce.dashboard.controller;

import com.ecommerce.dashboard.model.Product;
import com.ecommerce.dashboard.service.AIAnalysisService;
import com.ecommerce.dashboard.service.ChartService;
import com.ecommerce.dashboard.service.DataService;
import com.ecommerce.dashboard.view.components.KPICard;
import com.ecommerce.dashboard.view.components.StockAlertPanel;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.chart.PieChart;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {
    private final DataService dataService = new DataService();
    private final ChartService chartService = new ChartService();
    private final AIAnalysisService aiService = new AIAnalysisService();
    private final PredictionController predictionController = new PredictionController(aiService, dataService);

    private final Label clockLabel = new Label();
    private final TextArea aiOutput = new TextArea("Cliquez sur 'Analyser' pour obtenir une analyse IA complète...");

    public BorderPane buildView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard-root");
        root.setTop(buildTopBar());
        root.setLeft(buildSidebar());
        root.setCenter(buildCenter());
        startClock();
        return root;
    }

    private HBox buildTopBar() {
        Label logo = new Label("▲");
        logo.getStyleClass().add("logo-icon");
        Label title = new Label("E-Commerce Intelligence Dashboard");
        title.getStyleClass().add("app-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label("● API Connected");
        status.getStyleClass().add("status-ok");

        Button refresh = new Button("⟳ Refresh");
        refresh.getStyleClass().add("small-btn");
        refresh.setOnAction(e -> {
            RotateTransition rt = new RotateTransition(Duration.millis(500), refresh);
            rt.setByAngle(360);
            rt.play();
        });

        HBox top = new HBox(14, logo, title, spacer, clockLabel, status, refresh);
        top.getStyleClass().add("top-bar");
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 14, 10, 14));
        return top;
    }

    private VBox buildSidebar() {
        VBox side = new VBox(8);
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(14));
        side.setPrefWidth(220);
        side.getChildren().addAll(
            navItem("📊 Vue Générale"),
            navItem("📈 Prédictions IA"),
            navItem("📦 Gestion Stock"),
            navItem("🏆 Top Produits"),
            navItem("⚙️ Paramètres")
        );
        return side;
    }

    private Button navItem(String t) {
        Button b = new Button(t);
        b.getStyleClass().add("nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private ScrollPane buildCenter() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        HBox kpis = new HBox(12,
            new KPICard("💰", "Chiffre d'Affaires Total", 4230, " DT", "▲ +12%", "trend-up"),
            new KPICard("📦", "Produits Actifs", 47, "", "▲ +8%", "trend-up"),
            new KPICard("⚠️", "Alertes Stock", 2, "", "▼ -5%", "trend-down"),
            new KPICard("⭐", "Score IA", 87, "/100", "▲ +4%", "trend-up")
        );

        var line = chartService.createSalesLineChart(dataService.salesHistory());
        var bar = chartService.createRevenueBarChart(dataService.topRevenueProducts());
        HBox chartRow = new HBox(12, panel("Évolution", line), panel("Top Revenus", bar));
        HBox.setHgrow(chartRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(chartRow.getChildren().get(1), Priority.ALWAYS);

        VBox predSection = predictionController.buildPredictionSection();

        PieChart pie = chartService.createCatalogPieChart(dataService.catalogDistribution());
        pie.getData().forEach(d -> d.getNode().setOnMouseClicked(e -> showSlicePopup(d.getName(), d.getPieValue())));

        HBox row4 = new HBox(12,
            new StockAlertPanel(dataService.stockAlerts()),
            panel("Répartition du Catalogue", pie)
        );
        HBox.setHgrow(row4.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row4.getChildren().get(1), Priority.ALWAYS);

        aiOutput.setWrapText(true);
        aiOutput.setEditable(false);
        aiOutput.getStyleClass().add("analysis-area");
        Button analyze = new Button("🔍 Analyser le Catalogue");
        analyze.getStyleClass().add("accent-btn");
        analyze.setOnAction(e -> runAiAnalysis());
        Button copy = new Button("📋 Copier le Rapport");
        copy.getStyleClass().add("small-btn");
        copy.setOnAction(e -> aiOutput.copy());
        HBox aiButtons = new HBox(8, analyze, copy);
        VBox aiPanel = new VBox(10, title("🧠 Analyse Intelligente - Anthropic Claude API"), aiOutput, aiButtons);
        aiPanel.getStyleClass().add("panel-card");

        content.getChildren().addAll(kpis, chartRow, predSection, row4, aiPanel);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    private VBox panel(String title, javafx.scene.Node node) {
        VBox v = new VBox(8, title(title), node);
        v.getStyleClass().add("panel-card");
        VBox.setVgrow(node, Priority.ALWAYS);
        return v;
    }

    private Label title(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("panel-title");
        return l;
    }

    private void runAiAnalysis() {
        aiOutput.setText("Analyse en cours...");
        new Thread(() -> {
            String prompt = "Analyse ces indicateurs e-commerce (ventes, top produits, alertes stock) et donne des actions concrètes sur 3 mois.";
            String answer = aiService.analyzeCatalog(prompt);
            Platform.runLater(() -> aiOutput.setText(answer));
        }, "dashboard-ai-analysis").start();
    }

    private void showSlicePopup(String name, double value) {
        Popup popup = new Popup();
        VBox box = new VBox(new Text(name + " : " + String.format("%.1f", value) + "%"));
        box.setStyle("-fx-background-color: #161B22; -fx-padding: 8; -fx-border-color: #58A6FF;");
        popup.getContent().add(box);
        popup.show(clockLabel.getScene().getWindow());
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(1.2), e -> popup.hide()));
        t.play();
    }

    private void startClock() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> clockLabel.setText(LocalDateTime.now().format(fmt))));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
}
