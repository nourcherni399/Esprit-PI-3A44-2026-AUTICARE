package org.example.models;

/**
 * Emplacement logistique : {@code quantite} = réserve <strong>non encore allouée</strong> aux fiches produit.
 * Créer ou augmenter une fiche prélève sur cette réserve ; une commande client ne modifie que {@code produit.quantite}.
 */
public class Stock {
    private int id;
    private String nom;
    private int quantite;

    public Stock() {
    }

    public Stock(int id, String nom, int quantite) {
        this.id = id;
        this.nom = nom;
        this.quantite = quantite;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public int getQuantite() {
        return quantite;
    }

    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }

    @Override
    public String toString() {
        return nom + " (" + quantite + " dispo)";
    }
}
