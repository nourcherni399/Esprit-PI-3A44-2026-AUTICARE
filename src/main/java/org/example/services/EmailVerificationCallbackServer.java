package org.example.services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Petit serveur local pour traiter les clics du lien d'activation email.
 */
public class EmailVerificationCallbackServer implements AutoCloseable {

    private static final Properties FILE_CONFIG = loadFileConfig();

    private final UserService userService = new UserService();
    private HttpServer server;

    public void start() throws Exception {
        if (server != null) return;
        URI callback = URI.create(readCallbackUrl());
        String host = callback.getHost() == null ? "127.0.0.1" : callback.getHost();
        int port = callback.getPort() > 0 ? callback.getPort() : 8899;
        String path = callback.getPath() == null || callback.getPath().isBlank() ? "/verify-email" : callback.getPath();
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext(path, new VerifyEmailHandler(userService));
        server.start();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /**
     * URL de base du lien dans l’email (sans {@code ?token=}), alignée sur le port d’écoute de ce serveur.
     */
    public static String readCallbackUrl() {
        String prop = System.getProperty("auticare.emailVerification.baseUrl");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("AUTICARE_EMAIL_VERIFICATION_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String file = FILE_CONFIG.getProperty("auticare.emailVerification.baseUrl");
        if (file != null && !file.isBlank()) {
            return file.trim();
        }
        return "http://127.0.0.1:8899/verify-email";
    }

    /** Lien complet à mettre dans l’email (même base que {@link #readCallbackUrl()}). */
    public static String buildVerifyUrl(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token");
        }
        String base = readCallbackUrl();
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "token=" + URLEncoder.encode(token.trim(), StandardCharsets.UTF_8);
    }

    /**
     * Si {@code false}, l’app n’exige pas l’email pour activer le compte (inscription immédiatement active).
     */
    public static boolean isEmailVerificationEnabled() {
        String v = firstNonBlank(
                System.getProperty("auticare.emailVerification.enabled"),
                System.getenv("AUTICARE_EMAIL_VERIFICATION_ENABLED"),
                FILE_CONFIG.getProperty("auticare.emailVerification.enabled"));
        if (v == null || v.isBlank()) {
            return true;
        }
        return !"false".equalsIgnoreCase(v.trim());
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        if (c != null && !c.isBlank()) {
            return c;
        }
        return null;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = EmailVerificationCallbackServer.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        return p;
    }

    private static final class VerifyEmailHandler implements HttpHandler {
        private final UserService userService;

        private VerifyEmailHandler(UserService userService) {
            this.userService = userService;
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
                String token = q.get("token");
                boolean ok = false;
                if (token != null && !token.isBlank()) {
                    ok = userService.activateByEmailVerificationToken(token);
                }
                String html = buildPage(
                        ok ? "Compte activé" : "Lien invalide ou expiré",
                        ok
                                ? "Votre compte est maintenant actif. Vous pouvez revenir dans l'application AutiCare."
                                : "Ce lien d'activation est invalide ou expiré. Merci de demander un nouveau lien.",
                        ok
                );
                write(exchange, 200, html);
            } catch (SQLException e) {
                try {
                    write(exchange, 500, buildPage(
                            "Erreur serveur",
                            "Une erreur base de données est survenue pendant l'activation.",
                            false
                    ));
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                try {
                    write(exchange, 500, buildPage(
                            "Erreur d'activation",
                            "Une erreur interne est survenue pendant la vérification du lien.",
                            false
                    ));
                } catch (Exception ignored) {
                }
            }
        }

        private static String buildPage(String title, String message, boolean success) {
            String badge = success ? "SUCCES" : "ECHEC";
            String badgeColor = success ? "#1f9d62" : "#c2410c";
            String accent = success ? "#16a34a" : "#dc2626";
            String icon = success ? "&#10003;" : "&#9888;";
            return """
                    <!doctype html>
                    <html lang="fr">
                    <head>
                      <meta charset="utf-8"/>
                      <meta name="viewport" content="width=device-width,initial-scale=1"/>
                      <title>AutiCare - Activation</title>
                      <style>
                        :root { color-scheme: light; }
                        body {
                          margin: 0;
                          min-height: 100vh;
                          display: grid;
                          place-items: center;
                          font-family: "Segoe UI", Arial, sans-serif;
                          background: linear-gradient(135deg, #eaf5fb 0%%, #f8fbff 45%%, #ffffff 100%%);
                          color: #0f172a;
                        }
                        .card {
                          width: min(680px, 92vw);
                          border-radius: 16px;
                          background: #ffffff;
                          border: 1px solid #dbe7f1;
                          box-shadow: 0 14px 45px rgba(15, 61, 83, 0.14);
                          overflow: hidden;
                        }
                        .top {
                          padding: 18px 24px;
                          background: linear-gradient(90deg, #0f3d53 0%%, #136086 100%%);
                          color: #ffffff;
                          font-weight: 700;
                          letter-spacing: 0.2px;
                        }
                        .content {
                          padding: 28px 24px 24px;
                        }
                        .badge {
                          display: inline-block;
                          font-size: 12px;
                          font-weight: 700;
                          color: #ffffff;
                          background: %s;
                          border-radius: 999px;
                          padding: 6px 10px;
                          margin-bottom: 14px;
                        }
                        h1 {
                          margin: 0;
                          font-size: 28px;
                          line-height: 1.2;
                          color: %s;
                          display: flex;
                          align-items: center;
                          gap: 10px;
                        }
                        p {
                          margin: 14px 0 0;
                          font-size: 15px;
                          line-height: 1.65;
                          color: #334155;
                        }
                        .hint {
                          margin-top: 16px;
                          font-size: 13px;
                          color: #64748b;
                        }
                      </style>
                    </head>
                    <body>
                      <div class="card">
                        <div class="top">AutiCare - Verification d'email</div>
                        <div class="content">
                          <span class="badge">%s</span>
                          <h1><span>%s</span> %s</h1>
                          <p>%s</p>
                          <p class="hint">Vous pouvez fermer cette page et retourner dans l'application AutiCare.</p>
                        </div>
                      </div>
                    </body>
                    </html>
                    """.formatted(badgeColor, accent, badge, icon, title, message);
        }

        private static Map<String, String> parseQuery(String raw) {
            HashMap<String, String> out = new HashMap<>();
            if (raw == null || raw.isBlank()) return out;
            for (String pair : raw.split("&")) {
                int idx = pair.indexOf('=');
                if (idx <= 0) continue;
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                out.put(k, v);
            }
            return out;
        }

        private static void write(HttpExchange ex, int status, String html) throws Exception {
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }
}

