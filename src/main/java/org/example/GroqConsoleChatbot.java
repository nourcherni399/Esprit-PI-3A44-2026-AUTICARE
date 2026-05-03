package org.example;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chatbot console Java Maven utilisant Groq API + option image Hugging Face.
 *
 * Variables d'environnement:
 * - GROQ_API_KEY (obligatoire)
 * - HUGGINGFACE_API_KEY (optionnel, pour /image)
 *
 * Commandes:
 * - /help
 * - /quit
 * - /reset
 * - /json on|off
 * - /image <prompt>
 */
public final class GroqConsoleChatbot {

    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama3-70b-8192";
    private static final String HF_IMAGE_MODEL = "runwayml/stable-diffusion-v1-5";
    private static final String HF_IMAGE_ENDPOINT = "https://api-inference.huggingface.co/models/" + HF_IMAGE_MODEL;
    private static final int MAX_MEMORY_MESSAGES = 14;

    private static final String SYSTEM_PROMPT = """
            Tu es un assistant professionnel pour prise de rendez-vous, orientation médicale et tâches administratives.
            Tu comprends:
            - le français standard
            - l'arabe dialectal en alphabet arabe
            - le mélange FR+AR dans la même phrase
            - les fautes d'orthographe et formulations approximatives

            Objectifs:
            1) Détecter l'intention utilisateur même avec fautes.
            2) Extraire si possible: date, heure, spécialité médicale.
            3) Si une information clé manque pour exécuter la demande, poser UNE question de clarification concise.
            4) Répondre avec ton professionnel, court et clair.

            IMPORTANT:
            - N'invente pas une date/heure/spécialité absente.
            - Si ambigu, pose une question ciblée.
            - Reste orienté contexte: rendez-vous, médecin, administratif.
            - Réponds en français clair; si l'utilisateur écrit en arabe, accepte et reformule utilement en français simple.

            FORMAT DE SORTIE STRICT:
            ASSISTANT_REPLY: <réponse finale utilisateur, concise et utile>
            STRUCTURED_JSON: {"intent":"","date":"","time":"","speciality":""}
            """;

    private static final List<Message> FEW_SHOT_MESSAGES = List.of(
            new Message("user", "slt nheb rdv ghodwa m3a dermato fel 10h"),
            new Message("assistant",
                    "ASSISTANT_REPLY: Très bien. Je peux vous aider à réserver un rendez-vous demain à 10h avec un dermatologue. Confirmez-vous la ville ou le cabinet souhaité ?\n"
                            + "STRUCTURED_JSON: {\"intent\":\"book_appointment\",\"date\":\"demain\",\"time\":\"10:00\",\"speciality\":\"dermatologue\"}"),
            new Message("user", "je veux docteur pour mon fils, je sais pas quelle specialite"),
            new Message("assistant",
                    "ASSISTANT_REPLY: D'accord. Pour bien vous orienter, quel est le principal symptôme de votre enfant ?\n"
                            + "STRUCTURED_JSON: {\"intent\":\"medical_orientation\",\"date\":\"\",\"time\":\"\",\"speciality\":\"\"}"),
            new Message("user", "بدي نبدل موعدي بعد غدوة"),
            new Message("assistant",
                    "ASSISTANT_REPLY: Bien noté. Vous souhaitez déplacer votre rendez-vous à après-demain. Quelle heure vous convient ?\n"
                            + "STRUCTURED_JSON: {\"intent\":\"reschedule_appointment\",\"date\":\"après-demain\",\"time\":\"\",\"speciality\":\"\"}")
    );

    private record Message(String role, String content) {}
    private record AssistantResult(String reply, String structuredJson) {}

    private GroqConsoleChatbot() {}

    public static void main(String[] args) {
        String groqApiKey = env("GROQ_API_KEY");
        if (groqApiKey.isBlank()) {
            System.err.println("Erreur: GROQ_API_KEY manquante.");
            System.err.println("PowerShell: $env:GROQ_API_KEY=\"votre_cle\"");
            return;
        }

        String hfApiKey = env("HUGGINGFACE_API_KEY");
        boolean jsonMode = false;
        List<Message> memory = new ArrayList<>();

        printWelcome();
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.print("\nVous> ");
                String input = console.readLine();
                if (input == null) {
                    break;
                }
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }

                if (isQuitCommand(input)) {
                    System.out.println("Assistant> Merci, à bientôt.");
                    break;
                }
                if ("/help".equalsIgnoreCase(input)) {
                    printHelp();
                    continue;
                }
                if ("/reset".equalsIgnoreCase(input)) {
                    memory.clear();
                    System.out.println("Assistant> Mémoire conversation effacée.");
                    continue;
                }
                if (input.toLowerCase(Locale.ROOT).startsWith("/json")) {
                    jsonMode = handleJsonModeCommand(input, jsonMode);
                    continue;
                }
                if (input.toLowerCase(Locale.ROOT).startsWith("/image ")) {
                    handleImageCommand(hfApiKey, input.substring(7).trim());
                    continue;
                }

                try {
                    AssistantResult result = askGroq(groqApiKey, memory, input);
                    memory.add(new Message("user", input));
                    memory.add(new Message("assistant", result.reply));
                    trimMemory(memory, MAX_MEMORY_MESSAGES);

                    System.out.println("Assistant> " + cleanDisplay(result.reply));
                    if (jsonMode) {
                        System.out.println("JSON> " + result.structuredJson);
                    }
                } catch (Exception e) {
                    System.err.println("Erreur chatbot: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur console: " + e.getMessage());
        }
    }

    private static AssistantResult askGroq(String apiKey, List<Message> memory, String userInput) throws Exception {
        List<Message> requestMessages = buildRequestMessages(memory, userInput);
        String payload = buildGroqPayload(requestMessages);
        String apiResponse = postJson(GROQ_ENDPOINT, payload, apiKey, 20000, 65000, "application/json");
        String assistantRaw = extractAssistantContentFromGroqResponse(apiResponse);
        if (assistantRaw.isBlank()) {
            throw new IllegalStateException("Réponse assistant vide.");
        }

        String reply = extractTaggedValue(assistantRaw, "ASSISTANT_REPLY");
        String structured = extractTaggedValue(assistantRaw, "STRUCTURED_JSON");
        if (reply == null || reply.isBlank()) {
            reply = assistantRaw.trim();
        }
        if (structured == null || structured.isBlank()) {
            structured = "{\"intent\":\"\",\"date\":\"\",\"time\":\"\",\"speciality\":\"\"}";
        }
        return new AssistantResult(reply.trim(), normalizeStructuredJson(structured));
    }

    private static List<Message> buildRequestMessages(List<Message> memory, String userInput) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", SYSTEM_PROMPT));
        messages.addAll(FEW_SHOT_MESSAGES);
        messages.addAll(memory);
        messages.add(new Message("user", userInput));
        return messages;
    }

    private static String buildGroqPayload(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(GROQ_MODEL).append("\",");
        sb.append("\"temperature\":0.2,");
        sb.append("\"max_tokens\":700,");
        sb.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"role\":\"").append(escapeJson(m.role())).append("\",");
            sb.append("\"content\":\"").append(escapeJson(m.content())).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void handleImageCommand(String hfApiKey, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            System.out.println("Usage: /image <prompt>");
            return;
        }
        if (hfApiKey.isBlank()) {
            System.err.println("HUGGINGFACE_API_KEY manquante.");
            return;
        }
        try {
            String dataUrl = generateImageWithHuggingFace(hfApiKey, prompt);
            if (dataUrl.isBlank()) {
                System.err.println("Échec génération image (réponse vide).");
                return;
            }
            int previewLength = Math.min(140, dataUrl.length());
            System.out.println("Image data URL (aperçu): " + dataUrl.substring(0, previewLength) + "...");
        } catch (Exception e) {
            System.err.println("Erreur image HF: " + e.getMessage());
        }
    }

    private static String generateImageWithHuggingFace(String apiKey, String prompt) throws Exception {
        String payload = "{\"inputs\":\"" + escapeJson(prompt) + "\"}";
        HttpResult result = postJsonForBytes(HF_IMAGE_ENDPOINT, payload, apiKey, 20000, 90000, "*/*");

        if (result.status / 100 == 2 && result.contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            String ct = result.contentType.split(";")[0].trim();
            return "data:" + ct + ";base64," + Base64.getEncoder().encodeToString(result.bodyBytes);
        }

        String bodyText = new String(result.bodyBytes, StandardCharsets.UTF_8);
        if (result.status / 100 != 2) {
            throw new IllegalStateException("HTTP " + result.status + " " + truncate(bodyText, 220));
        }
        String b64 = jsonStringValue(bodyText, "image");
        if (b64 == null || b64.isBlank()) {
            b64 = jsonStringValue(bodyText, "image_base64");
        }
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException("Réponse HF non exploitable.");
        }
        return "data:image/png;base64," + b64.trim();
    }

    private static String postJson(String endpoint, String payload, String apiKey,
                                   int connectTimeoutMs, int readTimeoutMs, String accept) throws Exception {
        HttpResult result = postJsonForBytes(endpoint, payload, apiKey, connectTimeoutMs, readTimeoutMs, accept);
        String body = new String(result.bodyBytes, StandardCharsets.UTF_8);
        if (result.status / 100 != 2) {
            throw new IllegalStateException("HTTP " + result.status + " " + truncate(body, 220));
        }
        return body;
    }

    private static HttpResult postJsonForBytes(String endpoint, String payload, String apiKey,
                                               int connectTimeoutMs, int readTimeoutMs, String accept) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", accept);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        String contentType = conn.getHeaderField("Content-Type");
        byte[] bodyBytes = readHttpBodyBytes(conn, status);
        return new HttpResult(status, contentType != null ? contentType : "", bodyBytes);
    }

    private static String extractAssistantContentFromGroqResponse(String json) {
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json != null ? json : "");
        if (!m.find()) {
            return "";
        }
        return unescapeJson(m.group(1)).trim();
    }

    private static String extractTaggedValue(String text, String tag) {
        if (text == null || tag == null) {
            return null;
        }
        Pattern p = Pattern.compile("(?s)" + Pattern.quote(tag) + "\\s*:\\s*(.*?)(?:\\n[A-Z_]+\\s*:|$)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        return m.group(1).trim();
    }

    private static String normalizeStructuredJson(String raw) {
        String intent = safe(jsonStringValue(raw, "intent"));
        String date = safe(jsonStringValue(raw, "date"));
        String time = safe(jsonStringValue(raw, "time"));
        String speciality = safe(jsonStringValue(raw, "speciality"));
        return "{\"intent\":\"" + escapeJson(intent) + "\","
                + "\"date\":\"" + escapeJson(date) + "\","
                + "\"time\":\"" + escapeJson(time) + "\","
                + "\"speciality\":\"" + escapeJson(speciality) + "\"}";
    }

    private static String jsonStringValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json != null ? json : "");
        if (!m.find()) {
            return "";
        }
        return unescapeJson(m.group(1)).trim();
    }

    private static String cleanDisplay(String text) {
        if (text == null) {
            return "";
        }
        String out = text.replaceAll("\\s+", " ").trim();
        if (out.startsWith("ASSISTANT_REPLY:")) {
            out = out.substring("ASSISTANT_REPLY:".length()).trim();
        }
        return out;
    }

    private static void trimMemory(List<Message> memory, int maxMessages) {
        while (memory.size() > maxMessages) {
            memory.remove(0);
        }
    }

    private static boolean isQuitCommand(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        return "/quit".equals(s) || "quit".equals(s) || "exit".equals(s);
    }

    private static boolean handleJsonModeCommand(String input, boolean currentMode) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.equals("/json on")) {
            System.out.println("Assistant> Mode JSON activé.");
            return true;
        }
        if (s.equals("/json off")) {
            System.out.println("Assistant> Mode JSON désactivé.");
            return false;
        }
        System.out.println("Assistant> Usage: /json on|off (état actuel: " + (currentMode ? "on" : "off") + ")");
        return currentMode;
    }

    private static byte[] readHttpBodyBytes(HttpURLConnection conn, int status) throws Exception {
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            return new byte[0];
        }
        try (InputStream input = is) {
            return input.readAllBytes();
        }
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return v == null ? "" : v.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
    }

    private static void printWelcome() {
        System.out.println("Chatbot Groq prêt.");
        System.out.println("Commandes: /help | /json on|off | /reset | /image <prompt> | /quit");
    }

    private static void printHelp() {
        System.out.println("Aide:");
        System.out.println("- Écrivez librement (français, arabe ou mixte).");
        System.out.println("- /json on  : affiche aussi la structure intent/date/time/speciality");
        System.out.println("- /json off : masque la structure JSON");
        System.out.println("- /reset    : efface la mémoire de conversation");
        System.out.println("- /image <prompt> : génère une image via Hugging Face");
        System.out.println("- /quit     : quitter");
    }

    private record HttpResult(int status, String contentType, byte[] bodyBytes) {}
}

