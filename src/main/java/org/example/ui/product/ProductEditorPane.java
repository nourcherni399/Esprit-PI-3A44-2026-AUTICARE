package org.example.ui.product;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.models.Product;
import org.example.models.Stock;
import org.example.utils.ProductDescriptionSuggest;
import org.example.utils.ProductImageStorage;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Formulaire produit : nom, description, catégorie, prix, stock, image (fichier local uniquement).
 */
public final class ProductEditorPane {

    public final TextField nomField;
    public final TextArea descriptionArea;
    public final Button suggestDescriptionButton;
    public final TextField prixField;
    /** Valeurs enum MySQL {@code produit.categorie}. */
    public final ComboBox<ProductFormUi.ProductCategoryChoice> categoryCombo;
    public final ComboBox<Stock> stockCombo;
    public final TextField imagePathField;
    public final Button chooseImageButton;

    private final boolean editMode;
    private final int initialCatalogQuantity;
    private final Consumer<String> onImageError;

    public ProductEditorPane(Product existing, ObservableList<Stock> stocks, Consumer<String> onImageError) {
        this.onImageError = onImageError != null ? onImageError : (s -> { /* no-op */ });
        this.editMode = existing != null && existing.getId() > 0;
        this.initialCatalogQuantity = editMode ? Math.max(0, existing.getStock()) : 1;

        nomField = new TextField();
        nomField.setPromptText("Ex: Coussin sensoriel leste");
        ProductFormUi.styleInputAddProduct(nomField);

        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Description du produit…");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);
        ProductFormUi.styleInputAddProduct(descriptionArea);

        suggestDescriptionButton = new Button("Suggérer une description");
        suggestDescriptionButton.setMnemonicParsing(false);
        suggestDescriptionButton.setStyle(
            "-fx-background-color: #95c6ee; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-size: 15px; -fx-padding: 8 14 8 14;"
        );

        categoryCombo = new ComboBox<>(FXCollections.observableArrayList(ProductFormUi.getProductCategories()));
        categoryCombo.setPromptText("Choisir une catégorie");
        ProductFormUi.styleStockCombo(categoryCombo);

        prixField = new TextField();
        prixField.setPromptText("Ex. 12,50 ou 12.5 (DT) — décimal avec , ou .");
        ProductFormUi.styleInputAddProduct(prixField);
        UnaryOperator<TextFormatter.Change> prixDecimalInput = c -> {
            if (!c.isContentChange()) {
                return c;
            }
            String nt = c.getControlNewText();
            if (nt.isEmpty()) {
                return c;
            }
            return nt.matches("[0-9]*([.,][0-9]*)?") ? c : null;
        };
        prixField.setTextFormatter(new TextFormatter<>(prixDecimalInput));

        stockCombo = new ComboBox<>(stocks);
        stockCombo.setPromptText("Choisir un stock");
        ProductFormUi.styleStockCombo(stockCombo);

        imagePathField = new TextField();
        imagePathField.setEditable(false);
        imagePathField.setPromptText("Aucun fichier choisi");
        ProductFormUi.styleInputAddProduct(imagePathField);

        chooseImageButton = new Button("Choisir un fichier");
        chooseImageButton.setStyle("-fx-font-size: 15px; -fx-padding: 8 14 8 14;");

        if (editMode) {
            nomField.setText(existing.getNom() == null ? "" : existing.getNom());
            descriptionArea.setText(existing.getDescription() == null ? "" : existing.getDescription());
            categoryCombo.setValue(ProductFormUi.resolveCategoryChoice(existing.getCategorie()));
            if (existing.getPrix() > 0) {
                NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRENCH);
                nf.setGroupingUsed(false);
                nf.setMaximumFractionDigits(10);
                nf.setMinimumFractionDigits(0);
                prixField.setText(nf.format(existing.getPrix()));
            }
            if (existing.getImagePath() != null && !existing.getImagePath().isBlank()) {
                imagePathField.setText(existing.getImagePath().trim());
            }
            for (Stock s : stocks) {
                if (s.getId() == existing.getStockId()) {
                    stockCombo.setValue(s);
                    break;
                }
            }
        } else {
            categoryCombo.setValue(ProductFormUi.getDefaultCategoryChoice());
        }

        suggestDescriptionButton.setOnAction(e ->
            descriptionArea.setText(
                ProductDescriptionSuggest.generate(
                    nomField.getText(),
                    categoryCombo.getValue() != null ? categoryCombo.getValue().getDbValue() : ProductFormUi.DEFAULT_CATEGORY
                )
            )
        );

        chooseImageButton.setOnAction(e -> {
            Window w = nomField.getScene() != null ? nomField.getScene().getWindow() : null;
            if (w == null) {
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choisir l'image principale");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                    "Images",
                    "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif", "*.bmp", "*.tif", "*.tiff"
                )
            );
            File file = chooser.showOpenDialog(w);
            if (file == null) {
                return;
            }
            try {
                imagePathField.setText(ProductImageStorage.copyProductImage(file));
            } catch (IOException ex) {
                onImageError.accept(ex.getMessage() == null ? "Erreur copie image" : ex.getMessage());
            }
        });
    }

    public boolean isEditMode() {
        return editMode;
    }

    /** Quantité catalogue : 1 à la création, quantité existante en modification. */
    public int getCatalogQuantityForSave() {
        return editMode ? initialCatalogQuantity : 1;
    }

    /**
     * Titre + grille formulaire (style page ppp).
     */
    public VBox createFormSection() {
        Label title = new Label(editMode ? "Modifier le produit" : "Nouveau produit");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2a2a2a;");

        GridPane form = buildGrid();
        VBox page = new VBox(14, title, form);
        page.setPadding(Insets.EMPTY);
        page.setMaxWidth(640);
        return page;
    }

    private GridPane buildGrid() {
        String help = editMode
            ? "Choisissez l’emplacement dans la liste."
            : "Choisissez le nom dans la liste (aucune saisie manuelle). "
                + "Chaque ajout crée le produit avec 1 unité et retire 1 unité au stock choisi.";
        Label stockHelp = new Label(help);
        stockHelp.setWrapText(true);
        stockHelp.setStyle("-fx-text-fill: #4b5563; -fx-font-size: 15px;");
        VBox stockBox = new VBox(8, stockCombo, stockHelp);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(220);
        labelCol.setHgrow(Priority.NEVER);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        grid.add(ProductFormUi.formLabel("Nom du produit"), 0, 0);
        grid.add(nomField, 1, 0);
        grid.add(ProductFormUi.formLabel("Description"), 0, 1);
        grid.add(suggestDescriptionButton, 1, 1);
        grid.add(descriptionArea, 1, 2);
        grid.add(ProductFormUi.formLabel("Catégorie"), 0, 3);
        grid.add(categoryCombo, 1, 3);
        grid.add(ProductFormUi.formLabel("Prix (DT)"), 0, 4);
        grid.add(prixField, 1, 4);
        grid.add(ProductFormUi.formLabel("Stock"), 0, 5);
        grid.add(stockBox, 1, 5);

        Label imageTitle = ProductFormUi.formLabel("Image du produit");
        Label imageHint = new Label(
            "Choisissez une image sur votre ordinateur. Elle est copiée dans le dossier du projet pour l’affichage catalogue."
        );
        imageHint.setWrapText(true);
        imageHint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        HBox fileRow = new HBox(8, chooseImageButton, imagePathField);
        HBox.setHgrow(imagePathField, Priority.ALWAYS);
        VBox imageBlock = new VBox(10, imageHint, fileRow);
        grid.add(imageTitle, 0, 6);
        grid.add(imageBlock, 1, 6);
        return grid;
    }
}
