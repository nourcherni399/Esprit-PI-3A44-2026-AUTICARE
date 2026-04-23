package org.example.models;

import java.time.LocalDateTime;

/**
 * Entrée d'historique d'action sur un module (journal en mémoire, session courante).
 */
public class ModuleActionEntry {
    private final LocalDateTime dateHeure;
    private final String action;
    private final String moduleTitre;
    private final int moduleId;

    public ModuleActionEntry(LocalDateTime dateHeure, String action, String moduleTitre, int moduleId) {
        this.dateHeure = dateHeure;
        this.action = action;
        this.moduleTitre = moduleTitre;
        this.moduleId = moduleId;
    }

    public LocalDateTime getDateHeure() {
        return dateHeure;
    }

    public String getAction() {
        return action;
    }

    public String getModuleTitre() {
        return moduleTitre;
    }

    public int getModuleId() {
        return moduleId;
    }
}
