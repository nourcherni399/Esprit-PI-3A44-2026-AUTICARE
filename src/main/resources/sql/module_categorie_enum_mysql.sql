-- À exécuter sur MySQL (base pidb) si la colonne `module.categorie` est un type ENUM.
-- Étend la liste pour correspondre à org.example.models.ModuleCategorie

ALTER TABLE `module`
    MODIFY COLUMN `categorie` ENUM(
        'NON_DEFINI',
        'COMPRENDRE_TSA',
        'AUTONOMIE',
        'COMMUNICATION',
        'EMOTIONS',
        'VIE_QUOTIDIENNE',
        'ACCOMPAGNEMENT'
    ) NOT NULL DEFAULT 'COMPRENDRE_TSA';
