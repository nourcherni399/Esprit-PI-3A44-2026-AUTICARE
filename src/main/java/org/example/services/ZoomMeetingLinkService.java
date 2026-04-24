package org.example.services;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Génère un lien Zoom pour un événement.
 * Si la configuration Zoom manque, retourne un lien local de secours unique.
 */
public class ZoomMeetingLinkService {
    private static final Properties FILE_CONFIG = loadFileConfig();
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JOIN_URL_PATTERN = Pattern.compile("\"join_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final DateTimeFormatter ZOOM_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public String generateMeetingLink(String topic, LocalDateTime startAt, int durationMinutes) throws Exception {
        String accountId = cfgPreferFile("zoom.accountId", "ZOOM_ACCOUNT_ID", "");
        String clientId = cfgPreferFile("zoom.clientId", "ZOOM_CLIENT_ID", "");
        String clientSecret = cfgPreferFile("zoom.clientSecret", "ZOOM_CLIENT_SECRET", "");

        if (accountId.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            return generateLocalFallbackLink();
        }

        String accessToken = fetchAccessToken(accountId, clientId, clientSecret);
        return createMeeting(accessToken, topic, startAt, durationMinutes);
    }

    private String fetchAccessToken(String accountId, String clientId, String clientSecret) throws Exception {
        String auth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String url = "https://zoom.us/oauth/token?grant_type=account_credentials&account_id=" + accountId;

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Zoom token HTTP " + res.statusCode());
        }
        String token = extract(ACCESS_TOKEN_PATTERN, res.body());
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Token Zoom introuvable.");
        }
        return token;
    }

    private String createMeeting(String accessToken, String topic, LocalDateTime startAt, int durationMinutes) throws Exception {
        String cleanTopic = (topic == null || topic.isBlank()) ? "Reunion evenement" : topic.trim();
        LocalDateTime when = startAt != null ? startAt : LocalDateTime.now().plusMinutes(5);
        int duration = durationMinutes > 0 ? durationMinutes : 60;

        String payload = "{"
                + "\"topic\":\"" + jsonEsc(cleanTopic) + "\","
                + "\"type\":2,"
                + "\"start_time\":\"" + when.format(ZOOM_TIME_FMT) + "\","
                + "\"duration\":" + duration + ","
                + "\"timezone\":\"" + jsonEsc(ZoneId.systemDefault().getId()) + "\","
                + "\"settings\":{"
                + "\"join_before_host\":true,"
                + "\"approval_type\":2"
                + "}"
                + "}";

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.zoom.us/v2/users/me/meetings"))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Zoom meeting HTTP " + res.statusCode());
        }
        String joinUrl = extract(JOIN_URL_PATTERN, res.body());
        if (joinUrl == null || joinUrl.isBlank()) {
            throw new IllegalStateException("join_url Zoom introuvable.");
        }
        return joinUrl;
    }

    private static String generateLocalFallbackLink() {
        String token = UUID.randomUUID().toString().replace("-", "");
        return "https://zoom.us/j/" + token.substring(0, 10) + "?pwd=" + token.substring(10, 22);
    }

    private static String extract(Pattern p, String text) {
        Matcher m = p.matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : null;
    }

    private static String jsonEsc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String cfgPreferFile(String prop, String env, String fallback) {
        String f = FILE_CONFIG.getProperty(prop);
        if (f != null && !f.isBlank()) {
            return f.trim();
        }
        String fe = FILE_CONFIG.getProperty(env);
        if (fe != null && !fe.isBlank()) {
            return fe.trim();
        }
        String s = System.getProperty(prop);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = ZoomMeetingLinkService.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        return p;
    }
}
