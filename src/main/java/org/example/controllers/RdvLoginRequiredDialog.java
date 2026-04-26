package org.example.controllers;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Boite de dialogue simple pour signaler qu'une connexion est necessaire
 * avant de reserver un rendez-vous.
 */
public final class RdvLoginRequiredDialog {

    private RdvLoginRequiredDialog() {
    }

    public static void show(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title != null && !title.isBlank() ? title : "Connexion requise");
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.getDialogPane().getStyleClass().add("rdv-login-required-dialog");
        alert.showAndWait();
    }
}

