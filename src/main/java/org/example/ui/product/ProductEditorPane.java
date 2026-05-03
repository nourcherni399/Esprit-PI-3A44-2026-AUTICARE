package org.example.ui.product;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.models.Product;
import org.example.models.Stock;
import org.example.utils.ProductImageLoader;
import org.example.utils.ProductDescriptionSuggest;
import org.example.utils.ProductImageStorage;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Formulaire produit : nom, description, catégorie, prix, stock, image(s).
 */
public final class ProductEditorPane {

    public final TextField nomField;
    public final TextArea descriptionArea;
    public final Button suggestDescriptionButton;
    public final Button summarizeDescriptionButton;
    public final TextField prixField;
    /** Valeurs enum MySQL {@code produit.categorie}. */
    public final ComboBox<ProductFormUi.ProductCategoryChoice> categoryCombo;
    public final ComboBox<Stock> stockCombo;
    public final TextField imagePathField;
    public final Button chooseImageButton;
    public final Button useImageUrlButton;
    public final Button replaceImageButton;
    public final Button clearImageButton;
    private final ImageView previewImageView;
    private final Label preview3dHint;
    private final FlowPane imageThumbsPane;
    private final List<String> imagePaths = new ArrayList<>();
    private int selectedImageIndex = -1;

    private final boolean editMode;
    private final int initialCatalogQuantity;
    private final Consumer<String> onImageError;
    private String lastSuggestedDescription = "";

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
        summarizeDescriptionButton = new Button("Résumer la description");
        summarizeDescriptionButton.setMnemonicParsing(false);
        summarizeDescriptionButton.setStyle(
            "-fx-background-color: #64748b; -fx-text-fill: white; -fx-background-radius: 8; "
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
        imagePathField.setEditable(true);
        imagePathField.setPromptText("Collez une ou plusieurs URLs (séparées par ; ou retour ligne)");
        ProductFormUi.styleInputAddProduct(imagePathField);

        chooseImageButton = new Button("Choisir des fichiers");
        chooseImageButton.setStyle("-fx-font-size: 15px; -fx-padding: 10 16 10 16;");
        chooseImageButton.setMinWidth(180);
        chooseImageButton.setMaxWidth(180);
        useImageUrlButton = new Button("Utiliser URL");
        useImageUrlButton.setStyle("-fx-font-size: 15px; -fx-padding: 10 16 10 16;");
        useImageUrlButton.setMinWidth(130);
        useImageUrlButton.setMaxWidth(130);
        replaceImageButton = new Button("Remplacer sélection");
        replaceImageButton.setStyle("-fx-font-size: 15px; -fx-padding: 10 16 10 16;");
        replaceImageButton.setMinWidth(170);
        replaceImageButton.setMaxWidth(170);
        clearImageButton = new Button("Désélectionner");
        clearImageButton.setStyle("-fx-font-size: 15px; -fx-padding: 10 16 10 16;");
        clearImageButton.setMinWidth(140);
        clearImageButton.setMaxWidth(140);

        previewImageView = new ImageView();
        previewImageView.setFitWidth(250);
        previewImageView.setFitHeight(180);
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);
        preview3dHint = new Label("Aperçu image exact (2D) : affichage identique sans déformation.");
        preview3dHint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        imageThumbsPane = new FlowPane(8, 8);
        imageThumbsPane.setPrefWrapLength(600);
        imageThumbsPane.setStyle("-fx-padding: 6 0 4 0;");

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
            setImagePathsFromRaw(existing.getImagePath());
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
        summarizeDescriptionButton.setOnAction(e -> {
            String raw = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();
            if (raw.isBlank()) {
                return;
            }
            if (raw.length() <= 180) {
                return;
            }
            String shortText = raw.substring(0, 180).trim();
            int cut = Math.max(shortText.lastIndexOf('.'), Math.max(shortText.lastIndexOf('!'), shortText.lastIndexOf('?')));
            if (cut > 60) {
                shortText = shortText.substring(0, cut + 1).trim();
            } else {
                shortText = shortText + "...";
            }
            descriptionArea.setText(shortText);
        });

        chooseImageButton.setOnAction(e -> {
            Window w = nomField.getScene() != null ? nomField.getScene().getWindow() : null;
            if (w == null) {
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choisir une ou plusieurs images");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                    "Images (tous formats)",
                    "*.*"
                )
            );
            java.util.List<File> files = chooser.showOpenMultipleDialog(w);
            if (files == null || files.isEmpty()) {
                return;
            }
            try {
                int previousSize = imagePaths.size();
                for (File file : files) {
                    String copied = ProductImageStorage.copyProductImage(file);
                    addImagePath(copied);
                }
                if (selectedImageIndex < 0 && imagePaths.size() > previousSize) {
                    selectedImageIndex = previousSize;
                }
                refreshImagesUi();
            } catch (IOException ex) {
                onImageError.accept(ex.getMessage() == null ? "Erreur copie image" : ex.getMessage());
            }
        });
        useImageUrlButton.setOnAction(e -> {
            String raw = imagePathField.getText() == null ? "" : imagePathField.getText().trim();
            if (raw.isEmpty()) {
                onImageError.accept("Collez d'abord une ou plusieurs URL(s) image (http/https ou data:image).");
                return;
            }
            List<String> urls = splitImagePaths(raw);
            if (urls.isEmpty()) {
                onImageError.accept("Aucune URL valide détectée.");
                return;
            }
            int before = imagePaths.size();
            for (String url : urls) {
                String low = url.toLowerCase(Locale.ROOT);
                if (low.startsWith("http://") || low.startsWith("https://") || low.startsWith("data:image/")) {
                    addImagePath(url);
                }
            }
            if (imagePaths.size() == before) {
                onImageError.accept("Utilisez des URLs qui commencent par http://, https:// ou data:image/");
                return;
            }
            if (selectedImageIndex < 0) {
                selectedImageIndex = before;
            }
            refreshImagesUi();
        });
        replaceImageButton.setOnAction(e -> {
            if (selectedImageIndex < 0 || selectedImageIndex >= imagePaths.size()) {
                onImageError.accept("Sélectionnez d'abord une image à remplacer.");
                return;
            }
            Window w = nomField.getScene() != null ? nomField.getScene().getWindow() : null;
            if (w == null) {
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Remplacer l'image sélectionnée");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                    "Images (tous formats)",
                    "*.*"
                )
            );
            File file = chooser.showOpenDialog(w);
            if (file == null) {
                return;
            }
            try {
                String copied = ProductImageStorage.copyProductImage(file);
                if (copied != null && !copied.isBlank()) {
                    imagePaths.set(selectedImageIndex, copied.trim());
                    refreshImagesUi();
                }
            } catch (IOException ex) {
                onImageError.accept(ex.getMessage() == null ? "Erreur copie image" : ex.getMessage());
            }
        });
        clearImageButton.setOnAction(e -> {
            if (selectedImageIndex < 0 || selectedImageIndex >= imagePaths.size()) {
                return;
            }
            imagePaths.remove(selectedImageIndex);
            if (imagePaths.isEmpty()) {
                selectedImageIndex = -1;
            } else if (selectedImageIndex >= imagePaths.size()) {
                selectedImageIndex = imagePaths.size() - 1;
            }
            refreshImagesUi();
        });

        refreshImagesUi();
    }

    public boolean isEditMode() {
        return editMode;
    }

    /** Quantité catalogue : 1 à la création, quantité existante en modification. */
    public int getCatalogQuantityForSave() {
        return editMode ? initialCatalogQuantity : 1;
    }

    public String getLastSuggestedDescription() {
        return lastSuggestedDescription == null ? "" : lastSuggestedDescription;
    }

    public void setLastSuggestedDescription(String value) {
        this.lastSuggestedDescription = value == null ? "" : value.trim();
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
        HBox descButtons = new HBox(8, suggestDescriptionButton, summarizeDescriptionButton);
        grid.add(descButtons, 1, 1);
        grid.add(descriptionArea, 1, 2);
        grid.add(ProductFormUi.formLabel("Catégorie"), 0, 3);
        grid.add(categoryCombo, 1, 3);
        grid.add(ProductFormUi.formLabel("Prix (DT)"), 0, 4);
        grid.add(prixField, 1, 4);
        grid.add(ProductFormUi.formLabel("Stock"), 0, 5);
        grid.add(stockBox, 1, 5);

        Label imageTitle = ProductFormUi.formLabel("Image du produit");
        Label imageHint = new Label(
            "Ajoutez plusieurs images : sélectionnez plusieurs fichiers ou collez des URLs. "
                + "Séparez les entrées par ';' (la première sera l'image principale)."
        );
        imageHint.setWrapText(true);
        imageHint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        HBox fileRow = new HBox(8, chooseImageButton, replaceImageButton, clearImageButton, useImageUrlButton);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        HBox pathRow = new HBox(8, imagePathField);
        HBox.setHgrow(imagePathField, Priority.ALWAYS);
        imagePathField.setMinWidth(220);
        StackPane preview3d = build3dPreviewPane();
        Label thumbsTitle = new Label("Images ajoutées (cliquez pour sélectionner celle à supprimer/remplacer)");
        thumbsTitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        VBox imageBlock = new VBox(10, imageHint, fileRow, pathRow, thumbsTitle, imageThumbsPane, preview3dHint, preview3d);
        grid.add(imageTitle, 0, 6);
        grid.add(imageBlock, 1, 6);
        return grid;
    }

    private StackPane build3dPreviewPane() {
        StackPane host = new StackPane(previewImageView);
        host.setAlignment(Pos.CENTER);
        host.setStyle(
            "-fx-background-color: #f8fafc; -fx-background-radius: 10; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 10;"
        );
        host.setMaxWidth(270);
        host.setPrefHeight(200);
        return host;
    }

    private void updateImagePreview(String pathOrUrl) {
        String p = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (!p.isBlank()) {
            Image img = ProductImageLoader.loadForDisplay(p, 320, 320);
            if (img != null && !img.isError()) {
                previewImageView.setImage(img);
                preview3dHint.setText("Aperçu image exact (2D) : affichage fidèle.");
                return;
            }
        }
        previewImageView.setImage(null);
        preview3dHint.setText("Aperçu image indisponible, ajoutez un fichier ou une URL valide.");
    }

    private void addImagePath(String raw) {
        if (raw == null) {
            return;
        }
        String normalized = ProductFormValidation.normalizeImagePaths(raw);
        if (normalized.isBlank()) {
            return;
        }
        for (String p : splitImagePaths(normalized)) {
            if (!p.isBlank()) {
                imagePaths.add(p);
            }
        }
    }

    private void setImagePathsFromRaw(String raw) {
        imagePaths.clear();
        addImagePath(raw);
        selectedImageIndex = imagePaths.isEmpty() ? -1 : 0;
    }

    private void refreshImagesUi() {
        imagePathField.setText(String.join(" ; ", imagePaths));
        rebuildImageThumbs();
        if (selectedImageIndex >= 0 && selectedImageIndex < imagePaths.size()) {
            updateImagePreview(imagePaths.get(selectedImageIndex));
        } else if (!imagePaths.isEmpty()) {
            selectedImageIndex = 0;
            updateImagePreview(imagePaths.get(0));
        } else {
            selectedImageIndex = -1;
            updateImagePreview("");
        }
    }

    private void rebuildImageThumbs() {
        imageThumbsPane.getChildren().clear();
        for (int i = 0; i < imagePaths.size(); i++) {
            final int idx = i;
            String path = imagePaths.get(i);
            StackPane thumbHost = new StackPane();
            thumbHost.setPrefSize(76, 62);
            thumbHost.setMinSize(76, 62);
            thumbHost.setMaxSize(76, 62);
            thumbHost.setStyle(
                idx == selectedImageIndex
                    ? "-fx-background-color: #eef6ff; -fx-border-color: #2563eb; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
                    : "-fx-background-color: #f8fafc; -fx-border-color: #d1d5db; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
            );
            Image thumb = ProductImageLoader.loadForDisplay(path, 70, 56);
            if (thumb != null && !thumb.isError()) {
                ImageView iv = new ImageView(thumb);
                iv.setFitWidth(70);
                iv.setFitHeight(56);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                thumbHost.getChildren().add(iv);
            } else {
                thumbHost.getChildren().add(ProductImagePlaceholder.create(70, 56));
            }
            thumbHost.setOnMouseClicked(e -> {
                selectedImageIndex = idx;
                refreshImagesUi();
            });
            imageThumbsPane.getChildren().add(thumbHost);
        }
    }

    private static List<String> splitImagePaths(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String t = raw.trim();
        if (t.toLowerCase(Locale.ROOT).startsWith("data:image/")) {
            out.add(t);
            return out;
        }
        String[] parts = t.split("[;|\\n]");
        for (String p : parts) {
            if (p != null) {
                String x = p.trim();
                if (!x.isBlank()) {
                    out.add(x);
                }
            }
        }
        return out;
    }
}
