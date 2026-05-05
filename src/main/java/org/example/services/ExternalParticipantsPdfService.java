package org.example.services;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.example.models.EventRegistration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Génère un PDF via API externe (PDFShift) à partir d'un HTML.
 * Si la clé API n'est pas configurée, l'appel n'est pas tenté.
 */
public class ExternalParticipantsPdfService {

    private static final Properties FILE_CONFIG = loadFileConfig();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter EXPORT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);
    private static final String DEFAULT_LOGO_PATH =
            "C:\\Users\\Administrator\\.cursor\\projects\\c-Users-Administrator-Desktop-Validation-java-Jeudi-lina\\assets\\c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_3412ad5402dd7f23bdba625279c8ee88_images_LOGO_AUTICARE-5f672d89-c4b9-4138-b218-0b70e4dc2383.png";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public Optional<byte[]> generateParticipantsPdf(
            String eventTitle,
            List<ParticipantPdfRow> participants) throws Exception {
        String apiKey = cfg("pdfshift.apiKey", "PDFSHIFT_API_KEY", "");
        if (apiKey.isBlank()) {
            return Optional.empty();
        }
        String endpoint = cfg("pdfshift.endpoint", "PDFSHIFT_ENDPOINT", "https://api.pdfshift.io/v3/convert/pdf");
        String html = buildParticipantsHtml(eventTitle, participants);

        String jsonBody = "{\"source\":\"" + escapeJson(html) + "\"}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(40))
                .header("X-API-Key", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            return Optional.of(res.body());
        }
        return Optional.empty();
    }

    public byte[] generateParticipantsPdfLocally(
            String eventTitle,
            List<ParticipantPdfRow> participants) throws IOException {
        String html = buildParticipantsHtml(eventTitle, participants);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Génération PDF locale impossible.", e);
        }
    }

    private static String buildParticipantsHtml(String eventTitle, List<ParticipantPdfRow> participants) {
        String safeTitle = escapeHtml(eventTitle == null || eventTitle.isBlank() ? "Événement" : eventTitle.trim());
        int participantCount = participants == null ? 0 : participants.size();
        String exportedAt = EXPORT_FMT.format(LocalDateTime.now());
        String logoSrc = resolveLogoSrc();
        String logoBlock = logoSrc.isBlank()
                ? "<span class='logo-badge'>AUTICARE</span>"
                : "<img class='logo-img' src='" + escapeHtml(logoSrc) + "' alt='AutiCare' />";
        int acceptedCount = 0;
        int pendingCount = 0;
        int refusedCount = 0;
        StringBuilder rows = new StringBuilder();
        if (participantCount == 0) {
            rows.append("<tr><td colspan='4' class='empty'>Aucun participant pour le moment.</td></tr>");
        } else {
            for (ParticipantPdfRow p : participants) {
                String normalizedStatus = p.status() == null ? "" : p.status().trim().toUpperCase(Locale.ROOT);
                if ("ACCEPTE".equals(normalizedStatus) || "ACCEPTEE".equals(normalizedStatus) || "ACCEPTED".equals(normalizedStatus)) {
                    acceptedCount++;
                } else if ("EN_ATTENTE".equals(normalizedStatus) || "PENDING".equals(normalizedStatus)) {
                    pendingCount++;
                } else if ("REFUSE".equals(normalizedStatus) || "REFUSEE".equals(normalizedStatus) || "REFUSED".equals(normalizedStatus)) {
                    refusedCount++;
                }
                String statusClass = cssClassForStatus(p.status());
                rows.append("<tr>")
                        .append("<td class='name'>").append(escapeHtml(p.fullName())).append("</td>")
                        .append("<td class='email'>").append(escapeHtml(p.email())).append("</td>")
                        .append("<td class='date'>").append(escapeHtml(p.requestDate())).append("</td>")
                        .append("<td class='status'><span class='badge ").append(statusClass).append("'>")
                        .append(escapeHtml(prettyStatusLabel(p.status())))
                        .append("</span></td>")
                        .append("</tr>");
            }
        }
        return "<?xml version='1.0' encoding='UTF-8'?>"
                + "<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8' />"
                + "<style>"
                + "@page{size:A4;margin:24mm 14mm 18mm 14mm;"
                + "@bottom-right{content:'Page ' counter(page) ' / ' counter(pages);font-size:10px;color:#64748b;}"
                + "}"
                + "body{font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#0f172a}"
                + ".header{margin-bottom:14px;padding-bottom:8px;border-bottom:2px solid #2563eb}"
                + ".brand-row{width:100%;border-collapse:collapse;border:none}"
                + ".brand-row td{border:none;padding:0;vertical-align:middle}"
                + ".brand-left{text-align:left}"
                + ".brand-right{text-align:right}"
                + ".report-label{font-size:10px;color:#64748b}"
                + ".logo-badge{display:inline-block;padding:7px 12px;border-radius:999px;background:#1d4ed8;color:#ffffff;font-weight:700;letter-spacing:.5px;font-size:11px}"
                + ".logo-img{height:78px;max-width:240px;object-fit:contain}"
                + ".title{font-size:22px;font-weight:700;color:#1e3a8a;margin:10px 0 0}"
                + ".subtitle{font-size:13px;color:#334155;margin:6px 0 0}"
                + ".meta{margin:12px 0 18px;padding:10px 12px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px}"
                + ".meta-line{margin:3px 0;color:#334155}"
                + ".meta-label{font-weight:700;color:#0f172a}"
                + ".stats{margin:10px 0 16px}"
                + ".stat-chip{display:inline-block;padding:5px 10px;border-radius:999px;font-size:10px;font-weight:700;margin-right:7px}"
                + ".chip-total{background:#dbeafe;color:#1e3a8a}"
                + ".chip-accepted{background:#dcfce7;color:#166534}"
                + ".chip-pending{background:#fef9c3;color:#854d0e}"
                + ".chip-refused{background:#fee2e2;color:#991b1b}"
                + "table{width:100%;border-collapse:collapse;table-layout:fixed}"
                + "thead{display:table-header-group}"
                + "tfoot{display:table-row-group}"
                + "th,td{border:1px solid #dbe2ea;padding:8px 9px;vertical-align:middle;word-wrap:break-word}"
                + "th{background:#eef2ff;color:#1e3a8a;text-transform:uppercase;font-size:10px;letter-spacing:.4px}"
                + "tbody tr:nth-child(even){background:#f8fafc}"
                + ".name{width:28%}"
                + ".email{width:34%}"
                + ".date{width:22%}"
                + ".status{width:16%;text-align:center}"
                + ".badge{display:inline-block;padding:3px 8px;border-radius:999px;font-size:10px;font-weight:700}"
                + ".badge-accepted{background:#dcfce7;color:#166534}"
                + ".badge-pending{background:#fef9c3;color:#854d0e}"
                + ".badge-refused{background:#fee2e2;color:#991b1b}"
                + ".badge-default{background:#e2e8f0;color:#334155}"
                + ".empty{text-align:center;color:#64748b;padding:16px 10px}"
                + ".footer{margin-top:14px;font-size:10px;color:#64748b;text-align:right}"
                + "</style></head><body>"
                + "<div class='header'>"
                + "<table class='brand-row'><tr>"
                + "<td class='brand-left'>" + logoBlock + "</td>"
                + "<td class='brand-right'><span class='report-label'>Rapport participants</span></td>"
                + "</tr></table>"
                + "<h1 class='title'>Liste des participants</h1>"
                + "<p class='subtitle'>Synthèse des inscriptions à l'événement</p></div>"
                + "<div class='meta'>"
                + "<p class='meta-line'><span class='meta-label'>Événement :</span> " + safeTitle + "</p>"
                + "<p class='meta-line'><span class='meta-label'>Nombre de participants :</span> " + participantCount + "</p>"
                + "<p class='meta-line'><span class='meta-label'>Exporté le :</span> " + escapeHtml(exportedAt) + "</p>"
                + "</div>"
                + "<div class='stats'>"
                + "<span class='stat-chip chip-total'>Total : " + participantCount + "</span>"
                + "<span class='stat-chip chip-accepted'>Acceptées : " + acceptedCount + "</span>"
                + "<span class='stat-chip chip-pending'>En attente : " + pendingCount + "</span>"
                + "<span class='stat-chip chip-refused'>Refusées : " + refusedCount + "</span>"
                + "</div>"
                + "<table><thead><tr><th>Participant</th><th>Email</th><th>Date d'inscription</th><th>Statut</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table>"
                + "<div class='footer'>Document généré automatiquement par AutiCare</div>"
                + "</body></html>";
    }

    private static String resolveLogoSrc() {
        String raw = cfg("pdf.participants.logoPath", "PDF_PARTICIPANTS_LOGO", DEFAULT_LOGO_PATH);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            Path p = Path.of(raw.trim());
            if (Files.exists(p)) {
                return p.toUri().toString();
            }
        } catch (Exception ignored) {
            // On garde le fallback badge texte.
        }
        return "";
    }

    private static String cssClassForStatus(String status) {
        if (status == null) {
            return "badge-default";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ACCEPTE", "ACCEPTEE", "ACCEPTED" -> "badge-accepted";
            case "EN_ATTENTE", "PENDING" -> "badge-pending";
            case "REFUSE", "REFUSEE", "REFUSED" -> "badge-refused";
            default -> "badge-default";
        };
    }

    private static String prettyStatusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "Inconnu";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ACCEPTE", "ACCEPTEE", "ACCEPTED" -> "Acceptée";
            case "EN_ATTENTE", "PENDING" -> "En attente";
            case "REFUSE", "REFUSEE", "REFUSED" -> "Refusée";
            default -> status;
        };
    }

    private static String cfg(String key, String env, String defVal) {
        String s = System.getProperty(key);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        s = System.getenv(env);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        s = FILE_CONFIG.getProperty(key);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        return defVal;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = ExternalParticipantsPdfService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // repli silencieux
        }
        return p;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
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

    public record ParticipantPdfRow(String fullName, String email, String requestDate, String status) {
        public static ParticipantPdfRow of(String fullName, String email, EventRegistration r) {
            String when = r != null && r.getDateInscription() != null ? DATE_FMT.format(r.getDateInscription()) : "—";
            String st = r != null && r.getStatut() != null ? r.getStatut().name() : "—";
            return new ParticipantPdfRow(
                    fullName == null || fullName.isBlank() ? "—" : fullName.trim(),
                    email == null || email.isBlank() ? "—" : email.trim(),
                    when,
                    st);
        }
    }
}
