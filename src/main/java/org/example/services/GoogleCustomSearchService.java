package org.example.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

/**
 * Recherche “comme Google” via l’API <a href="https://developers.google.com/custom-search/v1/introduction">Custom Search JSON API</a>.
 * Nécessite une clé API (Custom Search API activée) et l’ID moteur {@code cx} (Programmable Search Engine, recherche web entière de préférence).
 */
public class GoogleCustomSearchService {

    private static final String CSE_URL = "https://www.googleapis.com/customsearch/v1";
    private static final String GOOGLE_NEWS_RSS_URL = "https://news.google.com/rss/search";
    private static final String DDG_API_URL = "https://api.duckduckgo.com/";
    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/";
    private static final Pattern YEAR4 = Pattern.compile("^[0-9]{4}$");
    private static final Properties FILE_CONFIG = loadFileConfig();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public List<CseResult> searchEventIdeas(String keywords, String periodeChoisie) throws Exception {
        String key = cfg("google.search.apiKey", "GOOGLE_CSE_API_KEY", "");
        String cx = cfg("google.search.engineId", "GOOGLE_CSE_ENGINE_ID", "");
        if (keywords == null || keywords.isBlank()) {
            throw new IllegalArgumentException("Indiquez des mots-clés pour la recherche.");
        }
        String p = periodeChoisie == null ? "Ce mois" : periodeChoisie.trim();
        String baseKeywords = keywords.trim();
        StringBuilder q = new StringBuilder(keywords.trim());
        q.append(" événement conférence colloque atelier");
        String periodQueryHint = periodQueryHint(p);
        if (!periodQueryHint.isBlank()) {
            q.append(" ").append(periodQueryHint);
        }
        String dateRestrict;
        if ("Ce mois".equals(p)) {
            dateRestrict = "m1";
        } else if ("Ce trimestre".equals(p)) {
            dateRestrict = "m3";
        } else {
            dateRestrict = null;
        }

        StringBuilder url = new StringBuilder(CSE_URL);
        url.append("?key=").append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        url.append("&cx=").append(URLEncoder.encode(cx, StandardCharsets.UTF_8));
        url.append("&q=").append(URLEncoder.encode(q.toString().trim(), StandardCharsets.UTF_8));
        url.append("&num=10&safe=off&hl=fr&lr=lang_fr");
        if (dateRestrict != null && !dateRestrict.isEmpty()) {
            url.append("&dateRestrict=").append(URLEncoder.encode(dateRestrict, StandardCharsets.UTF_8));
        }
        String googleSort = googleSortParam(p);
        if (!googleSort.isBlank()) {
            url.append("&sort=").append(URLEncoder.encode(googleSort, StandardCharsets.UTF_8));
        }
        List<String> failures = new ArrayList<>();

        if (!key.isBlank() && !cx.isBlank()) {
            try {
                List<CseResult> google = queryGoogleCse(url.toString());
                if (!google.isEmpty()) {
                    return google;
                }
                failures.add("Google CSE: aucun resultat");
            } catch (Exception ex) {
                failures.add("Google CSE: " + sanitizeOneLine(ex.getMessage()));
            }
        } else {
            failures.add("Google CSE: non configure");
        }

        try {
            List<CseResult> news = queryGoogleNewsRss(q.toString().trim(), p);
            if (!news.isEmpty()) {
                return news;
            }
            news = queryGoogleNewsRss(baseKeywords, p);
            if (!news.isEmpty()) {
                return news;
            }
            failures.add("Google News RSS: aucun resultat (strict + elargi)");
        } catch (Exception ex) {
            failures.add("Google News RSS: " + sanitizeOneLine(ex.getMessage()));
        }

        try {
            List<CseResult> ddg = queryDuckDuckGoApi(q.toString().trim(), p);
            if (!ddg.isEmpty()) {
                return ddg;
            }
            ddg = queryDuckDuckGoApi(baseKeywords, p);
            if (!ddg.isEmpty()) {
                return ddg;
            }
            ddg = queryDuckDuckGo(q.toString().trim(), p);
            if (!ddg.isEmpty()) {
                return ddg;
            }
            ddg = queryDuckDuckGo(baseKeywords, p);
            if (!ddg.isEmpty()) {
                return ddg;
            }
            failures.add("DuckDuckGo: aucun resultat (API + HTML, strict + elargi)");
        } catch (Exception ex) {
            failures.add("DuckDuckGo: " + sanitizeOneLine(ex.getMessage()));
        }

        List<CseResult> fallback = new ArrayList<>();
        fallback.add(new CseResult(
                "Recherche web alternative (ouvrir la page de resultats)",
                "https://duckduckgo.com/?q=" + URLEncoder.encode(q.toString().trim(), StandardCharsets.UTF_8),
                "Sources testees: " + String.join(" | ", failures) + ". Ouvrez ce lien pour consulter les resultats web directement."));
        return fallback;
    }

    private List<CseResult> queryGoogleCse(String url) throws Exception {
        HttpResponse<String> res = performGet(url, true);
        String body = res.body() == null ? "" : res.body();
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String detail = tryMessage(body);
            String base = "Google CSE a repondu " + res.statusCode() + (detail == null ? "" : " - " + detail);
            if (detail != null
                    && (detail.contains("does not have the access to Custom Search JSON API")
                    || detail.contains("Custom Search JSON API"))) {
                base += " | L'API Custom Search JSON peut etre refusee pour certains projets Google Cloud recents.";
            }
            throw new IllegalStateException(base);
        }
        return parseJson(body, 10);
    }

    private List<CseResult> queryGoogleNewsRss(String query, String periodChoice) throws Exception {
        String periodPart = periodNewsQueryPart(periodChoice);
        String q = query + (periodPart.isBlank() ? "" : " " + periodPart);
        StringBuilder url = new StringBuilder(GOOGLE_NEWS_RSS_URL);
        url.append("?q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
        url.append("&hl=fr&gl=FR&ceid=FR:fr");
        HttpResponse<String> res = performGet(url.toString(), false);
        String body = res.body() == null ? "" : res.body();
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Google News RSS a repondu " + res.statusCode());
        }
        return parseGoogleNewsRss(body, 10);
    }

    private List<CseResult> queryDuckDuckGo(String query, String periodChoice) throws Exception {
        StringBuilder url = new StringBuilder(DDG_HTML_URL);
        url.append("?q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&kp=-2&kl=fr-fr");
        String ddgDf = ddgDateFilter(periodChoice);
        if (!ddgDf.isBlank()) {
            url.append("&df=").append(URLEncoder.encode(ddgDf, StandardCharsets.UTF_8));
        }
        HttpResponse<String> res = performGet(url.toString(), false);
        String body = res.body() == null ? "" : res.body();
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("DuckDuckGo a repondu " + res.statusCode());
        }
        return parseDuckHtml(body, 10);
    }

    private List<CseResult> queryDuckDuckGoApi(String query, String periodChoice) throws Exception {
        String q = query;
        String dateHint = periodQueryHint(periodChoice);
        if (!dateHint.isBlank()) {
            q += " " + dateHint;
        }
        StringBuilder url = new StringBuilder(DDG_API_URL);
        url.append("?q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
        url.append("&format=json&no_redirect=1&no_html=1&skip_disambig=1");
        HttpResponse<String> res = performGet(url.toString(), true);
        String body = res.body() == null ? "" : res.body();
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("DuckDuckGo API a repondu " + res.statusCode());
        }
        return parseDuckApiJson(body, 10);
    }

    private HttpResponse<String> performGet(String url, boolean json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .header("User-Agent", "AutiCareDesktop/1.0 (+web-search)");
        if (json) {
            b.header("Accept", "application/json");
        } else {
            b.header("Accept", "text/html,application/xhtml+xml");
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static List<CseResult> parseGoogleNewsRss(String xml, int maxItems) {
        List<CseResult> out = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return out;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList items = doc.getElementsByTagName("item");
            int n = 0;
            for (int i = 0; i < items.getLength() && n < maxItems; i++) {
                org.w3c.dom.Node item = items.item(i);
                NodeList children = item.getChildNodes();
                String title = "";
                String link = "";
                String pubDate = "";
                for (int c = 0; c < children.getLength(); c++) {
                    org.w3c.dom.Node child = children.item(c);
                    String name = child.getNodeName();
                    String text = child.getTextContent();
                    if ("title".equalsIgnoreCase(name)) {
                        title = sanitizeOneLine(text);
                    } else if ("link".equalsIgnoreCase(name)) {
                        link = sanitizeOneLine(text);
                    } else if ("pubDate".equalsIgnoreCase(name)) {
                        pubDate = sanitizeOneLine(text);
                    }
                }
                if (!link.isBlank()) {
                    String snippet = pubDate.isBlank() ? "Source: Google News RSS" : "Publie le " + pubDate;
                    out.add(new CseResult(title.isBlank() ? "Resultat actualite" : title, link, snippet));
                    n++;
                }
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    private static List<CseResult> parseDuckHtml(String html, int maxItems) {
        List<CseResult> out = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return out;
        }
        Pattern p = Pattern.compile(
                "(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
        java.util.regex.Matcher m = p.matcher(html);
        int n = 0;
        while (m.find() && n < maxItems) {
            String link = htmlDecode(stripTags(m.group(1)));
            String title = htmlDecode(stripTags(m.group(2)));
            if (link == null || link.isBlank() || title == null || title.isBlank()) {
                continue;
            }
            out.add(new CseResult(sanitizeOneLine(title), sanitizeOneLine(link), ""));
            n++;
        }
        return out;
    }

    private static List<CseResult> parseDuckApiJson(String json, int maxItems) {
        List<CseResult> out = new ArrayList<>();
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return out;
        }
        if (root.has("Results") && root.get("Results").isJsonArray()) {
            addDuckApiTopics(root.getAsJsonArray("Results"), out, maxItems);
        }
        if (out.size() < maxItems && root.has("RelatedTopics") && root.get("RelatedTopics").isJsonArray()) {
            addDuckApiTopics(root.getAsJsonArray("RelatedTopics"), out, maxItems);
        }
        return out;
    }

    private static void addDuckApiTopics(JsonArray arr, List<CseResult> out, int maxItems) {
        for (JsonElement el : arr) {
            if (out.size() >= maxItems) {
                return;
            }
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            if (o.has("Topics") && o.get("Topics").isJsonArray()) {
                addDuckApiTopics(o.getAsJsonArray("Topics"), out, maxItems);
                continue;
            }
            String text = o.has("Text") && o.get("Text").isJsonPrimitive() ? o.get("Text").getAsString() : "";
            String link = o.has("FirstURL") && o.get("FirstURL").isJsonPrimitive() ? o.get("FirstURL").getAsString() : "";
            if (!link.isBlank()) {
                String title = text;
                int dash = text.indexOf(" - ");
                if (dash > 0) {
                    title = text.substring(0, dash).trim();
                }
                out.add(new CseResult(sanitizeOneLine(title), sanitizeOneLine(link), sanitizeOneLine(text)));
            }
        }
    }

    private static String stripTags(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("(?is)<[^>]+>", " ").trim();
    }

    private static String htmlDecode(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String periodQueryHint(String periodChoice) {
        if ("Ce mois".equals(periodChoice)) {
            LocalDate d = LocalDate.now();
            return d.getMonthValue() + "/" + d.getYear() + " derniers événements";
        }
        if ("Ce trimestre".equals(periodChoice)) {
            LocalDate d = LocalDate.now();
            return "3 derniers mois " + d.getYear();
        }
        if (YEAR4.matcher(periodChoice).matches()) {
            return "en " + periodChoice;
        }
        return "";
    }

    private static String googleSortParam(String periodChoice) {
        if (!YEAR4.matcher(periodChoice).matches()) {
            return "";
        }
        String y = periodChoice;
        return "date:r:" + y + "0101:" + y + "1231";
    }

    private static String ddgDateFilter(String periodChoice) {
        if ("Ce mois".equals(periodChoice)) {
            return "m";
        }
        if ("Ce trimestre".equals(periodChoice)) {
            return "m";
        }
        if (YEAR4.matcher(periodChoice).matches()) {
            if (periodChoice.equals(String.valueOf(LocalDate.now().getYear()))) {
                return "y";
            }
            return "";
        }
        return "";
    }

    private static String periodNewsQueryPart(String periodChoice) {
        if ("Ce mois".equals(periodChoice)) {
            return "when:30d";
        }
        if ("Ce trimestre".equals(periodChoice)) {
            return "when:90d";
        }
        if (YEAR4.matcher(periodChoice).matches()) {
            String y = periodChoice;
            return "after:" + y + "-01-01 before:" + y + "-12-31";
        }
        return "";
    }

    private static String tryMessage(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("error") && o.get("error").isJsonObject()) {
                JsonObject err = o.getAsJsonObject("error");
                if (err.has("message") && !err.get("message").isJsonNull()) {
                    return err.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<CseResult> parseJson(String json, int maxItems) {
        List<CseResult> out = new ArrayList<>();
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return out;
        }
        if (!root.has("items") || !root.get("items").isJsonArray()) {
            return out;
        }
        JsonArray items = root.getAsJsonArray("items");
        int n = 0;
        for (JsonElement el : items) {
            if (!el.isJsonObject() || n >= maxItems) {
                break;
            }
            JsonObject o = el.getAsJsonObject();
            String title = o.has("title") && o.get("title").isJsonPrimitive() ? o.get("title").getAsString() : "—";
            String link = o.has("link") && o.get("link").isJsonPrimitive() ? o.get("link").getAsString() : "";
            String snip = o.has("snippet") && o.get("snippet").isJsonPrimitive() ? o.get("snippet").getAsString() : "";
            if (!link.isBlank()) {
                out.add(new CseResult(sanitizeOneLine(title), link, sanitizeOneLine(snip)));
                n++;
            }
        }
        return out;
    }

    private static String sanitizeOneLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    public record CseResult(String title, String link, String snippet) {
    }

    private static String cfg(String key, String env, String def) {
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
        return def;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = GoogleCustomSearchService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        return p;
    }
}
