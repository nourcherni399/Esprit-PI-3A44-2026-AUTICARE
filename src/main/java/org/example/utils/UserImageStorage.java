package org.example.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Enregistre une image utilisateur sous {@code public/uploads/users/} comme Symfony
 * ({@code handleUserImageUpload}), et retourne le chemin relatif pour la colonne {@code image}.
 */
public final class UserImageStorage {

    private static final String RELATIVE_DIR = "uploads/users";

    private UserImageStorage() {
    }

    /**
     * Copie le fichier choisi vers {@code public/uploads/users/} avec un nom unique.
     *
     * @return chemin à stocker en BDD, ex. {@code uploads/users/a1b2c3d4.jpg}
     */
    public static String copyUserImage(File source) throws IOException {
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
