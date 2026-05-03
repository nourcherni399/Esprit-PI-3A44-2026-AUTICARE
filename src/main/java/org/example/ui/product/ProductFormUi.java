package org.example.ui.product;

import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;

import java.util.List;
import java.util.Locale;

/**
 * Styles et libellés communs au formulaire produit (aligné sur le projet ppp).
 */
public final class ProductFormUi {

    /** Catégorie par défaut en base lorsque le formulaire simplifié ne la saisit pas. */
    public static final String DEFAULT_CATEGORY = "sensoriels";

    /**
     * Valeur {@code produit.categorie} (enum MySQL) + libellé affiché, comme {@code App\Enum\Categorie} côté Symfony.
     */
    public static final class ProductCategoryChoice {
        private final String dbValue;
        private final String label;

        public ProductCategoryChoice(String dbValue, String label) {
            this.dbValue = dbValue;
            this.label = label;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final List<ProductCategoryChoice> CATEGORIES = List.of(
        new ProductCategoryChoice("sensoriels", "Sensoriels"),
        new ProductCategoryChoice("bruit_et_environnement", "Bruit et environnement"),
        new ProductCategoryChoice("education_apprentissage", "Education & apprentissage"),
        new ProductCategoryChoice("communication_langage", "Communication & langage"),
        new ProductCategoryChoice("jeux_therapeutiques_developpement", "Jeux thérapeutiques & développement"),
        new ProductCategoryChoice("bien_etre_relaxation", "Bien-être & relaxation"),
        new ProductCategoryChoice("vie_quotidienne", "Vie quotidienne")
    );

    public static List<ProductCategoryChoice> getProductCategories() {
        return CATEGORIES;
    }

    /** Choix par défaut (première valeur enum). */
    public static ProductCategoryChoice getDefaultCategoryChoice() {
        return CATEGORIES.get(0);
    }

    /** Associe une valeur base (insensible à la casse) au choix, ou le défaut si inconnue. */
    public static ProductCategoryChoice resolveCategoryChoice(String dbValueFromProduct) {
        if (dbValueFromProduct == null || dbValueFromProduct.isBlank()) {
            return getDefaultCategoryChoice();
        }
        String k = dbValueFromProduct.trim().toLowerCase(Locale.ROOT);
        for (ProductCategoryChoice c : CATEGORIES) {
            if (c.getDbValue().equals(k)) {
                return c;
            }
        }
        return getDefaultCategoryChoice();
    }

    private ProductFormUi() {
    }

    public static Label formLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 15px; -fx-font-weight: 500; -fx-text-fill: #1f2937;");
        return l;
    }

    public static void styleInputAddProduct(TextInputControl field) {
        field.setStyle(
            "-fx-background-color: #ffffff; "
                + "-fx-background-radius: 6; "
                + "-fx-border-color: #e5e7eb; "
                + "-fx-border-radius: 6; "
                + "-fx-padding: 8 12 8 12; "
                + "-fx-font-size: 15px;"
        );
    }

    public static void styleStockCombo(javafx.scene.control.ComboBox<?> combo) {
        combo.setStyle(
            "-fx-background-color: #ffffff; "
                + "-fx-background-radius: 6; "
                + "-fx-border-color: #e5e7eb; "
                + "-fx-border-radius: 6; "
                + "-fx-font-size: 15px;"
        );
        combo.setMaxWidth(Double.MAX_VALUE);
    }
}
