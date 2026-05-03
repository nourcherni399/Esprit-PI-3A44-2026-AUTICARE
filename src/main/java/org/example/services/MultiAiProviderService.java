package org.example.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Passerelle IA multi-fournisseurs : OpenAI, Groq, Gemini, Hugging Face + recherche image Pexels.
 * <p>Configuration via {@code application.properties} (ou variables d'environnement).</p>
 */
public final class MultiAiProviderService {
    /**
     * Bases URL inférence image HF : le routeur remplace progressivement api-inference ;
     * certains modèles ne répondent que sur l'un des deux.
     */
    private static final String[] HF_IMAGE_INFERENCE_BASES = {
            "https://api-inference.huggingface.co/models/",
            "https://router.huggingface.co/hf-inference/models/"
    };
    private static final String[] HF_IMAGE_MODEL_CANDIDATES = {
            "runwayml/stable-diffusion-v1-5",
            "stabilityai/stable-diffusion-2-1-base",
            "CompVis/stable-diffusion-v1-4",
            "stabilityai/stable-diffusion-xl-base-1.0",
            "stabilityai/sdxl-turbo",
            "black-forest-labs/FLUX.1-schnell"
    };
    private static final Map<String, Boolean> HF_INFERENCE_MODEL_SUPPORT_CACHE = new HashMap<>();

    public record ImageGenResult(String dataUrl, String providerUsed, String error) {}
    public record RdvPlannerCandidate(int availabilityId,
                                      int doctorId,
                                      String doctorName,
                                      String specialty,
                                      String cabinet,
                                      String address,
                                      String startIso) {}
    public record AiRdvRankResult(List<Integer> availabilityIds, String shortSummary, String error) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are an intelligent, patient, and supportive AI assistant.\n"
                    + "\n"
                    + "Your mission:\n"
                    + "- Understand any user request, even if unclear or short\n"
                    + "- Support neurodivergent users, including people with Autism (ASD)\n"
                    + "- Communicate in a calm, structured, and predictable way\n"
                    + "\n"
                    + "Core abilities:\n"
                    + "- Understand mixed languages (French, Arabic, English)\n"
                    + "- Detect user intent accurately\n"
                    + "- Correct spelling mistakes automatically\n"
                    + "- Adapt to any domain (medical, admin, daily life, technical)\n"
                    + "\n"
                    + "Communication style (VERY IMPORTANT):\n"
                    + "- Use simple, clear, and direct sentences\n"
                    + "- Avoid ambiguity and complex expressions\n"
                    + "- Be calm, neutral, and reassuring\n"
                    + "- Structure answers step-by-step when possible\n"
                    + "- Avoid too much information at once\n"
                    + "- Do NOT overwhelm the user\n"
                    + "\n"
                    + "Behavior rules:\n"
                    + "- If the request is clear -> give a direct answer\n"
                    + "- If the request is unclear -> ask ONE simple clarification question\n"
                    + "- If the user seems confused -> simplify your explanation\n"
                    + "- Always be respectful and non-judgmental\n"
                    + "\n"
                    + "Understanding rules:\n"
                    + "- Interpret incomplete or imperfect sentences\n"
                    + "- Infer meaning from context\n"
                    + "- Handle sensory or emotional sensitivity carefully\n"
                    + "\n"
                    + "Response format:\n"
                    + "1. Short understanding (optional)\n"
                    + "2. Clear answer\n"
                    + "3. Optional simple question (only if needed)\n"
                    + "\n"
                    + "Examples:\n"
                    + "\n"
                    + "User: nheb rdv\n"
                    + "Assistant: Vous souhaitez un rendez-vous.\n"
                    + "A quelle date ?\n"
                    + "\n"
                    + "User: je comprend pas java\n"
                    + "Assistant: Vous voulez comprendre Java.\n"
                    + "Java est un langage de programmation.\n"
                    + "Voulez-vous un exemple simple ?\n"
                    + "\n"
                    + "User: ana m3a9ed\n"
                    + "Assistant: Je comprends que c'est difficile.\n"
                    + "Pouvez-vous me dire ce qui vous pose probleme exactement ?\n";

    public String chatRdv(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "";
        }
        String provider = cfg("ai.provider", "AI_PROVIDER", "groq").trim().toLowerCase(Locale.ROOT);
        try {
            return switch (provider) {
                case "groq" -> groqChat(DEFAULT_SYSTEM_PROMPT, userPrompt);
                case "gemini" -> geminiChat(DEFAULT_SYSTEM_PROMPT, userPrompt);
                case "huggingface", "hf" -> huggingFaceTextGen(DEFAULT_SYSTEM_PROMPT + "\n\nQuestion: " + userPrompt);
                default -> "Provider IA non reconnu (" + provider + "). Utilisez groq, gemini ou huggingface.";
            };
        } catch (Exception e) {
            return "Erreur API IA: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * Classe des créneaux de RDV via API IA et retourne un ordre d'IDs de disponibilités.
     * Réponse attendue: JSON strict {"availability_ids":[...], "short_summary":"..."}.
     */
    public AiRdvRankResult rankRdvCandidates(List<RdvPlannerCandidate> candidates,
                                             String desiredIso,
                                             String preferredSpecialty,
                                             String preferredLieu) {
        if (candidates == null || candidates.isEmpty()) {
            return new AiRdvRankResult(List.of(), "", "Aucun candidat.");
        }
        String prompt = buildRdvRankingPrompt(candidates, desiredIso, preferredSpecialty, preferredLieu);
        String raw = chatRdv(prompt);
        if (raw == null || raw.isBlank()) {
            return new AiRdvRankResult(List.of(), "", "Réponse IA vide.");
        }
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.startsWith("clé ")
                || low.startsWith("provider ia non reconnu")
                || low.startsWith("erreur api ia:")
                || low.startsWith("http ")
                || low.contains("\"invalid_api_key\"")
                || low.contains("\"error\"")
                || low.contains("api key")
                || low.contains("unauthorized")
                || low.contains("forbidden")) {
            return new AiRdvRankResult(List.of(), "", truncate(raw, 160));
        }
        List<Integer> ids = parseAvailabilityIds(raw);
        String summary = parseShortSummary(raw);
        return new AiRdvRankResult(ids, summary, "");
    }

    /**
     * Génère une image de type tache symétrique (style projectif) via API.
     * Retourne une data URL (data:image/png;base64,...) ou vide en cas d'échec.
     */
    public String generateInkblotImageDataUrl(String prompt, int seedIndex) {
        String provider = cfg("ai.imageProvider", "AI_IMAGE_PROVIDER", "huggingface").trim().toLowerCase(Locale.ROOT);
        try {
            return switch (provider) {
                case "huggingface", "hf" -> huggingFaceGenerateImageDataUrl(prompt, seedIndex);
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Génère une image via provider configuré.
     * En mode actuel: HuggingFace uniquement (sans fallback Pexels).
     */
    public String generateInkblotImageDataUrlWithIaFallback(String prompt, int seedIndex) {
        String first = generateInkblotImageDataUrl(prompt, seedIndex);
        if (first != null && !first.isBlank()) {
            return first;
        }
        return "";
    }

    /**
     * Même logique que generateInkblotImageDataUrlWithIaFallback, mais retourne aussi la cause d'échec.
     */
    public ImageGenResult generateInkblotImageWithDebug(String prompt, int seedIndex) {
        String preferred = cfg("ai.imageProvider", "AI_IMAGE_PROVIDER", "huggingface").trim().toLowerCase(Locale.ROOT);
        if ("huggingface".equals(preferred) || "hf".equals(preferred)) {
            ImageGenResult hf = tryHfImageWithDebug(prompt, seedIndex);
            if (hf.dataUrl() != null && !hf.dataUrl().isBlank()) {
                return hf;
            }
            return new ImageGenResult("", "", "HF: " + hf.error());
        } else {
            ImageGenResult hf = tryHfImageWithDebug(prompt, seedIndex);
            if (hf.dataUrl() != null && !hf.dataUrl().isBlank()) {
                return hf;
            }
            return new ImageGenResult("", "", "HF: " + hf.error());
        }
    }

    /**
     * Génération image demandée en mode HuggingFace uniquement.
     */
    public ImageGenResult generateInkblotImageWithHfPexelsDebug(String prompt, int seedIndex) {
        ImageGenResult hf = tryHfImageWithDebug(prompt, seedIndex);
        if (hf.dataUrl() != null && !hf.dataUrl().isBlank()) {
            return hf;
        }
        return new ImageGenResult("", "", "HF: " + hf.error());
    }

    /** Recherche la 1ère image Pexels pour une requête (URL publique). */
    public String firstPexelsImageUrl(String query) {
        String apiKey = cfg("pexels.apiKey", "PEXELS_API_KEY", "").trim();
        if (apiKey.isEmpty() || query == null || query.isBlank()) {
            return firstPexelsCuratedUrl(apiKey);
        }
        try {
            String[] queries = new String[] {
                    query.trim(),
                    "inkblot abstract black white",
                    "abstract symmetry black white",
                    "ink texture abstract"
            };
            for (String q : queries) {
                String url = "https://api.pexels.com/v1/search?per_page=5&query=" + enc(q);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", apiKey)
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 != 2) {
                    continue;
                }
                String medium = jsonValue(resp.body(), "medium");
                if (medium != null && !medium.isBlank()) {
                    return medium;
                }
                String original = jsonValue(resp.body(), "original");
                if (original != null && !original.isBlank()) {
                    return original;
                }
            }
            return firstPexelsCuratedUrl(apiKey);
        } catch (Exception ignored) {
            return firstPexelsCuratedUrl(apiKey);
        }
    }

    private String firstPexelsCuratedUrl(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        try {
            String url = "https://api.pexels.com/v1/curated?per_page=5";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                return "";
            }
            String medium = jsonValue(resp.body(), "medium");
            if (medium != null && !medium.isBlank()) {
                return medium;
            }
            String original = jsonValue(resp.body(), "original");
            return original != null ? original : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Cherche une image sur Pexels puis la convertit en data URL utilisable directement par JavaFX.
     */
    public String pexelsImageDataUrl(String query) {
        String url = firstPexelsImageUrl(query);
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                return "";
            }
            String contentType = resp.headers().firstValue("content-type").orElse("image/jpeg");
            if (!contentType.startsWith("image/")) {
                return "";
            }
            String b64 = Base64.getEncoder().encodeToString(resp.body());
            return "data:" + contentType + ";base64," + b64;
        } catch (Exception e) {
            return "";
        }
    }

    private String openAiChat(String systemPrompt, String userPrompt) throws Exception {
        String key = cfg("openai.apiKey", "OPENAI_API_KEY", "").trim();
        if (key.isEmpty()) {
            return "Clé OpenAI manquante (openai.apiKey / OPENAI_API_KEY).";
        }
        String model = cfg("openai.model", "OPENAI_MODEL", "gpt-4o-mini").trim();
        String payload = "{"
                + "\"model\":\"" + j(model) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + j(systemPrompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + j(userPrompt) + "\"}"
                + "],"
                + "\"temperature\":0.4"
                + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseCommonChatResponse(resp);
    }

    private String groqChat(String systemPrompt, String userPrompt) throws Exception {
        String key = cfg("groq.apiKey", "GROQ_API_KEY", "").trim();
        if (key.isEmpty()) {
            return "Clé Groq manquante (groq.apiKey / GROQ_API_KEY).";
        }
        String model = cfg("groq.model", "GROQ_MODEL", "llama-3.1-8b-instant").trim();
        String payload = "{"
                + "\"model\":\"" + j(model) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + j(systemPrompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + j(userPrompt) + "\"}"
                + "],"
                + "\"temperature\":0.4"
                + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseCommonChatResponse(resp);
    }

    private String geminiChat(String systemPrompt, String userPrompt) throws Exception {
        String key = cfg("gemini.apiKey", "GEMINI_API_KEY", "").trim();
        if (key.isEmpty()) {
            return "Clé Gemini manquante (gemini.apiKey / GEMINI_API_KEY).";
        }
        String model = cfg("gemini.model", "GEMINI_MODEL", "gemini-1.5-flash").trim();
        String payload = "{"
                + "\"contents\":["
                + "{\"parts\":[{\"text\":\"" + j(systemPrompt + "\n\nQuestion utilisateur: " + userPrompt) + "\"}]}"
                + "]"
                + "}";
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + enc(model)
                + ":generateContent?key=" + enc(key);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            return "Gemini HTTP " + resp.statusCode() + " : " + truncate(resp.body(), 320);
        }
        String txt = jsonValue(resp.body(), "text");
        return txt != null && !txt.isBlank() ? txt : "Réponse Gemini vide.";
    }

    private String huggingFaceTextGen(String prompt) throws Exception {
        String key = cfg("huggingface.apiKey", "HUGGINGFACE_API_KEY", "").trim();
        if (key.isEmpty()) {
            return "Clé Hugging Face manquante (huggingface.apiKey / HUGGINGFACE_API_KEY).";
        }
        String model = cfg("huggingface.model", "HUGGINGFACE_MODEL", "google/flan-t5-large").trim();
        String url = "https://api-inference.huggingface.co/models/" + encPath(model);
        String payload = "{"
                + "\"inputs\":\"" + j(prompt) + "\","
                + "\"parameters\":{\"max_new_tokens\":180,\"temperature\":0.4}"
                + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            return "Hugging Face HTTP " + resp.statusCode() + " : " + truncate(resp.body(), 320);
        }
        String text = jsonValue(resp.body(), "generated_text");
        if (text == null || text.isBlank()) {
            text = jsonValue(resp.body(), "summary_text");
        }
        return text != null && !text.isBlank() ? text : "Réponse Hugging Face vide.";
    }

    private String parseCommonChatResponse(HttpResponse<String> resp) {
        if (resp.statusCode() / 100 != 2) {
            return "HTTP " + resp.statusCode() + " : " + truncate(resp.body(), 320);
        }
        String txt = jsonValue(resp.body(), "content");
        return txt != null && !txt.isBlank() ? txt : "Réponse IA vide.";
    }

    private static String cfg(String prop, String env, String fallback) {
        return SmtpMailUtil.readConfig(prop, env, fallback);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** Encodage pour segment de chemin (garde '/'). */
    private static String encPath(String s) {
        if (s == null) {
            return "";
        }
        String[] parts = s.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(enc(parts[i]));
        }
        return out.toString();
    }

    private static String j(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String jsonValue(String json, String key) {
        if (json == null || key == null || key.isBlank()) {
            return null;
        }
        String src = json;
        String token = "\"" + key + "\"";
        int from = 0;
        while (from >= 0 && from < src.length()) {
            int k = src.indexOf(token, from);
            if (k < 0) {
                return null;
            }
            int i = k + token.length();
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
                i++;
            }
            if (i >= src.length() || src.charAt(i) != ':') {
                from = k + token.length();
                continue;
            }
            i++;
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
                i++;
            }
            if (i >= src.length() || src.charAt(i) != '"') {
                from = k + token.length();
                continue;
            }
            i++;
            StringBuilder out = new StringBuilder();
            boolean escaped = false;
            for (; i < src.length(); i++) {
                char c = src.charAt(i);
                if (escaped) {
                    switch (c) {
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case '"' -> out.append('"');
                        case '/' -> out.append('/');
                        case '\\' -> out.append('\\');
                        default -> out.append(c);
                    }
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    return out.toString();
                }
                out.append(c);
            }
            return out.toString();
        }
        return null;
    }

    private static String jsonNumberValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
        var m = p.matcher(json != null ? json : "");
        if (!m.find()) {
            return null;
        }
        return m.group(1);
    }

    /**
     * Certains pipelines HF renvoient du JSON (ex. image en base64) au lieu d'octets bruts image/*.
     */
    private static String tryExtractImageDataUrlFromHfJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String t = body.trim();
        if (!t.startsWith("{") && !t.startsWith("[")) {
            return null;
        }
        Pattern dataUrl = Pattern.compile("data:image/[a-zA-Z0-9+.-]+;base64,[A-Za-z0-9+/=]+");
        var dm = dataUrl.matcher(t);
        if (dm.find()) {
            return dm.group();
        }
        String b64 = jsonValue(t, "image");
        if (b64 != null && !b64.isBlank() && b64.length() > 80) {
            return "data:image/png;base64," + b64.trim();
        }
        String b64Image = jsonValue(t, "image_base64");
        if (b64Image != null && !b64Image.isBlank() && b64Image.length() > 80) {
            return "data:image/png;base64," + b64Image.trim();
        }
        String b64Artifact = jsonValue(t, "base64");
        if (b64Artifact != null && !b64Artifact.isBlank() && b64Artifact.length() > 80) {
            return "data:image/png;base64," + b64Artifact.trim();
        }
        String imageUrl = jsonValue(t, "url");
        if (imageUrl != null && !imageUrl.isBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            return imageUrl.trim();
        }
        String imageUrlAlt = jsonValue(t, "image_url");
        if (imageUrlAlt != null && !imageUrlAlt.isBlank()
                && (imageUrlAlt.startsWith("http://") || imageUrlAlt.startsWith("https://"))) {
            return imageUrlAlt.trim();
        }
        return null;
    }

    private static String sanitizeMimeType(String raw, String fallback) {
        String ct = raw != null ? raw.trim() : "";
        if (ct.isBlank()) {
            return fallback;
        }
        int semi = ct.indexOf(';');
        if (semi > 0) {
            ct = ct.substring(0, semi).trim();
        }
        if (!ct.startsWith("image/")) {
            return fallback;
        }
        return ct;
    }

    private static String detectImageMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return "image/gif";
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String buildRdvRankingPrompt(List<RdvPlannerCandidate> candidates,
                                                String desiredIso,
                                                String preferredSpecialty,
                                                String preferredLieu) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu classes des créneaux de rendez-vous médicaux.\n");
        sb.append("Objectif: proposer les meilleurs créneaux proches de la préférence patient.\n");
        sb.append("Préférence date/heure: ").append(desiredIso != null ? desiredIso : "").append("\n");
        sb.append("Spécialité souhaitée: ").append(preferredSpecialty != null ? preferredSpecialty : "").append("\n");
        sb.append("Lieu souhaité: ").append(preferredLieu != null ? preferredLieu : "").append("\n");
        sb.append("Priorités: 1) proximité date/heure, 2) même jour, 3) créneaux après l'heure demandée, ")
                .append("4) spécialité, 5) lieu.\n");
        sb.append("Retourne UNIQUEMENT un JSON strict sans markdown, format:\n");
        sb.append("{\"availability_ids\":[id1,id2,id3,id4,id5],\"short_summary\":\"phrase courte max 110 caractères\"}\n");
        sb.append("Candidats:\n");
        for (RdvPlannerCandidate c : candidates) {
            sb.append("- {")
                    .append("\"availability_id\":").append(c.availabilityId()).append(",")
                    .append("\"doctor_id\":").append(c.doctorId()).append(",")
                    .append("\"doctor_name\":\"").append(j(c.doctorName())).append("\",")
                    .append("\"specialty\":\"").append(j(c.specialty())).append("\",")
                    .append("\"cabinet\":\"").append(j(c.cabinet())).append("\",")
                    .append("\"address\":\"").append(j(c.address())).append("\",")
                    .append("\"start\":\"").append(j(c.startIso())).append("\"")
                    .append("}\n");
        }
        return sb.toString();
    }

    private static List<Integer> parseAvailabilityIds(String raw) {
        String body = raw == null ? "" : raw;
        Pattern keyed = Pattern.compile("\"availability_ids\"\\s*:\\s*\\[([^\\]]*)\\]");
        var keyedMatch = keyed.matcher(body);
        String arrayBody = null;
        if (keyedMatch.find()) {
            arrayBody = keyedMatch.group(1);
        } else {
            Pattern anyArray = Pattern.compile("\\[([^\\]]*)\\]");
            var any = anyArray.matcher(body);
            if (any.find()) {
                arrayBody = any.group(1);
            }
        }
        if (arrayBody == null || arrayBody.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        Pattern n = Pattern.compile("\\d+");
        var m = n.matcher(arrayBody);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group()));
            } catch (Exception ignored) {
                // ignore
            }
        }
        return new ArrayList<>(out);
    }

    private static String parseShortSummary(String raw) {
        String s = jsonValue(raw, "short_summary");
        if (s == null || s.isBlank()) {
            s = raw != null ? raw.trim() : "";
        }
        String oneLine = s.replace("\n", " ").replace("\r", " ").trim();
        int dot = oneLine.indexOf('.');
        String shortText = dot > 0 ? oneLine.substring(0, dot + 1).trim() : oneLine;
        if (shortText.length() > 110) {
            shortText = shortText.substring(0, 107).trim() + "...";
        }
        return shortText;
    }

    private String openAiGenerateImageDataUrl(String prompt) throws Exception {
        String key = cfg("openai.apiKey", "OPENAI_API_KEY", "").trim();
        if (key.isEmpty()) {
            return "";
        }
        String model = cfg("openai.imageModel", "OPENAI_IMAGE_MODEL", "gpt-image-1").trim();
        String payload = "{"
                + "\"model\":\"" + j(model) + "\","
                + "\"prompt\":\"" + j(prompt) + "\","
                + "\"size\":\"512x512\","
                + "\"quality\":\"low\","
                + "\"response_format\":\"b64_json\""
                + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/images/generations"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            return "";
        }
        String b64 = jsonValue(resp.body(), "b64_json");
        if (b64 == null || b64.isBlank()) {
            return "";
        }
        return "data:image/png;base64," + b64.trim();
    }

    private ImageGenResult tryOpenAiImageWithDebug(String prompt) {
        try {
            String key = cfg("openai.apiKey", "OPENAI_API_KEY", "").trim();
            if (key.isEmpty()) {
                return new ImageGenResult("", "openai", "clé absente");
            }
            String model = cfg("openai.imageModel", "OPENAI_IMAGE_MODEL", "gpt-image-1").trim();
            String payload = "{"
                    + "\"model\":\"" + j(model) + "\","
                    + "\"prompt\":\"" + j(prompt) + "\","
                    + "\"size\":\"512x512\","
                    + "\"quality\":\"low\","
                    + "\"response_format\":\"b64_json\""
                    + "}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/images/generations"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                return new ImageGenResult("", "openai", "HTTP " + resp.statusCode() + " " + truncate(resp.body(), 160));
            }
            String b64 = jsonValue(resp.body(), "b64_json");
            if (b64 == null || b64.isBlank()) {
                return new ImageGenResult("", "openai", "b64_json absent");
            }
            return new ImageGenResult("data:image/png;base64," + b64.trim(), "openai", "");
        } catch (Exception e) {
            return new ImageGenResult("", "openai", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String huggingFaceGenerateImageDataUrl(String prompt, int seedIndex) {
        String key = cfg("huggingface.apiKey", "HUGGINGFACE_API_KEY", "").trim();
        if (key.isEmpty()) {
            return "";
        }
        String primaryModel = sanitizeHfModelId(cfg("huggingface.imageModel", "HUGGINGFACE_IMAGE_MODEL",
                "runwayml/stable-diffusion-v1-5"));
        String[] candidates = buildSupportedHfImageCandidates(primaryModel);
        for (String model : candidates) {
            String out = huggingFaceGenerateImageDataUrlForModel(key, model, prompt, seedIndex);
            if (out != null && !out.isBlank()) {
                return out;
            }
        }
        return "";
    }

    private ImageGenResult tryHfImageWithDebug(String prompt, int seedIndex) {
        try {
            String key = cfg("huggingface.apiKey", "HUGGINGFACE_API_KEY", "").trim();
            if (key.isEmpty()) {
                return new ImageGenResult("", "huggingface", "clé absente");
            }
            String model = sanitizeHfModelId(cfg("huggingface.imageModel", "HUGGINGFACE_IMAGE_MODEL",
                    "runwayml/stable-diffusion-v1-5"));
            String[] candidates = buildSupportedHfImageCandidates(model);
            String lastErr = "";
            for (String m : candidates) {
                var r = hfAttemptModel(key, m, prompt, seedIndex);
                if (r.dataUrl() != null && !r.dataUrl().isBlank()) {
                    return r;
                }
                lastErr = r.error();
            }
            return new ImageGenResult("", "huggingface", lastErr.isBlank() ? "échec inconnu" : lastErr);
        } catch (Exception e) {
            return new ImageGenResult("", "huggingface", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String[] buildSupportedHfImageCandidates(String primaryModel) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (primaryModel != null && !primaryModel.isBlank()) {
            ordered.add(sanitizeHfModelId(primaryModel));
        }
        for (String m : HF_IMAGE_MODEL_CANDIDATES) {
            ordered.add(sanitizeHfModelId(m));
        }
        List<String> supported = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String m : ordered) {
            if (m == null || m.isBlank()) {
                continue;
            }
            Boolean ok = isModelLikelySupportedByHfInference(m);
            if (Boolean.TRUE.equals(ok)) {
                supported.add(m);
            } else {
                unknown.add(m);
            }
        }
        if (!supported.isEmpty()) {
            return supported.toArray(String[]::new);
        }
        return unknown.toArray(String[]::new);
    }

    private static Boolean isModelLikelySupportedByHfInference(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Boolean.FALSE;
        }
        synchronized (HF_INFERENCE_MODEL_SUPPORT_CACHE) {
            if (HF_INFERENCE_MODEL_SUPPORT_CACHE.containsKey(modelId)) {
                return HF_INFERENCE_MODEL_SUPPORT_CACHE.get(modelId);
            }
        }
        boolean supported = false;
        try {
            String hubUrl = "https://huggingface.co/api/models/" + encPath(modelId) + "?expand[]=inferenceProviderMapping";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(hubUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "AutiCareDesktop/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 == 2) {
                String body = resp.body() != null ? resp.body().toLowerCase(Locale.ROOT) : "";
                if (body.contains("\"hf-inference\"")) {
                    supported = true;
                }
            }
        } catch (Exception ignored) {
            // si la sonde échoue, on garde le modèle "inconnu" et on testera quand même en requête réelle.
        }
        synchronized (HF_INFERENCE_MODEL_SUPPORT_CACHE) {
            HF_INFERENCE_MODEL_SUPPORT_CACHE.put(modelId, supported);
        }
        return supported;
    }

    /** Enlève commentaire inline (#...) souvent collé par erreur dans application.properties. */
    private static String sanitizeHfModelId(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        int hash = s.indexOf('#');
        if (hash >= 0) {
            s = s.substring(0, hash).trim();
        }
        return s.replaceAll("\\s+", "");
    }

    private ImageGenResult hfAttemptModel(String key, String model, String prompt, int seedIndex) {
        try {
            model = sanitizeHfModelId(model);
            if (model.isBlank()) {
                return new ImageGenResult("", "huggingface", "modèle vide");
            }
            String modelPath = encPath(model.trim());
            String payload = "{"
                    + "\"inputs\":\"" + j(prompt) + "\","
                    + "\"parameters\":{"
                    + "\"num_inference_steps\":28,"
                    + "\"guidance_scale\":8.0,"
                    + "\"width\":768,"
                    + "\"height\":512,"
                    + "\"negative_prompt\":\"colorful, bright colors, text, watermark, logo, frame, border, photo, realistic face\","
                    + "\"seed\":" + Math.max(1, seedIndex + 1)
                    + "}"
                    + "}";
            String lastError = "";
            for (String base : HF_IMAGE_INFERENCE_BASES) {
                String url = base + modelPath;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(90))
                            .header("Authorization", "Bearer " + key)
                            .header("Content-Type", "application/json")
                            .header("Accept", "*/*")
                            .header("X-Wait-For-Model", "true")
                            .header("User-Agent", "AutiCareDesktop/1.0")
                            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                    int status = resp.statusCode();
                    String contentType = sanitizeMimeType(resp.headers().firstValue("content-type").orElse(""), "");
                    if (status / 100 == 2 && !contentType.isBlank()) {
                        String b64 = Base64.getEncoder().encodeToString(resp.body());
                        return new ImageGenResult("data:" + contentType + ";base64," + b64, "huggingface/" + model, "");
                    }
                    if (status / 100 == 2) {
                        String sniffedMime = detectImageMimeType(resp.body());
                        if (sniffedMime != null) {
                            String b64 = Base64.getEncoder().encodeToString(resp.body());
                            return new ImageGenResult("data:" + sniffedMime + ";base64," + b64, "huggingface/" + model, "");
                        }
                    }
                    String bodyText = new String(resp.body(), StandardCharsets.UTF_8);
                    if (status / 100 == 2) {
                        String fromJson = tryExtractImageDataUrlFromHfJson(bodyText);
                        if (fromJson != null && !fromJson.isBlank()) {
                            if (fromJson.startsWith("http://") || fromJson.startsWith("https://")) {
                                String dataUrl = fetchUrlAsImageDataUrl(fromJson);
                                if (dataUrl != null && !dataUrl.isBlank()) {
                                    return new ImageGenResult(dataUrl, "huggingface/" + model, "");
                                }
                            }
                            return new ImageGenResult(fromJson, "huggingface/" + model, "");
                        }
                    }
                    if (status == 503 || bodyText.toLowerCase(Locale.ROOT).contains("loading")) {
                        String eta = jsonNumberValue(bodyText, "estimated_time");
                        long sleepMs = 6000L;
                        if (eta != null) {
                            try { sleepMs = Math.max(4000L, (long) (Double.parseDouble(eta) * 1000L)); } catch (Exception ignored) {}
                        }
                        Thread.sleep(Math.min(sleepMs, 15000L));
                        continue;
                    }
                    lastError = "HTTP " + status + " " + truncate(bodyText, 160);
                    break;
                }
            }
            // Fallback provider-side: certains modèles ne répondent qu'en mode router/fal-ai.
            ImageGenResult fal = tryFalRouterTextToImage(key, model, prompt, seedIndex);
            if (fal.dataUrl() != null && !fal.dataUrl().isBlank()) {
                return fal;
            }
            if (fal.error() != null && !fal.error().isBlank()) {
                lastError = (lastError == null || lastError.isBlank())
                        ? "fal-ai: " + fal.error()
                        : lastError + " | fal-ai: " + fal.error();
            }
            // Fallback IA sans clé: utile quand HF retourne 402 (crédits épuisés).
            ImageGenResult pollinations = tryPollinationsTextToImage(prompt, seedIndex);
            if (pollinations.dataUrl() != null && !pollinations.dataUrl().isBlank()) {
                return pollinations;
            }
            if (pollinations.error() != null && !pollinations.error().isBlank()) {
                lastError = (lastError == null || lastError.isBlank())
                        ? "pollinations: " + pollinations.error()
                        : lastError + " | pollinations: " + pollinations.error();
            }
            return new ImageGenResult("", "huggingface/" + model,
                    (lastError.isBlank() ? "timeout/loading persistant" : lastError));
        } catch (Exception e) {
            return new ImageGenResult("", "huggingface/" + model, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * Si l'API classique hf-inference ne répond pas, appelle le modèle via le routeur Inference Providers
     * (ex. fal-ai), comme le client Python {@code InferenceClient(provider="auto")}.
     */
    private ImageGenResult tryFalRouterTextToImage(String key, String hfModelId, String prompt, int seedIndex) {
        try {
            hfModelId = sanitizeHfModelId(hfModelId);
            if (hfModelId.isBlank()) {
                return new ImageGenResult("", "huggingface/fal", "modèle vide");
            }
            String hubUrl = "https://huggingface.co/api/models/" + encPath(hfModelId) + "?expand[]=inferenceProviderMapping";
            HttpRequest hubReq = HttpRequest.newBuilder()
                    .uri(URI.create(hubUrl))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "AutiCareDesktop/1.0")
                    .GET()
                    .build();
            HttpResponse<String> hubResp = HTTP.send(hubReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (hubResp.statusCode() / 100 != 2) {
                return new ImageGenResult("", "huggingface/fal",
                        "Hub mapping HTTP " + hubResp.statusCode() + " " + truncate(hubResp.body(), 120));
            }
            String hubBody = hubResp.body();
            String falBlock = extractJsonObjectAfterField(hubBody, "\"fal-ai\"");
            if (falBlock == null) {
                return new ImageGenResult("", "huggingface/fal", "pas de provider fal-ai dans inferenceProviderMapping");
            }
            String st = jsonValue(falBlock, "status");
            if (st != null && !"live".equalsIgnoreCase(st.trim())) {
                return new ImageGenResult("", "huggingface/fal", "fal-ai status=" + st);
            }
            String providerId = jsonValue(falBlock, "providerId");
            if (providerId == null || providerId.isBlank()) {
                return new ImageGenResult("", "huggingface/fal", "providerId fal vide");
            }
            String falUrl = "https://router.huggingface.co/fal-ai/" + encPath(providerId.trim());
            String payload = "{"
                    + "\"prompt\":\"" + j(prompt) + "\","
                    + "\"seed\":" + Math.max(1, seedIndex + 1)
                    + "}";
            HttpRequest post = HttpRequest.newBuilder()
                    .uri(URI.create(falUrl))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, image/*")
                    .header("User-Agent", "AutiCareDesktop/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(post, HttpResponse.BodyHandlers.ofByteArray());
            int status = resp.statusCode();
            String contentType = resp.headers().firstValue("content-type").orElse("");
            if (status / 100 == 2 && contentType.startsWith("image/")) {
                String b64 = Base64.getEncoder().encodeToString(resp.body());
                return new ImageGenResult("data:" + contentType + ";base64," + b64, "huggingface/fal/" + hfModelId, "");
            }
            String bodyText = new String(resp.body(), StandardCharsets.UTF_8);
            if (status / 100 == 2) {
                String imgUrl = jsonFirstUrlInImagesArray(bodyText);
                if (imgUrl != null && !imgUrl.isBlank()) {
                    String dataUrl = fetchUrlAsImageDataUrl(imgUrl);
                    if (dataUrl != null && !dataUrl.isBlank()) {
                        return new ImageGenResult(dataUrl, "huggingface/fal/" + hfModelId, "");
                    }
                }
                String fromJson = tryExtractImageDataUrlFromHfJson(bodyText);
                if (fromJson != null && !fromJson.isBlank()) {
                    return new ImageGenResult(fromJson, "huggingface/fal/" + hfModelId, "");
                }
            }
            return new ImageGenResult("", "huggingface/fal/" + hfModelId,
                    "HTTP " + status + " " + truncate(bodyText, 200));
        } catch (Exception e) {
            return new ImageGenResult("", "huggingface/fal",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /** Extrait le premier objet JSON après {@code "fieldName":} (ex. {@code "fal-ai": { ... }}). */
    private static String extractJsonObjectAfterField(String json, String fieldNameToken) {
        if (json == null) {
            return null;
        }
        int k = json.indexOf(fieldNameToken);
        if (k < 0) {
            return null;
        }
        int i = json.indexOf('{', k + fieldNameToken.length());
        if (i < 0) {
            return null;
        }
        int depth = 0;
        for (int j = i; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(i, j + 1);
                }
            }
        }
        return null;
    }

    private static String jsonFirstUrlInImagesArray(String json) {
        if (json == null) {
            return null;
        }
        Pattern p = Pattern.compile(
                "\"images\"\\s*:\\s*\\[\\s*\\{\\s*\"url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
                Pattern.CASE_INSENSITIVE);
        var m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        return m.group(1)
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
    }

    private static String fetchUrlAsImageDataUrl(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isBlank()) {
                return null;
            }
            if (imageUrl.startsWith("data:image/")) {
                return imageUrl;
            }
            HttpRequest get = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "AutiCareDesktop/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> r = HTTP.send(get, HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() / 100 != 2) {
                return null;
            }
            String ct = r.headers().firstValue("content-type").orElse("image/png");
            if (!ct.startsWith("image/")) {
                ct = "image/png";
            }
            int semi = ct.indexOf(';');
            if (semi > 0) {
                ct = ct.substring(0, semi).trim();
            }
            String b64 = Base64.getEncoder().encodeToString(r.body());
            return "data:" + ct + ";base64," + b64;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Fallback IA image public (sans clé) pour éviter un blocage utilisateur
     * quand le provider principal est en quota.
     */
    private ImageGenResult tryPollinationsTextToImage(String prompt, int seedIndex) {
        try {
            String cleanPrompt = prompt == null || prompt.isBlank()
                    ? "Rorschach inkblot, bilateral symmetry, black ink on white background"
                    : prompt;
            String url = "https://image.pollinations.ai/prompt/" + enc(cleanPrompt)
                    + "?width=768&height=512&nologo=true&seed=" + Math.max(1, seedIndex + 1);
            String dataUrl = fetchUrlAsImageDataUrl(url);
            if (dataUrl != null && !dataUrl.isBlank()) {
                return new ImageGenResult(dataUrl, "pollinations", "");
            }
            return new ImageGenResult("", "pollinations", "réponse image vide");
        } catch (Exception e) {
            return new ImageGenResult("", "pollinations",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String huggingFaceGenerateImageDataUrlForModel(String key, String model, String prompt, int seedIndex) {
        return hfAttemptModel(key, model, prompt, seedIndex).dataUrl();
    }
}
