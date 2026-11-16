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
