-- Annonces du gerant : chaines de slides (image OU video) presentees en
-- popup a l'ouverture de l'App Client, comme sur SenYummies.
CREATE TABLE `annonces` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `titre` varchar(80) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `publiee_le` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_annonces_statut` CHECK (`statut` IN ('BROUILLON', 'ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `annonce_slides` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `annonce_id` bigint NOT NULL,
  `position` int NOT NULL,
  `titre` varchar(80) NOT NULL,
  `message` varchar(300) DEFAULT NULL,
  `media_url` varchar(500) DEFAULT NULL,
  `media_type` varchar(5) NOT NULL DEFAULT 'IMAGE',
  PRIMARY KEY (`id`),
  KEY `ix_annonce_slides_annonce` (`annonce_id`, `position`),
  CONSTRAINT `fk_annonce_slides_annonce` FOREIGN KEY (`annonce_id`) REFERENCES `annonces` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_annonce_slides_media` CHECK (`media_type` IN ('IMAGE', 'VIDEO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
