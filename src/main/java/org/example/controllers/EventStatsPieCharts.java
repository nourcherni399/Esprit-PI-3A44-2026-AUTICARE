package org.example.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Camemberts des stats événements (période / inscriptions), alignés sur l’écran thématiques.
 */
public final class EventStatsPieCharts {

    private static final String PIE_GRAY_EMPTY = "#d1d5db";
    /** Futur (date de début après aujourd’hui) : rouge brique. */
    private static final String PIE_PERIOD_VENIR = "#c2410c";
    /** Déjà commencés / passés (date de début ≤ maintenant) : bleu. */
    private static final String PIE_PERIOD_PASSES = "#2563eb";
    private static final String PIE_INSC_ACC = "#22c55e";
    private static final String PIE_INSC_ATT = "#f97316";
    private static final String PIE_INSC_REF = "#ef4444";

    /** Classes Modena sur les pastilles : elles appliquent CHART_COLOR (souvent orange) si on ne les retire pas. */
    private static final String[] CHART_DEFAULT_COLOR_CLASSES = {
            "default-color0", "default-color1", "default-color2", "default-color3",
            "default-color4", "default-color5", "default-color6", "default-color7"
    };

    private EventStatsPieCharts() {
    }

    public static void configure(PieChart chart, boolean statsStyleFirstChart) {
        if (chart == null) {
            return;
        }
        String legendCls = statsStyleFirstChart ? "admin-thematiques-pie-stats" : "admin-thematiques-pie-events";
        if (!chart.getStyleClass().contains(legendCls)) {
            chart.getStyleClass().add(legendCls);
        }
        chart.setAnimated(false);
        chart.setClockwise(true);
        chart.setStartAngle(90);
        chart.setLabelsVisible(false);
        chart.setTitle("");
        chart.setMinWidth(200);
        chart.setMinHeight(190);
        chart.setPrefHeight(210);
        chart.setLegendVisible(true);
        chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        Platform.runLater(() -> centerLegend(chart));
    }

    public static void centerLegend(PieChart chart) {
        if (chart == null) {
            return;
        }
        Node legendNode = chart.lookup(".chart-legend");
        if (!(legendNode instanceof TilePane legend)) {
            return;
        }
        legend.setAlignment(Pos.CENTER);
        legend.setMaxWidth(Double.MAX_VALUE);
        /* Ne pas appeler applyCss() ici : il réapplique les couleurs Modena sur la légende
         * et annule syncLegendColors (pastilles orange au lieu des couleurs des parts). */
        chart.layout();
        double w = chart.getWidth();
        if (w > 0) {
            legend.setPrefWidth(w);
        }
        chart.requestLayout();
    }

    private static void applySliceColor(PieChart.Data d, String hex) {
        Node n = d.getNode();
        if (n != null) {
            n.setStyle("-fx-pie-color: " + hex + ";");
        }
    }

    /**
     * Préfixe les sélecteurs avec les classes du camembert (ex. {@code .chart.pie-chart.admin-thematiques-pie-stats})
     * pour battre la spécificité de {@code app.css} sur {@code .chart-legend-item-symbol} ; sinon les pastilles restent vides.
     */
    private static String chartScopedSelectorPrefix(PieChart chart) {
        StringBuilder sb = new StringBuilder();
        for (String c : chart.getStyleClass()) {
            if (c != null && !c.isBlank()) {
                sb.append('.').append(c);
            }
        }
        return sb.length() > 0 ? sb.toString() : ".chart.pie-chart";
    }

    private static final String LEGEND_SYM_DIMS =
            "-fx-background-insets: 0; "
                    + "-fx-min-width: 26px; -fx-max-width: 26px; "
                    + "-fx-min-height: 8px; -fx-max-height: 8px; "
                    + "-fx-pref-width: 26px; -fx-pref-height: 8px; "
                    + "-fx-background-radius: 2px; ";

    /**
     * Applique les couleurs par indice de série ({@code default-color0}, …) sur les parts et la légende,
     * comme le thème Modena. Plus fiable que la seule mise à jour des nœuds quand le CSS du graphe réapplique les teintes par défaut.
     * Au-delà de 8 parts, le cycle JavaFX fait se chevaucher les classes : on n’injecte pas de feuille (rester sur {@link #syncLegendColors}).
     */
    public static void applyIndexedPieChartColorsCss(PieChart chart, String... hexBySliceIndex) {
        if (chart == null || hexBySliceIndex == null || hexBySliceIndex.length == 0) {
            return;
        }
        if (hexBySliceIndex.length > CHART_DEFAULT_COLOR_CLASSES.length) {
            chart.setStyle("");
            return;
        }
        String scope = chartScopedSelectorPrefix(chart);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hexBySliceIndex.length; i++) {
            String h = hexBySliceIndex[i];
            if (h == null || h.isBlank()) {
                continue;
            }
            int sc = i % CHART_DEFAULT_COLOR_CLASSES.length;
            sb.append(scope).append(" .default-color").append(sc).append(".chart-pie { -fx-pie-color: ").append(h).append("; }\n");
            sb.append(scope)
                    .append(" .chart-legend-item-symbol.default-color")
                    .append(sc)
                    .append(", ")
                    .append(scope)
                    .append(" .default-color")
                    .append(sc)
                    .append(".chart-legend-item-symbol { -fx-background-color: ")
                    .append(h)
                    .append("; ")
                    .append(LEGEND_SYM_DIMS)
                    .append("}\n");
        }
        chart.setStyle(sb.toString());
    }

    /**
     * Les pastilles de légende JavaFX ne suivent pas automatiquement {@code -fx-pie-color}.
     * On aligne chaque pastille sur la part du même index (ordre de la légende = ordre des données).
     * {@code lookupAll} n’est pas ordonné : on parcourt les enfants de {@code .chart-legend}.
     * Ne pas invoquer {@code applyCss()} ici : cela réinitialiserait les pastilles.
     */
    public static void syncLegendColors(PieChart chart, Map<String, String> colorByLabel) {
        if (chart == null || colorByLabel == null || colorByLabel.isEmpty()) {
            return;
        }
        Runnable paint = () -> {
            if (chart.getScene() == null) {
                return;
            }
            chart.layout();
            ObservableList<PieChart.Data> data = chart.getData();
            if (data == null || data.isEmpty()) {
                return;
            }
            int painted = paintLegendOrderedByDataIndex(chart, colorByLabel, data);
            if (painted == 0) {
                paintLegendByMatchingLabels(chart, colorByLabel);
            }
        };
        Platform.runLater(() -> {
            paint.run();
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO, e -> paint.run()),
                    new KeyFrame(Duration.millis(40), e -> paint.run()),
                    new KeyFrame(Duration.millis(100), e -> paint.run()),
                    new KeyFrame(Duration.millis(200), e -> paint.run()));
            tl.play();
        });
    }

    /**
     * @return nombre de pastilles colorées
     */
    private static int paintLegendOrderedByDataIndex(
            PieChart chart, Map<String, String> colorByLabel, ObservableList<PieChart.Data> data) {
        Node legendNode = chart.lookup(".chart-legend");
        if (!(legendNode instanceof Parent legend)) {
            return 0;
        }
        int idx = 0;
        int painted = 0;
        for (Node item : legend.getChildrenUnmodifiable()) {
            if (!item.getStyleClass().contains("chart-legend-item")) {
                continue;
            }
            if (idx >= data.size()) {
                break;
            }
            PieChart.Data slice = data.get(idx);
            String sliceName = slice.getName();
            String hex = resolveHex(colorByLabel, sliceName != null ? sliceName : "");
            Node symNode = item.lookup(".chart-legend-item-symbol");
            if (hex != null && symNode instanceof Region sym) {
                applyHexToLegendRegion(sym, hex);
                painted++;
            }
            idx++;
        }
        return painted;
    }

    /** Secours : association par libellé affiché (si la structure de légende diffère). */
    private static void paintLegendByMatchingLabels(PieChart chart, Map<String, String> colorByLabel) {
        Set<Node> symbols = chart.lookupAll(".chart-legend-item-symbol");
        for (Node symNode : symbols) {
            if (!(symNode instanceof Region sym)) {
                continue;
            }
            String label = legendLabelBesideSymbol(symNode);
            if (label == null) {
                continue;
            }
            String hex = resolveHex(colorByLabel, label);
            if (hex != null) {
                applyHexToLegendRegion(sym, hex);
            }
        }
    }

    private static String legendLabelBesideSymbol(Node symbolNode) {
        Node parent = symbolNode.getParent();
        if (parent == null) {
            return null;
        }
        Node textNode = parent.lookup(".chart-legend-item-text");
        if (textNode instanceof Label lab) {
            return lab.getText();
        }
        return null;
    }

    private static String resolveHex(Map<String, String> colorByLabel, String rawLabel) {
        if (rawLabel == null) {
            return null;
        }
        String t = rawLabel.trim();
        String hex = colorByLabel.get(t);
        if (hex != null) {
            return hex;
        }
        for (Map.Entry<String, String> e : colorByLabel.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(t)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static void applyHexToLegendRegion(Region sym, String hex) {
        try {
            Color.web(hex);
        } catch (IllegalArgumentException e) {
            return;
        }
        sym.getStyleClass().removeAll(CHART_DEFAULT_COLOR_CLASSES);
        sym.setBackground(null);
        sym.setStyle(
                "-fx-background-color: "
                        + hex
                        + ";"
                        + "-fx-background-radius: 2px;"
                        + "-fx-min-width: 26px; -fx-max-width: 26px;"
                        + "-fx-min-height: 8px; -fx-max-height: 8px;"
                        + "-fx-pref-width: 26px; -fx-pref-height: 8px;");
    }

    private static void applySlicesThenLegend(PieChart chart, Runnable applySliceStyles, Map<String, String> legendColors) {
        Runnable full = () -> {
            chart.applyCss();
            chart.layout();
            applySliceStyles.run();
            syncLegendColors(chart, legendColors);
        };
        Platform.runLater(() -> {
            full.run();
            Platform.runLater(full);
        });
    }

    private static void applyAllSame(PieChart chart, String hex) {
        chart.applyCss();
        chart.layout();
        for (PieChart.Data d : chart.getData()) {
            applySliceColor(d, hex);
        }
    }

    private static String[] hexForPeriodSlices(ObservableList<PieChart.Data> data) {
        String[] out = new String[data.size()];
        for (int i = 0; i < data.size(); i++) {
            String name = data.get(i).getName();
            if ("À venir".equals(name)) {
                out[i] = PIE_PERIOD_VENIR;
            } else if ("Passés".equals(name)) {
                out[i] = PIE_PERIOD_PASSES;
            } else if ("Sans date".equals(name)) {
                out[i] = PIE_GRAY_EMPTY;
            } else {
                out[i] = PIE_GRAY_EMPTY;
            }
        }
        return out;
    }

    public static void rebuildPeriodPie(PieChart chart, long aVenir, long passes, long totalEvents) {
        if (chart == null) {
            return;
        }
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        if (totalEvents <= 0) {
            data.add(new PieChart.Data("Aucun événement", 1));
            chart.setData(data);
            applyIndexedPieChartColorsCss(chart, PIE_GRAY_EMPTY);
            Map<String, String> leg = new HashMap<>();
            leg.put("Aucun événement", PIE_GRAY_EMPTY);
            applySlicesThenLegend(chart, () -> applyAllSame(chart, PIE_GRAY_EMPTY), leg);
            Platform.runLater(() -> centerLegend(chart));
            return;
        }
        long accounted = aVenir + passes;
        long autres = totalEvents - accounted;
        if (aVenir == 0 && passes == 0 && autres > 0) {
            data.add(new PieChart.Data("Sans date de début", Math.max(1, autres)));
            chart.setData(data);
            applyIndexedPieChartColorsCss(chart, PIE_GRAY_EMPTY);
            Map<String, String> leg = new HashMap<>();
            leg.put("Sans date de début", PIE_GRAY_EMPTY);
            applySlicesThenLegend(chart, () -> applyAllSame(chart, PIE_GRAY_EMPTY), leg);
            Platform.runLater(() -> centerLegend(chart));
            return;
        }
        if (aVenir > 0) {
            data.add(new PieChart.Data("À venir", aVenir));
        }
        if (passes > 0) {
            data.add(new PieChart.Data("Passés", passes));
        }
        if (autres > 0) {
            data.add(new PieChart.Data("Sans date", autres));
        }
        if (data.isEmpty()) {
            data.add(new PieChart.Data("—", 1));
            chart.setData(data);
            applyIndexedPieChartColorsCss(chart, PIE_GRAY_EMPTY);
            Map<String, String> leg = new HashMap<>();
            leg.put("—", PIE_GRAY_EMPTY);
            applySlicesThenLegend(chart, () -> applyAllSame(chart, PIE_GRAY_EMPTY), leg);
            Platform.runLater(() -> centerLegend(chart));
            return;
        }
        chart.setData(data);
        applyIndexedPieChartColorsCss(chart, hexForPeriodSlices(data));
        Map<String, String> legend = new HashMap<>();
        legend.put("À venir", PIE_PERIOD_VENIR);
        legend.put("Passés", PIE_PERIOD_PASSES);
        legend.put("Sans date", PIE_GRAY_EMPTY);
        applySlicesThenLegend(chart, () -> {
            for (PieChart.Data d : chart.getData()) {
                String name = d.getName();
                if ("À venir".equals(name)) {
                    applySliceColor(d, PIE_PERIOD_VENIR);
                } else if ("Passés".equals(name)) {
                    applySliceColor(d, PIE_PERIOD_PASSES);
                } else if ("Sans date".equals(name)) {
                    applySliceColor(d, PIE_GRAY_EMPTY);
                }
            }
        }, legend);
        Platform.runLater(() -> centerLegend(chart));
    }

    public static void rebuildRegsPie(PieChart chart, long acc, long att, long ref) {
        if (chart == null) {
            return;
        }
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        long sum = acc + att + ref;
        if (sum <= 0) {
            data.add(new PieChart.Data("Aucune inscription", 1));
            chart.setData(data);
            Map<String, String> leg0 = new HashMap<>();
            leg0.put("Aucune inscription", PIE_GRAY_EMPTY);
            applySlicesThenLegend(chart, () -> applyAllSame(chart, PIE_GRAY_EMPTY), leg0);
            Platform.runLater(() -> centerLegend(chart));
            return;
        }
        if (acc > 0) {
            data.add(new PieChart.Data("Acceptées", acc));
        }
        if (att > 0) {
            data.add(new PieChart.Data("En attente", att));
        }
        if (ref > 0) {
            data.add(new PieChart.Data("Refusées", ref));
        }
        chart.setData(data);
        Map<String, String> leg = new HashMap<>();
        leg.put("Acceptées", PIE_INSC_ACC);
        leg.put("En attente", PIE_INSC_ATT);
        leg.put("Refusées", PIE_INSC_REF);
        applySlicesThenLegend(chart, () -> {
            for (PieChart.Data d : chart.getData()) {
                String name = d.getName();
                if ("Acceptées".equals(name)) {
                    applySliceColor(d, PIE_INSC_ACC);
                } else if ("En attente".equals(name)) {
                    applySliceColor(d, PIE_INSC_ATT);
                } else if ("Refusées".equals(name)) {
                    applySliceColor(d, PIE_INSC_REF);
                }
            }
        }, leg);
        Platform.runLater(() -> centerLegend(chart));
    }

    public static void rebuildRegsPieError(PieChart chart) {
        if (chart == null) {
            return;
        }
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList(new PieChart.Data("Données indisponibles", 1));
        chart.setData(data);
        Map<String, String> leg = new HashMap<>();
        leg.put("Données indisponibles", PIE_GRAY_EMPTY);
        applySlicesThenLegend(chart, () -> applyAllSame(chart, PIE_GRAY_EMPTY), leg);
        Platform.runLater(() -> centerLegend(chart));
    }
}
