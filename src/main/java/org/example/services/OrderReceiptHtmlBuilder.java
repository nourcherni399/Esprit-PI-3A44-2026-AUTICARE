package org.example.services;

import org.example.models.CustomerOrder;
import org.example.models.CustomerOrderLine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * HTML du bon de livraison / reçu — équivalent {@code templates/front/order/receipt_pdf.html.twig}.
 */
public final class OrderReceiptHtmlBuilder {

    private static final DateTimeFormatter ORDER_TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FOOTER_TS = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private OrderReceiptHtmlBuilder() {
    }

    public static boolean isPaiementEnLigne(CustomerOrder o) {
        if (o == null) {
            return false;
        }
        String m = o.modePayment() == null ? "" : o.modePayment().trim();
        if ("carte_bancaire".equals(m)) {
            return true;
        }
        if (m.startsWith("carte_")) {
            return true;
        }
        return o.stripePaymentIntent() != null && !o.stripePaymentIntent().isBlank();
    }

    public static String buildReceiptHtml(CustomerOrder commande, List<CustomerOrderLine> lignes) {
        boolean online = isPaiementEnLigne(commande);
        String dateCmd = commande.dateCreation() != null
            ? ORDER_TS.format(commande.dateCreation())
            : "—";
        String nowStr = FOOTER_TS.format(LocalDateTime.now());

        StringBuilder rows = new StringBuilder();
        for (CustomerOrderLine l : lignes) {
            String nom = l.produitNom() != null ? l.produitNom() : "Produit";
            rows.append("<tr><td>")
                .append(esc(nom))
                .append("</td><td>")
                .append(l.quantite())
                .append("</td><td>")
                .append(fmtMoney(l.prix()))
                .append(" DT</td><td>")
                .append(fmtMoney(l.sousTotal()))
                .append(" DT</td></tr>");
        }

        String headerSub = online
            ? "Bon de livraison — Paiement déjà effectué (à présenter au livreur)"
            : "Bon de livraison";
        String confirmBox = online
            ? ("Ce document atteste que le client a payé sa commande n°" + commande.id()
            + " en ligne. À présenter au livreur pour la remise du colis.")
            : ("Ce document atteste que le client a bien passé la commande n°" + commande.id()
            + " auprès d'AutiCare. Paiement à la livraison.");

        String modePaiement = online
            ? "Carte bancaire / Paiement en ligne (déjà réglé)"
            : "À la livraison";

        String signTitle = online
            ? "Remise de la commande — Paiement déjà effectué"
            : "Paiement à la livraison";
        String signHint = online
            ? "Le client a payé en ligne. Le livreur remet la commande au client et signe ci-dessous. Aucun paiement à percevoir."
            : "Le livreur remet la commande, perçoit le paiement et signe ci-dessous.";

        String montantPercuBlock = "";
        if (!online) {
            montantPercuBlock = """
                <div style="margin: 12px 0;">
                    <span class="label">Montant perçu :</span>
                    <span style="display: inline-block; width: 80px; border-bottom: 1px solid #333; margin-left: 8px; vertical-align: middle;"></span>
                    <span style="margin-left: 8px;">DT</span>
                </div>
                """;
        }

        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: DejaVu Sans, sans-serif; font-size: 11px; color: #333; line-height: 1.4; }
                h1 { font-size: 16px; margin-bottom: 8px; color: #4B5563; }
                .header { text-align: center; margin-bottom: 20px; padding-bottom: 12px; border-bottom: 1px solid #E5E0D8; }
                .label { font-weight: bold; color: #4B5563; }
                table { width: 100%; border-collapse: collapse; margin: 12px 0; }
                th, td { padding: 6px 8px; text-align: left; border-bottom: 1px solid #E5E0D8; }
                th { background: #F5F1EB; font-size: 10px; }
                .total-row { font-weight: bold; font-size: 12px; }
                .block { margin: 10px 0; }
                .confirmation-box { background: #F5F1EB; padding: 12px; margin: 16px 0; border: 1px solid #E5E0D8; text-align: center; font-weight: bold; }
                .signature-zone { margin-top: 30px; padding-top: 20px; border-top: 1px solid #E5E0D8; }
                .signature-line { border-bottom: 1px solid #333; width: 250px; height: 40px; margin: 8px 0 4px 0; }
                .footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #E5E0D8; font-size: 10px; color: #6B7280; text-align: center; }
              </style>
            </head>
            <body>
              <div class="header">
                <h1>Confirmation de commande - AutiCare</h1>
                <div><strong>"""
            + esc(headerSub)
            + "</strong> - Commande n°"
            + commande.id()
            + " | Date : "
            + esc(dateCmd)
            + """
                </div>
              </div>
              <div class="confirmation-box">"""
            + esc(confirmBox)
            + """
              </div>
              <div class="block">
                <span class="label">Client :</span><br/>
                """
            + esc(commande.nom())
            + "<br/>"
            + esc(commande.email())
            + "<br/>"
            + esc(commande.telephone())
            + """
              </div>
              <div class="block">
                <span class="label">Mode de paiement :</span>
                """
            + esc(modePaiement)
            + """
              </div>
              <div class="block">
                <span class="label">Adresse de livraison :</span><br/>
                """
            + esc(commande.adresse())
            + ", "
            + esc(commande.codePostal())
            + " "
            + esc(commande.ville())
            + """
              </div>
              <table>
                <thead>
                  <tr>
                    <th>Produit</th>
                    <th>Qté</th>
                    <th>Prix unit.</th>
                    <th>Sous-total</th>
                  </tr>
                </thead>
                <tbody>"""
            + rows
            + """
                </tbody>
                <tfoot>
                  <tr class="total-row">
                    <td colspan="3">Total</td>
                    <td>"""
            + fmtMoney(commande.total())
            + """
                 DT</td>
                  </tr>
                </tfoot>
              </table>
              <div class="signature-zone">
                <div class="label">"""
            + esc(signTitle)
            + """
                </div>
                <p style="font-size: 9px; color: #6B7280;">"""
            + esc(signHint)
            + """
                </p>"""
            + montantPercuBlock
            + """
                <div class="label">Signature du livreur / Client</div>
                <div class="signature-line"></div>
                <div style="font-size: 9px; color: #6B7280;">Nom du livreur</div>
              </div>
              <div class="footer">
                Merci pour votre confiance. Ce bon atteste de la commande n°"""
            + commande.id()
            + ".<br/>AutiCare - Document généré le "
            + esc(nowStr)
            + """
              </div>
            </body>
            </html>
            """;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String fmtMoney(double v) {
        return String.format(Locale.FRENCH, "%,.2f", v).replace('\u00A0', ' ');
    }
}
