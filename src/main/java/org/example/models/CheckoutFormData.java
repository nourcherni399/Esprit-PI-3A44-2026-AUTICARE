package org.example.models;

/**
 * Données du formulaire checkout (équivalent CommandeType Symfony).
 */
public record CheckoutFormData(
    String nom,
    String email,
    String telephone,
    String adresse,
    String codePostal,
    String ville,
    String modePayment,
    String stripePaymentIntent
) {
}
