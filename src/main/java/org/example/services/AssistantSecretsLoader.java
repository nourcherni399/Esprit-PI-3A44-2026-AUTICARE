package org.example.services;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Charge les clés Groq / Unsplash pour le projet Java uniquement (aucun dossier Symfony).
 * <p>
 * Ordre de fusion (les étapes suivantes surchargent les précédentes) :
 * <ol>
 *   <li>Fichiers {@code .env} puis {@code .env.local} à la racine du projet (système : {@code user.dir}, souvent le dossier du {@code pom.xml})</li>
 *   <li>Même chose dans le dossier optionnel {@code -Dacticare.project.root=...} si défini</li>
 *   <li>{@code assistant-local.properties} dans chacun de ces dossiers (racine puis optionnel)</li>
 *   <li>{@code %USERPROFILE%\.auticare\assistant.properties}</li>
 *   <li>Fichier indiqué par {@code AUTICARE_ASSISTANT_PROPERTIES} (chemin absolu)</li>
 * </ol>
 * <p>
 * Dans les fichiers {@code .env} ou {@code .properties}, les noms reconnus incluent notamment
 * {@code groq.api.key}, {@code GROQ_API_KEY}, {@code CHAT_API_KEY}, {@code groq.model}, {@code CHAT_MODEL},
 * {@code unsplash.access.key}, {@code UNSPLASH_ACCESS_KEY}, {@code UNSPLASH_CLIENT_ID}.
 */
public final class AssistantSecretsLoader {

    private static final Properties PROPS = new Properties();

    static {
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of(System.getProperty("user.dir", ".")));
        String optRoot = System.getProperty("acticare.project.root");
        if (optRoot != null && !optRoot.isBlank()) {
            Path p = Path.of(optRoot.trim()).toAbsolutePath().normalize();
            if (!roots.contains(p)) {
                roots.add(p);
            }
        }
        for (Path root : roots) {
            DotEnvStyleParser.mergeInto(PROPS, root.resolve(".env"));
            DotEnvStyleParser.mergeInto(PROPS, root.resolve(".env.local"));
        }
        for (Path root : roots) {
            loadPropertiesFile(root.resolve("assistant-local.properties"));
        }
        loadPropertiesFile(Path.of(System.getProperty("user.home"), ".auticare", "assistant.properties"));
        String assistantPath = System.getenv("AUTICARE_ASSISTANT_PROPERTIES");
        if (assistantPath != null && !assistantPath.isBlank()) {
            loadPropertiesFile(Path.of(assistantPath.trim()));
        }
    }

    private AssistantSecretsLoader() {
    }

    private static void loadPropertiesFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try (var in = Files.newInputStream(path)) {
            PROPS.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // —
        }
    }

    /** Valeur brute ou {@code null} si absente. */
    public static String get(String key) {
        return PROPS.getProperty(key);
    }
}
