package org.example.models;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum AppLanguage {
    FR("fr", "Français", Locale.FRENCH, false),
    EN("en", "English", Locale.ENGLISH, false),
    AR("ar", "العربية", new Locale("ar"), true),
    ES("es", "Español", new Locale("es"), false),
    DE("de", "Deutsch", Locale.GERMAN, false),
    IT("it", "Italiano", Locale.ITALIAN, false),
    PT("pt", "Português", new Locale("pt"), false),
    TR("tr", "Türkçe", new Locale("tr"), false);

    private final String code;
    private final String label;
    private final Locale locale;
    private final boolean rtl;

    AppLanguage(String code, String label, Locale locale, boolean rtl) {
        this.code = code;
        this.label = label;
        this.locale = locale;
        this.rtl = rtl;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public Locale locale() {
        return locale;
    }

    public boolean rtl() {
        return rtl;
    }

    public String shortLabel() {
        return code.toUpperCase(Locale.ROOT);
    }

    public static AppLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            return FR;
        }
        String c = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(v -> v.code.equals(c))
                .findFirst()
                .orElse(FR);
    }

    public static List<AppLanguage> supported() {
        return List.of(values());
    }
}

