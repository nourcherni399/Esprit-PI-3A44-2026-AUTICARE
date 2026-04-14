package org.example.models;

import java.time.LocalDateTime;

/**
 * En-tête commande client (table {@code commande}), pour reçu PDF / « Mes commandes ».
 */
public record CustomerOrder(
    int id,
    String nom,
    String email,
    String telephone,
    String adresse,
    String codePostal,
    String ville,
    double total,
    String statut,
    String modePayment,
    LocalDateTime dateCreation,
    String stripePaymentIntent,
    int userId
) {
}
