package org.example.services;

public class TTSService {

    private Process currentProcess;

    public Process getCurrentProcess() {
        return currentProcess;
    }

    public boolean isSpeaking() {
        return currentProcess != null && currentProcess.isAlive();
    }

    public void stop() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
        }
        currentProcess = null;
    }

    /**
     * Lit le texte avec la voix par défaut du système.
     */
    public void speak(String text) {
        speak(text, null);
    }

    /**
     * Lit le texte en essayant de sélectionner la voix Windows correspondant
     * au code de langue (ex: "fr", "en", "ar", "es", etc.).
     * Si aucune voix installée ne correspond, utilise la voix par défaut.
     *
     * @param text     Le texte à lire
     * @param langCode Le code ISO de la langue (peut être null pour la voix par défaut)
     */
    public void speak(String text, String langCode) {
        stop();
        if (text == null || text.isBlank()) return;

        // Échapper les apostrophes et guillemets pour PowerShell
        String escaped = text.replace("'", " ").replace("\"", " ").replace("\n", " ").replace("\r", " ");

        String script;
        if (langCode != null && !langCode.isBlank() && !"fr".equalsIgnoreCase(langCode)) {
            // Construire la culture Windows (ex: "en" -> "en-US", "ar" -> "ar-SA", etc.)
            String culture = toCultureTag(langCode);
            // Essayer de sélectionner une voix correspondant à la culture, sinon voix par défaut
            script =
                "Add-Type -AssemblyName System.Speech; " +
                "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$s.Rate = 0; " +
                "try { " +
                "  $voices = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq '" + langCode.toLowerCase() + "' }; " +
                "  if ($voices) { $s.SelectVoice($voices[0].VoiceInfo.Name) } " +
                "} catch {}; " +
                "$s.Speak('" + escaped + "')";
        } else {
            script =
                "Add-Type -AssemblyName System.Speech; " +
                "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$s.Rate = 0; " +
                "$s.Speak('" + escaped + "')";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", script
            );
            pb.redirectErrorStream(true);
            currentProcess = pb.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convertit un code ISO 639-1 en tag de culture Windows (BCP 47).
     */
    private static String toCultureTag(String langCode) {
        if (langCode == null) return "fr-FR";
        return switch (langCode.toLowerCase()) {
            case "en" -> "en-US";
            case "ar" -> "ar-SA";
            case "es" -> "es-ES";
            case "de" -> "de-DE";
            case "it" -> "it-IT";
            case "pt" -> "pt-PT";
            case "tr" -> "tr-TR";
            default   -> "fr-FR";
        };
    }
}
