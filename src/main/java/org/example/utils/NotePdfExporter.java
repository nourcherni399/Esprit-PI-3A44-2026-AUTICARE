package org.example.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.example.services.SmtpMailUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Export PDF simple pour le détail d’une note (texte, compatible polices standard PDF). */
public final class NotePdfExporter {

    private static final float MARGIN = 50;
    private static final String DEFAULT_LOGO_PATH =
            "c:\\Users\\user\\.cursor\\projects\\c-Users-user-Desktop-Validation-java-Jeudi\\assets\\"
                    + "c__Users_user_AppData_Roaming_Cursor_User_workspaceStorage_aa1d15e37aea35391c43bf62eb73f2c6_"
                    + "images_619312073_1573214347279523_7321763532368069041_n__2_-ad971811-37de-4762-96fa-d0642c03f4f5.png";
    private static final String[] FONT_CANDIDATES = {
            "C:\\Windows\\Fonts\\arial.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    };
    private static final String[] FONT_BOLD_CANDIDATES = {
            "C:\\Windows\\Fonts\\arialbd.ttf",
            "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    };
    private static final String[] FONT_ITALIC_CANDIDATES = {
            "C:\\Windows\\Fonts\\ariali.ttf",
            "/System/Library/Fonts/Supplemental/Arial Italic.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Oblique.ttf"
    };

    private NotePdfExporter() {
    }

    public static void save(File dest, String docTitle, String subtitle, String bodyPlain) throws IOException {
        float pageW = PDRectangle.A4.getWidth();
        float pageH = PDRectangle.A4.getHeight();
        String safeTitle = docTitle != null && !docTitle.isBlank() ? docTitle : "Note medicale";
        String safeSubtitle = subtitle != null ? subtitle : "";
        String footerText = "AutiCare - Export note medecin";

        try (PDDocument doc = new PDDocument()) {
            FontPack fonts = loadFontPack(doc);
            float maxW = pageW - 2 * MARGIN;
            float contentYStart = pageH - 194;
            float contentMaxW = maxW - 24;
            List<StyledLine> bodyLines = buildStyledBodyLines(bodyPlain != null ? bodyPlain : "", fonts, contentMaxW);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = contentYStart;

            try {
                drawPageChrome(doc, cs, pageW, pageH, safeTitle, safeSubtitle, footerText, fonts.bold(), fonts.base(), fonts.italic(), fonts.unicode());
                for (StyledLine line : bodyLines) {
                    float lh = line.size + 4 + line.gapBefore;
                    if (y < MARGIN + 35) {
                        cs.close();
                        page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        drawPageChrome(doc, cs, pageW, pageH, safeTitle, safeSubtitle, footerText, fonts.bold(), fonts.base(), fonts.italic(), fonts.unicode());
                        y = contentYStart;
                    }
                    y -= line.gapBefore;
                    if (!line.text.isEmpty()) {
                        cs.beginText();
                        cs.setFont(line.font, line.size);
                        cs.newLineAtOffset(MARGIN + 12 + line.indent, y - line.size);
                        cs.showText(normalizeForFont(line.text, fonts.unicode()));
                        cs.endText();
                    }
                    y -= lh;
                }
            } finally {
                cs.close();
            }
            applyProtectionIfConfigured(doc);
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

    private static List<String> wrapParagraphs(String text, PDFont font, float fontSize, float maxWidth, boolean unicodeEnabled)
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
                float tw = font.getStringWidth(normalizeForFont(trial, unicodeEnabled)) / 1000f * fontSize;
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

    private static List<StyledLine> buildStyledBodyLines(String text, FontPack fonts, float maxWidth) throws IOException {
        List<StyledLine> out = new ArrayList<>();
        String[] rawLines = (text == null ? "" : text).split("\r?\n");
        for (String raw : rawLines) {
            String line = raw != null ? raw.trim() : "";
            if (line.isEmpty()) {
                out.add(new StyledLine("", fonts.base(), 11, 0, 6));
                continue;
            }
            if (line.startsWith("## ")) {
                for (String w : wrapParagraphs(line.substring(3), fonts.bold(), 12, maxWidth, fonts.unicode())) {
                    out.add(new StyledLine(w, fonts.bold(), 12, 0, 4));
                }
                continue;
            }
            if (line.startsWith("# ")) {
                for (String w : wrapParagraphs(line.substring(2), fonts.bold(), 14, maxWidth, fonts.unicode())) {
                    out.add(new StyledLine(w, fonts.bold(), 14, 0, 6));
                }
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("• ")) {
                String content = line.substring(2).trim();
                List<String> wrapped = wrapParagraphs(content, fonts.base(), 11, maxWidth - 14, fonts.unicode());
                for (int i = 0; i < wrapped.size(); i++) {
                    String prefix = i == 0 ? "• " : "  ";
                    out.add(new StyledLine(prefix + wrapped.get(i), fonts.base(), 11, 0, 2));
                }
                continue;
            }
            if (isStrongTitleLine(line)) {
                String titleText = line.replace("**", "").trim();
                for (String w : wrapParagraphs(titleText, fonts.bold(), 12, maxWidth, fonts.unicode())) {
                    out.add(new StyledLine(w, fonts.bold(), 12, 0, 4));
                }
                continue;
            }
            if (isSectionTitle(line)) {
                for (String w : wrapParagraphs(line, fonts.bold(), 12, maxWidth, fonts.unicode())) {
                    out.add(new StyledLine(w, fonts.bold(), 12, 0, 4));
                }
                continue;
            }
            for (String w : wrapParagraphs(line, fonts.base(), 11, maxWidth, fonts.unicode())) {
                out.add(new StyledLine(w, fonts.base(), 11, 0, 2));
            }
        }
        return out;
    }

    private static boolean isSectionTitle(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        if (line.endsWith(":") && line.length() <= 80) {
            return true;
        }
        if (line.length() <= 60 && line.equals(line.toUpperCase())) {
            return true;
        }
        return line.matches("^\\d+(?:\\.|\\)|-)\\s+.+$");
    }

    private static boolean isStrongTitleLine(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        if (t.length() < 3) {
            return false;
        }
        if (t.startsWith("**") && t.endsWith("**") && t.length() > 4) {
            return true;
        }
        if (t.length() <= 55 && !t.endsWith(".") && !t.endsWith(";") && !t.endsWith(",")) {
            return t.split("\\s+").length <= 8 && Character.isUpperCase(t.charAt(0));
        }
        return false;
    }

    private static void drawPageChrome(PDDocument doc,
                                       PDPageContentStream cs,
                                       float pageW,
                                       float pageH,
                                       String title,
                                       String subtitle,
                                       String footerText,
                                       PDFont titleFont,
                                       PDFont baseFont,
                                       PDFont italicFont,
                                       boolean unicodeEnabled) throws IOException {
        float cardX = MARGIN;
        float cardW = pageW - (2 * MARGIN);

        // Template premium : bandeau principal.
        cs.setNonStrokingColor(15, 23, 42);
        cs.addRect(cardX, pageH - 118, cardW, 70);
        cs.fill();

        // Barre accent premium.
        cs.setNonStrokingColor(56, 189, 248);
        cs.addRect(cardX, pageH - 118, 8, 70);
        cs.fill();

        boolean logoDrawn = tryDrawLogo(doc, cs, cardX + cardW - 94, pageH - 116, 80, 56);
        if (!logoDrawn) {
            // Fallback logo texte.
            cs.setNonStrokingColor(224, 242, 254);
            cs.addRect(cardX + cardW - 94, pageH - 116, 80, 56);
            cs.fill();
            cs.setNonStrokingColor(3, 105, 161);
            cs.beginText();
            cs.setFont(titleFont, 24);
            cs.newLineAtOffset(cardX + cardW - 63, pageH - 84);
            cs.showText("A");
            cs.endText();
        }

        cs.setNonStrokingColor(248, 250, 252);
        cs.beginText();
        cs.setFont(titleFont, 18);
        cs.newLineAtOffset(cardX + 20, pageH - 76);
        cs.showText(normalizeForFont(title, unicodeEnabled));
        cs.endText();

        // Sous-titre meta
        cs.setNonStrokingColor(203, 213, 225);
        cs.beginText();
        cs.setFont(baseFont, 10);
        cs.newLineAtOffset(cardX + 20, pageH - 97);
        cs.showText(normalizeForFont(subtitle, unicodeEnabled));
        cs.endText();

        // Carte de contenu (corps principal).
        cs.setNonStrokingColor(255, 255, 255);
        cs.addRect(cardX, MARGIN + 34, cardW, pageH - 246);
        cs.fill();
        cs.setStrokingColor(226, 232, 240);
        cs.addRect(cardX, MARGIN + 34, cardW, pageH - 246);
        cs.stroke();

        // En-tête section contenu.
        cs.setNonStrokingColor(30, 64, 175);
        cs.beginText();
        cs.setFont(titleFont, 12);
        cs.newLineAtOffset(cardX + 16, pageH - 136);
        cs.showText("Contenu de la note");
        cs.endText();

        // Ligne séparatrice sous le titre de section.
        cs.setStrokingColor(191, 219, 254);
        cs.moveTo(cardX + 16, pageH - 142);
        cs.lineTo(cardX + cardW - 16, pageH - 142);
        cs.stroke();

        // Footer
        cs.setNonStrokingColor(100, 116, 139);
        cs.beginText();
        cs.setFont(italicFont, 9);
        cs.newLineAtOffset(cardX, MARGIN - 2);
        cs.showText(normalizeForFont(footerText + " | Template premium", unicodeEnabled));
        cs.endText();
    }

    private static FontPack loadFontPack(PDDocument doc) {
        for (int i = 0; i < FONT_CANDIDATES.length; i++) {
            try {
                Path regular = Paths.get(FONT_CANDIDATES[i]);
                Path bold = i < FONT_BOLD_CANDIDATES.length ? Paths.get(FONT_BOLD_CANDIDATES[i]) : regular;
                Path italic = i < FONT_ITALIC_CANDIDATES.length ? Paths.get(FONT_ITALIC_CANDIDATES[i]) : regular;
                if (Files.isRegularFile(regular) && Files.isRegularFile(bold) && Files.isRegularFile(italic)) {
                    PDType0Font regularFont = PDType0Font.load(doc, regular.toFile());
                    PDType0Font boldFont = PDType0Font.load(doc, bold.toFile());
                    PDType0Font italicFont = PDType0Font.load(doc, italic.toFile());
                    return new FontPack(regularFont, boldFont, italicFont, true);
                }
            } catch (Exception ignored) {
                // try next font
            }
        }
        return new FontPack(PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD, PDType1Font.HELVETICA_OBLIQUE, false);
    }

    private static boolean tryDrawLogo(PDDocument doc,
                                       PDPageContentStream cs,
                                       float x,
                                       float y,
                                       float maxW,
                                       float maxH) {
        try {
            Path logoPath = resolveLogoPath();
            if (logoPath == null || !Files.isRegularFile(logoPath)) {
                return false;
            }
            PDImageXObject image = PDImageXObject.createFromFileByContent(logoPath.toFile(), doc);
            float iw = image.getWidth();
            float ih = image.getHeight();
            if (iw <= 0 || ih <= 0) {
                return false;
            }
            float ratio = Math.min(maxW / iw, maxH / ih);
            float drawW = iw * ratio;
            float drawH = ih * ratio;
            float drawX = x + ((maxW - drawW) / 2f);
            float drawY = y + ((maxH - drawH) / 2f);
            cs.drawImage(image, drawX, drawY, drawW, drawH);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path resolveLogoPath() {
        String sys = System.getProperty("auticare.logo.path");
        if (sys != null && !sys.isBlank()) {
            return Paths.get(sys.trim());
        }
        String env = System.getenv("AUTICARE_LOGO_PATH");
        if (env != null && !env.isBlank()) {
            return Paths.get(env.trim());
        }
        return Paths.get(DEFAULT_LOGO_PATH);
    }

    private static void applyProtectionIfConfigured(PDDocument doc) throws IOException {
        String userPwd = SmtpMailUtil.readConfig("pdf.notes.userPassword", "PDF_NOTES_USER_PASSWORD", "").trim();
        if (userPwd.isBlank()) {
            return;
        }
        String ownerPwd = SmtpMailUtil.readConfig("pdf.notes.ownerPassword", "PDF_NOTES_OWNER_PASSWORD", userPwd).trim();
        AccessPermission ap = new AccessPermission();
        ap.setCanExtractContent(false);
        ap.setCanModify(false);
        ap.setCanModifyAnnotations(false);
        ap.setCanFillInForm(false);
        ap.setCanAssembleDocument(false);
        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
        spp.setEncryptionKeyLength(128);
        spp.setPermissions(ap);
        doc.protect(spp);
    }

    private static String normalizeForFont(String s, boolean unicodeEnabled) {
        if (unicodeEnabled) {
            return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
        }
        return sanitizeForStandardFont(s);
    }

    private record FontPack(PDFont base, PDFont bold, PDFont italic, boolean unicode) {}
    private record StyledLine(String text, PDFont font, float size, float indent, float gapBefore) {}
}
