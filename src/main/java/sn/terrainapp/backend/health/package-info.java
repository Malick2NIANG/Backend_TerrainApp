/**
 * Sonde de sante minimale (GET /health, sans authentification, sans acces
 * base de donnees) : permet aux frontends (App Client, back-office)
 * de detecter une coupure reseau sans solliciter un endpoint metier a
 * chaque verification (voir NetworkStatus.jsx cote back-office).
 */
package sn.terrainapp.backend.health;
