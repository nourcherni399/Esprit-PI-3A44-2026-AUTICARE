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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

/**
 * Envoi de rappels e-mail pour les participants déjà inscrits à un événement.
 */
public class EventReminderEmailService {

    private static final String DEFAULT_FROM = "no-reply@auticare.local";
    private static final String DEFAULT_HOST = "smtp.gmail.com";
    private static final String DEFAULT_PORT = "587";
    private static final Properties FILE_CONFIG = loadFileConfig();

    public void sendEventReminderEmail(String recipientEmail, String recipientDisplayName, Event event)
            throws MessagingException {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new MessagingException("Destinataire e-mail manquant.");
        }
        if (event == null) {
            throw new MessagingException("Événement manquant pour le rappel.");
        }

        String smtpUser = readConfig("auticare.smtp.user", "AUTICARE_SMTP_USER", DEFAULT_FROM);
        String smtpPasswordRaw = readConfig("auticare.smtp.appPassword", "AUTICARE_SMTP_APP_PASSWORD", "");
        String smtpPassword = smtpPasswordRaw.replaceAll("\\s+", "");
        if (smtpPassword.isBlank()) {
            Path propsPath = locateProjectPropertiesFile();
            throw new MessagingException(
                    "Configuration SMTP manquante: auticare.smtp.appPassword (mot de passe d'application Gmail). "
                            + "[user.dir=" + System.getProperty("user.dir", "?")
                            + ", propsPath=" + propsPath
                            + ", exists=" + Files.exists(propsPath) + "]");
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
        message.setSubject("Rappel - " + safe(event.getTitre(), "Événement AutiCare"));
        message.setContent(buildHtmlTemplate(recipientDisplayName, event), "text/html; charset=UTF-8");
        Transport.send(message);
    }

    private static String buildHtmlTemplate(String recipientDisplayName, Event event) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
        String when = event.getDateDebut() != null ? event.getDateDebut().format(dtf) : "Date à confirmer";
        String mode = safe(event.getModeEvenement(), "—");
        String titre = safe(event.getTitre(), "Événement");
        String lieu = safe(event.getLieu(), "—");
        String zoom = safe(event.getLienZoomVisio(), "");
        String maps = safe(event.getLienGoogleMaps(), "");
        String name = safe(recipientDisplayName, "Participant");

        String locationLine = "En ligne".equalsIgnoreCase(mode)
                ? (zoom.isBlank() ? "Lien visio: à venir" : "Lien visio: " + zoom)
                : ("Lieu: " + lieu);

        String links = "";
        if (!zoom.isBlank()) {
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
        String fromFile = FILE_CONFIG.getProperty(propKey);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        String fromProjectFile = readFromProjectProperties(propKey);
        if (fromProjectFile != null && !fromProjectFile.isBlank()) {
            return fromProjectFile.trim();
        }
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = EventReminderEmailService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // fallback env/system/default
        }
        return p;
    }

    private static String readFromProjectProperties(String key) {
        try {
            Path p = locateProjectPropertiesFile();
            if (!Files.exists(p)) {
                return null;
            }
            Properties props = new Properties();
            try (var in = Files.newInputStream(p)) {
                props.load(in);
            }
            return props.getProperty(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path locateProjectPropertiesFile() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path cur = cwd;
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.exists(cur.resolve("pom.xml"))) {
                return cur.resolve("src").resolve("main").resolve("resources").resolve("application.properties");
            }
            cur = cur.getParent();
        }
        return cwd.resolve("src").resolve("main").resolve("resources").resolve("application.properties");
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
}
