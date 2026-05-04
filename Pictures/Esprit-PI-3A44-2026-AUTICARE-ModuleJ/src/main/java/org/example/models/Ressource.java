package org.example.models;

import java.time.LocalDateTime;

public class Ressource {
    private int id;
    private String titre;
    private String typeRessource;
    private String contenu;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModif;
    private Integer ordre;
    private boolean isActive;
    private int moduleId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getTypeRessource() { return typeRessource; }
    public void setTypeRessource(String typeRessource) { this.typeRessource = typeRessource; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public LocalDateTime getDateModif() { return dateModif; }
    public void setDateModif(LocalDateTime dateModif) { this.dateModif = dateModif; }
    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public int getModuleId() { return moduleId; }
    public void setModuleId(int moduleId) { this.moduleId = moduleId; }

    @Override
    public String toString() {
        return titre + " (" + typeRessource + ")";
    }
}
