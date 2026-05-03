package org.example.ui.product;

import javafx.scene.control.Spinner;

/**
 * Règles de saisie stocks alignées sur le projet ppp ({@code validateStockNom}, {@code validateStockQuantiteStrictementPositive}).
 */
public final class StockFormValidation {

    public static final int NOM_MAX_LEN = 100;
    public static final int QTY_MAX = 10_000_000;

    private StockFormValidation() {
    }

    /** @return message d’erreur ou {@code null} si OK */
    public static String validateNom(String raw) {
        if (raw == null) {
            return "Le nom du stock est obligatoire.";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "Le nom du stock est obligatoire.";
        }
        if (t.length() > NOM_MAX_LEN) {
            return "Le nom ne peut pas dépasser " + NOM_MAX_LEN + " caractères.";
        }
        return null;
    }

    /** Quantité strictement positive (création / mise à jour d’emplacement). */
    public static String validateQuantiteStrictementPositive(int q) {
        if (q <= 0) {
            return "La quantité doit être un entier strictement positif.";
        }
        if (q > QTY_MAX) {
            return "La quantité ne peut pas dépasser " + QTY_MAX + ".";
        }
        return null;
    }

    /**
     * Lit la quantité saisie dans un {@link Spinner} éditable (même logique que ppp).
     */
    public static int parseQuantityFromEditableSpinner(Spinner<Integer> spinner) throws NumberFormatException {
        String text = spinner.getEditor().getText();
        if (text == null || text.isBlank()) {
            throw new NumberFormatException("empty");
        }
        return Integer.parseInt(text.trim());
    }
}
