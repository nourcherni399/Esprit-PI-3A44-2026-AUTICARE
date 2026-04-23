package org.example.controllers;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.User;
import org.example.services.AppointmentService;
import org.example.services.UserService;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Dialogue rouge pour les réponses médecin (RDV) : non lues en tête + historique.
 */
public final class PatientRdvNotificationsDialog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private PatientRdvNotificationsDialog() {
    }

    public static void show(Window owner, int patientId, AppointmentService appointmentService, UserService userService)
            throws SQLException {
        List<Appointment> history = appointmentService.findPatientDecisionHistory(patientId, 50);
        List<Appointment> sorted = new ArrayList<>(history);
        sorted.sort(Comparator
                .comparing(Appointment::isPatientReponseLue)
                .thenComparing(Appointment::getDateHeure, Comparator.nullsLast(Comparator.reverseOrder())));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Notifications");
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.OK);
        java.net.URL css = PatientRdvNotificationsDialog.class.getResource("/styles/patient-rdv-notifications.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("patient-rdv-notif-dialog");

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("patient-rdv-notif-root");

        Label head = new Label("Réponses concernant vos rendez-vous");
        head.getStyleClass().add("patient-rdv-notif-header");
        root.getChildren().add(head);

        if (sorted.isEmpty()) {
            Label empty = new Label("Vous n’avez encore aucune réponse enregistrée pour vos demandes de rendez-vous.");
            empty.setWrapText(true);
            empty.getStyleClass().add("patient-rdv-notif-empty");
            root.getChildren().add(empty);
        } else {
            Label hint = new Label("Les nouvelles réponses sont mises en évidence. Fermez cette fenêtre pour les marquer comme lues.");
            hint.setWrapText(true);
            hint.getStyleClass().add("patient-rdv-notif-hint");
            root.getChildren().add(hint);

            VBox listBox = new VBox(10);
            for (Appointment ap : sorted) {
                listBox.getChildren().add(buildLine(ap, userService));
            }
            Label histTitle = new Label("Historique (vos réponses acceptation / refus, les plus récentes d’abord)");
            histTitle.getStyleClass().add("patient-rdv-notif-subtitle");
            root.getChildren().add(histTitle);

            ScrollPane sp = new ScrollPane(listBox);
            sp.setFitToWidth(true);
            sp.setPrefViewportHeight(280);
            sp.setMaxHeight(360);
            sp.getStyleClass().add("patient-rdv-notif-scroll");
            VBox.setVgrow(sp, javafx.scene.layout.Priority.ALWAYS);
            root.getChildren().add(sp);
        }

        pane.setContent(root);

        Node okBtn = pane.lookupButton(ButtonType.OK);
        if (okBtn instanceof javafx.scene.control.Button b) {
            b.setText("Fermer");
        }

        dialog.showAndWait();

        if (!history.isEmpty() && history.stream().anyMatch(a -> !a.isPatientReponseLue())) {
            appointmentService.markPatientDecisionsReadForPatient(patientId);
        }
    }

    private static Node buildLine(Appointment ap, UserService userService) {
        VBox line = new VBox(4);
        boolean unread = !ap.isPatientReponseLue();
        line.getStyleClass().add("patient-rdv-notif-line");
        if (unread) {
            line.getStyleClass().add("patient-rdv-notif-line-unread");
        } else {
            line.getStyleClass().add("patient-rdv-notif-line-read");
        }

        Label text = new Label(formatLine(ap, userService));
        text.setWrapText(true);
        text.getStyleClass().add("patient-rdv-notif-line-text");
        line.getChildren().add(text);

        if (unread) {
            Label badge = new Label("Nouveau");
            badge.getStyleClass().add("patient-rdv-notif-badge");
            line.getChildren().add(badge);
        }
        return line;
    }

    /** Réutilisé par le menu notifications unifié (coque + accueil). */
    public static String summaryForMenu(Appointment ap, UserService userService) {
        return formatLine(ap, userService);
    }

    private static String formatLine(Appointment ap, UserService userService) {
        String doc = resolveDoctorName(ap.getMedecinId(), userService);
        String when = ap.getDateHeure() != null ? ap.getDateHeure().format(FMT) : "—";
        if (ap.getStatus() == AppointmentStatus.PLANIFIE) {
            return "✓ Rendez-vous du " + when + " avec " + doc + " — accepté.";
        }
        if (ap.getStatus() == AppointmentStatus.ANNULE) {
            return "✗ Demande du " + when + " avec " + doc + " — refusée.";
        }
        return when + " — " + doc;
    }

    private static String resolveDoctorName(int medecinId, UserService userService) {
        try {
            Optional<User> opt = userService.findById(medecinId);
            if (opt.isPresent()) {
                User d = opt.get();
                String n = ((d.getPrenom() != null ? d.getPrenom().trim() : "") + " "
                        + (d.getNom() != null ? d.getNom().trim() : "")).trim();
                if (!n.isBlank()) {
                    return "Dr " + n;
                }
                if (d.getEmail() != null && !d.getEmail().isBlank()) {
                    return d.getEmail();
                }
            }
        } catch (SQLException ignored) {
        }
        return "votre médecin";
    }
}
