-- Annonce de demonstration (profil dev uniquement) : une video puis deux
-- photos, publiee. Medias servis par les fronts (/videos/demo, /images/demo).
INSERT IGNORE INTO `annonces` (`id`, `titre`, `statut`, `publiee_le`, `version`, `cree_le`, `modifie_le`) VALUES
  (1, 'Bienvenue', 'ACTIF', NOW(6), 0, NOW(6), NOW(6));

INSERT IGNORE INTO `annonce_slides` (`id`, `annonce_id`, `position`, `titre`, `message`, `media_url`, `media_type`) VALUES
  (1, 1, 0, 'Réservez en une minute', 'Choisissez votre terrain et votre créneau, payez par Wave ou Orange Money : votre ticket QR arrive tout de suite.',
   '/videos/demo/presentation.mp4', 'VIDEO'),
  (2, 1, 1, 'Soirées à -20 %', 'Du lundi au jeudi après 22 h, les créneaux du Terrain B sont à prix doux. Venez avec votre équipe !',
   '/images/demo/foot-gazon.jpg', 'IMAGE'),
  (3, 1, 2, 'Tournoi du mois', 'Inscrivez votre équipe de 7 au tournoi du dernier samedi du mois sur le Grand terrain.',
   '/images/demo/foot-ballon.jpg', 'IMAGE');
