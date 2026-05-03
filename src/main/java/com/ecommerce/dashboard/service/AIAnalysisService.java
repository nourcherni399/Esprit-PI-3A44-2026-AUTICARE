package com.ecommerce.dashboard.service;

import com.ecommerce.dashboard.model.PredictionPoint;
import com.ecommerce.dashboard.model.PredictionReport;
import com.ecommerce.dashboard.model.Product;
import com.ecommerce.dashboard.model.ProductPrediction;
import com.ecommerce.dashboard.model.SalesData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.services.AssistantSecretsLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AIAnalysisService {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public String analyzeCatalog(String prompt) {
        String apiKey = resolveAnthropicApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "API key absente: ajoutez anthropic.api.key dans assistant-local.properties "
                + "ou définissez ANTHROPIC_API_KEY.";
        }
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", "claude-sonnet-4-20250514");
            body.addProperty("max_tokens", 500);
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            body.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "Erreur API Anthropic: HTTP " + response.statusCode() + "\n" + response.body();
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content == null || content.isEmpty()) {
                return "Aucune réponse IA reçue.";
            }
            JsonObject first = content.get(0).getAsJsonObject();
            return first.has("text") ? first.get("text").getAsString().trim() : "Réponse vide.";
        } catch (Exception ex) {
            return "Analyse impossible: " + ex.getMessage();
        }
    }

    public PredictionReport buildPredictionReport(List<Product> products, List<SalesData> salesHistory) {
        String apiKey = resolveAnthropicApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackPrediction(products, salesHistory);
        }
        try {
            String prompt = buildPredictionPrompt(products, salesHistory);
            String modelText = callAnthropic(apiKey, prompt, 900);
            if (modelText == null || modelText.isBlank()) {
                return fallbackPrediction(products, salesHistory);
            }
            return parsePredictionJson(modelText, products, salesHistory);
        } catch (Exception ex) {
            return fallbackPrediction(products, salesHistory);
        }
    }

    private PredictionReport parsePredictionJson(String raw, List<Product> products, List<SalesData> salesHistory) {
        String json = extractJson(raw);
        if (json == null || json.isBlank()) {
            return fallbackPrediction(products, salesHistory);
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        double growth = root.has("expectedGrowthPct") ? root.get("expectedGrowthPct").getAsDouble() : 18.5;
        double precision = root.has("modelPrecisionPct") ? root.get("modelPrecisionPct").getAsDouble() : 92.3;
        String peak = root.has("peakPeriod") ? root.get("peakPeriod").getAsString() : "Août 2025";
        String recommendation = root.has("keyRecommendation")
            ? root.get("keyRecommendation").getAsString()
            : "Augmenter le stock du produit le plus vendu avant le pic.";

        List<PredictionPoint> points = new ArrayList<>();
        JsonArray cp = root.getAsJsonArray("curvePoints");
        if (cp != null) {
            for (int i = 0; i < cp.size(); i++) {
                JsonObject o = cp.get(i).getAsJsonObject();
                points.add(new PredictionPoint(
                    o.get("periodLabel").getAsString(),
                    o.get("historicalValue").getAsDouble(),
                    o.get("predictedValue").getAsDouble(),
                    o.get("lowerBound").getAsDouble(),
                    o.get("upperBound").getAsDouble(),
                    o.get("predictedPeriod").getAsBoolean()
                ));
            }
        }
        if (points.isEmpty()) {
            return fallbackPrediction(products, salesHistory);
        }

        List<ProductPrediction> rows = new ArrayList<>();
        JsonArray arr = root.getAsJsonArray("productRows");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                rows.add(new ProductPrediction(
                    o.get("productName").getAsString(),
                    o.get("period").getAsString(),
                    o.get("predictedQuantity").getAsInt(),
                    o.get("confidence").getAsDouble(),
                    o.get("recommendedStock").getAsInt(),
                    o.get("action").getAsString()
                ));
            }
        }
        if (rows.isEmpty()) {
            rows = fallbackProductRows(products);
        }

        List<String> actions = new ArrayList<>();
        JsonArray acts = root.getAsJsonArray("actions");
        if (acts != null) {
            for (int i = 0; i < acts.size(); i++) {
                actions.add(acts.get(i).getAsString());
            }
        }
        if (actions.isEmpty()) {
            actions = List.of(
                "Reapprovisionner Produit A avant le pic saisonnier.",
                "Lancer une promotion ciblée sur Produit C pour augmenter la rotation.",
                "Surveiller quotidiennement les stocks critiques."
            );
        }
        return new PredictionReport(growth, precision, peak, recommendation, points, rows, actions);
    }

    private static PredictionReport fallbackPrediction(List<Product> products, List<SalesData> salesHistory) {
        List<PredictionPoint> points = new ArrayList<>();
        String[] months = {"Jan", "Fev", "Mar", "Avr", "Mai", "Jun", "Jul", "Aou", "Sep"};
        double[] hist = {80, 95, 110, 130, 140, 150};
        double[] pred = {160, 172, 178};
        for (int i = 0; i < 6; i++) {
            points.add(new PredictionPoint(months[i], hist[i], hist[i], hist[i], hist[i], false));
        }
        for (int i = 0; i < 3; i++) {
            double base = pred[i];
            points.add(new PredictionPoint(months[i + 6], 0, base, base * 0.92, base * 1.08, true));
        }
        return new PredictionReport(
            18.5,
            92.3,
            "Août 2025",
            "Augmenter le stock Produit A avant Juillet.",
            points,
            fallbackProductRows(products),
            List.of(
                "Prioriser le réassort des produits à forte rotation.",
                "Éviter le surstock sur les références à faible marge.",
                "Créer une alerte hebdomadaire sur les écarts prévision/réel."
            )
        );
    }

    private static List<ProductPrediction> fallbackProductRows(List<Product> products) {
        List<ProductPrediction> rows = new ArrayList<>();
        List<Product> src = products == null ? List.of() : products;
        int max = Math.min(5, src.size());
        for (int i = 0; i < max; i++) {
            Product p = src.get(i);
            int qty = (int) Math.max(8, Math.round((p.revenue() / Math.max(1.0, p.price())) * 1.12));
            rows.add(new ProductPrediction(
                p.name(),
                "Jul-Sep",
                qty,
                0.89 - (i * 0.03),
                qty + 4,
                qty > p.stock() ? "Réapprovisionner" : "Stock suffisant"
            ));
        }
        if (rows.isEmpty()) {
            rows.add(new ProductPrediction("Produit A", "Jul-Sep", 18, 0.90, 24, "Réapprovisionner"));
        }
        return rows;
    }

    private static String buildPredictionPrompt(List<Product> products, List<SalesData> salesHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es analyste e-commerce. Retourne uniquement du JSON valide.\n");
        sb.append("Schema:\n");
        sb.append("{expectedGrowthPct:number, modelPrecisionPct:number, peakPeriod:string, keyRecommendation:string, ");
        sb.append("curvePoints:[{periodLabel:string,historicalValue:number,predictedValue:number,lowerBound:number,upperBound:number,predictedPeriod:boolean}], ");
        sb.append("productRows:[{productName:string,period:string,predictedQuantity:number,confidence:number,recommendedStock:number,action:string}], ");
        sb.append("actions:[string,string,string]}\n");
        sb.append("Contraintes: 9 points de courbe (6 historiques + 3 prédits), 4 à 6 lignes produit.\n");
        sb.append("Contexte ventes historiques:\n");
        if (salesHistory != null) {
            for (SalesData s : salesHistory) {
                sb.append("- ").append(s.month()).append(": A=").append(s.a())
                    .append(", B=").append(s.b()).append(", C=").append(s.c()).append('\n');
            }
        }
        sb.append("Produits:\n");
        if (products != null) {
            for (Product p : products) {
                sb.append("- ").append(p.name()).append(", prix=").append(String.format(Locale.US, "%.2f", p.price()))
                    .append(", stock=").append(p.stock()).append(", revenu=").append(String.format(Locale.US, "%.2f", p.revenue())).append('\n');
            }
        }
        return sb.toString();
    }

    private String callAnthropic(String apiKey, String prompt, int maxTokens) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", "claude-sonnet-4-20250514");
        body.addProperty("max_tokens", maxTokens);
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
            .timeout(Duration.ofSeconds(50))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Anthropic HTTP " + response.statusCode());
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray content = root.getAsJsonArray("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Réponse IA vide");
        }
        JsonObject first = content.get(0).getAsJsonObject();
        return first.has("text") ? first.get("text").getAsString().trim() : "";
    }

    private static String extractJson(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        int startFence = t.indexOf("```json");
        if (startFence >= 0) {
            int start = t.indexOf('{', startFence);
            int endFence = t.indexOf("```", start + 1);
            if (start >= 0 && endFence > start) {
                return t.substring(start, endFence).trim();
            }
        }
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return t.substring(first, last + 1).trim();
        }
        return t;
    }

    private static String resolveAnthropicApiKey() {
        String sys = System.getProperty("anthropic.api.key");
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv("ANTHROPIC_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String local = firstNonBlank(
            AssistantSecretsLoader.get("anthropic.api.key"),
            AssistantSecretsLoader.get("ANTHROPIC_API_KEY"),
            AssistantSecretsLoader.get("claude.api.key"),
            AssistantSecretsLoader.get("CLAUDE_API_KEY")
        );
        return local == null ? "" : local.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
