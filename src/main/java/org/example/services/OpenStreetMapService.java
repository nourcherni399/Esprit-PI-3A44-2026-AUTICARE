package org.example.services;

import org.example.utils.MapEmbedUrls;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service OpenStreetMap (adresse -> latitude/longitude) pour le formulaire événement.
 */
public class OpenStreetMapService {
    private static final Properties FILE_CONFIG = loadFileConfig();
    private static final double MIN_ACCEPT_SCORE = 0.62;
    private static final double EARLY_ACCEPT_SCORE = 0.88;
    private static final Pattern GOOGLE_STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern GOOGLE_LAT_PATTERN = Pattern.compile("\"lat\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern GOOGLE_LNG_PATTERN = Pattern.compile("\"lng\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern GOOGLE_FORMATTED_ADDRESS_PATTERN = Pattern.compile("\"formatted_address\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OSM_LAT_PATTERN = Pattern.compile("\"lat\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OSM_LON_PATTERN = Pattern.compile("\"lon\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OSM_DISPLAY_NAME_PATTERN = Pattern.compile("\"display_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PHOTON_COORDS_PATTERN = Pattern.compile(
            "\"coordinates\"\\s*:\\s*\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\]");
    private static final Pattern PHOTON_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PHOTON_CITY_PATTERN = Pattern.compile("\"city\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PHOTON_STATE_PATTERN = Pattern.compile("\"state\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PHOTON_COUNTRY_PATTERN = Pattern.compile("\"country\"\\s*:\\s*\"([^\"]+)\"");
    private static final String TUNISIA_BBOX = "7.5,30.1,11.7,37.6";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static final class GeocodeResult {
        private final double latitude;
        private final double longitude;
        private final String label;
        private final String provider;
        private final double score;

        public GeocodeResult(double latitude, double longitude, String label, String provider, double score) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.label = label;
            this.provider = provider;
            this.score = score;
        }

        public double latitude() {
            return latitude;
        }

        public double longitude() {
            return longitude;
        }

        public String label() {
            return label;
        }

        public String provider() {
            return provider;
        }

        public double score() {
            return score;
        }
    }

    public Optional<double[]> tryExtractFromMapLink(String mapsLink) {
        return MapEmbedUrls.tryExtractLatLng(mapsLink);
    }

    public Optional<double[]> geocodeAddress(String address) throws Exception {
        Optional<GeocodeResult> detailed = geocodeAddressDetailed(address);
        if (detailed.isEmpty()) {
            return Optional.empty();
        }
        GeocodeResult r = detailed.get();
        return Optional.of(new double[]{r.latitude(), r.longitude()});
    }

    public Optional<GeocodeResult> geocodeAddressDetailed(String address) throws Exception {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeAddress(address);
        String googleApiKey = cfgPreferFile("google.maps.apiKey", "GOOGLE_MAPS_API_KEY", "");

        if (!googleApiKey.isBlank()) {
            GeocodeResult googleBest = null;
            googleBest = pickBest(googleBest, geocodeQueryWithGoogleMaps(normalized, googleApiKey).orElse(null));
            if (googleBest != null && googleBest.score() >= EARLY_ACCEPT_SCORE) {
                return Optional.of(googleBest);
            }
            List<String> googleQueries = buildQueryVariants(normalized);
            for (String q : googleQueries) {
                googleBest = pickBest(googleBest, geocodeQueryWithGoogleMaps(q, googleApiKey).orElse(null));
            }
            if (googleBest != null && googleBest.score() >= MIN_ACCEPT_SCORE) {
                return Optional.of(googleBest);
            }
        }

        // Essai prioritaire: requête exacte telle que saisie.
        GeocodeResult exactBest = null;
        exactBest = pickBest(exactBest, geocodeQueryWithPhoton(normalized, true).orElse(null));
        exactBest = pickBest(exactBest, geocodeQueryWithOpenStreetMap(normalized, true).orElse(null));
        if (exactBest != null && exactBest.score() >= EARLY_ACCEPT_SCORE) {
            return Optional.of(exactBest);
        }

        List<String> queries = buildQueryVariants(normalized);
        GeocodeResult best = null;

        for (String q : queries) {
            best = pickBest(best, geocodeQueryWithPhoton(q, true).orElse(null));
            best = pickBest(best, geocodeQueryWithOpenStreetMap(q, true).orElse(null));
        }

        for (String q : queries) {
            best = pickBest(best, geocodeQueryWithPhoton(q, false).orElse(null));
            best = pickBest(best, geocodeQueryWithOpenStreetMap(q, false).orElse(null));
        }
        if (best == null || best.score() < MIN_ACCEPT_SCORE) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    public String buildMapLink(double lat, double lon) {
        return String.format(java.util.Locale.US,
                "https://www.google.com/maps?q=%f,%f",
                lat, lon);
    }

    private Optional<GeocodeResult> geocodeQueryWithOpenStreetMap(String query, boolean tunisiaOnly) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://nominatim.openstreetmap.org/search?q=" + encoded
                + "&format=jsonv2&limit=5&addressdetails=1";
        if (tunisiaOnly) {
            url += "&countrycodes=tn";
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AutiCare-JavaFX/1.0")
                .header("Accept-Language", "fr")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenStreetMap HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank() || "[]".equals(body.trim())) {
            return Optional.empty();
        }
        List<String> lats = findAll(OSM_LAT_PATTERN, body);
        List<String> lons = findAll(OSM_LON_PATTERN, body);
        List<String> labels = findAll(OSM_DISPLAY_NAME_PATTERN, body);
        int n = Math.min(Math.min(lats.size(), lons.size()), labels.size());
        GeocodeResult best = null;
        for (int i = 0; i < n; i++) {
            String label = labels.get(i);
            if (!isRelevantMatch(query, label)) {
                continue;
            }
            double lat = Double.parseDouble(lats.get(i));
            double lon = Double.parseDouble(lons.get(i));
            double score = computeMatchScore(query, label);
            best = pickBest(best, new GeocodeResult(lat, lon, label, "Nominatim", score));
        }
        return Optional.ofNullable(best);
    }

    private Optional<GeocodeResult> geocodeQueryWithPhoton(String query, boolean tunisiaBias) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://photon.komoot.io/api/?q=" + encoded + "&limit=5&lang=fr";
        if (tunisiaBias) {
            url += "&bbox=" + TUNISIA_BBOX;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AutiCare-JavaFX/1.0")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Photon OpenStreetMap HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank() || body.contains("\"features\":[]")) {
            return Optional.empty();
        }
        List<String> names = findAll(PHOTON_NAME_PATTERN, body);
        List<String> cities = findAll(PHOTON_CITY_PATTERN, body);
        List<String> states = findAll(PHOTON_STATE_PATTERN, body);
        List<String> countries = findAll(PHOTON_COUNTRY_PATTERN, body);

        Matcher m = PHOTON_COORDS_PATTERN.matcher(body);
        int idx = 0;
        GeocodeResult best = null;
        while (m.find()) {
            String name = idx < names.size() ? names.get(idx) : query;
            String city = idx < cities.size() ? cities.get(idx) : "";
            String state = idx < states.size() ? states.get(idx) : "";
            String country = idx < countries.size() ? countries.get(idx) : "";
            String label = (name + " " + city + " " + state + " " + country).trim();
            if (label.isBlank()) {
                label = query;
            }
            if (isRelevantMatch(query, label)) {
                double lon = Double.parseDouble(m.group(1));
                double lat = Double.parseDouble(m.group(2));
                double score = computeMatchScore(query, label);
                best = pickBest(best, new GeocodeResult(lat, lon, label, "Photon", score));
            }
            idx++;
        }
        return Optional.ofNullable(best);
    }

    private Optional<GeocodeResult> geocodeQueryWithGoogleMaps(String query, String apiKey) throws Exception {
        if (query == null || query.isBlank() || apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address="
                + encoded
                + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&language=fr"
                + "&region=tn";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google Geocoding HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        String status = extractFirst(GOOGLE_STATUS_PATTERN, body).orElse("");
        if (!"OK".equalsIgnoreCase(status)) {
            return Optional.empty();
        }

        List<String> lats = findAll(GOOGLE_LAT_PATTERN, body);
        List<String> lngs = findAll(GOOGLE_LNG_PATTERN, body);
        List<String> labels = findAll(GOOGLE_FORMATTED_ADDRESS_PATTERN, body);
        int n = Math.min(Math.min(lats.size(), lngs.size()), labels.size());
        GeocodeResult best = null;
        for (int i = 0; i < n; i++) {
            String label = labels.get(i);
            if (!isRelevantMatch(query, label)) {
                continue;
            }
            double lat = Double.parseDouble(lats.get(i));
            double lng = Double.parseDouble(lngs.get(i));
            double score = Math.min(1.0, computeMatchScore(query, label) + 0.15);
            best = pickBest(best, new GeocodeResult(lat, lng, label, "Google", score));
        }
        return Optional.ofNullable(best);
    }

    private static GeocodeResult pickBest(GeocodeResult current, GeocodeResult candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return candidate.score() > current.score() ? candidate : current;
    }

    private static boolean isRelevantMatch(String query, String displayName) {
        Set<String> queryTokens = significantTokens(query);
        if (queryTokens.isEmpty()) {
            return true;
        }
        Set<String> resultTokens = significantTokens(displayName);
        int matched = 0;
        for (String t : queryTokens) {
            if (resultTokens.contains(t)) {
                matched++;
            }
        }
        double ratio = (double) matched / (double) queryTokens.size();
        if (queryTokens.size() >= 2) {
            return matched >= 2 || ratio >= 0.67;
        }
        return ratio >= 0.6;
    }

    private static Set<String> significantTokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String[] parts = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split(" ");
        for (String p : parts) {
            if (p.length() >= 3) {
                out.add(p);
            }
        }
        return out;
    }

    private static double computeMatchScore(String query, String label) {
        if (query == null || query.isBlank() || label == null || label.isBlank()) {
            return 0.0;
        }
        Set<String> q = significantTokens(query);
        if (q.isEmpty()) {
            return 0.0;
        }
        Set<String> l = significantTokens(label);
        int matched = 0;
        for (String t : q) {
            if (l.contains(t)) {
                matched++;
            }
        }
        int missing = Math.max(0, q.size() - matched);
        double ratio = (double) matched / (double) q.size();
        ratio -= Math.min(0.35, missing * 0.08);
        String qNorm = Normalizer.normalize(query, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        String lNorm = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        if (lNorm.contains(qNorm) || qNorm.contains(lNorm)) {
            ratio += 0.2;
        }
        return Math.min(1.0, ratio);
    }

    private static List<String> findAll(Pattern pattern, String text) {
        List<String> out = new ArrayList<>();
        Matcher m = pattern.matcher(text == null ? "" : text);
        while (m.find()) {
            if (m.groupCount() >= 1 && m.group(1) != null) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private static Optional<String> extractFirst(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        if (m.find() && m.groupCount() >= 1 && m.group(1) != null && !m.group(1).isBlank()) {
            return Optional.of(m.group(1).trim());
        }
        return Optional.empty();
    }

    private static String normalizeAddress(String raw) {
        String normalized = raw.trim()
                .replaceAll("[\\t\\n\\r]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ", ");
        // Corrections orthographiques fréquentes sur les adresses locales.
        normalized = normalized.replaceAll("(?i)ariena", "Ariana");
        normalized = normalized.replaceAll("(?i)ghazela", "Ghazala");
        return normalized;
    }

    private static List<String> buildQueryVariants(String normalized) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(normalized);

        String commaAsSpace = normalized.replace(",", " ").replaceAll("\\s+", " ").trim();
        if (!commaAsSpace.isBlank()) {
            variants.add(commaAsSpace);
        }

        String[] rawTokens = commaAsSpace.split(" ");
        List<String> longTokens = new ArrayList<>();
        for (String t : rawTokens) {
            String clean = t.trim();
            if (clean.length() >= 3) {
                longTokens.add(clean);
            }
        }
        if (longTokens.size() >= 2) {
            variants.add(String.join(" ", longTokens));
        }

        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String q : variants) {
            String lower = q.toLowerCase(Locale.ROOT);
            expanded.add(q);
            if (!lower.contains("tunisie") && !lower.contains("tunisia")) {
                expanded.add(q + ", Tunisie");
            }
        }
        return new ArrayList<>(expanded);
    }

    private static String cfgPreferFile(String prop, String env, String fallback) {
        String f = FILE_CONFIG.getProperty(prop);
        if (f != null && !f.isBlank()) {
            return f.trim();
        }
        String fe = FILE_CONFIG.getProperty(env);
        if (fe != null && !fe.isBlank()) {
            return fe.trim();
        }
        String s = System.getProperty(prop);
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = OpenStreetMapService.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        return p;
    }
}
