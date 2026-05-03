package com.ecommerce.dashboard.service;

import com.ecommerce.dashboard.model.Product;
import com.ecommerce.dashboard.model.SalesData;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.chart.*;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

public class ChartService {

    public LineChart<String, Number> createSalesLineChart(List<SalesData> data) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Mois");
        yAxis.setLabel("Ventes");
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setTitle("Évolution des Ventes (6 mois)");

        XYChart.Series<String, Number> a = new XYChart.Series<>();
        a.setName("Produit A");
        XYChart.Series<String, Number> b = new XYChart.Series<>();
        b.setName("Produit B");
        XYChart.Series<String, Number> c = new XYChart.Series<>();
        c.setName("Produit C");

        for (SalesData s : data) {
            a.getData().add(new XYChart.Data<>(s.month(), s.a()));
            b.getData().add(new XYChart.Data<>(s.month(), s.b()));
            c.getData().add(new XYChart.Data<>(s.month(), s.c()));
        }
        chart.getData().addAll(a, b, c);
        return chart;
    }

    public BarChart<String, Number> createRevenueBarChart(List<Product> products) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Produits");
        yAxis.setLabel("Revenu (DT)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setTitle("Top Produits par Revenus");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Product p : products) {
            XYChart.Data<String, Number> d = new XYChart.Data<>(p.name(), p.revenue());
            series.getData().add(d);
        }
        chart.getData().add(series);

        Timeline tl = new Timeline();
        int i = 1;
        for (XYChart.Data<String, Number> d : series.getData()) {
            Number target = d.getYValue();
            d.setYValue(0);
            Tooltip.install(d.getNode(), new Tooltip(String.format("%.2f DT", target.doubleValue())));
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(120L * i++), e -> d.setYValue(target)));
        }
        tl.play();
        return chart;
    }

    public PieChart createCatalogPieChart(Map<String, Double> data) {
        PieChart pie = new PieChart();
        pie.setTitle("Répartition du Catalogue");
        data.forEach((k, v) -> pie.getData().add(new PieChart.Data(k, v)));
        pie.setClockwise(true);
        pie.setLabelsVisible(true);
        return pie;
    }
}
