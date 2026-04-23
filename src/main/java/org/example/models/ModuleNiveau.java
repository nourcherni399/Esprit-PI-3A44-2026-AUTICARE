package org.example.models;

/**
 * Valeurs de la colonne {@code niveau} (ENUM MySQL) de la table {@code module}.
 */
public enum ModuleNiveau {
    difficile,
    moyen,
    facile;

    public static ModuleNiveau fromDb(String s) {
        if (s == null || s.isBlank()) {
            return moyen;
        }
        try {
            return ModuleNiveau.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return moyen;
        }
    }
}
