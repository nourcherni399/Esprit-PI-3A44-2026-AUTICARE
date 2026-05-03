package org.example.services;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Lecture minimale des fichiers {@code .env} / {@code .env.local} (style Symfony) : lignes {@code CLE=valeur},
 * commentaires {@code #}, guillemets optionnels sur la valeur.
 */
final class DotEnvStyleParser {

    private DotEnvStyleParser() {
    }

    /**
     * Fusionne les variables reconnues dans {@code target} (écrase les clés homonymes).
     */
    static void mergeInto(Properties target, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int eq = t.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String rawKey = t.substring(0, eq).trim();
                String val = t.substring(eq + 1).trim();
                val = stripInlineComment(val);
                val = unquote(val);
                if (val.isEmpty()) {
                    continue;
                }
                String mapped = mapToJavaKey(rawKey);
                if (mapped != null) {
                    target.setProperty(mapped, val);
                }
            }
        } catch (Exception ignored) {
            // —
        }
    }

    private static String stripInlineComment(String val) {
        int i = val.indexOf(" #");
        if (i < 0) {
            return val;
        }
        return val.substring(0, i).trim();
    }

    private static String unquote(String v) {
        if (v.length() >= 2) {
            char a = v.charAt(0);
            char z = v.charAt(v.length() - 1);
            if ((a == '"' && z == '"') || (a == '\'' && z == '\'')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    /**
     * Noms souvent présents dans un projet Symfony (.env) → clés lues par {@link AssistantSecretsLoader}.
     */
    private static String mapToJavaKey(String symfonyKey) {
        if (symfonyKey == null || symfonyKey.isBlank()) {
            return null;
        }
        String k = symfonyKey.trim();
        String u = k.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "GROQ_API_KEY" -> "groq.api.key";
            case "GROQ_MODEL" -> "groq.model";
            case "CHAT_API_KEY" -> "groq.api.key";
            case "CHAT_MODEL" -> "groq.model";
            case "UNSPLASH_ACCESS_KEY", "UNSPLASH_KEY", "UNSPLASH_CLIENT_ID" -> "unsplash.access.key";
            default -> {
                String lower = k.toLowerCase(Locale.ROOT);
                if (lower.equals("groq.api.key") || lower.equals("unsplash.access.key") || lower.equals("groq.model")) {
                    yield lower;
                }
                yield null;
            }
        };
    }
}
