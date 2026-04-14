-- Migration MySQL : emplacements `stock` + FK `produit.stock_id`.
-- Exécuter sur la base `pidb` (répéter : ignorer « Duplicate column » si déjà appliqué).

CREATE TABLE IF NOT EXISTS `stock` (
    id INT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    quantite INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `stock` (nom, quantite)
SELECT 'Dépôt principal', 0
FROM (SELECT 1 AS c) t
WHERE NOT EXISTS (SELECT 1 FROM `stock` LIMIT 1);

ALTER TABLE `produit` ADD COLUMN `stock_id` INT NULL;

UPDATE `produit` SET `stock_id` = (SELECT s.id FROM `stock` s ORDER BY s.id ASC LIMIT 1)
WHERE `stock_id` IS NULL;

ALTER TABLE `produit` MODIFY COLUMN `stock_id` INT NOT NULL;

-- Optionnel : contrainte référentielle (échoue si déjà créée)
-- ALTER TABLE `produit` ADD CONSTRAINT `fk_produit_stock` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`);
