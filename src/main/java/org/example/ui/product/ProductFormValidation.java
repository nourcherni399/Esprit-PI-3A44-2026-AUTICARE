package org.example.ui.product;

/**
 * Contrôles de saisie formulaire produit (équivalent des validations dans {@code MainController} ppp pour l’ajout / édition).
 */
public final class ProductFormValidation {

    private static final double PRIX_MAX = 999_999_999.99;

    private ProductFormValidation() {
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /**
     * Un seul séparateur décimal autorisé : {@code .} ou {@code ,} (pas les deux dans la même saisie).
     * Retourne une chaîne utilisable par {@link Double#parseDouble(String)} ou {@code null} si ambigu / invalide.
     */
    public static String normalizePrixText(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replace(" ", "").replace("\u00a0", "");
        if (s.isEmpty()) {
            return "";
        }
        int dots = countChar(s, '.');
        int commas = countChar(s, ',');
        if (dots > 1 || commas > 1) {
            return null;
        }
        if (dots >= 1 && commas >= 1) {
            return null;
        }
        if (commas == 1) {
            return s.replace(',', '.');
        }
        return s;
    }

    public static String validateNom(String nom) {
        if (nom == null || nom.trim().isEmpty()) {
            return "Le champ Nom du produit est obligatoire.";
        }
        return null;
    }

    public static String validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "Le champ Description est obligatoire.";
        }
        return null;
    }

    public static String validateCategorieChoice(ProductFormUi.ProductCategoryChoice choice) {
        if (choice == null) {
            return "Choisissez une catégorie.";
        }
        return null;
    }

    /**
     * Prix obligatoire, nombre décimal strictement &gt; 0 ; séparateur : point ou virgule (un seul à la fois).
     */
    public static String validatePrixText(String prixText) {
        if (prixText == null || prixText.trim().isEmpty()) {
            return "Le champ Prix est obligatoire.";
        }
        String normalized = normalizePrixText(prixText);
        if (normalized == null) {
            return "Utilisez un seul séparateur décimal : point (.) ou virgule (,), pas les deux.";
        }
        if (normalized.isEmpty()) {
            return "Le champ Prix est obligatoire.";
        }
        double prix;
        try {
            prix = Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return "Le prix doit être un nombre valide (ex. 12 ou 12,50 ou 12.5).";
        }
        if (!Double.isFinite(prix)) {
            return "Le prix n'est pas valide.";
        }
        if (prix <= 0) {
            return "Le prix doit être strictement supérieur à 0.";
        }
        if (prix > PRIX_MAX) {
            return "Le prix est trop élevé.";
        }
        return null;
    }

    public static double parsePrix(String prixText) {
        String normalized = normalizePrixText(prixText);
        if (normalized == null || normalized.isEmpty()) {
            throw new NumberFormatException("prix");
        }
        return Double.parseDouble(normalized);
    }

    /** Image obligatoire à la création (comme ppp). */
    public static String validateImageRequiredForCreate(String imagePath) {
        if (!hasAtLeastOneImage(imagePath)) {
            return "Ajoutez au moins une image produit.";
        }
        return null;
    }

    /** À l’édition, une image doit rester définie (chemin copié ou existant). */
    public static String validateImagePresentEdit(String imagePath) {
        if (!hasAtLeastOneImage(imagePath)) {
            return "Conservez au moins une image du produit.";
        }
        return null;
    }

    public static String normalizeImagePaths(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (isDataImageUrl(trimmed)) {
            return trimmed;
        }
        StringBuilder out = new StringBuilder();
        String[] parts = raw.split("[;|\\n]");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String x = part.trim();
            if (x.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(" ; ");
            }
            out.append(x);
        }
        return out.toString();
    }

    private static boolean isDataImageUrl(String value) {
        if (value == null) {
            return false;
        }
        String low = value.trim().toLowerCase();
        return low.startsWith("data:image/");
    }

    private static boolean hasAtLeastOneImage(String raw) {
        return !normalizeImagePaths(raw).isBlank();
    }

    public static String validateCatalogQuantity(int q) {
        if (q < 0) {
            return "La quantité catalogue ne peut pas être négative.";
        }
        if (q > StockFormValidation.QTY_MAX) {
            return "La quantité catalogue est trop élevée.";
        }
        return null;
    }
}
