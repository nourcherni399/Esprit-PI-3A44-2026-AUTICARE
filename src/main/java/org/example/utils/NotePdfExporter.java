package org.example.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Export PDF simple pour le détail d’une note (texte, compatible polices standard PDF). */
public final class NotePdfExporter {

    private static final float MARGIN = 50;

    private NotePdfExporter() {
    }

    public static void save(File dest, String docTitle, String subtitle, String bodyPlain) throws IOException {
        PDFont font = PDType1Font.HELVETICA;
        PDFont fontBold = PDType1Font.HELVETICA_BOLD;
        float pageW = PDRectangle.A4.getWidth();
        float pageH = PDRectangle.A4.getHeight();
        float maxW = pageW - 2 * MARGIN;

        List<String> bodyLines = wrapParagraphs(bodyPlain != null ? bodyPlain : "", font, 11, maxW);
        List<LineChunk> chunks = new ArrayList<>();
        chunks.add(new LineChunk(docTitle != null ? docTitle : "", fontBold, 16));
        chunks.add(new LineChunk(subtitle != null ? subtitle : "", font, 11));
        chunks.add(new LineChunk("", font, 11));
        for (String bl : bodyLines) {
            chunks.add(new LineChunk(bl, font, 11));
        }

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = pageH - MARGIN;

            try {
                for (LineChunk ch : chunks) {
                    float lh = ch.size + 4;
                    if (y < MARGIN + lh) {
                        cs.close();
                        page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        y = pageH - MARGIN;
                    }
                    if (!ch.text.isEmpty()) {
                        cs.beginText();
                        cs.setFont(ch.font, ch.size);
                        cs.newLineAtOffset(MARGIN, y - ch.size);
                        cs.showText(sanitizeForStandardFont(ch.text));
                        cs.endText();
                    }
                    y -= lh;
                }
            } finally {
                cs.close();
            }
            doc.save(dest);
        }
    }

    /** Remplace les caractères non couverts par Helvetica standard (WinAnsi). */
    private static String sanitizeForStandardFont(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else if (c == '\n' || c == '\r') {
                sb.append(' ');
            } else {
                switch (c) {
                    case 'à', 'â' -> sb.append('a');
                    case 'é', 'è', 'ê', 'ë' -> sb.append('e');
                    case 'î', 'ï' -> sb.append('i');
                    case 'ô' -> sb.append('o');
                    case 'ù', 'û', 'ü' -> sb.append('u');
                    case 'ç' -> sb.append('c');
                    case 'À', 'Â' -> sb.append('A');
                    case 'É', 'È', 'Ê', 'Ë' -> sb.append('E');
                    case 'Î', 'Ï' -> sb.append('I');
                    case 'Ô' -> sb.append('O');
                    case 'Ù', 'Û', 'Ü' -> sb.append('U');
                    case 'Ç' -> sb.append('C');
                    case 'œ' -> sb.append("oe");
                    case 'Œ' -> sb.append("OE");
                    case '\u2019', '\u2018' -> sb.append('\'');
                    case '\u201c', '\u201d' -> sb.append('"');
                    case '\u2026' -> sb.append("...");
                    default -> sb.append('?');
                }
            }
        }
        return sb.toString();
    }

    private static List<String> wrapParagraphs(String text, PDFont font, float fontSize, float maxWidth)
            throws IOException {
        List<String> out = new ArrayList<>();
        String[] paras = text.split("\r?\n");
        for (String para : paras) {
            if (para.isEmpty()) {
                out.add("");
                continue;
            }
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) {
                    continue;
                }
                String trial = line.isEmpty() ? w : line + " " + w;
                float tw = font.getStringWidth(sanitizeForStandardFont(trial)) / 1000f * fontSize;
                if (tw > maxWidth && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line.setLength(0);
                    line.append(trial);
                }
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
            }
        }
        return out;
    }

    private static final class LineChunk {
        final String text;
        final PDFont font;
        final float size;

        LineChunk(String text, PDFont font, float size) {
            this.text = text;
            this.font = font;
            this.size = size;
        }
    }
}
