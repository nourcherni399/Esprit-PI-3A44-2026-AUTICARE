-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Hôte : 127.0.0.1
-- Généré le : mer. 08 avr. 2026 à 14:55
-- Version du serveur : 10.4.32-MariaDB
-- Version de PHP : 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de données : `pidb`
--

-- --------------------------------------------------------

--
-- Structure de la table `action_history`
--

CREATE TABLE `action_history` (
  `id` int(11) NOT NULL,
  `date_heure` datetime NOT NULL,
  `utilisateur` varchar(255) NOT NULL,
  `action` varchar(255) NOT NULL,
  `module` varchar(255) DEFAULT NULL,
  `details` longtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `avis_produit`
--

CREATE TABLE `avis_produit` (
  `id` int(11) NOT NULL,
  `note` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `produit_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `blog`
--

CREATE TABLE `blog` (
  `id` int(11) NOT NULL,
  `titre` varchar(255) NOT NULL,
  `type` enum('recommandation','plainte','question','experience') NOT NULL,
  `is_published` tinyint(4) NOT NULL,
  `image` varchar(255) NOT NULL,
  `is_urgent` tinyint(4) DEFAULT NULL,
  `is_visible` tinyint(4) NOT NULL,
  `date_creation` datetime NOT NULL,
  `date_modif` datetime NOT NULL,
  `contenu` longtext DEFAULT NULL,
  `module_id` int(11) NOT NULL,
  `user_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `cart`
--

CREATE TABLE `cart` (
  `id` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `cart_item`
--

CREATE TABLE `cart_item` (
  `id` int(11) NOT NULL,
  `quantite` int(11) NOT NULL,
  `prix` double NOT NULL,
  `cart_id` int(11) NOT NULL,
  `produit_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `commande`
--

CREATE TABLE `commande` (
  `id` int(11) NOT NULL,
  `nom` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `telephone` varchar(20) NOT NULL,
  `adresse` varchar(255) NOT NULL,
  `code_postal` varchar(20) NOT NULL,
  `ville` varchar(100) NOT NULL,
  `total` double NOT NULL,
  `statut` varchar(50) NOT NULL,
  `mode_payment` varchar(50) NOT NULL,
  `date_creation` datetime NOT NULL,
  `stripe_session_id` varchar(255) DEFAULT NULL,
  `stripe_payment_intent` varchar(255) DEFAULT NULL,
  `user_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `commentaire`
--

CREATE TABLE `commentaire` (
  `id` int(11) NOT NULL,
  `contenu` longtext NOT NULL,
  `media` varchar(255) DEFAULT NULL,
  `is_published` tinyint(4) NOT NULL,
  `date_creation` datetime NOT NULL,
  `date_modif` datetime DEFAULT NULL,
  `user_id` int(11) DEFAULT NULL,
  `blog_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `commentaire_reaction`
--

CREATE TABLE `commentaire_reaction` (
  `id` int(11) NOT NULL,
  `type` varchar(20) NOT NULL,
  `created_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL,
  `commentaire_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `demande_produit`
--

CREATE TABLE `demande_produit` (
  `id` int(11) NOT NULL,
  `demande_client` longtext NOT NULL,
  `nom` varchar(255) NOT NULL,
  `description` longtext DEFAULT NULL,
  `categorie` varchar(255) NOT NULL,
  `prix_estime` double NOT NULL,
  `budget_client` double DEFAULT NULL,
  `caracteristiques` longtext DEFAULT NULL,
  `donnees_externes` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`donnees_externes`)),
  `statut` varchar(20) NOT NULL,
  `created_at` datetime NOT NULL,
  `validated_at` datetime DEFAULT NULL,
  `demandeur_id` int(11) DEFAULT NULL,
  `validated_by_id` int(11) DEFAULT NULL,
  `produit_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `disponibilite`
--

CREATE TABLE `disponibilite` (
  `id` int(11) NOT NULL,
  `heure_debut` time NOT NULL,
  `heure_fin` time NOT NULL,
  `date` date NOT NULL,
  `duree` int(11) NOT NULL DEFAULT 0,
  `est_dispo` tinyint(4) NOT NULL DEFAULT 1,
  `medecin_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `doctrine_migration_versions`
--

CREATE TABLE `doctrine_migration_versions` (
  `version` varchar(191) NOT NULL,
  `executed_at` datetime DEFAULT NULL,
  `execution_time` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Déchargement des données de la table `doctrine_migration_versions`
--

INSERT INTO `doctrine_migration_versions` (`version`, `executed_at`, `execution_time`) VALUES
('DoctrineMigrations\\Version20260303011322', '2026-03-03 02:13:29', 5700),
('DoctrineMigrations\\Version20260315120000_cervical_slides', '2026-03-15 15:16:44', 673),
('DoctrineMigrations\\Version20260315142459', '2026-03-15 15:25:11', 443),
('DoctrineMigrations\\Version20260315144807', '2026-03-15 15:48:13', 128),
('DoctrineMigrations\\Version20260315144933', '2026-03-15 15:49:39', 36),
('DoctrineMigrations\\Version20260315150755', '2026-03-15 16:08:13', 920),
('DoctrineMigrations\\Version20260315152126', '2026-03-15 16:21:39', 4299),
('DoctrineMigrations\\Version20260315160000_healthinsight_entities', '2026-03-15 15:58:50', 356),
('DoctrineMigrations\\Version20260315170000_drop_auticare_tables', '2026-03-15 16:08:14', 57);

-- --------------------------------------------------------

--
-- Structure de la table `evenement`
--

CREATE TABLE `evenement` (
  `id` int(11) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` longtext DEFAULT NULL,
  `date_event` date NOT NULL,
  `heure_debut` time NOT NULL,
  `heure_fin` time NOT NULL,
  `lieu` varchar(255) DEFAULT NULL,
  `location_url` varchar(500) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `mode` varchar(20) NOT NULL DEFAULT 'presentiel',
  `meeting_url` varchar(500) DEFAULT NULL,
  `image` varchar(500) DEFAULT NULL,
  `thematique_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `favoris`
--

CREATE TABLE `favoris` (
  `id` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL,
  `produit_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `favoris_article`
--

CREATE TABLE `favoris_article` (
  `id` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL,
  `blog_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `favoris_module`
--

CREATE TABLE `favoris_module` (
  `id` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL,
  `module_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `idee_evenement`
--

CREATE TABLE `idee_evenement` (
  `id` int(11) NOT NULL,
  `titre` varchar(255) NOT NULL,
  `description` longtext DEFAULT NULL,
  `theme` varchar(100) DEFAULT NULL,
  `pourquoi` varchar(500) DEFAULT NULL,
  `mots_cle` varchar(255) DEFAULT NULL,
  `score` int(11) DEFAULT NULL,
  `created_at` datetime NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `inscrit_events`
--

CREATE TABLE `inscrit_events` (
  `id` int(11) NOT NULL,
  `date_inscrit` date NOT NULL,
  `est_inscrit` tinyint(4) NOT NULL DEFAULT 1,
  `statut` varchar(20) NOT NULL DEFAULT 'en_attente',
  `user_id` int(11) NOT NULL,
  `evenement_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `ligne_commande`
--

CREATE TABLE `ligne_commande` (
  `id` int(11) NOT NULL,
  `quantite` int(11) NOT NULL,
  `prix` double NOT NULL,
  `sous_total` double NOT NULL,
  `commande_id` int(11) NOT NULL,
  `produit_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `medecin_rating`
--

CREATE TABLE `medecin_rating` (
  `id` int(11) NOT NULL,
  `note` smallint(6) NOT NULL,
  `created_at` datetime NOT NULL,
  `medecin_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `message_evenement`
--

CREATE TABLE `message_evenement` (
  `id` int(11) NOT NULL,
  `contenu` longtext NOT NULL,
  `date_envoi` datetime NOT NULL,
  `envoye_par` varchar(10) NOT NULL,
  `lu` tinyint(4) NOT NULL DEFAULT 0,
  `evenement_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `messenger_messages`
--

CREATE TABLE `messenger_messages` (
  `id` bigint(20) NOT NULL,
  `body` longtext NOT NULL,
  `headers` longtext NOT NULL,
  `queue_name` varchar(190) NOT NULL,
  `created_at` datetime NOT NULL,
  `available_at` datetime NOT NULL,
  `delivered_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `module`
--

CREATE TABLE `module` (
  `id` int(11) NOT NULL,
  `titre` varchar(255) NOT NULL,
  `description` varchar(255) NOT NULL,
  `contenu` longtext DEFAULT NULL,
  `niveau` enum('difficile','moyen','facile') NOT NULL,
  `image` varchar(255) NOT NULL,
  `is_published` tinyint(4) NOT NULL,
  `date_creation` datetime NOT NULL,
  `date_modif` datetime NOT NULL,
  `categorie` enum('','COMPRENDRE_TSA','AUTONOMIE','COMMUNICATION','EMOTIONS','VIE_QUOTIDIENNE','ACCOMPAGNEMENT') NOT NULL,
  `admin_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `module_bookmark`
--

CREATE TABLE `module_bookmark` (
  `id` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL,
  `module_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `module_completion`
--

CREATE TABLE `module_completion` (
  `id` int(11) NOT NULL,
  `completed_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL,
  `module_id` int(11) NOT NULL,
  `quiz_attempt_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `module_quiz`
--

CREATE TABLE `module_quiz` (
  `id` int(11) NOT NULL,
  `questions_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`questions_json`)),
  `created_at` datetime NOT NULL,
  `module_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `module_quiz_attempt`
--

CREATE TABLE `module_quiz_attempt` (
  `id` int(11) NOT NULL,
  `score_percent` decimal(5,2) NOT NULL,
  `passed` tinyint(4) NOT NULL,
  `answers_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`answers_json`)),
  `completed_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL,
  `module_id` int(11) NOT NULL,
  `quiz_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `note`
--

CREATE TABLE `note` (
  `id` int(11) NOT NULL,
  `contenu` longtext NOT NULL,
  `date_creation` datetime NOT NULL,
  `medecin_id` int(11) NOT NULL,
  `patient_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `notification`
--

CREATE TABLE `notification` (
  `id` int(11) NOT NULL,
  `type` varchar(50) NOT NULL,
  `lu` tinyint(4) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL,
  `destinataire_id` int(11) NOT NULL,
  `rendez_vous_id` int(11) DEFAULT NULL,
  `commande_id` int(11) DEFAULT NULL,
  `demande_produit_id` int(11) DEFAULT NULL,
  `produit_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `order`
--

CREATE TABLE `order` (
  `id` int(11) NOT NULL,
  `total_price` double NOT NULL,
  `status` varchar(50) NOT NULL,
  `payment_method` varchar(50) NOT NULL,
  `card_type` varchar(50) DEFAULT NULL,
  `first_name` varchar(255) NOT NULL,
  `last_name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `phone` varchar(255) NOT NULL,
  `address` varchar(255) NOT NULL,
  `city` varchar(255) NOT NULL,
  `postal_code` varchar(10) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `order_item`
--

CREATE TABLE `order_item` (
  `id` int(11) NOT NULL,
  `quantite` int(11) NOT NULL,
  `prix` double NOT NULL,
  `order_id` int(11) NOT NULL,
  `produit_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `produit`
--

CREATE TABLE `produit` (
  `id` int(11) NOT NULL,
  `nom` varchar(255) NOT NULL,
  `description` longtext DEFAULT NULL,
  `categorie` enum('sensoriels','bruit_et_environnement','education_apprentissage','communication_langage','jeux_therapeutiques_developpement','bien_etre_relaxation','vie_quotidienne') DEFAULT NULL,
  `prix` double NOT NULL,
  `disponibilite` tinyint(4) NOT NULL DEFAULT 1,
  `image` varchar(500) DEFAULT NULL,
  `sku` varchar(255) DEFAULT NULL,
  `statut_publication` varchar(20) NOT NULL DEFAULT 'brouillon',
  `note_moyenne` double NOT NULL DEFAULT 0,
  `nb_avis` int(11) NOT NULL DEFAULT 0,
  `seuil_alerte` int(11) DEFAULT NULL,
  `quantite` int(11) NOT NULL DEFAULT 1,
  `genere_par_ia` tinyint(4) NOT NULL DEFAULT 0,
  `valide` tinyint(4) NOT NULL DEFAULT 1,
  `user_id` int(11) DEFAULT NULL,
  `stock_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `produit_historique`
--

CREATE TABLE `produit_historique` (
  `id` int(11) NOT NULL,
  `champ` varchar(255) NOT NULL,
  `ancienne_valeur` varchar(500) DEFAULT NULL,
  `nouvelle_valeur` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `produit_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `produit_image`
--

CREATE TABLE `produit_image` (
  `id` int(11) NOT NULL,
  `chemin` varchar(500) NOT NULL,
  `ordre` int(11) NOT NULL,
  `produit_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `rendez_vous`
--

CREATE TABLE `rendez_vous` (
  `id` int(11) NOT NULL,
  `date_rdv` date DEFAULT NULL,
  `nom` varchar(255) NOT NULL,
  `prenom` varchar(255) NOT NULL,
  `adresse` varchar(500) DEFAULT NULL,
  `date_naissance` date DEFAULT NULL,
  `telephone` varchar(30) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `note_patient` longtext DEFAULT 'vide',
  `status` enum('en_attente','confirmer','annuler') DEFAULT NULL,
  `motif` enum('urgence','suivie','normal') DEFAULT NULL,
  `rappel_sms_envoye_at` datetime DEFAULT NULL,
  `token_annulation` varchar(64) DEFAULT NULL,
  `medecin_id` int(11) NOT NULL,
  `disponibilite_id` int(11) DEFAULT NULL,
  `patient_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `ressource`
--

CREATE TABLE `ressource` (
  `id` int(11) NOT NULL,
  `titre` varchar(255) NOT NULL,
  `type_ressource` varchar(20) NOT NULL,
  `contenu` longtext DEFAULT NULL,
  `date_creation` datetime NOT NULL,
  `datemodif` datetime NOT NULL,
  `ordre` int(11) DEFAULT NULL,
  `is_active` tinyint(4) NOT NULL,
  `module_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `stock`
--

CREATE TABLE `stock` (
  `id` int(11) NOT NULL,
  `nom` varchar(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `thematique`
--

CREATE TABLE `thematique` (
  `id` int(11) NOT NULL,
  `nom_thematique` varchar(255) NOT NULL,
  `code_thematique` varchar(50) NOT NULL,
  `description` longtext DEFAULT NULL,
  `couleur` varchar(20) DEFAULT NULL,
  `image` varchar(500) DEFAULT NULL,
  `sous_titre` varchar(255) DEFAULT NULL,
  `ordre` smallint(6) DEFAULT NULL,
  `actif` tinyint(4) NOT NULL DEFAULT 1,
  `public_cible` enum('Enfant','Parent','Médecin','Éducateur','Aidant','Autre') DEFAULT NULL,
  `niveau_difficulte` enum('Débutant','Intermédiaire','Avancé') DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `user`
--

CREATE TABLE `user` (
  `id` int(11) NOT NULL,
  `nom` varchar(255) NOT NULL,
  `prenom` varchar(255) NOT NULL,
  `email` varchar(180) NOT NULL,
  `telephone` int(11) NOT NULL,
  `password` varchar(255) NOT NULL,
  `is_active` tinyint(1) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `role` enum('ROLE_ADMIN','ROLE_PARENT','ROLE_PATIENT','ROLE_MEDECIN','ROLE_USER') DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `reset_pin` varchar(6) DEFAULT NULL,
  `reset_pin_expires_at` datetime DEFAULT NULL,
  `email_verification_token` varchar(64) DEFAULT NULL,
  `email_verification_expires_at` datetime DEFAULT NULL,
  `email_verified_at` datetime DEFAULT NULL,
  `google_id` varchar(255) DEFAULT NULL,
  `data_face_api` longtext DEFAULT NULL,
  `type` varchar(255) NOT NULL,
  `date_naissance` date DEFAULT NULL,
  `adresse` varchar(500) DEFAULT NULL,
  `sexe` varchar(20) DEFAULT NULL,
  `relation_avec_patient` varchar(100) DEFAULT NULL,
  `specialite` varchar(255) DEFAULT NULL,
  `nom_cabinet` varchar(255) DEFAULT NULL,
  `adresse_cabinet` varchar(500) DEFAULT NULL,
  `telephone_cabinet` varchar(30) DEFAULT NULL,
  `tarif_consultation` double DEFAULT NULL,
  `google_calendar_id` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `user`
--

INSERT INTO `user` (`id`, `nom`, `prenom`, `email`, `telephone`, `password`, `is_active`, `created_at`, `updated_at`, `role`, `image`, `reset_pin`, `reset_pin_expires_at`, `email_verification_token`, `email_verification_expires_at`, `email_verified_at`, `google_id`, `data_face_api`, `type`, `date_naissance`, `adresse`, `sexe`, `relation_avec_patient`, `specialite`, `nom_cabinet`, `adresse_cabinet`, `telephone_cabinet`, `tarif_consultation`, `google_calendar_id`) VALUES
(1, 'hedil', 'amara', 'amarahedil8@gmail.com', 26666289, '$2y$13$iMlj3PLSXMqHNCftTAdAa.TCWfjZaxzMuUIEqwDOZA53iIrPgP1KO', 0, '2026-03-03 02:14:29', '2026-03-03 02:15:02', 'ROLE_ADMIN', NULL, NULL, NULL, 'c50ce1d2951fffea3cde44c58d3cb1708eaddfe55d954b899cf5063fd4eaf96e', '2026-03-04 02:14:29', NULL, '104093699741050298220', '[-0.0669427290558815,-0.033045459538698196,0.08363857865333557,-0.048455141484737396,-0.0741039589047432,-0.0867607444524765,-0.07452745735645294,-0.07998772710561752,0.1553529053926468,-0.1831359714269638,0.15097860991954803,-0.03231506049633026,-0.23088595271110535,0.07256700843572617,0.02085789665579796,0.09508200734853745,-0.19087374210357666,-0.04527490958571434,-0.04019656404852867,-0.045600444078445435,0.09299588948488235,-0.015459442511200905,0.04982466250658035,0.11276064068078995,-0.15157344937324524,-0.3016519546508789,-0.17652347683906555,-0.11550446599721909,-0.09219639748334885,-0.09336736053228378,0.013989787548780441,0.020361924543976784,-0.09020192921161652,0.03843618184328079,-0.029171716421842575,0.05532503500580788,-0.026332326233386993,-0.15251244604587555,0.19561408460140228,0.010708282701671124,-0.21338994801044464,-0.04107591137290001,0.02546042948961258,0.19398874044418335,0.17518863081932068,0.02808421105146408,0.01350709330290556,-0.02174517512321472,0.13955466449260712,-0.3040635287761688,0.00558332446962595,0.14590460062026978,-0.01791294664144516,0.05993456020951271,0.1430220901966095,-0.15481026470661163,0.0743187963962555,0.1070576086640358,-0.1343832015991211,0.04976818338036537,0.010423735715448856,-0.07757587730884552,0.06303957849740982,-0.02738305926322937,0.18726477026939392,0.11249825358390808,-0.14713090658187866,-0.06954549998044968,0.0909905657172203,-0.20980122685432434,-0.04038187861442566,0.11589698493480682,-0.16544778645038605,-0.2667011022567749,-0.24636052548885345,-0.03614780306816101,0.3855383098125458,0.15551838278770447,-0.11486291885375977,0.02288583107292652,-0.0897965133190155,-0.054061707109212875,0.10027500241994858,0.1523606926202774,0.04649234563112259,0.010538062080740929,-0.09053070098161697,0.043270956724882126,0.21249231696128845,-0.04603631794452667,0.01506826002150774,0.30642247200012207,-0.013170880265533924,0.015159237198531628,-0.0035239779390394688,0.04772224649786949,-0.09394080936908722,-0.02696855552494526,-0.08611596375703812,0.049327678978443146,0.028513958677649498,-0.05683930218219757,-0.05704135075211525,0.056071799248456955,-0.2277681827545166,0.09061767160892487,-0.024402689188718796,-0.07466055452823639,-0.052156962454319,-0.03494281694293022,-0.17447778582572937,0.002965744584798813,0.16687141358852386,-0.3112605810165405,0.07805273681879044,0.2266208678483963,0.008133837021887302,0.14383819699287415,-0.020880162715911865,0.04606496915221214,-0.03118029423058033,-0.08510608971118927,-0.17092080414295197,-0.018932225182652473,0.01054089330136776,0.02012423612177372,-0.04507887735962868,-0.031691618263721466]', 'admin', NULL, NULL, NULL, 'Tuteur', NULL, NULL, NULL, NULL, NULL, NULL),
(2, 'bani', 'emna', 'emna1340@gmail.com', 21041783, '$2y$13$9OJrhr93jX1mIjy5zEFXYe/gcaH3xh3gXfoYsJv2ktuh0z9WowSzS', 1, '2026-03-03 02:45:05', '2026-03-03 02:46:34', 'ROLE_PARENT', NULL, NULL, NULL, NULL, NULL, '2026-03-03 02:45:34', '117565326139024374020', NULL, 'parent', NULL, NULL, NULL, 'Mere', NULL, NULL, NULL, NULL, NULL, NULL),
(5, 'skon', 'ellefy', 'skonellefy@gmail.com', 21041783, '$2y$13$ZGqK5kKJkUfql0sYzQNVwOxRUoR8SW5EUpkvx/AegvMQ0ue1JSGbS', 1, '2026-03-03 03:07:15', '2026-03-03 03:07:15', 'ROLE_MEDECIN', 'uploads/users/WIN-20260217-18-48-45-Pro-69a64253640ef.jpg', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'medcin', NULL, NULL, NULL, NULL, 'psy', 'doudou', 'bardo', '21041783', 50, NULL),
(6, 'Admin', 'Admin', 'admin@auticare.fr', 0, '$2y$13$Poka8i5y7gd8oRb3e.fCO.JE0JKFT9ZRXgiJCYeMsCqPphG8X3A0W', 1, '2026-03-15 15:54:33', '2026-03-15 15:54:33', 'ROLE_ADMIN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'admin', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

-- --------------------------------------------------------

--
-- Structure de la table `user_highlight`
--

CREATE TABLE `user_highlight` (
  `id` int(11) NOT NULL,
  `target_type` varchar(20) NOT NULL,
  `target_id` int(11) NOT NULL,
  `start_offset` int(11) NOT NULL,
  `end_offset` int(11) NOT NULL,
  `color` varchar(20) NOT NULL DEFAULT 'yellow',
  `created_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `user_history`
--

CREATE TABLE `user_history` (
  `id` int(11) NOT NULL,
  `action` varchar(30) NOT NULL,
  `item_type` varchar(30) NOT NULL,
  `item_id` int(11) DEFAULT NULL,
  `metadata` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`metadata`)),
  `created_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `user_preference`
--

CREATE TABLE `user_preference` (
  `id` int(11) NOT NULL,
  `category` varchar(120) NOT NULL,
  `weight` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Index pour les tables déchargées
--

--
-- Index pour la table `action_history`
--
ALTER TABLE `action_history`
  ADD PRIMARY KEY (`id`);

--
-- Index pour la table `avis_produit`
--
ALTER TABLE `avis_produit`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `avis_produit_user_unique` (`produit_id`,`user_id`),
  ADD KEY `IDX_2A67C21F347EFB` (`produit_id`),
  ADD KEY `IDX_2A67C21A76ED395` (`user_id`);

--
-- Index pour la table `blog`
--
ALTER TABLE `blog`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_C0155143AFC2B591` (`module_id`),
  ADD KEY `IDX_C0155143A76ED395` (`user_id`);

--
-- Index pour la table `cart`
--
ALTER TABLE `cart`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_BA388B7A76ED395` (`user_id`);

--
-- Index pour la table `cart_item`
--
ALTER TABLE `cart_item`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_F0FE25271AD5CDBF` (`cart_id`),
  ADD KEY `IDX_F0FE2527F347EFB` (`produit_id`);

--
-- Index pour la table `commande`
--
ALTER TABLE `commande`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_6EEAA67DA76ED395` (`user_id`);

--
-- Index pour la table `commentaire`
--
ALTER TABLE `commentaire`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_67F068BCA76ED395` (`user_id`),
  ADD KEY `IDX_67F068BCDAE07E97` (`blog_id`);

--
-- Index pour la table `commentaire_reaction`
--
ALTER TABLE `commentaire_reaction`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_56C6CF2BA76ED395` (`user_id`),
  ADD KEY `IDX_56C6CF2BBA9CD190` (`commentaire_id`);

--
-- Index pour la table `demande_produit`
--
ALTER TABLE `demande_produit`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UNIQ_19525788F347EFB` (`produit_id`),
  ADD KEY `IDX_1952578895A6EE59` (`demandeur_id`),
  ADD KEY `IDX_19525788C69DE5E5` (`validated_by_id`);

--
-- Index pour la table `disponibilite`
--
ALTER TABLE `disponibilite`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_2CBACE2F4F31A84` (`medecin_id`);

--
-- Index pour la table `doctrine_migration_versions`
--
ALTER TABLE `doctrine_migration_versions`
  ADD PRIMARY KEY (`version`);

--
-- Index pour la table `evenement`
--
ALTER TABLE `evenement`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_B26681E476556AF` (`thematique_id`);

--
-- Index pour la table `favoris`
--
ALTER TABLE `favoris`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_8933C432A76ED395` (`user_id`),
  ADD KEY `IDX_8933C432F347EFB` (`produit_id`);

--
-- Index pour la table `favoris_article`
--
ALTER TABLE `favoris_article`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_B7D0A763A76ED395` (`user_id`),
  ADD KEY `IDX_B7D0A763DAE07E97` (`blog_id`);

--
-- Index pour la table `favoris_module`
--
ALTER TABLE `favoris_module`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_36421295A76ED395` (`user_id`),
  ADD KEY `IDX_36421295AFC2B591` (`module_id`);

--
-- Index pour la table `idee_evenement`
--
ALTER TABLE `idee_evenement`
  ADD PRIMARY KEY (`id`);

--
-- Index pour la table `inscrit_events`
--
ALTER TABLE `inscrit_events`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UNIQ_USER_EVENT` (`user_id`,`evenement_id`),
  ADD KEY `IDX_8079EEFAA76ED395` (`user_id`),
  ADD KEY `IDX_8079EEFAFD02F13` (`evenement_id`);

--
-- Index pour la table `ligne_commande`
--
ALTER TABLE `ligne_commande`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_3170B74B82EA2E54` (`commande_id`),
  ADD KEY `IDX_3170B74BF347EFB` (`produit_id`);

--
-- Index pour la table `medecin_rating`
--
ALTER TABLE `medecin_rating`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `medecin_user_unique` (`medecin_id`,`user_id`),
  ADD KEY `IDX_4030A62F4F31A84` (`medecin_id`),
  ADD KEY `IDX_4030A62FA76ED395` (`user_id`);

--
-- Index pour la table `message_evenement`
--
ALTER TABLE `message_evenement`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_619F6E28FD02F13` (`evenement_id`),
  ADD KEY `IDX_619F6E28A76ED395` (`user_id`);

--
-- Index pour la table `messenger_messages`
--
ALTER TABLE `messenger_messages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_75EA56E0FB7336F0E3BD61CE16BA31DBBF396750` (`queue_name`,`available_at`,`delivered_at`,`id`);

--
-- Index pour la table `module`
--
ALTER TABLE `module`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_C242628642B8210` (`admin_id`);

--
-- Index pour la table `module_bookmark`
--
ALTER TABLE `module_bookmark`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_BEA241E3A76ED395` (`user_id`),
  ADD KEY `IDX_BEA241E3AFC2B591` (`module_id`);

--
-- Index pour la table `module_completion`
--
ALTER TABLE `module_completion`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uniq_user_module_completion` (`user_id`,`module_id`),
  ADD KEY `IDX_AD331CE3A76ED395` (`user_id`),
  ADD KEY `IDX_AD331CE3AFC2B591` (`module_id`),
  ADD KEY `IDX_AD331CE3F8FE9957` (`quiz_attempt_id`);

--
-- Index pour la table `module_quiz`
--
ALTER TABLE `module_quiz`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_1E2EBF9EAFC2B591` (`module_id`);

--
-- Index pour la table `module_quiz_attempt`
--
ALTER TABLE `module_quiz_attempt`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_62E53734A76ED395` (`user_id`),
  ADD KEY `IDX_62E53734AFC2B591` (`module_id`),
  ADD KEY `IDX_62E53734853CD175` (`quiz_id`);

--
-- Index pour la table `note`
--
ALTER TABLE `note`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_CFBDFA144F31A84` (`medecin_id`),
  ADD KEY `IDX_CFBDFA146B899279` (`patient_id`);

--
-- Index pour la table `notification`
--
ALTER TABLE `notification`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_BF5476CAA4F84F6E` (`destinataire_id`),
  ADD KEY `IDX_BF5476CA91EF7EAA` (`rendez_vous_id`),
  ADD KEY `IDX_BF5476CA82EA2E54` (`commande_id`),
  ADD KEY `IDX_BF5476CAF9F8745A` (`demande_produit_id`),
  ADD KEY `IDX_BF5476CAF347EFB` (`produit_id`);

--
-- Index pour la table `order`
--
ALTER TABLE `order`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_F5299398A76ED395` (`user_id`);

--
-- Index pour la table `order_item`
--
ALTER TABLE `order_item`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_52EA1F098D9F6D38` (`order_id`),
  ADD KEY `IDX_52EA1F09F347EFB` (`produit_id`);

--
-- Index pour la table `produit`
--
ALTER TABLE `produit`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UNIQ_29A5EC27F9038C4` (`sku`),
  ADD KEY `IDX_29A5EC27A76ED395` (`user_id`),
  ADD KEY `IDX_29A5EC27DCD6110` (`stock_id`);

--
-- Index pour la table `produit_historique`
--
ALTER TABLE `produit_historique`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_4487ECE1F347EFB` (`produit_id`),
  ADD KEY `IDX_4487ECE1A76ED395` (`user_id`);

--
-- Index pour la table `produit_image`
--
ALTER TABLE `produit_image`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_F5A163CBF347EFB` (`produit_id`);

--
-- Index pour la table `rendez_vous`
--
ALTER TABLE `rendez_vous`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UNIQ_65E8AA0AEAA36C3A` (`token_annulation`),
  ADD KEY `IDX_65E8AA0A4F31A84` (`medecin_id`),
  ADD KEY `IDX_65E8AA0A2B9D6493` (`disponibilite_id`),
  ADD KEY `IDX_65E8AA0A6B899279` (`patient_id`);

--
-- Index pour la table `ressource`
--
ALTER TABLE `ressource`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_939F4544AFC2B591` (`module_id`);

--
-- Index pour la table `stock`
--
ALTER TABLE `stock`
  ADD PRIMARY KEY (`id`);

--
-- Index pour la table `thematique`
--
ALTER TABLE `thematique`
  ADD PRIMARY KEY (`id`);

--
-- Index pour la table `user`
--
ALTER TABLE `user`
  ADD PRIMARY KEY (`id`);

--
-- Index pour la table `user_highlight`
--
ALTER TABLE `user_highlight`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_C97855D0A76ED395` (`user_id`);

--
-- Index pour la table `user_history`
--
ALTER TABLE `user_history`
  ADD PRIMARY KEY (`id`),
  ADD KEY `IDX_7FB76E41A76ED395` (`user_id`);

--
-- Index pour la table `user_preference`
--
ALTER TABLE `user_preference`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uniq_user_preference_user_category` (`user_id`,`category`),
  ADD KEY `IDX_FA0E76BFA76ED395` (`user_id`);

--
-- AUTO_INCREMENT pour les tables déchargées
--

--
-- AUTO_INCREMENT pour la table `action_history`
--
ALTER TABLE `action_history`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `avis_produit`
--
ALTER TABLE `avis_produit`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `blog`
--
ALTER TABLE `blog`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `cart`
--
ALTER TABLE `cart`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `cart_item`
--
ALTER TABLE `cart_item`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `commande`
--
ALTER TABLE `commande`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `commentaire`
--
ALTER TABLE `commentaire`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `commentaire_reaction`
--
ALTER TABLE `commentaire_reaction`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `demande_produit`
--
ALTER TABLE `demande_produit`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `disponibilite`
--
ALTER TABLE `disponibilite`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `evenement`
--
ALTER TABLE `evenement`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `favoris`
--
ALTER TABLE `favoris`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `favoris_article`
--
ALTER TABLE `favoris_article`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `favoris_module`
--
ALTER TABLE `favoris_module`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `idee_evenement`
--
ALTER TABLE `idee_evenement`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `inscrit_events`
--
ALTER TABLE `inscrit_events`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `ligne_commande`
--
ALTER TABLE `ligne_commande`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `medecin_rating`
--
ALTER TABLE `medecin_rating`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `message_evenement`
--
ALTER TABLE `message_evenement`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `messenger_messages`
--
ALTER TABLE `messenger_messages`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `module`
--
ALTER TABLE `module`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `module_bookmark`
--
ALTER TABLE `module_bookmark`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `module_completion`
--
ALTER TABLE `module_completion`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `module_quiz`
--
ALTER TABLE `module_quiz`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `module_quiz_attempt`
--
ALTER TABLE `module_quiz_attempt`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `note`
--
ALTER TABLE `note`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `notification`
--
ALTER TABLE `notification`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `order`
--
ALTER TABLE `order`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `order_item`
--
ALTER TABLE `order_item`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `produit`
--
ALTER TABLE `produit`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `produit_historique`
--
ALTER TABLE `produit_historique`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `produit_image`
--
ALTER TABLE `produit_image`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `rendez_vous`
--
ALTER TABLE `rendez_vous`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `ressource`
--
ALTER TABLE `ressource`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `stock`
--
ALTER TABLE `stock`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `thematique`
--
ALTER TABLE `thematique`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `user`
--
ALTER TABLE `user`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT pour la table `user_highlight`
--
ALTER TABLE `user_highlight`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `user_history`
--
ALTER TABLE `user_history`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT pour la table `user_preference`
--
ALTER TABLE `user_preference`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- Contraintes pour les tables déchargées
--

--
-- Contraintes pour la table `avis_produit`
--
ALTER TABLE `avis_produit`
  ADD CONSTRAINT `FK_2A67C21A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_2A67C21F347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `blog`
--
ALTER TABLE `blog`
  ADD CONSTRAINT `FK_C0155143A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_C0155143AFC2B591` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`);

--
-- Contraintes pour la table `cart`
--
ALTER TABLE `cart`
  ADD CONSTRAINT `FK_BA388B7A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `cart_item`
--
ALTER TABLE `cart_item`
  ADD CONSTRAINT `FK_F0FE25271AD5CDBF` FOREIGN KEY (`cart_id`) REFERENCES `cart` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_F0FE2527F347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`);

--
-- Contraintes pour la table `commande`
--
ALTER TABLE `commande`
  ADD CONSTRAINT `FK_6EEAA67DA76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);

--
-- Contraintes pour la table `commentaire`
--
ALTER TABLE `commentaire`
  ADD CONSTRAINT `FK_67F068BCA76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_67F068BCDAE07E97` FOREIGN KEY (`blog_id`) REFERENCES `blog` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `commentaire_reaction`
--
ALTER TABLE `commentaire_reaction`
  ADD CONSTRAINT `FK_56C6CF2BA76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_56C6CF2BBA9CD190` FOREIGN KEY (`commentaire_id`) REFERENCES `commentaire` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `demande_produit`
--
ALTER TABLE `demande_produit`
  ADD CONSTRAINT `FK_1952578895A6EE59` FOREIGN KEY (`demandeur_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `FK_19525788C69DE5E5` FOREIGN KEY (`validated_by_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `FK_19525788F347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`) ON DELETE SET NULL;

--
-- Contraintes pour la table `disponibilite`
--
ALTER TABLE `disponibilite`
  ADD CONSTRAINT `FK_2CBACE2F4F31A84` FOREIGN KEY (`medecin_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `evenement`
--
ALTER TABLE `evenement`
  ADD CONSTRAINT `FK_B26681E476556AF` FOREIGN KEY (`thematique_id`) REFERENCES `thematique` (`id`) ON DELETE SET NULL;

--
-- Contraintes pour la table `favoris`
--
ALTER TABLE `favoris`
  ADD CONSTRAINT `FK_8933C432A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_8933C432F347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `favoris_article`
--
ALTER TABLE `favoris_article`
  ADD CONSTRAINT `FK_B7D0A763A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_B7D0A763DAE07E97` FOREIGN KEY (`blog_id`) REFERENCES `blog` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `favoris_module`
--
ALTER TABLE `favoris_module`
  ADD CONSTRAINT `FK_36421295A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_36421295AFC2B591` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `inscrit_events`
--
ALTER TABLE `inscrit_events`
  ADD CONSTRAINT `FK_8079EEFAA76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_8079EEFAFD02F13` FOREIGN KEY (`evenement_id`) REFERENCES `evenement` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `ligne_commande`
--
ALTER TABLE `ligne_commande`
  ADD CONSTRAINT `FK_3170B74B82EA2E54` FOREIGN KEY (`commande_id`) REFERENCES `commande` (`id`),
  ADD CONSTRAINT `FK_3170B74BF347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`);

--
-- Contraintes pour la table `medecin_rating`
--
ALTER TABLE `medecin_rating`
  ADD CONSTRAINT `FK_4030A62F4F31A84` FOREIGN KEY (`medecin_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_4030A62FA76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `message_evenement`
--
ALTER TABLE `message_evenement`
  ADD CONSTRAINT `FK_619F6E28A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_619F6E28FD02F13` FOREIGN KEY (`evenement_id`) REFERENCES `evenement` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `module`
--
ALTER TABLE `module`
  ADD CONSTRAINT `FK_C242628642B8210` FOREIGN KEY (`admin_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;

--
-- Contraintes pour la table `module_bookmark`
--
ALTER TABLE `module_bookmark`
  ADD CONSTRAINT `FK_BEA241E3A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_BEA241E3AFC2B591` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `module_completion`
--
ALTER TABLE `module_completion`
  ADD CONSTRAINT `FK_AD331CE3A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_AD331CE3AFC2B591` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_AD331CE3F8FE9957` FOREIGN KEY (`quiz_attempt_id`) REFERENCES `module_quiz_attempt` (`id`) ON DELETE SET NULL;

--
-- Contraintes pour la table `module_quiz`
--
ALTER TABLE `module_quiz`
  ADD CONSTRAINT `FK_1E2EBF9EAFC2B591` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `module_quiz_attempt`
--
ALTER TABLE `module_quiz_attempt`
  ADD CONSTRAINT `FK_62E53734853CD175` FOREIGN KEY (`quiz_id`) REFERENCES `module_quiz` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_62E53734A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_62E53734AFC2B591` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `note`
--
ALTER TABLE `note`
  ADD CONSTRAINT `FK_CFBDFA144F31A84` FOREIGN KEY (`medecin_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_CFBDFA146B899279` FOREIGN KEY (`patient_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `notification`
--
ALTER TABLE `notification`
  ADD CONSTRAINT `FK_BF5476CA82EA2E54` FOREIGN KEY (`commande_id`) REFERENCES `commande` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_BF5476CA91EF7EAA` FOREIGN KEY (`rendez_vous_id`) REFERENCES `rendez_vous` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_BF5476CAA4F84F6E` FOREIGN KEY (`destinataire_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_BF5476CAF347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_BF5476CAF9F8745A` FOREIGN KEY (`demande_produit_id`) REFERENCES `demande_produit` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `order`
--
ALTER TABLE `order`
  ADD CONSTRAINT `FK_F5299398A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);

--
-- Contraintes pour la table `order_item`
--
ALTER TABLE `order_item`
  ADD CONSTRAINT `FK_52EA1F098D9F6D38` FOREIGN KEY (`order_id`) REFERENCES `order` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_52EA1F09F347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`);

--
-- Contraintes pour la table `produit`
--
ALTER TABLE `produit`
  ADD CONSTRAINT `FK_29A5EC27A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `FK_29A5EC27DCD6110` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `produit_historique`
--
ALTER TABLE `produit_historique`
  ADD CONSTRAINT `FK_4487ECE1A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_4487ECE1F347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `produit_image`
--
ALTER TABLE `produit_image`
  ADD CONSTRAINT `FK_F5A163CBF347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `rendez_vous`
--
ALTER TABLE `rendez_vous`
  ADD CONSTRAINT `FK_65E8AA0A2B9D6493` FOREIGN KEY (`disponibilite_id`) REFERENCES `disponibilite` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `FK_65E8AA0A4F31A84` FOREIGN KEY (`medecin_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_65E8AA0A6B899279` FOREIGN KEY (`patient_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;

--
-- Contraintes pour la table `ressource`
--
ALTER TABLE `ressource`
  ADD CONSTRAINT `FK_939F4544AFC2B591` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `user_highlight`
--
ALTER TABLE `user_highlight`
  ADD CONSTRAINT `FK_C97855D0A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `user_history`
--
ALTER TABLE `user_history`
  ADD CONSTRAINT `FK_7FB76E41A76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

--
-- Contraintes pour la table `user_preference`
--
ALTER TABLE `user_preference`
  ADD CONSTRAINT `FK_FA0E76BFA76ED395` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
