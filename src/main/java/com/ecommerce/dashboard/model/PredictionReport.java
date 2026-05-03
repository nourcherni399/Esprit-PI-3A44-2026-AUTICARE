package com.ecommerce.dashboard.model;

import java.util.List;

public record PredictionReport(
    double expectedGrowthPct,
    double modelPrecisionPct,
    String peakPeriod,
    String keyRecommendation,
    List<PredictionPoint> curvePoints,
    List<ProductPrediction> productRows,
    List<String> actions
) {
}
