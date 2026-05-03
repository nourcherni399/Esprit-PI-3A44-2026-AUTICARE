package org.example.services;

/**
 * Résultat de {@link FaceIdClientService#identifyBestAmongUsers}; {@code userId} &lt; 0 si aucun candidat valide.
 */
public record FaceIdentifyMatch(int userId, double similarity) {
    public static final int NO_MATCH = -1;

    public boolean hasMatch() {
        return userId >= 0;
    }
}
