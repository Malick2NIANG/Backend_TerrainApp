-- Donnees initiales chargees depuis le dossier du complexe (clients/<id>/donnees/) :
-- chaque element (sport, terrain, annonce, abonnement, compte, horaires) est
-- note ici sous sa cle du dossier. Rejouer le chargement ne cree donc jamais de
-- doublon, et ne remplace jamais ce que le gerant a modifie ensuite dans le Manager.
CREATE TABLE `elements_configures` (
  `type` varchar(20) NOT NULL,
  `cle` varchar(60) NOT NULL,
  `entite_id` bigint DEFAULT NULL,
  `charge_le` datetime(6) NOT NULL,
  PRIMARY KEY (`type`, `cle`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
