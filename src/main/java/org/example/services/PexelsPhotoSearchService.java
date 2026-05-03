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
import java.util.List;

/**
 * Recherche d'images produit via l'API Pexels.
 */
public final class PexelsPhotoSearchService {

    private static final String SEARCH_URL = "https://api.pexels.com/v1/search";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    public static boolean hasApiKeyConfigured() {
        return !resolveApiKey().isBlank();
    }

    private static String resolveApiKey() {
        String p = System.getProperty("pexels.api.key");
        if (p != null && !p.isBlank()) {
            return p.trim();
        }
        String e = System.getenv("PEXELS_API_KEY");
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        String local = firstNonBlank(
            AssistantSecretsLoader.get("pexels.api.key"),
            AssistantSecretsLoader.get("PEXELS_API_KEY")
        );
        return local == null ? "" : local.trim();
    }

    public List<PexelsHit> search(String query, int perPage) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Clé Pexels absente (pexels.api.key / PEXELS_API_KEY).");
        }
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new IOException("Requête image trop courte.");
        }
        int n = Math.max(1, Math.min(8, perPage));
        String url = SEARCH_URL
            + "?query=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
            + "&per_page=" + n
            + "&orientation=landscape";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Authorization", apiKey)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Pexels HTTP " + resp.statusCode());
        }
        return parse(resp.body());
    }

    private static List<PexelsHit> parse(String body) throws IOException {
        try {
            List<PexelsHit> out = new ArrayList<>();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray photos = root.getAsJsonArray("photos");
            if (photos == null) {
                return out;
            }
            for (var it : photos) {
                if (!it.isJsonObject()) {
                    continue;
                }
                JsonObject o = it.getAsJsonObject();
                String pageUrl = o.has("url") ? o.get("url").getAsString() : "";
                String photographer = o.has("photographer") ? o.get("photographer").getAsString() : "";
                String preview = "";
                String large = "";
                if (o.has("src") && o.get("src").isJsonObject()) {
                    JsonObject src = o.getAsJsonObject("src");
                    if (src.has("medium")) {
                        preview = src.get("medium").getAsString();
                    } else if (src.has("small")) {
                        preview = src.get("small").getAsString();
                    } else if (src.has("tiny")) {
                        preview = src.get("tiny").getAsString();
                    }
                    if (src.has("large2x")) {
                        large = src.get("large2x").getAsString();
                    } else if (src.has("large")) {
                        large = src.get("large").getAsString();
                    } else if (src.has("original")) {
                        large = src.get("original").getAsString();
                    } else {
                        large = preview;
                    }
                }
                if (!pageUrl.isBlank()) {
                    out.add(new PexelsHit(pageUrl, preview, large, photographer));
                }
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Réponse Pexels inattendue: " + e.getMessage(), e);
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
