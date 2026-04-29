package org.example.models;

import java.time.LocalDateTime;

public class EventIdeaSuggestion {
    private int id;
    private int evenementId;
    private int participantId;
    private String description;
    private String themePreference;
    private String formatPreference;
    private String preferredPeriod;
    private String participantComment;
    private LocalDateTime createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEvenementId() {
        return evenementId;
    }

    public void setEvenementId(int evenementId) {
        this.evenementId = evenementId;
    }

    public int getParticipantId() {
        return participantId;
    }

    public void setParticipantId(int participantId) {
        this.participantId = participantId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThemePreference() {
        return themePreference;
    }

    public void setThemePreference(String themePreference) {
        this.themePreference = themePreference;
    }

    public String getFormatPreference() {
        return formatPreference;
    }

    public void setFormatPreference(String formatPreference) {
        this.formatPreference = formatPreference;
    }

    public String getPreferredPeriod() {
        return preferredPeriod;
    }

    public void setPreferredPeriod(String preferredPeriod) {
        this.preferredPeriod = preferredPeriod;
    }

    public String getParticipantComment() {
        return participantComment;
    }

    public void setParticipantComment(String participantComment) {
        this.participantComment = participantComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
