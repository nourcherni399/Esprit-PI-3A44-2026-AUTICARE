package org.example.services;

import org.example.models.Appointment;
import org.example.models.AppointmentStatus;
import org.example.utils.MyDatabase;

import java.sql.*;
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

    @Override
    public void add(Appointment a) throws SQLException {
        if (hasConflict(a.getMedecinId(), a.getDateHeure())) {
            throw new SQLException("Conflit: ce creneau est deja reserve.");
        }
        String col = dateTimeColumn();
        String sql = "INSERT INTO rendez_vous(medecin_id,patient_id,`" + col + "`,motif,statut,notes) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, a, false);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Appointment a) throws SQLException {
        String col = dateTimeColumn();
        String sql = "UPDATE rendez_vous SET medecin_id=?,patient_id=?,`" + col + "`=?,motif=?,statut=?,notes=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fill(ps, a, true);
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

    public boolean hasConflict(int medecinId, java.time.LocalDateTime dateHeure) throws SQLException {
        String col = dateTimeColumn();
        String sql = "SELECT COUNT(*) FROM rendez_vous WHERE medecin_id=? AND `" + col + "`=? AND statut<>'ANNULE'";
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

    private void fill(PreparedStatement ps, Appointment a, boolean withId) throws SQLException {
        ps.setInt(1, a.getMedecinId());
        ps.setInt(2, a.getPatientId());
        ps.setTimestamp(3, Timestamp.valueOf(a.getDateHeure()));
        ps.setString(4, a.getMotif());
        ps.setString(5, a.getStatus().name());
        ps.setString(6, a.getNotes());
        if (withId) {
            ps.setInt(7, a.getId());
        }
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
        a.setStatus(AppointmentStatus.valueOf(rs.getString("statut")));
        a.setNotes(rs.getString("notes"));
        return a;
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
            for (String cand : DATE_TIME_COLUMN_CANDIDATES) {
                if (columnsLower.contains(cand.toLowerCase(Locale.ROOT))) {
                    cachedRendezVousDateColumn = cand.toLowerCase(Locale.ROOT);
                    return cachedRendezVousDateColumn;
                }
            }
            throw new SQLException(
                    "Table rendez_vous : aucune colonne date/heure reconnue parmi "
                            + String.join(", ", DATE_TIME_COLUMN_CANDIDATES)
                            + ". Colonnes trouvées : " + columnsLower);
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
