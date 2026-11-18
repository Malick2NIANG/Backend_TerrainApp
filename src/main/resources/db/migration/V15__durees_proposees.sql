-- Durees de location proposees par terrain (ex. "60,90,120").
-- NULL : seule la duree de creneau existante (comportement inchange).
ALTER TABLE `terrains` ADD COLUMN `durees_proposees` varchar(60) NULL AFTER `duree_creneau_minutes`;
