package org.example.utils;

import java.util.Locale;

/** Affichage du tarif consultation (cartes liste + barres latérales parcours RDV). */
public final class RdvTarifFormat {

    private RdvTarifFormat() {
    }

    public static String format(String raw) {
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        String t = raw.trim();
        if (t.toUpperCase(Locale.ROOT).contains("DT")) {
            return t;
        }
        return t + " DT";
    }
}
