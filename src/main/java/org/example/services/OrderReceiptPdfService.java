package org.example.services;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.example.models.CustomerOrder;
import org.example.models.CustomerOrderLine;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF du bon de livraison / reçu — équivalent route Symfony {@code receipt}
 * ({@code /confirmation/{id}/recu}).
 */
public final class OrderReceiptPdfService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private OrderReceiptPdfService() {
    }

    public static String defaultFileName(int commandeId) {
        return "recu_commande_" + commandeId + "_" + LocalDate.now().format(FILE_DATE) + ".pdf";
    }

    public static void writePdf(CustomerOrder commande, List<CustomerOrderLine> lignes, OutputStream out) throws IOException {
        String html = OrderReceiptHtmlBuilder.buildReceiptHtml(commande, lignes);
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Génération PDF impossible.", e);
        }
    }

    public static void writePdfToFile(CustomerOrder commande, List<CustomerOrderLine> lignes, Path target) throws IOException {
        try (OutputStream os = Files.newOutputStream(target)) {
            writePdf(commande, lignes, os);
        }
    }
}
