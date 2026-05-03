package org.example.services;

/**
 * Contexte du prompt système Groq pour l’assistant produit.
 */
public enum GroqAssistantContext {
    /** Formulaire « demander un produit » (rédaction de demande). */
    DEMANDE_PRODUIT,
    /** Catalogue boutique : aider à choisir, filtrer, comparer des usages. */
    CATALOGUE_BOUTIQUE,
    /**
     * Modale création produit : l’utilisateur donne un nom → l’assistant propose une description
     * → l’utilisateur valide (oui) ou demande des ajustements (non / précisions).
     */
    CREATION_FICHE_PRODUIT,
    /**
     * Assistant conversationnel général : répond à tous types de questions.
     */
    UNIVERSAL_ASSISTANT
}
