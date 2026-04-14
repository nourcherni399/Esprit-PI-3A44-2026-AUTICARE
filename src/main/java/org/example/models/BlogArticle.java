package org.example.models;

import java.time.LocalDateTime;

public class BlogArticle {
    private int id;
    private String titre;
    private String contenu;
    private int auteurId;
    private LocalDateTime datePublication;
    private String categorie;
    private String slug;
    private Integer moduleId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public int getAuteurId() { return auteurId; }
    public void setAuteurId(int auteurId) { this.auteurId = auteurId; }
    public LocalDateTime getDatePublication() { return datePublication; }
    public void setDatePublication(LocalDateTime datePublication) { this.datePublication = datePublication; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Integer getModuleId() { return moduleId; }
    public void setModuleId(Integer moduleId) { this.moduleId = moduleId; }

    @Override
    public String toString() {
        return titre + " / " + categorie;
    }
}
