-- Code PIN du client (retrouver ses reservations depuis un autre telephone),
-- date de paiement (recu PDF), position GPS des terrains (itineraire).
ALTER TABLE `reservations`
  ADD COLUMN `pin_empreinte` varchar(100) DEFAULT NULL,
  ADD COLUMN `payee_le` datetime(6) DEFAULT NULL;

UPDATE `reservations` SET `payee_le` = `modifie_le` WHERE `statut` IN ('CONFIRMEE', 'HONOREE');

ALTER TABLE `terrains`
  ADD COLUMN `latitude` decimal(9,6) DEFAULT NULL,
  ADD COLUMN `longitude` decimal(9,6) DEFAULT NULL,
  ADD CONSTRAINT `ck_terrains_gps` CHECK ((`latitude` IS NULL AND `longitude` IS NULL)
    OR (`latitude` BETWEEN -90 AND 90 AND `longitude` BETWEEN -180 AND 180));

-- Code de recuperation (PIN oublie) envoye par SMS : un par numero, seule
-- son empreinte est gardee, 10 minutes de validite, essais et envois comptes.
CREATE TABLE `codes_recuperation` (
  `telephone` varchar(20) NOT NULL,
  `empreinte` varchar(100) NOT NULL,
  `expire_le` datetime(6) NOT NULL,
  `essais` int NOT NULL DEFAULT 0,
  `envois` int NOT NULL DEFAULT 0,
  `premier_envoi_le` datetime(6) NOT NULL,
  PRIMARY KEY (`telephone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
