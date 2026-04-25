package org.example.services;

import jakarta.mail.MessagingException;
import org.example.models.Event;
import org.example.models.EventRegistration;
import org.example.models.User;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Envoi d'un e-mail "inscription acceptée" avec QR ticket.
 */
public class EventRegistrationTicketEmailService {

    private final EventReminderEmailService smtpMailer = new EventReminderEmailService();

    public void sendAcceptedRegistrationTicket(User user, Event event, EventRegistration registration)
            throws MessagingException {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new MessagingException("Destinataire e-mail manquant.");
        }
        if (event == null) {
            throw new MessagingException("Événement manquant.");
        }
        if (registration == null) {
            throw new MessagingException("Inscription manquante.");
        }
        smtpMailer.sendHtmlEmail(
                user.getEmail().trim(),
                "Inscription acceptée + QR ticket - " + safe(event.getTitre(), "Événement AutiCare"),
                buildHtmlTemplate(user, event, registration));
    }

    private static String buildHtmlTemplate(User user, Event event, EventRegistration registration) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
        String name = safe((user.getPrenom() == null ? "" : user.getPrenom()) + " " + (user.getNom() == null ? "" : user.getNom()), "Participant");
        String title = safe(event.getTitre(), "Événement");
        String mode = safe(event.getModeEvenement(), "—");
        String when = event.getDateDebut() != null ? event.getDateDebut().format(dtf) : "Date à confirmer";
        String duration = formatEventDuration(event);
        String lieu = safe(event.getLieu(), "—");
        String zoom = safe(event.getLienZoomVisio(), "");
        String zoomForEmail = normalizeZoomJoinLink(zoom);

        String payload = "AUTICARE|EVENT=" + event.getId()
                + "|REG=" + registration.getId()
                + "|USER=" + registration.getUtilisateurId();
        String autoCheckinUrl = buildAutoCheckinUrl(event.getId(), registration.getId(), registration.getUtilisateurId(), payload);
        String qrContent = autoCheckinUrl != null ? autoCheckinUrl : payload;
        String qrUrl = "https://quickchart.io/qr?size=240&text="
                + URLEncoder.encode(qrContent, StandardCharsets.UTF_8);

        boolean onlineOrHybrid = isOnlineOrHybridMode(mode);
        String locationLine = onlineOrHybrid
                ? (zoomForEmail.isBlank() ? "Lien visio: à venir" : "Lien visio prêt")
                : "Lieu: " + lieu;
        String joinLine = onlineOrHybrid && !zoomForEmail.isBlank()
                ? "<p style=\"margin:0 0 10px 0;\"><a href=\"" + escapeHtml(zoomForEmail)
                + "\" style=\"display:inline-block;padding:10px 14px;background:#2563eb;color:#ffffff;"
                + "text-decoration:none;border-radius:8px;font-weight:700;\">Rejoindre la réunion Zoom</a></p>"
                + "<p style=\"margin:0 0 10px 0;font-size:12px;color:#475569;word-break:break-all;\">"
                + "Lien de secours : <a href=\"" + escapeHtml(zoomForEmail) + "\" style=\"color:#2563eb;\">"
                + escapeHtml(zoomForEmail) + "</a></p>"
                : "";

        String safeName = escapeHtml(name);
        String safeTitle = escapeHtml(title);
        String safeWhen = escapeHtml(when);
        String safeDuration = escapeHtml(duration);
        String safeMode = escapeHtml(mode);
        String safeLocation = escapeHtml(locationLine);
        String safeQrUrl = escapeHtml(qrUrl);
        String safePayload = escapeHtml(payload);

        StringBuilder html = new StringBuilder(2048);
        html.append("<!doctype html>")
                .append("<html lang=\"fr\">")
                .append("<body style=\"margin:0;padding:24px;background:#f3f4f6;font-family:Segoe UI,Arial,sans-serif;color:#111827;\">")
                .append("<div style=\"max-width:680px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;padding:24px;\">")
                .append("<h2 style=\"margin:0 0 10px 0;color:#1e3a8a;\">Inscription confirmée</h2>")
                .append("<p style=\"margin:0 0 14px 0;\">Bonjour ").append(safeName).append(", votre inscription a bien été acceptée.</p>")
                .append("<div style=\"border:1px solid #dbeafe;border-radius:10px;padding:12px 14px;margin-bottom:14px;background:#f8fbff;\">")
                .append("<p style=\"margin:0 0 6px 0;\"><strong>Événement :</strong> ").append(safeTitle).append("</p>")
                .append("<p style=\"margin:0 0 6px 0;\"><strong>Date/heure :</strong> ").append(safeWhen).append("</p>")
                .append("<p style=\"margin:0 0 6px 0;\"><strong>Durée :</strong> ").append(safeDuration).append("</p>")
                .append("<p style=\"margin:0 0 6px 0;\"><strong>Mode :</strong> ").append(safeMode).append("</p>")
                .append("<p style=\"margin:0;\"><strong>").append(safeLocation).append("</strong></p>")
                .append("</div>")
                .append("<p style=\"margin:0 0 8px 0;\">Présentez ce QR code le jour de l'événement :</p>")
                .append("<div style=\"text-align:center;margin:10px 0 10px 0;\">")
                .append("<img src=\"").append(safeQrUrl).append("\" alt=\"QR ticket AutiCare\" style=\"max-width:240px;border:1px solid #e5e7eb;border-radius:8px;padding:8px;background:#fff;\" />")
                .append("</div>")
                .append("<p style=\"margin:0 0 8px 0;font-size:12px;color:#475569;\">Code ticket: ").append(safePayload).append("</p>");
        if (!joinLine.isBlank()) {
            html.append(joinLine);
        }
        html.append("<p style=\"margin:14px 0 0 0;color:#6b7280;\">À bientôt,<br/>L'équipe AutiCare</p>")
                .append("</div>")
                .append("</body>")
                .append("</html>");
        return html.toString();
    }

    private static String buildAutoCheckinUrl(int eventId, int regId, int userId, String payload) {
        String baseUrl = readConfig("auticare.checkin.baseUrl", "AUTICARE_CHECKIN_BASE_URL", "");
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String cleanBase = baseUrl.trim();
        String sep = cleanBase.contains("?") ? "&" : "?";
        return cleanBase + sep
                + "event=" + eventId
                // "reg" peut être transformé en caractère "®" par certains décodeurs QR HTML.
                // On utilise "rid" (registration id) pour éviter ce piège.
                + "&rid=" + regId
                + "&user=" + userId
                + "&code=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);
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
        String fromFile = readClasspathProperty(propKey);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return fallback;
    }

    private static String readClasspathProperty(String key) {
        try (InputStream in = EventRegistrationTicketEmailService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in == null) {
                return null;
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty(key);
        } catch (Exception ignored) {
            return null;
        }
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

    private static String normalizeZoomJoinLink(String rawZoomUrl) {
        if (rawZoomUrl == null || rawZoomUrl.isBlank()) {
            return "";
        }
        String input = rawZoomUrl.trim();
        try {
            URI uri = URI.create(input);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!host.contains("zoom.us")) {
                return input;
            }
            if (path.startsWith("/j/") || path.startsWith("/w/")) {
                return input;
            }
            // Convertit /wc/{meetingId}/start?... vers un lien participant /j/{meetingId}?pwd=...
            if (path.startsWith("/wc/")) {
                String[] parts = path.split("/");
                if (parts.length >= 3) {
                    String meetingId = parts[2];
                    if (!meetingId.isBlank()) {
                        Map<String, String> params = parseQueryParams(uri.getRawQuery());
                        String pwd = params.getOrDefault("pwd", "");
                        if (!pwd.isBlank()) {
                            return "https://zoom.us/j/" + meetingId + "?pwd=" + URLEncoder.encode(pwd, StandardCharsets.UTF_8);
                        }
                        return "https://zoom.us/j/" + meetingId;
                    }
                }
            }
        } catch (Exception ignored) {
            return input;
        }
        return input;
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int i = pair.indexOf('=');
            String k = i >= 0 ? pair.substring(0, i) : pair;
            String v = i >= 0 ? pair.substring(i + 1) : "";
            try {
                k = URLDecoder.decode(k, StandardCharsets.UTF_8);
                v = URLDecoder.decode(v, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // conserve brut
            }
            if (!k.isBlank() && !map.containsKey(k)) {
                map.put(k, v);
            }
        }
        return map;
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
