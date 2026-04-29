-- Propositions d'idees d'evenement (participants acceptes uniquement).
-- mysql -u root -p pidb < src/main/resources/sql/migration_participant_event_ideas.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS participant_event_ideas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    evenement_id INT NOT NULL,
    participant_id INT NOT NULL,
    description TEXT NOT NULL,
    theme_preference VARCHAR(120) NOT NULL,
    format_preference VARCHAR(40) NOT NULL,
    preferred_period VARCHAR(120) NOT NULL,
    participant_comment VARCHAR(1000) NULL,
    created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_idea_event FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
    CONSTRAINT fk_idea_user FOREIGN KEY (participant_id) REFERENCES `user`(id) ON DELETE CASCADE,
    INDEX idx_idea_created (created_at),
    INDEX idx_idea_theme (theme_preference),
    INDEX idx_idea_format (format_preference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
