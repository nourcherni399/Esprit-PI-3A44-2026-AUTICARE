package org.example.services;

import org.example.models.Appointment;
import org.example.models.User;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lien Google Agenda (action TEMPLATE) pour le médecin : ouverture du navigateur avec l’événement prérempli
 * lorsqu’aucun jeton API n’est configuré. Les textes titre / détails sont partagés avec {@link GoogleCalendarApiService}.
 */
public final class GoogleCalendarTemplateLinks {

    private static final DateTimeFormatter GCAL_DATES = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final int MAX_DETAILS_LEN = 1200;

    private GoogleCalendarTemplateLinks() {
    }

    public static ZoneId calendarZoneId() {
        String id = SmtpMailUtil.readConfig(
                "auticare.calendar.timezone", "AUTICARE_CALENDAR_TZ", "Africa/Tunis").trim();
        if (id.isBlank()) {
            id = "Africa/Tunis";
        }
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return ZoneId.of("Africa/Tunis");
        }
    }

    public static LocalDateTime resolveEndForCalendar(LocalDateTime debut, LocalDateTime slotEnd) {
        if (slotEnd != null) {
            return slotEnd;
        }
        if (debut != null) {
            return debut.plusMinutes(30);
        }
        return null;
    }

    public static String buildGoogleCalendarTemplateUrl(
            LocalDateTime start,
            LocalDateTime end,
            String title,
            String details,
            String location) {
        if (start == null || end == null) {
            return "";
        }
        LocalDateTime endEff = end.isAfter(start) ? end : start.plusMinutes(30);
        ZoneId z = calendarZoneId();
        ZonedDateTime zs = start.atZone(z);
        ZonedDateTime ze = endEff.atZone(z);
        String dates = zs.format(GCAL_DATES) + "/" + ze.format(GCAL_DATES);
        String t = title != null ? title.trim() : "";
        String d = truncate(details != null ? details.trim() : "", MAX_DETAILS_LEN);
        String loc = location != null ? location.trim() : "";
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + enc(t)
                + "&dates=" + enc(dates)
                + "&ctz=" + enc(z.getId())
                + "&details=" + enc(d)
                + "&location=" + enc(loc);
    }

    /** Titre d’événement (lien template ou API Calendar). */
    public static String calendarTitleForAcceptedRdv(String patientLabel) {
        return "AutiCare — RDV : " + (patientLabel == null || patientLabel.isBlank() ? "Patient" : patientLabel.trim());
    }

    /** Description (détails) alignée sur le lien template. */
    public static String calendarDetailsForAcceptedRdv(Appointment appt, User medecin, String patientLabel) {
        StringBuilder details = new StringBuilder();
        details.append("Rendez-vous patient (AutiCare).\n");
        String self = medecin != null ? shortMedecinLabel(medecin) : "";
        if (!self.isBlank()) {
            details.append("Praticien : ").append(self).append("\n");
        }
        if (appt != null && appt.getMotif() != null && !appt.getMotif().isBlank()) {
            details.append("Motif : ").append(appt.getMotif().trim());
        }
        return details.toString();
    }

    public static String buildMedecinConfirmationUrl(
            LocalDateTime debut,
            LocalDateTime fin,
            String patientLabel,
            String praticienSelf,
            String lieuPhysique,
            String cabinetNom,
            String motif) {
        String title = calendarTitleForAcceptedRdv(patientLabel);
        StringBuilder details = new StringBuilder();
        details.append("Rendez-vous patient (AutiCare).\n");
        if (praticienSelf != null && !praticienSelf.isBlank()) {
            details.append("Praticien : ").append(praticienSelf.trim()).append("\n");
        }
        if (motif != null && !motif.isBlank()) {
            details.append("Motif : ").append(motif.trim());
        }
        String location = formatLocationForCalendar(lieuPhysique, cabinetNom);
        return buildGoogleCalendarTemplateUrl(debut, fin, title, details.toString(), location);
    }

    public static String formatLocationForCalendar(String adr, String cab) {
        String a = adr != null ? adr.trim() : "";
        String c = cab != null ? cab.trim() : "";
        if (!a.isBlank() && !c.isBlank() && !a.equalsIgnoreCase(c)) {
            return c + ", " + a;
        }
        if (!a.isBlank()) {
            return a;
        }
        return c;
    }

    public static void browseIfPossible(String googleCalendarUrl) {
        if (googleCalendarUrl == null || googleCalendarUrl.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(googleCalendarUrl));
            }
        } catch (Exception ignored) {
            /* Ne pas bloquer le flux RDV. */
        }
    }

    public static void browseMedecinAddAfterAccept(Appointment appt, LocalDateTime slotEnd, User medecin, String patientLabel) {
        if (appt == null || appt.getDateHeure() == null) {
            return;
        }
        LocalDateTime end = resolveEndForCalendar(appt.getDateHeure(), slotEnd);
        String adr = medecin != null && medecin.getAdresse() != null ? medecin.getAdresse().trim() : "";
        String cab = medecin != null && medecin.getCabinet() != null ? medecin.getCabinet().trim() : "";
        String self = medecin != null ? shortMedecinLabel(medecin) : "";
        String url = buildMedecinConfirmationUrl(
                appt.getDateHeure(), end, patientLabel, self, adr, cab, appt.getMotif());
        browseIfPossible(url);
    }

    public static String shortMedecinLabel(User medecin) {
        if (medecin == null) {
            return "";
        }
        String p = medecin.getPrenom() != null ? medecin.getPrenom().trim() : "";
        String n = medecin.getNom() != null ? medecin.getNom().trim() : "";
        String both = (p + " " + n).trim();
        return both.isEmpty() ? "Moi" : both;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max - 1) + "…";
    }
}
