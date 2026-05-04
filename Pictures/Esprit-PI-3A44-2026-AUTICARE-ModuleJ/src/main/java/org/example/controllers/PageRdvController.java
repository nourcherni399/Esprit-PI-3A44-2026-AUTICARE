package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.UserService;
import org.example.utils.AppState;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Liste publique des professionnels (page Rendez-vous) : navigation vers l’étape « créneau ».
 */
public class PageRdvController implements PublicShellAware {

    private PublicShellController shell;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void onPrendreRdvLandoulsi() {
        startBooking("landoulsi", "Dr. Landoulsi Ameni");
    }

    @FXML
    private void onPrendreRdvEssefi() {
        startBooking("essefi", "Dr. Selma Essefi");
    }

    private void startBooking(String nameKey, String displayName) {
        if (AppState.getCurrentUser() == null) {
            Alert needLogin = new Alert(Alert.AlertType.WARNING);
            needLogin.setTitle("Connexion requise");
            needLogin.setHeaderText(null);
            needLogin.setContentText(
                    "Pour prendre un rendez-vous, vous devez d'abord vous connecter à votre compte.\n\n"
                            + "Utilisez « Connexion » ou « Inscription » depuis l'accueil, puis revenez sur cette page.");
            needLogin.showAndWait();
            return;
        }
        int doctorId = resolveMedecinId(nameKey);
        AppState.beginPublicRdvBooking(doctorId, displayName);
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv-booking");
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Navigation");
            a.setHeaderText(null);
            a.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            a.showAndWait();
        }
    }

    private static int resolveMedecinId(String nameKey) {
        String key = nameKey.toLowerCase(Locale.ROOT);
        try {
            UserService us = new UserService();
            for (User u : us.findByRole(Role.MEDECIN)) {
                String nom = u.getNom() != null ? u.getNom().toLowerCase(Locale.ROOT) : "";
                String prenom = u.getPrenom() != null ? u.getPrenom().toLowerCase(Locale.ROOT) : "";
                if (nom.contains(key) || prenom.contains(key) || (nom + " " + prenom).contains(key)) {
                    return u.getId();
                }
            }
        } catch (SQLException ignored) {
            // fallback ci-dessous
        }
        return -1;
    }
}
