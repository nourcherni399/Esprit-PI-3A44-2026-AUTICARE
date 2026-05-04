package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import org.example.MainApp;
import org.example.services.PasswordRecoveryEmailService;
import org.example.services.UserService;
import org.example.utils.PasswordRecoveryState;

import java.io.IOException;
public class ForgotPasswordController extends AuthPageController {

    @FXML
    private TextField emailField;
    private final UserService userService = new UserService();
    private final PasswordRecoveryEmailService emailService = new PasswordRecoveryEmailService();

    @FXML
    public void initialize() {
        initializeAuth();
    }

    @FXML
    public void onResetPassword() {
        String email = emailField != null && emailField.getText() != null
                ? emailField.getText().trim()
                : "";
        if (email.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champ requis", "Indiquez votre email ou nom d'utilisateur.");
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            showAlert(Alert.AlertType.WARNING, "Email", "Indiquez une adresse email valide.");
            return;
        }
        PasswordRecoveryState.setEmailOrUsername(email);
        PasswordRecoveryState.setVerifiedUserId(null);
        Exception sendError = null;
        try {
            var issue = userService.createAndStoreResetPinForEmail(email);
            if (issue.isPresent()) {
                emailService.sendPinEmail(issue.get().email(), issue.get().pinCode());
            }
        } catch (Exception e) {
            sendError = e;
        }
        PasswordRecoveryState.markPinSentNow();
        if (sendError != null) {
            showAlert(Alert.AlertType.ERROR, "Envoi e-mail",
                    "Impossible d'envoyer l'e-mail PIN: " + sendError.getClass().getSimpleName()
                            + (sendError.getMessage() != null ? " - " + sendError.getMessage() : ""));
            return;
        }
        showAlert(Alert.AlertType.INFORMATION, "Si le compte existe",
                "Si un compte correspond a cet email, un code PIN vient d'etre envoye.");
        try {
            MainApp.showResetPassword();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onBackToLogin() {
        try {
            MainApp.showLogin();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }
}
