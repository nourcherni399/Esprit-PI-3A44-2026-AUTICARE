package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.Availability;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.AppointmentService;
import org.example.services.AvailabilityService;
import org.example.services.RdvPatientEmailService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.utils.PublicRdvDoctorSidebarHelper;
import org.example.utils.RdvPublicBookingStepper;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Étape « Vos informations » : formulaire, récapitulatif et envoi de la demande de RDV.
 */
public class PageRdvInfoController implements PublicShellAware {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter LINE_DETAIL =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", FR);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", FR);
    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy", FR);

    private static final int MIN_NOM_PRENOM_LEN = 3;
    private static final int MIN_PHONE_DIGITS = 8;
    /** Format e-mail raisonnable (local@domaine.tld). */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    /** Lettres (avec accents) + séparateurs usuels dans les noms. */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\s'-]*$");

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

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        RdvPublicBookingStepper.fill(infoStepperBox, 2);
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
        if (infoNom != null) {
            UnaryOperator<TextFormatter.Change> lettersOnly = c -> {
                String t = c.getControlNewText();
                return t.isEmpty() || t.matches("[\\p{L}\\s'-]*") ? c : null;
            };
            infoNom.setTextFormatter(new TextFormatter<>(lettersOnly));
        }
        if (infoPrenom != null && session != null && session.getPrenom() != null) {
            infoPrenom.setText(session.getPrenom().trim());
        }
        if (infoTel != null) {
            UnaryOperator<TextFormatter.Change> digitsOnly = c -> {
                String t = c.getControlNewText();
                return t.matches("\\d*") ? c : null;
            };
            infoTel.setTextFormatter(new TextFormatter<>(digitsOnly));
            if (session != null && session.getTelephone() != null && !session.getTelephone().isBlank()) {
                infoTel.setText(session.getTelephone().replaceAll("\\D", ""));
            }
        }
        if (infoDob != null) {
            LocalDate todayDob = LocalDate.now();
            infoDob.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    setDisable(empty || date.isAfter(todayDob));
                }
            });
            Platform.runLater(() -> {
                TextField ed = infoDob.getEditor();
                if (ed == null) {
                    return;
                }
                UnaryOperator<TextFormatter.Change> dobChars = c -> {
                    String t = c.getControlNewText();
                    if (t.length() > 10) {
                        return null;
                    }
                    return t.matches("[0-9/.\\-]*") ? c : null;
                };
                ed.setTextFormatter(new TextFormatter<>(dobChars));
            });
        }
        fillRecap();
        if (recapMode != null) {
            recapMode.setText("Mode : Au cabinet");
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
            AppointmentService rdvSvc = new AppointmentService();
            if (rdvSvc.hasConflict(medId, slot.getDebut())
                    || rdvSvc.hasConflictForDisponibiliteSlot(availId)) {
                alertWarn(
                        "Créneau indisponible",
                        "Ce créneau a été confirmé entre-temps par le médecin. Revenez à l’étape précédente pour en choisir un autre.");
                return;
            }
            int patientId = resolvePatientId();
            if (patientId <= 0) {
                alertWarn(
                        "Compte requis",
                        "Vous devez être connecté avec un compte patient ou parent pour envoyer une demande de rendez-vous.");
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
            appt.setPatientNom(infoNom.getText().trim());
            appt.setPatientPrenom(infoPrenom.getText().trim());
            appt.setDateHeure(slot.getDebut());
            appt.setDisponibiliteId(availId);
            appt.setMotif(motif);
            appt.setStatus(AppointmentStatus.EN_ATTENTE);
            appt.setMedecinDemandeLue(false);
            appt.setPatientReponseLue(true);
            appt.setNotes(notes);
            rdvSvc.add(appt);
            Optional<String> erreurMail = new RdvPatientEmailService().trySendDemandeEnregistree(
                    appt, slot, infoEmail.getText().trim());
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Proposition envoyée");
            ok.setHeaderText(null);
            ok.setContentText(
                    "Votre proposition de rendez-vous a été transmise au médecin. Vous serez notifié ici (icône cloche) lorsqu’il l’aura acceptée ou refusée.");
            ok.showAndWait();
            erreurMail.ifPresent(msg -> {
                Alert w = new Alert(Alert.AlertType.WARNING);
                w.setTitle("E-mail");
                w.setHeaderText("L’e-mail récapitulatif n’a pas pu être envoyé.");
                w.setContentText(msg
                        + "\n\nVérifiez auticare.smtp.user et auticare.smtp.appPassword dans "
                        + "src/main/resources/application.properties, puis recompilez (Maven / Build) "
                        + "pour copier le fichier vers target/classes.\n"
                        + "Les valeurs de ce fichier priment sur les fichiers locaux ; "
                        + "un mot de passe d’application Google révoqué ou mal copié provoque l’erreur 535.");
                w.showAndWait();
            });
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
        String nom = infoNom.getText().trim();
        String prenom = infoPrenom.getText().trim();
        if (nom.length() < MIN_NOM_PRENOM_LEN) {
            alertWarn("Nom", "Le nom doit contenir au moins " + MIN_NOM_PRENOM_LEN + " caractères.");
            return false;
        }
        if (!NAME_PATTERN.matcher(nom).matches()) {
            alertWarn("Nom", "Le nom doit contenir uniquement des lettres.");
            return false;
        }
        if (prenom.length() < MIN_NOM_PRENOM_LEN) {
            alertWarn("Prénom", "Le prénom doit contenir au moins " + MIN_NOM_PRENOM_LEN + " caractères.");
            return false;
        }
        String telDigits = infoTel.getText() != null ? infoTel.getText().replaceAll("\\D", "") : "";
        if (telDigits.length() < MIN_PHONE_DIGITS) {
            alertWarn(
                    "Téléphone",
                    "Saisissez uniquement des chiffres, avec au moins " + MIN_PHONE_DIGITS + " chiffres pour un numéro valide.");
            return false;
        }
        String email = infoEmail.getText().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            alertWarn("E-mail", "Veuillez saisir une adresse e-mail au format valide (ex. : nom@domaine.fr).");
            return false;
        }
        if (infoDob.getValue() == null) {
            alertWarn("Date de naissance", "Veuillez indiquer la date de naissance du patient.");
            return false;
        }
        LocalDate dob = infoDob.getValue();
        LocalDate today = LocalDate.now();
        if (dob.isAfter(today)) {
            alertWarn("Date de naissance", "La date de naissance ne peut pas être postérieure à aujourd’hui.");
            return false;
        }
        if (dob.isBefore(today.minusYears(130))) {
            alertWarn("Date de naissance", "La date de naissance semble invalide.");
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

    private static int resolvePatientId() throws SQLException {
        User session = AppState.getCurrentUser();
        if (session != null && (session.getRole() == Role.PATIENT || session.getRole() == Role.PARENT)) {
            return session.getId();
        }
        return -1;
    }
}
