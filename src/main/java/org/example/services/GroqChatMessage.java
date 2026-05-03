package org.example.services;

/**
 * Tour de conversation pour l’API Groq (rôles {@code user} ou {@code assistant}).
 */
public record GroqChatMessage(String role, String content) {
}
