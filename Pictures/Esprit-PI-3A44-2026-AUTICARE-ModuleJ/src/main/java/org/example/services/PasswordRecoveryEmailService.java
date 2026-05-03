package org.example.services;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.InputStream;
import java.util.Properties;

/**
 * Service d'envoi du code PIN de réinitialisation via SMTP Gmail.
 *
 * <p>Configuration attendue (variables d'environnement ou propriétés système) :</p>
 * <ul>
 *   <li>AUTICARE_SMTP_USER (ou -Dauticare.smtp.user)</li>
 *   <li>AUTICARE_SMTP_APP_PASSWORD (ou -Dauticare.smtp.appPassword)</li>
 * </ul>
 */
public class PasswordRecoveryEmailService {

    private static final String DEFAULT_FROM = "amarahedil8@gmail.com";
    private static final String DEFAULT_HOST = "smtp.gmail.com";
    private static final String DEFAULT_PORT = "587";
    private static final Properties FILE_CONFIG = loadFileConfig();

    public void sendPinEmail(String recipientEmail, String pinCode) throws MessagingException {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new MessagingException("Destinataire email manquant.");
        }
        if (pinCode == null || pinCode.isBlank()) {
            throw new MessagingException("PIN manquant.");
        }

        String smtpUser = readConfig("auticare.smtp.user", "AUTICARE_SMTP_USER", DEFAULT_FROM);
        String smtpPasswordRaw = readConfig("auticare.smtp.appPassword", "AUTICARE_SMTP_APP_PASSWORD", "");
        // Google affiche souvent le mot de passe d'application groupe par blocs.
        // On accepte les variantes avec espaces en retirant tout whitespace.
        final String smtpPassword = smtpPasswordRaw.replaceAll("\\s+", "");
        if (smtpPassword.isBlank()) {
            throw new MessagingException(
                    "Configuration SMTP manquante: AUTICARE_SMTP_APP_PASSWORD (mot de passe d'application Gmail).");
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

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail.trim(), false));
        message.setSubject("AutiCare - Reinitialisation du mot de passe");
        message.setContent(buildHtmlTemplate(recipientEmail.trim(), pinCode), "text/html; charset=UTF-8");
        Transport.send(message);
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
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = PasswordRecoveryEmailService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // fallback env/system/default
        }
        return p;
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
