package org.example.utils;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Charge {@code .ai.local.properties} depuis plusieurs emplacements probables
 * (répertoire de travail, racine projet Maven depuis {@code target/classes}, etc.).
 */
public final class LocalAiPropertiesFile {

    private LocalAiPropertiesFile() {}

    public static String readProperty(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (Path p : candidatePaths()) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(p)) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty(key);
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return null;
    }

    private static List<Path> candidatePaths() {
        Set<Path> unique = new LinkedHashSet<>();
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            unique.add(Path.of(userDir).resolve(".ai.local.properties").normalize());
        }
        try {
            URL u = LocalAiPropertiesFile.class.getProtectionDomain().getCodeSource().getLocation();
            if (u != null && "file".equalsIgnoreCase(u.getProtocol())) {
                Path code = Paths.get(u.toURI()).normalize();
                Path dir = Files.isDirectory(code) ? code : code.getParent();
                if (dir != null) {
                    unique.add(dir.resolve(".ai.local.properties").normalize());
                    String dirName = (dir.getFileName() != null) ? dir.getFileName().toString() : "";
                    boolean underMavenClasses = "classes".equals(dirName)
                            || dir.endsWith(Path.of("target", "classes"));
                    if (underMavenClasses) {
                        Path target = dir.getParent();
                        if (target != null) {
                            Path projectRoot = target.getParent();
                            if (projectRoot != null) {
                                unique.add(projectRoot.resolve(".ai.local.properties").normalize());
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignored
        }
        unique.add(Path.of(".ai.local.properties").toAbsolutePath().normalize());
        return new ArrayList<>(unique);
    }
}
