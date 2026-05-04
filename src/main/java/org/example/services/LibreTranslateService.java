package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.AppLanguage;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Service de traduction multi-provider avec fallbacks.
 *
 * Ordre des providers :
 *   1. Google Cloud Translation API (officiel, 500k chars/mois gratuit) — si clé configurée
 *   2. Google Translate endpoint non-officiel (sans clé, sans quota visible)
 *   3. MyMemory (500 mots/j anonyme, 10k avec email)
 *   4. LibreTranslate public (fallback de dernier recours)
 *
 * Configuration dans .ai.local.properties :
 *   google.translate.api.key=AIzaSy...    ← recommandé
 *   libretranslate.url=http://localhost:5000/translate   ← optionnel
 */
public class LibreTranslateService {

    // ── MyMemory ───────────────────────────────────────────────────────────
    // Laisser vide = 500 mots/j  |  Mettre un email = 10 000 mots/j gratuit
    private static final String MYMEMORY_EMAIL = "";
    private static final int    CHUNK_LIMIT    = 450;

    // ── LibreTranslate public (derniers fallbacks) ─────────────────────────
    private static final String       DEFAULT_LT_URL  = "https://libretranslate.com/translate";
    private static final String       FALLBACK_LT_URL = "https://translate.argosopentech.com/translate";
    private static final List<String> EXTRA_LT_URLS   = List.of(
            "https://libretranslate.de/translate"
    );

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    private final HttpClient   httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Cache mémoire : évite de re-traduire le même texte dans la même session. */
    private final Map<String, String>                    cache    = new ConcurrentHashMap<>();
    /** Déduplication des requêtes identiques simultanées. */
    private final Map<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────────────
    // API publique
    // ──────────────────────────────────────────────────────────────────────

    public String translateFromFrench(String text, AppLanguage targetLanguage) {
        return translateFromFrenchDetailed(text, targetLanguage).translatedText();
    }

    public String translateAuto(String text, AppLanguage targetLanguage) {
        return translateAutoDetailed(text, targetLanguage).translatedText();
    }

    public TranslationOutcome translateFromFrenchDetailed(String text, AppLanguage targetLanguage) {
        if (text == null || text.isBlank()) {
            return new TranslationOutcome(text, false, "none", "empty");
        }
        if (targetLanguage == null || "fr".equalsIgnoreCase(targetLanguage.code())) {
            return new TranslationOutcome(text, true, "none", "");
        }
        if (!isTranslatable(text)) {
            return new TranslationOutcome(text, true, "skip", "non_translatable");
        }

        String key = "fr->" + targetLanguage.code() + "::" + text;

        // 1) Cache mémoire
        String cached = cache.get(key);
        if (cached != null) {
            return new TranslationOutcome(cached, true, "cache", "");
        }

        // 2) Déduplication
        CompletableFuture<String> existing = inFlight.get(key);
        if (existing != null) {
            try {
                String result = existing.get(15, TimeUnit.SECONDS);
                if (result != null && !result.isBlank()) {
                    return new TranslationOutcome(result, true, "dedup", "");
                }
            } catch (Exception ignored) {}
        }

        // 3) Traduction avec fallbacks
        CompletableFuture<String> future = new CompletableFuture<>();
        inFlight.put(key, future);
        try {
            TranslationOutcome outcome = doTranslate(text, "fr", targetLanguage.code());
            String translated = outcome.translatedText();
            if (outcome.success() && translated != null && !translated.isBlank()) {
                cache.put(key, translated);
                future.complete(translated);
                return outcome;
            }
            future.complete(null);
            // Fallback gracieux : renvoyer le texte original (FR) avec success=false
            return new TranslationOutcome(text, false, outcome.provider(), outcome.error());
        } catch (Exception e) {
            future.complete(null);
            return new TranslationOutcome(text, false, "network",
                    e.getMessage() != null ? e.getMessage() : "request_failed");
        } finally {
            inFlight.remove(key);
        }
    }

    public TranslationOutcome translateAutoDetailed(String text, AppLanguage targetLanguage) {
        if (text == null || text.isBlank()) {
            return new TranslationOutcome(text, false, "none", "empty");
        }
        if (targetLanguage == null) {
            return new TranslationOutcome(text, true, "none", "");
        }
        if (!isTranslatable(text)) {
            return new TranslationOutcome(text, true, "skip", "non_translatable");
        }

        String targetCode = targetLanguage.code();
        String key = "auto->" + targetCode + "::" + text;

        // 1) Cache mémoire
        String cached = cache.get(key);
        if (cached != null) {
            return new TranslationOutcome(cached, true, "cache", "");
        }

        // 2) Déduplication
        CompletableFuture<String> existing = inFlight.get(key);
        if (existing != null) {
            try {
                String result = existing.get(15, TimeUnit.SECONDS);
                if (result != null && !result.isBlank()) {
                    return new TranslationOutcome(result, true, "dedup", "");
                }
            } catch (Exception ignored) {}
        }

        // 3) Traduction avec fallbacks
        CompletableFuture<String> future = new CompletableFuture<>();
        inFlight.put(key, future);
        try {
            TranslationOutcome outcome = doTranslate(text, "auto", targetCode);
            String translated = outcome.translatedText();
            if (outcome.success() && translated != null && !translated.isBlank()) {
                cache.put(key, translated);
                future.complete(translated);
                return outcome;
            }
            future.complete(null);
            return new TranslationOutcome(text, false, outcome.provider(), outcome.error());
        } catch (Exception e) {
            future.complete(null);
            return new TranslationOutcome(text, false, "network",
                    e.getMessage() != null ? e.getMessage() : "request_failed");
        } finally {
            inFlight.remove(key);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cascade de providers
    // ──────────────────────────────────────────────────────────────────────

    private TranslationOutcome doTranslate(String text, String source, String target) {

        // ── 1) Google Cloud Translation API (officiel) — si clé présente ──
        String googleApiKey = resolveGoogleApiKey();
        if (googleApiKey != null) {
            try {
                String gc = requestViaGoogleCloud(text, source, target, googleApiKey);
                if (gc != null && !gc.isBlank()) {
                    return new TranslationOutcome(gc, true, "google-cloud", "");
                }
            } catch (Exception ex) {
                System.err.println("[Translation] Google Cloud échoué: " + ex.getMessage());
            }
        }

        // ── 2) Google Translate endpoint non-officiel (sans clé) ──
        try {
            String gt = requestViaGoogleUnofficial(text, source, target);
            if (gt != null && !gt.isBlank()) {
                return new TranslationOutcome(gt, true, "google-gtx", "");
            }
        } catch (Exception ex) {
            System.err.println("[Translation] Google GTX échoué: " + ex.getMessage());
        }

        // ── 3) MyMemory ──
        try {
            String mm = requestViaMyMemory(text, source, target);
            if (mm != null && !mm.isBlank()) {
                return new TranslationOutcome(mm, true, "mymemory", "");
            }
        } catch (Exception ex) {
            System.err.println("[Translation] MyMemory échoué: " + ex.getMessage());
        }

        // ── 4) LibreTranslate public (dernier recours) ──
        String apiKey    = resolveLtApiKey();
        String lastProv  = "none";
        String lastError = "all_endpoints_failed";
        for (String url : resolveLtUrls()) {
            try {
                String out = requestLtOnce(url, text, source, target, apiKey, timeoutFor(url));
                if (out != null && !out.isBlank() && !looksLikeProviderError(out)) {
                    return new TranslationOutcome(out, true, url, "");
                }
                lastProv  = url;
                lastError = "empty_or_provider_error";
            } catch (Exception ex) {
                lastProv  = url;
                lastError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            }
        }
        return new TranslationOutcome("", false, lastProv, lastError);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Google Cloud Translation API (officiel v2)
    // ──────────────────────────────────────────────────────────────────────

    private String requestViaGoogleCloud(String text, String source, String target, String apiKey)
            throws IOException, InterruptedException {
        if (text.length() > CHUNK_LIMIT) {
            return requestViaGoogleCloudChunked(text, source, target, apiKey);
        }
        return requestViaGoogleCloudOnce(text, source, target, apiKey);
    }

    private String requestViaGoogleCloudOnce(String text, String source, String target, String apiKey)
            throws IOException, InterruptedException {
        // Utilise l'API REST v2 (Basic) — pas besoin du SDK
        String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey;

        var payload = mapper.createObjectNode();
        payload.put("q", text);
        payload.put("source", source);
        payload.put("target", target);
        payload.put("format", "text");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 429) throw new IOException("Google Cloud rate limit (429)");
        if (response.statusCode() == 403) throw new IOException("Google Cloud clé invalide ou API non activée (403)");
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Google Cloud HTTP " + response.statusCode());

        JsonNode root = mapper.readTree(response.body());
        return root.path("data").path("translations").path(0).path("translatedText").asText("");
    }

    private String requestViaGoogleCloudChunked(String text, String source, String target, String apiKey)
            throws IOException, InterruptedException {
        List<String>  chunks = splitIntoChunks(text, CHUNK_LIMIT);
        StringBuilder out    = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String translated = requestViaGoogleCloudOnce(chunks.get(i), source, target, apiKey);
            if (translated == null || translated.isBlank()) throw new IOException("Google Cloud chunk vide");
            if (i > 0) out.append(" ");
            out.append(translated.trim());
        }
        return out.toString().trim();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Google Translate endpoint non-officiel (sans clé)
    // ──────────────────────────────────────────────────────────────────────

    private String requestViaGoogleUnofficial(String text, String source, String target)
            throws IOException, InterruptedException {
        if (text.length() > CHUNK_LIMIT) {
            return requestViaGoogleUnofficialChunked(text, source, target);
        }
        return requestViaGoogleUnofficialOnce(text, source, target);
    }

    private String requestViaGoogleUnofficialOnce(String text, String source, String target)
            throws IOException, InterruptedException {
        String q   = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://translate.googleapis.com/translate_a/single"
                + "?client=gtx&sl=" + source + "&tl=" + target + "&dt=t&q=" + q;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 429) throw new IOException("Google GTX rate limit (429)");
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Google GTX HTTP " + response.statusCode());

        // Format : [[["traduit","original",null,null,1],...],...]
        JsonNode root = mapper.readTree(response.body());
        StringBuilder sb = new StringBuilder();
        JsonNode translations = root.path(0);
        if (translations.isArray()) {
            for (JsonNode segment : translations) {
                String part = segment.path(0).asText("");
                if (!part.isBlank()) sb.append(part);
            }
        }
        return sb.toString().trim();
    }

    private String requestViaGoogleUnofficialChunked(String text, String source, String target)
            throws IOException, InterruptedException {
        List<String>  chunks = splitIntoChunks(text, CHUNK_LIMIT);
        StringBuilder out    = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String translated = requestViaGoogleUnofficialOnce(chunks.get(i), source, target);
            if (translated == null || translated.isBlank()) throw new IOException("Google GTX chunk vide");
            if (i > 0) out.append(" ");
            out.append(translated.trim());
        }
        return out.toString().trim();
    }

    // ──────────────────────────────────────────────────────────────────────
    // MyMemory
    // ──────────────────────────────────────────────────────────────────────

    private String requestViaMyMemory(String text, String source, String target)
            throws IOException, InterruptedException {
        return text.length() > CHUNK_LIMIT
                ? requestViaMyMemoryChunked(text, source, target)
                : requestViaMyMemoryOnce(text, source, target);
    }

    private String requestViaMyMemoryOnce(String text, String source, String target)
            throws IOException, InterruptedException {
        String        q          = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String        lp         = URLEncoder.encode(source + "|" + target, StandardCharsets.UTF_8);
        StringBuilder urlBuilder = new StringBuilder(
                "https://api.mymemory.translated.net/get?q=" + q + "&langpair=" + lp);
        if (MYMEMORY_EMAIL != null && !MYMEMORY_EMAIL.isBlank()) {
            urlBuilder.append("&de=").append(URLEncoder.encode(MYMEMORY_EMAIL, StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonNode root           = mapper.readTree(response.body());
        int      responseStatus = root.path("responseStatus").asInt(200);
        if (responseStatus == 429 || responseStatus == 522 || response.statusCode() == 429) {
            throw new IOException("MyMemory quota atteint (status " + responseStatus + ")");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("MyMemory HTTP " + response.statusCode());
        }
        String translated = root.path("responseData").path("translatedText").asText("");
        if (looksLikeProviderError(translated)) throw new IOException("MyMemory erreur: " + translated);
        return translated;
    }

    private String requestViaMyMemoryChunked(String text, String source, String target)
            throws IOException, InterruptedException {
        List<String>  chunks = splitIntoChunks(text, CHUNK_LIMIT);
        StringBuilder out    = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String translated = requestViaMyMemoryOnce(chunks.get(i), source, target);
            if (translated == null || translated.isBlank()) throw new IOException("MyMemory chunk vide");
            if (i > 0) out.append(" ");
            out.append(translated.trim());
        }
        return out.toString().trim();
    }

    // ──────────────────────────────────────────────────────────────────────
    // LibreTranslate
    // ──────────────────────────────────────────────────────────────────────

    private String requestLtOnce(String url, String text, String source, String target,
                                  String apiKey, Duration timeout)
            throws IOException, InterruptedException {
        var payload = mapper.createObjectNode();
        payload.put("q", text);
        payload.put("source", source);
        payload.put("target", target);
        payload.put("format", "text");
        if (apiKey != null && !apiKey.isBlank()) payload.put("api_key", apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 429) throw new IOException("LibreTranslate rate limit (429) sur " + url);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("LibreTranslate status " + response.statusCode());
        return mapper.readTree(response.body()).path("translatedText").asText("");
    }

    private static List<String> resolveLtUrls() {
        List<String> urls  = new ArrayList<>();
        String custom = LocalAiPropertiesFile.readProperty("libretranslate.url");
        if (custom != null && !custom.isBlank()) urls.add(custom.trim());
        if (urls.stream().noneMatch(u -> DEFAULT_LT_URL.equalsIgnoreCase(u)))  urls.add(DEFAULT_LT_URL);
        if (urls.stream().noneMatch(u -> FALLBACK_LT_URL.equalsIgnoreCase(u))) urls.add(FALLBACK_LT_URL);
        for (String extra : EXTRA_LT_URLS)
            if (urls.stream().noneMatch(u -> extra.equalsIgnoreCase(u))) urls.add(extra);
        return urls;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ──────────────────────────────────────────────────────────────────────

    private static String resolveGoogleApiKey() {
        String env = System.getenv("GOOGLE_TRANSLATE_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        String fromFile = LocalAiPropertiesFile.readProperty("google.translate.api.key");
        return (fromFile != null && !fromFile.isBlank()) ? fromFile.trim() : null;
    }

    private static String resolveLtApiKey() {
        String env = System.getenv("LIBRETRANSLATE_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        String fromFile = LocalAiPropertiesFile.readProperty("libretranslate.api.key");
        return (fromFile != null && !fromFile.isBlank()) ? fromFile.trim() : null;
    }

    private static List<String> splitIntoChunks(String text, int maxLen) {
        List<String>  parts     = new ArrayList<>();
        if (text == null || text.isBlank()) return parts;
        String[]      sentences = SENTENCE_SPLIT.split(text.trim());
        StringBuilder current   = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) continue;
            String s = sentence.trim();
            if (s.length() > maxLen) {
                if (current.length() > 0) { parts.add(current.toString().trim()); current.setLength(0); }
                for (int start = 0; start < s.length(); start += maxLen)
                    parts.add(s.substring(start, Math.min(start + maxLen, s.length())));
                continue;
            }
            if (current.length() == 0) {
                current.append(s);
            } else if (current.length() + 1 + s.length() <= maxLen) {
                current.append(" ").append(s);
            } else {
                parts.add(current.toString().trim());
                current.setLength(0);
                current.append(s);
            }
        }
        if (current.length() > 0) parts.add(current.toString().trim());
        if (parts.isEmpty()) parts.add(text);
        return parts;
    }

    private static Duration timeoutFor(String url) {
        return (url != null && (url.contains("localhost") || url.contains("127.0.0.1")))
                ? Duration.ofSeconds(3) : Duration.ofSeconds(8);
    }

    private static boolean isTranslatable(String text) {
        String trimmed = text.trim();
        if (trimmed.isBlank()) return false;
        String lower = trimmed.toLowerCase();
        return !lower.startsWith("http://")
                && !lower.startsWith("https://")
                && !trimmed.matches("^[A-Z0-9_./\\\\-]{3,}$");
    }

    private static boolean looksLikeProviderError(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.contains("query length limit exceeded")
                || t.contains("max allowed query")
                || t.startsWith("error:")
                || t.contains("too many requests")
                || t.contains("quota");
    }

    public record TranslationOutcome(String translatedText, boolean success, String provider, String error) {}
}
