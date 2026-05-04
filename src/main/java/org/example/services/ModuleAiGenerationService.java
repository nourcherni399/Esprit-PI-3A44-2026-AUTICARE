package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.ModuleAiSuggestion;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleNiveau;
import org.example.utils.LocalAiPropertiesFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModuleAiGenerationService {
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";
    private static final int MAX_DESCRIPTION = 255;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ModuleAiSuggestion generate(String sujet, ModuleCategorie categorie) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String safeSujet = sujet != null ? sujet.trim() : "";
        String explicitTitle = extractExplicitTitle(safeSujet);

        String systemPrompt = """
                Tu es un assistant pédagogique pour une plateforme d'accompagnement TSA.
                Ici, l'acronyme TSA signifie EXCLUSIVEMENT: Trouble du Spectre de l'Autisme.
                Interdiction absolue de confondre TSA avec la fiscalité (taxe, TVA, impôt, valeur ajoutée).
                Tu dois générer un module structuré en français.
                Réponds UNIQUEMENT au format texte balisé ci-dessous, sans markdown, sans JSON, sans phrase avant/après.
                Si l'utilisateur impose un titre (TITRE_IMPOSE), tu dois l'utiliser EXACTEMENT tel quel dans TITRE.

                Format obligatoire:
                TITRE: <une seule ligne>
                DESCRIPTION: <une seule ligne, max 255 caractères>
                NIVEAU: <facile|moyen|difficile>
                CONTENU:
                <contenu détaillé en 4 à 8 paragraphes>
                FIN_CONTENU
                """;

        String userPrompt = "Catégorie: " + categorie.name() + " (" + categorie.getLibelle() + "). "
                + "Sujet: " + safeSujet + "."
                + (explicitTitle.isBlank() ? "" : "\nTITRE_IMPOSE: " + explicitTitle);

        String payload = buildPayload(systemPrompt, userPrompt);
        String content = callGroq(payload);
        ParsedText parsed;
        try {
            parsed = parseTaggedContent(content);
        } catch (IOException firstParseError) {
            // Auto-retry: demande à l'IA de reformater strictement la réponse précédente.
            String repairSystemPrompt = """
                    Tu reformates une réponse de module en format strict sans rien inventer.
                    Réponds UNIQUEMENT avec:
                    TITRE: ...
                    DESCRIPTION: ...
                    NIVEAU: facile|moyen|difficile
                    CONTENU:
                    ...
                    FIN_CONTENU
                    """;
            String repairUserPrompt = "Reformate ce texte en conservant le sens:\n\n" + safeTrim(content, 6000);
            String repaired = callGroq(buildPayload(repairSystemPrompt, repairUserPrompt));
            parsed = parseTaggedContent(repaired);
        }
        String titre = safeTrim(explicitTitle.isBlank() ? parsed.titre() : explicitTitle, 255);
        String description = safeTrim(parsed.description(), MAX_DESCRIPTION);
        String contenu = parsed.contenu().trim();
        ModuleNiveau niveau = parseNiveau(parsed.niveau());

        if (titre.isBlank() || description.isBlank() || contenu.isBlank()) {
            throw new IOException("Réponse IA incomplète. Réessayez.");
        }

        if (hasFiscalTsaConfusion(titre, description, contenu)) {
            String strictSystemPrompt = """
                    Tu es un assistant pédagogique TSA.
                    Règle non négociable: "TSA" = "Trouble du Spectre de l'Autisme" uniquement.
                    Interdiction de parler de taxe, TVA, impôt, fiscalité, valeur ajoutée.
                    Génère un module sur l'autisme/TSA en gardant le format strict:
                    TITRE: <une seule ligne>
                    DESCRIPTION: <une seule ligne, max 255 caractères>
                    NIVEAU: <facile|moyen|difficile>
                    CONTENU:
                    <contenu détaillé en 4 à 8 paragraphes>
                    FIN_CONTENU
                    """;
            String strictUserPrompt = userPrompt + "\nIMPORTANT: Le sigle TSA dans cette application signifie seulement Trouble du Spectre de l'Autisme.";
            String strictContent = callGroq(buildPayload(strictSystemPrompt, strictUserPrompt));
            ParsedText strictParsed = parseTaggedContent(strictContent);
            titre = safeTrim(explicitTitle.isBlank() ? strictParsed.titre() : explicitTitle, 255);
            description = safeTrim(strictParsed.description(), MAX_DESCRIPTION);
            contenu = strictParsed.contenu().trim();
            niveau = parseNiveau(strictParsed.niveau());
        }

        if (hasFiscalTsaConfusion(titre, description, contenu)) {
            throw new IOException("Le prompt a généré une confusion TSA/fiscalité. Précisez: \"TSA = Trouble du Spectre de l'Autisme\".");
        }

        return new ModuleAiSuggestion(titre, description, contenu, niveau);
    }

    /**
     * Vérifie que le prompt est bien lié à l'autisme/TSA.
     */
    public TsaRelevanceResult validatePromptRelevantToAutisme(String prompt) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }

        String safePrompt = prompt != null ? prompt.trim() : "";
        if (safePrompt.isBlank()) {
            return new TsaRelevanceResult(false, "Veuillez saisir un prompt.");
        }

        // Couche 1: filtre mots-clés rapide
        String normalized = normalizeKey(safePrompt);
        boolean hasTsaSignal = containsAnyToken(normalized,
                "TSA",
                "AUTISME",
                "AUTISTE",
                "SPECTRE_AUTISTIQUE",
                "TROUBLE_SPECTRE_AUTISTIQUE",
                "DIAGNOSTIC_TSA",
                "NEURODIVERSITE");
        if (hasTsaSignal) {
            return new TsaRelevanceResult(true, "");
        }

        // Couche 2: vérification sémantique par Groq
        String systemPrompt = """
                Tu filtres les prompts pour la génération de modules d'une plateforme TSA.
                Accepte UNIQUEMENT les prompts liés clairement à l'autisme/TSA.
                Refuse les sujets génériques sans lien explicite (même s'ils parlent d'émotions, autonomie ou éducation).

                Réponds UNIQUEMENT au format:
                AUTISME_LIE: OUI|NON
                SCORE_AUTISME: <0-100>
                MESSAGE: <une phrase courte en français>
                """;
        String userPrompt = "PROMPT:\n" + safeTrim(safePrompt, 1200);
        String modelContent = callGroq(buildPayload(systemPrompt, userPrompt));
        return parseTsaRelevance(modelContent);
    }

    /**
     * Vérifie que le prompt correspond bien à la catégorie choisie.
     * Si non, retourne la catégorie détectée pour guider l'utilisateur.
     */
    public PromptCategoryAlignmentResult validatePromptForCategory(String prompt, ModuleCategorie chosenCategory)
            throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }
        String safePrompt = prompt != null ? prompt.trim() : "";
        if (safePrompt.isBlank()) {
            return new PromptCategoryAlignmentResult(false, ModuleCategorie.NON_DEFINI,
                    "Veuillez saisir un prompt.");
        }
        ModuleCategorie chosen = chosenCategory != null ? chosenCategory : ModuleCategorie.NON_DEFINI;

        // Couche 1: filtre mots-clés (rapide, strict sur incohérence évidente)
        CategoryKeywordScores scores = scoreCategoriesByKeywords(safePrompt);
        KeywordCategorySignal keywordSignal = detectCategoryByKeywords(safePrompt);
        int chosenScore = scores.scoreFor(chosen);
        if (keywordSignal.category() != ModuleCategorie.NON_DEFINI
                && keywordSignal.score() >= 2
                && chosenScore == 0
                && keywordSignal.category() != chosen) {
            String msg = "Le prompt ne contient pas d'indice clair pour \"" + chosen.getLibelle()
                    + "\" mais contient des indices pour \"" + keywordSignal.category().getLibelle()
                    + "\" (indices: " + keywordSignal.reason() + ").";
            return new PromptCategoryAlignmentResult(false, keywordSignal.category(), msg);
        }
        if (keywordSignal.category() != ModuleCategorie.NON_DEFINI
                && keywordSignal.score() >= 2
                && keywordSignal.category() != chosen) {
            String msg = "Le prompt semble orienté vers la catégorie \"" + keywordSignal.category().getLibelle()
                    + "\" (indices: " + keywordSignal.reason() + "), pas \"" + chosen.getLibelle() + "\".";
            return new PromptCategoryAlignmentResult(false, keywordSignal.category(), msg);
        }

        String systemPrompt = """
                Tu vérifies si un prompt pédagogique correspond à une catégorie de module.
                Catégories possibles (nom technique -> libellé):
                - COMPRENDRE_TSA -> Comprendre le TSA
                - AUTONOMIE -> Autonomie
                - COMMUNICATION -> Communication
                - EMOTIONS -> Émotions
                - VIE_QUOTIDIENNE -> Vie quotidienne
                - ACCOMPAGNEMENT -> Accompagnement
                - NON_DEFINI -> Non défini (à éviter)

                Règles:
                - Déduis la catégorie la plus pertinente d'après l'intention globale du prompt.
                - Réponds NON si la catégorie choisie ne correspond pas.
                - Sois strict: si le sujet principal est différent, ALIGNE doit être NON.
                - Tu dois toujours choisir la catégorie dominante selon le sens global, pas juste un mot isolé.
                - SCORE est un entier de 0 à 100.
                - CATEGORIE_DETECTEE doit être le nom technique exact (ex: VIE_QUOTIDIENNE).

                Réponds UNIQUEMENT au format:
                CATEGORIE_DETECTEE: <NON_DEFINI|COMPRENDRE_TSA|AUTONOMIE|COMMUNICATION|EMOTIONS|VIE_QUOTIDIENNE|ACCOMPAGNEMENT>
                SCORE: <0-100>
                ALIGNE: OUI|NON
                MESSAGE: <une phrase courte en français>
                """;
        String userPrompt = "CATEGORIE_CHOISIE: " + chosen.name() + "\nPROMPT: " + safeTrim(safePrompt, 1200);
        String modelContent = callGroq(buildPayload(systemPrompt, userPrompt));
        PromptCategoryAlignmentResult semantic = parseCategoryAlignment(modelContent, chosen);

        // Couche 3: garde-fou combiné (keywords + sémantique)
        if (semantic.aligned()
                && keywordSignal.category() != ModuleCategorie.NON_DEFINI
                && keywordSignal.score() >= 2
                && keywordSignal.category() != chosen) {
            String msg = "Le prompt contient des indices forts de \"" + keywordSignal.category().getLibelle()
                    + "\" (mots-clés: " + keywordSignal.reason() + ").";
            return new PromptCategoryAlignmentResult(false, keywordSignal.category(), msg);
        }
        return semantic;
    }

    private String buildPayload(String systemPrompt, String userPrompt) throws IOException {
        JsonNode payload = objectMapper.createObjectNode()
                .put("model", GROQ_MODEL)
                .put("temperature", 0.2)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", systemPrompt))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", userPrompt)));
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

    private ParsedText parseTaggedContent(String rawContent) throws IOException {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IOException("Réponse IA vide.");
        }
        String text = rawContent.trim().replace("\r\n", "\n");
        // Nettoie les wrappers markdown fréquents.
        text = text.replace("```text", "").replace("```", "").trim();

        String titre = extractLineField(text, "TITRE", "TITLE", "INTITULE", "INTITULÉ");
        String description = extractLineField(text, "DESCRIPTION", "DESC");
        String niveau = extractLineField(text, "NIVEAU", "DIFFICULTE", "DIFFICULTÉ");
        String contenu = extractContentField(text);

        if (titre.isBlank() || description.isBlank() || niveau.isBlank() || contenu.isBlank()) {
            throw new IOException("Réponse IA non conforme. Le format attendu (TITRE/DESCRIPTION/NIVEAU/CONTENU) n'a pas été respecté.");
        }

        return new ParsedText(titre, description, niveau, contenu);
    }

    private static String extractLineField(String text, String... labels) {
        for (String label : labels) {
            String pattern = "(?im)^\\s*[*#\\-`> ]*(?:" + Pattern.quote(label) + ")\\s*[:\\-]\\s*(.+)$";
            Matcher m = Pattern.compile(pattern).matcher(text);
            if (m.find()) {
                String value = m.group(1) != null ? m.group(1).trim() : "";
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String extractContentField(String text) {
        String pattern = "(?is)^\\s*[*#\\-`> ]*CONTENU\\s*[:\\-]\\s*\\n?(.*?)(?=^\\s*[*#\\-`> ]*FIN_CONTENU\\b|\\z)";
        Matcher m = Pattern.compile(pattern, Pattern.MULTILINE).matcher(text);
        if (m.find()) {
            String value = m.group(1) != null ? m.group(1).trim() : "";
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record ParsedText(String titre, String description, String niveau, String contenu) {}

    @SuppressWarnings("unused")
    private JsonNode parseGeneratedJson(String rawContent) throws IOException {
        return objectMapper.readTree(rawContent);
    }

    private static ModuleNiveau parseNiveau(String raw) {
        if (raw == null) return ModuleNiveau.moyen;
        String v = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
        if (v.contains("facile")) return ModuleNiveau.facile;
        if (v.contains("difficile")) return ModuleNiveau.difficile;
        return ModuleNiveau.moyen;
    }

    private static String safeTrim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static PromptCategoryAlignmentResult parseCategoryAlignment(String raw, ModuleCategorie chosen) {
        if (raw == null || raw.isBlank()) {
            return new PromptCategoryAlignmentResult(false, chosen,
                    "Analyse de catégorie vide. Réessayez avec un prompt plus précis.");
        }
        String text = raw.trim();
        String upper = text.toUpperCase();
        int idxDetected = upper.indexOf("CATEGORIE_DETECTEE:");
        int idxScore = upper.indexOf("SCORE:");
        int idxAligned = upper.indexOf("ALIGNE:");
        int idxMessage = upper.indexOf("MESSAGE:");

        if (idxDetected < 0 || idxAligned < 0) {
            // Fallback robuste: essaie quand même d'inférer une catégorie depuis la réponse libre.
            ModuleCategorie inferred = parseCategorie(text);
            if (inferred != ModuleCategorie.NON_DEFINI && inferred != chosen) {
                String msg = "Le prompt semble plutôt lié à \"" + inferred.getLibelle()
                        + "\". Veuillez choisir cette catégorie.";
                return new PromptCategoryAlignmentResult(false, inferred, msg);
            }
            if (inferred == chosen) {
                return new PromptCategoryAlignmentResult(true, chosen, "");
            }
            return new PromptCategoryAlignmentResult(false, chosen,
                    "Impossible de valider clairement la catégorie du prompt. Reformulez avec un objectif plus précis.");
        }

        String detectedRaw = section(text, idxDetected + "CATEGORIE_DETECTEE:".length(), idxScore, idxAligned, idxMessage);
        String scoreRaw = idxScore >= 0 ? section(text, idxScore + "SCORE:".length(), idxAligned, idxMessage) : "";
        String alignedRaw = section(text, idxAligned + "ALIGNE:".length(), idxMessage);
        String message = idxMessage >= 0 ? text.substring(idxMessage + "MESSAGE:".length()).trim() : "";

        ModuleCategorie detected = parseCategorie(detectedRaw);
        boolean aligned = alignedRaw.toUpperCase().startsWith("OUI");
        int score = parseScore(scoreRaw);

        if (!aligned) {
            String fallback = "Le prompt correspond plutôt à la catégorie \"" + detected.getLibelle()
                    + "\" qu'à \"" + chosen.getLibelle() + "\".";
            return new PromptCategoryAlignmentResult(false, detected, message.isBlank() ? fallback : message);
        }
        if (score >= 0 && score < 75) {
            String msg = "Le prompt est ambigu (confiance " + score + "%). Précisez mieux le sujet pour la catégorie.";
            return new PromptCategoryAlignmentResult(false, detected, message.isBlank() ? msg : message);
        }
        if (detected != ModuleCategorie.NON_DEFINI && detected != chosen) {
            String fallback = "Le prompt est plus proche de \"" + detected.getLibelle() + "\".";
            return new PromptCategoryAlignmentResult(false, detected, message.isBlank() ? fallback : message);
        }
        if (detected == ModuleCategorie.NON_DEFINI) {
            String fallback = "Impossible d'identifier clairement la catégorie. Précisez l'objectif principal du module.";
            return new PromptCategoryAlignmentResult(false, chosen, message.isBlank() ? fallback : message);
        }
        return new PromptCategoryAlignmentResult(true, chosen, "");
    }

    private static String section(String text, int start, int... endCandidates) {
        int end = text.length();
        for (int c : endCandidates) {
            if (c >= start && c < end) end = c;
        }
        return text.substring(start, end).trim();
    }

    private static int parseScore(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static TsaRelevanceResult parseTsaRelevance(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new TsaRelevanceResult(false, "Impossible de vérifier le lien avec l'autisme/TSA. Réessayez.");
        }
        String text = rawContent.trim();
        String upper = text.toUpperCase();
        int idxLie = upper.indexOf("AUTISME_LIE:");
        int idxScore = upper.indexOf("SCORE_AUTISME:");
        int idxMessage = upper.indexOf("MESSAGE:");

        if (idxLie < 0) {
            return new TsaRelevanceResult(false,
                    "Le prompt doit concerner explicitement l'autisme/TSA pour générer un module.");
        }

        String liePart = section(text, idxLie + "AUTISME_LIE:".length(), idxScore, idxMessage);
        boolean oui = liePart.toUpperCase().startsWith("OUI");
        String scoreRaw = idxScore >= 0 ? section(text, idxScore + "SCORE_AUTISME:".length(), idxMessage) : "";
        int scoreAutisme = parseScore(scoreRaw);
        String message = idxMessage >= 0 ? text.substring(idxMessage + "MESSAGE:".length()).trim() : "";

        if (!oui) {
            String fallback = "Le prompt n'est pas assez lié à l'autisme/TSA. Mentionnez clairement le contexte TSA.";
            return new TsaRelevanceResult(false, message.isBlank() ? fallback : message);
        }
        if (scoreAutisme >= 0 && scoreAutisme < 60) {
            String fallback = "Le lien avec l'autisme/TSA est trop faible (" + scoreAutisme + "%). Précisez le contexte TSA.";
            return new TsaRelevanceResult(false, message.isBlank() ? fallback : message);
        }
        return new TsaRelevanceResult(true, "");
    }

    private static ModuleCategorie parseCategorie(String raw) {
        if (raw == null || raw.isBlank()) return ModuleCategorie.NON_DEFINI;
        String normalized = normalizeKey(raw);
        try {
            return ModuleCategorie.valueOf(normalized);
        } catch (Exception ignored) {
            // try matching by label-like content
        }
        String n = normalizeKey(raw).replace("_", " ");
        if (n.contains("COMPRENDRE") && n.contains("TSA")) return ModuleCategorie.COMPRENDRE_TSA;
        if (n.contains("AUTONOM")) return ModuleCategorie.AUTONOMIE;
        if (n.contains("COMMUNIC")) return ModuleCategorie.COMMUNICATION;
        if (n.contains("EMOTION")) return ModuleCategorie.EMOTIONS;
        if (n.contains("VIE") && n.contains("QUOTIDIEN")) return ModuleCategorie.VIE_QUOTIDIENNE;
        if (n.contains("ACCOMPAGN")) return ModuleCategorie.ACCOMPAGNEMENT;
        return ModuleCategorie.NON_DEFINI;
    }

    private static String normalizeKey(String s) {
        String out = s == null ? "" : s.trim();
        out = Normalizer.normalize(out, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        out = out.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        out = out.replaceAll("^_+|_+$", "");
        return out;
    }

    private static String extractExplicitTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) return "";
        String p = prompt.trim();

        Pattern[] patterns = new Pattern[] {
                Pattern.compile("(?i)(?:titre\\s*(?:du\\s*module)?\\s*(?:=|:|est|sera|soit)\\s*)([^\\n\\r\\.;]{2,120})"),
                Pattern.compile("(?i)(?:nom\\s*du\\s*module\\s*(?:=|:|est|sera|soit)\\s*)([^\\n\\r\\.;]{2,120})"),
                Pattern.compile("(?i)(?:intitul[ée]\\s*(?:du\\s*module)?\\s*(?:=|:|est|sera|soit)\\s*)([^\\n\\r\\.;]{2,120})")
        };

        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(p);
            if (m.find()) {
                String candidate = m.group(1);
                if (candidate != null) {
                    candidate = candidate.trim().replaceAll("\\s+", " ");
                    // coupe les connecteurs finaux fréquents
                    candidate = candidate.replaceAll("(?i)\\b(et|avec|pour|qui)\\s*$", "").trim();
                    if (candidate.length() >= 2) {
                        return candidate;
                    }
                }
            }
        }
        return "";
    }

    private static KeywordCategorySignal detectCategoryByKeywords(String prompt) {
        CategoryKeywordScores scores = scoreCategoriesByKeywords(prompt);

        ModuleCategorie best = ModuleCategorie.NON_DEFINI;
        int bestScore = 0;
        String reason = "";

        if (scores.tsa() > bestScore) { best = ModuleCategorie.COMPRENDRE_TSA; bestScore = scores.tsa(); reason = "TSA/autisme"; }
        if (scores.autonomie() > bestScore) { best = ModuleCategorie.AUTONOMIE; bestScore = scores.autonomie(); reason = "autonomie/faire seul"; }
        if (scores.communication() > bestScore) { best = ModuleCategorie.COMMUNICATION; bestScore = scores.communication(); reason = "communication/langage"; }
        if (scores.emotions() > bestScore) { best = ModuleCategorie.EMOTIONS; bestScore = scores.emotions(); reason = "émotions/sentiments"; }
        if (scores.vie() > bestScore) { best = ModuleCategorie.VIE_QUOTIDIENNE; bestScore = scores.vie(); reason = "vie quotidienne"; }
        if (scores.accompagnement() > bestScore) { best = ModuleCategorie.ACCOMPAGNEMENT; bestScore = scores.accompagnement(); reason = "accompagnement/famille/pro"; }

        return new KeywordCategorySignal(best, bestScore, reason);
    }

    private static CategoryKeywordScores scoreCategoriesByKeywords(String prompt) {
        String t = " " + normalizeKey(prompt) + " ";
        int tsa = 0;
        int autonomie = 0;
        int communication = 0;
        int emotions = 0;
        int vie = 0;
        int accompagnement = 0;

        // COMPRENDRE_TSA
        if (containsAny(t, " TSA ", " AUTISME ", " SPECTRE_AUTISTIQUE ", " DIAGNOSTIC ", " SIGNES_AUTISME ")) tsa += 2;
        if (containsAny(t, " TROUBLE_SPECTRE_AUTISTIQUE ", " COMPRENDRE_AUTISME ", " QU_EST_CE_QUE_TSA ")) tsa += 2;
        if (containsAny(t, " COMPRENDRE_TSA ", " EXPLIQUER_TSA ", " CARACTERISTIQUES_TSA ", " CAUSES_TSA ", " DIFFERENCES_TSA ")) tsa += 3;

        // AUTONOMIE
        if (containsAny(t, " AUTONOMIE ", " INDEPENDANCE ", " SE_DEBROUILLER ", " HABITUDES_AUTONOMES ")) autonomie += 2;
        if (containsAny(t, " FAIRE_SEUL ", " ROUTINE_AUTONOME ", " GESTION_TACHES ")) autonomie += 1;
        if (containsAny(t, " APPRENDRE_A_FAIRE_SEUL ", " HABILLAGE ", " TOILETTE ", " GESTION_DU_TEMPS ", " ORGANISER_SEUL ", " RESPONSABILITES_PERSONNELLES ")) autonomie += 3;

        // COMMUNICATION
        if (containsAny(t, " COMMUNICATION ", " LANGAGE ", " PARLER ", " EXPRESSION ", " PICTOGRAMME ", " PECS ")) communication += 2;
        if (containsAny(t, " CONVERSATION ", " COMPREHENSION_VERBALE ", " ECHOLALIE ")) communication += 1;
        if (containsAny(t, " EXPRIMER_SES_BESOINS ", " DEMANDER_DE_L_AIDE ", " INTERACTION_SOCIALE ", " CONTACT_VISUEL ", " COMMUNICATION_NON_VERBALE ")) communication += 3;

        // EMOTIONS
        if (containsAny(t, " EMOTION ", " EMOTIONS ", " SENTIMENT ", " PEUR ", " COLERE ", " STRESS ", " ANXIETE ", " FRUSTRATION ")) emotions += 2;
        if (containsAny(t, " CONFIANCE_EN_SOI ", " GESTION_EMOTIONNELLE ", " APAISER ", " CALMER ", " SECURITE_AFFECTIVE ", " SE_SENTIR_EN_SECURITE ", " SE_SENTIR ")) emotions += 1;
        if (containsAny(t, " GERER_SES_EMOTIONS ", " REGULATION_EMOTIONNELLE ", " CRISE_EMOTIONNELLE ", " COLERE_ET_FRUSTRATION ", " GERER_L_ANXIETE ", " IDENTIFIER_LES_EMOTIONS ")) emotions += 3;

        // VIE_QUOTIDIENNE
        if (containsAny(t, " VIE_QUOTIDIENNE ", " QUOTIDIEN ", " MAISON ", " ECOLE ", " SOMMEIL ", " REPAS ", " HYGIENE ", " SORTIE ")) vie += 2;
        if (containsAny(t, " HABITUDES_JOURNALIERES ", " ACTIVITES_JOURNALIERES ", " ORGANISATION_JOURNEE ")) vie += 1;
        if (containsAny(t, " ROUTINE_DU_QUOTIDIEN ", " ROUTINE_JOURNALIERE ", " TRANSITIONS_DE_LA_JOURNEE ", " ORGANISATION_DE_LA_MAISON ", " ACTIVITES_DE_LA_VIE_JOURNALIERE ")) vie += 3;

        // ACCOMPAGNEMENT
        if (containsAny(t, " ACCOMPAGNER ", " ACCOMPAGNEMENT ", " ACCOMPAGNE ")) accompagnement += 3;
        if (containsAny(t, " PARENT ", " FAMILLE ", " AIDANT ", " ACCOMPAGNEMENT ", " EDUCATRICE ", " PROFESSIONNEL ", " PSYCHOLOGUE ", " THERAPEUTE ", " ENSEIGNANT ")) accompagnement += 2;
        if (containsAny(t, " SOUTIEN_FAMILIAL ", " COORDINATION ", " SUIVI ", " GUIDER_LES_PARENTS ")) accompagnement += 1;
        if (containsAny(t, " SOUTENIR_LES_PARENTS ", " STRATEGIES_POUR_LES_AIDANTS ", " COLLABORATION_FAMILLE_ECOLE ", " PLAN_D_ACCOMPAGNEMENT ", " ROLE_DES_AIDANTS ")) accompagnement += 3;

        return new CategoryKeywordScores(tsa, autonomie, communication, emotions, vie, accompagnement);
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private static boolean containsAnyToken(String normalizedText, String... tokens) {
        if (normalizedText == null || normalizedText.isBlank()) return false;
        for (String token : tokens) {
            if (containsToken(normalizedText, token)) return true;
        }
        return false;
    }

    private static boolean containsToken(String normalizedText, String token) {
        if (token == null || token.isBlank()) return false;
        String t = token.trim().toUpperCase();
        if (normalizedText.equals(t)) return true;
        return normalizedText.startsWith(t + "_")
                || normalizedText.endsWith("_" + t)
                || normalizedText.contains("_" + t + "_");
    }

    private static boolean hasFiscalTsaConfusion(String titre, String description, String contenu) {
        String normalized = normalizeKey((titre != null ? titre : "") + " "
                + (description != null ? description : "") + " "
                + (contenu != null ? contenu : ""));
        return containsAnyToken(normalized,
                "TVA",
                "TAXE",
                "IMPOT",
                "IMPOTS",
                "FISCALITE",
                "VALEUR_AJOUTEE",
                "TAXE_SUR_LA_VALEUR_AJOUTEE",
                "FISCAL");
    }

    private static String resolveApiKey() {
        String env = System.getenv("GROQ_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = LocalAiPropertiesFile.readProperty("groq.api.key");
        return fromFile != null && !fromFile.isBlank() ? fromFile : null;
    }

    private record KeywordCategorySignal(ModuleCategorie category, int score, String reason) {}
    private record CategoryKeywordScores(int tsa, int autonomie, int communication, int emotions, int vie, int accompagnement) {
        int scoreFor(ModuleCategorie category) {
            if (category == null) return 0;
            return switch (category) {
                case COMPRENDRE_TSA -> tsa;
                case AUTONOMIE -> autonomie;
                case COMMUNICATION -> communication;
                case EMOTIONS -> emotions;
                case VIE_QUOTIDIENNE -> vie;
                case ACCOMPAGNEMENT -> accompagnement;
                default -> 0;
            };
        }
    }
    public record TsaRelevanceResult(boolean relevant, String message) {}
    public record PromptCategoryAlignmentResult(boolean aligned, ModuleCategorie detectedCategory, String message) {}
}
