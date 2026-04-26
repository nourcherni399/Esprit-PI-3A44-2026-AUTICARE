-- Exemple : un événement PUBLIE pour tester « Autres événements » et le bouton « Voir » (MySQL pidb).
-- Exécuter une fois : mysql -u root -p pidb < src/main/resources/sql/seed_evenement_demo_publie.sql
-- Si la colonne `thematique` existe (migration), elle reste NULL par défaut.

INSERT INTO evenements (titre, description, date_debut, date_fin, lieu, latitude, longitude, places_max, statut)
SELECT 'Jardin',
       'atelier',
       '2026-04-08 10:30:00',
       '2026-04-08 11:33:00',
       'EL mourouj',
       NULL,
       NULL,
       20,
       'PUBLIE'
WHERE NOT EXISTS (SELECT 1 FROM evenements WHERE titre = 'Jardin' AND lieu = 'EL mourouj');
