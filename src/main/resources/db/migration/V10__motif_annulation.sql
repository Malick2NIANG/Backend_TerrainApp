-- Motif d'une annulation par le personnel (repris dans le SMS au client et le journal).
ALTER TABLE `reservations` ADD COLUMN `motif_annulation` varchar(200) DEFAULT NULL;
