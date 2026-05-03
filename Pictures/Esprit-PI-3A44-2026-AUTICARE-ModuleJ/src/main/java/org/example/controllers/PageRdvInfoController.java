package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.Availability;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.AppointmentService;
import org.example.services.AvailabilityService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.PublicRdvDoctorSidebarHelper;
import org.example.utils.RdvPublicBookingStepper;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Étape « Vos informations » : formulaire, récapitulatif et envoi de la demande de RDV.
 */
public class PageRdvInfoController implements PublicShellAware {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter LINE_DETAIL =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", FR);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", FR);
    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy", FR);

    private PublicShellController shell;

    @FXML
    private HBox infoStepperBox;
    @FXML
    private Label infoAvatarInitials;
    @FXML
    private Label infoSidebarName;
    @FXML
    private Label infoSidebarBio;
    @FXML
    private Label infoSidebarPhone;
    @FXML
    private Label infoSidebarEmail;
    @FXML
    private ComboBox<String> infoVoiceLang;
    @FXML
    private TextField infoNom;
    @FXML
    private TextField infoPrenom;
    @FXML
    private TextField infoTel;
    @FXML
    private TextField infoEmail;
    @FXML
    private TextField infoAdresse;
    @FXML
    private DatePicker infoDob;
    @FXML
    private TextArea infoNotes;
    @FXML
    private Label recapCreneau;
    @FXML
    private Label recapType;
    @FXML
    private Label recapMode;
    @FXML
    private Label recapMotif;
    @FXML
    private Label recapTarif;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        RdvPublicBookingStepper.fill(infoStepperBox, 2);
        if (infoVoiceLang != null) {
            infoVoiceLang.getItems().setAll("Français", "English");
            infoVoiceLang.getSelectionModel().selectFirst();
        }
        PublicRdvDoctorSidebarHelper.populate(
                AppState.getPendingPublicRdvDoctorId(),
                AppState.getPendingPublicRdvDoctorName(),
                infoSidebarName,
                infoAvatarInitials,
                infoSidebarBio,
                infoSidebarPhone,
                infoSidebarEmail);
        User session = AppState.getCurrentUser();
        if (session != null && infoEmail != null && session.getEmail() != null && !session.getEmail().isBlank()) {
            infoEmail.setText(session.getEmail().trim());
        }
        if (infoNom != null && session != null && session.getNom() != null) {
            infoNom.setText(session.getNom().trim());
        }
        if (infoPrenom != null && session != null && session.getPrenom() != null) {
            infoPrenom.setText(session.getPrenom().trim());
        }
        if (infoTel != null && session != null && session.getTelephone() != null && !session.getTelephone().isBlank()) {
            infoTel.setText(session.getTelephone().trim());
        }
        fillRecap();
        if (recapMode != null) {
            recapMode.setText("Mode : Au cabinet");
        }
        if (recapTarif != null) {
            recapTarif.setText("Tarif : 0 DT");
        }
    }

    private void fillRecap() {
        String type = AppState.getPendingPublicRdvConsultTypeLabel();
        String motif = AppState.getPendingPublicRdvMotif();
        if (recapType != null) {
            recapType.setText("Type : " + (type.isBlank() ? "—" : type));
        }
        if (recapMotif != null) {
            recapMotif.setText("Motif : " + (motif.isBlank() ? "—" : motif));
        }
        int aid = AppState.getPendingPublicRdvAvailabilityId();
        if (recapCreneau == null || aid <= 0) {
            if (recapCreneau != null) {
                recapCreneau.setText("Créneau : —");
            }
            return;
        }
        try {
            Optional<Availability> av = new AvailabilityService().findById(aid);
            if (av.isEmpty() || av.get().getDebut() == null || av.get().getFin() == null) {
                recapCreneau.setText("Créneau : —");
                return;
            }
            Availability a = av.get();
            String raw = a.getDebut().format(LINE_DETAIL);
            String dayCap = raw.isEmpty() ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            String range = a.getDebut().format(HM) + " – " + a.getFin().format(HM);
            recapCreneau.setText("Créneau : " + dayCap + ", " + range);
        } catch (SQLException e) {
            recapCreneau.setText("Créneau : —");
        }
    }

    @FXML
    private void onVoiceAssist() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Assistant vocal");
        a.setHeaderText(null);
        a.setContentText("Fonction « Parler pour prendre RDV » : branchement à prévoir (reconnaissance vocale / API).");
        a.showAndWait();
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
        }
    }

    @FXML
    private void onRetourType() {
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv-type");
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onEnvoyerDemande() {
        if (!validateForm()) {
            return;
        }
        int availId = AppState.getPendingPublicRdvAvailabilityId();
        int medId = AppState.getPendingPublicRdvDoctorId();
        if (availId <= 0 || medId <= 0) {
            alertWarn("Parcours incomplet", "Revenez au choix du créneau et du type de consultation.");
            return;
        }
        try {
            Optional<Availability> avOpt = new AvailabilityService().findById(availId);
            if (avOpt.isEmpty() || avOpt.get().getDebut() == null) {
                alertWarn("Créneau", "Ce créneau n’est plus disponible. Veuillez en choisir un autre.");
                return;
            }
            Availability slot = avOpt.get();
            String email = infoEmail.getText().trim();
            int patientId = resolvePatientId(email);
            if (patientId <= 0) {
                alertWarn(
                        "Compte requis",
                        "Aucun compte patient ou parent n’est associé à cet e-mail, ou le compte n’a pas le bon profil.\n"
                                + "Créez un compte ou connectez-vous avec un compte patient / parent.");
                return;
            }
            String motif = AppState.getPendingPublicRdvMotif();
            if (motif.isBlank()) {
                motif = "Demande en ligne";
            }
            String notes = buildNotesPayload();
            Appointment appt = new Appointment();
            appt.setMedecinId(medId);
            appt.setPatientId(patientId);
            appt.setDateHeure(slot.getDebut());
            appt.setMotif(motif);
            appt.setStatus(AppointmentStatus.PLANIFIE);
            appt.setNotes(notes);
            new AppointmentService().add(appt);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Demande envoyée");
            ok.setHeaderText(null);
            ok.setContentText("Votre rendez-vous a été enregistré. Vous recevrez une confirmation à l’adresse indiquée.");
            ok.showAndWait();
            AppState.clearPendingPublicRdvBooking();
            if (shell != null) {
                try {
                    shell.loadPage("rdv");
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException e) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Enregistrement");
            err.setHeaderText(null);
            err.setContentText(e.getMessage());
            err.showAndWait();
        }
    }

    private String buildNotesPayload() {
        String nom = infoNom.getText().trim();
        String prenom = infoPrenom.getText().trim();
        String tel = infoTel.getText().trim();
        String email = infoEmail.getText().trim();
        String adr = infoAdresse.getText().trim();
        String dobStr = infoDob.getValue() != null ? DOB_FMT.format(infoDob.getValue()) : "";
        String extra = infoNotes.getText() != null ? infoNotes.getText().trim() : "";
        String type = AppState.getPendingPublicRdvConsultTypeLabel();
        StringBuilder sb = new StringBuilder();
        sb.append("Demandeur : ").append(prenom).append(" ").append(nom).append("\n");
        sb.append("Tél. : ").append(tel).append("\n");
        sb.append("E-mail : ").append(email).append("\n");
        sb.append("Adresse : ").append(adr).append("\n");
        sb.append("Date de naissance (patient) : ").append(dobStr).append("\n");
        if (!type.isBlank()) {
            sb.append("Type de consultation : ").append(type).append("\n");
        }
        if (!extra.isBlank()) {
            sb.append("Notes : ").append(extra);
        }
        return sb.toString();
    }

    private boolean validateForm() {
        if (trimBlank(infoNom) || trimBlank(infoPrenom) || trimBlank(infoTel)
                || trimBlank(infoEmail) || trimBlank(infoAdresse)) {
            alertWarn("Champs requis", "Veuillez remplir tous les champs obligatoires (*).");
            return false;
        }
        String email = infoEmail.getText().trim();
        if (!email.contains("@") || email.length() < 5) {
            alertWarn("E-mail", "Veuillez saisir une adresse e-mail valide.");
            return false;
        }
        if (infoDob.getValue() == null) {
            alertWarn("Date de naissance", "Veuillez indiquer la date de naissance du patient.");
            return false;
        }
        return true;
    }

    private static boolean trimBlank(TextField f) {
        return f == null || f.getText() == null || f.getText().trim().isBlank();
    }

    private static void alertWarn(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private static int resolvePatientId(String email) throws SQLException {
        User session = AppState.getCurrentUser();
        if (session != null && (session.getRole() == Role.PATIENT || session.getRole() == Role.PARENT)) {
            return session.getId();
        }
        Optional<User> byMail = new UserService().findByEmail(email);
        if (byMail.isPresent()) {
            Role r = byMail.get().getRole();
            if (r == Role.PATIENT || r == Role.PARENT) {
                return byMail.get().getId();
            }
        }
        return -1;
    }
}
