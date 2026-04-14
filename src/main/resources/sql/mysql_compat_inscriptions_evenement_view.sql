-- Optionnel : si une ancienne version du client Java interroge encore `inscriptions_evenement`,
-- exécutez ce script sur la base `pidb` pour créer une vue en lecture seule (les INSERT/UPDATE
-- via cette vue peuvent échouer — préférez recompiler et lancer avec `mvn clean javafx:run`).
-- La version actuelle du code utilise directement `inscrit_events`.

CREATE OR REPLACE VIEW `inscriptions_evenement` AS
SELECT
  `id`,
  `evenement_id`,
  `user_id` AS `utilisateur_id`,
  CASE `statut`
    WHEN 'en_attente' THEN 'EN_ATTENTE'
    WHEN 'accepte' THEN 'ACCEPTE'
    WHEN 'refuse' THEN 'REFUSE'
    ELSE 'EN_ATTENTE'
  END AS `statut`,
  TIMESTAMP(`date_inscrit`) AS `date_inscription`
FROM `inscrit_events`;
