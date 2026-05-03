package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.AppLanguage;
import org.example.models.ModuleCategorie;
import org.example.models.ModuleContent;
import org.example.models.ModuleNiveau;
import org.example.utils.LocalAiPropertiesFile;
import org.example.utils.MyDatabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ModuleQuizService {
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public QuizData getOrCreateQuizForModule(ModuleContent module, AppLanguage language) throws SQLException, IOException, InterruptedException {
        AppLanguage targetLanguage = language != null ? language : AppLanguage.FR;
        QuizData existing = findLatestQuizByModule(module.getId());
        if (existing != null
                && existing.questions().size() == 6
                && targetLanguage.code().equalsIgnoreCase(existing.languageCode())) {
            return existing;
        }

        QuizData generated = generateQuizWithGroq(module, targetLanguage);
        int quizId = insertQuiz(module.getId(), serializeQuestions(generated.questions(), generated.languageCode()));
        return new QuizData(quizId, module.getId(), generated.questions(), generated.languageCode());
    }

    public QuizAttemptResult submitAttempt(int userId, int moduleId, int quizId, List<Integer> answers, List<QuizQuestion> questions)
            throws SQLException, IOException {
        if (answers == null || questions == null || answers.size() != questions.size()) {
            throw new IOException("Réponses quiz invalides.");
        }
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) {
            if (answers.get(i) != null && answers.get(i) == questions.get(i).correctIndex()) {
                correct++;
            }
        }
        double scorePercent = questions.isEmpty() ? 0.0 : (100.0 * correct / questions.size());
        boolean passed = scorePercent >= 80.0;

        String answersJson = objectMapper.writeValueAsString(answers);
        insertAttempt(userId, moduleId, quizId, scorePercent, passed, answersJson);
        return new QuizAttemptResult(scorePercent, passed);
    }

    public boolean hasPassedQuizForModule(int userId, int moduleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `module_quiz_attempt` WHERE user_id=? AND module_id=? AND passed=1";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, moduleId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public double getBestScorePercent(int userId, int moduleId) throws SQLException {
        String sql = "SELECT MAX(score_percent) FROM `module_quiz_attempt` WHERE user_id=? AND module_id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, moduleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double score = rs.getDouble(1);
                return rs.wasNull() ? -1.0 : score;
            }
        }
        return -1.0;
    }

    public LevelProgressResult getLevelProgress(int userId, ModuleCategorie categorie, ModuleNiveau level) throws SQLException {
        String totalSql = "SELECT COUNT(*) FROM `module` WHERE categorie=? AND niveau=? AND is_published=1";
        int total = 0;
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(totalSql)) {
            ps.setString(1, categorie.name());
            ps.setString(2, level.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                total = rs.getInt(1);
            }
        }
        if (total <= 0) {
            return new LevelProgressResult(0, 0, true);
        }

        String passedSql = """
                SELECT COUNT(DISTINCT a.module_id)
                FROM module_quiz_attempt a
                JOIN module m ON m.id = a.module_id
                WHERE a.user_id=? AND a.passed=1 AND m.categorie=? AND m.niveau=? AND m.is_published=1
                """;
        int passed = 0;
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(passedSql)) {
            ps.setInt(1, userId);
            ps.setString(2, categorie.name());
            ps.setString(3, level.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                passed = rs.getInt(1);
            }
        }
        return new LevelProgressResult(total, passed, passed >= total);
    }

    private QuizData findLatestQuizByModule(int moduleId) throws SQLException {
        String sql = "SELECT id, questions_json FROM `module_quiz` WHERE module_id=? ORDER BY created_at DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                String json = rs.getString("questions_json");
                List<QuizQuestion> questions = parseQuestions(json);
                if (questions.size() == 6) {
                    String languageCode = parseLanguageCode(json);
                    return new QuizData(id, moduleId, questions, languageCode);
                }
            }
        } catch (Exception ignored) {
            // fallback to generation
        }
        return null;
    }

    private int insertQuiz(int moduleId, String questionsJson) throws SQLException {
        String sql = "INSERT INTO `module_quiz` (questions_json, created_at, module_id) VALUES (?, NOW(), ?)";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, questionsJson);
            ps.setInt(2, moduleId);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        String fallbackSql = "SELECT id FROM `module_quiz` WHERE module_id=? ORDER BY created_at DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(fallbackSql)) {
            ps.setInt(1, moduleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Impossible de créer le quiz.");
    }

    private void insertAttempt(int userId, int moduleId, int quizId, double scorePercent, boolean passed, String answersJson)
            throws SQLException {
        String sql = """
                INSERT INTO `module_quiz_attempt` (score_percent, passed, answers_json, completed_at, user_id, module_id, quiz_id)
                VALUES (?, ?, ?, NOW(), ?, ?, ?)
                """;
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setDouble(1, scorePercent);
            ps.setInt(2, passed ? 1 : 0);
            ps.setString(3, answersJson);
            ps.setInt(4, userId);
            ps.setInt(5, moduleId);
            ps.setInt(6, quizId);
            ps.executeUpdate();
        }
    }

    private QuizData generateQuizWithGroq(ModuleContent module, AppLanguage language) throws IOException, InterruptedException {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Clé Groq introuvable. Définissez GROQ_API_KEY ou .ai.local.properties");
        }
        String systemPrompt = """
                Tu génères des quiz pédagogiques TSA.
                Génère EXACTEMENT 6 questions à choix unique (4 options par question) basées sur le module fourni.
                Important:
                - Une seule bonne réponse par question.
                - Les questions doivent être claires et adaptées aux parents/accompagnants.
                - Langue de sortie obligatoire: %s (%s).
                - Retourne UNIQUEMENT du JSON valide (sans markdown, sans texte avant/après), sous ce format:
                {
                  "questions": [
                    {
                      "question": "Texte",
                      "options": ["A","B","C","D"],
                      "correctIndex": 0
                    }
                  ]
                }
                """.formatted(language.label(), language.code());
        String userPrompt = "Titre module: " + safe(module.getTitre(), 300)
                + "\nCatégorie: " + module.getCategorie()
                + "\nNiveau: " + (module.getNiveau() != null ? module.getNiveau().name() : "moyen")
                + "\nDescription: " + safe(module.getDescription(), 800)
                + "\nContenu:\n" + safe(module.getContenu(), 2800)
                + "\nLangue cible: " + language.label() + " (" + language.code() + ")";

        String payload = buildPayload(systemPrompt, userPrompt);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Erreur API Groq (" + response.statusCode() + "): " + safe(response.body(), 300));
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw new IOException("Réponse quiz IA vide.");
        }
        List<QuizQuestion> questions = parseQuestions(content);
        if (questions.size() != 6) {
            throw new IOException("Quiz IA invalide. Le quiz doit contenir 6 questions.");
        }
        return new QuizData(-1, module.getId(), questions, language.code());
    }

    private String buildPayload(String systemPrompt, String userPrompt) throws IOException {
        JsonNode payload = objectMapper.createObjectNode()
                .put("model", GROQ_MODEL)
                .put("temperature", 0.2)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("role", "system").put("content", systemPrompt))
                        .add(objectMapper.createObjectNode().put("role", "user").put("content", userPrompt)));
        return objectMapper.writeValueAsString(payload);
    }

    private List<QuizQuestion> parseQuestions(String jsonText) throws IOException {
        String raw = jsonText.trim();
        if (raw.startsWith("```")) {
            raw = raw.replaceAll("^```(?:json)?", "").replaceAll("```$", "").trim();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                root = objectMapper.readTree(raw.substring(start, end + 1));
            } else {
                throw new IOException("Quiz IA non lisible.");
            }
        }
        JsonNode arr = root.path("questions");
        if (!arr.isArray()) {
            throw new IOException("Quiz IA non conforme (questions manquantes).");
        }
        List<QuizQuestion> out = new ArrayList<>();
        for (JsonNode q : arr) {
            String question = q.path("question").asText("").trim();
            JsonNode optionsNode = q.path("options");
            int correctIndex = q.path("correctIndex").asInt(-1);
            if (question.isBlank() || !optionsNode.isArray() || optionsNode.size() != 4 || correctIndex < 0 || correctIndex > 3) {
                continue;
            }
            List<String> options = new ArrayList<>();
            for (JsonNode opt : optionsNode) {
                String text = opt.asText("").trim();
                options.add(text.isBlank() ? "Option" : text);
            }
            out.add(new QuizQuestion(question, options, correctIndex));
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    private String serializeQuestions(List<QuizQuestion> questions, String languageCode) throws IOException {
        var root = objectMapper.createObjectNode();
        root.put("languageCode", languageCode != null ? languageCode : "fr");
        var arr = objectMapper.createArrayNode();
        for (QuizQuestion q : questions) {
            var qNode = objectMapper.createObjectNode();
            qNode.put("question", q.question());
            var opts = objectMapper.createArrayNode();
            if (q.options() != null) {
                for (String opt : q.options()) {
                    opts.add(opt != null ? opt : "");
                }
            }
            qNode.set("options", opts);
            qNode.put("correctIndex", q.correctIndex());
            arr.add(qNode);
        }
        root.set("questions", arr);
        return objectMapper.writeValueAsString(root);
    }

    private String parseLanguageCode(String jsonText) {
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            String code = root.path("languageCode").asText("");
            return (code == null || code.isBlank()) ? "fr" : code.trim().toLowerCase();
        } catch (Exception ignored) {
            return "fr";
        }
    }

    private static String resolveApiKey() {
        String env = System.getenv("GROQ_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = LocalAiPropertiesFile.readProperty("groq.api.key");
        return fromFile != null && !fromFile.isBlank() ? fromFile : null;
    }

    private static String safe(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    public record QuizQuestion(String question, List<String> options, int correctIndex) {}
    public record QuizData(int quizId, int moduleId, List<QuizQuestion> questions, String languageCode) {}
    public record QuizAttemptResult(double scorePercent, boolean passed) {}
    public record LevelProgressResult(int totalModules, int passedModules, boolean complete) {}
}

