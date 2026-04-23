package org.example.utils;

/**
 * Texte de description suggéré (équivalent léger du bouton « Suggérer une description » du projet ppp).
 */
public final class ProductDescriptionSuggest {

    private ProductDescriptionSuggest() {
    }

    public static String generate(String nom, String categorie) {
        String n = nom == null ? "" : nom.trim();
        String c = categorie == null || categorie.isBlank()
            ? "général"
            : categorie.trim().replace('_', ' ');
        if (n.isEmpty()) {
            return "Indiquez d’abord un nom de produit, puis recliquez sur « Suggérer une description ».";
        }
        return String.format(
            "%s — produit de la catégorie « %s », pensé pour le confort et l’usage au quotidien.%n%n"
                + "Vérifiez que le matériau et l’usage conviennent à la personne concernée ; adaptez le texte si besoin.",
            n,
            c
        );
    }
}
