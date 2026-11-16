-- Annonces des terrains : galerie de photos (la premiere = couverture)
-- et localite (quartier, site) pour la recherche de l'App Client.
CREATE TABLE `terrain_photos` (
  `terrain_id` bigint NOT NULL,
  `ordre` int NOT NULL,
  `url` varchar(500) NOT NULL,
  PRIMARY KEY (`terrain_id`, `ordre`),
  CONSTRAINT `fk_terrain_photos_terrain` FOREIGN KEY (`terrain_id`) REFERENCES `terrains` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `terrain_photos` (`terrain_id`, `ordre`, `url`)
  SELECT `id`, 0, `photo_url` FROM `terrains` WHERE `photo_url` IS NOT NULL;

ALTER TABLE `terrains`
  DROP COLUMN `photo_url`,
  ADD COLUMN `localite` varchar(120) DEFAULT NULL AFTER `description`;
