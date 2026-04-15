package org.example.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public class AdminUsersController {

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private TextField filterField;
    @FXML
    private HBox statsRow;
    @FXML
    private TableView<User> usersTable;

    private final UserService userService = new UserService();
    private ObservableList<User> masterList = FXCollections.observableArrayList();
    private FilteredList<User> filteredList;
    private final Label[] statValueLabels = new Label[6];

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);

        buildStatCardsPlaceholder();
        setupTable();
        reloadUsers();

        if (filterField != null) {
            filterField.textProperty().addListener((o, old, v) -> applyFilters());
        }
        if (topSearchField != null) {
            topSearchField.textProperty().addListener((o, old, v) -> applyFilters());
        }
    }

    private void buildStatCardsPlaceholder() {
        if (statsRow == null) {
            return;
        }
        statsRow.getChildren().clear();
        record StatDef(String key, String title, String emoji) {}
        StatDef[] defs = {
                new StatDef("total", "Total", "👥"),
                new StatDef("actifs", "Actifs", "✓"),
                new StatDef("admins", "Admins", "⚙"),
                new StatDef("medecins", "Médecins", "🎓"),
                new StatDef("patients", "Patients", "👤"),
                new StatDef("parents", "Parents", "👨‍👩‍👧")
        };
        for (int i = 0; i < defs.length; i++) {
            StatDef d = defs[i];
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().addAll("admin-stat-card", "admin-stat-card-" + d.key());
            row.setMaxWidth(Double.MAX_VALUE);

            VBox left = new VBox(4);
            left.setAlignment(Pos.TOP_LEFT);
            Label lab = new Label(d.title());
            lab.getStyleClass().addAll("admin-stat-label", "admin-stat-label-" + d.key());

            Label val = new Label("0");
            val.getStyleClass().addAll("admin-stat-value", "admin-stat-value-" + d.key());
            statValueLabels[i] = val;
            left.getChildren().addAll(lab, val);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label icon = new Label(d.emoji());
            icon.getStyleClass().addAll("admin-stat-icon-circle", "admin-stat-icon-" + d.key());
            icon.setMinSize(44, 44);
            icon.setAlignment(Pos.CENTER);

            row.getChildren().addAll(left, spacer, icon);
            statsRow.getChildren().add(row);
        }
    }

    private void updateStats(List<User> users) {
        long total = users.size();
        long actifs = users.stream().filter(User::isActif).count();
        long admins = users.stream().filter(u -> u.getRole() == Role.ADMIN).count();
        long med = users.stream().filter(u -> u.getRole() == Role.MEDECIN).count();
        long pat = users.stream().filter(u -> u.getRole() == Role.PATIENT).count();
        long par = users.stream().filter(u -> u.getRole() == Role.PARENT).count();
        long[] vals = {total, actifs, admins, med, pat, par};
        for (int i = 0; i < statValueLabels.length && i < vals.length; i++) {
            if (statValueLabels[i] != null) {
                statValueLabels[i].setText(String.valueOf(vals[i]));
            }
        }
    }

    private void setupTable() {
        TableColumn<User, String> colPhoto = new TableColumn<>("PHOTO");
        colPhoto.setPrefWidth(72);
        colPhoto.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                User u = getTableRow().getItem();
                setGraphic(UserAvatarGraphic.build(u, 36, UserAvatarGraphic.initialsFor(u), null));
            }
        });
        colPhoto.setCellValueFactory(c -> new SimpleStringProperty(""));

        TableColumn<User, String> colNom = new TableColumn<>("NOM");
        colNom.setPrefWidth(180);
        colNom.setCellValueFactory(c -> {
            User u = c.getValue();
            String n = (u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : "");
            return new SimpleStringProperty(n.trim());
        });

        TableColumn<User, String> colEmail = new TableColumn<>("EMAIL");
        colEmail.setPrefWidth(220);
        colEmail.setCellValueFactory(c -> {
            String email = c.getValue() != null ? c.getValue().getEmail() : "";
            return new SimpleStringProperty(email != null ? email : "");
        });

        TableColumn<User, String> colRole = new TableColumn<>("RÔLE");
        colRole.setPrefWidth(120);
        colRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                User u = getTableRow().getItem();
                Label badge = new Label(roleDisplay(u.getRole()));
                badge.getStyleClass().addAll("role-badge", roleStyleClass(u.getRole()));
                setGraphic(badge);
            }
        });
        colRole.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole().name()));

        TableColumn<User, Void> colActions = new TableColumn<>("ACTIONS");
        colActions.setPrefWidth(140);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button view = new Button("👁");
            private final Button edit = new Button("✎");
            private final Button del = new Button("🗑");
            private final HBox box = new HBox(6, view, edit, del);

            {
                view.getStyleClass().addAll("admin-btn-icon", "admin-btn-view");
                edit.getStyleClass().addAll("admin-btn-icon", "admin-btn-edit");
                del.getStyleClass().addAll("admin-btn-icon", "admin-btn-delete");
                view.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        doViewUser(getTableRow().getItem());
                    }
                });
                edit.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        doEditUser(getTableRow().getItem());
                    }
                });
                del.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        doDeleteUser(getTableRow().getItem());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : box);
            }
        });

        usersTable.getColumns().setAll(colPhoto, colNom, colEmail, colRole, colActions);
    }

    private void doViewUser(User u) {
        AppState.setAdminDetailUser(u);
        try {
            MainApp.showAdminUserDetail();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void doEditUser(User u) {
        AppState.beginAdminEdit(u, false);
        try {
            MainApp.showAdminUserEdit();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void doDeleteUser(User u) {
        AppState.beginAdminDelete(u, false);
        try {
            MainApp.showAdminUserDelete();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
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

    private void reloadUsers() {
        try {
            List<User> list = userService.findAll();
            masterList.setAll(list);
            filteredList = new FilteredList<>(masterList, u -> true);
            usersTable.setItems(filteredList);
            updateStats(list);
            applyFilters();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void applyFilters() {
        if (filteredList == null) {
            return;
        }
        String f1 = filterField != null && filterField.getText() != null ? filterField.getText().trim().toLowerCase(Locale.FRENCH) : "";
        String f2 = topSearchField != null && topSearchField.getText() != null ? topSearchField.getText().trim().toLowerCase(Locale.FRENCH) : "";
        Predicate<User> p = u -> {
            String nom = ((u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : "")).toLowerCase(Locale.FRENCH);
            String mail = u.getEmail() != null ? u.getEmail().toLowerCase(Locale.FRENCH) : "";
            boolean ok1 = f1.isEmpty() || nom.contains(f1) || mail.contains(f1);
            boolean ok2 = f2.isEmpty() || nom.contains(f2) || mail.contains(f2);
            return ok1 && ok2;
        };
        filteredList.setPredicate(p);
    }

    @FXML
    public void onSearchUsers() {
        applyFilters();
    }

    @FXML
    public void onSortAsc() {
        sortUsers(true);
    }

    @FXML
    public void onSortDesc() {
        sortUsers(false);
    }

    private void sortUsers(boolean asc) {
        Collator coll = Collator.getInstance(Locale.FRENCH);
        Comparator<User> cmp = Comparator.comparing(
                (User u) -> (u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : ""),
                coll);
        if (!asc) {
            cmp = cmp.reversed();
        }
        FXCollections.sort(masterList, cmp);
    }

    @FXML
    public void onNewUser() {
        try {
            MainApp.showAdminUserAdd();
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

    /** Onglets {@code dashboard.fxml} : 0 Utilisateurs, 1 Produits, 2 RDV, 3 Dispo, 4 Événements, 5 Inscriptions, 6 Modules, 7 Blog. */
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
        try {
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
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
