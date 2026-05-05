package org.example.controllers;

import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.MedecinRatingService;
import org.example.services.MultiAiProviderService;
import org.example.services.AppointmentService;
import org.example.services.AvailabilityService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.MainApp;
import org.example.utils.PublicRdvDoctorSidebarHelper;
import org.example.utils.RdvTarifFormat;
import org.example.utils.UserAvatarGraphic;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.net.URLEncoder;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.example.models.Availability;
import javafx.util.StringConverter;

/**
 * Liste des médecins ayant un compte actif (table {@code user}) pour le parcours « Prendre RDV ».
 */
public class PageRdvController implements PublicShellAware {

    private static final String ALL_SPECIALTIES = "Toutes les spécialités";
    private static final DateTimeFormatter RDV_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private PublicShellController shell;

    @FXML
    private VBox rdvDoctorsList;
    @FXML
    private Label rdvCountNumLabel;
    @FXML
    private ComboBox<String> rdvSpecialtyFilter;
    @FXML
    private TextField rdvLieuFilter;
    @FXML
    private DatePicker rdvAiDatePicker;
    @FXML
    private ComboBox<String> rdvAiTimeCombo;
    @FXML
    private Label rdvAiStatusLabel;
    @FXML
    private VBox rdvAiSuggestionsBox;
    @FXML
    private VBox rdvChatbotPanel;
    @FXML
    private VBox rdvChatbotHistory;
    @FXML
    private ScrollPane rdvChatbotHistoryScroll;
    @FXML
    private TextField rdvChatbotInput;
    @FXML
    private Button rdvChatbotSendBtn;
    @FXML
    private Button rdvQuizQuestionnaireBtn;
    @FXML
    private Button rdvQuizImagesBtn;
    @FXML
    private Button rdvChatbotExpandBtn;
    private VBox rdvAssistantTypingBubble;
    /** Bulle « IA en cours » pendant la génération d'une planche Rorschach (points animés). */
    private VBox rdvImageGenPendingBubble;
    private Timeline rdvImageGenDotsTimeline;
    private boolean rdvChatbotExpanded;
    /** Empêche les requêtes IA chat concurrentes qui laissent parfois l'indicateur bloqué. */
    private volatile boolean rdvAssistantRequestInFlight = false;
    private Label rdvEditingMessageLabel;
    private String rdvEditingOriginalText;
    private HBox rdvEditingMessageRow;

    private final UserService userService = new UserService();
    private final AvailabilityService availabilityService = new AvailabilityService();
    private final AppointmentService appointmentService = new AppointmentService();
    private final MedecinRatingService ratingService = new MedecinRatingService();
    private final MultiAiProviderService aiService = new MultiAiProviderService();
    private List<User> medecinsCompteActif = new ArrayList<>();

    private enum ChatQuizMode { NONE, AUTISM_QUESTIONNAIRE, RORSCHACH_IMAGES }
    private ChatQuizMode chatQuizMode = ChatQuizMode.NONE;
    private int chatQuizIndex = -1;
    private int chatQuizScore = 0;
    private final List<String> questionnaireResponses = new ArrayList<>();
    private final List<String> autismQuizQuestionsDynamic = new ArrayList<>();
    private final List<String> rorschachResponses = new ArrayList<>();
    /** Vrai uniquement quand une planche est visible et attend la description utilisateur. */
    private boolean rorschachAwaitingDescription = false;
    /** Empêche les lancements concurrents pour une même planche. */
    private final HashSet<Integer> rorschachPlateInFlight = new HashSet<>();
    /** Une seule bulle "indisponible" par planche pour éviter les doublons visuels. */
    private final Map<Integer, VBox> rorschachRetryBubbles = new HashMap<>();
    private static final HashSet<String> CHAT_ESSENTIAL_WORDS = new HashSet<>(List.of(
            "autisme", "tsa", "communication", "interactions", "sensorielle", "routine",
            "stereotypies", "diagnostic", "accompagnement", "surcharge", "consultation"
    ));

    private static final String[] RORSCHACH_SVG = {
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 360 220'><rect width='360' height='220' fill='#fff'/>"
                    + "<ellipse cx='120' cy='110' rx='82' ry='64' fill='#222'/><ellipse cx='240' cy='110' rx='82' ry='64' fill='#222'/>"
                    + "<circle cx='180' cy='110' r='18' fill='#000'/><path d='M180 30 C165 70,165 150,180 190 C195 150,195 70,180 30Z' fill='#111'/></svg>",
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 360 220'><rect width='360' height='220' fill='#fff'/>"
                    + "<path d='M180 22 C145 40,130 80,120 110 C110 135,105 165,80 188 C120 182,145 170,165 148 C175 137,178 125,180 112 Z' fill='#1f1f1f'/>"
                    + "<path d='M180 22 C215 40,230 80,240 110 C250 135,255 165,280 188 C240 182,215 170,195 148 C185 137,182 125,180 112 Z' fill='#1f1f1f'/>"
                    + "<ellipse cx='180' cy='118' rx='20' ry='14' fill='#000'/></svg>",
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 360 220'><rect width='360' height='220' fill='#fff'/>"
                    + "<path d='M180 34 C150 46,128 70,120 98 C112 126,122 152,138 170 C150 182,160 186,180 190 Z' fill='#222'/>"
                    + "<path d='M180 34 C210 46,232 70,240 98 C248 126,238 152,222 170 C210 182,200 186,180 190 Z' fill='#222'/>"
                    + "<circle cx='150' cy='108' r='12' fill='#111'/><circle cx='210' cy='108' r='12' fill='#111'/>"
                    + "<rect x='170' y='92' width='20' height='40' rx='8' fill='#000'/></svg>",
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 360 220'><rect width='360' height='220' fill='#fff'/>"
                    + "<path d='M180 20 C162 52,138 72,120 88 C100 106,92 140,104 162 C118 188,152 194,180 198 Z' fill='#1d1d1d'/>"
                    + "<path d='M180 20 C198 52,222 72,240 88 C260 106,268 140,256 162 C242 188,208 194,180 198 Z' fill='#1d1d1d'/>"
                    + "<ellipse cx='180' cy='104' rx='28' ry='16' fill='#0f0f0f'/></svg>"
    };

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        loadMedecins();
        fillSpecialtyFilter();
        initAiRdvAssistant();
        if (rdvSpecialtyFilter != null) {
            rdvSpecialtyFilter.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshDoctorCards());
        }
        if (rdvLieuFilter != null) {
            rdvLieuFilter.textProperty().addListener((o, a, b) -> refreshDoctorCards());
        }
        bootstrapHelpChatbot();
        refreshDoctorCards();
    }

    private void initAiRdvAssistant() {
        if (rdvAiTimeCombo != null) {
            List<String> times = new ArrayList<>();
            for (int h = 7; h <= 20; h++) {
                times.add(String.format("%02d:00", h));
                times.add(String.format("%02d:30", h));
            }
            rdvAiTimeCombo.getItems().setAll(times);
            rdvAiTimeCombo.getSelectionModel().select("09:00");
        }
        if (rdvAiDatePicker != null) {
            rdvAiDatePicker.setConverter(new StringConverter<>() {
                @Override
                public String toString(LocalDate date) {
                    return date == null ? "" : RDV_DATE_FORMATTER.format(date);
                }

                @Override
                public LocalDate fromString(String text) {
                    if (text == null || text.trim().isEmpty()) {
                        return null;
                    }
                    try {
                        return LocalDate.parse(text.trim(), RDV_DATE_FORMATTER);
                    } catch (DateTimeParseException ex) {
                        return null;
                    }
                }
            });
            rdvAiDatePicker.setEditable(false);
            rdvAiDatePicker.setValue(LocalDate.now().plusDays(1));
        }
    }

    @FXML
    private void onSuggestByAvailability() {
        if (rdvAiDatePicker == null || rdvAiTimeCombo == null || rdvAiSuggestionsBox == null) {
            return;
        }
        LocalDate d = rdvAiDatePicker.getValue();
        if (rdvAiDatePicker.getEditor() != null) {
            String rawDate = rdvAiDatePicker.getEditor().getText();
            if (rawDate != null && !rawDate.isBlank()) {
                try {
                    LocalDate.parse(rawDate.trim(), RDV_DATE_FORMATTER);
                } catch (DateTimeParseException ex) {
                    if (rdvAiStatusLabel != null) {
                        rdvAiStatusLabel.setText("Date invalide. Utilisez le format jj/MM/aaaa et une date réelle.");
                    }
                    return;
                }
            }
        }
        String t = rdvAiTimeCombo.getSelectionModel().getSelectedItem();
        if (d == null || t == null || t.isBlank()) {
            if (rdvAiStatusLabel != null) {
                rdvAiStatusLabel.setText("Sélectionnez une date et une heure.");
            }
            return;
        }
        LocalDateTime desired;
        try {
            desired = LocalDateTime.of(d, LocalTime.parse(t.trim()));
        } catch (Exception ex) {
            if (rdvAiStatusLabel != null) {
                rdvAiStatusLabel.setText("Heure invalide.");
            }
            return;
        }
        if (!desired.isAfter(LocalDateTime.now())) {
            if (rdvAiStatusLabel != null) {
                rdvAiStatusLabel.setText("Choisissez un créneau futur.");
            }
            return;
        }

        rdvAiSuggestionsBox.getChildren().clear();
        String preferredSpec = rdvSpecialtyFilter != null ? rdvSpecialtyFilter.getSelectionModel().getSelectedItem() : null;
        String preferredLieu = rdvLieuFilter != null && rdvLieuFilter.getText() != null
                ? rdvLieuFilter.getText().trim() : "";
        if (rdvAiStatusLabel != null) {
            rdvAiStatusLabel.setText("Analyse IA en cours...");
        }
        showAiSkeletonLoading(3);
        new Thread(() -> {
            int flexMin = 90;
            List<AiSuggestion> suggestions = rankSuggestionsWithExternalAi(desired, preferredSpec, preferredLieu, true, 60);
            boolean exactDateUsed = !suggestions.isEmpty();
            boolean widenedToNearestDates = false;
            if (suggestions.isEmpty()) {
                suggestions = rankSuggestionsWithExternalAi(desired, preferredSpec, preferredLieu, false, 90);
                widenedToNearestDates = !suggestions.isEmpty();
            }
            boolean widened = false;
            if (suggestions.isEmpty()) {
                suggestions = computeClosestDoctorSuggestions(desired, Math.max(360, flexMin * 2), true, false);
                widened = !suggestions.isEmpty();
            }
            if (suggestions.isEmpty()) {
                suggestions = computeClosestDoctorSuggestions(desired, Integer.MAX_VALUE, true, false);
            }
            String finalStatus;
            if (suggestions.isEmpty()) {
                finalStatus = "Aucun créneau disponible trouvé actuellement. Essayez une autre date.";
            } else {
                String msg = aiRankShortSummary(desired, preferredSpec, preferredLieu, suggestions);
                if (msg == null || msg.isBlank()) {
                    msg = "Plan généré: " + suggestions.size() + " options classées par pertinence.";
                }
                if (widened) {
                    msg += " Fenêtre élargie.";
                }
                if (exactDateUsed) {
                    msg += " Jour demandé priorisé.";
                } else if (widenedToNearestDates) {
                    msg += " Dates proches utilisées.";
                }
                finalStatus = msg;
            }
            List<AiSuggestion> finalSuggestions = suggestions;
            Platform.runLater(() -> {
                rdvAiSuggestionsBox.getChildren().clear();
                if (rdvAiStatusLabel != null) {
                    rdvAiStatusLabel.setText(finalStatus);
                }
                if (finalSuggestions.isEmpty()) {
                    return;
                }
                for (int i = 0; i < finalSuggestions.size(); i++) {
                    rdvAiSuggestionsBox.getChildren().add(buildAiSuggestionCard(finalSuggestions.get(i), desired, i));
                }
            });
        }, "rdv-ai-planner-thread").start();
    }

    private List<AiSuggestion> rankSuggestionsWithExternalAi(LocalDateTime desired,
                                                             String preferredSpec,
                                                             String preferredLieu,
                                                             boolean sameDayOnly,
                                                             int candidateLimit) {
        List<AiSuggestion> pool = collectCandidateSuggestions(desired, sameDayOnly, candidateLimit);
        if (pool.isEmpty()) {
            return pool;
        }
        List<MultiAiProviderService.RdvPlannerCandidate> payload = new ArrayList<>();
        for (AiSuggestion s : pool) {
            payload.add(new MultiAiProviderService.RdvPlannerCandidate(
                    s.slot.getId(),
                    s.doctor.getId(),
                    PublicRdvDoctorSidebarHelper.formatDrName(s.doctor),
                    s.doctor.getSpecialite() != null ? s.doctor.getSpecialite() : "",
                    s.doctor.getCabinet() != null ? s.doctor.getCabinet() : "",
                    s.doctor.getAdresse() != null ? s.doctor.getAdresse() : "",
                    s.slot.getDebut() != null ? s.slot.getDebut().toString() : ""
            ));
        }
        MultiAiProviderService.AiRdvRankResult ranked = aiService.rankRdvCandidates(
                payload,
                desired.toString(),
                preferredSpec != null ? preferredSpec : "",
                preferredLieu != null ? preferredLieu : ""
        );
        if (ranked == null || ranked.availabilityIds() == null || ranked.availabilityIds().isEmpty()) {
            return List.of();
        }
        Map<Integer, AiSuggestion> byAvailabilityId = new HashMap<>();
        for (AiSuggestion s : pool) {
            byAvailabilityId.put(s.slot.getId(), s);
        }
        List<AiSuggestion> out = new ArrayList<>();
        for (Integer id : ranked.availabilityIds()) {
            if (id == null) {
                continue;
            }
            AiSuggestion hit = byAvailabilityId.get(id);
            if (hit != null) {
                out.add(hit);
            }
            if (out.size() >= 5) {
                break;
            }
        }
        return out;
    }

    private List<AiSuggestion> collectCandidateSuggestions(LocalDateTime desired, boolean sameDayOnly, int limit) {
        List<AiSuggestion> out = new ArrayList<>();
        for (User doc : medecinsCompteActif) {
            try {
                List<Availability> slots = availabilityService.findByDoctor(doc.getId());
                for (Availability a : slots) {
                    if (a.getDebut() == null || a.getFin() == null) {
                        continue;
                    }
                    if (!a.getFin().isAfter(LocalDateTime.now())) {
                        continue;
                    }
                    if (sameDayOnly && !a.getDebut().toLocalDate().equals(desired.toLocalDate())) {
                        continue;
                    }
                    boolean busy = appointmentService.hasConflict(doc.getId(), a.getDebut())
                            || appointmentService.hasConflictForDisponibiliteSlot(a.getId());
                    if (busy) {
                        continue;
                    }
                    long diffMin = Math.abs(Duration.between(desired, a.getDebut()).toMinutes());
                    out.add(new AiSuggestion(doc, a, diffMin, diffMin));
                }
            } catch (Exception ignored) {
                // ignorer ce médecin
            }
        }
        out.sort(Comparator
                .comparingLong((AiSuggestion s) -> s.diffMinutes)
                .thenComparing(s -> s.slot.getDebut()));
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    private String aiRankShortSummary(LocalDateTime desired,
                                      String preferredSpec,
                                      String preferredLieu,
                                      List<AiSuggestion> rankedSuggestions) {
        if (rankedSuggestions == null || rankedSuggestions.isEmpty()) {
            return "";
        }
        List<MultiAiProviderService.RdvPlannerCandidate> payload = new ArrayList<>();
        for (AiSuggestion s : rankedSuggestions) {
            payload.add(new MultiAiProviderService.RdvPlannerCandidate(
                    s.slot.getId(),
                    s.doctor.getId(),
                    PublicRdvDoctorSidebarHelper.formatDrName(s.doctor),
                    s.doctor.getSpecialite() != null ? s.doctor.getSpecialite() : "",
                    s.doctor.getCabinet() != null ? s.doctor.getCabinet() : "",
                    s.doctor.getAdresse() != null ? s.doctor.getAdresse() : "",
                    s.slot.getDebut() != null ? s.slot.getDebut().toString() : ""
            ));
        }
        MultiAiProviderService.AiRdvRankResult ranked = aiService.rankRdvCandidates(
                payload,
                desired.toString(),
                preferredSpec != null ? preferredSpec : "",
                preferredLieu != null ? preferredLieu : ""
        );
        if (ranked != null && ranked.shortSummary() != null && !ranked.shortSummary().isBlank()) {
            return ranked.shortSummary();
        }
        return buildLocalNarrativeSummary(desired, rankedSuggestions);
    }

    private void showAiSkeletonLoading(int cards) {
        rdvAiSuggestionsBox.getChildren().clear();
        for (int i = 0; i < cards; i++) {
            VBox sk = new VBox(8);
            sk.getStyleClass().add("rdv-ai-skeleton-card");
            Region l1 = new Region();
            l1.getStyleClass().add("rdv-ai-skeleton-line");
            l1.setPrefWidth(220);
            l1.setPrefHeight(14);
            Region l2 = new Region();
            l2.getStyleClass().addAll("rdv-ai-skeleton-line", "rdv-ai-skeleton-line-wide");
            l2.setPrefHeight(12);
            Region l3 = new Region();
            l3.getStyleClass().add("rdv-ai-skeleton-line");
            l3.setPrefWidth(150);
            l3.setPrefHeight(12);
            sk.getChildren().addAll(l1, l2, l3);
            rdvAiSuggestionsBox.getChildren().add(sk);
        }
    }

    private List<AiSuggestion> computeClosestDoctorSuggestions(LocalDateTime desired,
                                                               int flexMinutes,
                                                               boolean relaxedMode,
                                                               boolean sameDayOnly) {
        List<AiSuggestion> out = new ArrayList<>();
        String preferredSpec = rdvSpecialtyFilter != null ? rdvSpecialtyFilter.getSelectionModel().getSelectedItem() : null;
        String preferredLieu = rdvLieuFilter != null && rdvLieuFilter.getText() != null
                ? rdvLieuFilter.getText().trim().toLowerCase(Locale.ROOT) : "";
        for (User doc : medecinsCompteActif) {
            try {
                List<Availability> slots = availabilityService.findByDoctor(doc.getId());
                AiSuggestion best = null;
                for (Availability a : slots) {
                    if (a.getDebut() == null || a.getFin() == null) {
                        continue;
                    }
                    if (!a.getFin().isAfter(LocalDateTime.now())) {
                        continue;
                    }
                    if (sameDayOnly && !a.getDebut().toLocalDate().equals(desired.toLocalDate())) {
                        continue;
                    }
                    boolean busy = appointmentService.hasConflict(doc.getId(), a.getDebut())
                            || appointmentService.hasConflictForDisponibiliteSlot(a.getId());
                    if (busy) {
                        continue;
                    }
                    long signedDiffMin = Duration.between(desired, a.getDebut()).toMinutes();
                    long diffMin = Math.abs(signedDiffMin);
                    if (!relaxedMode && diffMin > flexMinutes) {
                        continue;
                    }
                    long score = diffMin;
                    // Préférence UX : privilégier les créneaux après l'heure demandée.
                    if (signedDiffMin < 0) {
                        score += 25;
                    } else {
                        score -= 5;
                    }
                    // Bonus si spécialité déjà filtrée côté patient.
                    if (preferredSpec != null && !preferredSpec.isBlank()
                            && !"Toutes les spécialités".equalsIgnoreCase(preferredSpec)
                            && doc.getSpecialite() != null
                            && doc.getSpecialite().equalsIgnoreCase(preferredSpec)) {
                        score -= 20;
                    }
                    // Bonus proximité textuelle de lieu (cabinet/adresse).
                    if (!preferredLieu.isBlank()) {
                        String cab = doc.getCabinet() != null ? doc.getCabinet().toLowerCase(Locale.ROOT) : "";
                        String adr = doc.getAdresse() != null ? doc.getAdresse().toLowerCase(Locale.ROOT) : "";
                        if (cab.contains(preferredLieu) || adr.contains(preferredLieu)) {
                            score -= 15;
                        }
                    }
                    // Bonus si même jour que la préférence.
                    if (a.getDebut().toLocalDate().equals(desired.toLocalDate())) {
                        score -= 10;
                    }
                    if (best == null || score < best.score) {
                        best = new AiSuggestion(doc, a, diffMin, score);
                    }
                }
                if (best != null) {
                    out.add(best);
                }
            } catch (Exception ignored) {
                // ignorer ce médecin
            }
        }
        out.sort(Comparator
                .comparingLong((AiSuggestion s) -> s.score)
                .thenComparing(s -> s.slot.getDebut()));
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    private VBox buildAiSuggestionCard(AiSuggestion s, LocalDateTime desired, int rank) {
        VBox card = new VBox(8);
        card.getStyleClass().add("rdv-ai-suggestion-card");
        if (rank == 0) {
            card.getStyleClass().add("rdv-ai-suggestion-best-card");
        }
        String dr = PublicRdvDoctorSidebarHelper.formatDrName(s.doctor);
        String when = s.slot.getDebut().toLocalDate() + " • " + s.slot.getDebut().toLocalTime().withSecond(0).withNano(0);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label rankBadge = new Label(rank == 0 ? "Meilleur choix" : ("Alternative " + rank));
        rankBadge.getStyleClass().add("rdv-ai-rank-badge");
        rankBadge.getStyleClass().add(rank == 0 ? "rdv-ai-rank-best" : "rdv-ai-rank-alt");
        Label l1 = new Label("🩺 " + dr);
        l1.getStyleClass().add("rdv-ai-suggestion-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label("Écart " + formatGapHuman(s.diffMinutes));
        badge.getStyleClass().add("rdv-ai-suggestion-badge");
        top.getChildren().addAll(rankBadge, l1, spacer, badge);

        Label l2 = new Label("Créneau proposé : " + when);
        l2.getStyleClass().add("rdv-ai-suggestion-meta");
        l2.setWrapText(true);

        Label fitLabel = new Label("Recommandation IA: " + fitLevelText(s.diffMinutes));
        fitLabel.getStyleClass().add("rdv-ai-fit-badge");
        fitLabel.getStyleClass().add(fitLevelStyleClass(s.diffMinutes));

        HBox chips = new HBox(6);
        chips.getStyleClass().add("rdv-ai-chip-row");
        chips.getChildren().add(createAiChip(decisionBadgeText(s, desired), "rdv-ai-chip-decision"));
        chips.getChildren().add(createAiChip("⏱ " + formatGapHuman(s.diffMinutes), ""));
        if (s.doctor.getSpecialite() != null && !s.doctor.getSpecialite().isBlank()) {
            chips.getChildren().add(createAiChip("🧠 " + s.doctor.getSpecialite().trim(), ""));
        }

        Label reason = new Label(describeSuggestionReason(s, desired));
        reason.getStyleClass().add("rdv-ai-suggestion-reason");
        reason.setWrapText(true);

        Button pick = new Button("Choisir cette recommandation");
        pick.getStyleClass().add("rdv-ai-pick-btn");
        pick.setOnAction(e -> selectAiSuggestion(s));

        VBox content = new VBox(8);
        content.getChildren().addAll(top, l2, fitLabel, chips, reason, pick);
        VBox timeline = new VBox(0);
        timeline.getStyleClass().add("rdv-ai-timeline-col");
        Region dot = new Region();
        dot.getStyleClass().add("rdv-ai-timeline-dot");
        Region line = new Region();
        line.getStyleClass().add("rdv-ai-timeline-line");
        VBox.setVgrow(line, Priority.ALWAYS);
        timeline.getChildren().addAll(dot, line);
        HBox row = new HBox(10, timeline, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        card.getChildren().add(row);
        return card;
    }

    private static String fitLevelText(long diffMinutes) {
        if (diffMinutes <= 30) {
            return "Idéal";
        }
        if (diffMinutes <= 120) {
            return "Proche";
        }
        if (diffMinutes <= 360) {
            return "Acceptable";
        }
        if (diffMinutes <= 720) {
            return "Alternative";
        }
        return "Compromis";
    }

    private static String fitLevelStyleClass(long diffMinutes) {
        if (diffMinutes <= 120) {
            return "rdv-ai-fit-good";
        }
        if (diffMinutes <= 720) {
            return "rdv-ai-fit-medium";
        }
        return "rdv-ai-fit-low";
    }

    private static Label createAiChip(String text, String extraStyleClass) {
        Label chip = new Label(text);
        chip.getStyleClass().add("rdv-ai-insight-chip");
        if (extraStyleClass != null && !extraStyleClass.isBlank()) {
            chip.getStyleClass().add(extraStyleClass);
        }
        return chip;
    }

    private static String decisionBadgeText(AiSuggestion s, LocalDateTime desired) {
        if (s.diffMinutes > 720) {
            return "Compromis";
        }
        if (s.diffMinutes <= 90) {
            return "Rapide";
        }
        if (s.slot.getDebut().toLocalDate().equals(desired.toLocalDate())) {
            return "Confort";
        }
        return "Recommandé";
    }

    private String describeSuggestionReason(AiSuggestion s, LocalDateTime desired) {
        List<String> reasons = new ArrayList<>();
        if (s.slot.getDebut().toLocalDate().equals(desired.toLocalDate())) {
            reasons.add("même jour demandé");
        }
        if (s.doctor.getSpecialite() != null && !s.doctor.getSpecialite().isBlank()) {
            reasons.add("spécialité: " + s.doctor.getSpecialite().trim());
        }
        if (reasons.isEmpty()) {
            return "Recommandé car c’est le créneau disponible le plus proche de votre préférence.";
        }
        return "Pourquoi ce choix: " + String.join(", ", reasons) + ".";
    }

    private void selectAiSuggestion(AiSuggestion s) {
        if (s == null) {
            return;
        }
        AppState.beginPublicRdvBooking(s.doctor.getId(), PublicRdvDoctorSidebarHelper.formatDrName(s.doctor));
        AppState.setPendingPublicRdvAvailabilityId(s.slot.getId());
        if (AppState.getCurrentUser() == null) {
            if (rdvAiStatusLabel != null) {
                rdvAiStatusLabel.setText("Connexion obligatoire : connectez-vous pour continuer avec ce créneau.");
            }
            if (shell != null) {
                try {
                    shell.loadPage("login");
                } catch (Exception ignored) {
                    // garder la page actuelle si navigation indisponible
                }
            }
            return;
        }
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv-type");
        } catch (Exception ignored) {
            // stay
        }
    }

    private void requestAiNarrativeSummary(LocalDateTime desired, List<AiSuggestion> suggestions) {
        if (rdvAiStatusLabel == null || suggestions == null || suggestions.isEmpty()) {
            return;
        }
        rdvAiStatusLabel.setText("Analyse IA en cours...");
        String prompt = buildAiNarrativePrompt(desired, suggestions);
        new Thread(() -> {
            String ai = aiService.chatRdv(prompt);
            String fallback = buildLocalNarrativeSummary(desired, suggestions);
            String finalMsg = normalizeAiNarrative(ai, fallback);
            Platform.runLater(() -> rdvAiStatusLabel.setText(finalMsg));
        }, "rdv-ai-suggestion-summary").start();
    }

    private static String buildAiNarrativePrompt(LocalDateTime desired, List<AiSuggestion> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un assistant de prise de rendez-vous premium. ");
        sb.append("Donne UNE SEULE phrase courte en français (max 110 caractères), claire et directe, sans diagnostic.\n");
        sb.append("Disponibilité patient souhaitée: ").append(desired).append("\n");
        sb.append("Top suggestions:\n");
        int i = 1;
        for (AiSuggestion s : suggestions) {
            if (i > 5) {
                break;
            }
            String dr = PublicRdvDoctorSidebarHelper.formatDrName(s.doctor);
            sb.append(i).append(") ").append(dr)
                    .append(" | créneau=").append(s.slot.getDebut())
                    .append(" | écart=").append(s.diffMinutes).append(" min")
                    .append(" | score=").append(s.score)
                    .append(" | spécialité=").append(s.doctor.getSpecialite() != null ? s.doctor.getSpecialite() : "—")
                    .append("\n");
            i++;
        }
        sb.append("Règle: si l’écart dépasse 720 min, indiquer 'compromis'.");
        return sb.toString();
    }

    private static String normalizeAiNarrative(String ai, String fallback) {
        if (ai == null || ai.isBlank()) {
            return fallback;
        }
        String raw = ai.trim();
        String low = raw.toLowerCase(Locale.ROOT);
        // Ne jamais afficher une erreur technique brute dans l'UI planning.
        if (low.startsWith("clé ")
                || low.startsWith("provider ia non reconnu")
                || low.startsWith("erreur api ia:")
                || low.startsWith("http ")
                || low.contains("\"invalid_api_key\"")
                || low.contains("\"error\"")
                || low.contains("api key")
                || low.contains("unauthorized")
                || low.contains("forbidden")) {
            return fallback;
        }
        String oneLine = raw.replace("\n", " ").replace("\r", " ").trim();
        int dot = oneLine.indexOf('.');
        String shortText = dot > 0 ? oneLine.substring(0, dot + 1).trim() : oneLine;
        if (shortText.length() > 110) {
            shortText = shortText.substring(0, 107).trim() + "...";
        }
        return shortText;
    }

    private static String buildLocalNarrativeSummary(LocalDateTime desired, List<AiSuggestion> suggestions) {
        AiSuggestion best = suggestions.get(0);
        String bestDr = PublicRdvDoctorSidebarHelper.formatDrName(best.doctor);
        if (best.diffMinutes > 720) {
            return "Compromis proposé: " + bestDr + " (" + formatGapHuman(best.diffMinutes) + ").";
        }
        return "Meilleure option: " + bestDr + " avec un écart de " + formatGapHuman(best.diffMinutes) + ".";
    }

    private static String formatGapHuman(long diffMinutes) {
        if (diffMinutes < 60) {
            return diffMinutes + " min";
        }
        long h = diffMinutes / 60;
        long m = diffMinutes % 60;
        if (h < 24) {
            return m == 0 ? h + " h" : h + " h " + m + " min";
        }
        long d = h / 24;
        long rh = h % 24;
        if (rh == 0 && m == 0) {
            return d + " j";
        }
        if (m == 0) {
            return d + " j " + rh + " h";
        }
        return d + " j " + rh + " h " + m + " min";
    }

    private static final class AiSuggestion {
        final User doctor;
        final Availability slot;
        final long diffMinutes;
        final long score;
        AiSuggestion(User doctor, Availability slot, long diffMinutes, long score) {
            this.doctor = doctor;
            this.slot = slot;
            this.diffMinutes = diffMinutes;
            this.score = score;
        }
    }

    private void bootstrapHelpChatbot() {
        if (rdvChatbotPanel != null) {
            rdvChatbotPanel.setManaged(false);
            rdvChatbotPanel.setVisible(false);
            setChatbotPanelCompact();
        }
        if (rdvChatbotHistory != null) {
            rdvChatbotHistory.getChildren().clear();
        }
    }

    @FXML
    private void onClearRdvChatbotHistory() {
        removeAssistantTypingIndicator();
        removeRorschachImageGeneratingIndicator();
        if (rdvChatbotHistory != null) {
            rdvChatbotHistory.getChildren().clear();
        }
        clearEditUserMessageState();
        rdvAssistantTypingBubble = null;
        rdvAssistantRequestInFlight = false;
        rorschachPlateInFlight.clear();
        rorschachRetryBubbles.clear();
        rorschachAwaitingDescription = false;
        hideRorschachImage();
        chatQuizMode = ChatQuizMode.NONE;
        chatQuizIndex = -1;
        chatQuizScore = 0;
        appendChatbotLine("Assistant", "Conversation réinitialisée. Je suis prêt à vous accompagner.");
    }

    @FXML
    private void onToggleRdvChatbot() {
        if (rdvChatbotPanel == null) {
            return;
        }
        boolean show = !rdvChatbotPanel.isVisible();
        rdvChatbotPanel.setManaged(show);
        rdvChatbotPanel.setVisible(show);
        if (show && rdvChatbotInput != null) {
            rdvChatbotInput.requestFocus();
        }
    }

    @FXML
    private void onToggleRdvChatbotSize() {
        if (rdvChatbotPanel == null) {
            return;
        }
        if (rdvChatbotExpanded) {
            setChatbotPanelCompact();
        } else {
            setChatbotPanelExpanded();
        }
    }

    private void setChatbotPanelCompact() {
        rdvChatbotExpanded = false;
        if (rdvChatbotPanel != null) {
            rdvChatbotPanel.setPrefWidth(390);
            rdvChatbotPanel.setMaxWidth(390);
            rdvChatbotPanel.setPrefHeight(500);
            rdvChatbotPanel.setMaxHeight(500);
        }
        if (rdvChatbotExpandBtn != null) {
            rdvChatbotExpandBtn.setText("⤢");
        }
    }

    private void setChatbotPanelExpanded() {
        rdvChatbotExpanded = true;
        if (rdvChatbotPanel != null) {
            rdvChatbotPanel.setPrefWidth(470);
            rdvChatbotPanel.setMaxWidth(470);
            rdvChatbotPanel.setPrefHeight(620);
            rdvChatbotPanel.setMaxHeight(620);
        }
        if (rdvChatbotExpandBtn != null) {
            rdvChatbotExpandBtn.setText("⤡");
        }
    }

    @FXML
    private void onSendRdvChatbotMessage() {
        if (rdvChatbotInput == null) {
            return;
        }
        String msg = rdvChatbotInput.getText() != null ? rdvChatbotInput.getText().trim() : "";
        if (msg.isEmpty()) {
            return;
        }
        if (rdvEditingMessageLabel != null) {
            String oldText = rdvEditingOriginalText != null ? rdvEditingOriginalText : rdvEditingMessageLabel.getText();
            rdvEditingMessageLabel.setText(msg);
            applyUserMessageEditToQuizResponses(oldText, msg);
            removeAssistantRowAfter(rdvEditingMessageRow);
            rdvChatbotInput.clear();
            clearEditUserMessageState();
            // En mode discussion libre, on régénère une nouvelle réponse assistant à partir du message corrigé.
            if (chatQuizMode == ChatQuizMode.NONE) {
                requestAssistantReplyAsync(msg, "rdv-ai-chat-edit");
            }
            return;
        }
        appendChatbotLine("Vous", msg);
        rdvChatbotInput.clear();
        if (chatQuizMode == ChatQuizMode.AUTISM_QUESTIONNAIRE) {
            if (shouldExitQuizForFreeChat(msg)) {
                chatQuizMode = ChatQuizMode.NONE;
                chatQuizIndex = -1;
                rorschachAwaitingDescription = false;
                hideRorschachImage();
                appendChatbotLine("Assistant", "Très bien, nous revenons en discussion libre. Posez votre question.");
                return;
            } else {
                handleQuestionnaireAnswer(msg);
                return;
            }
        }
        if (chatQuizMode == ChatQuizMode.RORSCHACH_IMAGES) {
            if (shouldExitQuizForFreeChat(msg)) {
                chatQuizMode = ChatQuizMode.NONE;
                chatQuizIndex = -1;
                rorschachAwaitingDescription = false;
                hideRorschachImage();
                appendChatbotLine("Assistant", "Très bien, nous revenons en discussion libre. Posez votre question.");
                return;
            } else {
                handleRorschachAnswer(msg);
                return;
            }
        }
        ChatQuizMode requestedQuiz = detectQuizRequestFromMessage(msg);
        if (requestedQuiz == ChatQuizMode.AUTISM_QUESTIONNAIRE) {
            onStartAutismQuestionnaireQuiz();
            return;
        }
        if (requestedQuiz == ChatQuizMode.RORSCHACH_IMAGES) {
            onStartRorschachImageQuiz();
            return;
        }
        requestAssistantReplyAsync(msg, "rdv-ai-chat");
    }

    @FXML
    private void onStartAutismQuestionnaireQuiz() {
        chatQuizMode = ChatQuizMode.AUTISM_QUESTIONNAIRE;
        chatQuizIndex = 0;
        chatQuizScore = 0;
        questionnaireResponses.clear();
        autismQuizQuestionsDynamic.clear();
        hideRorschachImage();
        appendChatbotLine("Assistant", "Je prépare quatre questions personnalisées avec l'IA...");
        showAssistantTypingIndicator();
        new Thread(() -> {
            List<String> generated = generateAutismQuizQuestionsWithAi();
            Platform.runLater(() -> {
                removeAssistantTypingIndicator();
                if (generated.isEmpty()) {
                    chatQuizMode = ChatQuizMode.NONE;
                    appendChatbotLine("Assistant", "Je ne peux pas générer les questions IA pour le moment. Merci de réessayer dans un instant.");
                    return;
                }
                autismQuizQuestionsDynamic.addAll(generated);
                appendChatbotLine("Assistant", "Le quiz questionnaire est lancé (4 questions, réponses libres).");
                appendChatbotLine("Assistant", autismQuizQuestionsDynamic.get(0));
            });
        }, "rdv-ai-quiz-questions").start();
    }

    @FXML
    private void onStartRorschachImageQuiz() {
        chatQuizMode = ChatQuizMode.RORSCHACH_IMAGES;
        chatQuizIndex = 0;
        chatQuizScore = 0;
        rorschachAwaitingDescription = false;
        rorschachResponses.clear();
        showRorschachPlate(chatQuizIndex);
    }

    @FXML
    private void onQuickThemeEmotions() {
        startQuickTheme("J’ai besoin d’aide pour gérer mes émotions au quotidien.");
    }

    @FXML
    private void onQuickThemeSensoriel() {
        startQuickTheme("Comment réduire la surcharge sensorielle dans mes activités quotidiennes ?");
    }

    @FXML
    private void onQuickThemeRoutine() {
        startQuickTheme("Aide-moi à construire une routine simple et rassurante.");
    }

    @FXML
    private void onQuickThemeCalme() {
        startQuickTheme("Propose-moi des techniques courtes pour me calmer en cas de stress.");
    }

    private void startQuickTheme(String text) {
        // Un clic sur un thème rapide passe toujours en mode discussion libre.
        if (chatQuizMode != ChatQuizMode.NONE) {
            chatQuizMode = ChatQuizMode.NONE;
            chatQuizIndex = -1;
            rorschachAwaitingDescription = false;
            hideRorschachImage();
            appendChatbotLine("Assistant", "Le mode quiz est arrêté. Nous passons en conversation libre.");
        }
        pushQuickPrompt(text);
    }

    private void pushQuickPrompt(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        appendChatbotLine("Vous", text);
        requestAssistantReplyAsync(text, "rdv-ai-chat-quick");
    }

    private void requestAssistantReplyAsync(String userText, String threadName) {
        if (rdvAssistantRequestInFlight) {
            appendChatbotLine("Assistant", "Je finalise d'abord la réponse en cours. Merci de patienter quelques secondes.");
            return;
        }
        rdvAssistantRequestInFlight = true;
        showAssistantTypingIndicator();
        new Thread(() -> {
            String answer;
            try {
                answer = generateRdvHelpAnswer(userText);
                if (answer == null || answer.isBlank()) {
                    answer = buildConversationLocalFallback(userText);
                }
                if (isQuickThemePrompt(userText) && answer.trim().length() < 220) {
                    // Les suggestions rapides doivent toujours produire une réponse riche et actionnable.
                    answer = buildDetailedQuickThemeFallback(userText);
                }
            } catch (Throwable t) {
                System.err.println("[AutiCare] Chat IA erreur: " + truncateUi(t.getMessage(), 220));
                answer = buildConversationLocalFallback(userText);
                if (isQuickThemePrompt(userText)) {
                    answer = buildDetailedQuickThemeFallback(userText);
                }
            }
            final String finalAnswer = answer;
            Platform.runLater(() -> {
                removeAssistantTypingIndicator();
                rdvAssistantRequestInFlight = false;
                appendChatbotLine("Assistant", finalAnswer);
            });
        }, threadName).start();
    }

    private String buildConversationLocalFallback(String userText) {
        String q = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        String original = userText != null ? userText.trim() : "";
        if (q.contains("émotion") || q.contains("emotion") || q.contains("stress") || q.contains("anx")
                || q.contains("angoiss") || q.contains("panique") || q.contains("colère") || q.contains("colere")) {
            return "Voici une approche simple en 3 étapes :\n"
                    + "1) Pause de 90 secondes avec respiration lente (inspirer 4, expirer 6).\n"
                    + "2) Réduire un stimulus (bruit, lumière, notifications) pendant 10 minutes.\n"
                    + "3) Nommer le besoin du moment en une phrase courte (ex: \"j'ai besoin de calme\").\n"
                    + "Si vous voulez, je peux vous proposer une routine anti-surcharge sur 1 journée, adaptée à votre situation.";
        }
        if (q.contains("sensoriel") || q.contains("bruit") || q.contains("lumi")
                || q.contains("foule") || q.contains("odeur") || q.contains("surcharge")) {
            return "Pour réduire la surcharge sensorielle au quotidien :\n"
                    + "- Identifiez 2 déclencheurs principaux (bruit, lumière, foule).\n"
                    + "- Préparez un kit rapide (écouteurs, lunettes, eau, objet d'ancrage).\n"
                    + "- Planifiez 2 pauses sensorielles de 5-10 minutes dans la journée.\n"
                    + "Je peux aussi vous faire un plan personnalisé maison/travail.";
        }
        if (q.contains("routine") || q.contains("organis") || q.contains("planning")
                || q.contains("retard") || q.contains("procrast")) {
            return "Routine simple et réaliste :\n"
                    + "- Matin : 3 tâches prioritaires maximum.\n"
                    + "- Milieu de journée : 1 pause de régulation.\n"
                    + "- Soir : préparation légère du lendemain (5 minutes).\n"
                    + "Le but est la stabilité, pas la perfection.";
        }
        if (q.contains("sommeil") || q.contains("dorm") || q.contains("nuit") || q.contains("fatigu")) {
            return "Pour améliorer le sommeil, essayez ce plan ce soir :\n"
                    + "- 60 min avant le coucher : baisser lumière/écrans.\n"
                    + "- 20 min avant : routine répétitive courte (douche tiède, respiration, lecture calme).\n"
                    + "- Si les pensées tournent : notez-les sur papier, puis revenez à la respiration lente.\n"
                    + "Si vous voulez, je peux vous faire une routine du soir en 10 minutes.";
        }
        if (q.contains("social") || q.contains("parler") || q.contains("communication")
                || q.contains("regard") || q.contains("conversation")) {
            return "Pour les situations sociales, voici un format simple :\n"
                    + "- Préparer 2 phrases d'ouverture à l'avance.\n"
                    + "- Poser 1 question courte, puis écouter.\n"
                    + "- Prévoir une phrase de sortie polie si fatigue sociale.\n"
                    + "Je peux vous proposer des exemples adaptés à école, travail ou famille.";
        }
        if (q.contains("oui") || q.contains("yes")) {
            return "Parfait. Pour vous répondre précisément, dites-moi juste votre contexte principal :\n"
                    + "1) études, 2) travail, 3) maison/famille,\n"
                    + "et ce qui vous gêne le plus en ce moment (émotions, surcharge, routine, sommeil, social).";
        }
        if (!original.isBlank()) {
            return "J'ai bien compris votre message : \"" + original + "\".\n"
                    + "Voici une réponse immédiate : je peux vous accompagner sur les questions TSA "
                    + "(communication, sensoriel, routines, émotions) mais aussi sur les demandes administratives du quotidien.\n"
                    + "Si vous voulez une version plus ciblée, dites-moi simplement votre objectif principal et je vous fais un plan précis.";
        }
        return "Merci pour votre message. Je peux répondre librement à vos questions avec un ton professionnel et concret, "
                + "tout en tenant compte du contexte TSA si c'est votre besoin.";
    }

    private static boolean isQuickThemePrompt(String userText) {
        String q = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        return q.contains("émotion")
                || q.contains("emotion")
                || q.contains("sensoriel")
                || q.contains("surcharge")
                || q.contains("routine")
                || q.contains("calmer")
                || q.contains("stress");
    }

    private String buildDetailedQuickThemeFallback(String userText) {
        String q = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        String contexte = detectDailyContext(q);
        if (q.contains("routine")) {
            return "Très bonne base. Voici une routine simple, rassurante et détaillée que vous pouvez appliquer dès aujourd'hui :\n\n"
                    + "1) Matin (10-15 min)\n"
                    + "- Vérifiez un mini-plan en 3 tâches maximum.\n"
                    + "- Commencez par une tâche \"facile\" pour créer de l'élan.\n"
                    + "- Préparez un plan B court si imprévu (ex: \"je fais au moins 5 minutes\").\n\n"
                    + "2) Milieu de journée (5-10 min)\n"
                    + "- Pause de régulation: respiration lente + eau + réduction des stimuli.\n"
                    + "- Ajustez les priorités: garder l'essentiel, reporter le non urgent.\n\n"
                    + "3) Fin de journée (8-12 min)\n"
                    + "- Notez 1 chose faite (même petite).\n"
                    + "- Préparez le lendemain: vêtements, sac, première tâche.\n"
                    + "- Heure de coucher stable (même plage horaire).\n\n"
                    + contextSpecificTips(contexte)
                    + "Si vous voulez, je peux vous transformer ce plan en version \"maison\" ou \"travail/études\".";
        }
        if (q.contains("sensoriel") || q.contains("surcharge")) {
            return "Voici un plan détaillé pour réduire la surcharge sensorielle dans vos activités quotidiennes :\n\n"
                    + "1) Identifier vos déclencheurs (2-3 jours)\n"
                    + "- Notez quand ça monte: bruit, lumière, odeurs, foule, transitions.\n"
                    + "- Évaluez l'intensité de 0 à 10.\n\n"
                    + "2) Prévenir avant exposition\n"
                    + "- Kit sensoriel prêt: écouteurs, lunettes, eau, objet d'ancrage.\n"
                    + "- Script court: \"J'ai besoin de 5 minutes au calme\".\n\n"
                    + "3) Réguler pendant la journée\n"
                    + "- Pauses courtes planifiées (5-10 min toutes 2-3 heures).\n"
                    + "- Réduire un stimulus à la fois (son puis lumière).\n\n"
                    + "4) Récupérer après surcharge\n"
                    + "- 15-20 min de décompression (calme, respiration, faible lumière).\n"
                    + "- Activité apaisante répétitive (marche, douche tiède, musique douce).\n\n"
                    + contextSpecificTips(contexte)
                    + "Je peux aussi vous faire un protocole \"urgence surcharge\" en 60 secondes.";
        }
        return "Très bien. Voici une réponse détaillée et pratique pour la gestion émotionnelle/stress :\n\n"
                + "1) Stop immédiat (60-90 sec)\n"
                + "- Inspirez 4 sec, expirez 6 sec, 6 cycles.\n"
                + "- Posez les épaules et desserrez la mâchoire.\n\n"
                + "2) Nommer ce qui se passe\n"
                + "- \"Je sens une montée de stress/surcharge\".\n"
                + "- \"Mon besoin maintenant est: calme / pause / clarté\".\n\n"
                + "3) Action courte et concrète\n"
                + "- Retirez-vous 5 minutes d'un stimulus.\n"
                + "- Buvez de l'eau, ralentissez le rythme, simplifiez la tâche en 1 micro-étape.\n\n"
                + "4) Prévenir la prochaine montée\n"
                + "- Préparez 2 stratégies d'avance.\n"
                + "- Prévenez une personne de confiance du signal d'alerte.\n\n"
                + contextSpecificTips(contexte)
                + "Si vous voulez, je peux vous donner une version personnalisée selon votre journée type.";
    }

    private static String detectDailyContext(String q) {
        if (q.contains("étude") || q.contains("etude") || q.contains("cours") || q.contains("examen")) {
            return "etudes";
        }
        if (q.contains("travail") || q.contains("bureau") || q.contains("réunion") || q.contains("reunion")) {
            return "travail";
        }
        if (q.contains("maison") || q.contains("famille") || q.contains("enfant")) {
            return "maison";
        }
        return "general";
    }

    private static String contextSpecificTips(String contexte) {
        return switch (contexte) {
            case "etudes" -> "Adaptation études:\n"
                    + "- Fractionnez le travail en blocs de 25 minutes avec 5 minutes de pause.\n"
                    + "- Préparez la veille le sac + la première matière pour réduire la charge mentale.\n\n";
            case "travail" -> "Adaptation travail:\n"
                    + "- Regroupez les réunions/appels pour limiter les changements de contexte.\n"
                    + "- Utilisez un signal clair \"indisponible 10 min\" pour vos pauses de régulation.\n\n";
            case "maison" -> "Adaptation maison/famille:\n"
                    + "- Créez 2 créneaux calmes fixes (matin/soir) même courts.\n"
                    + "- Affichez une mini-routine visuelle partagée avec la famille.\n\n";
            default -> "Adaptation selon votre journée:\n"
                    + "- Choisissez un seul ajustement à tester pendant 3 jours.\n"
                    + "- Gardez ce qui aide réellement, simplifiez le reste.\n\n";
        };
    }

    private void appendChatbotLine(String author, String text) {
        if (rdvChatbotHistory == null) {
            return;
        }
        String safeAuthor = author != null ? author : "";
        String safeText = text != null ? text : "";
        boolean isUser = "Vous".equalsIgnoreCase(safeAuthor);

        HBox row = new HBox();
        row.setFillHeight(false);
        row.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("rdv-chatbot-bubble");
        bubble.getStyleClass().add(isUser ? "rdv-chatbot-bubble-user" : "rdv-chatbot-bubble-assistant");
        bubble.setMaxWidth(460);
        Label header = new Label(safeAuthor + " :");
        header.getStyleClass().add("rdv-chatbot-bubble-author");
        Label content = new Label(safeText);
        content.setWrapText(true);
        content.setMaxWidth(Double.MAX_VALUE);
        content.getStyleClass().add("rdv-chatbot-bubble-message");
        bubble.getChildren().addAll(header, content);
        if (isUser) {
            Button editBtn = new Button("✎");
            editBtn.getStyleClass().add("rdv-chatbot-edit-btn");
            editBtn.setOnAction(ev -> beginEditUserMessage(content, row));
            bubble.getChildren().add(editBtn);
        }
        row.getChildren().add(bubble);
        bubble.setOpacity(0);
        bubble.setTranslateY(8);
        rdvChatbotHistory.getChildren().add(row);
        playChatBubbleEnterAnimation(bubble);
        if (rdvChatbotHistoryScroll != null) {
            Platform.runLater(() -> rdvChatbotHistoryScroll.setVvalue(1.0));
        }
    }

    private void beginEditUserMessage(Label contentLabel, HBox ownerRow) {
        if (contentLabel == null || rdvChatbotInput == null) {
            return;
        }
        rdvEditingMessageLabel = contentLabel;
        rdvEditingMessageRow = ownerRow;
        rdvEditingOriginalText = contentLabel.getText();
        rdvChatbotInput.setText(rdvEditingOriginalText != null ? rdvEditingOriginalText : "");
        rdvChatbotInput.requestFocus();
        rdvChatbotInput.positionCaret(rdvChatbotInput.getText().length());
        if (rdvChatbotSendBtn != null) {
            rdvChatbotSendBtn.setText("Mettre à jour");
        }
    }

    private void clearEditUserMessageState() {
        rdvEditingMessageLabel = null;
        rdvEditingMessageRow = null;
        rdvEditingOriginalText = null;
        if (rdvChatbotSendBtn != null) {
            rdvChatbotSendBtn.setText("Envoyer");
        }
    }

    private void removeAssistantRowAfter(HBox messageRow) {
        if (rdvChatbotHistory == null || messageRow == null) {
            return;
        }
        List<javafx.scene.Node> nodes = rdvChatbotHistory.getChildren();
        int idx = nodes.indexOf(messageRow);
        if (idx < 0 || idx + 1 >= nodes.size()) {
            return;
        }
        javafx.scene.Node next = nodes.get(idx + 1);
        if (!(next instanceof HBox nextRow) || nextRow.getChildren().isEmpty()) {
            return;
        }
        javafx.scene.Node first = nextRow.getChildren().get(0);
        if (!(first instanceof VBox bubble)) {
            return;
        }
        if (!bubble.getStyleClass().contains("rdv-chatbot-bubble-assistant")) {
            return;
        }
        nodes.remove(nextRow);
    }

    private void applyUserMessageEditToQuizResponses(String oldText, String newText) {
        replaceResponseSuffix(questionnaireResponses, oldText, newText);
        replaceResponseSuffix(rorschachResponses, oldText, newText);
    }

    private static void replaceResponseSuffix(List<String> responses, String oldText, String newText) {
        if (responses == null || responses.isEmpty() || oldText == null || newText == null) {
            return;
        }
        String oldTrim = oldText.trim();
        String newTrim = newText.trim();
        for (int i = 0; i < responses.size(); i++) {
            String row = responses.get(i);
            if (row == null) {
                continue;
            }
            int sep = row.indexOf(':');
            if (sep < 0) {
                continue;
            }
            String prefix = row.substring(0, sep + 1);
            String value = row.substring(sep + 1).trim();
            if (value.equals(oldTrim)) {
                responses.set(i, prefix + " " + newTrim);
                return; // modifie une seule réponse correspondante
            }
        }
    }

    private void showAssistantTypingIndicator() {
        if (rdvChatbotHistory == null) {
            return;
        }
        removeAssistantTypingIndicator();
        VBox bubble = new VBox(4);
        bubble.getStyleClass().addAll("rdv-chatbot-bubble", "rdv-chatbot-bubble-assistant", "rdv-chatbot-bubble-typing");
        Label header = new Label("Assistant :");
        header.getStyleClass().add("rdv-chatbot-bubble-author");
        Label content = new Label("Assistant écrit...");
        content.getStyleClass().add("rdv-chatbot-bubble-message");
        content.setWrapText(true);
        bubble.getChildren().addAll(header, content);
        bubble.setOpacity(0.85);
        rdvAssistantTypingBubble = bubble;
        rdvChatbotHistory.getChildren().add(bubble);
        if (rdvChatbotHistoryScroll != null) {
            Platform.runLater(() -> rdvChatbotHistoryScroll.setVvalue(1.0));
        }
    }

    private void removeAssistantTypingIndicator() {
        if (rdvAssistantTypingBubble == null || rdvChatbotHistory == null) {
            return;
        }
        rdvChatbotHistory.getChildren().remove(rdvAssistantTypingBubble);
        rdvAssistantTypingBubble = null;
    }

    private void removeRorschachImageGeneratingIndicator() {
        if (rdvImageGenDotsTimeline != null) {
            rdvImageGenDotsTimeline.stop();
            rdvImageGenDotsTimeline = null;
        }
        if (rdvImageGenPendingBubble != null && rdvChatbotHistory != null) {
            rdvChatbotHistory.getChildren().remove(rdvImageGenPendingBubble);
            rdvImageGenPendingBubble = null;
        }
    }

    /**
     * Affiche une bulle assistant avec points animés pour indiquer que la génération d'image IA est en cours.
     */
    private void showRorschachImageGeneratingIndicator(int plateNumber, int totalPlates) {
        if (rdvChatbotHistory == null) {
            return;
        }
        removeRorschachImageGeneratingIndicator();
        VBox bubble = new VBox(4);
        bubble.getStyleClass().addAll("rdv-chatbot-bubble", "rdv-chatbot-bubble-assistant", "rdv-chatbot-bubble-typing");
        Label header = new Label("Assistant :");
        header.getStyleClass().add("rdv-chatbot-bubble-author");
        Label content = new Label();
        content.getStyleClass().add("rdv-chatbot-bubble-message");
        content.setWrapText(true);
        String base = "L'IA génère l'image (planche " + plateNumber + "/" + totalPlates + "). Cela peut prendre un peu de temps";
        final int[] dotCount = {1};
        content.setText(base + ".");
        Timeline tl = new Timeline(new KeyFrame(javafx.util.Duration.millis(450), e -> {
            dotCount[0] = (dotCount[0] % 3) + 1;
            content.setText(base + ".".repeat(dotCount[0]));
        }));
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
        rdvImageGenDotsTimeline = tl;
        bubble.getChildren().addAll(header, content);
        bubble.setOpacity(0.9);
        rdvImageGenPendingBubble = bubble;
        rdvChatbotHistory.getChildren().add(bubble);
        if (rdvChatbotHistoryScroll != null) {
            Platform.runLater(() -> rdvChatbotHistoryScroll.setVvalue(1.0));
        }
    }

    private static void playChatBubbleEnterAnimation(VBox bubble) {
        if (bubble == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(javafx.util.Duration.millis(160), bubble);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        TranslateTransition slide = new TranslateTransition(javafx.util.Duration.millis(180), bubble);
        slide.setFromY(8);
        slide.setToY(0);
        ParallelTransition in = new ParallelTransition(fade, slide);
        in.play();
    }

    private TextFlow createStyledChatText(String text, boolean emphasizeEssentials) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(3);
        if (text == null || text.isBlank()) {
            flow.getChildren().add(new Text(""));
            return flow;
        }
        String[] tokens = text.split("(?=\\s)|(?<=\\s)");
        for (String token : tokens) {
            Text t = new Text(token);
            t.getStyleClass().add("rdv-chatbot-text");
            if (emphasizeEssentials && isEssentialToken(token)) {
                t.setStyle("-fx-font-weight: 700;");
            }
            flow.getChildren().add(t);
        }
        return flow;
    }

    private static boolean isEssentialToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String clean = token.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]", "");
        if (clean.isBlank()) {
            return false;
        }
        if (CHAT_ESSENTIAL_WORDS.contains(clean)) {
            return true;
        }
        return clean.equals("important") || clean.equals("priorite") || clean.equals("essentiel");
    }

    private String generateRdvHelpAnswer(String userText) {
        try {
            String safeUserText = userText != null ? userText : "";
            String ai = aiService.chatRdv(safeUserText);
            if (ai != null && !ai.isBlank() && !ai.startsWith("Clé ") && !ai.startsWith("Provider IA non reconnu")) {
                return ai;
            }

            String q = safeUserText.toLowerCase(Locale.ROOT);
            if (q.contains("autisme") || q.contains("tsa")) {
                return "Oui, je peux répondre à vos questions sur le TSA de façon simple et concrète: "
                        + "communication, surcharge sensorielle, routines, émotions, école/travail et organisation du quotidien. "
                        + "Si vous voulez, posez votre situation en une phrase et je vous réponds avec des étapes précises.";
            }
            if (q.contains("rdv") && (q.contains("prendre") || q.contains("reserver") || q.contains("réserver"))
                    || q.contains("consultation")) {
                return "Je peux vous aider avec une réponse simple et utile selon votre besoin concret, "
                        + "sans passer par la prise de rendez-vous. Dites-moi juste votre objectif.";
            }
            if (q.contains("annul") || q.contains("report")) {
                return "Bien sûr. Je peux vous aider à rédiger un message simple et poli, "
                        + "ou à reformuler votre demande clairement.";
            }
            if (q.contains("connect") || q.contains("compte") || q.contains("login")) {
                return "Je peux vous aider sur les points administratifs aussi. Dites-moi exactement où ça bloque "
                        + "(connexion, compte, validation, mot de passe) et je vous donne les étapes.";
            }
            if (q.contains("tarif") || q.contains("prix") || q.contains("coût") || q.contains("cout")) {
                return "Je peux vous aider à comparer les options et clarifier les coûts. "
                        + "Si vous me donnez votre contexte, je vous propose une réponse structurée et pratique.";
            }
            if (q.contains("horaire") || q.contains("heure") || q.contains("dispon")) {
                return "Je peux vous aider à organiser les horaires de manière réaliste. "
                        + "Dites-moi votre contrainte principale (travail, transport, fatigue, disponibilité).";
            }
            if (q.contains("email") || q.contains("mail") || q.contains("sms") || q.contains("confirm")) {
                return "Oui, je peux vous aider à rédiger un mail ou un SMS professionnel et clair. "
                        + "Dites-moi le destinataire et l'objectif du message.";
            }
            return "Je peux répondre à toutes vos questions, y compris hors quiz, avec un style clair et professionnel. "
                    + "Posez votre demande librement et je vous réponds de façon concrète.";
        } catch (Throwable t) {
            System.err.println("[AutiCare] generateRdvHelpAnswer erreur: " + formatThrowableForUi(t));
            return buildGeneralFallbackAnswer(userText);
        }
    }

    private String buildGeneralFallbackAnswer(String userText) {
        String q = userText != null ? userText.toLowerCase(Locale.ROOT) : "";
        if (q.contains("emotion") || q.contains("stress") || q.contains("anx")) {
            return "Je comprends, ce n'est pas simple quand la tension monte. On peut faire court: "
                    + "90 secondes de respiration lente, relâcher les épaules, puis choisir une seule petite action faisable maintenant.";
        }
        if (q.contains("sensor")) {
            return "Bonne question. Pour diminuer la surcharge sensorielle, essayez d'abord de baisser une stimulation "
                    + "(bruit, lumière, foule), puis gardez un repère apaisant et ajoutez de petites pauses régulières.";
        }
        if (q.contains("routine")) {
            return "On peut construire une routine simple ensemble: 3 étapes fixes, toujours dans le même ordre, "
                    + "avec un plan B très court si un imprévu arrive.";
        }
        return "Je peux répondre à votre question de manière claire, même si elle n'est pas liée au quiz. "
                + "Si vous voulez une réponse plus précise, indiquez juste votre objectif en une phrase.";
    }

    private void handleQuestionnaireAnswer(String msg) {
        String answer = msg != null ? msg.trim() : "";
        if (answer.isBlank()) {
            appendChatbotLine("Assistant", "Votre réponse est vide. Merci d'écrire une phrase courte.");
            return;
        }
        if (!isComprehensibleQuizAnswer(answer)) {
            appendChatbotLine("Assistant", "Je n'ai pas bien compris votre réponse. Merci d'écrire un message clair avec au moins 2 ou 3 mots compréhensibles.");
            return;
        }
        questionnaireResponses.add("Q" + (chatQuizIndex + 1) + ": " + answer);
        chatQuizIndex++;
        if (chatQuizIndex >= autismQuizQuestionsDynamic.size()) {
            appendChatbotLine("Assistant", "Merci. Voici une synthèse de vos réponses :");
            appendChatbotLine("Assistant", buildQuestionnaireSummary());
            appendChatbotLine("Assistant", "Je prépare maintenant une analyse personnalisée avec l'IA...");
            showAssistantTypingIndicator();
            new Thread(() -> {
                String analysis;
                try {
                    analysis = generateQuestionnaireAiAnalysis();
                } catch (Throwable t) {
                    System.err.println("[AutiCare] Questionnaire analyse erreur: " + truncateUi(t.getMessage(), 220));
                    analysis = buildQuestionnaireLocalAnalysisFallback();
                }
                final String finalAnalysis = (analysis == null || analysis.isBlank())
                        ? buildQuestionnaireLocalAnalysisFallback()
                        : analysis;
                Platform.runLater(() -> {
                    removeAssistantTypingIndicator();
                    appendChatbotLine("Assistant", finalAnalysis);
                    appendChatbotLine("Assistant", "Ce questionnaire est informatif et ne remplace pas un avis médical professionnel.");
                    chatQuizMode = ChatQuizMode.NONE;
                    chatQuizIndex = -1;
                    chatQuizScore = 0;
                });
            }, "rdv-ai-quiz-analysis").start();
            return;
        }
        appendChatbotLine("Assistant", autismQuizQuestionsDynamic.get(chatQuizIndex));
    }

    private boolean isComprehensibleQuizAnswer(String answer) {
        if (answer == null) {
            return false;
        }
        String s = answer.trim();
        if (s.length() < 2) {
            return false;
        }
        String normalized = s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
        if (normalized.length() < 2) {
            return false;
        }
        if (normalized.chars().distinct().count() <= 1) {
            return false;
        }
        if (normalized.matches("oui|non|peur|bruit|stress|fatigue|triste|colere|colère|anxiete|anxiété|routine|sensoriel")) {
            return true;
        }
        // Détecte les chaînes très aléatoires (ex: "jygtkfujm").
        if (normalized.matches(".*[bcdfghjklmnpqrstvwxz]{7,}.*")) {
            return false;
        }
        return normalized.matches(".*[aeiouyàâäéèêëîïôöùûü].*");
    }

    private String generateQuestionnaireAiAnalysis() {
        String ai = aiService.chatRdv(buildQuestionnaireAiAnalysisPrompt());
        if (ai != null && !ai.isBlank()
                && !ai.startsWith("Clé ")
                && !ai.startsWith("Provider IA non reconnu")
                && !ai.toLowerCase(Locale.ROOT).startsWith("erreur api ia:")) {
            return ai.trim();
        }
        return buildQuestionnaireLocalAnalysisFallback();
    }

    private String buildQuestionnaireLocalAnalysisFallback() {
        int filled = 0;
        for (String r : questionnaireResponses) {
            if (r != null && !r.isBlank()) {
                filled++;
            }
        }
        return "Merci pour vos réponses, elles donnent déjà une bonne base.\n\n"
                + "Ce qui ressort surtout, c'est un besoin de régulation plus stable au quotidien (organisation, émotions, interactions).\n"
                + "Je dirais que la difficulté est plutôt modérée, avec des pics selon les contextes.\n\n"
                + "Pour avancer concrètement, vous pouvez commencer par:\n"
                + "- repérer 2 déclencheurs fréquents,\n"
                + "- préparer 2 stratégies d'apaisement simples,\n"
                + "- formuler vos besoins à une personne de confiance.\n\n"
                + "Vous avez déjà franchi une étape importante en mettant des mots sur votre vécu (" + filled + " réponse(s)).";
    }

    private String buildQuestionnaireAiAnalysisPrompt() {
        String questions = autismQuizQuestionsDynamic.isEmpty()
                ? "Questions non disponibles."
                : String.join("\n- ", autismQuizQuestionsDynamic);
        String responses = questionnaireResponses.isEmpty()
                ? "Aucune réponse."
                : String.join("\n- ", questionnaireResponses);
        return """
                Tu es un assistant spécialisé autisme/TSA, orienté neuroaffirmatif.
                À partir des réponses ci-dessous, fais une analyse courte et utile en la RELIANT au vécu des personnes autistes ou avec un profil TSA
                (sensoriel, routines, surcharge, communication, besoin de clarté, forces et stratégies au quotidien).
                Ne pose AUCUN diagnostic à la personne : tu commentes des réponses à un questionnaire, tu ne dis pas qu'elle est ou n'est pas autiste.
                Réponds en français, ton bienveillant, simple, concret, respectueux.
                Structure attendue:
                1) Points clés liés aux réponses (3 puces max), avec au moins une piste concrète « au quotidien » pour des personnes TSA
                2) Niveau de difficulté perçu (faible/modéré/élevé + 1 phrase) dans ce cadre informatif uniquement
                3) Plan d'action concret (3 actions pratiques adaptées au contexte TSA / neuroatypique)
                4) Phrase de soutien finale (1 phrase) qui valide la personne sans stigmatiser

                Questions:
                - %s

                Réponses utilisateur:
                - %s
                """.formatted(questions, responses);
    }

    private List<String> generateAutismQuizQuestionsWithAi() {
        String prompt = """
                Génère exactement 4 questions courtes en français pour un questionnaire autisme/TSA.
                Contraintes:
                - Questions ouvertes (pas oui/non)
                - Ton bienveillant, simple, non médical
                - Thèmes: sensoriel, routine/changement, régulation émotionnelle, besoins de communication/accompagnement au quotidien
                Format de sortie strict:
                1) ...
                2) ...
                3) ...
                4) ...
                N'ajoute aucun titre, aucune introduction, aucune explication.
                """;
        String raw = aiService.chatRdv(prompt);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.startsWith("clé ") || low.startsWith("provider ia non reconnu")
                || low.startsWith("erreur api ia:") || low.startsWith("http ")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();

        // Cas fréquent: l'IA renvoie "1) ... 2) ... 3) ... 4) ..." sur une seule ligne.
        String normalized = raw.replace('\n', ' ').replace('\r', ' ').trim();
        Pattern numberedPattern = Pattern.compile("(?:^|\\s)(\\d+)\\s*[\\)\\.:-]\\s*(.+?)(?=(?:\\s+\\d+\\s*[\\)\\.:-]\\s)|$)");
        Matcher matcher = numberedPattern.matcher(normalized);
        while (matcher.find()) {
            String q = sanitizeGeneratedQuestion(matcher.group(2));
            if (!q.isBlank()) {
                out.add(q);
            }
            if (out.size() == 4) {
                return out;
            }
        }

        // Fallback: extraction ligne par ligne en ignorant les lignes d'introduction.
        for (String line : raw.split("\\R")) {
            String q = sanitizeGeneratedQuestion(line);
            if (q.isBlank()) {
                continue;
            }
            if (q.contains("?")) {
                out.add(q);
            }
            if (out.size() == 4) {
                return out;
            }
        }
        return List.of();
    }

    private static String sanitizeGeneratedQuestion(String text) {
        String q = text == null ? "" : text.trim();
        if (q.isEmpty()) {
            return "";
        }
        q = q.replaceFirst("^[-*]\\s*", "");
        q = q.replaceFirst("^\\d+[\\).:-]\\s*", "");
        q = q.replaceFirst("^(voici|questions|questionnaire)\\b.*?:\\s*", "");
        q = q.trim();
        return q;
    }

    private void handleRorschachAnswer(String msg) {
        if (!rorschachAwaitingDescription) {
            appendChatbotLine("Assistant", "L'image n'est pas encore prête. Merci d'attendre ou d'utiliser « Réessayer IA ».");
            return;
        }
        if (msg == null || msg.isBlank()) {
            appendChatbotLine("Assistant", "Merci de décrire ce que vous observez, même en quelques mots.");
            return;
        }
        rorschachAwaitingDescription = false;
        if (!msg.trim().isEmpty()) {
            chatQuizScore++;
        }
        rorschachResponses.add("Planche " + (chatQuizIndex + 1) + ": " + msg.trim());
        chatQuizIndex++;
        if (chatQuizIndex >= RORSCHACH_SVG.length) {
            hideRorschachImage();
            final List<String> snapshot = new ArrayList<>(rorschachResponses);
            final int scoreFinal = chatQuizScore;
            final int nPlates = RORSCHACH_SVG.length;
            appendChatbotLine("Assistant", "Le quiz images est terminé. Merci pour vos descriptions.");
            appendChatbotLine("Assistant", "Lecture pédagogique : " + scoreFinal + "/" + nPlates
                    + " réponses ont été prises en compte. "
                    + "Ce quiz est un outil d'exploration de la perception et de l'expression ; "
                    + "il ne permet pas d'établir un diagnostic TSA ni psychiatrique.");
            appendChatbotLine("Assistant", "Je rédige maintenant une analyse de vos descriptions (langage, imagination et pistes bienveillantes)...");
            showAssistantTypingIndicator();
            new Thread(() -> {
                String analysis;
                try {
                    analysis = generateRorschachAiAnalysis(snapshot, scoreFinal, nPlates);
                } catch (Throwable t) {
                    analysis = buildRorschachPedagogicFallbackAnalysis(snapshot, scoreFinal, nPlates);
                }
                final String finalAnalysis = (analysis == null || analysis.isBlank())
                        ? buildRorschachPedagogicFallbackAnalysis(snapshot, scoreFinal, nPlates)
                        : analysis;
                Platform.runLater(() -> {
                    removeAssistantTypingIndicator();
                    appendChatbotLine("Assistant", finalAnalysis);
                    appendChatbotLine("Assistant", "Si vous le souhaitez, je peux vous proposer des stratégies pratiques pour le quotidien en contexte TSA.");
                    chatQuizMode = ChatQuizMode.NONE;
                    chatQuizIndex = -1;
                    chatQuizScore = 0;
                    rorschachAwaitingDescription = false;
                });
            }, "rdv-rorschach-ai-analysis").start();
            return;
        }
        showRorschachPlate(chatQuizIndex);
    }

    private String rorschachQuestionForIndex(int idx) {
        int n = idx + 1;
        int total = RORSCHACH_SVG.length;
        return "Image " + n + "/" + total + " : que voyez-vous ?";
    }

    private void showRorschachPlate(int idx) {
        if (idx < 0 || idx >= RORSCHACH_SVG.length) {
            rorschachAwaitingDescription = false;
            hideRorschachImage();
            return;
        }
        rorschachAwaitingDescription = false;
        synchronized (rorschachPlateInFlight) {
            if (rorschachPlateInFlight.contains(idx)) {
                return;
            }
            rorschachPlateInFlight.add(idx);
        }
        hideRorschachImage();
        final int plate = idx;
        final int total = RORSCHACH_SVG.length;
        Runnable showPending = () -> showRorschachImageGeneratingIndicator(plate + 1, total);
        if (Platform.isFxApplicationThread()) {
            showPending.run();
        } else {
            Platform.runLater(showPending);
        }
        new Thread(() -> {
            MultiAiProviderService.ImageGenResult result = null;
            Throwable failure = null;
            try {
                String prompt = "Rorschach-style inkblot, perfectly bilateral symmetry, centered composition, "
                        + "black ink on pure white paper, minimal grayscale only, high contrast edges, "
                        + "organic abstract stain, no colors, no text, no watermark, no frame, "
                        + "clinical test-card aesthetic, unique variation #" + (plate + 1);
                result = aiService.generateInkblotImageWithDebug(prompt, plate);
            } catch (Throwable t) {
                failure = t;
            }
            final MultiAiProviderService.ImageGenResult finalResult = result;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                synchronized (rorschachPlateInFlight) {
                    rorschachPlateInFlight.remove(plate);
                }
                removeRorschachImageGeneratingIndicator();
                if (finalFailure != null) {
                    System.err.println("[AutiCare] Rorschach IA exception: "
                            + truncateUi(finalFailure.getMessage(), 260));
                    String caption = "Planche " + (plate + 1) + "/" + RORSCHACH_SVG.length
                            + " indisponible (incident technique IA).";
                    appendChatbotRetryBubble(caption, plate);
                    rorschachAwaitingDescription = false;
                    return;
                }
                if (finalResult != null && finalResult.dataUrl() != null && !finalResult.dataUrl().isBlank()) {
                    Image rendered = new Image(finalResult.dataUrl(), false);
                    if (rendered.isError()) {
                        String caption = "Planche " + (plate + 1) + "/" + RORSCHACH_SVG.length
                                + " indisponible (image IA invalide).";
                        appendChatbotRetryBubble(caption, plate);
                        rorschachAwaitingDescription = false;
                        return;
                    }
                    String caption = "Planche " + (plate + 1) + "/" + RORSCHACH_SVG.length
                            + " — Que voyez-vous ?";
                    appendChatbotImageBubble(rendered, caption, false, plate);
                    rorschachAwaitingDescription = true;
                } else {
                    if (finalResult != null && finalResult.error() != null && !finalResult.error().isBlank()) {
                        System.err.println("[AutiCare] Rorschach IA indisponible: " + truncateUi(finalResult.error(), 260));
                    }
                    String caption = "Planche " + (plate + 1) + "/" + RORSCHACH_SVG.length
                            + " indisponible (échec IA).";
                    appendChatbotRetryBubble(caption, plate);
                    rorschachAwaitingDescription = false;
                }
            });
        }, "rdv-rorschach-image-" + idx).start();
    }

    private void appendChatbotImageBubble(Image img, String caption, boolean allowRetry, int plateIndex) {
        if (rdvChatbotHistory == null || img == null) {
            return;
        }
        removeRetryBubbleForPlate(plateIndex);
        if (img.isError()) {
            appendChatbotRetryBubble(caption != null ? caption : "Image indisponible.", plateIndex);
            return;
        }
        VBox bubble = new VBox(6);
        bubble.getStyleClass().addAll("rdv-chatbot-bubble", "rdv-chatbot-bubble-assistant");
        Label header = new Label("Assistant :");
        header.getStyleClass().add("rdv-chatbot-bubble-author");
        ImageView iv = new ImageView(img);
        iv.getStyleClass().add("rdv-chatbot-bubble-image");
        iv.setFitWidth(300);
        iv.setPreserveRatio(true);
        Label cap = new Label(caption != null ? caption : "");
        cap.getStyleClass().add("rdv-chatbot-bubble-image-caption");
        cap.setWrapText(true);
        bubble.getChildren().addAll(header, iv, cap);
        if (allowRetry) {
            Button retry = new Button("Réessayer IA");
            retry.getStyleClass().add("rdv-chatbot-retry-btn");
            retry.setOnAction(ev -> showRorschachPlate(plateIndex));
            bubble.getChildren().add(retry);
        }
        bubble.setOpacity(0);
        bubble.setTranslateY(8);
        rdvChatbotHistory.getChildren().add(bubble);
        playChatBubbleEnterAnimation(bubble);
        if (rdvChatbotHistoryScroll != null) {
            Platform.runLater(() -> rdvChatbotHistoryScroll.setVvalue(1.0));
        }
    }

    private void appendChatbotRetryBubble(String message, int plateIndex) {
        if (rdvChatbotHistory == null) {
            return;
        }
        removeRetryBubbleForPlate(plateIndex);
        VBox bubble = new VBox(6);
        bubble.getStyleClass().addAll("rdv-chatbot-bubble", "rdv-chatbot-bubble-assistant");
        Label header = new Label("Assistant :");
        header.getStyleClass().add("rdv-chatbot-bubble-author");
        Label msg = new Label(message != null ? message : "Image IA indisponible.");
        msg.getStyleClass().add("rdv-chatbot-bubble-message");
        msg.setWrapText(true);
        Button retry = new Button("Réessayer IA");
        retry.getStyleClass().add("rdv-chatbot-retry-btn");
        retry.setOnAction(ev -> showRorschachPlate(plateIndex));
        bubble.getChildren().addAll(header, msg, retry);
        bubble.setOpacity(0);
        bubble.setTranslateY(8);
        rorschachRetryBubbles.put(plateIndex, bubble);
        rdvChatbotHistory.getChildren().add(bubble);
        playChatBubbleEnterAnimation(bubble);
        if (rdvChatbotHistoryScroll != null) {
            Platform.runLater(() -> rdvChatbotHistoryScroll.setVvalue(1.0));
        }
    }

    private void removeRetryBubbleForPlate(int plateIndex) {
        VBox old = rorschachRetryBubbles.remove(plateIndex);
        if (old != null && rdvChatbotHistory != null) {
            rdvChatbotHistory.getChildren().remove(old);
        }
    }

    /**
     * Fallback local fiable : génère une tache symétrique (style encre) même sans API IA.
     */
    private static Image buildLocalInkblotFallbackImage(int plateIndex) {
        int width = 360;
        int height = 220;
        WritableImage img = new WritableImage(width, height);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pw.setColor(x, y, Color.WHITE);
            }
        }
        Random rnd = new Random(1337L + plateIndex * 7919L);
        // Silhouette centrale légère pour rappeler la pliure Rorschach.
        for (int y = 18; y < height - 18; y++) {
            double alpha = 0.04 + (rnd.nextDouble() * 0.05);
            pw.setColor(width / 2, y, Color.rgb(22, 22, 22, alpha));
            pw.setColor((width / 2) - 1, y, Color.rgb(22, 22, 22, alpha * 0.6));
        }
        // Blobs noirs symétriques (aspect "encre") au lieu du bruit gris.
        int blobs = 34 + rnd.nextInt(10);
        for (int i = 0; i < blobs; i++) {
            int cx = 42 + rnd.nextInt((width / 2) - 66);
            int cy = 18 + rnd.nextInt(height - 36);
            int rx = 10 + rnd.nextInt(26);
            int ry = 8 + rnd.nextInt(24);
            drawMirroredInkBlob(pw, width, height, cx, cy, rx, ry, rnd);
        }
        return img;
    }

    private static void drawMirroredInkBlob(PixelWriter pw,
                                            int width,
                                            int height,
                                            int cx,
                                            int cy,
                                            int rx,
                                            int ry,
                                            Random rnd) {
        int minX = Math.max(0, cx - rx - 2);
        int maxX = Math.min((width / 2) - 1, cx + rx + 2);
        int minY = Math.max(0, cy - ry - 2);
        int maxY = Math.min(height - 1, cy + ry + 2);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double nx = (x - cx) / (double) Math.max(1, rx);
                double ny = (y - cy) / (double) Math.max(1, ry);
                double d = nx * nx + ny * ny;
                if (d > 1.25) {
                    continue;
                }
                // Bord irrégulier style encre.
                double edgeNoise = (rnd.nextDouble() - 0.5) * 0.24;
                double ink = 1.0 - d + edgeNoise;
                if (ink <= 0.08) {
                    continue;
                }
                double alpha = Math.min(0.92, 0.42 + ink * 0.62);
                int base = 10 + rnd.nextInt(20); // noir/brun très foncé
                Color c = Color.rgb(base, base, base, alpha);
                pw.setColor(x, y, c);
                int mx = width - 1 - x;
                pw.setColor(mx, y, c);
            }
        }
    }

    private static String truncateUi(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String formatThrowableForUi(Throwable t) {
        if (t == null) {
            return "erreur inconnue";
        }
        String msg = t.getMessage();
        if (msg != null && !msg.isBlank()) {
            return truncateUi(msg, 220);
        }
        return t.getClass().getSimpleName();
    }

    private static String humanProviderName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "IA";
        }
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.contains("openai")) {
            return "OpenAI";
        }
        if (low.contains("huggingface") || low.contains("hf")) {
            return "HuggingFace";
        }
        if (low.contains("pexels")) {
            return "Pexels";
        }
        return raw;
    }

    private static String svgToDataUrl(String svg) {
        try {
            String encoded = URLEncoder.encode(svg != null ? svg : "", StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return "data:image/svg+xml;utf8," + encoded;
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean shouldExitQuizForFreeChat(String msg) {
        if (msg == null) {
            return false;
        }
        String s = msg.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return false;
        }
        String compact = s.replaceAll("\\s+", " ").trim();
        if (compact.equals("stop")
                || compact.equals("quitter")
                || compact.equals("libre")
                || compact.equals("annuler")
                || compact.equals("arreter")
                || compact.equals("arrêter")
                || compact.equals("exit")) {
            return true;
        }
        return compact.contains("quitter le test")
                || compact.contains("quitter ce test")
                || compact.contains("arrêter le test")
                || compact.contains("arreter le test")
                || compact.contains("arrêter ce test")
                || compact.contains("arreter ce test")
                || compact.contains("stop le test")
                || compact.contains("sortir du test")
                || compact.contains("je veux quitter")
                || compact.contains("je veux arreter")
                || compact.contains("je veux arrêter")
                || compact.contains("on arrête")
                || compact.contains("on arrete")
                || compact.contains("fin du test")
                || compact.contains("passer en discussion libre")
                || compact.contains("mode libre");
    }

    private static ChatQuizMode detectQuizRequestFromMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return ChatQuizMode.NONE;
        }
        String s = msg.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .trim();
        String ascii = s
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("à", "a")
                .replace("â", "a")
                .replace("î", "i")
                .replace("ï", "i")
                .replace("ô", "o")
                .replace("ù", "u")
                .replace("û", "u")
                .replace("ç", "c");
        boolean asksQuiz = s.contains("quiz")
                || s.contains("test")
                || s.contains("questionnaire")
                || s.contains("rorschach")
                || s.contains("image")
                || ascii.contains("nheb quiz")
                || ascii.contains("nheb test")
                || ascii.contains("abda quiz")
                || ascii.contains("bda quiz")
                || ascii.contains("bdit quiz")
                || ascii.contains("start quiz")
                || s.contains("اختبار")
                || s.contains("كويز")
                || s.contains("quiz texte")
                || s.contains("quiz image");
        if (!asksQuiz) {
            return ChatQuizMode.NONE;
        }
        if (s.contains("rorschach")
                || s.contains("image")
                || s.contains("images")
                || s.contains("planche")
                || s.contains("tache")
                || s.contains("صورة")
                || s.contains("صور")
                || ascii.contains("quiz image")
                || ascii.contains("test image")
                || ascii.contains("quiz rorschach")) {
            return ChatQuizMode.RORSCHACH_IMAGES;
        }
        if (s.contains("questionnaire")
                || s.contains("question")
                || s.contains("texte")
                || s.contains("autisme")
                || s.contains("tsa")
                || s.contains("اسئلة")
                || s.contains("سؤال")
                || ascii.contains("quiz texte")
                || ascii.contains("test texte")
                || ascii.contains("quiz question")
                || ascii.contains("test question")) {
            return ChatQuizMode.AUTISM_QUESTIONNAIRE;
        }
        return ChatQuizMode.AUTISM_QUESTIONNAIRE;
    }

    private void hideRorschachImage() {
    }

    private static boolean isAffirmative(String msg) {
        String s = msg.trim().toLowerCase(Locale.ROOT);
        return s.equals("oui") || s.equals("o") || s.equals("yes") || s.equals("y");
    }

    private static boolean isNegative(String msg) {
        String s = msg.trim().toLowerCase(Locale.ROOT);
        return s.equals("non") || s.equals("n") || s.equals("no");
    }

    private static String buildAutismQuizFeedback(int score, int total) {
        double ratio = total <= 0 ? 0.0 : ((double) score / total);
        if (ratio >= 0.70) {
            return "Vous avez indiqué plusieurs marqueurs possibles. Un échange avec un spécialiste TSA pourrait être utile.";
        }
        if (ratio >= 0.40) {
            return "Quelques marqueurs sont présents. Vous pouvez suivre cela avec un professionnel si ces difficultés vous impactent.";
        }
        return "Peu de marqueurs ressortent dans ce quiz rapide. Continuez à observer vos besoins sensoriels et sociaux au quotidien.";
    }

    private String buildQuestionnaireSummary() {
        if (questionnaireResponses.isEmpty()) {
            return "Résumé: aucune réponse enregistrée.";
        }
        return "Résumé de vos réponses:\n- " + String.join("\n- ", questionnaireResponses);
    }

    private String generateRorschachAiAnalysis(List<String> descriptions, int detailedCount, int totalPlates) {
        try {
            String prompt = buildRorschachAiAnalysisPrompt(descriptions, detailedCount, totalPlates);
            String ai = aiService.chatRdv(prompt);
            if (ai != null && !ai.isBlank()
                    && !ai.startsWith("Clé ")
                    && !ai.startsWith("Provider IA non reconnu")
                    && !ai.toLowerCase(Locale.ROOT).startsWith("erreur api ia:")
                    && !ai.toLowerCase(Locale.ROOT).startsWith("http ")) {
                return ai.trim();
            }
        } catch (Throwable ignored) {
            // fallback local ci-dessous
        }
        return buildRorschachPedagogicFallbackAnalysis(descriptions, detailedCount, totalPlates);
    }

    private static String buildRorschachAiAnalysisPrompt(List<String> descriptions, int detailedCount, int totalPlates) {
        String lines = descriptions == null || descriptions.isEmpty()
                ? "Aucune description."
                : String.join("\n", descriptions);
        return "Tu es un assistant bienveillant, orienté accompagnement des personnes autistes ou avec un profil TSA (approche neuroaffirmative).\n"
                + "Contexte: l'utilisateur vient de décrire quatre taches d'encre symétriques (inspiration projective / Rorschach) "
                + "dans un cadre strictement non clinique et ludique.\n\n"
                + "Descriptions fournies par planche (à utiliser comme matière, sans recopier bêtement la liste à l'identique) :\n"
                + lines + "\n\n"
                + "Indicateur technique côté application (longueur des textes, sans aucune valeur psychologique ou diagnostique) : "
                + detailedCount + "/" + totalPlates + " réponses comptées comme « détaillées ».\n\n"
                + "Consignes:\n"
                + "- Réponds en français, ton calme et respectueux.\n"
                + "- Ne dis JAMAIS à la personne qu'elle est ou n'est pas autiste/TSA et n'établis aucun diagnostic médical ou psychiatrique.\n"
                + "- RELIE explicitement ton analyse au vécu des personnes TSA: par exemple perception du détail ou du global, "
                + "sensibilité aux contrastes ou à la symétrie, verbalisation de l'imaginaire, fatigue cognitive quand il faut décrire vite, "
                + "besoin de temps pour formuler, richesse d'association d'idées — toujours comme pistes générales ou « cela peut rappeler pour certaines personnes… », "
                + "sans attribuer un profil à l'utilisateur.\n"
                + "- Fais une analyse qualitative: vocabulaire, cohérence, thèmes (formes, mouvement, nature, émotions suggérées), contrastes entre planches.\n"
                + "- Si les textes sont très courts ou peu lisibles, le dire avec tact et proposer des pistes compatibles avec le vécu TSA "
                + "(prendre son temps, décrire le ressenti corporel ou les micro-détails) sans juger.\n"
                + "- Structure en quatre paragraphes numérotés 1) à 4): synthèse des descriptions; langage et imagination; "
                + "pont explicite avec le quotidien des personnes autistes (forces, stratégies, sensorialité) sans diagnostic; "
                + "phrase de clôture invitant à poursuivre (outils, entourage, professionnel si besoin).\n"
                + "- Pas de markdown (# ou **), texte brut adapté à une bulle de chat.\n";
    }

    private static String buildRorschachPedagogicFallbackAnalysis(List<String> descriptions,
                                                                  int detailedCount,
                                                                  int totalPlates) {
        if (descriptions == null || descriptions.isEmpty()) {
            return "Je n'ai pas encore assez de descriptions pour faire une lecture utile.\n\n"
                    + "Si vous voulez, on peut réessayer avec une méthode simple: décrire les formes, les contrastes, "
                    + "et ce que l'image vous évoque spontanément.\n\n"
                    + "Prendre son temps pour mettre des mots sur le ressenti est déjà un vrai point positif.";
        }
        int shortish = 0;
        for (String line : descriptions) {
            String t = line == null ? "" : line.replaceFirst("^Planche\\s+\\d+:\\s*", "").trim();
            if (t.length() < 12) {
                shortish++;
            }
        }
        StringBuilder b = new StringBuilder();
        b.append("Merci, vos descriptions donnent déjà une base intéressante. ")
                .append(shortish >= 3
                        ? "Plusieurs réponses sont encore très brèves; avec un peu plus de détails (formes, mouvement, ambiance), "
                        + "on obtient une lecture plus riche.\n\n"
                        : "Vos formulations montrent déjà une bonne mise en mots de ce que vous percevez.\n\n");
        b.append("Chaque planche active une perception un peu différente. Observer ce qui attire votre attention "
                + "(symétrie, contraste, densité) peut aider à mieux comprendre votre manière de traiter l'information visuelle.\n\n");
        b.append("Au quotidien, cet exercice peut aussi servir à ralentir et clarifier le ressenti, surtout en période de surcharge. "
                + "Indicateur technique (non clinique): ")
                .append(detailedCount).append("/").append(totalPlates)
                .append(" réponses dépassent le seuil « détaillé » de l'application.\n\n");
        b.append("Si vous voulez, on peut continuer avec 2 ou 3 pistes concrètes adaptées à votre quotidien, sans poser de diagnostic.");
        return b.toString();
    }

    private void loadMedecins() {
        medecinsCompteActif = new ArrayList<>();
        try {
            medecinsCompteActif = userService.findByRole(Role.MEDECIN).stream()
                    .filter(User::isActif)
                    .sorted(Comparator
                            .comparing((User u) -> blankToEmpty(u.getNom()), String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(u -> blankToEmpty(u.getPrenom()), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        } catch (SQLException ignored) {
            // liste vide
        }
    }

    private void fillSpecialtyFilter() {
        if (rdvSpecialtyFilter == null) {
            return;
        }
        TreeSet<String> specs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (User u : medecinsCompteActif) {
            String s = u.getSpecialite();
            if (s != null && !s.isBlank()) {
                specs.add(s.trim());
            }
        }
        List<String> items = new ArrayList<>();
        items.add(ALL_SPECIALTIES);
        items.addAll(specs);
        rdvSpecialtyFilter.getItems().setAll(items);
        rdvSpecialtyFilter.getSelectionModel().selectFirst();
    }

    private void refreshDoctorCards() {
        if (rdvDoctorsList == null) {
            return;
        }
        rdvDoctorsList.getChildren().clear();
        List<User> filtered = medecinsCompteActif.stream()
                .filter(this::matchesFilters)
                .collect(Collectors.toList());
        if (rdvCountNumLabel != null) {
            rdvCountNumLabel.setText(String.valueOf(filtered.size()));
        }
        if (filtered.isEmpty()) {
            Label empty = new Label(medecinsCompteActif.isEmpty()
                    ? "Aucun professionnel avec compte actif pour le moment. Les médecins inscrits sur AutiCare apparaîtront ici."
                    : "Aucun professionnel ne correspond à ces filtres.");
            empty.setWrapText(true);
            empty.getStyleClass().add("rdv-tip-text");
            rdvDoctorsList.getChildren().add(empty);
            return;
        }
        boolean alt = false;
        for (User u : filtered) {
            rdvDoctorsList.getChildren().add(buildDoctorCard(u, alt));
            alt = !alt;
        }
    }

    private boolean matchesFilters(User u) {
        String selectedSpec = rdvSpecialtyFilter != null ? rdvSpecialtyFilter.getSelectionModel().getSelectedItem() : null;
        if (selectedSpec != null && !selectedSpec.isBlank() && !ALL_SPECIALTIES.equals(selectedSpec)) {
            String us = u.getSpecialite() != null ? u.getSpecialite().trim() : "";
            if (!us.equalsIgnoreCase(selectedSpec.trim())) {
                return false;
            }
        }
        String lieu = rdvLieuFilter != null && rdvLieuFilter.getText() != null
                ? rdvLieuFilter.getText().trim().toLowerCase(Locale.ROOT)
                : "";
        if (!lieu.isEmpty()) {
            String cab = u.getCabinet() != null ? u.getCabinet().toLowerCase(Locale.ROOT) : "";
            String adr = u.getAdresse() != null ? u.getAdresse().toLowerCase(Locale.ROOT) : "";
            if (!cab.contains(lieu) && !adr.contains(lieu)) {
                return false;
            }
        }
        return true;
    }

    private VBox buildDoctorCard(User u, boolean altAvatar) {
        VBox card = new VBox(16);
        card.getStyleClass().addAll("rdv-card", "rdv-card-v2");

        String cab = u.getCabinet() != null && !u.getCabinet().isBlank() ? u.getCabinet().trim() : "";
        String adr = u.getAdresse() != null && !u.getAdresse().isBlank() ? u.getAdresse().trim() : "";
        String mail = u.getEmail() != null && !u.getEmail().isBlank() ? u.getEmail().trim() : "";
        String tel = u.getTelephone() != null && !u.getTelephone().isBlank() ? u.getTelephone().trim() : "";

        String spec = u.getSpecialite() != null && !u.getSpecialite().isBlank()
                ? u.getSpecialite().trim()
                : "Professionnel";

        User cur = AppState.getCurrentUser();
        MedecinRatingService.AvgRating avg = safeAverageRating(u.getId());
        Optional<MedecinRatingService.PatientReview> myReview = Optional.empty();
        if (cur != null) {
            myReview = safeFindReview(u.getId(), cur);
        }
        AtomicInteger starPick = new AtomicInteger(0);
        myReview.ifPresent(r -> starPick.set(r.stars()));

        HBox header = new HBox(14);
        header.setAlignment(Pos.TOP_LEFT);

        StackPane av = UserAvatarGraphic.build(u, 58, UserAvatarGraphic.initialsFor(u), "rdv-av-txt");
        av.getStyleClass().add("rdv-av");
        if (altAvatar) {
            av.getStyleClass().add("rdv-av-alt");
        }

        VBox nameCol = new VBox(6);
        nameCol.setAlignment(Pos.TOP_LEFT);

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(PublicRdvDoctorSidebarHelper.formatDrName(u));
        nameLbl.getStyleClass().add("rdv-name");

        Label badge = new Label(spec);
        badge.getStyleClass().addAll("rdv-badge", "rdv-badge-purple");

        HBox starsRow = (cur != null && cur.getId() != u.getId())
                ? buildInteractiveStarsRow(starPick)
                : buildReadOnlyStarsRow(avg);
        titleRow.getChildren().addAll(nameLbl, badge, starsRow);

        Label specSub = new Label(buildSpecialiteSubline(spec));
        specSub.getStyleClass().add("rdv-card-spec-sub");

        Label tarifSub = new Label("Tarif consultation : " + RdvTarifFormat.format(u.getTarifConsultation()));
        tarifSub.getStyleClass().add("rdv-card-tarif-highlight");

        nameCol.getChildren().addAll(titleRow, specSub, tarifSub);
        if (avg.count() > 0) {
            Label avgTxt = new Label(avg.labelFr());
            avgTxt.getStyleClass().add("rdv-card-rating-avg-tiny");
            nameCol.getChildren().add(avgTxt);
        } else {
            Label noAvg = new Label("Pas encore d'avis");
            noAvg.getStyleClass().add("rdv-card-rating-hint");
            nameCol.getChildren().add(noAvg);
        }

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button rdvBtn = new Button("Prendre RDV");
        rdvBtn.getStyleClass().add("rdv-btn");
        rdvBtn.setOnAction(e -> startBooking(u));

        Button proposalBtn = new Button("✉ Proposer un RDV");
        proposalBtn.getStyleClass().add("rdv-proposal-btn");
        proposalBtn.setOnAction(e -> startProposalRequest(u));

        HBox actionsCol = new HBox(8);
        actionsCol.setAlignment(Pos.CENTER_RIGHT);
        actionsCol.getChildren().addAll(rdvBtn, proposalBtn);

        header.getChildren().addAll(av, nameCol, headerSpacer, actionsCol);

        Label bio = new Label(buildRdvCardBio(cab));
        bio.setWrapText(true);
        bio.getStyleClass().add("rdv-card-bio");

        VBox ratingSection = buildReviewBlock(u, starPick, myReview);

        Label infoHeading = new Label("Coordonnées & lieu");
        infoHeading.getStyleClass().add("rdv-mod-section-title");

        VBox addrCell = rdvModernContactCell("📍", "Adresse", formatLocationHint(adr, cab));
        addrCell.getStyleClass().add("rdv-mod-cell-address");

        HBox telMailRow = new HBox(0);
        telMailRow.setMaxWidth(Double.MAX_VALUE);
        telMailRow.getStyleClass().add("rdv-mod-split-row");

        VBox telCell = rdvModernContactCell("📞", "Téléphone", tel);
        telCell.getStyleClass().add("rdv-mod-cell-half-left");
        Region telMailSep = new Region();
        telMailSep.getStyleClass().add("rdv-mod-vsep");
        VBox mailCell = rdvModernContactCell("✉", "E-mail", mail);
        mailCell.getStyleClass().add("rdv-mod-cell-half-right");
        HBox.setHgrow(telCell, Priority.ALWAYS);
        HBox.setHgrow(mailCell, Priority.ALWAYS);
        telMailRow.getChildren().addAll(telCell, telMailSep, mailCell);

        VBox infoPanel = new VBox(0);
        infoPanel.getStyleClass().add("rdv-card-info-modern");
        infoPanel.getChildren().addAll(infoHeading, addrCell, telMailRow);

        if (!cab.isBlank()) {
            VBox cabCell = rdvModernContactCell("🏥", "Cabinet", cab);
            cabCell.getStyleClass().add("rdv-mod-cabinet-cell");
            infoPanel.getChildren().add(cabCell);
        }

        card.getChildren().addAll(header, bio, ratingSection, infoPanel);
        return card;
    }

    private VBox buildReviewBlock(User medecin, AtomicInteger starSelection, Optional<MedecinRatingService.PatientReview> myReview) {
        VBox box = new VBox(10);
        box.getStyleClass().add("rdv-card-rating-section");

        User cur = AppState.getCurrentUser();
        if (cur == null) {
            Label hint = new Label("Connectez-vous pour noter ce praticien (étoiles ci-dessus) et rédiger un commentaire.");
            hint.setWrapText(true);
            hint.getStyleClass().add("rdv-card-rating-hint");
            box.getChildren().add(hint);
            return box;
        }
        if (cur.getId() == medecin.getId()) {
            return box;
        }

        VBox card = new VBox(12);
        card.getStyleClass().add("rdv-review-card");

        Label cardTitle = new Label("Votre avis");
        cardTitle.getStyleClass().add("rdv-review-card-title");

        Label cardSub = new Label("Une note via les étoiles dans l’en-tête, puis un commentaire optionnel.");
        cardSub.setWrapText(true);
        cardSub.getStyleClass().add("rdv-review-card-sub");

        TextArea commentArea = new TextArea();
        commentArea.setPromptText("Décrivez votre expérience : accueil, consultation, suivi…");
        commentArea.setWrapText(true);
        commentArea.setPrefRowCount(4);
        commentArea.setMinHeight(96);
        commentArea.getStyleClass().add("rdv-review-textarea");

        final int maxChars = 500;
        Label counter = new Label("0 / " + maxChars);
        counter.getStyleClass().add("rdv-review-counter");
        commentArea.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.length() > maxChars) {
                commentArea.setText(newV.substring(0, maxChars));
                return;
            }
            int n = newV == null ? 0 : newV.length();
            counter.setText(n + " / " + maxChars);
        });
        counter.setText((commentArea.getText() != null ? commentArea.getText().length() : 0) + " / " + maxChars);

        Button publish = new Button("Publier mon avis");
        publish.getStyleClass().addAll("rdv-review-submit", "rdv-review-btn-primary");
        publish.setMaxWidth(Region.USE_PREF_SIZE);

        Button seeAllReviews = new Button("Voir tous les avis");
        seeAllReviews.getStyleClass().addAll("rdv-review-submit", "rdv-review-btn-secondary");
        seeAllReviews.setMaxWidth(Region.USE_PREF_SIZE);
        seeAllReviews.setOnAction(ev -> showAllReviewsDialog(medecin));

        VBox reviewActions = new VBox(6);
        reviewActions.setAlignment(Pos.CENTER);
        reviewActions.getStyleClass().add("rdv-review-actions");
        reviewActions.getChildren().addAll(publish, seeAllReviews);

        int medecinId = medecin.getId();
        int patientId = cur.getId();
        publish.setOnAction(ev -> {
            int s = starSelection.get();
            if (s < 1 || s > 5) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setTitle("Avis");
                a.setHeaderText(null);
                a.setContentText("Cliquez sur les étoiles à côté du nom pour choisir une note de 1 à 5.");
                a.showAndWait();
                return;
            }
            try {
                String raw = commentArea.getText() != null ? commentArea.getText().trim() : "";
                ratingService.upsertReview(medecinId, patientId, s, raw.isEmpty() ? null : raw);
                refreshDoctorCards();
            } catch (SQLException ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setTitle("Avis");
                a.setHeaderText(null);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Impossible d'enregistrer votre avis.");
                a.showAndWait();
            }
        });

        card.getChildren().addAll(cardTitle, cardSub, commentArea, counter, reviewActions);
        box.getChildren().add(card);
        return box;
    }

    private void showAllReviewsDialog(User medecin) {
        if (medecin == null || medecin.getId() <= 0) {
            return;
        }
        List<MedecinRatingService.PublicReview> reviews;
        try {
            reviews = ratingService.listReviewsForMedecin(medecin.getId());
        } catch (SQLException ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Avis");
            err.setHeaderText(null);
            err.setContentText(ex.getMessage() != null ? ex.getMessage() : "Impossible de charger les avis.");
            err.showAndWait();
            return;
        }

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Avis");
        dialog.setHeaderText("Avis pour " + PublicRdvDoctorSidebarHelper.formatDrName(medecin));

        DialogPane pane = dialog.getDialogPane();
        pane.setPrefWidth(620);
        pane.setPrefHeight(520);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10, 4, 2, 4));

        if (reviews.isEmpty()) {
            Label empty = new Label("Aucun commentaire disponible pour ce médecin.");
            empty.setWrapText(true);
            empty.getStyleClass().add("rdv-card-rating-hint");
            content.getChildren().add(empty);
        } else {
            for (MedecinRatingService.PublicReview review : reviews) {
                VBox reviewCard = new VBox(6);
                reviewCard.setPadding(new Insets(10));
                reviewCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10; "
                        + "-fx-border-color: #e6e8ef; -fx-border-radius: 10;");

                String headerTxt = starsToText(review.stars()) + "  " + review.auteurAffiche() + "  •  " + review.dateAffiche();
                Label header = new Label(headerTxt);
                header.setStyle("-fx-font-weight: 700; -fx-text-fill: #2f3a52;");
                header.setWrapText(true);

                String comment = review.commentaire() != null && !review.commentaire().isBlank()
                        ? review.commentaire().trim()
                        : "(Aucun commentaire texte)";
                Label body = new Label(comment);
                body.setWrapText(true);
                body.setStyle("-fx-text-fill: #3d4a63;");

                reviewCard.getChildren().addAll(header, body);
                content.getChildren().add(reviewCard);
            }
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(430);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private static String starsToText(int stars) {
        int s = Math.max(0, Math.min(5, stars));
        return "★".repeat(s) + "☆".repeat(5 - s);
    }

    private static HBox buildReadOnlyStarsRow(MedecinRatingService.AvgRating avg) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rdv-stars-row");
        int filled = 0;
        if (avg.count() > 0) {
            filled = (int) Math.round(avg.average());
            filled = Math.min(5, Math.max(0, filled));
        }
        for (int i = 0; i < 5; i++) {
            Label star = new Label(i < filled ? "★" : "☆");
            star.getStyleClass().add(i < filled ? "rdv-star-readonly-on" : "rdv-star-readonly-off");
            row.getChildren().add(star);
        }
        return row;
    }

    private static HBox buildInteractiveStarsRow(AtomicInteger starPick) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rdv-stars-row");
        Label[] labs = new Label[5];
        for (int i = 0; i < 5; i++) {
            final int value = i + 1;
            Label L = new Label("☆");
            L.setCursor(Cursor.HAND);
            L.getStyleClass().add("rdv-star-btn");
            labs[i] = L;
            L.setOnMouseClicked(e -> {
                starPick.set(value);
                applyInteractiveStarStyles(labs, starPick.get());
            });
            L.setOnMouseEntered(e -> applyInteractiveStarStyles(labs, value));
            L.setOnMouseExited(e -> applyInteractiveStarStyles(labs, starPick.get()));
            row.getChildren().add(L);
        }
        applyInteractiveStarStyles(labs, starPick.get());
        return row;
    }

    private static void applyInteractiveStarStyles(Label[] labels, int lit) {
        int n = Math.max(0, Math.min(5, lit));
        for (int i = 0; i < 5; i++) {
            boolean on = n > 0 && i < n;
            labels[i].setText(on ? "★" : "☆");
            labels[i].getStyleClass().removeAll("rdv-star-on", "rdv-star-off");
            labels[i].getStyleClass().add(on ? "rdv-star-on" : "rdv-star-off");
        }
    }

    private static String buildSpecialiteSubline(String spec) {
        String s = spec != null && !spec.isBlank() ? spec.trim() : "—";
        return "Spécialité : " + s + ".";
    }

    private MedecinRatingService.AvgRating safeAverageRating(int medecinId) {
        try {
            return ratingService.averageForMedecin(medecinId);
        } catch (SQLException e) {
            return new MedecinRatingService.AvgRating(0, 0);
        }
    }

    private Optional<MedecinRatingService.PatientReview> safeFindReview(int medecinId, User patient) {
        try {
            return ratingService.findPatientReview(medecinId, patient.getId());
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /** Bloc coordonnées type fiche moderne (sans pastille carrée). */
    private static VBox rdvModernContactCell(String emoji, String fieldKey, String value) {
        VBox cell = new VBox(6);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.getStyleClass().add("rdv-mod-cell");

        HBox cap = new HBox(8);
        cap.setAlignment(Pos.CENTER_LEFT);
        Label ic = new Label(emoji);
        ic.getStyleClass().add("rdv-mod-emoji");
        Label kl = new Label(fieldKey.toUpperCase(Locale.FRENCH));
        kl.getStyleClass().add("rdv-mod-key");
        cap.getChildren().addAll(ic, kl);

        String v = value != null && !value.isBlank() ? value.trim() : "—";
        Label vl = new Label(v);
        vl.setWrapText(true);
        vl.getStyleClass().add("rdv-mod-value");

        cell.getChildren().addAll(cap, vl);
        return cell;
    }

    private static String buildRdvCardBio(String cabinetName) {
        String base = "Praticien accompagnant les personnes avec TSA.";
        if (cabinetName != null && !cabinetName.isBlank()) {
            return base + " Cabinet : " + cabinetName.trim() + ".";
        }
        return base;
    }

    private static String formatLocationHint(String adresse, String cabinetName) {
        if (adresse != null && !adresse.isBlank()) {
            String[] parts = adresse.split("[,;\n]");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last.length() > 64 ? last.substring(0, 61) + "…" : last;
            }
        }
        if (cabinetName != null && !cabinetName.isBlank()) {
            return cabinetName.trim();
        }
        return "—";
    }

    private static String blankToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * Invité : remplace la liste des professionnels par une petite carte (plus de modale plein écran).
     */
    private void showLoginRequiredInline(User u) {
        if (rdvDoctorsList == null) {
            return;
        }
        rdvDoctorsList.getChildren().clear();

        String drName = u != null ? PublicRdvDoctorSidebarHelper.formatDrName(u) : "ce professionnel";
        String subText = "ce professionnel".equals(drName)
                ? "Connectez-vous pour envoyer votre proposition de rendez-vous à " + drName + "."
                : "Connectez-vous pour envoyer votre proposition de rendez-vous au " + drName + ".";

        StackPane logoMini = new StackPane();
        logoMini.setMinSize(40, 40);
        logoMini.setPrefSize(40, 40);
        logoMini.setMaxSize(40, 40);
        logoMini.getStyleClass().add("rdv-inline-login-logo");
        Label letter = new Label("A");
        letter.getStyleClass().add("rdv-inline-login-logo-letter");
        logoMini.getChildren().add(letter);

        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Vous devez vous connecter");
        title.getStyleClass().add("rdv-inline-login-title");
        Label sparkle = new Label("✦");
        sparkle.getStyleClass().add("rdv-inline-login-sparkle");
        titleRow.getChildren().addAll(title, sparkle);

        Label sub = new Label(subText);
        sub.setWrapText(true);
        sub.getStyleClass().add("rdv-inline-login-sub");

        VBox textCol = new VBox(8);
        textCol.getChildren().addAll(titleRow, sub);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        HBox topRow = new HBox(14);
        topRow.setAlignment(Pos.TOP_LEFT);
        topRow.getChildren().addAll(logoMini, textCol);

        Button btnLogin = new Button("Se connecter");
        btnLogin.getStyleClass().add("rdv-inline-login-btn-primary");
        btnLogin.setOnAction(e -> {
            try {
                if (shell != null) {
                    shell.loadPage("login");
                } else {
                    MainApp.showLogin();
                }
            } catch (IOException ex) {
                showNavError(ex);
            }
        });

        Button btnSignup = new Button("Créer un compte");
        btnSignup.getStyleClass().add("rdv-inline-login-btn-secondary");
        btnSignup.setOnAction(e -> {
            try {
                if (shell != null) {
                    shell.loadPage("signup");
                } else {
                    MainApp.showPublicPage("signup");
                }
            } catch (IOException ex) {
                showNavError(ex);
            }
        });

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        actions.setMaxWidth(Double.MAX_VALUE);
        actions.getStyleClass().add("rdv-inline-login-actions");
        actions.getChildren().addAll(btnLogin, btnSignup);

        Hyperlink back = new Hyperlink("← Afficher les professionnels");
        back.getStyleClass().add("rdv-inline-login-back");
        back.setOnAction(e -> refreshDoctorCards());

        VBox card = new VBox(14);
        card.setMaxWidth(420);
        card.setPadding(new Insets(20, 22, 22, 22));
        card.getStyleClass().add("rdv-inline-login-card");
        card.getChildren().addAll(topRow, actions, back);

        StackPane holder = new StackPane();
        holder.setPadding(new Insets(4, 8, 20, 8));
        holder.setMinHeight(260);
        holder.getStyleClass().add("rdv-inline-login-holder");
        holder.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        rdvDoctorsList.getChildren().add(holder);
    }

    private void showNavError(IOException ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Navigation");
        a.setHeaderText(null);
        a.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        a.showAndWait();
    }

    private void startBooking(User u) {
        if (AppState.getCurrentUser() == null) {
            showLoginRequiredInline(u);
            return;
        }
        if (u == null || u.getId() <= 0) {
            return;
        }
        String displayName = PublicRdvDoctorSidebarHelper.formatDrName(u);
        AppState.beginPublicRdvBooking(u.getId(), displayName);
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv-booking");
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Navigation");
            a.setHeaderText(null);
            a.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            a.showAndWait();
        }
    }

    private void startProposalRequest(User medecin) {
        User current = AppState.getCurrentUser();
        if (current == null) {
            showLoginRequiredInline(medecin);
            return;
        }
        if (current.getRole() != Role.PATIENT && current.getRole() != Role.PARENT) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Proposition de rendez-vous");
            a.setHeaderText(null);
            a.setContentText("Seuls les comptes patient ou parent peuvent envoyer une proposition de rendez-vous.");
            a.showAndWait();
            return;
        }
        if (medecin == null || medecin.getId() <= 0) {
            return;
        }

        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(1));
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        ComboBox<String> timeCombo = new ComboBox<>();
        List<String> times = new ArrayList<>();
        for (int h = 7; h <= 20; h++) {
            times.add(String.format("%02d:00", h));
            times.add(String.format("%02d:30", h));
        }
        timeCombo.getItems().setAll(times);
        timeCombo.getSelectionModel().select("09:00");

        TextArea motifArea = new TextArea();
        motifArea.setWrapText(true);
        motifArea.setPrefRowCount(4);
        motifArea.setPromptText("Ex. disponibilité après 18h, suivi, urgence, etc.");

        String drName = PublicRdvDoctorSidebarHelper.formatDrName(medecin);

        Label kicker = new Label("PROPOSITION DE CRÉNEAU");
        kicker.getStyleClass().add("rdv-proposal-kicker");

        Label formTitle = new Label("Indiquez votre disponibilité");
        formTitle.getStyleClass().add("rdv-proposal-form-title");
        formTitle.setWrapText(true);

        Label formSub = new Label("La demande sera envoyée à " + drName
                + ". Le médecin pourra accepter ou refuser ; vous serez prévenu dans vos notifications.");
        formSub.getStyleClass().add("rdv-proposal-form-sub");
        formSub.setWrapText(true);

        Label pill = new Label("Non confirmé — en attente du médecin");
        pill.getStyleClass().add("rdv-proposal-pill");

        HBox rowDt = new HBox(14);
        rowDt.setAlignment(Pos.TOP_LEFT);
        VBox colDate = new VBox(6);
        Label capDate = new Label("Date souhaitée");
        capDate.getStyleClass().add("rdv-proposal-field-cap");
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.getStyleClass().add("rdv-proposal-datepicker");
        colDate.getChildren().addAll(capDate, datePicker);
        HBox.setHgrow(colDate, Priority.ALWAYS);

        VBox colTime = new VBox(6);
        Label capTime = new Label("Heure souhaitée");
        capTime.getStyleClass().add("rdv-proposal-field-cap");
        timeCombo.setMaxWidth(Double.MAX_VALUE);
        timeCombo.getStyleClass().add("rdv-proposal-combo");
        colTime.getChildren().addAll(capTime, timeCombo);
        HBox.setHgrow(colTime, Priority.ALWAYS);
        rowDt.getChildren().addAll(colDate, colTime);

        Label capMotif = new Label("Votre message au médecin");
        capMotif.getStyleClass().add("rdv-proposal-field-cap");
        motifArea.setMaxWidth(Double.MAX_VALUE);
        motifArea.getStyleClass().add("rdv-proposal-textarea");

        Label hint = new Label("Conseil : restez bref et précis (255 caractères maximum).");
        hint.getStyleClass().add("rdv-proposal-hint");
        hint.setWrapText(true);

        VBox formRoot = new VBox(14);
        formRoot.getStyleClass().add("rdv-proposal-form-root");
        formRoot.setMaxWidth(500);
        formRoot.getChildren().addAll(kicker, formTitle, formSub, pill, rowDt, capMotif, motifArea, hint);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Proposer un rendez-vous");
        dialog.setHeaderText(null);
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (rdvDoctorsList != null && rdvDoctorsList.getScene() != null && rdvDoctorsList.getScene().getWindow() != null) {
            dialog.initOwner(rdvDoctorsList.getScene().getWindow());
        }
        URL css = MainApp.class.getResource("/styles/public-page.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("rdv-proposal-dialog");
        dialog.getDialogPane().setContent(formRoot);
        dialog.getDialogPane().setPrefWidth(540);

        ButtonType sendBtn = new ButtonType("Envoyer la proposition", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(sendBtn, ButtonType.CANCEL);

        dialog.setOnShown(ev -> {
            javafx.scene.Node okNode = dialog.getDialogPane().lookupButton(sendBtn);
            if (okNode instanceof Button b) {
                b.getStyleClass().add("rdv-proposal-submit-btn");
            }
            javafx.scene.Node cancelNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (cancelNode instanceof Button b) {
                b.getStyleClass().add("rdv-proposal-cancel-btn");
            }
        });

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != sendBtn) {
            return;
        }

        LocalDate d = datePicker.getValue();
        String t = timeCombo.getValue();
        String motif = motifArea.getText() != null ? motifArea.getText().trim() : "";
        if (d == null || t == null || t.isBlank()) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Proposition de rendez-vous");
            a.setHeaderText(null);
            a.setContentText("Veuillez indiquer une date et une heure.");
            a.showAndWait();
            return;
        }
        if (motif.isBlank()) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Proposition de rendez-vous");
            a.setHeaderText(null);
            a.setContentText("Veuillez préciser votre proposition en quelques mots.");
            a.showAndWait();
            return;
        }
        if (motif.length() > 255) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Proposition de rendez-vous");
            a.setHeaderText(null);
            a.setContentText("Le motif est trop long (maximum 255 caractères).");
            a.showAndWait();
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(t);
        } catch (Exception e) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Proposition de rendez-vous");
            a.setHeaderText(null);
            a.setContentText("Heure invalide.");
            a.showAndWait();
            return;
        }
        LocalDateTime proposedAt = LocalDateTime.of(d, time);
        if (!proposedAt.isAfter(LocalDateTime.now())) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Proposition de rendez-vous");
            a.setHeaderText(null);
            a.setContentText("Veuillez choisir un créneau futur.");
            a.showAndWait();
            return;
        }

        try {
            Appointment appt = new Appointment();
            appt.setMedecinId(medecin.getId());
            appt.setPatientId(current.getId());
            appt.setPatientNom(current.getNom() != null ? current.getNom().trim() : "");
            appt.setPatientPrenom(current.getPrenom() != null ? current.getPrenom().trim() : "");
            appt.setDateHeure(proposedAt);
            appt.setMotif(motif);
            appt.setStatus(AppointmentStatus.EN_ATTENTE);
            appt.setMedecinDemandeLue(false);
            appt.setPatientReponseLue(true);
            appt.setNotes("Proposition envoyée par le patient (hors créneau prédéfini médecin).");
            appointmentService.add(appt);

            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Proposition envoyée");
            ok.setHeaderText(null);
            ok.setContentText("Votre proposition a été envoyée au médecin. "
                    + "Vous recevrez une réponse (acceptée ou refusée) dans vos notifications.");
            ok.showAndWait();
        } catch (SQLException ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Proposition de rendez-vous");
            err.setHeaderText(null);
            err.setContentText(ex.getMessage() != null ? ex.getMessage() : "Envoi impossible.");
            err.showAndWait();
        }
    }

    private static int resolveMedecinId(String nameKey) {
        String key = nameKey.toLowerCase(Locale.ROOT);
        try {
            UserService us = new UserService();
            for (User u : us.findByRole(Role.MEDECIN)) {
                String nom = u.getNom() != null ? u.getNom().toLowerCase(Locale.ROOT) : "";
                String prenom = u.getPrenom() != null ? u.getPrenom().toLowerCase(Locale.ROOT) : "";
                if (nom.contains(key) || prenom.contains(key) || (nom + " " + prenom).contains(key)) {
                    return u.getId();
                }
            }
        } catch (SQLException ignored) {
            // fallback ci-dessous
        }
        return -1;
    }
}
