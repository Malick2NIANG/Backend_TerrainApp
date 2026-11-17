-- Plus de code PIN : on retrouve ses reservations depuis un autre telephone
-- avec un code a usage unique recu par SMS (table codes_recuperation).
ALTER TABLE `reservations` DROP COLUMN `pin_empreinte`;
