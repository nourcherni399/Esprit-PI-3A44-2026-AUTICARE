package org.example.elasticsearch;

import java.io.InputStream;
import java.util.Properties;

/**
 * Charge l’URL du cluster et la clé API depuis l’environnement ou {@code application.properties}.
 * Ne jamais committer de clé : utiliser {@code ELASTICSEARCH_API_KEY} ou {@code -Delasticsearch.apiKey=}.
 */
public final class ElasticsearchConfig {

    private static final String PROPS_PATH = "application.properties";

    private ElasticsearchConfig() {
    }

    public static String clusterUrl() {
        String env = trimToNull(System.getenv("ELASTICSEARCH_URL"));
        if (env != null) {
            return env;
        }
        return trimToNull(readProps().getProperty("elasticsearch.url"));
    }

    /**
     * Valeur brute pour l’en-tête {@code Authorization: ApiKey …} (souvent la chaîne Base64 fournie par Elastic).
     */
    public static String apiKey() {
        String env = trimToNull(System.getenv("ELASTICSEARCH_API_KEY"));
        if (env != null) {
            return env;
        }
        env = trimToNull(System.getenv("ELASTIC_API_KEY"));
        if (env != null) {
            return env;
        }
        return trimToNull(readProps().getProperty("elasticsearch.apiKey"));
    }

    public static boolean isEnabled() {
        return clusterUrl() != null && apiKey() != null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Properties readProps() {
        Properties p = new Properties();
        try (InputStream in = ElasticsearchConfig.class.getClassLoader().getResourceAsStream(PROPS_PATH)) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // —
        }
        return p;
    }
}
