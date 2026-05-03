package org.example.models;

/**
 * Ligne de commande avec nom produit (pour PDF et liste).
 * {@code imagePath} : colonne {@code produit.image} (optionnel, pour l’UI).
 */
public record CustomerOrderLine(
    int quantite,
    double prix,
    double sousTotal,
    int produitId,
    String produitNom,
    String imagePath
) {
    public CustomerOrderLine(int quantite, double prix, double sousTotal, int produitId, String produitNom) {
        this(quantite, prix, sousTotal, produitId, produitNom, null);
    }
}
