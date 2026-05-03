package org.example.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.models.DemandeProduit;
import org.example.models.Role;
import org.example.models.Stock;
import org.example.models.User;
import org.example.services.CustomerOrderService;
import org.example.services.DemandeProduitService;
import org.example.services.NotificationService;
import org.example.services.StockService;
import org.example.ui.product.ProductFormUi;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Cloche admin : nouvelles commandes + demandes produit (approuver / rejeter).
 */
public final class AdminNotificationBellHelper {

    private static final NotificationService NOTIFICATION_SERVICE = new NotificationService();
    private static final DemandeProduitService DEMANDE_SERVICE = new DemandeProduitService();
    private static final CustomerOrderService ORDER_SERVICE = new CustomerOrderService();
    private static final StockService STOCK_SERVICE = new StockService();

    private AdminNotificationBellHelper() {
    }

    public static void attach(Label bellLabel) {
        if (bellLabel == null) {
            return;
        }
        bellLabel.getStyleClass().add("admin-bell-label");
        refreshCount(bellLabel);
        bellLabel.setOnMouseClicked(e -> showUnreadAndMarkRead(bellLabel));
    }

    public static void refreshCount(Label bellLabel) {
        if (bellLabel == null) {
            return;
        }
        User current = AppState.getCurrentUser();
        if (current == null || current.getRole() != Role.ADMIN) {
            bellLabel.setText("🔔");
            return;
        }
        try {
            int nOrders = NOTIFICATION_SERVICE.findUnreadOrderNotificationsForAdmin(current.getId()).size();
            int nDemandes = NOTIFICATION_SERVICE.findUnreadDemandeNotificationsForAdmin(current.getId()).size();
            int count = nOrders + nDemandes;
            bellLabel.setText(count > 0 ? "🔔 " + count : "🔔");
        } catch (Exception ex) {
            bellLabel.setText("🔔");
        }
    }

    private static void showUnreadAndMarkRead(Label bellLabel) {
        User current = AppState.getCurrentUser();
        if (current == null || current.getRole() != Role.ADMIN) {
            return;
        }
        try {
            List<NotificationService.OrderNotificationView> orderNotifs =
                NOTIFICATION_SERVICE.findUnreadOrderNotificationsForAdmin(current.getId());
            List<NotificationService.DemandeNotificationView> demandeNotifs =
                NOTIFICATION_SERVICE.findUnreadDemandeNotificationsForAdmin(current.getId());

            if (orderNotifs.isEmpty() && demandeNotifs.isEmpty()) {
                Alert empty = new Alert(Alert.AlertType.INFORMATION);
                empty.setTitle("Notifications");
                empty.setHeaderText(null);
                empty.setContentText("Aucune nouvelle notification.");
                empty.showAndWait();
                refreshCount(bellLabel);
                return;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            List<Integer> orderIds = new ArrayList<>();
            for (NotificationService.OrderNotificationView n : orderNotifs) {
                orderIds.add(n.notificationId());
            }
            List<Integer> demandeNotifIds = new ArrayList<>();
            for (NotificationService.DemandeNotificationView n : demandeNotifs) {
                demandeNotifIds.add(n.notificationId());
            }

            Set<Integer> readAlready = new HashSet<>();

            VBox root = new VBox(14);
            root.setStyle("-fx-padding: 4;");

            if (!demandeNotifs.isEmpty()) {
                Label secDem = new Label("Demandes produit");
                secDem.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #111827;");
                root.getChildren().add(secDem);
                Window ownerWin = bellLabel.getScene() != null ? bellLabel.getScene().getWindow() : null;
                for (NotificationService.DemandeNotificationView n : demandeNotifs) {
                    root.getChildren().add(buildDemandeSummaryCard(n, current, fmt, readAlready, bellLabel, ownerWin));
                }
            }

            if (!orderNotifs.isEmpty()) {
                Label secOrd = new Label("Nouvelles commandes");
                secOrd.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #111827;");
                root.getChildren().add(secOrd);
                for (NotificationService.OrderNotificationView n : orderNotifs) {
                    root.getChildren().add(buildOrderCard(n, fmt, readAlready, bellLabel));
                }
            }

            ScrollPane scroll = new ScrollPane(root);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(360);
            scroll.setStyle("-fx-background-color: transparent;");

            Label summary = new Label(
                "Total : " + (demandeNotifs.size() + orderNotifs.size())
                    + " (demandes : " + demandeNotifs.size() + ", commandes : " + orderNotifs.size() + ")"
            );
            summary.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

            VBox content = new VBox(10, summary, scroll);
            content.setStyle("-fx-padding: 6 0 0 0;");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Notifications");
            alert.setHeaderText("Notifications non lues");
            alert.getButtonTypes().setAll(
                new ButtonType("Fermer et marquer tout comme lu", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL
            );
            alert.getDialogPane().setContent(content);
            alert.getDialogPane().setPrefWidth(620);

            ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
            if (choice.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                List<Integer> toMark = new ArrayList<>();
                for (Integer id : orderIds) {
                    if (!readAlready.contains(id)) {
                        toMark.add(id);
                    }
                }
                for (Integer id : demandeNotifIds) {
                    if (!readAlready.contains(id)) {
                        toMark.add(id);
                    }
                }
                NOTIFICATION_SERVICE.markNotificationsAsRead(toMark);
            }
            refreshCount(bellLabel);
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Notifications");
            err.setHeaderText(null);
            err.setContentText("Impossible de charger les notifications : " + ex.getMessage());
            err.showAndWait();
        }
    }

    /**
     * Liste courte : l’admin voit seulement qu’une demande a été soumise ; le détail (aperçu fiche + actions) s’ouvre au clic.
     */
    private static VBox buildDemandeSummaryCard(
        NotificationService.DemandeNotificationView n,
        User admin,
        DateTimeFormatter fmt,
        Set<Integer> readAlready,
        Label bellLabel,
        Window owner
    ) {
        VBox card = new VBox(10);
        card.setStyle(
            "-fx-background-color: #f0fdf4; -fx-background-radius: 10; -fx-border-radius: 10; "
                + "-fx-border-color: #bbf7d0; -fx-border-width: 1; -fx-padding: 12 14 12 14;"
        );

        HBox rowTop = new HBox(8);
        rowTop.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Demande de création de produit");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #14532d;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String date = n.createdAt() != null ? n.createdAt().format(fmt) : "";
        Label dateLine = new Label(date.isBlank() ? "" : date);
        dateLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");
        rowTop.getChildren().addAll(title, spacer, dateLine);

        Label hint = new Label("Un membre a soumis une demande pour validation.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");

        Button expand = new Button("Agrandir — voir la fiche et approuver ou rejeter");
        expand.setMnemonicParsing(false);
        expand.setMaxWidth(Double.MAX_VALUE);
        expand.setStyle(
            "-fx-background-color: #15803d; -fx-text-fill: white; -fx-font-weight: 700; "
                + "-fx-background-radius: 8; -fx-padding: 8 14;"
        );
        expand.setOnAction(ev -> openDemandeDetailStage(n, admin, owner, readAlready, bellLabel, card));

        card.getChildren().addAll(rowTop, hint, expand);
        return card;
    }

    private static void openDemandeDetailStage(
        NotificationService.DemandeNotificationView n,
        User admin,
        Window owner,
        Set<Integer> readAlready,
        Label bellLabel,
        VBox summaryCard
    ) {
        try {
            Optional<DemandeProduit> opt = DEMANDE_SERVICE.findById(n.demandeId());
            if (opt.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setHeaderText(null);
                a.setContentText("Cette demande n’est plus disponible.");
                a.showAndWait();
                return;
            }
            DemandeProduit d = opt.get();
            Stage st = new Stage();
            if (owner != null) {
                st.initOwner(owner);
            }
            st.initModality(Modality.WINDOW_MODAL);
            st.setTitle("Demande de création de produit");

            VBox body = buildDemandeApprovalFormPane(d, n, admin, () -> {
                readAlready.add(n.notificationId());
                if (summaryCard != null) {
                    summaryCard.setVisible(false);
                    summaryCard.setManaged(false);
                }
                refreshCount(bellLabel);
                st.close();
            });

            ScrollPane sp = new ScrollPane(body);
            sp.setFitToWidth(true);
            sp.setStyle("-fx-background-color: #fafafa;");
            Scene sc = new Scene(sp, 680, 700);
            st.setScene(sc);
            st.show();
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText(null);
            a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Impossible d’ouvrir le détail.");
            a.showAndWait();
        }
    }

    private static VBox buildDemandeApprovalFormPane(
        DemandeProduit d,
        NotificationService.DemandeNotificationView n,
        User admin,
        Runnable afterApproveOrReject
    ) {
        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        root.setMaxWidth(640);
        root.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 14; "
                + "-fx-border-color: #e5e7eb; -fx-border-radius: 14; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 14, 0, 0, 3);"
        );

        StackPane imgHost = new StackPane();
        imgHost.setMinHeight(200);
        imgHost.setMaxHeight(220);
        imgHost.setStyle("-fx-background-color: #f3f4f6; -fx-background-radius: 10;");
        String imgUrl = extractPreviewImageUrl(d.donneesExternesJson());
        if (imgUrl != null && !imgUrl.isBlank()) {
            ImageView iv = new ImageView();
            iv.setPreserveRatio(true);
            iv.setFitWidth(600);
            iv.setFitHeight(200);
            Image img = new Image(imgUrl, true);
            Label errLbl = new Label("Image indisponible");
            errLbl.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13px;");
            errLbl.setVisible(false);
            errLbl.managedProperty().bind(errLbl.visibleProperty());
            img.errorProperty().addListener((obs, wasErr, isErr) -> {
                if (Boolean.TRUE.equals(isErr)) {
                    iv.setImage(null);
                    errLbl.setVisible(true);
                }
            });
            img.progressProperty().addListener((obs, o, p) -> {
                if (p != null && p.doubleValue() >= 1.0 && img.isError()) {
                    iv.setImage(null);
                    errLbl.setVisible(true);
                }
            });
            iv.setImage(img);
            imgHost.getChildren().addAll(iv, errLbl);
            StackPane.setAlignment(iv, Pos.CENTER);
            StackPane.setAlignment(errLbl, Pos.CENTER);
        } else {
            Label ph = new Label("Aucune image fournie pour cette demande");
            ph.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13px;");
            ph.setWrapText(true);
            imgHost.getChildren().add(ph);
        }

        String nom = d.nom() != null && !d.nom().isBlank() ? d.nom() : "Sans titre";
        Label nameLbl = new Label(nom);
        nameLbl.setWrapText(true);
        nameLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", d.prixEstime()));
        priceLbl.setStyle("-fx-font-size: 17px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");

        String rawDesc = d.description() == null ? "" : d.description().trim();
        String shortDesc = rawDesc.length() > 220 ? rawDesc.substring(0, 217) + "…" : rawDesc;
        Label descLbl = new Label(shortDesc.isBlank() ? "—" : shortDesc);
        descLbl.setWrapText(true);
        descLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #4b5563; -fx-line-spacing: 2px;");

        String catLabel = ProductFormUi.resolveCategoryChoice(d.categorie()).getLabel();
        Label catLbl = new Label("Catégorie : " + catLabel);
        catLbl.setWrapText(true);
        catLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        Label stockLbl = new Label("Stock catalogue : à définir lors de la création de la fiche");
        stockLbl.setWrapText(true);
        stockLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a;");

        TextField nomField = new TextField(nom);
        TextArea descField = new TextArea(rawDesc);
        descField.setWrapText(true);
        descField.setPrefRowCount(5);
        ComboBox<ProductFormUi.ProductCategoryChoice> catCombo = new ComboBox<>();
        catCombo.getItems().setAll(ProductFormUi.getProductCategories());
        catCombo.setValue(ProductFormUi.resolveCategoryChoice(d.categorie()));
        TextField prixField = new TextField(String.format(Locale.US, "%.2f", d.prixEstime()));
        TextField imageField = new TextField(imgUrl == null ? "" : imgUrl);
        ComboBox<Stock> stockCombo = new ComboBox<>();
        stockCombo.getItems().setAll(loadStocks());
        if (!stockCombo.getItems().isEmpty()) {
            stockCombo.getSelectionModel().selectFirst();
        }
        Spinner<Integer> qtySpin = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99999, 1));
        qtySpin.setEditable(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, formLabel("Nom"), nomField);
        form.addRow(1, formLabel("Description"), descField);
        form.addRow(2, formLabel("Catégorie"), catCombo);
        form.addRow(3, formLabel("Prix (DT)"), prixField);
        form.addRow(4, formLabel("Photo (URL/chemin)"), imageField);
        form.addRow(5, formLabel("Stock"), stockCombo);
        form.addRow(6, formLabel("Quantité"), qtySpin);
        GridPane.setHgrow(nomField, Priority.ALWAYS);
        GridPane.setHgrow(descField, Priority.ALWAYS);
        GridPane.setHgrow(catCombo, Priority.ALWAYS);
        GridPane.setHgrow(prixField, Priority.ALWAYS);
        GridPane.setHgrow(imageField, Priority.ALWAYS);
        GridPane.setHgrow(stockCombo, Priority.ALWAYS);

        boolean pending = DemandeProduitService.STATUT_EN_ATTENTE.equals(d.statut());
        Label statutLbl = new Label("Statut : " + formatDemandeStatut(d.statut()));
        statutLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        String req = d.demandeClient() == null ? "" : d.demandeClient().trim();
        Label reqLbl = null;
        if (!req.isBlank()) {
            String reqShort = req.length() > 160 ? req.substring(0, 157) + "…" : req;
            reqLbl = new Label("Message associé : " + reqShort);
            reqLbl.setWrapText(true);
            reqLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button approve = new Button("Approuver");
        approve.setMnemonicParsing(false);
        approve.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 18;");
        Button reject = new Button("Rejeter");
        reject.setMnemonicParsing(false);
        reject.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 18;");
        approve.setDisable(!pending);
        reject.setDisable(!pending);

        Runnable markReadAndRefresh = () -> {
            try {
                NOTIFICATION_SERVICE.markNotificationsAsRead(List.of(n.notificationId()));
            } catch (Exception ignored) {
            }
            afterApproveOrReject.run();
        };

        approve.setOnAction(ev -> {
            try {
                if (stockCombo.getValue() == null) {
                    Alert a = new Alert(Alert.AlertType.WARNING);
                    a.setHeaderText(null);
                    a.setContentText("Choisissez un emplacement stock.");
                    a.showAndWait();
                    return;
                }
                qtySpin.commitValue();
                int qty = qtySpin.getValue() == null ? 1 : qtySpin.getValue();
                String nomFinal = nomField.getText() == null ? "" : nomField.getText().trim();
                String descFinal = descField.getText() == null ? "" : descField.getText().trim();
                ProductFormUi.ProductCategoryChoice cat = catCombo.getValue();
                String catDb = cat != null ? cat.getDbValue() : d.categorie();
                String img = imageField.getText() == null ? "" : imageField.getText().trim();
                String prixTxt = prixField.getText() == null ? "" : prixField.getText().trim().replace(",", ".");
                double prix = Double.parseDouble(prixTxt);
                if (prix <= 0) {
                    throw new IllegalArgumentException("Prix invalide.");
                }

                DEMANDE_SERVICE.approuver(n.demandeId(), admin.getId());
                DEMANDE_SERVICE.creerProduitDepuisDemandeAvecFormulaire(
                    n.demandeId(),
                    stockCombo.getValue().getId(),
                    qty,
                    admin.getId(),
                    nomFinal,
                    descFinal,
                    catDb,
                    prix,
                    img
                );
                markReadAndRefresh.run();
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });
        reject.setOnAction(ev -> {
            try {
                DEMANDE_SERVICE.rejeter(n.demandeId(), admin.getId());
                markReadAndRefresh.run();
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });

        actions.getChildren().addAll(approve, reject);
        root.getChildren().addAll(imgHost, nameLbl, priceLbl, descLbl, form);
        if (reqLbl != null) {
            root.getChildren().add(reqLbl);
        }
        root.getChildren().addAll(catLbl, stockLbl, statutLbl, actions);
        return root;
    }

    private static List<Stock> loadStocks() {
        try {
            return STOCK_SERVICE.findAll();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static Label formLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: 600;");
        return l;
    }

    private static String formatDemandeStatut(String statut) {
        if (statut == null) {
            return "—";
        }
        return switch (statut) {
            case DemandeProduitService.STATUT_EN_ATTENTE -> "en attente de validation";
            case DemandeProduitService.STATUT_APPROUVE -> "acceptée";
            case DemandeProduitService.STATUT_REJETE -> "refusée";
            default -> statut;
        };
    }

    private static String extractPreviewImageUrl(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(json.trim()).getAsJsonObject();
            String[] keys = {"imageUrl", "image_url", "photoUrl", "photo_url", "url", "image", "previewUrl", "preview_url"};
            for (String k : keys) {
                if (o.has(k) && o.get(k).isJsonPrimitive()) {
                    String s = o.get(k).getAsString();
                    if (s != null && !s.isBlank()) {
                        return s.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static VBox buildOrderCard(
        NotificationService.OrderNotificationView n,
        DateTimeFormatter fmt,
        Set<Integer> readAlready,
        Label bellLabel
    ) {
        String client = n.clientNom() != null ? n.clientNom() : "Client";
        String date = n.createdAt() != null ? n.createdAt().format(fmt) : "";
        VBox card = new VBox(6);
        card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-radius: 10; "
                + "-fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-padding: 10 12 10 12;"
        );

        HBox rowTop = new HBox(8);
        Label title = new Label("Nouvelle commande");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #111827;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amount = new Label(String.format(Locale.FRENCH, "%.2f DT", n.total()));
        amount.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #1f2937;");
        rowTop.getChildren().addAll(title, spacer, amount);

        Label clientLine = new Label("Client : " + client);
        clientLine.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        Label emailLine = new Label("E-mail : " + (n.email() != null && !n.email().isBlank() ? n.email() : "—"));
        emailLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
        Label telLine = new Label("Tél. : " + (n.telephone() != null && !n.telephone().isBlank() ? n.telephone() : "—"));
        telLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
        String adrTxt = buildAdresseUneLigne(n.adresse(), n.codePostal(), n.ville());
        Label adrLine = new Label("Adresse livraison : " + adrTxt);
        adrLine.setWrapText(true);
        adrLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
        Label payLine = new Label(
            "Paiement : " + (n.modePayment() != null && !n.modePayment().isBlank() ? n.modePayment() : "—"));
        payLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
        Label dateLine = new Label(date.isBlank() ? "" : "Date : " + date);
        dateLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");
        Label stLine = new Label("Statut actuel : " + (n.statutCommande() != null ? n.statutCommande() : "—"));
        stLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        VBox linesBox = new VBox(4);
        linesBox.setStyle("-fx-padding: 4 0 0 0;");
        boolean pending = CustomerOrderService.isEnAttenteCommande(n.statutCommande());
        Label detailTitle = new Label(
            pending
                ? "Articles commandés (le stock sera débité après votre validation) :"
                : "Articles commandés :"
        );
        detailTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #374151;");
        linesBox.getChildren().add(detailTitle);
        try {
            for (NotificationService.OrderLineDetailRow r : NOTIFICATION_SERVICE.findOrderLineDetails(n.commandeId())) {
                String empl = "";
                if (r.stockLocationName() != null && !r.stockLocationName().isBlank()) {
                    empl = " | Empl. « " + r.stockLocationName() + " »";
                }
                String one = String.format(
                    Locale.FRENCH,
                    "• %s  ×%d  %,.2f DT  (reste à vendre : %d)%s",
                    r.productName() != null ? r.productName() : "—",
                    r.quantityOrdered(),
                    r.lineTotal(),
                    r.remainingSellableQuantity(),
                    empl
                );
                Label li = new Label(one);
                li.setWrapText(true);
                li.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
                linesBox.getChildren().add(li);
            }
        } catch (Exception ignored) {
            Label err = new Label("(Détail des lignes indisponible.)");
            err.setStyle("-fx-font-size: 11px; -fx-text-fill: #9ca3af;");
            linesBox.getChildren().add(err);
        }

        boolean canApprove = CustomerOrderService.isEnAttenteCommande(n.statutCommande());
        boolean canCancel = CustomerOrderService.isCommandeCancellableByAdmin(n.statutCommande());
        HBox ordActions = new HBox(10);
        ordActions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Button approveOrd = new Button("Approuver");
        approveOrd.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 14;");
        Button cancelOrd = new Button("Annuler la commande");
        cancelOrd.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 14;");
        approveOrd.setDisable(!canApprove);
        cancelOrd.setDisable(!canCancel);
        approveOrd.setOnAction(ev -> {
            try {
                ORDER_SERVICE.advanceOrderStatut(false, n.commandeId());
                NOTIFICATION_SERVICE.markNotificationsAsRead(List.of(n.notificationId()));
                readAlready.add(n.notificationId());
                card.setVisible(false);
                card.setManaged(false);
                refreshCount(bellLabel);
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });
        cancelOrd.setOnAction(ev -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Annuler cette commande ?");
            confirm.setContentText("Le client sera notifié. Cette action est définitive pour cette commande.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
            try {
                ORDER_SERVICE.annulerCommandeParAdmin(n.commandeId());
                NOTIFICATION_SERVICE.markNotificationsAsRead(List.of(n.notificationId()));
                readAlready.add(n.notificationId());
                card.setVisible(false);
                card.setManaged(false);
                refreshCount(bellLabel);
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });
        ordActions.getChildren().addAll(approveOrd, cancelOrd);

        card.getChildren().addAll(rowTop, clientLine, emailLine, telLine, adrLine, payLine, dateLine, stLine, linesBox, ordActions);
        return card;
    }

    private static String buildAdresseUneLigne(String adresse, String cp, String ville) {
        String a = adresse != null ? adresse.trim() : "";
        String c = cp != null ? cp.trim() : "";
        String v = ville != null ? ville.trim() : "";
        String cv = (c + " " + v).trim();
        StringBuilder sb = new StringBuilder();
        if (!a.isBlank()) {
            sb.append(a);
        }
        if (!cv.isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(cv);
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(0, max - 1)).trim() + "…";
    }
}
