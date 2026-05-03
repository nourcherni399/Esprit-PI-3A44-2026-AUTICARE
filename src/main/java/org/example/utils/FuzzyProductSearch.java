package org.example.utils;

import org.example.models.Product;
import org.example.ui.product.ProductFormUi;

import java.util.Locale;

/**
 * Recherche tolérante (fautes de frappe) alignée sur l’idée du trait Symfony {@code FuzzyProductSearchTrait}.
 */
public final class FuzzyProductSearch {

    private FuzzyProductSearch() {
    }

    public static boolean matches(String rawTerm, Product p) {
        if (p == null) {
            return false;
        }
        String search = rawTerm == null ? "" : rawTerm.trim().toLowerCase(Locale.FRENCH);
        if (search.isEmpty()) {
            return true;
        }
        if (search.chars().allMatch(Character::isDigit) || search.contains(",") || search.contains(".")) {
            String prix = String.format(Locale.FRENCH, "%.2f", p.getPrix());
            if (prix.contains(search) || String.valueOf(p.getId()).contains(search)) {
                return true;
            }
        }

        ProductFormUi.ProductCategoryChoice cc = ProductFormUi.resolveCategoryChoice(p.getCategorie());
        String catLabel = cc.getLabel() == null ? "" : cc.getLabel().toLowerCase(Locale.FRENCH);
        String[] fields = {
            p.getNom() == null ? "" : p.getNom(),
            p.getDescription() == null ? "" : p.getDescription(),
            catLabel,
            p.getCategorie() == null ? "" : p.getCategorie()
        };
        for (String text : fields) {
            if (text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.FRENCH);
            if (lower.contains(search)) {
                return true;
            }
            for (String token : search.split("\\s+")) {
                if (token.length() < 2) {
                    continue;
                }
                if (fuzzyWordMatch(token, lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fuzzyWordMatch(String needle, String haystack) {
        if (needle.length() <= 1) {
            return haystack.contains(needle);
        }
        if (haystack.contains(needle)) {
            return true;
        }
        for (String w : haystack.split("[\\s,.;:!?]+")) {
            if (w.length() < 2) {
                continue;
            }
            if (levenshtein(needle, w) <= maxDistance(needle.length())) {
                return true;
            }
        }
        return false;
    }

    private static int maxDistance(int len) {
        if (len <= 4) {
            return 1;
        }
        if (len <= 8) {
            return 2;
        }
        return 3;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }
}
