package org.example.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordUtil {
    private PasswordUtil() {
    }

    /**
     * Vérifie un mot de passe : hash SHA-256 (comptes créés dans l’appli desktop) ou bcrypt Symfony ({@code $2a$}, {@code $2y$}, {@code $2b$}).
     */
    public static boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        String h = storedHash.trim();
        if (h.startsWith("$2a$") || h.startsWith("$2y$") || h.startsWith("$2b$")) {
            BCrypt.Result result = BCrypt.verifyer().verify(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    h.getBytes(StandardCharsets.UTF_8));
            return result.verified;
        }
        return hash(rawPassword).equals(h);
    }

    /** Hash bcrypt (nouveaux comptes stockés comme Symfony / PHP {@code password_hash}). */
    public static String hashBcrypt(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("password required");
        }
        return BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());
    }

    public static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }
}
