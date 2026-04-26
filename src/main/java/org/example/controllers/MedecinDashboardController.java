package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.animation.PauseTransition;
import javafx.concurrent.Worker;
import javafx.util.Duration;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import netscape.javascript.JSObject;
import org.example.MainApp;
import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.Availability;
import org.example.models.Role;
import org.example.models.MedecinPatientNote;
import org.example.models.User;
import org.example.services.AppointmentService;
import org.example.services.AvailabilityService;
import org.example.services.GoogleCalendarApiService;
import org.example.services.GoogleCalendarTemplateLinks;
import org.example.services.MedecinPatientNoteService;
import org.example.services.TwilioSmsService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.NoteHtmlUtil;
import org.example.utils.NotePdfExporter;
import org.example.utils.RdvNotesFormat;
import org.example.utils.UserAvatarGraphic;

import java.text.Normalizer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Portail réservé aux comptes {@link Role#MEDECIN}.
 */
public class MedecinDashboardController {

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);
    /** Affichage « 11/04/2026 à 09:00 – 09:30 » dans l’écran modifier RDV. */
    private static final DateTimeFormatter RDV_EDIT_DATE_LINE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE);
    private static final DateTimeFormatter DISPO_SEARCH_DAY_FR = DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH);
    private static final DateTimeFormatter DISPO_SEARCH_DATE_LONG_FR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter DISPO_JS_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String FULLCALENDAR_VERSION = "6.1.15";
    private static final String FULLCALENDAR_CSS_CDN =
            "https://cdn.jsdelivr.net/npm/fullcalendar@" + FULLCALENDAR_VERSION + "/index.global.min.css";
    private static final String FULLCALENDAR_JS_CDN =
            "https://cdn.jsdelivr.net/npm/fullcalendar@" + FULLCALENDAR_VERSION + "/index.global.min.js";
    private static final DateTimeFormatter[] DISPO_SEARCH_DATE_PATTERNS = {
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.FRENCH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH),
            DateTimeFormatter.ofPattern("d/M/yy", Locale.FRENCH),
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.FRENCH),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.FRENCH),
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.FRENCH),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    private enum MainView {
        HOME, DISPO, NOTES, RDV, NOTIFS
    }

    @FXML
    private TextField searchField;
    @FXML
    private ScrollPane medHomeScroll;
    @FXML
    private ScrollPane medDispoScroll;
    @FXML
    private ScrollPane medNotesScroll;
    @FXML
    private ScrollPane medRdvScroll;
    @FXML
    private ScrollPane medNotifScroll;
    @FXML
    private Label medNotifTotalLabel;
    @FXML
    private Label notifStatNonLuesLabel;
    @FXML
    private Label notifStatDemandesLabel;
    @FXML
    private Label notifStatAujourdhuiLabel;
    @FXML
    private Label notifStatSemaineLabel;
    @FXML
    private Label medTopbarBellLabel;
    @FXML
    private Label medNotifHistoryCountLabel;
    @FXML
    private Label notifEmptyLabel;
    @FXML
    private ScrollPane notifListScroll;
    @FXML
    private VBox notifListContainer;
    @FXML
    private Label medRdvTotalLabel;
    @FXML
    private Label rdvStatConfirmesLabel;
    @FXML
    private Label rdvStatAttenteLabel;
    @FXML
    private Label rdvStatAnnulesLabel;
    @FXML
    private Label rdvStatAujourdhuiLabel;
    @FXML
    private ComboBox<String> rdvSortCombo;
    @FXML
    private TextField rdvSearchField;
    @FXML
    private VBox rdvEmptyState;
    @FXML
    private VBox rdvBoardRoot;
    @FXML
    private Label rdvKanbanTitleConfirm;
    @FXML
    private Label rdvKanbanTitleWait;
    @FXML
    private Label rdvKanbanTitleCancel;
    @FXML
    private VBox rdvKanbanBodyConfirm;
    @FXML
    private VBox rdvKanbanBodyWait;
    @FXML
    private VBox rdvKanbanBodyCancel;
    @FXML
    private Label medNotesTotalLabel;
    @FXML
    private Label medNotesNewHint;
    @FXML
    private ComboBox<PatientNoteChoice> notesPatientCombo;
    @FXML
    private TextField notesTableSearchPatientField;
    @FXML
    private DatePicker notesTableDateFilterPicker;
    @FXML
    private HTMLEditor notesHtmlEditor;
    @FXML
    private TableView<NoteRow> notesTableView;
    @FXML
    private StackPane medAvatarHost;
    @FXML
    private Label medUserNameLabel;
    @FXML
    private Label medUserEmailLabel;
    @FXML
    private Label medWelcomeTitleLabel;
    @FXML
    private Label statPatientsLabel;
    @FXML
    private Label statRdvLabel;
    @FXML
    private Label statNotesLabel;
    @FXML
    private Label statDispoLabel;
    @FXML
    private Label medProfileNameLabel;
    @FXML
    private Label medProfileEmailLabel;
    @FXML
    private Label medTodayDateLabel;
    @FXML
    private Label medTodayRdvLabel;
    @FXML
    private Button navBtnDashboard;
    @FXML
    private Button navBtnDispo;
    @FXML
    private Button navBtnNotes;
    @FXML
    private Button navBtnRdv;
    @FXML
    private Button navBtnNotif;
    @FXML
    private VBox medSidebarBrandBox;
    @FXML
    private VBox medSidebarFooterBox;
    @FXML
    private Hyperlink medSidebarLinkAccueil;
    @FXML
    private Hyperlink medSidebarLinkRetour;
    @FXML
    private TextField dispoSlotSearchField;
    @FXML
    private Label dispoMonthLabel;
    @FXML
    private GridPane dispoCalendarGrid;
    @FXML
    private WebView dispoFullCalendarView;
    @FXML
    private LineChart<String, Number> medActivityChart;

    private final AvailabilityService availabilityService = new AvailabilityService();
    private final AppointmentService appointmentService = new AppointmentService();
    private final MedecinPatientNoteService medecinPatientNoteService = new MedecinPatientNoteService();
    private final UserService userService = new UserService();
    private int currentDoctorId;
    private YearMonth dispoMonth = YearMonth.from(LocalDate.now());
    private LocalDate selectedDispoDate = LocalDate.now();
    private List<Availability> cachedAvailabilities = new ArrayList<>();
    private Object dispoJsBridge;
    private boolean dispoCalendarBridgeInstalled;
    private boolean dispoCalendarStatusBridgeInstalled;
    private final ObservableList<NoteRow> notesMasterList = FXCollections.observableArrayList();
    private FilteredList<NoteRow> notesFilteredList;
    /** Créneaux dont la suppression est interdite (RDV non annulé lié). */
    private Set<Integer> disponibiliteIdsWithBlockingRdv = Set.of();
    private final List<Appointment> rdvAppointments = new ArrayList<>();
    private MainView mainView = MainView.HOME;
    /** Entrées du menu latéral filtrées par {@link #searchField}. */
    private final List<SidebarSearchTarget> sidebarSearchTargets = new ArrayList<>();

    @FXML
    private void initialize() {
        User u = AppState.getCurrentUser();
        if (u == null || u.getRole() != Role.MEDECIN) {
            try {
                MainApp.showLogin();
            } catch (IOException e) {
                alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
            }
            return;
        }
        currentDoctorId = u.getId();
        // Exposer un objet bridge public dédié (plus fiable avec WebView/JSObject qu'un gros controller FXML).
        dispoJsBridge = new DispoCalendarJsBridge(this);
        applyUser(u);
        if (medTodayDateLabel != null) {
            medTodayDateLabel.setText(LocalDate.now().format(
                    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)));
        }
        if (statNotesLabel != null) {
            statNotesLabel.setText("0");
        }
        if (statDispoLabel != null) {
            statDispoLabel.setText("Actif");
        }
        setMainView(MainView.HOME);
        refreshMedecinPendingDemandesUi();
        if (dispoSlotSearchField != null) {
            dispoSlotSearchField.textProperty().addListener((o, a, b) -> {
                if (mainView == MainView.DISPO) {
                    rebuildCalendarGrid();
                    rebuildDispoFullCalendar();
                }
            });
        }
        setupNotesTable();
        if (notesHtmlEditor != null) {
            notesHtmlEditor.setHtmlText(NoteHtmlUtil.emptyEditorHtml());
        }
        if (rdvSortCombo != null) {
            rdvSortCombo.getItems().setAll(
                    "Trier par date (ancien — récent)",
                    "Trier par date (récent — ancien)");
            rdvSortCombo.getSelectionModel().select(1);
            rdvSortCombo.valueProperty().addListener((o, a, b) -> {
                if (mainView == MainView.RDV) {
                    rebuildRdvList();
                }
            });
        }
        if (rdvSearchField != null) {
            rdvSearchField.setTooltip(new Tooltip(
                    "Filtre en direct : patient, date affichée, statut, téléphone, e-mail ou motif."));
            rdvSearchField.textProperty().addListener((o, a, b) -> {
                if (mainView == MainView.RDV) {
                    rebuildRdvList();
                }
            });
        }
        initMedActivityChart();
        refreshRdvFromDb();
        refreshNotesFromDb();
        setupSidebarSearchFilter();
    }

    private void setupSidebarSearchFilter() {
        sidebarSearchTargets.clear();
        addSidebarSearchEntry(medSidebarBrandBox, "médecin", "medicin", "rôle", "role", "logo", "auticare");
        addSidebarSearchEntry(navBtnDashboard, "tableau", "dashboard", "accueil", "statistiques", "activité");
        addSidebarSearchEntry(navBtnDispo, "disponibilité", "disponibilites", "créneau", "creneau", "agenda", "calendrier");
        addSidebarSearchEntry(navBtnNotes, "note", "notes", "patient");
        addSidebarSearchEntry(navBtnRdv, "rendez-vous", "rendezvous", "rdv", "rendez");
        addSidebarSearchEntry(navBtnNotif, "notification", "alerte", "demande");
        addSidebarSearchEntry(medSidebarLinkAccueil, "accueil", "retour tableau");
        addSidebarSearchEntry(medSidebarLinkRetour, "retour", "site", "public", "accueil site");
        if (searchField != null) {
            searchField.textProperty().addListener((o, a, b) -> applySidebarSearchFilter());
            applySidebarSearchFilter();
        }
    }

    private void addSidebarSearchEntry(Node node, String... extraKeywords) {
        if (node == null) {
            return;
        }
        List<String> needles = new ArrayList<>();
        if (node instanceof Labeled labeled && labeled.getText() != null && !labeled.getText().isBlank()) {
            String t = normalizeSidebarSearchText(labeled.getText());
            if (!t.isEmpty()) {
                needles.add(t);
            }
        }
        for (String kw : extraKeywords) {
            String t = normalizeSidebarSearchText(kw);
            if (!t.isEmpty()) {
                needles.add(t);
            }
        }
        sidebarSearchTargets.add(new SidebarSearchTarget(node, needles.stream().distinct().toList()));
    }

    private static String normalizeSidebarSearchText(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.FRENCH).replaceAll("[^a-z0-9àâäéèêëïîôùûç\\s-]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private void applySidebarSearchFilter() {
        String q = normalizeSidebarSearchText(searchField != null ? searchField.getText() : "");
        boolean showAll = q.isEmpty();
        for (SidebarSearchTarget t : sidebarSearchTargets) {
            boolean match = showAll || t.matches(q);
            t.node.setVisible(match);
            t.node.setManaged(match);
        }
        syncMedSidebarFooterVisibility();
    }

    private void syncMedSidebarFooterVisibility() {
        if (medSidebarFooterBox == null) {
            return;
        }
        boolean any = medSidebarFooterBox.getChildren().stream().anyMatch(Node::isVisible);
        medSidebarFooterBox.setVisible(any);
        medSidebarFooterBox.setManaged(any);
    }

    private static final class SidebarSearchTarget {
        private final Node node;
        private final List<String> needles;

        private SidebarSearchTarget(Node node, List<String> needles) {
            this.node = node;
            this.needles = needles;
        }

        private boolean matches(String q) {
            for (String n : needles) {
                if (n.startsWith(q)) {
                    return true;
                }
                if (q.length() >= 3 && n.contains(q)) {
                    return true;
                }
                for (String word : n.split(" ")) {
                    if (!word.isEmpty() && word.startsWith(q)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private void initMedActivityChart() {
        if (medActivityChart == null) {
            return;
        }
        medActivityChart.setAnimated(false);
        medActivityChart.setCreateSymbols(false);
        medActivityChart.setLegendVisible(true);
        CategoryAxis xa = (CategoryAxis) medActivityChart.getXAxis();
        xa.setTickMarkVisible(false);
        NumberAxis ya = (NumberAxis) medActivityChart.getYAxis();
        ya.setForceZeroInRange(false);
        ya.setMinorTickCount(0);
        String[] labels = new String[6];
        YearMonth start = YearMonth.now().minusMonths(5);
        DateTimeFormatter mf = DateTimeFormatter.ofPattern("LLL", Locale.FRENCH);
        for (int i = 0; i < 6; i++) {
            String raw = start.plusMonths(i).format(mf);
            labels[i] = raw.substring(0, 1).toUpperCase(Locale.FRENCH) + raw.substring(1).replace(".", "");
        }
        medActivityChart.getData().clear();
        addActivitySeries("Rendez-vous", labels, new double[]{2, 4, 3, 6, 5, 7});
        addActivitySeries("Demandes", labels, new double[]{1, 2, 4, 3, 5, 4});
        addActivitySeries("Notes", labels, new double[]{0, 1, 2, 2, 3, 5});
        addActivitySeries("Créneaux", labels, new double[]{3, 5, 4, 7, 6, 8});
    }

    private void addActivitySeries(String name, String[] categories, double[] values) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName(name);
        for (int i = 0; i < categories.length && i < values.length; i++) {
            s.getData().add(new XYChart.Data<>(categories[i], values[i]));
        }
        medActivityChart.getData().add(s);
    }

    private void updateHomePatientKpi() {
        if (statPatientsLabel == null) {
            return;
        }
        long n = rdvAppointments.stream().mapToInt(Appointment::getPatientId).distinct().count();
        statPatientsLabel.setText(String.valueOf(n));
    }

    private void updateMedTodayRdvBanner() {
        if (medTodayRdvLabel == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        long c = rdvAppointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.ANNULE
                        && a.getDateHeure() != null
                        && a.getDateHeure().toLocalDate().equals(today))
                .count();
        if (c == 0) {
            medTodayRdvLabel.setText("Aucun rendez-vous prévu");
        } else if (c == 1) {
            medTodayRdvLabel.setText("1 rendez-vous prévu");
        } else {
            medTodayRdvLabel.setText(c + " rendez-vous prévus");
        }
    }

    private void applyUser(User u) {
        String prenom = u.getPrenom() != null ? u.getPrenom().trim() : "";
        String nom = u.getNom() != null ? u.getNom().trim() : "";
        String displayDr;
        if (!prenom.isBlank() && !nom.isBlank()) {
            displayDr = "Dr " + prenom + " " + nom;
        } else if (!prenom.isBlank()) {
            displayDr = "Dr " + prenom;
        } else if (!nom.isBlank()) {
            displayDr = "Dr " + nom;
        } else if (u.getEmail() != null && !u.getEmail().isBlank()) {
            displayDr = u.getEmail();
        } else {
            displayDr = "Médecin";
        }
        if (medWelcomeTitleLabel != null) {
            medWelcomeTitleLabel.setText(prenom.isBlank()
                    ? "Bonjour, " + displayDr
                    : "Bonjour, Dr " + prenom);
        }
        if (medUserNameLabel != null) {
            medUserNameLabel.setText(displayDr);
        }
        if (medUserEmailLabel != null) {
            medUserEmailLabel.setText(u.getEmail() != null ? u.getEmail() : "");
        }
        if (medProfileNameLabel != null) {
            medProfileNameLabel.setText(displayDr);
        }
        if (medProfileEmailLabel != null) {
            medProfileEmailLabel.setText(u.getEmail() != null ? u.getEmail() : "");
        }
        if (medAvatarHost != null) {
            medAvatarHost.getChildren().setAll(
                    UserAvatarGraphic.build(u, 40, UserAvatarGraphic.initialsFor(u), "med-topbar-avatar"));
        }
    }

    @FXML
    private void onNavTableauBord() {
        setMainView(MainView.HOME);
    }

    @FXML
    private void onRdvSearchAction() {
        if (mainView == MainView.RDV) {
            rebuildRdvList();
        }
    }

    private void setMainView(MainView view) {
        mainView = view;
        boolean home = view == MainView.HOME;
        boolean dispo = view == MainView.DISPO;
        boolean notes = view == MainView.NOTES;
        boolean rdv = view == MainView.RDV;
        boolean notifs = view == MainView.NOTIFS;
        if (medHomeScroll != null) {
            medHomeScroll.setVisible(home);
            medHomeScroll.setManaged(home);
        }
        if (medDispoScroll != null) {
            medDispoScroll.setVisible(dispo);
            medDispoScroll.setManaged(dispo);
        }
        if (medNotesScroll != null) {
            medNotesScroll.setVisible(notes);
            medNotesScroll.setManaged(notes);
        }
        if (medRdvScroll != null) {
            medRdvScroll.setVisible(rdv);
            medRdvScroll.setManaged(rdv);
        }
        if (medNotifScroll != null) {
            medNotifScroll.setVisible(notifs);
            medNotifScroll.setManaged(notifs);
        }
        updateNavStyles(view);
        if (dispo) {
            refreshDispoFromDb();
            updateMonthTitle();
            rebuildCalendarGrid();
            rebuildDispoFullCalendar();
        }
        if (notes) {
            refreshNotesFromDb();
        }
        if (rdv) {
            refreshRdvFromDb();
        }
        if (notifs) {
            if (currentDoctorId > 0) {
                try {
                    appointmentService.markAllEnAttenteDemandesLuesForMedecin(currentDoctorId);
                } catch (SQLException ignored) {
                    // l’affichage des notifications reste disponible
                }
            }
            refreshNotifications();
        }
    }

    private void updateNavStyles(MainView view) {
        clearNavActive(navBtnDashboard);
        clearNavActive(navBtnDispo);
        clearNavActive(navBtnNotes);
        clearNavActive(navBtnRdv);
        clearNavActive(navBtnNotif);
        if (view == MainView.HOME && navBtnDashboard != null) {
            navBtnDashboard.getStyleClass().add("med-nav-item-active");
        } else if (view == MainView.DISPO && navBtnDispo != null) {
            navBtnDispo.getStyleClass().add("med-nav-item-active-dispo");
        } else if (view == MainView.NOTES && navBtnNotes != null) {
            navBtnNotes.getStyleClass().add("med-nav-item-active-dispo");
        } else if (view == MainView.RDV && navBtnRdv != null) {
            navBtnRdv.getStyleClass().add("med-nav-item-active-dispo");
        } else if (view == MainView.NOTIFS && navBtnNotif != null) {
            navBtnNotif.getStyleClass().add("med-nav-item-active-dispo");
        }
    }

    private static void clearNavActive(Button b) {
        if (b == null) {
            return;
        }
        b.getStyleClass().removeAll("med-nav-item-active", "med-nav-item-active-dispo");
    }

    private void refreshDispoFromDb() {
        try {
            cachedAvailabilities = availabilityService.findByDoctor(currentDoctorId);
        } catch (SQLException e) {
            cachedAvailabilities = new ArrayList<>();
            alert(Alert.AlertType.ERROR, "Disponibilités",
                    "Impossible de charger les créneaux : " + e.getMessage());
        }
        reloadDisponibiliteDeleteBlockSet();
        rebuildDispoFullCalendar();
    }

    private void reloadDisponibiliteDeleteBlockSet() {
        if (currentDoctorId <= 0) {
            disponibiliteIdsWithBlockingRdv = Set.of();
            return;
        }
        try {
            disponibiliteIdsWithBlockingRdv =
                    appointmentService.disponibiliteIdsLinkedToNonAnnuleRdvForMedecin(currentDoctorId);
        } catch (SQLException e) {
            disponibiliteIdsWithBlockingRdv = Set.of();
        }
    }

    private void updateMonthTitle() {
        if (dispoMonthLabel == null) {
            return;
        }
        String raw = dispoMonth.atDay(1).format(MONTH_TITLE);
        if (!raw.isEmpty()) {
            raw = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
        dispoMonthLabel.setText(raw);
    }

    @FXML
    private void onDispoPrevMonth() {
        dispoMonth = dispoMonth.minusMonths(1);
        if (!dispoMonth.isValidDay(selectedDispoDate.getDayOfMonth())) {
            selectedDispoDate = dispoMonth.atEndOfMonth();
        } else {
            selectedDispoDate = dispoMonth.atDay(selectedDispoDate.getDayOfMonth());
        }
        updateMonthTitle();
        rebuildCalendarGrid();
        rebuildDispoFullCalendar();
    }

    @FXML
    private void onDispoNextMonth() {
        dispoMonth = dispoMonth.plusMonths(1);
        if (!dispoMonth.isValidDay(selectedDispoDate.getDayOfMonth())) {
            selectedDispoDate = dispoMonth.atEndOfMonth();
        } else {
            selectedDispoDate = dispoMonth.atDay(selectedDispoDate.getDayOfMonth());
        }
        updateMonthTitle();
        rebuildCalendarGrid();
        rebuildDispoFullCalendar();
    }

    @FXML
    private void onNouvelleDisponibilite() {
        showAvailabilityEditor(null);
    }

    private void rebuildCalendarGrid() {
        if (dispoCalendarGrid == null) {
            return;
        }
        reloadDisponibiliteDeleteBlockSet();
        dispoCalendarGrid.getChildren().clear();
        dispoCalendarGrid.getColumnConstraints().clear();
        dispoCalendarGrid.getRowConstraints().clear();
        for (int c = 0; c < 7; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            cc.setMinWidth(72);
            dispoCalendarGrid.getColumnConstraints().add(cc);
        }
        RowConstraints dowRow = new RowConstraints();
        dowRow.setMinHeight(26);
        dowRow.setPrefHeight(30);
        dowRow.setMaxHeight(30);
        dowRow.setVgrow(Priority.NEVER);
        dowRow.setValignment(VPos.CENTER);
        dispoCalendarGrid.getRowConstraints().add(dowRow);
        for (int w = 0; w < 6; w++) {
            RowConstraints weekRow = new RowConstraints();
            weekRow.setMinHeight(104);
            weekRow.setPrefHeight(136);
            weekRow.setMaxHeight(136);
            weekRow.setVgrow(Priority.NEVER);
            weekRow.setValignment(VPos.TOP);
            dispoCalendarGrid.getRowConstraints().add(weekRow);
        }
        String[] dow = {"LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM"};
        for (int i = 0; i < 7; i++) {
            Label h = new Label(dow[i]);
            h.getStyleClass().add("med-cal-dow");
            h.setMaxWidth(Double.MAX_VALUE);
            h.setAlignment(Pos.CENTER);
            dispoCalendarGrid.add(h, i, 0);
        }
        LocalDate first = dispoMonth.atDay(1);
        int offset = (first.getDayOfWeek().getValue() + 6) % 7;
        int daysInMonth = dispoMonth.lengthOfMonth();
        int dayNum = 1;
        for (int week = 0; week < 6; week++) {
            for (int c = 0; c < 7; c++) {
                int row = week + 1;
                if (week == 0 && c < offset) {
                    StackPane empty = new StackPane();
                    empty.getStyleClass().addAll("med-cal-cell", "med-cal-cell-muted");
                    GridPane.setValignment(empty, VPos.TOP);
                    dispoCalendarGrid.add(empty, c, row);
                    continue;
                }
                if (dayNum > daysInMonth) {
                    StackPane empty = new StackPane();
                    empty.getStyleClass().addAll("med-cal-cell", "med-cal-cell-muted");
                    GridPane.setValignment(empty, VPos.TOP);
                    dispoCalendarGrid.add(empty, c, row);
                    continue;
                }
                LocalDate date = dispoMonth.atDay(dayNum);
                VBox cell = buildDayCell(date, dayNum);
                dispoCalendarGrid.add(cell, c, row);
                dayNum++;
            }
        }
    }

    /**
     * Vue calendrier moderne (FullCalendar via WebView) pour les disponibilités médecin.
     * La grille JavaFX reste disponible en secours visuel.
     */
    private void rebuildDispoFullCalendar() {
        if (dispoFullCalendarView == null) {
            return;
        }
        ensureDispoCalendarBridgeInstalled();
        String eventsJson = buildDisponibiliteEventsJson();
        String initialDate = dispoMonth.atDay(1).toString();
        String cssUrl = resolveFullCalendarCssUrl();
        String jsUrl = resolveFullCalendarJsUrl();
        String html = """
                <!doctype html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <link href="%s" rel="stylesheet"/>
                  <style>
                    html, body { margin:0; padding:0; background:#f4f7ff; font-family: "Segoe UI", Arial, sans-serif; }
                    .dispo-cal-legend {
                      display:flex; flex-wrap:wrap; align-items:center; gap:18px;
                      padding:10px 12px; margin:0 8px 8px 8px; border-radius:10px;
                      background:#fff; border:1px solid #dbe4f3; font-size:13px; color:#1e3558;
                      box-shadow:0 1px 3px rgba(30,53,88,.08);
                    }
                    .dispo-cal-legend span { display:inline-flex; align-items:center; gap:8px; font-weight:600; }
                    .dispo-cal-legend .dot { width:11px; height:11px; border-radius:50%%; display:inline-block; }
                    .dispo-cal-legend .dot-free { background:#16a34a; box-shadow:0 0 0 2px rgba(22,163,74,.25); }
                    .dispo-cal-legend .dot-busy { background:#ef4444; box-shadow:0 0 0 2px rgba(239,68,68,.25); }
                    #cal { max-width: 100%%; margin: 0; padding: 8px; }
                    .fc .fc-toolbar-title { font-size: 1.05rem; font-weight: 700; color:#1e3558; }
                    .fc .fc-button { background:#213a63; border-color:#213a63; }
                    .fc .fc-button:hover { background:#172846; border-color:#172846; }
                    .fc .fc-daygrid-event { border-radius: 8px; border: 0; padding: 2px 6px; }
                    .fc-event-main { font-weight: 600; }
                  </style>
                </head>
                <body>
                  <div class="dispo-cal-legend" aria-label="Légende">
                    <span><span class="dot dot-free"></span>Disponible</span>
                    <span><span class="dot dot-busy"></span>Pris par RDV</span>
                  </div>
                  <div id="cal"></div>
                  <script src="%s"></script>
                  <script>
                    function resolveAvailabilityId(eventObj) {
                      if (!eventObj) return null;
                      if (eventObj.id !== undefined && eventObj.id !== null && String(eventObj.id).trim() !== '') {
                        return String(eventObj.id);
                      }
                      const ext = eventObj.extendedProps || {};
                      if (ext.availabilityId !== undefined && ext.availabilityId !== null && String(ext.availabilityId).trim() !== '') {
                        return String(ext.availabilityId);
                      }
                      return null;
                    }
                    function emitDispoStatus(action, args) {
                      const encoded = (args || []).map(function(v) {
                        return encodeURIComponent(v == null ? '' : String(v));
                      });
                      try {
                        window.status = ['AC_DISPO', action].concat(encoded).join('|');
                      } catch (e) {
                        // ignore
                      }
                    }
                    function callJavaDispo(methodName, args, retryCount) {
                      const triesLeft = (retryCount === undefined || retryCount === null) ? 4 : retryCount;
                      const bridge = window.javaDispo;
                      const fn = bridge && bridge[methodName];
                      if (typeof fn === 'function') {
                        fn.apply(bridge, args || []);
                        return;
                      }
                      if (triesLeft > 0) {
                        setTimeout(function() { callJavaDispo(methodName, args, triesLeft - 1); }, 120);
                      }
                    }
                    const calendar = new FullCalendar.Calendar(document.getElementById('cal'), {
                      locale: 'fr',
                      initialView: 'dayGridMonth',
                      initialDate: '%s',
                      headerToolbar: { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,timeGridDay' },
                      buttonText: { today: "Aujourd'hui", month: 'Mois', week: 'Semaine', day: 'Jour' },
                      height: 'auto',
                      editable: true,
                      nowIndicator: true,
                      displayEventTime: false,
                      eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
                      events: %s,
                      dateClick: function(info) {
                        info.jsEvent.preventDefault();
                        emitDispoStatus('dateClick', [info.dateStr]);
                        callJavaDispo('onDateClick', [info.dateStr]);
                      },
                      eventClick: function(info) {
                        info.jsEvent.preventDefault();
                        const id = resolveAvailabilityId(info.event);
                        const linked = !!info.event.extendedProps.hasLinkedRdv;
                        if (id != null) {
                          emitDispoStatus('eventClick', [id, linked ? "1" : "0"]);
                          callJavaDispo('onEventClick', [id, linked ? "1" : "0"]);
                        }
                      },
                      eventDidMount: function(info) {
                        info.el.addEventListener('contextmenu', function(ev) {
                          ev.preventDefault();
                          const id = resolveAvailabilityId(info.event);
                          if (id != null) {
                            emitDispoStatus('eventContextMenu', [id]);
                            callJavaDispo('onEventContextMenu', [id]);
                          }
                        });
                        info.el.addEventListener('dblclick', function(ev) {
                          ev.preventDefault();
                          const id = resolveAvailabilityId(info.event);
                          const linked = !!info.event.extendedProps.hasLinkedRdv;
                          if (id != null) {
                            emitDispoStatus('eventClick', [id, linked ? "1" : "0"]);
                            callJavaDispo('onEventClick', [id, linked ? "1" : "0"]);
                          }
                        });
                      },
                      eventDrop: function(info) {
                        const id = resolveAvailabilityId(info.event);
                        if (id != null) {
                          emitDispoStatus('eventMove', [id, info.event.startStr, info.event.endStr || ""]);
                          callJavaDispo('onEventMove', [id, info.event.startStr, info.event.endStr || ""]);
                        }
                      },
                      eventResize: function(info) {
                        const id = resolveAvailabilityId(info.event);
                        if (id != null) {
                          emitDispoStatus('eventMove', [id, info.event.startStr, info.event.endStr || ""]);
                          callJavaDispo('onEventMove', [id, info.event.startStr, info.event.endStr || ""]);
                        }
                      }
                    });
                    calendar.render();
                  </script>
                </body>
                </html>
                """.formatted(cssUrl, jsUrl, initialDate, eventsJson);
        Platform.runLater(() -> {
            dispoFullCalendarView.getEngine().loadContent(html);
            PauseTransition pause = new PauseTransition(Duration.millis(280));
            pause.setOnFinished(ev -> tryInjectDispoCalendarBridge());
            pause.play();
        });
    }

    private void tryInjectDispoCalendarBridge() {
        if (dispoCalendarViewUnavailable() || dispoJsBridge == null) {
            return;
        }
        try {
            JSObject window = (JSObject) dispoFullCalendarView.getEngine().executeScript("window");
            window.setMember("javaDispo", dispoJsBridge);
        } catch (Exception ex) {
            System.err.println("[AutiCare] Pont WebView FullCalendar (javaDispo) : " + ex.getMessage());
        }
    }

    // Méthodes publiques exposées directement à window.javaDispo (WebView JS bridge)
    public void onDateClick(String dateIso) {
        Platform.runLater(() -> dispCalBridgeDateClick(dateIso));
    }

    public void onEventClick(String availabilityId, String hasLinkedFlag) {
        Platform.runLater(() -> dispCalBridgeEventClick(availabilityId, hasLinkedFlag));
    }

    public void onEventMove(String availabilityId, String startIso, String endIso) {
        Platform.runLater(() -> dispCalBridgeEventMove(availabilityId, startIso, endIso));
    }

    public void onEventContextMenu(String availabilityId) {
        Platform.runLater(() -> dispCalBridgeContextMenu(availabilityId));
    }

    private void ensureDispoCalendarBridgeInstalled() {
        if (dispoCalendarViewUnavailable()) {
            return;
        }
        if (!dispoCalendarStatusBridgeInstalled) {
            dispoCalendarStatusBridgeInstalled = true;
            dispoFullCalendarView.getEngine().setOnStatusChanged(ev -> handleDispoStatusBridgeMessage(ev.getData()));
        }
        if (dispoCalendarBridgeInstalled) {
            return;
        }
        dispoCalendarBridgeInstalled = true;
        dispoFullCalendarView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                Platform.runLater(this::tryInjectDispoCalendarBridge);
                PauseTransition pause = new PauseTransition(Duration.millis(120));
                pause.setOnFinished(ev -> tryInjectDispoCalendarBridge());
                pause.play();
            }
        });
    }

    private boolean dispoCalendarViewUnavailable() {
        return dispoFullCalendarView == null || dispoFullCalendarView.getEngine() == null;
    }

    private void handleDispoStatusBridgeMessage(String payload) {
        if (payload == null || !payload.startsWith("AC_DISPO|")) {
            return;
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 2) {
            return;
        }
        String action = parts[1];
        String p1 = decodeBridgePart(parts, 2);
        String p2 = decodeBridgePart(parts, 3);
        String p3 = decodeBridgePart(parts, 4);
        Platform.runLater(() -> {
            switch (action) {
                case "dateClick" -> dispCalBridgeDateClick(p1);
                case "eventClick" -> dispCalBridgeEventClick(p1, p2);
                case "eventMove" -> dispCalBridgeEventMove(p1, p2, p3);
                case "eventContextMenu" -> dispCalBridgeContextMenu(p1);
                default -> {
                    // message inconnu
                }
            }
        });
    }

    private static String decodeBridgePart(String[] parts, int idx) {
        if (parts == null || idx < 0 || idx >= parts.length) {
            return "";
        }
        try {
            return URLDecoder.decode(parts[idx], StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return parts[idx];
        }
    }

    private String resolveFullCalendarCssUrl() {
        URL bundled = MainApp.class.getResource("/vendor/fullcalendar/index.global.min.css");
        if (bundled != null) {
            return bundled.toExternalForm();
        }
        URL webjar = MainApp.class.getResource(
                "/META-INF/resources/webjars/fullcalendar/" + FULLCALENDAR_VERSION + "/index.global.min.css");
        return webjar != null ? webjar.toExternalForm() : FULLCALENDAR_CSS_CDN;
    }

    private String resolveFullCalendarJsUrl() {
        URL bundled = MainApp.class.getResource("/vendor/fullcalendar/index.global.min.js");
        if (bundled != null) {
            return bundled.toExternalForm();
        }
        URL webjar = MainApp.class.getResource(
                "/META-INF/resources/webjars/fullcalendar/" + FULLCALENDAR_VERSION + "/index.global.min.js");
        return webjar != null ? webjar.toExternalForm() : FULLCALENDAR_JS_CDN;
    }

    private String buildDisponibiliteEventsJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Availability a : cachedAvailabilities) {
            if (a == null || a.getDebut() == null || a.getFin() == null) {
                continue;
            }
            if (!matchesSlotSearch(a)) {
                continue;
            }
            String title = a.getDebut().format(TIME_FMT) + " - " + a.getFin().format(TIME_FMT);
            String start = a.getDebut().format(DISPO_JS_ISO);
            String end = a.getFin().format(DISPO_JS_ISO);
            boolean linked = disponibiliteIdsWithBlockingRdv.contains(a.getId());
            String color = linked ? "#ef4444" : "#16a34a";
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"id\":\"").append(a.getId())
                    .append("\",\"title\":\"").append(jsonEscape(title))
                    .append("\",\"start\":\"").append(start)
                    .append("\",\"end\":\"").append(end)
                    .append("\",\"availabilityId\":").append(a.getId())
                    .append(",\"hasLinkedRdv\":").append(linked)
                    .append(",\"backgroundColor\":\"").append(color)
                    .append("\",\"borderColor\":\"").append(color)
                    .append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Appelé depuis {@link DispoCalendarJsBridge} (méthode publique exposée au JS). */
    void dispCalBridgeDateClick(String dateIso) {
        LocalDate d = parseJsDate(dateIso);
        if (d == null) {
            return;
        }
        selectedDispoDate = d;
        dispoMonth = YearMonth.from(d);
        updateMonthTitle();
        showAvailabilityEditor(null);
    }

    void dispCalBridgeEventClick(String availabilityId, String hasLinkedFlag) {
        Integer id = parseAvailabilityId(availabilityId);
        if (id == null) {
            return;
        }
        Availability a = findCachedAvailability(id);
        if (a != null) {
            boolean linked = "1".equals(hasLinkedFlag) || disponibiliteIdsWithBlockingRdv.contains(a.getId());
            if (linked) {
                showLinkedRdvDetails(a);
            } else {
                showAvailabilityQuickActions(a);
            }
        }
    }

    void dispCalBridgeEventMove(String availabilityId, String startIso, String endIso) {
        Integer id = parseAvailabilityId(availabilityId);
        if (id == null) {
            return;
        }
        Availability existing = findCachedAvailability(id);
        if (existing == null) {
            refreshDispoFromDb();
            existing = findCachedAvailability(id);
        }
        if (existing == null) {
            return;
        }
        if (disponibiliteIdsWithBlockingRdv.contains(existing.getId())) {
            alert(Alert.AlertType.WARNING, "Déplacement impossible",
                    "Ce créneau est lié à un rendez-vous confirmé.");
            rebuildDispoFullCalendar();
            return;
        }
        LocalDateTime newStart = parseJsDateTime(startIso);
        LocalDateTime newEnd = parseJsDateTime(endIso);
        if (newStart == null || newEnd == null) {
            rebuildDispoFullCalendar();
            return;
        }
        try {
            validateAvailabilityCandidate(existing, newStart, newEnd);
            existing.setDebut(newStart);
            existing.setFin(newEnd);
            availabilityService.update(existing);
            selectedDispoDate = newStart.toLocalDate();
            dispoMonth = YearMonth.from(selectedDispoDate);
            updateMonthTitle();
            refreshDispoFromDb();
            rebuildCalendarGrid();
            rebuildDispoFullCalendar();
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Disponibilité",
                    ex.getMessage() != null ? ex.getMessage() : "Mise à jour impossible.");
            rebuildDispoFullCalendar();
        }
    }

    void dispCalBridgeContextMenu(String availabilityId) {
        Integer id = parseAvailabilityId(availabilityId);
        if (id == null) {
            return;
        }
        Availability a = findCachedAvailability(id);
        if (a == null) {
            refreshDispoFromDb();
            a = findCachedAvailability(id);
        }
        if (a == null) {
            return;
        }
        if (disponibiliteIdsWithBlockingRdv.contains(a.getId())) {
            showLinkedRdvDetails(a);
            return;
        }
        showDispoActionsDialog(a, "Action rapide", "Créneau disponible");
    }

    private void showAvailabilityQuickActions(Availability a) {
        showDispoActionsDialog(a, "Disponibilité", "Créneau disponible");
    }

    /**
     * Boîte d’actions avec icônes Windows (Segoe MDL2 Assets) : plus fiable que les emojis dans les Alert.
     */
    private void showDispoActionsDialog(Availability a, String windowTitle, String headerText) {
        if (a == null) {
            return;
        }
        Dialog<Void> d = new Dialog<>();
        d.setTitle(windowTitle);
        d.initModality(Modality.APPLICATION_MODAL);
        Stage owner = resolveMedecinDashboardStage();
        if (owner != null) {
            d.initOwner(owner);
        }
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        d.getDialogPane().setMinWidth(420);
        Node hiddenCloseBtn = d.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (hiddenCloseBtn != null) {
            hiddenCloseBtn.setVisible(false);
            hiddenCloseBtn.setManaged(false);
        }
        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.setMinWidth(380);
        Label head = new Label(headerText);
        head.setWrapText(true);
        head.setStyle("-fx-font-weight:700;-fx-font-size:15px;-fx-text-fill:#1e3558;");
        Label msg = new Label("Modifier ou supprimer ce créneau.");
        msg.setWrapText(true);
        msg.setStyle("-fx-text-fill:#475569;");
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);
        Button btnEdit = buildDispoGlyphButton("Modifier", '\uE70F', "#16a34a");
        Button btnDel = buildDispoGlyphButton("Supprimer", '\uE74D', "#dc2626");
        btnEdit.setOnAction(ev -> {
            d.close();
            showAvailabilityEditor(a);
        });
        btnDel.setOnAction(ev -> {
            d.close();
            confirmDelete(a);
        });
        HBox foot = new HBox();
        foot.setAlignment(Pos.CENTER_RIGHT);
        Button btnClose = new Button("Fermer");
        btnClose.setOnAction(ev -> d.close());
        row.getChildren().addAll(btnEdit, btnDel);
        foot.getChildren().add(btnClose);
        root.getChildren().addAll(head, msg, row, foot);
        d.getDialogPane().setContent(root);
        d.showAndWait();
    }

    private static Button buildDispoGlyphButton(String caption, char mdl2Glyph, String accentColor) {
        Button b = new Button(caption);
        Label g = new Label(String.valueOf(mdl2Glyph));
        g.setStyle("-fx-font-family:'Segoe MDL2 Assets','Segoe UI Symbol';-fx-font-size:26px;-fx-text-fill:"
                + accentColor + ";");
        b.setGraphic(g);
        b.setContentDisplay(ContentDisplay.TOP);
        b.setPrefWidth(120);
        b.setMinHeight(86);
        b.setStyle("-fx-background-radius:12;-fx-border-radius:12;-fx-border-color:#e2e8f0;-fx-border-width:1;"
                + "-fx-background-color:#ffffff;-fx-cursor:hand;");
        return b;
    }

    private Stage resolveMedecinDashboardStage() {
        if (medDispoScroll != null && medDispoScroll.getScene() != null
                && medDispoScroll.getScene().getWindow() instanceof Stage st1) {
            return st1;
        }
        if (searchField != null && searchField.getScene() != null
                && searchField.getScene().getWindow() instanceof Stage st2) {
            return st2;
        }
        return null;
    }

    private Availability findCachedAvailability(int id) {
        for (Availability a : cachedAvailabilities) {
            if (a != null && a.getId() == id) {
                return a;
            }
        }
        return null;
    }

    private static Integer parseAvailabilityId(String raw) {
        try {
            return raw == null ? null : Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDate parseJsDate(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDateTime parseJsDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        try {
            return LocalDateTime.parse(v);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(v).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(v).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    private VBox buildDayCell(LocalDate date, int dayInMonth) {
        VBox root = new VBox(6);
        root.getStyleClass().add("med-cal-cell");
        root.setFillWidth(true);
        root.setMaxHeight(Double.MAX_VALUE);
        GridPane.setValignment(root, VPos.TOP);
        root.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) {
                selectedDispoDate = date;
                rebuildCalendarGrid();
            }
        });

        Label dayLab = new Label(String.valueOf(dayInMonth));
        dayLab.getStyleClass().add("med-cal-day-num");
        if (date.equals(selectedDispoDate)) {
            dayLab.getStyleClass().remove("med-cal-day-num");
            dayLab.getStyleClass().add("med-cal-day-selected");
        }
        dayLab.setMaxWidth(Double.MAX_VALUE);
        dayLab.setAlignment(Pos.CENTER_LEFT);
        dayLab.setMinHeight(Region.USE_PREF_SIZE);
        dayLab.setMaxHeight(Region.USE_PREF_SIZE);
        root.getChildren().add(dayLab);

        List<Availability> daySlots = cachedAvailabilities.stream()
                .filter(a -> a.getDebut() != null
                        && !a.getDebut().toLocalDate().isBefore(date)
                        && !a.getDebut().toLocalDate().isAfter(date))
                .sorted(Comparator.comparing(Availability::getDebut))
                .filter(this::matchesSlotSearch)
                .collect(Collectors.toList());

        VBox slotsInner = new VBox(6);
        slotsInner.setFillWidth(true);
        for (Availability a : daySlots) {
            slotsInner.getChildren().add(buildSlotCard(a));
        }
        ScrollPane slotScroll = new ScrollPane(slotsInner);
        slotScroll.setFitToWidth(true);
        slotScroll.setMinHeight(0);
        slotScroll.setPrefHeight(80);
        slotScroll.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(slotScroll, Priority.ALWAYS);
        slotScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        slotScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        slotScroll.getStyleClass().add("med-cal-slots-scroll");
        slotScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        slotsInner.setStyle("-fx-background-color: transparent;");
        root.getChildren().add(slotScroll);
        return root;
    }

    private boolean matchesSlotSearch(Availability a) {
        if (dispoSlotSearchField == null) {
            return true;
        }
        String q = dispoSlotSearchField.getText();
        if (q == null || q.isBlank()) {
            return true;
        }
        String raw = q.trim();
        LocalDate parsedDate = tryParseDisponibiliteSearchAsDate(raw);
        if (parsedDate != null && a.getDebut() != null) {
            return a.getDebut().toLocalDate().equals(parsedDate);
        }
        String needle = raw.toLowerCase(Locale.FRENCH);
        String hay = buildDisponibiliteSlotSearchHaystack(a);
        return hay.contains(needle);
    }

    /**
     * Si la saisie ressemble à une date complète, retourne le {@link LocalDate} correspondant ; sinon {@code null}
     * (recherche libre : jour de la semaine, mois, heures…).
     */
    private static LocalDate tryParseDisponibiliteSearchAsDate(String raw) {
        String s = raw.trim();
        if (s.length() < 6) {
            return null;
        }
        for (DateTimeFormatter f : DISPO_SEARCH_DATE_PATTERNS) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // essai suivant
            }
        }
        return null;
    }

    private static String buildDisponibiliteSlotSearchHaystack(Availability a) {
        if (a.getDebut() == null) {
            return "";
        }
        LocalDateTime d = a.getDebut();
        LocalDate date = d.toLocalDate();
        StringBuilder sb = new StringBuilder();
        sb.append(formatSlotSummary(a).toLowerCase(Locale.ROOT)).append(' ');
        sb.append(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)).toLowerCase(Locale.ROOT)).append(' ');
        sb.append(date.format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.FRENCH)).toLowerCase(Locale.ROOT)).append(' ');
        sb.append(date.format(DateTimeFormatter.ISO_LOCAL_DATE).toLowerCase(Locale.ROOT)).append(' ');
        String dayFr = d.format(DISPO_SEARCH_DAY_FR);
        sb.append(dayFr.toLowerCase(Locale.FRENCH)).append(' ');
        sb.append(dayFr.toLowerCase(Locale.FRENCH).replace(".", "")).append(' ');
        sb.append(d.getDayOfWeek().name().toLowerCase(Locale.ROOT)).append(' ');
        sb.append(d.format(DISPO_SEARCH_DATE_LONG_FR).toLowerCase(Locale.FRENCH)).append(' ');
        int m = date.getMonthValue();
        sb.append(m).append(' ');
        sb.append(date.getMonth().name().toLowerCase(Locale.ROOT)).append(' ');
        return sb.toString();
    }

    private static String formatSlotSummary(Availability a) {
        long min = ChronoUnit.MINUTES.between(a.getDebut(), a.getFin());
        return a.getDebut().format(TIME_FMT) + " " + a.getFin().format(TIME_FMT) + " " + min + " min";
    }

    private VBox buildSlotCard(Availability a) {
        VBox card = new VBox(4);
        card.getStyleClass().add("med-slot-card");
        boolean hasLinkedRdv = disponibiliteIdsWithBlockingRdv.contains(a.getId());
        card.setOnMouseClicked(ev -> {
            ev.consume();
            if (ev.getButton() != MouseButton.PRIMARY || !ev.isStillSincePress()) {
                return;
            }
            if (hasLinkedRdv) {
                showLinkedRdvDetails(a);
            }
        });

        String timeText = a.getDebut().format(TIME_FMT) + " – " + a.getFin().format(TIME_FMT);
        Label time = new Label(timeText);
        time.getStyleClass().add("med-slot-time");

        long minutes = Math.max(0, ChronoUnit.MINUTES.between(a.getDebut(), a.getFin()));
        Label dur = new Label(minutes + " min");
        dur.getStyleClass().add("med-slot-dur");

        HBox actions = new HBox(4);
        actions.getStyleClass().add("med-slot-actions");
        Button edit = new Button("✎");
        edit.getStyleClass().add("med-slot-icon-btn");
        edit.setOnAction(ev -> {
            ev.consume();
            showAvailabilityEditor(a);
        });
        Button del = new Button("🗑");
        del.getStyleClass().addAll("med-slot-icon-btn", "med-slot-del");
        boolean blockDelete = hasLinkedRdv;
        del.setDisable(blockDelete);
        if (blockDelete) {
            del.setTooltip(new Tooltip(
                    "Suppression impossible : un rendez-vous confirmé est lié à ce créneau."));
        }
        del.setOnAction(ev -> {
            ev.consume();
            confirmDelete(a);
        });
        actions.getChildren().addAll(edit, del);

        card.getChildren().addAll(time, dur, actions);
        return card;
    }

    private void showLinkedRdvDetails(Availability availability) {
        if (availability == null || availability.getId() <= 0) {
            return;
        }
        Optional<Appointment> linkedOpt = findLinkedAppointmentForAvailability(availability);
        if (linkedOpt.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Rendez-vous",
                    "Aucun rendez-vous trouvé pour ce créneau.\n"
                            + "Vérifiez que le rendez-vous est bien lié (disponibilite_id) ou qu’il tombe dans l’horaire du créneau.");
            return;
        }
        Appointment linked = linkedOpt.get();
        String patient = resolvePatientLabel(linked.getPatientId());
        Optional<User> patientUser = resolvePatientUser(linked.getPatientId());
        String tel = dashIfBlank(patientUser.map(User::getTelephone).orElse(null));
        String email = dashIfBlank(patientUser.map(User::getEmail).orElse(null));
        String adresse = dashIfBlank(patientUser.map(User::getAdresse).orElse(null));
        String when = formatRdvDateTimeRange(linked, availability);
        String status = statusShortLabelFr(linked.getStatus());
        String motif = dashIfBlank(linked.getMotif());
        String notesValue = extractPatientFreeNoteFromRdvNotes(linked.getNotes());
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Rendez-vous lié");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.getDialogPane().getStyleClass().add("med-slot-rdv-detail-pane");
        dialog.setResizable(true);
        Node hiddenCloseBtn = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (hiddenCloseBtn != null) {
            hiddenCloseBtn.setVisible(false);
            hiddenCloseBtn.setManaged(false);
        }

        URL medCss = getClass().getResource("/styles/medecin-dashboard.css");
        if (medCss != null) {
            dialog.getDialogPane().getStylesheets().add(medCss.toExternalForm());
        }
        URL appCss = getClass().getResource("/styles/app.css");
        if (appCss != null) {
            dialog.getDialogPane().getStylesheets().add(appCss.toExternalForm());
        }

        Window owner = resolveMedecinDashboardWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.WINDOW_MODAL);

        Label title = new Label("Détails du rendez-vous");
        title.getStyleClass().add("med-slot-rdv-detail-title");
        Label sub = new Label("Créneau " + availability.getDebut().format(TIME_FMT) + " – " + availability.getFin().format(TIME_FMT));
        sub.getStyleClass().add("med-slot-rdv-detail-sub");

        VBox header = new VBox(4, title, sub);
        header.getStyleClass().add("med-slot-rdv-detail-header");

        VBox grid = new VBox(10);
        grid.getStyleClass().add("med-slot-rdv-detail-grid");
        grid.getChildren().addAll(
                buildSlotRdvInfoItem("Patient", patient),
                buildSlotRdvInfoItem("Statut", status),
                buildSlotRdvInfoItem("Date et heure", when),
                buildSlotRdvInfoItem("Motif", motif),
                buildSlotRdvInfoItem("Téléphone", tel),
                buildSlotRdvInfoItem("Email", email),
                buildSlotRdvInfoItem("Adresse", adresse));

        Label notesCap = new Label("Notes");
        notesCap.getStyleClass().add("med-slot-rdv-detail-caption");
        Label notesLbl = new Label(notesValue);
        notesLbl.setWrapText(true);
        notesLbl.getStyleClass().add("med-slot-rdv-detail-notes");

        VBox notesBox = new VBox(6, notesCap, notesLbl);
        notesBox.getStyleClass().add("med-slot-rdv-detail-notes-box");

        VBox body = new VBox(12, grid, new Separator(), notesBox);
        body.setPadding(new Insets(16, 18, 16, 18));
        body.getStyleClass().add("med-slot-rdv-detail-body");

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefViewportHeight(430);
        scroll.getStyleClass().add("med-slot-rdv-detail-scroll");

        Button btnEditRdv = new Button("Modifier le rendez-vous");
        btnEditRdv.getStyleClass().add("med-btn-primary");
        btnEditRdv.setOnAction(ev -> {
            dialog.close();
            openRdvEditDialog(linked);
        });
        Button close = new Button("Fermer");
        close.getStyleClass().add("med-slot-rdv-detail-close-btn");
        close.setOnAction(ev -> dialog.close());
        HBox actions = new HBox(12, btnEditRdv, close);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12, 16, 14, 16));
        actions.getStyleClass().add("med-slot-rdv-detail-actions");

        VBox root = new VBox(header, scroll, actions);
        root.getStyleClass().add("med-slot-rdv-detail-root");

        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private static VBox buildSlotRdvInfoItem(String label, String value) {
        Label cap = new Label(label);
        cap.getStyleClass().add("med-slot-rdv-detail-caption");
        Label val = new Label(dashIfBlank(value));
        val.setWrapText(true);
        val.getStyleClass().add("med-slot-rdv-detail-value");
        return new VBox(2, cap, val);
    }

    private static String extractPatientFreeNoteFromRdvNotes(String fullNotes) {
        String patientBloc = RdvNotesFormat.extractPatientBloc(fullNotes != null ? fullNotes : "");
        if (patientBloc.isBlank()) {
            return "—";
        }
        String[] lines = patientBloc.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] != null ? lines[i].trim() : "";
            if (!line.regionMatches(true, 0, "Notes", 0, "Notes".length())) {
                continue;
            }
            int colon = line.indexOf(':');
            String first = colon >= 0 ? line.substring(colon + 1).trim() : "";
            StringBuilder out = new StringBuilder(first);
            for (int j = i + 1; j < lines.length; j++) {
                String next = lines[j] != null ? lines[j].trim() : "";
                if (next.isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(next);
            }
            String note = out.toString().trim();
            return note.isEmpty() ? "—" : note;
        }
        return "—";
    }

    private Optional<Appointment> findLinkedNonCancelledAppointment(int disponibiliteId) {
        if (disponibiliteId <= 0 || currentDoctorId <= 0) {
            return Optional.empty();
        }
        try {
            return appointmentService.findByMedecin(currentDoctorId).stream()
                    .filter(a -> a.getDisponibiliteId() == disponibiliteId)
                    .filter(a -> a.getStatus() != AppointmentStatus.ANNULE)
                    .sorted(Comparator
                            .comparing(Appointment::getDateHeure, Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed()
                            .thenComparing(Appointment::getId, Comparator.reverseOrder()))
                    .findFirst();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Rendez-vous",
                    "Impossible de charger les détails du rendez-vous : " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * RDV lié au créneau : d’abord par {@code disponibilite_id}, sinon par chevauchement d’horaire (données héritées).
     */
    private Optional<Appointment> findLinkedAppointmentForAvailability(Availability availability) {
        if (availability == null || availability.getId() <= 0) {
            return Optional.empty();
        }
        Optional<Appointment> byId = findLinkedNonCancelledAppointment(availability.getId());
        if (byId.isPresent()) {
            return byId;
        }
        if (availability.getDebut() == null || availability.getFin() == null || currentDoctorId <= 0) {
            return Optional.empty();
        }
        try {
            return appointmentService.findByMedecin(currentDoctorId).stream()
                    .filter(a -> a.getStatus() != AppointmentStatus.ANNULE)
                    .filter(a -> appointmentStartsInsideAvailability(a, availability))
                    .max(Comparator
                            .comparing(Appointment::getDateHeure, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Appointment::getId, Comparator.reverseOrder()));
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Rendez-vous",
                    "Impossible de charger les rendez-vous : " + e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean appointmentStartsInsideAvailability(Appointment appt, Availability slot) {
        if (appt.getDateHeure() == null || slot.getDebut() == null || slot.getFin() == null) {
            return false;
        }
        LocalDateTime t = appt.getDateHeure();
        return !t.isBefore(slot.getDebut()) && t.isBefore(slot.getFin());
    }

    private void confirmDelete(Availability a) {
        try {
            if (appointmentService.disponibiliteHasBlockingRdv(a.getId())) {
                alert(Alert.AlertType.WARNING, "Suppression impossible",
                        "Ce créneau est lié à un rendez-vous confirmé. Annulez ou supprimez le rendez-vous avant de supprimer la disponibilité.");
                return;
            }
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Disponibilité", ex.getMessage());
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer ce créneau ?");
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        try {
            availabilityService.delete(a.getId());
            refreshDispoFromDb();
            rebuildCalendarGrid();
            rebuildDispoFullCalendar();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Suppression", e.getMessage());
        }
    }

    private void showAvailabilityEditor(Availability existing) {
        try {
            URL url = MainApp.class.getResource("/fxml/medecin-disponibilite-dialog.fxml");
            if (url == null) {
                alert(Alert.AlertType.ERROR, "Erreur", "Formulaire introuvable.");
                return;
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            MedecinDisponibiliteDialogController ctrl = loader.getController();
            Stage owner = null;
            if (medDispoScroll != null && medDispoScroll.getScene() != null
                    && medDispoScroll.getScene().getWindow() instanceof Stage) {
                owner = (Stage) medDispoScroll.getScene().getWindow();
            } else if (searchField != null && searchField.getScene() != null
                    && searchField.getScene().getWindow() instanceof Stage) {
                owner = (Stage) searchField.getScene().getWindow();
            }
            Stage st = new Stage();
            if (owner != null) {
                st.initOwner(owner);
            }
            st.initModality(Modality.APPLICATION_MODAL);
            st.setTitle(existing == null ? "Nouvelle disponibilité" : "Modifier la disponibilité");
            Scene dlgScene = new Scene(root);
            st.setScene(dlgScene);
            MainApp.applyThemeToScene(dlgScene);
            ctrl.setStage(st);
            ctrl.prepare(existing, selectedDispoDate, (debut, fin) -> {
                validateAvailabilityCandidate(existing, debut, fin);
                if (existing == null) {
                    Availability na = new Availability();
                    na.setMedecinId(currentDoctorId);
                    na.setDebut(debut);
                    na.setFin(fin);
                    availabilityService.add(na);
                } else {
                    existing.setMedecinId(currentDoctorId);
                    existing.setDebut(debut);
                    existing.setFin(fin);
                    availabilityService.update(existing);
                }
                selectedDispoDate = debut.toLocalDate();
                dispoMonth = YearMonth.from(selectedDispoDate);
                updateMonthTitle();
            });
            st.showAndWait();
            refreshDispoFromDb();
            rebuildCalendarGrid();
            rebuildDispoFullCalendar();
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void validateAvailabilityCandidate(Availability existing, LocalDateTime debut, LocalDateTime fin) {
        LocalDateTime now = LocalDateTime.now();
        if (debut == null || fin == null) {
            throw new IllegalArgumentException("Créneau invalide.");
        }
        if (!fin.isAfter(debut)) {
            throw new IllegalArgumentException("L'heure de fin doit être après l'heure de début.");
        }
        if (!debut.isAfter(now)) {
            throw new IllegalArgumentException("Impossible d'ajouter une disponibilité avec une date/heure déjà dépassée.");
        }
        int editingId = existing != null ? existing.getId() : -1;
        boolean duplicate = cachedAvailabilities.stream()
                .filter(a -> a != null && a.getId() != editingId)
                .anyMatch(a -> debut.equals(a.getDebut()) && fin.equals(a.getFin()));
        if (duplicate) {
            throw new IllegalArgumentException("Ce créneau existe déjà pour cette disponibilité.");
        }
    }

    @FXML
    private void onLogout() {
        AppState.clear();
        try {
            MainApp.showLogin();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    private void onBackToSite() {
        setMainView(MainView.HOME);
    }

    @FXML
    private void onNavDisponibilites() {
        setMainView(MainView.DISPO);
    }

    private void setupNotesTable() {
        if (notesTableView == null) {
            return;
        }
        Label ph = new Label("Aucune note.");
        ph.getStyleClass().add("med-notes-placeholder");
        notesTableView.setPlaceholder(ph);
        notesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<NoteRow, String> colPatient = new TableColumn<>("Patient");
        colPatient.setMinWidth(100);
        colPatient.setCellValueFactory(cd -> {
            NoteRow r = cd.getValue();
            return new SimpleStringProperty(r != null && r.getPatient() != null ? r.getPatient() : "");
        });

        TableColumn<NoteRow, String> colContenu = new TableColumn<>("Contenu");
        colContenu.setMinWidth(120);
        colContenu.setCellValueFactory(cd -> {
            NoteRow r = cd.getValue();
            String raw = r != null && r.getContenu() != null ? r.getContenu() : "";
            return new SimpleStringProperty(NoteHtmlUtil.toPlainPreview(raw, 400));
        });
        colContenu.setCellFactory(col -> new TableCell<>() {
            private final Label lab = new Label();
            private boolean widthBound;

            {
                lab.setWrapText(true);
                lab.setMaxHeight(120);
                lab.getStyleClass().add("med-notes-contenu-cell");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!widthBound) {
                    lab.maxWidthProperty().bind(col.widthProperty().subtract(20));
                    widthBound = true;
                }
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lab.setText(item);
                    setGraphic(lab);
                }
            }
        });

        TableColumn<NoteRow, String> colDate = new TableColumn<>("Date");
        colDate.setMinWidth(88);
        colDate.setMaxWidth(200);
        colDate.setCellValueFactory(cd -> {
            NoteRow r = cd.getValue();
            return new SimpleStringProperty(r != null && r.getDateLabel() != null ? r.getDateLabel() : "");
        });

        TableColumn<NoteRow, Void> colActions = new TableColumn<>("Actions");
        colActions.setMinWidth(200);
        colActions.setMaxWidth(280);
        colActions.setResizable(false);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnVoir = new Button("Voir");
            private final Button btnMod = new Button("Modifier");
            private final Button btnDel = new Button("Supprimer");
            private final HBox box = new HBox(6);

            {
                btnVoir.getStyleClass().add("med-notes-mini-btn");
                btnMod.getStyleClass().add("med-notes-mini-btn");
                btnDel.getStyleClass().addAll("med-notes-mini-btn", "med-notes-mini-btn-danger");
                box.setAlignment(Pos.CENTER_LEFT);
                btnVoir.setOnAction(ev -> {
                    NoteRow r = getTableRow() != null ? getTableRow().getItem() : null;
                    if (r != null) {
                        showNoteDetail(r);
                    }
                });
                btnMod.setOnAction(ev -> {
                    NoteRow r = getTableRow() != null ? getTableRow().getItem() : null;
                    if (r != null) {
                        onEditNoteRow(r);
                    }
                });
                btnDel.setOnAction(ev -> {
                    NoteRow r = getTableRow() != null ? getTableRow().getItem() : null;
                    if (r != null) {
                        onDeleteNoteRow(r);
                    }
                });
                box.getChildren().addAll(btnVoir, btnMod, btnDel);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                }
            }
        });

        notesTableView.getColumns().setAll(colPatient, colContenu, colDate, colActions);
        notesFilteredList = new FilteredList<>(notesMasterList, p -> true);
        notesTableView.setItems(notesFilteredList);
        if (notesTableSearchPatientField != null) {
            notesTableSearchPatientField.textProperty().addListener((o, a, b) -> updateNotesTableFilter());
        }
        if (notesTableDateFilterPicker != null) {
            notesTableDateFilterPicker.valueProperty().addListener((o, a, b) -> updateNotesTableFilter());
        }
    }

    private void updateNotesTableFilter() {
        if (notesFilteredList == null) {
            return;
        }
        String q = "";
        if (notesTableSearchPatientField != null && notesTableSearchPatientField.getText() != null) {
            q = notesTableSearchPatientField.getText().trim().toLowerCase(Locale.ROOT);
        }
        LocalDate dateOnly = notesTableDateFilterPicker != null ? notesTableDateFilterPicker.getValue() : null;
        final String qf = q;
        final LocalDate df = dateOnly;
        notesFilteredList.setPredicate(row -> {
            if (row == null) {
                return false;
            }
            if (!qf.isEmpty()) {
                String p = row.getPatient() != null ? row.getPatient().toLowerCase(Locale.ROOT) : "";
                if (!p.contains(qf)) {
                    return false;
                }
            }
            if (df != null) {
                LocalDateTime st = row.getSortTime();
                if (st == null || !st.toLocalDate().equals(df)) {
                    return false;
                }
            }
            return true;
        });
    }

    @FXML
    private void onResetNotesTableFilters() {
        if (notesTableSearchPatientField != null) {
            notesTableSearchPatientField.clear();
        }
        if (notesTableDateFilterPicker != null) {
            notesTableDateFilterPicker.setValue(null);
        }
        updateNotesTableFilter();
    }

    private void onEditNoteRow(NoteRow row) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Modifier la note");
        d.setHeaderText(row.getPatient() + " — " + row.getDateLabel());
        String currentFullRdvNotes = "";
        if (row.getKind() == NoteKind.RENDEZ_VOUS) {
            currentFullRdvNotes = row.getRawRdvNotes();
            if (currentFullRdvNotes == null || currentFullRdvNotes.isBlank()) {
                try {
                    Optional<Appointment> opt = appointmentService.findById(row.getEntityId());
                    currentFullRdvNotes = opt.map(Appointment::getNotes).orElse("");
                } catch (SQLException ex) {
                    currentFullRdvNotes = "";
                }
            }
        }
        String areaSeed = row.getKind() == NoteKind.RENDEZ_VOUS
                ? RdvNotesFormat.extractMedecinNote(currentFullRdvNotes)
                : (row.getContenu() != null ? row.getContenu() : "");
        HTMLEditor editor = new HTMLEditor();
        editor.setHtmlText(NoteHtmlUtil.wrapForEditor(areaSeed));
        editor.setPrefHeight(340);
        d.getDialogPane().setContent(editor);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Window w = notesTableView != null && notesTableView.getScene() != null
                ? notesTableView.getScene().getWindow()
                : MainApp.getPrimaryStage();
        if (w != null) {
            d.initOwner(w);
        }
        d.initModality(Modality.WINDOW_MODAL);
        Optional<ButtonType> res = d.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) {
            return;
        }
        persistNoteRowHtmlEdit(row, editor.getHtmlText());
    }

    /**
     * Enregistre le HTML édité pour une ligne du tableau notes (libre ou note médecin d’un RDV).
     *
     * @return {@code true} si la persistance a réussi
     */
    private boolean persistNoteRowHtmlEdit(NoteRow row, String html) {
        if (row.getKind() == NoteKind.NOTE_LIBRE && NoteHtmlUtil.isEffectivelyEmpty(html)) {
            alert(Alert.AlertType.WARNING, "Note", "Le contenu ne peut pas être vide.");
            return false;
        }
        String t = html != null ? html.trim() : "";
        try {
            if (row.getKind() == NoteKind.NOTE_LIBRE) {
                if (!MedecinPatientNoteService.tableExists()) {
                    alert(Alert.AlertType.ERROR, "Note", "Table des notes indisponible.");
                    return false;
                }
                medecinPatientNoteService.update(row.getEntityId(), currentDoctorId, t);
            } else {
                Optional<Appointment> opt = appointmentService.findById(row.getEntityId());
                if (opt.isEmpty()) {
                    throw new SQLException("Rendez-vous introuvable.");
                }
                Appointment a = opt.get();
                if (a.getMedecinId() != currentDoctorId) {
                    throw new SQLException("Vous ne pouvez pas modifier ce rendez-vous.");
                }
                String currentFull = a.getNotes() != null ? a.getNotes() : "";
                String medic = NoteHtmlUtil.isEffectivelyEmpty(html) ? "" : t;
                String merged = RdvNotesFormat.mergePatientBlocWithMedecinNote(currentFull, medic);
                a.setNotes(merged);
                appointmentService.update(a);
            }
            refreshNotesFromDb();
            return true;
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Note", ex.getMessage() != null ? ex.getMessage() : "Enregistrement impossible.");
            return false;
        }
    }

    private void onDeleteNoteRow(NoteRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer la note");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer cette note pour « " + row.getPatient() + " » ?");
        Window w = notesTableView != null && notesTableView.getScene() != null
                ? notesTableView.getScene().getWindow()
                : MainApp.getPrimaryStage();
        if (w != null) {
            confirm.initOwner(w);
        }
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        try {
            if (row.getKind() == NoteKind.NOTE_LIBRE) {
                if (!MedecinPatientNoteService.tableExists()) {
                    alert(Alert.AlertType.ERROR, "Note", "Table des notes indisponible.");
                    return;
                }
                medecinPatientNoteService.delete(row.getEntityId(), currentDoctorId);
            } else {
                Optional<Appointment> opt = appointmentService.findById(row.getEntityId());
                if (opt.isEmpty()) {
                    throw new SQLException("Rendez-vous introuvable.");
                }
                Appointment a = opt.get();
                if (a.getMedecinId() != currentDoctorId) {
                    throw new SQLException("Vous ne pouvez pas modifier ce rendez-vous.");
                }
                /* Suppression complète : retirer toute la ligne du tableau (fiche patient + note médecin). */
                a.setNotes("");
                appointmentService.update(a);
            }
            refreshNotesFromDb();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Note", ex.getMessage() != null ? ex.getMessage() : "Suppression impossible.");
        }
    }

    @FXML
    private void onClearAllNotes() {
        if (notesTableView == null || currentDoctorId <= 0) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Vider toutes les notes");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Supprimer définitivement toutes les notes affichées dans le tableau ?\n\n"
                        + "• Notes libres (table « note »)\n"
                        + "• Textes liés aux rendez-vous (champ notes)\n\n"
                        + "Cette action est irréversible.");
        Window w = notesTableView.getScene() != null ? notesTableView.getScene().getWindow() : MainApp.getPrimaryStage();
        if (w != null) {
            confirm.initOwner(w);
        }
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        try {
            int nLibre = 0;
            if (MedecinPatientNoteService.tableExists()) {
                nLibre = medecinPatientNoteService.deleteAllForMedecin(currentDoctorId);
            }
            int nRdv = appointmentService.clearAllNotesForMedecin(currentDoctorId);
            refreshNotesFromDb();
            alert(Alert.AlertType.INFORMATION, "Notes",
                    "Liste vidée : " + nLibre + " note(s) libre(s) et " + nRdv + " rendez-vous mis à jour.");
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Notes", ex.getMessage() != null ? ex.getMessage() : "Opération impossible.");
        }
    }

    private void refreshNotesFromDb() {
        if (notesTableView == null) {
            return;
        }
        try {
            List<Appointment> appts = appointmentService.findByMedecin(currentDoctorId);
            Set<Integer> patientIds = new HashSet<>();
            for (Appointment a : appts) {
                if (a == null || a.getPatientId() <= 0) {
                    continue;
                }
                AppointmentStatus st = a.getStatus();
                boolean accepted = st == AppointmentStatus.PLANIFIE || st == AppointmentStatus.TERMINE;
                if (accepted) {
                    patientIds.add(a.getPatientId());
                }
            }
            if (notesPatientCombo != null) {
                int keepId = -1;
                PatientNoteChoice cur = notesPatientCombo.getValue();
                if (cur != null) {
                    keepId = cur.getPatientId();
                }
                List<PatientNoteChoice> choices = patientIds.stream()
                        .map(pid -> new PatientNoteChoice(pid, resolvePatientLabel(pid)))
                        .sorted(Comparator.comparing(PatientNoteChoice::getLabel, String.CASE_INSENSITIVE_ORDER))
                        .collect(Collectors.toList());
                notesPatientCombo.setItems(FXCollections.observableArrayList(choices));
                if (keepId >= 0) {
                    for (PatientNoteChoice c : choices) {
                        if (c.getPatientId() == keepId) {
                            notesPatientCombo.setValue(c);
                            break;
                        }
                    }
                }
            }
            if (medNotesNewHint != null) {
                if (patientIds.isEmpty()) {
                    medNotesNewHint.setText(
                            "Aucun patient accepté pour l'instant. Les patients apparaissent après un rendez-vous accepté.");
                } else {
                    medNotesNewHint.setText(
                            "Rédigez ici une note pour le patient choisi : elle est enregistrée et apparaît dans le tableau "
                                    + "avec la date. Utilisez « Voir » pour ouvrir le détail, « Modifier » pour la mettre à jour.");
                }
            }
            List<NoteRow> rows = new ArrayList<>();
            for (Appointment a : appts) {
                String n = a.getNotes();
                if (n == null || n.isBlank()) {
                    continue;
                }
                String patientLabel = resolvePatientLabel(a.getPatientId());
                LocalDateTime sort = a.getDateHeure() != null ? a.getDateHeure() : LocalDateTime.MIN;
                String dateLabel = a.getDateHeure() != null ? a.getDateHeure().format(NOTE_DATE) : "—";
                String raw = n.strip();
                String contenuMedecin = RdvNotesFormat.extractMedecinNote(raw);
                if (contenuMedecin == null || contenuMedecin.isBlank()) {
                    continue;
                }
                rows.add(new NoteRow(
                        NoteKind.RENDEZ_VOUS,
                        a.getId(),
                        a.getPatientId(),
                        patientLabel,
                        contenuMedecin,
                        dateLabel,
                        sort,
                        raw));
            }
            if (MedecinPatientNoteService.tableExists()) {
                for (MedecinPatientNote mn : medecinPatientNoteService.findByMedecin(currentDoctorId)) {
                    String c = mn.getContenu();
                    if (c == null || c.isBlank()) {
                        continue;
                    }
                    LocalDateTime sort = mn.getCreatedAt() != null ? mn.getCreatedAt() : LocalDateTime.MIN;
                    String dateLabel = mn.getCreatedAt() != null ? mn.getCreatedAt().format(NOTE_DATE) : "—";
                    rows.add(new NoteRow(
                            NoteKind.NOTE_LIBRE,
                            mn.getId(),
                            mn.getPatientId(),
                            resolvePatientLabel(mn.getPatientId()),
                            c.strip(),
                            dateLabel,
                            sort,
                            null));
                }
            }
            rows.sort(Comparator.comparing(NoteRow::getSortTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            notesMasterList.setAll(rows);
            updateNotesTableFilter();
            if (medNotesTotalLabel != null) {
                medNotesTotalLabel.setText(String.valueOf(rows.size()));
            }
            if (statNotesLabel != null) {
                statNotesLabel.setText(String.valueOf(rows.size()));
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Notes", "Impossible de charger les données : " + e.getMessage());
            notesMasterList.clear();
            updateNotesTableFilter();
            if (medNotesTotalLabel != null) {
                medNotesTotalLabel.setText("0");
            }
        }
    }

    @FXML
    private void onSavePatientNote() {
        if (notesPatientCombo == null || notesHtmlEditor == null) {
            return;
        }
        PatientNoteChoice choice = notesPatientCombo.getValue();
        String html = notesHtmlEditor.getHtmlText();
        if (choice == null) {
            alert(Alert.AlertType.WARNING, "Note", "Sélectionnez un patient dans la liste.");
            return;
        }
        if (NoteHtmlUtil.isEffectivelyEmpty(html)) {
            alert(Alert.AlertType.WARNING, "Note", "Saisissez le contenu de la note (texte mis en forme).");
            return;
        }
        try {
            if (!MedecinPatientNoteService.tableExists()) {
                alert(Alert.AlertType.ERROR, "Note",
                        "La base de données n'inclut pas encore la table « note ». Relancez l'application après migration.");
                return;
            }
            medecinPatientNoteService.add(currentDoctorId, choice.getPatientId(), html.trim());
            notesHtmlEditor.setHtmlText(NoteHtmlUtil.emptyEditorHtml());
            refreshNotesFromDb();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Note", ex.getMessage() != null ? ex.getMessage() : "Enregistrement impossible.");
        }
    }

    @FXML
    private void onExportNotesPdf() {
        if (notesTableView == null) {
            return;
        }
        ObservableList<NoteRow> items = notesTableView.getItems();
        if (items == null || items.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Export", "Aucune note à exporter.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter les notes");
        fc.setInitialFileName("notes-patients-auticare.txt");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier texte", "*.txt"));
        Window w = notesTableView.getScene() != null ? notesTableView.getScene().getWindow() : null;
        if (w == null) {
            w = MainApp.getPrimaryStage();
        }
        if (w == null) {
            return;
        }
        File f = fc.showSaveDialog(w);
        if (f == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder("AutiCare — Notes patients\n");
            sb.append("Médecin ID ").append(currentDoctorId).append("\n\n");
            for (NoteRow r : items) {
                sb.append("— ").append(r.getPatient()).append(" | ").append(r.getDateLabel()).append("\n");
                sb.append(r.getContenu()).append("\n\n");
            }
            Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8);
            alert(Alert.AlertType.INFORMATION, "Export",
                    "Fichier enregistré.\nL'export PDF pourra être ajouté dans une prochaine version.");
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Export", ex.getMessage());
        }
    }

    private void showNoteDetail(NoteRow row) {
        final String detailTitle = "Détail de la note";
        final String metaLine = "Patient : " + row.getPatient() + " — " + row.getDateLabel();

        String rawRdv = "";
        if (row.getKind() == NoteKind.RENDEZ_VOUS) {
            rawRdv = row.getRawRdvNotes();
            if (rawRdv == null || rawRdv.isBlank()) {
                try {
                    Optional<Appointment> opt = appointmentService.findById(row.getEntityId());
                    rawRdv = opt.map(Appointment::getNotes).orElse("");
                } catch (SQLException ex) {
                    rawRdv = "";
                }
            }
        }

        final boolean useWebView;
        final String htmlDocument;
        final String plainBody;

        if (row.getKind() == NoteKind.RENDEZ_VOUS) {
            String patientBloc = RdvNotesFormat.extractPatientBloc(rawRdv);
            plainBody = patientBloc.isBlank() ? "—" : patientBloc;
            if (NoteHtmlUtil.looksLikeHtml(patientBloc)) {
                useWebView = true;
                htmlDocument = patientBloc.toLowerCase().contains("<html")
                        ? patientBloc
                        : "<html><body>" + patientBloc + "</body></html>";
            } else {
                useWebView = false;
                htmlDocument = null;
            }
        } else {
            String c = row.getContenu() != null ? row.getContenu() : "";
            if (NoteHtmlUtil.looksLikeHtml(c)) {
                useWebView = true;
                htmlDocument = c.toLowerCase().contains("<html") ? c : "<html><body>" + c + "</body></html>";
            } else {
                useWebView = false;
                htmlDocument = null;
            }
            plainBody = NoteHtmlUtil.looksLikeHtml(c) ? NoteHtmlUtil.stripToPlain(c) : c;
        }

        Dialog<Void> d = new Dialog<>();
        d.setTitle(detailTitle);
        d.getDialogPane().getButtonTypes().clear();
        d.getDialogPane().getStyleClass().add("med-note-detail-pane");
        d.setResizable(true);

        URL medCss = getClass().getResource("/styles/medecin-dashboard.css");
        if (medCss != null) {
            d.getDialogPane().getStylesheets().add(medCss.toExternalForm());
        }
        URL appCss = getClass().getResource("/styles/app.css");
        if (appCss != null) {
            d.getDialogPane().getStylesheets().add(appCss.toExternalForm());
        }

        VBox header = new VBox(8);
        header.getStyleClass().add("med-note-detail-header");
        Label titleLbl = new Label(detailTitle);
        titleLbl.getStyleClass().add("med-note-detail-title");
        Label metaLbl = new Label(metaLine);
        metaLbl.getStyleClass().add("med-note-detail-meta");
        metaLbl.setWrapText(true);
        header.getChildren().addAll(titleLbl, metaLbl);

        Separator sepTop = new Separator();

        /*
         * HTMLEditor ne doit pas être placé dans un ScrollPane (clavier / focus / WebView cassés).
         * Lecture seule : ScrollPane ; édition : HTMLEditor directement dans le StackPane.
         */
        StackPane bodyHost = new StackPane();
        bodyHost.setMinWidth(520);
        bodyHost.setMinHeight(220);
        bodyHost.setPrefHeight(320);
        bodyHost.setMaxHeight(Double.MAX_VALUE);
        bodyHost.getStyleClass().add("med-note-detail-body-host");
        VBox.setVgrow(bodyHost, Priority.ALWAYS);

        ScrollPane readScroll = new ScrollPane();
        readScroll.setFitToWidth(true);
        readScroll.setFitToHeight(true);
        readScroll.getStyleClass().add("med-note-detail-body-scroll");
        readScroll.setMinViewportHeight(200);
        readScroll.setPrefViewportHeight(300);

        final String htmlReadOnly = useWebView && htmlDocument != null
                ? ensureReadOnlyBodyInHtml(htmlDocument)
                : null;
        Runnable showReadOnlyBody = () -> {
            if (useWebView && htmlReadOnly != null) {
                readScroll.setContent(buildNoteDetailReadOnlyWebView(htmlReadOnly));
            } else {
                TextArea ro = new TextArea(plainBody != null ? plainBody : "");
                ro.setEditable(false);
                ro.setWrapText(true);
                ro.setFocusTraversable(false);
                ro.setPrefRowCount(14);
                ro.setMinHeight(200);
                ro.setMaxWidth(Double.MAX_VALUE);
                ro.getStyleClass().add("med-note-detail-plain-read");
                readScroll.setContent(ro);
            }
            bodyHost.getChildren().setAll(readScroll);
        };
        showReadOnlyBody.run();

        /** Zone d’édition : toujours {@link TextArea} (le {@link HTMLEditor} est peu fiable dans un {@link Dialog}). */
        final TextArea[] editAreaRef = new TextArea[1];

        Separator sepBottom = new Separator();

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("med-note-detail-actions");

        Button btnPdf = new Button("Télécharger en PDF");
        Label pdfGlyph = new Label("PDF");
        pdfGlyph.getStyleClass().add("med-note-detail-pdf-glyph");
        btnPdf.setGraphic(pdfGlyph);
        btnPdf.getStyleClass().addAll("med-note-detail-btn", "med-note-detail-btn-pdf");

        Button btnEdit = new Button("Modifier");
        btnEdit.getStyleClass().addAll("med-note-detail-btn", "med-note-detail-btn-edit");

        Button btnSave = new Button("Enregistrer");
        btnSave.getStyleClass().addAll("med-note-detail-btn", "med-note-detail-btn-save");
        btnSave.setVisible(false);
        btnSave.setManaged(false);

        Button btnAnnulEdit = new Button("Annuler");
        btnAnnulEdit.getStyleClass().addAll("med-note-detail-btn", "med-note-detail-btn-annul");
        btnAnnulEdit.setVisible(false);
        btnAnnulEdit.setManaged(false);

        Button btnBack = new Button("Retour à la liste");
        btnBack.getStyleClass().addAll("med-note-detail-btn", "med-note-detail-btn-back");

        Window owner = resolveMedecinDashboardWindow();
        if (owner != null) {
            d.initOwner(owner);
        }
        d.initModality(Modality.WINDOW_MODAL);
        d.setOnCloseRequest(_evt -> hideNoteDetailWindow(d));

        Runnable setActionsReadOnly = () -> {
            btnEdit.setVisible(true);
            btnEdit.setManaged(true);
            btnSave.setVisible(false);
            btnSave.setManaged(false);
            btnAnnulEdit.setVisible(false);
            btnAnnulEdit.setManaged(false);
            actions.getChildren().setAll(btnPdf, btnEdit, btnBack);
        };
        Runnable setActionsEditing = () -> {
            btnEdit.setVisible(false);
            btnEdit.setManaged(false);
            btnSave.setVisible(true);
            btnSave.setManaged(true);
            btnAnnulEdit.setVisible(true);
            btnAnnulEdit.setManaged(true);
            actions.getChildren().setAll(btnPdf, btnSave, btnAnnulEdit, btnBack);
        };

        btnPdf.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer le PDF");
            String datePart = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            fc.setInitialFileName("note-detail-" + datePart + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document PDF", "*.pdf"));
            Window w = d.getDialogPane().getScene() != null ? d.getDialogPane().getScene().getWindow() : owner;
            File dest = w != null ? fc.showSaveDialog(w) : null;
            if (dest == null) {
                return;
            }
            try {
                NotePdfExporter.save(dest, detailTitle, metaLine, plainBody);
                alert(Alert.AlertType.INFORMATION, "PDF", "Le fichier a été enregistré.");
            } catch (IOException ex) {
                alert(Alert.AlertType.ERROR, "PDF", ex.getMessage() != null ? ex.getMessage() : "Export impossible.");
            }
        });
        btnEdit.setOnAction(ev -> {
            releaseNoteBodyEditor(editAreaRef[0]);
            editAreaRef[0] = null;

            String seed;
            if (row.getKind() == NoteKind.RENDEZ_VOUS) {
                String full = "";
                try {
                    full = appointmentService.findById(row.getEntityId()).map(Appointment::getNotes).orElse("");
                } catch (SQLException ignored) {
                    full = "";
                }
                seed = RdvNotesFormat.extractMedecinNote(full);
            } else {
                seed = row.getContenu() != null ? row.getContenu() : "";
            }

            String plainForEdit = NoteHtmlUtil.looksLikeHtml(seed)
                    ? NoteHtmlUtil.stripToPlain(seed)
                    : (seed != null ? seed : "");
            TextArea ta = new TextArea(plainForEdit);
            ta.setEditable(true);
            ta.setWrapText(true);
            ta.setPrefRowCount(16);
            ta.setMinSize(520, 280);
            ta.prefWidthProperty().bind(bodyHost.widthProperty());
            ta.getStyleClass().add("med-note-detail-plain-edit");
            editAreaRef[0] = ta;
            bodyHost.getChildren().setAll(ta);
            StackPane.setAlignment(ta, Pos.TOP_CENTER);
            setActionsEditing.run();
            Platform.runLater(() -> {
                bodyHost.requestLayout();
                ta.requestFocus();
            });
        });
        btnSave.setOnAction(ev -> {
            TextArea ta = editAreaRef[0];
            if (ta == null) {
                return;
            }
            String html = NoteHtmlUtil.wrapForEditor(ta.getText());
            if (persistNoteRowHtmlEdit(row, html)) {
                hideNoteDetailWindow(d);
            }
        });
        btnAnnulEdit.setOnAction(ev -> {
            releaseNoteBodyEditor(editAreaRef[0]);
            editAreaRef[0] = null;
            showReadOnlyBody.run();
            setActionsReadOnly.run();
        });
        btnBack.setOnAction(ev -> hideNoteDetailWindow(d));

        setActionsReadOnly.run();

        VBox root = new VBox(0);
        root.getStyleClass().add("med-note-detail-root");
        root.getChildren().addAll(header, sepTop, bodyHost, sepBottom, actions);
        d.getDialogPane().setContent(root);
        d.getDialogPane().setPrefWidth(620);
        d.getDialogPane().setMinHeight(480);
        d.showAndWait();
    }

    private static void releaseNoteBodyEditor(TextArea ta) {
        if (ta != null) {
            ta.prefWidthProperty().unbind();
        }
    }

    private void sendTestSmsForAppointment(Appointment appt) {
        if (appt == null || appt.getPatientId() <= 0) {
            alert(Alert.AlertType.WARNING, "SMS test", "Patient introuvable pour ce rendez-vous.");
            return;
        }
        if (!TwilioSmsService.isConfigured()) {
            alert(Alert.AlertType.WARNING, "SMS test",
                    "Twilio non configuré. Vérifiez twilio.enabled, accountSid, authToken et fromPhone.");
            return;
        }
        try {
            Optional<User> patientOpt = userService.findById(appt.getPatientId());
            if (patientOpt.isEmpty()) {
                alert(Alert.AlertType.WARNING, "SMS test", "Patient introuvable en base.");
                return;
            }
            User patient = patientOpt.get();
            String to = TwilioSmsService.toE164(patient.getTelephone(), TwilioSmsService.defaultCallingCodeDigits());
            if (to == null || to.length() < 8) {
                alert(Alert.AlertType.WARNING, "SMS test",
                        "Numéro patient invalide. Format attendu: +216XXXXXXXX (E.164).");
                return;
            }
            String medName = Optional.ofNullable(AppState.getCurrentUser())
                    .map(u -> (u.getPrenom() != null ? u.getPrenom().trim() : ""))
                    .filter(s -> !s.isBlank())
                    .orElse("votre médecin");
            String when = appt.getDateHeure() != null
                    ? appt.getDateHeure().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "—";
            String msg = "AutiCare | Test SMS\n"
                    + "Bonjour, ceci est un test de rappel.\n"
                    + "RDV: " + when + "\n"
                    + "Praticien: " + medName + ".";
            boolean ok = TwilioSmsService.sendSms(to, msg);
            if (ok) {
                alert(Alert.AlertType.INFORMATION, "SMS test", "SMS test envoyé avec succès vers " + to + ".");
            } else {
                String detail = TwilioSmsService.getLastErrorDetail();
                alert(Alert.AlertType.ERROR, "SMS test",
                        "Échec d'envoi Twilio.\n" + (detail == null || detail.isBlank() ? "Cause inconnue." : detail));
            }
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "SMS test", ex.getMessage() != null ? ex.getMessage() : "Erreur SMS test.");
        }
    }

    /**
     * Compatibilité avec d'anciens appels (détail note) pour éviter les erreurs de compilation
     * si l'IDE garde encore une ancienne référence pendant l'indexation.
     */
    private void sendTestSmsForNoteRow(NoteRow row) {
        if (row == null || row.getPatientId() <= 0) {
            alert(Alert.AlertType.WARNING, "SMS test", "Patient introuvable pour ce rendez-vous.");
            return;
        }
        if (!TwilioSmsService.isConfigured()) {
            alert(Alert.AlertType.WARNING, "SMS test",
                    "Twilio non configuré. Vérifiez twilio.enabled, accountSid, authToken et fromPhone.");
            return;
        }
        try {
            Optional<User> patientOpt = userService.findById(row.getPatientId());
            if (patientOpt.isEmpty()) {
                alert(Alert.AlertType.WARNING, "SMS test", "Patient introuvable en base.");
                return;
            }
            User patient = patientOpt.get();
            String to = TwilioSmsService.toE164(patient.getTelephone(), TwilioSmsService.defaultCallingCodeDigits());
            if (to == null || to.length() < 8) {
                alert(Alert.AlertType.WARNING, "SMS test",
                        "Numéro patient invalide. Format attendu: +216XXXXXXXX (E.164).");
                return;
            }
            String medName = Optional.ofNullable(AppState.getCurrentUser())
                    .map(u -> (u.getPrenom() != null ? u.getPrenom().trim() : ""))
                    .filter(s -> !s.isBlank())
                    .orElse("votre médecin");
            String msg = "AutiCare | Test SMS\n"
                    + "Bonjour, ceci est un test de rappel.\n"
                    + "Praticien: " + medName + ".";
            boolean ok = TwilioSmsService.sendSms(to, msg);
            if (ok) {
                alert(Alert.AlertType.INFORMATION, "SMS test", "SMS test envoyé avec succès vers " + to + ".");
            } else {
                String detail = TwilioSmsService.getLastErrorDetail();
                alert(Alert.AlertType.ERROR, "SMS test",
                        "Échec d'envoi Twilio.\n" + (detail == null || detail.isBlank() ? "Cause inconnue." : detail));
            }
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "SMS test", ex.getMessage() != null ? ex.getMessage() : "Erreur SMS test.");
        }
    }

    private static void hideNoteDetailWindow(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        dialog.hide();
        javafx.scene.Scene sc = dialog.getDialogPane().getScene();
        if (sc != null && sc.getWindow() != null) {
            sc.getWindow().hide();
        }
    }

    private static String buildRdvCombinedHtmlDocument(String patientBloc, String med) {
        String safePatient = NoteHtmlUtil.escapePlainForHtml(patientBloc.isBlank() ? "—" : patientBloc)
                .replace("\n", "<br>");
        String medHtml = med.contains("<html") ? med : "<div>" + med + "</div>";
        return "<html><head><meta charset='UTF-8'></head><body contenteditable=\"false\" style='font-family:sans-serif;font-size:14px;'>"
                + "<h3 style='margin:0 0 8px 0;'>Fiche demande</h3><p style='margin:0 0 16px 0;'>" + safePatient + "</p>"
                + "<h3 style='margin:0 0 8px 0;'>Note du médecin</h3>" + medHtml + "</body></html>";
    }

    /** Affichage WebView en lecture seule (pas d’édition dans le navigateur embarqué). */
    private static String ensureReadOnlyBodyInHtml(String html) {
        if (html == null || html.isBlank()) {
            return "<html><head><meta charset=\"UTF-8\"/></head><body contenteditable=\"false\"></body></html>";
        }
        String h = html.trim();
        String low = h.toLowerCase(Locale.ROOT);
        if (low.contains("<body")) {
            if (low.contains("contenteditable")) {
                return h;
            }
            return h.replaceFirst("(?i)<body\\b", "<body contenteditable=\"false\" ");
        }
        return "<html><head><meta charset=\"UTF-8\"/></head><body contenteditable=\"false\">" + h + "</body></html>";
    }

    private static Node buildNoteDetailReadOnlyWebView(String htmlReadOnly) {
        WebView wv = new WebView();
        wv.setPrefSize(560, 300);
        wv.getEngine().loadContent(htmlReadOnly, "text/html");
        return wv;
    }

    private void refreshRdvFromDb() {
        rdvAppointments.clear();
        try {
            rdvAppointments.addAll(appointmentService.findByMedecin(currentDoctorId));
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Rendez-vous", "Impossible de charger les données : " + e.getMessage());
        }
        updateRdvStats();
        rebuildRdvList();
        LocalDate today = LocalDate.now();
        long rdvAujourdhui = rdvAppointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.ANNULE
                        && a.getDateHeure() != null
                        && a.getDateHeure().toLocalDate().equals(today))
                .count();
        if (statRdvLabel != null) {
            statRdvLabel.setText(String.valueOf(rdvAujourdhui));
        }
        updateHomePatientKpi();
        updateMedTodayRdvBanner();
        refreshMedecinPendingDemandesUi();
    }

    private void updateRdvStats() {
        LocalDate today = LocalDate.now();
        long confirmes = rdvAppointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PLANIFIE || a.getStatus() == AppointmentStatus.TERMINE)
                .count();
        long enAttente = rdvAppointments.stream().filter(a -> a.getStatus() == AppointmentStatus.EN_ATTENTE).count();
        long annules = rdvAppointments.stream().filter(a -> a.getStatus() == AppointmentStatus.ANNULE).count();
        long aujourdhui = rdvAppointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.ANNULE
                        && a.getDateHeure() != null
                        && a.getDateHeure().toLocalDate().equals(today))
                .count();
        if (medRdvTotalLabel != null) {
            medRdvTotalLabel.setText(String.valueOf(rdvAppointments.size()));
        }
        if (rdvStatConfirmesLabel != null) {
            rdvStatConfirmesLabel.setText(String.valueOf(confirmes));
        }
        if (rdvStatAttenteLabel != null) {
            rdvStatAttenteLabel.setText(String.valueOf(enAttente));
        }
        if (rdvStatAnnulesLabel != null) {
            rdvStatAnnulesLabel.setText(String.valueOf(annules));
        }
        if (rdvStatAujourdhuiLabel != null) {
            rdvStatAujourdhuiLabel.setText(String.valueOf(aujourdhui));
        }
    }

    private void rebuildRdvList() {
        if (rdvEmptyState == null || rdvBoardRoot == null || rdvKanbanBodyConfirm == null) {
            return;
        }
        boolean empty = rdvAppointments.isEmpty();
        rdvEmptyState.setVisible(empty);
        rdvEmptyState.setManaged(empty);
        rdvBoardRoot.setVisible(!empty);
        rdvBoardRoot.setManaged(!empty);
        if (empty) {
            return;
        }
        String sortChoice = rdvSortCombo != null && rdvSortCombo.getValue() != null
                ? rdvSortCombo.getValue()
                : "";
        Comparator<Appointment> cmp = Comparator.comparing(Appointment::getDateHeure, Comparator.nullsLast(Comparator.naturalOrder()));
        if (sortChoice.contains("récent — ancien")) {
            cmp = cmp.reversed();
        }
        List<Appointment> confirmes = rdvAppointments.stream()
                .filter(this::rdvMatchesToolbarSearch)
                .filter(a -> a.getStatus() == AppointmentStatus.PLANIFIE || a.getStatus() == AppointmentStatus.TERMINE)
                .sorted(cmp)
                .collect(Collectors.toList());
        List<Appointment> attente = rdvAppointments.stream()
                .filter(this::rdvMatchesToolbarSearch)
                .filter(a -> a.getStatus() == AppointmentStatus.EN_ATTENTE)
                .sorted(cmp)
                .collect(Collectors.toList());
        List<Appointment> annules = rdvAppointments.stream()
                .filter(this::rdvMatchesToolbarSearch)
                .filter(a -> a.getStatus() == AppointmentStatus.ANNULE)
                .sorted(cmp)
                .collect(Collectors.toList());
        if (rdvKanbanTitleConfirm != null) {
            rdvKanbanTitleConfirm.setText("PROCHAINS CONFIRMÉS (" + confirmes.size() + ")");
        }
        if (rdvKanbanTitleWait != null) {
            rdvKanbanTitleWait.setText("EN ATTENTE D'ACTION (" + attente.size() + ")");
        }
        if (rdvKanbanTitleCancel != null) {
            rdvKanbanTitleCancel.setText("ANNULATIONS RÉCENTES (" + annules.size() + ")");
        }
        /* Couleur du texte forcée en ligne (priorité sur tout CSS parent qui imposerait du blanc). */
        applyRdvKanbanTitleTextFills();
        fillRdvKanbanColumn(rdvKanbanBodyConfirm, confirmes, MedRdvKanbanCol.CONFIRME,
                "Aucun rendez-vous confirmé pour cette recherche.");
        fillRdvKanbanColumn(rdvKanbanBodyWait, attente, MedRdvKanbanCol.ATTENTE,
                "Aucun rendez-vous en attente.");
        fillRdvKanbanColumn(rdvKanbanBodyCancel, annules, MedRdvKanbanCol.ANNULE,
                "Aucune annulation récente.");
    }

    private void applyRdvKanbanTitleTextFills() {
        if (rdvKanbanTitleConfirm != null) {
            rdvKanbanTitleConfirm.setStyle("-fx-text-fill: #047857;");
        }
        if (rdvKanbanTitleWait != null) {
            rdvKanbanTitleWait.setStyle("-fx-text-fill: #c2410c;");
        }
        if (rdvKanbanTitleCancel != null) {
            rdvKanbanTitleCancel.setStyle("-fx-text-fill: #be123c;");
        }
    }

    private boolean rdvMatchesToolbarSearch(Appointment a) {
        if (rdvSearchField == null) {
            return true;
        }
        String raw = rdvSearchField.getText();
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String q = normalizeSidebarSearchText(raw);
        if (q.isEmpty()) {
            return true;
        }
        String patient = normalizeSidebarSearchText(resolvePatientLabel(a.getPatientId()));
        String when = normalizeSidebarSearchText(formatRdvDateTimeRangeForUi(a));
        String statut = normalizeSidebarSearchText(statusShortLabelFr(a.getStatus()));
        Optional<User> pu = resolvePatientUser(a.getPatientId());
        String tel = normalizeSidebarSearchText(pu.map(User::getTelephone).orElse(""));
        String email = normalizeSidebarSearchText(pu.map(User::getEmail).orElse(""));
        String motif = normalizeSidebarSearchText(a.getMotif() != null ? a.getMotif() : "");
        return patient.contains(q) || when.contains(q) || statut.contains(q) || tel.contains(q)
                || email.contains(q) || motif.contains(q);
    }

    private enum MedRdvKanbanCol {
        CONFIRME,
        ATTENTE,
        ANNULE
    }

    private void fillRdvKanbanColumn(VBox body, List<Appointment> items, MedRdvKanbanCol col, String emptyText) {
        if (body == null) {
            return;
        }
        body.getChildren().clear();
        if (items.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.setWrapText(true);
            empty.getStyleClass().add("med-rdv-kanban-empty");
            empty.setMaxWidth(Double.MAX_VALUE);
            body.getChildren().add(empty);
            return;
        }
        for (Appointment a : items) {
            body.getChildren().add(buildRdvKanbanCard(a, col));
        }
    }

    private VBox buildRdvKanbanCard(Appointment a, MedRdvKanbanCol col) {
        String patientLabel = resolvePatientLabel(a.getPatientId());
        Optional<User> pu = resolvePatientUser(a.getPatientId());
        String initials = pu.map(UserAvatarGraphic::initialsFor)
                .filter(s -> !s.isBlank())
                .orElseGet(() -> initialsFromDisplayName(patientLabel));
        StackPane avatar = UserAvatarGraphic.build(pu.orElse(null), 40, initials, "med-rdv-avatar-initials");

        Label capPatient = new Label("PATIENT");
        capPatient.getStyleClass().add("med-rdv-kanban-caption");
        Label nameLbl = new Label(patientLabel);
        nameLbl.getStyleClass().add("med-rdv-kanban-patient-name");
        nameLbl.setWrapText(true);
        VBox nameCol = new VBox(2);
        nameCol.getChildren().addAll(capPatient, nameLbl);
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("med-rdv-kanban-card-patient-row");
        top.getChildren().addAll(avatar, nameCol);

        Label capWhen = new Label("DATE & HEURE");
        capWhen.getStyleClass().add("med-rdv-kanban-caption");
        Label whenLbl = new Label(formatRdvDateTimeRangeForUi(a));
        whenLbl.getStyleClass().add("med-rdv-kanban-value");
        whenLbl.setWrapText(true);
        VBox whenCol = new VBox(2);
        whenCol.getChildren().addAll(capWhen, whenLbl);

        Label capSt = new Label("STATUT");
        capSt.getStyleClass().add("med-rdv-kanban-caption");
        Label stLbl = new Label(statusShortLabelFr(a.getStatus()));
        stLbl.getStyleClass().addAll("med-rdv-badge", badgeStyleForStatus(a.getStatus()));
        VBox stCol = new VBox(4);
        stCol.getChildren().addAll(capSt, stLbl);

        String rawNotes = a.getNotes() != null ? a.getNotes() : "";
        boolean peutVoirDetail = rawNotes != null && !rawNotes.isBlank();

        Button btnVoirNote = new Button();
        Label eye = new Label("👁");
        eye.getStyleClass().add("med-rdv-voir-icon");
        btnVoirNote.setGraphic(eye);
        btnVoirNote.getStyleClass().addAll("med-rdv-kanban-icon-btn", "med-rdv-kanban-icon-view");
        btnVoirNote.setTooltip(new Tooltip("Voir le détail de la note"));
        btnVoirNote.setDisable(!peutVoirDetail);
        btnVoirNote.setOnAction(ev -> showNoteDetail(noteRowFromAppointment(a)));

        Button edit = new Button("✎");
        edit.getStyleClass().addAll("med-rdv-kanban-icon-btn", "med-rdv-kanban-icon-muted");
        edit.setTooltip(new Tooltip("Modifier"));
        edit.setOnAction(ev -> openRdvEditDialog(a));

        Button del = new Button("🗑");
        del.getStyleClass().addAll("med-rdv-kanban-icon-btn", "med-rdv-kanban-icon-danger");
        del.setTooltip(new Tooltip("Supprimer"));
        del.setOnAction(ev -> confirmDeleteRdv(a, patientLabel));

        Button sms = new Button("SMS test");
        sms.getStyleClass().addAll("med-rdv-kanban-icon-btn", "med-rdv-kanban-icon-sms");
        sms.setTooltip(new Tooltip("Tester SMS maintenant"));
        sms.setOnAction(ev -> sendTestSmsForAppointment(a));

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("med-rdv-kanban-actions");
        if (col == MedRdvKanbanCol.CONFIRME) {
            actions.getChildren().addAll(btnVoirNote, sms, edit, del);
        } else {
            actions.getChildren().addAll(btnVoirNote, edit, del);
        }

        VBox card = new VBox(8);
        card.getStyleClass().addAll("med-rdv-kanban-card", switch (col) {
            case CONFIRME -> "med-rdv-kanban-card-ok";
            case ATTENTE -> "med-rdv-kanban-card-wait";
            case ANNULE -> "med-rdv-kanban-card-cancel";
        });
        card.getChildren().addAll(top, whenCol, stCol, actions);
        return card;
    }

    /** Ligne « notes » (RDV) pour réutiliser le dialogue détail / édition médecin. */
    private NoteRow noteRowFromAppointment(Appointment a) {
        String patientLabel = resolvePatientLabel(a.getPatientId());
        String raw = a.getNotes() != null ? a.getNotes().strip() : "";
        LocalDateTime sort = a.getDateHeure() != null ? a.getDateHeure() : LocalDateTime.MIN;
        String dateLabel = a.getDateHeure() != null ? a.getDateHeure().format(NOTE_DATE) : "—";
        String contenuMedecin = RdvNotesFormat.extractMedecinNote(raw);
        return new NoteRow(
                NoteKind.RENDEZ_VOUS,
                a.getId(),
                a.getPatientId(),
                patientLabel,
                contenuMedecin,
                dateLabel,
                sort,
                raw);
    }

    private static String initialsFromDisplayName(String display) {
        if (display == null || display.isBlank()) {
            return "?";
        }
        String[] parts = display.trim().split("\\s+");
        if (parts.length >= 2) {
            String a = parts[0].isEmpty() ? "" : parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
            String b = parts[parts.length - 1].isEmpty() ? "" : parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT);
            return (a + b).isEmpty() ? "?" : a + b;
        }
        return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private Optional<User> resolvePatientUser(int patientId) {
        try {
            return userService.findById(patientId);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /**
     * Affiche date/heure du RDV ; si un créneau {@link Availability} est connu (même id que {@code disponibilite_id}),
     * utilise son début/fin (corrige les bases où seule la colonne DATE {@code date_rdv} était remplie → minuit).
     */
    private static String formatRdvDateTimeRange(Appointment a, Availability slot) {
        if (slot != null && slot.getId() > 0
                && a.getDisponibiliteId() == slot.getId()
                && slot.getDebut() != null && slot.getFin() != null) {
            LocalDateTime start = slot.getDebut();
            LocalDateTime end = slot.getFin();
            return start.format(NOTE_DATE) + " - " + end.format(TIME_FMT);
        }
        if (a.getDateHeure() == null) {
            return "—";
        }
        LocalDateTime start = a.getDateHeure();
        LocalDateTime end = start.plusMinutes(30);
        return start.format(NOTE_DATE) + " - " + end.format(TIME_FMT);
    }

    private String formatRdvDateTimeRangeForUi(Appointment a) {
        try {
            if (a.getDisponibiliteId() > 0) {
                Optional<Availability> av = availabilityService.findById(a.getDisponibiliteId());
                if (av.isPresent()) {
                    return formatRdvDateTimeRange(a, av.get());
                }
            }
        } catch (SQLException ignored) {
            // affichage : retomber sur date_heure du RDV
        }
        return formatRdvDateTimeRange(a, null);
    }

    private Window resolveMedecinDashboardWindow() {
        if (medRdvScroll != null && medRdvScroll.getScene() != null) {
            return medRdvScroll.getScene().getWindow();
        }
        if (notesTableView != null && notesTableView.getScene() != null) {
            return notesTableView.getScene().getWindow();
        }
        return MainApp.getPrimaryStage();
    }

    /** Copie fraîche du RDV pour l’écran modifier, ou le paramètre si la base est indisponible. */
    private Appointment loadAppointmentForRdvEdit(Appointment a) {
        try {
            return appointmentService.findById(a.getId()).orElse(a);
        } catch (SQLException ignored) {
            return a;
        }
    }

    private void openRdvEditDialog(Appointment a) {
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Modifier le rendez-vous");
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        d.getDialogPane().getStyleClass().add("med-rdv-edit-dialog-pane");
        d.setResizable(true);
        Node hiddenCloseBtn = d.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (hiddenCloseBtn != null) {
            hiddenCloseBtn.setVisible(false);
            hiddenCloseBtn.setManaged(false);
        }

        URL medCss = getClass().getResource("/styles/medecin-dashboard.css");
        if (medCss != null) {
            d.getDialogPane().getStylesheets().add(medCss.toExternalForm());
        }
        URL appCss = getClass().getResource("/styles/app.css");
        if (appCss != null) {
            d.getDialogPane().getStylesheets().add(appCss.toExternalForm());
        }

        Window ownerWin = resolveMedecinDashboardWindow();
        if (ownerWin != null) {
            d.initOwner(ownerWin);
        }
        d.initModality(Modality.WINDOW_MODAL);

        final Appointment snapshot = loadAppointmentForRdvEdit(a);

        String patientName = resolvePatientLabel(snapshot.getPatientId());
        Optional<User> pu = resolvePatientUser(snapshot.getPatientId());
        String tel = dashIfBlank(pu.map(User::getTelephone).orElse(null));
        String adresse = dashIfBlank(pu.map(User::getAdresse).orElse(null));
        String dateLigne = formatRdvDateTimeRangeEditForUi(snapshot);

        Button btnRetour = new Button("← Retour aux rendez-vous");
        btnRetour.getStyleClass().add("med-rdv-edit-back");
        btnRetour.setOnAction(ev -> hideNoteDetailWindow(d));

        Label title = new Label("Modifier le rendez-vous");
        title.getStyleClass().add("med-rdv-edit-page-title");

        VBox card = new VBox(14);
        card.getStyleClass().add("med-rdv-edit-card");

        Label dataTitle = new Label("Données du patient");
        dataTitle.getStyleClass().add("med-rdv-edit-section-title");

        GridPane dataGrid = new GridPane();
        dataGrid.setHgap(28);
        dataGrid.setVgap(12);
        dataGrid.getStyleClass().add("med-rdv-edit-data-grid");
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPercentWidth(50);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        dataGrid.getColumnConstraints().addAll(c0, c1);

        int gr = 0;
        dataGrid.add(buildRdvEditField("Patient", patientName), 0, gr);
        dataGrid.add(buildRdvEditField("Date du rendez-vous", dateLigne), 1, gr++);
        dataGrid.add(buildRdvEditField("Téléphone", tel), 0, gr);
        dataGrid.add(buildRdvEditField("Adresse", adresse), 1, gr);

        Separator sep = new Separator();
        sep.getStyleClass().add("med-rdv-edit-sep");

        HBox statutRow = new HBox(6);
        statutRow.setAlignment(Pos.CENTER_LEFT);
        Label stLbl = new Label("Statut");
        stLbl.getStyleClass().add("med-rdv-edit-field-label");
        Label star = new Label(" *");
        star.getStyleClass().add("med-rdv-edit-required");
        statutRow.getChildren().addAll(stLbl, star);

        ComboBox<AppointmentStatus> statusCombo = new ComboBox<>(
                FXCollections.observableArrayList(AppointmentStatus.values()));
        statusCombo.setValue(snapshot.getStatus() != null ? snapshot.getStatus() : AppointmentStatus.PLANIFIE);
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        statusCombo.getStyleClass().add("med-rdv-edit-status-combo");
        HBox.setHgrow(statusCombo, Priority.ALWAYS);
        statusCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AppointmentStatus s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : statusShortLabelFr(s));
            }
        });
        statusCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AppointmentStatus s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : statusShortLabelFr(s));
            }
        });

        Label motifLbl = new Label("Motif");
        motifLbl.getStyleClass().add("med-rdv-edit-field-label");
        TextArea motifField = new TextArea(snapshot.getMotif() != null ? snapshot.getMotif() : "");
        motifField.setPrefRowCount(2);
        motifField.setWrapText(true);
        motifField.getStyleClass().add("med-rdv-edit-motif");

        VBox statutBlock = new VBox(6, statutRow, statusCombo, motifLbl, motifField);

        Button btnSave = new Button("Enregistrer");
        btnSave.getStyleClass().addAll("med-rdv-edit-btn", "med-rdv-edit-btn-save");
        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().addAll("med-rdv-edit-btn", "med-rdv-edit-btn-cancel");
        HBox footer = new HBox(12, btnSave, btnCancel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("med-rdv-edit-footer");

        card.getChildren().addAll(dataTitle, dataGrid, sep, statutBlock, footer);

        btnCancel.setOnAction(ev -> hideNoteDetailWindow(d));
        btnSave.setOnAction(ev -> {
            AppointmentStatus newStat = statusCombo.getValue();
            if (newStat == null) {
                alert(Alert.AlertType.WARNING, "Rendez-vous", "Indiquez un statut.");
                return;
            }
            String motif = motifField.getText() != null ? motifField.getText().trim() : "";
            try {
                Optional<Appointment> fresh = appointmentService.findById(snapshot.getId());
                if (fresh.isEmpty()) {
                    alert(Alert.AlertType.ERROR, "Rendez-vous", "Ce rendez-vous n’existe plus.");
                    return;
                }
                Appointment upd = fresh.get();
                // Flux "depuis disponibilités" : on autorise la correction d'un ancien medecin_id incohérent.
                upd.setMedecinId(currentDoctorId);
                upd.setStatus(newStat);
                upd.setMotif(motif);
                boolean becamePlanifie = snapshot.getStatus() == AppointmentStatus.EN_ATTENTE
                        && newStat == AppointmentStatus.PLANIFIE;
                appointmentService.updateStatusAndMotifById(upd.getId(), currentDoctorId, newStat, motif);
                Optional<Appointment> afterSave = appointmentService.findById(upd.getId());
                if (afterSave.isEmpty()) {
                    alert(Alert.AlertType.ERROR, "Rendez-vous",
                            "Enregistrement incertain: rendez-vous introuvable après sauvegarde.");
                    return;
                }
                Appointment persisted = afterSave.get();
                String persistedMotif = persisted.getMotif() == null ? "" : persisted.getMotif().trim();
                String wantedMotif = motif == null ? "" : motif.trim();
                boolean persistedOk = persisted.getStatus() == newStat && persistedMotif.equals(wantedMotif);
                if (!persistedOk) {
                    alert(Alert.AlertType.ERROR, "Rendez-vous",
                            "La base n'a pas confirmé la modification (statut/motif inchangé).");
                    return;
                }
                hideNoteDetailWindow(d);
                refreshRdvFromDb();
                // L'édition peut être ouverte depuis l'écran Disponibilités (créneau rouge) :
                // on rafraîchit aussi les disponibilités pour refléter immédiatement le nouveau statut RDV.
                refreshDispoFromDb();
                rebuildCalendarGrid();
                rebuildDispoFullCalendar();
                appointmentService.notifyPatientAfterDoctorRdvEdit(snapshot, persisted);
                MedecinGcalSync gcal = MedecinGcalSync.SKIPPED;
                if (becamePlanifie) {
                    try {
                        gcal = tryOpenMedecinGoogleCalendarForAccepted(persisted);
                    } catch (Exception ignored) {
                        gcal = MedecinGcalSync.SKIPPED;
                    }
                }
                String msgSaved = "Modifications enregistrées.";
                if (becamePlanifie) {
                    if (gcal == MedecinGcalSync.API_CREATED) {
                        msgSaved += " L’événement a été ajouté à votre Google Agenda.";
                    } else if (gcal == MedecinGcalSync.TEMPLATE_BROWSER) {
                        msgSaved += " Si une page Google Agenda s’est ouverte, enregistrez l’événement pour le conserver.";
                    }
                }
                alert(Alert.AlertType.INFORMATION, "Rendez-vous", msgSaved);
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Rendez-vous",
                        errorMessageOrDefault(ex, "Enregistrement impossible."));
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Rendez-vous",
                        errorMessageOrDefault(ex, "Erreur inattendue pendant l'enregistrement."));
            }
        });

        VBox root = new VBox(18, btnRetour, title, card);
        root.getStyleClass().add("med-rdv-edit-root");
        root.setFillWidth(true);
        VBox.setVgrow(card, Priority.NEVER);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("med-rdv-edit-scroll");
        d.getDialogPane().setContent(sp);
        d.getDialogPane().setPrefWidth(580);
        d.getDialogPane().setMinHeight(Region.USE_COMPUTED_SIZE);

        d.showAndWait();
    }

    private static String dashIfBlank(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : "—";
    }

    private static String formatRdvDateTimeRangeEdit(Appointment a) {
        if (a.getDateHeure() == null) {
            return "—";
        }
        LocalDateTime start = a.getDateHeure();
        LocalDateTime end = start.plusMinutes(30);
        return start.format(RDV_EDIT_DATE_LINE) + " – " + end.format(TIME_FMT);
    }

    private String formatRdvDateTimeRangeEditForUi(Appointment a) {
        try {
            if (a.getDisponibiliteId() > 0) {
                Optional<Availability> av = availabilityService.findById(a.getDisponibiliteId());
                if (av.isPresent() && av.get().getDebut() != null && av.get().getFin() != null) {
                    LocalDateTime s = av.get().getDebut();
                    LocalDateTime e = av.get().getFin();
                    return s.format(RDV_EDIT_DATE_LINE) + " – " + e.format(TIME_FMT);
                }
            }
        } catch (SQLException ignored) {
        }
        return formatRdvDateTimeRangeEdit(a);
    }

    private static VBox buildRdvEditField(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("med-rdv-edit-field-label");
        Label v = new Label(value != null ? value : "—");
        v.setWrapText(true);
        v.getStyleClass().add("med-rdv-edit-field-value");
        VBox box = new VBox(4, l, v);
        return box;
    }

    private void confirmDeleteRdv(Appointment a, String patientLabel) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le rendez-vous");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer définitivement le rendez-vous de « " + patientLabel + " » ?");
        Window w = medRdvScroll != null && medRdvScroll.getScene() != null
                ? medRdvScroll.getScene().getWindow()
                : MainApp.getPrimaryStage();
        if (w != null) {
            confirm.initOwner(w);
        }
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        try {
            Optional<Appointment> fresh = appointmentService.findById(a.getId());
            if (fresh.isEmpty()) {
                refreshRdvFromDb();
                return;
            }
            if (fresh.get().getMedecinId() != currentDoctorId) {
                alert(Alert.AlertType.ERROR, "Rendez-vous", "Vous ne pouvez pas supprimer ce rendez-vous.");
                return;
            }
            appointmentService.delete(a.getId());
            refreshRdvFromDb();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Rendez-vous",
                    ex.getMessage() != null ? ex.getMessage() : "Suppression impossible.");
        }
    }

    private String resolvePatientLabel(int patientId) {
        try {
            Optional<User> pu = userService.findById(patientId);
            if (pu.isPresent()) {
                User p = pu.get();
                String pn = ((p.getPrenom() != null ? p.getPrenom().trim() : "") + " "
                        + (p.getNom() != null ? p.getNom().trim() : "")).trim();
                if (!pn.isBlank()) {
                    return pn;
                }
            }
        } catch (SQLException ignored) {
            // keep fallback
        }
        return "Patient #" + patientId;
    }

    private static String statusShortLabelFr(AppointmentStatus s) {
        if (s == null) {
            return "—";
        }
        return switch (s) {
            case EN_ATTENTE -> "En attente";
            case PLANIFIE -> "Confirmé";
            case ANNULE -> "Annulé";
            case TERMINE -> "Terminé";
        };
    }

    private static String badgeStyleForStatus(AppointmentStatus s) {
        if (s == null) {
            return "med-rdv-badge-wait";
        }
        return switch (s) {
            case EN_ATTENTE -> "med-rdv-badge-attente";
            case TERMINE -> "med-rdv-badge-ok";
            case ANNULE -> "med-rdv-badge-cancel";
            case PLANIFIE -> "med-rdv-badge-confirmed";
        };
    }

    /** Compte les demandes {@link AppointmentStatus#EN_ATTENTE} et met en évidence la cloche / le menu. */
    private void refreshMedecinPendingDemandesUi() {
        if (currentDoctorId <= 0) {
            return;
        }
        try {
            int n = appointmentService.countUnreadEnAttenteDemandesForMedecin(currentDoctorId);
            if (navBtnNotif != null) {
                navBtnNotif.setText(n > 0 ? "🔔 Notifications (" + n + ")" : "🔔 Notifications");
                navBtnNotif.getStyleClass().remove("med-nav-notif-urgent");
                if (n > 0) {
                    navBtnNotif.getStyleClass().add("med-nav-notif-urgent");
                }
            }
            if (medTopbarBellLabel != null) {
                medTopbarBellLabel.getStyleClass().remove("med-topbar-bell-urgent");
                if (n > 0) {
                    medTopbarBellLabel.getStyleClass().add("med-topbar-bell-urgent");
                }
            }
        } catch (SQLException ignored) {
            // garder l’UI utilisable
        }
    }

    private void refreshNotifications() {
        List<Appointment> pending = new ArrayList<>();
        List<Appointment> history = new ArrayList<>();
        try {
            pending = appointmentService.findEnAttenteByMedecin(currentDoctorId);
            history = appointmentService.findMedecinDecisionHistory(currentDoctorId, 50);
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Notifications", e.getMessage());
        }
        if (notifListContainer != null) {
            notifListContainer.getChildren().clear();
            Label titlePending = new Label("Demandes en attente");
            titlePending.getStyleClass().add("med-notif-section-title");
            notifListContainer.getChildren().add(titlePending);
            if (pending.isEmpty()) {
                Label noneP = new Label("Aucune demande en attente de validation.");
                noneP.getStyleClass().add("med-notif-section-empty");
                noneP.setWrapText(true);
                notifListContainer.getChildren().add(noneP);
            } else {
                for (Appointment a : pending) {
                    notifListContainer.getChildren().add(buildPendingDemandeNotifCard(a));
                }
            }
            Separator sep = new Separator();
            sep.getStyleClass().add("med-notif-sep");
            notifListContainer.getChildren().add(sep);
            Label titleHist = new Label("Historique (réponses enregistrées en base)");
            titleHist.getStyleClass().add("med-notif-section-title");
            notifListContainer.getChildren().add(titleHist);
            if (history.isEmpty()) {
                Label noneH = new Label("Aucun historique pour l’instant (les acceptations et refus apparaîtront ici).");
                noneH.getStyleClass().add("med-notif-section-empty");
                noneH.setWrapText(true);
                notifListContainer.getChildren().add(noneH);
            } else {
                for (Appointment a : history) {
                    notifListContainer.getChildren().add(buildHistoryDemandeNotifCard(a));
                }
            }
        }
        int totalPending = pending.size();
        int histCount = history.size();
        int nonLues;
        try {
            nonLues = appointmentService.countUnreadEnAttenteDemandesForMedecin(currentDoctorId);
        } catch (SQLException e) {
            nonLues = totalPending;
        }
        int demandes = totalPending;
        LocalDate today = LocalDate.now();
        LocalDate finSemaine = today.plusDays(7);
        int aujourdhui = (int) pending.stream()
                .filter(x -> x.getDateHeure() != null && x.getDateHeure().toLocalDate().equals(today))
                .count();
        int semaine = (int) pending.stream()
                .filter(x -> x.getDateHeure() != null
                        && !x.getDateHeure().toLocalDate().isBefore(today)
                        && !x.getDateHeure().toLocalDate().isAfter(finSemaine))
                .count();
        if (medNotifTotalLabel != null) {
            medNotifTotalLabel.setText(String.valueOf(totalPending + histCount));
        }
        if (notifStatNonLuesLabel != null) {
            notifStatNonLuesLabel.setText(String.valueOf(nonLues));
        }
        if (notifStatDemandesLabel != null) {
            notifStatDemandesLabel.setText(String.valueOf(demandes));
        }
        if (notifStatAujourdhuiLabel != null) {
            notifStatAujourdhuiLabel.setText(String.valueOf(aujourdhui));
        }
        if (notifStatSemaineLabel != null) {
            notifStatSemaineLabel.setText(String.valueOf(semaine));
        }
        if (medNotifHistoryCountLabel != null) {
            if (totalPending == 0 && histCount == 0) {
                medNotifHistoryCountLabel.setText("Aucune notification");
            } else {
                medNotifHistoryCountLabel.setText(
                        totalPending + " en attente · " + histCount + " dans l’historique (base de données)");
            }
        }
        if (notifEmptyLabel != null) {
            notifEmptyLabel.setText("Aucune notification à afficher.");
        }
        boolean empty = totalPending == 0 && histCount == 0;
        if (notifEmptyLabel != null) {
            notifEmptyLabel.setVisible(empty);
            notifEmptyLabel.setManaged(empty);
        }
        if (notifListScroll != null) {
            notifListScroll.setVisible(!empty);
            notifListScroll.setManaged(!empty);
        }
        refreshMedecinPendingDemandesUi();
    }

    private VBox buildPendingDemandeNotifCard(Appointment a) {
        VBox card = new VBox(12);
        card.getStyleClass().add("med-notif-demand-card");
        if (a.isMedecinDemandeLue()) {
            card.getStyleClass().add("med-notif-demand-card-read");
        } else {
            card.getStyleClass().add("med-notif-demand-card-unread");
        }
        Label title = new Label("Demande de rendez-vous");
        title.getStyleClass().add("med-notif-demand-title");
        String patient = resolvePatientLabel(a.getPatientId());
        String when = a.getDateHeure() != null ? a.getDateHeure().format(NOTE_DATE) : "—";
        String motif = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "—";
        Label body = new Label(patient + "\n" + when + "\nMotif : " + motif);
        body.setWrapText(true);
        body.getStyleClass().add("med-notif-demand-body");
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button accept = new Button("Accepter");
        accept.getStyleClass().addAll("med-notif-btn-accept", "med-btn-primary");
        int apptId = a.getId();
        accept.setOnAction(ev -> onMedecinRdvDecision(apptId, true));
        Button refuse = new Button("Refuser");
        refuse.getStyleClass().add("med-notif-btn-refuse");
        refuse.setOnAction(ev -> onMedecinRdvDecision(apptId, false));
        actions.getChildren().addAll(accept, refuse);
        card.getChildren().addAll(title, body, actions);

        card.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> {
            if (ev.getButton() != MouseButton.PRIMARY) {
                return;
            }
            for (Node n = ev.getTarget() instanceof Node ? (Node) ev.getTarget() : null;
                 n != null;
                 n = n.getParent()) {
                if (n instanceof Button) {
                    return;
                }
            }
            if (!a.isMedecinDemandeLue()) {
                medecinMarkDemandeSeen(apptId);
            }
        });
        return card;
    }

    /** Carte lecture seule : décision déjà persistée dans {@code rendez_vous}. */
    private VBox buildHistoryDemandeNotifCard(Appointment a) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("med-notif-demand-card", "med-notif-history-card");
        boolean accepte = a.getStatus() == AppointmentStatus.PLANIFIE;
        Label title = new Label(accepte ? "✓ Demande acceptée" : "✗ Demande refusée");
        title.getStyleClass().add(accepte ? "med-notif-history-title-ok" : "med-notif-history-title-ko");
        String patient = resolvePatientLabel(a.getPatientId());
        String when = a.getDateHeure() != null ? a.getDateHeure().format(NOTE_DATE) : "—";
        String motif = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "—";
        Label body = new Label(patient + "\nCréneau : " + when + "\nMotif : " + motif);
        body.setWrapText(true);
        body.getStyleClass().add("med-notif-demand-body");
        Label hint = new Label("Enregistré dans votre base (historique conservé).");
        hint.getStyleClass().add("med-notif-history-hint");
        hint.setWrapText(true);
        card.getChildren().addAll(title, body, hint);
        return card;
    }

    private void medecinMarkDemandeSeen(int apptId) {
        try {
            appointmentService.markMedecinDemandeLue(apptId, currentDoctorId);
            refreshNotifications();
            refreshMedecinPendingDemandesUi();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Notifications",
                    ex.getMessage() != null ? ex.getMessage() : "Action impossible.");
        }
    }

    private void onMedecinRdvDecision(int apptId, boolean accept) {
        try {
            appointmentService.medecinRepondreDemande(apptId, currentDoctorId, accept);
            MedecinGcalSync gcal = MedecinGcalSync.SKIPPED;
            if (accept) {
                Optional<Appointment> fresh = appointmentService.findById(apptId);
                if (fresh.isPresent()) {
                    gcal = tryOpenMedecinGoogleCalendarForAccepted(fresh.get());
                }
            }
            refreshRdvFromDb();
            if (mainView == MainView.NOTIFS) {
                refreshNotifications();
            } else {
                refreshMedecinPendingDemandesUi();
            }
            String acceptMsg = "Le rendez-vous est confirmé et le patient est notifié.";
            if (accept) {
                if (gcal == MedecinGcalSync.API_CREATED) {
                    acceptMsg += " L’événement a été ajouté à votre Google Agenda.";
                } else if (gcal == MedecinGcalSync.TEMPLATE_BROWSER) {
                    acceptMsg += " Si une page Google Agenda s’est ouverte, enregistrez l’événement pour le conserver.";
                }
            }
            alert(Alert.AlertType.INFORMATION, "Rendez-vous",
                    accept ? acceptMsg : "La demande a été refusée. Le patient sera notifié.");
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Rendez-vous",
                    ex.getMessage() != null ? ex.getMessage() : "Action impossible.");
        }
    }

    private enum MedecinGcalSync {
        SKIPPED,
        /** Lien template ouvert dans le navigateur. */
        TEMPLATE_BROWSER,
        /** Événement créé via l’API (jeton connecté au profil). */
        API_CREATED
    }

    /**
     * Si le médecin a connecté Google Agenda (refresh token), crée l’événement via l’API ;
     * sinon ouvre le lien template dans le navigateur.
     */
    private MedecinGcalSync tryOpenMedecinGoogleCalendarForAccepted(Appointment appt) {
        if (appt == null || appt.getDateHeure() == null) {
            return MedecinGcalSync.SKIPPED;
        }
        try {
            LocalDateTime slotEnd = null;
            if (appt.getDisponibiliteId() > 0) {
                Optional<Availability> av = availabilityService.findById(appt.getDisponibiliteId());
                if (av.isPresent()) {
                    slotEnd = av.get().getFin();
                }
            }
            User med = userService.findById(currentDoctorId).orElse(null);
            String patientLabel = resolvePatientLabel(appt.getPatientId());
            String rt = med != null && med.getGoogleCalendarRefreshToken() != null
                    ? med.getGoogleCalendarRefreshToken().trim()
                    : "";
            if (!rt.isEmpty()) {
                if (GoogleCalendarApiService.tryCreateMedecinRdvEvent(rt, appt, slotEnd, med, patientLabel)) {
                    return MedecinGcalSync.API_CREATED;
                }
            }
            GoogleCalendarTemplateLinks.browseMedecinAddAfterAccept(appt, slotEnd, med, patientLabel);
            return MedecinGcalSync.TEMPLATE_BROWSER;
        } catch (Exception ignored) {
            return MedecinGcalSync.SKIPPED;
        }
    }

    @FXML
    private void onNavNotes() {
        setMainView(MainView.NOTES);
    }

    @FXML
    private void onNavRendezVous() {
        setMainView(MainView.RDV);
    }

    @FXML
    private void onNavNotifications() {
        setMainView(MainView.NOTIFS);
    }

    @FXML
    private void onNotificationsBell(@SuppressWarnings("unused") MouseEvent e) {
        setMainView(MainView.NOTIFS);
    }

    @FXML
    private void onQuickNewSlot() {
        setMainView(MainView.DISPO);
        showAvailabilityEditor(null);
    }

    @FXML
    private void onQuickNotes() {
        setMainView(MainView.NOTES);
    }

    @FXML
    private void onQuickAgenda() {
        setMainView(MainView.RDV);
    }

    @FXML
    private void onTopbarNameClick(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        onViewProfile();
    }

    @FXML
    private void onViewProfile() {
        try {
            URL url = MainApp.class.getResource("/fxml/medecin-my-profile.fxml");
            if (url == null) {
                alert(Alert.AlertType.ERROR, "Erreur", "Formulaire profil introuvable.");
                return;
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            MedecinMyProfileController ctrl = loader.getController();
            Stage owner = null;
            if (medHomeScroll != null && medHomeScroll.getScene() != null
                    && medHomeScroll.getScene().getWindow() instanceof Stage) {
                owner = (Stage) medHomeScroll.getScene().getWindow();
            } else if (searchField != null && searchField.getScene() != null
                    && searchField.getScene().getWindow() instanceof Stage) {
                owner = (Stage) searchField.getScene().getWindow();
            }
            Stage st = new Stage();
            if (owner != null) {
                st.initOwner(owner);
            }
            st.initModality(Modality.APPLICATION_MODAL);
            st.setTitle("Mon profil");
            Scene scene = new Scene(root, 780, 820);
            MainApp.applyThemeToScene(scene);
            st.setScene(scene);
            ctrl.setStage(st);
            ctrl.setOnProfileSaved(() -> {
                User u = AppState.getCurrentUser();
                if (u != null) {
                    applyUser(u);
                }
            });
            st.showAndWait();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static void comingSoon(String feature) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("AutiCare Médecin");
        a.setHeaderText(feature);
        a.setContentText("Cette section sera branchée sur vos données prochainement.");
        a.showAndWait();
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg != null && !msg.isBlank() ? msg : "Erreur sans détail.");
        a.showAndWait();
    }

    private static String errorMessageOrDefault(Throwable ex, String fallback) {
        if (ex == null) {
            return fallback;
        }
        String m = ex.getMessage();
        if (m != null && !m.isBlank()) {
            return m;
        }
        return fallback + " (" + ex.getClass().getSimpleName() + ")";
    }

    /** Entrée du combo patient (patients ayant au moins un RDV avec ce médecin). */
    public static final class PatientNoteChoice {
        private final int patientId;
        private final String label;

        public PatientNoteChoice(int patientId, String label) {
            this.patientId = patientId;
            this.label = label != null ? label : ("Patient #" + patientId);
        }

        public int getPatientId() {
            return patientId;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum NoteKind {
        /** Champ {@code notes} d’un rendez-vous. */
        RENDEZ_VOUS,
        /** Ligne table {@code note} (notes libres médecin–patient). */
        NOTE_LIBRE
    }

    /** Ligne du tableau : note de rendez-vous ou note libre médecin–patient. */
    public static final class NoteRow {
        private final NoteKind kind;
        private final int entityId;
        private final int patientId;
        private final LocalDateTime sortTime;
        private final String patient;
        private final String contenu;
        private final String dateLabel;
        /** Texte complet {@code rendez_vous.notes} pour les lignes RDV ; {@code null} pour note libre. */
        private final String rawRdvNotes;

        public NoteRow(
                NoteKind kind,
                int entityId,
                int patientId,
                String patient,
                String contenu,
                String dateLabel,
                LocalDateTime sortTime,
                String rawRdvNotes) {
            this.kind = kind;
            this.entityId = entityId;
            this.patientId = patientId;
            this.patient = patient;
            this.contenu = contenu;
            this.dateLabel = dateLabel;
            this.sortTime = sortTime;
            this.rawRdvNotes = rawRdvNotes;
        }

        public NoteKind getKind() {
            return kind;
        }

        /** Id du RDV ou id de ligne dans la table {@code note}. */
        public int getEntityId() {
            return entityId;
        }

        public int getPatientId() {
            return patientId;
        }

        public LocalDateTime getSortTime() {
            return sortTime;
        }

        public String getPatient() {
            return patient;
        }

        public String getContenu() {
            return contenu;
        }

        public String getDateLabel() {
            return dateLabel;
        }

        public String getRawRdvNotes() {
            return rawRdvNotes;
        }
    }
}
