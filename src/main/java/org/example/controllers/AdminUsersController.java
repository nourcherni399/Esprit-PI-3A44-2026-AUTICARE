package org.example.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.MainApp;
import org.example.models.Role;
import org.example.models.User;
import org.example.models.AdminNotificationItem;
import org.example.services.AdminNotificationService;
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
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

public class AdminUsersController {

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private MenuButton notificationsMenuButton;
    @FXML
    private Label notifBadgeLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private TextField filterField;
    @FXML
    private HBox statsRow;
    @FXML
    private TableView<User> usersTable;

    @FXML
    private ScrollPane usersHomeScroll;
    @FXML
    private StackPane embeddedDashboardHost;

    private static final int DASHBOARD_TAB_EVENTS = 4;

    /** Tableau de bord CRUD chargé une fois et réutilisé (onglets dans la même fenêtre admin). */
    private TabPane embeddedMainTabPane;
    /** Vue événements seule (sidebar), sans TabPane ni barre d’outils de démo. */
    private Region embeddedEventsRoot;
    private AdminEventsPanelController embeddedEventsController;
    /** Vue thématiques (liste + stats + formulaire). */
    private Region embeddedThematiquesRoot;
    private AdminThematiquesPanelController embeddedThematiquesController;

    private final UserService userService = new UserService();
    private final AdminNotificationService adminNotificationService = new AdminNotificationService();
    private ObservableList<User> masterList = FXCollections.observableArrayList();
    private FilteredList<User> filteredList;
    private final Label[] statValueLabels = new Label[6];
    private static final DateTimeFormatter NOTIF_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);

        buildStatCardsPlaceholder();
        setupTable();
        reloadUsers();
        configureNotificationsUi();
        refreshNotificationsUi();

        if (filterField != null) {
            filterField.textProperty().addListener((o, old, v) -> applyFilters());
        }
        if (topSearchField != null) {
            topSearchField.textProperty().addListener((o, old, v) -> applyFilters());
        }

        String pendingSection = AppState.consumePendingAdminUsersSection();
        if ("events".equals(pendingSection)) {
            showEmbeddedDashboardSafely(4);
        } else if ("thematiques".equals(pendingSection)) {
            showEmbeddedThematiquesSafely();
        }
    }

    private void configureNotificationsUi() {
        if (notificationsMenuButton == null) {
            return;
        }
        notificationsMenuButton.setOnShowing(e -> refreshNotificationsMenuItems());
    }

    private void refreshNotificationsUi() {
        if (notifBadgeLabel == null) {
            return;
        }
        try {
            int unread = adminNotificationService.countUnread();
            notifBadgeLabel.setText(String.valueOf(unread));
            notifBadgeLabel.setVisible(unread > 0);
            notifBadgeLabel.setManaged(unread > 0);
        } catch (SQLException ignored) {
            notifBadgeLabel.setText("0");
            notifBadgeLabel.setVisible(false);
            notifBadgeLabel.setManaged(false);
        }
    }

    private void refreshNotificationsMenuItems() {
        if (notificationsMenuButton == null) {
            return;
        }
        notificationsMenuButton.getItems().clear();
        try {
            List<AdminNotificationItem> notifications = adminNotificationService.listLatest(8);
            int total = notifications.size();

            Label headerLabel = new Label(notificationGroupLabel(notifications) + " (" + total + ")");
            headerLabel.getStyleClass().add("admin-notif-menu-header");
            CustomMenuItem headerItem = new CustomMenuItem(headerLabel, false);
            headerItem.getStyleClass().add("admin-notif-menu-header-item");
            headerItem.setDisable(true);
            notificationsMenuButton.getItems().add(headerItem);

            if (notifications.isEmpty()) {
                MenuItem emptyItem = new MenuItem("Aucune notification.");
                emptyItem.setDisable(true);
                notificationsMenuButton.getItems().add(emptyItem);
                return;
            }
            for (AdminNotificationItem item : notifications) {
                VBox card = new VBox(3);
                card.getStyleClass().add("admin-notif-item");
                Label title = new Label(notificationCompactText(item));
                title.getStyleClass().add("admin-notif-item-title");
                String when = item.getDateCreation() != null ? NOTIF_TIME_FMT.format(item.getDateCreation()) : "";
                String actionLabel = notificationActionLabel(item);
                Label meta = new Label(when.isBlank() ? actionLabel : actionLabel + " • " + when);
                meta.getStyleClass().add("admin-notif-item-meta");
                card.getChildren().addAll(title, meta);

                CustomMenuItem menuItem = new CustomMenuItem(card, true);
                menuItem.setOnAction(e -> onNotificationClick(item));
                notificationsMenuButton.getItems().add(menuItem);
            }
        } catch (SQLException ex) {
            MenuItem errorItem = new MenuItem("Impossible de charger les notifications.");
            errorItem.setDisable(true);
            notificationsMenuButton.getItems().add(errorItem);
        }
    }

    private static String notificationGroupLabel(List<AdminNotificationItem> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return "Notifications";
        }
        long inscriptionsCount = notifications.stream()
                .filter(item -> item != null && AdminNotificationService.TYPE_INSCRIPTION_DEMANDE.equals(item.getTypeCode()))
                .count();
        if (inscriptionsCount == notifications.size()) {
            return "Inscriptions événements";
        }
        if (inscriptionsCount > 0) {
            return "Messages & inscriptions";
        }
        return "Messages événements";
    }

    private static String notificationCompactText(AdminNotificationItem item) {
        if (item == null) {
            return "Notification";
        }
        String sender = item.getExpediteurNom() != null && !item.getExpediteurNom().isBlank()
                ? item.getExpediteurNom()
                : "Utilisateur";
        String eventTitle = item.getEvenementTitre() != null && !item.getEvenementTitre().isBlank()
                ? item.getEvenementTitre()
                : "Événement";
        String action = AdminNotificationService.TYPE_MESSAGE_EVENEMENT.equals(item.getTypeCode())
                ? "a envoyé un message"
                : AdminNotificationService.TYPE_INSCRIPTION_DEMANDE.equals(item.getTypeCode())
                ? "a demandé une inscription"
                : "a envoyé une notification";
        return sender + " " + action + " • " + eventTitle;
    }

    private static String notificationActionLabel(AdminNotificationItem item) {
        if (item == null) {
            return "Notification";
        }
        if (AdminNotificationService.TYPE_MESSAGE_EVENEMENT.equals(item.getTypeCode())) {
            return "Cliquer pour répondre au message";
        }
        if (AdminNotificationService.TYPE_INSCRIPTION_DEMANDE.equals(item.getTypeCode())) {
            return "Cliquer pour accepter/refuser l'inscription";
        }
        return "Cliquer pour ouvrir";
    }

    private void onNotificationClick(AdminNotificationItem item) {
        if (item == null) {
            return;
        }
        try {
            adminNotificationService.markAsRead(item.getId());
            refreshNotificationsUi();
            Integer eventId = item.getEvenementId();
            if (eventId != null && eventId > 0) {
                showEmbeddedDashboard(DASHBOARD_TAB_EVENTS);
                if (embeddedEventsController != null) {
                    boolean focusDiscussion = AdminNotificationService.TYPE_MESSAGE_EVENEMENT.equals(item.getTypeCode());
                    embeddedEventsController.openEventDetailFromNotification(eventId, focusDiscussion);
                }
            }
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Notifications", formatErr(ex));
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
        /* Évite la « colonne vide » à droite lorsque la table est plus large que la somme des prefWidth. */
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void doViewUser(User u) {
        AppState.setAdminDetailUser(u);
        try {
            MainApp.showAdminUserDetail();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    private void doEditUser(User u) {
        AppState.beginAdminEdit(u, false);
        try {
            MainApp.showAdminUserEdit();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    private void doDeleteUser(User u) {
        AppState.beginAdminDelete(u, false);
        try {
            MainApp.showAdminUserDelete();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
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
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
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
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onOpenMyProfile() {
        try {
            MainApp.openAdminMyProfile(topbarAvatarHost, userNameLabel, userEmailLabel);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onLogout() {
        AppState.clear();
        try {
            MainApp.showLogin();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    /** Onglets {@code dashboard.fxml} : 0 Utilisateurs, 1 Produits, 2 RDV, 3 Dispo, 4 Événements, 5 Inscriptions, 6 Modules, 7 Blog. */
    @FXML
    public void onNavDashboard() {
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            showEmbeddedDashboardSafely(0);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onNavUsers() {
        try {
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
        showUsersHome();
    }

    @FXML
    public void onNavProducts() {
        try {
            MainApp.showAdminProducts();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onNavStocks() {
        try {
            MainApp.showAdminStocks();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onNavOrders() {
        try {
            MainApp.showAdminOrders();
        } catch (IOException e) {
            showEmbeddedDashboardSafely(1);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onNavEvents() {
        try {
            MainApp.showDashboard(4);
        } catch (IOException e) {
            showEmbeddedDashboardSafely(4);
            if (embeddedEventsController != null) {
                embeddedEventsController.showListView();
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onNavTopics() {
        try {
            MainApp.showDashboard(7);
        } catch (IOException e) {
            showEmbeddedThematiquesSafely();
            if (embeddedThematiquesController != null) {
                embeddedThematiquesController.showListView();
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onNavModules() {
        try {
            MainApp.showAdminModules();
        } catch (IOException e) {
            showEmbeddedDashboardSafely(6);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    @FXML
    public void onNavSettings() {
        try {
            MainApp.showDashboard(0);
        } catch (IOException e) {
            showEmbeddedDashboardSafely(0);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    private void showEmbeddedDashboardSafely(int tabIndex) {
        try {
            showEmbeddedDashboard(tabIndex);
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(ex));
        }
    }

    private void showEmbeddedThematiquesSafely() {
        try {
            showEmbeddedThematiques();
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(ex));
        }
    }

    /** Affiche la liste utilisateurs (vue d’accueil admin) sans recharger le FXML. */
    private void showUsersHome() {
        if (embeddedDashboardHost != null) {
            embeddedDashboardHost.setVisible(false);
            embeddedDashboardHost.setManaged(false);
        }
        if (usersHomeScroll != null) {
            usersHomeScroll.setVisible(true);
            usersHomeScroll.setManaged(true);
        }
    }

    /**
     * Zone centrale : soit le panneau événements dédié (onglet 4), soit le {@code TabPane} du dashboard.
     */
    private void showEmbeddedDashboard(int tabIndex) throws IOException {
        if (embeddedDashboardHost == null || usersHomeScroll == null) {
            return;
        }
        if (tabIndex == DASHBOARD_TAB_EVENTS) {
            ensureEmbeddedEventsPanelLoaded();
            swapEmbeddedContent(embeddedEventsRoot);
            embeddedEventsController.refreshEventsFromDb();
        } else {
            ensureEmbeddedDashboardTabPaneLoaded(tabIndex);
            swapEmbeddedContent(embeddedMainTabPane);
            int n = embeddedMainTabPane.getTabs().size();
            embeddedMainTabPane.getSelectionModel().select(Math.min(Math.max(0, tabIndex), n - 1));
        }
        usersHomeScroll.setVisible(false);
        usersHomeScroll.setManaged(false);
        embeddedDashboardHost.setVisible(true);
        embeddedDashboardHost.setManaged(true);
    }

    private void ensureEmbeddedEventsPanelLoaded() throws IOException {
        if (embeddedEventsRoot != null) {
            return;
        }
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/fxml/admin-events-panel.fxml"));
        Parent root = loader.load();
        embeddedEventsController = loader.getController();
        if (!(root instanceof Region region)) {
            throw new IOException("admin-events-panel.fxml: racine attendue Region");
        }
        embeddedEventsRoot = region;
    }

    private void showEmbeddedThematiques() throws IOException {
        if (embeddedDashboardHost == null || usersHomeScroll == null) {
            return;
        }
        ensureEmbeddedThematiquesPanelLoaded();
        swapEmbeddedContent(embeddedThematiquesRoot);
        embeddedThematiquesController.refreshFromDb();
        usersHomeScroll.setVisible(false);
        usersHomeScroll.setManaged(false);
        embeddedDashboardHost.setVisible(true);
        embeddedDashboardHost.setManaged(true);
    }

    private void ensureEmbeddedThematiquesPanelLoaded() throws IOException {
        if (embeddedThematiquesRoot != null) {
            return;
        }
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/fxml/admin-thematiques-panel.fxml"));
        Parent root = loader.load();
        embeddedThematiquesController = loader.getController();
        embeddedThematiquesController.setNavigateToNewEventForThematique(this::navigateToCreateEventForThematique);
        if (!(root instanceof Region region)) {
            throw new IOException("admin-thematiques-panel.fxml: racine attendue Region");
        }
        embeddedThematiquesRoot = region;
    }

    /** Depuis la fiche thématique : ouvre le panneau Événements avec « Nouvel événement » et la thématique présélectionnée. */
    private void navigateToCreateEventForThematique(String nomThematique) {
        try {
            showEmbeddedDashboard(4);
            embeddedEventsController.openNewEventWithThematiqueNom(nomThematique);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    private void ensureEmbeddedDashboardTabPaneLoaded(int tabIndexForFirstOpen) throws IOException {
        if (embeddedMainTabPane != null) {
            return;
        }
        MainApp.prepareEmbeddedDashboardTab(tabIndexForFirstOpen);
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/fxml/dashboard.fxml"));
        Parent root = loader.load();
        if (!(root instanceof BorderPane bp)) {
            throw new IOException("dashboard.fxml: racine attendue BorderPane");
        }
        Node center = bp.getCenter();
        if (!(center instanceof TabPane tp)) {
            throw new IOException("dashboard.fxml: centre attendu TabPane");
        }
        embeddedMainTabPane = tp;
        bp.setTop(null);
        bp.setCenter(null);
    }

    private void swapEmbeddedContent(Region content) {
        for (Node old : embeddedDashboardHost.getChildren()) {
            if (old instanceof Region ro) {
                ro.prefWidthProperty().unbind();
                ro.prefHeightProperty().unbind();
                ro.maxHeightProperty().unbind();
            }
        }
        embeddedDashboardHost.getChildren().setAll(content);
        StackPane.setAlignment(content, Pos.TOP_CENTER);
        content.prefWidthProperty().bind(embeddedDashboardHost.widthProperty());
        if (content instanceof ScrollPane sp) {
            sp.setFitToWidth(true);
            content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            sp.maxHeightProperty().bind(embeddedDashboardHost.heightProperty());
        } else {
            content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            content.prefHeightProperty().bind(embeddedDashboardHost.heightProperty());
        }
    }

    @FXML
    public void onNavAdminHome() {
        try {
            MainApp.showHome();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", formatErr(e));
        }
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /** Affiche la chaîne des causes (utile pour LoadException FXML tronquée dans la boîte). */
    private static String formatErr(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        int depth = 0;
        while (t != null && depth++ < 12) {
            if (depth > 1) {
                sb.append("\n\n");
            }
            sb.append(t.getClass().getSimpleName()).append(": ");
            sb.append(t.getMessage() != null ? t.getMessage() : "(sans message)");
            t = t.getCause();
        }
        return sb.toString();
    }
}
