package org.example.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.elasticsearch.ElasticsearchCatalogService;
import org.example.models.Product;
import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AdminProductPredictionService {

    public record ProductSalesPoint(int productId, String productName, int soldQty, int predictedQty) {
    }

    public record StockRiskPoint(int productId, String productName, int currentStock, int threshold, int predictedDemand, int projectedStock) {
    }

    public record PredictionSnapshot(
        List<ProductSalesPoint> topSold,
        List<ProductSalesPoint> leastSold,
        List<ProductSalesPoint> predictedTop,
        List<ProductSalesPoint> predictedLeast,
        List<StockRiskPoint> riskStocks,
        boolean aiUsed,
        String note
    ) {
    }

    public PredictionSnapshot buildSnapshot() throws SQLException {
        List<Product> allProducts = new ProductService().findAll();
        Map<Integer, Product> byId = allProducts.stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Integer, Integer> soldById = fetchSoldQtyLast90Days();
        Map<Integer, Integer> predictedById = predictNextDemand(byId, soldById);

        List<ProductSalesPoint> merged = new ArrayList<>();
        for (Product p : allProducts) {
            int sold = Math.max(0, soldById.getOrDefault(p.getId(), 0));
            int pred = Math.max(0, predictedById.getOrDefault(p.getId(), sold));
            merged.add(new ProductSalesPoint(p.getId(), safeName(p.getNom()), sold, pred));
        }

        List<ProductSalesPoint> topSold = merged.stream()
            .sorted(Comparator.comparingInt(ProductSalesPoint::soldQty).reversed().thenComparing(ProductSalesPoint::productName))
            .limit(8)
            .toList();
        List<ProductSalesPoint> leastSold = merged.stream()
            .sorted(Comparator.comparingInt(ProductSalesPoint::soldQty).thenComparing(ProductSalesPoint::productName))
            .limit(8)
            .toList();
        List<ProductSalesPoint> predictedTop = merged.stream()
            .sorted(Comparator.comparingInt(ProductSalesPoint::predictedQty).reversed().thenComparing(ProductSalesPoint::productName))
            .limit(8)
            .toList();
        List<ProductSalesPoint> predictedLeast = merged.stream()
            .sorted(Comparator.comparingInt(ProductSalesPoint::predictedQty).thenComparing(ProductSalesPoint::productName))
            .limit(8)
            .toList();

        List<StockRiskPoint> risks = new ArrayList<>();
        for (Product p : allProducts) {
            int stock = Math.max(0, p.getStock());
            int threshold = Math.max(1, p.getSeuilAlerte() != null ? p.getSeuilAlerte() : 2);
            int predicted = Math.max(0, predictedById.getOrDefault(p.getId(), soldById.getOrDefault(p.getId(), 0)));
            int projected = stock - predicted;
            if (projected <= threshold) {
                risks.add(new StockRiskPoint(p.getId(), safeName(p.getNom()), stock, threshold, predicted, projected));
            }
        }
        risks.sort(Comparator.comparingInt(StockRiskPoint::projectedStock));
        if (risks.size() > 10) {
            risks = risks.subList(0, 10);
        }

        boolean aiUsed = GroqChatCompletionService.hasApiKeyConfigured();
        String note = aiUsed
            ? "Prévision API IA active (Groq) + boost tendances Elastic."
            : "Prévision heuristique locale (clé IA absente).";

        return new PredictionSnapshot(topSold, leastSold, predictedTop, predictedLeast, risks, aiUsed, note);
    }

    private Map<Integer, Integer> fetchSoldQtyLast90Days() throws SQLException {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        String sql = """
            SELECT li.produit_id AS pid, COALESCE(SUM(li.quantite),0) AS sold
            FROM ligne_commande li
            JOIN commande c ON c.id = li.commande_id
            WHERE c.date_creation >= DATE_SUB(CURDATE(), INTERVAL 90 DAY)
            GROUP BY li.produit_id
            ORDER BY sold DESC
            """;
        try (Connection conn = MyDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getInt("pid"), rs.getInt("sold"));
            }
        } catch (SQLException ignored) {
            // fallback -> vide si schéma legacy
        }
        return out;
    }

    private Map<Integer, Integer> predictNextDemand(Map<Integer, Product> byId, Map<Integer, Integer> soldById) {
        Map<Integer, Double> factors = callAiFactors(byId, soldById);
        if (factors.isEmpty()) {
            factors = heuristicFactors(byId, soldById);
        }

        ElasticsearchCatalogService elastic = ElasticsearchCatalogService.getInstance();
        Set<Integer> trendBoostIds = Set.copyOf(elastic.recommendPublicIds(false, null, Set.of(), 20));

        Map<Integer, Integer> predicted = new HashMap<>();
        for (Map.Entry<Integer, Product> e : byId.entrySet()) {
            int id = e.getKey();
            Product p = e.getValue();
            int sold = Math.max(0, soldById.getOrDefault(id, 0));
            double factor = factors.getOrDefault(id, 1.0);
            if (trendBoostIds.contains(id)) {
                factor += 0.12;
            }
            int base = sold > 0 ? sold : Math.max(1, (int) Math.round(Math.max(0, p.getStock()) * 0.2));
            int pred = (int) Math.round(base * Math.max(0.2, Math.min(2.5, factor)));
            predicted.put(id, Math.max(0, pred));
        }
        return predicted;
    }

    private Map<Integer, Double> callAiFactors(Map<Integer, Product> byId, Map<Integer, Integer> soldById) {
        if (!GroqChatCompletionService.hasApiKeyConfigured()) {
            return Map.of();
        }
        List<Map.Entry<Integer, Integer>> ranked = soldById.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .limit(30)
            .toList();
        if (ranked.isEmpty()) {
            return Map.of();
        }

        JsonArray products = new JsonArray();
        for (Map.Entry<Integer, Integer> e : ranked) {
            Product p = byId.get(e.getKey());
            if (p == null) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", p.getId());
            o.addProperty("name", safeName(p.getNom()));
            o.addProperty("sold90d", Math.max(0, e.getValue()));
            o.addProperty("stock", Math.max(0, p.getStock()));
            o.addProperty("threshold", Math.max(1, p.getSeuilAlerte() != null ? p.getSeuilAlerte() : 2));
            products.add(o);
        }
        if (products.isEmpty()) {
            return Map.of();
        }

        String userPrompt = "Calcule un coefficient de demande pour 30 jours.\n"
            + "Retourne STRICTEMENT un JSON valide de la forme: {\"forecasts\":[{\"id\":1,\"factor\":1.10}]}\n"
            + "Contraintes: factor entre 0.20 et 2.50, seulement les ids fournis.\n"
            + "Produits:\n" + products;
        String system = """
            Tu es analyste de prévision ventes retail.
            Donne uniquement un JSON valide, sans markdown, sans texte autour.
            """;
        try {
            String raw = new GroqChatCompletionService().completeWithSystem(
                List.of(new GroqChatMessage("user", userPrompt)),
                system,
                0.2,
                0.9,
                700
            );
            return parseFactors(raw);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<Integer, Double> parseFactors(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            JsonObject root = JsonParser.parseString(raw.trim()).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("forecasts");
            if (arr == null) {
                return Map.of();
            }
            Map<Integer, Double> out = new HashMap<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("id") || !o.has("factor")) {
                    continue;
                }
                int id = o.get("id").getAsInt();
                double f = o.get("factor").getAsDouble();
                out.put(id, Math.max(0.2, Math.min(2.5, f)));
            }
            return out;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<Integer, Double> heuristicFactors(Map<Integer, Product> byId, Map<Integer, Integer> soldById) {
        Map<Integer, Double> out = new HashMap<>();
        double avg = soldById.values().stream().mapToInt(Integer::intValue).average().orElse(1.0);
        for (Map.Entry<Integer, Product> e : byId.entrySet()) {
            int id = e.getKey();
            Product p = e.getValue();
            int sold = Math.max(0, soldById.getOrDefault(id, 0));
            int stock = Math.max(0, p.getStock());
            double f = sold >= avg ? 1.1 : 0.9;
            if (stock <= Math.max(1, p.getSeuilAlerte() != null ? p.getSeuilAlerte() : 2)) {
                f += 0.1;
            }
            out.put(id, Math.max(0.2, Math.min(2.0, f)));
        }
        return out;
    }

    private static String safeName(String n) {
        if (n == null || n.isBlank()) {
            return "Produit";
        }
        return n.trim();
    }
}
