package org.example.models;

import java.time.LocalDateTime;

public class CommentaireReaction {
    private int id;
    private String type;
    private LocalDateTime createdAt;
    private int userId;
    private int commentaireId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getCommentaireId() { return commentaireId; }
    public void setCommentaireId(int commentaireId) { this.commentaireId = commentaireId; }
}
