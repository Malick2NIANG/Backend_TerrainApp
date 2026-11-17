-- Abonnements vendus au comptoir : creneau recurrent (chaque semaine ou
-- chaque mois). Chaque seance est une reservation rattachee a l'abonnement.
CREATE TABLE `abonnements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `terrain_id` bigint NOT NULL,
  `client_nom` varchar(80) NOT NULL,
  `client_telephone` varchar(20) NOT NULL,
  `frequence` varchar(15) NOT NULL,
  `premiere_seance` datetime(6) NOT NULL,
  `nombre_seances` int NOT NULL,
  `reglement` varchar(10) NOT NULL,
  `remise` bigint NOT NULL DEFAULT 0,
  `statut` varchar(10) NOT NULL,
  `resilie_le` datetime(6) DEFAULT NULL,
  `cree_par` varchar(255) DEFAULT NULL,
  `cree_le` datetime(6) NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `ix_abonnements_statut` (`statut`, `cree_le`),
  CONSTRAINT `fk_abonnements_terrain` FOREIGN KEY (`terrain_id`) REFERENCES `terrains` (`id`),
  CONSTRAINT `ck_abonnements_frequence` CHECK (`frequence` IN ('HEBDOMADAIRE', 'MENSUELLE')),
  CONSTRAINT `ck_abonnements_reglement` CHECK (`reglement` IN ('AVANCE', 'SEANCE')),
  CONSTRAINT `ck_abonnements_statut` CHECK (`statut` IN ('ACTIF', 'RESILIE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `reservations`
  ADD COLUMN `abonnement_id` bigint DEFAULT NULL,
  ADD KEY `ix_reservations_abonnement` (`abonnement_id`, `debut`),
  ADD CONSTRAINT `fk_reservations_abonnement` FOREIGN KEY (`abonnement_id`) REFERENCES `abonnements` (`id`);
