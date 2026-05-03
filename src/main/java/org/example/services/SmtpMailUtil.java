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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Envoi d’e-mails HTML via la même configuration SMTP que {@link PasswordRecoveryEmailService}.
 */
public final class SmtpMailUtil {

    private static final String DEFAULT_HOST = "smtp.gmail.com";
    private static final String DEFAULT_PORT = "587";

    private SmtpMailUtil() {
    }

    /**
     * Lit la config : propriétés système → variables d’environnement → fichiers (rechargés à chaque appel).
     * <p>Fusion fichiers : d’abord {@code ~/.auticare/application.properties} puis {@code user.dir/application.properties},
     * en n’appliquant que les clés <strong>non vides</strong> (évite d’écraser votre SMTP avec des lignes vides) ;
     * puis <strong>classpath:/application.properties</strong> en dernier — les valeurs du projet (ex. lignes SMTP)
     * priment donc toujours sur des fichiers locaux incomplets.</p>
     */
    public static String readConfig(String propKey, String envKey, String fallback) {
        String fromProp = System.getProperty(propKey);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFiles = readFromMergedPropertyFiles(propKey);
        if (fromFiles != null && !fromFiles.isBlank()) {
            return fromFiles.trim();
        }
        return fallback;
    }

    /**
     * Fichiers locaux (clés non vides seulement), puis {@code classpath:/application.properties} (prime sur tout).
     */
    private static String readFromMergedPropertyFiles(String propKey) {
        Properties merged = new Properties();
        overlayPropertiesFileNonBlank(merged, Paths.get(System.getProperty("user.home"), ".auticare", "application.properties"));
        overlayPropertiesFileNonBlank(merged, Paths.get(System.getProperty("user.dir"), "application.properties"));
        try (InputStream in = SmtpMailUtil.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                Properties cp = new Properties();
                cp.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                merged.putAll(cp);
            }
        } catch (Exception ignored) {
            // ignoré
        }
        String v = merged.getProperty(propKey);
        return v != null ? v : null;
    }

    /** N’ajoute que les entrées dont la valeur n’est pas vide (évite d’écraser le SMTP du classpath). */
    private static void overlayPropertiesFileNonBlank(Properties target, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try (InputStreamReader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            Properties over = new Properties();
            over.load(r);
            for (String name : over.stringPropertyNames()) {
                String v = over.getProperty(name);
                if (v != null && !v.isBlank()) {
                    target.setProperty(name, v.trim());
                }
            }
        } catch (Exception ignored) {
            // ignoré
        }
    }

    private static String flattenMessagingException(MessagingException e) {
        StringBuilder sb = new StringBuilder();
        for (Exception ex = e; ex != null; ex = ex instanceof MessagingException me ? me.getNextException() : null) {
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(ex.getMessage());
            }
        }
        return sb.toString().toLowerCase();
    }

    private static String normalizeSmtpPassword(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim()
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replaceAll("\\s+", "");
        if (t.length() >= 2) {
            char a = t.charAt(0);
            char z = t.charAt(t.length() - 1);
            if ((a == '"' && z == '"') || (a == '\'' && z == '\'')) {
                t = t.substring(1, t.length() - 1).trim().replaceAll("\\s+", "");
            }
        }
        return t;
    }

    /**
     * Envoie un message HTML ; l’expéditeur est {@code auticare.smtp.user}.
     *
     * @throws MessagingException si SMTP n’est pas configuré ou en cas d’erreur d’envoi
     */
    public static void sendHtml(String recipientEmail, String subject, String htmlBody) throws MessagingException {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new MessagingException("Destinataire email manquant.");
        }
        String smtpUser = readConfig("auticare.smtp.user", "AUTICARE_SMTP_USER", "").trim();
        String smtpPassword = normalizeSmtpPassword(
                readConfig("auticare.smtp.appPassword", "AUTICARE_SMTP_APP_PASSWORD", ""));
        if (smtpUser.isBlank() || smtpPassword.isBlank()) {
            throw new MessagingException(
                    "Configuration SMTP manquante : auticare.smtp.user / auticare.smtp.appPassword.");
        }
        String smtpHost = readConfig("auticare.smtp.host", "AUTICARE_SMTP_HOST", DEFAULT_HOST).trim();
        String smtpPort = readConfig("auticare.smtp.port", "AUTICARE_SMTP_PORT", DEFAULT_PORT).trim();
        boolean useSsl = "true".equalsIgnoreCase(readConfig("auticare.smtp.useSsl", "AUTICARE_SMTP_USE_SSL", "false").trim())
                || "465".equals(smtpPort);

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (useSsl) {
            /* Port 465 : TLS dès la connexion (souvent plus stable que STARTTLS sur 587 derrière certains pare-feu). */
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        /* Ne pas forcer LOGIN seul : Gmail accepte PLAIN ; un mécanisme trop strict peut provoquer des 535. */

        final String userFinal = smtpUser;
        final String passFinal = smtpPassword;
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userFinal, passFinal);
            }
        });
        String debug = readConfig("auticare.smtp.debug", "AUTICARE_SMTP_DEBUG", "false");
        if ("true".equalsIgnoreCase(debug)) {
            session.setDebug(true);
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail.trim(), false));
        message.setSubject(subject, "UTF-8");
        message.setContent(htmlBody, "text/html; charset=UTF-8");
        try {
            Transport.send(message);
        } catch (MessagingException e) {
            String detail = flattenMessagingException(e);
            if (detail.contains("535") || detail.contains("BadCredentials")
                    || detail.contains("Username and Password not accepted")) {
                String hintLen = smtpPassword.length() == 16
                        ? ""
                        : " Longueur du mot de passe d’application après espaces retirés : " + smtpPassword.length()
                        + " (Gmail en fournit en général 16). ";
                String hintSsl = useSsl
                        ? ""
                        : " Vous pouvez aussi essayer dans application.properties : auticare.smtp.useSsl=true et auticare.smtp.port=465 ";
                throw new MessagingException(
                        "Gmail a refusé la connexion SMTP (erreur 535). Vérifiez : "
                                + "(1) auticare.smtp.user = l’adresse Gmail exacte du compte qui a créé le mot de passe d’application ; "
                                + "(2) auticare.smtp.appPassword = mot de passe d’APPLICATION (Google → Sécurité → Validation en 2 étapes → Mots de passe des applications), "
                                + "pas le mot de passe du compte ; "
                                + "(3) créez un NOUVEAU mot de passe d’application (l’ancien est invalide s’il a été exposé ou révoqué). "
                                + hintLen + hintSsl
                                + "Détails : " + detail,
                        e);
            }
            throw e;
        }
    }
}
