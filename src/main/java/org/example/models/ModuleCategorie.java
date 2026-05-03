package org.example.models;

/**
 * Valeurs de la colonne {@code categorie} (ENUM / VARCHAR MySQL) de la table {@code module}.
 * Libellés affichés comme dans le formulaire admin (liste déroulante).
 * <p>
 * MySQL : si la colonne est un ENUM, exécuter aussi {@code sql/module_categorie_enum_mysql.sql}.
 */
public enum ModuleCategorie {
    NON_DEFINI("Non défini"),
    COMPRENDRE_TSA("Comprendre le TSA"),
    AUTONOMIE("Autonomie"),
    COMMUNICATION("Communication"),
    EMOTIONS("Émotions"),
    VIE_QUOTIDIENNE("Vie quotidienne"),
    ACCOMPAGNEMENT("Accompagnement");

    private final String libelle;

    ModuleCategorie(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /**
     * Lit la valeur issue de la base (nom d’énumération, ex. {@code COMPRENDRE_TSA}).
     */
    public static ModuleCategorie fromDb(String s) {
        if (s == null || s.isBlank()) {
            return NON_DEFINI;
        }
        String t = s.trim();
        try {
            return ModuleCategorie.valueOf(t);
        } catch (IllegalArgumentException e) {
            return NON_DEFINI;
        }
    }
}
