-- Heure réelle du RDV (DATETIME). À utiliser si la table n’a que date_rdv (DATE) → heure 00:00 en Java.
-- L’application exécute aussi ensureRendezVousDateHeureDatetimeColumn au démarrage.

ALTER TABLE rendez_vous
    ADD COLUMN date_heure DATETIME NULL DEFAULT NULL;

UPDATE rendez_vous rv
INNER JOIN disponibilite d ON d.id = rv.disponibilite_id
SET rv.date_heure = d.debut
WHERE d.debut IS NOT NULL
  AND rv.disponibilite_id IS NOT NULL AND rv.disponibilite_id <> 0
  AND (rv.date_heure IS NULL
       OR (DATE(rv.date_heure) = DATE(d.debut) AND rv.date_heure <> d.debut));

UPDATE rendez_vous
SET date_heure = CAST(date_rdv AS DATETIME)
WHERE date_heure IS NULL AND date_rdv IS NOT NULL;
