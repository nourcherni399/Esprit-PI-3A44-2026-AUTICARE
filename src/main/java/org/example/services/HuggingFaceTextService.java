package org.example.services;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuggingFaceTextService {

    private static final Properties FILE_CONFIG = loadFileConfig();
    private static final Pattern GENERATED_TEXT_PATTERN = Pattern.compile("\"generated_text\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
    private static final Pattern CHAT_CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
    private static final Pattern ERROR_PATTERN = Pattern.compile("\"error\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
    private static final Pattern ESTIMATED_TIME_PATTERN = Pattern.compile("\"estimated_time\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public String suggestDetailedDescription(String thematique) throws Exception {
        String safeTheme = thematique == null ? "" : thematique.trim();
        if (safeTheme.isBlank()) {
            throw new IllegalArgumentException("Veuillez choisir une thématique avant la génération.");
        }
        String prompt = """
                Rédige une description complète et professionnelle en français pour un événement autour de la thématique suivante : "%s".
                Contraintes :
                - 2 paragraphes clairs et naturels.
                - Entre 120 et 170 mots.
                - Ton engageant, humain, et informatif.
                - Inclure : objectifs, public visé, déroulement prévu, et bénéfices.
                - Ne pas utiliser de listes à puces.
                - Retourne uniquement la description finale.
                """.formatted(safeTheme);
        return runGeneration(prompt, 220, 0.8);
    }

    public String summarizeDescription(String description) throws Exception {
        String safeDescription = description == null ? "" : description.trim();
        if (safeDescription.isBlank()) {
            throw new IllegalArgumentException("Ajoutez d'abord une description à résumer.");
        }
        String prompt = """
                Résume le texte suivant en français, de façon claire et naturelle, en 2 phrases maximum.
                Garde les informations importantes sans inventer de détails.
                Retourne uniquement le résumé.

                Texte :
                %s
                """.formatted(safeDescription);
        return runGeneration(prompt, 120, 0.5);
    }

    public String suggestReplyForParticipantMessage(String eventTitle, String participantMessage, String conversationContext)
            throws Exception {
        String safeEvent = eventTitle == null || eventTitle.isBlank() ? "Événement" : eventTitle.trim();
        String safeMessage = participantMessage == null ? "" : participantMessage.trim();
        if (safeMessage.isBlank()) {
            throw new IllegalArgumentException("Message participant introuvable pour générer une réponse.");
        }
        String safeContext = conversationContext == null ? "" : conversationContext.trim();
        String prompt = """
                Tu es un assistant de support admin d'une application d'événements.
                Rédige une réponse en français à envoyer à un participant.
                Contexte :
                - Événement : %s
                - Historique récent : %s
                - Dernier message du participant : %s

                Contraintes :
                - Ton professionnel, empathique, clair.
                - 3 à 6 phrases.
                - Répondre précisément à la demande.
                - Si le participant dit seulement "bonjour", produire une réponse complète (salutation + proposition d'aide + prochaine étape).
                - Ne jamais retourner "..." ni une réponse incomplète.
                - Si une information manque, poser une courte question de clarification.
                - Retourne uniquement le texte de réponse final.
                """.formatted(safeEvent, safeContext.isBlank() ? "Aucun." : safeContext, safeMessage);
        return runGeneration(prompt, 190, 0.6);
    }

    public String analyzeParticipantMessage(String eventTitle, String participantMessage, String conversationContext)
            throws Exception {
        String safeEvent = eventTitle == null || eventTitle.isBlank() ? "Événement" : eventTitle.trim();
        String safeMessage = participantMessage == null ? "" : participantMessage.trim();
        if (safeMessage.isBlank()) {
            throw new IllegalArgumentException("Message participant introuvable pour l'analyse.");
        }
        String safeContext = conversationContext == null ? "" : conversationContext.trim();
        String prompt = """
                Analyse le message d'un participant pour un admin événementiel.
                Événement : %s
                Historique récent : %s
                Dernier message : %s

                Retourne une analyse concise en français avec exactement 4 lignes :
                1) Intention: ...
                2) Urgence: ...
                3) Ton du participant: ...
                4) Réponse conseillée: ...
                """.formatted(safeEvent, safeContext.isBlank() ? "Aucun." : safeContext, safeMessage);
        return runGeneration(prompt, 170, 0.4);
    }

    private String runGeneration(String prompt, int maxNewTokens, double temperature) throws Exception {
        String token = cfg("huggingface.apiToken", "HUGGINGFACE_API_TOKEN", "");
        if (token.isBlank()) {
            throw new IllegalStateException("Clé Hugging Face manquante. Configurez huggingface.apiToken.");
        }
        String model = cfg("huggingface.model", "HUGGINGFACE_MODEL", "google/flan-t5-large");
        String endpoint = cfg("huggingface.endpoint", "HUGGINGFACE_ENDPOINT", "https://api-inference.huggingface.co/models/");
        String body = "{\"inputs\":\"" + escapeJson(prompt) + "\",\"parameters\":{"
                + "\"max_new_tokens\":" + maxNewTokens + ","
                + "\"temperature\":" + temperature + ","
                + "\"return_full_text\":false"
                + "}}";
        // Priorité au nouveau router HF (tokens "Inference Providers").
        List<String> chatModels = List.of(
                model,
                "mistralai/Mistral-7B-Instruct-v0.3",
                "Qwen/Qwen2.5-7B-Instruct"
        );
        List<String> chatErrors = new ArrayList<>();
        for (String chatModel : chatModels) {
            if (chatModel == null || chatModel.isBlank()) {
                continue;
            }
            String chatBody = "{\"model\":\"" + escapeJson(chatModel) + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}],"
                    + "\"max_tokens\":" + maxNewTokens + ","
                    + "\"temperature\":" + temperature
                    + "}";
            String chatResult = tryChatCompletion(token, chatBody, chatErrors);
            if (!chatResult.isBlank()) {
                return chatResult;
            }
        }

        List<String> candidateUrls = buildCandidateUrls(endpoint, model);
        List<String> errors = new ArrayList<>();
        for (String url : candidateUrls) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String generated = extractGeneratedText(responseBody);
                if (!generated.isBlank()) {
                    return generated.trim();
                }
                String chatContent = extractChatContent(responseBody);
                if (!chatContent.isBlank()) {
                    return chatContent.trim();
                }
            }

            String loadingError = extractError(responseBody);
            if (!loadingError.isBlank() && loadingError.toLowerCase(Locale.ROOT).contains("loading")) {
                double waitSeconds = extractEstimatedTime(responseBody);
                long waitMillis = waitSeconds > 0.0 ? Math.round(waitSeconds * 1000) : 4000L;
                Thread.sleep(Math.min(waitMillis, 8000L));
                HttpResponse<String> retryResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String retryBody = retryResponse.body() == null ? "" : retryResponse.body();
                if (retryResponse.statusCode() >= 200 && retryResponse.statusCode() < 300) {
                    String generated = extractGeneratedText(retryBody);
                    if (!generated.isBlank()) {
                        return generated.trim();
                    }
                }
                errors.add("[" + url + "] " + shorten(retryBody));
                continue;
            }

            if (!loadingError.isBlank()) {
                errors.add("[" + url + "] " + loadingError);
                continue;
            }
            errors.add("[" + url + "] " + shorten(responseBody));
        }
        if (!errors.isEmpty()) {
            String detail = errors.get(0);
            if (!chatErrors.isEmpty()) {
                detail = "chat=" + chatErrors.get(0) + " | legacy=" + detail;
            }
            throw new IllegalStateException("Réponse IA invalide. Détail: " + detail);
        }
        if (!chatErrors.isEmpty()) {
            throw new IllegalStateException("Réponse IA invalide. Détail chat: " + chatErrors.get(0));
        }
        throw new IllegalStateException("Réponse IA invalide: aucun endpoint Hugging Face valide.");
    }

    private String tryChatCompletion(String token, String chatBody, List<String> errors) throws Exception {
        List<String> chatUrls = List.of(
                "https://router.huggingface.co/v1/chat/completions",
                "https://api-inference.huggingface.co/v1/chat/completions"
        );
        for (String url : chatUrls) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(chatBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String content = extractChatContent(responseBody);
                if (!content.isBlank()) {
                    return content.trim();
                }
            }
            String err = extractError(responseBody);
            if (err.isBlank()) {
                err = "status=" + response.statusCode() + " body=" + shorten(responseBody);
            }
            errors.add("[" + url + "] " + err);
        }
        return "";
    }

    private static List<String> buildCandidateUrls(String endpoint, String model) {
        Set<String> urls = new LinkedHashSet<>();
        String cleanModel = model == null ? "" : model.trim();
        String cleanEndpoint = endpoint == null ? "" : endpoint.trim();
        if (!cleanEndpoint.isBlank()) {
            String normalized = cleanEndpoint.endsWith("/") ? cleanEndpoint : cleanEndpoint + "/";
            if (normalized.contains("/models/")) {
                urls.add(normalized + cleanModel);
            } else {
                urls.add(normalized + "models/" + cleanModel);
            }
        }
        urls.add("https://api-inference.huggingface.co/models/" + cleanModel);
        urls.add("https://router.huggingface.co/hf-inference/models/" + cleanModel);
        return new ArrayList<>(urls);
    }

    private static String extractGeneratedText(String json) {
        Matcher matcher = GENERATED_TEXT_PATTERN.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private static String extractError(String json) {
        Matcher matcher = ERROR_PATTERN.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private static String extractChatContent(String json) {
        Matcher matcher = CHAT_CONTENT_PATTERN.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private static double extractEstimatedTime(String json) {
        Matcher matcher = ESTIMATED_TIME_PATTERN.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "..." : trimmed;
    }

    private static String cfg(String key, String env, String defVal) {
        String s = System.getProperty(key);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        s = System.getenv(env);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        s = FILE_CONFIG.getProperty(key);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        return defVal;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = HuggingFaceTextService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // repli silencieux
        }
        return p;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
