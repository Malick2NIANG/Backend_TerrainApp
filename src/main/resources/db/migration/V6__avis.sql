-- Avis des joueurs sur un terrain : un par reservation jouee (note 1 a 5,
-- commentaire facultatif). Le gerant peut masquer un avis.
CREATE TABLE `avis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reservation_id` bigint NOT NULL,
  `terrain_id` bigint NOT NULL,
  `note` int NOT NULL,
  `commentaire` varchar(500) DEFAULT NULL,
  `auteur` varchar(60) NOT NULL,
  `visible` bit(1) NOT NULL DEFAULT b'1',
  `cree_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_avis_reservation` UNIQUE (`reservation_id`),
  KEY `ix_avis_terrain` (`terrain_id`, `visible`, `cree_le`),
  CONSTRAINT `fk_avis_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservations` (`id`),
  CONSTRAINT `fk_avis_terrain` FOREIGN KEY (`terrain_id`) REFERENCES `terrains` (`id`),
  CONSTRAINT `ck_avis_note` CHECK (`note` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
