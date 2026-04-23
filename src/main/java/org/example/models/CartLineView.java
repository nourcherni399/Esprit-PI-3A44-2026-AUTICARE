package org.example.models;

/**
 * Ligne affichée dans le panier (produit + quantité + prix unitaire figé comme côté Symfony {@code cart_item.prix}).
 */
public record CartLineView(int itemId, Product product, int quantity, double unitPrice) {

    public double lineTotal() {
        return unitPrice * quantity;
    }
}
