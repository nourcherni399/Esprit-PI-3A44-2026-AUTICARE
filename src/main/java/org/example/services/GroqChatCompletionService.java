package org.example.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Appels à l’API <a href="https://console.groq.com/docs/overview">Groq</a> (format OpenAI chat completions).
 * <p>
 * Clé API : {@code -Dgroq.api.key=…} &gt; {@code GROQ_API_KEY} / {@code CHAT_API_KEY} (variables d’environnement)
 * &gt; {@link AssistantSecretsLoader} ({@code assistant-local.properties}, {@code .env.local}, etc.)
 * &gt; {@code application.properties} {@code groq.api.key}.
 * Modèle : {@code -Dgroq.model=…} &gt; {@code GROQ_MODEL} / {@code CHAT_MODEL} &gt; {@code groq.model} &gt; défaut {@code llama-3.3-70b-versatile}.
 */
public final class GroqChatCompletionService {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private static final String SYSTEM_PROMPT_DEMANDE = """
        Tu es l’assistant produit d’AutiCare (plateforme autisme / TDAH / accompagnement).
        Tu aides parents, proches ou patients à rédiger une demande de produit (matériel, jeu, outil…) ou à préparer une fiche produit.

        Compréhension des messages (obligatoire) :
        — Avant de répondre, déduis l’intention réelle : description, prix, catégorie, comparatif, liste de besoins, message court ou flou, etc.
        — Si le texte contient des fautes, du langage oral, des abréviations ou plusieurs questions en une fois, interprète de façon raisonnable et traite chaque point demandé.
        — Ne réponds pas à côté : si l’utilisateur demande X, commence par répondre à X ; si quelque chose manque, complète avec des hypothèses prudentes indiquées comme telles.
        — Si l’intention est trop vague pour être utile, pose au plus 2 questions très courtes et ciblées, puis propose déjà une première piste.

        Réponses : dans la langue de l’utilisateur (darija/arabe/français/anglais), ton bienveillant et clair.
        Tu peux proposer : un texte de description structuré (titre court + paragraphes + puces si utile),
        des mots-clés pour chercher des photos sur Unsplash ou autre banque (liste courte, français ou anglais),
        et une comparaison de prix indicative : cite des exemples « type Google Shopping / comparateurs » en dinars tunisiens (DT)
        sous forme de puces, en rappelant explicitement qu’il s’agit d’estimations non contractuelles (pas de prix officiel).
        N’invente pas de données médicales ; ne demande pas d’informations de santé sensibles.
        Reste concis sauf si l’utilisateur demande plus de détail.
        """;

    private static final String SYSTEM_PROMPT_CREATION_FICHE = """
        Tu es l’assistant AutiCare pour rédiger une **fiche produit** (matériel, jeu, outil d’accompagnement autisme / TDAH / quotidien).

        **Flux à respecter (très important) :**

        1) Quand l’utilisateur donne surtout un **nom de produit** (même court ou imprécis), réponds d’abord par une **description de fiche** structurée et prête à être publiée ou adaptée :
           — **Nom affiché** (tu peux légèrement reformuler pour la clarté si besoin) ;
           — **1 à 2 paragraphes** pour le public (bienveillant, sans promesse thérapeutique ni diagnostic) ;
           — **3 à 6 puces** : public visé, usages possibles, caractéristiques plausibles (matière, dimensions indicatives seulement si tu les déduis raisonnablement, sinon « à préciser ») ;
           — **Catégorie** suggérée parmi les familles habituelles du site (sensoriel, concentration, communication, etc.) si pertinent.
           Termine **obligatoirement** par cette invitation explicite (adapte légèrement la formulation si besoin, mais garde le sens) :
           « **Cette description vous convient-elle ?** Répondez par **oui** pour la valider, ou dites **non** / précisez ce que vous voulez changer (ton, longueur, public, usage, détails manquants…). »

        2) Si l’utilisateur répond par **oui**, **ok**, **parfait**, **ça marche**, **validé** (ou équivalent clair) : confirme en une phrase, **résume en 3–5 lignes** la description retenue, puis propose la suite utile (prix indicatif en DT avec rappel « estimation », mots-clés pour une image, ou autre point restant dans la conversation).

        3) Si l’utilisateur dit **non**, **pas tout à fait**, ou donne des **corrections** : **réécris une nouvelle version complète** de la description en tenant compte de ses retours, puis **repose la même question** de validation qu’en (1).

        4) Si le message contient à la fois un nom et des contraintes (âge, budget, contexte) : intègre tout dans la proposition, puis validation comme en (1).

        Compréhension : tolère fautes, abréviations et langage oral. Ne réponds pas à côté du dernier message.
        Réponds dans la langue de l’utilisateur (darija/arabe/français/anglais). Pas de données de santé sensibles.
        Reste concis sauf si l’utilisateur demande plus de détail.
        """;

    private static final String SYSTEM_PROMPT_CATALOGUE = """
        Tu es l’assistant AutiCare sur le catalogue produits en ligne (outils sensoriels, concentration, quotidien, TDAH / autisme).
        L’utilisateur parcourt les articles publiés par l’équipe.

        Compréhension : identifie ce qu’il cherche vraiment (usage, âge, budget, comparaison, définition d’un terme…).
        Tolère fautes et formulations courtes ; si plusieurs questions sont posées, réponds dans l’ordre ou en regroupant clairement.

        Aide-le à :
        — choisir ou comparer des types de produits selon un besoin (sans promesse thérapeutique) ;
        — formuler des critères de recherche ou des mots-clés adaptés aux catégories du site ;
        — expliquer en langage simple à quoi peut servir une famille de produits ;
        — proposer une fourchette de prix indicative en DT pour un produit comparable hors catalogue (toujours « estimation », jamais prix officiel) ;
        — suggérer des mots-clés pour des images de référence (Unsplash) si on cherche une ambiance visuelle.
        Règles : pas de diagnostic, pas d’avis médical, pas de données personnelles de santé.
        Réponds dans la langue de l’utilisateur (darija/arabe/français/anglais), ton rassurant.
        Réponses plutôt courtes (quelques paragraphes max) sauf si l’utilisateur demande le détail.
        """;

    private static final String SYSTEM_PROMPT_UNIVERSAL = """
        Tu es l'assistant IA général d'AutiCare.
        Tu dois comprendre et répondre à presque toutes les questions de l'utilisateur, comme un assistant conversationnel moderne.

        Règles de compréhension (obligatoire) :
        - Comprends l'intention réelle même avec fautes, langage familier, abréviations, ou phrases courtes.
        - Si la question est générale (études, rédaction, explication, code, idées, calcul, comparaison...), réponds directement.
        - Si la question concerne un produit/auticare, donne une réponse orientée action et utile pour l'application.
        - Si l'information manque, pose au plus 1 à 2 questions ciblées puis donne une première réponse utile.
        - Ne reste pas bloqué sur le domaine "produit" : accepte les questions hors produit.

        Style de réponse :
        - Même langue que l'utilisateur (français / arabe / darija / mixte).
        - Réponse claire, structurée, concise par défaut.
        - Donne des exemples concrets quand utile.
        - Si l'utilisateur demande "fais-le", propose une action directement exploitable.

        Sécurité :
        - N'invente pas des faits sensibles ou médicaux.
        - Pas de diagnostic médical, pas de conseils dangereux.
        """;

    private static final Properties FILE_CONFIG = loadFileConfig();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public GroqChatCompletionService() {
    }

    public static boolean hasApiKeyConfigured() {
        return !resolveApiKey().isBlank();
    }

    private static String resolveApiKey() {
        String p = System.getProperty("groq.api.key");
        if (p != null && !p.isBlank()) {
            return p.trim();
        }
        String e = System.getenv("GROQ_API_KEY");
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        String chatEnv = System.getenv("CHAT_API_KEY");
        if (chatEnv != null && !chatEnv.isBlank()) {
            return chatEnv.trim();
        }
        String local = firstNonBlank(
            AssistantSecretsLoader.get("groq.api.key"),
            AssistantSecretsLoader.get("GROQ_API_KEY"),
            AssistantSecretsLoader.get("CHAT_API_KEY")
        );
        if (local != null && !local.isBlank()) {
            return local.trim();
        }
        String f = FILE_CONFIG.getProperty("groq.api.key");
        if (f != null && !f.isBlank()) {
            return f.trim();
        }
        return "";
    }

    private static String resolveModel() {
        String p = System.getProperty("groq.model");
        if (p != null && !p.isBlank()) {
            return p.trim();
        }
        String e = System.getenv("GROQ_MODEL");
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        String chatModel = System.getenv("CHAT_MODEL");
        if (chatModel != null && !chatModel.isBlank()) {
            return chatModel.trim();
        }
        String localModel = firstNonBlank(
            AssistantSecretsLoader.get("groq.model"),
            AssistantSecretsLoader.get("GROQ_MODEL"),
            AssistantSecretsLoader.get("CHAT_MODEL")
        );
        if (localModel != null && !localModel.isBlank()) {
            return localModel.trim();
        }
        String f = FILE_CONFIG.getProperty("groq.model");
        if (f != null && !f.isBlank()) {
            return f.trim();
        }
        return DEFAULT_MODEL;
    }

    private static Properties loadFileConfig() {
        Properties props = new Properties();
        try (InputStream in = GroqChatCompletionService.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // —
        }
        return props;
    }

    private static String systemPrompt(GroqAssistantContext ctx) {
        if (ctx == GroqAssistantContext.CATALOGUE_BOUTIQUE) {
            return SYSTEM_PROMPT_CATALOGUE;
        }
        if (ctx == GroqAssistantContext.UNIVERSAL_ASSISTANT) {
            return SYSTEM_PROMPT_UNIVERSAL;
        }
        if (ctx == GroqAssistantContext.CREATION_FICHE_PRODUIT) {
            return SYSTEM_PROMPT_CREATION_FICHE;
        }
        return SYSTEM_PROMPT_DEMANDE;
    }

    /**
     * @param history tours user/assistant déjà échangés (sans message système)
     */
    public String complete(List<GroqChatMessage> history) throws IOException, InterruptedException {
        return complete(history, GroqAssistantContext.DEMANDE_PRODUIT);
    }

    /**
     * @param history tours user/assistant déjà échangés (sans message système)
     * @param context   prompt système adapté (demande produit vs catalogue)
     */
    public String complete(List<GroqChatMessage> history, GroqAssistantContext context) throws IOException, InterruptedException {
        if (context == GroqAssistantContext.UNIVERSAL_ASSISTANT) {
            return completeWithSystem(history, systemPrompt(context), 0.55, 0.9, 3072);
        }
        return completeWithSystem(history, systemPrompt(context), 0.72, 0.92, 2048);
    }

    /**
     * Appel chat avec prompt système explicite (ex. classification courte).
     *
     * @param history       tours user/assistant (sans message système)
     * @param systemPrompt  contenu du message système
     * @param temperature   température du modèle
     * @param topP          top_p du modèle
     * @param maxTokens     plafond de tokens générés
     */
    public String completeWithSystem(
        List<GroqChatMessage> history,
        String systemPrompt,
        double temperature,
        double topP,
        int maxTokens
    ) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Clé API Groq absente : fichier assistant-local.properties (groq.api.key), "
                    + "variables GROQ_API_KEY / CHAT_API_KEY, ou -Dgroq.api.key=…");
        }
        String model = resolveModel();
        String sys = systemPrompt == null ? "" : systemPrompt.trim();
        if (sys.isEmpty()) {
            throw new IOException("Prompt système vide.");
        }
        String langInstruction = buildLanguageInstruction(history);
        if (!langInstruction.isBlank()) {
            sys = sys + "\n\n" + langInstruction;
        }

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", sys);
        messages.add(system);
        for (GroqChatMessage turn : history) {
            String role = turn.role() == null ? "user" : turn.role().toLowerCase(Locale.ROOT);
            if (!"user".equals(role) && !"assistant".equals(role)) {
                role = "user";
            }
            JsonObject o = new JsonObject();
            o.addProperty("role", role);
            o.addProperty("content", turn.content() == null ? "" : turn.content());
            messages.add(o);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", temperature);
        body.addProperty("top_p", topP);
        body.addProperty("max_tokens", maxTokens);

        String json = body.toString();
        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        String respBody = resp.body();
        if (code < 200 || code >= 300) {
            String err = parseGroqError(respBody);
            throw new IOException("Groq HTTP " + code + (err.isEmpty() ? "" : " — " + err));
        }
        return extractAssistantText(respBody);
    }

    private static String parseGroqError(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject()) {
                JsonObject err = root.getAsJsonObject("error");
                if (err.has("message")) {
                    return err.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
            // —
        }
        return json.length() > 200 ? json.substring(0, 200) + "…" : json;
    }

    private static String extractAssistantText(String jsonBody) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            var choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("Réponse Groq sans choix.");
            }
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject message = first.getAsJsonObject("message");
            return message.get("content").getAsString();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Réponse Groq inattendue : " + e.getMessage(), e);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String buildLanguageInstruction(List<GroqChatMessage> history) {
        String lastUser = "";
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                GroqChatMessage msg = history.get(i);
                if (msg != null && "user".equalsIgnoreCase(msg.role()) && msg.content() != null && !msg.content().isBlank()) {
                    lastUser = msg.content();
                    break;
                }
            }
        }
        if (lastUser.isBlank()) {
            return "Réponds dans la même langue que l'utilisateur.";
        }
        if (containsArabicScript(lastUser)) {
            return "Langue de réponse obligatoire: arabe (ou darija naturelle si le message est en darija).";
        }
        if (looksEnglish(lastUser)) {
            return "Response language is mandatory: English.";
        }
        return "Langue de réponse obligatoire: français (ou darija si l'utilisateur mélange darija/français).";
    }

    private static boolean containsArabicScript(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.ARABIC
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
                || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                || block == Character.UnicodeBlock.ARABIC_EXTENDED_A) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksEnglish(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text.toLowerCase(Locale.ROOT);
        return s.matches(".*\\b(the|and|is|are|how|what|why|please|can you|i want|help me)\\b.*");
    }

    /** Copie défensive pour limiter la taille du contexte envoyé à l’API. */
    public static List<GroqChatMessage> trimHistory(List<GroqChatMessage> full, int maxTurns) {
        if (full.size() <= maxTurns) {
            return new ArrayList<>(full);
        }
        return new ArrayList<>(full.subList(full.size() - maxTurns, full.size()));
    }

    /**
     * Normalise le texte utilisateur pour l’API (espaces, retours à la ligne) sans changer le sens.
     * Limite la longueur pour éviter les envois accidentels énormes.
     */
    public static String normalizeUserChatContent(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        s = s.replace('\r', '\n');
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        final int max = 12000;
        if (s.length() > max) {
            s = s.substring(0, max).trim() + "…";
        }
        return s;
    }
}
