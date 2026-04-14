package org.example.models;

import java.time.LocalDateTime;

public class ModuleContent {
    private int id;
    private String titre;
    private String description;
    private String categorie;
    private LocalDateTime dateCreation;
    private String ressourcesLien;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public String getRessourcesLien() { return ressourcesLien; }
    public void setRessourcesLien(String ressourcesLien) { this.ressourcesLien = ressourcesLien; }

    @Override
    public String toString() {
        return titre + " [" + categorie + "]";
    }
}
