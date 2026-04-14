package org.example.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.MainApp;
import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.Availability;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.AppointmentService;
import org.example.services.AvailabilityService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.UserAvatarGraphic;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private VBox rdvEmptyState;
    @FXML
    private ScrollPane rdvListScroll;
    @FXML
    private VBox rdvListContainer;
    @FXML
    private Label medNotesTotalLabel;
    @FXML
    private Label medNotesNewHint;
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
    private TextField dispoSlotSearchField;
    @FXML
    private Label dispoMonthLabel;
    @FXML
    private GridPane dispoCalendarGrid;

    private final AvailabilityService availabilityService = new AvailabilityService();
    private final AppointmentService appointmentService = new AppointmentService();
    private final UserService userService = new UserService();
    private int currentDoctorId;
    private YearMonth dispoMonth = YearMonth.from(LocalDate.now());
    private LocalDate selectedDispoDate = LocalDate.now();
    private List<Availability> cachedAvailabilities = new ArrayList<>();
    private final List<Appointment> rdvAppointments = new ArrayList<>();
    private MainView mainView = MainView.HOME;

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
        applyUser(u);
        if (medTodayDateLabel != null) {
            medTodayDateLabel.setText(LocalDate.now().format(
                    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)));
        }
        if (medTodayRdvLabel != null) {
            medTodayRdvLabel.setText("0 rendez-vous prévus");
        }
        if (statPatientsLabel != null) {
            statPatientsLabel.setText("0");
        }
        if (statRdvLabel != null) {
            statRdvLabel.setText("0");
        }
        if (statNotesLabel != null) {
            statNotesLabel.setText("0");
        }
        if (statDispoLabel != null) {
            statDispoLabel.setText("Actif");
        }
        setMainView(MainView.HOME);
        if (dispoSlotSearchField != null) {
            dispoSlotSearchField.textProperty().addListener((o, a, b) -> {
                if (mainView == MainView.DISPO) {
                    rebuildCalendarGrid();
                }
            });
        }
        setupNotesTable();
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
        }
        if (notes) {
            refreshNotesFromDb();
        }
        if (rdv) {
            refreshRdvFromDb();
        }
        if (notifs) {
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
    }

    @FXML
    private void onNouvelleDisponibilite() {
        showAvailabilityEditor(null);
    }

    private void rebuildCalendarGrid() {
        if (dispoCalendarGrid == null) {
            return;
        }
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
        return formatSlotSummary(a).toLowerCase(Locale.ROOT).contains(q.trim().toLowerCase(Locale.ROOT));
    }

    private static String formatSlotSummary(Availability a) {
        long min = ChronoUnit.MINUTES.between(a.getDebut(), a.getFin());
        return a.getDebut().format(TIME_FMT) + " " + a.getFin().format(TIME_FMT) + " " + min + " min";
    }

    private VBox buildSlotCard(Availability a) {
        VBox card = new VBox(4);
        card.getStyleClass().add("med-slot-card");
        card.setOnMouseClicked(MouseEvent::consume);

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
        del.setOnAction(ev -> {
            ev.consume();
            confirmDelete(a);
        });
        actions.getChildren().addAll(edit, del);

        card.getChildren().addAll(time, dur, actions);
        return card;
    }

    private void confirmDelete(Availability a) {
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
            st.setScene(new Scene(root));
            ctrl.setStage(st);
            ctrl.prepare(existing, selectedDispoDate, (debut, fin) -> {
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
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
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
        try {
            MainApp.showHome();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
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

        TableColumn<NoteRow, String> colPatient = new TableColumn<>("Patient");
        colPatient.setCellValueFactory(new PropertyValueFactory<>("patient"));
        colPatient.setPrefWidth(160);

        TableColumn<NoteRow, String> colContenu = new TableColumn<>("Contenu");
        colContenu.setCellValueFactory(new PropertyValueFactory<>("contenu"));
        colContenu.setPrefWidth(380);

        TableColumn<NoteRow, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(new PropertyValueFactory<>("dateLabel"));
        colDate.setPrefWidth(150);

        TableColumn<NoteRow, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(88);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Voir");

            {
                btn.getStyleClass().add("med-notes-mini-btn");
                btn.setOnAction(ev -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        NoteRow row = getTableView().getItems().get(idx);
                        if (row != null) {
                            showNoteDetail(row);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        notesTableView.getColumns().setAll(colPatient, colContenu, colDate, colActions);
    }

    private void refreshNotesFromDb() {
        if (notesTableView == null) {
            return;
        }
        try {
            List<Appointment> appts = appointmentService.findByMedecin(currentDoctorId);
            Set<Integer> patientIds = new HashSet<>();
            for (Appointment a : appts) {
                patientIds.add(a.getPatientId());
            }
            if (medNotesNewHint != null) {
                if (patientIds.isEmpty()) {
                    medNotesNewHint.setText(
                            "Aucun patient pour l'instant. Les patients apparaîtront après un premier rendez-vous.");
                } else {
                    medNotesNewHint.setText(
                            "Les notes ci-dessous proviennent du champ « Notes » de vos rendez-vous enregistrés.");
                }
            }
            List<NoteRow> rows = new ArrayList<>();
            for (Appointment a : appts) {
                String n = a.getNotes();
                if (n == null || n.isBlank()) {
                    continue;
                }
                String patientLabel = "Patient #" + a.getPatientId();
                var pu = userService.findById(a.getPatientId());
                if (pu.isPresent()) {
                    User p = pu.get();
                    String pn = ((p.getPrenom() != null ? p.getPrenom().trim() : "") + " "
                            + (p.getNom() != null ? p.getNom().trim() : "")).trim();
                    if (!pn.isBlank()) {
                        patientLabel = pn;
                    }
                }
                rows.add(new NoteRow(
                        a.getId(),
                        patientLabel,
                        n.strip(),
                        a.getDateHeure().format(NOTE_DATE)));
            }
            notesTableView.setItems(FXCollections.observableArrayList(rows));
            if (medNotesTotalLabel != null) {
                medNotesTotalLabel.setText(String.valueOf(rows.size()));
            }
            if (statNotesLabel != null) {
                statNotesLabel.setText(String.valueOf(rows.size()));
            }
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Notes", "Impossible de charger les données : " + e.getMessage());
            notesTableView.setItems(FXCollections.observableArrayList());
            if (medNotesTotalLabel != null) {
                medNotesTotalLabel.setText("0");
            }
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
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Note patient");
        a.setHeaderText(row.getPatient() + " — " + row.getDateLabel());
        a.setContentText(row.getContenu());
        a.getDialogPane().setPrefWidth(520);
        a.setResizable(true);
        a.showAndWait();
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
        if (statRdvLabel != null) {
            statRdvLabel.setText(String.valueOf(rdvAppointments.size()));
        }
    }

    private void updateRdvStats() {
        LocalDate today = LocalDate.now();
        long termines = rdvAppointments.stream().filter(a -> a.getStatus() == AppointmentStatus.TERMINE).count();
        long planifies = rdvAppointments.stream().filter(a -> a.getStatus() == AppointmentStatus.PLANIFIE).count();
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
            rdvStatConfirmesLabel.setText(String.valueOf(termines));
        }
        if (rdvStatAttenteLabel != null) {
            rdvStatAttenteLabel.setText(String.valueOf(planifies));
        }
        if (rdvStatAnnulesLabel != null) {
            rdvStatAnnulesLabel.setText(String.valueOf(annules));
        }
        if (rdvStatAujourdhuiLabel != null) {
            rdvStatAujourdhuiLabel.setText(String.valueOf(aujourdhui));
        }
    }

    private void rebuildRdvList() {
        if (rdvListContainer == null || rdvEmptyState == null || rdvListScroll == null) {
            return;
        }
        rdvListContainer.getChildren().clear();
        List<Appointment> sorted = new ArrayList<>(rdvAppointments);
        String sortChoice = rdvSortCombo != null && rdvSortCombo.getValue() != null
                ? rdvSortCombo.getValue()
                : "";
        Comparator<Appointment> cmp = Comparator.comparing(Appointment::getDateHeure, Comparator.nullsLast(Comparator.naturalOrder()));
        if (sortChoice.contains("récent — ancien")) {
            cmp = cmp.reversed();
        }
        sorted.sort(cmp);
        boolean empty = sorted.isEmpty();
        rdvEmptyState.setVisible(empty);
        rdvEmptyState.setManaged(empty);
        rdvListScroll.setVisible(!empty);
        rdvListScroll.setManaged(!empty);
        if (empty) {
            return;
        }
        for (Appointment a : sorted) {
            String patientLabel = resolvePatientLabel(a.getPatientId());
            rdvListContainer.getChildren().add(buildRdvCard(a, patientLabel));
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

    private VBox buildRdvCard(Appointment a, String patientLabel) {
        VBox card = new VBox(8);
        card.getStyleClass().add("med-rdv-item-card");
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(patientLabel);
        name.getStyleClass().add("med-rdv-item-patient");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label(statusLabelFr(a.getStatus()));
        badge.getStyleClass().addAll("med-rdv-badge", badgeStyleForStatus(a.getStatus()));
        top.getChildren().addAll(name, spacer, badge);
        Label when = new Label(a.getDateHeure() != null ? a.getDateHeure().format(NOTE_DATE) : "—");
        when.getStyleClass().add("med-rdv-item-date");
        card.getChildren().addAll(top, when);
        String m = a.getMotif();
        if (m != null && !m.isBlank()) {
            Label motif = new Label(m);
            motif.setWrapText(true);
            motif.getStyleClass().add("med-rdv-item-motif");
            card.getChildren().add(motif);
        }
        return card;
    }

    private static String statusLabelFr(AppointmentStatus s) {
        if (s == null) {
            return "—";
        }
        return switch (s) {
            case PLANIFIE -> "Planifié";
            case ANNULE -> "Annulé";
            case TERMINE -> "Terminé";
        };
    }

    private static String badgeStyleForStatus(AppointmentStatus s) {
        if (s == null) {
            return "med-rdv-badge-wait";
        }
        return switch (s) {
            case TERMINE -> "med-rdv-badge-ok";
            case ANNULE -> "med-rdv-badge-cancel";
            case PLANIFIE -> "med-rdv-badge-wait";
        };
    }

    /**
     * Rafraîchit la page notifications. Sans table dédiée en base, l'historique reste vide
     * et les compteurs à zéro jusqu'à branchement d'un {@code NotificationService}.
     */
    private void refreshNotifications() {
        if (notifListContainer != null) {
            notifListContainer.getChildren().clear();
        }
        int total = 0;
        int nonLues = 0;
        int demandes = 0;
        int aujourdhui = 0;
        int semaine = 0;
        if (medNotifTotalLabel != null) {
            medNotifTotalLabel.setText(String.valueOf(total));
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
            medNotifHistoryCountLabel.setText(total == 1 ? "1 notification" : total + " notifications");
        }
        boolean empty = total == 0;
        if (notifEmptyLabel != null) {
            notifEmptyLabel.setVisible(empty);
            notifEmptyLabel.setManaged(empty);
        }
        if (notifListScroll != null) {
            notifListScroll.setVisible(!empty);
            notifListScroll.setManaged(!empty);
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
    private void onViewProfile() {
        comingSoon("Profil médecin");
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
        a.setContentText(msg);
        a.showAndWait();
    }

    /** Ligne du tableau des notes (issue d'un rendez-vous avec champ {@code notes} renseigné). */
    public static final class NoteRow {
        private final int appointmentId;
        private final String patient;
        private final String contenu;
        private final String dateLabel;

        public NoteRow(int appointmentId, String patient, String contenu, String dateLabel) {
            this.appointmentId = appointmentId;
            this.patient = patient;
            this.contenu = contenu;
            this.dateLabel = dateLabel;
        }

        public int getAppointmentId() {
            return appointmentId;
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
    }
}
