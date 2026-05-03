package org.example.ui.product;

import java.util.function.Consumer;

/**
 * Options d’affichage pour {@link ProductAssistantPanel} (bloc compact dans un formulaire vs fenêtre modale type chatbot).
 */
public record AssistantPanelMode(
    boolean hideUnsplashSection,
    boolean wideChatLayout,
    boolean visualChatbotTheme,
    String subtitleOverride,
    String inputPromptOverride,
    String welcomeTextOverride,
    Consumer<String> onUserMessagePreview,
    Consumer<String> onAssistantReplyPreview,
    boolean showTrashInHeader
) {

    public static AssistantPanelMode embeddedDemandeForm() {
        return new AssistantPanelMode(
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            false
        );
    }

    public static AssistantPanelMode modalProductCreation(
        Consumer<String> onUserMessagePreview,
        Consumer<String> onAssistantReplyPreview
    ) {
        String welcome = """
            Bonjour ! Je suis l’assistant IA AutiCare.

            Je peux répondre à tous types de questions (produits, explications, rédaction, comparaison, technique).

            Si vous donnez un nom de produit, je peux aussi proposer une description puis l’ajuster avec vos retours.

            Posez simplement votre question.""";
        return new AssistantPanelMode(
            true,
            true,
            true,
            "Assistant IA général (Groq)",
            "",
            welcome,
            onUserMessagePreview,
            onAssistantReplyPreview,
            true
        );
    }
}
