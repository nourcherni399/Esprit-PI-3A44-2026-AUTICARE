package org.example.services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.models.Role;
import org.example.models.User;
import org.example.utils.PasswordUtil;

import java.awt.Desktop;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAuth Google pour client desktop JavaFX (Authorization Code + callback local).
 */
public class GoogleOAuthService {

    private static final Properties FILE_CONFIG = loadFileConfig();
    private static final Pattern JSON_EMAIL = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern JSON_SUB = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final UserService userService = new UserService();

    public String getConfiguredRedirectUri() {
        String raw = cfgPreferFile("oauth.google.redirectUri", "OAUTH_GOOGLE_REDIRECT_URI",
                "http://127.0.0.1:8888/oauth/callback");
        return normalizeRedirectUri(raw);
    }

    public String getConfiguredClientIdMasked() {
        String clientId = cfgPreferFile("oauth.google.clientId", "OAUTH_GOOGLE_CLIENT_ID", "");
        if (clientId == null || clientId.isBlank()) {
            return "(missing)";
        }
        int keep = Math.min(10, clientId.length());
        return clientId.substring(0, keep) + "...";
    }

    public User signInWithGoogle() throws Exception {
        String clientId = cfgPreferFile("oauth.google.clientId", "OAUTH_GOOGLE_CLIENT_ID", "");
        String clientSecret = cfgPreferFile("oauth.google.clientSecret", "OAUTH_GOOGLE_CLIENT_SECRET", "");
        String redirectUri = getConfiguredRedirectUri();
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth non configure (clientId/clientSecret manquants).");
        }

        URI redirect = URI.create(redirectUri);
        if (!"127.0.0.1".equalsIgnoreCase(redirect.getHost()) && !"localhost".equalsIgnoreCase(redirect.getHost())) {
            throw new IllegalStateException("redirectUri doit pointer vers localhost/127.0.0.1 pour l'app desktop.");
        }
        int port = redirect.getPort() > 0 ? redirect.getPort() : 80;
        String path = redirect.getPath() == null || redirect.getPath().isBlank() ? "/" : redirect.getPath();

        String state = randomState();
        CompletableFuture<Map<String, String>> callbackFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext(path, new CallbackHandler(state, callbackFuture));
        server.start();

        try {
            String authUrl = buildAuthUrl(clientId, redirectUri, state);
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop browse non supporte. Ouvrez manuellement:\n" + authUrl);
            }
            Desktop.getDesktop().browse(URI.create(authUrl));
            Map<String, String> query = callbackFuture.get(120, TimeUnit.SECONDS);
            String code = query.get("code");
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("Google n'a pas retourne de code d'autorisation.");
            }

            String accessToken = exchangeCodeForAccessToken(code, clientId, clientSecret, redirectUri);
            GoogleProfile profile = fetchUserInfo(accessToken);
            if (profile.email == null || profile.email.isBlank()) {
                throw new IllegalStateException("Google n'a pas retourne d'email.");
            }

            Optional<User> existing = userService.findByEmail(profile.email);
            if (existing.isPresent()) {
                return existing.get();
            }
            User u = new User();
            String[] parts = splitName(profile.name);
            u.setPrenom(parts[0]);
            u.setNom(parts[1]);
            u.setEmail(profile.email);
            u.setTelephone("0");
            u.setMotDePasseHash(PasswordUtil.hashBcrypt(UUID.randomUUID().toString()));
            u.setRole(Role.USER);
            u.setActif(true);
            userService.add(u);
            return userService.findByEmail(profile.email).orElse(u);
        } finally {
            server.stop(0);
        }
    }

    private String buildAuthUrl(String clientId, String redirectUri, String state) {
        String scope = "openid email profile";
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc(scope)
                + "&state=" + enc(state)
                + "&access_type=offline"
                + "&prompt=consent";
    }

    private String exchangeCodeForAccessToken(String code, String clientId, String clientSecret, String redirectUri) throws Exception {
        String form = "code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirectUri)
                + "&grant_type=authorization_code";
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Token endpoint Google en echec: HTTP " + res.statusCode());
        }
        String token = extract(JSON_ACCESS_TOKEN, res.body());
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("access_token absent dans la reponse Google.");
        }
        return token;
    }

    private GoogleProfile fetchUserInfo(String accessToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://openidconnect.googleapis.com/v1/userinfo"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("userinfo Google en echec: HTTP " + res.statusCode());
        }
        String body = res.body();
        return new GoogleProfile(
                extract(JSON_SUB, body),
                extract(JSON_EMAIL, body),
                extract(JSON_NAME, body)
        );
    }

    private static String[] splitName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return new String[]{"Google", "User"};
        }
        int i = n.indexOf(' ');
        if (i < 0) {
            return new String[]{n, "Google"};
        }
        String p = n.substring(0, i).trim();
        String q = n.substring(i + 1).trim();
        if (q.isEmpty()) {
            q = "Google";
        }
        return new String[]{p, q};
    }

    private static String extract(Pattern p, String text) {
        Matcher m = p.matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : null;
    }

    private static String randomState() {
        byte[] b = new byte[18];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String cfg(String prop, String env, String fallback) {
        String s = System.getProperty(prop);
        if (s != null && !s.isBlank()) return s.trim();
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) return e.trim();
        String f = FILE_CONFIG.getProperty(prop);
        if (f != null && !f.isBlank()) return f.trim();
        String fe = FILE_CONFIG.getProperty(env);
        if (fe != null && !fe.isBlank()) return fe.trim();
        return fallback;
    }

    /**
     * Pour ce client desktop, on privilegie d'abord le fichier local de l'app,
     * puis les overrides runtime (System/env).
     */
    private static String cfgPreferFile(String prop, String env, String fallback) {
        String f = FILE_CONFIG.getProperty(prop);
        if (f != null && !f.isBlank()) return f.trim();
        String fe = FILE_CONFIG.getProperty(env);
        if (fe != null && !fe.isBlank()) return fe.trim();
        String s = System.getProperty(prop);
        if (s != null && !s.isBlank()) return s.trim();
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) return e.trim();
        return fallback;
    }

    private static String enc(String x) {
        return URLEncoder.encode(x == null ? "" : x, StandardCharsets.UTF_8);
    }

    /**
     * Normalise la redirect URI pour éviter les mismatch Google
     * (ex: "/oauth/callback/" -> "/oauth/callback").
     */
    private static String normalizeRedirectUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return "http://127.0.0.1:8888/oauth/callback";
        }
        try {
            URI uri = URI.create(raw.trim());
            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            URI normalized = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    uri.getFragment());
            return normalized.toString();
        } catch (Exception ignored) {
            return raw.trim();
        }
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = GoogleOAuthService.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) p.load(in);
        } catch (Exception ignored) {
        }
        return p;
    }

    private record GoogleProfile(String sub, String email, String name) {}

    private static final class CallbackHandler implements HttpHandler {
        private final String expectedState;
        private final CompletableFuture<Map<String, String>> future;

        private CallbackHandler(String expectedState, CompletableFuture<Map<String, String>> future) {
            this.expectedState = expectedState;
            this.future = future;
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                String rawQuery = exchange.getRequestURI().getRawQuery();
                Map<String, String> q = parseQuery(rawQuery);
                String state = q.get("state");
                if (state == null || !state.equals(expectedState)) {
                    write(exchange, 400, "State invalide.");
                    if (!future.isDone()) future.completeExceptionally(new IllegalStateException("State invalide"));
                    return;
                }
                write(exchange, 200, "Connexion Google terminee. Vous pouvez revenir a l'application.");
                if (!future.isDone()) future.complete(q);
            } catch (Exception e) {
                try {
                    write(exchange, 500, "Erreur OAuth.");
                } catch (Exception ignored) {
                }
                if (!future.isDone()) future.completeExceptionally(e);
            }
        }

        private static Map<String, String> parseQuery(String raw) {
            java.util.HashMap<String, String> map = new java.util.HashMap<>();
            if (raw == null || raw.isBlank()) return map;
            String[] pairs = raw.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf('=');
                if (idx <= 0) continue;
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
            return map;
        }

        private static void write(HttpExchange ex, int status, String msg) throws Exception {
            byte[] body = msg.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }
}

