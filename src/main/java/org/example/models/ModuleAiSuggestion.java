package org.example.models;

public class ModuleAiSuggestion {
    private final String titre;
    private final String description;
    private final String contenu;
    private final ModuleNiveau niveau;

    public ModuleAiSuggestion(String titre, String description, String contenu, ModuleNiveau niveau) {
        this.titre = titre;
        this.description = description;
        this.contenu = contenu;
        this.niveau = niveau;
    }

    public String getTitre() {
        return titre;
    }

    public String getDescription() {
        return description;
    }

    public String getContenu() {
        return contenu;
    }

    public ModuleNiveau getNiveau() {
        return niveau;
    }
}
