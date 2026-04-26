-- Messages utilisateur → organisateur (événement) + file d’attente notifications admin.
-- mysql -u root -p pidb < src/main/resources/sql/migration_evenement_messages_notifications.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS evenement_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    evenement_id INT NOT NULL,
    expediteur_user_id INT NOT NULL,
    destinataire_user_id INT NULL,
    corps TEXT NOT NULL,
    date_envoi DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
    FOREIGN KEY (expediteur_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    FOREIGN KEY (destinataire_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    INDEX idx_evmsg_evt_user (evenement_id, expediteur_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications_admin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(48) NOT NULL,
    evenement_id INT NULL,
    expediteur_user_id INT NOT NULL,
    resume VARCHAR(512) NOT NULL,
    lu TINYINT(1) NOT NULL DEFAULT 0,
    date_creation DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_evenement FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_user FOREIGN KEY (expediteur_user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    INDEX idx_notif_lu (lu),
    INDEX idx_notif_date (date_creation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications_user (
    id INT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id INT NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    evenement_id INT NULL,
    resume VARCHAR(512) NOT NULL,
    lu TINYINT(1) NOT NULL DEFAULT 0,
    date_creation DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (utilisateur_id) REFERENCES `user`(id) ON DELETE CASCADE,
    FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
    INDEX idx_nu_user_lu (utilisateur_id, lu),
    INDEX idx_nu_user_date (utilisateur_id, date_creation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
