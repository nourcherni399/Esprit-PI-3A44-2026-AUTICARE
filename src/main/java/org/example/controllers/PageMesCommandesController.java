package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.models.CustomerOrder;
import org.example.models.CustomerOrderLine;
import org.example.models.User;
import org.example.services.CustomerOrderService;
import org.example.services.OrderReceiptPdfService;
import org.example.ui.product.ProductFormUi;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.utils.AppState;
import org.example.utils.ProductImageLoader;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Liste des commandes du compte connecté (parent / patient / acheteur) : uniquement ses propres commandes
 * ({@code commande.user_id}), avec lignes détaillées (photo produit, nom, prix).
 */
public class PageMesCommandesController implements PublicShellAware {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int THUMB = 72;

    private PublicShellController shell;
    private final CustomerOrderService orderService = new CustomerOrderService();

    @FXML
    private VBox guestBox;

    @FXML
    private ScrollPane ordersScroll;

    @FXML
    private VBox ordersVBox;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
        loadOrders();
    }

    @FXML
    private void initialize() {
        /* setPublicShell appelle loadOrders */
    }

    private void loadOrders() {
        User user = AppState.getCurrentUser();
        if (user == null) {
            guestBox.setVisible(true);
            guestBox.setManaged(true);
            ordersScroll.setVisible(false);
            ordersScroll.setManaged(false);
            return;
        }
        guestBox.setVisible(false);
        guestBox.setManaged(false);
        ordersScroll.setVisible(true);
        ordersScroll.setManaged(true);
        ordersVBox.getChildren().clear();
        try {
            List<CustomerOrder> list = orderService.findAllForUser(user.getId());
            if (list.isEmpty()) {
                Label empty = new Label("Vous n'avez pas encore passé de commande.");
                empty.getStyleClass().add("public-product-form-hint");
                ordersVBox.getChildren().add(empty);
                return;
            }
            for (CustomerOrder o : list) {
                ordersVBox.getChildren().add(buildOrderCard(user.getId(), o));
            }
        } catch (SQLException e) {
            Label err = new Label("Impossible de charger les commandes : " + e.getMessage());
            err.setWrapText(true);
            err.getStyleClass().add("public-product-form-hint");
            ordersVBox.getChildren().add(err);
        }
    }

    private VBox buildOrderCard(int userId, CustomerOrder o) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("public-product-order-card");

        String dateStr = o.dateCreation() != null ? DT.format(o.dateCreation()) : "—";
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);

        Label num = new Label("Commande n° " + o.id());
        num.getStyleClass().add("public-product-order-num");

        Label date = new Label(dateStr);
        date.getStyleClass().add("public-product-order-meta");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label total = new Label(String.format(Locale.FRENCH, "Total : %,.2f DT", o.total()));
        total.getStyleClass().add("public-product-order-total");

        Label statut = new Label(labelStatut(o.statut()));
        statut.getStyleClass().add("public-product-order-badge");

        top.getChildren().addAll(num, date, sp, total, statut);

        Separator sep = new Separator();

        Label articlesTitle = ProductFormUi.formLabel("Articles commandés");

        VBox linesBox = new VBox(10);
        linesBox.setPadding(new Insets(4, 0, 0, 0));
        try {
            List<CustomerOrderLine> lines = orderService.findLinesForUserOrder(o.id(), userId);
            if (lines.isEmpty()) {
                linesBox.getChildren().add(new Label("Aucun détail d’article."));
            } else {
                for (CustomerOrderLine line : lines) {
                    linesBox.getChildren().add(buildLineRow(line));
                }
            }
        } catch (SQLException e) {
            linesBox.getChildren().add(new Label("Détail indisponible : " + e.getMessage()));
        }

        Button pdfBtn = new Button("Télécharger le bon / facture (PDF)");
        pdfBtn.getStyleClass().add("public-product-form-primary-btn");
        pdfBtn.setOnAction(ev -> downloadPdf(userId, o.id()));

        card.getChildren().addAll(top, sep, articlesTitle, linesBox, pdfBtn);
        return card;
    }

    private HBox buildLineRow(CustomerOrderLine line) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("public-product-order-line");

        StackPane thumbHost = new StackPane();
        thumbHost.setMinSize(THUMB, THUMB);
        thumbHost.setMaxSize(THUMB, THUMB);
        thumbHost.getStyleClass().add("public-product-order-thumb");

        Image img = ProductImageLoader.loadForDisplay(line.imagePath(), THUMB, THUMB);
        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(THUMB);
            iv.setFitHeight(THUMB);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setMouseTransparent(true);
            thumbHost.getChildren().add(iv);
        } else {
            thumbHost.getChildren().add(ProductImagePlaceholder.create(THUMB, THUMB));
        }

        String nom = line.produitNom() != null ? line.produitNom() : "Produit";
        Label nameLbl = new Label(nom);
        nameLbl.setWrapText(true);
        nameLbl.getStyleClass().add("public-product-line-name");
        nameLbl.setMaxWidth(420);

        Label prixUnit = new Label(String.format(Locale.FRENCH, "%.2f DT", line.prix()));
        prixUnit.getStyleClass().add("public-product-line-price");

        Label qtyLbl = new Label("× " + line.quantite());
        qtyLbl.getStyleClass().add("public-product-line-qty");

        HBox prixRow = new HBox(8, prixUnit, qtyLbl);
        prixRow.setAlignment(Pos.CENTER_LEFT);

        Label sous = new Label(String.format(Locale.FRENCH, "Sous-total : %.2f DT", line.sousTotal()));
        sous.getStyleClass().add("public-product-line-sub");

        VBox mid = new VBox(6, nameLbl, prixRow, sous);
        VBox.setVgrow(nameLbl, Priority.NEVER);
        HBox.setHgrow(mid, Priority.ALWAYS);

        row.getChildren().addAll(thumbHost, mid);
        return row;
    }

    private static String labelStatut(String raw) {
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
            default -> raw;
        };
    }

    private void downloadPdf(int userId, int commandeId) {
        try {
            var opt = orderService.findByIdForUser(commandeId, userId);
            if (opt.isEmpty()) {
                alert(Alert.AlertType.WARNING, "Commande introuvable.");
                return;
            }
            var lines = orderService.findLinesForUserOrder(commandeId, userId);
            Window owner = ordersVBox.getScene() != null ? ordersVBox.getScene().getWindow() : null;
            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer le bon de livraison");
            fc.setInitialFileName(OrderReceiptPdfService.defaultFileName(commandeId));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            java.io.File dest = fc.showSaveDialog(owner);
            if (dest == null) {
                return;
            }
            OrderReceiptPdfService.writePdfToFile(opt.get(), lines, dest.toPath());
            alert(Alert.AlertType.INFORMATION, "PDF enregistré :\n" + dest.getAbsolutePath());
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, ex.getMessage() != null ? ex.getMessage() : "Erreur PDF.");
        }
    }

    @FXML
    private void onGoLogin() {
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("login");
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    private void alert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
