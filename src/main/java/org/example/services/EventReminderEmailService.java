package org.example.services;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.example.models.Event;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Envoi de rappels e-mail pour les participants déjà inscrits à un événement.
 */
public class EventReminderEmailService {

    private static final String DEFAULT_FROM = "no-reply@auticare.local";
    private static final String DEFAULT_HOST = "smtp.gmail.com";
    private static final String DEFAULT_PORT = "587";
    public void sendEventReminderEmail(String recipientEmail, String recipientDisplayName, Event event)
            throws MessagingException {
        String title = safe(event != null ? event.getTitre() : null, "Événement AutiCare");
        sendHtmlEmail(
                recipientEmail,
                "Rappel - " + title,
                buildHtmlTemplate(recipientDisplayName, event));
    }

    /**
     * Envoi SMTP générique HTML.
     */
    public void sendHtmlEmail(String recipientEmail, String subject, String htmlBody) throws MessagingException {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new MessagingException("Destinataire e-mail manquant.");
        }
        String safeSubject = subject == null || subject.isBlank() ? "Notification AutiCare" : subject.trim();
        String safeHtml = htmlBody == null ? "" : htmlBody;

        String smtpUser = readConfig("auticare.smtp.user", "AUTICARE_SMTP_USER", DEFAULT_FROM);
        String smtpPasswordRaw = readConfig("auticare.smtp.appPassword", "AUTICARE_SMTP_APP_PASSWORD", "");
        String smtpPassword = sanitizeSecret(smtpPasswordRaw);
        if (smtpPassword.isBlank()) {
            Path propsPath = locateProjectPropertiesFile();
            throw new MessagingException(
                    "Configuration SMTP manquante: auticare.smtp.appPassword (mot de passe d'application Gmail). "
                            + "[user.dir=" + System.getProperty("user.dir", "?")
                            + ", propsPath=" + propsPath
                            + ", exists=" + Files.exists(propsPath)
                            + ", cpHasKey=" + hasValue(readFromFreshClasspathProperties("auticare.smtp.appPassword"))
                            + ", srcHasKey=" + hasValue(readFromProjectProperties("auticare.smtp.appPassword"))
                            + ", knownHasKey=" + hasValue(readFromKnownPropertyFiles("auticare.smtp.appPassword"))
                            + "]");
        }

        String smtpHost = readConfig("auticare.smtp.host", "AUTICARE_SMTP_HOST", DEFAULT_HOST);
        String smtpPort = readConfig("auticare.smtp.port", "AUTICARE_SMTP_PORT", DEFAULT_PORT);

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.auth.mechanisms", "LOGIN");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });
        String debug = readConfig("auticare.smtp.debug", "AUTICARE_SMTP_DEBUG", "false");
        if ("true".equalsIgnoreCase(debug)) {
            session.setDebug(true);
        }

        String to = recipientEmail.trim();
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        message.setSubject(safeSubject);
        String finalHtml = safeHtml.isBlank()
                ? "<html><body><p>Notification AutiCare</p></body></html>"
                : safeHtml;
        message.setContent(finalHtml, "text/html; charset=UTF-8");
        Transport.send(message);
    }

    private static String buildHtmlTemplate(String recipientDisplayName, Event event) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
        String when = event.getDateDebut() != null ? event.getDateDebut().format(dtf) : "Date à confirmer";
        String duration = formatEventDuration(event);
        String mode = safe(event.getModeEvenement(), "—");
        String titre = safe(event.getTitre(), "Événement");
        String lieu = safe(event.getLieu(), "—");
        String zoom = safe(event.getLienZoomVisio(), "");
        String maps = safe(event.getLienGoogleMaps(), "");
        String name = safe(recipientDisplayName, "Participant");
        boolean onlineOrHybrid = isOnlineOrHybridMode(mode);

        String locationLine = onlineOrHybrid
                ? (zoom.isBlank() ? "Lien visio: à venir" : "Lien visio: " + zoom)
                : ("Lieu: " + lieu);

        String links = "";
        if (onlineOrHybrid && !zoom.isBlank()) {
            links += "<p style=\"margin:0 0 8px 0;\"><a href=\"" + escapeHtml(zoom)
                    + "\" style=\"color:#2563eb;\">Rejoindre la réunion</a></p>";
        }
        if (!maps.isBlank()) {
            links += "<p style=\"margin:0 0 8px 0;\"><a href=\"" + escapeHtml(maps)
                    + "\" style=\"color:#2563eb;\">Voir l'adresse sur la carte</a></p>";
        }

        return """
                <!doctype html>
                <html lang="fr">
                <body style="margin:0;padding:24px;background:#f3f4f6;font-family:Segoe UI,Arial,sans-serif;color:#111827;">
                  <div style="max-width:660px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;padding:24px;">
                    <p style="margin:0 0 14px 0;">Bonjour %s,</p>
                    <p style="margin:0 0 12px 0;">Ceci est un rappel pour votre événement déjà inscrit :</p>
                    <h2 style="margin:0 0 12px 0;color:#1e3a8a;">%s</h2>
                    <p style="margin:0 0 6px 0;"><strong>Date/heure :</strong> %s</p>
                    <p style="margin:0 0 6px 0;"><strong>Durée :</strong> %s</p>
                    <p style="margin:0 0 6px 0;"><strong>Mode :</strong> %s</p>
                    <p style="margin:0 0 12px 0;"><strong>%s</strong></p>
                    %s
                    <p style="margin:16px 0 0 0;color:#6b7280;">Merci de votre participation, à très bientôt.</p>
                    <p style="margin:8px 0 0 0;color:#6b7280;">— L'équipe AutiCare</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(name),
                escapeHtml(titre),
                escapeHtml(when),
                escapeHtml(duration),
                escapeHtml(mode),
                escapeHtml(locationLine),
                links);
    }

    private static String readConfig(String propKey, String envKey, String fallback) {
        String fromProp = System.getProperty(propKey);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = readFromFreshClasspathProperties(propKey);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        String fromProjectFile = readFromProjectProperties(propKey);
        if (fromProjectFile != null && !fromProjectFile.isBlank()) {
            return fromProjectFile.trim();
        }
        String fromKnownFiles = readFromKnownPropertyFiles(propKey);
        if (fromKnownFiles != null && !fromKnownFiles.isBlank()) {
            return fromKnownFiles.trim();
        }
        String fromAlias = readFromAliasKeys(propKey);
        if (fromAlias != null && !fromAlias.isBlank()) {
            return fromAlias.trim();
        }
        return fallback;
    }

    private static String readFromFreshClasspathProperties(String key) {
        try (InputStream in = EventReminderEmailService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty(key);
            }
        } catch (Exception ignored) {
            // fallback env/system/default
        }
        return null;
    }

    private static String readFromProjectProperties(String key) {
        try {
            Path p = locateProjectPropertiesFile();
            if (!Files.exists(p)) {
                return null;
            }
            return readPropertyByScanningLines(p, key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readFromKnownPropertyFiles(String key) {
        try {
            Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            List<Path> candidates = new ArrayList<>();
            candidates.add(cwd.resolve("src").resolve("main").resolve("resources").resolve("application.properties"));
            candidates.add(cwd.resolve("target").resolve("classes").resolve("application.properties"));
            Path cur = cwd;
            for (int i = 0; i < 10 && cur != null; i++) {
                candidates.add(cur.resolve("src").resolve("main").resolve("resources").resolve("application.properties"));
                candidates.add(cur.resolve("target").resolve("classes").resolve("application.properties"));
                cur = cur.getParent();
            }
            for (Path p : candidates) {
                if (!Files.exists(p)) {
                    continue;
                }
                String value = readPropertyByScanningLines(p, key);
                if (value != null && !value.isBlank()) {
                    return value;
                }
                if ("auticare.smtp.appPassword".equals(key)) {
                    String fuzzy = readSmtpPasswordFuzzy(p);
                    if (fuzzy != null && !fuzzy.isBlank()) {
                        return fuzzy;
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String readFromAliasKeys(String key) {
        if (!"auticare.smtp.appPassword".equals(key)) {
            return null;
        }
        String[] aliases = new String[] {
                "auticare.smtp.apppassword",
                "auticare.smtp.app.password",
                "auticare.smtp.app_password",
                "auticare.smtp.gmail.appPassword",
                "auticare.smtp.gmail.apppassword"
        };
        for (String alias : aliases) {
            String fromCp = readFromFreshClasspathProperties(alias);
            if (hasValue(fromCp)) {
                return fromCp;
            }
            String fromSrc = readFromProjectProperties(alias);
            if (hasValue(fromSrc)) {
                return fromSrc;
            }
            String fromKnown = readFromKnownPropertyFiles(alias);
            if (hasValue(fromKnown)) {
                return fromKnown;
            }
        }
        try {
            Path src = locateProjectPropertiesFile();
            String fuzzy = readSmtpPasswordFuzzy(src);
            if (hasValue(fuzzy)) {
                return fuzzy;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String readPropertyByScanningLines(Path file, String key) {
        String normalizedKey = normalizeKeyForMatch(key);
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (raw == null) {
                    continue;
                }
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = line.substring(0, eq);
                if (!normalizeKeyForMatch(k).equals(normalizedKey)) {
                    continue;
                }
                return line.substring(eq + 1).trim();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String readSmtpPasswordFuzzy(Path file) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (raw == null) {
                    continue;
                }
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String lhs = normalizeKeyForMatch(line.substring(0, eq));
                // Fallback volontairement tolérant aux caractères invisibles/variantes.
                if (lhs.contains("auticare")
                        && lhs.contains("smtp")
                        && lhs.contains("app")
                        && lhs.contains("password")) {
                    return line.substring(eq + 1).trim();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String normalizeKeyForMatch(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("\uFEFF", "")
                .replace(" ", "")
                .replace("\t", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String sanitizeSecret(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\u00A0", "")
                .replace("\u2007", "")
                .replace("\u202F", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private static boolean hasValue(String value) {
        return value != null && !sanitizeSecret(value).isBlank();
    }

    private static Path locateProjectPropertiesFile() {
        List<Path> roots = new ArrayList<>();
        String mm = System.getProperty("maven.multiModuleProjectDirectory");
        if (mm != null && !mm.isBlank()) {
            roots.add(Path.of(mm));
        }
        String basedir = System.getProperty("basedir");
        if (basedir != null && !basedir.isBlank()) {
            roots.add(Path.of(basedir));
        }
        roots.add(Path.of(System.getProperty("user.dir", ".")));

        for (Path rootCandidate : roots) {
            Path cur = rootCandidate.toAbsolutePath().normalize();
            for (int i = 0; i < 10 && cur != null; i++) {
                if (Files.exists(cur.resolve("pom.xml"))) {
                    return cur.resolve("src").resolve("main").resolve("resources").resolve("application.properties");
                }
                cur = cur.getParent();
            }
        }
        return Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("application.properties");
    }

    private static String safe(String s, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        return s.trim();
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String formatEventDuration(Event event) {
        if (event == null || event.getDateDebut() == null || event.getDateFin() == null) {
            return "Non précisée";
        }
        long minutes = Duration.between(event.getDateDebut(), event.getDateFin()).toMinutes();
        if (minutes <= 0) {
            return "Non précisée";
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours > 0 && mins > 0) {
            return hours + " h " + mins + " min";
        }
        if (hours > 0) {
            return hours + " h";
        }
        return mins + " min";
    }

    private static boolean isOnlineOrHybridMode(String mode) {
        if (mode == null) {
            return false;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("en ligne") || normalized.contains("hybride");
    }
}
