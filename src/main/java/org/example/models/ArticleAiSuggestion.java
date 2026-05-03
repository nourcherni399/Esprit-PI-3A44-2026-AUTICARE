package org.example.models;

public class ArticleAiSuggestion {
    private final String titre;
    private final String type;
    private final String contenu;

    public ArticleAiSuggestion(String titre, String type, String contenu) {
        this.titre = titre;
        this.type = type;
        this.contenu = contenu;
    }

    public String getTitre() {
        return titre;
    }

    public String getType() {
        return type;
    }

    public String getContenu() {
        return contenu;
    }
}
