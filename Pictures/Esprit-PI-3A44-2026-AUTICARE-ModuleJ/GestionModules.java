import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Callback;

public class GestionModules extends Application {

    // ─── Modèle ────────────────────────────────────────────────────────────────

    public static class Module {
        private final StringProperty image       = new SimpleStringProperty();
        private final StringProperty titre       = new SimpleStringProperty();
        private final StringProperty description = new SimpleStringProperty();
        private final StringProperty niveau      = new SimpleStringProperty();
        private final StringProperty categorie   = new SimpleStringProperty();
        private final StringProperty publie      = new SimpleStringProperty();
        private final IntegerProperty articles   = new SimpleIntegerProperty();
        private final IntegerProperty ressources = new SimpleIntegerProperty();

        public Module(String image, String titre, String description,
                      String niveau, String categorie, String publie,
                      int articles, int ressources) {
            this.image.set(image);
            this.titre.set(titre);
            this.description.set(description);
            this.niveau.set(niveau);
            this.categorie.set(categorie);
            this.publie.set(publie);
            this.articles.set(articles);
            this.ressources.set(ressources);
        }

        public StringProperty  imageProperty()       { return image; }
        public StringProperty  titreProperty()       { return titre; }
        public StringProperty  descriptionProperty() { return description; }
        public StringProperty  niveauProperty()      { return niveau; }
        public StringProperty  categorieProperty()   { return categorie; }
        public StringProperty  publieProperty()      { return publie; }
        public IntegerProperty articlesProperty()    { return articles; }
        public IntegerProperty ressourcesProperty()  { return ressources; }

        public String getImage()       { return image.get(); }
        public String getTitre()       { return titre.get(); }
        public String getDescription() { return description.get(); }
        public String getNiveau()      { return niveau.get(); }
        public String getCategorie()   { return categorie.get(); }
        public String getPublie()      { return publie.get(); }
        public int    getArticles()    { return articles.get(); }
        public int    getRessources()  { return ressources.get(); }
    }

    // ─── Données ───────────────────────────────────────────────────────────────

    private final ObservableList<Module> masterData = FXCollections.observableArrayList(
        new Module("zzzzzz", "zzzzzz",
                   "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
                   "Difficile", "Vie quotidienne", "Publié", 0, 0),
        new Module("Emna", "Emna",
                   "HHHHHHHHAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                   "Difficile", "Comprendre le TSA", "Publié", 1, 1)
    );

    private ObservableList<Module> filteredData;

    // ─── UI ────────────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        filteredData = FXCollections.observableArrayList(masterData);

        // ── Root ────────────────────────────────────────────────────────────────
        VBox root = new VBox();
        root.setStyle("-fx-background-color: #f8fafc;");
        root.setPadding(new Insets(30));
        root.setSpacing(20);

        // ── Page title ──────────────────────────────────────────────────────────
        Label pageTitle = new Label("Gestion des modules");
        pageTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        pageTitle.setTextFill(Color.web("#4b5563"));

        // ── Card ────────────────────────────────────────────────────────────────
        VBox card = new VBox(0);
        card.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 12;
                -fx-border-color: #e5e0d8;
                -fx-border-width: 1;
                -fx-border-radius: 12;
                -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 14, 0.2, 0, 2);
                """);

        // ── Card header ─────────────────────────────────────────────────────────
        VBox cardHeader = new VBox(4);
        cardHeader.setPadding(new Insets(24, 24, 20, 24));
        
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label menuIcon = new Label("☰");
        menuIcon.setFont(Font.font("Segoe UI", 18));
        menuIcon.setTextFill(Color.web("#a7c7e7"));

        VBox headerText = new VBox(2);
        Label listTitle = new Label("Liste des modules");
        listTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 18));
        listTitle.setTextFill(Color.web("#4b5563"));

        Label listSubtitle = new Label("Recherche, tri et actions de gestion des modules");
        listSubtitle.setFont(Font.font("Segoe UI", 13));
        listSubtitle.setTextFill(Color.web("#6b7280"));

        headerText.getChildren().addAll(listTitle, listSubtitle);
        headerRow.getChildren().addAll(menuIcon, headerText);
        cardHeader.getChildren().add(headerRow);

        // ── Separator (trait noir épais) ────────────────────────────────────────
        Region separator = new Region();
        separator.setPrefHeight(2);
        separator.setMinHeight(2);
        separator.setMaxHeight(2);
        separator.setStyle("-fx-background-color: #111827;");

        // ── Toolbar ─────────────────────────────────────────────────────────────
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(36, 20, 22, 20));
        toolbar.setStyle("""
                -fx-border-color: transparent transparent #e8e4de transparent;
                -fx-border-width: 0 0 1 0;
                """);

        // Search icon + field
        Label searchIcon = new Label("🔍");
        searchIcon.setFont(Font.font(13));
        searchIcon.setTextFill(Color.web("#9ca3af"));

        TextField searchField = new TextField();
        searchField.setPromptText("Rechercher un module par titre...");
        searchField.setPrefWidth(300);
        searchField.setPrefHeight(36);
        searchField.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #d1d5db;
                -fx-border-radius: 4;
                -fx-background-radius: 4;
                -fx-border-width: 1;
                -fx-font-size: 13px;
                -fx-padding: 6 10 6 10;
                """);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setAll(masterData.stream()
                .filter(m -> m.getTitre().toLowerCase()
                              .contains(newVal.toLowerCase()))
                .toList());
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Nouveau module button
        Button btnNew = new Button("Nouveau module");
        btnNew.setPrefHeight(36);
        btnNew.setStyle("""
                -fx-background-color: #a7c7e7;
                -fx-text-fill: white;
                -fx-font-weight: normal;
                -fx-font-size: 13px;
                -fx-background-radius: 10;
                -fx-cursor: hand;
                -fx-padding: 8 16 8 16;
                """);
        btnNew.setOnMouseEntered(e -> btnNew.setStyle(btnNew.getStyle().replace("#a7c7e7", "#b8d4ed")));
        btnNew.setOnMouseExited(e -> btnNew.setStyle(btnNew.getStyle().replace("#b8d4ed", "#a7c7e7")));

        // A-Z sort button
        Button btnSort = new Button("⇅  A-Z");
        btnSort.setPrefHeight(36);
        btnSort.setStyle("""
                -fx-background-color: #7c3aed;
                -fx-text-fill: white;
                -fx-font-weight: normal;
                -fx-font-size: 13px;
                -fx-background-radius: 10;
                -fx-cursor: hand;
                -fx-padding: 8 16 8 16;
                """);
        btnSort.setOnMouseEntered(e -> btnSort.setStyle(btnSort.getStyle().replace("#7c3aed", "#9333ea")));
        btnSort.setOnMouseExited(e -> btnSort.setStyle(btnSort.getStyle().replace("#9333ea", "#7c3aed")));
        btnSort.setOnAction(e -> {
            FXCollections.sort(filteredData,
                (a, b) -> a.getTitre().compareToIgnoreCase(b.getTitre()));
        });

        // Exporter PDF button
        Button btnPdf = new Button("📄  Exporter PDF");
        btnPdf.setPrefHeight(36);
        btnPdf.setStyle("""
                -fx-background-color: #dc2626;
                -fx-text-fill: white;
                -fx-font-weight: normal;
                -fx-font-size: 13px;
                -fx-background-radius: 10;
                -fx-cursor: hand;
                -fx-padding: 8 16 8 16;
                """);
        btnPdf.setOnMouseEntered(e -> btnPdf.setStyle(btnPdf.getStyle().replace("#dc2626", "#b91c1c")));
        btnPdf.setOnMouseExited(e -> btnPdf.setStyle(btnPdf.getStyle().replace("#b91c1c", "#dc2626")));

        toolbar.getChildren().addAll(searchIcon, searchField, spacer, btnNew, btnSort, btnPdf);

        // ── Table wrapper (avec marges latérales) ───────────────────────────────
        VBox tableWrapper = new VBox();
        tableWrapper.setPadding(new Insets(20, 20, 20, 20));

        // ── Table ───────────────────────────────────────────────────────────────
        TableView<Module> table = new TableView<>(filteredData);
        table.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #d1d5db;
                -fx-border-width: 1;
                -fx-border-radius: 0 0 10 10;
                -fx-background-radius: 0 0 10 10;
                -fx-table-cell-border-color: #d1d5db;
                """);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(400);
        table.setPlaceholder(new Label("Aucun module trouvé"));
        table.setFixedCellSize(64);

        // Image column
        TableColumn<Module, String> colImage = new TableColumn<>("Image");
        colImage.setCellValueFactory(new PropertyValueFactory<>("image"));
        colImage.setPrefWidth(80);
        colImage.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) { 
                    setGraphic(null); 
                    setText(null); 
                    setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0;");
                    return; 
                }
                VBox box = new VBox(2);
                box.setAlignment(Pos.CENTER);
                // Placeholder image box
                StackPane imgBox = new StackPane();
                imgBox.setPrefSize(44, 44);
                imgBox.setStyle("""
                        -fx-background-color: #e8e4de;
                        -fx-background-radius: 6;
                        """);
                Label imgLabel = new Label("🖼");
                imgLabel.setFont(Font.font(14));
                imgBox.getChildren().add(imgLabel);
                box.getChildren().add(imgBox);
                setGraphic(box);
                setText(null);
                setAlignment(Pos.CENTER);
                setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0; -fx-padding: 10 14 10 14;");
            }
        });

        // Titre column
        TableColumn<Module, String> colTitre = new TableColumn<>("Titre");
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colTitre.setPrefWidth(100);
        colTitre.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { 
                    setText(null); 
                    setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0;");
                    return; 
                }
                setText(v);
                setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                setTextFill(Color.web("#111827"));
                setAlignment(Pos.CENTER_LEFT);
                setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0; -fx-padding: 10 14 10 14;");
            }
        });

        // Description column
        TableColumn<Module, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colDesc.setPrefWidth(220);
        colDesc.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { 
                    setText(null); 
                    setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0;");
                    return; 
                }
                String truncated = v.length() > 35 ? v.substring(0, 35) + "..." : v;
                setText(truncated);
                setFont(Font.font("Segoe UI", 12));
                setTextFill(Color.web("#6b7280"));
                setAlignment(Pos.CENTER_LEFT);
                setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0; -fx-padding: 10 14 10 14;");
                Tooltip.install(this, new Tooltip(v));
            }
        });

        // Niveau column
        TableColumn<Module, String> colNiveau = new TableColumn<>("Niveau");
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colNiveau.setPrefWidth(90);
        colNiveau.setCellFactory(badgeFactory("#fee2e2", "#991b1b"));

        // Catégorie column
        TableColumn<Module, String> colCat = new TableColumn<>("Catégorie");
        colCat.setCellValueFactory(new PropertyValueFactory<>("categorie"));
        colCat.setPrefWidth(130);
        colCat.setCellFactory(badgeFactory("#f3e8ff", "#6b21a8"));

        // Publié column
        TableColumn<Module, String> colPublie = new TableColumn<>("Publié");
        colPublie.setCellValueFactory(new PropertyValueFactory<>("publie"));
        colPublie.setPrefWidth(80);
        colPublie.setCellFactory(badgeFactory("#dcfce7", "#166534"));

        // Articles column
        TableColumn<Module, Integer> colArticles = new TableColumn<>("Articles");
        colArticles.setCellValueFactory(new PropertyValueFactory<>("articles"));
        colArticles.setPrefWidth(90);
        colArticles.setCellFactory(intBadgeFactory("#dbeafe", "#1e40af", "article"));

        // Ressources column
        TableColumn<Module, Integer> colRessources = new TableColumn<>("Ressources");
        colRessources.setCellValueFactory(new PropertyValueFactory<>("ressources"));
        colRessources.setPrefWidth(100);
        colRessources.setCellFactory(intBadgeFactory("#e0e7ff", "#3730a3", "ressource"));

        // Actions column
        TableColumn<Module, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(90);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit   = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox   box       = new HBox(8, btnEdit, btnDelete);

            {
                box.setAlignment(Pos.CENTER);
                styleBtn(btnEdit,   "#eff6ff", "#2563eb");
                styleBtn(btnDelete, "#fef2f2", "#dc2626");
                btnEdit.setFont(Font.font(16));
                btnDelete.setFont(Font.font(16));

                btnDelete.setOnAction(e -> {
                    Module m = getTableView().getItems().get(getIndex());
                    filteredData.remove(m);
                    masterData.remove(m);
                });
            }

            private void styleBtn(Button b, String bg, String fg) {
                b.setStyle(String.format("""
                        -fx-background-color: %s;
                        -fx-text-fill: %s;
                        -fx-background-radius: 8;
                        -fx-cursor: hand;
                        -fx-padding: 0;
                        -fx-min-width: 40;
                        -fx-min-height: 40;
                        -fx-max-width: 40;
                        -fx-max-height: 40;
                        -fx-border-width: 0;
                        """, bg, fg));
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                    setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0;");
                } else {
                    setGraphic(box);
                    setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0; -fx-padding: 10 14 10 14;");
                }
            }
        });

        table.getColumns().addAll(
            colImage, colTitre, colDesc, colNiveau,
            colCat, colPublie, colArticles, colRessources, colActions
        );

        tableWrapper.getChildren().add(table);

        // ── Assemble card ───────────────────────────────────────────────────────
        card.getChildren().addAll(cardHeader, separator, toolbar, tableWrapper);

        root.getChildren().addAll(pageTitle, card);

        // ── Scene avec CSS inline pour l'en-tête ────────────────────────────────
        Scene scene = new Scene(root, 1300, 680);
        
        // Style CSS pour l'en-tête du tableau (dégradé gris, texte blanc, centré)
        scene.getRoot().setStyle(scene.getRoot().getStyle() + """
                .table-view {
                    -fx-background-color: white;
                }
                .table-view .column-header-background {
                    -fx-background-color: linear-gradient(from 0% 0% to 0% 100%, #e8eaed 0%, #cfd4db 100%);
                }
                .table-view .column-header-background .filler {
                    -fx-background-color: transparent;
                }
                .table-view .column-header {
                    -fx-background-color: transparent;
                    -fx-border-color: #d1d5db;
                    -fx-border-width: 0 1 1 0;
                }
                .table-view .column-header .label {
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                    -fx-font-size: 12px;
                    -fx-alignment: center;
                    -fx-padding: 10 12 10 12;
                }
                .table-view:focused .table-row-cell:selected {
                    -fx-background-color: #eff6ff;
                }
                .table-view .table-row-cell:hover {
                    -fx-background-color: #f9fafb;
                }
                """);

        stage.setTitle("Gestion des modules - Style Maquette");
        stage.setScene(scene);
        stage.show();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Callback<TableColumn<Module, String>, TableCell<Module, String>>
            badgeFactory(String bg, String fg) {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { 
                    setGraphic(null); 
                    setText(null); 
                    setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0;");
                    return; 
                }
                Label badge = new Label(v);
                badge.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
                badge.setTextFill(Color.web(fg));
                badge.setStyle(String.format("""
                        -fx-background-color: %s;
                        -fx-background-radius: 999;
                        -fx-padding: 6 12 6 12;
                        -fx-max-width: 120;
                        """, bg));
                setGraphic(badge);
                setText(null);
                setAlignment(Pos.CENTER);
                setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0; -fx-padding: 10 14 10 14;");
            }
        };
    }

    private Callback<TableColumn<Module, Integer>, TableCell<Module, Integer>>
            intBadgeFactory(String bg, String fg, String unit) {
        return col -> new TableCell<>() {
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { 
                    setGraphic(null); 
                    setText(null); 
                    setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0;");
                    return; 
                }
                String text = v == 0 ? "0 " + unit : (v == 1 ? "1 " + unit : v + " " + unit + "s");
                Label badge = new Label(text);
                badge.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
                badge.setTextFill(Color.web(fg));
                badge.setStyle(String.format("""
                        -fx-background-color: %s;
                        -fx-background-radius: 999;
                        -fx-padding: 6 12 6 12;
                        -fx-max-width: 120;
                        """, bg));
                setGraphic(badge);
                setText(null);
                setAlignment(Pos.CENTER);
                setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; -fx-border-width: 0 1 1 0; -fx-padding: 10 14 10 14;");
            }
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}
