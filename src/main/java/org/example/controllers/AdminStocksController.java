package org.example.controllers;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;
import org.example.MainApp;
import org.example.models.Stock;
import org.example.services.StockService;
import org.example.ui.product.ProductFormUi;
import org.example.ui.product.StockFormValidation;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AdminNotificationBellHelper;
import org.example.utils.AppState;
import org.example.utils.UiResources;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des stocks — même flux que le projet ppp ({@code buildStockPage}, ajout, édition, suppression).
 */
public class AdminStocksController {

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
    private StackPane stocksHost;
    @FXML
    private ImageView sidebarLogoView;

    private final StockService stockService = new StockService();
    private final ObservableList<Stock> rows = FXCollections.observableArrayList();
    private String stocksSearchText = "";
    private TextField innerSearchField;
    private boolean suppressSearchSync;

    private FlowPane stocksFlow;
    private ScrollPane stocksScroll;
    private Stock selectedStock;

    private static final String STOCK_CARD_BASE =
        "-fx-background-color: #ffffff; -fx-background-radius: 14; -fx-border-radius: 14; "
            + "-fx-border-color: #e5e0d8; -fx-border-width: 1; "
            + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 14, 0, 0, 3);";
    private static final String STOCK_CARD_SELECTED =
        "-fx-background-color: #f0fdf4; -fx-background-radius: 14; -fx-border-radius: 14; "
            + "-fx-border-color: #90a67f; -fx-border-width: 2; "
            + "-fx-effect: dropshadow(gaussian, rgba(144,166,127,0.35), 16, 0, 0, 4);";

    @FXML
    public void initialize() {
        UiResources.applySidebarLogo(sidebarLogoView);
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        AdminNotificationBellHelper.attach(notifBellLabel);

        if (topSearchField != null) {
            topSearchField.setText(stocksSearchText);
            topSearchField.textProperty().addListener((obs, prev, cur) -> {
                if (suppressSearchSync) {
                    return;
                }
                stocksSearchText = cur == null ? "" : cur;
                suppressSearchSync = true;
                if (innerSearchField != null) {
                    innerSearchField.setText(stocksSearchText);
                }
                suppressSearchSync = false;
                loadStocks();
            });
        }

        showMainStockPage();
        loadStocks();
    }

    private void resetStockSearchFilter() {
        stocksSearchText = "";
        suppressSearchSync = true;
        if (topSearchField != null) {
            topSearchField.clear();
        }
        if (innerSearchField != null) {
            innerSearchField.clear();
        }
        suppressSearchSync = false;
    }

    /**
     * Liste principale : titre, aide, recherche, boutons, tableau (comme ppp).
     */
    private void showMainStockPage() {
        selectedStock = null;
        stocksFlow = new FlowPane(16, 16);
        stocksFlow.setStyle("-fx-padding: 8 4 16 4;");
        stocksScroll = new ScrollPane(stocksFlow);
        stocksScroll.setFitToWidth(true);
        stocksScroll.setStyle("-fx-background: #faf9f7; -fx-background-color: #faf9f7;");
        stocksScroll.setMinViewportHeight(420);
        stocksFlow.prefWrapLengthProperty().bind(
            Bindings.max(260, stocksScroll.widthProperty().subtract(48))
        );

        Button addBtn = new Button("Nouveau stock");
        addBtn.setStyle("-fx-background-color: #8fb2d9; -fx-text-fill: white; -fx-background-radius: 8;");
        addBtn.setOnAction(e -> showAddStockPage());

        Button modifyBtn = new Button("Modifier");
        modifyBtn.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-background-radius: 8;");
        modifyBtn.setOnAction(e -> {
            if (selectedStock == null) {
                alert(Alert.AlertType.WARNING, "Sélection", "Cliquez sur une carte pour sélectionner un stock à modifier.");
                return;
            }
            showEditStockPage(selectedStock);
        });

        Button deleteBtn = new Button("Supprimer");
        deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-background-radius: 8;");
        deleteBtn.setOnAction(e -> {
            if (selectedStock == null) {
                alert(Alert.AlertType.WARNING, "Sélection", "Cliquez sur une carte pour sélectionner un stock à supprimer.");
                return;
            }
            Stock selected = selectedStock;
            try {
                int linked = stockService.countProduitsByStockId(selected.getId());
                if (linked > 0) {
                    alert(
                        Alert.AlertType.WARNING,
                        "Suppression impossible",
                        "Impossible de supprimer ce stock : " + linked
                            + " produit(s) du catalogue y sont encore associés (stock_id). Modifiez d'abord ces fiches produit."
                    );
                    return;
                }
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Erreur", ex.getMessage());
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmation");
            confirm.setHeaderText("Supprimer le stock");
            confirm.setContentText("Supprimer définitivement « " + selected.getNom() + " » ?");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
            try {
                stockService.deleteById(selected.getId());
                loadStocks();
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Suppression impossible", ex.getMessage());
            }
        });

        Label pageTitle = new Label("Gestion des stocks");
        pageTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2a2a2a;");

        Label desc = new Label(
            "Emplacements de stockage : chaque carte résume le nom, la quantité disponible et la référence. "
                + "Cliquez sur une carte pour la sélectionner, puis Modifier ou Supprimer."
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #6b7280;");

        HBox searchRow = buildStockSearchRow();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, modifyBtn, deleteBtn, spacer, addBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(12, pageTitle, desc, searchRow, actions, stocksScroll);
        page.setPadding(new Insets(18));
        page.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #eee7df; -fx-border-radius: 12;");
        VBox.setVgrow(stocksScroll, Priority.ALWAYS);
        page.setMaxWidth(Double.MAX_VALUE);

        stocksHost.getChildren().setAll(page);
        rebuildStockCards();
    }

    private void rebuildStockCards() {
        if (stocksFlow == null) {
            return;
        }
        stocksFlow.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("Aucun stock");
            empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px;");
            stocksFlow.getChildren().add(empty);
            return;
        }
        for (Stock s : rows) {
            stocksFlow.getChildren().add(buildStockCard(s));
        }
        refreshStockCardStyles();
    }

    private VBox buildStockCard(Stock stock) {
        final int stockId = stock.getId();
        final Stock captured = stock;

        VBox card = new VBox(0);
        card.setUserData(stockId);
        card.setMinWidth(252);
        card.setMaxWidth(272);
        card.setCursor(Cursor.HAND);

        StackPane header = new StackPane();
        header.setMinHeight(96);
        header.setMaxHeight(96);
        header.setStyle(
            "-fx-background-color: linear-gradient(135deg, #90a67f 0%, #b5c4a8 100%); "
                + "-fx-background-radius: 12 12 0 0;"
        );
        Label glyph = new Label("📦");
        glyph.setStyle("-fx-font-size: 40px;");
        header.getChildren().add(glyph);

        VBox body = new VBox(8);
        body.setPadding(new Insets(12, 14, 14, 14));

        String nom = stock.getNom() != null ? stock.getNom() : "—";
        Label nameLabel = new Label(nom);
        nameLabel.setWrapText(true);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label refLbl = new Label("Réf. #" + stockId);
        refLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        Label qtyVal = new Label(String.valueOf(stock.getQuantite()));
        qtyVal.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #166534;");
        Label qtyUnit = new Label("unités");
        qtyUnit.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        HBox qtyRow = new HBox(8, qtyVal, qtyUnit);
        qtyRow.setAlignment(Pos.BASELINE_LEFT);

        Label hint = new Label("Réserve non allouée aux fiches produit du catalogue.");
        hint.setWrapText(true);
        hint.setMaxHeight(44);
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        body.getChildren().addAll(nameLabel, refLbl, qtyRow, hint);
        card.getChildren().addAll(header, body);

        card.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            selectedStock = captured;
            refreshStockCardStyles();
            e.consume();
        });

        return card;
    }

    private void refreshStockCardStyles() {
        if (stocksFlow == null) {
            return;
        }
        for (javafx.scene.Node n : stocksFlow.getChildren()) {
            if (!(n instanceof VBox v)) {
                continue;
            }
            Object u = v.getUserData();
            if (!(u instanceof Integer id)) {
                continue;
            }
            boolean sel = selectedStock != null && selectedStock.getId() == id;
            v.setStyle(sel ? STOCK_CARD_SELECTED : STOCK_CARD_BASE);
        }
    }

    private HBox buildStockSearchRow() {
        innerSearchField = new TextField();
        innerSearchField.setPromptText("Rechercher un stock (id, nom ou quantité)");
        innerSearchField.setText(stocksSearchText);
        innerSearchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(innerSearchField, Priority.ALWAYS);
        innerSearchField.setStyle(
            "-fx-background-color: #fafafa; -fx-background-radius: 8; -fx-border-color: #e8dfd5; "
                + "-fx-border-radius: 8; -fx-padding: 8 14 8 14;"
        );
        innerSearchField.textProperty().addListener((obs, previous, current) -> {
            if (suppressSearchSync) {
                return;
            }
            stocksSearchText = current == null ? "" : current;
            suppressSearchSync = true;
            if (topSearchField != null) {
                topSearchField.setText(stocksSearchText);
            }
            suppressSearchSync = false;
            loadStocks();
        });

        Label icon = new Label("🔍");
        Label searchLabel = new Label("Recherche");
        searchLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 13px; -fx-font-weight: 500;");

        HBox row = new HBox(10, icon, searchLabel, innerSearchField);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private void loadStocks() {
        try {
            String q = stocksSearchText == null ? "" : stocksSearchText.trim();
            List<Stock> list = q.isEmpty() ? stockService.findAll() : stockService.search(q);
            rows.setAll(list);
            if (selectedStock != null && rows.stream().noneMatch(x -> x.getId() == selectedStock.getId())) {
                selectedStock = null;
            }
            rebuildStockCards();
        } catch (Exception e) {
            rows.clear();
            selectedStock = null;
            rebuildStockCards();
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : "Impossible de charger la liste des stocks.";
            alert(Alert.AlertType.ERROR, "Chargement stocks", msg);
        }
    }

    private void showAddStockPage() {
        TextField nomField = new TextField();
        nomField.setPromptText("Ex: Entrepôt central (max. 100 caractères)");
        ProductFormUi.styleInputAddProduct(nomField);

        Spinner<Integer> quantiteSpinner = new Spinner<>();
        quantiteSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, StockFormValidation.QTY_MAX, 0)
        );
        quantiteSpinner.setEditable(true);
        quantiteSpinner.setPrefWidth(140);
        quantiteSpinner.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 6; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-font-size: 15px;"
        );

        Button saveBtn = new Button("Enregistrer");
        saveBtn.setStyle("-fx-background-color: #8fb2d9; -fx-text-fill: white; -fx-background-radius: 8;");
        Button cancelBtn = new Button("Annuler");
        cancelBtn.setStyle("-fx-background-color: #efefef; -fx-text-fill: #374151; -fx-background-radius: 8;");

        saveBtn.setOnAction(e -> {
            String err = StockFormValidation.validateNom(nomField.getText());
            if (err != null) {
                alert(Alert.AlertType.WARNING, "Saisie", err);
                return;
            }
            int qVal;
            try {
                qVal = StockFormValidation.parseQuantityFromEditableSpinner(quantiteSpinner);
            } catch (NumberFormatException ex) {
                alert(Alert.AlertType.WARNING, "Saisie", "Indiquez une quantité entière valide.");
                return;
            }
            err = StockFormValidation.validateQuantiteStrictementPositive(qVal);
            if (err != null) {
                alert(Alert.AlertType.WARNING, "Saisie", err);
                return;
            }
            try {
                stockService.insert(nomField.getText().trim(), qVal);
                resetStockSearchFilter();
                Platform.runLater(() -> {
                    showMainStockPage();
                    loadStocks();
                    alert(
                        Alert.AlertType.INFORMATION,
                        "Stock créé",
                        "Stock créé avec succès. Il apparaît dans le tableau ci-dessous."
                    );
                });
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Enregistrement impossible", ex.getMessage());
            }
        });
        cancelBtn.setOnAction(e -> showMainStockPage());

        Label nomLabel = new Label("Nom");
        nomLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #374151;");
        Label quantiteLabel = new Label("Quantité");
        quantiteLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #374151;");

        VBox form = new VBox(8);
        form.getChildren().addAll(nomLabel, nomField, quantiteLabel, quantiteSpinner);

        Label title = new Label("Nouveau stock");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2a2a2a;");
        Label back = new Label("< Retour à la liste des stocks");
        back.setStyle("-fx-text-fill: #6b7280;");
        back.setOnMouseClicked(e -> showMainStockPage());

        HBox actions = new HBox(10, saveBtn, cancelBtn);
        VBox page = new VBox(14, back, title, form, actions);
        page.setPadding(new Insets(18));
        page.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #eee7df; -fx-border-radius: 12;");
        page.setMaxWidth(560);

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: white;");
        stocksHost.getChildren().setAll(scroll);
    }

    private void showEditStockPage(Stock stock) {
        TextField nomField = new TextField(stock.getNom());
        nomField.setPromptText("Nom du stock");
        ProductFormUi.styleInputAddProduct(nomField);

        Spinner<Integer> quantiteSpinner = new Spinner<>();
        quantiteSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, StockFormValidation.QTY_MAX, Math.max(0, stock.getQuantite()))
        );
        quantiteSpinner.setEditable(true);
        quantiteSpinner.setPrefWidth(140);
        quantiteSpinner.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 6; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-font-size: 15px;"
        );

        Button saveBtn = new Button("Enregistrer les modifications");
        saveBtn.setStyle("-fx-background-color: #8fb2d9; -fx-text-fill: white; -fx-background-radius: 8;");
        Button cancelBtn = new Button("Annuler");
        cancelBtn.setStyle("-fx-background-color: #efefef; -fx-text-fill: #374151; -fx-background-radius: 8;");

        saveBtn.setOnAction(e -> {
            String err = StockFormValidation.validateNom(nomField.getText());
            if (err != null) {
                alert(Alert.AlertType.WARNING, "Saisie", err);
                return;
            }
            int qVal;
            try {
                qVal = StockFormValidation.parseQuantityFromEditableSpinner(quantiteSpinner);
            } catch (NumberFormatException ex) {
                alert(Alert.AlertType.WARNING, "Saisie", "Indiquez une quantité entière valide.");
                return;
            }
            err = StockFormValidation.validateQuantiteStrictementPositive(qVal);
            if (err != null) {
                alert(Alert.AlertType.WARNING, "Saisie", err);
                return;
            }
            try {
                stockService.update(stock.getId(), nomField.getText().trim(), qVal);
                resetStockSearchFilter();
                Platform.runLater(() -> {
                    showMainStockPage();
                    loadStocks();
                    alert(
                        Alert.AlertType.INFORMATION,
                        "Stock mis à jour",
                        "Les modifications sont enregistrées et visibles dans le tableau."
                    );
                });
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Modification impossible", ex.getMessage());
            }
        });
        cancelBtn.setOnAction(e -> showMainStockPage());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.add(new Label("ID"), 0, 0);
        form.add(new Label(String.valueOf(stock.getId())), 1, 0);
        form.add(new Label("Nom du stock *"), 0, 1);
        form.add(nomField, 1, 1);
        form.add(new Label("Quantité *"), 0, 2);
        form.add(quantiteSpinner, 1, 2);

        Label title = new Label("Modifier le stock");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2a2a2a;");
        Label back = new Label("< Retour à la liste des stocks");
        back.setStyle("-fx-text-fill: #6b7280;");
        back.setOnMouseClicked(e -> showMainStockPage());

        HBox actions = new HBox(10, saveBtn, cancelBtn);
        VBox page = new VBox(14, back, title, form, actions);
        page.setPadding(new Insets(18));
        page.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #eee7df; -fx-border-radius: 12;");
        page.setMaxWidth(560);

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: white;");
        stocksHost.getChildren().setAll(scroll);
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

    /** Rester sur cet écran ou revenir à la liste (comme rafraîchissement). */
    @FXML
    public void onNavStocks() {
        showMainStockPage();
        loadStocks();
    }

    @FXML
    public void onNavOrders() {
        try {
            MainApp.showAdminOrders();
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
}



