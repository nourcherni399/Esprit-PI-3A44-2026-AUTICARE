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

        double sousTotal = 0d;
        StringBuilder rows = new StringBuilder();
        for (CustomerOrderLine l : lignes) {
            String nom = l.produitNom() != null ? l.produitNom() : "Produit";
            sousTotal += l.sousTotal();
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

        String modePaiement = online
            ? "Carte bancaire / Paiement en ligne (déjà réglé)"
            : "À la livraison";
        double remise = 0d;
        double tva = 0d;
        double fraisLivraison = 7d;
        double totalFinal = sousTotal - remise + tva + fraisLivraison;
        String statutPaiement = online ? "Payé" : "En attente (paiement à la livraison)";

        return "<!DOCTYPE html>"
            + "<html lang=\"fr\">"
            + "<head>"
            + "<meta charset=\"UTF-8\"/>"
            + "<style>"
            + "body { font-family: DejaVu Sans, sans-serif; font-size: 11px; color: #333; line-height: 1.4; }"
            + "h1 { font-size: 16px; margin-bottom: 8px; color: #4B5563; }"
            + ".header { text-align: center; margin-bottom: 20px; padding-bottom: 12px; border-bottom: 1px solid #E5E0D8; }"
            + ".label { font-weight: bold; color: #4B5563; }"
            + "table { width: 100%; border-collapse: collapse; margin: 12px 0; }"
            + "th, td { padding: 6px 8px; text-align: left; border-bottom: 1px solid #E5E0D8; }"
            + "th { background: #F5F1EB; font-size: 10px; }"
            + ".total-row { font-weight: bold; }"
            + ".block { margin: 10px 0; }"
            + ".footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #E5E0D8; font-size: 10px; color: #6B7280; text-align: center; }"
            + "</style>"
            + "</head>"
            + "<body>"
            + "<div class=\"header\">"
            + "<h1>Facture client - AutiCare</h1>"
            + "<div>Date: " + esc(dateCmd) + "</div>"
            + "</div>"
            + "<div class=\"block\">"
            + "<span class=\"label\">Informations du client</span><br/>"
            + "<strong>Nom complet :</strong> " + esc(commande.nom())
            + "<br/><strong>Adresse :</strong> " + esc(commande.adresse()) + ", " + esc(commande.codePostal()) + " " + esc(commande.ville())
            + "<br/><strong>Téléphone :</strong> " + esc(commande.telephone())
            + "<br/><strong>Email :</strong> " + esc(commande.email())
            + "</div>"
            + "<div class=\"block\"><span class=\"label\">Détails des produits</span></div>"
            + "<table><thead><tr>"
            + "<th>Nom du produit</th><th>Quantité</th><th>Prix unitaire</th><th>Total par produit</th>"
            + "</tr></thead><tbody>" + rows + "</tbody></table>"
            + "<div class=\"block\">"
            + "<span class=\"label\">Totaux</span>"
            + "<table>"
            + "<tr><td>Sous-total</td><td>" + fmtMoney(sousTotal) + " DT</td></tr>"
            + "<tr><td>Remise</td><td>" + fmtMoney(remise) + " DT</td></tr>"
            + "<tr><td>TVA</td><td>" + fmtMoney(tva) + " DT</td></tr>"
            + "<tr><td>Frais de livraison</td><td>" + fmtMoney(fraisLivraison) + " DT</td></tr>"
            + "<tr class=\"total-row\"><td>Total final à payer</td><td>" + fmtMoney(totalFinal) + " DT</td></tr>"
            + "</table>"
            + "</div>"
            + "<div class=\"block\">"
            + "<span class=\"label\">Livraison et paiement</span><br/>"
            + "<strong>Adresse de livraison :</strong> " + esc(commande.adresse()) + ", " + esc(commande.codePostal()) + " " + esc(commande.ville())
            + "<br/><strong>Frais de livraison :</strong> " + fmtMoney(fraisLivraison) + " DT"
            + "<br/><strong>Statut du paiement :</strong> " + esc(statutPaiement)
            + "<br/><strong>Mode de paiement :</strong> " + esc(modePaiement)
            + "</div>"
            + "<div class=\"footer\">Merci pour votre confiance.<br/>AutiCare - Document généré le " + esc(nowStr) + "</div>"
            + "</body></html>";
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
