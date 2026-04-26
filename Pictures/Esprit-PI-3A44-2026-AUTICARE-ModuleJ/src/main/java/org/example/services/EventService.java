package org.example.services;

import org.example.models.Event;
import org.example.models.EventStatus;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventService implements IService<Event> {
    private static final String INSERT_SQL = """
            INSERT INTO evenements(titre,description,date_debut,date_fin,lieu,thematique,mode_evenement,lien_google_maps,lien_zoom_visio,latitude,longitude,places_max,statut)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE evenements SET titre=?,description=?,date_debut=?,date_fin=?,lieu=?,thematique=?,mode_evenement=?,lien_google_maps=?,lien_zoom_visio=?,latitude=?,longitude=?,places_max=?,statut=? WHERE id=?
            """;

    @Override
    public void add(Event e) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(INSERT_SQL)) {
            fill(ps, e, false);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Event e) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(UPDATE_SQL)) {
            fill(ps, e, true);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM evenements WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Event> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM evenements WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Event> findAll() throws SQLException {
        List<Event> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM evenements ORDER BY date_debut DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /** Événements dont le champ {@code thematique} correspond au nom affiché (trim, insensible à la casse). */
    public List<Event> findByThematiqueNom(String nom) throws SQLException {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM evenements WHERE LOWER(TRIM(IFNULL(thematique,''))) = LOWER(TRIM(?)) ORDER BY date_debut DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, nom == null ? "" : nom);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Événements affichés sur la page publique « Événements » (invités sans compte).
     * Inclut les brouillons tant que l’admin ne dispose pas d’un flux « publier » séparé — sinon les fiches
     * créées restaient invisibles ({@code BROUILLON} par défaut côté formulaire).
     * Inclut aussi les événements terminés pour l’historique.
     */
    public List<Event> findPublishedPublic() throws SQLException {
        List<Event> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(
                "SELECT * FROM evenements WHERE statut IN ('PUBLIE', 'BROUILLON', 'TERMINE') ORDER BY date_debut ASC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private void fill(PreparedStatement ps, Event e, boolean withId) throws SQLException {
        ps.setString(1, e.getTitre());
        ps.setString(2, e.getDescription());
        ps.setTimestamp(3, Timestamp.valueOf(e.getDateDebut()));
        ps.setTimestamp(4, Timestamp.valueOf(e.getDateFin()));
        ps.setString(5, e.getLieu());
        ps.setString(6, e.getThematique());
        ps.setString(7, e.getModeEvenement());
        ps.setString(8, e.getLienGoogleMaps());
        ps.setString(9, e.getLienZoomVisio());
        if (e.getLatitude() == null) {
            ps.setNull(10, Types.DOUBLE);
        } else {
            ps.setDouble(10, e.getLatitude());
        }
        if (e.getLongitude() == null) {
            ps.setNull(11, Types.DOUBLE);
        } else {
            ps.setDouble(11, e.getLongitude());
        }
        ps.setInt(12, e.getPlacesMax());
        ps.setString(13, e.getStatut().name());
        if (withId) {
            ps.setInt(14, e.getId());
        }
    }

    private Event map(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getInt("id"));
        e.setTitre(rs.getString("titre"));
        e.setDescription(rs.getString("description"));
        e.setDateDebut(rs.getTimestamp("date_debut").toLocalDateTime());
        e.setDateFin(rs.getTimestamp("date_fin").toLocalDateTime());
        e.setLieu(rs.getString("lieu"));
        e.setThematique(rs.getString("thematique"));
        try {
            e.setModeEvenement(rs.getString("mode_evenement"));
        } catch (SQLException ignored) {
            /* colonne absente sur très anciennes bases */
        }
        try {
            e.setLienGoogleMaps(rs.getString("lien_google_maps"));
        } catch (SQLException ignored) {
            /* idem */
        }
        try {
            e.setLienZoomVisio(rs.getString("lien_zoom_visio"));
        } catch (SQLException ignored) {
            /* colonne absente */
        }
        double lat = rs.getDouble("latitude");
        if (!rs.wasNull()) {
            e.setLatitude(lat);
        }
        double lng = rs.getDouble("longitude");
        if (!rs.wasNull()) {
            e.setLongitude(lng);
        }
        e.setPlacesMax(rs.getInt("places_max"));
        e.setStatut(EventStatus.valueOf(rs.getString("statut")));
        return e;
    }
}
