package org.example.controllers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.fxml.FXML;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.models.CartLineView;
import org.example.models.Product;
import org.example.models.User;
import org.example.services.CartService;
import org.example.utils.AppState;
import org.example.utils.ProductImageLoader;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Panier client (équivalent {@code templates/front/cart/index.html.twig} + {@code CartController} Symfony).
 */
public class PagePanierController implements PublicShellAware {

    private PublicShellController shell;
    private final CartService cartService = new CartService();

    @FXML
    private VBox cartRoot;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
        rebuild();
    }

    @FXML
    private void initialize() {
        /* rebuild() après injection de la coque via {@link #setPublicShell} */
    }

    private void rebuild() {
        if (cartRoot == null) {
            return;
        }
        cartRoot.getChildren().clear();

        VBox hero = new VBox(10);
        hero.setAlignment(Pos.TOP_CENTER);
        hero.getStyleClass().add("catalog-hero");
        Label kicker = new Label("PANIER");
        kicker.getStyleClass().add("public-page-kicker");
        Label title = new Label("Mon panier");
        title.getStyleClass().add("public-page-title");
        Label subtitle = new Label("Gérez vos articles avant de passer commande.");
        subtitle.getStyleClass().add("public-page-lead");
        subtitle.setWrapText(true);
        hero.getChildren().addAll(kicker, title, subtitle);
        cartRoot.getChildren().add(hero);

        List<CartLineView> lines;
        try {
            User u = AppState.getCurrentUser();
            if (u != null) {
                lines = cartService.loadCartLinesForUser(u.getId());
            } else {
                lines = cartService.loadCartLinesForGuest(AppState.guestCartSnapshot());
            }
            lines = syncCartLinesIfNeeded(u, lines);
        } catch (SQLException e) {
            VBox errBox = new VBox(8);
            errBox.getStyleClass().add("rdv-content-card");
            errBox.setPadding(new Insets(24));
            Label err = new Label(
                "Impossible de charger le panier. Vérifiez que les tables MySQL « cart » et « cart_item » existent.\n"
                    + e.getMessage()
            );
            err.setWrapText(true);
            err.setStyle("-fx-text-fill: #b91c1c;");
            errBox.getChildren().add(err);
            cartRoot.getChildren().add(errBox);
            return;
        }

        if (lines.isEmpty()) {
            VBox card = new VBox();
            card.getStyleClass().add("rdv-content-card");
            card.setAlignment(Pos.TOP_CENTER);
            VBox empty = new VBox(16);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("cart-empty-box");
            Label emptyTitle = new Label("Panier vide");
            emptyTitle.getStyleClass().add("cart-empty-title");
            Label emptyText = new Label("Votre panier ne contient aucun produit pour le moment.");
            emptyText.getStyleClass().add("cart-empty-text");
            emptyText.setWrapText(true);
            Button shop = new Button("Voir les produits");
            shop.getStyleClass().add("cart-primary-btn");
            shop.setMaxWidth(280);
            shop.setOnAction(e -> openProduits());
            empty.getChildren().addAll(emptyTitle, emptyText, shop);
            card.getChildren().add(empty);
            cartRoot.getChildren().add(card);
            return;
        }

        int totalItems = CartService.totalItems(lines);
        double totalPrice = CartService.totalPrice(lines);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setMinViewportHeight(320);
        scroll.setPrefViewportHeight(480);
        scroll.getStyleClass().add("cart-scroll");
        VBox listBox = new VBox(12);
        listBox.setPadding(new Insets(4, 0, 8, 0));
        for (CartLineView line : lines) {
            listBox.getChildren().add(buildLineRow(line));
        }
        scroll.setContent(listBox);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox linesColumn = new VBox();
        linesColumn.getStyleClass().add("cart-lines-column");
        HBox.setHgrow(linesColumn, Priority.ALWAYS);
        linesColumn.getChildren().add(scroll);

        VBox summaryInner = new VBox(12);
        summaryInner.getStyleClass().add("cart-summary-inner");
        Label recapTitle = new Label("Récapitulatif");
        recapTitle.getStyleClass().add("rdv-filters-title");

        HBox rowItems = summaryRow("Nombre d'articles :", String.valueOf(totalItems));
        HBox rowSub = summaryRow("Sous-total :", String.format(Locale.FRENCH, "%.2f DT", totalPrice));
        Separator sep = new Separator();
        HBox rowTotal = summaryRow("Total :", String.format(Locale.FRENCH, "%.2f DT", totalPrice));
        rowTotal.getStyleClass().add("cart-summary-total-row");

        Button checkout = new Button("Passer la commande");
        checkout.getStyleClass().add("cart-primary-btn");
        checkout.setMaxWidth(Double.MAX_VALUE);
        checkout.setOnAction(e -> onCheckout());

        Button cont = new Button("Continuer les achats");
        cont.getStyleClass().add("cart-secondary-btn");
        cont.setMaxWidth(Double.MAX_VALUE);
        cont.setOnAction(e -> openProduits());

        Button clear = new Button("Vider le panier");
        clear.getStyleClass().add("cart-danger-btn");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(e -> onClearCart());

        VBox actions = new VBox(10, checkout, cont, clear);
        summaryInner.getChildren().addAll(recapTitle, rowItems, rowSub, sep, rowTotal, actions);

        VBox summaryColumn = new VBox();
        summaryColumn.getStyleClass().add("cart-summary-panel");
        summaryColumn.getChildren().add(summaryInner);

        HBox mainRow = new HBox(28);
        mainRow.setAlignment(Pos.TOP_LEFT);
        mainRow.getStyleClass().add("rdv-two-col");
        mainRow.getChildren().addAll(linesColumn, summaryColumn);

        VBox card = new VBox();
        card.getStyleClass().add("rdv-content-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(mainRow);

        cartRoot.getChildren().add(card);
    }

    private static HBox summaryRow(String left, String right) {
        Label l = new Label(left);
        l.getStyleClass().add("cart-summary-label");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label r = new Label(right);
        r.getStyleClass().add("cart-summary-value");
        HBox row = new HBox(12, l, sp, r);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildLineRow(CartLineView line) {
        Product p = line.product();
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 14));
        row.getStyleClass().add("cart-line-row");

        StackPane thumb = new StackPane();
        thumb.setMinSize(56, 56);
        thumb.setMaxSize(56, 56);
        thumb.getStyleClass().add("cart-line-thumb");
        String imgPath = p.getImagePath();
        if (imgPath != null && !imgPath.isBlank()) {
            Image im = ProductImageLoader.loadForDisplay(imgPath, 112, 112);
            if (im != null && !im.isError()) {
                ImageView iv = new ImageView(im);
                iv.setFitWidth(56);
                iv.setFitHeight(56);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                thumb.getChildren().add(iv);
            }
        }
        if (thumb.getChildren().isEmpty()) {
            thumb.getChildren().add(ProductImagePlaceholder.create(56, 56));
        }

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nom = new Label(p.getNom() != null ? p.getNom() : "—");
        nom.getStyleClass().add("cart-line-name");
        nom.setWrapText(true);
        Label pu = new Label(String.format(Locale.FRENCH, "%.2f DT", line.unitPrice()));
        pu.getStyleClass().add("cart-line-pu");

        int maxStock = Math.max(1, cartService.getAvailableQuantityForCart(p));
        int initialQty = Math.min(line.quantity(), maxStock);
        Spinner<Integer> sp = new Spinner<>();
        sp.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxStock, initialQty));
        sp.setEditable(true);
        sp.setPrefWidth(96);
        sp.setMaxWidth(96);
        sp.setMinWidth(96);
        Runnable applySpinnerQty = () -> {
            try {
                sp.commitValue();
            } catch (IllegalArgumentException ignored) {
                // —
            }
            Integer n = sp.getValue();
            if (n != null && n >= 1 && n != initialQty) {
                applyQtyChange(p.getId(), n);
            }
        };
        sp.getEditor().setOnAction(e -> applySpinnerQty.run());
        sp.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                applySpinnerQty.run();
            }
        });
        HBox qtyRow = new HBox(6, sp);
        qtyRow.setAlignment(Pos.CENTER_LEFT);

        Label lineTot = new Label(String.format(Locale.FRENCH, "%.2f DT", line.lineTotal()));
        lineTot.getStyleClass().add("cart-line-total");

        Label qtyCaption = new Label("Qté");
        qtyCaption.getStyleClass().add("rdv-filter-label");
        VBox qtyCol = new VBox(4, qtyCaption, qtyRow);
        qtyCol.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(thumb, info, qtyCol, lineTot);
        info.getChildren().addAll(nom, pu);
        return row;
    }

    /**
     * Si les quantités en session / panier dépassent la disponibilité réelle (stock modifié entre-temps),
     * on les ramène au plafond ou on retire la ligne.
     */
    private List<CartLineView> syncCartLinesIfNeeded(User u, List<CartLineView> lines) throws SQLException {
        boolean changed = false;
        for (CartLineView line : lines) {
            int avail = cartService.getAvailableQuantityForCart(line.product());
            if (line.quantity() <= avail) {
                continue;
            }
            changed = true;
            int pid = line.product().getId();
            if (u != null) {
                if (avail < 1) {
                    cartService.removeProduct(u.getId(), pid);
                } else {
                    cartService.setQuantity(u.getId(), pid, avail);
                }
            } else {
                if (avail < 1) {
                    AppState.guestCartRemove(pid);
                } else {
                    AppState.guestCartSetQty(pid, avail);
                }
            }
        }
        if (!changed) {
            return lines;
        }
        if (u != null) {
            return cartService.loadCartLinesForUser(u.getId());
        }
        return cartService.loadCartLinesForGuest(AppState.guestCartSnapshot());
    }

    private void applyQtyChange(int productId, int qty) {
        try {
            User u = AppState.getCurrentUser();
            if (u != null) {
                cartService.setQuantity(u.getId(), productId, qty);
            } else {
                int clamped = cartService.clampDesiredQuantityForCart(productId, qty);
                if (clamped < 1) {
                    AppState.guestCartRemove(productId);
                } else {
                    AppState.guestCartSetQty(productId, clamped);
                }
            }
            AppState.notifyCartChanged();
            rebuild();
        } catch (SQLException ex) {
            if ("STOCK_OUT".equals(ex.getMessage())) {
                alert(Alert.AlertType.WARNING, "Stock", "Stock insuffisant.");
            } else {
                alert(Alert.AlertType.ERROR, "Panier", ex.getMessage());
            }
            rebuild();
        }
    }

    private void onRemoveLine(int productId) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Confirmation");
        c.setHeaderText(null);
        c.setContentText("Retirer ce produit du panier ?");
        if (c.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            User u = AppState.getCurrentUser();
            if (u != null) {
                cartService.removeProduct(u.getId(), productId);
            } else {
                AppState.guestCartRemove(productId);
            }
            AppState.notifyCartChanged();
            rebuild();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Panier", ex.getMessage());
        }
    }

    private void onClearCart() {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Vider le panier");
        c.setHeaderText(null);
        c.setContentText("Vider tout le panier ?");
        if (c.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            User u = AppState.getCurrentUser();
            if (u != null) {
                cartService.clearCart(u.getId());
            } else {
                AppState.guestCartClear();
            }
            AppState.notifyCartChanged();
            rebuild();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Panier", ex.getMessage());
        }
    }

    private void onCheckout() {
        if (AppState.getCurrentUser() == null) {
            alert(
                Alert.AlertType.WARNING,
                "Connexion requise",
                "Vous devez être connecté pour passer commande, comme dans le projet Symfony."
            );
            return;
        }
        if (shell != null) {
            try {
                shell.loadPage("checkout");
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Navigation", ex.getMessage());
            }
        }
    }

    private void openProduits() {
        if (shell != null) {
            try {
                shell.loadPage("produits");
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Navigation", ex.getMessage());
            }
        }
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg != null ? msg : "");
        a.showAndWait();
    }
}
