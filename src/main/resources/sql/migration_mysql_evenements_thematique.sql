-- MySQL (pidb) : ajouter la thématique pour les cartes « par thème » côté public.
-- Exécuter une fois : mysql -u root -p pidb < migration_mysql_evenements_thematique.sql

ALTER TABLE evenements
    ADD COLUMN thematique VARCHAR(120) NULL AFTER lieu;
