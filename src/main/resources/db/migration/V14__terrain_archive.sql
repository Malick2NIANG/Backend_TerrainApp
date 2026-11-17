-- Terrain archive (retire du complexe, hors planning), restaurable en brouillon.
ALTER TABLE `terrains` ADD COLUMN `archive` bit(1) NOT NULL DEFAULT b'0';
