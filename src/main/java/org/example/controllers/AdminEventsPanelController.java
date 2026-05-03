package org.example.controllers;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.models.Event;
import org.example.models.EventRegistration;
import org.example.models.EventMessage;
import org.example.models.EventStatus;
import org.example.models.RegistrationStatus;
import org.example.models.Thematique;
import org.example.models.User;
import org.example.models.Role;
import org.example.MainApp;
import org.example.services.EventRegistrationService;
import org.example.services.EventMessageService;
import org.example.services.EventService;
import org.example.services.ThematiqueService;
import org.example.services.UserService;
import org.example.services.UserNotificationService;
import org.example.utils.AppState;
import org.example.utils.FxInputConstraints;

import javafx.application.Platform;
import javafx.stage.FileChooser;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Panneau « Gestion des événements » pour l’admin (sidebar), sans le {@code TabPane} du dashboard.
 */
public class AdminEventsPanelController {

    /** Aligné sur la colonne SQL {@code evenements.titre}. */
    private static final int MAX_TITRE = 180;
    private static final int MIN_TITRE_LEN = 2;
    /** Limite raisonnable pour {@code description} (TEXT côté SQL). */
    private static final int MAX_DESCRIPTION = 8000;
    /** Description obligatoire : longueur minimale après suppression des espaces de bord. */
    private static final int MIN_DESCRIPTION_LEN = 10;
    /** Durée minimale entre début et fin (même jour). */
    private static final int MIN_EVENT_DURATION_MINUTES = 15;
    private static final int MIN_LIEU_LEN = 2;
    private static final int MIN_THEMATIQUE_LEN = 2;
    /** Aligné sur {@code lieu}. */
    private static final int MAX_LIEU = 180;
    /** Aligné sur {@code thematique}. */
    private static final int MAX_THEMATIQUE = 120;
    /** Aligné sur {@code lien_google_maps} / {@code lien_zoom_visio}. */
    private static final int MAX_URL = 512;

    /**
     * Titre événement : lettres (accents), espaces, apostrophe, tiret — pas de chiffres (aligné thématique nom / sous-titre).
     */
    private static final Pattern EVENT_TITRE_LETTRES_UNIQUEMENT =
            Pattern.compile("^[\\p{L}\\p{M}\\s'’\\-]+$");

    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortOrderBox;

    @FXML private VBox eventsListLayer;
    @FXML private ScrollPane eventsScrollRoot;
    @FXML private StackPane eventsStackPane;
    @FXML private AnchorPane newEventFormHost;
    @FXML private VBox newEventFormLayer;
    @FXML private VBox eventDetailLayer;

    @FXML private Label formPageTitle;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailMetaLabel;
    @FXML private Label detailDescriptionLabel;
    @FXML private Label detailParticipantsHeader;
    @FXML private Label detailParticipantsBody;
    @FXML private Label detailDiscussionBody;
    @FXML private Label detailUnreadBadgeLabel;
    @FXML private VBox detailParticipantsCard;
    @FXML private VBox detailParticipantsTableBox;
    @FXML private VBox detailDiscussionCard;
    @FXML private VBox detailConversationsListBox;
    @FXML private VBox detailConversationPlaceholderBox;
    @FXML private VBox detailConversationPanelBox;
    @FXML private Label detailSelectedConversationTitle;
    @FXML private Label detailSelectedConversationSub;
    @FXML private VBox detailConversationMessagesBox;
    @FXML private TextArea detailReplyArea;

    @FXML private TableView<Event> adminEventsTable;

    @FXML private PieChart piePeriodChart;
    @FXML private PieChart pieRegsChart;
    @FXML private Label statBadgePeriodVenir;
    @FXML private Label statBadgePeriodPasses;
    @FXML private Label statBadgeInscAccept;
    @FXML private Label statBadgeInscAttente;
    @FXML private Label statBadgeInscRefus;
    @FXML private Label statBigTotal;
    @FXML private Label statBigVenir;
    @FXML private Label statBigInscTotal;

    @FXML private TextField formTitre;
    @FXML private TextArea formDescription;
    @FXML private DatePicker formDate;
    @FXML private Spinner<Integer> formHeureDebutHeure;
    @FXML private Spinner<Integer> formHeureDebutMin;
    @FXML private ComboBox<String> formHeureDebutAmPm;
    @FXML private Spinner<Integer> formHeureFinHeure;
    @FXML private Spinner<Integer> formHeureFinMin;
    @FXML private ComboBox<String> formHeureFinAmPm;
    @FXML private ComboBox<String> formMode;
    @FXML private VBox sectionLieuPhysique;
    @FXML private VBox sectionVisio;
    @FXML private TextField formLieu;
    @FXML private TextField formLienMaps;
    @FXML private TextField formLienZoom;
    @FXML private TextField formLat;
    @FXML private TextField formLng;
    @FXML private ComboBox<String> formThematique;

    private final EventService eventService = new EventService();
    private final EventRegistrationService registrationService = new EventRegistrationService();
    private final EventMessageService eventMessageService = new EventMessageService();
    private final ThematiqueService thematiqueService = new ThematiqueService();
    private final UserService userService = new UserService();
    private final UserNotificationService userNotificationService = new UserNotificationService();

    /** Données brutes (avant filtre / tri affiché). */
    private final ObservableList<Event> masterEvents = FXCollections.observableArrayList();

    /** Événement affiché dans la fiche « Voir » (édition / rappels). */
    private Event detailShownEvent;
    /** Si non nul, le formulaire enregistre une mise à jour au lieu d’un insert. */
    private Integer editingEventId;
    private final Map<Integer, Integer> messageCountByEventId = new HashMap<>();
    private Integer selectedConversationUserId;
    private final List<Integer> currentConversationParticipantIds = new ArrayList<>();

    @FXML
    public void initialize() {
        sortOrderBox.setItems(FXCollections.observableArrayList("Croissant", "Décroissant"));
        sortOrderBox.getSelectionModel().selectFirst();
        sortOrderBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilterAndSort());

        formMode.setItems(FXCollections.observableArrayList("Présentiel", "En ligne", "Hybride"));
        formMode.getSelectionModel().selectFirst();

        refreshThematiqueComboFromDb();

        setupTimePickers();
        formMode.valueProperty().addListener((obs, o, n) -> updateModeSections());

        setupAdminEventsUi();
        EventStatsPieCharts.configure(piePeriodChart, true);
        EventStatsPieCharts.configure(pieRegsChart, false);
        refreshEventsFromDb();
        updateModeSections();
        setupNewEventFormInputConstraints();
        wireScrollContentFullWidth();
    }

    /** Le contenu du ScrollPane gardait une largeur préférée étroite : on l’aligne sur toute la zone utile. */
    private void wireScrollContentFullWidth() {
        if (eventsScrollRoot != null && eventsStackPane != null) {
            eventsStackPane.minWidthProperty().bind(eventsScrollRoot.widthProperty());
            eventsStackPane.prefWidthProperty().bind(eventsScrollRoot.widthProperty());
        }
        if (newEventFormHost != null && eventsStackPane != null) {
            newEventFormHost.minWidthProperty().bind(eventsStackPane.widthProperty());
            newEventFormHost.prefWidthProperty().bind(eventsStackPane.widthProperty());
        }
        if (newEventFormLayer != null && newEventFormHost != null) {
            final double formMax = 1320.0;
            final double margin = 48.0;
            var formW = Bindings.createDoubleBinding(
                    () -> {
                        double hw = newEventFormHost.getWidth();
                        if (hw <= 0) {
                            return 680.0;
                        }
                        return Math.max(680.0, Math.min(formMax, hw - margin));
                    },
                    newEventFormHost.widthProperty());
            newEventFormLayer.prefWidthProperty().bind(formW);
            newEventFormLayer.maxWidthProperty().bind(formW);
            newEventFormLayer.setMinWidth(680);
        }
    }

    /** Limites de saisie alignées sur {@link #validateAndFillNewEvent(org.example.models.Event)}. */
    private void setupNewEventFormInputConstraints() {
        if (formTitre != null) {
            formTitre.setTextFormatter(FxInputConstraints.maxLength(MAX_TITRE));
        }
        if (formDescription != null) {
            formDescription.setTextFormatter(FxInputConstraints.maxLength(MAX_DESCRIPTION));
        }
        if (formLieu != null) {
            formLieu.setTextFormatter(FxInputConstraints.maxLength(MAX_LIEU));
        }
        if (formLienMaps != null) {
            formLienMaps.setTextFormatter(FxInputConstraints.maxLength(MAX_URL));
        }
        if (formLienZoom != null) {
            formLienZoom.setTextFormatter(FxInputConstraints.maxLength(MAX_URL));
        }
        if (formLat != null) {
            formLat.setTextFormatter(FxInputConstraints.maxLength(20));
        }
        if (formLng != null) {
            formLng.setTextFormatter(FxInputConstraints.maxLength(20));
        }
        setupFormDateNoPast();
        Platform.runLater(() -> {
            if (formThematique != null && formThematique.getEditor() != null) {
                formThematique.getEditor().setTextFormatter(FxInputConstraints.maxLength(MAX_THEMATIQUE));
            }
        });
    }

    /** Calendrier : seuls le jour courant et les jours futurs sont sélectionnables. */
    private void setupFormDateNoPast() {
        if (formDate == null) {
            return;
        }
        formDate.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                LocalDate today = LocalDate.now();
                setDisable(empty || item.isBefore(today));
            }
        });
    }

    private void setupTimePickers() {
        if (formHeureDebutHeure != null) {
            formHeureDebutHeure.setValueFactory(new IntegerSpinnerValueFactory(1, 12, 9));
        }
        if (formHeureDebutMin != null) {
            formHeureDebutMin.setValueFactory(new IntegerSpinnerValueFactory(0, 59, 0));
        }
        if (formHeureFinHeure != null) {
            formHeureFinHeure.setValueFactory(new IntegerSpinnerValueFactory(1, 12, 5));
        }
        if (formHeureFinMin != null) {
            formHeureFinMin.setValueFactory(new IntegerSpinnerValueFactory(0, 59, 0));
        }
        ObservableList<String> ampm = FXCollections.observableArrayList("AM", "PM");
        if (formHeureDebutAmPm != null) {
            formHeureDebutAmPm.setItems(ampm);
            formHeureDebutAmPm.setVisibleRowCount(2);
            formHeureDebutAmPm.getSelectionModel().select("AM");
        }
        if (formHeureFinAmPm != null) {
            formHeureFinAmPm.setItems(FXCollections.observableArrayList("AM", "PM"));
            formHeureFinAmPm.setVisibleRowCount(2);
            formHeureFinAmPm.getSelectionModel().select("PM");
        }
    }

    /** Remplit la liste « Thématique » avec les fiches créées en base (écran Gestion des thématiques). */
    private void refreshThematiqueComboFromDb() {
        if (formThematique == null) {
            return;
        }
        try {
            List<Thematique> list = thematiqueService.findAll();
            List<String> noms = new ArrayList<>();
            for (Thematique t : list) {
                if (t.getNom() != null && !t.getNom().isBlank()) {
                    noms.add(t.getNom().trim());
                }
            }
            formThematique.setItems(FXCollections.observableArrayList(noms));
        } catch (SQLException e) {
            showError(e);
        }
    }

    private void updateModeSections() {
        String mode = formMode == null ? null : formMode.getValue();
        boolean lieu = "Présentiel".equals(mode) || "Hybride".equals(mode);
        boolean visio = "En ligne".equals(mode) || "Hybride".equals(mode);
        setSectionVisible(sectionLieuPhysique, lieu);
        setSectionVisible(sectionVisio, visio);
    }

    private static void setSectionVisible(VBox section, boolean visible) {
        if (section == null) {
            return;
        }
        section.setVisible(visible);
        section.setManaged(visible);
    }

    /** Recharge la table depuis la base (appelé à l’affichage depuis la sidebar). */
    public void refreshEventsFromDb() {
        try {
            masterEvents.setAll(eventService.findAll());
            reloadMessageCounts();
            applyFilterAndSort();
            updateAllStats();
        } catch (Exception e) {
            showError(e);
        }
    }

    private void reloadMessageCounts() {
        messageCountByEventId.clear();
        if (masterEvents.isEmpty()) {
            return;
        }
        List<Integer> ids = masterEvents.stream().map(Event::getId).toList();
        try {
            messageCountByEventId.putAll(eventMessageService.countByEventIds(ids));
        } catch (Exception ignored) {
            // En cas d'erreur SQL, on garde simplement 0 message affiché.
        }
    }

    @FXML
    public void onNewEvent() {
        editingEventId = null;
        if (formPageTitle != null) {
            formPageTitle.setText("Nouvel événement");
        }
        refreshThematiqueComboFromDb();
        clearNewEventForm();
        showNewEventForm();
    }

    /**
     * Ouvre le formulaire « Nouvel événement » avec la thématique présélectionnée (appel depuis la fiche thématique admin).
     */
    public void openNewEventWithThematiqueNom(String nomThematique) {
        refreshEventsFromDb();
        onNewEvent();
        if (nomThematique == null || nomThematique.isBlank() || formThematique == null) {
            return;
        }
        String n = nomThematique.trim();
        if (!formThematique.getItems().contains(n)) {
            formThematique.getItems().add(n);
        }
        formThematique.getSelectionModel().select(n);
    }

    @FXML
    public void onCloseNewEventForm() {
        hideNewEventForm();
    }

    @FXML
    public void onSaveNewEvent() {
        try {
            Event e = new Event();
            String err = validateAndFillNewEvent(e);
            if (err != null) {
                showValidationMessage(err);
                return;
            }

            if (editingEventId != null) {
                e.setId(editingEventId);
                Event old = eventService.findById(editingEventId).orElseThrow();
                e.setPlacesMax(old.getPlacesMax());
                e.setStatut(old.getStatut());
                eventService.update(e);
            } else {
                eventService.add(e);
            }
            editingEventId = null;
            hideNewEventForm();
            refreshEventsFromDb();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    @FXML
    public void onCloseEventDetail() {
        hideEventDetailToList();
    }

    @FXML
    public void onDetailEdit() {
        if (detailShownEvent != null) {
            openEditEvent(detailShownEvent);
        }
    }

    @FXML
    public void onDetailSendReminders() {
        showInfo("Rappels", "Envoi des rappels par e-mail : branchez votre service SMTP / liste d’inscrits.");
    }

    @FXML
    public void onDetailExportParticipantsPdf() {
        if (detailShownEvent == null) {
            showInfo("PDF", "Aucun événement sélectionné.");
            return;
        }
        try {
            List<EventRegistration> regs = registrationService.listParticipants(detailShownEvent.getId());
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter la liste des participants");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("participants-evenement-" + detailShownEvent.getId() + ".pdf");
            var window = eventDetailLayer != null && eventDetailLayer.getScene() != null ? eventDetailLayer.getScene().getWindow() : null;
            var file = chooser.showSaveDialog(window);
            if (file == null) {
                return;
            }
            writeSimpleParticipantsPdf(file.toPath(), regs);
            showInfo("PDF", "Export terminé : " + file.getName());
        } catch (Exception ex) {
            showError(ex);
        }
    }

    @FXML
    public void onDetailSendReply() {
        if (detailShownEvent == null || selectedConversationUserId == null || selectedConversationUserId <= 0) {
            showInfo("Discussion", "Sélectionnez d'abord une conversation.");
            return;
        }
        User me = AppState.getCurrentUser();
        if (me == null) {
            showInfo("Discussion", "Session expirée. Reconnectez-vous.");
            return;
        }
        String text = detailReplyArea != null && detailReplyArea.getText() != null ? detailReplyArea.getText().trim() : "";
        if (text.isBlank()) {
            showInfo("Discussion", "Saisissez une réponse avant l'envoi.");
            return;
        }
        try {
            eventMessageService.addMessage(detailShownEvent.getId(), me.getId(), selectedConversationUserId, text);
            userNotificationService.addNotification(
                    selectedConversationUserId,
                    UserNotificationService.TYPE_EVENT_MESSAGE_REPLY,
                    detailShownEvent.getId(),
                    "L'équipe a répondu à votre message pour « " + detailShownEvent.getTitre() + " ».");
            if (detailReplyArea != null) {
                detailReplyArea.clear();
            }
            selectConversation(detailShownEvent.getId(), selectedConversationUserId);
            reloadMessageCounts();
            applyFilterAndSort();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    /** Aperçu public : même écran que les visiteurs (fiche complète : lieu, météo, inscription, etc.). */
    private void openPublicEventPreview(Event rowEvent) {
        if (rowEvent == null) {
            return;
        }
        try {
            Event ev = eventService.findById(rowEvent.getId()).orElse(rowEvent);
            AppState.setPendingPublicEventDetailId(ev.getId());
            MainApp.showPublicPage("event-detail");
        } catch (Exception ex) {
            showError(ex);
        }
    }

    /** Fiche back-office (participants, rappels) — aussi accessible par double-clic sur une ligne. */
    private void openAdminEventDetail(Event rowEvent) {
        openAdminEventDetail(rowEvent, false);
    }

    private void openAdminEventDetail(Event rowEvent, boolean focusDiscussion) {
        if (rowEvent == null) {
            return;
        }
        try {
            Event ev = eventService.findById(rowEvent.getId()).orElse(rowEvent);
            detailShownEvent = ev;
            bindEventDetailView(ev);
            showEventDetailLayer();
            if (focusDiscussion) {
                focusDiscussionBlock();
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    public void openEventDetailFromNotification(int eventId, boolean focusDiscussion) {
        try {
            Event ev = eventService.findById(eventId).orElse(null);
            if (ev == null) {
                return;
            }
            openAdminEventDetail(ev, focusDiscussion);
            if (!focusDiscussion) {
                Platform.runLater(() -> {
                    if (eventsScrollRoot != null) {
                        eventsScrollRoot.setVvalue(0.55);
                    }
                    if (detailParticipantsCard != null) {
                        detailParticipantsCard.requestFocus();
                    }
                });
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void focusDiscussionBlock() {
        Platform.runLater(() -> {
            if (eventsScrollRoot != null) {
                eventsScrollRoot.setVvalue(1.0);
            }
            if (detailDiscussionCard != null) {
                detailDiscussionCard.requestFocus();
            }
        });
    }

    private void bindEventDetailView(Event e) {
        if (detailTitleLabel != null) {
            detailTitleLabel.setText(e.getTitre() != null ? e.getTitre() : "—");
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);
        String datePart = df.format(e.getDateDebut().toLocalDate());
        String timeRange = tf.format(e.getDateDebut()) + " - " + tf.format(e.getDateFin());
        String lieuLine;
        if (e.getLieu() != null && !e.getLieu().isBlank()) {
            lieuLine = e.getLieu();
        } else if ("En ligne".equals(e.getModeEvenement())) {
            lieuLine = "En ligne";
        } else {
            lieuLine = "—";
        }
        if (detailMetaLabel != null) {
            detailMetaLabel.setText(datePart + " " + timeRange + " · " + lieuLine);
        }
        if (detailDescriptionLabel != null) {
            String d = e.getDescription();
            detailDescriptionLabel.setText(d != null && !d.isBlank() ? d : "—");
        }
        int n = 0;
        List<EventRegistration> regs = new ArrayList<>();
        try {
            regs = registrationService.listParticipants(e.getId());
            n = regs.size();
        } catch (Exception ignored) {
            // affichage 0
        }
        if (detailParticipantsHeader != null) {
            detailParticipantsHeader.setText("Participants (" + n + ")");
        }
        if (detailParticipantsBody != null) {
            if (n == 0) {
                detailParticipantsBody.setText("Aucun participant pour le moment");
            } else {
                detailParticipantsBody.setText(n + " inscription(s) pour cet événement.");
            }
        }
        rebuildParticipantsRows(regs);
        bindEventDiscussionView(e);
    }

    private void rebuildParticipantsRows(List<EventRegistration> regs) {
        if (detailParticipantsTableBox == null) {
            return;
        }
        detailParticipantsTableBox.getChildren().clear();
        if (regs == null || regs.isEmpty()) {
            return;
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
        for (EventRegistration r : regs) {
            User u = null;
            try {
                u = userService.findById(r.getUtilisateurId()).orElse(null);
            } catch (Exception ignored) {
            }
            String name = displayNameForUser(u);
            String email = u != null && u.getEmail() != null ? u.getEmail() : "—";
            String when = r.getDateInscription() != null ? r.getDateInscription().format(f) : "—";
            String status = switch (r.getStatut()) {
                case ACCEPTE -> "Acceptée";
                case EN_ATTENTE -> "En attente";
                case REFUSE -> "Refusée";
            };

            Label nameL = new Label(name);
            nameL.setMinWidth(190);
            nameL.setPrefWidth(190);
            nameL.setMaxWidth(190);
            Label emailL = new Label(email);
            emailL.setMinWidth(230);
            emailL.setPrefWidth(230);
            emailL.setMaxWidth(230);
            Label whenL = new Label(when);
            whenL.setMinWidth(180);
            whenL.setPrefWidth(180);
            whenL.setMaxWidth(180);
            Label statusL = new Label(status);
            statusL.setMinWidth(130);
            statusL.setPrefWidth(130);
            statusL.setMaxWidth(130);
            statusL.setAlignment(Pos.CENTER);
            statusL.getStyleClass().add("admin-event-participant-status");
            statusL.setStyle(statusBadgeStyle(r.getStatut()));

            Button acceptBtn = new Button("Accepter");
            acceptBtn.getStyleClass().add("admin-event-participant-accept");
            acceptBtn.setMinWidth(96);
            acceptBtn.setDisable(r.getStatut() == RegistrationStatus.ACCEPTE);
            acceptBtn.setOnAction(evt -> updateRegistrationStatus(r, RegistrationStatus.ACCEPTE));

            Button refuseBtn = new Button("Refuser");
            refuseBtn.getStyleClass().add("admin-event-participant-refuse");
            refuseBtn.setMinWidth(96);
            refuseBtn.setDisable(r.getStatut() == RegistrationStatus.REFUSE);
            refuseBtn.setOnAction(evt -> updateRegistrationStatus(r, RegistrationStatus.REFUSE));

            HBox actions = new HBox(8, acceptBtn, refuseBtn);
            actions.setMinWidth(220);
            actions.setPrefWidth(220);
            actions.setMaxWidth(220);
            actions.setAlignment(Pos.CENTER);
            HBox row = new HBox(8, nameL, emailL, whenL, statusL, actions);
            row.getStyleClass().add("admin-event-participants-row");
            row.setAlignment(Pos.CENTER_LEFT);
            detailParticipantsTableBox.getChildren().add(row);
        }
    }

    private String statusBadgeStyle(RegistrationStatus status) {
        return switch (status) {
            case ACCEPTE -> "-fx-background-color:#dcfce7;-fx-text-fill:#166534;-fx-background-radius:8;-fx-padding:4 10 4 10;";
            case EN_ATTENTE -> "-fx-background-color:#fef3c7;-fx-text-fill:#92400e;-fx-background-radius:8;-fx-padding:4 10 4 10;";
            case REFUSE -> "-fx-background-color:#fee2e2;-fx-text-fill:#991b1b;-fx-background-radius:8;-fx-padding:4 10 4 10;";
        };
    }

    private void updateRegistrationStatus(EventRegistration reg, RegistrationStatus status) {
        if (reg == null) {
            return;
        }
        try {
            registrationService.setStatus(reg.getId(), status);
            String title = detailShownEvent != null && detailShownEvent.getTitre() != null
                    ? detailShownEvent.getTitre()
                    : ("Événement #" + reg.getEvenementId());
            if (status == RegistrationStatus.ACCEPTE) {
                userNotificationService.addNotification(
                        reg.getUtilisateurId(),
                        UserNotificationService.TYPE_EVENT_REGISTRATION_ACCEPTED,
                        reg.getEvenementId(),
                        "Votre inscription à « " + title + " » a été acceptée.");
            } else if (status == RegistrationStatus.REFUSE) {
                userNotificationService.addNotification(
                        reg.getUtilisateurId(),
                        UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED,
                        reg.getEvenementId(),
                        "Votre inscription à « " + title + " » a été refusée.");
            }
            reloadMessageCounts();
            if (detailShownEvent != null) {
                bindEventDetailView(detailShownEvent);
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void writeSimpleParticipantsPdf(Path path, List<EventRegistration> regs) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("Liste des participants");
        if (detailShownEvent != null && detailShownEvent.getTitre() != null) {
            lines.add("Événement : " + detailShownEvent.getTitre());
        }
        lines.add("");
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
        if (regs != null) {
            for (EventRegistration r : regs) {
                User u = null;
                try {
                    u = userService.findById(r.getUtilisateurId()).orElse(null);
                } catch (Exception ignored) {
                }
                String name = displayNameForUser(u);
                String email = u != null && u.getEmail() != null ? u.getEmail() : "—";
                String when = r.getDateInscription() != null ? r.getDateInscription().format(f) : "—";
                lines.add("- " + name + " | " + email + " | " + when + " | " + r.getStatut());
            }
        }
        String text = String.join("\n", lines).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        byte[] content = buildVerySimplePdf(text);
        Files.write(path, content);
    }

    private byte[] buildVerySimplePdf(String text) {
        String stream = "BT /F1 12 Tf 50 780 Td (" + text.replace("\n", ") Tj T* (") + ") Tj ET";
        String obj1 = "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n";
        String obj2 = "2 0 obj << /Type /Pages /Count 1 /Kids [3 0 R] >> endobj\n";
        String obj3 = "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n";
        String obj4 = "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n";
        String obj5 = "5 0 obj << /Length " + stream.length() + " >> stream\n" + stream + "\nendstream endobj\n";
        String body = obj1 + obj2 + obj3 + obj4 + obj5;
        String pdf = "%PDF-1.4\n" + body + "xref\n0 6\n0000000000 65535 f \n"
                + "0000000010 00000 n \n0000000070 00000 n \n0000000132 00000 n \n0000000257 00000 n \n0000000327 00000 n \n"
                + "trailer << /Root 1 0 R /Size 6 >>\nstartxref\n420\n%%EOF";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }

    private void bindEventDiscussionView(Event e) {
        selectedConversationUserId = null;
        int msgCount = messageCountByEventId.getOrDefault(e.getId(), 0);
        if (detailUnreadBadgeLabel != null) {
            String t = msgCount + " message(s) non lu(s)";
            detailUnreadBadgeLabel.setText(t);
            detailUnreadBadgeLabel.setVisible(msgCount > 0);
            detailUnreadBadgeLabel.setManaged(msgCount > 0);
        }
        if (detailConversationsListBox != null) {
            detailConversationsListBox.getChildren().clear();
        }
        currentConversationParticipantIds.clear();
        showConversationPlaceholder("Sélectionnez une conversation dans la liste.");
        if (msgCount <= 0) {
            showConversationPlaceholder("Aucune discussion pour le moment.");
            return;
        }
        try {
            List<Integer> participantIds = eventMessageService.listParticipantIdsForEvent(e.getId());
            List<Integer> sortedParticipantIds = participantIds.stream().distinct().sorted().collect(Collectors.toList());
            if (sortedParticipantIds.isEmpty()) {
                showConversationPlaceholder("Aucune discussion pour le moment.");
                return;
            }
            currentConversationParticipantIds.addAll(sortedParticipantIds);
            renderConversationList(e.getId());
            if (!sortedParticipantIds.isEmpty()) {
                selectConversation(e.getId(), sortedParticipantIds.get(0));
            }
        } catch (Exception ex) {
            showConversationPlaceholder("Chargement des discussions impossible.");
        }
    }

    private void renderConversationList(int eventId) {
        if (detailConversationsListBox == null) {
            return;
        }
        detailConversationsListBox.getChildren().clear();
        for (Integer participantId : currentConversationParticipantIds) {
            addConversationCard(eventId, participantId);
        }
    }

    private void addConversationCard(int eventId, int participantId) {
        if (detailConversationsListBox == null) {
            return;
        }
        try {
            User user = userService.findById(participantId).orElse(null);
            String name = displayNameForUser(user);
            List<EventMessage> conv = eventMessageService.listConversationForEventAndParticipant(eventId, participantId);
            String when = conv.isEmpty() || conv.get(conv.size() - 1).getDateEnvoi() == null
                    ? ""
                    : conv.get(conv.size() - 1).getDateEnvoi().format(DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRENCH));
            Button cardBtn = new Button();
            cardBtn.setMaxWidth(Double.MAX_VALUE);
            cardBtn.getStyleClass().add("admin-event-detail-conv-item");
            if (selectedConversationUserId != null && selectedConversationUserId == participantId) {
                cardBtn.getStyleClass().add("admin-event-detail-conv-item-active");
            }
            String initials = name.isBlank() ? "?" : name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.FRENCH);
            Label avatar = new Label(initials);
            avatar.getStyleClass().add("admin-event-detail-conv-avatar");
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("admin-event-detail-conv-name");
            Label metaLabel = new Label("Dernier msg. " + when);
            metaLabel.getStyleClass().add("admin-event-detail-conv-meta");
            VBox textBox = new VBox(2, nameLabel, metaLabel);
            HBox cardContent = new HBox(12, avatar, textBox);
            cardContent.setAlignment(Pos.CENTER_LEFT);
            cardBtn.setGraphic(cardContent);
            cardBtn.setText("");
            cardBtn.setOnAction(evt -> selectConversation(eventId, participantId));
            cardBtn.setAlignment(Pos.CENTER_LEFT);
            detailConversationsListBox.getChildren().add(cardBtn);
        } catch (Exception ignored) {
            // Ignore la ligne défaillante et continue.
        }
    }

    private void selectConversation(int eventId, int participantId) {
        selectedConversationUserId = participantId;
        renderConversationList(eventId);
        try {
            User u = userService.findById(participantId).orElse(null);
            String name = displayNameForUser(u);
            if (detailSelectedConversationTitle != null) {
                detailSelectedConversationTitle.setText("Conversation avec " + name);
            }
            if (detailSelectedConversationSub != null) {
                detailSelectedConversationSub.setText(u != null && u.getEmail() != null ? u.getEmail() : "");
            }
            if (detailConversationMessagesBox != null) {
                detailConversationMessagesBox.getChildren().clear();
            }
            List<EventMessage> messages = eventMessageService.listConversationForEventAndParticipant(eventId, participantId);
            for (EventMessage msg : messages) {
                renderConversationMessageBubble(msg);
            }
            if (messages.isEmpty()) {
                showConversationPlaceholder("Aucun message dans cette conversation.");
            } else {
                if (detailConversationPlaceholderBox != null) {
                    detailConversationPlaceholderBox.setVisible(false);
                    detailConversationPlaceholderBox.setManaged(false);
                }
                if (detailConversationPanelBox != null) {
                    detailConversationPanelBox.setVisible(true);
                    detailConversationPanelBox.setManaged(true);
                }
            }
        } catch (Exception ex) {
            showConversationPlaceholder("Chargement de la conversation impossible.");
        }
    }

    private void renderConversationMessageBubble(EventMessage msg) {
        if (detailConversationMessagesBox == null || msg == null) {
            return;
        }
        User me = AppState.getCurrentUser();
        int meId = me != null ? me.getId() : -1;
        boolean mine = msg.getExpediteurUserId() == meId;
        VBox bubble = new VBox(4);
        bubble.getStyleClass().add(mine ? "admin-event-detail-msg-bubble-me" : "admin-event-detail-msg-bubble-them");
        Label body = new Label(msg.getCorps() != null ? msg.getCorps() : "");
        body.setWrapText(true);
        body.getStyleClass().add("admin-event-detail-msg-body");
        String who = mine ? "Admin" : "Participant";
        String when = msg.getDateEnvoi() != null
                ? msg.getDateEnvoi().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH))
                : "";
        Label meta = new Label(when + " · " + who);
        meta.getStyleClass().add("admin-event-detail-msg-meta");
        bubble.getChildren().addAll(body, meta);
        if (mine) {
            Button deleteBtn = new Button("Supprimer");
            deleteBtn.getStyleClass().add("admin-event-detail-msg-delete-btn");
            deleteBtn.setOnAction(evt -> onDeleteOwnAdminMessage(msg));
            bubble.getChildren().add(deleteBtn);
        }
        detailConversationMessagesBox.getChildren().add(bubble);
    }

    private void onDeleteOwnAdminMessage(EventMessage msg) {
        if (msg == null || detailShownEvent == null || selectedConversationUserId == null) {
            return;
        }
        User me = AppState.getCurrentUser();
        if (me == null || msg.getExpediteurUserId() != me.getId()) {
            showInfo("Suppression", "Vous ne pouvez supprimer que vos propres messages.");
            return;
        }
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Supprimer le message");
        conf.setHeaderText(null);
        conf.setContentText("Supprimer ce message définitivement ?");
        Optional<ButtonType> r = conf.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        try {
            boolean deleted = eventMessageService.deleteOwnMessage(msg.getId(), me.getId());
            if (!deleted) {
                showInfo("Suppression", "Ce message n'est plus disponible.");
            }
            selectConversation(detailShownEvent.getId(), selectedConversationUserId);
            reloadMessageCounts();
            applyFilterAndSort();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showConversationPlaceholder(String text) {
        if (detailDiscussionBody != null) {
            detailDiscussionBody.setText(text);
        }
        if (detailConversationPlaceholderBox != null) {
            detailConversationPlaceholderBox.setVisible(true);
            detailConversationPlaceholderBox.setManaged(true);
        }
        if (detailConversationPanelBox != null) {
            detailConversationPanelBox.setVisible(false);
            detailConversationPanelBox.setManaged(false);
        }
    }

    private static String displayNameForUser(User u) {
        if (u == null) {
            return "Compte membre";
        }
        String n = ((u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : "")).trim();
        if (!n.isBlank()) {
            return n;
        }
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            return u.getEmail();
        }
        return "Compte membre";
    }

    private void openEditEvent(Event ev) {
        if (ev == null) {
            return;
        }
        try {
            Event full = eventService.findById(ev.getId()).orElse(ev);
            editingEventId = full.getId();
            refreshThematiqueComboFromDb();
            clearNewEventForm();
            applyEventToForm(full);
            if (formPageTitle != null) {
                formPageTitle.setText("Modifier l'événement");
            }
            showNewEventForm();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void applyEventToForm(Event e) {
        if (formTitre != null) {
            formTitre.setText(e.getTitre() != null ? e.getTitre() : "");
        }
        if (formDescription != null) {
            formDescription.setText(e.getDescription() != null ? e.getDescription() : "");
        }
        if (formDate != null && e.getDateDebut() != null) {
            LocalDate d = e.getDateDebut().toLocalDate();
            LocalDate today = LocalDate.now();
            formDate.setValue(d.isBefore(today) ? today : d);
        }
        if (e.getDateDebut() != null) {
            setSpinnersFromLocalTime(formHeureDebutHeure, formHeureDebutMin, formHeureDebutAmPm, e.getDateDebut().toLocalTime());
        }
        if (e.getDateFin() != null) {
            setSpinnersFromLocalTime(formHeureFinHeure, formHeureFinMin, formHeureFinAmPm, e.getDateFin().toLocalTime());
        }
        if (formMode != null) {
            String mode = e.getModeEvenement();
            if (mode != null && formMode.getItems().contains(mode)) {
                formMode.getSelectionModel().select(mode);
            } else {
                formMode.getSelectionModel().selectFirst();
            }
        }
        if (formLieu != null) {
            formLieu.setText(evStr(e.getLieu()));
        }
        if (formLienMaps != null) {
            formLienMaps.setText(evStr(e.getLienGoogleMaps()));
        }
        if (formLat != null) {
            formLat.setText(e.getLatitude() != null ? String.valueOf(e.getLatitude()) : "");
        }
        if (formLng != null) {
            formLng.setText(e.getLongitude() != null ? String.valueOf(e.getLongitude()) : "");
        }
        if (formLienZoom != null) {
            formLienZoom.setText(evStr(e.getLienZoomVisio()));
        }
        if (formThematique != null) {
            String th = e.getThematique();
            if (th != null && !th.isBlank()) {
                if (formThematique.getItems().contains(th)) {
                    formThematique.getSelectionModel().select(th);
                } else {
                    formThematique.getSelectionModel().clearSelection();
                    if (formThematique.getEditor() != null) {
                        formThematique.getEditor().setText(th);
                    }
                }
            }
        }
        updateModeSections();
    }

    private static String evStr(String s) {
        return s != null ? s : "";
    }

    private static void setSpinnersFromLocalTime(
            Spinner<Integer> hSpin,
            Spinner<Integer> mSpin,
            ComboBox<String> amPm,
            LocalTime t) {
        if (hSpin == null || mSpin == null || amPm == null || t == null) {
            return;
        }
        int hour24 = t.getHour();
        int min = t.getMinute();
        boolean pm = hour24 >= 12;
        int h12 = hour24 % 12;
        if (h12 == 0) {
            h12 = 12;
        }
        ((IntegerSpinnerValueFactory) hSpin.getValueFactory()).setValue(h12);
        ((IntegerSpinnerValueFactory) mSpin.getValueFactory()).setValue(min);
        amPm.getSelectionModel().select(pm ? "PM" : "AM");
    }

    private void confirmDeleteEvent(Event ev) {
        if (ev == null) {
            return;
        }
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Supprimer l'événement");
        conf.setHeaderText(null);
        conf.setContentText("Supprimer définitivement « " + ev.getTitre() + " » ?");
        Optional<ButtonType> r = conf.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        try {
            eventService.delete(ev.getId());
            if (detailShownEvent != null && detailShownEvent.getId() == ev.getId()) {
                hideEventDetailToList();
            }
            refreshEventsFromDb();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showLayerEventsListOnly() {
        if (eventsListLayer != null) {
            eventsListLayer.setVisible(true);
            eventsListLayer.setManaged(true);
        }
        if (newEventFormHost != null) {
            newEventFormHost.setVisible(false);
            newEventFormHost.setManaged(false);
        }
        if (eventDetailLayer != null) {
            eventDetailLayer.setVisible(false);
            eventDetailLayer.setManaged(false);
        }
    }

    /** Affiche explicitement la liste principale des événements (vue cap 1). */
    public void showListView() {
        detailShownEvent = null;
        editingEventId = null;
        if (newEventFormHost != null) {
            newEventFormHost.setVisible(false);
            newEventFormHost.setManaged(false);
        }
        if (eventDetailLayer != null) {
            eventDetailLayer.setVisible(false);
            eventDetailLayer.setManaged(false);
        }
        showLayerEventsListOnly();
    }

    private void showEventDetailLayer() {
        if (newEventFormHost != null) {
            newEventFormHost.setVisible(false);
            newEventFormHost.setManaged(false);
        }
        if (eventsListLayer != null) {
            eventsListLayer.setVisible(false);
            eventsListLayer.setManaged(false);
        }
        if (eventDetailLayer != null) {
            eventDetailLayer.setVisible(true);
            eventDetailLayer.setManaged(true);
            eventDetailLayer.toFront();
        }
    }

    private void hideEventDetailToList() {
        detailShownEvent = null;
        if (eventDetailLayer != null) {
            eventDetailLayer.setVisible(false);
            eventDetailLayer.setManaged(false);
        }
        showLayerEventsListOnly();
    }

    /**
     * Valide tous les champs du formulaire et remplit {@code e} si tout est correct.
     *
     * @return message d’erreur affichable, ou {@code null} si la validation a réussi.
     */
    private String validateAndFillNewEvent(Event e) {
        String titreRaw = formTitre != null ? formTitre.getText() : "";
        if (titreRaw == null || titreRaw.isBlank()) {
            return "Le titre est obligatoire : de " + MIN_TITRE_LEN + " à " + MAX_TITRE
                    + " caractères, sans espaces superflus en début ou fin de texte.";
        }
        String titre = titreRaw.trim();
        if (titre.length() < MIN_TITRE_LEN) {
            return "Le titre est trop court : au moins " + MIN_TITRE_LEN + " caractères et au plus " + MAX_TITRE
                    + " caractères, hors espaces superflus.";
        }
        if (titre.length() > MAX_TITRE) {
            return "Le titre ne doit pas dépasser " + MAX_TITRE + " caractères.";
        }
        if (titre.chars().anyMatch(Character::isDigit)) {
            return "Le titre ne doit pas contenir de chiffres (0-9).";
        }
        if (!EVENT_TITRE_LETTRES_UNIQUEMENT.matcher(titre).matches()) {
            return "Le titre : utilisez uniquement des lettres, espaces, apostrophes (') ou tirets (-). "
                    + "Les autres caractères spéciaux ne sont pas autorisés.";
        }
        if (titre.codePoints().noneMatch(Character::isLetter)) {
            return "Le titre doit contenir au moins une lettre.";
        }

        String desc = formDescription != null && formDescription.getText() != null
                ? formDescription.getText()
                : "";
        String descTrim = desc.trim();
        if (descTrim.isBlank()) {
            return "La description est obligatoire : de " + MIN_DESCRIPTION_LEN + " à " + MAX_DESCRIPTION
                    + " caractères, avec un texte réel une fois les espaces de bord retirés.";
        }
        if (descTrim.length() < MIN_DESCRIPTION_LEN) {
            return "La description est trop courte : il faut au moins " + MIN_DESCRIPTION_LEN
                    + " caractères et au plus " + MAX_DESCRIPTION + " caractères.";
        }
        if (desc.length() > MAX_DESCRIPTION) {
            return "La description ne doit pas dépasser " + MAX_DESCRIPTION + " caractères.";
        }

        LocalDate date = formDate != null ? formDate.getValue() : null;
        if (date == null) {
            return "La date est obligatoire : sélectionnez un jour dans le calendrier.";
        }
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            return "La date de l'événement doit être aujourd'hui ou une date ultérieure (les dates passées ne sont pas autorisées).";
        }

        String clockErr = validateClockPickers();
        if (clockErr != null) {
            return clockErr;
        }

        LocalTime tDebut;
        LocalTime tFin;
        try {
            tDebut = readTimeFromPickers(
                    formHeureDebutHeure, formHeureDebutMin, formHeureDebutAmPm, "Heure de début");
            tFin = readTimeFromPickers(
                    formHeureFinHeure, formHeureFinMin, formHeureFinAmPm, "Heure de fin");
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }

        LocalDateTime debut = LocalDateTime.of(date, tDebut);
        LocalDateTime fin = LocalDateTime.of(date, tFin);
        if (!fin.isAfter(debut)) {
            return "L'heure de fin doit être strictement postérieure à l'heure de début le même jour.";
        }
        long dureeMin = ChronoUnit.MINUTES.between(debut, fin);
        if (dureeMin < MIN_EVENT_DURATION_MINUTES) {
            return "L'événement doit durer au moins " + MIN_EVENT_DURATION_MINUTES
                    + " minutes : écartez davantage l'heure de fin par rapport au début.";
        }

        String mode = formMode != null ? formMode.getValue() : null;
        if (mode == null || mode.isBlank()) {
            return "Le mode est obligatoire : choisissez Présentiel, En ligne ou Hybride dans la liste.";
        }
        if (!List.of("Présentiel", "En ligne", "Hybride").contains(mode)) {
            return "Mode d'événement non reconnu.";
        }

        String thematique = resolveThematiqueText();
        if (thematique == null || thematique.isBlank()) {
            return "La thématique est obligatoire : de " + MIN_THEMATIQUE_LEN + " à " + MAX_THEMATIQUE
                    + " caractères, au choix dans la liste ou en saisie libre.";
        }
        thematique = thematique.trim();
        if (thematique.length() < MIN_THEMATIQUE_LEN) {
            return "La thématique est trop courte : de " + MIN_THEMATIQUE_LEN + " à " + MAX_THEMATIQUE
                    + " caractères.";
        }
        if (thematique.length() > MAX_THEMATIQUE) {
            return "La thématique ne doit pas dépasser " + MAX_THEMATIQUE + " caractères.";
        }

        Double lat = null;
        Double lng = null;
        if ("Présentiel".equals(mode) || "Hybride".equals(mode)) {
            String coordErr = parseAndValidateLatLng();
            if (coordErr != null) {
                return coordErr;
            }
            try {
                lat = parseOptionalDouble(formLat);
                lng = parseOptionalDouble(formLng);
            } catch (NumberFormatException ex) {
                return "Latitude et longitude : saisissez uniquement des nombres décimaux, par exemple 36,8065.";
            }
        }

        e.setTitre(titre);
        e.setDescription(descTrim);
        e.setDateDebut(debut);
        e.setDateFin(fin);
        e.setModeEvenement(mode);
        e.setThematique(thematique);
        e.setPlacesMax(0);
        /* Visible sur la vitrine publique ; la modification conserve le statut existant (voir onSaveNewEvent). */
        e.setStatut(EventStatus.PUBLIE);

        switch (mode) {
            case "Présentiel" -> {
                String lieu = formLieu != null ? nullIfBlank(formLieu.getText()) : null;
                if (lieu == null) {
                    return "Le lieu est obligatoire en mode présentiel : de " + MIN_LIEU_LEN + " à " + MAX_LIEU
                            + " caractères.";
                }
                if (lieu.length() < MIN_LIEU_LEN) {
                    return "Le lieu est trop court : de " + MIN_LIEU_LEN + " à " + MAX_LIEU + " caractères.";
                }
                if (lieu.length() > MAX_LIEU) {
                    return "Le lieu ne doit pas dépasser " + MAX_LIEU + " caractères.";
                }
                String maps = formLienMaps != null ? nullIfBlank(formLienMaps.getText()) : null;
                String mapsErr = validateOptionalHttpUrl("Lien Google Maps", maps, false);
                if (mapsErr != null) {
                    return mapsErr;
                }
                e.setLieu(lieu);
                e.setLienGoogleMaps(maps);
                e.setLatitude(lat);
                e.setLongitude(lng);
                e.setLienZoomVisio(null);
            }
            case "En ligne" -> {
                String zoom = formLienZoom != null ? nullIfBlank(formLienZoom.getText()) : null;
                String zoomErr = validateOptionalHttpUrl("Lien réunion Zoom / visio", zoom, true);
                if (zoomErr != null) {
                    return zoomErr;
                }
                e.setLieu(null);
                e.setLienGoogleMaps(null);
                e.setLatitude(null);
                e.setLongitude(null);
                e.setLienZoomVisio(zoom);
            }
            case "Hybride" -> {
                String lieu = formLieu != null ? nullIfBlank(formLieu.getText()) : null;
                if (lieu == null) {
                    return "Le lieu est obligatoire en mode hybride : de " + MIN_LIEU_LEN + " à " + MAX_LIEU
                            + " caractères.";
                }
                if (lieu.length() < MIN_LIEU_LEN) {
                    return "Le lieu est trop court : de " + MIN_LIEU_LEN + " à " + MAX_LIEU + " caractères.";
                }
                if (lieu.length() > MAX_LIEU) {
                    return "Le lieu ne doit pas dépasser " + MAX_LIEU + " caractères.";
                }
                String maps = formLienMaps != null ? nullIfBlank(formLienMaps.getText()) : null;
                String mapsErr = validateOptionalHttpUrl("Lien Google Maps", maps, false);
                if (mapsErr != null) {
                    return mapsErr;
                }
                String zoom = formLienZoom != null ? nullIfBlank(formLienZoom.getText()) : null;
                String zoomErr = validateOptionalHttpUrl("Lien réunion Zoom / visio", zoom, true);
                if (zoomErr != null) {
                    return zoomErr;
                }
                e.setLieu(lieu);
                e.setLienGoogleMaps(maps);
                e.setLatitude(lat);
                e.setLongitude(lng);
                e.setLienZoomVisio(zoom);
            }
            default -> {
                return "Mode d'événement non reconnu.";
            }
        }

        return null;
    }

    private String resolveThematiqueText() {
        if (formThematique == null) {
            return null;
        }
        if (formThematique.getEditor() != null) {
            String ed = formThematique.getEditor().getText();
            if (ed != null && !ed.isBlank()) {
                return ed.trim();
            }
        }
        String val = formThematique.getValue();
        return val != null && !val.isBlank() ? val.trim() : null;
    }

    /**
     * Vérifie une URL http(s) optionnelle ou obligatoire.
     *
     * @param required si {@code true}, une valeur vide est refusée.
     */
    private static String validateOptionalHttpUrl(String fieldLabel, String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                return "Le champ « " + fieldLabel + " » est obligatoire : une URL commençant par http:// ou https://, "
                        + "longueur maximale " + MAX_URL + " caractères.";
            }
            return null;
        }
        if (value.length() > MAX_URL) {
            return "« " + fieldLabel + " » ne doit pas dépasser " + MAX_URL + " caractères.";
        }
        String t = value.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "« " + fieldLabel + " » doit être une URL commençant par http:// ou https://.";
        }
        return null;
    }

    /**
     * @return {@code null} si les coordonnées sont vides (les deux) ou valides ; sinon message d’erreur.
     */
    private String parseAndValidateLatLng() {
        String latRaw = formLat != null && formLat.getText() != null ? formLat.getText().trim() : "";
        String lngRaw = formLng != null && formLng.getText() != null ? formLng.getText().trim() : "";
        boolean emptyLat = latRaw.isBlank();
        boolean emptyLng = lngRaw.isBlank();
        if (emptyLat && emptyLng) {
            return null;
        }
        if (emptyLat != emptyLng) {
            return "Renseignez la latitude et la longitude ensemble, ou laissez les deux champs vides. "
                    + "La latitude va de -90 à 90 et la longitude de -180 à 180.";
        }
        double la;
        double lg;
        try {
            la = Double.parseDouble(latRaw.replace(',', '.'));
            lg = Double.parseDouble(lngRaw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return "Latitude et longitude : saisissez des nombres décimaux, par exemple 36,8065 et 10,1815.";
        }
        if (la < -90.0 || la > 90.0) {
            return "La latitude doit être comprise entre -90 et 90.";
        }
        if (lg < -180.0 || lg > 180.0) {
            return "La longitude doit être comprise entre -180 et 180.";
        }
        return null;
    }

    @FXML
    public void onFormAiSummarize() {
        showInfo("IA", "Fonction « Résumer avec l'IA » : à brancher sur votre service (API).");
    }

    @FXML
    public void onFormAiSuggest() {
        showInfo("IA", "Fonction « Suggérer une description » : à brancher sur votre service (API).");
    }

    @FXML
    public void onFormFetchCoords() {
        showInfo("Coordonnées", "Récupération automatique à partir du lieu : branchez un géocodage (API) si besoin.");
    }

    @FXML
    public void onFormGenerateZoomLink() {
        if (formLienZoom != null) {
            formLienZoom.setText("https://zoom.us/j/0000000000?pwd=exemple");
        }
        showInfo("Zoom", "Lien d'exemple inséré. Branchez l'API Zoom pour une génération réelle.");
    }

    private static String nullIfBlank(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Double parseOptionalDouble(TextField field) throws NumberFormatException {
        if (field == null) {
            return null;
        }
        String t = field.getText();
        if (t == null || t.isBlank()) {
            return null;
        }
        return Double.parseDouble(t.trim().replace(',', '.'));
    }

    /**
     * Vérifie les spinners d’heure / minutes / AM–PM avant conversion en {@link LocalTime}.
     */
    private String validateClockPickers() {
        String e = validateOneClockRow(formHeureDebutHeure, formHeureDebutMin, formHeureDebutAmPm, "Heure de début");
        if (e != null) {
            return e;
        }
        return validateOneClockRow(formHeureFinHeure, formHeureFinMin, formHeureFinAmPm, "Heure de fin");
    }

    private static String validateOneClockRow(
            Spinner<Integer> hourSpin,
            Spinner<Integer> minSpin,
            ComboBox<String> amPmBox,
            String label) {
        if (hourSpin == null || minSpin == null || amPmBox == null) {
            return label + " : contrôles d'heure manquants.";
        }
        Integer h12 = hourSpin.getValue();
        Integer mm = minSpin.getValue();
        if (h12 == null || mm == null) {
            return label + " : indiquez l'heure entre 1 et 12 et les minutes entre 0 et 59.";
        }
        if (h12 < 1 || h12 > 12) {
            return label + " : l'heure doit être entre 1 et 12.";
        }
        if (mm < 0 || mm > 59) {
            return label + " : les minutes doivent être entre 0 et 59.";
        }
        String ap = amPmBox.getValue();
        if (ap == null || ap.isBlank()) {
            return label + " : choisissez AM ou PM.";
        }
        String aps = ap.trim();
        if (!"AM".equalsIgnoreCase(aps) && !"PM".equalsIgnoreCase(aps)) {
            return label + " : sélectionnez AM ou PM dans la liste.";
        }
        return null;
    }

    private static LocalTime readTimeFromPickers(
            Spinner<Integer> hourSpin,
            Spinner<Integer> minSpin,
            ComboBox<String> amPmBox,
            String label) {
        if (hourSpin == null || minSpin == null || amPmBox == null) {
            throw new IllegalArgumentException(label + " : contrôles manquants.");
        }
        Integer h12 = hourSpin.getValue();
        Integer mm = minSpin.getValue();
        String ap = amPmBox.getValue();
        if (h12 == null || mm == null) {
            throw new IllegalArgumentException(label + " : indiquez une heure complète.");
        }
        if (h12 < 1 || h12 > 12 || mm < 0 || mm > 59) {
            throw new IllegalArgumentException(label + " : heure ou minutes hors plage valide.");
        }
        if (ap == null || ap.isBlank()) {
            throw new IllegalArgumentException(label + " : choisissez AM ou PM.");
        }
        return toLocalTime12h(h12, mm, ap);
    }

    /** Convertit heure affichée 1–12 + AM/PM en {@link LocalTime} (24 h). */
    private static LocalTime toLocalTime12h(int hour12, int minute, String ampm) {
        String ap = ampm == null ? "" : ampm.trim();
        int h;
        if ("PM".equalsIgnoreCase(ap)) {
            h = (hour12 == 12) ? 12 : hour12 + 12;
        } else {
            h = (hour12 == 12) ? 0 : hour12;
        }
        return LocalTime.of(h, minute);
    }

    private void clearNewEventForm() {
        if (formTitre != null) {
            formTitre.clear();
        }
        if (formDescription != null) {
            formDescription.clear();
        }
        if (formDate != null) {
            formDate.setValue(LocalDate.now());
        }
        if (formHeureDebutHeure != null) {
            ((IntegerSpinnerValueFactory) formHeureDebutHeure.getValueFactory()).setValue(9);
        }
        if (formHeureDebutMin != null) {
            ((IntegerSpinnerValueFactory) formHeureDebutMin.getValueFactory()).setValue(0);
        }
        if (formHeureDebutAmPm != null) {
            formHeureDebutAmPm.getSelectionModel().select("AM");
        }
        if (formHeureFinHeure != null) {
            ((IntegerSpinnerValueFactory) formHeureFinHeure.getValueFactory()).setValue(5);
        }
        if (formHeureFinMin != null) {
            ((IntegerSpinnerValueFactory) formHeureFinMin.getValueFactory()).setValue(0);
        }
        if (formHeureFinAmPm != null) {
            formHeureFinAmPm.getSelectionModel().select("PM");
        }
        if (formMode != null) {
            formMode.getSelectionModel().selectFirst();
        }
        if (formLieu != null) {
            formLieu.clear();
        }
        if (formLienMaps != null) {
            formLienMaps.clear();
        }
        if (formLat != null) {
            formLat.clear();
        }
        if (formLng != null) {
            formLng.clear();
        }
        if (formLienZoom != null) {
            formLienZoom.clear();
        }
        if (formThematique != null) {
            formThematique.setValue(null);
            formThematique.getSelectionModel().clearSelection();
            if (formThematique.getEditor() != null) {
                formThematique.getEditor().clear();
            }
        }
        updateModeSections();
    }

    private void showNewEventForm() {
        detailShownEvent = null;
        if (eventDetailLayer != null) {
            eventDetailLayer.setVisible(false);
            eventDetailLayer.setManaged(false);
        }
        if (eventsListLayer != null) {
            eventsListLayer.setVisible(false);
            eventsListLayer.setManaged(false);
        }
        if (newEventFormHost != null) {
            newEventFormHost.setVisible(true);
            newEventFormHost.setManaged(true);
            newEventFormHost.toFront();
        }
    }

    private void hideNewEventForm() {
        editingEventId = null;
        if (formPageTitle != null) {
            formPageTitle.setText("Nouvel événement");
        }
        if (newEventFormHost != null) {
            newEventFormHost.setVisible(false);
            newEventFormHost.setManaged(false);
        }
        showLayerEventsListOnly();
    }

    @FXML
    public void onAdvancedSearch() {
        showInfo(
                "Recherche avancée",
                "Cette fonctionnalité sera proposée dans une version ultérieure.\n"
                        + "Pour l’instant, utilisez le champ de recherche sous le titre.");
    }

    private void applyFilterAndSort() {
        String q = searchField == null || searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.FRENCH);

        List<Event> filtered = new ArrayList<>();
        for (Event e : masterEvents) {
            if (matchesSearch(e, q)) {
                filtered.add(e);
            }
        }

        Comparator<Event> cmp = comparatorForSort();
        boolean desc = "Décroissant".equals(sortOrderBox == null ? null : sortOrderBox.getValue());
        if (desc) {
            cmp = cmp.reversed();
        }
        filtered.sort(cmp);

        adminEventsTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private boolean matchesSearch(Event e, String q) {
        if (q.isEmpty()) {
            return true;
        }
        if (contains(e.getTitre(), q)) {
            return true;
        }
        if (contains(e.getLieu(), q)) {
            return true;
        }
        if (contains(e.getThematique(), q)) {
            return true;
        }
        if (e.getDateDebut() != null && contains(e.getDateDebut().toString(), q)) {
            return true;
        }
        if (e.getDateDebut() != null) {
            String fr = e.getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            if (fr.toLowerCase(Locale.FRENCH).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.FRENCH).contains(q);
    }

    private Comparator<Event> comparatorForSort() {
        return Comparator.comparing(
                Event::getDateDebut,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private void updateAllStats() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> events = new ArrayList<>(masterEvents);

        long total = events.size();
        /* À venir / passés : basé sur la date de début (cohérent avec « événement déjà eu lieu »). */
        long aVenir = events.stream()
                .filter(e -> e.getDateDebut() != null && e.getDateDebut().isAfter(now))
                .count();
        long passes = events.stream()
                .filter(e -> e.getDateDebut() != null && !e.getDateDebut().isAfter(now))
                .count();

        if (statBadgePeriodVenir != null) {
            statBadgePeriodVenir.setText(String.valueOf(aVenir));
        }
        if (statBadgePeriodPasses != null) {
            statBadgePeriodPasses.setText(String.valueOf(passes));
        }
        if (statBigTotal != null) {
            statBigTotal.setText(String.valueOf(total));
        }
        if (statBigVenir != null) {
            statBigVenir.setText(String.valueOf(aVenir));
        }

        EventStatsPieCharts.rebuildPeriodPie(piePeriodChart, aVenir, passes, total);

        try {
            List<EventRegistration> regs = registrationService.findAll();
            long acc = regs.stream().filter(r -> r.getStatut() == RegistrationStatus.ACCEPTE).count();
            long att = regs.stream().filter(r -> r.getStatut() == RegistrationStatus.EN_ATTENTE).count();
            long ref = regs.stream().filter(r -> r.getStatut() == RegistrationStatus.REFUSE).count();

            if (statBadgeInscAccept != null) {
                statBadgeInscAccept.setText(String.valueOf(acc));
            }
            if (statBadgeInscAttente != null) {
                statBadgeInscAttente.setText(String.valueOf(att));
            }
            if (statBadgeInscRefus != null) {
                statBadgeInscRefus.setText(String.valueOf(ref));
            }
            if (statBigInscTotal != null) {
                statBigInscTotal.setText(String.valueOf(regs.size()));
            }
            EventStatsPieCharts.rebuildRegsPie(pieRegsChart, acc, att, ref);
        } catch (Exception ex) {
            EventStatsPieCharts.rebuildRegsPieError(pieRegsChart);
            if (statBigInscTotal != null) {
                statBigInscTotal.setText("—");
            }
            if (statBadgeInscAccept != null) {
                statBadgeInscAccept.setText("—");
            }
            if (statBadgeInscAttente != null) {
                statBadgeInscAttente.setText("—");
            }
            if (statBadgeInscRefus != null) {
                statBadgeInscRefus.setText("—");
            }
        }
    }

    private void setupAdminEventsUi() {
        adminEventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Event, String> colTitre = new TableColumn<>("TITRE");
        /* Pas de PropertyValueFactory : avec JPMS, la réflexion sur org.example.models.Event échoue ;
         * les autres colonnes utilisent déjà des lambdas. */
        colTitre.setCellValueFactory(c -> {
            Event ev = c.getValue();
            String t = ev != null ? ev.getTitre() : null;
            return new ReadOnlyObjectWrapper<>(t != null ? t : "");
        });
        TableColumn<Event, String> colMsg = new TableColumn<>("MESSAGES");
        colMsg.setCellValueFactory(c -> {
            Event ev = c.getValue();
            if (ev == null) {
                return new ReadOnlyObjectWrapper<>("0");
            }
            int count = messageCountByEventId.getOrDefault(ev.getId(), 0);
            return new ReadOnlyObjectWrapper<>(String.valueOf(count));
        });
        colMsg.setCellFactory(col -> new TableCell<Event, String>() {
            @Override
            protected void updateItem(String countText, boolean empty) {
                super.updateItem(countText, empty);
                setAlignment(Pos.CENTER);
                Event ev = getTableRow() != null ? (Event) getTableRow().getItem() : null;
                if (empty || ev == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                int count = 0;
                try {
                    count = Integer.parseInt(countText != null ? countText : "0");
                } catch (NumberFormatException ignored) {
                    // garde 0
                }

                Button iconBtn = new Button("💬");
                iconBtn.setFocusTraversable(false);
                iconBtn.setMinSize(18, 18);
                iconBtn.setPrefSize(18, 18);
                iconBtn.setMaxSize(18, 18);
                iconBtn.setStyle(
                        "-fx-background-color: #1da1f2;"
                                + "-fx-text-fill: white;"
                                + "-fx-font-size: 8px;"
                                + "-fx-font-weight: 700;"
                                + "-fx-background-radius: 999;"
                                + "-fx-padding: 0;"
                                + "-fx-cursor: hand;");
                iconBtn.setTooltip(new Tooltip("Ouvrir les discussions de cet événement"));
                iconBtn.setOnAction(actionEvent -> openAdminEventDetail(ev, true));

                Label countLabel = new Label(String.valueOf(count));
                countLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151; -fx-font-weight: 600;");

                HBox cellBox = new HBox(6, iconBtn, countLabel);
                cellBox.setAlignment(Pos.CENTER);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);
                setGraphic(cellBox);
                setText(null);
            }
        });
        TableColumn<Event, String> colDate = new TableColumn<>("DATE");
        colDate.setCellValueFactory(c -> {
            Event ev = c.getValue();
            if (ev == null || ev.getDateDebut() == null) {
                return new ReadOnlyObjectWrapper<>("");
            }
            return new ReadOnlyObjectWrapper<>(ev.getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        });
        TableColumn<Event, String> colLieu = new TableColumn<>("LIEU");
        colLieu.setCellValueFactory(c -> {
            Event ev = c.getValue();
            String l = ev != null ? ev.getLieu() : null;
            return new ReadOnlyObjectWrapper<>(l != null && !l.isBlank() ? l : "—");
        });
        TableColumn<Event, String> colTheme = new TableColumn<>("THÉMATIQUE");
        colTheme.setCellValueFactory(c -> {
            Event ev = c.getValue();
            String t = ev != null ? ev.getThematique() : null;
            return new ReadOnlyObjectWrapper<>(t != null && !t.isBlank() ? t : "—");
        });
        colTheme.setCellFactory(col -> new TableCell<Event, String>() {
            @Override
            protected void updateItem(String t, boolean empty) {
                super.updateItem(t, empty);
                setAlignment(Pos.CENTER);
                if (empty || t == null || t.isBlank() || "—".equals(t)) {
                    setGraphic(null);
                    setText("—");
                    return;
                }
                Label pill = new Label(t.trim());
                pill.getStyleClass().add("admin-events-thematique-pill");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setGraphic(pill);
                setText(null);
            }
        });
        TableColumn<Event, Event> colActions = new TableColumn<>("ACTIONS");
        colActions.setPrefWidth(156);
        colActions.setMinWidth(140);
        /* Une valeur par ligne : sans cellValueFactory, JavaFX laisse souvent empty=true → pas d’icônes. */
        colActions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colActions.setCellFactory(col -> new TableCell<Event, Event>() {
            @Override
            protected void updateItem(Event ev, boolean empty) {
                super.updateItem(ev, empty);
                setAlignment(Pos.CENTER);
                if (empty || ev == null) {
                    setGraphic(null);
                    return;
                }
                HBox box = AdminEventsTableActionBar.create(
                        () -> openAdminEventDetail(ev),
                        () -> openEditEvent(ev),
                        () -> confirmDeleteEvent(ev));
                setGraphic(box);
            }
        });
        adminEventsTable.getColumns().setAll(colTitre, colMsg, colDate, colLieu, colTheme, colActions);

        adminEventsTable.setRowFactory(tv -> {
            TableRow<Event> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openAdminEventDetail(row.getItem());
                }
            });
            return row;
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, prev, cur) -> applyFilterAndSort());
        }

        EventTableHeightUtil.bindHeightToItems(adminEventsTable);
    }

    private void showValidationMessage(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Contrôle de saisie");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText("Operation impossible");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
