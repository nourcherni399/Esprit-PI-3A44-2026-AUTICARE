package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.utils.LocalAiPropertiesFile;

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

public class YouTubeService {

    private static final String SEARCH_URL = "https://www.googleapis.com/youtube/v3/search";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<YouTubeVideo> searchVideos(String query, int maxResults) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé YouTube introuvable. Définissez YOUTUBE_API_KEY ou youtube.api.key dans .ai.local.properties");
        }
        String q = query != null ? query.trim() : "";
        if (q.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(maxResults, 15));
        String url = SEARCH_URL
                + "?part=snippet&type=video&maxResults=" + limit
                + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("User-Agent", "AutiCare-Desktop/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Erreur YouTube API (" + response.statusCode() + "). Vérifiez la clé API et les quotas.");
        }

        JsonNode root = objectMapper.readTree(response.body());
        List<YouTubeVideo> out = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            String videoId = item.path("id").path("videoId").asText("");
            if (videoId.isBlank()) {
                continue;
            }
            JsonNode snippet = item.path("snippet");
            String title = snippet.path("title").asText("Vidéo YouTube");
            String channel = snippet.path("channelTitle").asText("");
            String thumb = pickBestThumb(snippet.path("thumbnails"));
            String watchUrl = "https://www.youtube.com/watch?v=" + videoId;
            out.add(new YouTubeVideo(videoId, title, channel, thumb, watchUrl));
        }
        return out;
    }

    private static String pickBestThumb(JsonNode thumbs) {
        if (thumbs == null || thumbs.isMissingNode()) return "";
        String maxres = thumbs.path("maxres").path("url").asText("");
        if (!maxres.isBlank()) return maxres;
        String high = thumbs.path("high").path("url").asText("");
        if (!high.isBlank()) return high;
        String medium = thumbs.path("medium").path("url").asText("");
        if (!medium.isBlank()) return medium;
        return thumbs.path("default").path("url").asText("");
    }

    private static String resolveApiKey() {
        String env = System.getenv("YOUTUBE_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = LocalAiPropertiesFile.readProperty("youtube.api.key");
        return fromFile != null && !fromFile.isBlank() ? fromFile : null;
    }

    public record YouTubeVideo(String id, String title, String channel, String thumbnailUrl, String watchUrl) {}
}

