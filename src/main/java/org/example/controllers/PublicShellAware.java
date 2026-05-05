package org.example.controllers;

import org.example.models.AppLanguage;

/**
 * Pages chargées dans {@link PublicShellController} reçoivent la référence à la coque pour la navigation interne.
 */
public interface PublicShellAware {
    void setPublicShell(PublicShellController shell);

    /** Appelé juste après {@link #setPublicShell(PublicShellController)} (chargement FXML terminé). */
    default void onShellReady() {
    }

    /** Appelé quand la langue active de la coque publique change. */
    default void onShellLanguageChanged(AppLanguage language) {
    }
}
