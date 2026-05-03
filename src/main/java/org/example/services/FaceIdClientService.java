package org.example.services;

import java.io.File;
import java.util.List;

import org.example.models.User;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FaceIdClientService implements FaceBiometricService {
    private static final Pattern JSON_SIMILARITY = Pattern.compile("\"similarity\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern JSON_IDENTIFY_SIM = Pattern.compile("\"similarity\"\\s*:\\s*([-0-9.eE+]+)");
    private static final Pattern JSON_BEST_KEY = Pattern.compile("\"bestKey\"\\s*:\\s*\"([0-9]+)\"");
    private static final Pattern JSON_BEST_KEY_NULL = Pattern.compile("\"bestKey\"\\s*:\\s*null");
    private static final Pattern JSON_ERROR_CODE = Pattern.compile("\"errorCode\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern JSON_TEMPLATE = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private final FaceIdConfig config;
    private final HttpClient http;

    public FaceIdClientService() {
        this(new FaceIdConfig());
    }

    public FaceIdClientService(FaceIdConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public boolean isHealthy() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.serviceUrl() + "/health"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null && res.body().contains("\"ok\"");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String enrollFromImage(File imageFile) throws FaceBiometricException {
        if (imageFile == null || !imageFile.isFile()) {
            throw new FaceBiometricException("INVALID_IMAGE", "Image invalide.");
        }
        try {
            String boundary = "----AutoCareFaceBoundary" + System.currentTimeMillis();
            String contentType = guessContentType(imageFile.getName());
            byte[] fileBytes = Files.readAllBytes(imageFile.toPath());
            byte[] body = buildMultipart(boundary, imageFile.getName(), contentType, fileBytes);

            HttpRequest req = HttpRequest.newBuilder(URI.create(config.serviceUrl() + "/enroll"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw toBusinessError(res.statusCode(), res.body(), "SERVICE_ERROR", "Impossible d'enrôler le visage.");
            }
            String payload = res.body() != null ? res.body().trim() : "";
            if (!JSON_TEMPLATE.matcher(payload).matches()) {
                throw new FaceBiometricException("INVALID_TEMPLATE", "Template biométrique invalide.");
            }
            return payload;
        } catch (FaceBiometricException e) {
            throw e;
        } catch (Exception e) {
            throw new FaceBiometricException("SERVICE_UNAVAILABLE",
                    "Service Face ID indisponible. Vérifiez que le service local est démarré.", e);
        }
    }

    @Override
    public double similarity(String enrolledTemplateJson, File probeImage) throws FaceBiometricException {
        if (probeImage == null || !probeImage.isFile()) {
            throw new FaceBiometricException("INVALID_IMAGE", "Image de vérification invalide.");
        }
        if (enrolledTemplateJson == null || enrolledTemplateJson.isBlank()) {
            throw new FaceBiometricException("INVALID_TEMPLATE", "Template biométrique absent.");
        }
        try {
            byte[] probeBytes = Files.readAllBytes(probeImage.toPath());
            String probeB64 = Base64.getEncoder().encodeToString(probeBytes);
            String normalizedTemplate = normalizeTemplateForVerify(enrolledTemplateJson);
            String payload = "{"
                    + "\"template\":" + normalizedTemplate + ","
                    + "\"probe_image_base64\":\"" + jsonEscape(probeB64) + "\""
                    + "}";

            HttpRequest req = HttpRequest.newBuilder(URI.create(config.serviceUrl() + "/verify"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw toBusinessError(res.statusCode(), res.body(), "SERVICE_ERROR", "Impossible de vérifier le visage.");
            }
            Matcher m = JSON_SIMILARITY.matcher(res.body() != null ? res.body() : "");
            if (!m.find()) {
                throw new FaceBiometricException("INVALID_RESPONSE", "Réponse Face ID invalide.");
            }
            double s = Double.parseDouble(m.group(1));
            if (s < 0.0) return 0.0;
            return Math.min(s, 1.0);
        } catch (FaceBiometricException e) {
            throw e;
        } catch (Exception e) {
            throw new FaceBiometricException("SERVICE_UNAVAILABLE",
                    "Service Face ID indisponible. Vérifiez que le service local est démarré.", e);
        }
    }

    /**
     * FastAPI expects {@code template} as a JSON object. Some DB rows store only the embedding array {@code [...]}.
     */
    private static String normalizeTemplateForVerify(String rawTemplate) throws FaceBiometricException {
        if (rawTemplate == null || rawTemplate.isBlank()) {
            throw new FaceBiometricException("INVALID_TEMPLATE", "Template biométrique absent.");
        }
        String t = rawTemplate.trim();
        if (t.startsWith("[")) {
            return "{\"embedding\":" + t + "}";
        }
        if (t.startsWith("{")) {
            return t;
        }
        throw new FaceBiometricException("INVALID_TEMPLATE", "Template biométrique invalide.");
    }

    /**
     * Compare la sonde à plusieurs utilisateurs en un seul appel réseau (le service ne détecte le visage qu'une fois).
     */
    public FaceIdentifyMatch identifyBestAmongUsers(List<User> users, File probe) throws FaceBiometricException {
        if (probe == null || !probe.isFile()) {
            throw new FaceBiometricException("INVALID_IMAGE", "Image de vérification invalide.");
        }
        if (users == null || users.isEmpty()) {
            return new FaceIdentifyMatch(FaceIdentifyMatch.NO_MATCH, -1.0);
        }
        try {
            byte[] probeBytes = Files.readAllBytes(probe.toPath());
            String probeB64 = Base64.getEncoder().encodeToString(probeBytes);
            StringBuilder sb = new StringBuilder(Math.min(256 + users.size() * 4096, 50_000_000));
            sb.append("{\"probe_image_base64\":\"").append(jsonEscape(probeB64)).append("\",\"candidates\":[");
            boolean any = false;
            for (User u : users) {
                String raw = u.getDataFaceApi();
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                if (any) {
                    sb.append(',');
                }
                any = true;
                String tpl = normalizeTemplateForVerify(raw);
                sb.append("{\"key\":\"").append(u.getId()).append("\",\"template\":").append(tpl).append('}');
            }
            sb.append("]}");
            if (!any) {
                return new FaceIdentifyMatch(FaceIdentifyMatch.NO_MATCH, -1.0);
            }
            String payload = sb.toString();
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.serviceUrl() + "/identify"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                String errBody = res.body() != null ? res.body() : "";
                if (res.statusCode() == 400) {
                    if (errBody.contains("\"NO_FACE\"") || errBody.contains("NO_FACE")) {
                        throw new FaceBiometricException("NO_FACE", "Aucun visage détecté. Utilisez une photo nette du visage.");
                    }
                    if (errBody.contains("MULTIPLE_FACES")) {
                        throw new FaceBiometricException("MULTIPLE_FACES",
                                "Plusieurs visages détectés. Utilisez une image avec un seul visage.");
                    }
                    if (errBody.contains("INVALID_IMAGE")) {
                        throw new FaceBiometricException("INVALID_IMAGE", "Image de vérification invalide.");
                    }
                }
                throw toBusinessError(res.statusCode(), errBody, "SERVICE_ERROR", "Impossible d'identifier le visage.");
            }
            String body = res.body() != null ? res.body() : "";
            Matcher sm = JSON_IDENTIFY_SIM.matcher(body);
            if (!sm.find()) {
                throw new FaceBiometricException("INVALID_RESPONSE", "Réponse Face ID invalide.");
            }
            double score = Double.parseDouble(sm.group(1));
            Matcher km = JSON_BEST_KEY.matcher(body);
            if (km.find()) {
                int userId = Integer.parseInt(km.group(1));
                return new FaceIdentifyMatch(userId, Math.min(1.0, Math.max(-1.0, score)));
            }
            Matcher kn = JSON_BEST_KEY_NULL.matcher(body);
            if (kn.find()) {
                return new FaceIdentifyMatch(FaceIdentifyMatch.NO_MATCH, Math.min(1.0, Math.max(-1.0, score)));
            }
            throw new FaceBiometricException("INVALID_RESPONSE", "Réponse Face ID invalide (bestKey).");
        } catch (FaceBiometricException e) {
            throw e;
        } catch (Exception e) {
            throw new FaceBiometricException("SERVICE_UNAVAILABLE",
                    "Service Face ID indisponible. Vérifiez que le service local est démarré.", e);
        }
    }

    private static FaceBiometricException toBusinessError(int statusCode, String json, String defaultCode, String defaultMessage) {
        String code = defaultCode;
        String message = defaultMessage;
        if (json != null) {
            Matcher c = JSON_ERROR_CODE.matcher(json);
            if (c.find()) {
                code = c.group(1);
            }
            Matcher m = JSON_MESSAGE.matcher(json);
            if (m.find() && !m.group(1).isBlank()) {
                message = m.group(1);
            }
        }
        if (defaultMessage.equals(message)) {
            String raw = compactForUi(json);
            if (!raw.isEmpty()) {
                message = defaultMessage + " (HTTP " + statusCode + "): " + raw;
            } else {
                message = defaultMessage + " (HTTP " + statusCode + ")";
            }
        }
        return new FaceBiometricException(code, message);
    }

    private static String compactForUi(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 180) {
            return cleaned.substring(0, 180) + "...";
        }
        return cleaned;
    }

    private static byte[] buildMultipart(String boundary, String fileName, String contentType, byte[] content) {
        String start = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"" + url(fileName) + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        String end = "\r\n--" + boundary + "--\r\n";
        byte[] a = start.getBytes(StandardCharsets.UTF_8);
        byte[] b = end.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[a.length + content.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(content, 0, out, a.length, content.length);
        System.arraycopy(b, 0, out, a.length + content.length, b.length);
        return out;
    }

    private static String guessContentType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    private static String jsonEscape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String url(String s) {
        return URLEncoder.encode(s == null ? "image" : s, StandardCharsets.UTF_8);
    }
}
