package com.ecommerce.dashboard.model;

public record PredictionPoint(
    String periodLabel,
    double historicalValue,
    double predictedValue,
    double lowerBound,
    double upperBound,
    boolean predictedPeriod
) {
}
