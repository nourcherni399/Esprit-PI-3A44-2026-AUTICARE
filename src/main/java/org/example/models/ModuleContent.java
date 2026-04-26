package org.example.models;

import java.time.LocalDateTime;

/**
 * Ligne de la table MySQL {@code module} (pidb).
 */
public class ModuleContent {
    private int id;
    private String titre;
    /** Résumé court (varchar 255 côté base). */
    private String description;
    /** Contenu détaillé (longtext). */
    private String contenu;
    private ModuleNiveau niveau = ModuleNiveau.moyen;
    /** Chemin ou URL d’image (varchar 255). */
    private String image = "";
    private boolean published = true;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModif;
    private ModuleCategorie categorie = ModuleCategorie.COMPRENDRE_TSA;
    /** Auteur admin (nullable en base). */
    private Integer adminId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public ModuleNiveau getNiveau() {
        return niveau;
    }

    public void setNiveau(ModuleNiveau niveau) {
        this.niveau = niveau != null ? niveau : ModuleNiveau.moyen;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDateModif() {
        return dateModif;
    }

    public void setDateModif(LocalDateTime dateModif) {
        this.dateModif = dateModif;
    }

    public ModuleCategorie getCategorieEnum() {
        return categorie;
    }

    public void setCategorieEnum(ModuleCategorie categorie) {
        this.categorie = categorie != null ? categorie : ModuleCategorie.NON_DEFINI;
    }

    /** Chaîne pour la base (ENUM). */
    public String getCategorie() {
        return categorie != null ? categorie.name() : ModuleCategorie.NON_DEFINI.name();
    }

    public void setCategorie(String categorie) {
        this.categorie = ModuleCategorie.fromDb(categorie);
    }

    public Integer getAdminId() {
        return adminId;
    }

    public void setAdminId(Integer adminId) {
        this.adminId = adminId;
    }

    @Override
    public String toString() {
        return titre + " [" + getCategorie() + "]";
    }
}