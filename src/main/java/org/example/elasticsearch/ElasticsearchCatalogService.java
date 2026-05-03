package org.example.elasticsearch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.models.Product;
import org.example.services.ProductService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Index + recherche via l’API REST Elasticsearch (compatible module Java).
 * Désactivé si {@link ElasticsearchConfig#isEnabled()} est faux.
 */
public final class ElasticsearchCatalogService {

    public static final String INDEX = "auticare-products";
    private static final Gson GSON = new Gson();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(25);

    private static final String MAPPING_JSON = """
        {
          "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "analysis": {
              "analyzer": {
                "auticare_french": {
                  "tokenizer": "standard",
                  "filter": ["lowercase", "asciifolding"]
                }
              }
            }
          },
          "mappings": {
            "properties": {
              "id": { "type": "integer" },
              "nom": { "type": "text", "analyzer": "auticare_french" },
              "description": { "type": "text", "analyzer": "auticare_french" },
              "categorie": { "type": "keyword" },
              "prix": { "type": "double" },
              "publie": { "type": "boolean" },
              "guest_admin_catalog": { "type": "boolean" },
              "note_moyenne": { "type": "double" },
              "suggest": { "type": "completion", "max_input_length": 60 }
            }
          }
        }
        """;

    private static volatile ElasticsearchCatalogService instance;
    private final AtomicBoolean reindexRunning = new AtomicBoolean(false);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

    private volatile boolean clientInitFailed;

    public static ElasticsearchCatalogService getInstance() {
        if (instance == null) {
            synchronized (ElasticsearchCatalogService.class) {
                if (instance == null) {
                    instance = new ElasticsearchCatalogService();
                }
            }
        }
        return instance;
    }

    public boolean isUsable() {
        if (!ElasticsearchConfig.isEnabled()) {
            return false;
        }
        return !clientInitFailed;
    }

    private String baseUrl() {
        String u = Objects.requireNonNull(ElasticsearchConfig.clusterUrl(), "url").trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private HttpRequest.Builder authRequestBuilder(String path) {
        String key = Objects.requireNonNull(ElasticsearchConfig.apiKey(), "apiKey");
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .timeout(HTTP_TIMEOUT)
            .header("Authorization", "ApiKey " + key)
            .header("Content-Type", "application/json");
    }

    private String post(String path, String json) throws Exception {
        HttpRequest req = authRequestBuilder(path).POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        if (code >= 400) {
            throw new IllegalStateException("HTTP " + code + " : " + truncate(resp.body(), 400));
        }
        return resp.body();
    }

    private String postNdjson(String path, String body) throws Exception {
        String key = Objects.requireNonNull(ElasticsearchConfig.apiKey(), "apiKey");
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .timeout(HTTP_TIMEOUT)
            .header("Authorization", "ApiKey " + key)
            .header("Content-Type", "application/x-ndjson")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        if (code >= 400) {
            throw new IllegalStateException("HTTP " + code + " : " + truncate(resp.body(), 400));
        }
        return resp.body();
    }

    private String put(String path, String json) throws Exception {
        HttpRequest req = authRequestBuilder(path).PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        if (code >= 400 && code != 400) {
            throw new IllegalStateException("HTTP " + code + " : " + truncate(resp.body(), 400));
        }
        return resp.body();
    }

    private String head(String path) throws Exception {
        HttpRequest req = authRequestBuilder(path).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        return String.valueOf(resp.statusCode());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public void ensureIndexExists() {
        if (!ElasticsearchConfig.isEnabled()) {
            return;
        }
        try {
            int code = http.send(
                authRequestBuilder("/" + INDEX).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding()
            ).statusCode();
            if (code == 404) {
                put("/" + INDEX, MAPPING_JSON);
            }
        } catch (Exception e) {
            clientInitFailed = true;
            System.err.println("[Elasticsearch] ensureIndexExists : " + e.getMessage());
        }
    }

    public void reindexAllProductsAsync(ProductService productService) {
        if (!ElasticsearchConfig.isEnabled() || !reindexRunning.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                ensureIndexExists();
                List<Product> all = productService.findAll();
                Set<Integer> guestAdminIds = productService.findPublishedCatalogByAdmin().stream()
                    .map(Product::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                StringBuilder nd = new StringBuilder();
                for (Product p : all) {
                    String id = String.valueOf(p.getId());
                    nd.append("{\"index\":{\"_index\":\"").append(INDEX).append("\",\"_id\":\"").append(id).append("\"}}\n");
                    nd.append(GSON.toJson(toDocument(p, guestAdminIds.contains(p.getId())))).append('\n');
                }
                if (!nd.isEmpty()) {
                    postNdjson("/_bulk?refresh=true", nd.toString());
                }
            } catch (Exception e) {
                System.err.println("[Elasticsearch] reindex : " + e.getMessage());
            } finally {
                reindexRunning.set(false);
            }
        }, "elastic-reindex");
        t.setDaemon(true);
        t.start();
    }

    private static Map<String, Object> toDocument(Product p, boolean guestAdminCatalog) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("nom", p.getNom() != null ? p.getNom() : "");
        m.put("description", p.getDescription() != null ? p.getDescription() : "");
        m.put("categorie", p.getCategorie() != null ? p.getCategorie() : "");
        m.put("prix", p.getPrix());
        m.put("publie", ProductService.isPublicationStatusPublic(p.getStatutPublication()) || p.isPublie());
        m.put("guest_admin_catalog", guestAdminCatalog);
        m.put("note_moyenne", p.getNoteMoyenne() != null ? p.getNoteMoyenne() : 0.0);
        Map<String, Object> suggest = new LinkedHashMap<>();
        suggest.put("input", suggestInputs(p.getNom()));
        m.put("suggest", suggest);
        return m;
    }

    private static List<String> suggestInputs(String nom) {
        List<String> out = new ArrayList<>();
        if (nom == null || nom.isBlank()) {
            out.add("produit");
            return out;
        }
        String n = nom.trim();
        out.add(n);
        out.add(n.toLowerCase(Locale.FRENCH));
        for (String token : n.split("[\\s,;]+")) {
            if (token.length() >= 2 && !out.contains(token)) {
                out.add(token);
            }
        }
        return out.stream().limit(12).toList();
    }

    public List<Integer> searchPublicProductIds(
        String query,
        String categoryDb,
        double minPrice,
        double maxPrice,
        boolean guestSession,
        int limit
    ) {
        if (!isUsable() || query == null || query.isBlank()) {
            return List.of();
        }
        int lim = Math.max(1, Math.min(80, limit));
        try {
            JsonObject root = new JsonObject();
            root.addProperty("size", lim);
            JsonObject bool = new JsonObject();
            JsonArray must = new JsonArray();
            JsonObject mm = new JsonObject();
            mm.addProperty("query", query.trim());
            mm.add("fields", GSON.toJsonTree(List.of("nom^3", "description^2", "categorie")));
            mm.addProperty("type", "best_fields");
            mm.addProperty("fuzziness", "AUTO");
            JsonObject mmWrap = new JsonObject();
            mmWrap.add("multi_match", mm);
            must.add(mmWrap);
            bool.add("must", must);
            JsonArray filter = new JsonArray();
            filter.add(term("publie", true));
            if (guestSession) {
                filter.add(term("guest_admin_catalog", true));
            }
            if (categoryDb != null && !categoryDb.isBlank()) {
                filter.add(term("categorie", categoryDb));
            }
            JsonObject range = new JsonObject();
            JsonObject pr = new JsonObject();
            pr.addProperty("gte", minPrice);
            pr.addProperty("lte", maxPrice);
            range.add("prix", pr);
            JsonObject rf = new JsonObject();
            rf.add("range", range);
            filter.add(rf);
            bool.add("filter", filter);
            JsonObject q = new JsonObject();
            q.add("bool", bool);
            root.add("query", q);
            return parseIdsFromSearch(post("/" + INDEX + "/_search", root.toString()));
        } catch (Exception e) {
            System.err.println("[Elasticsearch] searchPublic : " + e.getMessage());
            return List.of();
        }
    }

    public List<Integer> searchAdminProductIds(String query, int limit) {
        if (!isUsable() || query == null || query.isBlank()) {
            return List.of();
        }
        int lim = Math.max(1, Math.min(200, limit));
        try {
            JsonObject root = new JsonObject();
            root.addProperty("size", lim);
            JsonObject bool = new JsonObject();
            JsonArray must = new JsonArray();
            JsonObject mm = new JsonObject();
            mm.addProperty("query", query.trim());
            mm.add("fields", GSON.toJsonTree(List.of("nom^3", "description^2", "categorie")));
            mm.addProperty("type", "best_fields");
            mm.addProperty("fuzziness", "AUTO");
            JsonObject mmWrap = new JsonObject();
            mmWrap.add("multi_match", mm);
            must.add(mmWrap);
            bool.add("must", must);
            JsonObject q = new JsonObject();
            q.add("bool", bool);
            root.add("query", q);
            return parseIdsFromSearch(post("/" + INDEX + "/_search", root.toString()));
        } catch (Exception e) {
            System.err.println("[Elasticsearch] searchAdmin : " + e.getMessage());
            return List.of();
        }
    }

    private static JsonObject term(String field, Object value) {
        JsonObject t = new JsonObject();
        JsonObject inner = new JsonObject();
        if (value instanceof Boolean b) {
            inner.addProperty(field, b);
        } else if (value instanceof Number n) {
            inner.addProperty(field, n);
        } else {
            inner.addProperty(field, String.valueOf(value));
        }
        t.add("term", inner);
        return t;
    }

    private static List<Integer> parseIdsFromSearch(String jsonBody) {
        List<Integer> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
        JsonObject hits = root.getAsJsonObject("hits");
        if (hits == null) {
            return out;
        }
        JsonArray arr = hits.getAsJsonArray("hits");
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            JsonObject h = el.getAsJsonObject();
            JsonObject src = h.getAsJsonObject("_source");
            if (src != null && src.has("id") && !src.get("id").isJsonNull()) {
                out.add(src.get("id").getAsInt());
            }
        }
        return out;
    }

    public List<String> suggestCompletion(String prefix, boolean guestSession, int limit) {
        if (!isUsable() || prefix == null || prefix.length() < 2) {
            return List.of();
        }
        int lim = Math.max(1, Math.min(12, limit));
        String pfx = prefix.trim();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("size", lim);
            JsonObject bool = new JsonObject();
            JsonArray must = new JsonArray();
            JsonObject mbp = new JsonObject();
            mbp.addProperty("field", "nom");
            mbp.addProperty("query", pfx);
            JsonObject wrap = new JsonObject();
            wrap.add("match_bool_prefix", mbp);
            must.add(wrap);
            bool.add("must", must);
            JsonArray filter = new JsonArray();
            filter.add(term("publie", true));
            if (guestSession) {
                filter.add(term("guest_admin_catalog", true));
            }
            bool.add("filter", filter);
            JsonObject q = new JsonObject();
            q.add("bool", bool);
            root.add("query", q);
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            String body = post("/" + INDEX + "/_search", root.toString());
            JsonObject res = JsonParser.parseString(body).getAsJsonObject();
            JsonArray hits = res.getAsJsonObject("hits").getAsJsonArray("hits");
            for (JsonElement el : hits) {
                JsonObject src = el.getAsJsonObject().getAsJsonObject("_source");
                if (src != null && src.has("nom")) {
                    String n = src.get("nom").getAsString();
                    if (n != null && !n.isBlank()) {
                        seen.add(n.trim());
                    }
                }
                if (seen.size() >= lim) {
                    break;
                }
            }
            return new ArrayList<>(seen);
        } catch (Exception e) {
            System.err.println("[Elasticsearch] suggest : " + e.getMessage());
            return List.of();
        }
    }

    public List<Integer> recommendPublicIds(
        boolean guestSession,
        String boostCategoryDb,
        Set<Integer> excludeIds,
        int limit
    ) {
        if (!isUsable()) {
            return List.of();
        }
        int pool = Math.max(limit * 6, 36);
        try {
            JsonObject root = new JsonObject();
            root.addProperty("size", pool);
            JsonObject bool = new JsonObject();
            JsonArray must = new JsonArray();
            must.add(JsonParser.parseString("{\"match_all\":{}}"));
            bool.add("must", must);
            JsonArray filter = new JsonArray();
            filter.add(term("publie", true));
            if (guestSession) {
                filter.add(term("guest_admin_catalog", true));
            }
            bool.add("filter", filter);
            if (boostCategoryDb != null && !boostCategoryDb.isBlank()) {
                JsonArray should = new JsonArray();
                should.add(term("categorie", boostCategoryDb));
                bool.add("should", should);
                bool.addProperty("minimum_should_match", 0);
            }
            if (excludeIds != null) {
                for (Integer id : excludeIds) {
                    if (id != null && id > 0) {
                        JsonObject mn = new JsonObject();
                        JsonObject inner = new JsonObject();
                        inner.addProperty("id", id);
                        mn.add("term", inner);
                        if (!bool.has("must_not")) {
                            bool.add("must_not", new JsonArray());
                        }
                        bool.getAsJsonArray("must_not").add(mn);
                    }
                }
            }
            JsonArray sort = new JsonArray();
            JsonObject s1 = new JsonObject();
            JsonObject nm = new JsonObject();
            nm.addProperty("order", "desc");
            s1.add("note_moyenne", nm);
            sort.add(s1);
            root.add("sort", sort);
            JsonObject q = new JsonObject();
            q.add("bool", bool);
            root.add("query", q);
            List<Integer> ids = parseIdsFromSearch(post("/" + INDEX + "/_search", root.toString()));
            Collections.shuffle(ids);
            return ids.stream().limit(Math.max(1, limit)).toList();
        } catch (Exception e) {
            System.err.println("[Elasticsearch] recommend : " + e.getMessage());
            return List.of();
        }
    }
}
