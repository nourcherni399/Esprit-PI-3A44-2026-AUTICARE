package org.example.services;

import org.example.models.Thematique;
import org.example.utils.MyDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ThematiqueService implements IService<Thematique> {

    private static final String INSERT = """
            INSERT INTO thematiques(nom,code,description,couleur,sous_titre,image_chemin,ordre_affichage,visible_site,public_cible,niveau_difficulte)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """;

    private static final String UPDATE = """
            UPDATE thematiques SET nom=?,code=?,description=?,couleur=?,sous_titre=?,image_chemin=?,ordre_affichage=?,visible_site=?,public_cible=?,niveau_difficulte=? WHERE id=?
            """;

    @Override
    public void add(Thematique t) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(INSERT)) {
            fill(ps, t, false);
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Thematique t) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(UPDATE)) {
            fill(ps, t, true);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM thematiques WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Thematique> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("SELECT * FROM thematiques WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Thematique> findAll() throws SQLException {
        List<Thematique> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM thematiques ORDER BY ordre_affichage ASC, nom ASC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private static void fill(PreparedStatement ps, Thematique t, boolean withId) throws SQLException {
        ps.setString(1, t.getNom());
        ps.setString(2, t.getCode());
        ps.setString(3, t.getDescription());
        ps.setString(4, t.getCouleur());
        ps.setString(5, t.getSousTitre());
        ps.setString(6, t.getImageChemin());
        ps.setInt(7, t.getOrdreAffichage());
        ps.setInt(8, t.isVisibleSite() ? 1 : 0);
        ps.setString(9, t.getPublicCible());
        ps.setString(10, t.getNiveauDifficulte());
        if (withId) {
            ps.setInt(11, t.getId());
        }
    }

    private static Thematique map(ResultSet rs) throws SQLException {
        Thematique t = new Thematique();
        t.setId(rs.getInt("id"));
        t.setNom(rs.getString("nom"));
        t.setCode(rs.getString("code"));
        t.setDescription(rs.getString("description"));
        t.setCouleur(rs.getString("couleur"));
        t.setSousTitre(rs.getString("sous_titre"));
        t.setImageChemin(rs.getString("image_chemin"));
        t.setOrdreAffichage(rs.getInt("ordre_affichage"));
        t.setVisibleSite(rs.getInt("visible_site") != 0);
        t.setPublicCible(rs.getString("public_cible"));
        t.setNiveauDifficulte(rs.getString("niveau_difficulte"));
        return t;
    }

    /** Compte les événements dont {@code evenements.thematique} correspond au nom affiché (trim, insensible à la casse). */
    public int countEvenementsForNomThematique(String nom) throws SQLException {
        String sql = "SELECT COUNT(*) FROM evenements WHERE LOWER(TRIM(IFNULL(thematique,''))) = LOWER(TRIM(?))";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, nom == null ? "" : nom);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int countTotalEvenements() throws SQLException {
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM evenements")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Thématiques marquées visibles sur le site, tri ordre puis nom (page publique événements). */
    public List<Thematique> findVisibleOnSiteOrdered() throws SQLException {
        List<Thematique> list = new ArrayList<>();
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(
                "SELECT * FROM thematiques WHERE visible_site = 1 ORDER BY ordre_affichage ASC, nom ASC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Trouve une thématique par son nom affiché (comme {@code evenements.thematique}), insensible à la casse / espaces.
     */
    public Optional<Thematique> findByNomAffiche(String nom) throws SQLException {
        if (nom == null || nom.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM thematiques WHERE LOWER(TRIM(nom)) = LOWER(TRIM(?))";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, nom.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }
}
