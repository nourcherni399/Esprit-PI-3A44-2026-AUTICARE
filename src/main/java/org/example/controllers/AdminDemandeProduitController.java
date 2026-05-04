package org.example.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import org.example.MainApp;
import org.example.models.DemandeProduit;
import org.example.models.Stock;
import org.example.models.User;
import org.example.services.DemandeProduitService;
import org.example.services.StockService;
import org.example.utils.AdminNotificationBellHelper;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.UiResources;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Administration des demandes produit (aligné Symfony {@code /admin/demandes-produit}).
 */
public class AdminDemandeProduitController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private TableView<DemandeProduit> table;
    @FXML
    private ImageView sidebarLogoView;
    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private Label notifBellLabel;

    private final DemandeProduitService demandeService = new DemandeProduitService();
    private final StockService stockService = new StockService();

    @FXML
    private void initialize() {
        UiResources.applySidebarLogo(sidebarLogoView);
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        AdminNotificationBellHelper.attach(notifBellLabel);

        TableColumn<DemandeProduit, String> colId = new TableColumn<>("Id");
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().id())));
        TableColumn<DemandeProduit, String> colNom = new TableColumn<>("Nom proposé");
        colNom.setPrefWidth(180);
        colNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nom()));
        TableColumn<DemandeProduit, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().statut()));
        TableColumn<DemandeProduit, String> colPrix = new TableColumn<>("Prix est.");
        colPrix.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().prixEstime())));
        TableColumn<DemandeProduit, String> colDate = new TableColumn<>("Créée le");
        colDate.setPrefWidth(140);
        colDate.setCellValueFactory(c -> {
            var t = c.getValue().createdAt();
            return new SimpleStringProperty(t == null ? "" : FMT.format(t));
        });
        table.getColumns().setAll(List.of(colId, colNom, colStatut, colPrix, colDate));
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            table.setItems(FXCollections.observableArrayList(demandeService.findAll()));
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Base", e.getMessage());
        }
    }

    @FXML
    private void onApprouver() {
        DemandeProduit d = table.getSelectionModel().getSelectedItem();
        if (d == null) {
            alert(Alert.AlertType.WARNING, "Sélection", "Sélectionnez une demande.");
            return;
        }
        User admin = AppState.getCurrentUser();
        if (admin == null) {
            return;
        }
        try {
            demandeService.approuver(d.id(), admin.getId());
            alert(Alert.AlertType.INFORMATION, "OK", "Demande approuvée.");
            refresh();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    private void onRejeter() {
        DemandeProduit d = table.getSelectionModel().getSelectedItem();
        if (d == null) {
            alert(Alert.AlertType.WARNING, "Sélection", "Sélectionnez une demande.");
            return;
        }
        User admin = AppState.getCurrentUser();
        if (admin == null) {
            return;
        }
        try {
            demandeService.rejeter(d.id(), admin.getId());
            alert(Alert.AlertType.INFORMATION, "OK", "Demande rejetée.");
            refresh();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    private void onCreerProduit() {
        DemandeProduit d = table.getSelectionModel().getSelectedItem();
        if (d == null) {
            alert(Alert.AlertType.WARNING, "Sélection", "Sélectionnez une demande approuvée.");
            return;
        }
        if (!DemandeProduitService.STATUT_APPROUVE.equals(d.statut())) {
            alert(Alert.AlertType.WARNING, "Statut", "Approuvez d’abord la demande, puis créez le produit.");
            return;
        }
        User admin = AppState.getCurrentUser();
        if (admin == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Créer le produit");
        dialog.setHeaderText("Choisissez l’emplacement stock et la quantité catalogue.");
        ComboBox<Stock> stockCombo = new ComboBox<>();
        Spinner<Integer> qtySpin = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99999, 1));
        try {
            stockCombo.getItems().setAll(stockService.findAll());
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Stock", e.getMessage());
            return;
        }
        if (stockCombo.getItems().isEmpty()) {
            alert(Alert.AlertType.WARNING, "Stock", "Créez d’abord un emplacement dans Stocks.");
            return;
        }
        stockCombo.getSelectionModel().selectFirst();
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("Stock"), stockCombo);
        g.addRow(1, new Label("Quantité catalogue"), qtySpin);
        dialog.getDialogPane().setContent(new VBox(10, g));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        Stock s = stockCombo.getSelectionModel().getSelectedItem();
        if (s == null) {
            alert(Alert.AlertType.WARNING, "Stock", "Choisissez un emplacement.");
            return;
        }
        qtySpin.commitValue();
        int qty = qtySpin.getValue() != null ? qtySpin.getValue() : 1;
        try {
            int pid = demandeService.creerProduitDepuisDemande(d.id(), s.getId(), qty, admin.getId());
            alert(Alert.AlertType.INFORMATION, "Produit créé", "Produit #" + pid + " créé et lié à la demande.");
            refresh();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavDashboard() throws IOException {
        MainApp.showDashboard(0);
    }

    @FXML
    public void onNavUsers() throws IOException {
        MainApp.showAdminUsers();
    }

    @FXML
    public void onNavProducts() throws IOException {
        MainApp.showAdminProducts();
    }

    @FXML
    public void onNavStocks() throws IOException {
        MainApp.showAdminStocks();
    }

    @FXML
    public void onNavAdminHome() throws IOException {
        MainApp.showHome();
    }

    @FXML
    public void onOpenMyProfile() {
        try {
            MainApp.openAdminMyProfile(topbarAvatarHost, userNameLabel, userEmailLabel);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
