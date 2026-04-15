package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import org.example.MainApp;

import java.io.IOException;

public class ResetPasswordController extends AuthPageController {

    @FXML
    public void initialize() {
        initializeAuth();
    }

    @FXML
    public void onEnterPin() {
        try {
            MainApp.showPin();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onBackToForgot() {
        try {
            MainApp.showForgotPassword();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }
}
