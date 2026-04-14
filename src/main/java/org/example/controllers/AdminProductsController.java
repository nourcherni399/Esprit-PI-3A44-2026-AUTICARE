package org.example.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.application.Platform;
import javafx.fxml.FXML;
import org.example.MainApp;
import org.example.models.Product;
import org.example.models.Stock;
import org.example.services.ProductExcelExportService;
import org.example.services.ProductService;
import org.example.services.StockService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AdminNotificationBellHelper;
import org.example.utils.AppState;
import org.example.stats.ProductStatsCalculator;
import org.example.stats.ProductStatsCalculator.ProductStatsResult;
import org.example.ui.product.ProductEditorPane;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.ui.product.ProductFormValidation;
import org.example.ui.product.ProductStatsWindow;
import org.example.utils.UiResources;
import org.example.utils.ProductImageLoader;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Écran « Nos Produits » (liste, recherche, tri) aligné sur le projet ppp.
 */
public class AdminProductsController {

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
    private StackPane productsHost;
    @FXML
    private ImageView sidebarLogoView;

    /** Ancienne vue tableau (conservée pour réutilisation éventuelle). */
    private TableView<Product> productTable;
    private Label summaryTotalLabel;
    private Label summaryDispoLabel;
    private Label summaryIndispoLabel;
    private boolean useTableMainView = true;

    private final ProductService productService = new ProductService();
    private final StockService stockService = new StockService();
    private final ObservableList<Product> rows = FXCollections.observableArrayList();
    private String productsSearchText = "";
    private TextField innerSearchField;
    /** Évite une boucle entre la recherche topbar et la zone « Nos Produits ». */
    private boolean suppressSearchSync;
    /** Grille de cartes produits (remplace l’ancien tableau). */
    private FlowPane productsFlow;
    /** Sélection par id produit (Ctrl+clic = multi sur l’écran principal). */
    private final Set<Integer> selectedProductIds = new LinkedHashSet<>();
    /** Liste complète : sélection unique sans Ctrl. */
    private boolean productCardsSingleSelectOnly;
    /** Vue « Afficher produits » : cartes et images plus grandes pour l’aperçu. */
    private boolean productCardsLargeMode;
    /** Carte cliquée : affichage agrandi (un seul à la fois). */
    private Integer expandedProductId;

    @FXML
    public void initialize() {
        UiResources.applySidebarLogo(sidebarLogoView);
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);
        AdminNotificationBellHelper.attach(notifBellLabel);

        if (topSearchField != null) {
            topSearchField.setText(productsSearchText);
            topSearchField.textProperty().addListener((obs, prev, cur) -> {
                if (suppressSearchSync) {
                    return;
                }
                productsSearchText = cur == null ? "" : cur;
                suppressSearchSync = true;
                if (innerSearchField != null) {
                    innerSearchField.setText(productsSearchText);
                }
                suppressSearchSync = false;
                loadProducts();
            });
        }

        showMainProductsPage();
    }

    private void resetProductSearchFilter() {
        productsSearchText = "";
        suppressSearchSync = true;
        if (topSearchField != null) {
            topSearchField.clear();
        }
        if (innerSearchField != null) {
            innerSearchField.clear();
        }
        suppressSearchSync = false;
    }

    private void showMainProductsPage() {
        useTableMainView = false;
        productTable = null;
        productCardsSingleSelectOnly = false;
        productCardsLargeMode = false;
        expandedProductId = null;
        selectedProductIds.clear();

        Label pageTitle = new Label("Gestion des Produits");
        pageTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2a2a2a;");
        Label pageSubtitle = new Label("Consultez et gérez le catalogue produits");
        pageSubtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280;");

        HBox searchRow = buildSearchRow();
        Button searchBtn = new Button("Rechercher");
        searchBtn.setMnemonicParsing(false);
        searchBtn.setStyle(
            "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 14px; -fx-padding: 8 18 8 18;"
        );
        searchBtn.setOnAction(e -> loadProducts());
        HBox searchBar = new HBox(12, searchRow, searchBtn);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        HBox sortRow = buildSortRow();

        Button statsBtn = new Button("Stats");
        statsBtn.setMnemonicParsing(false);
        applySymfonyStatsOutlineButton(statsBtn);
        statsBtn.setOnAction(e -> showStats());

        Button exportExcelBtn = new Button("Exporter Excel");
        exportExcelBtn.setMnemonicParsing(false);
        applySymfonyExportExcelButton(exportExcelBtn);
        exportExcelBtn.setOnAction(e -> exportProductsExcel());

        Button afficherBtn = new Button("Afficher");
        afficherBtn.setMnemonicParsing(false);
        afficherBtn.setStyle(
            "-fx-background-color: #f8fafc; -fx-text-fill: #334155; -fx-background-radius: 8; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-font-size: 13px; -fx-padding: 8 14 8 14;"
        );
        afficherBtn.setOnAction(e -> afficherSelectedProduct());

        Button editSelectedBtn = new Button("Modifier produit");
        editSelectedBtn.setMnemonicParsing(false);
        editSelectedBtn.setStyle(
            "-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 13px; -fx-padding: 8 14 8 14;"
        );
        editSelectedBtn.setOnAction(e -> editSelectedProductFromTable());

        Button deleteSelectedBtn = new Button("Supprimer produit");
        deleteSelectedBtn.setMnemonicParsing(false);
        deleteSelectedBtn.setStyle(
            "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 13px; -fx-padding: 8 14 8 14;"
        );
        deleteSelectedBtn.setOnAction(e -> confirmDeleteSelectedFromTable());

        Button addBtn = new Button("+ Nouveau produit");
        addBtn.setMnemonicParsing(false);
        addBtn.setStyle(
            "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 14px; -fx-font-weight: 600; -fx-padding: 10 20 10 20;"
        );
        addBtn.setOnAction(e -> showProductEditor(null));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(
            10,
            statsBtn,
            exportExcelBtn,
            afficherBtn,
            editSelectedBtn,
            deleteSelectedBtn,
            spacer,
            addBtn
        );
        actions.setAlignment(Pos.CENTER_LEFT);

        ScrollPane cardsScroll = buildProductCardsScroll();
        if (productsFlow != null) {
            productsFlow.prefWrapLengthProperty().bind(
                Bindings.max(280, cardsScroll.widthProperty().subtract(48))
            );
        }
        HBox summaryRow = buildSummaryRow();

        VBox page = new VBox(
            14,
            pageTitle,
            pageSubtitle,
            searchBar,
            sortRow,
            actions,
            cardsScroll,
            summaryRow
        );
        page.setPadding(new Insets(18));
        page.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #eee7df; -fx-border-radius: 12;");
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
        page.setMaxWidth(Double.MAX_VALUE);

        productsHost.getChildren().setAll(page);
        loadProducts();
    }

    private HBox buildSummaryRow() {
        summaryTotalLabel = new Label("0");
        summaryDispoLabel = new Label("0");
        summaryIndispoLabel = new Label("0");
        for (Label l : List.of(summaryTotalLabel, summaryDispoLabel, summaryIndispoLabel)) {
            l.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        }
        VBox cardTotal = summaryCard("Total des produits", summaryTotalLabel, "#e5e7eb");
        VBox cardDispo = summaryCard("Disponibles", summaryDispoLabel, "#86efac");
        VBox cardIndispo = summaryCard("Indisponibles", summaryIndispoLabel, "#fcd34d");
        HBox row = new HBox(16, cardTotal, cardDispo, cardIndispo);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static VBox summaryCard(String title, Label valueLabel, String accentColor) {
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        VBox box = new VBox(6, t, valueLabel);
        box.setPadding(new Insets(14, 18, 14, 18));
        box.setStyle(
            "-fx-background-color: #fafafa; -fx-background-radius: 10; -fx-border-radius: 10; "
                + "-fx-border-color: " + accentColor + "; -fx-border-width: 0 0 3 0;"
        );
        box.setMinWidth(200);
        return box;
    }

    private TableView<Product> buildProductsTable() {
        TableView<Product> table = new TableView<>();
        table.setPlaceholder(new Label("Aucun produit."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: white; -fx-background-radius: 8;");

        TableColumn<Product, Product> colProduit = new TableColumn<>("PRODUIT");
        colProduit.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colProduit.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                ImageView thumb = new ImageView();
                thumb.setFitWidth(44);
                thumb.setFitHeight(44);
                thumb.setPreserveRatio(true);
                thumb.setSmooth(true);
                String path = p.getImagePath();
                if (path != null && !path.isBlank()) {
                    Image im = ProductImageLoader.loadForDisplay(path, 44, 44);
                    if (im != null && !im.isError()) {
                        thumb.setImage(im);
                    }
                }
                Label name = new Label(p.getNom() != null ? p.getNom() : "—");
                name.setWrapText(true);
                name.setStyle("-fx-font-weight: 600; -fx-text-fill: #111827;");
                HBox row = new HBox(10, thumb, name);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        TableColumn<Product, String> colCat = new TableColumn<>("CATÉGORIE");
        colCat.setCellValueFactory(c ->
            new SimpleStringProperty(ProductStatsCalculator.categoryLabel(c.getValue().getCategorie())));

        TableColumn<Product, String> colPrix = new TableColumn<>("PRIX");
        colPrix.setCellValueFactory(c ->
            new SimpleStringProperty(
                String.format(Locale.FRENCH, "%.2f DT", c.getValue().getPrix())));

        TableColumn<Product, String> colStatut = new TableColumn<>("STATUT");
        colStatut.setCellValueFactory(c -> new SimpleStringProperty(formatStatutProduct(c.getValue())));

        TableColumn<Product, Product> colActions = new TableColumn<>("ACTIONS");
        colActions.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                Button viewB = new Button("👁");
                viewB.setMnemonicParsing(false);
                viewB.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                viewB.setOnAction(e -> showProductsCatalogWindow(Set.of(p.getId())));
                Button editB = new Button("✎");
                editB.setMnemonicParsing(false);
                editB.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                editB.setOnAction(e -> showProductEditor(p));
                Button delB = new Button("🗑");
                delB.setMnemonicParsing(false);
                delB.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                delB.setOnAction(e -> confirmDeleteProduct(p));
                HBox row = new HBox(6, viewB, editB, delB);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        table.getColumns().addAll(colProduit, colCat, colPrix, colStatut, colActions);
        return table;
    }

    private static String formatStatutProduct(Product p) {
        if (!p.isDisponible()) {
            return "Indisponible";
        }
        if (p.isPublie()) {
            return "Publié";
        }
        return "Brouillon";
    }

    /**
     * Fenêtre modale : grille de cartes produits (même présentation que la liste principale).
     *
     * @param highlightIds si non vide, surligne ces produits (sinon utilise la sélection courante des cartes).
     */
    private void showProductsCatalogWindow(Set<Integer> highlightIds) {
        Window owner = productsHost != null && productsHost.getScene() != null
            ? productsHost.getScene().getWindow()
            : null;
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Aperçu des produits");

        LinkedHashSet<Integer> highlight = new LinkedHashSet<>();
        if (highlightIds != null && !highlightIds.isEmpty()) {
            highlight.addAll(highlightIds);
        } else {
            highlight.addAll(selectedProductIds);
        }

        Map<Integer, String> stockNames = new HashMap<>();
        try {
            for (Stock s : stockService.findAll()) {
                stockNames.put(s.getId(), s.getNom());
            }
        } catch (SQLException ignored) {
            // —
        }

        Label header = new Label("Les produits du catalogue (liste actuelle)");
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        Label sub = new Label(
            highlight.isEmpty()
                ? "Cartes en lecture seule — même mise en page que la gestion des produits."
                : "La sélection est surlignée en bleu."
        );
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        FlowPane flow = new FlowPane(18, 18);
        flow.setPadding(new Insets(8, 4, 16, 4));
        flow.setStyle("-fx-background-color: #faf9f7;");
        if (rows.isEmpty()) {
            Label empty = new Label("Aucun produit à afficher.");
            empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px;");
            flow.getChildren().add(empty);
        } else {
            for (Product p : rows) {
                boolean sel = highlight.contains(p.getId());
                flow.getChildren().add(buildProductPreviewCard(p, stockNames, sel));
            }
        }

        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #faf9f7; -fx-background-color: #faf9f7;");
        scroll.setMinViewportHeight(420);
        flow.prefWrapLengthProperty().bind(Bindings.max(280, scroll.widthProperty().subtract(48)));

        Button closeBtn = new Button("Fermer");
        closeBtn.setDefaultButton(true);
        closeBtn.setOnAction(e -> stage.close());
        closeBtn.setStyle(
            "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 14px; -fx-padding: 8 22 8 22;"
        );
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 0, 0, 0));

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");
        root.getChildren().addAll(header, sub, scroll, footer);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 960, 640);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Fenêtre modale : photo grande taille + infos complètes (clic sur une carte produit).
     */
    private void showProductDetailWindow(Product p, Map<Integer, String> stockNames) {
        Window owner = productsHost != null && productsHost.getScene() != null
            ? productsHost.getScene().getWindow()
            : null;
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(p.getNom() != null && !p.getNom().isBlank() ? p.getNom() : "Fiche produit");

        final int imgMaxW = 780;
        final int imgMaxH = 520;

        StackPane imgHost = new StackPane();
        imgHost.setMaxWidth(imgMaxW);
        imgHost.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8fafc, #f1f5f9); "
                + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #e2e8f0; -fx-border-width: 1;"
        );

        String path = p.getImagePath();
        Image img = path != null && !path.isBlank() ? ProductImageLoader.loadForDisplay(path, imgMaxW, imgMaxH) : null;
        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(imgMaxW);
            iv.setFitHeight(imgMaxH);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            imgHost.getChildren().add(iv);
        } else {
            imgHost.getChildren().add(ProductImagePlaceholder.create(imgMaxW, imgMaxH));
        }

        Label nameLbl = new Label(p.getNom() != null ? p.getNom() : "—");
        nameLbl.setWrapText(true);
        nameLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
        priceLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");

        String catLabel = ProductStatsCalculator.categoryLabel(p.getCategorie());
        Label catLbl = new Label("Catégorie : " + catLabel);
        catLbl.setWrapText(true);
        catLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");

        String descFull = p.getDescription() == null || p.getDescription().isBlank() ? "—" : p.getDescription();
        Label descLbl = new Label(descFull);
        descLbl.setWrapText(true);
        descLbl.setMaxWidth(imgMaxW);
        descLbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #374151; -fx-line-spacing: 4px;");

        String stockNom = stockNames.get(p.getStockId());
        String stockLine = stockNom != null && !stockNom.isBlank()
            ? "Stock : " + stockNom
            : "Stock : #" + p.getStockId();
        Label stockLbl = new Label(stockLine);
        stockLbl.setWrapText(true);
        stockLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a;");

        Label statutLbl = new Label("Statut : " + formatStatutProduct(p));
        statutLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        VBox content = new VBox(14, imgHost, nameLbl, priceLbl, catLbl, descLbl, stockLbl, statutLbl);
        content.setPadding(new Insets(8, 0, 0, 0));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");

        Button closeBtn = new Button("Fermer");
        closeBtn.setDefaultButton(true);
        closeBtn.setOnAction(e -> stage.close());
        closeBtn.setStyle(
            "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 14px; -fx-padding: 8 22 8 22;"
        );
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 0, 0, 0));

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");
        root.getChildren().addAll(scroll, footer);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 860, 720);
        stage.setScene(scene);
        stage.show();
    }

    /** Carte lecture seule (même structure que la grille admin : image, nom, prix, description, stock). */
    private VBox buildProductPreviewCard(Product p, Map<Integer, String> stockNames, boolean selected) {
        final int cardW = 280;
        final int imgW = 280;
        final int imgH = 168;
        final int descMaxLen = 130;
        final double descMaxHeight = 56;
        final int vgap = 8;

        VBox card = new VBox(vgap);
        card.setMinWidth(cardW);
        card.setMaxWidth(cardW);
        card.setPadding(new Insets(12));
        card.setStyle(selected ? PRODUCT_CARD_STYLE_SELECTED : PRODUCT_CARD_STYLE_BASE);

        StackPane imgFrame = new StackPane();
        imgFrame.setMinSize(imgW, imgH);
        imgFrame.setMaxSize(imgW, imgH);
        imgFrame.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8fafc, #f1f5f9); "
                + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #e2e8f0; -fx-border-width: 1;"
        );

        String path = p.getImagePath();
        Image img = path != null && !path.isBlank() ? ProductImageLoader.loadForDisplay(path, imgW, imgH) : null;
        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(imgW);
            iv.setFitHeight(imgH);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setMouseTransparent(true);
            imgFrame.getChildren().add(iv);
        } else {
            imgFrame.getChildren().add(ProductImagePlaceholder.create(imgW, imgH));
        }

        String nom = p.getNom() != null ? p.getNom() : "—";
        Label nameLbl = new Label(nom);
        nameLbl.setWrapText(true);
        nameLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
        priceLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");

        String rawDesc = p.getDescription() == null || p.getDescription().isBlank() ? "" : p.getDescription();
        String descText = rawDesc.isEmpty()
            ? ""
            : (rawDesc.length() > descMaxLen ? rawDesc.substring(0, descMaxLen - 1) + "…" : rawDesc);
        Label descLbl = new Label(descText.isEmpty() ? " " : descText);
        descLbl.setWrapText(true);
        descLbl.setMaxHeight(descMaxHeight);
        descLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #64748b; -fx-line-spacing: 2px;");

        String stockNom = stockNames.get(p.getStockId());
        String stockLine = stockNom != null && !stockNom.isBlank()
            ? "Stock : " + stockNom
            : "Stock : #" + p.getStockId();
        Label stockLbl = new Label(stockLine);
        stockLbl.setWrapText(true);
        stockLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a;");

        card.getChildren().addAll(imgFrame, nameLbl, priceLbl, descLbl, stockLbl);
        return card;
    }

    private void confirmDeleteProduct(Product p) {
        if (p == null) {
            return;
        }
        try {
            productService.delete(p.getId());
            loadProducts();
        } catch (SQLException ignored) {
            // suppression silencieuse
        }
    }

    private Product getSelectedProductForToolbar() {
        if (useTableMainView && productTable != null) {
            return productTable.getSelectionModel().getSelectedItem();
        }
        return getSingleSelectedProduct();
    }

    private void afficherSelectedProduct() {
        showProductsCatalogWindow(null);
    }

    private void editSelectedProductFromTable() {
        Product p = getSelectedProductForToolbar();
        if (p == null) {
            alert(Alert.AlertType.WARNING, "Sélection", "Veuillez sélectionner un produit à modifier.");
            return;
        }
        showProductEditor(p);
    }

    private void confirmDeleteSelectedFromTable() {
        Product p = getSelectedProductForToolbar();
        if (p == null) {
            return;
        }
        try {
            productService.delete(p.getId());
            loadProducts();
        } catch (SQLException ignored) {
            // suppression silencieuse
        }
    }

    private void refreshProductViews() {
        if (useTableMainView && productTable != null) {
            productTable.setItems(rows);
        }
        updateSummaryCards();
        if (productsFlow != null) {
            rebuildProductCards();
        }
    }

    private void updateSummaryCards() {
        if (summaryTotalLabel == null || summaryDispoLabel == null || summaryIndispoLabel == null) {
            return;
        }
        ProductStatsResult r = ProductStatsCalculator.compute(new ArrayList<>(rows));
        summaryTotalLabel.setText(String.valueOf(r.totalProduits()));
        summaryDispoLabel.setText(String.valueOf(r.produitsDisponibles()));
        summaryIndispoLabel.setText(String.valueOf(r.produitsIndisponibles()));
    }

    private void showListPage() {
        useTableMainView = false;
        productTable = null;
        productCardsSingleSelectOnly = true;
        productCardsLargeMode = true;
        expandedProductId = null;
        selectedProductIds.clear();
        ScrollPane cardsScroll = buildProductCardsScroll();
        if (productsFlow != null) {
            productsFlow.prefWrapLengthProperty().bind(
                Bindings.max(280, cardsScroll.widthProperty().subtract(48))
            );
        }

        Label title = new Label("Liste complète des produits");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2a2a2a;");
        Label listHint = new Label(
            "Cartes agrandies. Cliquez sur une carte pour ouvrir la fiche en grand (Ctrl+clic : sélection multiple)."
        );
        listHint.setWrapText(true);
        listHint.setStyle("-fx-text-fill: #6b7280;");

        HBox searchRow = buildSearchRow();
        HBox sortRow = buildSortRow();

        Button backBtn = new Button("Retour");
        backBtn.setStyle("-fx-background-color: #efefef; -fx-text-fill: #374151; -fx-background-radius: 8;");
        backBtn.setOnAction(e -> showMainProductsPage());

        Button refreshBtn = new Button("Rafraichir");
        refreshBtn.setStyle("-fx-background-color: #90a67f; -fx-text-fill: white; -fx-background-radius: 8;");
        refreshBtn.setOnAction(e -> loadProducts());

        Button listStatsBtn = new Button("Statistiques");
        listStatsBtn.setMnemonicParsing(false);
        applySymfonyStatsOutlineButton(listStatsBtn);
        listStatsBtn.setOnAction(e -> showStats());

        Button listExportBtn = new Button("Exporter Excel");
        listExportBtn.setMnemonicParsing(false);
        applySymfonyExportExcelButton(listExportBtn);
        listExportBtn.setOnAction(e -> exportProductsExcel());

        HBox actions = new HBox(10, backBtn, refreshBtn, listStatsBtn, listExportBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(12, title, listHint, searchRow, sortRow, cardsScroll, actions);
        page.setPadding(new Insets(18));
        page.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #eee7df; -fx-border-radius: 12;");
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
        page.setMaxWidth(Double.MAX_VALUE);
        productsHost.getChildren().setAll(page);
        rebuildProductCards();
    }

    private HBox buildSearchRow() {
        innerSearchField = new TextField();
        innerSearchField.setPromptText(
            "Rechercher un produit (id, nom, description, catégorie, prix, quantité, stock…)"
        );
        innerSearchField.setText(productsSearchText);
        innerSearchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(innerSearchField, Priority.ALWAYS);
        innerSearchField.setStyle(
            "-fx-background-color: #fafafa; -fx-background-radius: 8; -fx-border-color: #e8dfd5; "
                + "-fx-border-radius: 8; -fx-padding: 8 14 8 14;"
        );
        innerSearchField.textProperty().addListener((obs, p, c) -> {
            if (suppressSearchSync) {
                return;
            }
            productsSearchText = c == null ? "" : c;
            suppressSearchSync = true;
            if (topSearchField != null) {
                topSearchField.setText(productsSearchText);
            }
            suppressSearchSync = false;
            loadProducts();
        });

        Label icon = new Label("🔍");
        Label searchLabel = new Label("Recherche");
        searchLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 13px; -fx-font-weight: 500;");

        HBox row = new HBox(10, icon, searchLabel, innerSearchField);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private HBox buildSortRow() {
        MenuButton trierPar = new MenuButton("Trier");
        trierPar.setMnemonicParsing(false);
        trierPar.setStyle(
            "-fx-background-color: #e8e4dc; -fx-text-fill: #374151; -fx-background-radius: 8; "
                + "-fx-font-size: 13px; -fx-font-weight: 500; -fx-padding: 8 16 8 16;"
        );

        MenuItem nomAz = new MenuItem("Nom (A → Z)");
        nomAz.setOnAction(e -> {
            FXCollections.sort(rows, Comparator.comparing((Product p) -> p.getNom() == null ? "" : p.getNom(), String.CASE_INSENSITIVE_ORDER));
            refreshProductViews();
        });
        MenuItem nomZa = new MenuItem("Nom (Z → A)");
        nomZa.setOnAction(e -> {
            FXCollections.sort(
                rows,
                Comparator.comparing((Product p) -> p.getNom() == null ? "" : p.getNom(), String.CASE_INSENSITIVE_ORDER).reversed()
            );
            refreshProductViews();
        });
        MenuItem prixC = new MenuItem("Prix croissant");
        prixC.setOnAction(e -> {
            FXCollections.sort(rows, Comparator.comparingDouble(Product::getPrix));
            refreshProductViews();
        });
        MenuItem prixD = new MenuItem("Prix décroissant");
        prixD.setOnAction(e -> {
            FXCollections.sort(rows, Comparator.comparingDouble(Product::getPrix).reversed());
            refreshProductViews();
        });

        trierPar.getItems().addAll(nomAz, nomZa, new SeparatorMenuItem(), prixC, prixD);
        HBox row = new HBox(8, trierPar);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 6, 0));
        return row;
    }

    private static final String PRODUCT_CARD_STYLE_BASE =
        "-fx-background-color: #ffffff; -fx-background-radius: 14; -fx-border-radius: 14; "
            + "-fx-border-color: #e5e0d8; -fx-border-width: 1; "
            + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 14, 0, 0, 3);";
    private static final String PRODUCT_CARD_STYLE_SELECTED =
        "-fx-background-color: #f0f7ff; -fx-background-radius: 14; -fx-border-radius: 14; "
            + "-fx-border-color: #3b82f6; -fx-border-width: 2; "
            + "-fx-effect: dropshadow(gaussian, rgba(59,130,246,0.28), 16, 0, 0, 4);";

    private ScrollPane buildProductCardsScroll() {
        int gap = productCardsLargeMode ? 22 : 18;
        productsFlow = new FlowPane(gap, gap);
        productsFlow.setPrefWrapLength(productCardsLargeMode ? 1280 : 1120);
        productsFlow.setStyle("-fx-padding: 8 4 16 4;");
        ScrollPane scroll = new ScrollPane(productsFlow);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #faf9f7; -fx-background-color: #faf9f7;");
        scroll.setMinViewportHeight(productCardsLargeMode ? 520 : 440);
        return scroll;
    }

    private void rebuildProductCards() {
        if (productsFlow == null) {
            return;
        }
        productsFlow.getChildren().clear();
        Map<Integer, String> stockNames = new HashMap<>();
        try {
            for (Stock s : stockService.findAll()) {
                stockNames.put(s.getId(), s.getNom());
            }
        } catch (SQLException ignored) {
            // —
        }
        if (rows.isEmpty()) {
            Label empty = new Label("Aucun produit chargé");
            empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px;");
            productsFlow.getChildren().add(empty);
            return;
        }
        for (Product p : rows) {
            productsFlow.getChildren().add(buildProductCard(p, stockNames, productCardsLargeMode));
        }
        refreshCardSelectionStyles();
    }

    private VBox buildProductCard(Product p, Map<Integer, String> stockNames, boolean listLarge) {
        boolean expanded = expandedProductId != null && expandedProductId == p.getId();

        int cardW;
        int imgW;
        int imgH;
        int descMaxLen;
        double descMaxHeight;
        int vgap;
        boolean bigFonts;

        if (expanded) {
            if (listLarge) {
                cardW = 520;
                imgW = 520;
                imgH = 360;
                descMaxLen = 420;
                descMaxHeight = 180;
            } else {
                cardW = 460;
                imgW = 460;
                imgH = 320;
                descMaxLen = 360;
                descMaxHeight = 150;
            }
            vgap = 14;
            bigFonts = true;
        } else if (listLarge) {
            cardW = 400;
            imgW = 400;
            imgH = 260;
            descMaxLen = 260;
            descMaxHeight = 112;
            vgap = 12;
            bigFonts = true;
        } else {
            /* Grille admin principale : cartes fixes, image → nom → prix → description. */
            cardW = 280;
            imgW = 280;
            imgH = 168;
            descMaxLen = 130;
            descMaxHeight = 56;
            vgap = 8;
            bigFonts = false;
        }

        VBox card = new VBox(vgap);
        card.setUserData(p.getId());
        card.setMinWidth(cardW);
        card.setMaxWidth(cardW);
        card.setPadding(new Insets(expanded ? 16 : (listLarge ? 14 : 12)));
        card.setCursor(Cursor.HAND);

        StackPane imgFrame = new StackPane();
        imgFrame.setMinSize(imgW, imgH);
        imgFrame.setMaxSize(imgW, imgH);
        imgFrame.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8fafc, #f1f5f9); "
                + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #e2e8f0; -fx-border-width: 1;"
        );

        String path = p.getImagePath();
        Image img = path != null && !path.isBlank() ? ProductImageLoader.loadForDisplay(path, imgW, imgH) : null;
        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(imgW);
            iv.setFitHeight(imgH);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            /* Les clics sur l’image remontent au parent (sinon l’ImageView capte et le VBox ne reçoit pas MOUSE_CLICKED). */
            iv.setMouseTransparent(true);
            imgFrame.getChildren().add(iv);
        } else {
            imgFrame.getChildren().add(ProductImagePlaceholder.create(imgW, imgH));
        }

        String nom = p.getNom() != null ? p.getNom() : "—";
        Label nameLbl = new Label(nom);
        nameLbl.setWrapText(true);
        nameLbl.setStyle(
            "-fx-font-size: "
                + (expanded ? "18px" : (bigFonts ? "17px" : "15px"))
                + "; -fx-font-weight: bold; -fx-text-fill: #111827;"
        );

        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
        priceLbl.setStyle(
            "-fx-font-size: "
                + (expanded ? "17px" : (bigFonts ? "16px" : "15px"))
                + "; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;"
        );

        String rawDesc = p.getDescription() == null || p.getDescription().isBlank() ? "" : p.getDescription();
        String descText = rawDesc.isEmpty()
            ? ""
            : (rawDesc.length() > descMaxLen ? rawDesc.substring(0, descMaxLen - 1) + "…" : rawDesc);
        Label descLbl = new Label(descText.isEmpty() ? " " : descText);
        descLbl.setWrapText(true);
        descLbl.setMaxHeight(descMaxHeight);
        descLbl.setStyle(
            "-fx-font-size: "
                + (expanded ? "15px" : (bigFonts ? "14px" : "12.5px"))
                + "; -fx-text-fill: #64748b; -fx-line-spacing: 2px;"
        );

        String stockNom = stockNames.get(p.getStockId());
        String stockLine = stockNom != null && !stockNom.isBlank()
            ? "Stock : " + stockNom
            : "Stock : #" + p.getStockId();
        Label stockLbl = new Label(stockLine);
        stockLbl.setWrapText(true);
        stockLbl.setStyle(
            "-fx-font-size: "
                + (expanded ? "14px" : (bigFonts ? "13px" : "12px"))
                + "; -fx-font-weight: 600; -fx-text-fill: #5c6d4a;"
        );

        if (expanded) {
            Label hint = new Label("Cliquez à nouveau pour réduire · Ctrl+clic : sélection multiple");
            hint.setWrapText(true);
            hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            hint.setMouseTransparent(true);
            card.getChildren().addAll(imgFrame, nameLbl, priceLbl, descLbl, stockLbl, hint);
        } else {
            card.getChildren().addAll(imgFrame, nameLbl, priceLbl, descLbl, stockLbl);
        }

        /* Filtre en phase de capture : reçoit le clic même si un enfant (ImageView, Label) le bloquait avant. */
        card.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            int id = p.getId();
            if (e.isControlDown()) {
                if (!selectedProductIds.add(id)) {
                    selectedProductIds.remove(id);
                }
                refreshCardSelectionStyles();
                e.consume();
                return;
            }
            expandedProductId = null;
            selectedProductIds.clear();
            selectedProductIds.add(id);
            refreshCardSelectionStyles();
            showProductDetailWindow(p, stockNames);
            e.consume();
        });

        return card;
    }

    private void refreshCardSelectionStyles() {
        if (productsFlow == null) {
            return;
        }
        for (javafx.scene.Node n : productsFlow.getChildren()) {
            if (!(n instanceof VBox v)) {
                continue;
            }
            Object u = v.getUserData();
            if (!(u instanceof Integer id)) {
                continue;
            }
            boolean sel = selectedProductIds.contains(id);
            boolean exp = expandedProductId != null && expandedProductId.equals(id);
            if (exp) {
                v.setStyle(
                    sel
                        ? PRODUCT_CARD_STYLE_SELECTED
                        : "-fx-background-color: #fafcff; -fx-background-radius: 14; -fx-border-radius: 14; "
                            + "-fx-border-color: #93c5fd; -fx-border-width: 2; "
                            + "-fx-effect: dropshadow(gaussian, rgba(59,130,246,0.18), 18, 0, 0, 4);"
                );
            } else {
                v.setStyle(sel ? PRODUCT_CARD_STYLE_SELECTED : PRODUCT_CARD_STYLE_BASE);
            }
        }
    }

    private Product getSingleSelectedProduct() {
        if (selectedProductIds.size() != 1) {
            return null;
        }
        int id = selectedProductIds.iterator().next();
        return rows.stream().filter(x -> x.getId() == id).findFirst().orElse(null);
    }

    private void deleteCardSelection() {
        List<Product> selected = rows.stream()
            .filter(p -> selectedProductIds.contains(p.getId()))
            .collect(Collectors.toList());
        if (selected.isEmpty()) {
            return;
        }
        try {
            for (Product p : selected) {
                productService.delete(p.getId());
            }
            selectedProductIds.clear();
            loadProducts();
        } catch (SQLException ignored) {
            // suppression silencieuse
        }
    }

    private void loadProducts() {
        try {
            String q = productsSearchText == null ? "" : productsSearchText.trim();
            List<Product> list = q.isEmpty() ? productService.findAll() : productService.search(q);
            rows.setAll(list);
            if (expandedProductId != null && rows.stream().noneMatch(x -> x.getId() == expandedProductId)) {
                expandedProductId = null;
            }
            refreshProductViews();
        } catch (SQLException e) {
            rows.clear();
            expandedProductId = null;
            refreshProductViews();
            alert(Alert.AlertType.ERROR, "Base de données", e.getMessage());
        }
    }

    private void exportProductsExcel() {
        try {
            List<Product> all = productService.findAll();
            Map<Integer, String> stockNames = new HashMap<>();
            for (Stock s : stockService.findAll()) {
                stockNames.put(s.getId(), s.getNom() != null ? s.getNom() : "");
            }
            Path tmp = Files.createTempFile("produits_auticare_", ".xlsx");
            ProductExcelExportService.write(tmp, all, stockNames);
            java.io.File f = tmp.toFile();
            if (Desktop.isDesktopSupported()) {
                Desktop desk = Desktop.getDesktop();
                if (desk.isSupported(Desktop.Action.OPEN)) {
                    desk.open(f);
                    return;
                }
            }
            alert(
                Alert.AlertType.INFORMATION,
                "Export Excel",
                "Fichier créé :\n" + f.getAbsolutePath() + "\n\n"
                    + "L’ouverture automatique n’est pas disponible. Ouvrez le fichier manuellement."
            );
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Export Excel", ex.getMessage() != null ? ex.getMessage() : "Erreur d’export.");
        }
    }

    /** Statistiques + indicateurs « prévision » (fusion des deux anciens boutons), aligné contenu Symfony. */
    private void showStats() {
        try {
            List<Product> all = productService.findAll();
            ProductStatsResult stats = ProductStatsCalculator.compute(all);
            Window owner = productsHost != null && productsHost.getScene() != null
                ? productsHost.getScene().getWindow()
                : null;
            ProductStatsWindow.show(owner, stats, "Statistiques & prévision catalogue");
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    /** Bouton secondaire bordé — comme {@code admin/produit/index.html.twig} (Stats). */
    private static void applySymfonyStatsOutlineButton(Button b) {
        b.getStyleClass().add("admin-produit-stats-outline");
    }

    /** Exporter Excel — comme le lien Symfony (bordure #E5E0D8, texte #6B7280, survol #F5F1EB). */
    private static void applySymfonyExportExcelButton(Button b) {
        b.getStyleClass().add("admin-produit-export-excel");
    }

    /**
     * Formulaire ajout / modification : champs alignés sur ppp (voir {@link ProductEditorPane}).
     */
    private void showProductEditor(Product existing) {
        boolean edit = existing != null && existing.getId() > 0;

        ObservableList<Stock> stocks;
        try {
            stocks = FXCollections.observableArrayList(stockService.findAll());
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Stock", e.getMessage());
            return;
        }

        ProductEditorPane editor = new ProductEditorPane(existing, stocks, msg -> alert(Alert.AlertType.ERROR, "Image", msg));

        Button saveBtn = new Button(edit ? "Enregistrer" : "Publier produit");
        saveBtn.setStyle(
            "-fx-background-color: #8fb2d9; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 15px; -fx-padding: 10 18 10 18;"
        );
        Button cancelBtn = new Button("Retour à la liste");
        cancelBtn.setStyle(
            "-fx-background-color: #efefef; -fx-text-fill: #374151; -fx-background-radius: 8; "
                + "-fx-font-size: 15px; -fx-padding: 10 18 10 18;"
        );
        HBox actions = new HBox(12, saveBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox formRoot = new VBox(14, editor.createFormSection(), actions);
        formRoot.setPadding(new Insets(18));
        formRoot.setMaxWidth(Double.MAX_VALUE);
        formRoot.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #eee7df; -fx-border-radius: 12;"
        );

        ScrollPane scroll = new ScrollPane(formRoot);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: white;");

        cancelBtn.setOnAction(e -> showMainProductsPage());
        saveBtn.setOnAction(e -> {
            try {
                String nom = editor.nomField.getText() == null ? "" : editor.nomField.getText().trim();
                String description = editor.descriptionArea.getText() == null ? "" : editor.descriptionArea.getText().trim();
                String prixText = editor.prixField.getText() == null ? "" : editor.prixField.getText().trim();
                String imagePath = editor.imagePathField.getText() == null ? "" : editor.imagePathField.getText().trim();

                // Contrôles dans l’ordre d’affichage du formulaire (nom → description → catégorie → prix → image → stock → quantité…)
                String err = ProductFormValidation.validateNom(nom);
                if (err != null) {
                    alert(Alert.AlertType.WARNING, "Saisie", err);
                    return;
                }
                err = ProductFormValidation.validateDescription(description);
                if (err != null) {
                    alert(Alert.AlertType.WARNING, "Saisie", err);
                    return;
                }
                err = ProductFormValidation.validateCategorieChoice(editor.categoryCombo.getValue());
                if (err != null) {
                    alert(Alert.AlertType.WARNING, "Saisie", err);
                    return;
                }
                err = ProductFormValidation.validatePrixText(prixText);
                if (err != null) {
                    alert(Alert.AlertType.WARNING, "Saisie", err);
                    return;
                }
                if (!edit) {
                    err = ProductFormValidation.validateImageRequiredForCreate(imagePath);
                } else {
                    err = ProductFormValidation.validateImagePresentEdit(imagePath);
                }
                if (err != null) {
                    alert(Alert.AlertType.WARNING, "Saisie", err);
                    return;
                }

                Stock loc = editor.stockCombo.getValue();
                if (loc == null) {
                    alert(
                        Alert.AlertType.WARNING,
                        "Sélection",
                        "Choisissez un emplacement dans la liste (créez d’abord un stock dans le menu Stocks)."
                    );
                    return;
                }

                double prix = ProductFormValidation.parsePrix(prixText);

                int qtyProduit = editor.getCatalogQuantityForSave();
                if (qtyProduit < 0) {
                    alert(Alert.AlertType.WARNING, "Saisie", "Indiquez une quantité catalogue entière valide.");
                    return;
                }
                err = ProductFormValidation.validateCatalogQuantity(qtyProduit);
                if (err != null) {
                    alert(Alert.AlertType.WARNING, "Saisie", err);
                    return;
                }

                if (!edit && loc.getQuantite() < qtyProduit) {
                    alert(
                        Alert.AlertType.WARNING,
                        "Stock",
                        "Stock insuffisant : « " + loc.getNom() + " » n'a plus d'unité disponible."
                    );
                    return;
                }

                Product p = new Product();
                String categorieDb = editor.categoryCombo.getValue().getDbValue();
                if (edit) {
                    p.setId(existing.getId());
                    p.setCategorie(categorieDb);
                    p.setStock(qtyProduit);
                    p.setDisponible(existing.isDisponible());
                    p.setPublie(existing.isPublie());
                    p.setValide(existing.isValide());
                    p.setGenereParIa(existing.isGenereParIa());
                    p.setSku(existing.getSku());
                    p.setSeuilAlerte(existing.getSeuilAlerte());
                    p.setNoteMoyenne(existing.getNoteMoyenne());
                    p.setUserId(existing.getUserId());
                } else {
                    p.setCategorie(categorieDb);
                    p.setStock(qtyProduit);
                    p.setDisponible(true);
                    p.setPublie(true);
                    p.setValide(true);
                    p.setGenereParIa(false);
                    p.setSku(null);
                    p.setSeuilAlerte(null);
                    p.setNoteMoyenne(null);
                    if (AppState.getCurrentUser() != null) {
                        p.setUserId(AppState.getCurrentUser().getId());
                    }
                }

                p.setNom(nom);
                p.setDescription(description);
                p.setPrix(prix);
                p.setStockId(loc.getId());
                p.setImagePath(imagePath.isBlank() ? null : imagePath.trim());

                if (edit) {
                    productService.update(p);
                } else {
                    productService.add(p);
                }
                resetProductSearchFilter();
                loadProducts();
                showMainProductsPage();
                Platform.runLater(() -> alert(
                    Alert.AlertType.INFORMATION,
                    edit ? "Produit mis à jour" : "Produit publié",
                    edit
                        ? "Le produit a été mis à jour. La liste a été rafraîchie."
                        : "Le produit a été publié et apparaît dans la liste. Le stock physique a été mis à jour."
                ));
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Enregistrement", ex.getMessage());
            }
        });

        productsHost.getChildren().setAll(scroll);
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
