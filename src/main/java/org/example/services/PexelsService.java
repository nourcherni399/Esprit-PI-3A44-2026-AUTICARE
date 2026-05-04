package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.example.utils.LocalAiPropertiesFile;

/**
 * Client minimal pour l'API Pexels (recherche + téléchargement d'aperçu).
 * Clé : variable d'environnement {@code PEXELS_API_KEY} ou propriété {@code pexels.api.key} dans {@code .ai.local.properties}.
 */
public class PexelsService {

    private static final String PEXELS_SEARCH = "https://api.pexels.com/v1/search";
    private static final int CONNECT_TIMEOUT_SEC = 15;
    private static final int REQUEST_TIMEOUT_SEC = 45;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record PexelsPhoto(long id, String thumbUrl, String downloadUrl, String photographer) {}

    public List<PexelsPhoto> search(String query, int perPage) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Pexels introuvable. Définissez PEXELS_API_KEY ou pexels.api.key dans .ai.local.properties");
        }
        String q = query != null ? query.trim() : "";
        if (q.isEmpty()) {
            throw new IOException("Saisissez des mots-clés pour la recherche.");
        }
        int n = Math.min(Math.max(perPage, 1), 30);
        String url = PEXELS_SEARCH + "?query=" + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&per_page=" + n;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .header("Authorization", apiKey)
                .header("User-Agent", "AutiCareDesktop/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Erreur API Pexels (" + response.statusCode() + "): " + trim(response.body(), 300));
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode photos = root.path("photos");
        List<PexelsPhoto> out = new ArrayList<>();
        if (!photos.isArray()) {
            return out;
        }
        for (JsonNode p : photos) {
            long id = p.path("id").asLong(0);
            JsonNode src = p.path("src");
            String thumb = textOrEmpty(src.path("tiny"));
            if (thumb.isEmpty()) {
                thumb = textOrEmpty(src.path("small"));
            }
            String dl = textOrEmpty(src.path("large2x"));
            if (dl.isEmpty()) {
                dl = textOrEmpty(src.path("large"));
            }
            if (dl.isEmpty()) {
                dl = textOrEmpty(src.path("original"));
            }
            if (thumb.isEmpty()) {
                thumb = dl;
            }
            if (id == 0 || dl.isEmpty()) {
                continue;
            }
            if (!thumb.isEmpty()) {
                try {
                    thumb = cacheThumbToLocalUri(thumb, id);
                } catch (Exception ignored) {
                    // fallback URL distante
                }
            }
            String photoBy = textOrEmpty(p.path("photographer"));
            out.add(new PexelsPhoto(id, thumb, dl, photoBy));
        }
        return out;
    }

    /**
     * Télécharge l'image en fichier local court (pour respecter varchar 255 côté base).
     */
    public String downloadToLocalFile(String imageUrl, long photoId) throws IOException, InterruptedException {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IOException("URL d'image invalide.");
        }
        HttpRequest imgReq = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .header("User-Agent", "AutiCareDesktop/1.0")
                .GET()
                .build();

        HttpResponse<byte[]> resp = httpClient.send(imgReq, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Téléchargement image échoué (" + resp.statusCode() + ")");
        }

        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "auticare-pexels");
        Files.createDirectories(dir);
        Path target = dir.resolve("p" + photoId + "_" + System.nanoTime() + ".jpg");
        Files.write(target, resp.body());
        String abs = target.toAbsolutePath().normalize().toString();
        return abs;
    }

    private static String textOrEmpty(JsonNode n) {
        return n != null && n.isTextual() ? n.asText().trim() : "";
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String cacheThumbToLocalUri(String thumbUrl, long photoId) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(thumbUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .header("User-Agent", "AutiCareDesktop/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Miniature indisponible");
        }
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "auticare-pexels");
        Files.createDirectories(dir);
        Path target = dir.resolve("pt" + photoId + ".jpg");
        Files.write(target, resp.body());
        return target.toAbsolutePath().normalize().toUri().toString();
    }

    private static String resolveApiKey() {
        String env = System.getenv("PEXELS_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = LocalAiPropertiesFile.readProperty("pexels.api.key");
        return fromFile != null && !fromFile.isBlank() ? fromFile : null;
    }
}
