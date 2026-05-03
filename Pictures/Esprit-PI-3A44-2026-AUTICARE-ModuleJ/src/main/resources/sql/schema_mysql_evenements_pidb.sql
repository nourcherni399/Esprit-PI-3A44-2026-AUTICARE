-- MySQL (base pidb) — tables événements, inscriptions, modules, blog.
-- Exécution manuelle si besoin :
--   mysql -u root -p pidb < src/main/resources/sql/schema_mysql_evenements_pidb.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS evenements (
    id INT AUTO_INCREMENT PRIMARY KEY,
    titre VARCHAR(180) NOT NULL,
    description TEXT,
    date_debut DATETIME NOT NULL,
    date_fin DATETIME NOT NULL,
    lieu VARCHAR(180),
    thematique VARCHAR(120) NULL,
    mode_evenement VARCHAR(40) NULL,
    lien_google_maps VARCHAR(512) NULL,
    lien_zoom_visio VARCHAR(512) NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    places_max INT NOT NULL DEFAULT 0,
    statut VARCHAR(32) NOT NULL DEFAULT 'BROUILLON'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inscriptions_evenement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    evenement_id INT NOT NULL,
    utilisateur_id INT NOT NULL,
    statut VARCHAR(32) NOT NULL DEFAULT 'EN_ATTENTE',
    date_inscription DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_inscription_evt_user (evenement_id, utilisateur_id),
    CONSTRAINT fk_insc_evenement FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
    CONSTRAINT fk_insc_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS modules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    titre VARCHAR(180) NOT NULL,
    description TEXT,
    categorie VARCHAR(120),
    date_creation DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    ressources_lien VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS `blog` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    titre VARCHAR(500) NOT NULL,
    contenu MEDIUMTEXT,
    auteur_id INT NOT NULL,
    date_publication DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    categorie VARCHAR(120),
    slug VARCHAR(180),
    module_id INT NULL,
    user_id INT NULL,
    CONSTRAINT fk_blog_auteur FOREIGN KEY (auteur_id) REFERENCES `user`(id) ON DELETE CASCADE,
    CONSTRAINT fk_blog_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
