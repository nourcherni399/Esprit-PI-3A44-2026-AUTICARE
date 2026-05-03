package org.example.services;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.example.models.CartLineView;
import org.example.models.CheckoutFormData;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Envoie un e-mail de confirmation après une commande client.
 */
public class OrderConfirmationEmailService {

    private static final String DEFAULT_FROM = "amarahedil8@gmail.com";
    private static final String DEFAULT_HOST = "smtp.gmail.com";
    private static final String DEFAULT_PORT = "587";
    private static final Properties FILE_CONFIG = loadFileConfig();

    public void sendOrderConfirmation(CheckoutFormData form, List<CartLineView> lines, double totalAmount) throws MessagingException {
        if (form == null || form.email() == null || form.email().isBlank()) {
            throw new MessagingException("Destinataire email manquant.");
        }
        String smtpUser = readConfig("auticare.smtp.user", "AUTICARE_SMTP_USER", DEFAULT_FROM);
        String smtpPasswordRaw = readConfig("auticare.smtp.appPassword", "AUTICARE_SMTP_APP_PASSWORD", "");
        String smtpPassword = smtpPasswordRaw.replaceAll("\\s+", "");
        if (smtpPassword.isBlank()) {
            throw new MessagingException("Configuration SMTP manquante.");
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
        String smtpDebug = readConfig("auticare.smtp.debug", "AUTICARE_SMTP_DEBUG", "false");
        if ("true".equalsIgnoreCase(smtpDebug)) {
            session.setDebug(true);
        }

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(form.email().trim(), false));
        boolean card = "carte_bancaire".equalsIgnoreCase(safe(form.modePayment()));
        message.setSubject(
            card
                ? "AutiCare - Paiement par carte bancaire confirmé"
                : "AutiCare - Confirmation de votre commande"
        );
        message.setContent(buildHtmlTemplate(form, lines, totalAmount), "text/html; charset=UTF-8");
        Transport.send(message);
    }

    private static String buildHtmlTemplate(CheckoutFormData form, List<CartLineView> lines, double totalAmount) {
        String customerName = safe(form.nom());
        String dateText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String paymentMethodText = paymentLabel(form.modePayment());
        String paymentStatusText = paymentStatus(form.modePayment());
        String detailsRowsHtml = buildDetailsRowsHtml(lines);
        String supportEmail = readConfig("auticare.support.email", "AUTICARE_SUPPORT_EMAIL", "contact@auticare.tn");
        String supportPhone = readConfig("auticare.support.phone", "AUTICARE_SUPPORT_PHONE", "+216 00 000 000");
        String subtotalText = escapeHtml(String.format(Locale.FRENCH, "%.2f DT", totalAmount));

        String greetingName = customerName.isBlank() ? "client" : escapeHtml(customerName);
        return """
            <!doctype html>
            <html lang="fr">
            <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
            <body style="margin:0;padding:20px 12px;background:#f1f5f9;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#1e293b;font-size:15px;line-height:1.5;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" style="max-width:600px;border-collapse:collapse;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;">
                      <tr>
                        <td style="background:#0f172a;color:#ffffff;padding:18px 22px;">
                          <div style="font-size:11px;letter-spacing:0.08em;text-transform:uppercase;opacity:0.9;">AutiCare</div>
                          <div style="font-size:20px;font-weight:700;margin-top:6px;line-height:1.25;">Confirmation de commande</div>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:22px 22px 26px 22px;">
                          <p style="margin:0 0 6px 0;font-size:16px;">Bonjour <strong>%s</strong>,</p>
                          <p style="margin:0 0 22px 0;color:#64748b;font-size:14px;">Votre commande est enregistrée. Récapitulatif ci-dessous.</p>

                          <div style="border:1px solid #e2e8f0;border-radius:10px;padding:16px 18px;margin-bottom:18px;background:#fafafa;">
                            <div style="font-size:12px;font-weight:700;color:#64748b;text-transform:uppercase;letter-spacing:0.06em;margin-bottom:12px;">Détails de la commande</div>
                            <p style="margin:0 0 14px 0;font-size:14px;"><strong>Date</strong> : %s</p>
                            <table role="presentation" width="100%%" style="border-collapse:collapse;font-size:14px;">
                              <tr>
                                <td style="padding:8px 0;border-bottom:1px solid #cbd5e1;color:#64748b;font-weight:600;">Produit</td>
                                <td align="right" style="padding:8px 0;border-bottom:1px solid #cbd5e1;color:#64748b;font-weight:600;">Montant</td>
                              </tr>
                              %s
                              <tr>
                                <td style="padding:12px 0 0 0;font-weight:700;">Total</td>
                                <td align="right" style="padding:12px 0 0 0;font-weight:700;">%s</td>
                              </tr>
                            </table>
                          </div>

                          <div style="border:1px solid #e2e8f0;border-radius:10px;padding:16px 18px;margin-bottom:18px;">
                            <div style="font-size:12px;font-weight:700;color:#64748b;text-transform:uppercase;letter-spacing:0.06em;margin-bottom:12px;">Paiement & livraison</div>
                            <p style="margin:0 0 8px 0;font-size:14px;"><strong>Statut du paiement</strong> : %s</p>
                            <p style="margin:0 0 14px 0;font-size:14px;"><strong>Mode de paiement</strong> : %s</p>
                            <p style="margin:0;font-size:14px;color:#475569;"><strong>Adresse de livraison</strong><br/>%s, %s %s</p>
                            <p style="margin:10px 0 0 0;font-size:13px;color:#94a3b8;">Délai indicatif : 3 à 5 jours ouvrables.</p>
                          </div>

                          <div style="border:1px solid #e2e8f0;border-radius:10px;padding:14px 18px;margin-bottom:22px;background:#f8fafc;font-size:14px;color:#475569;">
                            <strong>Assistance</strong><br/>
                            <a href="mailto:%s" style="color:#2563eb;">%s</a> · %s
                          </div>

                          <p style="margin:0;font-size:14px;color:#64748b;">Merci pour votre confiance.<br/>L'équipe AutiCare</p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
            greetingName,
            escapeHtml(dateText),
            detailsRowsHtml,
            subtotalText,
            escapeHtml(paymentStatusText),
            escapeHtml(paymentMethodText),
            escapeHtml(safe(form.adresse())),
            escapeHtml(safe(form.codePostal())),
            escapeHtml(safe(form.ville())),
            escapeHtml(supportEmail),
            escapeHtml(supportEmail),
            escapeHtml(supportPhone)
        );
    }

    private static String buildDetailsRowsHtml(List<CartLineView> lines) {
        StringBuilder sb = new StringBuilder();
        for (CartLineView line : lines) {
            String name = line.product() != null && line.product().getNom() != null ? line.product().getNom() : "Produit";
            sb.append("<tr>")
                .append("<td style=\"padding:8px 0;border-bottom:1px solid #ebe8e1;\">")
                .append(escapeHtml(name))
                .append(" (x")
                .append(line.quantity())
                .append(")")
                .append("</td>")
                .append("<td style=\"padding:8px 0;border-bottom:1px solid #ebe8e1;text-align:right;white-space:nowrap;\">")
                .append(escapeHtml(String.format(Locale.FRENCH, "%.2f DT", line.lineTotal())))
                .append("</td>")
                .append("</tr>");
        }
        return sb.toString();
    }

    private static String paymentLabel(String mode) {
        if (mode == null) {
            return "Non precise";
        }
        return switch (mode.trim().toLowerCase()) {
            case "carte_bancaire" -> "Carte bancaire (en ligne)";
            case "a_la_livraison" -> "A la livraison";
            default -> mode;
        };
    }

    private static String paymentStatus(String mode) {
        if (mode == null) {
            return "En attente";
        }
        return switch (mode.trim().toLowerCase()) {
            case "carte_bancaire" -> "Payé par carte bancaire (enregistré sur AutiCare)";
            case "a_la_livraison" -> "En attente";
            default -> "En attente";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
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
        String fromAssistantProps = AssistantSecretsLoader.get(propKey);
        if (fromAssistantProps != null && !fromAssistantProps.isBlank()) {
            return fromAssistantProps.trim();
        }
        String fromFile = FILE_CONFIG.getProperty(propKey);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = OrderConfirmationEmailService.class.getClassLoader()
            .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // fallback env/system/default
        }
        return p;
    }
}
