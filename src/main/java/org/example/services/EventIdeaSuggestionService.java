package org.example.services;

import org.example.models.EventIdeaSuggestion;
import org.example.utils.MyDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class EventIdeaSuggestionService {

    public void add(EventIdeaSuggestion idea) throws SQLException {
        String sql = "INSERT INTO participant_event_ideas(" +
                "evenement_id, participant_id, description, theme_preference, format_preference, preferred_period, participant_comment, created_at" +
                ") VALUES(?,?,?,?,?,?,?,NOW())";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idea.getEvenementId());
            ps.setInt(2, idea.getParticipantId());
            ps.setString(3, idea.getDescription());
            ps.setString(4, idea.getThemePreference());
            ps.setString(5, idea.getFormatPreference());
            ps.setString(6, idea.getPreferredPeriod());
            ps.setString(7, idea.getParticipantComment());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    idea.setId(rs.getInt(1));
                }
            }
        }
    }

    public void update(EventIdeaSuggestion idea) throws SQLException {
        String sql = "UPDATE participant_event_ideas SET " +
                "evenement_id=?, description=?, theme_preference=?, format_preference=?, preferred_period=?, participant_comment=? " +
                "WHERE id=?";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, idea.getEvenementId());
            ps.setString(2, idea.getDescription());
            ps.setString(3, idea.getThemePreference());
            ps.setString(4, idea.getFormatPreference());
            ps.setString(5, idea.getPreferredPeriod());
            ps.setString(6, idea.getParticipantComment());
            ps.setInt(7, idea.getId());
            ps.executeUpdate();
        }
    }

    public List<EventIdeaSuggestion> findAll() throws SQLException {
        List<EventIdeaSuggestion> out = new ArrayList<>();
        String sql = "SELECT * FROM participant_event_ideas ORDER BY created_at DESC";
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    public List<EventIdeaSuggestion> findByEventAndParticipant(int eventId, int participantId) throws SQLException {
        List<EventIdeaSuggestion> out = new ArrayList<>();
        String sql = "SELECT * FROM participant_event_ideas WHERE evenement_id=? AND participant_id=? ORDER BY created_at DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setInt(2, participantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Optional<EventIdeaSuggestion> findLatestByParticipant(int participantId) throws SQLException {
        String sql = "SELECT * FROM participant_event_ideas WHERE participant_id=? ORDER BY created_at DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, participantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<EventIdeaSuggestion> findByParticipantForDay(int participantId, LocalDate day) throws SQLException {
        String sql = "SELECT * FROM participant_event_ideas WHERE participant_id=? AND DATE(created_at)=? " +
                "ORDER BY created_at DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setInt(1, participantId);
            ps.setDate(2, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Map<String, Long> countByTheme() throws SQLException {
        return countByColumn("theme_preference");
    }

    public Map<String, Long> countByFormat() throws SQLException {
        return countByColumn("format_preference");
    }

    public Map<String, Long> countByPeriod() throws SQLException {
        return countByColumn("preferred_period");
    }

    public Map<String, Long> countByThemeForDay(LocalDate day) throws SQLException {
        return countByColumnForDay("theme_preference", day);
    }

    public Map<String, Long> countByFormatForDay(LocalDate day) throws SQLException {
        return countByColumnForDay("format_preference", day);
    }

    public Map<String, Long> countByPeriodForDay(LocalDate day) throws SQLException {
        return countByColumnForDay("preferred_period", day);
    }

    private Map<String, Long> countByColumn(String column) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        String sql = "SELECT " + column + " AS k, COUNT(*) AS c FROM participant_event_ideas GROUP BY " + column + " ORDER BY c DESC";
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String key = rs.getString("k");
                if (key == null || key.isBlank()) {
                    key = "Non renseigné";
                }
                out.put(key.trim(), rs.getLong("c"));
            }
        }
        return out;
    }

    private Map<String, Long> countByColumnForDay(String column, LocalDate day) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        String sql = "SELECT " + column + " AS k, COUNT(*) AS c FROM participant_event_ideas " +
                "WHERE DATE(created_at)=? GROUP BY " + column + " ORDER BY c DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("k");
                    if (key == null || key.isBlank()) {
                        key = "Non renseigné";
                    }
                    out.put(key.trim(), rs.getLong("c"));
                }
            }
        }
        return out;
    }

    public List<EventIdeaSuggestion> findAllForDay(LocalDate day) throws SQLException {
        List<EventIdeaSuggestion> out = new ArrayList<>();
        String sql = "SELECT * FROM participant_event_ideas WHERE DATE(created_at)=? ORDER BY created_at DESC";
        try (PreparedStatement ps = MyDatabase.getConnection().prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public List<LocalDate> listCollectionDaysDesc() throws SQLException {
        List<LocalDate> out = new ArrayList<>();
        String sql = "SELECT DISTINCT DATE(created_at) AS d FROM participant_event_ideas ORDER BY d DESC";
        try (Statement st = MyDatabase.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Date d = rs.getDate("d");
                if (d != null) {
                    out.add(d.toLocalDate());
                }
            }
        }
        return out;
    }

    public List<IdeaCluster> clusterIdeasBySimilarity() throws SQLException {
        List<EventIdeaSuggestion> ideas = findAll();
        return clusterIdeasBySimilarity(ideas);
    }

    public List<IdeaCluster> clusterIdeasBySimilarityForDay(LocalDate day) throws SQLException {
        List<EventIdeaSuggestion> ideas = findAllForDay(day);
        return clusterIdeasBySimilarity(ideas);
    }

    private List<IdeaCluster> clusterIdeasBySimilarity(List<EventIdeaSuggestion> ideas) {
        List<IdeaCluster> clusters = new ArrayList<>();
        for (EventIdeaSuggestion idea : ideas) {
            String text = normalize(idea.getDescription());
            IdeaCluster best = null;
            double bestScore = 0.0;
            for (IdeaCluster c : clusters) {
                double score = jaccard(text, c.centroidNormalizedDescription);
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            if (best != null && bestScore >= 0.35) {
                best.items.add(idea);
                if (idea.getDescription() != null && idea.getDescription().length() > best.exampleDescription.length()) {
                    best.exampleDescription = idea.getDescription();
                    best.centroidNormalizedDescription = text;
                }
            } else {
                IdeaCluster c = new IdeaCluster();
                c.items.add(idea);
                c.exampleDescription = idea.getDescription() != null ? idea.getDescription() : "";
                c.centroidNormalizedDescription = text;
                clusters.add(c);
            }
        }
        return clusters;
    }

    public static class IdeaCluster {
        public final List<EventIdeaSuggestion> items = new ArrayList<>();
        public String exampleDescription = "";
        private String centroidNormalizedDescription = "";
    }

    private EventIdeaSuggestion map(ResultSet rs) throws SQLException {
        EventIdeaSuggestion x = new EventIdeaSuggestion();
        x.setId(rs.getInt("id"));
        x.setEvenementId(rs.getInt("evenement_id"));
        x.setParticipantId(rs.getInt("participant_id"));
        x.setDescription(rs.getString("description"));
        x.setThemePreference(rs.getString("theme_preference"));
        x.setFormatPreference(rs.getString("format_preference"));
        x.setPreferredPeriod(rs.getString("preferred_period"));
        x.setParticipantComment(rs.getString("participant_comment"));
        Timestamp ts = rs.getTimestamp("created_at");
        x.setCreatedAt(ts != null ? ts.toLocalDateTime() : LocalDateTime.now());
        return x;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double jaccard(String a, String b) {
        if (a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        String[] aa = a.split(" ");
        String[] bb = b.split(" ");
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (String w : aa) {
            if (!w.isBlank()) {
                seen.put(w, 1);
            }
        }
        int inter = 0;
        int union = seen.size();
        for (String w : bb) {
            if (w.isBlank()) {
                continue;
            }
            Integer had = seen.putIfAbsent(w, 2);
            if (had == null) {
                union++;
            } else if (had == 1) {
                inter++;
                seen.put(w, 3);
            }
        }
        return union == 0 ? 0.0 : ((double) inter / (double) union);
    }
}
