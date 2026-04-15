package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.example.MainApp;
import org.example.models.User;
import org.example.services.UserService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

public class AdminUserDeleteController {

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private Text deleteNameText;
    @FXML
    private Text deleteEmailText;

    private final UserService userService = new UserService();
    private User targetUser;

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        User me = AppState.getCurrentUser();

        User ref = AppState.getAdminDeleteUser();
        if (ref == null) {
            navigateAfterCancel(false, -1);
            return;
        }
        targetUser = ref;

        if (me != null && targetUser.getId() == me.getId()) {
            alert(Alert.AlertType.WARNING, "Action impossible", "Vous ne pouvez pas supprimer votre propre compte administrateur.");
            boolean backToDetail = AppState.isAdminDeleteReturnToDetail();
            int uid = targetUser.getId();
            AppState.clearAdminDeleteContext();
            navigateAfterCancel(backToDetail, uid);
            return;
        }

        String prenom = targetUser.getPrenom() != null ? targetUser.getPrenom() : "";
        String nom = targetUser.getNom() != null ? targetUser.getNom() : "";
        String displayName = (prenom + " " + nom).trim();
        if (displayName.isEmpty()) {
            displayName = "—";
        }
        deleteNameText.setText(displayName);
        deleteEmailText.setText(targetUser.getEmail() != null ? targetUser.getEmail() : "—");
    }

    @FXML
    public void onConfirmDelete() {
        if (targetUser == null) {
            navigateAfterCancel(false, -1);
            return;
        }
        User me = AppState.getCurrentUser();
        if (me != null && targetUser.getId() == me.getId()) {
            alert(Alert.AlertType.WARNING, "Action impossible", "Vous ne pouvez pas supprimer votre propre compte.");
            return;
        }
        int id = targetUser.getId();
        try {
            userService.delete(id);
            AppState.clearAdminDeleteContext();
            if (AppState.getAdminDetailUser() != null && AppState.getAdminDetailUser().getId() == id) {
                AppState.clearAdminDetailUser();
            }
            MainApp.showAdminUsers();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        boolean toDetail = AppState.isAdminDeleteReturnToDetail();
        int id = targetUser != null ? targetUser.getId() : -1;
        navigateAfterCancel(toDetail, id);
    }

    @FXML
    public void onBack() {
        onCancel();
    }

    /** Après Annuler / Retour, ou après blocage auto-suppression ({@code userId} pour rouvrir la fiche). */
    private void navigateAfterCancel(boolean toDetail, int userId) {
        AppState.clearAdminDeleteContext();
        try {
            if (toDetail && userId > 0) {
                Optional<User> u = userService.findById(userId);
                if (u.isPresent()) {
                    AppState.setAdminDetailUser(u.get());
                    MainApp.showAdminUserDetail();
                } else {
                    MainApp.showAdminUsers();
                }
            } else {
                MainApp.showAdminUsers();
            }
        } catch (IOException | SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onOpenMyProfile() {
        try {
            MainApp.openAdminMyProfile(topbarAvatarHost, userNameLabel, userEmailLabel);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onLogout() {
        AppState.clear();
        try {
            MainApp.showLogin();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavDashboard() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavUsers() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavProducts() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavStocks() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavOrders() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavEvents() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(4);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavTopics() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(7);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavModules() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(6);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavSettings() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavAdminHome() {
        AppState.clearAdminDeleteContext();
        try {
            MainApp.showHome();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
