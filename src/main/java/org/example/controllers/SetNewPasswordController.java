package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import org.example.MainApp;
import org.example.services.UserService;
import org.example.utils.PasswordRecoveryState;

import java.io.IOException;
import java.util.regex.Pattern;

public class SetNewPasswordController extends AuthPageController {

    private static final Pattern HAS_UPPER = Pattern.compile("[A-ZÀÂÄÉÈÊËÏÎÔÙÛÜÇ]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label ruleLenLabel;
    @FXML
    private Label ruleUpperLabel;
    @FXML
    private Label ruleDigitLabel;

    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        initializeAuth();
        if (newPasswordField != null) {
            newPasswordField.textProperty().addListener((obs, o, n) -> updatePasswordRules(n));
            updatePasswordRules(newPasswordField.getText());
        }
    }

    @FXML
    public void onResetPassword() {
        Integer uid = PasswordRecoveryState.getVerifiedUserId();
        if (uid == null) {
            showAlert(Alert.AlertType.WARNING, "Session expiree",
                    "Veuillez recommencer la procedure de reinitialisation.");
            return;
        }
        String p1 = newPasswordField != null && newPasswordField.getText() != null
                ? newPasswordField.getText()
                : "";
        String p2 = confirmPasswordField != null && confirmPasswordField.getText() != null
                ? confirmPasswordField.getText()
                : "";
        if (p1.length() < 8) {
            showAlert(Alert.AlertType.WARNING, "Mot de passe",
                    "Le mot de passe doit contenir au moins 8 caracteres.");
            return;
        }
        if (!HAS_UPPER.matcher(p1).find()) {
            showAlert(Alert.AlertType.WARNING, "Mot de passe",
                    "Le mot de passe doit contenir au moins une lettre majuscule.");
            return;
        }
        if (!HAS_DIGIT.matcher(p1).find()) {
            showAlert(Alert.AlertType.WARNING, "Mot de passe",
                    "Le mot de passe doit contenir au moins un chiffre.");
            return;
        }
        if (!p1.equals(p2)) {
            showAlert(Alert.AlertType.WARNING, "Confirmation",
                    "La confirmation du mot de passe ne correspond pas.");
            return;
        }
        try {
            userService.updatePasswordAfterReset(uid, p1);
            PasswordRecoveryState.clear();
            showAlert(Alert.AlertType.INFORMATION, "Succes",
                    "Votre mot de passe a ete reinitialise avec succes.");
            MainApp.showLogin();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onBack() {
        try {
            MainApp.showPin();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void updatePasswordRules(String pwd) {
        String value = pwd != null ? pwd : "";
        setRuleState(ruleLenLabel, value.length() >= 8);
        setRuleState(ruleUpperLabel, HAS_UPPER.matcher(value).find());
        setRuleState(ruleDigitLabel, HAS_DIGIT.matcher(value).find());
    }

    private static void setRuleState(Label label, boolean ok) {
        if (label == null) {
            return;
        }
        label.getStyleClass().removeAll("pwd-rule-valid", "pwd-rule-invalid");
        label.getStyleClass().add(ok ? "pwd-rule-valid" : "pwd-rule-invalid");
    }
}

