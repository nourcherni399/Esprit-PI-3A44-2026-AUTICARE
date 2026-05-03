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

public class LanguageToolService {

    private static final String DEFAULT_URL = "https://api.languagetool.org/v2/check";
    /** Maximum number of suggestions to return per match. */
    private static final int MAX_SUGGESTIONS = 5;

    /**
     * A single spell/grammar match.
     * {@code suggestions} contains up to MAX_SUGGESTIONS alternatives (best first).
     */
    public record SpellMatch(
            int offset,
            int length,
            String original,
            List<String> suggestions,
            String message
    ) {
        /** Convenience: first (best) suggestion. */
        public String bestSuggestion() {
            return suggestions.isEmpty() ? original : suggestions.get(0);
        }
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private String resolveUrl() {
        String url = LocalAiPropertiesFile.readProperty("languagetool.url");
        return (url != null && !url.isBlank()) ? url : DEFAULT_URL;
    }

    private String resolveApiKey() {
        return LocalAiPropertiesFile.readProperty("languagetool.api.key");
    }

    /**
     * Checks the given text for spelling and grammar errors using the "picky" level
     * for maximum detection coverage.
     * Returns matches sorted by offset descending (safe for in-place replacement).
     */
    public List<SpellMatch> check(String text, String languageCode) throws IOException, InterruptedException {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String lang = (languageCode != null && !languageCode.isBlank()) ? languageCode : "fr";

        StringBuilder body = new StringBuilder();
        body.append("text=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        body.append("&language=").append(URLEncoder.encode(lang, StandardCharsets.UTF_8));
        // "picky" level enables more style/grammar rules beyond basic spell check
        body.append("&level=picky");

        String apiKey = resolveApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            body.append("&apiKey=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveUrl()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("LanguageTool API error: HTTP " + response.statusCode() + " — " + response.body());
        }

        return parseMatches(response.body(), text);
    }

    private List<SpellMatch> parseMatches(String json, String originalText) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode matches = root.path("matches");

        List<SpellMatch> result = new ArrayList<>();
        for (JsonNode m : matches) {
            int offset = m.path("offset").asInt(-1);
            int length = m.path("length").asInt(0);
            if (offset < 0 || length <= 0 || offset + length > originalText.length()) {
                continue;
            }

            String original = originalText.substring(offset, offset + length);

            // Collect up to MAX_SUGGESTIONS replacements
            JsonNode replacements = m.path("replacements");
            List<String> suggestions = new ArrayList<>();
            if (replacements.isArray()) {
                for (int i = 0; i < Math.min(replacements.size(), MAX_SUGGESTIONS); i++) {
                    String val = replacements.get(i).path("value").asText(null);
                    if (val != null && !val.equalsIgnoreCase(original)) {
                        suggestions.add(val);
                    }
                }
            }

            if (suggestions.isEmpty()) {
                continue;
            }

            String message = m.path("message").asText("Correction suggérée");
            result.add(new SpellMatch(offset, length, original, suggestions, message));
        }

        // Sort descending by offset for safe in-place replacement
        result.sort((a, b) -> Integer.compare(b.offset(), a.offset()));
        return result;
    }

    /**
     * Applies corrections to {@code text}.
     * {@code selectedSuggestions} must have the same size as {@code matches},
     * containing the chosen suggestion for each match (parallel lists).
     * Matches must be sorted by offset descending.
     */
    public static String applyCorrections(String text, List<SpellMatch> matches, List<String> selectedSuggestions) {
        if (text == null || matches == null || matches.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < matches.size(); i++) {
            SpellMatch m = matches.get(i);
            String chosen = (selectedSuggestions != null && i < selectedSuggestions.size())
                    ? selectedSuggestions.get(i)
                    : m.bestSuggestion();
            if (chosen != null && m.offset() >= 0 && m.offset() + m.length() <= sb.length()) {
                sb.replace(m.offset(), m.offset() + m.length(), chosen);
            }
        }
        return sb.toString();
    }

    /** Convenience overload: applies best suggestion for every match. */
    public static String applyCorrections(String text, List<SpellMatch> matches) {
        return applyCorrections(text, matches, null);
    }
}
