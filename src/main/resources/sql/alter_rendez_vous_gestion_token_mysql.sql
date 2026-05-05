-- Jeton pour liens « gérer le RDV » dans les e-mails (MySQL / table rendez_vous).
-- L’application exécute aussi une migration automatique au démarrage (MyDatabase).

ALTER TABLE rendez_vous
    ADD COLUMN gestion_token VARCHAR(64) NULL DEFAULT NULL;
