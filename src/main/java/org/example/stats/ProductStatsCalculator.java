package org.example.stats;

import org.example.models.Product;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Agrégations catalogue alignées sur {@code StatsController} / {@code getProduitStats} du projet Symfony
 * (totaux, disponibilité, répartition et prix moyens par catégorie, tops prix).
 */
public final class ProductStatsCalculator {

    private ProductStatsCalculator() {
    }

    public record CategoryCount(String categorieLabel, int nombre) {
    }

    public record CategoryPrixMoyen(String categorieLabel, double prixMoyen) {
    }

    public record ProductStatsResult(
        int totalProduits,
        int produitsDisponibles,
        int produitsIndisponibles,
        int produitsPublies,
        long quantiteTotaleCatalogue,
        double valeurCatalogueDt,
        List<CategoryCount> parCategorie,
        List<CategoryPrixMoyen> prixMoyensParCategorie,
        List<Product> topPlusChers,
        List<Product> topMoinsChers
    ) {
    }

    private static final Map<String, String> CATEGORY_LABELS = Map.ofEntries(
        Map.entry("sensoriels", "Sensoriels"),
        Map.entry("bruit_et_environnement", "Bruit et environnement"),
        Map.entry("education_apprentissage", "Education & apprentissage"),
        Map.entry("communication_langage", "Communication & langage"),
        Map.entry("jeux_therapeutiques_developpement", "Jeux thérapeutiques & développement"),
        Map.entry("bien_etre_relaxation", "Bien-être & relaxation"),
        Map.entry("vie_quotidienne", "Vie quotidienne")
    );

    /** Libellé affiché comme dans {@code App\Enum\Categorie::label()}. */
    public static String categoryLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Non défini";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return CATEGORY_LABELS.getOrDefault(key, humanizeKey(key));
    }

    private static String humanizeKey(String key) {
        return key.replace('_', ' ');
    }

    public static ProductStatsResult compute(List<Product> products) {
        List<Product> list = products == null ? List.of() : List.copyOf(products);
        int total = list.size();
        int disponibles = (int) list.stream().filter(Product::isDisponible).count();
        int indisponibles = total - disponibles;
        int publies = (int) list.stream().filter(Product::isPublie).count();
        long sumQty = list.stream().mapToLong(Product::getStock).sum();
        double valeur = list.stream().mapToDouble(p -> p.getPrix() * p.getStock()).sum();

        Map<String, List<Product>> byCat = list.stream()
            .collect(Collectors.groupingBy(p -> {
                String c = p.getCategorie();
                return c == null || c.isBlank() ? "" : c.trim().toLowerCase(Locale.ROOT);
            }, LinkedHashMap::new, Collectors.toList()));

        List<CategoryCount> parCategorie = new ArrayList<>();
        List<CategoryPrixMoyen> prixMoyens = new ArrayList<>();
        for (Map.Entry<String, List<Product>> e : byCat.entrySet()) {
            String key = e.getKey();
            List<Product> pl = e.getValue();
            String label = categoryLabel(key.isEmpty() ? null : key);
            parCategorie.add(new CategoryCount(label, pl.size()));
            double avg = pl.stream().mapToDouble(Product::getPrix).average().orElse(0);
            prixMoyens.add(new CategoryPrixMoyen(label, Math.round(avg * 100.0) / 100.0));
        }
        parCategorie.sort(Comparator.comparingInt(CategoryCount::nombre).reversed());
        prixMoyens.sort(Comparator.comparingDouble(CategoryPrixMoyen::prixMoyen).reversed());

        List<Product> topChers = list.stream()
            .sorted(Comparator.comparingDouble(Product::getPrix).reversed())
            .limit(5)
            .collect(Collectors.toList());
        List<Product> topMoins = list.stream()
            .sorted(Comparator.comparingDouble(Product::getPrix))
            .limit(5)
            .collect(Collectors.toList());

        return new ProductStatsResult(
            total,
            disponibles,
            indisponibles,
            publies,
            sumQty,
            valeur,
            parCategorie,
            prixMoyens,
            topChers,
            topMoins
        );
    }
}
