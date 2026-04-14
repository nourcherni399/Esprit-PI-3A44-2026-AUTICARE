package org.example.utils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Racine du dossier {@code public} du projet Symfony (fichiers servis comme {@code /uploads/...}).
 * <p>
 * Même logique que {@code asset()} côté web : la BDD stocke un chemin relatif
 * (ex. {@code uploads/users/photo.jpg}) résolu sous {@code public/}.
 * </p>
 * <ul>
 *   <li>Propriété système {@code auticare.public.root} (chemin absolu vers {@code public})</li>
 *   <li>ou variable d’environnement {@code AUTICARE_PUBLIC_ROOT}</li>
 *   <li>sinon valeur par défaut (à adapter si besoin)</li>
 * </ul>
 */
public final class UserPublicAssets {

    private static final String PROP = "auticare.public.root";
    private static final String ENV = "AUTICARE_PUBLIC_ROOT";
    /** Dossier {@code public} par défaut — aligné sur le logo admin (logo sous {@code public/images}). */
    private static final Path DEFAULT_PUBLIC = Path.of("C:/Users/comme/Desktop/PI/public");

    private UserPublicAssets() {
    }

    public static Path getPublicRoot() {
        String p = System.getProperty(PROP);
        if (p != null && !p.isBlank()) {
            return Path.of(p.trim()).toAbsolutePath().normalize();
        }
        String e = System.getenv(ENV);
        if (e != null && !e.isBlank()) {
            return Path.of(e.trim()).toAbsolutePath().normalize();
        }
        return DEFAULT_PUBLIC.toAbsolutePath().normalize();
    }

    /**
     * Résout un chemin relatif BDD (ex. {@code uploads/users/x.jpg}) vers un fichier sur le disque.
     *
     * @return {@code null} si {@code relativePath} vide ou invalide (path traversal refusé)
     */
    public static Path resolvePublicRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String norm = relativePath.trim().replace('\\', '/');
        if (norm.startsWith("/")) {
            norm = norm.substring(1);
        }
        if (norm.contains("..")) {
            return null;
        }
        Path base = getPublicRoot();
        Path resolved = base.resolve(norm).normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved;
    }

    public static boolean imageFileExists(String relativePath) {
        Path p = resolvePublicRelative(relativePath);
        return p != null && Files.isRegularFile(p);
    }
}
