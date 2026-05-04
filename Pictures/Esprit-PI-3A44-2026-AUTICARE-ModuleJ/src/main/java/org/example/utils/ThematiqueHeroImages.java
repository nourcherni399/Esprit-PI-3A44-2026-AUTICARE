package org.example.utils;

import java.util.Locale;

/**
 * URL d’image d’en-tête par thématique. Utilise {@code picsum.photos} avec un seed stable par libellé
 * (pas d’API key, compatible avec un chargement HTTP « navigateur »).
 * Remplacez plus tard par des médias stockés en base / CDN métier.
 */
public final class ThematiqueHeroImages {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 400;

    private ThematiqueHeroImages() {
    }

    /**
     * URL d’une image représentative : une même thématique donne toujours la même image (seed fixe).
     */
    public static String urlForThematique(String thematique) {
        String seed = seedForThematique(thematique);
        return "https://picsum.photos/seed/" + seed + "/" + WIDTH + "/" + HEIGHT;
    }

    private static String seedForThematique(String thematique) {
        if (thematique == null || thematique.isBlank()) {
            return "auticare-default";
        }
        String t = thematique.trim().toLowerCase(Locale.FRENCH);
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replace("&", " et ").replaceAll("[^a-z0-9]+", "-").replaceAll("-+", "-");
        t = t.replaceAll("^-|-$", "");
        if (t.isEmpty()) {
            return "t-" + (Math.abs(thematique.hashCode()) % 1_000_000);
        }
        if (t.length() > 48) {
            t = t.substring(0, 48).replaceAll("-$", "");
        }
        return t;
    }
}
