package org.example.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.text.Normalizer;
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

    public String suggestReplyForParticipantMessage(String eventTitle, String participantMessage, String conversationContext,
                                                    String eventFacts)
            throws Exception {
        String safeEvent = eventTitle == null || eventTitle.isBlank() ? "Événement" : eventTitle.trim();
        String safeMessage = participantMessage == null ? "" : participantMessage.trim();
        if (safeMessage.isBlank()) {
            throw new IllegalArgumentException("Message participant introuvable pour générer une réponse.");
        }
        String safeContext = conversationContext == null ? "" : conversationContext.trim();
        String safeFacts = eventFacts == null ? "" : eventFacts.trim();
        String prompt = """
                Tu es un assistant de support admin d'une application d'événements.
                Rédige une réponse en français à envoyer à un participant.
                Contexte :
                - Événement : %s
                - Informations connues sur l'événement : %s
                - Historique récent : %s
                - Dernier message du participant : %s

                Contraintes :
                - Ton professionnel, empathique, clair.
                - 3 à 6 phrases.
                - Répondre précisément à la demande.
                - Si le participant dit seulement "bonjour", produire une réponse complète (salutation + proposition d'aide + prochaine étape).
                - Ne jamais retourner "..." ni une réponse incomplète.
                - Si une information manque, poser une courte question de clarification.
                - Si le participant demande le lieu et qu'il est connu dans les informations, répondre directement avec ce lieu.
                - N'invente jamais une information absente du contexte.
                - Retourne uniquement le texte de réponse final.
                """.formatted(
                safeEvent,
                safeFacts.isBlank() ? "Aucune information supplémentaire." : safeFacts,
                safeContext.isBlank() ? "Aucun." : safeContext,
                safeMessage
        );
        String firstReply = runGeneration(prompt, 210, 0.55).trim();
        if (isReplyRelevantToMessage(firstReply, safeMessage)) {
            return firstReply;
        }

        String strictPrompt = """
                Tu es un assistant de support admin d'une application d'événements.
                Tu dois répondre de façon directement liée au message du participant.

                Événement : %s
                Informations connues : %s
                Historique récent : %s
                Message du participant : %s

                Instructions strictes :
                - La première phrase doit répondre au besoin principal exprimé dans le message du participant.
                - Reprends explicitement au moins un mot-clé du message (ex: retard, activités, lieu, horaire, inscription).
                - Si l'info n'est pas connue, dis-le clairement puis pose UNE question de clarification utile.
                - Ne donne pas de réponse générique.
                - N'invente aucune information.
                - 2 à 5 phrases.
                - Retourne uniquement la réponse finale en français.
                """.formatted(
                safeEvent,
                safeFacts.isBlank() ? "Aucune information supplémentaire." : safeFacts,
                safeContext.isBlank() ? "Aucun." : safeContext,
                safeMessage
        );
        String secondReply = runGeneration(strictPrompt, 220, 0.35).trim();
        if (!secondReply.isBlank()) {
            return secondReply;
        }
        return firstReply;
    }

    private static boolean isReplyRelevantToMessage(String reply, String participantMessage) {
        String safeReply = reply == null ? "" : reply.trim();
        String safeMessage = participantMessage == null ? "" : participantMessage.trim();
        if (safeReply.isBlank() || safeMessage.isBlank()) {
            return false;
        }
        if (safeReply.length() < 25) {
            return false;
        }

        Set<String> messageKeywords = extractMeaningfulTokens(safeMessage);
        if (messageKeywords.isEmpty()) {
            return true;
        }
        Set<String> replyKeywords = extractMeaningfulTokens(safeReply);
        int overlaps = 0;
        for (String keyword : messageKeywords) {
            if (replyKeywords.contains(keyword)) {
                overlaps++;
            }
        }
        return overlaps >= 1;
    }

    private static Set<String> extractMeaningfulTokens(String text) {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> stopWords = Set.of(
                "bonjour", "bonsoir", "salut", "merci", "svp", "s il", "si", "je", "tu", "vous", "nous",
                "de", "du", "des", "la", "le", "les", "un", "une", "et", "ou", "a", "au", "aux",
                "est", "ce", "cela", "ça", "ca", "pour", "dans", "sur", "avec", "par", "que", "qui",
                "qu", "mon", "ma", "mes", "ton", "ta", "tes", "notre", "votre", "leur", "leurs",
                "pouvoir", "possible", "peut", "faire", "avoir", "etre", "savoir"
        );
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : normalized.split("\\s+")) {
            String token = part.trim();
            if (token.length() < 3) {
                continue;
            }
            if (stopWords.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccent.replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    /**
     * Propose des événements inspirés par les résultats d’index web (résumé texte) déjà regroupé par l’appelant.
     */
    public String suggestEventProposalsFromWebContext(String userKeywords, String periode, String resultDigest)
            throws Exception {
        String kw = userKeywords == null ? "" : userKeywords.trim();
        String p = periode == null ? "" : periode.trim();
        if (kw.isBlank()) {
            throw new IllegalArgumentException("Aucun mot-clé de recherche.");
        }
        String body = resultDigest == null ? "" : resultDigest.trim();
        if (body.isBlank()) {
            throw new IllegalArgumentException("Aucun résultat de recherche à transmettre à l’IA.");
        }
        String safeBody = body.length() > 12000 ? body.substring(0, 12000) + "…" : body;
        String safeKw = kw.length() > 200 ? kw.substring(0, 200) : kw;
        String periodLabel = p.isBlank() ? "—" : p;
        String prompt = """
                Contexte (sources web publiques indexées par la recherche, à utiliser seulement comme idées générales) :
                %s

                Mots-clés utilisés par l’utilisateur : « %s ». Période retenue : %s.

                Tâche : proposer 3 à 4 idées d’événements que l’association pourrait organiser, en cohérence avec cette recherche.
                Pour chaque idée, fournir en français, en respectant le format structuré suivant :

                1) TITRE: ...
                2) DESCRIPTION: (2 à 4 phrases, ton institutionnel, sans inventer de partenaires inconnus) ...
                3) SUJETS: (3 à 6 mots-thèmes séparés par des virgules) ...

                Règles :
                - S’inspirer des thèmes et lieux proposés par l’index sans copier de longs extraits d’un site.
                - Ne jamais affirmer qu’un événement a lieu si ce n’est pas sûr ; proposer plutôt « une journée d’échanges sur... »
                - Si les sources sont incertaines, le signaler en une courte phrase.
                - Retourner uniquement le texte des propositions, une idée numérotée 1) à 4) sans préambule.
                """.formatted(safeBody, safeKw, periodLabel);
        return runGeneration(prompt, 800, 0.65);
    }

    public String recommendEventFromParticipantIdeas(String analysisDigest) throws Exception {
        String digest = analysisDigest == null ? "" : analysisDigest.trim();
        if (digest.isBlank()) {
            throw new IllegalArgumentException("Analyse des idees vide.");
        }
        String safeDigest = digest.length() > 12000 ? digest.substring(0, 12000) + "..." : digest;
        String prompt = """
                Tu es un assistant IA qui aide un admin a choisir un evenement.
                Base-toi UNIQUEMENT sur l'analyse suivante des propositions des participants :
                %s

                Tache :
                1) Proposer un evenement principal recommande.
                2) Donner un court "pourquoi" (2 a 3 phrases max).
                3) Donner 3 recommandations actionnables pour l'admin.

                Format de sortie strict :
                TITRE: ...
                DESCRIPTION: ...
                POURQUOI: ...
                RECOMMANDATIONS:
                - ...
                - ...
                - ...
                """.formatted(safeDigest);
        return runGeneration(prompt, 280, 0.5);
    }

    public String recommendEventFromIdeaAnalysisJson(String structuredPrompt) throws Exception {
        String prompt = structuredPrompt == null ? "" : structuredPrompt.trim();
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt d'analyse idees vide.");
        }
        String safePrompt = prompt.length() > 14000 ? prompt.substring(0, 14000) + "..." : prompt;
        String generated = runGeneration(safePrompt, 420, 0.35);
        String json = extractJsonObject(generated);
        return json.isBlank() ? generated : json;
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
        String parsed = extractFromJson(json, "generated_text");
        if (!parsed.isBlank()) {
            return parsed;
        }
        Matcher matcher = GENERATED_TEXT_PATTERN.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private static String extractError(String json) {
        String parsed = extractFromJson(json, "error");
        if (!parsed.isBlank()) {
            return parsed;
        }
        Matcher matcher = ERROR_PATTERN.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private static String extractChatContent(String json) {
        String parsed = extractFromJson(json, "content");
        if (!parsed.isBlank()) {
            return parsed;
        }
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

    private static String extractFromJson(String json, String key) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            return findStringByKey(root, key);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String findStringByKey(JsonElement element, String targetKey) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has(targetKey)) {
                JsonElement v = obj.get(targetKey);
                if (v != null && v.isJsonPrimitive()) {
                    return v.getAsString();
                }
            }
            for (var e : obj.entrySet()) {
                String found = findStringByKey(e.getValue(), targetKey);
                if (!found.isBlank()) {
                    return found;
                }
            }
            return "";
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement child : arr) {
                String found = findStringByKey(child, targetKey);
                if (!found.isBlank()) {
                    return found;
                }
            }
        }
        return "";
    }

    private static String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String s = text.trim();
        int fenceStart = s.indexOf("```");
        if (fenceStart >= 0) {
            int jsonStart = s.indexOf("{", fenceStart);
            int fenceEnd = s.lastIndexOf("```");
            if (jsonStart >= 0 && fenceEnd > jsonStart) {
                String fenced = s.substring(jsonStart, fenceEnd).trim();
                if (isValidJsonObject(fenced)) {
                    return fenced;
                }
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = s.substring(start, end + 1).trim();
            if (isValidJsonObject(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static boolean isValidJsonObject(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            return el != null && el.isJsonObject();
        } catch (Exception ignored) {
            return false;
        }
    }
}
