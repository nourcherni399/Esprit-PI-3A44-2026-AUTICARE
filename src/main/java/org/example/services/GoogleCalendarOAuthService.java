package org.example.services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * OAuth 2.0 Google pour l’API Calendar : obtention d’un {@code refresh_token} (scope
 * {@code https://www.googleapis.com/auth/calendar.events}) avec le même client que la connexion Google.
 */
public final class GoogleCalendarOAuthService {

    public static final String CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events";

    private static final String AUTH_BASE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private GoogleCalendarOAuthService() {
    }

    /**
     * Ouvre le navigateur, attend le callback sur {@code oauth.google.redirectUri}, échange le code contre des jetons.
     *
     * @return refresh token si succès (peut être absent si l’utilisateur a déjà consenti sans prompt — utiliser prompt=consent)
     */
    public static Optional<String> authorizeInteractive() throws IOException, InterruptedException {
        String clientId = SmtpMailUtil.readConfig("oauth.google.clientId", "OAUTH_GOOGLE_CLIENT_ID", "").trim();
        String clientSecret = SmtpMailUtil.readConfig("oauth.google.clientSecret", "OAUTH_GOOGLE_CLIENT_SECRET", "").trim();
        String redirectUri = SmtpMailUtil.readConfig("oauth.google.redirectUri", "OAUTH_GOOGLE_REDIRECT_URI",
                "http://127.0.0.1:8888/oauth/callback/").trim();
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new IOException("oauth.google.clientId / oauth.google.clientSecret manquants.");
        }

        URI red = URI.create(redirectUri);
        String host = red.getHost() != null ? red.getHost() : "127.0.0.1";
        int port = red.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(red.getScheme()) ? 443 : 80;
        }

        CompletableFuture<Result> result = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", ex -> handleCallback(ex, result));
        server.setExecutor(null);
        server.start();

        String state = Long.toHexString(System.nanoTime());
        String authUrl = AUTH_BASE
                + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc(CALENDAR_EVENTS_SCOPE)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + enc(state);

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(authUrl));
            } else {
                server.stop(0);
                throw new IOException("Impossible d’ouvrir le navigateur (Desktop.browse indisponible).");
            }

            Result r;
            try {
                r = result.get(3, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                return Optional.empty();
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof IOException io) {
                    throw io;
                }
                if (c instanceof RuntimeException re) {
                    throw re;
                }
                throw new IOException(c != null ? c.getMessage() : e.getMessage(), c);
            }
            if (r.error != null && !r.error.isBlank()) {
                if ("access_denied".equalsIgnoreCase(r.error)) {
                    return Optional.empty();
                }
                throw new IOException("Google OAuth : " + r.error + (r.errorDesc != null ? " — " + r.errorDesc : ""));
            }
            if (r.code == null || r.code.isBlank()) {
                return Optional.empty();
            }
            if (r.state != null && !r.state.isBlank() && !state.equals(r.state)) {
                throw new IOException("Paramètre state invalide.");
            }

            String body = formBody(Map.of(
                    "code", r.code,
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "redirect_uri", redirectUri,
                    "grant_type", "authorization_code"));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("Échange jetons HTTP " + resp.statusCode() + " : " + truncate(resp.body(), 400));
            }
            String refresh = jsonStringValue(resp.body(), "refresh_token");
            if (refresh == null || refresh.isBlank()) {
                throw new IOException("Google n’a pas renvoyé de refresh_token. Réessayez après prompt=consent "
                        + "ou vérifiez que le compte n’est pas déjà lié avec les mêmes scopes.");
            }
            return Optional.of(refresh);
        } finally {
            server.stop(0);
        }
    }

    private static void handleCallback(HttpExchange ex, CompletableFuture<Result> result) {
        try {
            URI uri = ex.getRequestURI();
            String path = uri.getPath() != null ? uri.getPath() : "";
            if (!path.startsWith("/oauth/callback")) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            Map<String, String> q = parseQuery(uri.getRawQuery());
            String err = q.get("error");
            String errDesc = q.get("error_description");
            String code = q.get("code");
            String st = q.get("state");
            String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>AutiCare</title></head>"
                    + "<body><p>Vous pouvez fermer cette fenêtre et revenir à AutiCare.</p></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
            if (!result.isDone()) {
                result.complete(new Result(code, err, errDesc, st));
            }
        } catch (Exception e) {
            if (!result.isDone()) {
                result.completeExceptionally(e);
            }
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return m;
        }
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) {
                continue;
            }
            String k = URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String formBody(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue() != null ? e.getValue() : "", StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String jsonStringValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        var m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private record Result(String code, String error, String errorDesc, String state) {
    }
}
