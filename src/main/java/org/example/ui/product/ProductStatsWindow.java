package org.example.ui.product;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.AmbientLight;
import javafx.scene.PointLight;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
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

        VBox chart3d = build3dSalesCard(s);
        HBox topsRow = new HBox(16);
        topsRow.setAlignment(Pos.TOP_LEFT);
        VBox topChers = topBox("Top 5 — plus chers", s.topPlusChers(), true);
        VBox topMoins = topBox("Top 5 — moins chers", s.topMoinsChers(), false);
        HBox.setHgrow(topChers, Priority.ALWAYS);
        HBox.setHgrow(topMoins, Priority.ALWAYS);
        topsRow.getChildren().addAll(topChers, topMoins);

        VBox synth = synthSection(s);

        VBox page = new VBox(18, title, sub, kpiRow, chartsRow, chart3d, topsRow, synth);
        return page;
    }

    private static VBox build3dSalesCard(ProductStatsResult s) {
        Label h = new Label("Vue 3D — Produits par catégorie");
        h.setWrapText(true);
        h.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_MAIN + ";");

        Label hint = new Label("Glissez pour tourner, molette pour zoom.");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_MUTED + ";");

        List<CategoryCount> cats = s.parCategorie();
        if (cats == null || cats.isEmpty()) {
            VBox emptyCard = new VBox(8, h, new Label("Aucune donnée catégorie pour le rendu 3D."), hint);
            emptyCard.setPadding(new Insets(12));
            emptyCard.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: " + BORDER
                    + "; -fx-border-radius: 12; -fx-border-width: 1;"
            );
            return emptyCard;
        }

        Group root3d = new Group();
        Group barsGroup = new Group();
        root3d.getChildren().add(barsGroup);

        int n = Math.min(8, cats.size());
        double spacing = 85.0;
        double startX = -((n - 1) * spacing) / 2.0;
        int maxCount = cats.stream().mapToInt(CategoryCount::nombre).max().orElse(1);
        if (maxCount <= 0) {
            maxCount = 1;
        }

        Box ground = new Box(720, 2, 300);
        ground.setTranslateY(120);
        PhongMaterial groundMat = new PhongMaterial(Color.web("#e2e8f0"));
        ground.setMaterial(groundMat);
        barsGroup.getChildren().add(ground);

        // Axes (X horizontal catégories, Y vertical volume)
        Box xAxis = new Box(720, 2, 2);
        xAxis.setTranslateY(120);
        xAxis.setMaterial(new PhongMaterial(Color.web("#64748b")));
        Box yAxis = new Box(2, 240, 2);
        yAxis.setTranslateX(startX - 70);
        yAxis.setTranslateY(0);
        yAxis.setMaterial(new PhongMaterial(Color.web("#64748b")));
        barsGroup.getChildren().addAll(xAxis, yAxis);

        // Lignes de grille pour lecture visuelle des hauteurs
        for (int i = 1; i <= 4; i++) {
            double y = 120 - (i * 45.0);
            Box grid = new Box(720, 1, 1);
            grid.setTranslateY(y);
            grid.setTranslateZ(80);
            grid.setMaterial(new PhongMaterial(Color.web("#cbd5e1")));
            barsGroup.getChildren().add(grid);
        }

        Color[] palette = {
            Color.web("#3b82f6"),
            Color.web("#16a34a"),
            Color.web("#f59e0b"),
            Color.web("#8b5cf6"),
            Color.web("#ef4444"),
            Color.web("#06b6d4"),
            Color.web("#84cc16"),
            Color.web("#f97316")
        };

        VBox legend = new VBox(8);
        legend.setMinWidth(230);
        legend.setPrefWidth(230);
        legend.setPadding(new Insets(6, 4, 6, 6));
        for (int i = 0; i < n; i++) {
            CategoryCount c = cats.get(i);
            double hVal = 28.0 + (190.0 * c.nombre() / maxCount);
            Box bar = new Box(48, hVal, 48);
            bar.setTranslateX(startX + i * spacing);
            bar.setTranslateY(120.0 - hVal / 2.0);
            bar.setTranslateZ(0.0);
            PhongMaterial mat = new PhongMaterial(palette[i % palette.length]);
            bar.setMaterial(mat);
            barsGroup.getChildren().add(bar);

            Label dot = new Label("■");
            dot.setStyle("-fx-font-size: 12px; -fx-text-fill: " + toHex(palette[i % palette.length]) + ";");
            Label text = new Label(c.categorieLabel() + "  (" + c.nombre() + ")");
            text.setWrapText(true);
            text.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
            HBox row = new HBox(6, dot, text);
            row.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(row);
        }

        AmbientLight ambient = new AmbientLight(Color.color(0.75, 0.75, 0.75));
        PointLight keyLight = new PointLight(Color.WHITE);
        keyLight.setTranslateX(-220);
        keyLight.setTranslateY(-180);
        keyLight.setTranslateZ(-260);
        root3d.getChildren().addAll(ambient, keyLight);

        Rotate rx = new Rotate(-20, Rotate.X_AXIS);
        Rotate ry = new Rotate(-32, Rotate.Y_AXIS);
        barsGroup.getTransforms().addAll(rx, ry);

        SubScene sub = new SubScene(root3d, 700, 340, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#f8fafc"));

        PerspectiveCamera cam = new PerspectiveCamera(true);
        cam.setNearClip(0.1);
        cam.setFarClip(3000.0);
        cam.setTranslateZ(-980);
        cam.setTranslateY(-40);
        sub.setCamera(cam);

        final double[] anchor = new double[2];
        sub.setOnMousePressed(e -> {
            anchor[0] = e.getSceneX();
            anchor[1] = e.getSceneY();
        });
        sub.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - anchor[0];
            double dy = e.getSceneY() - anchor[1];
            ry.setAngle(ry.getAngle() + dx * 0.3);
            rx.setAngle(Math.max(-70, Math.min(10, rx.getAngle() - dy * 0.2)));
            anchor[0] = e.getSceneX();
            anchor[1] = e.getSceneY();
        });
        sub.setOnScroll(e -> {
            double z = cam.getTranslateZ() + (e.getDeltaY() > 0 ? 60 : -60);
            cam.setTranslateZ(Math.max(-1500, Math.min(-520, z)));
        });

        VBox legendCard = new VBox(8, new Label("Légende"), legend);
        legendCard.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #e2e8f0;"
                + "-fx-border-radius: 10; -fx-padding: 8;"
        );
        ((Label) legendCard.getChildren().get(0))
            .setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #334155;");

        HBox content = new HBox(12, sub, legendCard);
        HBox.setHgrow(sub, Priority.ALWAYS);

        VBox card = new VBox(8, h, content, hint);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: " + BORDER
                + "; -fx-border-radius: 12; -fx-border-width: 1;"
        );
        return card;
    }

    private static String toHex(Color color) {
        int r = (int) Math.round(color.getRed() * 255.0);
        int g = (int) Math.round(color.getGreen() * 255.0);
        int b = (int) Math.round(color.getBlue() * 255.0);
        return String.format("#%02x%02x%02x", r, g, b);
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
