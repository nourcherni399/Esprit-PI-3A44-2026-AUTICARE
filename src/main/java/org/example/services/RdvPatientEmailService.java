package org.example.services;

import jakarta.mail.MessagingException;
import org.example.models.Appointment;
import org.example.models.Availability;
import org.example.models.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * E-mails patient : demande de RDV enregistrée, confirmation après acceptation médecin.
 */
public class RdvPatientEmailService {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy", FR);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", FR);

    private final UserService userService = new UserService();
    /** Même service / même compte SMTP que « mot de passe oublié » ({@link PasswordRecoveryEmailService}). */
    private final PasswordRecoveryEmailService transactionalMail = new PasswordRecoveryEmailService();

    /**
     * Après envoi de la demande (statut EN_ATTENTE) — ne bloque pas le parcours si l’e-mail échoue.
     *
     * @param emailFormulaire e-mail saisi sur le formulaire (prioritaire pour l’envoi)
     * @return vide si OK, sinon message d’erreur à afficher à l’utilisateur
     */
    public Optional<String> trySendDemandeEnregistree(Appointment appt, Availability slot, String emailFormulaire) {
        try {
            sendDemandeEnregistree(appt, slot, emailFormulaire);
            return Optional.empty();
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return Optional.of(Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /**
     * Après acceptation médecin — ne bloque pas si l’e-mail échoue.
     *
     * @return vide si OK, sinon message d’erreur
     */
    public Optional<String> trySendConfirmation(Appointment appt, LocalDateTime slotEnd) {
        try {
            sendConfirmation(appt, slotEnd);
            return Optional.empty();
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return Optional.of(Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /**
     * @param emailFormulaire adresse saisie au parcours public (utilisée en priorité pour le destinataire)
     */
    public void sendDemandeEnregistree(Appointment appt, Availability slot, String emailFormulaire) throws Exception {
        if (appt == null) {
            return;
        }
        User patient = userService.findById(appt.getPatientId()).orElse(null);
        User medecin = userService.findById(appt.getMedecinId()).orElse(null);
        String to = firstNonBlank(
                emailFormulaire != null ? emailFormulaire.trim() : null,
                patient != null ? patient.getEmail() : null,
                "");
        if (to.isBlank()) {
            throw new MessagingException("Aucune adresse e-mail pour envoyer la confirmation de demande.");
        }
        String prenom = firstNonBlank(
                appt.getPatientPrenom(),
                patient != null ? patient.getPrenom() : null,
                "Patient");
        String dr = formatPraticienName(medecin);
        LocalDateTime debut = appt.getDateHeure() != null ? appt.getDateHeure() : (slot != null ? slot.getDebut() : null);
        LocalDateTime fin = slot != null && slot.getFin() != null ? slot.getFin() : (debut != null ? debut.plusMinutes(30) : null);
        String dateStr = debut != null ? debut.format(DATE_FR) : "—";
        String horaires = formatHoraires(debut, fin);
        Optional<String> manageUrl = buildManageUrl(appt.getGestionToken());
        String cabinetEmail = firstNonBlank(
                SmtpMailUtil.readConfig(
                        "auticare.rdv.contact.cabinetEmail", "AUTICARE_RDV_CABINET_EMAIL", "").trim(),
                medecin != null ? medecin.getEmail() : null,
                "");
        String telBrut = medecin != null ? medecin.getTelephone() : null;
        String html = htmlDemandeEnregistree(
                escapeHtml(prenom), escapeHtml(dr), escapeHtml(dateStr), escapeHtml(horaires),
                manageUrl,
                prenom, dr, dateStr, horaires,
                appt.getGestionToken(),
                cabinetEmail,
                telBrut);
        transactionalMail.sendTransactionalHtml(to, "AutiCare — Demande de rendez-vous enregistrée", html);
    }

    public void sendConfirmation(Appointment appt, LocalDateTime slotEnd) throws Exception {
        if (appt == null || appt.getPatientId() <= 0) {
            return;
        }
        User patient = userService.findById(appt.getPatientId()).orElse(null);
        User medecin = userService.findById(appt.getMedecinId()).orElse(null);
        String to = firstNonBlank(
                patient != null ? patient.getEmail() : null,
                extractEmailFromNotes(appt.getNotes()),
                "");
        if (to.isBlank()) {
            throw new MessagingException("E-mail patient introuvable.");
        }
        String prenom = firstNonBlank(appt.getPatientPrenom(), patient.getPrenom(), "Patient");
        String dr = formatPraticienName(medecin);
        LocalDateTime debut = appt.getDateHeure();
        LocalDateTime fin = slotEnd != null ? slotEnd : (debut != null ? debut.plusMinutes(30) : null);
        String dateStr = debut != null ? debut.format(DATE_FR) : "—";
        String horaires = formatHoraires(debut, fin);
        String adr = medecin != null && medecin.getAdresse() != null ? medecin.getAdresse().trim() : "";
        String cab = medecin != null && medecin.getCabinet() != null ? medecin.getCabinet().trim() : "";
        /* Lieu = adresse géographique en priorité ; le champ « cabinet » est plutôt le nom de l’enseigne. */
        String lieuRaw = firstNonBlank(adr, cab, "");
        String lieu = escapeHtml(lieuRaw.isBlank() ? "—" : lieuRaw);
        String cabinetRowHtml = "";
        if (!cab.isBlank() && !adr.isBlank() && !cab.equalsIgnoreCase(adr)) {
            cabinetRowHtml = "<p style=\"margin:0 0 8px 0;font-size:15px;\"><strong>Cabinet :</strong> "
                    + escapeHtml(cab) + "</p>";
        }
        String tel = escapeHtml(firstNonBlank(
                medecin != null ? medecin.getTelephone() : null, null, "—"));
        String html = htmlConfirmation(escapeHtml(prenom), escapeHtml(dr), escapeHtml(dateStr),
                escapeHtml(horaires), cabinetRowHtml, lieu, tel);
        transactionalMail.sendTransactionalHtml(
                to, "AutiCare — Votre rendez-vous est confirmé", html);
    }

    private static Optional<String> buildManageUrl(String gestionToken) {
        if (gestionToken == null || gestionToken.isBlank()) {
            return Optional.empty();
        }
        String base = SmtpMailUtil.readConfig(
                "auticare.rdv.public.manage.baseUrl", "AUTICARE_RDV_MANAGE_BASE_URL", "").trim();
        if (base.isBlank()) {
            return Optional.empty();
        }
        String sep = base.contains("?") ? "&" : "?";
        return Optional.of(base + sep + "token=" + URLEncoder.encode(gestionToken, StandardCharsets.UTF_8));
    }

    private static String appendQueryParam(String url, String key, String value) {
        String enc = URLEncoder.encode(value, StandardCharsets.UTF_8);
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + key + "=" + enc;
    }

    /** Pour attribut HTML {@code href} : les {@code &} des query strings doivent être échappés. */
    private static String hrefAmpersands(String href) {
        return href.replace("&", "&amp;");
    }

    private static String buildMailtoDemandeRdv(String to, String subject, String body) {
        String encSub = URLEncoder.encode(subject, StandardCharsets.UTF_8);
        String encBody = URLEncoder.encode(body, StandardCharsets.UTF_8);
        return "mailto:" + to.trim() + "?subject=" + encSub + "&body=" + encBody;
    }

    private static String corpsMailDemande(String actionLabel, String prenom, String praticien, String dateStr,
                                           String horaires, String gestionToken) {
        String ref = (gestionToken != null && !gestionToken.isBlank())
                ? "Référence (jeton) : " + gestionToken + "\n"
                : "";
        return "Bonjour,\n\n"
                + "Je souhaite " + actionLabel + " mon rendez-vous demandé en ligne (AutiCare).\n\n"
                + "Patient : " + prenom + "\n"
                + "Praticien : " + praticien + "\n"
                + "Date : " + dateStr + "\n"
                + "Créneau : " + horaires + "\n"
                + ref
                + "\nMerci.\n";
    }

    private static String telHref(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return "tel:" + raw.replaceAll("[^0-9+]", "");
    }

    private static String formatPraticienName(User medecin) {
        if (medecin == null) {
            return "Votre praticien";
        }
        String p = medecin.getPrenom() != null ? medecin.getPrenom().trim() : "";
        String n = medecin.getNom() != null ? medecin.getNom().trim() : "";
        String both = (p + " " + n).trim();
        return both.isEmpty() ? "Votre praticien" : both;
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return fallback;
    }

    /**
     * Compatibilité : dans certains parcours publics, l’e-mail peut être conservé uniquement dans notes.
     */
    private static String extractEmailFromNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return "";
        }
        for (String line : notes.split("\\R")) {
            if (line == null) {
                continue;
            }
            String l = line.trim();
            if (!l.toLowerCase(Locale.ROOT).startsWith("e-mail")) {
                continue;
            }
            int idx = l.indexOf(':');
            if (idx < 0 || idx + 1 >= l.length()) {
                continue;
            }
            String candidate = l.substring(idx + 1).trim();
            if (candidate.contains("@") && candidate.contains(".")) {
                return candidate;
            }
        }
        return "";
    }

    private static String formatHoraires(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null) {
            return "—";
        }
        String d1 = debut.format(HM);
        if (fin == null) {
            return d1;
        }
        return d1 + " – " + fin.format(HM);
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

    private static String htmlDemandeEnregistree(String prenom, String praticien, String dateStr, String horaires,
                                                 Optional<String> manageUrl,
                                                 String prenomBrut, String praticienBrut, String dateBrut,
                                                 String horairesBrut, String gestionToken,
                                                 String cabinetEmailBrut, String telephoneBrut) {
        String bouton = manageUrl.map(url -> {
            String urlAnnuler = appendQueryParam(url, "action", "annuler");
            String urlReporter = appendQueryParam(url, "action", "reporter");
            /* Styles type « bouton » lisibles dans la plupart des webmails (table pour alignement). */
            String stylePrincipal = "display:inline-block;padding:16px 32px;background:#c4b5fe;color:#ffffff;"
                    + "font-weight:700;font-size:16px;border-radius:14px;text-decoration:none;"
                    + "box-shadow:0 4px 14px rgba(124,58,237,0.35);";
            String styleSecondaire = "display:inline-block;padding:12px 24px;background:#ddd6fe;color:#4c1d95;"
                    + "font-weight:700;font-size:14px;border-radius:12px;text-decoration:none;";
            return """
                    <p style="margin:20px 0 10px 0;font-size:16px;line-height:1.65;color:#374151;">Vous pouvez <strong>annuler</strong> ou <strong>reporter</strong> ce rendez-vous sans vous connecter&nbsp;:</p>
                    <p style="margin:0 0 20px 0;text-align:center;">
                      <a href="%s" style="%s">Gérer mon rendez-vous (annuler / reporter)</a>
                    </p>
                    <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:0 auto 8px auto;">
                      <tr>
                        <td style="padding:6px 8px;text-align:center;">
                          <a href="%s" style="%s">Annuler le rendez-vous</a>
                        </td>
                        <td style="padding:6px 8px;text-align:center;">
                          <a href="%s" style="%s">Reporter le rendez-vous</a>
                        </td>
                      </tr>
                    </table>
                    """.formatted(url, stylePrincipal, urlAnnuler, styleSecondaire, urlReporter, styleSecondaire);
        }).orElseGet(() -> {
            boolean hasMail = cabinetEmailBrut != null && !cabinetEmailBrut.isBlank();
            boolean hasTel = telephoneBrut != null && !telephoneBrut.isBlank();
            String telAffiche = escapeHtml(telephoneBrut != null ? telephoneBrut.trim() : "");
            String stylePrincipal = "display:inline-block;padding:16px 32px;background:#c4b5fe;color:#ffffff;"
                    + "font-weight:700;font-size:16px;border-radius:14px;text-decoration:none;"
                    + "box-shadow:0 4px 14px rgba(124,58,237,0.35);";
            String styleSecondaire = "display:inline-block;padding:12px 24px;background:#ddd6fe;color:#4c1d95;"
                    + "font-weight:700;font-size:14px;border-radius:12px;text-decoration:none;";
            StringBuilder sb = new StringBuilder();
            sb.append("""
                    <p style="margin:20px 0 10px 0;font-size:16px;line-height:1.65;color:#374151;">Vous pouvez <strong>annuler</strong> ou <strong>reporter</strong> ce rendez-vous sans vous connecter&nbsp;: ouvrez votre messagerie depuis les boutons ci-dessous, ou appelez le cabinet.</p>
                    """);
            if (hasMail) {
                String hrefGerer = hrefAmpersands(buildMailtoDemandeRdv(cabinetEmailBrut,
                        "Demande concernant mon rendez-vous (AutiCare)",
                        corpsMailDemande("modifier ou annuler", prenomBrut, praticienBrut, dateBrut, horairesBrut, gestionToken)));
                String hrefAnnuler = hrefAmpersands(buildMailtoDemandeRdv(cabinetEmailBrut,
                        "Annulation de rendez-vous (AutiCare)",
                        corpsMailDemande("ANNULER", prenomBrut, praticienBrut, dateBrut, horairesBrut, gestionToken)));
                String hrefReporter = hrefAmpersands(buildMailtoDemandeRdv(cabinetEmailBrut,
                        "Report de rendez-vous (AutiCare)",
                        corpsMailDemande("REPORTER", prenomBrut, praticienBrut, dateBrut, horairesBrut, gestionToken)));
                sb.append("""
                        <p style="margin:0 0 16px 0;text-align:center;">
                          <a href="%s" style="%s">Écrire au cabinet (annuler / reporter)</a>
                        </p>
                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:0 auto 16px auto;">
                          <tr>
                            <td style="padding:6px 8px;text-align:center;">
                              <a href="%s" style="%s">Annuler le rendez-vous</a>
                            </td>
                            <td style="padding:6px 8px;text-align:center;">
                              <a href="%s" style="%s">Reporter le rendez-vous</a>
                            </td>
                          </tr>
                        </table>
                        """.formatted(hrefGerer, stylePrincipal, hrefAnnuler, styleSecondaire, hrefReporter, styleSecondaire));
            }
            if (hasTel) {
                String th = telHref(telephoneBrut);
                sb.append("""
                        <p style="margin:0 0 12px 0;font-size:15px;line-height:1.6;color:#374151;">Par téléphone&nbsp;: <a href="%s" style="color:#5b21b6;font-weight:600;">%s</a></p>
                        """.formatted(th, telAffiche));
            }
            if (!hasMail && !hasTel) {
                sb.append("""
                        <p style="margin:0 0 8px 0;font-size:14px;color:#4b5563;">Pour toute modification, contactez le cabinet par les coordonnées habituelles. Vous pouvez aussi renseigner
                          <code style="background:#f3f4f6;padding:2px 6px;border-radius:4px;">auticare.rdv.contact.cabinetEmail</code> (e-mail du cabinet) ou
                          <code style="background:#f3f4f6;padding:2px 6px;border-radius:4px;">auticare.rdv.public.manage.baseUrl</code> (lien web) dans <code style="background:#f3f4f6;padding:2px 6px;border-radius:4px;">application.properties</code>, ou l’e-mail / téléphone du praticien dans le profil.</p>
                        """);
            }
            return sb.toString();
        });
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f4f4f7;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#1f2937;">
                  <div style="max-width:600px;margin:0 auto;padding:32px 20px;">
                    <p style="margin:0 0 24px 0;font-size:22px;font-weight:600;color:#a78bfa;letter-spacing:0.02em;">AutiCare</p>
                    <h1 style="margin:0 0 20px 0;font-size:26px;font-weight:700;color:#111827;">Demande de rendez-vous enregistrée</h1>
                    <p style="margin:0 0 12px 0;font-size:16px;line-height:1.6;">Bonjour %s,</p>
                    <p style="margin:0 0 12px 0;font-size:16px;line-height:1.6;">Votre demande de rendez-vous a bien été reçue.</p>
                    <p style="margin:0 0 24px 0;font-size:16px;line-height:1.6;">Le praticien va l’examiner et vous recevrez un e-mail de confirmation dès qu’il aura accepté le créneau.</p>
                    <div style="background:#ede9fe;border-radius:12px;padding:20px 22px;margin:0 0 24px 0;">
                      <p style="margin:0 0 8px 0;font-size:15px;"><strong>Praticien :</strong> %s</p>
                      <p style="margin:0 0 8px 0;font-size:15px;"><strong>Date :</strong> %s</p>
                      <p style="margin:0;font-size:15px;"><strong>Horaires :</strong> %s</p>
                    </div>
                    <p style="margin:0 0 8px 0;font-size:15px;line-height:1.6;color:#374151;">En cas de refus ou de modification, le cabinet vous contactera.</p>
                    %s
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:28px 0 16px 0;">
                    <p style="margin:0;font-size:14px;color:#6b7280;">— L’équipe AutiCare</p>
                  </div>
                </body>
                </html>
                """.formatted(prenom, praticien, dateStr, horaires, bouton);
    }

    private static String htmlConfirmation(String prenom, String praticien, String dateStr, String horaires,
                                           String cabinetRowHtml, String lieu, String telephone) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f4f4f7;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#1f2937;">
                  <div style="max-width:600px;margin:0 auto;padding:32px 20px;">
                    <p style="margin:0 0 20px 0;font-size:22px;font-weight:600;color:#a78bfa;">AutiCare</p>
                    <h1 style="margin:0 0 14px 0;font-size:26px;font-weight:700;color:#111827;">Votre rendez-vous est confirmé</h1>
                    <p style="margin:0 0 22px 0;display:inline-block;padding:6px 14px;background:#d1fae5;color:#065f46;border-radius:999px;font-size:13px;font-weight:600;">Confirmé</p>
                    <p style="margin:18px 0 12px 0;font-size:16px;line-height:1.6;">Bonjour %s,</p>
                    <p style="margin:0 0 24px 0;font-size:16px;line-height:1.6;">Le praticien a accepté votre demande. Votre rendez-vous est confirmé aux détails ci-dessous.</p>
                    <div style="background:#f3f4f6;border-left:4px solid #10b981;border-radius:0 12px 12px 0;padding:20px 22px;margin:0 0 24px 0;">
                      <p style="margin:0 0 8px 0;font-size:15px;"><strong>Praticien :</strong> %s</p>
                      <p style="margin:0 0 8px 0;font-size:15px;"><strong>Date :</strong> %s</p>
                      <p style="margin:0 0 8px 0;font-size:15px;"><strong>Horaires :</strong> %s</p>
                      %s
                      <p style="margin:0 0 8px 0;font-size:15px;"><strong>Lieu :</strong> %s</p>
                      <p style="margin:0;font-size:15px;"><strong>Téléphone :</strong> %s</p>
                    </div>
                    <p style="margin:0;font-size:15px;line-height:1.6;color:#374151;">Pensez à vous présenter un peu à l’avance. En cas d’empêchement, prévenez le cabinet.</p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:28px 0 16px 0;">
                    <p style="margin:0;font-size:14px;color:#6b7280;">— L’équipe AutiCare</p>
                  </div>
                </body>
                </html>
                """.formatted(prenom, praticien, dateStr, horaires, cabinetRowHtml, lieu, telephone);
    }
}
