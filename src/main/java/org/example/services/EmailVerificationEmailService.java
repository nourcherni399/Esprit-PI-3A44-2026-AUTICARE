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

public class EmailVerificationEmailService {

    private static final String DEFAULT_FROM = "amarahedil8@gmail.com";
    private static final String DEFAULT_HOST = "smtp.gmail.com";
    private static final String DEFAULT_PORT = "587";
    private static final Properties FILE_CONFIG = loadFileConfig();

    public void sendVerificationEmail(String recipientEmail, String verifyUrl) throws MessagingException {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new MessagingException("Destinataire email manquant.");
        }
        if (verifyUrl == null || verifyUrl.isBlank()) {
            throw new MessagingException("Lien de vérification manquant.");
        }
        String smtpUser = readConfig("auticare.smtp.user", "AUTICARE_SMTP_USER", DEFAULT_FROM);
        String smtpPasswordRaw = readConfig("auticare.smtp.appPassword", "AUTICARE_SMTP_APP_PASSWORD", "");
        String smtpPassword = smtpPasswordRaw.replaceAll("\\s+", "");
        if (smtpPassword.isBlank()) {
            throw new MessagingException("Configuration SMTP manquante: AUTICARE_SMTP_APP_PASSWORD.");
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
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail.trim(), false));
        message.setSubject("AutiCare - Activation de votre compte");
        message.setContent(buildHtmlTemplate(verifyUrl), "text/html; charset=UTF-8");
        Transport.send(message);
    }

    private static String readConfig(String propKey, String envKey, String fallback) {
        String fromProp = System.getProperty(propKey);
        if (fromProp != null && !fromProp.isBlank()) return fromProp.trim();
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        String fromFile = FILE_CONFIG.getProperty(propKey);
        if (fromFile != null && !fromFile.isBlank()) return fromFile.trim();
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = EmailVerificationEmailService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) p.load(in);
        } catch (Exception ignored) {
        }
        return p;
    }

    private static String buildHtmlTemplate(String verifyUrl) {
        return """
                <!doctype html>
                <html lang="fr">
                <body style="margin:0;padding:24px;background:#f3f4f6;font-family:Segoe UI,Arial,sans-serif;color:#111827;">
                  <div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;padding:28px;">
                    <p style="margin:0 0 16px 0;">Bienvenue sur AutiCare 👋</p>
                    <p style="margin:0 0 16px 0;">Pour activer votre compte, cliquez sur le lien suivant :</p>
                    <p style="margin:18px 0 24px 0;">
                      <a href="%s" style="display:inline-block;background:#2563eb;color:#fff;text-decoration:none;padding:10px 16px;border-radius:8px;">
                        Activer mon compte
                      </a>
                    </p>
                    <p style="margin:0 0 10px 0;font-size:13px;color:#6b7280;">Si le bouton ne fonctionne pas, copiez ce lien :</p>
                    <p style="margin:0;word-break:break-all;font-size:12px;color:#374151;">%s</p>
                  </div>
                </body>
                </html>
                """.formatted(verifyUrl, verifyUrl);
    }
}

