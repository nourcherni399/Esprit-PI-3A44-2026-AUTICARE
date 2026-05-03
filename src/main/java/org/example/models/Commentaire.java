package org.example.models;

import java.time.LocalDateTime;

public class Commentaire {
    private int id;
    private String contenu;
    private String media;
    private boolean isPublished;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModif;
    private Integer userId;
    private int blogId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public String getMedia() { return media; }
    public void setMedia(String media) { this.media = media; }

    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDateModif() { return dateModif; }
    public void setDateModif(LocalDateTime dateModif) { this.dateModif = dateModif; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public int getBlogId() { return blogId; }
    public void setBlogId(int blogId) { this.blogId = blogId; }
}
