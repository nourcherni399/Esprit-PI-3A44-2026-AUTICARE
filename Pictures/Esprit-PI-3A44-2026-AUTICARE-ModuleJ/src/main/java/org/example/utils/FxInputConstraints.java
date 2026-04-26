package org.example.utils;

import javafx.scene.control.TextFormatter;

/**
 * Filtres de saisie réutilisables (longueur max, code alphanumérique, entiers bornés).
 */
public final class FxInputConstraints {

    private FxInputConstraints() {
    }

    public static TextFormatter<TextFormatter.Change> maxLength(int max) {
        return new TextFormatter<>(change -> {
            if (change.getControlNewText().length() <= max) {
                return change;
            }
            return null;
        });
    }

    /**
     * Code thématique : lettres ASCII, chiffres et tiret bas, longueur limitée (ex. 2–32).
     */
    public static TextFormatter<TextFormatter.Change> asciiIdentifierMax(int maxLen) {
        return new TextFormatter<>(change -> {
            String t = change.getControlNewText();
            if (t.length() > maxLen) {
                return null;
            }
            if (!t.matches("[A-Za-z0-9_]*")) {
                return null;
            }
            return change;
        });
    }

    /** Chiffres uniquement, au plus {@code maxDigits} caractères (ex. ordre d'affichage). */
    public static TextFormatter<TextFormatter.Change> unsignedIntDigits(int maxDigits) {
        return new TextFormatter<>(change -> {
            String t = change.getControlNewText();
            if (t.isEmpty()) {
                return change;
            }
            if (t.length() > maxDigits || !t.matches("\\d+")) {
                return null;
            }
            return change;
        });
    }
}
