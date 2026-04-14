package org.example.utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Racine du dossier {@code public} du projet Symfony (fichiers servis comme {@code /uploads/...}).
 * <p>
 * Même logique que {@code asset()} côté web : la BDD stocke un chemin relatif
 * (ex. {@code uploads/users/photo.jpg}) résolu sous {@code public/}.
 * </p>
 * <ul>
 *   <li>Propriété système {@code auticare.public.root} (chemin absolu vers {@code public})</li>
 *   <li>ou variable d’environnement {@code AUTICARE_PUBLIC_ROOT}</li>
 *   <li>ou clé {@code auticare.public.root} dans {@code application.properties}</li>
 *   <li>sinon valeur par défaut (à adapter si besoin)</li>
 * </ul>
 */
public final class UserPublicAssets {

    private static final String PROP = "auticare.public.root";
    private static final String ENV = "AUTICARE_PUBLIC_ROOT";
    private static final Properties FILE_CONFIG = loadFileConfig();

    private UserPublicAssets() {
    }

    private static Properties loadFileConfig() {
        Properties p = new Properties();
        try (InputStream in = UserPublicAssets.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // —
        }
        return p;
    }

    /**
     * Racine {@code public} par défaut : {@code %USERPROFILE%\.auticare\public} (Windows)
     * ou équivalent, toujours accessible en écriture pour l’utilisateur courant.
     * <p>
     * Surcharge possible : propriété système {@code -Dauticare.public.root=...},
     * variable d’environnement {@code AUTICARE_PUBLIC_ROOT}, ou {@code application.properties}.
     */
    private static Path defaultPublicRoot() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.dir", ".");
        }
        return Path.of(home, ".auticare", "public");
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
        String fromFile = FILE_CONFIG.getProperty(PROP);
        if (fromFile != null && !fromFile.isBlank()) {
            return Path.of(fromFile.trim()).toAbsolutePath().normalize();
        }
        return defaultPublicRoot().toAbsolutePath().normalize();
    }

    /**
     * Normalise un chemin BDD (slashes, sans {@code ..}), et retire un préfixe {@code public/}
     * si présent (anciens chemins Symfony / copie de dump).
     *
     * @return {@code null} si vide ou invalide
     */
    static String normalizePublicRelativePath(String relativePath) {
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
        if (norm.startsWith("public/")) {
            norm = norm.substring("public/".length());
        }
        return norm;
    }

    /**
     * Résout un chemin relatif BDD (ex. {@code uploads/users/x.jpg}) vers un emplacement sous la racine {@code public}.
     *
     * @return {@code null} si {@code relativePath} vide ou invalide (path traversal refusé)
     */
    public static Path resolvePublicRelative(String relativePath) {
        String norm = normalizePublicRelativePath(relativePath);
        if (norm == null) {
            return null;
        }
        Path base = getPublicRoot();
        Path resolved = base.resolve(norm).normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved;
    }

    /**
     * Trouve un fichier uploadé existant : d’abord sous {@link #getPublicRoot()}, sinon sous
     * {@code user.dir/public} (ex. projet Symfony ouvert depuis l’IDE).
     *
     * @return chemin absolu du fichier, ou {@code null} si introuvable
     */
    public static Path findPublicRelativeFile(String relativePath) {
        Path primary = resolvePublicRelative(relativePath);
        if (primary != null && Files.isRegularFile(primary)) {
            return primary;
        }
        String norm = normalizePublicRelativePath(relativePath);
        if (norm == null) {
            return null;
        }
        Path cwdPublic = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve("public");
        Path candidate = cwdPublic.resolve(norm).normalize();
        if (!candidate.startsWith(cwdPublic)) {
            return null;
        }
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        return null;
    }

    public static boolean imageFileExists(String relativePath) {
        return findPublicRelativeFile(relativePath) != null;
    }
}
