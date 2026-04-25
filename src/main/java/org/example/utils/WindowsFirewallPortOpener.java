package org.example.utils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/**
 * Ouvre automatiquement le port check-in dans le pare-feu Windows.
 * <p>
 * Best effort: si l'opération échoue (droits admin absents), l'application continue.
 */
public final class WindowsFirewallPortOpener {

    private WindowsFirewallPortOpener() {
    }

    public static void ensureCheckinPortOpen() {
        if (!isWindows()) {
            return;
        }
        int port = resolveCheckinPort();
        if (port <= 0 || port > 65535) {
            port = 8787;
        }
        String ruleName = "AutiCare QR Checkin " + port;

        try {
            if (firewallRuleExists(ruleName)) {
                return;
            }
            addFirewallRule(ruleName, port);
        } catch (Exception ex) {
            System.err.println("[AutiCare] Impossible d'ouvrir automatiquement le port firewall " + port
                    + " (droits admin requis): " + ex.getMessage());
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static int resolveCheckinPort() {
        String fromSys = System.getProperty("auticare.checkin.port");
        if (isNumeric(fromSys)) {
            return Integer.parseInt(fromSys.trim());
        }
        String fromEnv = System.getenv("AUTICARE_CHECKIN_PORT");
        if (isNumeric(fromEnv)) {
            return Integer.parseInt(fromEnv.trim());
        }
        String fromClasspath = readClasspathProperty("auticare.checkin.port");
        if (isNumeric(fromClasspath)) {
            return Integer.parseInt(fromClasspath.trim());
        }
        return 8787;
    }

    private static String readClasspathProperty(String key) {
        try (InputStream in = WindowsFirewallPortOpener.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in == null) {
                return null;
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean firewallRuleExists(String ruleName) throws Exception {
        Process p = new ProcessBuilder(
                "netsh",
                "advfirewall",
                "firewall",
                "show",
                "rule",
                "name=" + ruleName)
                .redirectErrorStream(true)
                .start();
        byte[] out = p.getInputStream().readAllBytes();
        int exit = p.waitFor();
        String txt = new String(out, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return exit == 0 && txt.contains("nom de la") && txt.contains(ruleName.toLowerCase(Locale.ROOT));
    }

    private static void addFirewallRule(String ruleName, int port) throws Exception {
        Process p = new ProcessBuilder(
                "netsh",
                "advfirewall",
                "firewall",
                "add",
                "rule",
                "name=" + ruleName,
                "dir=in",
                "action=allow",
                "protocol=TCP",
                "localport=" + port)
                .redirectErrorStream(true)
                .start();
        byte[] out = p.getInputStream().readAllBytes();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(new String(out, StandardCharsets.UTF_8));
        }
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (char c : value.trim().toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}

