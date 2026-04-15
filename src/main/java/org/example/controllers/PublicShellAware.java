package org.example.controllers;

/**
 * Pages chargées dans {@link PublicShellController} reçoivent la référence à la coque pour la navigation interne.
 */
public interface PublicShellAware {
    void setPublicShell(PublicShellController shell);
}
