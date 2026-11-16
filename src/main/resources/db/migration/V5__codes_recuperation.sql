-- Code de recuperation (PIN oublie) envoye par SMS : un par numero, seule
-- son empreinte est gardee, 10 minutes de validite, essais et envois comptes.
CREATE TABLE `codes_recuperation` (
  `telephone` varchar(20) NOT NULL,
  `empreinte` varchar(100) NOT NULL,
  `expire_le` datetime(6) NOT NULL,
  `essais` int NOT NULL DEFAULT 0,
  `envois` int NOT NULL DEFAULT 0,
  `premier_envoi_le` datetime(6) NOT NULL,
  PRIMARY KEY (`telephone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
