package org.example.models;

/**
 * Thématique configurable (admin) — liée aux événements par le champ {@code evenements.thematique} (nom affiché).
 */
public class Thematique {
    private int id;
    private String nom;
    private String code;
    private String description;
    /** Couleur d’accent (#RRGGBB). */
    private String couleur;
    private String sousTitre;
    /** Chemin local vers une image (JPG, PNG…). */
    private String imageChemin;
    private int ordreAffichage;
    private boolean visibleSite;
    private String publicCible;
    private String niveauDifficulte;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCouleur() {
        return couleur;
    }

    public void setCouleur(String couleur) {
        this.couleur = couleur;
    }

    public String getSousTitre() {
        return sousTitre;
    }

    public void setSousTitre(String sousTitre) {
        this.sousTitre = sousTitre;
    }

    public String getImageChemin() {
        return imageChemin;
    }

    public void setImageChemin(String imageChemin) {
        this.imageChemin = imageChemin;
    }

    public int getOrdreAffichage() {
        return ordreAffichage;
    }

    public void setOrdreAffichage(int ordreAffichage) {
        this.ordreAffichage = ordreAffichage;
    }

    public boolean isVisibleSite() {
        return visibleSite;
    }

    public void setVisibleSite(boolean visibleSite) {
        this.visibleSite = visibleSite;
    }

    public String getPublicCible() {
        return publicCible;
    }

    public void setPublicCible(String publicCible) {
        this.publicCible = publicCible;
    }

    public String getNiveauDifficulte() {
        return niveauDifficulte;
    }

    public void setNiveauDifficulte(String niveauDifficulte) {
        this.niveauDifficulte = niveauDifficulte;
    }
}
