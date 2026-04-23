package org.example.services;

import java.io.InputStream;
import java.util.Properties;

public final class FaceIdConfig {
    private static final Properties FILE_CONFIG = loadFileConfig();

    public boolean isEnabled() {
        return Boolean.parseBoolean(cfgPreferFile("faceid.enabled", "FACEID_ENABLED", "true"));
    }

    public boolean isRequireHealthy() {
        return Boolean.parseBoolean(cfgPreferFile("faceid.requireHealthy", "FACEID_REQUIRE_HEALTHY", "true"));
    }

    public double threshold() {
        String raw = cfgPreferFile("faceid.threshold", "FACEID_THRESHOLD", "0.75");
        try {
            double x = Double.parseDouble(raw);
            if (x < 0.0) {
                return 0.0;
            }
            return Math.min(x, 1.0);
        } catch (Exception ignored) {
            return 0.75;
        }
    }

    public String serviceUrl() {
        String url = cfgPreferFile("faceid.service.url", "FACEID_SERVICE_URL", "http://127.0.0.1:8099");
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String cfgPreferFile(String prop, String env, String fallback) {
        String f = FILE_CONFIG.getProperty(prop);
        if (f != null && !f.isBlank()) return f.trim();
        String fe = FILE_CONFIG.getProperty(env);
        if (fe != null && !fe.isBlank()) return fe.trim();
        String s = System.getProperty(prop);
        if (s != null && !s.isBlank()) return s.trim();
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) return e.trim();
        return fallback;
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = FaceIdConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) p.load(in);
        } catch (Exception ignored) {
        }
        return p;
    }
}
