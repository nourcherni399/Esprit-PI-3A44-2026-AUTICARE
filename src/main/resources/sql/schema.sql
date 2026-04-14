-- SQLite : fichier pi.db (voir MyDatabase.java)
-- Appliquer avec : sqlite3 pi.db < src/main/resources/sql/schema.sql

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom VARCHAR(120) NOT NULL,
    prenom VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    telephone VARCHAR(40),
    mot_de_passe_hash VARCHAR(255) NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('ADMIN','MEDECIN','PARENT','PATIENT','USER')),
    actif INTEGER NOT NULL DEFAULT 1,
    specialite VARCHAR(120),
    cabinet VARCHAR(150),
    relation_parent VARCHAR(100),
    date_naissance TEXT,
    adresse VARCHAR(255),
    sexe VARCHAR(20),
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS produits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom VARCHAR(150) NOT NULL,
    description TEXT,
    prix REAL NOT NULL,
    categorie VARCHAR(100),
    stock INTEGER DEFAULT 0,
    image_path VARCHAR(255),
    publie INTEGER DEFAULT 1,
    note_moyenne REAL
);

CREATE TABLE IF NOT EXISTS disponibilites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    medecin_id INTEGER NOT NULL,
    debut TEXT NOT NULL,
    fin TEXT NOT NULL,
    FOREIGN KEY (medecin_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS rendez_vous (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    medecin_id INTEGER NOT NULL,
    patient_id INTEGER NOT NULL,
    date_heure TEXT NOT NULL,
    motif VARCHAR(255),
    statut TEXT DEFAULT 'PLANIFIE' CHECK (statut IN ('PLANIFIE','ANNULE','TERMINE')),
    notes TEXT,
    FOREIGN KEY (medecin_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS evenements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    titre VARCHAR(180) NOT NULL,
    description TEXT,
    date_debut TEXT NOT NULL,
    date_fin TEXT NOT NULL,
    lieu VARCHAR(180),
    latitude REAL,
    longitude REAL,
    places_max INTEGER DEFAULT 0,
    statut TEXT DEFAULT 'BROUILLON' CHECK (statut IN ('BROUILLON','PUBLIE','TERMINE'))
);

CREATE TABLE IF NOT EXISTS inscriptions_evenement (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    evenement_id INTEGER NOT NULL,
    utilisateur_id INTEGER NOT NULL,
    statut TEXT DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE','ACCEPTE','REFUSE')),
    date_inscription TEXT DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (evenement_id, utilisateur_id),
    FOREIGN KEY (evenement_id) REFERENCES evenements(id) ON DELETE CASCADE,
    FOREIGN KEY (utilisateur_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS modules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    titre VARCHAR(180) NOT NULL,
    description TEXT,
    categorie VARCHAR(120),
    date_creation TEXT DEFAULT CURRENT_TIMESTAMP,
    ressources_lien VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS articles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    titre VARCHAR(180) NOT NULL,
    contenu TEXT,
    auteur_id INTEGER NOT NULL,
    date_publication TEXT DEFAULT CURRENT_TIMESTAMP,
    categorie VARCHAR(120),
    slug VARCHAR(180),
    module_id INTEGER,
    FOREIGN KEY (auteur_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL
);

INSERT INTO users (nom, prenom, email, telephone, mot_de_passe_hash, role, actif)
SELECT 'Admin', 'Default', 'admin@auticare.local', '00000000',
       '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
       'ADMIN', 1
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'admin@auticare.local'
);
