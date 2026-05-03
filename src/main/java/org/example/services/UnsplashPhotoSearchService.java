package org.example.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Recherche de photos via l’<a href="https://unsplash.com/documentation">API Unsplash</a>.
 * <p>
 * Utilise l’<strong>Access Key</strong> (identifiant « Client ID » de l’application) :
 * {@code -Dunsplash.access.key=…} &gt; {@code UNSPLASH_ACCESS_KEY} &gt; {@code assistant-local.properties}
 * ou {@code ~/.auticare/assistant.properties} &gt; {@code application.properties}. Ne committez pas la clé.
 * La <em>Secret key</em> sert surtout au flux OAuth utilisateur ; la recherche publique utilise le Client-ID.
 */
public final class UnsplashPhotoSearchService {

    private static final String SEARCH_URL = "https://api.unsplash.com/search/photos";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Properties FILE_CONFIG = loadFileConfig();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public static boolean hasAccessKeyConfigured() {
        return !resolveAccessKey().isBlank();
    }

    private static String resolveAccessKey() {
        String p = System.getProperty("unsplash.access.key");
        if (p != null && !p.isBlank()) {
            return p.trim();
        }
        String e = System.getenv("UNSPLASH_ACCESS_KEY");
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        String clientId = System.getenv("UNSPLASH_CLIENT_ID");
        if (clientId != null && !clientId.isBlank()) {
            return clientId.trim();
        }
        String local = firstNonBlank(
            AssistantSecretsLoader.get("unsplash.access.key"),
            AssistantSecretsLoader.get("UNSPLASH_ACCESS_KEY"),
            AssistantSecretsLoader.get("UNSPLASH_CLIENT_ID")
        );
        if (local != null && !local.isBlank()) {
            return local.trim();
        }
        String f = FILE_CONFIG.getProperty("unsplash.access.key");
        if (f != null && !f.isBlank()) {
            return f.trim();
        }
        return "";
    }

    private static Properties loadFileConfig() {
        Properties props = new Properties();
        try (InputStream in = UnsplashPhotoSearchService.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // —
        }
        return props;
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

    /**
     * @param query   texte de recherche
     * @param perPage nombre de résultats (max. 30 côté API ; on borne à 12)
     */
    public List<UnsplashHit> search(String query, int perPage) throws IOException, InterruptedException {
        String accessKey = resolveAccessKey();
        if (accessKey.isBlank()) {
            throw new IOException("Clé Unsplash absente : définissez UNSPLASH_ACCESS_KEY ou -Dunsplash.access.key=…");
        }
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new IOException("Requête trop courte.");
        }
        int n = Math.min(12, Math.max(1, perPage));
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
        URI uri = URI.create(SEARCH_URL + "?query=" + encoded + "&per_page=" + n + "&orientation=squarish");

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept-Version", "v1")
                .header("Authorization", "Client-ID " + accessKey)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        String body = resp.body();
        if (code < 200 || code >= 300) {
            throw new IOException("Unsplash HTTP " + code + " — " + summarizeError(body));
        }
        return parseResults(body);
    }

    private static String summarizeError(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("errors") && root.get("errors").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("errors");
                if (!arr.isEmpty()) {
                    return arr.get(0).getAsString();
                }
            }
        } catch (Exception ignored) {
            // —
        }
        return json.length() > 160 ? json.substring(0, 160) + "…" : json;
    }

    private static List<UnsplashHit> parseResults(String jsonBody) throws IOException {
        List<UnsplashHit> out = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            JsonArray results = root.getAsJsonArray("results");
            if (results == null) {
                return out;
            }
            for (var el : results) {
                JsonObject o = el.getAsJsonObject();
                String pageUrl = o.getAsJsonObject("links").get("html").getAsString();
                JsonObject urls = o.getAsJsonObject("urls");
                String thumb = urls.has("small") ? urls.get("small").getAsString() : urls.get("thumb").getAsString();
                String cap = "";
                if (o.has("description") && !o.get("description").isJsonNull()) {
                    cap = o.get("description").getAsString();
                } else if (o.has("alt_description") && !o.get("alt_description").isJsonNull()) {
                    cap = o.get("alt_description").getAsString();
                }
                out.add(new UnsplashHit(pageUrl, thumb, cap));
            }
        } catch (Exception e) {
            throw new IOException("Réponse Unsplash inattendue : " + e.getMessage(), e);
        }
        return out;
    }
}
