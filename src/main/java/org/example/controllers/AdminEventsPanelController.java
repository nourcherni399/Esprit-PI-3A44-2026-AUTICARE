package org.example.controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
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
import org.example.services.EventRegistrationTicketEmailService;
import org.example.services.EventService;
import org.example.services.EventReminderEmailService;
import org.example.services.ExternalParticipantsPdfService;
import org.example.services.GoogleCustomSearchService;
import org.example.services.HuggingFaceTextService;
import org.example.services.OpenStreetMapService;
import org.example.services.ThematiqueService;
import org.example.services.UserService;
import org.example.services.UserNotificationService;
import org.example.services.ZoomMeetingLinkService;
import org.example.utils.AppState;
import org.example.utils.FxInputConstraints;
import org.example.utils.MapEmbedUrls;

import javafx.application.Platform;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

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
    @FXML private Label checkinServerHintLabel;

    @FXML private VBox eventsListLayer;
    @FXML private ScrollPane eventsScrollRoot;
    @FXML private StackPane eventsStackPane;
    @FXML private AnchorPane newEventFormHost;
    @FXML private VBox newEventFormLayer;
    @FXML private VBox eventDetailLayer;
    @FXML private VBox worldSearchSection;
    @FXML private TextField worldSearchKeywords;
    @FXML private ComboBox<String> worldSearchPeriod;
    @FXML private VBox worldSearchResultsBox;
    @FXML private Label worldSearchHintLabel;
    @FXML private Button worldSearchAiButton;
    @FXML private AnchorPane worldSearchAiHost;
    @FXML private VBox worldSearchAiLoadingBox;
    @FXML private ProgressIndicator worldSearchAiProgress;
    @FXML private ScrollPane worldSearchAiContentScroll;
    @FXML private VBox worldSearchAiContentRoot;
    @FXML private Label worldAiContextKeyword;
    @FXML private Label worldAiContextPeriod;
    @FXML private Label worldAiContextCount;
    @FXML private Label worldAiTrendMain;
    @FXML private Label worldAiTrendPeriod;
    @FXML private Label worldAiTrendAudience;
    @FXML private Label worldAiTrendLocation;
    @FXML private VBox worldAiTitleChoicesBox;
    @FXML private VBox worldAiDescriptionChoicesBox;
    @FXML private TextField worldAiFormTitle;
    @FXML private TextArea worldAiFormDescription;
    @FXML private TextField worldAiFormAudience;
    @FXML private Label worldAiScoreLabel;
    @FXML private ProgressBar worldAiScoreProgress;
    @FXML private Label worldAiScoreHint;
    @FXML private VBox worldAiRecommendationsBox;
    @FXML private Label worldAiWhySuggestionLabel;

    @FXML private Label formPageTitle;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailMetaLabel;
    @FXML private Label detailDescriptionLabel;
    @FXML private Label detailParticipantsHeader;
    @FXML private Label detailParticipantsBody;
    @FXML private Label detailQrValidationLabel;
    @FXML private Label detailDiscussionBody;
    @FXML private Label detailUnreadBadgeLabel;
    @FXML private VBox detailParticipantsCard;
    @FXML private VBox detailParticipantsTableBox;
    @FXML private TextField detailQrTicketInput;
    @FXML private VBox detailDiscussionCard;
    @FXML private VBox detailConversationsListBox;
    @FXML private VBox detailConversationPlaceholderBox;
    @FXML private VBox detailConversationPanelBox;
    @FXML private ScrollPane detailConversationMessagesScroll;
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
    @FXML private StackPane formMapPreviewHost;

    private final EventService eventService = new EventService();
    private final EventRegistrationService registrationService = new EventRegistrationService();
    private final EventMessageService eventMessageService = new EventMessageService();
    private final ThematiqueService thematiqueService = new ThematiqueService();
    private final UserService userService = new UserService();
    private final UserNotificationService userNotificationService = new UserNotificationService();
    private final OpenStreetMapService openStreetMapService = new OpenStreetMapService();
    private final ZoomMeetingLinkService zoomMeetingLinkService = new ZoomMeetingLinkService();
    private final ExternalParticipantsPdfService externalParticipantsPdfService = new ExternalParticipantsPdfService();
    private final HuggingFaceTextService huggingFaceTextService = new HuggingFaceTextService();
    private final GoogleCustomSearchService googleCustomSearchService = new GoogleCustomSearchService();
    private final EventReminderEmailService eventReminderEmailService = new EventReminderEmailService();
    private final EventRegistrationTicketEmailService eventRegistrationTicketEmailService = new EventRegistrationTicketEmailService();
    private HttpServer localQrCheckinServer;
    private volatile boolean localQrCheckinServerStarted;

    /** Derniers résultats CSE, pour l’appel à l’IA. */
    private final List<GoogleCustomSearchService.CseResult> lastWorldSearchResults = new ArrayList<>();

    /** Données brutes (avant filtre / tri affiché). */
    private final ObservableList<Event> masterEvents = FXCollections.observableArrayList();

    /** Événement affiché dans la fiche « Voir » (édition / rappels). */
    private Event detailShownEvent;
    /** Si non nul, le formulaire enregistre une mise à jour au lieu d’un insert. */
    private Integer editingEventId;
    private final Map<Integer, Integer> messageCountByEventId = new HashMap<>();
    private Integer selectedConversationUserId;
    private final List<Integer> currentConversationParticipantIds = new ArrayList<>();
    private WebView formMapPreviewWeb;

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
        if (worldSearchPeriod != null) {
            worldSearchPeriod.setItems(FXCollections.observableArrayList(
                    "Ce mois", "3 derniers mois", "Cette année"));
            worldSearchPeriod.getSelectionModel().selectFirst();
        }
        if (worldSearchKeywords != null) {
            worldSearchKeywords.setTextFormatter(FxInputConstraints.maxLength(400));
        }
        ensureLocalQrCheckinServer();
        refreshCheckinServerHintUi();
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
        if (worldSearchAiHost != null && eventsStackPane != null) {
            worldSearchAiHost.minWidthProperty().bind(eventsStackPane.widthProperty());
            worldSearchAiHost.prefWidthProperty().bind(eventsStackPane.widthProperty());
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
        if (detailConversationMessagesScroll != null && detailConversationMessagesBox != null) {
            detailConversationMessagesBox.minWidthProperty().bind(detailConversationMessagesScroll.widthProperty().subtract(18));
            detailConversationMessagesBox.prefWidthProperty().bind(detailConversationMessagesScroll.widthProperty().subtract(18));
            detailConversationMessagesBox.maxWidthProperty().bind(detailConversationMessagesScroll.widthProperty().subtract(18));
            detailConversationMessagesBox.setFillWidth(true);
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
        if (!lieu) {
            clearFormMapPreview();
            return;
        }
        try {
            Double lat = parseOptionalDouble(formLat);
            Double lng = parseOptionalDouble(formLng);
            if (lat != null && lng != null) {
                updateFormMapPreview(lat, lng);
            }
        } catch (NumberFormatException ignored) {
            clearFormMapPreview();
        }
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
        if (detailShownEvent == null) {
            showInfo("Rappels", "Aucun événement sélectionné.");
            return;
        }
        final Event event = detailShownEvent;
        CompletableFuture.supplyAsync(() -> {
            int sent = 0;
            int failed = 0;
            List<String> failedEmails = new ArrayList<>();
            List<String> failedReasons = new ArrayList<>();
            try {
                List<EventRegistration> regs = registrationService.listParticipants(event.getId());
                if (regs == null || regs.isEmpty()) {
                    return new ReminderSendResult(0, 0, List.of(), List.of(), true);
                }
                // Évite les doublons de rappel si un même utilisateur apparaît plusieurs fois.
                var notifiedUserIds = new LinkedHashSet<Integer>();
                for (EventRegistration reg : regs) {
                    if (reg == null || !notifiedUserIds.add(reg.getUtilisateurId())) {
                        continue;
                    }
                    try {
                        Optional<User> uOpt = userService.findById(reg.getUtilisateurId());
                        if (uOpt.isEmpty()) {
                            failed++;
                            failedEmails.add("user#" + reg.getUtilisateurId());
                            continue;
                        }
                        User u = uOpt.get();
                        String to = nullIfBlank(u.getEmail());
                        if (to == null) {
                            failed++;
                            failedEmails.add(displayNameForUser(u, reg.getUtilisateurId()));
                            continue;
                        }
                        String displayName = displayNameForUser(u, reg.getUtilisateurId());
                        eventReminderEmailService.sendEventReminderEmail(to, displayName, event);
                        sent++;
                    } catch (Exception mailEx) {
                        failed++;
                        String ident = "user#" + reg.getUtilisateurId();
                        try {
                            Optional<User> uOpt = userService.findById(reg.getUtilisateurId());
                            if (uOpt.isPresent()) {
                                String e = nullIfBlank(uOpt.get().getEmail());
                                if (e != null) {
                                    ident = e;
                                }
                            }
                        } catch (Exception ignored) {
                            // conserve ident par défaut
                        }
                        failedEmails.add(ident);
                        String reason = mailEx.getMessage() == null ? mailEx.toString() : mailEx.getMessage();
                        reason = reason.replace("\r", " ").replace("\n", " ").trim();
                        if (reason.length() > 220) {
                            reason = reason.substring(0, 220) + "...";
                        }
                        failedReasons.add(ident + " -> " + reason);
                    }
                }
            } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
            return new ReminderSendResult(sent, failed, failedEmails, failedReasons, false);
        }).whenComplete((result, th) -> Platform.runLater(() -> {
            if (th != null) {
                Throwable t = th instanceof java.util.concurrent.CompletionException && th.getCause() != null
                        ? th.getCause() : th;
                if (t instanceof Exception) {
                    showError((Exception) t);
                } else {
                    showInfo("Rappels", t.getMessage() != null ? t.getMessage() : t.toString());
                }
                return;
            }
            if (result.emptyParticipants()) {
                showInfo("Rappels", "Aucun participant inscrit pour cet événement.");
                return;
            }
            StringBuilder msg = new StringBuilder();
            msg.append("Rappels envoyés: ").append(result.sentCount())
                    .append(" | Échecs: ").append(result.failedCount());
            if (!result.failedTargets().isEmpty()) {
                msg.append("\nÉchecs sur: ")
                        .append(String.join(", ", result.failedTargets().stream().limit(5).toList()));
                if (result.failedTargets().size() > 5) {
                    msg.append(" ...");
                }
            }
            if (!result.failedReasons().isEmpty()) {
                msg.append("\nCause: ")
                        .append(result.failedReasons().get(0));
            }
            showInfo("Rappels", msg.toString());
        }));
    }

    private record ReminderSendResult(int sentCount, int failedCount, List<String> failedTargets,
                                      List<String> failedReasons,
                                      boolean emptyParticipants) {
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
            boolean usedExternalApi = writeParticipantsPdfWithExternalApiOrFallback(file.toPath(), regs);
            if (usedExternalApi) {
                showInfo("PDF", "Export terminé via API externe : " + file.getName());
            } else {
                showInfo("PDF", "Export terminé : " + file.getName());
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private boolean writeParticipantsPdfWithExternalApiOrFallback(Path path, List<EventRegistration> regs) throws Exception {
        List<ExternalParticipantsPdfService.ParticipantPdfRow> rows = new ArrayList<>();
        if (regs != null) {
            for (EventRegistration r : regs) {
                User u = null;
                try {
                    u = userService.findById(r.getUtilisateurId()).orElse(null);
                } catch (Exception ignored) {
                    // repli sur placeholders
                }
                String name = displayNameForUser(u, r.getUtilisateurId());
                String email = u != null && u.getEmail() != null ? u.getEmail() : "—";
                rows.add(ExternalParticipantsPdfService.ParticipantPdfRow.of(name, email, r));
            }
        }
        try {
            Optional<byte[]> externalPdf = externalParticipantsPdfService.generateParticipantsPdf(
                    detailShownEvent != null ? detailShownEvent.getTitre() : "Événement",
                    rows);
            if (externalPdf.isPresent()) {
                Files.write(path, externalPdf.get());
                return true;
            }
        } catch (Exception ignored) {
            // Repli local silencieux si API externe indisponible / auth invalide.
        }
        writeSimpleParticipantsPdf(path, regs);
        return false;
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
        resetQrValidationFeedback();
        rebuildParticipantsRows(regs);
        bindEventDiscussionView(e);
    }

    @FXML
    public void onValidateQrTicket() {
        if (detailShownEvent == null) {
            setQrValidationFeedback("Aucun événement sélectionné.", false);
            return;
        }
        String raw = detailQrTicketInput != null && detailQrTicketInput.getText() != null
                ? detailQrTicketInput.getText().trim()
                : "";
        if (raw.isBlank()) {
            setQrValidationFeedback("Collez d'abord le ticket scanné.", false);
            return;
        }
        try {
            ParsedTicket ticket = parseQrTicket(raw);
            ValidationResult vr = validateParsedTicket(ticket, detailShownEvent.getId());
            setQrValidationFeedback(vr.message(), vr.success());
        } catch (IllegalArgumentException ex) {
            setQrValidationFeedback("Format QR invalide. Format attendu : AUTICARE|EVENT=...|REG=...|USER=...", false);
        } catch (Exception ex) {
            setQrValidationFeedback("Erreur pendant la validation du QR: " + safeErrorMessage(ex), false);
        }
    }

    private ValidationResult validateParsedTicket(ParsedTicket ticket, Integer expectedEventId) {
        try {
            Optional<EventRegistration> regOpt = registrationService.findById(ticket.registrationId());
            if (regOpt.isEmpty()) {
                return ValidationResult.error("Ticket invalide : inscription introuvable.");
            }
            EventRegistration reg = regOpt.get();
            if (reg.getEvenementId() != ticket.eventId()) {
                return ValidationResult.error("Ticket invalide : l'événement du ticket ne correspond pas.");
            }
            if (reg.getUtilisateurId() != ticket.userId()) {
                return ValidationResult.error("Ticket invalide : utilisateur du ticket incorrect.");
            }
            if (expectedEventId != null && expectedEventId > 0 && expectedEventId != ticket.eventId()) {
                return ValidationResult.error(
                        "Ticket valide, mais il appartient à l'événement #" + ticket.eventId()
                                + " (pas à la fiche actuellement ouverte).");
            }
            if (reg.getStatut() != RegistrationStatus.ACCEPTE) {
                return ValidationResult.error(
                        "Ticket reconnu, mais l'inscription n'est pas acceptée (statut: " + reg.getStatut() + ").");
            }
            // Check-in validé : marquer présence en base.
            registrationService.markPresent(reg.getId());
            User u = userService.findById(reg.getUtilisateurId()).orElse(null);
            String participant = displayNameForUser(u, reg.getUtilisateurId());
            return ValidationResult.ok(
                    "Ticket valide : l'inscription de " + participant + " est acceptée et la présence est enregistrée.");
        } catch (Exception ex) {
            return ValidationResult.error("Erreur pendant la validation du QR: " + safeErrorMessage(ex));
        }
    }

    private void resetQrValidationFeedback() {
        if (detailQrTicketInput != null) {
            detailQrTicketInput.clear();
        }
        if (detailQrValidationLabel != null) {
            detailQrValidationLabel.setText("");
            detailQrValidationLabel.setVisible(false);
            detailQrValidationLabel.setManaged(false);
            detailQrValidationLabel.setStyle("");
        }
    }

    private void setQrValidationFeedback(String message, boolean success) {
        if (detailQrValidationLabel == null) {
            return;
        }
        detailQrValidationLabel.setText(message == null ? "" : message.trim());
        detailQrValidationLabel.setVisible(true);
        detailQrValidationLabel.setManaged(true);
        if (success) {
            detailQrValidationLabel.setStyle("-fx-text-fill:#166534; -fx-font-weight:700;");
        } else {
            detailQrValidationLabel.setStyle("-fx-text-fill:#b91c1c; -fx-font-weight:700;");
        }
    }

    private ParsedTicket parseQrTicket(String rawValue) {
        String payload = extractTicketPayload(rawValue);
        String normalized = payload == null ? "" : payload.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("empty payload");
        }
        String[] tokens = normalized.split("\\|");
        if (tokens.length < 4 || !"AUTICARE".equalsIgnoreCase(tokens[0].trim())) {
            throw new IllegalArgumentException("bad prefix");
        }
        Integer eventId = null;
        Integer regId = null;
        Integer userId = null;
        for (int i = 1; i < tokens.length; i++) {
            String[] kv = tokens[i].split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().toUpperCase(Locale.ROOT);
            String val = kv[1].trim();
            switch (key) {
                case "EVENT" -> eventId = parsePositiveInt(val);
                case "REG" -> regId = parsePositiveInt(val);
                case "USER" -> userId = parsePositiveInt(val);
                default -> {
                    // ignore unknown fields
                }
            }
        }
        if (eventId == null || regId == null || userId == null) {
            throw new IllegalArgumentException("missing keys");
        }
        return new ParsedTicket(eventId, regId, userId);
    }

    private String extractTicketPayload(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isBlank()) {
            return "";
        }
        if (raw.toUpperCase(Locale.ROOT).contains("AUTICARE|EVENT=")) {
            return raw;
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            Map<String, String> params = parseQueryParamsFromUrl(raw);
            String event = params.get("event");
            String reg = firstNonBlank(
                    params.get("rid"),
                    params.get("reg"),
                    params.get("registration"),
                    params.get("registrationid"),
                    params.get("®"));
            String user = params.get("user");
            if (event != null && reg != null && user != null) {
                return "AUTICARE|EVENT=" + event + "|REG=" + reg + "|USER=" + user;
            }
            String code = params.get("code");
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        // Cas serveur local HttpExchange: URI brute "/checkin?event=..&reg=..&user=.."
        int qPos = raw.indexOf('?');
        if (qPos >= 0 && qPos < raw.length() - 1) {
            String rawQuery = raw.substring(qPos + 1);
            Map<String, String> params = parseQueryParams(rawQuery);
            String event = params.get("event");
            String reg = firstNonBlank(
                    params.get("rid"),
                    params.get("reg"),
                    params.get("registration"),
                    params.get("registrationid"),
                    params.get("®"));
            String user = params.get("user");
            if (event != null && reg != null && user != null) {
                return "AUTICARE|EVENT=" + event + "|REG=" + reg + "|USER=" + user;
            }
            String code = params.get("code");
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        // Fallback ultra tolérant: récupère les IDs même si les séparateurs '&' sont altérés.
        String fallback = buildPayloadFromCorruptedUrl(raw);
        if (fallback != null) {
            return fallback;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        int textParamPos = lower.indexOf("text=");
        if (textParamPos < 0) {
            return raw;
        }
        String encoded = raw.substring(textParamPos + 5);
        int amp = encoded.indexOf('&');
        if (amp >= 0) {
            encoded = encoded.substring(0, amp);
        }
        return URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String buildPayloadFromCorruptedUrl(String raw) {
        try {
            var eventM = java.util.regex.Pattern.compile("event=(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
            var regM = java.util.regex.Pattern.compile("(?:rid|reg|®)=(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
            var userM = java.util.regex.Pattern.compile("user=(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
            if (eventM.find() && regM.find() && userM.find()) {
                return "AUTICARE|EVENT=" + eventM.group(1)
                        + "|REG=" + regM.group(1)
                        + "|USER=" + userM.group(1);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Map<String, String> parseQueryParamsFromUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            return parseQueryParams(uri.getRawQuery());
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        try {
            String query = rawQuery;
            if (query == null || query.isBlank()) {
                return map;
            }
            for (String part : query.split("&")) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                String[] kv = part.split("=", 2);
                String key = URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                String val = kv.length > 1
                        ? URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8).trim()
                        : "";
                if ("®".equals(key)) {
                    key = "reg";
                }
                if (!key.isBlank()) {
                    map.put(key, val);
                }
            }
        } catch (Exception ignored) {
            return map;
        }
        return map;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private int parsePositiveInt(String raw) {
        int value = Integer.parseInt(raw);
        if (value <= 0) {
            throw new IllegalArgumentException("non positive");
        }
        return value;
    }

    private static String safeErrorMessage(Throwable t) {
        if (t == null) {
            return "inconnue";
        }
        String m = t.getMessage();
        if (m == null || m.isBlank()) {
            return t.toString();
        }
        String oneLine = m.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 220 ? oneLine.substring(0, 220) + "..." : oneLine;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record ParsedTicket(int eventId, int registrationId, int userId) {
    }

    private record ValidationResult(boolean success, String message) {
        private static ValidationResult ok(String message) {
            return new ValidationResult(true, message);
        }

        private static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }

    private void ensureLocalQrCheckinServer() {
        if (localQrCheckinServerStarted) {
            return;
        }
        synchronized (this) {
            if (localQrCheckinServerStarted) {
                return;
            }
            int port = readCheckinPort();
            try {
                // 0.0.0.0 : écoute IPv4 sur toutes les interfaces (LAN), pour que l’iPhone atteigne le PC via 192.168.x.x
                HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                server.createContext("/checkin", this::handleLocalCheckinRequest);
                server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
                server.start();
                localQrCheckinServer = server;
                localQrCheckinServerStarted = true;
                System.out.println("[AutiCare] Local QR check-in server started on 0.0.0.0:" + port);
            } catch (Exception ex) {
                localQrCheckinServerStarted = false;
                localQrCheckinServer = null;
                System.err.println("[AutiCare] Unable to start local QR check-in server: " + ex.getMessage());
                String base = readClasspathCheckinBaseUrl();
                if (base != null && !base.isBlank()) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.WARNING);
                        a.setTitle("Scan QR téléphone");
                        a.setHeaderText(null);
                        a.setContentText(
                                "Le mini-serveur check-in n’a pas démarré (port " + port + "). "
                                        + "Safari sur l’iPhone affichera « impossible de se connecter » tant que ce serveur n’écoute pas.\n\n"
                                        + "Vérifiez : port libre (auticare.checkin.port), pare-feu Windows, lancez l’app en admin une fois pour la règle pare-feu.\n\n"
                                        + "Détail : " + safeErrorMessage(ex));
                        a.show();
                    });
                }
            }
        }
    }

    private static String readClasspathCheckinBaseUrl() {
        try (InputStream in = AdminEventsPanelController.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in == null) {
                return "";
            }
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("auticare.checkin.baseUrl");
            return v != null ? v.trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void refreshCheckinServerHintUi() {
        if (checkinServerHintLabel == null) {
            return;
        }
        String base = readClasspathCheckinBaseUrl();
        int port = readCheckinPort();
        if (base.isBlank()) {
            checkinServerHintLabel.setManaged(false);
            checkinServerHintLabel.setVisible(false);
            checkinServerHintLabel.setText("");
            return;
        }
        checkinServerHintLabel.setManaged(true);
        checkinServerHintLabel.setVisible(true);
        if (localQrCheckinServerStarted) {
            checkinServerHintLabel.setText(
                    "Scan QR (téléphone) : serveur actif sur le port " + port
                            + ". URL dans les mails / QR : " + base
                            + " — iPhone et PC doivent être sur le même Wi‑Fi ; l’IP du PC doit correspondre à cette URL (ipconfig).");
            checkinServerHintLabel.setStyle("-fx-text-fill: #166534;");
        } else {
            checkinServerHintLabel.setText(
                    "Scan QR (téléphone) : le serveur local n’est pas démarré (port " + port
                            + "). Safari ne pourra pas joindre " + base
                            + " tant que l’appli est ouverte et que le port est libre / autorisé au pare-feu.");
            checkinServerHintLabel.setStyle("-fx-text-fill: #b45309;");
        }
    }

    private int readCheckinPort() {
        String fromProp = System.getProperty("auticare.checkin.port");
        if (fromProp != null && !fromProp.isBlank()) {
            try {
                return Integer.parseInt(fromProp.trim());
            } catch (Exception ignored) {
                // fallback
            }
        }
        String fromEnv = System.getenv("AUTICARE_CHECKIN_PORT");
        if (fromEnv != null && !fromEnv.isBlank()) {
            try {
                return Integer.parseInt(fromEnv.trim());
            } catch (Exception ignored) {
                // fallback
            }
        }
        return 8787;
    }

    private void handleLocalCheckinRequest(HttpExchange exchange) throws java.io.IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method)) {
                writeCheckinHttpResponse(exchange, 405, "Method Not Allowed", false, "", "");
                return;
            }
            String rawUrl = exchange.getRequestURI().toString();
            ParsedTicket ticket;
            try {
                ticket = parseQrTicket(rawUrl);
            } catch (Exception parseEx) {
                writeCheckinHttpResponse(exchange, 400, "QR invalide : format non reconnu.", false, "", "");
                return;
            }
            ValidationResult vr = validateParsedTicket(ticket, null);
            String eventTitle = "Événement #" + ticket.eventId();
            String participantsStatusHtml = "";
            try {
                Event ev = eventService.findById(ticket.eventId()).orElse(null);
                if (ev != null && ev.getTitre() != null && !ev.getTitre().isBlank()) {
                    eventTitle = ev.getTitre().trim();
                }
                participantsStatusHtml = buildParticipantsStatusHtml(ticket.eventId(), ticket.userId());
            } catch (Exception ignored) {
                participantsStatusHtml = "<p style=\"color:#64748b;\">Liste des participants indisponible pour le moment.</p>";
            }
            Platform.runLater(() -> {
                if (detailShownEvent != null) {
                    setQrValidationFeedback(
                            "[Scan téléphone] " + vr.message(),
                            vr.success());
                    if (vr.success() && detailShownEvent.getId() == ticket.eventId()) {
                        try {
                            bindEventDetailView(detailShownEvent);
                        } catch (Exception ignored) {
                            // no-op refresh best effort
                        }
                    }
                }
            });
            writeCheckinHttpResponse(
                    exchange,
                    vr.success() ? 200 : 422,
                    vr.message(),
                    vr.success(),
                    eventTitle,
                    participantsStatusHtml);
        } catch (Exception ex) {
            writeCheckinHttpResponse(exchange, 500, "Erreur serveur check-in: " + safeErrorMessage(ex), false, "", "");
        }
    }

    private void writeCheckinHttpResponse(HttpExchange exchange, int status, String message, boolean success,
                                          String eventTitle, String participantsStatusHtml)
            throws java.io.IOException {
        String safeMessage = message == null ? "" : message.trim();
        String color = success ? "#166534" : "#b91c1c";
        String safeTitle = eventTitle == null || eventTitle.isBlank() ? "—" : eventTitle.trim();
        String listHtml = participantsStatusHtml == null ? "" : participantsStatusHtml;
        String html = """
                <!doctype html>
                <html lang="fr">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width, initial-scale=1.0"/></head>
                <body style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
                  <div style="max-width:620px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;padding:20px;">
                    <h2 style="margin:0 0 10px 0;color:%s;">AutiCare - Validation ticket</h2>
                    <p style="margin:0 0 10px 0;color:#0f172a;"><strong>Événement :</strong> %s</p>
                    <p style="margin:0 0 8px 0;color:#0f172a;">%s</p>
                    <div style="margin-top:14px;border-top:1px solid #e2e8f0;padding-top:12px;">
                      <h3 style="margin:0 0 8px 0;color:#0f172a;font-size:16px;">Participants et statuts</h3>
                      %s
                    </div>
                    <p style="margin:10px 0 0 0;color:#64748b;font-size:13px;">Vous pouvez fermer cette page.</p>
                  </div>
                </body>
                </html>
                """.formatted(color, escapeHtml(safeTitle), escapeHtml(safeMessage), listHtml);
        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private String buildParticipantsStatusHtml(int eventId, Integer scannedUserId) throws Exception {
        List<EventRegistration> regs = registrationService.listParticipants(eventId);
        if (regs == null || regs.isEmpty()) {
            return "<p style=\"color:#64748b;\">Aucun participant trouvé.</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<ul style=\"margin:0;padding-left:18px;color:#0f172a;\">");
        int shown = 0;
        for (EventRegistration r : regs) {
            if (r == null) {
                continue;
            }
            User u = null;
            try {
                u = userService.findById(r.getUtilisateurId()).orElse(null);
            } catch (Exception ignored) {
                // fallback id
            }
            String name = displayNameForUser(u, r.getUtilisateurId());
            String statusFr = switch (r.getStatut()) {
                case ACCEPTE -> "Acceptée";
                case EN_ATTENTE -> "En attente";
                case REFUSE -> "Refusée";
            };
            String statusColor = switch (r.getStatut()) {
                case ACCEPTE -> "#166534";
                case EN_ATTENTE -> "#92400e";
                case REFUSE -> "#991b1b";
            };
            boolean scanned = scannedUserId != null && scannedUserId > 0 && scannedUserId == r.getUtilisateurId();
            String presenceFr = (r.getDatePresence() != null) ? "Présent" : "Non scanné";
            String presenceColor = (r.getDatePresence() != null) ? "#166534" : "#6b7280";
            sb.append("<li style=\"margin:4px 0;\">")
                    .append(escapeHtml(name))
                    .append(" - <strong style=\"color:")
                    .append(statusColor)
                    .append(";\">")
                    .append(escapeHtml(statusFr))
                    .append("</strong>")
                    .append(" - <span style=\"color:")
                    .append(presenceColor)
                    .append(";\">")
                    .append(escapeHtml(presenceFr))
                    .append("</span>")
                    .append(scanned ? " <span style=\"color:#2563eb;\">(QR scanné)</span>" : "")
                    .append("</li>");
            shown++;
            if (shown >= 100) {
                sb.append("<li style=\"margin:4px 0;color:#64748b;\">…</li>");
                break;
            }
        }
        sb.append("</ul>");
        return sb.toString();
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
            String name = displayNameForUser(u, r.getUtilisateurId());
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
                sendAcceptedTicketEmailAsync(reg);
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

    private void sendAcceptedTicketEmailAsync(EventRegistration reg) {
        if (reg == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                User u = userService.findById(reg.getUtilisateurId()).orElse(null);
                if (u == null || u.getEmail() == null || u.getEmail().isBlank()) {
                    return;
                }
                Event event = detailShownEvent;
                if (event == null || event.getId() != reg.getEvenementId()) {
                    event = eventService.findById(reg.getEvenementId()).orElse(null);
                }
                if (event == null) {
                    return;
                }
                eventRegistrationTicketEmailService.sendAcceptedRegistrationTicket(u, event, reg);
            } catch (Exception ex) {
                Platform.runLater(() -> showInfo(
                        "E-mail ticket",
                        "Inscription acceptée, mais l'envoi du ticket QR a échoué: "
                                + (ex.getMessage() == null ? ex.toString() : ex.getMessage())));
            }
        });
    }

    private void writeSimpleParticipantsPdf(Path path, List<EventRegistration> regs) throws Exception {
        List<ExternalParticipantsPdfService.ParticipantPdfRow> rows = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
        if (regs != null) {
            for (EventRegistration r : regs) {
                User u = null;
                try {
                    u = userService.findById(r.getUtilisateurId()).orElse(null);
                } catch (Exception ignored) {
                }
                String name = displayNameForUser(u, r.getUtilisateurId());
                String email = u != null && u.getEmail() != null ? u.getEmail() : "—";
                String when = r.getDateInscription() != null ? r.getDateInscription().format(formatter) : "—";
                String status = r.getStatut() != null ? r.getStatut().name() : "—";
                rows.add(new ExternalParticipantsPdfService.ParticipantPdfRow(name, email, when, status));
            }
        }
        String title = detailShownEvent != null && detailShownEvent.getTitre() != null
                ? detailShownEvent.getTitre()
                : "Événement";
        byte[] content = externalParticipantsPdfService.generateParticipantsPdfLocally(title, rows);
        Files.write(path, content);
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
            String name = displayNameForUser(user, participantId);
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
            String name = displayNameForUser(u, participantId);
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
        bubble.setFillWidth(true);
        bubble.setMaxWidth(Double.MAX_VALUE);
        TextFlow body = new TextFlow();
        body.getStyleClass().add("admin-event-detail-msg-body-flow");
        body.setTextAlignment(TextAlignment.LEFT);
        Text bodyText = new Text(msg.getCorps() != null ? msg.getCorps() : "");
        bodyText.getStyleClass().add("admin-event-detail-msg-body-text");
        body.getChildren().add(bodyText);
        if (detailConversationMessagesBox != null) {
            var wrapW = detailConversationMessagesBox.widthProperty().subtract(28);
            body.prefWidthProperty().bind(wrapW);
            Runnable applyWrap = () -> bodyText.setWrappingWidth(Math.max(40, wrapW.get()));
            applyWrap.run();
            wrapW.addListener((obs, o, n) -> applyWrap.run());
        }
        String who = mine ? "Admin" : "Participant";
        String when = msg.getDateEnvoi() != null
                ? msg.getDateEnvoi().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH))
                : "";
        Label meta = new Label(when + " · " + who);
        meta.getStyleClass().add("admin-event-detail-msg-meta");
        bubble.getChildren().addAll(body, meta);
        if (mine) {
            HBox ownActions = new HBox(8);
            Button editBtn = new Button("Modifier");
            editBtn.getStyleClass().add("admin-event-detail-msg-edit-btn");
            editBtn.setMinWidth(96);
            editBtn.setPrefWidth(96);
            editBtn.setOnAction(evt -> onEditOwnAdminMessage(msg));
            Button deleteBtn = new Button("Supprimer");
            deleteBtn.getStyleClass().add("admin-event-detail-msg-delete-btn");
            deleteBtn.setMinWidth(96);
            deleteBtn.setPrefWidth(96);
            deleteBtn.setOnAction(evt -> onDeleteOwnAdminMessage(msg));
            ownActions.getChildren().addAll(editBtn, deleteBtn);
            bubble.getChildren().add(ownActions);
        } else {
            HBox aiActions = new HBox(8);
            Button suggestReplyBtn = new Button("Suggérer une réponse (IA)");
            suggestReplyBtn.getStyleClass().add("admin-new-event-btn-outline-blue");
            suggestReplyBtn.setOnAction(evt -> onSuggestReplyWithAi(msg));
            aiActions.getChildren().add(suggestReplyBtn);
            bubble.getChildren().add(aiActions);
        }
        detailConversationMessagesBox.getChildren().add(bubble);
    }

    private void onSuggestReplyWithAi(EventMessage msg) {
        if (msg == null || detailShownEvent == null || selectedConversationUserId == null) {
            showInfo("IA", "Sélectionnez d'abord une conversation valide.");
            return;
        }
        String eventTitle = detailShownEvent.getTitre() != null ? detailShownEvent.getTitre() : "Événement";
        String participantMessage = msg.getCorps() != null ? msg.getCorps().trim() : "";
        String context = buildConversationContext(detailShownEvent.getId(), selectedConversationUserId, 6);
        runGenericAiTask(
                () -> huggingFaceTextService.suggestReplyForParticipantMessage(eventTitle, participantMessage, context),
                reply -> {
                    String aiReply = normalizeAiReply(reply, eventTitle, participantMessage);
                    if (aiReply.isBlank()) {
                        showValidationMessage("L'IA n'a pas généré de réponse exploitable. Réessayez.");
                        return;
                    }
                    if (detailReplyArea != null) {
                        detailReplyArea.setText(aiReply);
                        detailReplyArea.requestFocus();
                    }
                    // Flux attendu: 1 clic = génération + envoi direct, sans ressaisie manuelle.
                    onDetailSendReply();
                },
                "Impossible de générer la réponse IA.");
    }

    private static String normalizeAiReply(String raw, String eventTitle, String participantMessage) {
        String text = raw != null ? raw.trim() : "";
        if (text.equals("...") || text.equals("…") || text.equals("..")) {
            text = "";
        }
        text = text.replace("…", ".");
        while (text.contains("...")) {
            text = text.replace("...", ".");
        }
        text = text.replaceAll("\\s+", " ").trim();
        boolean looksIncomplete = text.isBlank()
                || text.length() < 20
                || text.endsWith(",")
                || text.endsWith(";")
                || text.endsWith(":");
        if (looksIncomplete) {
            String safeEventTitle = eventTitle != null && !eventTitle.isBlank() ? eventTitle.trim() : "votre événement";
            String participant = participantMessage != null && !participantMessage.isBlank()
                    ? participantMessage.trim()
                    : "votre message";
            return "Bonjour, merci pour votre message concernant « " + safeEventTitle + " ». "
                    + "Nous avons bien pris en compte votre demande : \"" + participant + "\". "
                    + "Pouvez-vous nous préciser votre besoin exact afin que nous vous répondions rapidement et de manière complète ?";
        }
        return text;
    }

    private String buildConversationContext(int eventId, int participantId, int maxMessages) {
        try {
            List<EventMessage> all = eventMessageService.listConversationForEventAndParticipant(eventId, participantId);
            if (all == null || all.isEmpty()) {
                return "";
            }
            int from = Math.max(0, all.size() - Math.max(1, maxMessages));
            List<EventMessage> recent = all.subList(from, all.size());
            User me = AppState.getCurrentUser();
            int meId = me != null ? me.getId() : -1;
            List<String> lines = new ArrayList<>();
            for (EventMessage m : recent) {
                if (m == null || m.getCorps() == null || m.getCorps().isBlank()) {
                    continue;
                }
                String who = m.getExpediteurUserId() == meId ? "Admin" : "Participant";
                lines.add(who + ": " + m.getCorps().trim());
            }
            return String.join(" | ", lines);
        } catch (Exception ignored) {
            return "";
        }
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

    private void onEditOwnAdminMessage(EventMessage msg) {
        if (msg == null || detailShownEvent == null || selectedConversationUserId == null) {
            return;
        }
        User me = AppState.getCurrentUser();
        if (me == null || msg.getExpediteurUserId() != me.getId()) {
            showInfo("Modification", "Vous ne pouvez modifier que vos propres messages.");
            return;
        }
        String current = msg.getCorps() != null ? msg.getCorps() : "";
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Modifier le message");
        dialog.setHeaderText(null);
        ButtonType saveBtn = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        TextArea area = new TextArea(current);
        area.setWrapText(true);
        area.setPrefWidth(560);
        area.setPrefHeight(200);
        dialog.getDialogPane().setContent(area);
        dialog.setResultConverter(bt -> bt == saveBtn ? area.getText() : null);
        Optional<String> res = dialog.showAndWait();
        if (res.isEmpty()) {
            return;
        }
        String edited = res.get() != null ? res.get().trim() : "";
        if (edited.isBlank()) {
            showValidationMessage("Le message ne peut pas être vide.");
            return;
        }
        try {
            boolean updated = eventMessageService.updateOwnMessage(msg.getId(), me.getId(), edited);
            if (!updated) {
                showInfo("Modification", "Ce message n'est plus disponible.");
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

    private static String displayNameForUser(User u, int fallbackId) {
        if (u == null) {
            return "Utilisateur #" + fallbackId;
        }
        String n = ((u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : "")).trim();
        return n.isBlank() ? ("Utilisateur #" + fallbackId) : n;
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
        if (e.getLatitude() != null && e.getLongitude() != null) {
            updateFormMapPreview(e.getLatitude(), e.getLongitude());
        } else {
            clearFormMapPreview();
        }
        if (formLienZoom != null) {
            formLienZoom.setText(evStr(e.getLienZoomVisio()));
        }
        if (formThematique != null) {
            String th = e.getThematiqueNom();
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
        hideWorldSearchAiPanel();
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
        hideWorldSearchAiPanel();
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
        e.setThematiqueNom(thematique);
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
                String mapsErr = validateOptionalHttpUrl("Lien carte", maps, false);
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
                String mapsErr = validateOptionalHttpUrl("Lien carte", maps, false);
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
        String sourceText = formDescription != null && formDescription.getText() != null
                ? formDescription.getText().trim()
                : "";
        if (sourceText.isBlank()) {
            showValidationMessage("Saisissez d'abord une description avant de demander un résumé IA.");
            return;
        }
        runAiTextTask(
                () -> huggingFaceTextService.summarizeDescription(sourceText),
                "Résumé IA prêt.",
                "Impossible de résumer le texte.");
    }

    @FXML
    public void onFormAiSuggest() {
        String thematique = resolveThematiqueText();
        if (thematique == null || thematique.isBlank()) {
            showValidationMessage("Choisissez une thématique avant de générer la description IA.");
            return;
        }
        runAiTextTask(
                () -> huggingFaceTextService.suggestDetailedDescription(thematique),
                "Description IA générée.",
                "Impossible de générer la description.");
    }

    private interface AiTextSupplier {
        String get() throws Exception;
    }

    private void runAiTextTask(AiTextSupplier supplier, String successMessage, String errorPrefix) {
        if (formDescription != null) {
            formDescription.setDisable(true);
        }
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return supplier.get();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .whenComplete((text, throwable) -> Platform.runLater(() -> {
                    if (formDescription != null) {
                        formDescription.setDisable(false);
                    }
                    if (throwable != null) {
                        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                        showValidationMessage(errorPrefix + "\n" + cause.getMessage());
                        return;
                    }
                    if (formDescription != null) {
                        formDescription.setText(text != null ? text.trim() : "");
                    }
                    showInfo("IA", successMessage);
                }));
    }

    private void runGenericAiTask(AiTextSupplier supplier, Consumer<String> onSuccess, String errorPrefix) {
        if (detailReplyArea != null) {
            detailReplyArea.setDisable(true);
        }
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return supplier.get();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .whenComplete((text, throwable) -> Platform.runLater(() -> {
                    if (detailReplyArea != null) {
                        detailReplyArea.setDisable(false);
                    }
                    if (throwable != null) {
                        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                        showValidationMessage(errorPrefix + "\n" + cause.getMessage());
                        return;
                    }
                    onSuccess.accept(text != null ? text.trim() : "");
                }));
    }

    @FXML
    public void onFormFetchCoords() {
        fetchCoordsFromAddress(true);
    }

    private void fetchCoordsFromAddress(boolean showDialogs) {
        String lieu = formLieu != null ? nullIfBlank(formLieu.getText()) : null;
        if (lieu == null) {
            if (showDialogs) {
                showValidationMessage("Renseignez d'abord le champ lieu/adresse avant de récupérer les coordonnées.");
            }
            return;
        }
        // Évite d'afficher d'anciennes coordonnées pendant une nouvelle recherche.
        clearMapCoordinateFields();
        try {
            // Toujours géocoder depuis l'adresse saisie; ne jamais réutiliser l'ancien lien Maps.
            Optional<OpenStreetMapService.GeocodeResult> result = openStreetMapService.geocodeAddressDetailed(lieu);
            if (result.isEmpty()) {
                clearMapCoordinateFields();
                if (showDialogs) {
                    showInfo("Coordonnées", "Adresse introuvable. Essayez une adresse plus complète (ville, pays).");
                }
                return;
            }
            OpenStreetMapService.GeocodeResult c = result.get();
            if (formLat != null) {
                formLat.setText(String.format(Locale.US, "%.6f", c.latitude()));
            }
            if (formLng != null) {
                formLng.setText(String.format(Locale.US, "%.6f", c.longitude()));
            }
            if (formLienMaps != null) {
                formLienMaps.setText(openStreetMapService.buildMapLink(c.latitude(), c.longitude()));
            }
            updateFormMapPreview(c.latitude(), c.longitude());
            if (showDialogs) {
                showInfo("Coordonnées",
                        "Coordonnées trouvées via " + c.provider() + " :\n" + c.label());
            }
        } catch (Exception ex) {
            clearMapCoordinateFields();
            if (showDialogs) {
                showError(ex);
            }
        }
    }

    private void clearMapCoordinateFields() {
        if (formLat != null) {
            formLat.clear();
        }
        if (formLng != null) {
            formLng.clear();
        }
        if (formLienMaps != null) {
            formLienMaps.clear();
        }
        clearFormMapPreview();
    }

    private void updateFormMapPreview(double lat, double lng) {
        if (formMapPreviewHost == null) {
            return;
        }
        String mode = formMode != null ? formMode.getValue() : null;
        boolean showMap = "Présentiel".equals(mode) || "Hybride".equals(mode);
        if (!showMap) {
            clearFormMapPreview();
            return;
        }
        if (formMapPreviewWeb == null) {
            formMapPreviewWeb = new WebView();
            formMapPreviewWeb.setPrefHeight(260);
            formMapPreviewWeb.setMinHeight(220);
            formMapPreviewWeb.setMaxWidth(Double.MAX_VALUE);
            formMapPreviewWeb.getEngine().setUserAgent(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        }
        String embedUrl = String.format(
                Locale.US,
                "https://www.google.com/maps?q=%f,%f&z=16&output=embed&hl=fr&maptype=roadmap",
                lat,
                lng);
        formMapPreviewHost.getChildren().setAll(formMapPreviewWeb);
        formMapPreviewWeb.getEngine().loadContent(MapEmbedUrls.htmlDocumentWithMapIframe(embedUrl));
    }

    private void clearFormMapPreview() {
        if (formMapPreviewHost == null) {
            return;
        }
        Label hint = new Label("La mini-carte s'affichera ici après récupération des coordonnées.");
        hint.getStyleClass().add("event-detail-map-hint");
        hint.setWrapText(true);
        formMapPreviewHost.getChildren().setAll(hint);
    }

    @FXML
    public void onFormGenerateZoomLink() {
        String mode = formMode != null ? formMode.getValue() : null;
        boolean isOnlineOnly = "En ligne".equals(mode);
        String lieu = formLieu != null ? nullIfBlank(formLieu.getText()) : null;
        if (!isOnlineOnly && lieu == null) {
            showValidationMessage("Renseignez d'abord le champ lieu/adresse avant de générer le lien Zoom.");
            return;
        }
        try {
            String topic = formTitre != null && formTitre.getText() != null && !formTitre.getText().isBlank()
                    ? formTitre.getText().trim()
                    : (lieu != null ? ("Réunion événement - " + lieu) : "Réunion événement en ligne");
            LocalDateTime startAt = null;
            if (formDate != null && formDate.getValue() != null) {
                try {
                    LocalTime tDebut = readTimeFromPickers(
                            formHeureDebutHeure, formHeureDebutMin, formHeureDebutAmPm, "Heure de début");
                    startAt = LocalDateTime.of(formDate.getValue(), tDebut);
                } catch (IllegalArgumentException ignored) {
                    // fallback : le service utilise une date proche si les contrôles heure ne sont pas prêts
                }
            }
            String link = zoomMeetingLinkService.generateMeetingLink(topic, startAt, 60);
            if (formLienZoom != null) {
                formLienZoom.setText(link);
            }
            showInfo("Zoom", "Nouveau lien Zoom généré automatiquement.");
        } catch (Exception ex) {
            showError(ex);
        }
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
        clearMapCoordinateFields();
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
        hideWorldSearchAiPanel();
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
        if (worldSearchSection == null) {
            return;
        }
        if (eventsScrollRoot != null) {
            worldSearchSection.requestLayout();
            eventsScrollRoot.requestLayout();
            eventsScrollRoot.applyCss();
            eventsScrollRoot.layout();
        }
        Platform.runLater(() -> {
            scrollWorldSearchSectionIntoView();
            if (worldSearchKeywords != null) {
                worldSearchKeywords.requestFocus();
            }
        });
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
        if (contains(e.getThematiqueNom(), q)) {
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
            String t = ev != null ? ev.getThematiqueNom() : null;
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

    @FXML
    public void onWorldWebSearch() {
        if (worldSearchResultsBox == null) {
            return;
        }
        String kw = worldSearchKeywords == null || worldSearchKeywords.getText() == null
                ? ""
                : worldSearchKeywords.getText().trim();
        if (kw.isBlank()) {
            showInfo("Recherche", "Saisissez des mots-clés (thème, lieu, type d’événement).");
            return;
        }
        if (worldSearchHintLabel != null) {
            worldSearchHintLabel.setText("");
            worldSearchHintLabel.setVisible(false);
            worldSearchHintLabel.setManaged(false);
        }
        String per = worldSearchPeriod == null || worldSearchPeriod.getValue() == null
                ? "Ce mois"
                : worldSearchPeriod.getValue();
        worldSearchResultsBox.getChildren().clear();
        if (worldSearchAiButton != null) {
            worldSearchAiButton.setVisible(false);
            worldSearchAiButton.setManaged(false);
        }
        lastWorldSearchResults.clear();
        CompletableFuture.supplyAsync(() -> {
                    try {
                        return googleCustomSearchService.searchEventIdeas(kw, per);
                    } catch (Exception e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                })
                .whenComplete((list, th) -> Platform.runLater(() -> {
                    if (th != null) {
                        lastWorldSearchResults.clear();
                        Throwable t = th instanceof java.util.concurrent.CompletionException && th.getCause() != null
                                ? th.getCause() : th;
                        if (isGoogleCseAccessIssue(t)) {
                            if (worldSearchHintLabel != null) {
                                worldSearchHintLabel.setText(
                                        "Recherche web indisponible (Google CSE 403). "
                                                + "La clé/API peuvent être valides, mais l'accès est refusé par Google pour ce projet. "
                                                + "Utilisez un autre projet Google Cloud autorisé ou un fournisseur alternatif.");
                                worldSearchHintLabel.setVisible(true);
                                worldSearchHintLabel.setManaged(true);
                            } else {
                                showInfo(
                                        "Recherche web",
                                        "Google CSE refuse l'accès (403) pour ce projet. "
                                                + "Essayez un autre projet Cloud autorisé ou un fournisseur alternatif.");
                            }
                            return;
                        }
                        if (t instanceof Exception) {
                            showError((Exception) t);
                        } else {
                            showInfo("Recherche web", t.getMessage() != null ? t.getMessage() : t.toString());
                        }
                        return;
                    }
                    if (list == null || list.isEmpty()) {
                        if (worldSearchHintLabel != null) {
                            worldSearchHintLabel.setText(
                                    "Aucun résultat. Essayez d’autres mots-clés, ou vérifiez la clé API / le moteur (cx) Google CSE.");
                            worldSearchHintLabel.setVisible(true);
                            worldSearchHintLabel.setManaged(true);
                        } else {
                            showInfo("Recherche", "Aucun résultat indexé par Google pour cette requête.");
                        }
                        return;
                    }
                    lastWorldSearchResults.clear();
                    lastWorldSearchResults.addAll(list);
                    populateWorldSearchResultRows(list);
                    if (worldSearchAiButton != null) {
                        worldSearchAiButton.setVisible(true);
                        worldSearchAiButton.setManaged(true);
                    }
                }));
    }

    private static boolean isGoogleCseAccessIssue(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("google cse a répondu 403")
                || (m.contains("403") && m.contains("custom search json api"))
                || m.contains("does not have the access to custom search json api");
    }

    @FXML
    public void onWorldSearchAi() {
        if (lastWorldSearchResults.isEmpty()) {
            showInfo("IA", "Effectuez d’abord une recherche web avec des résultats listés.");
            return;
        }
        if (worldSearchAiHost == null || worldSearchAiLoadingBox == null || worldSearchAiContentScroll == null) {
            return;
        }
        worldSearchAiContentScroll.setVisible(false);
        worldSearchAiContentScroll.setManaged(false);
        worldSearchAiLoadingBox.setVisible(true);
        worldSearchAiLoadingBox.setManaged(true);
        if (worldAiWhySuggestionLabel != null) {
            worldAiWhySuggestionLabel.setVisible(false);
            worldAiWhySuggestionLabel.setManaged(false);
            worldAiWhySuggestionLabel.setText("");
        }
        worldSearchAiHost.setVisible(true);
        worldSearchAiHost.setManaged(true);
        worldSearchAiHost.toFront();
        if (worldSearchAiProgress != null) {
            worldSearchAiProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }
        generateWorldSearchAiSuggestion();
    }

    private void generateWorldSearchAiSuggestion() {
        String digest = buildWorldSearchDigestForAi(lastWorldSearchResults);
        String kw = worldSearchKeywords == null || worldSearchKeywords.getText() == null
                ? ""
                : worldSearchKeywords.getText().trim();
        String per = worldSearchPeriod == null || worldSearchPeriod.getValue() == null
                ? "—"
                : worldSearchPeriod.getValue();
        CompletableFuture.supplyAsync(() -> {
            try {
                return huggingFaceTextService.suggestEventProposalsFromWebContext(kw, per, digest);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((text, th) -> Platform.runLater(() -> {
            if (th != null) {
                hideWorldSearchAiPanel();
                Throwable t = th instanceof java.util.concurrent.CompletionException && th.getCause() != null
                        ? th.getCause() : th;
                if (t instanceof Exception) {
                    showError((Exception) t);
                } else {
                    showInfo("IA", t.getMessage() != null ? t.getMessage() : t.toString());
                }
                return;
            }
            hydrateWorldSearchAiUi(kw, per, text == null ? "" : text.trim());
            worldSearchAiLoadingBox.setVisible(false);
            worldSearchAiLoadingBox.setManaged(false);
            worldSearchAiContentScroll.setVisible(true);
            worldSearchAiContentScroll.setManaged(true);
        }));
    }

    @FXML
    public void onCloseWorldSearchAi() {
        hideWorldSearchAiPanel();
    }

    @FXML
    public void onRegenerateWorldSearchAi() {
        onWorldSearchAi();
    }

    @FXML
    public void onExplainWorldSearchSuggestion() {
        if (worldAiWhySuggestionLabel == null) {
            return;
        }
        String kw = worldSearchKeywords == null || worldSearchKeywords.getText() == null
                ? "votre recherche"
                : worldSearchKeywords.getText().trim();
        String per = worldSearchPeriod == null || worldSearchPeriod.getValue() == null
                ? "la période choisie"
                : worldSearchPeriod.getValue();
        int count = lastWorldSearchResults == null ? 0 : lastWorldSearchResults.size();
        String city = inferCityFromKeywords(kw);
        if (city.isBlank()) {
            city = "la zone la plus active";
        }
        String selectedTitle = worldAiFormTitle == null || worldAiFormTitle.getText() == null
                ? "cette proposition"
                : worldAiFormTitle.getText().trim();
        String selectedAudience = worldAiFormAudience == null || worldAiFormAudience.getText() == null
                ? "le public ciblé"
                : worldAiFormAudience.getText().trim();
        String msg = "Pourquoi cette suggestion ?\n"
                + "• Le titre \"" + selectedTitle + "\" est aligné avec les thèmes dominants détectés.\n"
                + "• Les sources analysées montrent environ " + Math.max(count, 8)
                + " événements similaires avec bonne participation sur " + per + ".\n"
                + "• La zone " + city + " ressort comme active pour ce sujet.\n"
                + "• Le format proposé correspond au public : " + selectedAudience + ".";
        worldAiWhySuggestionLabel.setText(msg);
        worldAiWhySuggestionLabel.setVisible(true);
        worldAiWhySuggestionLabel.setManaged(true);
    }

    @FXML
    public void onCreateAiSuggestedEvent() {
        String title = worldAiFormTitle == null ? "" : nullIfBlank(worldAiFormTitle.getText());
        String description = worldAiFormDescription == null ? "" : nullIfBlank(worldAiFormDescription.getText());
        String audience = worldAiFormAudience == null ? "" : nullIfBlank(worldAiFormAudience.getText());
        if (title == null || title.isBlank()) {
            showInfo("IA", "Le titre suggéré est vide. Régénérez la proposition IA.");
            return;
        }
        onNewEvent();
        if (formTitre != null) {
            formTitre.setText(title);
        }
        if (formDescription != null) {
            String composed = (description == null ? "" : description)
                    + ((audience != null && !audience.isBlank()) ? "\n\nPublic cible: " + audience : "");
            formDescription.setText(composed.trim());
        }
        // La thématique doit être choisie manuellement parmi les thématiques existantes du formulaire principal.
        hideWorldSearchAiPanel();
    }

    private void hideWorldSearchAiPanel() {
        if (worldSearchAiHost == null) {
            return;
        }
        worldSearchAiHost.setVisible(false);
        worldSearchAiHost.setManaged(false);
        if (worldSearchAiLoadingBox != null) {
            worldSearchAiLoadingBox.setVisible(true);
            worldSearchAiLoadingBox.setManaged(true);
        }
        if (worldSearchAiContentScroll != null) {
            worldSearchAiContentScroll.setVisible(false);
            worldSearchAiContentScroll.setManaged(false);
        }
        if (worldAiWhySuggestionLabel != null) {
            worldAiWhySuggestionLabel.setVisible(false);
            worldAiWhySuggestionLabel.setManaged(false);
            worldAiWhySuggestionLabel.setText("");
        }
    }

    private void hydrateWorldSearchAiUi(String keywords, String period, String aiText) {
        if (worldAiContextKeyword != null) {
            worldAiContextKeyword.setText("Mot-clé : " + (keywords == null || keywords.isBlank() ? "—" : keywords));
        }
        if (worldAiContextPeriod != null) {
            worldAiContextPeriod.setText("Période : " + (period == null || period.isBlank() ? "—" : period));
        }
        int count = lastWorldSearchResults == null ? 0 : lastWorldSearchResults.size();
        if (worldAiContextCount != null) {
            worldAiContextCount.setText("Résultats : " + count);
        }
        if (worldAiTrendMain != null) {
            worldAiTrendMain.setText("Les événements pratiques autour de " + keywords + " progressent, surtout en format atelier.");
        }
        if (worldAiTrendPeriod != null) {
            worldAiTrendPeriod.setText("Les meilleurs taux de participation sont observés sur " + period + ".");
        }
        if (worldAiTrendAudience != null) {
            worldAiTrendAudience.setText(inferAudience(aiText));
        }
        if (worldAiTrendLocation != null) {
            String city = inferCityFromKeywords(keywords);
            worldAiTrendLocation.setText(city.isBlank()
                    ? "Les grandes villes restent les plus actives."
                    : city + " reste une zone très active.");
        }

        String title = extractPrefilledTitle(aiText, keywords);
        String description = extractPrefilledDescription(aiText, keywords, period);
        String audience = inferAudience(aiText);
        List<String> titleChoices = buildTitleChoices(aiText, title, keywords);
        List<String> descChoices = buildDescriptionChoices(aiText, description, keywords, period);

        if (worldAiFormTitle != null) {
            worldAiFormTitle.setText(title);
        }
        if (worldAiFormDescription != null) {
            worldAiFormDescription.setText(description);
        }
        setupChoiceCheckboxes(worldAiTitleChoicesBox, titleChoices, value -> {
            if (worldAiFormTitle != null) {
                worldAiFormTitle.setText(value);
            }
        });
        setupChoiceCheckboxes(worldAiDescriptionChoicesBox, descChoices, value -> {
            if (worldAiFormDescription != null) {
                worldAiFormDescription.setText(value);
            }
        });
        if (worldAiFormAudience != null) {
            worldAiFormAudience.setText(audience);
        }

        int score = Math.min(95, 72 + (count * 3));
        if (worldAiScoreLabel != null) {
            worldAiScoreLabel.setText(score + "%");
        }
        if (worldAiScoreProgress != null) {
            worldAiScoreProgress.setProgress(score / 100.0);
        }
        if (worldAiScoreHint != null) {
            worldAiScoreHint.setText("Basé sur les tendances actuelles");
        }

        if (worldAiRecommendationsBox != null) {
            worldAiRecommendationsBox.getChildren().clear();
            worldAiRecommendationsBox.getChildren().addAll(
                    buildRecommendationLabel("• Utiliser des activités interactives et courtes"),
                    buildRecommendationLabel("• Limiter le nombre de participants pour mieux accompagner"),
                    buildRecommendationLabel("• Promouvoir l’événement sur des groupes spécialisés"),
                    buildRecommendationLabel("• Prévoir un support visuel clair pour les familles")
            );
        }
    }

    private Label buildRecommendationLabel(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add("admin-world-ai-reco-item");
        return l;
    }

    private void setupChoiceCheckboxes(VBox box, List<String> choices, Consumer<String> onPick) {
        if (box == null) {
            return;
        }
        box.getChildren().clear();
        List<CheckBox> checks = new ArrayList<>();
        for (String c : choices) {
            if (c == null || c.isBlank()) {
                continue;
            }
            CheckBox cb = new CheckBox(c);
            cb.setWrapText(true);
            cb.getStyleClass().add("admin-world-ai-choice-check");
            cb.selectedProperty().addListener((obs, oldV, selected) -> {
                if (!selected) {
                    return;
                }
                for (CheckBox other : checks) {
                    if (other != cb) {
                        other.setSelected(false);
                    }
                }
                onPick.accept(c);
            });
            checks.add(cb);
            box.getChildren().add(cb);
        }
        if (!checks.isEmpty()) {
            checks.get(0).setSelected(true);
        }
    }

    private static List<String> buildTitleChoices(String aiText, String fallback, String keywords) {
        List<String> out = new ArrayList<>();
        if (aiText != null && !aiText.isBlank()) {
            for (String line : aiText.split("\\R")) {
                String cleaned = line.trim();
                if (cleaned.isBlank()) continue;
                String low = cleaned.toLowerCase(Locale.ROOT);
                if (low.startsWith("titre") || low.contains("titre:")) {
                    int idx = cleaned.indexOf(':');
                    if (idx >= 0 && idx < cleaned.length() - 1) {
                        cleaned = cleaned.substring(idx + 1).trim();
                    }
                    cleaned = cleaned.replaceAll("^[\\-•\\d\\.)\\s]+", "");
                    if (cleaned.length() >= 8) out.add(cleaned);
                }
                if (out.size() >= 3) break;
            }
        }
        if (out.isEmpty()) out.add(fallback);
        if (out.size() < 3) {
            String city = inferCityFromKeywords(keywords);
            out.add("Atelier pratique inclusion et accompagnement" + (city.isBlank() ? "" : " - " + city));
        }
        if (out.size() < 3) {
            out.add("Rencontre thématique: familles et professionnels");
        }
        return out.subList(0, Math.min(3, out.size()));
    }

    private static List<String> buildDescriptionChoices(String aiText, String fallback, String keywords, String period) {
        List<String> out = new ArrayList<>();
        if (aiText != null && !aiText.isBlank()) {
            String[] parts = aiText.split("\\n\\s*\\n");
            for (String p : parts) {
                String cleaned = extractDescriptionOnly(p);
                if (cleaned.isBlank()) {
                    cleaned = p.trim().replaceAll("\\s+", " ");
                }
                if (cleaned.length() >= 60) {
                    if (cleaned.length() > 320) cleaned = cleaned.substring(0, 320) + "…";
                    out.add(cleaned);
                }
                if (out.size() >= 3) break;
            }
        }
        if (out.isEmpty()) out.add(fallback);
        if (out.size() < 3) {
            out.add("Événement orienté pratique autour de " + keywords
                    + ", avec ateliers guidés, retours d’expérience et échanges experts sur la période " + period + ".");
        }
        if (out.size() < 3) {
            out.add("Session collaborative pour familles et professionnels : activités interactives, conseils concrets et ressources utiles.");
        }
        return out.subList(0, Math.min(3, out.size()));
    }

    private static String extractDescriptionOnly(String block) {
        if (block == null || block.isBlank()) {
            return "";
        }
        String normalized = block.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        int descIdx = lower.indexOf("description");
        if (descIdx >= 0) {
            int colon = normalized.indexOf(':', descIdx);
            if (colon >= 0 && colon < normalized.length() - 1) {
                String tail = normalized.substring(colon + 1).trim();
                int nextField = indexOfNextStructuredField(tail.toLowerCase(Locale.ROOT));
                if (nextField > 0) {
                    tail = tail.substring(0, nextField).trim();
                }
                return tail.replaceAll("^[\\-•\\d\\.)\\s]+", "");
            }
        }
        return normalized
                .replaceAll("(?i)\\b\\d+\\)\\s*titre\\s*:[^\\d]*(?=\\d+\\)|$)", "")
                .replaceAll("(?i)\\bdescription\\s*:", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int indexOfNextStructuredField(String s) {
        int i1 = s.indexOf("3) sujets");
        int i2 = s.indexOf("sujets:");
        int i3 = s.indexOf("titre:");
        int best = -1;
        for (int i : new int[]{i1, i2, i3}) {
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    private static String inferAudience(String aiText) {
        String low = aiText == null ? "" : aiText.toLowerCase(Locale.ROOT);
        if (low.contains("educateur") || low.contains("enseignant")) {
            return "Parents et éducateurs spécialisés";
        }
        if (low.contains("adolescent")) {
            return "Adolescents TSA et familles";
        }
        return "Parents, accompagnants et professionnels";
    }

    private static String inferCategory(String aiText, String keywords) {
        String low = ((aiText == null ? "" : aiText) + " " + (keywords == null ? "" : keywords)).toLowerCase(Locale.ROOT);
        if (low.contains("conférence") || low.contains("conference")) {
            return "Conférence";
        }
        if (low.contains("colloque")) {
            return "Colloque";
        }
        return "Atelier";
    }

    private static String extractPrefilledTitle(String aiText, String keywords) {
        String city = inferCityFromKeywords(keywords);
        String topic = (keywords == null || keywords.isBlank()) ? "inclusion" : keywords;
        String base = "Atelier sensoriel autour de " + topic;
        if (!city.isBlank()) {
            base += " - " + city;
        }
        if (aiText != null && aiText.length() > 12) {
            String firstLine = aiText.split("\\R", 2)[0].trim();
            if (firstLine.length() >= 10 && firstLine.length() <= 120) {
                return firstLine.replaceAll("^[\\-•\\d\\.)\\s]+", "");
            }
        }
        return base;
    }

    private static String extractPrefilledDescription(String aiText, String keywords, String period) {
        if (aiText != null && !aiText.isBlank()) {
            String cleaned = aiText.trim();
            if (cleaned.length() > 1000) {
                cleaned = cleaned.substring(0, 1000) + "…";
            }
            return cleaned;
        }
        return "Événement conçu à partir des tendances détectées pour \"" + keywords + "\" sur la période " + period
                + ". Objectif: proposer une expérience pratique, inclusive et utile pour les familles.";
    }

    private static String inferCityFromKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return "";
        }
        String low = keywords.toLowerCase(Locale.ROOT);
        if (low.contains("paris")) return "Paris";
        if (low.contains("tunis")) return "Tunis";
        if (low.contains("lyon")) return "Lyon";
        if (low.contains("marseille")) return "Marseille";
        if (low.contains("espagne")) return "Espagne";
        if (low.contains("france")) return "France";
        return "";
    }

    private void scrollWorldSearchSectionIntoView() {
        if (eventsScrollRoot == null || worldSearchSection == null) {
            return;
        }
        var content = eventsScrollRoot.getContent();
        if (content == null) {
            return;
        }
        if (content instanceof Parent c) {
            c.applyCss();
            c.layout();
        }
        worldSearchSection.applyCss();
        worldSearchSection.layout();
        double yNode = 0.0;
        for (var n = (javafx.scene.Node) worldSearchSection; n != null && n != content; n = n.getParent()) {
            yNode += n.getLayoutY();
        }
        double contentH = content.getLayoutBounds().getHeight();
        double vh = eventsScrollRoot.getViewportBounds().getHeight();
        if (contentH > vh && contentH - vh > 1) {
            double v = yNode / (contentH - vh);
            eventsScrollRoot.setVvalue(clamp01(v));
        } else {
            eventsScrollRoot.setVvalue(0.0);
        }
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0.0;
        }
        if (v > 1) {
            return 1.0;
        }
        return v;
    }

    private void populateWorldSearchResultRows(List<GoogleCustomSearchService.CseResult> list) {
        if (worldSearchResultsBox == null) {
            return;
        }
        worldSearchResultsBox.getChildren().clear();
        if (list == null) {
            return;
        }
        for (var r : list) {
            VBox card = new VBox(6.0);
            card.getStyleClass().add("admin-world-search-result-card");
            String t = r.title() == null ? "—" : r.title();
            if (t.length() > 220) {
                t = t.substring(0, 217) + "…";
            }
            Label titleL = new Label(t);
            titleL.setWrapText(true);
            titleL.getStyleClass().add("admin-world-search-result-title");
            String rawLink = r.link() == null ? "" : r.link().trim();
            Hyperlink linkL = new Hyperlink(rawLink.isBlank() ? "—" : rawLink);
            linkL.setWrapText(true);
            linkL.setMaxWidth(Double.MAX_VALUE);
            linkL.getStyleClass().add("admin-world-search-result-link");
            if (!rawLink.isBlank()) {
                linkL.setTooltip(new Tooltip(rawLink));
                linkL.setOnAction(e -> openExternalUrl(rawLink));
            } else {
                linkL.setDisable(true);
            }
            String s = r.snippet() == null ? "" : r.snippet();
            if (s.length() > 320) {
                s = s.substring(0, 317) + "…";
            }
            Label snipL = new Label(s);
            snipL.setWrapText(true);
            snipL.getStyleClass().add("admin-world-search-result-snippet");
            card.getChildren().addAll(titleL, linkL, snipL);
            VBox.setMargin(card, new Insets(0, 0, 0, 0));
            worldSearchResultsBox.getChildren().add(card);
        }
    }

    private void openExternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                showInfo("Lien", "Ouverture navigateur non supportée sur cet environnement.");
                return;
            }
            Desktop.getDesktop().browse(URI.create(url.trim()));
        } catch (Exception ex) {
            showInfo("Lien", "Impossible d’ouvrir le lien: " + ex.getMessage());
        }
    }

    private String buildWorldSearchDigestForAi(List<GoogleCustomSearchService.CseResult> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        int i = 1;
        var sb = new StringBuilder();
        for (var r : list) {
            sb.append(i).append(") Titre: ").append(r.title() == null ? "" : r.title()).append('\n');
            sb.append("Lien: ").append(r.link() == null ? "" : r.link()).append('\n');
            sb.append("Aperçu: ").append(r.snippet() == null ? "" : r.snippet()).append("\n\n");
            if (i++ >= 10) {
                break;
            }
        }
        return sb.toString();
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
