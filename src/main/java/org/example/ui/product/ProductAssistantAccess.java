package org.example.ui.product;

import org.example.models.Role;
import org.example.models.User;

/**
 * Règle d’éligibilité à l’assistant produit (Groq / Unsplash) : comptes « famille » et patient,
 * pas l’équipe ({@link Role#ADMIN}, {@link Role#MEDECIN}).
 */
public final class ProductAssistantAccess {

    private ProductAssistantAccess() {
    }

    /**
     * @return {@code true} si l’utilisateur connecté peut voir l’assistant (patient, parent, proche / USER, etc.)
     */
    public static boolean isEligible(User u) {
        if (u == null) {
            return false;
        }
        Role r = u.getRole();
        if (r == null) {
            return true;
        }
        return r != Role.ADMIN && r != Role.MEDECIN;
    }
}
