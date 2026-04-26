package org.example.services;

import jakarta.mail.MessagingException;

/**
 * Service d’envoi des e-mails transactionnels AutiCare (PIN mot de passe oublié, RDV patient, etc.).
 * <p>
 * Tous les envois passent par la même pile SMTP que {@link SmtpMailUtil} : même
 * {@code auticare.smtp.user} (expéditeur From) et même {@code auticare.smtp.appPassword}.
 * </p>
 * <p>Configuration : {@code AUTICARE_SMTP_USER} / {@code auticare.smtp.user},
 * {@code AUTICARE_SMTP_APP_PASSWORD} / {@code auticare.smtp.appPassword}.</p>
 */
public class PasswordRecoveryEmailService {

    /**
     * Même transport et même adresse d’expéditeur que le PIN : à utiliser pour tout e-mail « métier »
     * (rendez-vous, notifications, etc.).
     */
    public void sendTransactionalHtml(String recipientEmail, String subject, String htmlBody) throws MessagingException {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new MessagingException("Destinataire email manquant.");
        }
        if (subject == null || subject.isBlank()) {
            throw new MessagingException("Sujet email manquant.");
        }
        if (htmlBody == null || htmlBody.isBlank()) {
            throw new MessagingException("Corps HTML manquant.");
        }
        SmtpMailUtil.sendHtml(recipientEmail.trim(), subject.trim(), htmlBody);
    }

    public void sendPinEmail(String recipientEmail, String pinCode) throws MessagingException {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new MessagingException("Destinataire email manquant.");
        }
        if (pinCode == null || pinCode.isBlank()) {
            throw new MessagingException("PIN manquant.");
        }
        sendTransactionalHtml(
                recipientEmail.trim(),
                "AutiCare - Reinitialisation du mot de passe",
                buildHtmlTemplate(recipientEmail.trim(), pinCode));
    }

    private static String buildHtmlTemplate(String email, String pinCode) {
        return """
                <!doctype html>
                <html lang="fr">
                <body style="margin:0;padding:24px;background:#f3f4f6;font-family:Segoe UI,Arial,sans-serif;color:#111827;">
                  <div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;padding:28px;">
                    <p style="margin:0 0 16px 0;">Bonjour,</p>
                    <p style="margin:0 0 16px 0;">Vous avez demande a reinitialiser votre mot de passe sur AutiCare.</p>
                    <p style="margin:0 0 10px 0;">Voici votre code PIN a usage unique (valide 15 minutes)&nbsp;:</p>
                    <div style="text-align:center;margin:18px 0 22px 0;font-size:42px;letter-spacing:10px;color:#9ec0e6;font-weight:700;">%s</div>
                    <p style="margin:0 0 8px 0;"><strong>Dans l'application AutiCare Desktop&nbsp;:</strong></p>
                    <ol style="margin:0 0 18px 18px;padding:0;line-height:1.6;">
                      <li>Ouvrez l'ecran <em>Verify PIN</em>.</li>
                      <li>Saisissez ce code PIN.</li>
                      <li>Definissez ensuite votre nouveau mot de passe.</li>
                    </ol>
                    <p style="margin:0 0 18px 0;font-size:13px;color:#6b7280;">Compte cible&nbsp;: %s</p>
                    <p style="margin:0 0 22px 0;">Si vous n'etes pas a l'origine de cette demande, ignorez cet e-mail.</p>
                    <p style="margin:0;color:#6b7280;">- L'equipe AutiCare</p>
                  </div>
                </body>
                </html>
                """.formatted(pinCode, email);
    }
}
