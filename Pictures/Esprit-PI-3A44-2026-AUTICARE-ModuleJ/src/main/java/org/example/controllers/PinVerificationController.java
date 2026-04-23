package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import org.example.MainApp;
import org.example.services.PasswordRecoveryEmailService;
import org.example.services.UserService;
import org.example.utils.PasswordRecoveryState;

import java.io.IOException;

public class PinVerificationController extends AuthPageController {

    @FXML
    private TextField emailField;
    @FXML
    private TextField pinCodeField;
    private final PasswordRecoveryEmailService emailService = new PasswordRecoveryEmailService();
    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        initializeAuth();
        if (emailField != null) {
            emailField.setText(PasswordRecoveryState.getEmailOrUsername());
            emailField.setEditable(false);
        }
    }

    @FXML
    public void onVerifyPin() {
        String pin = pinCodeField != null && pinCodeField.getText() != null
                ? pinCodeField.getText().trim().replaceAll("\\s+", "")
                : "";
        if (pin.length() != 6 || !pin.chars().allMatch(Character::isDigit)) {
            showAlert(Alert.AlertType.WARNING, "PIN", "Entrez un code à 6 chiffres.");
            return;
        }
        if (PasswordRecoveryState.getEmailOrUsername().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Session expirée",
                    "Recommencez la procédure de mot de passe oublié.");
            return;
        }
        try {
            var uid = userService.verifyResetPin(PasswordRecoveryState.getEmailOrUsername(), pin);
            if (uid.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "PIN", "Code invalide ou expire.");
                return;
            }
            PasswordRecoveryState.setVerifiedUserId(uid.get());
            MainApp.showSetNewPassword();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onResendPin() {
        String email = PasswordRecoveryState.getEmailOrUsername();
        if (email.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Session expirée",
                    "Recommencez la procedure depuis 'Forgot password'.");
            return;
        }
        long left = PasswordRecoveryState.getRemainingResendCooldownMillis();
        if (left > 0) {
            long s = (long) Math.ceil(left / 1000.0);
            showAlert(Alert.AlertType.INFORMATION, "Patientez",
                    "Veuillez attendre " + s + " seconde(s) avant de renvoyer le code.");
            return;
        }
        try {
            var issue = userService.createAndStoreResetPinForEmail(email);
            if (issue.isPresent()) {
                emailService.sendPinEmail(issue.get().email(), issue.get().pinCode());
                PasswordRecoveryState.markPinSentNow();
            }
            showAlert(Alert.AlertType.INFORMATION, "Code renvoyé",
                    "Si le compte existe, un nouveau code PIN a ete envoye.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Envoi e-mail",
                    "Impossible de renvoyer le code PIN.");
        }
    }

    @FXML
    public void onBackToReset() {
        try {
            MainApp.showResetPassword();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }
}
