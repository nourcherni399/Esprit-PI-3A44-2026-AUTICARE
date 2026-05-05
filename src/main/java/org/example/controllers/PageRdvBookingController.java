package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.models.Availability;
import org.example.services.AppointmentService;
import org.example.services.AvailabilityService;
import org.example.utils.AppState;
import org.example.utils.PublicRdvDoctorSidebarHelper;
import org.example.utils.RdvPublicBookingStepper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
/**
 * Étape « Créneau » : mise en page proche du parcours web (étapes, fiche médecin, cartes horaires).
 */
public class PageRdvBookingController implements PublicShellAware {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DAY_NAME = DateTimeFormatter.ofPattern("EEEE", FR);
    private static final DateTimeFormatter LINE_DETAIL =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", FR);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", FR);

    private PublicShellController shell;
    private Availability selectedAvailability;

    @FXML
    private HBox bookingStepperBox;
    @FXML
    private Label bookingAvatarInitials;
    @FXML
    private Label bookingSidebarName;
    @FXML
    private Label bookingSidebarBio;
    @FXML
    private Label bookingSidebarPhone;
    @FXML
    private Label bookingSidebarEmail;
    @FXML
    private FlowPane bookingSlotsFlow;
    @FXML
    private Button bookingContinuerBtn;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        buildStepper();
        loadDoctorSidebar();
        loadSlotsFromDb();
    }

    private void buildStepper() {
        RdvPublicBookingStepper.fill(bookingStepperBox, 0);
    }

    private void loadDoctorSidebar() {
        PublicRdvDoctorSidebarHelper.populate(
                AppState.getPendingPublicRdvDoctorId(),
                AppState.getPendingPublicRdvDoctorName(),
                bookingSidebarName,
                bookingAvatarInitials,
                bookingSidebarBio,
                bookingSidebarPhone,
                bookingSidebarEmail);
    }

    private void loadSlotsFromDb() {
        if (bookingSlotsFlow == null) {
            return;
        }
        bookingSlotsFlow.getChildren().clear();
        selectedAvailability = null;
        if (bookingContinuerBtn != null) {
            bookingContinuerBtn.setDisable(true);
        }
        int doctorId = AppState.getPendingPublicRdvDoctorId();
        if (doctorId <= 0) {
            Label empty = new Label("Sélectionnez un professionnel depuis la liste, puis cliquez à nouveau sur « Prendre RDV ».");
            empty.setWrapText(true);
            empty.getStyleClass().add("rdv-booking-hint");
            bookingSlotsFlow.getChildren().add(empty);
            return;
        }
        try {
            List<Availability> list = new AvailabilityService().findByDoctor(doctorId);
            AppointmentService apptSvc = new AppointmentService();
            List<Availability> valides = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (Availability a : list) {
                if (a.getDebut() == null || a.getFin() == null) {
                    continue;
                }
                // Ne pas afficher les créneaux déjà passés côté front.
                if (!a.getFin().isAfter(now)) {
                    continue;
                }
                valides.add(a);
            }
            if (valides.isEmpty()) {
                Label empty = new Label(
                        "Aucun créneau disponible pour l’instant. Le médecin peut en ajouter depuis son portail AutiCare.");
                empty.setWrapText(true);
                empty.getStyleClass().add("rdv-booking-hint");
                bookingSlotsFlow.getChildren().add(empty);
                return;
            }
            boolean anyFree = false;
            for (Availability a : valides) {
                boolean occupe = apptSvc.hasConflict(doctorId, a.getDebut())
                        || apptSvc.hasConflictForDisponibiliteSlot(a.getId());
                if (!occupe) {
                    anyFree = true;
                }
                bookingSlotsFlow.getChildren().add(buildSlotCard(a, occupe));
            }
            if (!anyFree) {
                Label hint = new Label(
                        "Tous les créneaux affichés sont déjà confirmés par le médecin (cartes en rouge). Revenez plus tard ou contactez le cabinet.");
                hint.setWrapText(true);
                hint.getStyleClass().addAll("rdv-booking-hint", "rdv-booking-hint-blocked");
                bookingSlotsFlow.getChildren().add(0, hint);
            }
            int savedId = AppState.getPendingPublicRdvAvailabilityId();
            if (savedId > 0) {
                for (Node n : bookingSlotsFlow.getChildren()) {
                    if (n instanceof VBox v
                            && v.getUserData() instanceof Availability av
                            && av.getId() == savedId
                            && !isBlockedSlotCard(v)) {
                        selectSlotCard(v, av);
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            Label err = new Label("Impossible de charger les créneaux : " + e.getMessage());
            err.setWrapText(true);
            err.getStyleClass().add("rdv-booking-hint");
            bookingSlotsFlow.getChildren().add(err);
        }
    }

    private static boolean isBlockedSlotCard(VBox card) {
        return card.getStyleClass().contains("rdv-slot-card-blocked");
    }

    private VBox buildSlotCard(Availability a, boolean blocked) {
        VBox card = new VBox(10);
        card.getStyleClass().add("rdv-slot-card");
        if (blocked) {
            card.getStyleClass().add("rdv-slot-card-blocked");
            card.setCursor(Cursor.DEFAULT);
        } else {
            card.setCursor(Cursor.HAND);
        }
        card.setMinWidth(200);
        card.setPrefWidth(220);

        String dayRaw = a.getDebut().format(DAY_NAME);
        String dayCap = dayRaw.isEmpty() ? "" : Character.toUpperCase(dayRaw.charAt(0)) + dayRaw.substring(1);
        Label badge = new Label(dayCap);
        badge.getStyleClass().add(blocked ? "rdv-slot-badge-blocked" : "rdv-slot-badge");

        String range = a.getDebut().format(HM) + " – " + a.getFin().format(HM);
        Label timeBig = new Label(range);
        timeBig.getStyleClass().add(blocked ? "rdv-slot-time-big-blocked" : "rdv-slot-time-big");

        String rawLine = a.getDebut().format(LINE_DETAIL);
        String detail = (rawLine.isEmpty()
                ? rawLine
                : Character.toUpperCase(rawLine.charAt(0)) + rawLine.substring(1)) + ", " + range;
        Label line = new Label(detail);
        line.getStyleClass().add(blocked ? "rdv-slot-detail-blocked" : "rdv-slot-detail");
        line.setWrapText(true);

        card.getChildren().addAll(badge, timeBig, line);
        if (blocked) {
            Label stamp = new Label("Indisponible");
            stamp.getStyleClass().add("rdv-slot-blocked-stamp");
            stamp.setWrapText(true);
            card.getChildren().add(stamp);
        }
        card.setUserData(a);
        if (!blocked) {
            card.setOnMouseClicked(ev -> selectSlotCard(card, a));
        }
        return card;
    }

    private void selectSlotCard(VBox card, Availability a) {
        for (Node n : bookingSlotsFlow.getChildren()) {
            if (n instanceof VBox v && !isBlockedSlotCard(v)) {
                v.getStyleClass().remove("rdv-slot-card-selected");
            }
        }
        card.getStyleClass().add("rdv-slot-card-selected");
        selectedAvailability = a;
        if (bookingContinuerBtn != null) {
            bookingContinuerBtn.setDisable(false);
        }
    }

    @FXML
    private void onContinuer() {
        if (selectedAvailability == null || shell == null) {
            return;
        }
        AppState.setPendingPublicRdvAvailabilityId(selectedAvailability.getId());
        try {
            shell.loadPage("rdv-type");
        } catch (Exception ignored) {
            // conserver la page actuelle
        }
    }

    @FXML
    private void onBackToList() {
        AppState.clearPendingPublicRdvBooking();
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv");
        } catch (Exception ignored) {
            // conserver la page actuelle
        }
    }
}
