-- Texte d'avis optionnel sur medecin_rating (après création de la table).
ALTER TABLE `medecin_rating`
    ADD COLUMN `commentaire` VARCHAR(2000) NULL DEFAULT NULL AFTER `stars`;
