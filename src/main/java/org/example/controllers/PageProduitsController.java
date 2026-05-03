package org.example.controllers;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.models.Product;
import org.example.models.Stock;
import org.example.models.User;
import org.example.services.CartService;
import org.example.services.FavorisService;
import org.example.services.ProductService;
import org.example.services.StockService;
import org.example.services.AvisProduitService;
import org.example.services.GroqChatCompletionService;
import org.example.services.GroqChatMessage;
import org.example.ui.product.ProductCreationChatStage;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.ui.product.ProductFormUi;
import org.example.ui.product.ProductFormUi.ProductCategoryChoice;
import org.example.elasticsearch.ElasticsearchCatalogService;
import org.example.utils.AppState;
import org.example.utils.FuzzyProductSearch;
import org.example.utils.ProductImageLoader;

import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.util.Duration;

/**
 * Catalogue « Nos Produits » dans la coque publique : produits publiés par l’admin, filtres et tri.
 */
public class PageProduitsController implements PublicShellAware {
    private static final String LOGIN_REQUIRED_ACTION_MESSAGE = "Veuillez vous connecter pour effectuer cette action.";

    private static final String BTN_FILTER_IDLE =
        "-fx-background-color: #f3f4f0; -fx-text-fill: #374151; -fx-background-radius: 10; "
            + "-fx-border-radius: 10; -fx-border-color: #e5e7eb; -fx-border-width: 1; "
            + "-fx-font-size: 13px; -fx-padding: 8 12 8 12; -fx-cursor: hand;";
    private static final String BTN_FILTER_ACTIVE =
        "-fx-background-color: #90a67f; -fx-text-fill: #ffffff; -fx-background-radius: 10; "
            + "-fx-border-radius: 10; -fx-border-color: #7d946f; -fx-border-width: 1; "
            + "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 8 12 8 12; -fx-cursor: hand;";
    private final ProductService productService = new ProductService();
    private final StockService stockService = new StockService();
    private final CartService cartService = new CartService();
    private final FavorisService favorisService = new FavorisService();
    private final AvisProduitService avisProduitService = new AvisProduitService();
    private final GroqChatCompletionService groqChatService = new GroqChatCompletionService();
    private PublicShellController shell;
    private List<Product> publishedSource = List.of();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<Button> priceButtons = new ArrayList<>();
    private String selectedCategoryDb = "";
    private PriceBand selectedPrice = PriceBand.ALL;
    private SortMode sortMode = SortMode.DEFAULT;
    private Map<Integer, Integer> soldQtyByProductId = Map.of();

    @FXML
    private VBox categoryButtonsBox;
    @FXML
    private VBox priceButtonsBox;
    @FXML
    private ComboBox<SortOption> sortCombo;
    @FXML
    private Label resultsCountLabel;
    @FXML
    private TextField searchField;
    @FXML
    private Button createProductBtn;
    @FXML
    private Button favoritesToggleBtn;
    @FXML
    private ScrollPane productsScroll;
    @FXML
    private FlowPane productsFlow;
    @FXML
    private ListView<String> searchSuggestionList;
    @FXML
    private VBox recommendationsSection;
    @FXML
    private HBox recommendationsRow;
    private boolean favoritesOnlyMode;
    /** IDs produits favoris du membre connecté (vide si non connecté). */
    private Set<Integer> favoriteProductIds = Set.of();

    private final ElasticsearchCatalogService elasticsearch = ElasticsearchCatalogService.getInstance();
    private PauseTransition searchSuggestDebounce;
    /** Quand true, l’ordre vient d’Elasticsearch (pertinence) : on ne réordonne pas si tri = défaut. */
    private boolean elasticsearchRelevanceOrder;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        buildCategoryFilters();
        buildPriceFilters();
        buildSortCombo();
        if (searchField != null) {
            searchField.setOnAction(e -> {
                hideSearchSuggestions();
                applyFiltersAndRender();
            });
        }
        if (searchSuggestionList != null) {
            searchSuggestionList.setMaxHeight(180);
            searchSuggestionList.setVisible(false);
            searchSuggestionList.setManaged(false);
            searchSuggestionList.setOnMouseClicked(ev -> {
                if (searchSuggestionList.getSelectionModel().getSelectedItem() == null) {
                    return;
                }
                String pick = searchSuggestionList.getSelectionModel().getSelectedItem();
                if (searchField != null) {
                    searchField.setText(pick);
                }
                hideSearchSuggestions();
                applyFiltersAndRender();
            });
        }
        searchSuggestDebounce = new PauseTransition(Duration.millis(280));
        searchSuggestDebounce.setOnFinished(e -> runSearchSuggestions());
        if (searchField != null) {
            searchField.textProperty().addListener((obs, prev, cur) -> {
                if (searchSuggestDebounce != null) {
                    searchSuggestDebounce.stop();
                }
                if (cur == null || cur.trim().length() < 2 || !elasticsearch.isUsable()) {
                    hideSearchSuggestions();
                    return;
                }
                if (searchSuggestDebounce != null) {
                    searchSuggestDebounce.playFromStart();
                }
            });
            searchField.focusedProperty().addListener((obs, prev, focused) -> {
                if (!focused) {
                    hideSearchSuggestions();
                }
            });
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

    private void hideSearchSuggestions() {
        if (searchSuggestionList != null) {
            searchSuggestionList.getItems().clear();
            searchSuggestionList.setVisible(false);
            searchSuggestionList.setManaged(false);
        }
    }

    private void runSearchSuggestions() {
        if (searchField == null || searchSuggestionList == null || !elasticsearch.isUsable()) {
            return;
        }
        String prefix = searchField.getText();
        if (prefix == null || prefix.trim().length() < 2) {
            Platform.runLater(this::hideSearchSuggestions);
            return;
        }
        boolean guest = AppState.getCurrentUser() == null;
        String pfx = prefix.trim();
        new Thread(() -> {
            try {
                List<String> sug = elasticsearch.suggestCompletion(pfx, guest, 10);
                Platform.runLater(() -> {
                    if (searchField == null || !searchField.isFocused()) {
                        return;
                    }
                    searchSuggestionList.setItems(FXCollections.observableArrayList(sug));
                    boolean show = !sug.isEmpty();
                    searchSuggestionList.setVisible(show);
                    searchSuggestionList.setManaged(show);
                });
            } catch (Exception ignored) {
                Platform.runLater(this::hideSearchSuggestions);
            }
        }, "es-suggest").start();
    }

    @FXML
    private void onSearch() {
        applyFiltersAndRender();
    }

    @FXML
    private void onCreateProduct() {
        if (AppState.getCurrentUser() == null) {
            cartAlert(Alert.AlertType.INFORMATION, "Connexion requise", LOGIN_REQUIRED_ACTION_MESSAGE);
            return;
        }
        Window owner = createProductBtn != null && createProductBtn.getScene() != null
            ? createProductBtn.getScene().getWindow()
            : null;
        ProductCreationChatStage.show(owner);
    }

    @FXML
    private void onToggleFavorites() {
        // Favoris masqué côté client.
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

    private void selectCategory(String dbValue) {
        selectedCategoryDb = dbValue == null ? "" : dbValue;
        refreshCategoryStyles();
        applyFiltersAndRender();
        updateRecommendationsSection();
    }

    private void selectCategory(String dbValue, Button ignoredSource) {
        selectCategory(dbValue);
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
                updateRecommendationsSection();
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

    private void buildSortCombo() {
        if (sortCombo == null) {
            return;
        }
        sortCombo.getItems().setAll(
            new SortOption("Par défaut", SortMode.DEFAULT),
            new SortOption("A → Z", SortMode.NAME_AZ),
            new SortOption("Z → A", SortMode.NAME_ZA),
            new SortOption("Prix croissant", SortMode.PRICE_ASC),
            new SortOption("Prix décroissant", SortMode.PRICE_DESC),
            new SortOption("Produits mieux notés", SortMode.RATING_DESC),
            new SortOption("Plus vendus", SortMode.TOP_SOLD)
        );
        sortCombo.getSelectionModel().selectFirst();
        ProductFormUi.styleStockCombo(sortCombo);
        sortCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                sortMode = newV.mode();
            } else {
                sortMode = SortMode.DEFAULT;
            }
            applyFiltersAndRender();
        });
    }

    private void loadCatalog() {
        try {
            User session = AppState.getCurrentUser();
            if (session == null) {
                publishedSource = productService.findPublishedCatalogByAdmin();
            } else {
                publishedSource = productService.findPublishedCatalog();
            }
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
        loadSellingStats();
        applyFiltersAndRender();
        elasticsearch.reindexAllProductsAsync(productService);
        updateRecommendationsSection();
    }

    private void loadSellingStats() {
        try {
            soldQtyByProductId = productService.findSoldQuantitiesByProductId();
        } catch (SQLException ignored) {
            soldQtyByProductId = Map.of();
        }
    }

    private void applyFiltersAndRender() {
        if (resultsCountLabel == null || productsFlow == null) {
            return;
        }

        refreshFavoriteIds();
        Map<Integer, String> stockNames = loadStockNames();
        String q = searchField == null ? "" : searchField.getText();
        String termRaw = q == null ? "" : q.trim();
        String term = termRaw.toLowerCase(Locale.FRENCH);

        elasticsearchRelevanceOrder = false;
        boolean guestSession = AppState.getCurrentUser() == null;
        List<Product> filtered;
        if (elasticsearch.isUsable() && !termRaw.isEmpty()) {
            List<Integer> esIds = elasticsearch.searchPublicProductIds(
                termRaw,
                selectedCategoryDb,
                selectedPrice.min,
                selectedPrice.max,
                guestSession,
                200
            );
            if (!esIds.isEmpty()) {
                elasticsearchRelevanceOrder = true;
                Map<Integer, Product> byId = publishedSource.stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
                filtered = new ArrayList<>();
                for (Integer id : esIds) {
                    Product p = byId.get(id);
                    if (p != null && (!favoritesOnlyMode || favoriteProductIds.contains(p.getId()))) {
                        filtered.add(p);
                    }
                }
            } else {
                filtered = publishedSource.stream()
                    .filter(p -> !favoritesOnlyMode || favoriteProductIds.contains(p.getId()))
                    .filter(this::matchesCategory)
                    .filter(this::matchesPrice)
                    .filter(p -> matchesSearch(p, term))
                    .collect(Collectors.toCollection(ArrayList::new));
            }
        } else {
            filtered = publishedSource.stream()
                .filter(p -> !favoritesOnlyMode || favoriteProductIds.contains(p.getId()))
                .filter(this::matchesCategory)
                .filter(p -> matchesPrice(p))
                .filter(p -> matchesSearch(p, term))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        if (!(elasticsearchRelevanceOrder && sortMode == SortMode.DEFAULT)) {
            sortList(filtered);
        }

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
        boolean loggedIn = session != null;
        if (createProductBtn != null) {
            createProductBtn.setDisable(!loggedIn);
        }
        if (favoritesToggleBtn != null) {
            favoritesToggleBtn.setVisible(false);
            favoritesToggleBtn.setManaged(false);
        }
        for (Product p : filtered) {
            productsFlow.getChildren().add(buildPublicCard(p, stockNames, session));
        }
    }

    private void updateRecommendationsSection() {
        if (recommendationsRow == null || recommendationsSection == null) {
            return;
        }
        if (!elasticsearch.isUsable() || publishedSource.isEmpty()) {
            recommendationsRow.getChildren().clear();
            recommendationsSection.setVisible(false);
            recommendationsSection.setManaged(false);
            return;
        }
        boolean guest = AppState.getCurrentUser() == null;
        String cat = selectedCategoryDb != null && !selectedCategoryDb.isBlank() ? selectedCategoryDb : "";
        new Thread(() -> {
            try {
                List<Integer> ids = elasticsearch.recommendPublicIds(guest, cat, Set.of(), 6);
                Platform.runLater(() -> fillRecommendationRow(ids));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    recommendationsRow.getChildren().clear();
                    recommendationsSection.setVisible(false);
                    recommendationsSection.setManaged(false);
                });
            }
        }, "es-recommend").start();
    }

    private void fillRecommendationRow(List<Integer> ids) {
        if (recommendationsRow == null || recommendationsSection == null) {
            return;
        }
        recommendationsRow.getChildren().clear();
        Map<Integer, String> stockNames = loadStockNames();
        User session = AppState.getCurrentUser();
        Map<Integer, Product> byId = publishedSource.stream()
            .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (Integer id : ids) {
            Product p = byId.get(id);
            if (p != null) {
                recommendationsRow.getChildren().add(buildPublicCard(p, stockNames, session));
            }
        }
        boolean show = !recommendationsRow.getChildren().isEmpty();
        recommendationsSection.setVisible(show);
        recommendationsSection.setManaged(show);
    }

    private void appendSuggestionBlocks(User session, List<Product> filtered, Map<Integer, String> stockNames) {
        if (productsFlow == null) {
            return;
        }
        List<Product> suggestions = buildGroqTopRatedSuggestions(session, 6);
        appendSuggestionBlock(
            "Suggestions (4★ et plus)",
            suggestions,
            stockNames,
            session,
            "Aucune suggestion disponible pour le moment (produits notés < 4★ ou absence de données)."
        );
    }

    private List<Product> buildGroqTopRatedSuggestions(User session, int limit) {
        Map<Integer, Integer> myRatings = Map.of();
        if (session != null) {
            try {
                myRatings = avisProduitService.listNotesByUser(session.getId());
            } catch (SQLException ignored) {
                myRatings = Map.of();
            }
        }
        final Map<Integer, Integer> ratings = myRatings;
        List<Product> topRatedCandidates = publishedSource.stream()
            .filter(p -> p.getNoteMoyenne() != null && p.getNoteMoyenne() >= 4.0)
            .filter(p -> !ratings.containsKey(p.getId()))
            .sorted(Comparator
                .comparingDouble((Product p) -> p.getNoteMoyenne() == null ? 0.0 : p.getNoteMoyenne()).reversed()
                .thenComparingInt(Product::getId).reversed())
            .limit(20)
            .toList();
        if (topRatedCandidates.isEmpty()) {
            return List.of();
        }
        List<Product> likedByUser = ratings.entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue() >= 4)
            .map(e -> publishedSource.stream().filter(p -> p.getId() == e.getKey()).findFirst().orElse(null))
            .filter(p -> p != null)
            .toList();
        List<Product> reranked = rerankCandidatesWithGroq(likedByUser, topRatedCandidates, limit);
        if (!reranked.isEmpty()) {
            return reranked;
        }
        return topRatedCandidates.stream().limit(limit).toList();
    }

    private List<Product> rerankCandidatesWithGroq(List<Product> liked, List<Product> candidates, int limit) {
        if (candidates == null || candidates.isEmpty() || !GroqChatCompletionService.hasApiKeyConfigured()) {
            return List.of();
        }
        try {
            String likedBlock = liked.stream()
                .limit(12)
                .map(p -> p.getId() + " | " + safe(p.getNom()) + " | " + safe(p.getCategorie()) + " | usage: " + safe(p.getDescription()))
                .collect(Collectors.joining("\n"));
            String candBlock = candidates.stream()
                .map(p -> p.getId() + " | " + safe(p.getNom()) + " | " + safe(p.getCategorie()) + " | usage: " + safe(p.getDescription()))
                .collect(Collectors.joining("\n"));

            String prompt = """
                Produits déjà appréciés par l'utilisateur :
                %s

                Candidats à classer :
                %s

                Classe les candidats par pertinence selon :
                - même catégorie
                - même type de produit
                - même usage
                - garder uniquement des suggestions avec qualité perçue élevée (déjà notées >= 4 étoiles côté catalogue)
                Réponds uniquement avec une liste CSV de %d IDs maximum.
                """.formatted(likedBlock.isBlank() ? "(aucun like explicite, utilise catégorie/usage populaires)" : likedBlock, candBlock, limit);

            String answer = groqChatService.completeWithSystem(
                List.of(new GroqChatMessage("user", prompt)),
                "Tu es un re-ranker e-commerce. Réponds uniquement avec des IDs séparés par des virgules.",
                0.2,
                0.9,
                140
            );
            List<Integer> ids = parseIds(answer);
            Map<Integer, Product> byId = candidates.stream()
                .collect(Collectors.toMap(Product::getId, x -> x, (x, y) -> x, LinkedHashMap::new));
            List<Product> out = new ArrayList<>();
            for (Integer id : ids) {
                Product p = byId.get(id);
                if (p != null) {
                    out.add(p);
                }
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Integer> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<Integer> out = new LinkedHashSet<>();
        String[] parts = raw.split("[^0-9]+");
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            try {
                out.add(Integer.parseInt(p));
            } catch (NumberFormatException ignored) {
                // —
            }
        }
        return new ArrayList<>(out);
    }

    private void appendSuggestionBlock(
        String titleText,
        List<Product> products,
        Map<Integer, String> stockNames,
        User session,
        String emptyMessage
    ) {
        Label spacer = new Label(" ");
        spacer.setPadding(new Insets(6, 0, 0, 0));
        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 700; -fx-text-fill: #1f2937;");
        title.setMinWidth(900);
        title.setWrapText(true);
        productsFlow.getChildren().addAll(spacer, title);
        if (products == null || products.isEmpty()) {
            Label empty = new Label(emptyMessage);
            empty.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #64748b;");
            empty.setMinWidth(900);
            empty.setWrapText(true);
            productsFlow.getChildren().add(empty);
            return;
        }
        for (Product p : products) {
            productsFlow.getChildren().add(buildPublicCard(p, stockNames, session));
        }
    }

    private static String safe(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').replace('|', ' ').replace(',', ' ');
        if (compact.length() > 140) {
            return compact.substring(0, 140) + "…";
        }
        return compact.trim();
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
            if (favoritesOnlyMode) {
                favoritesOnlyMode = false;
                if (favoritesToggleBtn != null) {
                    favoritesToggleBtn.setText("Favoris");
                }
            }
            return;
        }
        try {
            favoriteProductIds = new HashSet<>(favorisService.favoriteProductIdsForUser(u.getId()));
            if (favoritesOnlyMode && favoriteProductIds.isEmpty()) {
                favoritesOnlyMode = false;
                if (favoritesToggleBtn != null) {
                    favoritesToggleBtn.setText("Favoris");
                }
            }
        } catch (SQLException e) {
            favoriteProductIds = Set.of();
        }
    }

    private void sortList(List<Product> list) {
        if (sortMode == SortMode.RATING_DESC) {
            list.removeIf(p -> p.getNoteMoyenne() == null || p.getNoteMoyenne() < 4.0);
        } else if (sortMode == SortMode.TOP_SOLD) {
            list.removeIf(p -> soldQtyByProductId.getOrDefault(p.getId(), 0) <= 0);
        }

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
            case RATING_DESC -> Comparator
                .comparingDouble((Product p) -> p.getNoteMoyenne() == null ? 0.0 : p.getNoteMoyenne())
                .reversed()
                .thenComparingInt(Product::getId).reversed();
            case TOP_SOLD -> Comparator
                .comparingInt((Product p) -> soldQtyByProductId.getOrDefault(p.getId(), 0))
                .reversed()
                .thenComparingInt(Product::getId).reversed();
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
        card.setStyle(card.getStyle() + "-fx-cursor: hand;");

        StackPane imgFrame = new StackPane();
        imgFrame.setMinSize(imgW, imgH);
        imgFrame.setMaxSize(imgW, imgH);
        imgFrame.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8fafc, #f1f5f9); "
                + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #e2e8f0; -fx-border-width: 1;"
        );
        enforceImageFrameClip(imgFrame, 12);

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

        Label noteLbl = new Label(starsFromAverage(p.getNoteMoyenne()));
        noteLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #f59e0b; -fx-font-weight: 700;");

        String stockNom = stockNames.get(p.getStockId());
        String stockLine = stockNom != null && !stockNom.isBlank()
            ? "Stock : " + stockNom
            : "Emplacement catalogue";
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
            noteLbl,
            stockLbl,
            addCart
        );
        VBox.setVgrow(textCol, Priority.ALWAYS);

        card.getChildren().addAll(imgFrame, textCol);
        card.setOnMouseClicked(e -> {
            if (isButtonClickTarget(e.getTarget())) {
                return;
            }
            AppState.markProductInterest(p.getId());
            showPublicProductDetailWindow(p, stockNames, session);
        });
        return card;
    }

    private static boolean isButtonClickTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node cur = node;
        while (cur != null) {
            if (cur instanceof Button) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    private void showPublicProductDetailWindow(Product p, Map<Integer, String> stockNames, User session) {
        if (p == null) {
            return;
        }
        Window owner = productsFlow != null && productsFlow.getScene() != null ? productsFlow.getScene().getWindow() : null;
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(p.getNom() != null && !p.getNom().isBlank() ? p.getNom() : "Fiche produit");

        final int imgMaxW = 760;
        final int imgMaxH = 460;

        VBox galleryBox = buildProductGallery(p, imgMaxW, imgMaxH);

        ProductCategoryChoice cc = ProductFormUi.resolveCategoryChoice(p.getCategorie());
        Label nameLbl = new Label(p.getNom() != null ? p.getNom() : "—");
        nameLbl.setWrapText(true);
        nameLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
        priceLbl.setStyle("-fx-font-size: 19px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");

        Label ratingValueLbl = new Label(
            p.getNoteMoyenne() != null
                ? String.format(Locale.FRENCH, "Note moyenne : %.1f / 5", p.getNoteMoyenne())
                : "Pas encore d’avis"
        );
        ratingValueLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        HBox starsRow = new HBox(4);
        starsRow.setAlignment(Pos.CENTER_LEFT);
        if (session != null) {
            Integer existing = null;
            try {
                existing = avisProduitService.findNoteByUserAndProduct(session.getId(), p.getId());
            } catch (SQLException ignored) {
                // —
            }
            final int[] selected = {existing == null ? 0 : Math.max(1, Math.min(5, existing))};
            final Button[] starButtons = new Button[5];
            for (int i = 0; i < 5; i++) {
                final int note = i + 1;
                Button star = new Button("★");
                star.setMnemonicParsing(false);
                star.setStyle(starStyle(note <= selected[0]));
                star.setOnAction(e -> {
                    try {
                        avisProduitService.saveOrUpdateNote(session.getId(), p.getId(), note);
                        selected[0] = note;
                        for (int j = 0; j < 5; j++) {
                            starButtons[j].setStyle(starStyle((j + 1) <= selected[0]));
                        }
                        productService.findById(p.getId()).ifPresent(updated -> {
                            p.setNoteMoyenne(updated.getNoteMoyenne());
                            if (updated.getNoteMoyenne() != null) {
                                ratingValueLbl.setText(String.format(Locale.FRENCH, "Note moyenne : %.1f / 5", updated.getNoteMoyenne()));
                            } else {
                                ratingValueLbl.setText("Pas encore d’avis");
                            }
                        });
                        applyFiltersAndRender();
                    } catch (SQLException ex) {
                        cartAlert(Alert.AlertType.ERROR, "Avis produit", ex.getMessage());
                    }
                });
                starButtons[i] = star;
                starsRow.getChildren().add(star);
            }
        } else {
            Label loginHint = new Label("Connectez-vous pour noter ce produit.");
            loginHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
            starsRow.getChildren().add(loginHint);
        }

        Label catLbl = new Label("Catégorie : " + cc.getLabel());
        catLbl.setWrapText(true);
        catLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");

        Label descLbl = new Label(
            p.getDescription() == null || p.getDescription().isBlank() ? "—" : p.getDescription()
        );
        descLbl.setWrapText(true);
        descLbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #374151; -fx-line-spacing: 4px;");

        String stockNom = stockNames.get(p.getStockId());
        String stockLine = stockNom != null && !stockNom.isBlank()
            ? "Stock : " + stockNom
            : "Emplacement catalogue";
        Label stockLbl = new Label(stockLine);
        stockLbl.setWrapText(true);
        stockLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a;");

        int maxCart = cartService.getAvailableQuantityForCart(p);
        boolean canOrder = p.isDisponible() && maxCart > 0;

        Button addCartBtn = new Button(canOrder ? "Ajouter au panier" : "Indisponible");
        addCartBtn.setMnemonicParsing(false);
        addCartBtn.getStyleClass().add("catalog-add-cart-btn");
        addCartBtn.setDisable(!canOrder);
        addCartBtn.setOnAction(e -> addProductToCart(p));

        Button backBtn = new Button("Retour");
        backBtn.setMnemonicParsing(false);
        backBtn.getStyleClass().add("cart-secondary-btn");
        backBtn.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(10, addCartBtn, spacer, backBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        if (session == null) {
            Label hint = new Label("Veuillez vous connecter pour effectuer cette action.");
            hint.setWrapText(true);
            hint.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #b91c1c;");
            actionRow = new HBox(10, spacer, backBtn);
            actionRow.setAlignment(Pos.CENTER_RIGHT);
            VBox body = new VBox(14, galleryBox, nameLbl, priceLbl, ratingValueLbl, starsRow, catLbl, descLbl, stockLbl, hint, actionRow);
            body.setPadding(new Insets(6, 0, 0, 0));
            showProductDetailStage(stage, body);
            return;
        }

        VBox body = new VBox(14, galleryBox, nameLbl, priceLbl, ratingValueLbl, starsRow, catLbl, descLbl, stockLbl, actionRow);
        body.setPadding(new Insets(6, 0, 0, 0));
        showProductDetailStage(stage, body);
    }

    private static String starStyle(boolean active) {
        if (active) {
            return "-fx-background-color: transparent; -fx-text-fill: #f59e0b; -fx-font-size: 20px; -fx-padding: 0 2 0 2; -fx-cursor: hand;";
        }
        return "-fx-background-color: transparent; -fx-text-fill: #d1d5db; -fx-font-size: 20px; -fx-padding: 0 2 0 2; -fx-cursor: hand;";
    }

    private static String starsFromAverage(Double avg) {
        if (avg == null || avg <= 0) {
            return "☆☆☆☆☆";
        }
        double clamped = Math.max(0, Math.min(5, avg));
        int full = (int) Math.floor(clamped);
        double fraction = clamped - full;
        boolean half = fraction >= 0.25 && fraction < 0.75;
        if (fraction >= 0.75 && full < 5) {
            full++;
            half = false;
        }
        StringBuilder sb = new StringBuilder(5);
        for (int i = 0; i < full && sb.length() < 5; i++) {
            sb.append('★');
        }
        if (half && sb.length() < 5) {
            sb.append('⯪');
        }
        while (sb.length() < 5) {
            sb.append('☆');
        }
        return sb.toString();
    }

    private VBox buildProductGallery(Product p, int imgMaxW, int imgMaxH) {
        List<String> images = p.getImagePaths();
        int[] selectedIndex = {0};

        StackPane imgHost = new StackPane();
        imgHost.setMaxWidth(imgMaxW);
        imgHost.setMinHeight(imgMaxH + 8);
        imgHost.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8fafc, #f1f5f9); "
                + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #e2e8f0; -fx-border-width: 1;"
        );

        Runnable refreshMain = () -> {
            imgHost.getChildren().clear();
            if (images.isEmpty()) {
                imgHost.getChildren().add(ProductImagePlaceholder.create(imgMaxW, imgMaxH));
                return;
            }
            int idx = Math.max(0, Math.min(selectedIndex[0], images.size() - 1));
            Image img = ProductImageLoader.loadForDisplay(images.get(idx), imgMaxW, imgMaxH);
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
        };
        refreshMain.run();

        VBox thumbs = new VBox(8);
        thumbs.setPrefWidth(96);
        thumbs.setMinWidth(96);
        thumbs.setMaxWidth(96);
        for (int i = 0; i < images.size(); i++) {
            final int idx = i;
            StackPane thumbHost = new StackPane();
            thumbHost.setPrefSize(86, 72);
            thumbHost.setMinSize(86, 72);
            thumbHost.setMaxSize(86, 72);
            thumbHost.setStyle(
                "-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-width: 1; "
                    + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
            );
            Image thumb = ProductImageLoader.loadForDisplay(images.get(i), 78, 62);
            if (thumb != null && !thumb.isError()) {
                ImageView iv = new ImageView(thumb);
                iv.setFitWidth(78);
                iv.setFitHeight(62);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                thumbHost.getChildren().add(iv);
            } else {
                thumbHost.getChildren().add(ProductImagePlaceholder.create(78, 62));
            }
            thumbHost.setOnMouseClicked(e -> {
                selectedIndex[0] = idx;
                refreshMain.run();
                for (Node n : thumbs.getChildren()) {
                    n.setStyle(
                        "-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-border-width: 1; "
                            + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
                    );
                }
                thumbHost.setStyle(
                    "-fx-background-color: #eef6ff; -fx-border-color: #2563eb; -fx-border-width: 2; "
                        + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
                );
            });
            if (i == 0) {
                thumbHost.setStyle(
                    "-fx-background-color: #eef6ff; -fx-border-color: #2563eb; -fx-border-width: 2; "
                        + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
                );
            }
            thumbs.getChildren().add(thumbHost);
        }

        if (images.size() <= 1) {
            return new VBox(8, imgHost);
        }
        HBox galleryRow = new HBox(12, thumbs, imgHost);
        galleryRow.setAlignment(Pos.TOP_LEFT);
        Label hint = new Label("Cliquez sur une miniature (à gauche) pour changer l'image principale.");
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        return new VBox(8, galleryRow, hint);
    }

    private static void showProductDetailStage(Stage stage, VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");
        VBox root = new VBox(scroll);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: white;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Scene scene = new Scene(root, 900, 760);
        stage.setScene(scene);
        stage.show();
    }

    private static void enforceImageFrameClip(StackPane frame, double radius) {
        if (frame == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        frame.setClip(clip);
    }

    private void addProductToCart(Product p) {
        if (p == null) {
            return;
        }
        User u = AppState.getCurrentUser();
        if (u == null) {
            cartAlert(Alert.AlertType.INFORMATION, "Connexion requise", LOGIN_REQUIRED_ACTION_MESSAGE);
            return;
        }
        try {
            cartService.addOne(u.getId(), p.getId());
            AppState.markProductInterest(p.getId());
            AppState.notifyCartChanged();
            navigateToPanier();
        } catch (SQLException ex) {
            if ("STOCK_OUT".equals(ex.getMessage())) {
                cartAlert(Alert.AlertType.WARNING, "Stock", "Stock insuffisant ou produit indisponible.");
            } else {
                cartAlert(Alert.AlertType.ERROR, "Panier", ex.getMessage());
            }
        }
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
        PRICE_DESC,
        RATING_DESC,
        TOP_SOLD
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

    private record ScoredProduct(Product product, double score) {
    }

    private record SortOption(String label, SortMode mode) {
        @Override
        public String toString() {
            return label;
        }
    }

}
