package com.ecommerce.dashboard.model;

public record ProductPrediction(
    String productName,
    String period,
    int predictedQuantity,
    double confidence,
    int recommendedStock,
    String action
) {
}
