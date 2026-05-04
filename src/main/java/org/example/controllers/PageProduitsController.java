package org.example.controllers;

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

public class PageProduitsController implements PublicShellAware {
    private static final String BTN_FILTER_IDLE = "-fx-background-color: #f3f4f0;";
    private static final String BTN_FILTER_ACTIVE = "-fx-background-color: #90a67f; -fx-text-fill: #fff;";
    private static final String BTN_SORT_IDLE = "-fx-background-color: #eef1ea;";
    private static final String BTN_SORT_ACTIVE = "-fx-background-color: #5c6d4a; -fx-text-fill: #fff;";

    private final ProductService productService = new ProductService();
    private final StockService stockService = new StockService();
    private final CartService cartService = new CartService();
    private final FavorisService favorisService = new FavorisService();

    private PublicShellController shell;
    private List<Product> publishedSource = List.of();
    private Set<Integer> favoriteProductIds = Set.of();
    private String selectedCategoryDb = "";
    private PriceBand selectedPrice = PriceBand.ALL;
    private SortMode sortMode = SortMode.DEFAULT;

    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<Button> priceButtons = new ArrayList<>();
    private final List<Button> sortButtons = new ArrayList<>();

    @FXML private VBox categoryButtonsBox;
    @FXML private VBox priceButtonsBox;
    @FXML private HBox sortToolbar;
    @FXML private Label resultsCountLabel;
    @FXML private TextField searchField;
    @FXML private ScrollPane productsScroll;
    @FXML private FlowPane productsFlow;

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
            productsFlow.prefWrapLengthProperty().bind(Bindings.max(260, productsScroll.widthProperty().subtract(40)));
        }
        loadCatalog();
        applyFiltersAndRender();
    }

    @Override
    public void onShellReady() {
        applyFiltersAndRender();
    }

    @FXML
    private void onSearch() {
        applyFiltersAndRender();
    }

    private void buildCategoryFilters() {
        if (categoryButtonsBox == null) return;
        categoryButtons.clear();
        categoryButtonsBox.getChildren().clear();

        Button all = new Button("Toutes les categories");
        all.setMaxWidth(Double.MAX_VALUE);
        all.setOnAction(e -> selectCategory(""));
        categoryButtons.add(all);
        categoryButtonsBox.getChildren().add(all);

        for (ProductCategoryChoice c : ProductFormUi.getProductCategories()) {
            Button b = new Button(c.getLabel());
            b.setMaxWidth(Double.MAX_VALUE);
            b.setUserData(c.getDbValue());
            b.setOnAction(ev -> selectCategory(c.getDbValue()));
            categoryButtons.add(b);
            categoryButtonsBox.getChildren().add(b);
        }
        refreshCategoryStyles();
    }

    private void buildPriceFilters() {
        if (priceButtonsBox == null) return;
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

    private void buildSortToolbar() {
        if (sortToolbar == null) return;
        sortToolbar.getChildren().clear();
        sortButtons.clear();
        addSortButton("Defaut", SortMode.DEFAULT);
        addSortButton("A -> Z", SortMode.NAME_AZ);
        addSortButton("Z -> A", SortMode.NAME_ZA);
        addSortButton("Prix +", SortMode.PRICE_ASC);
        addSortButton("Prix -", SortMode.PRICE_DESC);
        refreshSortStyles();
    }

    private void addSortButton(String text, SortMode mode) {
        Button b = new Button(text);
        b.setUserData(mode);
        b.setOnAction(e -> {
            sortMode = mode;
            refreshSortStyles();
            applyFiltersAndRender();
        });
        sortButtons.add(b);
        sortToolbar.getChildren().add(b);
    }

    private void loadCatalog() {
        try {
            publishedSource = productService.findPublishedCatalog();
        } catch (SQLException ex) {
            publishedSource = List.of();
            cartAlert(Alert.AlertType.ERROR, "Catalogue produits", ex.getMessage());
        }
    }

    private void applyFiltersAndRender() {
        if (productsFlow == null || resultsCountLabel == null) return;
        refreshFavoriteIds();
        Map<Integer, String> stockNames = loadStockNames();
        String term = searchField == null || searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.FRENCH);

        List<Product> filtered = publishedSource.stream()
                .filter(this::matchesCategory)
                .filter(this::matchesPrice)
                .filter(p -> matchesSearch(p, term))
                .collect(Collectors.toCollection(ArrayList::new));
        sortList(filtered);

        productsFlow.getChildren().clear();
        resultsCountLabel.setText(filtered.size() + " produit(s) trouves");
        if (filtered.isEmpty()) {
            Label empty = new Label("Aucun produit ne correspond a ces filtres.");
            empty.setPadding(new Insets(24));
            productsFlow.getChildren().add(empty);
            return;
        }
        User session = AppState.getCurrentUser();
        for (Product p : filtered) {
            productsFlow.getChildren().add(buildPublicCard(p, stockNames, session));
        }
    }

    private VBox buildPublicCard(Product p, Map<Integer, String> stockNames, User session) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setMinWidth(300);
        card.setMaxWidth(300);

        StackPane imgFrame = new StackPane();
        imgFrame.setMinSize(280, 182);
        imgFrame.setMaxSize(280, 182);
        Image img = (p.getImagePath() != null && !p.getImagePath().isBlank())
                ? ProductImageLoader.loadForDisplay(p.getImagePath(), 280, 182)
                : null;
        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(280);
            iv.setFitHeight(182);
            iv.setPreserveRatio(true);
            imgFrame.getChildren().add(iv);
        } else {
            imgFrame.getChildren().add(ProductImagePlaceholder.create(280, 182));
        }

        ProductCategoryChoice cc = ProductFormUi.resolveCategoryChoice(p.getCategorie());
        Label catLbl = new Label(cc.getLabel());
        Label nameLbl = new Label(p.getNom() == null ? "-" : p.getNom());
        nameLbl.setWrapText(true);
        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
        String desc = p.getDescription() == null ? "" : p.getDescription();
        Label descLbl = new Label(desc.length() > 120 ? desc.substring(0, 117) + "..." : desc);
        descLbl.setWrapText(true);

        String stockNom = stockNames.get(p.getStockId());
        Label stockLbl = new Label(stockNom != null && !stockNom.isBlank() ? "Stock : " + stockNom : "Ref. stock #" + p.getStockId());

        Button addCart = new Button("Ajouter au panier");
        addCart.setMaxWidth(Double.MAX_VALUE);
        addCart.setDisable(!p.isDisponible() || cartService.getAvailableQuantityForCart(p) <= 0);
        addCart.setOnAction(e -> addProductToCart(p));

        HBox favRow = new HBox(8);
        if (session != null) {
            Button favBtn = new Button(favoriteProductIds.contains(p.getId()) ? "Retirer favori" : "Ajouter favori");
            favBtn.setOnAction(e -> toggleFavorite(session, p, favBtn));
            favRow.getChildren().add(favBtn);
        }

        VBox info = new VBox(6, catLbl, nameLbl, priceLbl, descLbl, stockLbl, favRow, addCart);
        VBox.setVgrow(info, Priority.ALWAYS);
        card.getChildren().addAll(imgFrame, info);
        return card;
    }

    private void toggleFavorite(User user, Product product, Button button) {
        try {
            if (favoriteProductIds.contains(product.getId())) {
                favorisService.remove(user.getId(), product.getId());
            } else {
                favorisService.add(user.getId(), product.getId());
            }
            refreshFavoriteIds();
            button.setText(favoriteProductIds.contains(product.getId()) ? "Retirer favori" : "Ajouter favori");
        } catch (SQLException ex) {
            cartAlert(Alert.AlertType.ERROR, "Favoris", ex.getMessage());
        }
    }

    private void addProductToCart(Product p) {
        User u = AppState.getCurrentUser();
        if (u == null) {
            cartAlert(Alert.AlertType.INFORMATION, "Connexion requise", "Veuillez vous connecter pour ajouter au panier.");
            return;
        }
        try {
            cartService.addOne(u.getId(), p.getId());
            AppState.notifyCartChanged();
            if (shell != null) shell.loadPage("panier");
        } catch (SQLException | IOException ex) {
            cartAlert(Alert.AlertType.ERROR, "Panier", ex.getMessage());
        }
    }

    private Map<Integer, String> loadStockNames() {
        Map<Integer, String> map = new HashMap<>();
        try {
            for (Stock s : stockService.findAll()) map.put(s.getId(), s.getNom());
        } catch (SQLException ignored) {}
        return map;
    }

    private void selectCategory(String dbValue) {
        selectedCategoryDb = dbValue == null ? "" : dbValue;
        refreshCategoryStyles();
        applyFiltersAndRender();
    }

    private void refreshCategoryStyles() {
        for (Button b : categoryButtons) {
            String ud = b.getUserData() instanceof String s ? s : "";
            boolean on = selectedCategoryDb.isEmpty() && ud.isEmpty() || (!selectedCategoryDb.isEmpty() && selectedCategoryDb.equals(ud));
            b.setStyle(on ? BTN_FILTER_ACTIVE : BTN_FILTER_IDLE);
        }
    }

    private void refreshPriceStyles() {
        for (Button b : priceButtons) {
            PriceBand band = b.getUserData() instanceof PriceBand pb ? pb : PriceBand.ALL;
            b.setStyle(band == selectedPrice ? BTN_FILTER_ACTIVE : BTN_FILTER_IDLE);
        }
    }

    private void refreshSortStyles() {
        for (Button b : sortButtons) {
            SortMode m = b.getUserData() instanceof SortMode sm ? sm : SortMode.DEFAULT;
            b.setStyle(m == sortMode ? BTN_SORT_ACTIVE : BTN_SORT_IDLE);
        }
    }

    private boolean matchesCategory(Product p) {
        if (selectedCategoryDb.isBlank()) return true;
        String cat = p.getCategorie();
        return cat != null && selectedCategoryDb.equalsIgnoreCase(cat.trim());
    }

    private boolean matchesPrice(Product p) {
        double x = p.getPrix();
        return x >= selectedPrice.min && x <= selectedPrice.max;
    }

    private boolean matchesSearch(Product p, String term) {
        if (term.isBlank()) return true;
        ProductCategoryChoice cc = ProductFormUi.resolveCategoryChoice(p.getCategorie());
        String catLabel = cc.getLabel() == null ? "" : cc.getLabel().toLowerCase(Locale.FRENCH);
        String nom = p.getNom() == null ? "" : p.getNom().toLowerCase(Locale.FRENCH);
        String desc = p.getDescription() == null ? "" : p.getDescription().toLowerCase(Locale.FRENCH);
        String prix = String.format(Locale.FRENCH, "%.2f", p.getPrix());
        return nom.contains(term) || desc.contains(term) || catLabel.contains(term) || prix.contains(term)
                || FuzzyProductSearch.matches(term, p);
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
            case NAME_AZ -> Comparator.comparing((Product p) -> p.getNom() == null ? "" : p.getNom(), String.CASE_INSENSITIVE_ORDER);
            case NAME_ZA -> Comparator.comparing((Product p) -> p.getNom() == null ? "" : p.getNom(), String.CASE_INSENSITIVE_ORDER).reversed();
            case PRICE_ASC -> Comparator.comparingDouble(Product::getPrix);
            case PRICE_DESC -> Comparator.comparingDouble(Product::getPrix).reversed();
            case DEFAULT -> Comparator.comparingInt(Product::getId).reversed();
        };
        list.sort(cmp);
    }

    private void cartAlert(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.show();
    }

    private enum SortMode {
        DEFAULT, NAME_AZ, NAME_ZA, PRICE_ASC, PRICE_DESC
    }

    private enum PriceBand {
        ALL("Tous les prix", 0, Double.POSITIVE_INFINITY),
        B0_500("0 - 500 DT", 0, 500),
        B500_1000("500 - 1 000 DT", 500, 1000),
        B1000_2000("1 000 - 2 000 DT", 1000, 2000),
        B2000_5000("2 000 - 5 000 DT", 2000, 5000),
        B5000_10000("5 000 - 10 000 DT", 5000, 10000);

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
