package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
import org.example.services.UserService;
import org.example.utils.AdminNotificationBellHelper;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.UiResources;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Administration des demandes produit : fiches structurées par carte, actions et notifications client.
 */
public class AdminDemandeProduitController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private VBox cardsBox;
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
    private final UserService userService = new UserService();

    @FXML
    private void initialize() {
        UiResources.applySidebarLogo(sidebarLogoView);
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        AdminNotificationBellHelper.attach(notifBellLabel);

        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        if (cardsBox == null) {
            return;
        }
        cardsBox.getChildren().clear();
        try {
            List<DemandeProduit> list = demandeService.findAll();
            if (list.isEmpty()) {
                Label empty = new Label("Aucune demande pour le moment.");
                empty.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");
                cardsBox.getChildren().add(empty);
                return;
            }
            for (DemandeProduit d : list) {
                cardsBox.getChildren().add(buildDemandeCard(d));
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Base", e.getMessage());
        }
    }

    private VBox buildDemandeCard(DemandeProduit d) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 12; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 12, 0, 0, 2);"
        );

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        String demandeTitre = d.createdAt() != null
            ? "Demande du " + FMT.format(d.createdAt())
            : "Demande produit";
        Label idLbl = new Label(demandeTitre);
        idLbl.setStyle("-fx-font-weight: 800; -fx-font-size: 15px; -fx-text-fill: #0f172a;");
        Label statutBadge = new Label(formatStatutLabel(d.statut()));
        statutBadge.setStyle(statutBadgeStyle(d.statut()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(idLbl, spacer, statutBadge);

        Label titre = new Label(d.nom() == null || d.nom().isBlank() ? "(Sans nom)" : d.nom());
        titre.setWrapText(true);
        titre.setStyle("-fx-font-size: 17px; -fx-font-weight: 700; -fx-text-fill: #1e293b;");

        GridPane meta = new GridPane();
        meta.setHgap(16);
        meta.setVgap(6);
        int r = 0;
        meta.add(detailLabel("Catégorie"), 0, r);
        meta.add(detailValue(d.categorie() == null ? "—" : d.categorie()), 1, r++);
        meta.add(detailLabel("Prix estimé"), 0, r);
        meta.add(detailValue(String.format(Locale.FRENCH, "%.2f DT", d.prixEstime())), 1, r++);
        if (d.budgetClient() != null) {
            meta.add(detailLabel("Budget client"), 0, r);
            meta.add(detailValue(String.format(Locale.FRENCH, "%.2f DT", d.budgetClient())), 1, r++);
        }
        meta.add(detailLabel("Créée le"), 0, r);
        meta.add(detailValue(d.createdAt() == null ? "—" : FMT.format(d.createdAt())), 1, r++);
        meta.add(detailLabel("Demandeur"), 0, r);
        meta.add(detailValue(resolveDemandeurLabel(d)), 1, r++);
        if (d.produitId() != null && d.produitId() > 0) {
            meta.add(detailLabel("Produit catalogue"), 0, r);
            meta.add(detailValue("Oui — publié dans le catalogue Produits"), 1, r++);
        }

        Label blocDemandeT = new Label("Message / demande du client");
        blocDemandeT.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        TextArea demandeTxt = readOnlyArea(d.demandeClient(), 3);

        Label blocDescT = new Label("Description proposée");
        blocDescT.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        TextArea descTxt = readOnlyArea(d.description(), 5);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        User admin = AppState.getCurrentUser();

        if (DemandeProduitService.STATUT_EN_ATTENTE.equals(d.statut()) && admin != null) {
            Button accept = styledPrimary("Accepter la demande");
            accept.setOnAction(e -> runIfAdmin(admin, () -> approveDemande(d)));
            Button refuse = styledSecondary("Refuser");
            refuse.setOnAction(e -> runIfAdmin(admin, () -> rejectDemande(d)));
            actions.getChildren().addAll(accept, refuse);
        } else if (DemandeProduitService.STATUT_APPROUVE.equals(d.statut()) && admin != null
            && (d.produitId() == null || d.produitId() <= 0)) {
            Button create = styledPrimary("Créer le produit dans le catalogue");
            create.setOnAction(e -> runIfAdmin(admin, () -> openCreateProductDialog(d)));
            actions.getChildren().add(create);
        } else if (DemandeProduitService.STATUT_APPROUVE.equals(d.statut()) && d.produitId() != null && d.produitId() > 0) {
            Label done = new Label("Produit déjà créé et publié dans le catalogue.");
            done.setStyle("-fx-text-fill: #15803d; -fx-font-weight: 600;");
            actions.getChildren().add(done);
        } else if (DemandeProduitService.STATUT_REJETE.equals(d.statut())) {
            Label rej = new Label("Demande refusée — aucune action possible.");
            rej.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: 600;");
            actions.getChildren().add(rej);
        }

        card.getChildren().addAll(header, titre, meta, blocDemandeT, demandeTxt, blocDescT, descTxt, actions);
        return card;
    }

    private static Label detailLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        return l;
    }

    private static Label detailValue(String t) {
        Label l = new Label(t);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: 600;");
        return l;
    }

    private String resolveDemandeurLabel(DemandeProduit d) {
        if (d.demandeurId() == null) {
            return "Non connecté / anonyme";
        }
        try {
            return userService.findById(d.demandeurId())
                .map(u -> u.getEmail() != null && !u.getEmail().isBlank() ? u.getEmail() : "Compte membre")
                .orElse("Compte membre");
        } catch (SQLException e) {
            return "Compte membre";
        }
    }

    private static TextArea readOnlyArea(String text, int rows) {
        TextArea a = new TextArea(text == null ? "" : text);
        a.setEditable(false);
        a.setWrapText(true);
        a.setPrefRowCount(rows);
        a.setMaxHeight(rows * 22 + 24);
        a.setStyle(
            "-fx-control-inner-background: #f8fafc; -fx-font-size: 12.5px; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 8;"
        );
        return a;
    }

    private static String formatStatutLabel(String statut) {
        if (statut == null) {
            return "—";
        }
        return switch (statut) {
            case DemandeProduitService.STATUT_EN_ATTENTE -> "En attente";
            case DemandeProduitService.STATUT_APPROUVE -> "Acceptée";
            case DemandeProduitService.STATUT_REJETE -> "Refusée";
            default -> statut;
        };
    }

    private static String statutBadgeStyle(String statut) {
        String base = "-fx-background-radius: 999; -fx-padding: 4 12; -fx-font-weight: 700; -fx-font-size: 11px;";
        if (DemandeProduitService.STATUT_EN_ATTENTE.equals(statut)) {
            return base + "-fx-background-color: #fef3c7; -fx-text-fill: #92400e;";
        }
        if (DemandeProduitService.STATUT_APPROUVE.equals(statut)) {
            return base + "-fx-background-color: #dcfce7; -fx-text-fill: #166534;";
        }
        if (DemandeProduitService.STATUT_REJETE.equals(statut)) {
            return base + "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
        }
        return base + "-fx-background-color: #e2e8f0; -fx-text-fill: #334155;";
    }

    private static Button styledPrimary(String text) {
        Button b = new Button(text);
        b.setMnemonicParsing(false);
        b.setStyle(
            "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 700; "
                + "-fx-background-radius: 8; -fx-padding: 8 14;"
        );
        return b;
    }

    private static Button styledSecondary(String text) {
        Button b = new Button(text);
        b.setMnemonicParsing(false);
        b.setStyle(
            "-fx-background-color: #f1f5f9; -fx-text-fill: #b91c1c; -fx-font-weight: 700; "
                + "-fx-background-radius: 8; -fx-padding: 8 14; -fx-border-color: #fecaca; -fx-border-radius: 8;"
        );
        return b;
    }

    private void runIfAdmin(User admin, Runnable action) {
        if (admin == null) {
            return;
        }
        action.run();
    }

    private void approveDemande(DemandeProduit d) {
        User admin = AppState.getCurrentUser();
        if (admin == null) {
            return;
        }
        try {
            demandeService.approuver(d.id(), admin.getId());
            alert(Alert.AlertType.INFORMATION, "Demande acceptée", "La demande a été acceptée. Le client a été notifié.");
            refresh();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void rejectDemande(DemandeProduit d) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Refuser la demande");
        confirm.setHeaderText(null);
        confirm.setContentText("Refuser cette demande ? Le client recevra une notification.");
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        User admin = AppState.getCurrentUser();
        if (admin == null) {
            return;
        }

        try {
            demandeService.rejeter(d.id(), admin.getId());
            alert(Alert.AlertType.INFORMATION, "Demande refusée", "Le client a été notifié du refus.");
            refresh();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void openCreateProductDialog(DemandeProduit d) {
        if (d == null) {
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
        dialog.setHeaderText((d.nom() == null || d.nom().isBlank()) ? "Créer le produit" : d.nom());
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
        g.addRow(0, new Label("Emplacement stock"), stockCombo);
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
            demandeService.creerProduitDepuisDemande(d.id(), s.getId(), qty, admin.getId());
            alert(
                Alert.AlertType.INFORMATION,
                "Produit créé",
                "La fiche produit a été créée et liée à cette demande. Le client a été notifié."
            );
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
