package org.example.ui.product;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.application.Platform;
import org.example.models.Product;
import org.example.stats.ProductStatsCalculator;
import org.example.stats.ProductStatsCalculator.CategoryCount;
import org.example.stats.ProductStatsCalculator.CategoryPrixMoyen;
import org.example.stats.ProductStatsCalculator.ProductStatsResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Fenêtre « Statistiques des produits » proche de {@code templates/admin/stats/index.html.twig}
 * (cartes KPI, histogrammes par catégorie, tops prix). Les stats paniers web ne sont pas disponibles côté Java.
 */
public final class ProductStatsWindow {

    private static final String ACCENT = "#A7C7E7";
    private static final String BORDER = "#E5E0D8";
    private static final String TEXT_MUTED = "#6B7280";
    private static final String TEXT_MAIN = "#4B5563";

    private ProductStatsWindow() {
    }

    public static void show(Window owner, ProductStatsResult stats) {
        show(owner, stats, "Statistiques des produits");
    }

    public static void show(Window owner, ProductStatsResult stats, String windowTitle) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(windowTitle != null && !windowTitle.isBlank() ? windowTitle : "Statistiques des produits");

        VBox root = buildContent(stats, windowTitle);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #faf9f7;");

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #faf9f7; -fx-background-color: #faf9f7;");

        Scene scene = new Scene(scroll, 980, 720);
        stage.setScene(scene);
        stage.show();
        Platform.runLater(() -> {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        });
    }

    private static VBox buildContent(ProductStatsResult s, String heading) {
        String updated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH));

        String h = heading != null && !heading.isBlank() ? heading : "Statistiques des produits";
        Label title = new Label(h);
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_MAIN + ";");

        Label sub = new Label("Dernière mise à jour : " + updated);
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_MUTED + ";");

        HBox kpiRow = new HBox(16);
        kpiRow.setAlignment(Pos.CENTER_LEFT);
        double pctDispo = s.totalProduits() > 0 ? (100.0 * s.produitsDisponibles() / s.totalProduits()) : 0;
        double pctIndispo = s.totalProduits() > 0 ? (100.0 * s.produitsIndisponibles() / s.totalProduits()) : 0;
        double pctPublie = s.totalProduits() > 0 ? (100.0 * s.produitsPublies() / s.totalProduits()) : 0;

        kpiRow.getChildren().addAll(
            kpiCard("Total produits", String.valueOf(s.totalProduits()), null, "#3b82f6", "Total catalogue"),
            kpiCard(
                "Produits disponibles",
                String.valueOf(s.produitsDisponibles()),
                String.format(Locale.FRENCH, "%.1f %% du total", pctDispo),
                "#16a34a",
                "disponibilite = oui"
            ),
            kpiCard(
                "Produits indisponibles",
                String.valueOf(s.produitsIndisponibles()),
                String.format(Locale.FRENCH, "%.1f %% du total", pctIndispo),
                "#dc2626",
                null
            ),
            kpiCard(
                "Produits publiés",
                String.valueOf(s.produitsPublies()),
                String.format(Locale.FRENCH, "%.1f %% du total", pctPublie),
                ACCENT,
                "visibles catalogue"
            )
        );
        HBox.setHgrow(kpiRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(kpiRow.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(kpiRow.getChildren().get(2), Priority.ALWAYS);
        HBox.setHgrow(kpiRow.getChildren().get(3), Priority.ALWAYS);

        HBox chartsRow = new HBox(16);
        chartsRow.setAlignment(Pos.TOP_LEFT);
        VBox chartCount = wrapChart(
            "Quantité de produits par catégorie",
            buildCountChart(s),
            "Comme le graphique 3D Symfony (vue 2D barrés)."
        );
        VBox chartPrice = wrapChart(
            "Prix moyen (DT) par catégorie",
            buildAvgPriceChart(s),
            "Moyenne arithmétique des prix unitaires."
        );
        HBox.setHgrow(chartCount, Priority.ALWAYS);
        HBox.setHgrow(chartPrice, Priority.ALWAYS);
        chartsRow.getChildren().addAll(chartCount, chartPrice);

        HBox topsRow = new HBox(16);
        topsRow.setAlignment(Pos.TOP_LEFT);
        VBox topChers = topBox("Top 5 — plus chers", s.topPlusChers(), true);
        VBox topMoins = topBox("Top 5 — moins chers", s.topMoinsChers(), false);
        HBox.setHgrow(topChers, Priority.ALWAYS);
        HBox.setHgrow(topMoins, Priority.ALWAYS);
        topsRow.getChildren().addAll(topChers, topMoins);

        VBox synth = synthSection(s);

        VBox page = new VBox(18, title, sub, kpiRow, chartsRow, topsRow, synth);
        return page;
    }

    private static VBox synthSection(ProductStatsResult s) {
        Label h = new Label("Synthèse catalogue");
        h.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_MAIN + ";");

        GridPane g = new GridPane();
        g.setHgap(24);
        g.setVgap(8);
        g.setPadding(new Insets(12));
        g.setStyle(
            "-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: " + BORDER
                + "; -fx-border-radius: 10; -fx-border-width: 1;"
        );

        int r = 0;
        g.add(rowLabel("Quantité totale (unités catalogue)"), 0, r);
        g.add(valueLabel(String.valueOf(s.quantiteTotaleCatalogue())), 1, r++);
        g.add(rowLabel("Valeur catalogue estimée (prix × quantité)"), 0, r);
        g.add(valueLabel(String.format(Locale.FRENCH, "%.2f DT", s.valeurCatalogueDt())), 1, r++);
        double prixMoyen = s.totalProduits() > 0
            ? s.valeurCatalogueDt() / Math.max(1, s.quantiteTotaleCatalogue())
            : 0;
        g.add(rowLabel("Prix moyen pondéré (sur quantités)"), 0, r);
        g.add(valueLabel(String.format(Locale.FRENCH, "%.2f DT", prixMoyen)), 1, r);

        return new VBox(8, h, g);
    }

    private static Label rowLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 13px;");
        return l;
    }

    private static Label valueLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: " + TEXT_MAIN + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        return l;
    }

    private static VBox kpiCard(String title, String value, String subtitle, String accentRgb, String hint) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(16));
        box.setMaxWidth(Double.MAX_VALUE);
        box.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: " + BORDER
                + "; -fx-border-radius: 12; -fx-border-width: 1;"
        );

        HBox head = new HBox();
        head.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_MUTED + ";");
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + accentRgb + "; -fx-font-size: 22px;");
        head.getChildren().addAll(t, spacer, dot);

        Label v = new Label(value);
        v.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_MAIN + ";");

        box.getChildren().add(head);
        box.getChildren().add(v);
        if (subtitle != null && !subtitle.isBlank()) {
            Label s = new Label(subtitle);
            s.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_MUTED + ";");
            box.getChildren().add(s);
        }
        if (hint != null && !hint.isBlank()) {
            Label h = new Label(hint);
            h.setStyle("-fx-font-size: 10px; -fx-text-fill: " + TEXT_MUTED + ";");
            box.getChildren().add(h);
        }
        return box;
    }

    private static VBox wrapChart(String heading, BarChart<String, Number> chart, String foot) {
        Label h = new Label(heading);
        h.setWrapText(true);
        h.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_MAIN + ";");
        Label f = new Label(foot);
        f.setWrapText(true);
        f.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_MUTED + ";");
        chart.setLegendVisible(false);
        chart.setPrefHeight(320);
        chart.setMinHeight(280);
        chart.setStyle("-fx-background-color: white;");
        VBox v = new VBox(8, h, chart, f);
        v.setPadding(new Insets(12));
        v.setMaxWidth(Double.MAX_VALUE);
        v.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: " + BORDER
                + "; -fx-border-radius: 12; -fx-border-width: 1;"
        );
        return v;
    }

    private static BarChart<String, Number> buildCountChart(ProductStatsResult s) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Produits");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(null);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Nombre");
        if (s.parCategorie().isEmpty()) {
            x.getCategories().add("—");
            series.getData().add(new XYChart.Data<>("—", 0));
        } else {
            for (CategoryCount c : s.parCategorie()) {
                series.getData().add(new XYChart.Data<>(c.categorieLabel(), c.nombre()));
            }
        }
        chart.getData().add(series);
        x.setTickLabelRotation(35);
        x.setTickLabelGap(2);
        styleSeries(series, ACCENT);
        return chart;
    }

    private static BarChart<String, Number> buildAvgPriceChart(ProductStatsResult s) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("DT");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(null);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Prix moyen");
        if (s.prixMoyensParCategorie().isEmpty()) {
            x.getCategories().add("—");
            series.getData().add(new XYChart.Data<>("—", 0));
        } else {
            for (CategoryPrixMoyen c : s.prixMoyensParCategorie()) {
                series.getData().add(new XYChart.Data<>(c.categorieLabel(), c.prixMoyen()));
            }
        }
        chart.getData().add(series);
        x.setTickLabelRotation(35);
        x.setTickLabelGap(2);
        styleSeries(series, "#16a34a");
        return chart;
    }

    private static void styleSeries(XYChart.Series<String, Number> series, String color) {
        for (XYChart.Data<String, Number> d : series.getData()) {
            Runnable apply = () -> {
                if (d.getNode() != null) {
                    d.getNode().setStyle("-fx-bar-fill: " + color + ";");
                }
            };
            d.nodeProperty().addListener((obs, o, n) -> apply.run());
            Platform.runLater(apply);
        }
    }

    private static VBox topBox(String title, List<Product> items, boolean expensive) {
        Label h = new Label(title);
        h.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_MAIN + ";");

        VBox list = new VBox(8);
        list.setPadding(new Insets(12));
        list.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: " + BORDER
                + "; -fx-border-radius: 12; -fx-border-width: 1;"
        );

        if (items == null || items.isEmpty()) {
            list.getChildren().add(new Label("Aucun produit"));
        } else {
            String priceColor = expensive ? "#dc2626" : "#16a34a";
            for (Product p : items) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                VBox left = new VBox(2);
                Label name = new Label(p.getNom() != null ? p.getNom() : "—");
                name.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: " + TEXT_MAIN + ";");
                Label cat = new Label(ProductStatsCalculator.categoryLabel(p.getCategorie()));
                cat.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_MUTED + ";");
                left.getChildren().addAll(name, cat);
                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);
                Label price = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
                price.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + priceColor + ";");
                row.getChildren().addAll(left, sp, price);
                row.setPadding(new Insets(6, 8, 6, 8));
                row.setStyle("-fx-border-color: " + BORDER + "; -fx-border-radius: 8; -fx-background-radius: 8;");
                list.getChildren().add(row);
            }
        }

        return new VBox(8, h, list);
    }
}
