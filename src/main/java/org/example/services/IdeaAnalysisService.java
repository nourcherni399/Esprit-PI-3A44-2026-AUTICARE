package org.example.services;

import org.example.models.EventIdeaSuggestion;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Analyse intelligente des idees participants:
 * - pretraitement texte (stop words, synonymes, stemming)
 * - clustering par similarite Jaccard
 * - scoring pondere des dominants
 * - detection du cluster principal
 * - construction d'un prompt IA structure
 */
public class IdeaAnalysisService {

    private static final Logger LOGGER = Logger.getLogger(IdeaAnalysisService.class.getName());
    private static final Properties FILE_CONFIG = loadFileConfig();

    private static final Set<String> FR_STOP_WORDS = Set.of(
            "a", "au", "aux", "avec", "ce", "ces", "dans", "de", "des", "du", "elle", "en", "et", "eux",
            "il", "ils", "je", "la", "le", "les", "leur", "lui", "ma", "mais", "me", "meme", "mes", "moi",
            "mon", "ne", "nos", "notre", "nous", "on", "ou", "par", "pas", "pour", "qu", "que", "qui",
            "sa", "se", "ses", "son", "sur", "ta", "te", "tes", "toi", "ton", "tu", "un", "une", "vos",
            "votre", "vous", "cette", "cet", "d", "l", "y", "est", "sont", "etre", "ete", "avait", "avoir"
    );

    private static final Map<String, String> TOKEN_SYNONYMS = Map.ofEntries(
            Map.entry("ia", "intelligence_artificielle"),
            Map.entry("ai", "intelligence_artificielle"),
            Map.entry("formation", "education"),
            Map.entry("formations", "education"),
            Map.entry("apprentissage", "education"),
            Map.entry("atelier", "workshop"),
            Map.entry("ateliers", "workshop"),
            Map.entry("sensibilisation", "awareness")
    );

    private final EventIdeaSuggestionService ideaSuggestionService;
    private double similarityThreshold;
    private double occurrenceWeight;
    private double clusterWeight;
    private double recencyWeight;
    private boolean explanationMode;

    public IdeaAnalysisService(EventIdeaSuggestionService ideaSuggestionService) {
        this.ideaSuggestionService = ideaSuggestionService;
        this.similarityThreshold = cfgDouble("idea.analysis.similarityThreshold", "IDEA_ANALYSIS_SIMILARITY_THRESHOLD", 0.35);
        this.occurrenceWeight = cfgDouble("idea.analysis.weight.occurrence", "IDEA_ANALYSIS_WEIGHT_OCCURRENCE", 0.6);
        this.clusterWeight = cfgDouble("idea.analysis.weight.cluster", "IDEA_ANALYSIS_WEIGHT_CLUSTER", 0.3);
        this.recencyWeight = cfgDouble("idea.analysis.weight.recency", "IDEA_ANALYSIS_WEIGHT_RECENCY", 0.1);
        this.explanationMode = cfgBool("idea.analysis.explanationMode", "IDEA_ANALYSIS_EXPLANATION_MODE", true);
    }

    public void updateWeights(double occurrenceWeight, double clusterWeight, double recencyWeight) {
        double sum = occurrenceWeight + clusterWeight + recencyWeight;
        if (sum <= 0.0) {
            return;
        }
        this.occurrenceWeight = occurrenceWeight / sum;
        this.clusterWeight = clusterWeight / sum;
        this.recencyWeight = recencyWeight / sum;
    }

    public IdeaAnalysisResult analyzeForDay(LocalDate day) throws Exception {
        List<EventIdeaSuggestion> ideas = ideaSuggestionService.findAllForDay(day);
        Map<String, Long> byTheme = ideaSuggestionService.countByThemeForDay(day);
        Map<String, Long> byFormat = ideaSuggestionService.countByFormatForDay(day);
        Map<String, Long> byPeriod = ideaSuggestionService.countByPeriodForDay(day);

        List<ClusterResult> clusters = buildClusters(ideas);
        ScoredDominant topTheme = computeDominant(ideas, byTheme, clusters, EventIdeaSuggestion::getThemePreference, true);
        ScoredDominant topFormat = computeDominant(ideas, byFormat, clusters, EventIdeaSuggestion::getFormatPreference, false);
        ClusterResult mainCluster = resolveMainCluster(clusters, topTheme.normalizedKey());
        int totalIdeas = ideas.size();
        double confidence = computeConfidence(totalIdeas, mainCluster, topTheme.normalizedScore());

        List<String> explanations = buildExplanations(topTheme, topFormat, mainCluster, totalIdeas, clusters.size());
        for (String explanation : explanations) {
            LOGGER.info("[IdeaAnalysis] " + explanation);
        }

        return new IdeaAnalysisResult(
                day,
                ideas,
                byTheme,
                byFormat,
                byPeriod,
                clusters,
                topTheme,
                topFormat,
                mainCluster,
                confidence,
                explanations
        );
    }

    public String buildAiPrompt(IdeaAnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un assistant expert en planification d'evenements.\n");
        sb.append("Objectif: proposer le meilleur evenement possible a partir d'idees participants.\n\n");
        sb.append("STATISTIQUES GLOBALES\n");
        sb.append("- Date collecte: ").append(result.day()).append('\n');
        sb.append("- Total idees: ").append(result.ideas().size()).append('\n');
        sb.append("- Repartition themes: ").append(result.byTheme()).append('\n');
        sb.append("- Repartition formats: ").append(result.byFormat()).append('\n');
        sb.append("- Repartition periodes: ").append(result.byPeriod()).append('\n');
        sb.append("- Nombre clusters: ").append(result.clusters().size()).append('\n');
        sb.append("- Theme dominant: ").append(result.topTheme().label())
                .append(" (score=").append(String.format(Locale.ROOT, "%.3f", result.topTheme().rawScore()))
                .append(", normalise=").append(String.format(Locale.ROOT, "%.3f", result.topTheme().normalizedScore())).append(")\n");
        sb.append("- Format dominant: ").append(result.topFormat().label())
                .append(" (score=").append(String.format(Locale.ROOT, "%.3f", result.topFormat().rawScore()))
                .append(", normalise=").append(String.format(Locale.ROOT, "%.3f", result.topFormat().normalizedScore())).append(")\n");
        if (result.mainCluster() != null) {
            sb.append("- Cluster principal: taille=").append(result.mainCluster().size())
                    .append(", theme=").append(result.mainCluster().dominantThemeLabel())
                    .append(", format=").append(result.mainCluster().dominantFormatLabel())
                    .append(", score=").append(String.format(Locale.ROOT, "%.3f", result.mainCluster().score()))
                    .append('\n');
            if (!result.mainCluster().representativeDescription().isBlank()) {
                sb.append("- Exemple cluster principal: ").append(result.mainCluster().representativeDescription()).append('\n');
            }
        }
        sb.append('\n');
        if (explanationMode && !result.explanations().isEmpty()) {
            sb.append("EXPLICATIONS SYSTEME\n");
            for (String line : result.explanations()) {
                sb.append("- ").append(line).append('\n');
            }
            sb.append('\n');
        }

        sb.append("TACHE IA\n");
        sb.append("Genere une recommandation evenementielle en JSON STRICT (sans markdown, sans texte hors JSON):\n");
        sb.append("{\n");
        sb.append("  \"title\": \"...\",\n");
        sb.append("  \"description\": \"...\",\n");
        sb.append("  \"why\": \"...\",\n");
        sb.append("  \"recommendations\": [\"...\", \"...\", \"...\"],\n");
        sb.append("  \"confidence\": 0\n");
        sb.append("}\n");
        sb.append("Regles:\n");
        sb.append("- confidence doit etre un entier 0..100.\n");
        sb.append("- description concise et actionnable.\n");
        sb.append("- recommendations: 3 a 5 actions concretes.\n");
        return sb.toString();
    }

    private List<ClusterResult> buildClusters(List<EventIdeaSuggestion> ideas) {
        List<WorkingCluster> working = new ArrayList<>();
        for (EventIdeaSuggestion idea : ideas) {
            String normalizedDescription = preprocess(idea.getDescription());
            WorkingCluster best = null;
            double bestScore = 0.0;
            for (WorkingCluster cluster : working) {
                double score = jaccardTokens(normalizedDescription, cluster.centroidProcessedDescription);
                if (score > bestScore) {
                    bestScore = score;
                    best = cluster;
                }
            }
            if (best != null && bestScore >= similarityThreshold) {
                best.items.add(idea);
                if (safeLen(idea.getDescription()) > safeLen(best.representativeDescription)) {
                    best.representativeDescription = safe(idea.getDescription());
                    best.centroidProcessedDescription = normalizedDescription;
                }
            } else {
                WorkingCluster cluster = new WorkingCluster();
                cluster.items.add(idea);
                cluster.representativeDescription = safe(idea.getDescription());
                cluster.centroidProcessedDescription = normalizedDescription;
                working.add(cluster);
            }
        }

        List<ClusterResult> out = new ArrayList<>();
        int idx = 1;
        for (WorkingCluster wc : working) {
            String dominantTheme = resolveClusterDominantLabel(wc.items, EventIdeaSuggestion::getThemePreference);
            String dominantFormat = resolveClusterDominantLabel(wc.items, EventIdeaSuggestion::getFormatPreference);
            double coherence = computeClusterThemeCoherence(wc.items, dominantTheme);
            double clusterScore = wc.items.size() + coherence;
            out.add(new ClusterResult(
                    "C" + idx++,
                    List.copyOf(wc.items),
                    wc.items.size(),
                    wc.representativeDescription,
                    dominantTheme,
                    dominantFormat,
                    coherence,
                    clusterScore
            ));
        }
        return out;
    }

    private ScoredDominant computeDominant(
            List<EventIdeaSuggestion> ideas,
            Map<String, Long> countMap,
            List<ClusterResult> clusters,
            Function<EventIdeaSuggestion, String> valueExtractor,
            boolean themeAxis
    ) {
        if (countMap == null || countMap.isEmpty()) {
            return new ScoredDominant("—", "—", 0.0, 0.0, 0L, 0, 0.0);
        }

        long maxOccurrences = countMap.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        int maxClusterLinkedSize = Math.max(1, countMap.keySet().stream()
                .mapToInt(key -> linkedClusterSize(clusters, normalizeKey(key), themeAxis))
                .max().orElse(1));
        double maxRecency = Math.max(0.0001, countMap.keySet().stream()
                .mapToDouble(key -> recencyAverageForValue(ideas, valueExtractor, normalizeKey(key)))
                .max().orElse(0.0));

        List<ScoredDominant> scored = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countMap.entrySet()) {
            String label = safe(entry.getKey()).isBlank() ? "Non renseigne" : safe(entry.getKey()).trim();
            String normalized = normalizeKey(label);
            long occurrences = entry.getValue() == null ? 0L : entry.getValue();
            int linkedClusterSize = linkedClusterSize(clusters, normalized, themeAxis);
            double recency = recencyAverageForValue(ideas, valueExtractor, normalized);

            double rawScore = (occurrences * occurrenceWeight)
                    + (linkedClusterSize * clusterWeight)
                    + (recency * recencyWeight);
            double normalizedScore = (occurrencesWeightPart(occurrences, maxOccurrences) * occurrenceWeight)
                    + (clusterWeightPart(linkedClusterSize, maxClusterLinkedSize) * clusterWeight)
                    + (recencyWeightPart(recency, maxRecency) * recencyWeight);

            scored.add(new ScoredDominant(label, normalized, rawScore, normalizedScore, occurrences, linkedClusterSize, recency));
        }

        scored.sort(Comparator
                .comparingDouble(ScoredDominant::rawScore).reversed()
                .thenComparingLong(ScoredDominant::occurrences).reversed()
                .thenComparingDouble(ScoredDominant::recencyAvg).reversed()
                .thenComparing(ScoredDominant::label, String.CASE_INSENSITIVE_ORDER));
        return scored.get(0);
    }

    private ClusterResult resolveMainCluster(List<ClusterResult> clusters, String dominantThemeNormalized) {
        if (clusters == null || clusters.isEmpty()) {
            return null;
        }
        return clusters.stream()
                .max(Comparator
                        .comparingDouble((ClusterResult c) -> c.score() + themeBonus(c, dominantThemeNormalized))
                        .thenComparingInt(ClusterResult::size)
                        .thenComparing(ClusterResult::id))
                .orElse(null);
    }

    private static double themeBonus(ClusterResult cluster, String dominantThemeNormalized) {
        if (cluster == null || dominantThemeNormalized == null || dominantThemeNormalized.isBlank()) {
            return 0.0;
        }
        return normalizeKey(cluster.dominantThemeLabel()).equals(dominantThemeNormalized) ? 0.75 : 0.0;
    }

    private static double computeConfidence(int totalIdeas, ClusterResult mainCluster, double dominantScoreNormalized) {
        if (totalIdeas <= 0 || mainCluster == null) {
            return 0.0;
        }
        double clusterPart = ((double) mainCluster.size() / (double) totalIdeas) * 0.7;
        double dominantPart = clamp01(dominantScoreNormalized) * 0.3;
        return clamp01(clusterPart + dominantPart);
    }

    private List<String> buildExplanations(
            ScoredDominant topTheme,
            ScoredDominant topFormat,
            ClusterResult mainCluster,
            int totalIdeas,
            int clusterCount
    ) {
        List<String> out = new ArrayList<>();
        out.add("Theme choisi: " + topTheme.label() + " car score pondere le plus eleve (" + round3(topTheme.rawScore()) + ").");
        out.add("Format choisi: " + topFormat.label() + " car score pondere le plus eleve (" + round3(topFormat.rawScore()) + ").");
        if (mainCluster != null) {
            out.add("Cluster principal: " + mainCluster.id() + " avec " + mainCluster.size()
                    + " idees (coherence theme=" + round3(mainCluster.themeCoherence()) + ").");
        }
        out.add("Volume analyse: " + totalIdeas + " idees reparties en " + clusterCount + " clusters.");
        return out;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double occurrencesWeightPart(long value, long max) {
        return max <= 0 ? 0.0 : ((double) value / (double) max);
    }

    private static double clusterWeightPart(int value, int max) {
        return max <= 0 ? 0.0 : ((double) value / (double) max);
    }

    private static double recencyWeightPart(double value, double max) {
        return max <= 0.0 ? 0.0 : (value / max);
    }

    private int linkedClusterSize(List<ClusterResult> clusters, String normalizedValue, boolean themeAxis) {
        int total = 0;
        for (ClusterResult cluster : clusters) {
            String clusterValue = themeAxis ? cluster.dominantThemeLabel() : cluster.dominantFormatLabel();
            if (normalizeKey(clusterValue).equals(normalizedValue)) {
                total += cluster.size();
            }
        }
        return total;
    }

    private double recencyAverageForValue(
            List<EventIdeaSuggestion> ideas,
            Function<EventIdeaSuggestion, String> valueExtractor,
            String normalizedValue
    ) {
        if (ideas == null || ideas.isEmpty() || normalizedValue == null || normalizedValue.isBlank()) {
            return 0.0;
        }
        LocalDateTime now = LocalDateTime.now();
        double sum = 0.0;
        int count = 0;
        for (EventIdeaSuggestion idea : ideas) {
            if (idea == null) {
                continue;
            }
            String value = normalizeKey(valueExtractor.apply(idea));
            if (!normalizedValue.equals(value)) {
                continue;
            }
            LocalDateTime createdAt = idea.getCreatedAt();
            long days = createdAt == null ? 365 : Math.max(0, ChronoUnit.DAYS.between(createdAt, now));
            double freshness = 1.0 / (1.0 + days);
            sum += freshness;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static String resolveClusterDominantLabel(List<EventIdeaSuggestion> ideas, Function<EventIdeaSuggestion, String> extractor) {
        if (ideas == null || ideas.isEmpty()) {
            return "—";
        }
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> firstLabel = new HashMap<>();
        for (EventIdeaSuggestion idea : ideas) {
            String raw = safe(extractor.apply(idea)).isBlank() ? "Non renseigne" : safe(extractor.apply(idea)).trim();
            String normalized = normalizeKey(raw);
            counts.merge(normalized, 1, Integer::sum);
            firstLabel.putIfAbsent(normalized, raw);
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(e -> firstLabel.getOrDefault(e.getKey(), e.getKey()))
                .orElse("—");
    }

    private static double computeClusterThemeCoherence(List<EventIdeaSuggestion> ideas, String dominantThemeLabel) {
        if (ideas == null || ideas.isEmpty()) {
            return 0.0;
        }
        String dominant = normalizeKey(dominantThemeLabel);
        long same = ideas.stream()
                .filter(i -> normalizeKey(i != null ? i.getThemePreference() : "").equals(dominant))
                .count();
        return (double) same / (double) ideas.size();
    }

    private String preprocess(String text) {
        String normalized = safe(text).toLowerCase(Locale.ROOT)
                .replace("intelligence artificielle", "intelligence_artificielle")
                .replaceAll("[^\\p{L}\\p{Nd}\\s_]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            if (token.isBlank()) {
                continue;
            }
            String mapped = TOKEN_SYNONYMS.getOrDefault(token, token);
            String stem = stem(mapped);
            if (stem.isBlank() || FR_STOP_WORDS.contains(stem)) {
                continue;
            }
            tokens.add(stem);
        }
        return String.join(" ", tokens);
    }

    private static String stem(String token) {
        String t = token == null ? "" : token.trim();
        if (t.length() <= 4) {
            return t;
        }
        String[] suffixes = {"issements", "issement", "ations", "ation", "ements", "ement", "ment", "euses", "euse",
                "eaux", "eaux", "aux", "ies", "ers", "er", "ir", "re", "es", "s", "e"};
        for (String suffix : suffixes) {
            if (t.endsWith(suffix) && t.length() - suffix.length() >= 4) {
                return t.substring(0, t.length() - suffix.length());
            }
        }
        return t;
    }

    private static double jaccardTokens(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        Set<String> sa = new LinkedHashSet<>(List.of(a.split(" ")));
        Set<String> sb = new LinkedHashSet<>(List.of(b.split(" ")));
        sa.removeIf(String::isBlank);
        sb.removeIf(String::isBlank);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        return union.isEmpty() ? 0.0 : (double) inter.size() / (double) union.size();
    }

    private static String normalizeKey(String value) {
        return safe(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}\\s_]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int safeLen(String s) {
        return s == null ? 0 : s.length();
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double cfgDouble(String key, String env, double defVal) {
        String v = cfg(key, env, "");
        if (v.isBlank()) {
            return defVal;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException ex) {
            return defVal;
        }
    }

    private static boolean cfgBool(String key, String env, boolean defVal) {
        String v = cfg(key, env, "");
        if (v.isBlank()) {
            return defVal;
        }
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    private static String cfg(String key, String env, String defVal) {
        String s = System.getProperty(key);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        s = System.getenv(env);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        s = FILE_CONFIG.getProperty(key);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        return defVal;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = IdeaAnalysisService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // fallback silencieux
        }
        return p;
    }

    private static final class WorkingCluster {
        private final List<EventIdeaSuggestion> items = new ArrayList<>();
        private String representativeDescription = "";
        private String centroidProcessedDescription = "";
    }

    public record ScoredDominant(
            String label,
            String normalizedKey,
            double rawScore,
            double normalizedScore,
            long occurrences,
            int linkedClusterSize,
            double recencyAvg
    ) {
    }

    public record ClusterResult(
            String id,
            List<EventIdeaSuggestion> items,
            int size,
            String representativeDescription,
            String dominantThemeLabel,
            String dominantFormatLabel,
            double themeCoherence,
            double score
    ) {
    }

    public record IdeaAnalysisResult(
            LocalDate day,
            List<EventIdeaSuggestion> ideas,
            Map<String, Long> byTheme,
            Map<String, Long> byFormat,
            Map<String, Long> byPeriod,
            List<ClusterResult> clusters,
            ScoredDominant topTheme,
            ScoredDominant topFormat,
            ClusterResult mainCluster,
            double confidence,
            List<String> explanations
    ) {
        public int confidencePercent() {
            return (int) Math.round(Math.max(0.0, Math.min(1.0, confidence)) * 100.0);
        }

        public List<ClusterResult> clustersSortedByScoreDesc() {
            return clusters.stream()
                    .sorted(Comparator.comparingDouble(ClusterResult::score).reversed())
                    .collect(Collectors.toList());
        }
    }
}
