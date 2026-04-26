package org.example.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Copie une image de thématique vers {@code public/uploads/thematiques/} ; la BDD stocke le chemin relatif.
 */
public final class ThematiqueImageStorage {

    private static final String RELATIVE_DIR = "uploads/thematiques";

    private ThematiqueImageStorage() {
    }

    public static String copyThematiqueImage(File source) throws IOException {
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
}
