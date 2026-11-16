-- Donnees de demonstration (profil dev uniquement, jamais en prod) :
-- 3 sports et 5 terrains fictifs, avec heures pleines le soir et le week-end.
-- Rejouee seulement si ce fichier change ; INSERT IGNORE : ne remplace
-- jamais ce que le gerant a modifie.
INSERT IGNORE INTO `sports` (`id`, `nom`, `ordre`, `actif`) VALUES
  (1, 'Football 5', 1, b'1'),
  (2, 'Football 7', 2, b'1'),
  (3, 'Padel', 3, b'1');

INSERT IGNORE INTO `terrains` (`id`, `sport_id`, `nom`, `description`, `surface`, `capacite`, `equipements`,
  `photo_url`, `duree_creneau_minutes`, `tarif_heure`, `actif`, `ordre`, `cree_le`) VALUES
  (1, 1, 'Terrain A', 'Terrain couvert, idéal pour les matchs du soir.', 'Gazon synthétique', 10,
   'Éclairage,Vestiaires,Douches', NULL, 60, 15000, b'1', 1, NOW(6)),
  (2, 1, 'Terrain B', 'Terrain extérieur, au calme.', 'Gazon synthétique', 10,
   'Éclairage,Vestiaires', NULL, 60, 12000, b'1', 2, NOW(6)),
  (3, 2, 'Grand terrain', 'Terrain à 7 avec tribune.', 'Gazon synthétique', 14,
   'Éclairage,Vestiaires,Douches,Tribune', NULL, 90, 25000, b'1', 1, NOW(6)),
  (4, 3, 'Padel 1', 'Court vitré.', 'Gazon synthétique', 4, 'Éclairage,Location de raquettes', NULL, 60, 10000, b'1', 1, NOW(6)),
  (5, 3, 'Padel 2', 'Court vitré.', 'Gazon synthétique', 4, 'Éclairage,Location de raquettes', NULL, 60, 10000, b'1', 2, NOW(6));

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
