package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recommandations personnalisées via OpenAI Chat Completions.
 */
public class AiRecommendationService {
    private static final Logger LOG = Logger.getLogger(AiRecommendationService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final URI OPENAI_URI = URI.create("https://api.openai.com/v1/chat/completions");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public boolean isConfigured() {
        String key = System.getenv("OPENAI_API_KEY");
        return key != null && !key.isBlank();
    }

    public AiRecommendationResult recommend(AiRecommendationInput input) throws IOException, InterruptedException {
        Objects.requireNonNull(input, "input");
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("OPENAI_API_KEY is missing.");
        }
        String model = System.getenv("OPENAI_MODEL");
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini";
        }

        String userPrompt = buildPrompt(input);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.15);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system",
                        "content", "You are a recommendation engine. Return only valid JSON."),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(OPENAI_URI)
                .timeout(Duration.ofSeconds(22))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOG.warning("OpenAI request failed with HTTP status " + response.statusCode());
            throw new IOException("OpenAI HTTP " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IOException("OpenAI response has no choices.");
        }
        JsonNode contentNode = choices.get(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new IOException("OpenAI response content is empty.");
        }

        try {
            return MAPPER.readValue(contentNode.asText(), AiRecommendationResult.class);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "OpenAI JSON parsing failure.", e);
            throw new IOException("Invalid JSON from OpenAI.");
        }
    }

    private String buildPrompt(AiRecommendationInput input) throws IOException {
        return "Recommend events and products for the user. Constraints:\n"
                + "- Exclude already joined events.\n"
                + "- Exclude already purchased products.\n"
                + "- Prioritize matching user preferences.\n"
                + "- Return only JSON using this exact shape:\n"
                + "{\n"
                + "  \"events\": [{\"id\": 1, \"reason\": \"...\"}],\n"
                + "  \"products\": [{\"id\": 2, \"reason\": \"...\"}]\n"
                + "}\n"
                + "- Maximum 3 events and 3 products.\n\n"
                + "DATA:\n"
                + MAPPER.writeValueAsString(input);
    }

    public static class AiRecommendationInput {
        public Integer userId;
        public List<String> preferredEventThemes = new ArrayList<>();
        public List<String> preferredProductCategories = new ArrayList<>();
        public List<Integer> excludeEventIds = new ArrayList<>();
        public List<Integer> excludeProductIds = new ArrayList<>();
        public List<EventCandidate> availableEvents = new ArrayList<>();
        public List<ProductCandidate> availableProducts = new ArrayList<>();
    }

    public static class EventCandidate {
        public int id;
        public String title;
        public String theme;
        public String dateStart;
    }

    public static class ProductCandidate {
        public int id;
        public String name;
        public String category;
        public double price;
    }

    public static class AiRecommendationResult {
        public List<RecommendedItem> events = new ArrayList<>();
        public List<RecommendedItem> products = new ArrayList<>();
    }

    public static class RecommendedItem {
        public int id;
        public String reason;
    }
}
