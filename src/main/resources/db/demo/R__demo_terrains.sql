-- Donnees de demonstration (profil dev uniquement, jamais en prod) :
-- 3 sports et 5 terrains fictifs, avec heures pleines le soir et le week-end.
-- Photos et video de demonstration : servies par les fronts (/images/demo, /videos/demo).
-- Rejouee seulement si ce fichier change ; INSERT IGNORE : ne remplace
-- jamais ce que le gerant a modifie.
INSERT IGNORE INTO `sports` (`id`, `nom`, `ordre`, `actif`) VALUES
  (1, 'Football 5', 1, b'1'),
  (2, 'Football 7', 2, b'1'),
  (3, 'Padel', 3, b'1');

INSERT IGNORE INTO `terrains` (`id`, `sport_id`, `nom`, `description`, `localite`, `surface`, `capacite`, `equipements`,
  `duree_creneau_minutes`, `tarif_heure`, `actif`, `ordre`, `cree_le`) VALUES
  (1, 1, 'Terrain A', 'Terrain couvert, idéal pour les matchs du soir. Gazon récent, filets neufs et éclairage LED : on y voit comme en plein jour.',
   'Liberté 6, Dakar', 'Gazon synthétique', 10, 'Éclairage,Vestiaires,Douches,Parking', 60, 15000, b'1', 1, NOW(6)),
  (2, 1, 'Terrain B', 'Terrain extérieur, au calme, à l''abri du vent. Parfait pour les matchs entre collègues.',
   'Liberté 6, Dakar', 'Gazon synthétique', 10, 'Éclairage,Vestiaires,Parking', 60, 12000, b'1', 2, NOW(6)),
  (3, 2, 'Grand terrain', 'Terrain à 7 avec tribune de 80 places : idéal pour les tournois et les matchs de quartier.',
   'Mermoz, Dakar', 'Gazon synthétique', 14, 'Éclairage,Vestiaires,Douches,Tribune,Buvette', 90, 25000, b'1', 1, NOW(6)),
  (4, 3, 'Padel 1', 'Court vitré aux dimensions officielles.', 'Liberté 6, Dakar', 'Gazon synthétique', 4,
   'Éclairage,Location de raquettes', 60, 10000, b'1', 1, NOW(6)),
  (5, 3, 'Padel 2', 'Court vitré aux dimensions officielles.', 'Liberté 6, Dakar', 'Gazon synthétique', 4,
   'Éclairage,Location de raquettes', 60, 10000, b'1', 2, NOW(6));

-- Positions GPS fictives (Dakar), pour l'itineraire.
UPDATE `terrains` SET `latitude` = 14.725600, `longitude` = -17.456900 WHERE `id` IN (1, 2, 4, 5) AND `latitude` IS NULL;
UPDATE `terrains` SET `latitude` = 14.706800, `longitude` = -17.473200 WHERE `id` = 3 AND `latitude` IS NULL;

-- Base de demo creee avant la V2 : localite renseignee si encore vide.
UPDATE `terrains` SET `localite` = CASE WHEN `id` = 3 THEN 'Mermoz, Dakar' ELSE 'Liberté 6, Dakar' END
  WHERE `id` BETWEEN 1 AND 5 AND `localite` IS NULL;

INSERT IGNORE INTO `terrain_photos` (`terrain_id`, `ordre`, `url`) VALUES
  (1, 0, '/images/demo/foot-coin.jpg'),
  (1, 1, '/images/demo/foot-gazon.jpg'),
  (1, 2, '/images/demo/foot-ballon.jpg'),
  (2, 0, '/images/demo/foot-gazon.jpg'),
  (2, 1, '/images/demo/foot-coin.jpg'),
  (3, 0, '/images/demo/foot-ballon.jpg'),
  (3, 1, '/images/demo/foot-gazon.jpg'),
  (3, 2, '/images/demo/foot-coin.jpg'),
  (3, 3, '/videos/demo/presentation.mp4');

-- Heures pleines : tous les jours de 18:00 a 01:00 ; week-end toute la journee.
-- Jours : bit 0 = lundi ... bit 6 = dimanche (127 = tous, 96 = samedi + dimanche).
INSERT IGNORE INTO `plages_tarifaires` (`id`, `terrain_id`, `libelle`, `jours`, `debut`, `fin`, `tarif_heure`) VALUES
  (1, 1, 'Soirée', 127, '18:00', '01:00', 20000),
  (2, 2, 'Soirée', 127, '18:00', '01:00', 16000),
  (3, 3, 'Soirée', 127, '18:00', '01:00', 32000),
  (4, 4, 'Week-end', 96, '08:00', '18:00', 12000),
  (5, 4, 'Soirée', 127, '18:00', '01:00', 14000),
  (6, 5, 'Week-end', 96, '08:00', '18:00', 12000),
  (7, 5, 'Soirée', 127, '18:00', '01:00', 14000);
