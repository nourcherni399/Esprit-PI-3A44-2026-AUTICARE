package org.example.services;

import org.example.models.Appointment;
import org.example.models.User;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Création d’événements sur l’agenda principal Google via refresh token (offline).
 */
public final class GoogleCalendarApiService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    private static final DateTimeFormatter LOCAL_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private GoogleCalendarApiService() {
    }

    /**
     * Crée un événement « RDV AutiCare » sur le calendrier principal.
     *
     * @return {@code true} si l’API a répondu 2xx
     */
    public static boolean tryCreateMedecinRdvEvent(
            String refreshToken,
            Appointment appt,
            LocalDateTime slotEnd,
            User medecin,
            String patientLabel) {
        if (refreshToken == null || refreshToken.isBlank() || appt == null || appt.getDateHeure() == null) {
            return false;
        }
        try {
            String clientId = SmtpMailUtil.readConfig("oauth.google.clientId", "OAUTH_GOOGLE_CLIENT_ID", "").trim();
            String clientSecret = SmtpMailUtil.readConfig("oauth.google.clientSecret", "OAUTH_GOOGLE_CLIENT_SECRET", "").trim();
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                return false;
            }
            String access = refreshAccessToken(refreshToken, clientId, clientSecret);
            if (access == null || access.isBlank()) {
                return false;
            }
            LocalDateTime start = appt.getDateHeure();
            LocalDateTime end = GoogleCalendarTemplateLinks.resolveEndForCalendar(start, slotEnd);
            if (end == null) {
                end = start.plusMinutes(30);
            }
            if (!end.isAfter(start)) {
                end = start.plusMinutes(30);
            }
            ZoneId z = GoogleCalendarTemplateLinks.calendarZoneId();
            String tz = z.getId();
            String title = GoogleCalendarTemplateLinks.calendarTitleForAcceptedRdv(patientLabel);
            String details = GoogleCalendarTemplateLinks.calendarDetailsForAcceptedRdv(appt, medecin, patientLabel);
            String adr = medecin != null && medecin.getAdresse() != null ? medecin.getAdresse().trim() : "";
            String cab = medecin != null && medecin.getCabinet() != null ? medecin.getCabinet().trim() : "";
            String location = GoogleCalendarTemplateLinks.formatLocationForCalendar(adr, cab);

            String json = "{"
                    + "\"summary\":\"" + jsonEsc(title) + "\","
                    + "\"description\":\"" + jsonEsc(details) + "\","
                    + "\"location\":\"" + jsonEsc(location) + "\","
                    + "\"start\":{\"dateTime\":\"" + jsonEsc(LOCAL_DT.format(start)) + "\",\"timeZone\":\"" + jsonEsc(tz) + "\"},"
                    + "\"end\":{\"dateTime\":\"" + jsonEsc(LOCAL_DT.format(end)) + "\",\"timeZone\":\"" + jsonEsc(tz) + "\"}"
                    + "}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(EVENTS_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + access)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                System.err.println("Google Calendar API : HTTP " + resp.statusCode() + " " + truncate(resp.body(), 500));
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("Google Calendar API : " + e.getMessage());
            return false;
        }
    }

    private static String refreshAccessToken(String refreshToken, String clientId, String clientSecret)
            throws java.io.IOException, InterruptedException {
        String body = formBody(Map.of(
                "refresh_token", refreshToken,
                "client_id", clientId,
                "client_secret", clientSecret,
                "grant_type", "refresh_token"));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            System.err.println("Google token refresh : HTTP " + resp.statusCode() + " " + truncate(resp.body(), 400));
            return null;
        }
        Pattern p = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]*)\"");
        var m = p.matcher(resp.body());
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
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

    private static String jsonEsc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
