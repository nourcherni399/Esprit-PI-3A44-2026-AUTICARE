package org.example.controllers;

import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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
    private ImageView rdvChatbotImageView;
    @FXML
    private Label rdvChatbotImageCaption;
    @FXML
    private Button rdvQuizQuestionnaireBtn;
    @FXML
    private Button rdvQuizImagesBtn;
    @FXML
    private Button rdvChatbotExpandBtn;
    private VBox rdvAssistantTypingBubble;
    private boolean rdvChatbotExpanded;

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
    private final List<String> rorschachResponses = new ArrayList<>();
    private static final HashSet<String> CHAT_ESSENTIAL_WORDS = new HashSet<>(List.of(
            "autisme", "tsa", "communication", "interactions", "sensorielle", "routine",
            "stereotypies", "diagnostic", "accompagnement", "surcharge", "consultation"
    ));

    private static final String[] AUTISM_QUIZ_QUESTIONS = {
            "Je me sens souvent submergé(e) par les bruits, lumières ou textures. (oui/non)",
            "Les changements imprévus dans ma routine me mettent en difficulté. (oui/non)",
            "Je trouve les interactions sociales spontanées fatigantes ou difficiles à décoder. (oui/non)",
            "Je préfère des consignes claires et précises plutôt que implicites. (oui/non)",
            "J’ai des centres d’intérêt très intenses ou spécifiques. (oui/non)",
            "Le contact visuel peut être inconfortable pour moi. (oui/non)",
            "Je peux avoir besoin de temps pour comprendre les émotions des autres. (oui/non)",
            "Je me sens mieux avec des routines stables au quotidien. (oui/non)"
    };

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
        if (rdvChatbotImageView != null) {
            rdvChatbotImageView.setVisible(false);
            rdvChatbotImageView.setManaged(false);
        }
        if (rdvChatbotImageCaption != null) {
            rdvChatbotImageCaption.setText("");
        }
    }

    @FXML
    private void onClearRdvChatbotHistory() {
        if (rdvChatbotHistory != null) {
            rdvChatbotHistory.getChildren().clear();
        }
        rdvAssistantTypingBubble = null;
        hideRorschachImage();
        chatQuizMode = ChatQuizMode.NONE;
        chatQuizIndex = -1;
        chatQuizScore = 0;
        appendChatbotLine("Assistant", "Conversation réinitialisée.");
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
            rdvChatbotPanel.setPrefWidth(430);
            rdvChatbotPanel.setMaxWidth(430);
            rdvChatbotPanel.setPrefHeight(560);
            rdvChatbotPanel.setMaxHeight(560);
        }
        if (rdvChatbotExpandBtn != null) {
            rdvChatbotExpandBtn.setText("⤢");
        }
    }

    private void setChatbotPanelExpanded() {
        rdvChatbotExpanded = true;
        if (rdvChatbotPanel != null) {
            rdvChatbotPanel.setPrefWidth(520);
            rdvChatbotPanel.setMaxWidth(520);
            rdvChatbotPanel.setPrefHeight(700);
            rdvChatbotPanel.setMaxHeight(700);
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
        appendChatbotLine("Vous", msg);
        rdvChatbotInput.clear();
        if (chatQuizMode == ChatQuizMode.AUTISM_QUESTIONNAIRE) {
            if (shouldExitQuizForFreeChat(msg)) {
                chatQuizMode = ChatQuizMode.NONE;
                appendChatbotLine("Assistant", "Mode libre activé. Posez votre question.");
            } else {
                handleQuestionnaireAnswer(msg);
                return;
            }
        }
        if (chatQuizMode == ChatQuizMode.RORSCHACH_IMAGES) {
            if (shouldExitQuizForFreeChat(msg)) {
                chatQuizMode = ChatQuizMode.NONE;
                appendChatbotLine("Assistant", "Mode libre activé. Posez votre question.");
            } else {
                handleRorschachAnswer(msg);
                return;
            }
        }
        showAssistantTypingIndicator();
        new Thread(() -> {
            String answer = generateRdvHelpAnswer(msg);
            Platform.runLater(() -> {
                removeAssistantTypingIndicator();
                appendChatbotLine("Assistant", answer);
            });
        }, "rdv-ai-chat").start();
    }

    @FXML
    private void onStartAutismQuestionnaireQuiz() {
        chatQuizMode = ChatQuizMode.AUTISM_QUESTIONNAIRE;
        chatQuizIndex = 0;
        chatQuizScore = 0;
        questionnaireResponses.clear();
        hideRorschachImage();
        appendChatbotLine("Assistant", "Quiz questionnaire lancé (8 questions, répondez par oui/non).");
        appendChatbotLine("Assistant", AUTISM_QUIZ_QUESTIONS[chatQuizIndex]);
    }

    @FXML
    private void onStartRorschachImageQuiz() {
        chatQuizMode = ChatQuizMode.RORSCHACH_IMAGES;
        chatQuizIndex = 0;
        chatQuizScore = 0;
        rorschachResponses.clear();
        appendChatbotLine("Assistant", rorschachQuestionForIndex(chatQuizIndex));
        showRorschachPlate(chatQuizIndex);
    }

    @FXML
    private void onQuickThemeEmotions() {
        pushQuickPrompt("J’ai besoin d’aide pour gérer mes émotions avant un rendez-vous.");
    }

    @FXML
    private void onQuickThemeSensoriel() {
        pushQuickPrompt("Comment réduire la surcharge sensorielle pendant la consultation ?");
    }

    @FXML
    private void onQuickThemeRoutine() {
        pushQuickPrompt("Aide-moi à préparer une routine simple avant mon rendez-vous.");
    }

    @FXML
    private void onQuickThemeCalme() {
        pushQuickPrompt("Propose-moi des techniques courtes pour me calmer avant le rendez-vous.");
    }

    @FXML
    private void onMediaActionInfo() {
        appendChatbotLine("Assistant", "La capture image/voix arrive bientôt. Pour l’instant, écrivez votre message.");
    }

    private void pushQuickPrompt(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        appendChatbotLine("Vous", text);
        showAssistantTypingIndicator();
        new Thread(() -> {
            String answer = generateRdvHelpAnswer(text);
            Platform.runLater(() -> {
                removeAssistantTypingIndicator();
                appendChatbotLine("Assistant", answer);
            });
        }, "rdv-ai-chat-quick").start();
    }

    private void appendChatbotLine(String author, String text) {
        if (rdvChatbotHistory == null) {
            return;
        }
        String safeAuthor = author != null ? author : "";
        String safeText = text != null ? text : "";
        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("rdv-chatbot-bubble");
        bubble.getStyleClass().add("Vous".equalsIgnoreCase(safeAuthor) ? "rdv-chatbot-bubble-user" : "rdv-chatbot-bubble-assistant");
        Label header = new Label(safeAuthor + " :");
        header.getStyleClass().add("rdv-chatbot-bubble-author");
        Label content = new Label(safeText);
        content.setWrapText(true);
        content.setMaxWidth(Double.MAX_VALUE);
        content.getStyleClass().add("rdv-chatbot-bubble-message");
        bubble.getChildren().addAll(header, content);
        bubble.setOpacity(0);
        bubble.setTranslateY(8);
        rdvChatbotHistory.getChildren().add(bubble);
        playChatBubbleEnterAnimation(bubble);
        if (rdvChatbotHistoryScroll != null) {
            Platform.runLater(() -> rdvChatbotHistoryScroll.setVvalue(1.0));
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
        Label content = new Label("en train d'écrire...");
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
        String ai = aiService.chatRdv(userText);
        if (ai != null && !ai.isBlank() && !ai.startsWith("Clé ") && !ai.startsWith("Provider IA non reconnu")) {
            return ai;
        }

        String q = userText.toLowerCase(Locale.ROOT);
        if (q.contains("quiz") && (q.contains("questionnaire") || q.contains("autisme"))) {
            return "Cliquez sur « Quiz texte » pour commencer.";
        }
        if (q.contains("quiz") && (q.contains("image") || q.contains("rorschach"))) {
            return "Cliquez sur « Quiz Rorschach » pour lancer les planches.";
        }
        if (q.contains("autisme") || q.contains("tsa")) {
            return "Je peux vous proposer un plan simple pour préparer votre rendez-vous en contexte TSA.";
        }
        if (q.contains("rdv") && (q.contains("prendre") || q.contains("reserver") || q.contains("réserver"))) {
            return "Choisissez un médecin, puis cliquez sur « Prendre RDV ».";
        }
        if (q.contains("annul") || q.contains("report")) {
            return "Pour annuler ou reporter, utilisez le lien reçu par e-mail.";
        }
        if (q.contains("connect") || q.contains("compte") || q.contains("login")) {
            return "Connectez-vous pour confirmer la réservation.";
        }
        if (q.contains("tarif") || q.contains("prix") || q.contains("coût") || q.contains("cout")) {
            return "Le tarif est affiché sur la fiche médecin.";
        }
        if (q.contains("horaire") || q.contains("heure") || q.contains("dispon")) {
            return "Les horaires apparaissent après sélection du médecin.";
        }
        if (q.contains("email") || q.contains("mail") || q.contains("sms") || q.contains("confirm")) {
            return "Après validation, vous recevez une confirmation.";
        }
        String pexels = aiService.firstPexelsImageUrl(userText);
        if (pexels != null && !pexels.isBlank()) {
            return "Je peux vous aider pour la prise de RDV. Image utile : " + pexels;
        }
        return "Je peux vous aider pour la prise de RDV et les questions fréquentes.";
    }

    private void handleQuestionnaireAnswer(String msg) {
        boolean yes = isAffirmative(msg);
        boolean no = isNegative(msg);
        if (!yes && !no) {
            appendChatbotLine("Assistant", "Répondez simplement par « oui » ou « non ».");
            return;
        }
        if (yes) {
            chatQuizScore++;
        }
        questionnaireResponses.add("Q" + (chatQuizIndex + 1) + ": " + (yes ? "oui" : "non"));
        chatQuizIndex++;
        if (chatQuizIndex >= AUTISM_QUIZ_QUESTIONS.length) {
            int total = AUTISM_QUIZ_QUESTIONS.length;
            appendChatbotLine("Assistant", "Résultat questionnaire: " + chatQuizScore + "/" + total + " réponses « oui ».");
            appendChatbotLine("Assistant", buildAutismQuizFeedback(chatQuizScore, total));
            appendChatbotLine("Assistant", buildQuestionnaireSummary());
            appendChatbotLine("Assistant", "Ce résultat n’est pas un diagnostic. Pour une évaluation clinique, consultez un professionnel spécialisé TSA.");
            chatQuizMode = ChatQuizMode.NONE;
            chatQuizIndex = -1;
            chatQuizScore = 0;
            return;
        }
        appendChatbotLine("Assistant", AUTISM_QUIZ_QUESTIONS[chatQuizIndex]);
    }

    private void handleRorschachAnswer(String msg) {
        if (msg == null || msg.isBlank()) {
            appendChatbotLine("Assistant", "Décrivez ce que vous voyez, même en quelques mots.");
            return;
        }
        if (msg.trim().length() >= 12) {
            chatQuizScore++;
        }
        rorschachResponses.add("Planche " + (chatQuizIndex + 1) + ": " + msg.trim());
        chatQuizIndex++;
        if (chatQuizIndex >= RORSCHACH_SVG.length) {
            hideRorschachImage();
            appendChatbotLine("Assistant", "Quiz images terminé. Merci pour vos descriptions.");
            appendChatbotLine("Assistant", "Lecture pédagogique: " + chatQuizScore + "/" + RORSCHACH_SVG.length
                    + " réponses détaillées. Cela ne permet pas de diagnostiquer un TSA.");
            appendChatbotLine("Assistant", buildRorschachSummary());
            appendChatbotLine("Assistant", "Si vous voulez, je peux vous proposer des stratégies pratiques pour préparer un rendez-vous en contexte TSA.");
            chatQuizMode = ChatQuizMode.NONE;
            chatQuizIndex = -1;
            chatQuizScore = 0;
            return;
        }
        appendChatbotLine("Assistant", rorschachQuestionForIndex(chatQuizIndex));
        showRorschachPlate(chatQuizIndex);
    }

    private String rorschachQuestionForIndex(int idx) {
        int n = idx + 1;
        int total = RORSCHACH_SVG.length;
        return "Image " + n + "/" + total + " : que voyez-vous ?";
    }

    private void showRorschachPlate(int idx) {
        if (idx < 0 || idx >= RORSCHACH_SVG.length) {
            hideRorschachImage();
            return;
        }
        hideRorschachImage();
        final int plate = idx;
        new Thread(() -> {
            String prompt = "Abstract symmetrical inkblot test card, centered on white paper, black and dark gray ink, "
                    + "high contrast, no text, no watermark, psychological projective style, unique variation #" + (plate + 1);
            final MultiAiProviderService.ImageGenResult result = aiService.generateInkblotImageWithDebug(prompt, plate);
            Platform.runLater(() -> {
                if (result != null && result.dataUrl() != null && !result.dataUrl().isBlank()) {
                    Image rendered = new Image(result.dataUrl(), false);
                    String caption = "Planche " + (plate + 1) + "/" + RORSCHACH_SVG.length
                            + " — Que voyez-vous ? (" + humanProviderName(result.providerUsed()) + " • succès)";
                    appendChatbotImageBubble(rendered, caption, false, plate);
                } else {
                    // Secours visuel pour garantir l'affichage de la planche, même si l'IA distante échoue.
                    Image rendered = new Image(svgToDataUrl(RORSCHACH_SVG[plate]), false);
                    String providerHint = humanProviderName(result != null ? result.providerUsed() : "");
                    String caption = "Planche " + (plate + 1) + "/" + RORSCHACH_SVG.length
                            + " — image de secours affichée (" + providerHint + " • échec IA).";
                    if (result != null && result.error() != null && !result.error().isBlank()) {
                        System.err.println("[AutiCare] Rorschach IA indisponible: " + truncateUi(result.error(), 260));
                    }
                    appendChatbotImageBubble(rendered, caption, true, plate);
                }
            });
        }, "rdv-rorschach-image-" + idx).start();
    }

    private void appendChatbotImageBubble(Image img, String caption, boolean allowRetry, int plateIndex) {
        if (rdvChatbotHistory == null || img == null) {
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
        rdvChatbotHistory.getChildren().add(bubble);
        playChatBubbleEnterAnimation(bubble);
        if (rdvChatbotHistoryScroll != null) {
            Platform.runLater(() -> rdvChatbotHistoryScroll.setVvalue(1.0));
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
        if (s.equals("stop") || s.equals("quitter") || s.equals("libre") || s.equals("annuler")) {
            return true;
        }
        // Si message plus long qu'une réponse quiz oui/non, on bascule en mode libre.
        return !(isAffirmative(s) || isNegative(s)) && s.split("\\s+").length >= 4;
    }

    private void hideRorschachImage() {
        if (rdvChatbotImageView != null) {
            rdvChatbotImageView.setImage(null);
            rdvChatbotImageView.setVisible(false);
            rdvChatbotImageView.setManaged(false);
        }
        if (rdvChatbotImageCaption != null) {
            rdvChatbotImageCaption.setText("");
        }
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

    private String buildRorschachSummary() {
        if (rorschachResponses.isEmpty()) {
            return "Résumé: aucune description enregistrée.";
        }
        return "Résumé de vos descriptions:\n- " + String.join("\n- ", rorschachResponses);
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

        header.getChildren().addAll(av, nameCol, headerSpacer, rdvBtn);

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
                ? "Connectez-vous pour finaliser votre rendez-vous avec " + drName + "."
                : "Connectez-vous pour finaliser votre rendez-vous avec le " + drName + ".";

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
