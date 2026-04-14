package org.example.models;

import java.time.LocalDateTime;

/**
 * Ligne {@code demande_produit} (aligné Symfony / table {@code pidb}).
 */
public record DemandeProduit(
    int id,
    String demandeClient,
    String nom,
    String description,
    String categorie,
    double prixEstime,
    Double budgetClient,
    String caracteristiques,
    String donneesExternesJson,
    String statut,
    LocalDateTime createdAt,
    LocalDateTime validatedAt,
    Integer demandeurId,
    Integer validatedById,
    Integer produitId
) {
}
