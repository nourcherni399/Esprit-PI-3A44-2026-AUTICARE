package org.example.services;

import org.example.models.Role;
import org.example.models.User;
import org.example.utils.MyDatabase;
import org.example.utils.PasswordUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserService implements IService<User> {

    public record ResetPinIssue(int userId, String email, String pinCode) {}

    /** Colonnes alignées sur la table Symfony {@code user}. */
    private static final String MYSQL_USER_SELECT =
            "SELECT id, nom, prenom, email, CAST(telephone AS CHAR) AS telephone, password AS mot_de_passe_hash, "
                    + "is_active AS actif, role, specialite, nom_cabinet AS cabinet, relation_avec_patient AS relation_parent, "
                    + "date_naissance, adresse, tarif_consultation, sexe, created_at, updated_at, image, data_face_api FROM `user` WHERE ";

    @Override
    public void add(User u) throws SQLException {
        String sql = "INSERT INTO `user` (nom, prenom, email, telephone, password, is_active, created_at, updated_at, role, type, "
                + "specialite, nom_cabinet, relation_avec_patient, date_naissance, adresse, sexe, tarif_consultation, image) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fillMysqlInsert(ps, u);
            ps.executeUpdate();
        }
    }

    /**
     * Inscription avec validation email : compte créé inactif jusqu’au clic sur le lien.
     * Le jeton doit tenir dans {@code email_verification_token VARCHAR(64)} (p. ex. 32 octets en hex).
     */
    public void addWithEmailVerification(User u, String verificationToken, LocalDateTime verificationExpiresAt)
            throws SQLException {
        if (verificationToken != null && verificationToken.length() > 64) {
            throw new SQLException("Jeton de vérification trop long pour la colonne email_verification_token (max 64).");
        }
        String sql = "INSERT INTO `user` (nom, prenom, email, telephone, password, is_active, created_at, updated_at, role, type, "
                + "specialite, nom_cabinet, relation_avec_patient, date_naissance, adresse, sexe, tarif_consultation, image, "
                + "email_verification_token, email_verification_expires_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fillMysqlInsert(ps, u);
            ps.setString(19, verificationToken);
            if (verificationExpiresAt != null) {
                ps.setTimestamp(20, Timestamp.valueOf(verificationExpiresAt));
            } else {
                ps.setNull(20, Types.TIMESTAMP);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void update(User u) throws SQLException {
        String sql = "UPDATE `user` SET nom=?, prenom=?, email=?, telephone=?, password=?, is_active=?, role=?, type=?, "
                + "specialite=?, nom_cabinet=?, relation_avec_patient=?, date_naissance=?, adresse=?, sexe=?, image=?, "
                + "tarif_consultation=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            fillMysqlUpdate(ps, u);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement("DELETE FROM `user` WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<User> findById(int id) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(MYSQL_USER_SELECT + "id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, nom, prenom, email, CAST(telephone AS CHAR) AS telephone, password AS mot_de_passe_hash, "
                + "is_active AS actif, role, specialite, nom_cabinet AS cabinet, relation_avec_patient AS relation_parent, "
                + "date_naissance, adresse, tarif_consultation, sexe, created_at, updated_at, image, data_face_api FROM `user` ORDER BY created_at DESC";
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    /** Utilisateurs avec enrôlement visage ({@code data_face_api} non vide), pour la connexion Face ID. */
    public List<User> findUsersWithFaceEnrollment() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, nom, prenom, email, CAST(telephone AS CHAR) AS telephone, password AS mot_de_passe_hash, "
                + "is_active AS actif, role, specialite, nom_cabinet AS cabinet, relation_avec_patient AS relation_parent, "
                + "date_naissance, adresse, tarif_consultation, sexe, created_at, updated_at, image, data_face_api FROM `user` "
                + "WHERE data_face_api IS NOT NULL AND CHAR_LENGTH(TRIM(data_face_api)) > 0 ORDER BY created_at DESC";
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    public List<User> search(String keyword) throws SQLException {
        List<User> users = new ArrayList<>();
        String pattern = "%" + keyword + "%";
        String sql = "SELECT id, nom, prenom, email, CAST(telephone AS CHAR) AS telephone, password AS mot_de_passe_hash, "
                + "is_active AS actif, role, specialite, nom_cabinet AS cabinet, relation_avec_patient AS relation_parent, "
                + "date_naissance, adresse, tarif_consultation, sexe, created_at, updated_at, image, data_face_api FROM `user` "
                + "WHERE email LIKE ? OR nom LIKE ? OR prenom LIKE ? ORDER BY created_at DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    public List<User> findByRole(Role role) throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, nom, prenom, email, CAST(telephone AS CHAR) AS telephone, password AS mot_de_passe_hash, "
                + "is_active AS actif, role, specialite, nom_cabinet AS cabinet, relation_avec_patient AS relation_parent, "
                + "date_naissance, adresse, tarif_consultation, sexe, created_at, updated_at, image, data_face_api FROM `user` WHERE role=? ORDER BY created_at DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, "ROLE_" + role.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        if (email == null) {
            return Optional.empty();
        }
        String e = email.trim();
        if (e.isEmpty()) {
            return Optional.empty();
        }
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(
                MYSQL_USER_SELECT + "LOWER(TRIM(email)) = LOWER(?)")) {
            ps.setString(1, e);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    /**
     * Connexion : email (insensible à la casse), mot de passe bcrypt Symfony ou SHA-256 local, compte actif.
     */
    public Optional<User> authenticate(String email, String password) throws SQLException {
        Optional<User> u = findByEmail(email);
        if (u.isEmpty()) {
            return Optional.empty();
        }
        User user = u.get();
        if (!user.isActif()) {
            return Optional.empty();
        }
        if (!PasswordUtil.matches(password, user.getMotDePasseHash())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * Genere et persiste un PIN (15 min) pour un email donne.
     *
     * <p>Retourne vide si l'email n'existe pas, pour permettre une reponse UI non revelatrice.</p>
     */
    public Optional<ResetPinIssue> createAndStoreResetPinForEmail(String email) throws SQLException {
        Optional<User> u = findByEmail(email);
        if (u.isEmpty()) {
            return Optional.empty();
        }
        User user = u.get();
        String pin = String.format("%06d", (int) (Math.random() * 1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plus(15, ChronoUnit.MINUTES);

        String sql = "UPDATE `user` SET reset_pin=?, reset_pin_expires_at=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, pin);
            ps.setTimestamp(2, Timestamp.valueOf(expiresAt));
            ps.setInt(3, user.getId());
            ps.executeUpdate();
        }
        return Optional.of(new ResetPinIssue(user.getId(), user.getEmail(), pin));
    }

    /**
     * Verifie le PIN stocke en base et sa date d'expiration.
     *
     * @return id utilisateur si valide, sinon vide
     */
    public Optional<Integer> verifyResetPin(String email, String pin) throws SQLException {
        if (email == null || email.isBlank() || pin == null || pin.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, reset_pin, reset_pin_expires_at FROM `user` WHERE LOWER(TRIM(email)) = LOWER(?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, email.trim());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }
            String dbPin = rs.getString("reset_pin");
            Timestamp exp = rs.getTimestamp("reset_pin_expires_at");
            if (dbPin == null || exp == null) {
                return Optional.empty();
            }
            if (!dbPin.equals(pin.trim())) {
                return Optional.empty();
            }
            if (exp.toInstant().isBefore(java.time.Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(rs.getInt("id"));
        }
    }

    /**
     * Met a jour le mot de passe (bcrypt) et efface reset_pin/reset_pin_expires_at.
     */
    public void updatePasswordAfterReset(int userId, String rawPassword) throws SQLException {
        String hash = PasswordUtil.hashBcrypt(rawPassword);
        String sql = "UPDATE `user` SET password=?, reset_pin=NULL, reset_pin_expires_at=NULL, updated_at=? WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    /** Met à jour le template visage (JSON renvoyé par le service Face ID). */
    public void updateDataFaceApi(int userId, String templateJson) throws SQLException {
        if (templateJson == null || templateJson.isBlank()) {
            return;
        }
        String sql = "UPDATE `user` SET data_face_api = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, templateJson);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Active le compte via le jeton envoyé par email (lien {@code ?token=...}).
     *
     * @return {@code true} si exactement une ligne a été mise à jour (jeton valide et non expiré).
     */
    public boolean activateByEmailVerificationToken(String rawToken) throws SQLException {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String token = rawToken.trim();
        /* Colonne MySQL typique : VARCHAR(64) — accepter au moins les jetons hex 32 octets. */
        if (token.length() > 128) {
            return false;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String sql = "UPDATE `user` SET is_active = 1, "
                + "email_verification_token = NULL, email_verification_expires_at = NULL, "
                + "email_verified_at = NOW(), updated_at = ? "
                + "WHERE email_verification_token = ? "
                + "AND (email_verification_expires_at IS NULL OR email_verification_expires_at > ?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, now);
            ps.setString(2, token);
            ps.setTimestamp(3, now);
            return ps.executeUpdate() == 1;
        } catch (SQLException first) {
            // Bases sans email_verified_at ou colonnes partielles : activation minimale.
            String fallback = "UPDATE `user` SET is_active = 1, "
                    + "email_verification_token = NULL, email_verification_expires_at = NULL, "
                    + "updated_at = ? WHERE email_verification_token = ? "
                    + "AND (email_verification_expires_at IS NULL OR email_verification_expires_at > ?)";
            try (PreparedStatement ps2 = MyDatabase.getConnection().prepareStatement(fallback)) {
                ps2.setTimestamp(1, now);
                ps2.setString(2, token);
                ps2.setTimestamp(3, now);
                return ps2.executeUpdate() == 1;
            }
        }
    }

    private void fillMysqlInsert(PreparedStatement ps, User u) throws SQLException {
        String tel = u.getTelephone();
        int telInt = 0;
        if (tel != null && !tel.isBlank()) {
            try {
                telInt = Integer.parseInt(tel.replaceAll("\\D", ""));
            } catch (NumberFormatException ignored) {
                telInt = 0;
            }
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ps.setString(1, u.getNom());
        ps.setString(2, u.getPrenom());
        ps.setString(3, u.getEmail());
        ps.setInt(4, telInt);
        ps.setString(5, u.getMotDePasseHash());
        ps.setBoolean(6, u.isActif());
        ps.setTimestamp(7, now);
        ps.setTimestamp(8, now);
        ps.setString(9, "ROLE_" + u.getRole().name());
        ps.setString(10, roleToType(u.getRole()));
        ps.setString(11, u.getSpecialite());
        ps.setString(12, u.getCabinet());
        ps.setString(13, u.getRelationParent());
        if (u.getDateNaissance() != null) {
            ps.setDate(14, Date.valueOf(u.getDateNaissance()));
        } else {
            ps.setDate(14, null);
        }
        ps.setString(15, u.getAdresse());
        ps.setString(16, u.getSexe());
        ps.setString(17, u.getTarifConsultation());
        ps.setString(18, u.getImage());
    }

    private void fillMysqlUpdate(PreparedStatement ps, User u) throws SQLException {
        String tel = u.getTelephone();
        int telInt = 0;
        if (tel != null && !tel.isBlank()) {
            try {
                telInt = Integer.parseInt(tel.replaceAll("\\D", ""));
            } catch (NumberFormatException ignored) {
                telInt = 0;
            }
        }
        ps.setString(1, u.getNom());
        ps.setString(2, u.getPrenom());
        ps.setString(3, u.getEmail());
        ps.setInt(4, telInt);
        ps.setString(5, u.getMotDePasseHash());
        ps.setBoolean(6, u.isActif());
        ps.setString(7, "ROLE_" + u.getRole().name());
        ps.setString(8, roleToType(u.getRole()));
        ps.setString(9, u.getSpecialite());
        ps.setString(10, u.getCabinet());
        ps.setString(11, u.getRelationParent());
        if (u.getDateNaissance() != null) {
            ps.setDate(12, Date.valueOf(u.getDateNaissance()));
        } else {
            ps.setDate(12, null);
        }
        ps.setString(13, u.getAdresse());
        ps.setString(14, u.getSexe());
        ps.setString(15, u.getImage());
        ps.setString(16, u.getTarifConsultation());
        ps.setTimestamp(17, new Timestamp(System.currentTimeMillis()));
        ps.setInt(18, u.getId());
    }

    private static String roleToType(Role r) {
        return switch (r) {
            case ADMIN -> "admin";
            case MEDECIN -> "medcin";
            case PARENT -> "parent";
            case PATIENT -> "patient";
            case USER -> "user";
        };
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setNom(rs.getString("nom"));
        u.setPrenom(rs.getString("prenom"));
        u.setEmail(rs.getString("email"));
        u.setTelephone(rs.getString("telephone"));
        u.setMotDePasseHash(rs.getString("mot_de_passe_hash"));
        u.setRole(fromDbRole(rs.getString("role")));
        u.setActif(rs.getBoolean("actif"));
        u.setSpecialite(rs.getString("specialite"));
        u.setCabinet(rs.getString("cabinet"));
        u.setRelationParent(rs.getString("relation_parent"));
        Date dn = rs.getDate("date_naissance");
        if (dn != null) {
            u.setDateNaissance(dn.toLocalDate());
        }
        u.setAdresse(rs.getString("adresse"));
        u.setTarifConsultation(readTarifConsultation(rs));
        u.setSexe(rs.getString("sexe"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) {
            u.setCreatedAt(created.toLocalDateTime());
        }
        if (updated != null) {
            u.setUpdatedAt(updated.toLocalDateTime());
        }
        u.setImage(rs.getString("image"));
        try {
            String dfa = rs.getString("data_face_api");
            u.setDataFaceApi(rs.wasNull() ? null : dfa);
        } catch (SQLException ignored) {
            /* colonne absente sur très anciennes bases */
        }
        return u;
    }

    /** Lecture robuste (VARCHAR ou DECIMAL selon le schéma MySQL / SQLite). */
    private static String readTarifConsultation(ResultSet rs) throws SQLException {
        String s = rs.getString("tarif_consultation");
        if (s != null && !s.trim().isEmpty()) {
            return s.trim();
        }
        BigDecimal bd = rs.getBigDecimal("tarif_consultation");
        if (bd != null) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return null;
    }

    private static Role fromDbRole(String r) {
        if (r == null || r.isBlank()) {
            return Role.USER;
        }
        String x = r.trim();
        if (x.startsWith("ROLE_")) {
            return Role.valueOf(x.substring(5));
        }
        return Role.valueOf(x);
    }
}
