package org.example.services;

import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.Availability;
import org.example.utils.MyDatabase;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class AppointmentService implements IService<Appointment> {

    private static final SecureRandom GESTION_TOKEN_RANDOM = new SecureRandom();

    private static final String[] DATE_TIME_COLUMN_CANDIDATES = {
            "date_heure", "date_rdv", "datetime_rdv", "date_heure_rdv",
            "rdv_date", "scheduled_at", "start_at", "date_time"
    };

    private static volatile String cachedRendezVousDateColumn;
    private static volatile Set<String> cachedRendezVousColumnsLower;

    private static final DateTimeFormatter RDV_NOTIF_DT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    public static void clearRendezVousSchemaCache() {
        synchronized (AppointmentService.class) {
            cachedRendezVousDateColumn = null;
            cachedRendezVousColumnsLower = null;
        }
    }

    @Override
    public void add(Appointment a) throws SQLException {
        if (hasConflict(a.getMedecinId(), a.getDateHeure())) {
            throw new SQLException("Conflit: ce creneau est deja reserve.");
        }
        if (a.getDisponibiliteId() > 0 && hasConflictForDisponibiliteSlot(a.getDisponibiliteId())) {
            throw new SQLException("Conflit: ce creneau est deja reserve.");
        }
        String col = dateTimeColumn();
        boolean hasNom = hasRdvColumn("nom");
        boolean hasPrenom = hasRdvColumn("prenom");
        boolean hasDisponibiliteId = hasRdvColumn("disponibilite_id");
        boolean hasPatientReponseLue = hasRdvColumn("patient_reponse_lue");
        boolean hasMedecinDemandeLue = hasRdvColumn("medecin_demande_lue");
        boolean hasGestionToken = hasRdvColumn("gestion_token");

        StringBuilder cols = new StringBuilder("medecin_id,patient_id,`").append(col).append("`,motif,statut,notes");
        StringBuilder vals = new StringBuilder("?,?,?,?,?,?");
        if (hasNom) {
            cols.append(",nom");
            vals.append(",?");
        }
        if (hasPrenom) {
            cols.append(",prenom");
            vals.append(",?");
        }
        if (hasDisponibiliteId) {
            cols.append(",disponibilite_id");
            vals.append(",?");
        }
        if (hasPatientReponseLue) {
            cols.append(",patient_reponse_lue");
            vals.append(",?");
        }
        if (hasMedecinDemandeLue) {
            cols.append(",medecin_demande_lue");
            vals.append(",?");
        }
        if (hasGestionToken) {
            if (a.getGestionToken() == null || a.getGestionToken().isBlank()) {
                a.setGestionToken(newGestionTokenHex());
            }
            cols.append(",gestion_token");
            vals.append(",?");
        }
        String sql = "INSERT INTO rendez_vous(" + cols + ") VALUES(" + vals + ")";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, a.getMedecinId());
            ps.setInt(i++, a.getPatientId());
            ps.setTimestamp(i++, Timestamp.valueOf(a.getDateHeure()));
            ps.setString(i++, a.getMotif());
            ps.setString(i++, a.getStatus() == null ? AppointmentStatus.PLANIFIE.name() : a.getStatus().name());
            ps.setString(i++, a.getNotes());
            if (hasNom) {
                String nom = a.getPatientNom() != null ? a.getPatientNom().trim() : "";
                ps.setString(i++, nom);
            }
            if (hasPrenom) {
                String prenom = a.getPatientPrenom() != null ? a.getPatientPrenom().trim() : "";
                ps.setString(i++, prenom);
            }
            if (hasDisponibiliteId) {
                if (a.getDisponibiliteId() > 0) {
                    ps.setInt(i++, a.getDisponibiliteId());
                } else {
                    ps.setNull(i++, Types.INTEGER);
                }
            }
            if (hasPatientReponseLue) {
                ps.setInt(i++, a.isPatientReponseLue() ? 1 : 0);
            }
            if (hasMedecinDemandeLue) {
                ps.setInt(i++, a.isMedecinDemandeLue() ? 1 : 0);
            }
            if (hasGestionToken) {
                ps.setString(i++, a.getGestionToken());
            }
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    a.setId(gk.getInt(1));
                }
            }
            if (a.getId() <= 0) {
                try (Statement st = MyDatabase.getConnection().createStatement();
                     ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
                    if (rs.next()) {
                        int lid = rs.getInt(1);
                        if (lid > 0) {
                            a.setId(lid);
                        }
                    }
                }
            }
        }
    }

    private static String newGestionTokenHex() {
        byte[] buf = new byte[32];
        GESTION_TOKEN_RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    @Override
    public void update(Appointment a) throws SQLException {
        String col = dateTimeColumn();
        String sql = "UPDATE rendez_vous SET medecin_id=?,patient_id=?,`" + col + "`=?,motif=?,statut=?,notes=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, a.getMedecinId());
            ps.setInt(2, a.getPatientId());
            ps.setTimestamp(3, Timestamp.valueOf(a.getDateHeure()));
            ps.setString(4, a.getMotif());
            ps.setString(5, a.getStatus() == null ? AppointmentStatus.PLANIFIE.name() : a.getStatus().name());
            ps.setString(6, a.getNotes());
            ps.setInt(7, a.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Mise à jour légère pour l'écran médecin (statut/motif), robuste même sur des lignes héritées incomplètes.
     */
    public void updateStatusAndMotifById(int rdvId, int medecinId, AppointmentStatus status, String motif) throws SQLException {
        if (rdvId <= 0) {
            throw new SQLException("Identifiant rendez-vous invalide.");
        }
        String sql = "UPDATE rendez_vous SET medecin_id=?, motif=?, statut=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.setString(2, motif != null ? motif : "");
            ps.setString(3, status == null ? AppointmentStatus.PLANIFIE.name() : status.name());
            ps.setInt(4, rdvId);
            int n = ps.executeUpdate();
            if (n <= 0) {
                throw new SQLException("Aucune ligne modifiée (rendez-vous introuvable).");
            }
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM rendez_vous WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Appointment> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM rendez_vous WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Appointment> findAll() throws SQLException {
        String col = dateTimeColumn();
        List<Appointment> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rendez_vous ORDER BY `" + col + "` DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<Appointment> findByMedecin(int medecinId) throws SQLException {
        return findByForeign("medecin_id", medecinId);
    }

    public List<Appointment> findByPatient(int patientId) throws SQLException {
        return findByForeign("patient_id", patientId);
    }

    public List<Appointment> findEnAttenteByMedecin(int medecinId) throws SQLException {
        String col = dateTimeColumn();
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT * FROM rendez_vous WHERE medecin_id=? AND statut='EN_ATTENTE' ORDER BY `" + col + "` ASC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public int countUnreadEnAttenteDemandesForMedecin(int medecinId) throws SQLException {
        if (!hasRdvColumn("medecin_demande_lue")) {
            return countEnAttenteForMedecin(medecinId);
        }
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE medecin_id=? AND statut='EN_ATTENTE' AND medecin_demande_lue=0";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void markAllEnAttenteDemandesLuesForMedecin(int medecinId) throws SQLException {
        if (medecinId <= 0 || !hasRdvColumn("medecin_demande_lue")) {
            return;
        }
        String sql = "UPDATE rendez_vous SET medecin_demande_lue=1 WHERE medecin_id=? AND statut='EN_ATTENTE'";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.executeUpdate();
        }
    }

    public void markMedecinDemandeLue(int appointmentId, int medecinId) throws SQLException {
        if (!hasRdvColumn("medecin_demande_lue")) {
            return;
        }
        String sql = "UPDATE rendez_vous SET medecin_demande_lue=1 WHERE id=? AND medecin_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, medecinId);
            ps.executeUpdate();
        }
    }

    public void medecinRepondreDemande(int appointmentId, int medecinId, boolean accepter) throws SQLException {
        Optional<Appointment> opt = findById(appointmentId);
        if (opt.isEmpty()) {
            throw new SQLException("Rendez-vous introuvable.");
        }
        Appointment a = opt.get();
        if (a.getMedecinId() != medecinId) {
            throw new SQLException("Ce rendez-vous ne vous est pas attribue.");
        }
        if (a.getStatus() != AppointmentStatus.EN_ATTENTE) {
            throw new SQLException("Cette demande n'est plus en attente.");
        }
        if (accepter) {
            if (hasConflict(a.getMedecinId(), a.getDateHeure())) {
                throw new SQLException("Ce creneau est deja occupe.");
            }
            if (a.getDisponibiliteId() > 0 && hasConflictForDisponibiliteSlot(a.getDisponibiliteId())) {
                throw new SQLException("Ce creneau est deja occupe.");
            }
        }
        a.setStatus(accepter ? AppointmentStatus.PLANIFIE : AppointmentStatus.ANNULE);
        update(a);
        notifyPatientRdvDemandeCompleted(a, accepter);
    }

    /**
     * Après modification du statut depuis la fiche médecin (hors bouton « accepter/refuser » de la demande).
     * Couvre par ex. passage en annulé depuis un RDV planifié, ou validation depuis le dialogue d’édition.
     */
    public void notifyPatientAfterDoctorRdvEdit(Appointment before, Appointment after) {
        if (after == null || after.getPatientId() <= 0 || before == null) {
            return;
        }
        AppointmentStatus o = before.getStatus();
        AppointmentStatus n = after.getStatus();
        if (o == n) {
            return;
        }
        if (o == AppointmentStatus.EN_ATTENTE && n == AppointmentStatus.PLANIFIE) {
            notifyPatientRdvDemandeCompleted(after, true);
            return;
        }
        if (o == AppointmentStatus.EN_ATTENTE && n == AppointmentStatus.ANNULE) {
            notifyPatientRdvDemandeCompleted(after, false);
            return;
        }
        if (n == AppointmentStatus.ANNULE && o != AppointmentStatus.ANNULE && o != AppointmentStatus.EN_ATTENTE) {
            notifyPatientScheduledRdvCancelled(after);
        }
    }

    private void notifyPatientScheduledRdvCancelled(Appointment a) {
        if (a.getPatientId() <= 0) {
            return;
        }
        try {
            UserNotificationService svc = new UserNotificationService();
            String when = a.getDateHeure() != null ? a.getDateHeure().format(RDV_NOTIF_DT) : "—";
            String summary = "Votre rendez-vous du " + when + " a été annulé par le médecin.";
            svc.addNotification(a.getPatientId(), UserNotificationService.TYPE_RDV_CANCELLED, null, summary);
        } catch (SQLException ignored) {
        }
    }

    /**
     * Enregistre une ligne dans {@code notifications_user} et marque la réponse médecin comme lue côté RDV
     * pour éviter le double comptage avec le bandeau « réponses RDV ».
     */
    private void notifyPatientRdvDemandeCompleted(Appointment a, boolean accepted) {
        if (a == null || a.getPatientId() <= 0) {
            return;
        }
        try {
            UserNotificationService svc = new UserNotificationService();
            String when = a.getDateHeure() != null ? a.getDateHeure().format(RDV_NOTIF_DT) : "—";
            String summary = accepted
                    ? "Votre demande de rendez-vous du " + when + " a été acceptée."
                    : "Votre demande de rendez-vous du " + when + " a été refusée (annulée).";
            String type = accepted
                    ? UserNotificationService.TYPE_RDV_ACCEPTED
                    : UserNotificationService.TYPE_RDV_REFUSED;
            svc.addNotification(a.getPatientId(), type, null, summary);
        } catch (SQLException ignored) {
            /* Le RDV reste enregistré même si la notification échoue. */
        }
        if (accepted) {
            LocalDateTime slotEnd = null;
            try {
                if (a.getDisponibiliteId() > 0) {
                    Optional<Availability> av = new AvailabilityService().findById(a.getDisponibiliteId());
                    if (av.isPresent()) {
                        slotEnd = av.get().getFin();
                    }
                }
            } catch (SQLException ignored) {
                /* fin de créneau optionnelle pour l’e-mail */
            }
            new RdvPatientEmailService().trySendConfirmation(a, slotEnd).ifPresent(msg ->
                    System.err.println("[AutiCare] E-mail confirmation RDV : " + msg));
        }
        try {
            if (hasRdvColumn("patient_reponse_lue")) {
                markPatientDecisionRead(a.getId(), a.getPatientId());
            }
        } catch (SQLException ignored) {
        }
    }

    public int countUnreadPatientDecisions(int patientId) throws SQLException {
        if (!hasRdvColumn("patient_reponse_lue")) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE patient_id=? AND patient_reponse_lue=0 AND statut IN ('PLANIFIE','ANNULE')";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int markPatientDecisionsReadForPatient(int patientId) throws SQLException {
        if (!hasRdvColumn("patient_reponse_lue")) {
            return 0;
        }
        String sql = "UPDATE rendez_vous SET patient_reponse_lue=1 WHERE patient_id=? AND patient_reponse_lue=0 AND statut IN ('PLANIFIE','ANNULE')";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, patientId);
            return ps.executeUpdate();
        }
    }

    /** Marque une réponse médecin comme lue (menu notifications unifié). */
    public void markPatientDecisionRead(int appointmentId, int patientId) throws SQLException {
        if (appointmentId <= 0 || patientId <= 0 || !hasRdvColumn("patient_reponse_lue")) {
            return;
        }
        String sql = "UPDATE rendez_vous SET patient_reponse_lue=1 WHERE id=? AND patient_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, patientId);
            ps.executeUpdate();
        }
    }

    /** RDV avec réponse médecin non lue (patient / parent), les plus récents d’abord. */
    public List<Appointment> findUnreadPatientDecisionsOrdered(int patientId, int limit) throws SQLException {
        if (patientId <= 0 || !hasRdvColumn("patient_reponse_lue")) {
            return List.of();
        }
        String col = dateTimeColumn();
        int cap = Math.max(1, Math.min(limit, 50));
        String sql = "SELECT * FROM rendez_vous WHERE patient_id=? AND patient_reponse_lue=0 AND statut IN ('PLANIFIE','ANNULE') "
                + "ORDER BY `" + col + "` DESC LIMIT ?";
        List<Appointment> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setInt(2, cap);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public List<Appointment> findPatientDecisionHistory(int patientId, int maxRows) throws SQLException {
        String col = dateTimeColumn();
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT * FROM rendez_vous WHERE patient_id=? AND statut IN ('PLANIFIE','ANNULE') ORDER BY `" + col + "` DESC LIMIT ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setInt(2, Math.max(1, Math.min(maxRows, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public List<Appointment> findMedecinDecisionHistory(int medecinId, int maxRows) throws SQLException {
        String col = dateTimeColumn();
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT * FROM rendez_vous WHERE medecin_id=? AND statut IN ('PLANIFIE','ANNULE') ORDER BY `" + col + "` DESC LIMIT ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.setInt(2, Math.max(1, Math.min(maxRows, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public int clearAllNotesForMedecin(int medecinId) throws SQLException {
        if (!hasRdvColumn("notes")) {
            return 0;
        }
        String sql = "UPDATE rendez_vous SET notes='' WHERE medecin_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            return ps.executeUpdate();
        }
    }

    public boolean hasConflictForDisponibiliteSlot(int disponibiliteId) throws SQLException {
        if (disponibiliteId <= 0 || !hasRdvColumn("disponibilite_id")) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE disponibilite_id=? AND statut IN ('PLANIFIE','TERMINE')";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, disponibiliteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public boolean disponibiliteHasBlockingRdv(int disponibiliteId) throws SQLException {
        if (disponibiliteId <= 0 || !hasRdvColumn("disponibilite_id")) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE disponibilite_id=? AND statut IN ('PLANIFIE','TERMINE')";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, disponibiliteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public Set<Integer> disponibiliteIdsLinkedToNonAnnuleRdvForMedecin(int medecinId) throws SQLException {
        if (medecinId <= 0 || !hasRdvColumn("disponibilite_id")) {
            return Set.of();
        }
        HashSet<Integer> out = new HashSet<>();
        String sql = "SELECT DISTINCT disponibilite_id FROM rendez_vous WHERE medecin_id=? "
                + "AND disponibilite_id IS NOT NULL AND disponibilite_id<>0 "
                + "AND statut IN ('PLANIFIE','TERMINE')";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    if (!rs.wasNull() && id > 0) {
                        out.add(id);
                    }
                }
            }
        }
        return Set.copyOf(out);
    }

    public boolean hasConflict(int medecinId, java.time.LocalDateTime dateHeure) throws SQLException {
        String col = dateTimeColumn();
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE medecin_id=? AND `" + col + "`=? AND statut IN ('PLANIFIE','TERMINE')";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.setTimestamp(2, Timestamp.valueOf(dateHeure));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private int countEnAttenteForMedecin(int medecinId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE medecin_id=? AND statut='EN_ATTENTE'";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Mode test: sélectionne les RDV PLANIFIE entre +1 h et +48 h pour accélérer la validation
     * du rappel automatique sans attendre la veille exacte.
     */
    public List<Appointment> findPlanifiesForSmsReminderWindow(LocalDateTime now) throws SQLException {
        if (!hasRdvColumn("sms_rappel_24h_envoye_at")) {
            return List.of();
        }
        String col = dateTimeColumn();
        LocalDateTime winStart = now.plusHours(1);
        LocalDateTime winEnd = now.plusHours(48);
        String sql = "SELECT * FROM rendez_vous WHERE statut='PLANIFIE' AND `" + col + "` > ? AND `" + col + "` >= ? AND `"
                + col + "` <= ? AND sms_rappel_24h_envoye_at IS NULL";
        List<Appointment> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setTimestamp(2, Timestamp.valueOf(winStart));
            ps.setTimestamp(3, Timestamp.valueOf(winEnd));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Marque le rappel SMS comme envoyé (évite les doublons). */
    public void markSmsRappel24hEnvoye(int appointmentId) throws SQLException {
        if (!hasRdvColumn("sms_rappel_24h_envoye_at")) {
            return;
        }
        String sql = "UPDATE rendez_vous SET sms_rappel_24h_envoye_at=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, appointmentId);
            ps.executeUpdate();
        }
    }

    private List<Appointment> findByForeign(String field, int value) throws SQLException {
        String col = dateTimeColumn();
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT * FROM rendez_vous WHERE `" + field + "`=? ORDER BY `" + col + "` DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private Appointment map(ResultSet rs) throws SQLException {
        String col = dateTimeColumn();
        Appointment a = new Appointment();
        a.setId(rs.getInt("id"));
        a.setMedecinId(rs.getInt("medecin_id"));
        a.setPatientId(rs.getInt("patient_id"));
        Timestamp ts = rs.getTimestamp(col);
        if (ts != null) {
            a.setDateHeure(ts.toLocalDateTime());
        }
        a.setMotif(rs.getString("motif"));
        if (hasRdvColumn("statut")) {
            String st = rs.getString("statut");
            if (st != null && !st.isBlank()) {
                a.setStatus(AppointmentStatus.valueOf(st));
            } else {
                a.setStatus(AppointmentStatus.PLANIFIE);
            }
        } else {
            a.setStatus(AppointmentStatus.PLANIFIE);
        }
        if (hasRdvColumn("notes")) {
            a.setNotes(rs.getString("notes"));
        }
        if (hasRdvColumn("nom")) {
            a.setPatientNom(rs.getString("nom"));
        }
        if (hasRdvColumn("prenom")) {
            a.setPatientPrenom(rs.getString("prenom"));
        }
        if (hasRdvColumn("disponibilite_id")) {
            int did = rs.getInt("disponibilite_id");
            if (!rs.wasNull()) {
                a.setDisponibiliteId(did);
            }
        }
        if (hasRdvColumn("patient_reponse_lue")) {
            a.setPatientReponseLue(rs.getInt("patient_reponse_lue") != 0);
        }
        if (hasRdvColumn("medecin_demande_lue")) {
            a.setMedecinDemandeLue(rs.getInt("medecin_demande_lue") != 0);
        }
        if (hasRdvColumn("gestion_token")) {
            String tok = rs.getString("gestion_token");
            if (tok != null && !tok.isBlank()) {
                a.setGestionToken(tok);
            }
        }
        if (hasRdvColumn("sms_rappel_24h_envoye_at")) {
            Timestamp smsAt = rs.getTimestamp("sms_rappel_24h_envoye_at");
            if (smsAt != null) {
                a.setSmsRappel24hEnvoyeAt(smsAt.toLocalDateTime());
            }
        }
        return a;
    }

    private boolean hasRdvColumn(String columnNameLower) throws SQLException {
        dateTimeColumn();
        return cachedRendezVousColumnsLower != null && cachedRendezVousColumnsLower.contains(columnNameLower.toLowerCase(Locale.ROOT));
    }

    private static String dateTimeColumn() throws SQLException {
        if (cachedRendezVousDateColumn != null) {
            return cachedRendezVousDateColumn;
        }
        synchronized (AppointmentService.class) {
            if (cachedRendezVousDateColumn != null) {
                return cachedRendezVousDateColumn;
            }
            Connection conn = MyDatabase.getConnection();
            Set<String> columnsLower = new HashSet<>();
            DatabaseMetaData md = conn.getMetaData();
            String catalog = conn.getCatalog();
            readColumnNames(md, catalog, "rendez_vous", columnsLower);
            if (columnsLower.isEmpty() && catalog != null) {
                readColumnNames(md, null, "rendez_vous", columnsLower);
            }
            cachedRendezVousColumnsLower = Set.copyOf(columnsLower);
            for (String cand : DATE_TIME_COLUMN_CANDIDATES) {
                String lower = cand.toLowerCase(Locale.ROOT);
                if (columnsLower.contains(lower)) {
                    cachedRendezVousDateColumn = lower;
                    return cachedRendezVousDateColumn;
                }
            }
            throw new SQLException("Table rendez_vous: colonne date/heure introuvable.");
        }
    }

    private static void readColumnNames(DatabaseMetaData md, String catalog, String table, Set<String> out)
            throws SQLException {
        try (ResultSet crs = md.getColumns(catalog, null, table, "%")) {
            while (crs.next()) {
                String name = crs.getString("COLUMN_NAME");
                if (name != null) {
                    out.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
    }
}
