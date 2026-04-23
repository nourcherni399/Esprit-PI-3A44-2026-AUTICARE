-- MySQL / table Symfony `user` : tarif affiché sur « Prendre rendez-vous » (profil médecin).
-- Exécuter une fois sur la base utilisée par l’application Java.

ALTER TABLE `user`
    ADD COLUMN `tarif_consultation` VARCHAR(32) NULL DEFAULT NULL
        COMMENT 'Tarif consultation (ex. 80 ou 80,5), affiché au public' AFTER `adresse`;
