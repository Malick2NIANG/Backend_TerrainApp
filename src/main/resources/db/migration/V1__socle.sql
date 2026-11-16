-- V1 : socle TerrainApp (une installation = un complexe sportif).
-- Heures en UTC (Dakar = UTC+0), montants en F CFA entiers.

-- ---------------------------------------------------------------- personnel
CREATE TABLE `staff_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL,
  `username` varchar(255) NOT NULL,
  `must_change_password` bit(1) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `cni` varchar(255) DEFAULT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_staff_users_username` UNIQUE (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_evenements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `horodatage` datetime(6) NOT NULL,
  `acteur` varchar(255) NOT NULL,
  `acteur_nom` varchar(130) NOT NULL,
  `role` varchar(20) DEFAULT NULL,
  `domaine` varchar(20) NOT NULL,
  `action` varchar(40) NOT NULL,
  `cible_id` varchar(100) DEFAULT NULL,
  `message` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_audit_horodatage` (`horodatage`),
  KEY `ix_audit_role_horodatage` (`role`, `horodatage`),
  KEY `ix_audit_domaine_horodatage` (`domaine`, `horodatage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- complexe
-- Horaires d'ouverture du complexe, par jour (1 = lundi ... 7 = dimanche).
-- fermeture <= ouverture : le complexe ferme apres minuit (ex. 08:00 -> 01:00).
CREATE TABLE `horaires_ouverture` (
  `jour` tinyint NOT NULL,
  `ouvert` bit(1) NOT NULL DEFAULT b'1',
  `ouverture` time NOT NULL,
  `fermeture` time NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`jour`),
  CONSTRAINT `ck_horaires_jour` CHECK (`jour` BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `horaires_ouverture` (`jour`, `ouvert`, `ouverture`, `fermeture`) VALUES
  (1, b'1', '08:00', '23:00'), (2, b'1', '08:00', '23:00'), (3, b'1', '08:00', '23:00'),
  (4, b'1', '08:00', '23:00'), (5, b'1', '08:00', '00:00'), (6, b'1', '08:00', '00:00'),
  (7, b'1', '08:00', '23:00');

-- ---------------------------------------------------------------- terrains
CREATE TABLE `sports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nom` varchar(60) NOT NULL,
  `ordre` int NOT NULL DEFAULT 0,
  `actif` bit(1) NOT NULL DEFAULT b'1',
  `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_sports_nom` UNIQUE (`nom`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `terrains` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sport_id` bigint NOT NULL,
  `nom` varchar(80) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `surface` varchar(60) DEFAULT NULL,
  `capacite` int NOT NULL,
  `equipements` varchar(500) DEFAULT NULL,
  `photo_url` varchar(500) DEFAULT NULL,
  `duree_creneau_minutes` int NOT NULL DEFAULT 60,
  `tarif_heure` bigint NOT NULL,
  `actif` bit(1) NOT NULL DEFAULT b'1',
  `ordre` int NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_terrains_sport` (`sport_id`),
  CONSTRAINT `fk_terrains_sport` FOREIGN KEY (`sport_id`) REFERENCES `sports` (`id`),
  CONSTRAINT `ck_terrains_duree` CHECK (`duree_creneau_minutes` BETWEEN 30 AND 240),
  CONSTRAINT `ck_terrains_tarif` CHECK (`tarif_heure` >= 0),
  CONSTRAINT `ck_terrains_capacite` CHECK (`capacite` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tarif horaire particulier sur une plage (heures pleines, week-end...).
-- jours : masque (bit 0 = lundi ... bit 6 = dimanche). Sans plage : tarif_heure du terrain.
CREATE TABLE `plages_tarifaires` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `terrain_id` bigint NOT NULL,
  `libelle` varchar(60) NOT NULL,
  `jours` int NOT NULL,
  `debut` time NOT NULL,
  `fin` time NOT NULL,
  `tarif_heure` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_plages_terrain` (`terrain_id`),
  CONSTRAINT `fk_plages_terrain` FOREIGN KEY (`terrain_id`) REFERENCES `terrains` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_plages_jours` CHECK (`jours` BETWEEN 1 AND 127),
  CONSTRAINT `ck_plages_tarif` CHECK (`tarif_heure` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Creneaux fermes (entretien, evenement). terrain_id NULL = tout le complexe.
CREATE TABLE `blocages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `terrain_id` bigint DEFAULT NULL,
  `debut` datetime(6) NOT NULL,
  `fin` datetime(6) NOT NULL,
  `motif` varchar(200) DEFAULT NULL,
  `cree_par` varchar(255) NOT NULL,
  `cree_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_blocages_periode` (`debut`, `fin`),
  CONSTRAINT `fk_blocages_terrain` FOREIGN KEY (`terrain_id`) REFERENCES `terrains` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_blocages_periode` CHECK (`fin` > `debut`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- reservations
CREATE TABLE `reservations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reference` varchar(12) NOT NULL,
  `terrain_id` bigint NOT NULL,
  `debut` datetime(6) NOT NULL,
  `fin` datetime(6) NOT NULL,
  `montant` bigint NOT NULL,
  `statut` varchar(20) NOT NULL,
  `canal` varchar(10) NOT NULL,
  `moyen_paiement` varchar(20) NOT NULL,
  `client_nom` varchar(80) NOT NULL,
  `client_telephone` varchar(20) NOT NULL,
  -- Empreinte SHA-256 du jeton de suivi remis au client (jamais le jeton lui-meme).
  `jeton_suivi_empreinte` char(64) DEFAULT NULL,
  -- Code du QR du ticket, aleatoire, unique.
  `code_ticket` varchar(40) NOT NULL,
  `scanne_le` datetime(6) DEFAULT NULL,
  `scanne_par` varchar(255) DEFAULT NULL,
  `annulee_le` datetime(6) DEFAULT NULL,
  `annulee_par` varchar(255) DEFAULT NULL,
  `cree_par` varchar(255) DEFAULT NULL,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_reservations_reference` UNIQUE (`reference`),
  CONSTRAINT `uk_reservations_code_ticket` UNIQUE (`code_ticket`),
  KEY `ix_reservations_terrain_debut` (`terrain_id`, `debut`),
  KEY `ix_reservations_debut` (`debut`),
  KEY `ix_reservations_statut_cree_le` (`statut`, `cree_le`),
  KEY `ix_reservations_telephone` (`client_telephone`),
  CONSTRAINT `fk_reservations_terrain` FOREIGN KEY (`terrain_id`) REFERENCES `terrains` (`id`),
  CONSTRAINT `ck_reservations_periode` CHECK (`fin` > `debut`),
  CONSTRAINT `ck_reservations_montant` CHECK (`montant` >= 0),
  CONSTRAINT `ck_reservations_statut` CHECK (`statut` IN ('EN_ATTENTE_PAIEMENT','CONFIRMEE','HONOREE','ANNULEE','EXPIREE')),
  CONSTRAINT `ck_reservations_canal` CHECK (`canal` IN ('APP','COMPTOIR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- paiements
CREATE TABLE `paiements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reservation_id` bigint NOT NULL,
  `prestataire` varchar(30) NOT NULL,
  `reference` varchar(100) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `montant` bigint NOT NULL,
  `simule` bit(1) NOT NULL DEFAULT b'0',
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paiements_reference` (`prestataire`, `reference`),
  KEY `ix_paiements_reservation` (`reservation_id`),
  CONSTRAINT `fk_paiements_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservations` (`id`),
  CONSTRAINT `ck_paiements_statut` CHECK (`statut` IN ('en_attente', 'paye', 'echoue'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications_paiement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prestataire` varchar(30) NOT NULL,
  `evenement_id` varchar(100) NOT NULL,
  `reference` varchar(100) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `montant` bigint NOT NULL,
  `recue_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notifications_evenement` (`prestataire`, `evenement_id`),
  CONSTRAINT `ck_notifications_statut` CHECK (`statut` IN ('en_attente', 'paye', 'echoue'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- technique
CREATE TABLE `cles_idempotence` (
  `cle` varchar(100) NOT NULL,
  `empreinte_requete` char(64) NOT NULL,
  `reservation_id` bigint DEFAULT NULL,
  `reponse` mediumtext DEFAULT NULL,
  `cree_le` datetime(6) NOT NULL,
  PRIMARY KEY (`cle`),
  KEY `ix_cles_idempotence_cree_le` (`cree_le`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Outbox : evenement ecrit dans la transaction de la reservation, publie
-- ensuite vers le planning temps reel du back-office (SSE).
CREATE TABLE `evenements_sortants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(40) NOT NULL,
  `reservation_id` bigint NOT NULL,
  `cree_le` datetime(6) NOT NULL,
  `publie_le` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_evenements_a_publier` (`publie_le`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
