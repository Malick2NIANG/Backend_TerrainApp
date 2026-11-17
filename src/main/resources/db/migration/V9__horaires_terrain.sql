-- Horaires propres a un terrain (ex. padel jusqu'a 22:00, foot jusqu'a
-- minuit). Aucune ligne pour un terrain : il suit les horaires du complexe.
CREATE TABLE `terrain_horaires` (
  `terrain_id` bigint NOT NULL,
  `jour` tinyint NOT NULL,
  `ouvert` bit(1) NOT NULL,
  `ouverture` time NOT NULL,
  `fermeture` time NOT NULL,
  PRIMARY KEY (`terrain_id`, `jour`),
  CONSTRAINT `fk_terrain_horaires_terrain` FOREIGN KEY (`terrain_id`) REFERENCES `terrains` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_terrain_horaires_jour` CHECK (`jour` BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
