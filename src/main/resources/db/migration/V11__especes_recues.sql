-- Paiement en especes au comptoir : somme recue et monnaie rendue (recu de caisse).
ALTER TABLE `reservations`
  ADD COLUMN `especes_recues` bigint DEFAULT NULL,
  ADD COLUMN `monnaie_rendue` bigint DEFAULT NULL;
