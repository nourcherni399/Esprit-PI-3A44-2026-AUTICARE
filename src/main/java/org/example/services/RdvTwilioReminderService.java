package org.example.services;

import org.example.models.Appointment;
import org.example.models.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Envoie un SMS de rappel au patient environ 24 h avant un rendez-vous confirmé ({@code PLANIFIE}), via Twilio.
 * L’application doit rester ouverte pour que la tâche périodique puisse s’exécuter.
 */
public final class RdvTwilioReminderService {

    private static final DateTimeFormatter WHEN_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    private final AppointmentService appointmentService = new AppointmentService();
    private final UserService userService = new UserService();

    public void runOnce() {
        if (!TwilioSmsService.isConfigured()) {
            System.err.println("Rappel SMS RDV : Twilio non configuré (enabled/accountSid/authToken/fromPhone).");
            return;
        }
        final List<Appointment> batch;
        try {
            batch = appointmentService.findPlanifiesForSmsReminderWindow(LocalDateTime.now());
        } catch (SQLException e) {
            System.err.println("Rappel SMS RDV : " + e.getMessage());
            return;
        }
        if (batch.isEmpty()) {
            return;
        }
        String cc = TwilioSmsService.defaultCallingCodeDigits();
        for (Appointment appt : batch) {
            try {
                processOne(appt, cc);
            } catch (Exception e) {
                System.err.println("Rappel SMS RDV #" + appt.getId() + " : " + e.getMessage());
            }
        }
    }

    private void processOne(Appointment appt, String defaultCc) throws SQLException {
        Optional<User> patientOpt = userService.findById(appt.getPatientId());
        if (patientOpt.isEmpty()) {
            return;
        }
        User patient = patientOpt.get();
        String rawTel = patient.getTelephone();
        String to = TwilioSmsService.toE164(rawTel, defaultCc);
        if (to == null || to.length() < 8) {
            System.err.println("Rappel SMS RDV #" + appt.getId() + " : téléphone patient invalide [" + rawTel + "]");
            return;
        }
        Optional<User> medOpt = userService.findById(appt.getMedecinId());
        String medLabel = medOpt.map(this::formatMedecinShort).orElse("votre praticien");
        LocalDateTime dt = appt.getDateHeure();
        String when = dt != null ? WHEN_FR.format(dt) : "—";
        StringBuilder body = new StringBuilder();
        body.append("AutiCare | Rappel RDV\n");
        body.append("Bonjour, votre rendez-vous est prevu le ").append(when).append(".\n");
        body.append("Praticien: ").append(medLabel).append(".\n");
        body.append("Merci d'arriver 10 min en avance. Si besoin de reporter, contactez le cabinet.");
        if (body.length() > 1500) {
            body.setLength(1500);
        }
        boolean ok = TwilioSmsService.sendSms(to, body.toString());
        if (ok) {
            appointmentService.markSmsRappel24hEnvoye(appt.getId());
        } else {
            System.err.println("Rappel SMS RDV #" + appt.getId() + " : envoi Twilio échoué vers " + to);
        }
    }

    private String formatMedecinShort(User medecin) {
        String p = medecin.getPrenom() != null ? medecin.getPrenom().trim() : "";
        String n = medecin.getNom() != null ? medecin.getNom().trim() : "";
        String both = (p + " " + n).trim();
        return both.isEmpty() ? "—" : both;
    }

}
