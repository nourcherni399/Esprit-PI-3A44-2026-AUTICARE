package org.example.services;

import java.io.File;
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
                throw toBusinessError(res.body(), "SERVICE_ERROR", "Impossible d'enrôler le visage.");
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
            String payload = "{"
                    + "\"template\":" + enrolledTemplateJson.trim() + ","
                    + "\"probe_image_base64\":\"" + jsonEscape(probeB64) + "\""
                    + "}";

            HttpRequest req = HttpRequest.newBuilder(URI.create(config.serviceUrl() + "/verify"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw toBusinessError(res.body(), "SERVICE_ERROR", "Impossible de vérifier le visage.");
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

    private static FaceBiometricException toBusinessError(String json, String defaultCode, String defaultMessage) {
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
        return new FaceBiometricException(code, message);
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
