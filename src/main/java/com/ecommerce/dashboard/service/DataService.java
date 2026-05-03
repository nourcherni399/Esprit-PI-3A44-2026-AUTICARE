package com.ecommerce.dashboard.service;

import com.ecommerce.dashboard.model.Product;
import com.ecommerce.dashboard.model.SalesData;
import com.ecommerce.dashboard.model.StockAlert;

import java.util.List;
import java.util.Map;

public class DataService {

    public List<SalesData> salesHistory() {
        return List.of(
            new SalesData("Jan", 80, 60, 40),
            new SalesData("Fev", 95, 75, 55),
            new SalesData("Mar", 110, 85, 65),
            new SalesData("Avr", 130, 100, 75),
            new SalesData("Mai", 140, 115, 85),
            new SalesData("Jun", 150, 120, 90)
        );
    }

    public List<Product> topRevenueProducts() {
        return List.of(
            new Product("Produit A", "Electronique", 125, 24, 1875),
            new Product("Produit B", "Mode", 90, 31, 1800),
            new Product("Produit C", "Maison", 75, 17, 1350),
            new Product("Coussin", "Maison", 12.5, 1, 12.5),
            new Product("Produit D", "Sport", 15, 14, 15),
            new Product("Produit E", "Autre", 18, 11, 18)
        );
    }

    public List<StockAlert> stockAlerts() {
        return List.of(
            new StockAlert("Coussin", 1, 2),
            new StockAlert("Produit F", 3, 5)
        );
    }

    public Map<String, Double> catalogDistribution() {
        return Map.of(
            "Electronique", 35.0,
            "Mode", 25.0,
            "Maison", 20.0,
            "Sport", 15.0,
            "Autre", 5.0
        );
    }
}
