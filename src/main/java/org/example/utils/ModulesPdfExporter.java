package org.example.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Exporte la liste des modules affichés en PDF (Apache PDFBox).
 */
public final class ModulesPdfExporter {

    private static final float MARGIN = 50;
    private static final float LINE = 14;
    private static final float PAGE_BOTTOM = 56;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ModulesPdfExporter() {
    }

    public static void export(
            List<ModuleContent> modules,
            Map<Integer, Integer> articleCounts,
            Map<Integer, Integer> resourceCounts,
            File destination
    ) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = loadFont(doc);
            PDFont fontBold = font;
            float titleSize = 16;
            float bodySize = 10;
            float maxW = PDRectangle.A4.getWidth() - 2 * MARGIN;

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = page.getMediaBox().getHeight() - MARGIN;

            cs.setFont(fontBold, titleSize);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(safePdfText("AutiCare — Liste des modules"));
            cs.endText();
            y -= LINE * 2;

            cs.setFont(font, 9);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(safePdfText("Genere le " + FMT.format(LocalDateTime.now()) + " — " + modules.size() + " module(s)"));
            cs.endText();
            y -= LINE * 2.5f;

            int index = 1;
            for (ModuleContent m : modules) {
                float blockHeight = estimateBlockHeight(m);
                if (y - blockHeight < PAGE_BOTTOM) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - MARGIN;
                }

                String header = index + ". " + (m.getTitre() != null ? m.getTitre() : "(sans titre)");
                cs.setFont(fontBold, bodySize + 1);
                y = drawWrapped(cs, fontBold, bodySize + 1, MARGIN, y, header, maxW);
                y -= LINE * 0.5f;

                cs.setFont(font, bodySize);
                y = drawLine(cs, font, bodySize, MARGIN, y, "Categorie : " + formatCategorie(m));
                y = drawLine(cs, font, bodySize, MARGIN, y, "Niveau : " + formatNiveau(m.getNiveau()));
                y = drawLine(cs, font, bodySize, MARGIN, y, "Publie : " + (m.isPublished() ? "Oui" : "Non"));
                int art = articleCounts != null ? articleCounts.getOrDefault(m.getId(), 0) : 0;
                int res = resourceCounts != null ? resourceCounts.getOrDefault(m.getId(), 0) : 0;
                y = drawLine(cs, font, bodySize, MARGIN, y, "Articles : " + art + "  |  Ressources : " + res);
                String desc = m.getDescription();
                if (desc != null && !desc.isBlank()) {
                    y = drawWrapped(cs, font, bodySize, MARGIN, y, "Description : " + shorten(desc, 500), maxW);
                }
                y -= LINE;
                index++;
            }
            cs.close();
            doc.save(destination);
        }
    }

    private static float estimateBlockHeight(ModuleContent m) {
        float h = LINE * 8;
        String d = m.getDescription();
        if (d != null && d.length() > 80) {
            h += LINE * (d.length() / 80);
        }
        return h;
    }

    private static float drawLine(PDPageContentStream cs, PDFont font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safePdfText(text));
        cs.endText();
        return y - LINE;
    }

    private static float drawWrapped(PDPageContentStream cs, PDFont font, float size, float x, float y, String text, float maxWidth) throws IOException {
        String t = text != null ? text.replace('\r', ' ') : "";
        if (t.isEmpty()) {
            return y;
        }
        String[] words = t.split("\\s+");
        StringBuilder line = new StringBuilder();
        float yy = y;
        for (String w : words) {
            String trial = line.length() == 0 ? w : line + " " + w;
            float wlen = font.getStringWidth(safePdfText(trial)) / 1000f * size;
            if (wlen > maxWidth && line.length() > 0) {
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, yy);
                cs.showText(safePdfText(line.toString()));
                cs.endText();
                yy -= LINE;
                line = new StringBuilder(w);
            } else {
                if (line.length() == 0) {
                    line.append(w);
                } else {
                    line.append(" ").append(w);
                }
            }
        }
        if (line.length() > 0) {
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, yy);
            cs.showText(safePdfText(line.toString()));
            cs.endText();
            yy -= LINE;
        }
        return yy;
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "...";
    }

    private static String formatCategorie(ModuleContent m) {
        ModuleCategorie c = m.getCategorieEnum();
        if (c != null) {
            return c.getLibelle();
        }
        return m.getCategorie() != null ? m.getCategorie() : "";
    }

    private static String formatNiveau(ModuleNiveau n) {
        if (n == null) {
            return "Moyen";
        }
        return switch (n) {
            case facile -> "Facile";
            case difficile -> "Difficile";
            case moyen -> "Moyen";
        };
    }

    private static String safePdfText(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c < 32 && c != '\n') {
                b.append(' ');
            } else if (c > 255) {
                b.append('?');
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static PDFont loadFont(PDDocument doc) throws IOException {
        File[] candidates = new File[]{
                new File("C:/Windows/Fonts/arial.ttf"),
                new File("C:/Windows/Fonts/calibri.ttf"),
                new File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
                new File("/Library/Fonts/Arial.ttf"),
        };
        for (File f : candidates) {
            if (f.isFile()) {
                return PDType0Font.load(doc, f);
            }
        }
        return PDType1Font.HELVETICA;
    }
}
