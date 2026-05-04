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

/**
 * Service de modération de contenu utilisant l'API Sightengine.
 * Détecte les propos injurieux, le harcèlement et les contenus inappropriés.
 */
public class SightengineService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiUser;
    private final String apiSecret;

    public SightengineService() {
        this.apiUser = LocalAiPropertiesFile.readProperty("sightengine.api.user");
        this.apiSecret = LocalAiPropertiesFile.readProperty("sightengine.api.secret");
    }

    /**
     * Vérifie si le texte est offensant ou contient des propos inappropriés.
     * @param text Le texte à vérifier.
     * @param lang La langue du texte (ex: "fr", "en").
     * @return true si le contenu est jugé offensant, false sinon.
     */
    public boolean isOffensive(String text, String lang) {
        if (text == null || text.isBlank()) return false;
        
        // --- Secours Local (pour test immédiat sans clé) ---
        String lower = text.toLowerCase();
        String[] blackList = {"putain", "merde", "connard", "stupid", "stupide", "idiot", "idiote", "bête", "salaud", "con ", "ta gueule", "tg", "salope", "crétin"};
        for (String word : blackList) {
            if (lower.contains(word)) {
                System.out.println("[Moderation] Bloqué par filtre local : " + word);
                return true;
            }
        }

        if (apiUser == null || apiSecret == null || apiUser.isBlank() || apiSecret.isBlank()) {
            System.err.println("[Sightengine] API Keys non configurées dans .ai.local.properties. Modération désactivée.");
            return false;
        }

        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            // On demande tous les modes disponibles pour le texte
            String url = String.format("https://api.sightengine.com/1.0/text/check.json?text=%s&lang=%s&mode=rules,social,sentiment&api_user=%s&api_secret=%s",
                    encodedText, lang != null ? lang : "fr", apiUser, apiSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Sightengine] Response: " + response.body());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                
                // 1. Vérification Profanité
                JsonNode profanity = root.path("profanity");
                if (profanity.has("matches") && profanity.path("matches").isArray() && profanity.path("matches").size() > 0) {
                    System.out.println("[Moderation] Bloqué : Profanité détectée");
                    return true;
                }

                // 2. Vérification Harcèlement / Haine (Social)
                JsonNode social = root.path("social");
                if (social.has("matches") && social.path("matches").isArray() && social.path("matches").size() > 0) {
                    System.out.println("[Moderation] Bloqué : Contenu social/harcèlement détecté");
                    return true;
                }

                // 3. Vérification Sentiment (si trop négatif, on bloque)
                JsonNode sentiment = root.path("sentiment");
                if (sentiment.has("score")) {
                    double score = sentiment.path("score").asDouble();
                    // Score < -0.8 est souvent le signe d'une insulte ou agressivité forte
                    if (score < -0.85) {
                        System.out.println("[Moderation] Bloqué : Sentiment extrêmement négatif (" + score + ")");
                        return true;
                    }
                }
                
                return false;
            } else {
                System.err.println("[Sightengine] Erreur API : " + response.statusCode() + " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("[Sightengine] Erreur réseau : " + e.getMessage());
        }
        return false;
    }

    public static class ModerationResult {
        private final boolean offensive;
        private final String reason;

        public ModerationResult(boolean offensive, String reason) {
            this.offensive = offensive;
            this.reason = reason;
        }

        public boolean isOffensive() { return offensive; }
        public String getReason() { return reason; }
    }
}
