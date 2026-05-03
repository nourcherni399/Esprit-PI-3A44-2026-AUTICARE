package org.example.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestion de prix basée sur les résultats eBay via SerpApi.
 */
public final class SerpApiPriceSuggestionService {

    private static final String ENDPOINT = "https://serpapi.com/search.json";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern PRICE_NUM = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)");

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    public record PriceSuggestion(double min, double median, double max, int count) {
    }
    public record ProductOption(String title, String pageUrl, String imageUrl, double price) {
    }

    public static boolean hasApiKeyConfigured() {
        return !resolveApiKey().isBlank();
    }

    private static String resolveApiKey() {
        String p = System.getProperty("serpapi.api.key");
        if (p != null && !p.isBlank()) {
            return p.trim();
        }
        String e = System.getenv("SERPAPI_API_KEY");
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        String local = firstNonBlank(
            AssistantSecretsLoader.get("serpapi.api.key"),
            AssistantSecretsLoader.get("SERPAPI_API_KEY")
        );
        return local == null ? "" : local.trim();
    }

    public PriceSuggestion suggest(String query) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Clé SerpApi absente (serpapi.api.key / SERPAPI_API_KEY).");
        }
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            throw new IOException("Requête de prix vide.");
        }

        String url = ENDPOINT
            + "?engine=ebay"
            + "&_nkw=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
            + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("SerpApi HTTP " + resp.statusCode());
        }
        return parseSuggestion(resp.body());
    }

    public List<ProductOption> searchProductOptions(String query, int limit) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Clé SerpApi absente (serpapi.api.key / SERPAPI_API_KEY).");
        }
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            throw new IOException("Requête de prix vide.");
        }
        int n = Math.max(1, Math.min(8, limit));
        String url = ENDPOINT
            + "?engine=ebay"
            + "&_nkw=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
            + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("SerpApi HTTP " + resp.statusCode());
        }
        return parseProductOptions(resp.body(), n);
    }

    public List<ProductOption> searchJumiaOptions(String query, int limit) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Clé SerpApi absente (serpapi.api.key / SERPAPI_API_KEY).");
        }
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            throw new IOException("Requête produit vide.");
        }
        int n = Math.max(1, Math.min(10, limit));

        String shoppingUrl = ENDPOINT
            + "?engine=google_shopping"
            + "&gl=tn&hl=fr"
            + "&q=" + URLEncoder.encode(q + " jumia tunisie", StandardCharsets.UTF_8)
            + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(shoppingUrl))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("SerpApi HTTP " + resp.statusCode());
        }
        List<ProductOption> out = parseShoppingOptions(resp.body(), n);
        if (!out.isEmpty()) {
            return out;
        }

        // Fallback: recherche Google classique ciblée sur jumia.com.tn
        String organicUrl = ENDPOINT
            + "?engine=google"
            + "&gl=tn&hl=fr"
            + "&q=" + URLEncoder.encode("site:jumia.com.tn " + q, StandardCharsets.UTF_8)
            + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest req2 = HttpRequest.newBuilder(URI.create(organicUrl))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp2 = http.send(req2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp2.statusCode() < 200 || resp2.statusCode() >= 300) {
            throw new IOException("SerpApi HTTP " + resp2.statusCode());
        }
        return parseGoogleJumiaOrganicOptions(resp2.body(), n);
    }

    private static PriceSuggestion parseSuggestion(String body) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("organic_results");
            if (arr == null || arr.isEmpty()) {
                throw new IOException("Aucun résultat de prix.");
            }
            List<Double> prices = new ArrayList<>();
            for (var it : arr) {
                if (!it.isJsonObject()) {
                    continue;
                }
                JsonObject obj = it.getAsJsonObject();
                extractPrice(obj).ifPresent(prices::add);
            }
            if (prices.isEmpty()) {
                throw new IOException("Aucun prix exploitable trouvé.");
            }
            prices.sort(Comparator.naturalOrder());
            double min = prices.get(0);
            double max = prices.get(prices.size() - 1);
            double median = prices.get(prices.size() / 2);
            return new PriceSuggestion(min, median, max, prices.size());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Réponse SerpApi inattendue: " + e.getMessage(), e);
        }
    }

    private static List<ProductOption> parseProductOptions(String body, int limit) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("organic_results");
            List<ProductOption> out = new ArrayList<>();
            if (arr == null || arr.isEmpty()) {
                return out;
            }
            for (var it : arr) {
                if (!it.isJsonObject()) {
                    continue;
                }
                JsonObject obj = it.getAsJsonObject();
                Double p = extractPrice(obj).orElse(null);
                if (p == null || p <= 0) {
                    continue;
                }
                String title = obj.has("title") ? obj.get("title").getAsString() : "Produit";
                String link = obj.has("link") ? obj.get("link").getAsString() : "";
                String thumb = "";
                if (obj.has("thumbnail")) {
                    thumb = obj.get("thumbnail").getAsString();
                } else if (obj.has("image")) {
                    thumb = obj.get("image").getAsString();
                }
                out.add(new ProductOption(title, link, thumb, p));
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Réponse SerpApi inattendue: " + e.getMessage(), e);
        }
    }

    private static List<ProductOption> parseShoppingOptions(String body, int limit) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("shopping_results");
            List<ProductOption> out = new ArrayList<>();
            if (arr == null || arr.isEmpty()) {
                return out;
            }
            for (var it : arr) {
                if (!it.isJsonObject()) {
                    continue;
                }
                JsonObject obj = it.getAsJsonObject();
                String link = obj.has("link") ? obj.get("link").getAsString() : "";
                if (link.isBlank()) {
                    continue;
                }
                String ll = link.toLowerCase(Locale.ROOT);
                if (!ll.contains("jumia")) {
                    continue;
                }
                Double p = extractShoppingPrice(obj);
                if (p == null || p <= 0) {
                    continue;
                }
                String title = obj.has("title") ? obj.get("title").getAsString() : "Produit Jumia";
                String thumb = obj.has("thumbnail") ? obj.get("thumbnail").getAsString() : "";
                out.add(new ProductOption(title, link, thumb, p));
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Réponse SerpApi shopping inattendue: " + e.getMessage(), e);
        }
    }

    private static List<ProductOption> parseGoogleJumiaOrganicOptions(String body, int limit) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("organic_results");
            List<ProductOption> out = new ArrayList<>();
            if (arr == null || arr.isEmpty()) {
                return out;
            }
            for (var it : arr) {
                if (!it.isJsonObject()) {
                    continue;
                }
                JsonObject obj = it.getAsJsonObject();
                String link = obj.has("link") ? obj.get("link").getAsString() : "";
                if (link.isBlank() || !link.toLowerCase(Locale.ROOT).contains("jumia")) {
                    continue;
                }
                String title = obj.has("title") ? obj.get("title").getAsString() : "Produit Jumia";
                String thumb = obj.has("thumbnail") ? obj.get("thumbnail").getAsString() : "";
                String snippet = obj.has("snippet") ? obj.get("snippet").getAsString() : "";
                Double p = parsePrice(snippet);
                if (p == null || p <= 0) {
                    p = extractPrice(obj).orElse(null);
                }
                if (p == null || p <= 0) {
                    continue;
                }
                out.add(new ProductOption(title, link, thumb, p));
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Réponse SerpApi Google inattendue: " + e.getMessage(), e);
        }
    }

    private static Double extractShoppingPrice(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("extracted_price")) {
            try {
                double v = obj.get("extracted_price").getAsDouble();
                return v > 0 ? v : null;
            } catch (Exception ignored) {
                // fallback below
            }
        }
        if (obj.has("price")) {
            return parsePrice(obj.get("price").getAsString());
        }
        return null;
    }

    private static java.util.Optional<Double> extractPrice(JsonObject obj) {
        if (obj == null) {
            return java.util.Optional.empty();
        }
        if (obj.has("price")) {
            var p = obj.get("price");
            if (p.isJsonPrimitive()) {
                Double v = parsePrice(p.getAsString());
                if (v != null) {
                    return java.util.Optional.of(v);
                }
            } else if (p.isJsonObject()) {
                JsonObject po = p.getAsJsonObject();
                if (po.has("value")) {
                    Double v = parsePrice(po.get("value").getAsString());
                    if (v != null) {
                        return java.util.Optional.of(v);
                    }
                }
                if (po.has("extracted_value")) {
                    try {
                        return java.util.Optional.of(po.get("extracted_value").getAsDouble());
                    } catch (Exception ignored) {
                        // fallback below
                    }
                }
                Double v = parsePrice(po.toString());
                if (v != null) {
                    return java.util.Optional.of(v);
                }
            }
        }
        if (obj.has("primary")) {
            Double v = parsePrice(obj.get("primary").toString());
            if (v != null) {
                return java.util.Optional.of(v);
            }
        }
        return java.util.Optional.empty();
    }

    private static Double parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String x = raw.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
        Matcher m = PRICE_NUM.matcher(x);
        if (!m.find()) {
            return null;
        }
        String n = m.group(1).replace(',', '.');
        try {
            double v = Double.parseDouble(n);
            return v > 0 ? v : null;
        } catch (Exception ignored) {
            return null;
        }
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
