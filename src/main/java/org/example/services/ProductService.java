package org.example.services;

import org.example.models.Product;
import org.example.utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ProductService implements IService<Product> {

    private static final String INSERT_SQL = "INSERT INTO `produit`(`nom`,`description`,`categorie`,`prix`,`disponibilite`,`image`,`sku`,"
        + "`statut_publication`,`note_moyenne`,`quantite`,`genere_par_ia`,`valide`,`user_id`,`stock_id`,`seuil_alerte`) "
        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_SQL = "UPDATE `produit` SET `nom`=?,`description`=?,`categorie`=?,`prix`=?,`disponibilite`=?,`image`=?,`sku`=?,"
        + "`statut_publication`=?,`note_moyenne`=?,`quantite`=?,`genere_par_ia`=?,`valide`=?,`user_id`=?,`stock_id`=?,`seuil_alerte`=? WHERE `id`=?";
    private static final String SELECT_JOIN_BASE =
        "SELECT p.*, s.nom AS stock_nom FROM `produit` p LEFT JOIN `stock` s ON s.id = p.stock_id";

    @Override
    public void add(Product p) throws SQLException {
        Connection conn = MyDatabase.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            reserveOneUnitFromStock(conn, resolveStockId(p));
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                fill(ps, p, false);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    public int addReturningId(Product p) throws SQLException {
        Connection conn = MyDatabase.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            reserveOneUnitFromStock(conn, resolveStockId(p));
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
                fill(ps, p, false);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        conn.commit();
                        return id;
                    }
                }
            }
            conn.rollback();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
        throw new SQLException("Insertion produit impossible.");
    }

    @Override
    public void update(Product p) throws SQLException {
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(UPDATE_SQL)) {
            fill(ps, p, true);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        Connection conn = MyDatabase.getConnection();
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            Integer stockId = findStockIdForProduct(conn, id);
            deleteFromChildTables(conn, id);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM `produit` WHERE id=?")) {
                ps.setInt(1, id);
                int deleted = ps.executeUpdate();
                if (deleted <= 0) {
                    throw new SQLException("Produit introuvable (id=" + id + ").");
                }
            }
            if (stockId != null && stockId > 0) {
                incrementStockQuantity(conn, stockId);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Supprime les lignes qui référencent {@code produit} sans {@code ON DELETE CASCADE}
     * ({@code ligne_commande}, {@code cart_item}, {@code order_item}).
     */
    private static void deleteFromChildTables(Connection conn, int produitId) throws SQLException {
        String[] sqls = {
            "DELETE FROM `ligne_commande` WHERE produit_id=?",
            "DELETE FROM `cart_item` WHERE produit_id=?",
            "DELETE FROM `order_item` WHERE produit_id=?"
        };
        for (String sql : sqls) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, produitId);
                ps.executeUpdate();
            }
        }
    }

    private static Integer findStockIdForProduct(Connection conn, int produitId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT stock_id FROM `produit` WHERE id=?")) {
            ps.setInt(1, produitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int sid = rs.getInt("stock_id");
                return rs.wasNull() ? null : sid;
            }
        }
    }

    private static void incrementStockQuantity(Connection conn, int stockId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE `stock` SET quantite = quantite + 1 WHERE id=?")) {
            ps.setInt(1, stockId);
            int changed = ps.executeUpdate();
            if (changed <= 0) {
                throw new SQLException("Stock introuvable (id=" + stockId + ") lors de la suppression du produit.");
            }
        }
    }

    @Override
    public Optional<Product> findById(int id) throws SQLException {
        String sql = SELECT_JOIN_BASE + " WHERE p.id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Product> findAll() throws SQLException {
        List<Product> list = new ArrayList<>();
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(SELECT_JOIN_BASE + " ORDER BY p.id DESC")) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /** Nombre de lignes {@code produit} dont {@code user_id} pointe vers l’utilisateur (bandeau profil admin). */
    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `produit` WHERE user_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    public List<Product> searchByCategoryAndPrice(String category, double minPrice, double maxPrice) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = SELECT_JOIN_BASE + " WHERE (?='' OR p.categorie=?) AND p.prix BETWEEN ? AND ? ORDER BY p.prix ASC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, category);
            ps.setDouble(3, minPrice);
            ps.setDouble(4, maxPrice);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<Product> search(String keyword) throws SQLException {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return findAll();
        }
        List<Product> list = new ArrayList<>();
        String sql = SELECT_JOIN_BASE
            + " WHERE p.nom LIKE ? OR p.description LIKE ? OR p.categorie LIKE ? OR s.nom LIKE ? ORDER BY p.id DESC";
        String like = "%" + q + "%";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Recherche "smart" :
     * - recherche SQL classique d'abord
     * - puis matching tolérant aux fautes (distance de Levenshtein + score sémantique local)
     * - optionnel : reformulation courte via API IA si disponible
     */
    public List<Product> searchSmart(String keyword) throws SQLException {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isBlank()) {
            return findAll();
        }

        List<Product> direct = search(q);
        if (!direct.isEmpty()) {
            return direct;
        }

        List<Product> all = findAll();
        if (all.isEmpty()) {
            return all;
        }

        String intent = inferSemanticIntent(q);
        String base = normalizeForSearch(q);
        String semantic = normalizeForSearch(intent);
        String effectiveQuery = semantic.isBlank() || semantic.equals(base) ? base : (base + " " + semantic).trim();
        List<String> queryTokens = tokenize(effectiveQuery);

        List<ScoredProduct> scored = new ArrayList<>();
        for (Product p : all) {
            double s = scoreProductAgainstQuery(p, effectiveQuery, queryTokens);
            if (s > 0.12) {
                scored.add(new ScoredProduct(p, s));
            }
        }
        scored.sort(Comparator
            .comparingDouble(ScoredProduct::score).reversed()
            .thenComparingInt(x -> x.product().getId()).reversed());

        List<Product> out = new ArrayList<>();
        int limit = Math.min(24, scored.size());
        for (int i = 0; i < limit; i++) {
            out.add(scored.get(i).product());
        }
        return out.isEmpty() ? all.stream().limit(12).toList() : out;
    }

    public List<Product> findPublishedCatalog() throws SQLException {
        List<Product> all = findAll();
        return all.stream().filter(ProductService::isVisibleOnPublicCatalog).toList();
    }

    /**
     * Catalogue visiteur : uniquement les produits publiés par un compte admin.
     */
    public List<Product> findPublishedCatalogByAdmin() throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = SELECT_JOIN_BASE
            + " LEFT JOIN `user` u ON u.id = p.user_id "
            + "WHERE p.statut_publication IN ('publie','publié','published') "
            + "AND (u.role = 'ROLE_ADMIN' OR u.role = 'ADMIN') "
            + "ORDER BY p.id DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /** IDs produits les plus vendus (ligne_commande), triés décroissant. */
    public List<Integer> findTopSellingProductIds(int limit) throws SQLException {
        int lim = Math.max(1, Math.min(50, limit));
        List<Integer> out = new ArrayList<>();
        String sql = "SELECT produit_id, SUM(quantite) AS sold "
            + "FROM `ligne_commande` GROUP BY produit_id ORDER BY sold DESC LIMIT " + lim;
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getInt("produit_id"));
            }
        }
        return out;
    }

    /** Historique des ventes agrégées par produit (table {@code ligne_commande}). */
    public Map<Integer, Integer> findSoldQuantitiesByProductId() throws SQLException {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        String sql = "SELECT produit_id, SUM(quantite) AS sold "
            + "FROM `ligne_commande` GROUP BY produit_id ORDER BY sold DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getInt("produit_id"), rs.getInt("sold"));
            }
        }
        return out;
    }

    /**
     * Produit affiché dans le catalogue public (parent, patient, invité) : statut « publié » uniquement.
     * La disponibilité ({@code disponibilite} / stock) n’empêche pas l’affichage : la carte reste visible,
     * l’ajout au panier est bloqué si plus de stock (voir panier / {@code CartService}).
     */
    public static boolean isVisibleOnPublicCatalog(Product p) {
        return p != null && p.isPublie();
    }

    /** Reconnaît les variantes courantes de statut en base (Symfony / imports). */
    public static boolean isPublicationStatusPublic(String statutPublication) {
        if (statutPublication == null || statutPublication.isBlank()) {
            return false;
        }
        String s = statutPublication.trim().toLowerCase(Locale.FRENCH);
        return "publie".equals(s) || "publié".equals(s) || "published".equals(s);
    }

    private static int resolveStockId(Product p) throws SQLException {
        if (p != null && p.getStockId() > 0) {
            return p.getStockId();
        }
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM `stock` ORDER BY id ASC LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Aucun emplacement dans la table stock. Créez un stock ou associez stock_id au produit.");
    }

    private static String statutPublicationFromProduct(Product p) {
        if (p.getStatutPublication() != null && !p.getStatutPublication().isBlank()) {
            return p.getStatutPublication().trim();
        }
        return p.isPublie() ? "publie" : "brouillon";
    }

    /**
     * Règle métier demandée: chaque création de produit consomme exactement 1 unité du stock lié.
     * Refuse l'insertion si le stock est introuvable ou déjà à 0.
     */
    private static void reserveOneUnitFromStock(Connection conn, int stockId) throws SQLException {
        try (PreparedStatement dec = conn.prepareStatement(
            "UPDATE `stock` SET quantite = quantite - 1 WHERE id=? AND quantite > 0"
        )) {
            dec.setInt(1, stockId);
            int changed = dec.executeUpdate();
            if (changed > 0) {
                return;
            }
        }
        try (PreparedStatement check = conn.prepareStatement("SELECT quantite FROM `stock` WHERE id=?")) {
            check.setInt(1, stockId);
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Stock introuvable (id=" + stockId + ").");
                }
                int q = rs.getInt("quantite");
                throw new SQLException("Stock insuffisant pour cet emplacement (disponible=" + q + ").");
            }
        }
    }

    private void fill(PreparedStatement ps, Product p, boolean withId) throws SQLException {
        int stockId = resolveStockId(p);
        ps.setString(1, p.getNom());
        ps.setString(2, p.getDescription());
        ps.setString(3, p.getCategorie());
        ps.setDouble(4, p.getPrix());
        ps.setInt(5, p.isDisponible() ? 1 : 0);
        if (p.getImagePath() == null || p.getImagePath().isBlank()) {
            ps.setNull(6, Types.VARCHAR);
        } else {
            ps.setString(6, p.getImagePath().trim());
        }
        if (p.getSku() == null || p.getSku().isBlank()) {
            ps.setNull(7, Types.VARCHAR);
        } else {
            ps.setString(7, p.getSku().trim());
        }
        ps.setString(8, statutPublicationFromProduct(p));
        if (p.getNoteMoyenne() == null) {
            ps.setDouble(9, 0);
        } else {
            ps.setDouble(9, p.getNoteMoyenne());
        }
        ps.setInt(10, p.getStock());
        ps.setInt(11, p.isGenereParIa() ? 1 : 0);
        ps.setInt(12, p.isValide() ? 1 : 0);
        if (p.getUserId() == null) {
            ps.setNull(13, Types.INTEGER);
        } else {
            ps.setInt(13, p.getUserId());
        }
        ps.setInt(14, stockId);
        if (p.getSeuilAlerte() == null) {
            ps.setNull(15, Types.INTEGER);
        } else {
            ps.setInt(15, p.getSeuilAlerte());
        }
        if (withId) {
            ps.setInt(16, p.getId());
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setNom(rs.getString("nom"));
        p.setDescription(rs.getString("description"));
        p.setPrix(rs.getDouble("prix"));
        p.setCategorie(rs.getString("categorie"));
        p.setDisponible(rs.getInt("disponibilite") != 0);
        String img = rs.getString("image");
        p.setImagePath(rs.wasNull() ? null : img);
        String sku = rs.getString("sku");
        p.setSku(rs.wasNull() ? null : sku);
        String sp = rs.getString("statut_publication");
        p.setStatutPublication(sp);
        p.setPublie(isPublicationStatusPublic(sp));
        double note = rs.getDouble("note_moyenne");
        if (!rs.wasNull()) {
            p.setNoteMoyenne(note);
        }
        p.setStock(rs.getInt("quantite"));
        p.setGenereParIa(rs.getInt("genere_par_ia") != 0);
        p.setValide(rs.getInt("valide") != 0);
        int uid = rs.getInt("user_id");
        if (rs.wasNull()) {
            p.setUserId(null);
        } else {
            p.setUserId(uid);
        }
        p.setStockId(rs.getInt("stock_id"));
        int seuil = rs.getInt("seuil_alerte");
        if (rs.wasNull()) {
            p.setSeuilAlerte(null);
        } else {
            p.setSeuilAlerte(seuil);
        }
        return p;
    }

    private String inferSemanticIntent(String query) {
        if (!GroqChatCompletionService.hasApiKeyConfigured()) {
            return query;
        }
        try {
            String line = new GroqChatCompletionService().completeWithSystem(
                List.of(new GroqChatMessage("user", query)),
                """
                    Tu reformules une requête de recherche produit en une ligne courte (3 à 8 mots).
                    Corrige les fautes de frappe.
                    Réponds seulement par la requête corrigée.
                    """,
                0.2,
                0.9,
                40
            );
            return line == null || line.isBlank() ? query : line.trim();
        } catch (Exception ignored) {
            return query;
        }
    }

    private static double scoreProductAgainstQuery(Product p, String q, List<String> qTokens) {
        String name = normalizeForSearch(p.getNom());
        String desc = normalizeForSearch(p.getDescription());
        String cat = normalizeForSearch(p.getCategorie());
        String stock = normalizeForSearch(p.getStockNom());
        String text = (name + " " + desc + " " + cat + " " + stock).trim();

        double score = 0.0;
        if (!q.isBlank() && text.contains(q)) {
            score += 1.3;
        }
        for (String t : qTokens) {
            if (t.length() < 2) {
                continue;
            }
            if (name.contains(t)) {
                score += 0.55;
            } else if (cat.contains(t)) {
                score += 0.35;
            } else if (desc.contains(t) || stock.contains(t)) {
                score += 0.2;
            }
        }

        double bestWordSim = bestWordSimilarity(qTokens, tokenize(name));
        score += bestWordSim * 0.9;
        return score;
    }

    private static double bestWordSimilarity(List<String> queryTokens, List<String> productNameTokens) {
        double best = 0.0;
        for (String q : queryTokens) {
            if (q.length() < 3) {
                continue;
            }
            for (String n : productNameTokens) {
                int max = Math.max(q.length(), n.length());
                if (max == 0) {
                    continue;
                }
                int dist = levenshtein(q, n);
                double sim = 1.0 - ((double) dist / (double) max);
                if (sim > best) {
                    best = sim;
                }
            }
        }
        return best;
    }

    private static List<String> tokenize(String text) {
        String n = normalizeForSearch(text);
        List<String> out = new ArrayList<>();
        for (String s : n.split("[^a-z0-9]+")) {
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String normalizeForSearch(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String x = text.toLowerCase(Locale.ROOT).trim();
        x = x
            .replace('é', 'e').replace('è', 'e').replace('ê', 'e').replace('ë', 'e')
            .replace('à', 'a').replace('â', 'a')
            .replace('î', 'i').replace('ï', 'i')
            .replace('ô', 'o').replace('ö', 'o')
            .replace('ù', 'u').replace('û', 'u').replace('ü', 'u')
            .replace('ç', 'c');
        return x.replaceAll("\\s+", " ").trim();
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private record ScoredProduct(Product product, double score) {
    }
}
