-- Abonnement cree par erreur et supprime (rien de joue ni de paye) : masque de la liste.
ALTER TABLE `abonnements`
  DROP CHECK `ck_abonnements_statut`,
  ADD CONSTRAINT `ck_abonnements_statut` CHECK (`statut` IN ('ACTIF', 'RESILIE', 'SUPPRIME'));
