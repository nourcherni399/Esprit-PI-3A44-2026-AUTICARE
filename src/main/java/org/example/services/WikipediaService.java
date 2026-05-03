package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public class WikipediaService {

    private static final String OPENSEARCH_URL = "https://fr.wikipedia.org/w/api.php?action=opensearch&limit=1&format=json&search=";
    private static final String QUERYSEARCH_URL = "https://fr.wikipedia.org/w/api.php?action=query&list=search&srlimit=1&format=json&srsearch=";
    private static final String SUMMARY_URL_TEMPLATE = "https://%s.wikipedia.org/api/rest_v1/page/summary/%s";
    private static final String USER_AGENT = "AutiCare/1.0 (contact@example.org)";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public String getWikipediaUrl(String query) throws Exception {
        if (query == null || query.isBlank()) return "https://fr.wikipedia.org/wiki/Autisme";

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        
        // 1. Try OpenSearch (exact/fast match)
        String opensearchResult = callApi(OPENSEARCH_URL + encodedQuery);
        JsonNode openRoot = mapper.readTree(opensearchResult);
        if (openRoot.isArray() && openRoot.size() >= 4) {
            JsonNode links = openRoot.get(3);
            if (links.isArray() && links.size() > 0) {
                return links.get(0).asText();
            }
        }

        // 2. Try Query Search (broader, finds related titles)
        String queryResult = callApi(QUERYSEARCH_URL + encodedQuery);
        JsonNode queryRoot = mapper.readTree(queryResult);
        if (queryRoot.has("query") && queryRoot.get("query").has("search")) {
            JsonNode searchResults = queryRoot.get("query").get("search");
            if (searchResults.isArray() && searchResults.size() > 0) {
                String title = searchResults.get(0).get("title").asText();
                return "https://fr.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);
            }
        }
        
        // 3. Last Fallback: Just search for "Autisme"
        return "https://fr.wikipedia.org/wiki/Autisme";
    }

    public String getSummaryExtract(String query, String languageCode) throws Exception {
        if (query == null || query.isBlank()) {
            return "";
        }
        String lang = normalizeLanguage(languageCode);
        String encodedQuery = URLEncoder.encode(query.trim().replace(' ', '_'), StandardCharsets.UTF_8);
        String url = String.format(SUMMARY_URL_TEMPLATE, lang, encodedQuery);
        String body = callApi(url);
        JsonNode root = mapper.readTree(body);
        return root.path("extract").asText("");
    }

    public String getEnglishDictionaryDefinition(String word) throws Exception {
        if (word == null || word.isBlank()) {
            return "";
        }
        String encoded = URLEncoder.encode(word.trim(), StandardCharsets.UTF_8);
        String url = "https://api.dictionaryapi.dev/api/v2/entries/en/" + encoded;
        String body = callApi(url);
        JsonNode root = mapper.readTree(body);
        if (!root.isArray() || root.isEmpty()) {
            return "";
        }
        JsonNode first = root.get(0);
        JsonNode meanings = first.path("meanings");
        if (!meanings.isArray() || meanings.isEmpty()) {
            return "";
        }
        for (JsonNode meaning : meanings) {
            JsonNode defs = meaning.path("definitions");
            if (!defs.isArray() || defs.isEmpty()) continue;
            for (JsonNode def : defs) {
                String text = def.path("definition").asText("");
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    private String callApi(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Wikipedia HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String normalizeLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return "fr";
        }
        String code = languageCode.trim().toLowerCase(Locale.ROOT);
        if (code.contains("-")) {
            code = code.substring(0, code.indexOf('-'));
        }
        return switch (code) {
            case "fr", "en", "ar", "es", "de", "it", "pt", "tr" -> code;
            default -> "fr";
        };
    }
}
