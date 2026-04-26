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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Passerelle IA multi-fournisseurs : OpenAI, Groq, Gemini, Hugging Face + recherche image Pexels.
 * <p>Configuration via {@code application.properties} (ou variables d'environnement).</p>
 */
public final class MultiAiProviderService {
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
            "Tu es un assistant de support pour la prise de rendez-vous medicale, sensible aux besoins TSA/autisme. "
                    + "Reponds en francais, de facon claire, rassurante et concrete. "
                    + "Important: tu ne poses jamais de diagnostic medical, tu proposes seulement des conseils pratiques "
                    + "et recommandes un professionnel en cas de doute clinique.";

    public String chatRdv(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "";
        }
        String provider = cfg("ai.provider", "AI_PROVIDER", "openai").trim().toLowerCase(Locale.ROOT);
        try {
            return switch (provider) {
                case "openai" -> openAiChat(DEFAULT_SYSTEM_PROMPT, userPrompt);
                case "groq" -> groqChat(DEFAULT_SYSTEM_PROMPT, userPrompt);
                case "gemini" -> geminiChat(DEFAULT_SYSTEM_PROMPT, userPrompt);
                case "huggingface", "hf" -> huggingFaceTextGen(DEFAULT_SYSTEM_PROMPT + "\n\nQuestion: " + userPrompt);
                default -> "Provider IA non reconnu (" + provider + "). Utilisez openai, groq, gemini ou huggingface.";
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
        String provider = cfg("ai.imageProvider", "AI_IMAGE_PROVIDER", "openai").trim().toLowerCase(Locale.ROOT);
        try {
            return switch (provider) {
                case "openai" -> openAiGenerateImageDataUrl(prompt);
                case "huggingface", "hf" -> huggingFaceGenerateImageDataUrl(prompt, seedIndex);
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Génère une image via provider configuré, puis fallback IA vers l'autre provider IA.
     * N'utilise jamais de fallback local.
     */
    public String generateInkblotImageDataUrlWithIaFallback(String prompt, int seedIndex) {
        String preferred = cfg("ai.imageProvider", "AI_IMAGE_PROVIDER", "openai").trim().toLowerCase(Locale.ROOT);
        String first = generateInkblotImageDataUrl(prompt, seedIndex);
        if (first != null && !first.isBlank()) {
            return first;
        }
        try {
            if ("openai".equals(preferred)) {
                return huggingFaceGenerateImageDataUrl(prompt, seedIndex);
            }
            if ("huggingface".equals(preferred) || "hf".equals(preferred)) {
                return openAiGenerateImageDataUrl(prompt);
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    /**
     * Même logique que generateInkblotImageDataUrlWithIaFallback, mais retourne aussi la cause d'échec.
     */
    public ImageGenResult generateInkblotImageWithDebug(String prompt, int seedIndex) {
        String preferred = cfg("ai.imageProvider", "AI_IMAGE_PROVIDER", "openai").trim().toLowerCase(Locale.ROOT);
        if ("huggingface".equals(preferred) || "hf".equals(preferred)) {
            ImageGenResult hf = tryHfImageWithDebug(prompt, seedIndex);
            if (hf.dataUrl() != null && !hf.dataUrl().isBlank()) {
                return hf;
            }
            ImageGenResult oa = tryOpenAiImageWithDebug(prompt);
            if (oa.dataUrl() != null && !oa.dataUrl().isBlank()) {
                return oa;
            }
            return new ImageGenResult("", "", "HF: " + hf.error() + " | OpenAI: " + oa.error());
        } else {
            ImageGenResult oa = tryOpenAiImageWithDebug(prompt);
            if (oa.dataUrl() != null && !oa.dataUrl().isBlank()) {
                return oa;
            }
            ImageGenResult hf = tryHfImageWithDebug(prompt, seedIndex);
            if (hf.dataUrl() != null && !hf.dataUrl().isBlank()) {
                return hf;
            }
            return new ImageGenResult("", "", "OpenAI: " + oa.error() + " | HF: " + hf.error());
        }
    }

    /**
     * Génération image demandée en mode HF + Pexels :
     * 1) Pexels (image rapide)
     * 2) Hugging Face (secours text-to-image)
     */
    public ImageGenResult generateInkblotImageWithHfPexelsDebug(String prompt, int seedIndex) {
        String pexels = pexelsImageDataUrl("inkblot abstract symmetry black white");
        if (pexels != null && !pexels.isBlank()) {
            return new ImageGenResult(pexels, "pexels", "");
        }
        ImageGenResult hf = tryHfImageWithDebug(prompt, seedIndex);
        if (hf.dataUrl() != null && !hf.dataUrl().isBlank()) {
            return hf;
        }
        return new ImageGenResult("", "", "Pexels: aucune image retournée | HF: " + hf.error());
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
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        var m = p.matcher(json != null ? json : "");
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

    private static String jsonNumberValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
        var m = p.matcher(json != null ? json : "");
        if (!m.find()) {
            return null;
        }
        return m.group(1);
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
                + "\"quality\":\"low\""
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
                    + "\"quality\":\"low\""
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

    private String huggingFaceGenerateImageDataUrl(String prompt, int seedIndex) throws Exception {
        String key = cfg("huggingface.apiKey", "HUGGINGFACE_API_KEY", "").trim();
        if (key.isEmpty()) {
            return "";
        }
        String primaryModel = cfg("huggingface.imageModel", "HUGGINGFACE_IMAGE_MODEL",
                "stabilityai/stable-diffusion-2-1").trim();
        String[] candidates = new String[] {
                primaryModel,
                "runwayml/stable-diffusion-v1-5",
                "stabilityai/sd-turbo"
        };
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
            String model = cfg("huggingface.imageModel", "HUGGINGFACE_IMAGE_MODEL",
                    "runwayml/stable-diffusion-v1-5").trim();
            String[] candidates = new String[] { model, "runwayml/stable-diffusion-v1-5", "stabilityai/sd-turbo" };
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

    private ImageGenResult hfAttemptModel(String key, String model, String prompt, int seedIndex) {
        try {
            if (model == null || model.isBlank()) {
                return new ImageGenResult("", "huggingface", "modèle vide");
            }
            String modelPath = encPath(model.trim());
            String[] urls = new String[] {
                    "https://api-inference.huggingface.co/models/" + modelPath,
                    "https://router.huggingface.co/hf-inference/models/" + modelPath
            };
            String payload = "{"
                    + "\"inputs\":\"" + j(prompt) + "\","
                    + "\"parameters\":{\"num_inference_steps\":22,\"guidance_scale\":7.0,\"seed\":" + Math.max(1, seedIndex + 1) + "}"
                    + "}";
            String lastError = "";
            for (String url : urls) {
                for (int attempt = 1; attempt <= 3; attempt++) {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(90))
                            .header("Authorization", "Bearer " + key)
                            .header("Content-Type", "application/json")
                            .header("Accept", "image/*,application/json")
                            .header("User-Agent", "AutiCareDesktop/1.0")
                            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                    int status = resp.statusCode();
                    String contentType = resp.headers().firstValue("content-type").orElse("");
                    if (status / 100 == 2 && contentType.startsWith("image/")) {
                        String b64 = Base64.getEncoder().encodeToString(resp.body());
                        return new ImageGenResult("data:" + contentType + ";base64," + b64, "huggingface/" + model, "");
                    }
                    String bodyText = new String(resp.body(), StandardCharsets.UTF_8);
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
            return new ImageGenResult("", "huggingface/" + model,
                    lastError.isBlank() ? "timeout/loading persistant" : lastError);
        } catch (Exception e) {
            return new ImageGenResult("", "huggingface/" + model, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String huggingFaceGenerateImageDataUrlForModel(String key, String model, String prompt, int seedIndex)
            throws Exception {
        if (model == null || model.isBlank()) {
            return "";
        }
        String url = "https://api-inference.huggingface.co/models/" + encPath(model.trim());
        String payload = "{"
                + "\"inputs\":\"" + j(prompt) + "\","
                + "\"parameters\":{\"num_inference_steps\":22,\"guidance_scale\":7.0,\"seed\":" + Math.max(1, seedIndex + 1) + "}"
                + "}";

        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int status = resp.statusCode();
            String contentType = resp.headers().firstValue("content-type").orElse("");

            if (status / 100 == 2 && contentType.startsWith("image/")) {
                String b64 = Base64.getEncoder().encodeToString(resp.body());
                return "data:" + contentType + ";base64," + b64;
            }

            // Cas fréquent HF : modèle en cours de chargement (503 + JSON avec estimated_time).
            String bodyText = "";
            if (contentType.contains("application/json") || !contentType.startsWith("image/")) {
                bodyText = new String(resp.body(), StandardCharsets.UTF_8);
            }
            if (status == 503 || bodyText.toLowerCase(Locale.ROOT).contains("loading")) {
                String eta = jsonNumberValue(bodyText, "estimated_time");
                long sleepMs = 6000L;
                if (eta != null) {
                    try {
                        sleepMs = Math.max(4000L, (long) (Double.parseDouble(eta) * 1000L));
                    } catch (Exception ignored) {
                        // valeur par défaut
                    }
                }
                Thread.sleep(Math.min(sleepMs, 15000L));
                continue;
            }
            break;
        }
        return "";
    }
}
