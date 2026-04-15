-- Lien rendez_vous → disponibilite (prise de RDV publique). Exécuter si la migration auto (MyDatabase) n’a pas tourné.

ALTER TABLE `rendez_vous`
    ADD COLUMN `disponibilite_id` INT NULL DEFAULT NULL AFTER `patient_id`;

ALTER TABLE `rendez_vous`
    ADD KEY `idx_rdv_disponibilite` (`disponibilite_id`);

-- Optionnel (contrainte référentielle) :
-- ALTER TABLE `rendez_vous`
--     ADD CONSTRAINT `fk_rdv_disponibilite` FOREIGN KEY (`disponibilite_id`) REFERENCES `disponibilite` (`id`) ON DELETE SET NULL;
