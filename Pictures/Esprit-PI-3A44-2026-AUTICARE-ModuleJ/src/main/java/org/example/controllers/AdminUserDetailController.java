package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.MainApp;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.UserService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.UserAvatarGraphic;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class AdminUserDetailController {

    private static final DateTimeFormatter DT_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withLocale(Locale.FRENCH);

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private StackPane detailAvatarStack;
    @FXML
    private Label detailDisplayNameLabel;
    @FXML
    private Label detailEmailHeaderLabel;
    @FXML
    private Label detailRoleBadge;
    @FXML
    private Label detailActifBadge;
    @FXML
    private Label valIdLabel;
    @FXML
    private Label valNomLabel;
    @FXML
    private Label valPrenomLabel;
    @FXML
    private Label valEmailLabel;
    @FXML
    private Label valTelLabel;
    @FXML
    private Label valActifLabel;
    @FXML
    private Label valCreatedLabel;
    @FXML
    private Label valUpdatedLabel;
    @FXML
    private VBox parentAttrsBox;
    @FXML
    private Label valRelationLabel;

    private final UserService userService = new UserService();
    private User displayedUser;

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);

        User ref = AppState.getAdminDetailUser();
        if (ref == null) {
            goBackToList();
            return;
        }
        try {
            Optional<User> fresh = userService.findById(ref.getId());
            if (fresh.isEmpty()) {
                alert(Alert.AlertType.WARNING, "Introuvable", "Cet utilisateur n'existe plus.");
                goBackToList();
                return;
            }
            displayedUser = fresh.get();
            bindDetail(displayedUser);
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
            goBackToList();
        }
    }

    private void bindDetail(User u) {
        String prenom = u.getPrenom() != null ? u.getPrenom() : "";
        String nom = u.getNom() != null ? u.getNom() : "";
        String full = (prenom + " " + nom).trim();
        detailDisplayNameLabel.setText(full.isEmpty() ? "—" : full.toLowerCase(Locale.FRENCH));
        detailEmailHeaderLabel.setText(u.getEmail() != null ? u.getEmail() : "—");

        if (detailAvatarStack != null) {
            detailAvatarStack.getChildren().clear();
            detailAvatarStack.getChildren().add(
                    UserAvatarGraphic.build(u, 88, UserAvatarGraphic.initialsFor(u), "admin-detail-avatar"));
        }

        detailRoleBadge.setText(roleDisplay(u.getRole()));
        detailRoleBadge.getStyleClass().removeAll(
                "role-admin", "role-medecin", "role-parent", "role-patient", "role-user");
        detailRoleBadge.getStyleClass().add(roleStyleClass(u.getRole()));

        detailActifBadge.setText(u.isActif() ? "Actif" : "Inactif");
        detailActifBadge.getStyleClass().removeAll("admin-badge-actif", "admin-badge-inactif");
        detailActifBadge.getStyleClass().add(u.isActif() ? "admin-badge-actif" : "admin-badge-inactif");

        valIdLabel.setText(String.valueOf(u.getId()));
        valNomLabel.setText(emptyDash(nom));
        valPrenomLabel.setText(emptyDash(prenom));
        valEmailLabel.setText(emptyDash(u.getEmail()));
        valTelLabel.setText(emptyDash(u.getTelephone()));
        valActifLabel.setText(u.isActif() ? "Oui" : "Non");
        valCreatedLabel.setText(u.getCreatedAt() != null ? u.getCreatedAt().format(DT_FR) : "—");
        valUpdatedLabel.setText(u.getUpdatedAt() != null ? u.getUpdatedAt().format(DT_FR) : "—");

        boolean parent = u.getRole() == Role.PARENT;
        parentAttrsBox.setVisible(parent);
        parentAttrsBox.setManaged(parent);
        if (parent) {
            valRelationLabel.setText(emptyDash(u.getRelationParent()));
        }
    }

    private static String emptyDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private static String roleDisplay(Role r) {
        if (r == null) {
            return "";
        }
        return switch (r) {
            case ADMIN -> "ADMIN";
            case MEDECIN -> "MÉDECIN";
            case PARENT -> "PARENT";
            case PATIENT -> "PATIENT";
            case USER -> "USER";
        };
    }

    private static String roleStyleClass(Role r) {
        if (r == null) {
            return "role-user";
        }
        return switch (r) {
            case ADMIN -> "role-admin";
            case MEDECIN -> "role-medecin";
            case PARENT -> "role-parent";
            case PATIENT -> "role-patient";
            case USER -> "role-user";
        };
    }

    @FXML
    public void onBackToList() {
        goBackToList();
    }

    private void goBackToList() {
        AppState.clearAdminDetailUser();
        try {
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onEditUser() {
        if (displayedUser == null) {
            return;
        }
        AppState.beginAdminEdit(displayedUser, true);
        try {
            MainApp.showAdminUserEdit();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onDeleteUser() {
        if (displayedUser == null) {
            return;
        }
        AppState.beginAdminDelete(displayedUser, true);
        try {
            MainApp.showAdminUserDelete();
        } catch (IOException e) {
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
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavUsers() {
        goBackToList();
    }

    @FXML
    public void onNavProducts() {
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavStocks() {
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavOrders() {
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavEvents() {
        try {
            MainApp.showDashboard(4);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavTopics() {
        try {
            MainApp.showDashboard(7);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavModules() {
        try {
            MainApp.showAdminModules();
            MainApp.showDashboard(6);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavSettings() {
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavAdminHome() {
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
