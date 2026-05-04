package org.example.utils;

/**
 * Etat UI de reinitialisation.
 *
 * <p>Le PIN est stocke/valide en base de donnees (table user), pas ici.</p>
 */
public final class PasswordRecoveryState {

    private static final long RESEND_COOLDOWN_MILLIS = 60_000L;
    private static String emailOrUsername = "";
    private static Integer verifiedUserId;
    private static long lastPinSentAtMillis;

    private PasswordRecoveryState() {}

    public static String getEmailOrUsername() {
        return emailOrUsername != null ? emailOrUsername : "";
    }

    public static void setEmailOrUsername(String value) {
        emailOrUsername = value != null ? value.trim() : "";
    }

    public static Integer getVerifiedUserId() {
        return verifiedUserId;
    }

    public static void setVerifiedUserId(Integer userId) {
        verifiedUserId = userId;
    }

    public static void markPinSentNow() {
        lastPinSentAtMillis = System.currentTimeMillis();
    }

    public static long getRemainingResendCooldownMillis() {
        long diff = System.currentTimeMillis() - lastPinSentAtMillis;
        long left = RESEND_COOLDOWN_MILLIS - diff;
        return Math.max(0L, left);
    }

    public static void clear() {
        emailOrUsername = "";
        verifiedUserId = null;
        lastPinSentAtMillis = 0L;
    }
}
