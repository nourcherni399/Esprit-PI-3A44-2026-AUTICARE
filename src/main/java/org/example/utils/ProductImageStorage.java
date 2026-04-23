package org.example.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * Copie une image produit vers {@code public/uploads/products/} et retourne le chemin relatif pour la colonne {@code image}.
 */
public final class ProductImageStorage {

    private static final String RELATIVE_DIR = "uploads/products";

    private ProductImageStorage() {
    }

    /**
     * @return chemin relatif BDD, ex. {@code uploads/products/a1b2c3d4.jpg}
     */
    public static String copyProductImage(File source) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("Fichier image invalide");
        }
        Path publicRoot = UserPublicAssets.getPublicRoot();
        Path destDir = publicRoot.resolve(RELATIVE_DIR);
        Files.createDirectories(destDir);

        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : "";
        if (ext.length() > 12) {
            ext = "";
        }
        String fileName = UUID.randomUUID() + ext;
        Path dest = destDir.resolve(fileName);
        Files.copy(source.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

        return RELATIVE_DIR + "/" + fileName;
    }

    /**
     * Télécharge une image (HTTP/S) et l’enregistre comme les fichiers locaux (équivalent « image externe » Symfony).
     */
    public static String copyProductImageFromUrl(String urlString) throws IOException, InterruptedException {
        if (urlString == null || urlString.isBlank()) {
            throw new IOException("URL vide");
        }
        String u = urlString.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            throw new IOException("URL invalide (http ou https requis)");
        }
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(u))
            .timeout(Duration.ofSeconds(25))
            .GET()
            .build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() >= 400) {
            throw new IOException("Téléchargement impossible (HTTP " + resp.statusCode() + ")");
        }
        String ext = guessExtension(resp.headers().firstValue("content-type").orElse(""));
        Path tmp = Files.createTempFile("imgurl-", ext);
        try (InputStream in = resp.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            return copyProductImage(tmp.toFile());
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // —
            }
        }
    }

    private static String guessExtension(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if (ct.contains("png")) {
            return ".png";
        }
        if (ct.contains("webp")) {
            return ".webp";
        }
        if (ct.contains("gif")) {
            return ".gif";
        }
        return ".jpg";
    }
}
