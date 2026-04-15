package org.example.services;

import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.models.Availability;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class AppointmentService implements IService<Appointment> {

    /** Noms possibles pour la colonne date/heure du RDV (schémas Symfony, SQLite, etc.). */
    private static final String[] DATE_TIME_COLUMN_CANDIDATES = {
            "date_heure",
            "date_rdv",
            "datetime_rdv",
            "date_heure_rdv",
            "rdv_date",
            "scheduled_at",
            "start_at",
            "date_time"
    };

    private static volatile String cachedRendezVousDateColumn;
    /** Colonnes réelles de {@code rendez_vous} (minuscules), remplies avec {@link #dateTimeColumn()}. */
    private static volatile Set<String> cachedRendezVousColumnsLower;

    /** À appeler après migrations ALTER sur {@code rendez_vous} pour recharger le schéma JDBC. */
    public static void clearRendezVousSchemaCache() {
        synchronized (AppointmentService.class) {
            cachedRendezVousDateColumn = null;
            cachedRendezVousColumnsLower = null;
        }
    }

    /** Aligné sur {@code VARCHAR(255)} après migration MySQL ; évite « Data truncated for column 'motif' ». */
    private static final int MAX_MOTIF_LENGTH = 255;

    @Override
    public void add(Appointment a) throws SQLException {
        if (hasConflict(a.getMedecinId(), a.getDateHeure())) {
            throw new SQLException("Conflit: ce creneau est deja reserve.");
        }
        if (a.getDisponibiliteId() > 0 && hasConflictForDisponibiliteSlot(a.getDisponibiliteId())) {
            throw new SQLException("Conflit: ce creneau est deja reserve.");
        }
        String col = dateTimeColumn();
        boolean hasStatut = hasRdvColumn("statut");
        boolean hasNotes = hasRdvColumn("notes");
        boolean prl = hasPatientReponseLueColumn();
        boolean cNom = hasRdvColumn("nom");
        boolean cPrenom = hasRdvColumn("prenom");
        boolean mdl = hasMedecinDemandeLueColumn();
        boolean disp = hasRdvColumn("disponibilite_id");
        StringBuilder cols = new StringBuilder("medecin_id,patient_id,`").append(col).append("`,motif");
        StringBuilder qs = new StringBuilder("?,?,?,?");
        if (hasStatut) {
            cols.append(",statut");
            qs.append(",?");
        }
        if (hasNotes) {
            cols.append(",notes");
            qs.append(",?");
        }
        if (prl) {
            cols.append(",patient_reponse_lue");
            qs.append(",?");
        }
        if (cNom) {
            cols.append(",nom");
            qs.append(",?");
        }
        if (cPrenom) {
            cols.append(",prenom");
            qs.append(",?");
        }
        if (mdl) {
            cols.append(",medecin_demande_lue");
            qs.append(",?");
        }
        if (disp) {
            cols.append(",disponibilite_id");
            qs.append(",?");
        }
        String sql = "INSERT INTO rendez_vous(" + cols + ") VALUES (" + qs + ")";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, a.getMedecinId());
            ps.setInt(i++, a.getPatientId());
            ps.setTimestamp(i++, Timestamp.valueOf(a.getDateHeure()));
            ps.setString(i++, clipMotif(a.getMotif()));
            if (hasStatut) {
                ps.setString(i++, a.getStatus().name());
            }
            if (hasNotes) {
                ps.setString(i++, a.getNotes());
            }
            if (prl) {
                ps.setInt(i++, a.isPatientReponseLue() ? 1 : 0);
            }
            if (cNom) {
                ps.setString(i++, sqlNonEmpty(a.getPatientNom()));
            }
            if (cPrenom) {
                ps.setString(i++, sqlNonEmpty(a.getPatientPrenom()));
            }
            if (mdl) {
                ps.setInt(i++, a.getStatus() == AppointmentStatus.EN_ATTENTE ? 0 : 1);
            }
            if (disp) {
                if (a.getDisponibiliteId() > 0) {
                    ps.setInt(i++, a.getDisponibiliteId());
                } else {
                    ps.setNull(i++, Types.INTEGER);
                }
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Appointment a) throws SQLException {
        String col = dateTimeColumn();
        boolean hasStatut = hasRdvColumn("statut");
        boolean hasNotes = hasRdvColumn("notes");
        boolean prl = hasPatientReponseLueColumn();
        boolean cNom = hasRdvColumn("nom");
        boolean cPrenom = hasRdvColumn("prenom");
        boolean mdl = hasMedecinDemandeLueColumn();
        boolean disp = hasRdvColumn("disponibilite_id");
        StringBuilder set = new StringBuilder("UPDATE rendez_vous SET medecin_id=?,patient_id=?,`")
                .append(col)
                .append("`=?,motif=?");
        if (hasStatut) {
            set.append(",statut=?");
        }
        if (hasNotes) {
            set.append(",notes=?");
        }
        if (prl) {
            set.append(",patient_reponse_lue=?");
        }
        if (cNom) {
            set.append(",nom=?");
        }
        if (cPrenom) {
            set.append(",prenom=?");
        }
        if (mdl) {
            set.append(",medecin_demande_lue=?");
        }
        if (disp) {
            set.append(",disponibilite_id=?");
        }
        set.append(" WHERE id=?");
        String sql = set.toString();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, a.getMedecinId());
            ps.setInt(i++, a.getPatientId());
            ps.setTimestamp(i++, Timestamp.valueOf(a.getDateHeure()));
            ps.setString(i++, clipMotif(a.getMotif()));
            if (hasStatut) {
                ps.setString(i++, a.getStatus().name());
            }
            if (hasNotes) {
                ps.setString(i++, a.getNotes());
            }
            if (prl) {
                ps.setInt(i++, a.isPatientReponseLue() ? 1 : 0);
            }
            if (cNom) {
                ps.setString(i++, sqlNonEmpty(a.getPatientNom()));
            }
            if (cPrenom) {
                ps.setString(i++, sqlNonEmpty(a.getPatientPrenom()));
            }
            if (mdl) {
                ps.setInt(i++, a.isMedecinDemandeLue() ? 1 : 0);
            }
            if (disp) {
                if (a.getDisponibiliteId() > 0) {
                    ps.setInt(i++, a.getDisponibiliteId());
                } else {
                    ps.setNull(i++, Types.INTEGER);
                }
            }
            ps.setInt(i, a.getId());
            ps.executeUpdate();
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
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
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

    /** Demandes en attente de validation pour ce médecin. */
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

    public int countEnAttenteForMedecin(int medecinId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE medecin_id=? AND statut='EN_ATTENTE'";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Demandes {@code EN_ATTENTE} non encore ouvertes par le médecin (badge cloche). */
    public int countUnreadEnAttenteDemandesForMedecin(int medecinId) throws SQLException {
        if (!hasMedecinDemandeLueColumn()) {
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

    /** Marque la demande comme consultée par le médecin (reste en {@code EN_ATTENTE} jusqu’à acceptation / refus). */
    public void markMedecinDemandeLue(int appointmentId, int medecinId) throws SQLException {
        if (!hasMedecinDemandeLueColumn()) {
            return;
        }
        String sql = "UPDATE rendez_vous SET medecin_demande_lue=1 WHERE id=? AND medecin_id=? AND statut='EN_ATTENTE'";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, medecinId);
            ps.executeUpdate();
        }
    }

    /**
     * Marque toutes les demandes {@code EN_ATTENTE} du médecin comme consultées (ouverture de l’écran Notifications).
     */
    public void markAllEnAttenteDemandesLuesForMedecin(int medecinId) throws SQLException {
        if (medecinId <= 0 || !hasMedecinDemandeLueColumn()) {
            return;
        }
        String sql = "UPDATE rendez_vous SET medecin_demande_lue=1 WHERE medecin_id=? AND statut='EN_ATTENTE'";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.executeUpdate();
        }
    }

    /**
     * Accepte ou refuse une demande {@link AppointmentStatus#EN_ATTENTE}. Notifie le patient ({@code patient_reponse_lue = 0}).
     */
    public void medecinRepondreDemande(int appointmentId, int medecinId, boolean accepter) throws SQLException {
        Optional<Appointment> opt = findById(appointmentId);
        if (opt.isEmpty()) {
            throw new SQLException("Rendez-vous introuvable.");
        }
        Appointment a = opt.get();
        if (a.getMedecinId() != medecinId) {
            throw new SQLException("Ce rendez-vous ne vous est pas attribué.");
        }
        if (a.getStatus() != AppointmentStatus.EN_ATTENTE) {
            throw new SQLException("Cette demande n'est plus en attente de validation.");
        }
        if (accepter) {
            if (hasConflict(a.getMedecinId(), a.getDateHeure())) {
                throw new SQLException(
                        "Ce créneau est déjà occupé par un rendez-vous confirmé. Refusez cette demande en doublon.");
            }
            if (a.getDisponibiliteId() > 0 && hasConflictForDisponibiliteSlot(a.getDisponibiliteId())) {
                throw new SQLException(
                        "Ce créneau a déjà été confirmé pour un autre patient. Refusez cette demande en doublon.");
            }
        }
        a.setStatus(accepter ? AppointmentStatus.PLANIFIE : AppointmentStatus.ANNULE);
        if (hasPatientReponseLueColumn()) {
            a.setPatientReponseLue(false);
        }
        update(a);
    }

    public int countUnreadPatientDecisions(int patientId) throws SQLException {
        if (!hasPatientReponseLueColumn()) {
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

    public List<Appointment> findPatientUnreadDecisions(int patientId) throws SQLException {
        if (!hasPatientReponseLueColumn()) {
            return List.of();
        }
        String col = dateTimeColumn();
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT * FROM rendez_vous WHERE patient_id=? AND patient_reponse_lue=0 AND statut IN ('PLANIFIE','ANNULE') ORDER BY `" + col + "` DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Marque comme lues toutes les réponses médecin non lues du patient. */
    public int markPatientDecisionsReadForPatient(int patientId) throws SQLException {
        if (!hasPatientReponseLueColumn()) {
            return 0;
        }
        String sql = "UPDATE rendez_vous SET patient_reponse_lue=1 WHERE patient_id=? AND patient_reponse_lue=0 AND statut IN ('PLANIFIE','ANNULE')";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, patientId);
            return ps.executeUpdate();
        }
    }

    /**
     * Historique des réponses acceptation / refus pour l’affichage patient (persisté dans {@code rendez_vous}).
     */
    public List<Appointment> findPatientDecisionHistory(int patientId, int maxRows) throws SQLException {
        if (!hasRdvColumn("statut")) {
            return List.of();
        }
        String col = dateTimeColumn();
        int lim = Math.max(1, Math.min(maxRows, 100));
        String sql = "SELECT * FROM rendez_vous WHERE patient_id=? AND statut IN ('PLANIFIE','ANNULE') ORDER BY `"
                + col + "` DESC LIMIT ?";
        List<Appointment> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ps.setInt(2, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Historique des décisions médecin (acceptation / refus), lu depuis {@code rendez_vous} (persisté).
     */
    public List<Appointment> findMedecinDecisionHistory(int medecinId, int maxRows) throws SQLException {
        if (!hasRdvColumn("statut")) {
            return List.of();
        }
        String col = dateTimeColumn();
        int lim = Math.max(1, Math.min(maxRows, 100));
        String sql = "SELECT * FROM rendez_vous WHERE medecin_id=? AND statut IN ('PLANIFIE','ANNULE') ORDER BY `"
                + col + "` DESC LIMIT ?";
        List<Appointment> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.setInt(2, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Efface le champ {@code notes} pour tous les rendez-vous de ce médecin. */
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

    /**
     * Créneau public déjà <strong>confirmé</strong> (statut {@code PLANIFIE} ou {@code TERMINE}).
     * Les demandes {@code EN_ATTENTE} ne bloquent pas le créneau pour les autres patients.
     */
    public boolean hasConflictForDisponibiliteSlot(int disponibiliteId) throws SQLException {
        if (disponibiliteId <= 0) {
            return false;
        }
        dateTimeColumn();
        Set<String> cols = cachedRendezVousColumnsLower;
        if (cols == null || !cols.contains("disponibilite_id")) {
            return false;
        }
        boolean hasStatut = cols.contains("statut");
        String sql = hasStatut
                ? "SELECT COUNT(*) FROM rendez_vous WHERE disponibilite_id=? AND statut IN ('PLANIFIE','TERMINE')"
                : "SELECT COUNT(*) FROM rendez_vous WHERE disponibilite_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, disponibiliteId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    /**
     * Détache les rendez-vous du créneau supprimé (avant {@code DELETE disponibilite}).
     */
    public void clearDisponibiliteLinksToSlot(int disponibiliteId) throws SQLException {
        if (disponibiliteId <= 0) {
            return;
        }
        if (!hasRdvColumn("disponibilite_id")) {
            return;
        }
        String sql = "UPDATE rendez_vous SET disponibilite_id=NULL WHERE disponibilite_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, disponibiliteId);
            ps.executeUpdate();
        }
    }

    /**
     * Au moins un rendez-vous <strong>non annulé</strong> référence ce créneau ({@code disponibilite_id}).
     */
    public boolean disponibiliteHasBlockingRdv(int disponibiliteId) throws SQLException {
        if (disponibiliteId <= 0) {
            return false;
        }
        dateTimeColumn();
        Set<String> cols = cachedRendezVousColumnsLower;
        if (cols == null || !cols.contains("disponibilite_id")) {
            return false;
        }
        boolean hasStatut = cols.contains("statut");
        String sql = hasStatut
                ? "SELECT COUNT(*) FROM rendez_vous WHERE disponibilite_id=? AND statut<>'ANNULE'"
                : "SELECT COUNT(*) FROM rendez_vous WHERE disponibilite_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, disponibiliteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Identifiants des créneaux liés à au moins un RDV non annulé pour ce médecin (désactivation « Supprimer »).
     */
    public Set<Integer> disponibiliteIdsLinkedToNonAnnuleRdvForMedecin(int medecinId) throws SQLException {
        if (medecinId <= 0) {
            return Set.of();
        }
        dateTimeColumn();
        Set<String> cols = cachedRendezVousColumnsLower;
        if (cols == null || !cols.contains("disponibilite_id")) {
            return Set.of();
        }
        boolean hasStatut = cols.contains("statut");
        String sql = hasStatut
                ? "SELECT DISTINCT disponibilite_id FROM rendez_vous WHERE medecin_id=? AND disponibilite_id IS NOT NULL AND disponibilite_id<>0 AND statut<>'ANNULE'"
                : "SELECT DISTINCT disponibilite_id FROM rendez_vous WHERE medecin_id=? AND disponibilite_id IS NOT NULL AND disponibilite_id<>0";
        HashSet<Integer> out = new HashSet<>();
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

    /**
     * Un RDV déjà <strong>confirmé ou terminé</strong> existe à la même date/heure pour ce médecin.
     * Les demandes {@code EN_ATTENTE} ne comptent pas comme conflit (plusieurs demandes possibles jusqu’à acceptation).
     */
    public boolean hasConflict(int medecinId, java.time.LocalDateTime dateHeure) throws SQLException {
        String col = dateTimeColumn();
        boolean hasStatut = cachedRendezVousColumnsLower != null
                && cachedRendezVousColumnsLower.contains("statut");
        String sql = hasStatut
                ? "SELECT COUNT(*) FROM rendez_vous WHERE medecin_id=? AND `" + col + "`=? AND statut IN ('PLANIFIE','TERMINE')"
                : "SELECT COUNT(*) FROM rendez_vous WHERE medecin_id=? AND `" + col + "`=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, medecinId);
            ps.setTimestamp(2, Timestamp.valueOf(dateHeure));
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private List<Appointment> findByForeign(String field, int value) throws SQLException {
        String col = dateTimeColumn();
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT * FROM rendez_vous WHERE `" + field + "`=? ORDER BY `" + col + "` DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, value);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private boolean hasPatientReponseLueColumn() throws SQLException {
        dateTimeColumn();
        Set<String> cols = cachedRendezVousColumnsLower;
        return cols != null && cols.contains("patient_reponse_lue");
    }

    private boolean hasMedecinDemandeLueColumn() throws SQLException {
        dateTimeColumn();
        Set<String> cols = cachedRendezVousColumnsLower;
        return cols != null && cols.contains("medecin_demande_lue");
    }

    private boolean hasRdvColumn(String columnNameLower) throws SQLException {
        dateTimeColumn();
        Set<String> cols = cachedRendezVousColumnsLower;
        return cols != null && cols.contains(columnNameLower.toLowerCase(Locale.ROOT));
    }

    /** Évite NULL sur colonnes NOT NULL sans défaut (ex. {@code nom}). */
    private static String sqlNonEmpty(String s) {
        return s != null && !s.isBlank() ? s.trim() : "";
    }

    private static String clipMotif(String motif) {
        if (motif == null) {
            return null;
        }
        String t = motif.trim();
        if (t.length() <= MAX_MOTIF_LENGTH) {
            return t;
        }
        return t.substring(0, MAX_MOTIF_LENGTH);
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
        Set<String> cols = cachedRendezVousColumnsLower;
        if (cols != null && cols.contains("statut")) {
            String st = rs.getString("statut");
            if (st != null && !st.isBlank()) {
                try {
                    a.setStatus(AppointmentStatus.valueOf(st.trim()));
                } catch (IllegalArgumentException ex) {
                    a.setStatus(AppointmentStatus.PLANIFIE);
                }
            } else {
                a.setStatus(AppointmentStatus.PLANIFIE);
            }
        } else {
            a.setStatus(AppointmentStatus.PLANIFIE);
        }
        if (cols != null && cols.contains("notes")) {
            a.setNotes(rs.getString("notes"));
        } else {
            a.setNotes(null);
        }
        if (cols != null && cols.contains("patient_reponse_lue")) {
            a.setPatientReponseLue(rs.getInt("patient_reponse_lue") != 0);
        } else {
            a.setPatientReponseLue(true);
        }
        if (cols != null && cols.contains("nom")) {
            a.setPatientNom(rs.getString("nom"));
        }
        if (cols != null && cols.contains("prenom")) {
            a.setPatientPrenom(rs.getString("prenom"));
        }
        if (cols != null && cols.contains("medecin_demande_lue")) {
            a.setMedecinDemandeLue(rs.getInt("medecin_demande_lue") != 0);
        } else {
            a.setMedecinDemandeLue(true);
        }
        if (cols != null && cols.contains("disponibilite_id")) {
            int did = rs.getInt("disponibilite_id");
            if (!rs.wasNull()) {
                a.setDisponibiliteId(did);
            }
        }
        enrichDateHeureFromDisponibiliteIfMidnight(a);
        return a;
    }

    /**
     * Si {@code date_heure} en base est une date « seule » (minuit) mais le RDV est lié à un créneau
     * {@code disponibilite}, on reprend l’heure réelle du créneau (affiche correctement 09:00, etc.).
     */
    private static void enrichDateHeureFromDisponibiliteIfMidnight(Appointment a) {
        if (a.getDisponibiliteId() <= 0) {
            return;
        }
        if (a.getDateHeure() != null && !LocalTime.MIDNIGHT.equals(a.getDateHeure().toLocalTime())) {
            return;
        }
        try {
            Optional<Availability> av = new AvailabilityService().findById(a.getDisponibiliteId());
            if (av.isPresent() && av.get().getDebut() != null) {
                a.setDateHeure(av.get().getDebut());
            }
        } catch (SQLException ignored) {
            // conserver l’heure lue en base
        }
    }

    /**
     * Détecte le nom réel de la colonne date/heure dans {@code rendez_vous} (ex. {@code date_heure}, {@code date_rdv}).
     */
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
            String picked = null;
            int bestRank = -1;
            for (String cand : DATE_TIME_COLUMN_CANDIDATES) {
                String c = cand.toLowerCase(Locale.ROOT);
                if (!columnsLower.contains(c)) {
                    continue;
                }
                int sqlType = readRendezVousColumnSqlType(md, catalog, c);
                int rank = rankSqlTypeForAppointmentInstant(sqlType);
                if (rank > bestRank) {
                    bestRank = rank;
                    picked = c;
                }
            }
            if (picked == null) {
            throw new SQLException(
                    "Table rendez_vous : aucune colonne date/heure reconnue parmi "
                            + String.join(", ", DATE_TIME_COLUMN_CANDIDATES)
                            + ". Colonnes trouvées : " + columnsLower);
            }
            cachedRendezVousDateColumn = picked;
            return cachedRendezVousDateColumn;
        }
    }

    /** Préfère TIMESTAMP / DATETIME à une colonne SQL DATE seule (sinon lecture → 00:00). */
    private static int rankSqlTypeForAppointmentInstant(int sqlType) {
        return switch (sqlType) {
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> 30;
            case Types.DATE -> 5;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> 0;
            default -> 15;
        };
    }

    private static int readRendezVousColumnSqlType(DatabaseMetaData md, String catalog, String columnLower)
            throws SQLException {
        for (String table : new String[]{"rendez_vous", "RENDEZ_VOUS"}) {
            try (ResultSet crs = md.getColumns(catalog, null, table, "%")) {
                while (crs.next()) {
                    String name = crs.getString("COLUMN_NAME");
                    if (name != null && name.equalsIgnoreCase(columnLower)) {
                        return crs.getInt("DATA_TYPE");
                    }
                }
            }
        }
        return Types.OTHER;
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
