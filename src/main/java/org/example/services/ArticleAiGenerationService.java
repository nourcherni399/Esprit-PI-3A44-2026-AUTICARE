package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.ArticleAiSuggestion;
import org.example.utils.LocalAiPropertiesFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;
import java.util.Set;

public class ArticleAiGenerationService {
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";
    private static final Set<String> ALLOWED_TYPES = Set.of("recommandation", "plainte", "question", "experience");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ArticleAiSuggestion generate(String userPrompt, String type) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String normalizedType = normalizeType(type);

        String safePrompt = userPrompt != null ? userPrompt.trim() : "";
        String lengthInstruction = hasLengthInstruction(safePrompt)
                ? "Respecte la longueur explicitement demandée dans le texte utilisateur."
                : "Si aucune longueur n'est demandée, génère exactement 2 paragraphes pour le contenu.";

        String systemPrompt = """
                Tu es un assistant de rédaction pour une plateforme TSA (troubles du spectre de l'autisme).
                L'utilisateur écrit un seul bloc : il doit y figurer ce dont il parle (sujet) ET le contexte ou la raison (pourquoi).
                Le titre et le contenu doivent refléter ce bloc et rester centrés sur le TSA / l'autisme / l'accompagnement des personnes autistes ou de leurs familles.
                Réponds UNIQUEMENT au format balisé suivant, sans markdown et sans texte autour:
                TITRE: <une seule ligne>
                TYPE: <recommandation|plainte|question|experience>
                CONTENU:
                <contenu clair et utile>
                FIN_CONTENU
                
                %s
                """;

        String userBlock = "Consignes utilisateur:\n" + safePrompt + "\nType souhaité: " + normalizedType + ".";
        String payload = buildPayload(systemPrompt.formatted(lengthInstruction), userBlock);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Erreur API Groq (" + response.statusCode() + "): " + safeTrim(response.body(), 400));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new IOException("Réponse IA invalide (message vide).");
        }

        ParsedArticle parsed = parseTaggedContent(contentNode.asText().trim());
        String finalTitle = safeTrim(parsed.titre(), 255);
        String finalType = normalizeType(parsed.type());
        String finalContent = parsed.contenu().trim();

        if (finalTitle.isBlank() || finalContent.isBlank()) {
            throw new IOException("Réponse IA incomplète. Réessayez.");
        }
        return new ArticleAiSuggestion(finalTitle, finalType, finalContent);
    }

    public PromptAlignmentResult validatePromptForType(String prompt, String type) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String normalizedType = normalizeType(type);
        String safePrompt = prompt != null ? prompt.trim() : "";

        String systemPrompt = """
                Tu vérifies la cohérence entre les intentions de l'utilisateur et un type d'article.
                Le texte est un seul bloc (sujet + contexte mélangés) : interprète-le comme une seule intention.
                Classe le prompt dans EXACTEMENT un type parmi:
                - recommandation: conseiller une pratique, une personne, une méthode.
                - plainte: exprimer un problème, un mécontentement, une difficulté.
                - question: poser une demande d'aide, une interrogation explicite.
                - experience: raconter un vécu personnel/retour d'expérience.

                Règles:
                - Base-toi sur l'intention globale du prompt, pas sur des mots-clés.
                - Tu dois choisir un seul type détecté.
                - CONFIANCE doit être un entier de 0 à 100.
                - ALIGNE compare TYPE_DETECTE avec TYPE_CHOISI.

                Réponds UNIQUEMENT au format:
                TYPE_DETECTE: <recommandation|plainte|question|experience>
                SCORE: <0-100>
                ALIGNE: OUI|NON
                MESSAGE: <explication courte en français, 1 phrase>
                """;
        String userPrompt = "TYPE_CHOISI: " + normalizedType + "\nPROMPT: " + safeTrim(safePrompt, 1200);
        String payload = buildPayload(systemPrompt, userPrompt);
        String modelContent = callGroq(payload);
        return parseAlignment(modelContent, normalizedType);
    }

    /**
     * Vérifie que le texte utilisateur a un lien explicite ou clair avec l'autisme / les TSA
     * (pas un sujet générique : ex. remercier un médecin pour un enfant sans mention TSA/autisme).
     */
    public TsaRelevanceResult validatePromptRelevantToAutisme(String prompt) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String safePrompt = prompt != null ? prompt.trim() : "";
        if (safePrompt.isBlank()) {
            return new TsaRelevanceResult(false, "Saisissez un texte décrivant votre sujet et le contexte.");
        }

        String systemPrompt = """
                Tu filtres les demandes pour un blog TSA (troubles du spectre de l'autisme).
                Le texte utilisateur DOIT porter de façon claire sur au moins un des thèmes suivants :
                autisme, TSA, spectre autistique, personne autiste, enfant/adulte autiste, diagnostic TSA, accompagnement d'une personne autiste, école ou soins en lien avec l'autisme, neurodiversité explicitement liée au spectre autistique.

                Réponds NON (AUTISME_LIE: NON) si le texte est trop générique sans aucun lien identifiable au TSA/autisme
                (exemples à refuser : remercier un psychologue ou parler d'un enfant sans aucune mention ou contexte autisme/TSA/diagnostic spectre).

                Réponds OUI seulement si le lien au TSA/autisme est explicite ou fortement implicite (ex. "psy spécialisé autisme", "mon fils autiste", "atelier TSA", etc.).

                Réponds UNIQUEMENT au format:
                AUTISME_LIE: OUI|NON
                SCORE_AUTISME: <0-100>
                MESSAGE: <une courte phrase en français>
                """;
        String userPrompt = "TEXTE_UTILISATEUR:\n" + safeTrim(safePrompt, 1200);
        String payload = buildPayload(systemPrompt, userPrompt);
        String modelContent = callGroq(payload);
        return parseTsaRelevance(modelContent);
    }

    /**
     * Vérifie que le titre et le contenu de l'article sont cohérents avec le thème du module
     * (titre, description, extrait du contenu pédagogique, catégorie).
     */
    public ModuleArticleAlignmentResult validateArticleAlignsWithModule(
            String articleTitre,
            String articleContenu,
            String articleType,
            String moduleTitre,
            String moduleDescription,
            String moduleContenu,
            String moduleCategorie) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String normalizedArticleType = normalizeType(articleType);
        String safeTitre = articleTitre != null ? articleTitre.trim() : "";
        String safeContenu = articleContenu != null ? articleContenu.trim() : "";
        if (safeTitre.isBlank() || safeContenu.isBlank()) {
            return new ModuleArticleAlignmentResult(false, "Titre et contenu requis pour la vérification.");
        }

        String modTitre = moduleTitre != null ? moduleTitre.trim() : "";
        String modDesc = moduleDescription != null ? moduleDescription.trim() : "";
        String modCat = moduleCategorie != null ? moduleCategorie.trim() : "";
        String modExcerpt = safeTrim(moduleContenu != null ? moduleContenu : "", 2000);

        String systemPrompt = """
                Tu vérifies si un article de blog convient au module pédagogique auquel il sera rattaché.
                Le module a un titre, une description, une catégorie et un contenu pédagogique (aperçu fourni).
                L'article a un type (recommandation, plainte, question, experience), un titre et un contenu.

                Règles:
                - L'article doit traiter du MÊME thème, du même objectif pédagogique ou du même sujet que le module, ou être clairement utile dans ce cadre (ex. retour d'expérience sur le thème du module).
                - Une tangence très lointaine ou un sujet sans lien avec le module = ADEQUATION_MODULE: NON.
                - SCORE_ADEQUATION: entier 0-100 (confiance du lien thématique).
                - ADEQUATION_MODULE: OUI seulement si le lien est clair ou fortement plausible.

                Réponds UNIQUEMENT au format:
                ADEQUATION_MODULE: OUI|NON
                SCORE_ADEQUATION: <0-100>
                MESSAGE: <une courte phrase en français>
                """;

        String userPrompt = "MODULE_TITRE: " + safeTrim(modTitre, 300)
                + "\nMODULE_DESCRIPTION: " + safeTrim(modDesc, 500)
                + "\nMODULE_CATEGORIE: " + safeTrim(modCat, 120)
                + "\nMODULE_APERCU_CONTENU:\n" + modExcerpt
                + "\n\nARTICLE_TYPE: " + normalizedArticleType
                + "\nARTICLE_TITRE: " + safeTrim(safeTitre, 255)
                + "\nARTICLE_CONTENU:\n" + safeTrim(safeContenu, 3200);

        String payload = buildPayload(systemPrompt, userPrompt);
        String modelContent = callGroq(payload);
        return parseModuleArticleAlignment(modelContent);
    }

    public String generateModuleSummary(String titre, String description, String contenu)
            throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String systemPrompt = """
                Tu es un assistant pédagogique pour une plateforme TSA (troubles du spectre de l'autisme).
                Génère un résumé clair, accessible et bienveillant en exactement UN paragraphe (5 à 7 phrases).
                Le résumé doit aller directement au contenu : ce qu'on y apprend, les compétences développées, les difficultés abordées.
                N'utilise jamais de formules d'introduction comme "Ce module", "Dans ce module", "Ce cours", "Cette leçon" ou toute phrase qui présente le module.
                Commence directement par le sujet traité (ex: "Les adultes autistes peuvent...", "Reconnaître et exprimer ses émotions...").
                Réponds UNIQUEMENT avec le texte du résumé, sans titre, sans balise, sans markdown, sans liste.
                """;

        String userPrompt = "MODULE_TITRE: " + safeTrim(titre != null ? titre : "", 255)
                + "\nMODULE_DESCRIPTION: " + safeTrim(description != null ? description : "", 500)
                + "\nMODULE_CONTENU:\n" + safeTrim(contenu != null ? contenu : "", 2000);

        String payload = buildPayload(systemPrompt, userPrompt);
        return callGroq(payload);
    }

    public String generateHashtags(String titre, String contenu, String type) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String normalizedType = normalizeType(type);
        String systemPrompt = """
                Tu es un assistant social media.
                Génère entre 6 et 10 hashtags pertinents, en français, liés au contenu.
                Réponds UNIQUEMENT au format:
                HASHTAGS: #tag1 #tag2 #tag3 ...
                """;
        String userPrompt = "Titre: " + safeTrim(titre, 255)
                + "\nType: " + normalizedType
                + "\nContenu: " + safeTrim(contenu, 2500);

        String payload = buildPayload(systemPrompt, userPrompt);
        String modelContent = callGroq(payload);
        return parseHashtags(modelContent);
    }

    private String buildPayload(String systemPrompt, String userPrompt) throws IOException {
        JsonNode payload = objectMapper.createObjectNode()
                .put("model", GROQ_MODEL)
                .put("temperature", 0.2)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("role", "system").put("content", systemPrompt))
                        .add(objectMapper.createObjectNode().put("role", "user").put("content", userPrompt)));
        return objectMapper.writeValueAsString(payload);
    }

    private String callGroq(String payload) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Erreur API Groq (" + response.statusCode() + "): " + safeTrim(response.body(), 400));
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new IOException("Réponse IA invalide (message vide).");
        }
        return contentNode.asText().trim();
    }

    private ParsedArticle parseTaggedContent(String rawContent) throws IOException {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IOException("Réponse IA vide.");
        }
        String text = rawContent.trim();
        String upper = text.toUpperCase();

        int idxTitre = upper.indexOf("TITRE:");
        int idxType = upper.indexOf("TYPE:");
        int idxContenu = upper.indexOf("CONTENU:");
        int idxFinContenu = upper.indexOf("FIN_CONTENU");

        if (idxTitre < 0 || idxType < 0 || idxContenu < 0) {
            throw new IOException("Réponse IA non conforme. Format TITRE/TYPE/CONTENU attendu.");
        }
        if (idxFinContenu < 0) {
            idxFinContenu = text.length();
        }
        if (!(idxTitre < idxType && idxType < idxContenu)) {
            throw new IOException("Réponse IA non conforme (ordre des sections invalide).");
        }

        String titre = sectionValue(text, idxTitre + "TITRE:".length(), idxType);
        String type = sectionValue(text, idxType + "TYPE:".length(), idxContenu);
        String contenu = text.substring(idxContenu + "CONTENU:".length(), idxFinContenu).trim();
        return new ParsedArticle(titre, type, contenu);
    }

    private static String sectionValue(String full, int start, int end) {
        return full.substring(start, end).replace("\r", "").replace("\n", " ").trim();
    }

    private static String parseHashtags(String rawContent) throws IOException {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IOException("Réponse IA vide.");
        }
        String cleaned = rawContent.trim();
        String upper = cleaned.toUpperCase();
        int idx = upper.indexOf("HASHTAGS:");
        String payload = idx >= 0 ? cleaned.substring(idx + "HASHTAGS:".length()).trim() : cleaned;

        String[] parts = payload.split("\\s+");
        StringJoiner joiner = new StringJoiner(" ");
        int kept = 0;
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            String t = p.trim();
            if (!t.startsWith("#")) {
                t = "#" + t;
            }
            t = t.replaceAll("[^#a-zA-ZÀ-ÿ0-9_]", "");
            if (t.length() <= 1) continue;
            joiner.add(t);
            kept++;
            if (kept >= 10) break;
        }
        String out = joiner.toString().trim();
        if (out.isBlank()) {
            throw new IOException("Hashtags non générés.");
        }
        return out;
    }

    private static TsaRelevanceResult parseTsaRelevance(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new TsaRelevanceResult(false, "Réponse de vérification vide. Réessayez.");
        }
        String text = rawContent.trim();
        String upper = text.toUpperCase();
        int idxLie = upper.indexOf("AUTISME_LIE:");
        int idxScore = upper.indexOf("SCORE_AUTISME:");
        int idxMessage = upper.indexOf("MESSAGE:");

        if (idxLie < 0) {
            return new TsaRelevanceResult(false, "Impossible de vérifier le lien avec les TSA. Précisez autisme ou TSA dans votre texte.");
        }
        int afterLie = idxLie + "AUTISME_LIE:".length();
        int nextLie = nextIndexAfter(afterLie, idxScore, idxMessage, text.length());
        String liePart = text.substring(afterLie, nextLie).trim();
        boolean oui = liePart.toUpperCase().startsWith("OUI");

        String scorePart = "";
        if (idxScore >= 0) {
            int afterScore = idxScore + "SCORE_AUTISME:".length();
            int nextScore = nextIndexAfter(afterScore, idxMessage, text.length());
            scorePart = text.substring(afterScore, nextScore).trim();
        }
        int scoreAutisme = parseScore(scorePart);

        String messagePart = "";
        if (idxMessage >= 0) {
            messagePart = text.substring(idxMessage + "MESSAGE:".length()).trim();
        }

        if (!oui) {
            String msg = messagePart.isBlank()
                    ? "Votre texte ne montre pas assez clairement un lien avec l'autisme ou les TSA. Reformulez en mentionnant le contexte TSA (ex. accompagnement autiste, diagnostic, spécialiste autisme…)."
                    : messagePart;
            return new TsaRelevanceResult(false, msg);
        }
        if (scoreAutisme >= 0 && scoreAutisme < 60) {
            return new TsaRelevanceResult(false,
                    messagePart.isBlank()
                            ? "Le lien avec les TSA est trop faible (" + scoreAutisme + "%). Ajoutez des précisions sur l'autisme ou le spectre autistique."
                            : messagePart);
        }
        return new TsaRelevanceResult(true, "");
    }

    private static ModuleArticleAlignmentResult parseModuleArticleAlignment(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new ModuleArticleAlignmentResult(false, "Réponse de vérification vide. Réessayez.");
        }
        String text = rawContent.trim();
        String upper = text.toUpperCase();
        int idxAdeq = upper.indexOf("ADEQUATION_MODULE:");
        int idxScore = upper.indexOf("SCORE_ADEQUATION:");
        int idxMessage = upper.indexOf("MESSAGE:");

        if (idxAdeq < 0) {
            return new ModuleArticleAlignmentResult(false,
                    "Impossible de vérifier la cohérence avec le module. Réessayez ou reformulez l'article.");
        }
        int afterAdeq = idxAdeq + "ADEQUATION_MODULE:".length();
        int nextAdeq = nextIndexAfter(afterAdeq, idxScore, idxMessage, text.length());
        String adeqPart = text.substring(afterAdeq, nextAdeq).trim();
        boolean oui = adeqPart.toUpperCase().startsWith("OUI");

        String scorePart = "";
        if (idxScore >= 0) {
            int afterScore = idxScore + "SCORE_ADEQUATION:".length();
            int nextScore = nextIndexAfter(afterScore, idxMessage, text.length());
            scorePart = text.substring(afterScore, nextScore).trim();
        }
        int score = parseScore(scorePart);

        String messagePart = "";
        if (idxMessage >= 0) {
            messagePart = text.substring(idxMessage + "MESSAGE:".length()).trim();
        }

        if (!oui) {
            String msg = messagePart.isBlank()
                    ? "L'article ne semble pas correspondre au sujet de ce module. Ajustez le titre ou le contenu."
                    : messagePart;
            return new ModuleArticleAlignmentResult(false, msg);
        }
        if (score >= 0 && score < 60) {
            return new ModuleArticleAlignmentResult(false,
                    messagePart.isBlank()
                            ? "Le lien entre l'article et le module est trop faible (" + score + "%). Précisez le rapport avec le thème du module."
                            : messagePart);
        }
        return new ModuleArticleAlignmentResult(true, "");
    }

    private static PromptAlignmentResult parseAlignment(String rawContent, String chosenType) {
        if (rawContent == null || rawContent.isBlank()) {
            return new PromptAlignmentResult(true, "");
        }
        String text = rawContent.trim();
        String upper = text.toUpperCase();
        int idxDetectedType = upper.indexOf("TYPE_DETECTE:");
        int idxAligned = upper.indexOf("ALIGNE:");
        int idxScore = upper.indexOf("SCORE:");
        int idxMessage = upper.indexOf("MESSAGE:");

        if (idxAligned < 0 || idxDetectedType < 0 || idxScore < 0) {
            return new PromptAlignmentResult(true, "");
        }
        String alignedPart = "";
        String scorePart = "";
        String detectedTypePart = "";
        String messagePart = "";

        int afterDetected = idxDetectedType + "TYPE_DETECTE:".length();
        int nextAfterDetected = nextIndexAfter(afterDetected, idxScore, idxAligned, idxMessage, text.length());
        detectedTypePart = text.substring(afterDetected, nextAfterDetected).trim();

        if (idxScore >= 0) {
            int afterScore = idxScore + "SCORE:".length();
            int nextAfterScore = nextIndexAfter(afterScore, idxAligned, idxMessage, text.length());
            scorePart = text.substring(afterScore, nextAfterScore).trim();
        }
        if (idxAligned >= 0) {
            int afterAligned = idxAligned + "ALIGNE:".length();
            int nextAfterAligned = nextIndexAfter(afterAligned, idxMessage, text.length());
            alignedPart = text.substring(afterAligned, nextAfterAligned).trim();
        }
        if (idxMessage >= 0) {
            messagePart = text.substring(idxMessage + "MESSAGE:".length()).trim();
        }

        boolean aligned = alignedPart.toUpperCase().startsWith("OUI");
        int score = parseScore(scorePart);
        String detectedType = normalizeType(detectedTypePart);

        // Critère dur: le modèle dit NON.
        if (!aligned) {
            String finalMessage = messagePart.isBlank() ? defaultMismatchMessage(chosenType) : messagePart;
            return new PromptAlignmentResult(false, finalMessage);
        }

        // Critère de prudence: score faible => ambigu, demander reformulation.
        if (score >= 0 && score < 65) {
            return new PromptAlignmentResult(false,
                    "Le prompt est ambigu (confiance " + score + "%). Reformulez-le pour préciser l'intention.");
        }

        // Critère principal: type détecté différent du type choisi avec bonne confiance.
        String normalizedChosen = normalizeType(chosenType);
        if (!detectedType.equals(normalizedChosen) && score >= 65) {
            String fallback = "Le prompt correspond plutôt au type '" + detectedType + "' qu'au type choisi '" + normalizedChosen + "'.";
            return new PromptAlignmentResult(false, messagePart.isBlank() ? fallback : messagePart);
        }

        return new PromptAlignmentResult(true, "");
    }

    private static int parseScore(String scorePart) {
        if (scorePart == null || scorePart.isBlank()) return -1;
        String digits = scorePart.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int nextIndexAfter(int baseStart, int... candidates) {
        int min = Integer.MAX_VALUE;
        for (int c : candidates) {
            if (c >= baseStart && c < min) min = c;
        }
        return min == Integer.MAX_VALUE ? baseStart : min;
    }

    private static String defaultMismatchMessage(String type) {
        return switch (normalizeType(type)) {
            case "recommandation" -> "Le sujet ne ressemble pas à une recommandation claire. Donnez un conseil concret à proposer.";
            case "plainte" -> "Le sujet ne ressemble pas à une plainte. Décrivez un problème précis ou un mécontentement.";
            case "question" -> "Le sujet ne ressemble pas à une question. Reformulez sous forme d'interrogation explicite.";
            case "experience" -> "Le sujet ne ressemble pas à un retour d'expérience. Décrivez un vécu personnel concret.";
            default -> "Le sujet ne semble pas aligné avec le type choisi.";
        };
    }

    private static String normalizeType(String type) {
        if (type == null) return "question";
        String normalized = type.trim().toLowerCase();
        return ALLOWED_TYPES.contains(normalized) ? normalized : "question";
    }

    private static String safeTrim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static boolean hasLengthInstruction(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.toLowerCase();
        return t.contains("paragraphe")
                || t.contains("paragraphes")
                || t.contains("long")
                || t.contains("court")
                || t.contains("détaillé")
                || t.contains("detaille")
                || t.contains("résumé")
                || t.contains("resume");
    }

    private static String resolveApiKey() {
        String env = System.getenv("GROQ_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = LocalAiPropertiesFile.readProperty("groq.api.key");
        return fromFile != null && !fromFile.isBlank() ? fromFile : null;
    }

    private record ParsedArticle(String titre, String type, String contenu) {}
    public record PromptAlignmentResult(boolean aligned, String message) {}
    public record TsaRelevanceResult(boolean relevant, String message) {}
    public record ModuleArticleAlignmentResult(boolean aligned, String message) {}
}
