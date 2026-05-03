package com.ecommerce.dashboard.model;

public record StockAlert(String productName, int stock, int threshold) {
    public boolean critical() {
        return stock <= Math.max(1, threshold / 2);
    }
}
