package org.example.controllers;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.models.Product;
import org.example.models.User;
import org.example.services.CartService;
import org.example.services.FavorisService;
import org.example.services.ProductService;
import org.example.services.StockService;
import org.example.models.Stock;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.ui.product.ProductFormUi;
import org.example.utils.AppState;
import org.example.utils.ProductImageLoader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Liste des favoris (produits enregistrés).
 */
public class PageFavorisController implements PublicShellAware {

    @FXML
    private ScrollPane favScroll;
    @FXML
    private FlowPane favoritesFlow;
    @FXML
    private Button refreshBtn;

    private final FavorisService favorisService = new FavorisService();
    private final ProductService productService = new ProductService();
    private final StockService stockService = new StockService();
    private final CartService cartService = new CartService();
    private PublicShellController shell;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        if (favScroll != null && favoritesFlow != null) {
            favoritesFlow.prefWrapLengthProperty().bind(
                Bindings.max(260, favScroll.widthProperty().subtract(40))
            );
        }
        refreshBtn.setOnAction(e -> load());
        Platform.runLater(this::load);
    }

    private void load() {
        if (favoritesFlow == null) {
            return;
        }
        favoritesFlow.getChildren().clear();
        User u = AppState.getCurrentUser();
        if (u == null) {
            Label msg = new Label("Connectez-vous pour voir vos favoris.");
            msg.setStyle("-fx-text-fill: #6b7280;");
            favoritesFlow.getChildren().add(msg);
            return;
        }
        Map<Integer, String> stockNames = new HashMap<>();
        try {
            for (Stock s : stockService.findAll()) {
                stockNames.put(s.getId(), s.getNom());
            }
            List<Integer> ids = favorisService.listProductIdsOrdered(u.getId());
            if (ids.isEmpty()) {
                Label empty = new Label("Aucun favori pour l’instant. Ajoutez des cœurs depuis le catalogue Produits.");
                empty.setWrapText(true);
                empty.setStyle("-fx-text-fill: #6b7280;");
                favoritesFlow.getChildren().add(empty);
                return;
            }
            for (int pid : ids) {
                productService.findById(pid).ifPresent(p -> {
                    if (ProductService.isVisibleOnPublicCatalog(p)) {
                        favoritesFlow.getChildren().add(buildCard(p, stockNames, u));
                    }
                });
            }
        } catch (SQLException ex) {
            Label err = new Label("Erreur : " + ex.getMessage());
            err.setWrapText(true);
            favoritesFlow.getChildren().add(err);
        }
    }

    private VBox buildCard(Product p, Map<Integer, String> stockNames, User u) {
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
        ProductFormUi.ProductCategoryChoice cc = ProductFormUi.resolveCategoryChoice(p.getCategorie());
        Label catLbl = new Label(cc.getLabel());
        catLbl.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a; "
                + "-fx-background-color: rgba(144,166,127,0.2); -fx-background-radius: 8; -fx-padding: 3 8 3 8;"
        );
        Label nameLbl = new Label(p.getNom() != null ? p.getNom() : "—");
        nameLbl.setWrapText(true);
        nameLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        Label priceLbl = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
        priceLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: #0f172a;");
        String stockNom = stockNames.get(p.getStockId());
        String stockLine = stockNom != null && !stockNom.isBlank() ? "Stock : " + stockNom : "Réf. stock #" + p.getStockId();
        Label stockLbl = new Label(stockLine);
        stockLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #5c6d4a;");
        Button removeFav = new Button("Retirer des favoris");
        removeFav.setOnAction(e -> {
            try {
                favorisService.remove(u.getId(), p.getId());
                load();
                AppState.notifyCartChanged();
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Favoris", ex.getMessage());
            }
        });
        Button addCart = new Button("Ajouter au panier");
        addCart.setMaxWidth(Double.MAX_VALUE);
        addCart.getStyleClass().add("catalog-add-cart-btn");
        addCart.setOnAction(e -> addToCart(p));
        VBox textCol = new VBox(6, catLbl, nameLbl, priceLbl, stockLbl, removeFav, addCart);
        VBox.setVgrow(textCol, Priority.ALWAYS);
        card.getChildren().addAll(imgFrame, textCol);
        return card;
    }

    private void addToCart(Product p) {
        User u = AppState.getCurrentUser();
        if (u == null) {
            return;
        }
        try {
            cartService.addOne(u.getId(), p.getId());
            AppState.notifyCartChanged();
            if (shell != null) {
                shell.loadPage("panier");
            }
        } catch (SQLException ex) {
            if ("STOCK_OUT".equals(ex.getMessage())) {
                alert(Alert.AlertType.WARNING, "Stock", "Stock insuffisant.");
            } else {
                alert(Alert.AlertType.ERROR, "Panier", ex.getMessage());
            }
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Navigation", ex.getMessage());
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
