-- Notes des médecins (patients connectés). Exécuter une fois sur la base MySQL Symfony.

CREATE TABLE IF NOT EXISTS `medecin_rating` (
    `id` int NOT NULL AUTO_INCREMENT,
    `medecin_id` int NOT NULL,
    `patient_id` int NOT NULL,
    `stars` tinyint NOT NULL,
    `commentaire` varchar(2000) NULL DEFAULT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_medecin_rating_patient` (`medecin_id`, `patient_id`),
    KEY `idx_medecin_rating_medecin` (`medecin_id`),
    CONSTRAINT `fk_medecin_rating_medecin` FOREIGN KEY (`medecin_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_medecin_rating_patient` FOREIGN KEY (`patient_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
