package org.example.controllers;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.models.Product;
import org.example.models.Stock;
import org.example.models.User;
import org.example.services.CartService;
import org.example.services.FavorisService;
import org.example.services.ProductService;
import org.example.services.StockService;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.ui.product.ProductFormUi;
import org.example.ui.product.ProductFormUi.ProductCategoryChoice;
import org.example.utils.AppState;
import org.example.utils.FuzzyProductSearch;
import org.example.utils.ProductImageLoader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catalogue « Nos Produits » dans la coque publique : produits publiés par l’admin, filtres et tri.
 * Pas de création / édition (réservé au tableau de bord admin).
 */
public class PageProduitsController implements PublicShellAware {

    private static final String BTN_FILTER_IDLE =
        "-fx-background-color: #f3f4f0; -fx-text-fill: #374151; -fx-background-radius: 10; "
            + "-fx-border-radius: 10; -fx-border-color: #e5e7eb; -fx-border-width: 1; "
            + "-fx-font-size: 13px; -fx-padding: 8 12 8 12; -fx-cursor: hand;";
    private static final String BTN_FILTER_ACTIVE =
        "-fx-background-color: #90a67f; -fx-text-fill: #ffffff; -fx-background-radius: 10; "
            + "-fx-border-radius: 10; -fx-border-color: #7d946f; -fx-border-width: 1; "
            + "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 8 12 8 12; -fx-cursor: hand;";
    private static final String BTN_SORT_IDLE =
        "-fx-background-color: #eef1ea; -fx-text-fill: #374151; -fx-background-radius: 8; "
            + "-fx-font-size: 12px; -fx-padding: 6 10 6 10; -fx-cursor: hand;";
    private static final String BTN_SORT_ACTIVE =
        "-fx-background-color: #5c6d4a; -fx-text-fill: #ffffff; -fx-background-radius: 8; "
            + "-fx-font-size: 12px; -fx-font-weight: 600; -fx-padding: 6 10 6 10; -fx-cursor: hand;";

    private final ProductService productService = new ProductService();
    private final StockService stockService = new StockService();
    private final CartService cartService = new CartService();
    private final FavorisService favorisService = new FavorisService();
    private PublicShellController shell;
    private Set<Integer> favoriteProductIds = Set.of();

    private List<Product> publishedSource = List.of();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<Button> priceButtons = new ArrayList<>();
    private final List<Button> sortButtons = new ArrayList<>();

    private String selectedCategoryDb = "";
    private PriceBand selectedPrice = PriceBand.ALL;
    private SortMode sortMode = SortMode.DEFAULT;

    @FXML
    private VBox categoryButtonsBox;
    @FXML
    private VBox priceButtonsBox;
    @FXML
    private HBox sortToolbar;
    @FXML
    private Label resultsCountLabel;
    @FXML
    private TextField searchField;
    @FXML
    private ScrollPane productsScroll;
    @FXML
    private FlowPane productsFlow;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        buildCategoryFilters();
        buildPriceFilters();
        buildSortToolbar();
        if (searchField != null) {
            searchField.setOnAction(e -> applyFiltersAndRender());
        }
        if (productsScroll != null && productsFlow != null) {
            productsFlow.prefWrapLengthProperty().bind(
                Bindings.max(260, productsScroll.widthProperty().subtract(40))
            );
            productsScroll.setPrefViewportHeight(520);
        }
        loadCatalog();
        Platform.runLater(this::applyFiltersAndRender);
    }

    @FXML
    private void onSearch() {
        applyFiltersAndRender();
    }

    private void buildCategoryFilters() {
        if (categoryButtonsBox == null) {
            return;
        }
        categoryButtons.clear();
        categoryButtonsBox.getChildren().clear();

        Button all = new Button("Toutes les catégories");
        all.setMaxWidth(Double.MAX_VALUE);
        all.setOnAction(e -> selectCategory("", all));
        categoryButtons.add(all);
        categoryButtonsBox.getChildren().add(all);

        for (ProductCategoryChoice c : ProductFormUi.getProductCategories()) {
            Button b = new Button(c.getLabel());
            b.setMaxWidth(Double.MAX_VALUE);
            b.setUserData(c.getDbValue());
            b.setOnAction(ev -> selectCategory(c.getDbValue(), b));
            categoryButtons.add(b);
            categoryButtonsBox.getChildren().add(b);
        }
        refreshCategoryStyles();
    }

    private void selectCategory(String dbValue, Button clicked) {
        selectedCategoryDb = dbValue == null ? "" : dbValue;
        refreshCategoryStyles();
        applyFiltersAndRender();
    }

    private void refreshCategoryStyles() {
        for (Button b : categoryButtons) {
            String ud = b.getUserData() instanceof String s ? s : "";
            boolean on = selectedCategoryDb.isEmpty() && ud.isEmpty()
                || !selectedCategoryDb.isEmpty() && selectedCategoryDb.equals(ud);
            b.setStyle(on ? BTN_FILTER_ACTIVE : BTN_FILTER_IDLE);
        }
    }

    private void buildPriceFilters() {
        if (priceButtonsBox == null) {
            return;
        }
        priceButtons.clear();
        priceButtonsBox.getChildren().clear();

        for (PriceBand band : PriceBand.values()) {
            Button b = new Button(band.label);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setUserData(band);
            b.setOnAction(e -> {
                selectedPrice = band;
                refreshPriceStyles();
                applyFiltersAndRender();
            });
            priceButtons.add(b);
            priceButtonsBox.getChildren().add(b);
        }
        refreshPriceStyles();
    }

    private void refreshPriceStyles() {
        for (Button b : priceButtons) {
            PriceBand band = b.getUserData() instanceof PriceBand pb ? pb : PriceBand.ALL;
            b.setStyle(band == selectedPrice ? BTN_FILTER_ACTIVE : BTN_FILTER_IDLE);
        }
    }

    private void buildSortToolbar() {
        if (sortToolbar == null) {
            return;
        }
        sortToolbar.getChildren().clear();
        sortButtons.clear();

        addSortButton("A → Z", SortMode.NAME_AZ);
        addSortButton("Z → A", SortMode.NAME_ZA);
        addSortButton("Prix −", SortMode.PRICE_ASC);
        addSortButton("Prix +", SortMode.PRICE_DESC);
        refreshSortStyles();
    }

    private void addSortButton(String text, SortMode mode) {
        Button b = new Button(text);
        b.setMnemonicParsing(false);
        b.setUserData(mode);
        b.setOnAction(e -> {
            sortMode = mode;
            refreshSortStyles();
            applyFiltersAndRender();
        });
        sortButtons.add(b);
        sortToolbar.getChildren().add(b);
    }

    private void refreshSortStyles() {
        for (Button b : sortButtons) {
            SortMode m = b.getUserData() instanceof SortMode sm ? sm : SortMode.DEFAULT;
            boolean on = sortMode == m;
            b.setStyle(on ? BTN_SORT_ACTIVE : BTN_SORT_IDLE);
        }
    }

    private void loadCatalog() {
        try {
            publishedSource = productService.findPublishedCatalog();
        } catch (SQLException ex) {
            publishedSource = List.of();
            Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setTitle("Catalogue produits");
                a.setHeaderText("Impossible de lire les produits");
                a.setContentText(
                    "Vérifiez que MySQL tourne et que la base « pidb » est accessible (localhost, utilisateur root).\n\n"
                        + ex.getMessage()
                );
                if (productsFlow != null && productsFlow.getScene() != null && productsFlow.getScene().getWindow() != null) {
                    a.initOwner(productsFlow.getScene().getWindow());
                }
                a.show();
            });
        }
        applyFiltersAndRender();
    }

    private void applyFiltersAndRender() {
        if (resultsCountLabel == null || productsFlow == null) {
            return;
        }

        refreshFavoriteIds();
        Map<Integer, String> stockNames = loadStockNames();
        String q = searchField == null ? "" : searchField.getText();
        String term = q == null ? "" : q.trim().toLowerCase(Locale.FRENCH);

        List<Product> filtered = publishedSource.stream()
            .filter(this::matchesCategory)
            .filter(p -> matchesPrice(p))
            .filter(p -> matchesSearch(p, term))
            .collect(Collectors.toCollection(ArrayList::new));

        sortList(filtered);

        int n = filtered.size();
        resultsCountLabel.setText(n + " produit(s) trouvé(s)");

        productsFlow.getChildren().clear();
        if (filtered.isEmpty()) {
            boolean emptyDb = publishedSource.isEmpty();
            Label empty = new Label(
                emptyDb
                    ? "Aucun produit publié pour l’instant. Côté admin : Gestion → Produits → « Publier produit » (statut publié) pour qu’il apparaisse ici."
                    : "Aucun produit ne correspond à ces filtres ou à la recherche."
            );
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 15px;");
            empty.setPadding(new Insets(48, 16, 48, 16));
            productsFlow.getChildren().add(empty);
            return;
        }

        User session = AppState.getCurrentUser();
        for (Product p : filtered) {
            productsFlow.getChildren().add(buildPublicCard(p, stockNames, session));
        }
    }

    private Map<Integer, String> loadStockNames() {
        Map<Integer, String> map = new HashMap<>();
        try {
            for (Stock s : stockService.findAll()) {
                map.put(s.getId(), s.getNom());
            }
        } catch (SQLException ignored) {
            // —
        }
        return map;
    }

    private boolean matchesCategory(Product p) {
        if (selectedCategoryDb == null || selectedCategoryDb.isBlank()) {
            return true;
        }
        String cat = p.getCategorie();
        if (cat == null || cat.isBlank()) {
            return false;
        }
        return selectedCategoryDb.equalsIgnoreCase(cat.trim());
    }

    private boolean matchesPrice(Product p) {
        double x = p.getPrix();
        return x >= selectedPrice.min && x <= selectedPrice.max;
    }

    private boolean matchesSearch(Product p, String term) {
        if (term.isEmpty()) {
            return true;
        }
        ProductCategoryChoice cc = ProductFormUi.resolveCategoryChoice(p.getCategorie());
        String catLabel = cc.getLabel() == null ? "" : cc.getLabel().toLowerCase(Locale.FRENCH);
        String nom = p.getNom() == null ? "" : p.getNom().toLowerCase(Locale.FRENCH);
        String desc = p.getDescription() == null ? "" : p.getDescription().toLowerCase(Locale.FRENCH);
        String prixStr = String.format(Locale.FRENCH, "%.2f", p.getPrix());
        boolean plain = nom.contains(term) || desc.contains(term) || catLabel.contains(term) || prixStr.contains(term);
        if (plain) {
            return true;
        }
        return FuzzyProductSearch.matches(term, p);
    }

    private void refreshFavoriteIds() {
        User u = AppState.getCurrentUser();
        if (u == null) {
            favoriteProductIds = Set.of();
            return;
        }
        try {
            favoriteProductIds = new HashSet<>(favorisService.favoriteProductIdsForUser(u.getId()));
        } catch (SQLException e) {
            favoriteProductIds = Set.of();
        }
    }

    private void sortList(List<Product> list) {
        Comparator<Product> cmp = switch (sortMode) {
            case NAME_AZ -> Comparator.comparing(
                (Product p) -> p.getNom() == null ? "" : p.getNom(),
                String.CASE_INSENSITIVE_ORDER
            );
            case NAME_ZA -> Comparator.comparing(
                (Product p) -> p.getNom() == null ? "" : p.getNom(),
                String.CASE_INSENSITIVE_ORDER
            ).reversed();
            case PRICE_ASC -> Comparator.comparingDouble(Product::getPrix);
            case PRICE_DESC -> Comparator.comparingDouble(Product::getPrix).reversed();
            case DEFAULT -> Comparator.comparingInt(Product::getId).reversed();
        };
        list.sort(cmp);
    }

    private VBox buildPublicCard(Product p, Map<Integer, String> stockNames, User session) {
        final int imgW = 280;
        final int imgH = 182;

        VBox card = new VBox(10);
        card.setPadding(new Insets(12));
        card.setMinWidth(300);
        card.setMaxWidth(300);
        card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 14; -fx-border-radius: 14; "
                + "-fx-border-color: #e5e0d8; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 14, 0, 0, 3);"
        );

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

        ProductCategoryChoice cc = ProductFormUi.resolveCategoryChoice(p.getCategorie());
        Label catLbl = new Label(cc.getLabel());
        catLbl.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a; "
                + "-fx-background-color: rgba(144,166,127,0.2); -fx-background-radius: 8; -fx-padding: 3 8 3 8;"
        );

        Label nameLbl = new Label(p.getNom() != null ? p.getNom() : "—");
        nameLbl.setWrapText(true);
        nameLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
        priceLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");

        String rawDesc = p.getDescription() == null || p.getDescription().isBlank() ? "" : p.getDescription();
        String shortDesc = rawDesc.length() > 120 ? rawDesc.substring(0, 117) + "…" : rawDesc;
        Label descLbl = new Label(shortDesc.isEmpty() ? " " : shortDesc);
        descLbl.setWrapText(true);
        descLbl.setMaxHeight(68);
        descLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #64748b; -fx-line-spacing: 2px;");

        HBox favRow = new HBox(8);
        if (session != null) {
            boolean fav = favoriteProductIds.contains(p.getId());
            Button heart = new Button(fav ? "♥ Retirer" : "♡ Favori");
            heart.setMnemonicParsing(false);
            heart.setStyle("-fx-background-color: transparent; -fx-text-fill: #be123c; -fx-cursor: hand;");
            heart.setOnAction(e -> {
                try {
                    if (favoriteProductIds.contains(p.getId())) {
                        favorisService.remove(session.getId(), p.getId());
                        favoriteProductIds.remove(p.getId());
                        heart.setText("♡ Favori");
                    } else {
                        favorisService.add(session.getId(), p.getId());
                        favoriteProductIds.add(p.getId());
                        heart.setText("♥ Retirer");
                    }
                    AppState.notifyCartChanged();
                } catch (SQLException ex) {
                    cartAlert(Alert.AlertType.ERROR, "Favoris", ex.getMessage());
                }
            });
            favRow.getChildren().add(heart);
        }

        String noteTxt = p.getNoteMoyenne() != null
            ? String.format(Locale.FRENCH, "Note moyenne : %.1f / 5", p.getNoteMoyenne())
            : "Pas encore d’avis";
        Label noteLbl = new Label(noteTxt);
        noteLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        String stockNom = stockNames.get(p.getStockId());
        String stockLine = stockNom != null && !stockNom.isBlank()
            ? "Stock : " + stockNom
            : "Réf. stock #" + p.getStockId();
        Label stockLbl = new Label(stockLine);
        stockLbl.setWrapText(true);
        stockLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a;");

        int maxCart = cartService.getAvailableQuantityForCart(p);
        boolean canOrder = p.isDisponible() && maxCart > 0;
        Button addCart = new Button(canOrder ? "Ajouter au panier" : "Indisponible");
        addCart.setMnemonicParsing(false);
        addCart.setMaxWidth(Double.MAX_VALUE);
        addCart.getStyleClass().add("catalog-add-cart-btn");
        addCart.setDisable(!canOrder);
        addCart.setOnAction(e -> addProductToCart(p));

        /* Photo → nom → prix → description, puis métadonnées et actions. */
        VBox textCol = new VBox(
            6,
            nameLbl,
            priceLbl,
            descLbl,
            catLbl,
            favRow,
            noteLbl,
            stockLbl,
            addCart
        );
        VBox.setVgrow(textCol, Priority.ALWAYS);

        card.getChildren().addAll(imgFrame, textCol);
        return card;
    }

    private void addProductToCart(Product p) {
        if (p == null) {
            return;
        }
        User u = AppState.getCurrentUser();
        if (u != null) {
            try {
                cartService.addOne(u.getId(), p.getId());
                AppState.notifyCartChanged();
                navigateToPanier();
            } catch (SQLException ex) {
                if ("STOCK_OUT".equals(ex.getMessage())) {
                    cartAlert(Alert.AlertType.WARNING, "Stock", "Stock insuffisant ou produit indisponible.");
                } else {
                    cartAlert(Alert.AlertType.ERROR, "Panier", ex.getMessage());
                }
            }
            return;
        }
        int stock = cartService.getAvailableQuantityForCart(p);
        if (stock <= 0) {
            cartAlert(Alert.AlertType.WARNING, "Stock", "Ce produit n’est pas disponible en stock.");
            return;
        }
        int cur = AppState.guestCartGetQty(p.getId());
        if (cur >= stock) {
            cartAlert(Alert.AlertType.WARNING, "Stock", "Vous avez déjà la quantité maximale pour ce produit.");
            return;
        }
        AppState.guestCartAdd(p.getId(), 1);
        AppState.notifyCartChanged();
        navigateToPanier();
    }

    private void navigateToPanier() {
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("panier");
        } catch (IOException e) {
            cartAlert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    private void cartAlert(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        if (productsFlow != null && productsFlow.getScene() != null && productsFlow.getScene().getWindow() != null) {
            a.initOwner(productsFlow.getScene().getWindow());
        }
        a.show();
    }

    private enum SortMode {
        DEFAULT,
        NAME_AZ,
        NAME_ZA,
        PRICE_ASC,
        PRICE_DESC
    }

    private enum PriceBand {
        ALL("Tous les prix", 0, Double.POSITIVE_INFINITY),
        B0_500("0 — 500 DT", 0, 500),
        B500_1000("500 — 1 000 DT", 500, 1000),
        B1000_2000("1 000 — 2 000 DT", 1000, 2000),
        B2000_5000("2 000 — 5 000 DT", 2000, 5000),
        B5000_10000("5 000 — 10 000 DT", 5000, 10000);

        final String label;
        final double min;
        final double max;

        PriceBand(String label, double min, double max) {
            this.label = label;
            this.min = min;
            this.max = max;
        }
    }
}
