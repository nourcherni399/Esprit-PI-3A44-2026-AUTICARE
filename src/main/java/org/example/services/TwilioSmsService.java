package org.example.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Envoi de SMS via l’API REST Twilio (aucune dépendance Maven : {@link java.net.http.HttpClient}).
 */
public final class TwilioSmsService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static volatile String lastErrorDetail = "";

    private TwilioSmsService() {
    }

    public static boolean isEnabled() {
        String v = SmtpMailUtil.readConfig("twilio.enabled", "TWILIO_ENABLED", "false").trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    public static boolean isConfigured() {
        if (!isEnabled()) {
            return false;
        }
        return !accountSid().isBlank() && !authToken().isBlank() && !fromPhone().isBlank();
    }

    private static String accountSid() {
        return SmtpMailUtil.readConfig("twilio.accountSid", "TWILIO_ACCOUNT_SID", "").trim();
    }

    private static String authToken() {
        return SmtpMailUtil.readConfig("twilio.authToken", "TWILIO_AUTH_TOKEN", "").trim();
    }

    private static String fromPhone() {
        return SmtpMailUtil.readConfig("twilio.fromPhone", "TWILIO_FROM_PHONE", "").trim();
    }

    /**
     * Indicatif pays par défaut (chiffres, sans +), ex. {@code 216} pour la Tunisie, si le numéro stocké est national.
     */
    public static String defaultCallingCodeDigits() {
        return SmtpMailUtil.readConfig("twilio.defaultCallingCode", "TWILIO_DEFAULT_CALLING_CODE", "216")
                .trim().replaceAll("\\D", "");
    }

    /**
     * Normalise un numéro saisi en base vers l’E.164 attendu par Twilio.
     */
    public static String toE164(String rawPhone, String defaultCallingCodeDigits) {
        if (rawPhone == null) {
            return null;
        }
        String t = rawPhone.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.startsWith("+")) {
            String d = t.substring(1).replaceAll("\\D", "");
            return d.isEmpty() ? null : "+" + d;
        }
        String digits = t.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }
        while (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        String cc = defaultCallingCodeDigits != null ? defaultCallingCodeDigits.replaceAll("\\D", "") : "";
        if (!cc.isEmpty() && !digits.startsWith(cc)) {
            digits = cc + digits;
        }
        return "+" + digits;
    }

    /**
     * @return {@code true} si Twilio a accepté la requête (HTTP 2xx)
     */
    public static boolean sendSms(String toE164, String body) {
        lastErrorDetail = "";
        if (!isConfigured()) {
            lastErrorDetail = "Twilio non configuré (enabled/accountSid/authToken/fromPhone).";
            return false;
        }
        if (toE164 == null || toE164.isBlank() || body == null || body.isBlank()) {
            lastErrorDetail = "Numéro destinataire ou message vide.";
            return false;
        }
        String sid = accountSid();
        String token = authToken();
        String from = fromPhone();
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json";
        String form = "To=" + enc(toE164.trim())
                + "&From=" + enc(from)
                + "&Body=" + enc(body);
        String basic = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                String b = resp.body() != null ? resp.body() : "";
                String snippet = b.length() > 400 ? b.substring(0, 400) + "…" : b;
                if (snippet.contains("\"code\":21608")) {
                    lastErrorDetail = "Code 21608: numéro destinataire non vérifié (compte Twilio trial).";
                    System.err.println("Twilio SMS refusé (21608) : numéro destinataire non vérifié sur compte trial. "
                            + "Vérifiez le numéro dans Twilio Console > Verified Caller IDs. Détail: " + snippet);
                } else if (snippet.contains("\"code\":21211")) {
                    lastErrorDetail = "Code 21211: numéro destinataire invalide (format E.164 requis, ex: +216XXXXXXXX).";
                    System.err.println("Twilio SMS refusé (21211) : numéro destinataire invalide. "
                            + "Vérifiez le format E.164 (ex: +216XXXXXXXX). Détail: " + snippet);
                } else if (snippet.contains("\"code\":21606")) {
                    lastErrorDetail = "Code 21606: numéro Twilio émetteur invalide ou non autorisé SMS.";
                    System.err.println("Twilio SMS refusé (21606) : numéro émetteur (From) invalide/non-SMS. "
                            + "Vérifiez twilio.fromPhone. Détail: " + snippet);
                } else {
                    lastErrorDetail = "HTTP " + resp.statusCode() + " Twilio: " + snippet;
                    System.err.println("Twilio SMS HTTP " + resp.statusCode() + " : " + snippet);
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            lastErrorDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            System.err.println("Twilio SMS : " + e.getMessage());
            return false;
        }
    }

    public static String getLastErrorDetail() {
        return lastErrorDetail == null ? "" : lastErrorDetail;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
