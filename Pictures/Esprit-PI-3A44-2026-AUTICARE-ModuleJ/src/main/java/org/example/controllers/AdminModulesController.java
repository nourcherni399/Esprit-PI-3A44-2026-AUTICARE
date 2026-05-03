package org.example.controllers;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.MainApp;
import org.example.models.ModuleActionEntry;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;
import org.example.models.Ressource;
import org.example.services.BlogService;
import org.example.services.ModuleService;
import org.example.services.RessourceService;
import org.example.utils.AdminTopbarHelper;
import org.example.utils.AppState;
import org.example.utils.ModuleActionHistory;
import org.example.utils.ModulesPdfExporter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.sql.SQLException;
import java.text.Collator;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public class AdminModulesController {

    private static final double MODULE_IMAGE_THUMB = 38;
    private static final double MODULE_IMAGE_CORNER_RADIUS = 6;
    /** Tableaux ressources / historique */
    private static final double MOD_TABLE_ROW_HEIGHT = 72;
    private static final double MOD_TABLE_HEADER_HEIGHT = 52;
    /** Tableau liste des modules (plus compact) */
    private static final double MOD_MODULES_TABLE_ROW_HEIGHT = 56;
    private static final double MOD_MODULES_TABLE_HEADER_HEIGHT = 40;
    private static final DateTimeFormatter ACTION_TS_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private StackPane topbarAvatarHost;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private TextField topSearchField;
    @FXML
    private TextField filterField;
    @FXML
    private ComboBox<ModuleCategorie> categoryFilterCombo;
    @FXML
    private TableView<ModuleContent> modulesTable;
    @FXML
    private ComboBox<String> resourceModuleFilter;
    @FXML
    private TableView<Ressource> resourcesPlaceholderTable;
    @FXML
    private StackPane categoryChartPane;
    @FXML
    private StackPane levelChartPane;
    @FXML
    private BarChart<String, Number> moduleLevelChart;
    // Historique supprimé - pas nécessaire
    // private TableView<ModuleActionEntry> moduleHistoryTable;
    
    // Labels pour statistiques détaillées
    @FXML
    private Label statComLabel;
    @FXML
    private Label statAutoLabel;
    @FXML
    private Label statEduLabel;
    @FXML
    private Label statDebutantLabel;
    @FXML
    private Label statMoyenLabel;
    @FXML
    private Label statAvanceLabel;
    @FXML
    private Label statTotalModulesLabel;
    @FXML
    private Label statTotalResourcesLabel;
    @FXML
    private Label statTotalArticlesLabel;

    private final ModuleService moduleService = new ModuleService();
    private final BlogService blogService = new BlogService();
    private final RessourceService ressourceService = new RessourceService();
    private ObservableList<ModuleContent> masterList = FXCollections.observableArrayList();
    private FilteredList<ModuleContent> filteredList;
    /** Articles {@code blog} par {@code module_id}. */
    private final Map<Integer, Integer> articleCountByModuleId = new HashMap<>();
    /** Ressources par {@code module_id} (pour le badge sur le tableau des modules). */
    private final Map<Integer, Integer> resourceCountByModuleId = new HashMap<>();
    /** Titre affiché du module pour chaque id (liste des ressources). */
    private final Map<Integer, String> moduleTitleById = new HashMap<>();
    private final ObservableList<Ressource> resourceMasterList = FXCollections.observableArrayList();
    private FilteredList<Ressource> filteredResourceList;

    @FXML
    public void initialize() {
        AdminTopbarHelper.applyToTopbar(topbarAvatarHost, userNameLabel, userEmailLabel);

        if (resourcesPlaceholderTable != null) {
            resourcesPlaceholderTable.setPlaceholder(new Label("Aucune ressource."));
        }
        modulesTable.setPlaceholder(new Label("Aucun module."));
        setupTable();
        setupResourcesTable();
        setupModuleChartsAndHistory();
        
        reloadModules();

        // Initialiser le ComboBox de filtrage par catégorie
        if (categoryFilterCombo != null) {
            categoryFilterCombo.getItems().add(null); // Option "Toutes les catégories"
            categoryFilterCombo.getItems().addAll(ModuleCategorie.values());
            categoryFilterCombo.setConverter(new javafx.util.StringConverter<ModuleCategorie>() {
                @Override
                public String toString(ModuleCategorie cat) {
                    if (cat == null) return "Toutes les catégories";
                    return org.example.utils.ModuleCategorieStringConverter.INSTANCE.toString(cat);
                }
                @Override
                public ModuleCategorie fromString(String string) {
                    return null;
                }
            });
            categoryFilterCombo.getSelectionModel().selectFirst(); // Sélectionner "Toutes"
            categoryFilterCombo.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> applyFilters());
        }
        
        if (filterField != null) {
            filterField.textProperty().addListener((o, old, v) -> applyFilters());
        }
        if (topSearchField != null) {
            topSearchField.textProperty().addListener((o, old, v) -> applyFilters());
        }
        if (resourceModuleFilter != null) {
            resourceModuleFilter.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> applyResourceModuleFilter());
        }

        wireTableAutoHeight(modulesTable);
        wireTableAutoHeight(resourcesPlaceholderTable);
        // wireTableAutoHeight(moduleHistoryTable); // Supprimé

        ensureModulePageStylesheetsOnScene();
    }

    private void ensureModulePageStylesheetsOnScene() {
        Platform.runLater(() -> {
            if (modulesTable == null) return;
            Scene scene = modulesTable.getScene();
            if (scene == null) return;
            forceReloadStylesheet(scene, "/styles/app.css");
            forceReloadStylesheet(scene, "/styles/admin.css");
            scene.getRoot().applyCss();
            modulesTable.refresh();
            if (resourcesPlaceholderTable != null) resourcesPlaceholderTable.refresh();
        });
    }

    private static void forceReloadStylesheet(Scene scene, String classpath) {
        java.net.URL url = AdminModulesController.class.getResource(classpath);
        if (url == null) {
            return;
        }
        String external = url.toExternalForm();
        
        // Supprime toutes les occurrences existantes
        scene.getStylesheets().removeIf(s -> s.contains(classpath.replace("/styles/", "")));
        
        // Ajoute avec timestamp pour forcer le rechargement
        String withTimestamp = external + "?" + System.currentTimeMillis();
        scene.getStylesheets().add(withTimestamp);
    }

    /** Hauteur du tableau = en-tête + (nombre de lignes × hauteur de ligne), sans zone vide inutile. */
    private void applyTableHeight(TableView<?> table) {
        if (table == null) {
            return;
        }
        boolean modulesList = table == modulesTable;
        double rowH = modulesList ? MOD_MODULES_TABLE_ROW_HEIGHT : MOD_TABLE_ROW_HEIGHT;
        double headerH = modulesList ? MOD_MODULES_TABLE_HEADER_HEIGHT : MOD_TABLE_HEADER_HEIGHT;
        table.setFixedCellSize(rowH);
        int n = table.getItems() == null ? 0 : table.getItems().size();
        if (n == 0) {
            n = 1;
        }
        double h = headerH + n * rowH + 4;
        table.setMinHeight(h);
        table.setPrefHeight(h);
        table.setMaxHeight(h);
    }

    private void wireTableAutoHeight(TableView<?> table) {
        if (table == null) {
            return;
        }
        ObservableList<?> items = table.getItems();
        if (items != null) {
            items.addListener((InvalidationListener) obs -> applyTableHeight(table));
        }
        applyTableHeight(table);
    }

    private void setupTable() {
        TableColumn<ModuleContent, String> colImage = new TableColumn<>("Image");
        colImage.setPrefWidth(52);
        colImage.setStyle("-fx-alignment: CENTER;");
        colImage.setCellValueFactory(c -> new SimpleStringProperty(""));
        colImage.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                ModuleContent m = getTableRow().getItem();
                setGraphic(moduleImageThumbnail(m.getImage()));
            }
        });

        TableColumn<ModuleContent, String> colTitre = new TableColumn<>("Titre");
        colTitre.setPrefWidth(150);
        colTitre.setStyle("-fx-alignment: CENTER-LEFT;");
        colTitre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitre()));
        colTitre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label l = new Label(item);
                l.getStyleClass().add("mod-table-title");
                l.setTextOverrun(OverrunStyle.CLIP);
                l.setMaxWidth(200);
                l.setWrapText(false);
                setGraphic(l);
            }
        });

        TableColumn<ModuleContent, String> colDesc = new TableColumn<>("Description");
        colDesc.setPrefWidth(280);
        colDesc.setStyle("-fx-alignment: CENTER-LEFT;");
        colDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription() != null ? c.getValue().getDescription() : ""));
        colDesc.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label l = new Label(item);
                l.getStyleClass().addAll("mod-table-desc", "mod-table-desc-ellipsis");
                l.setTextOverrun(OverrunStyle.CLIP);
                l.setWrapText(false);
                l.setMaxWidth(320);
                setGraphic(l);
            }
        });

        TableColumn<ModuleContent, String> colNiveau = new TableColumn<>("Niveau");
        colNiveau.setPrefWidth(108);
        colNiveau.setStyle("-fx-alignment: CENTER;");
        colNiveau.setCellValueFactory(c -> {
            ModuleNiveau n = c.getValue().getNiveau();
            return new SimpleStringProperty(n != null ? n.name() : "");
        });
        colNiveau.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                ModuleNiveau n = getTableRow().getItem().getNiveau();
                Label badge = new Label(formatNiveauLibelle(n));
                badge.getStyleClass().addAll("mod-badge-pill", niveauBadgeClass(n));
                stylePillBadge(badge, 96);
                setGraphic(badge);
            }
        });

        TableColumn<ModuleContent, String> colCat = new TableColumn<>("Catégorie");
        colCat.setPrefWidth(168);
        colCat.setStyle("-fx-alignment: CENTER;");
        colCat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategorie()));
        colCat.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                ModuleContent m = getTableRow().getItem();
                Label badge = new Label(formatCategorieLibelle(m));
                badge.getStyleClass().addAll("mod-badge-pill", "mod-badge-categorie");
                stylePillBadge(badge, 104);
                setGraphic(badge);
            }
        });

        TableColumn<ModuleContent, String> colPub = new TableColumn<>("Publié");
        colPub.setPrefWidth(102);
        colPub.setStyle("-fx-alignment: CENTER;");
        colPub.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                boolean pub = getTableRow().getItem().isPublished();
                Label badge = new Label(pub ? "Publié" : "Brouillon");
                badge.getStyleClass().addAll("mod-badge-pill", pub ? "mod-badge-publie" : "mod-badge-brouillon");
                stylePillBadge(badge, 88);
                setGraphic(badge);
            }
        });
        colPub.setCellValueFactory(c -> new SimpleStringProperty(""));

        TableColumn<ModuleContent, String> colArticles = new TableColumn<>("Articles");
        colArticles.setPrefWidth(102);
        colArticles.setStyle("-fx-alignment: CENTER;");
        colArticles.setCellValueFactory(c -> {
            int n = articleCountByModuleId.getOrDefault(c.getValue().getId(), 0);
            return new SimpleStringProperty(String.valueOf(n));
        });
        colArticles.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                int n = articleCountByModuleId.getOrDefault(getTableRow().getItem().getId(), 0);
                Label badge = new Label(articlesLibelle(n));
                badge.getStyleClass().addAll("mod-badge-pill", "mod-badge-articles");
                stylePillBadge(badge, 100);
                setGraphic(badge);
            }
        });

        TableColumn<ModuleContent, String> colRes = new TableColumn<>("Ressources");
        colRes.setPrefWidth(118);
        colRes.setStyle("-fx-alignment: CENTER;");
        colRes.setCellValueFactory(c -> {
            int n = resourceCountByModuleId.getOrDefault(c.getValue().getId(), 0);
            return new SimpleStringProperty(String.valueOf(n));
        });
        colRes.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                int n = resourceCountByModuleId.getOrDefault(getTableRow().getItem().getId(), 0);
                Label badge = new Label(ressourcesLibelle(n));
                badge.getStyleClass().addAll("mod-badge-pill", "mod-badge-ressources");
                stylePillBadge(badge, 108);
                setGraphic(badge);
            }
        });

        TableColumn<ModuleContent, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(108);
        colActions.setStyle("-fx-alignment: CENTER;");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button edit = makeIconBtn("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z", "mod-table-btn-edit");
            private final Button del = makeIconBtn("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z", "mod-table-btn-delete");
            private final HBox box = new HBox(8, edit, del);
            { box.setAlignment(Pos.CENTER); }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                edit.setOnAction(e -> openEditModule(getTableRow().getItem()));
                del.setOnAction(e -> deleteModule(getTableRow().getItem()));
                setGraphic(box);
            }
        });

        modulesTable.getColumns().setAll(colImage, colTitre, colDesc, colNiveau, colCat, colPub, colArticles, colRes, colActions);
        modulesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void setupResourcesTable() {
        if (resourcesPlaceholderTable == null) {
            return;
        }

        TableColumn<Ressource, String> colTitre = new TableColumn<>("Titre");
        colTitre.setPrefWidth(130);
        colTitre.setStyle("-fx-alignment: CENTER-LEFT;");
        colTitre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitre() != null ? c.getValue().getTitre() : ""));
        colTitre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label l = new Label(item);
                l.setStyle("-fx-font-weight: 600; -fx-font-size: 13px; -fx-text-fill: #111827;");
                l.setTextOverrun(OverrunStyle.ELLIPSIS);
                l.setMaxWidth(160);
                l.setWrapText(false);
                setGraphic(l); setText(null);
            }
        });

        TableColumn<Ressource, String> colType = new TableColumn<>("Type");
        colType.setPrefWidth(90);
        colType.setStyle("-fx-alignment: CENTER-LEFT;");
        colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTypeRessource() != null ? c.getValue().getTypeRessource() : ""));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                String raw = getTableRow().getItem().getTypeRessource();
                Label badge = new Label(raw != null && !raw.isBlank() ? raw.toUpperCase() : "—");
                badge.getStyleClass().add("mod-badge-type");
                setGraphic(badge); setText(null);
            }
        });

        TableColumn<Ressource, String> colContenu = new TableColumn<>("Contenu");
        colContenu.setPrefWidth(280);
        colContenu.setStyle("-fx-alignment: CENTER-LEFT;");
        colContenu.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContenu() != null ? c.getValue().getContenu() : ""));
        colContenu.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label l = new Label(item);
                l.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");
                l.setTextOverrun(OverrunStyle.ELLIPSIS);
                l.setWrapText(false);
                l.setMaxWidth(340);
                setGraphic(l); setText(null);
            }
        });

        TableColumn<Ressource, String> colModule = new TableColumn<>("Module");
        colModule.setPrefWidth(120);
        colModule.setStyle("-fx-alignment: CENTER-LEFT;");
        colModule.setCellValueFactory(c -> {
            String t = moduleTitleById.get(c.getValue().getModuleId());
            return new SimpleStringProperty(t != null ? t : "—");
        });
        colModule.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                String t = moduleTitleById.get(getTableRow().getItem().getModuleId());
                Label lbl = new Label(t != null && !t.isBlank() ? t : "—");
                lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");
                setGraphic(lbl); setText(null);
            }
        });

        TableColumn<Ressource, String> colOrdre = new TableColumn<>("Ordre");
        colOrdre.setPrefWidth(70);
        colOrdre.setStyle("-fx-alignment: CENTER;");
        colOrdre.setCellValueFactory(c -> {
            Integer o = c.getValue().getOrdre();
            return new SimpleStringProperty(o != null ? String.valueOf(o) : "—");
        });
        colOrdre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Integer o = getTableRow().getItem().getOrdre();
                Label lbl = new Label(o != null ? String.valueOf(o) : "—");
                lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #9ca3af;");
                setGraphic(lbl); setText(null);
            }
        });

        TableColumn<Ressource, String> colActif = new TableColumn<>("Actif");
        colActif.setPrefWidth(80);
        colActif.setStyle("-fx-alignment: CENTER;");
        colActif.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Oui" : "Non"));
        colActif.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                boolean ok = getTableRow().getItem().isActive();
                Label badge = new Label(ok ? "Oui" : "Non");
                badge.getStyleClass().add(ok ? "mod-badge-oui" : "mod-badge-non");
                setGraphic(badge); setText(null);
            }
        });

        TableColumn<Ressource, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(90);
        colActions.setStyle("-fx-alignment: CENTER;");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button edit = makeIconBtn("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z", "mod-table-btn-edit");
            private final Button del = makeIconBtn("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z", "mod-table-btn-delete");
            private final HBox box = new HBox(6, edit, del);
            { box.setAlignment(Pos.CENTER); }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                edit.setOnAction(e -> openEditRessource(getTableRow().getItem()));
                del.setOnAction(e -> deleteRessource(getTableRow().getItem()));
                setGraphic(box);
            }
        });

        resourcesPlaceholderTable.getColumns().setAll(colTitre, colType, colContenu, colModule, colOrdre, colActif, colActions);
        resourcesPlaceholderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        filteredResourceList = new FilteredList<>(resourceMasterList, r -> true);
        resourcesPlaceholderTable.setItems(filteredResourceList);
    }

    private void applyResourceModuleFilter() {
        if (filteredResourceList == null) {
            return;
        }
        String sel = resourceModuleFilter != null ? resourceModuleFilter.getSelectionModel().getSelectedItem() : null;
        if (sel == null || "Tous les modules".equals(sel)) {
            filteredResourceList.setPredicate(r -> true);
        } else {
            filteredResourceList.setPredicate(r -> sel.equals(moduleTitleById.get(r.getModuleId())));
        }
        applyTableHeight(resourcesPlaceholderTable);
    }

    private void reloadResources() {
        if (resourcesPlaceholderTable == null) {
            return;
        }
        try {
            List<Ressource> all = ressourceService.findAll();
            resourceCountByModuleId.clear();
            for (Ressource r : all) {
                resourceCountByModuleId.merge(r.getModuleId(), 1, Integer::sum);
            }
            resourceMasterList.setAll(all);
            applyResourceModuleFilter();
            resourcesPlaceholderTable.refresh();
            modulesTable.refresh();
            applyTableHeight(resourcesPlaceholderTable);
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", "Impossible de charger les ressources : " + e.getMessage());
        }
    }

    private void openEditRessource(Ressource r) {
        try {
            Ressource fresh = ressourceService.findById(r.getId()).orElse(r);
            AppState.beginAdminRessourceEdit(fresh);
            MainApp.showAdminRessourceEdit();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void deleteRessource(Ressource r) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer la ressource « " + r.getTitre() + " » ?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            ressourceService.delete(r.getId());
            reloadResources();
            resourcesPlaceholderTable.getSelectionModel().clearSelection();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    /**
     * Tente de résoudre un fichier image local (absolu, relatif au répertoire de travail, séparateurs / ou \).
     */
    private static Optional<File> resolveLocalImageFile(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String p = path.trim();
        File[] candidates = {
                new File(p),
                new File(p.replace('/', File.separatorChar)),
                new File(System.getProperty("user.dir"), p),
                new File(System.getProperty("user.dir"), p.replace('/', File.separatorChar)),
        };
        for (File f : candidates) {
            try {
                File abs = f.getAbsoluteFile();
                if (abs.isFile()) {
                    return Optional.of(abs);
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return Optional.empty();
    }

    /**
     * Vignette 48×48 arrondie (rounded-lg) pour la colonne Image (URL http(s), file: ou chemin local).
     */
    private static Node moduleImageThumbnail(String raw) {
        if (raw == null || raw.isBlank()) {
            return thumbPlaceholder();
        }
        String t = raw.trim();
        String urlStr;

        if (t.startsWith("http://") || t.startsWith("https://")) {
            return buildRoundedThumbnail(t, true);
        }
        if (t.startsWith("file:")) {
            try {
                File f = new File(URI.create(t));
                if (f.isFile()) {
                    return buildRoundedThumbnail(f.toURI().toString(), false);
                }
            } catch (Exception ignored) {
                // fallback : charger comme URL
            }
            return buildRoundedThumbnail(t, true);
        }

        Optional<File> local = resolveLocalImageFile(t);
        if (local.isEmpty()) {
            return thumbPlaceholder();
        }
        return buildRoundedThumbnail(local.get().toURI().toString(), false);
    }

    private static Node buildRoundedThumbnail(String urlStr, boolean backgroundLoading) {
        Image image = new Image(urlStr, MODULE_IMAGE_THUMB, MODULE_IMAGE_THUMB, true, true, backgroundLoading);
        ImageView iv = new ImageView(image);
        iv.setFitWidth(MODULE_IMAGE_THUMB);
        iv.setFitHeight(MODULE_IMAGE_THUMB);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        Rectangle clip = new Rectangle(MODULE_IMAGE_THUMB, MODULE_IMAGE_THUMB);
        clip.setArcWidth(MODULE_IMAGE_CORNER_RADIUS * 2);
        clip.setArcHeight(MODULE_IMAGE_CORNER_RADIUS * 2);
        iv.setClip(clip);
        iv.getStyleClass().add("mod-table-module-thumb");

        StackPane stack = new StackPane(iv);
        stack.setMinSize(MODULE_IMAGE_THUMB, MODULE_IMAGE_THUMB);
        stack.setMaxSize(MODULE_IMAGE_THUMB, MODULE_IMAGE_THUMB);

        Runnable showError = () -> {
            stack.getChildren().setAll(thumbPlaceholder());
        };

        if (!backgroundLoading) {
            if (image.isError()) {
                return thumbPlaceholder();
            }
            return stack;
        }

        image.errorProperty().addListener((obs, oldVal, err) -> {
            if (Boolean.TRUE.equals(err)) {
                showError.run();
            }
        });
        if (image.isError()) {
            showError.run();
        }
        return stack;
    }

    private static Region thumbPlaceholder() {
        Region ph = new Region();
        ph.getStyleClass().add("mod-table-thumb-placeholder");
        ph.setMinSize(MODULE_IMAGE_THUMB, MODULE_IMAGE_THUMB);
        ph.setMaxSize(MODULE_IMAGE_THUMB, MODULE_IMAGE_THUMB);
        return ph;
    }

    private static String shorten(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static void stylePillBadge(Label badge, double maxWidth) {
        badge.setTextOverrun(OverrunStyle.CLIP);
        badge.setMaxWidth(maxWidth);
        badge.setWrapText(false);
    }

    private static Button makeIconBtn(String svgPath, String styleClass) {
        Region icon = new Region();
        icon.setStyle("-fx-shape: \"" + svgPath + "\"; -fx-scale-shape: true; -fx-background-color: currentColor;");
        icon.setPrefSize(15, 15);
        icon.setMaxSize(15, 15);
        icon.setMinSize(15, 15);
        Button btn = new Button();
        btn.setGraphic(icon);
        btn.getStyleClass().add(styleClass);
        btn.setFocusTraversable(false);
        return btn;
    }

    private static String formatNiveauLibelle(ModuleNiveau n) {
        if (n == null) {
            return "Moyen";
        }
        switch (n) {
            case moyen:
                return "Moyen";
            case difficile:
                return "Difficile";
            case facile:
                return "Facile";
            default:
                return n.name();
        }
    }

    private static String niveauBadgeClass(ModuleNiveau n) {
        if (n == null) {
            return "mod-badge-niveau-moyen";
        }
        switch (n) {
            case difficile:
                return "mod-badge-niveau-difficile";
            case facile:
                return "mod-badge-niveau-facile";
            case moyen:
            default:
                return "mod-badge-niveau-moyen";
        }
    }

    private static String formatCategorieLibelle(ModuleContent m) {
        ModuleCategorie c = m.getCategorieEnum();
        if (c != null) {
            return c.getLibelle();
        }
        String s = m.getCategorie();
        return s != null ? s : "";
    }

    private static String articlesLibelle(int n) {
        if (n <= 0) {
            return "0 article";
        }
        if (n == 1) {
            return "1 article";
        }
        return n + " articles";
    }

    private static String ressourcesLibelle(int n) {
        if (n <= 0) {
            return "0 ressource";
        }
        if (n == 1) {
            return "1 ressource";
        }
        return n + " ressources";
    }

    private void openEditModule(ModuleContent m) {
        AppState.beginAdminModuleEdit(m);
        try {
            MainApp.showAdminModuleEdit();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void reloadModules() {
        try {
            List<ModuleContent> list = moduleService.findAll();
            articleCountByModuleId.clear();
            for (ModuleContent m : list) {
                articleCountByModuleId.put(m.getId(), blogService.countByModuleId(m.getId()));
            }
            moduleTitleById.clear();
            for (ModuleContent m : list) {
                moduleTitleById.put(m.getId(), m.getTitre() != null ? m.getTitre() : "");
            }
            masterList.setAll(list);
            if (filteredList == null) {
                filteredList = new FilteredList<>(masterList, m -> true);
                modulesTable.setItems(filteredList);
            }
            modulesTable.refresh();
            refreshResourceModuleCombo(list);
            applyFilters();
            reloadResources();
            refreshModuleStatsAndHistory();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void setupModuleChartsAndHistory() {
        // Charts dessinés via Canvas dans refreshModuleStatsAndHistory
        if (moduleLevelChart != null) {
            moduleLevelChart.setAnimated(true);
            moduleLevelChart.setLegendVisible(false);
            moduleLevelChart.setCategoryGap(20);
            moduleLevelChart.setBarGap(4);
            moduleLevelChart.getData().clear();
        }
    }

    // Couleurs pastels pour les PieCharts Canvas
    private static final Color[] PASTEL_CATEGORY_COLORS = {
        Color.web("#A8D8EA"),
        Color.web("#B8E0D2"),
        Color.web("#FFD6A5"),
        Color.web("#D4A5C9"),
        Color.web("#FFDDD2"),
        Color.web("#C9E4CA"),
        Color.web("#BDE0FE")
    };

    private static final Color[] PASTEL_LEVEL_COLORS = {
        Color.web("#B5EAD7"),
        Color.web("#FFD6A5"),
        Color.web("#FFADAD")
    };

    private void drawPieChart(StackPane pane, String[] labels, int[] values, Color[] colors) {
        pane.getChildren().clear();
        double size = 220;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        int total = 0;
        for (int v : values) total += v;

        double cx = size / 2, cy = size / 2, r = (size - 20) / 2;

        if (total == 0) {
            gc.setFill(Color.web("#e5e7eb"));
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            double innerR = r * 0.52;
            gc.setFill(Color.WHITE);
            gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
            gc.setFill(Color.web("#9ca3af"));
            gc.setFont(Font.font("System", FontWeight.NORMAL, 12));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("Aucune donnée", cx, cy + 5);
            pane.getChildren().add(canvas);
            return;
        }

        double startAngle = -90;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) continue;
            double sweep = 360.0 * values[i] / total;
            gc.setFill(colors[i % colors.length]);
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, startAngle, sweep, javafx.scene.shape.ArcType.ROUND);
            startAngle += sweep;
        }

        // Séparateurs blancs entre slices
        startAngle = -90;
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2.5);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) continue;
            double rad = Math.toRadians(startAngle);
            gc.strokeLine(cx, cy, cx + r * Math.cos(rad), cy + r * Math.sin(rad));
            startAngle += 360.0 * values[i] / total;
        }

        // Cercle blanc au centre (donut)
        double innerR = r * 0.52;
        gc.setFill(Color.WHITE);
        gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        // Total au centre
        gc.setFill(Color.web("#374151"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 24));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.valueOf(total), cx, cy + 6);
        gc.setFont(Font.font("System", FontWeight.NORMAL, 11));
        gc.setFill(Color.web("#9ca3af"));
        gc.fillText("total", cx, cy + 20);

        pane.getChildren().add(canvas);

        // Légende
        HBox legendRow = new HBox(14);
        legendRow.setAlignment(Pos.CENTER);
        legendRow.setTranslateY(size / 2 + 14);
        for (int i = 0; i < labels.length; i++) {
            if (values[i] == 0) continue;
            Color c = colors[i % colors.length];
            javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(12, 12);
            rect.setFill(c);
            rect.setArcWidth(3);
            rect.setArcHeight(3);
            Label lbl = new Label(labels[i] + " (" + values[i] + ")");
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
            HBox item = new HBox(5, rect, lbl);
            item.setAlignment(Pos.CENTER_LEFT);
            legendRow.getChildren().add(item);
        }
        pane.getChildren().add(legendRow);
    }

    private void applyPastelColors(Object unused1, Object unused2) {
        // Remplacé par drawPieChart - méthode conservée pour compatibilité
    }

    private void refreshModuleStatsAndHistory() {
        // Compteurs pour statistiques
        int countCom = 0, countAuto = 0, countEdu = 0;
        int countDebutant = 0, countMoyen = 0, countAvance = 0;

        for (ModuleCategorie cat : ModuleCategorie.values()) {
            int n = (int) masterList.stream().filter(m -> cat.equals(m.getCategorieEnum())).count();
            if (cat == ModuleCategorie.COMMUNICATION) countCom = n;
            else if (cat == ModuleCategorie.AUTONOMIE) countAuto = n;
            else if (cat == ModuleCategorie.EMOTIONS) countEdu = n;
        }
        ModuleNiveau[] niveauOrder = { ModuleNiveau.facile, ModuleNiveau.moyen, ModuleNiveau.difficile };
        for (ModuleNiveau niv : niveauOrder) {
            int n = (int) masterList.stream().filter(m -> niv.equals(m.getNiveau())).count();
            if (niv == ModuleNiveau.facile) countDebutant = n;
            else if (niv == ModuleNiveau.moyen) countMoyen = n;
            else if (niv == ModuleNiveau.difficile) countAvance = n;
        }

        // Dessiner les pie charts via Canvas
        if (categoryChartPane != null) {
            String[] catLabels = new String[ModuleCategorie.values().length];
            int[] catValues = new int[ModuleCategorie.values().length];
            int ci = 0;
            for (ModuleCategorie cat : ModuleCategorie.values()) {
                catLabels[ci] = cat.getLibelle();
                catValues[ci] = (int) masterList.stream().filter(m -> cat.equals(m.getCategorieEnum())).count();
                ci++;
            }
            drawPieChart(categoryChartPane, catLabels, catValues, PASTEL_CATEGORY_COLORS);
        }

        if (levelChartPane != null) {
            String[] lvlLabels = { formatNiveauLibelle(ModuleNiveau.facile), formatNiveauLibelle(ModuleNiveau.moyen), formatNiveauLibelle(ModuleNiveau.difficile) };
            int[] lvlValues = { countDebutant, countMoyen, countAvance };
            drawPieChart(levelChartPane, lvlLabels, lvlValues, PASTEL_LEVEL_COLORS);
        }

        // BarChart
        if (moduleLevelChart != null) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Modules");
            for (ModuleNiveau niv : niveauOrder) {
                int n = (int) masterList.stream().filter(m -> niv.equals(m.getNiveau())).count();
                series.getData().add(new XYChart.Data<>(formatNiveauLibelle(niv), n));
            }
            moduleLevelChart.getData().clear();
            moduleLevelChart.getData().add(series);
        }

        // Mettre à jour les badges de statistiques
        final int finalCountCom = countCom;
        final int finalCountAuto = countAuto;
        final int finalCountEdu = countEdu;
        final int finalCountDebutant = countDebutant;
        final int finalCountMoyen = countMoyen;
        final int finalCountAvance = countAvance;
        final int finalTotalModules = masterList.size();

        Platform.runLater(() -> {
            try {
                if (statComLabel != null) statComLabel.setText(String.valueOf(finalCountCom));
                if (statAutoLabel != null) statAutoLabel.setText(String.valueOf(finalCountAuto));
                if (statEduLabel != null) statEduLabel.setText(String.valueOf(finalCountEdu));
                if (statDebutantLabel != null) statDebutantLabel.setText(String.valueOf(finalCountDebutant));
                if (statMoyenLabel != null) statMoyenLabel.setText(String.valueOf(finalCountMoyen));
                if (statAvanceLabel != null) statAvanceLabel.setText(String.valueOf(finalCountAvance));
                if (statTotalModulesLabel != null) statTotalModulesLabel.setText(String.valueOf(finalTotalModules));
                if (statTotalResourcesLabel != null) statTotalResourcesLabel.setText(String.valueOf(ressourceService.findAll().size()));
                if (statTotalArticlesLabel != null) statTotalArticlesLabel.setText(String.valueOf(blogService.findAll().size()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void refreshResourceModuleCombo(List<ModuleContent> list) {
        if (resourceModuleFilter == null) {
            return;
        }
        String sel = resourceModuleFilter.getSelectionModel().getSelectedItem();
        resourceModuleFilter.getItems().clear();
        resourceModuleFilter.getItems().add("Tous les modules");
        for (ModuleContent m : list) {
            if (m.getTitre() != null && !m.getTitre().isBlank()) {
                resourceModuleFilter.getItems().add(m.getTitre());
            }
        }
        if (sel != null && resourceModuleFilter.getItems().contains(sel)) {
            resourceModuleFilter.getSelectionModel().select(sel);
        } else {
            resourceModuleFilter.getSelectionModel().selectFirst();
        }
    }

    private void applyFilters() {
        if (filteredList == null) {
            return;
        }
        String f1 = filterField != null && filterField.getText() != null ? filterField.getText().trim().toLowerCase(Locale.FRENCH) : "";
        String f2 = topSearchField != null && topSearchField.getText() != null ? topSearchField.getText().trim().toLowerCase(Locale.FRENCH) : "";
        ModuleCategorie selectedCategory = categoryFilterCombo != null ? categoryFilterCombo.getValue() : null;
        
        Predicate<ModuleContent> p = m -> {
            String titre = m.getTitre() != null ? m.getTitre().toLowerCase(Locale.FRENCH) : "";
            String cat = m.getCategorie() != null ? m.getCategorie().toLowerCase(Locale.FRENCH) : "";
            String desc = m.getDescription() != null ? m.getDescription().toLowerCase(Locale.FRENCH) : "";
            String cont = m.getContenu() != null ? m.getContenu().toLowerCase(Locale.FRENCH) : "";
            
            // Filtrage par texte (recherche)
            boolean ok1 = f1.isEmpty() || titre.contains(f1) || cat.contains(f1) || desc.contains(f1) || cont.contains(f1);
            boolean ok2 = f2.isEmpty() || titre.contains(f2) || cat.contains(f2) || desc.contains(f2) || cont.contains(f2);
            
            // Filtrage par catégorie
            boolean okCategory = selectedCategory == null || m.getCategorieEnum() == selectedCategory;
            
            return ok1 && ok2 && okCategory;
        };
        filteredList.setPredicate(p);
        applyTableHeight(modulesTable);
    }

    @FXML
    public void onApplyFilter() {
        applyFilters();
    }

    @FXML
    public void onSortAsc() {
        Collator coll = Collator.getInstance(Locale.FRENCH);
        Comparator<ModuleContent> cmp = Comparator.comparing(
                m -> m.getTitre() != null ? m.getTitre() : "",
                coll);
        FXCollections.sort(masterList, cmp);
    }

    private void deleteModule(ModuleContent m) {
        // Vérifier si le module a des articles
        int articleCount = 0;
        try {
            articleCount = blogService.countByModuleId(m.getId());
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", "Impossible de vérifier les articles associés : " + e.getMessage());
            return;
        }
        
        // Message de confirmation adapté selon la présence d'articles
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText(null);
        
        if (articleCount > 0) {
            String message = "Ce module contient " + articleCount + " article(s).\n\n"
                           + "Êtes-vous sûr de vouloir supprimer ce module ?\n"
                           + "Tous les articles associés seront également supprimés.";
            confirm.setContentText(message);
            confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        } else {
            confirm.setContentText("Supprimer le module « " + m.getTitre() + " » ?");
        }
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || (articleCount > 0 && result.get() != ButtonType.YES) || (articleCount == 0 && result.get() != ButtonType.OK)) {
            return;
        }
        
        try {
            // Supprimer d'abord les articles associés si présents
            if (articleCount > 0) {
                List<org.example.models.BlogArticle> articles = blogService.findByModule(m.getId());
                for (org.example.models.BlogArticle article : articles) {
                    blogService.delete(article.getId());
                }
            }
            
            // Supprimer le module
            moduleService.delete(m.getId());
            ModuleActionHistory.record("Suppression", m.getTitre(), m.getId());
            reloadModules();
            modulesTable.getSelectionModel().clearSelection();
            
            alert(Alert.AlertType.INFORMATION, "Succès", "Le module et ses articles associés ont été supprimés.");
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
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
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavStocks() {
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavOrders() {
        try {
            MainApp.showDashboard(1);
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavEvents() {
        try {
            AppState.setPendingAdminUsersSection("events");
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavTopics() {
        try {
            AppState.setPendingAdminUsersSection("thematiques");
            MainApp.showAdminUsers();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNavModules() {
        try {
            MainApp.showAdminModules();
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

    @FXML
    public void onNewModule() {
        try {
            MainApp.showAdminModuleAdd();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onNewRessource() {
        try {
            MainApp.showAdminRessourceAdd();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    public void onExportModulesPdf() {
        if (modulesTable == null || modulesTable.getScene() == null) {
            return;
        }
        Stage st = (Stage) modulesTable.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter les modules en PDF");
        fc.setInitialFileName("modules-auticare.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = fc.showSaveDialog(st);
        if (f == null) {
            return;
        }
        try {
            List<ModuleContent> toExport = new ArrayList<>();
            if (filteredList != null) {
                toExport.addAll(filteredList);
            } else {
                toExport.addAll(masterList);
            }
            ModulesPdfExporter.export(toExport, articleCountByModuleId, resourceCountByModuleId, f);
            alert(Alert.AlertType.INFORMATION, "Export PDF",
                    "Fichier enregistré :\n" + f.getAbsolutePath());
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Export PDF",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
