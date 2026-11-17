-- Abonnements : plusieurs jours par semaine (ex. mardi et jeudi) ou dates
-- choisies une a une (frequence PERSONNALISEE).
ALTER TABLE `abonnements`
  ADD COLUMN `jours` varchar(20) DEFAULT NULL,
  DROP CHECK `ck_abonnements_frequence`,
  ADD CONSTRAINT `ck_abonnements_frequence` CHECK (`frequence` IN ('HEBDOMADAIRE', 'MENSUELLE', 'PERSONNALISEE'));
