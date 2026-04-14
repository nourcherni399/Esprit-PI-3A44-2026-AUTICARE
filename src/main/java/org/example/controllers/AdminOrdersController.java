package org.example.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.MainApp;
import org.example.models.CustomerOrder;
import org.example.models.CustomerOrderLine;
import org.example.services.CustomerOrderService;
import org.example.utils.AdminNotificationBellHelper;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.UiResources;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Liste des commandes clients pour l’admin : table {@code commande} (panier Java) et, si présentes,
 * anciennes lignes {@code order} (legacy Symfony).
 */
public class AdminOrdersController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private Label notifBellLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private StackPane ordersHost;
    @FXML
    private ImageView sidebarLogoView;

    private final CustomerOrderService orderService = new CustomerOrderService();
    private List<AdminOrderView> allOrders = new ArrayList<>();
    private final ObservableList<CustomerOrderRow> tableRows = FXCollections.observableArrayList();
    /** Référencé pour les actions sur commande. */
    private TableView<CustomerOrderRow> ordersTable;
    private Button nextStepBtn;
    private Button approveOrderBtn;
    private Button cancelOrderBtn;

    /** Ligne source (commande boutique ou table legacy {@code order}). */
    private static final class AdminOrderView {
        final CustomerOrder order;
        /** {@code true} si la ligne vient de {@code order} / {@code order_item}. */
        final boolean legacy;

        AdminOrderView(CustomerOrder order, boolean legacy) {
            this.order = order;
            this.legacy = legacy;
        }
    }

    /** Ligne tableau (valeurs formatées pour affichage). */
    public static final class CustomerOrderRow {
        private final String idDisplay;
        private final String dateStr;
        private final String nom;
        private final String email;
        private final String totalStr;
        private final String statutDisplay;
        private final String modePayment;
        private final int userId;
        private final CustomerOrder source;
        private final boolean legacy;

        CustomerOrderRow(AdminOrderView av) {
            CustomerOrder o = av.order;
            this.source = o;
            this.legacy = av.legacy;
            this.idDisplay = av.legacy ? "A" + o.id() : "C" + o.id();
            this.dateStr = o.dateCreation() != null ? DT.format(o.dateCreation()) : "—";
            this.nom = o.nom() != null ? o.nom() : "—";
            this.email = o.email() != null ? o.email() : "—";
            this.totalStr = String.format(Locale.FRENCH, "%.2f DT", o.total());
            this.statutDisplay = labelStatutAdmin(o.statut());
            this.modePayment = o.modePayment() != null ? o.modePayment() : "—";
            this.userId = o.userId();
        }

        public String getIdDisplay() {
            return idDisplay;
        }

        public boolean isLegacy() {
            return legacy;
        }

        public String getDateStr() {
            return dateStr;
        }

        public String getNom() {
            return nom;
        }

        public String getEmail() {
            return email;
        }

        public String getTotalStr() {
            return totalStr;
        }

        public String getStatutDisplay() {
            return statutDisplay;
        }

        public String getModePayment() {
            return modePayment;
        }

        public int getUserId() {
            return userId;
        }

        public CustomerOrder source() {
            return source;
        }
    }

    @FXML
    public void initialize() {
        UiResources.applySidebarLogo(sidebarLogoView);
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        AdminNotificationBellHelper.attach(notifBellLabel);

        if (topSearchField != null) {
            topSearchField.textProperty().addListener((obs, prev, cur) -> applyFilter());
        }

        showOrdersPage();
        loadOrders();
    }

    private void showOrdersPage() {
        Label title = new Label("Commandes clients");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2a2a2a;");
        Label desc = new Label(
            "Chaque ligne correspond à une commande (panier → paiement). "
                + "Approuver = première validation (en attente → préparation). Annuler = commande refusée (le client est notifié). "
                + "Étape suivante = avancer jusqu’à livrée (le client est notifié à la validation et à la livraison). Double-clic = détail."
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #6b7280;");

        Button refreshBtn = new Button("Actualiser");
        refreshBtn.setStyle(
            "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 13px; -fx-padding: 8 16 8 16;"
        );
        refreshBtn.setOnAction(e -> loadOrders());

        nextStepBtn = new Button("Étape suivante");
        nextStepBtn.setDisable(true);
        nextStepBtn.setStyle(
            "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 13px; -fx-padding: 8 16 8 16;"
        );
        nextStepBtn.setOnAction(e -> advanceSelectedOrder());

        approveOrderBtn = new Button("Approuver");
        approveOrderBtn.setDisable(true);
        approveOrderBtn.setStyle(
            "-fx-background-color: #15803d; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 13px; -fx-padding: 8 16 8 16;"
        );
        approveOrderBtn.setOnAction(e -> advanceSelectedOrder());

        cancelOrderBtn = new Button("Annuler la commande");
        cancelOrderBtn.setDisable(true);
        cancelOrderBtn.setStyle(
            "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 13px; -fx-padding: 8 16 8 16;"
        );
        cancelOrderBtn.setOnAction(e -> cancelSelectedOrder());

        TableView<CustomerOrderRow> table = new TableView<>(tableRows);
        this.ordersTable = table;
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Aucune commande pour l’instant."));
        table.setPrefHeight(520);

        TableColumn<CustomerOrderRow, String> colId = new TableColumn<>("N°");
        colId.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getIdDisplay()));
        colId.setMaxWidth(70);

        TableColumn<CustomerOrderRow, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDateStr()));

        TableColumn<CustomerOrderRow, String> colNom = new TableColumn<>("Nom (livraison)");
        colNom.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getNom()));

        TableColumn<CustomerOrderRow, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getEmail()));

        TableColumn<CustomerOrderRow, String> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTotalStr()));

        TableColumn<CustomerOrderRow, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatutDisplay()));

        TableColumn<CustomerOrderRow, String> colPay = new TableColumn<>("Paiement");
        colPay.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getModePayment()));

        TableColumn<CustomerOrderRow, Integer> colUser = new TableColumn<>("Compte");
        colUser.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getUserId()));
        colUser.setMaxWidth(80);

        table.getColumns().addAll(colId, colDate, colNom, colEmail, colTotal, colStatut, colPay, colUser);

        table.getSelectionModel().selectedItemProperty().addListener((obs, prev, cur) -> syncOrderActionButtonsState());

        table.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                showOrderDetail(table.getSelectionModel().getSelectedItem());
            }
        });

        HBox head = new HBox(12, refreshBtn, approveOrderBtn, cancelOrderBtn, nextStepBtn);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(12, title, desc, head, table);
        page.setPadding(new Insets(18));
        page.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #eee7df; -fx-border-radius: 12;");
        VBox.setVgrow(table, Priority.ALWAYS);

        ordersHost.getChildren().setAll(page);
        syncOrderActionButtonsState();
    }

    private void syncOrderActionButtonsState() {
        if (ordersTable == null) {
            return;
        }
        CustomerOrderRow row = ordersTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            if (nextStepBtn != null) {
                nextStepBtn.setDisable(true);
            }
            if (approveOrderBtn != null) {
                approveOrderBtn.setDisable(true);
            }
            if (cancelOrderBtn != null) {
                cancelOrderBtn.setDisable(true);
            }
            return;
        }
        CustomerOrder o = row.source();
        boolean legacy = row.isLegacy();
        if (nextStepBtn != null) {
            nextStepBtn.setDisable(!orderService.canAdvanceStatut(legacy, o.statut()));
        }
        if (approveOrderBtn != null) {
            approveOrderBtn.setDisable(legacy || !CustomerOrderService.isEnAttenteCommande(o.statut()));
        }
        if (cancelOrderBtn != null) {
            cancelOrderBtn.setDisable(legacy || !CustomerOrderService.isCommandeCancellableByAdmin(o.statut()));
        }
    }

    private void cancelSelectedOrder() {
        if (ordersTable == null) {
            return;
        }
        CustomerOrderRow row = ordersTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            alert(Alert.AlertType.INFORMATION, "Commandes", "Sélectionnez une commande dans le tableau.");
            return;
        }
        if (row.isLegacy()) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Annuler la commande");
        confirm.setHeaderText("Annuler la commande n° " + row.getIdDisplay() + " ?");
        confirm.setContentText(
            "Le client recevra une notification. Les commandes livrées ou déjà annulées ne peuvent pas être annulées."
        );
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            orderService.annulerCommandeParAdmin(row.source().id());
            loadOrders();
            syncOrderActionButtonsState();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Annulation", ex.getMessage() != null ? ex.getMessage() : "Erreur SQL.");
        }
    }

    private void advanceSelectedOrder() {
        if (ordersTable == null) {
            return;
        }
        CustomerOrderRow row = ordersTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            alert(Alert.AlertType.INFORMATION, "Commandes", "Sélectionnez une commande dans le tableau.");
            return;
        }
        try {
            orderService.advanceOrderStatut(row.isLegacy(), row.source().id());
            loadOrders();
            syncOrderActionButtonsState();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Statut", ex.getMessage() != null ? ex.getMessage() : "Erreur SQL.");
        }
    }

    private static String labelStatutAdmin(String raw) {
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "en_attente" -> "En attente";
            case "annulée", "annulee" -> "Annulée";
            case "confirmer" -> "Préparation";
            case "livraison" -> "En livraison";
            case "recu", "reçu" -> "Reçu";
            case "payée", "payee" -> "Payée";
            case "livrée", "livree" -> "Livrée";
            case "pending" -> "En attente";
            case "confirmed" -> "Confirmée";
            case "shipped" -> "Expédiée";
            case "delivered" -> "Livrée";
            case "processing" -> "En traitement";
            default -> raw;
        };
    }

    private void showOrderDetail(CustomerOrderRow row) {
        CustomerOrder o = row.source();
        try {
            List<CustomerOrderLine> lines = row.isLegacy()
                ? orderService.findLinesForLegacyOrder(o.id())
                : orderService.findLinesForOrder(o.id());
            StringBuilder sb = new StringBuilder();
            sb.append("Commande n° ").append(row.getIdDisplay()).append("\n");
            sb.append("Statut : ").append(labelStatutAdmin(o.statut())).append("\n");
            sb.append("Date : ").append(o.dateCreation() != null ? DT.format(o.dateCreation()) : "—").append("\n");
            sb.append("Client : ").append(o.nom() != null ? o.nom() : "—").append("\n");
            sb.append("Email : ").append(o.email() != null ? o.email() : "—").append("\n");
            sb.append("Tél. : ").append(o.telephone() != null ? o.telephone() : "—").append("\n");
            sb.append("Adresse : ")
                .append(o.adresse() != null ? o.adresse() : "")
                .append(", ")
                .append(o.codePostal() != null ? o.codePostal() : "")
                .append(" ")
                .append(o.ville() != null ? o.ville() : "")
                .append("\n\nArticles :\n");
            if (lines.isEmpty()) {
                sb.append("— (aucune ligne)");
            } else {
                for (CustomerOrderLine line : lines) {
                    sb.append("• ")
                        .append(line.produitNom() != null ? line.produitNom() : "Produit #" + line.produitId())
                        .append(" × ").append(line.quantite())
                        .append(" — ").append(String.format(Locale.FRENCH, "%.2f DT", line.sousTotal()))
                        .append("\n");
                }
            }
            sb.append("\nTotal : ").append(String.format(Locale.FRENCH, "%.2f DT", o.total()));
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Détail commande");
            a.setHeaderText("Commande n° " + row.getIdDisplay());
            a.setContentText(sb.toString());
            a.showAndWait();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", ex.getMessage());
        }
    }

    private void loadOrders() {
        try {
            List<AdminOrderView> merged = new ArrayList<>();
            for (CustomerOrder c : orderService.findAllOrders()) {
                merged.add(new AdminOrderView(c, false));
            }
            for (CustomerOrder c : orderService.findAllOrdersFromLegacyOrderTable()) {
                merged.add(new AdminOrderView(c, true));
            }
            merged.sort(Comparator.comparing(
                (AdminOrderView a) -> a.order.dateCreation(),
                Comparator.nullsLast(Comparator.naturalOrder())
            ).reversed());
            allOrders = merged;
            applyFilter();
            syncOrderActionButtonsState();
        } catch (SQLException e) {
            tableRows.clear();
            alert(Alert.AlertType.ERROR, "Commandes", e.getMessage());
        }
    }

    private void applyFilter() {
        String q = topSearchField == null ? "" : topSearchField.getText().trim().toLowerCase(Locale.FRENCH);
        tableRows.clear();
        for (AdminOrderView v : allOrders) {
            if (q.isEmpty() || matchesFilter(v, q)) {
                tableRows.add(new CustomerOrderRow(v));
            }
        }
    }

    private static boolean matchesFilter(AdminOrderView v, String q) {
        CustomerOrder o = v.order;
        if (("c" + o.id()).contains(q) || ("a" + o.id()).contains(q)) {
            return true;
        }
        if (String.valueOf(o.id()).contains(q)) {
            return true;
        }
        if (o.nom() != null && o.nom().toLowerCase(Locale.FRENCH).contains(q)) {
            return true;
        }
        if (o.email() != null && o.email().toLowerCase(Locale.FRENCH).contains(q)) {
            return true;
        }
        if (o.statut() != null && o.statut().toLowerCase(Locale.FRENCH).contains(q)) {
            return true;
        }
        if (String.valueOf(o.userId()).contains(q)) {
            return true;
        }
        return o.modePayment() != null && o.modePayment().toLowerCase(Locale.FRENCH).contains(q);
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
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
        try {
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavProducts() {
        try {
            MainApp.showAdminProducts();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavDemandesProduit() {
        try {
            MainApp.showAdminDemandesProduit();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavStocks() {
        try {
            MainApp.showAdminStocks();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavOrders() {
        loadOrders();
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
}
