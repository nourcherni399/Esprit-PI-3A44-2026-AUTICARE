package org.example.utils;

import java.util.regex.Pattern;

/** Utilitaires pour notes au format HTML (éditeur riche JavaFX) : aperçu texte, vide, échappement. */
public final class NoteHtmlUtil {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private NoteHtmlUtil() {
    }

    public static String emptyEditorHtml() {
        return "<html><head></head><body contenteditable=\"true\"></body></html>";
    }

    public static boolean looksLikeHtml(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.trim();
        if (!t.startsWith("<")) {
            return false;
        }
        String low = t.toLowerCase();
        return low.contains("<html")
                || low.contains("<body")
                || low.contains("<p")
                || low.contains("<div")
                || low.contains("<br")
                || low.contains("<span")
                || low.contains("<ul")
                || low.contains("<ol")
                || low.contains("<li")
                || low.contains("<b>")
                || low.contains("<i>")
                || low.contains("<u>");
    }

    public static String stripToPlain(String html) {
        if (html == null) {
            return "";
        }
        String t = SCRIPT.matcher(html).replaceAll(" ");
        t = TAGS.matcher(t).replaceAll(" ");
        t = t.replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        return t.replaceAll("\\s+", " ").trim();
    }

    public static boolean isEffectivelyEmpty(String html) {
        return stripToPlain(html).isBlank();
    }

    public static String toPlainPreview(String html, int maxLen) {
        String p = stripToPlain(html);
        if (p.length() <= maxLen) {
            return p;
        }
        return p.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    public static String escapePlainForHtml(String plain) {
        if (plain == null) {
            return "";
        }
        return plain.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Contenu initial pour l’éditeur HTML (texte brut ou fragment HTML). */
    public static String wrapForEditor(String plainOrHtml) {
        if (plainOrHtml == null || plainOrHtml.isBlank()) {
            return emptyEditorHtml();
        }
        if (looksLikeHtml(plainOrHtml)) {
            if (plainOrHtml.toLowerCase().contains("<html")) {
                return plainOrHtml;
            }
            return "<html><head></head><body contenteditable=\"true\">" + plainOrHtml + "</body></html>";
        }
        String esc = escapePlainForHtml(plainOrHtml).replace("\n", "<br>");
        return "<html><head></head><body contenteditable=\"true\"><p>" + esc + "</p></body></html>";
    }
}
