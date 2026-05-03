package org.example.ui.product;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.MainApp;
import org.example.elasticsearch.ElasticsearchCatalogService;
import org.example.models.Product;
import org.example.services.GroqAssistantContext;
import org.example.services.GroqChatCompletionService;
import org.example.services.GroqChatMessage;
import org.example.services.PexelsHit;
import org.example.services.PexelsPhotoSearchService;
import org.example.services.ProductService;
import org.example.services.SerpApiPriceSuggestionService;
import org.example.services.StockService;
import org.example.services.UnsplashHit;
import org.example.services.UnsplashPhotoSearchService;
import com.google.gson.Gson;
import org.example.services.DemandeProduitService;
import org.example.ui.product.ProductImagePlaceholder;
import org.example.utils.AppState;
import org.example.utils.ProductImageLoader;
import org.example.utils.ProductDescriptionSuggest;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat structuré (bulles) branché sur Groq, section Unsplash optionnelle, thème « chatbot » pour la modale création produit.
 */
public class ProductAssistantPanel extends VBox {
    private static final String CHATBOT_HEADER_AVATAR_FILE =
        "C:/Users/nourc/.cursor/projects/c-Users-nourc-OneDrive-Validation-java-Jeudi/assets/"
            + "c__Users_nourc_AppData_Roaming_Cursor_User_workspaceStorage_d40632f95559523a04fcca3b97efc9cf_images_"
            + "image-0f3506c6-b428-4f00-a2d3-2ff8a9398a50.png";

    private static final String BUBBLE_USER =
        "-fx-background-color: #6f855c; -fx-text-fill: #ffffff; -fx-background-radius: 16; "
            + "-fx-font-size: 13.5px; -fx-padding: 10 14 10 14;";
    private static final String BUBBLE_USER_CHATBOT =
        "-fx-background-color: #1d4ed8; -fx-text-fill: #ffffff; -fx-background-radius: 18; "
            + "-fx-font-size: 16px; -fx-font-weight: 700; -fx-padding: 12 16 12 16;";
    private static final String BUBBLE_ASSISTANT =
        "-fx-background-color: #ffffff; -fx-text-fill: #0f172a; -fx-background-radius: 18; "
            + "-fx-border-color: #d1d5db; -fx-border-radius: 18; -fx-border-width: 1.2; "
            + "-fx-font-size: 15.5px; -fx-font-weight: 600; -fx-padding: 12 16 12 16;";
    private static final String BUBBLE_ERROR =
        "-fx-background-color: #fef2f2; -fx-text-fill: #991b1b; -fx-background-radius: 12; "
            + "-fx-border-color: #fecaca; -fx-border-radius: 12; -fx-font-size: 12.5px; -fx-padding: 8 12 8 12;";

    private final GroqAssistantContext groqContext;
    private final AssistantPanelMode mode;
    private final HttpClient imageHttp = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    private final GroqChatCompletionService groqService = new GroqChatCompletionService();
    private final PexelsPhotoSearchService pexelsService = new PexelsPhotoSearchService();
    private final SerpApiPriceSuggestionService priceSuggestionService = new SerpApiPriceSuggestionService();
    private final UnsplashPhotoSearchService unsplashService = new UnsplashPhotoSearchService();
    private final ProductService productService = new ProductService();
    private final ElasticsearchCatalogService elasticsearch = ElasticsearchCatalogService.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "product-assistant");
        t.setDaemon(true);
        return t;
    });

    /**
     * Évite les faux positifs sur sous-chaîne (ex. « portable » ne doit pas matcher « table »).
     */
    private static boolean containsWholeWordIgnoreCase(String haystack, String word) {
        if (haystack == null || word == null || word.isBlank()) {
            return false;
        }
        String w = word.toLowerCase(Locale.ROOT);
        String h = haystack.toLowerCase(Locale.ROOT);
        return Pattern.compile("\\b" + Pattern.quote(w) + "\\b").matcher(h).find();
    }

    private final List<GroqChatMessage> chatHistory = new ArrayList<>();
    private String lastAssistantText = "";
    private ProductCreationWizard wizard;

    private final VBox messagesBox = new VBox(10);
    private final ScrollPane chatScroll = new ScrollPane(messagesBox);
    private final TextField chatInputField = new TextField();
    private final Button sendChatBtn = new Button("Envoyer");
    private final Button appendAssistantBtn = new Button();
    private final TextField unsplashKeywordField = new TextField();
    private final Button searchPhotosBtn = new Button("Rechercher");
    private final FlowPane unsplashResultsPane = new FlowPane();

    private final VBox configBanner = new VBox(6);
    private final Consumer<String> onAppendAssistantText;
    private record SuggestedBundle(String title, String imageUrl, double price, String description) {
    }

    public ProductAssistantPanel(GroqAssistantContext groqContext, String appendButtonLabel, Consumer<String> onAppendAssistantText) {
        this(groqContext, appendButtonLabel, onAppendAssistantText, AssistantPanelMode.embeddedDemandeForm());
    }

    public ProductAssistantPanel(
        GroqAssistantContext groqContext,
        String appendButtonLabel,
        Consumer<String> onAppendAssistantText,
        AssistantPanelMode mode
    ) {
        super(12);
        this.groqContext = groqContext;
        this.onAppendAssistantText = onAppendAssistantText;
        this.mode = mode;

        boolean wide = mode.wideChatLayout();
        setPadding(new Insets(wide ? 0 : 14));
        if (wide) {
            setMaxWidth(Double.MAX_VALUE);
            setMaxHeight(Double.MAX_VALUE);
            setMinWidth(400);
            setStyle("-fx-background-color: #ffffff;");
        } else {
            setMaxWidth(440);
            setMinWidth(320);
            setStyle(
                "-fx-background-color: #ffffff; -fx-background-radius: 16; -fx-border-radius: 16; "
                    + "-fx-border-color: #e5e0d8; -fx-border-width: 1; "
                    + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.1), 16, 0, 0, 3);"
            );
        }

        Label title = new Label(mode.visualChatbotTheme() ? "ASSISTANT AUTICARE" : "Assistant AutiCare");
        title.setStyle(mode.visualChatbotTheme()
            ? "-fx-font-size: 44px; -fx-font-weight: 900; -fx-text-fill: #1e3a8a; -fx-letter-spacing: 0.8px;"
            : "-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        String sub = mode.subtitleOverride();
        if (sub == null || sub.isBlank()) {
            sub = switch (groqContext) {
                case CATALOGUE_BOUTIQUE -> "Posez vos questions sur les produits du catalogue — réponses générées par Groq.";
                case CREATION_FICHE_PRODUIT -> "À partir du nom, proposition de description puis validation (oui / non + corrections).";
                case UNIVERSAL_ASSISTANT -> "Assistant IA général : réponses intelligentes pour tous types de questions.";
                default -> "Posez vos questions sur les produits — réponses générées par Groq.";
            };
        }
        if (mode.visualChatbotTheme()) {
            sub = (sub == null ? "" : sub).replace("(Groq)", "").trim();
            if (sub.toLowerCase(Locale.ROOT).contains("assistant ia général")) {
                sub = "";
            }
        }
        Label subtitle = new Label(sub);
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        boolean showSubtitle = sub != null && !sub.isBlank();
        subtitle.setVisible(showSubtitle);
        subtitle.setManaged(showSubtitle);
        VBox titleBlock = new VBox(4, title, subtitle);
        titleBlock.setAlignment(Pos.CENTER_LEFT);
        StackPane assistantAvatar = buildAssistantHeaderAvatar();

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Parent header;
        if (mode.showTrashInHeader()) {
            Button trashBtn = new Button("🗑");
            trashBtn.setMnemonicParsing(false);
            trashBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-size: 16px; "
                    + "-fx-cursor: hand; -fx-padding: 4 8;"
            );
            trashBtn.setOnAction(e -> resetConversation());
            HBox headerRow = new HBox(10, assistantAvatar, titleBlock, headerSpacer, trashBtn);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            header = headerRow;
        } else {
            HBox headerRow = new HBox(10, assistantAvatar, titleBlock);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            header = headerRow;
        }

        configBanner.setPadding(new Insets(8, 10, 8, 10));
        configBanner.setStyle("-fx-background-color: #fffbeb; -fx-background-radius: 10; -fx-border-color: #fde68a; -fx-border-radius: 10;");

        messagesBox.setPadding(new Insets(4, 6, 4, 6));
        chatScroll.setFitToWidth(true);
        chatScroll.setMinViewportHeight(wide ? 280 : 260);
        chatScroll.setPrefViewportHeight(wide ? 420 : 300);
        if (wide) {
            chatScroll.setMaxHeight(Double.MAX_VALUE);
        } else {
            chatScroll.setMaxHeight(380);
        }
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        messagesBox.setStyle(mode.visualChatbotTheme()
            ? "-fx-background-color: #ffffff; -fx-background-radius: 16;"
            : "-fx-background-color: #f8fafc; -fx-background-radius: 12;");
        VBox.setVgrow(chatScroll, wide ? Priority.ALWAYS : Priority.NEVER);

        String prompt = mode.inputPromptOverride();
        chatInputField.setPromptText(prompt != null && !prompt.isBlank()
            ? prompt
            : (mode.visualChatbotTheme() ? "Tapez votre message ici..." : "Écrivez votre question…"));
        setupInputClipboardMenu(chatInputField);
        if (mode.visualChatbotTheme()) {
            chatInputField.setPrefHeight(56);
            chatInputField.setStyle(
                "-fx-font-size: 16px; -fx-background-radius: 28; -fx-border-radius: 28; "
                    + "-fx-border-color: #94a3b8; -fx-border-width: 1.2; -fx-padding: 0 18;"
            );
        } else {
            chatInputField.setPrefHeight(36);
        }

        sendChatBtn.setMnemonicParsing(false);
        if (mode.visualChatbotTheme()) {
            sendChatBtn.setText("➤");
            sendChatBtn.setStyle(
                "-fx-background-color: #1e40af; -fx-text-fill: white; -fx-font-weight: 900; "
                    + "-fx-font-size: 18px; -fx-background-radius: 999; -fx-padding: 0; -fx-cursor: hand;"
            );
        } else {
            sendChatBtn.setStyle(
                "-fx-background-color: #5c6d4a; -fx-text-fill: white; -fx-font-weight: 700; "
                    + "-fx-background-radius: 10; -fx-padding: 10 18 10 18; -fx-cursor: hand;"
            );
        }
        Label inputGuide = new Label("Saisie : nom du produit, ou oui/non + précisions.");
        inputGuide.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        inputGuide.setVisible(false);
        inputGuide.setManaged(false);

        HBox inputRow = new HBox(10, chatInputField, sendChatBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(chatInputField, Priority.ALWAYS);
        if (mode.visualChatbotTheme()) {
            sendChatBtn.setMinSize(56, 56);
            sendChatBtn.setMaxSize(56, 56);
        }

        appendAssistantBtn.setMnemonicParsing(false);
        appendAssistantBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #5c6d4a; -fx-underline: true; -fx-cursor: hand;");
        boolean showAppend = onAppendAssistantText != null && appendButtonLabel != null && !appendButtonLabel.isBlank();
        appendAssistantBtn.setText(showAppend ? appendButtonLabel : "");
        appendAssistantBtn.setVisible(showAppend);
        appendAssistantBtn.setManaged(showAppend);
        appendAssistantBtn.setDisable(true);

        unsplashKeywordField.setPromptText("Mots-clés pour des visuels (Unsplash)…");
        searchPhotosBtn.setMnemonicParsing(false);
        searchPhotosBtn.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-background-radius: 8; -fx-cursor: hand;");
        HBox unsRow = new HBox(8, unsplashKeywordField, searchPhotosBtn);
        HBox.setHgrow(unsplashKeywordField, Priority.ALWAYS);
        unsplashResultsPane.setHgap(8);
        unsplashResultsPane.setVgap(8);
        unsplashResultsPane.setPrefWrapLength(360);
        ScrollPane unsScroll = new ScrollPane(unsplashResultsPane);
        unsScroll.setFitToWidth(true);
        unsScroll.setMaxHeight(160);
        unsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox unsplashBody = new VBox(10, unsRow, unsScroll);
        TitledPane unsplashPane = new TitledPane("Références photos (Unsplash)", unsplashBody);
        unsplashPane.setExpanded(false);
        unsplashPane.setAnimated(true);

        getChildren().add(header);
        getChildren().add(configBanner);
        getChildren().add(chatScroll);
        getChildren().add(inputGuide);
        getChildren().add(inputRow);
        getChildren().add(appendAssistantBtn);
        if (!mode.hideUnsplashSection()) {
            getChildren().add(unsplashPane);
        }

        sendChatBtn.setOnAction(e -> onSendChat());
        appendAssistantBtn.setOnAction(e -> onAppend());
        searchPhotosBtn.setOnAction(e -> onSearchUnsplash());
        chatInputField.setOnAction(e -> onSendChat());

        refreshConfigBanner();
        appendAssistantBubble(welcomeText(), false);
        unsplashResultsPane.getChildren().clear();
    }

    /** Efface l’historique visuel et API, relance le message d’accueil. */
    public void resetConversation() {
        messagesBox.getChildren().clear();
        chatHistory.clear();
        lastAssistantText = "";
        wizard = null;
        unsplashResultsPane.getChildren().clear();
        appendAssistantBtn.setDisable(true);
        appendAssistantBubble(welcomeText(), false);
        chatInputField.clear();
    }

    public void shutdown() {
        executor.shutdown();
    }

    private String welcomeText() {
        String over = mode.welcomeTextOverride();
        if (over != null && !over.isBlank()) {
            return over.trim();
        }
        if (groqContext == GroqAssistantContext.CATALOGUE_BOUTIQUE) {
            return "Bonjour ! Je suis là pour vous aider à explorer le catalogue : comparer des usages, affiner une recherche, "
                + "ou suggérer des idées de prix indicatives en DT. Qu’est-ce que vous cherchez aujourd’hui ?";
        }
        if (groqContext == GroqAssistantContext.CREATION_FICHE_PRODUIT) {
            return "Bonjour ! Indiquez le nom du produit : je vous proposerai une description structurée, puis vous me direz si elle vous convient (oui) ou quoi modifier (non + précisions).";
        }
        if (groqContext == GroqAssistantContext.UNIVERSAL_ASSISTANT) {
            return "Bonjour ! Je suis votre assistant IA général. Posez n’importe quelle question (produit, technique, explication, rédaction, comparaison) et je vous réponds de manière claire et structurée.";
        }
        return "Bonjour ! Décrivez le produit souhaité ou posez vos questions pour rédiger une demande claire pour l’équipe AutiCare.";
    }

    private void refreshConfigBanner() {
        configBanner.getChildren().clear();
        boolean groqOk = GroqChatCompletionService.hasApiKeyConfigured();
        boolean unsOk = UnsplashPhotoSearchService.hasAccessKeyConfigured();
        boolean needUnsplashInUi = !mode.hideUnsplashSection();
        if (groqOk && (!needUnsplashInUi || unsOk)) {
            configBanner.setVisible(false);
            configBanner.setManaged(false);
            sendChatBtn.setDisable(false);
            searchPhotosBtn.setDisable(false);
            return;
        }
        configBanner.setVisible(true);
        configBanner.setManaged(true);
        if (!groqOk) {
            Label l = new Label(
                "Pour activer le chat : à la racine du projet Java (à côté de pom.xml), créez le fichier assistant-local.properties "
                    + "avec la ligne groq.api.key=votre_clé (ou GROQ_API_KEY / CHAT_API_KEY dans .env.local), "
                    + "ou définissez la variable Windows GROQ_API_KEY. Redémarrez l’application ensuite."
            );
            l.setWrapText(true);
            l.setStyle("-fx-text-fill: #92400e; -fx-font-size: 11.5px;");
            configBanner.getChildren().add(l);
            sendChatBtn.setDisable(true);
        } else {
            sendChatBtn.setDisable(false);
        }
        if (needUnsplashInUi && !unsOk) {
            Label u = new Label(
                "Photos : dans assistant-local.properties ajoutez unsplash.access.key=… (ou variable Windows UNSPLASH_ACCESS_KEY)."
            );
            u.setWrapText(true);
            u.setStyle("-fx-text-fill: #92400e; -fx-font-size: 11.5px;");
            configBanner.getChildren().add(u);
            searchPhotosBtn.setDisable(true);
        } else if (needUnsplashInUi) {
            searchPhotosBtn.setDisable(false);
        }
    }

    private void scrollChatToBottom() {
        Platform.runLater(() -> {
            chatScroll.layout();
            chatScroll.setVvalue(1.0);
        });
    }

    private String bubbleUserStyle() {
        return mode.visualChatbotTheme() ? BUBBLE_USER_CHATBOT : BUBBLE_USER;
    }

    private static Label avatarChip(String text, boolean compact) {
        Label l = new Label(text);
        l.setMnemonicParsing(false);
        if (compact) {
            l.setMinSize(36, 36);
            l.setMaxSize(36, 36);
        } else {
            l.setMinSize(28, 28);
            l.setMaxSize(28, 28);
        }
        l.setAlignment(Pos.CENTER);
        l.setWrapText(false);
        l.setStyle(
            "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: 800; "
                + "-fx-background-radius: 18; -fx-padding: 0;"
        );
        return l;
    }

    private void appendUserBubble(String text) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 420 : 300);
        bubble.setStyle(bubbleUserStyle());
        makeBubbleCopyable(bubble, text);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row;
        if (mode.visualChatbotTheme()) {
            Label vous = avatarChip("Vous", true);
            row = new HBox(8, spacer, bubble, vous);
        } else {
            row = new HBox(spacer, bubble);
        }
        row.setAlignment(Pos.CENTER_RIGHT);
        messagesBox.getChildren().add(row);
        if (mode.onUserMessagePreview() != null) {
            mode.onUserMessagePreview().accept(text);
        }
        scrollChatToBottom();
    }

    private void appendAssistantBubble(String text, boolean addToApiHistory) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 520 : 320);
        bubble.setStyle(BUBBLE_ASSISTANT);
        makeBubbleCopyable(bubble, text);
        HBox row;
        if (mode.visualChatbotTheme()) {
            Label av = avatarChip("A", false);
            row = new HBox(8, av, bubble);
        } else {
            row = new HBox(bubble);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        if (addToApiHistory) {
            chatHistory.add(new GroqChatMessage("assistant", text));
        }
        if (addToApiHistory && mode.onAssistantReplyPreview() != null) {
            mode.onAssistantReplyPreview().accept(text);
        }
        scrollChatToBottom();
    }

    private void appendErrorBubble(String text) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(320);
        bubble.setStyle(BUBBLE_ERROR);
        makeBubbleCopyable(bubble, text);
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        scrollChatToBottom();
    }

    private static void makeBubbleCopyable(Label bubble, String text) {
        final String copyText = text == null ? "" : text;
        ContextMenu menu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copier");
        copyItem.setOnAction(e -> copyToClipboard(copyText));
        menu.getItems().add(copyItem);
        bubble.setContextMenu(menu);
        bubble.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) {
                copyToClipboard(copyText);
            }
        });
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void setupInputClipboardMenu(TextField field) {
        if (field == null) {
            return;
        }
        MenuItem cutItem = new MenuItem("Couper");
        cutItem.setOnAction(e -> field.cut());
        MenuItem copyItem = new MenuItem("Copier");
        copyItem.setOnAction(e -> field.copy());
        MenuItem pasteItem = new MenuItem("Coller");
        pasteItem.setOnAction(e -> field.paste());
        MenuItem selectAllItem = new MenuItem("Tout sélectionner");
        selectAllItem.setOnAction(e -> field.selectAll());
        ContextMenu menu = new ContextMenu(cutItem, copyItem, pasteItem, selectAllItem);
        field.setContextMenu(menu);
    }

    private void appendPhotoSuggestionsBubble(List<PexelsHit> hits, List<Double> estimatedPrices, Consumer<Double> onEstimatedPriceSelected) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        Label title = new Label("Suggestions photos produit (Pexels)");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");

        FlowPane grid = new FlowPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPrefWrapLength(500);
        for (int idx = 0; idx < hits.size(); idx++) {
            PexelsHit h = hits.get(idx);
            final int cardIdx = idx;
            final String selected = h.largeUrl() != null && !h.largeUrl().isBlank() ? h.largeUrl() : h.previewUrl();
            ImageView iv = new ImageView();
            iv.setFitWidth(138);
            iv.setFitHeight(96);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            Label noPreview = new Label("Aperçu indisponible");
            noPreview.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            StackPane previewPane = new StackPane(noPreview, iv);
            previewPane.setMinSize(138, 96);
            previewPane.setMaxSize(138, 96);
            previewPane.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8;");
            loadPreviewInto(iv, noPreview, h.previewUrl(), 138, 96);
            Label cap = new Label(
                h.photographer() != null && !h.photographer().isBlank()
                    ? "Photo: " + h.photographer()
                    : "Voir la photo"
            );
            cap.setWrapText(true);
            cap.setMaxWidth(138);
            cap.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748b;");
            Label priceLbl = new Label(formatEstimatedPrice(estimatedPrices, idx));
            priceLbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");
            Label zoomHint = new Label("Appuyez pour agrandir");
            zoomHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            Button selectBtn = new Button("Sélectionner");
            selectBtn.setMnemonicParsing(false);
            selectBtn.setVisible(false);
            selectBtn.setManaged(false);
            selectBtn.setStyle(
                "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 700; "
                    + "-fx-background-radius: 8; -fx-padding: 6 10; -fx-cursor: hand;"
            );

            VBox card = new VBox(6, previewPane, priceLbl, cap, zoomHint, selectBtn);
            card.setStyle(
                "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #e2e8f0; "
                    + "-fx-border-radius: 10; -fx-padding: 6; -fx-cursor: hand;"
            );
            previewPane.setOnMouseClicked(e -> showImagePreviewInApp(selected, "Aperçu photo produit"));
            card.setOnMouseClicked(e -> {
                markSelectedCard(grid, card);
                if (e.getClickCount() >= 2) {
                    showImagePreviewInApp(selected, "Aperçu photo produit");
                }
            });
            selectBtn.setOnAction(e -> {
                if (wizard != null) {
                    wizard.selectedImageUrl = selected;
                }
                Double selectedPrice = (estimatedPrices != null && cardIdx >= 0 && cardIdx < estimatedPrices.size())
                    ? estimatedPrices.get(cardIdx)
                    : null;
                if (selectedPrice == null || selectedPrice <= 0) {
                    appendAssistantBubble("Cette carte n’a pas de prix estimé exploitable. Choisissez une autre suggestion.", false);
                    e.consume();
                    return;
                }
                if (onEstimatedPriceSelected != null) {
                    onEstimatedPriceSelected.accept(selectedPrice);
                }
                e.consume();
            });
            grid.getChildren().add(card);
        }

        VBox bubble = new VBox(8, title, grid);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 540 : 360);
        bubble.setStyle(
            "-fx-background-color: #f8fafc; -fx-background-radius: 16; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 16; -fx-border-width: 1; "
                + "-fx-padding: 10 12 10 12;"
        );
        HBox row;
        if (mode.visualChatbotTheme()) {
            Label av = avatarChip("A", false);
            row = new HBox(8, av, bubble);
        } else {
            row = new HBox(bubble);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        scrollChatToBottom();
    }

    private void appendDescriptionOptionsBubble(
        List<String> aiOptions,
        String userDescription,
        Consumer<String> onSelectDescription
    ) {
        if (onSelectDescription == null) {
            return;
        }
        List<String> options = new ArrayList<>();
        if (aiOptions != null) {
            for (String x : aiOptions) {
                String t = x == null ? "" : x.trim();
                if (t.isBlank()) {
                    continue;
                }
                boolean exists = false;
                for (String cur : options) {
                    if (cur.equalsIgnoreCase(t)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    options.add(t);
                }
            }
        }
        String user = userDescription == null ? "" : userDescription.trim();
        if (!user.isBlank()) {
            options.add(user);
        }
        if (options.isEmpty()) {
            return;
        }

        Label title = new Label("Choisissez une description (clic)");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");

        FlowPane grid = new FlowPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPrefWrapLength(560);

        int aiCount = aiOptions == null ? 0 : aiOptions.size();
        for (int i = 0; i < options.size(); i++) {
            String text = options.get(i);
            boolean isUserOption = !user.isBlank() && i == options.size() - 1;
            String labelText = isUserOption
                ? "Votre description"
                : ("Description IA " + Math.min(i + 1, Math.max(aiCount, 1)));

            Label head = new Label(labelText);
            head.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 800; -fx-text-fill: #1e3a8a;");
            Label body = new Label(truncate(text, 220));
            body.setWrapText(true);
            body.setMaxWidth(170);
            body.setStyle("-fx-font-size: 11px; -fx-text-fill: #334155;");
            Label hint = new Label("Cliquer pour sélectionner");
            hint.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            Button selectBtn = new Button("Sélectionner");
            selectBtn.setMnemonicParsing(false);
            selectBtn.setVisible(false);
            selectBtn.setManaged(false);
            selectBtn.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; "
                    + "-fx-background-radius: 8; -fx-padding: 6 10; -fx-cursor: hand;"
            );

            VBox card = new VBox(6, head, body, hint, selectBtn);
            card.setMinWidth(182);
            card.setMaxWidth(182);
            card.setUserData(text);
            card.setStyle(
                "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #e2e8f0; "
                    + "-fx-border-radius: 10; -fx-padding: 8; -fx-cursor: hand;"
            );
            card.setOnMouseClicked(e -> {
                markSelectedDescriptionCard(grid, card);
            });
            selectBtn.setOnAction(e -> {
                onSelectDescription.accept(text);
                e.consume();
            });
            grid.getChildren().add(card);
        }

        VBox bubble = new VBox(8, title, grid);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 580 : 370);
        bubble.setStyle(
            "-fx-background-color: #f8fafc; -fx-background-radius: 16; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 16; -fx-border-width: 1; "
                + "-fx-padding: 10 12 10 12;"
        );
        HBox row = mode.visualChatbotTheme()
            ? new HBox(8, avatarChip("A", false), bubble)
            : new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        scrollChatToBottom();
    }

    private void appendProductReviewCard(
        String nom,
        String categorieLabel,
        String description,
        String prixText,
        String imageUrl,
        Runnable onSubmit,
        Runnable onEdit
    ) {
        Label topTag = new Label("Fiche prête");
        topTag.setStyle(
            "-fx-font-size: 10.5px; -fx-font-weight: 800; -fx-text-fill: #166534; "
                + "-fx-background-color: #dcfce7; -fx-background-radius: 999; -fx-padding: 4 10;"
        );
        Label title = new Label(safe(nom));
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        Label catChip = new Label("Catégorie : " + safe(categorieLabel));
        catChip.setStyle(
            "-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #1e3a8a; "
                + "-fx-background-color: #dbeafe; -fx-background-radius: 999; -fx-padding: 5 10;"
        );
        Label priceChip = new Label("Prix : " + safe(prixText) + " DT");
        priceChip.setStyle(
            "-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #1d4ed8; "
                + "-fx-background-color: #eff6ff; -fx-background-radius: 999; -fx-padding: 5 10;"
        );
        HBox chips = new HBox(8, catChip, priceChip);

        ImageView iv = new ImageView();
        iv.setFitWidth(360);
        iv.setFitHeight(240);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        Label noPreview = new Label("Aperçu indisponible");
        noPreview.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        StackPane previewPane = new StackPane(noPreview, iv);
        previewPane.setMinSize(360, 240);
        previewPane.setMaxSize(360, 240);
        previewPane.setStyle(
            "-fx-background-color: #f8fafc; -fx-background-radius: 12; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 12;"
        );
        loadPreviewInto(iv, noPreview, imageUrl, 360, 240);
        previewPane.setOnMouseClicked(e -> showImagePreviewInApp(imageUrl, "Aperçu fiche produit"));

        Label descTitle = new Label("Description");
        descTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label descLbl = new Label(safe(description));
        descLbl.setWrapText(true);
        descLbl.setMaxWidth(620);
        descLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #334155; -fx-line-spacing: 2;");
        VBox descBox = new VBox(6, descTitle, descLbl);
        descBox.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 12; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-padding: 12;"
        );

        Button submitBtn = new Button("Envoyer la fiche");
        submitBtn.setMnemonicParsing(false);
        submitBtn.setStyle(
            "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 700; "
                + "-fx-background-radius: 10; -fx-padding: 10 16; -fx-cursor: hand;"
        );
        submitBtn.setOnAction(e -> {
            if (onSubmit != null) {
                onSubmit.run();
            }
        });

        Button editBtn = new Button("Changer quelque chose");
        editBtn.setMnemonicParsing(false);
        editBtn.setStyle(
            "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; "
                + "-fx-background-radius: 10; -fx-padding: 10 16; -fx-cursor: hand;"
        );
        editBtn.setOnAction(e -> {
            if (onEdit != null) {
                onEdit.run();
            }
        });

        HBox actions = new HBox(12, submitBtn, editBtn);
        VBox header = new VBox(8, topTag, title, chips);
        VBox rightCol = new VBox(12, descBox, actions);
        HBox content = new HBox(16, previewPane, rightCol);
        HBox.setHgrow(rightCol, Priority.ALWAYS);
        VBox bubble = new VBox(14, header, content);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 940 : 500);
        bubble.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f8fafc 100%); "
                + "-fx-background-radius: 18; -fx-border-color: #cbd5e1; -fx-border-radius: 18; "
                + "-fx-border-width: 1; -fx-padding: 14 16 14 16;"
        );
        HBox row = mode.visualChatbotTheme()
            ? new HBox(8, avatarChip("A", false), bubble)
            : new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        scrollChatToBottom();
    }

    private void appendIntegratedSuggestionCardsBubble(
        List<SuggestedBundle> bundles,
        Consumer<SuggestedBundle> onSelect
    ) {
        if (bundles == null || bundles.isEmpty()) {
            return;
        }
        Label title = new Label("Suggestions complètes (photo + prix + description)");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");

        FlowPane grid = new FlowPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPrefWrapLength(620);

        for (SuggestedBundle b : bundles) {
            ImageView iv = new ImageView();
            iv.setFitWidth(160);
            iv.setFitHeight(100);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            Label noPreview = new Label("Aperçu indisponible");
            noPreview.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            StackPane previewPane = new StackPane(noPreview, iv);
            previewPane.setMinSize(160, 100);
            previewPane.setMaxSize(160, 100);
            previewPane.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8;");
            loadPreviewInto(iv, noPreview, b.imageUrl(), 160, 100);

            Label t = new Label(truncate(b.title(), 52));
            t.setWrapText(true);
            t.setMaxWidth(160);
            t.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");
            Label p = new Label(String.format(Locale.FRENCH, "Prix estimé: %.2f DT", b.price()));
            p.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");
            Label d = new Label(truncate(b.description(), 170));
            d.setWrapText(true);
            d.setMaxWidth(160);
            d.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #334155;");

            Button selectBtn = new Button("Sélectionner");
            selectBtn.setMnemonicParsing(false);
            selectBtn.setStyle(
                "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 700; "
                    + "-fx-background-radius: 8; -fx-padding: 6 10; -fx-cursor: hand;"
            );
            selectBtn.setOnAction(e -> {
                if (onSelect != null) {
                    onSelect.accept(b);
                }
            });

            HBox actions = new HBox(8, selectBtn);
            VBox card = new VBox(6, previewPane, t, p, d, actions);
            card.setStyle(
                "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #e2e8f0; "
                    + "-fx-border-radius: 10; -fx-padding: 8;"
            );
            previewPane.setOnMouseClicked(e -> showImagePreviewInApp(b.imageUrl(), "Aperçu suggestion"));
            grid.getChildren().add(card);
        }

        VBox bubble = new VBox(8, title, grid);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 680 : 380);
        bubble.setStyle(
            "-fx-background-color: #f8fafc; -fx-background-radius: 16; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 16; -fx-border-width: 1; "
                + "-fx-padding: 10 12 10 12;"
        );
        HBox row = mode.visualChatbotTheme()
            ? new HBox(8, avatarChip("A", false), bubble)
            : new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        scrollChatToBottom();
    }

    private void markSelectedCard(FlowPane grid, VBox selected) {
        for (var n : grid.getChildren()) {
            if (n instanceof VBox c) {
                c.setStyle(
                    "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #e2e8f0; "
                        + "-fx-border-radius: 10; -fx-padding: 6; -fx-cursor: hand;"
                );
                for (var child : c.getChildren()) {
                    if (child instanceof Button b && "Sélectionner".equals(b.getText())) {
                        b.setVisible(false);
                        b.setManaged(false);
                    }
                }
            }
        }
        selected.setStyle(
            "-fx-background-color: #f0fdf4; -fx-background-radius: 10; -fx-border-color: #22c55e; "
                + "-fx-border-radius: 10; -fx-border-width: 2; -fx-padding: 5; -fx-cursor: hand;"
        );
        for (var child : selected.getChildren()) {
            if (child instanceof Button b && "Sélectionner".equals(b.getText())) {
                b.setVisible(true);
                b.setManaged(true);
            }
        }
    }

    private void markSelectedDescriptionCard(FlowPane grid, VBox selected) {
        for (var n : grid.getChildren()) {
            if (n instanceof VBox c) {
                c.setStyle(
                    "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #e2e8f0; "
                        + "-fx-border-radius: 10; -fx-padding: 8; -fx-cursor: hand;"
                );
                for (var child : c.getChildren()) {
                    if (child instanceof Label lbl && "-fx-font-size: 11px; -fx-text-fill: #334155;".equals(lbl.getStyle())) {
                        Object full = c.getUserData();
                        String fullText = full == null ? "" : full.toString();
                        lbl.setText(truncate(fullText, 220));
                    }
                    if (child instanceof Button b && "Sélectionner".equals(b.getText())) {
                        b.setVisible(false);
                        b.setManaged(false);
                    }
                }
            }
        }
        selected.setStyle(
            "-fx-background-color: #eff6ff; -fx-background-radius: 10; -fx-border-color: #2563eb; "
                + "-fx-border-radius: 10; -fx-border-width: 2; -fx-padding: 7; -fx-cursor: hand;"
        );
        for (var child : selected.getChildren()) {
            if (child instanceof Label lbl && "-fx-font-size: 11px; -fx-text-fill: #334155;".equals(lbl.getStyle())) {
                Object full = selected.getUserData();
                lbl.setText(full == null ? "" : full.toString());
            }
            if (child instanceof Button b && "Sélectionner".equals(b.getText())) {
                b.setVisible(true);
                b.setManaged(true);
            }
        }
    }

    private static String formatEstimatedPrice(List<Double> estimatedPrices, int idx) {
        if (estimatedPrices == null || idx < 0 || idx >= estimatedPrices.size()) {
            return "Prix estimé: —";
        }
        Double p = estimatedPrices.get(idx);
        if (p == null || p <= 0) {
            return "Prix estimé: —";
        }
        return String.format(Locale.FRENCH, "Prix estimé: %.2f DT", p);
    }

    private void appendMarketOptionsBubble(
        List<SerpApiPriceSuggestionService.ProductOption> options,
        Consumer<SerpApiPriceSuggestionService.ProductOption> onSelectOption
    ) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Label title = new Label("Suggestions produits (photo + prix)");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");

        FlowPane grid = new FlowPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPrefWrapLength(520);
        for (SerpApiPriceSuggestionService.ProductOption o : options) {
            ImageView iv = new ImageView();
            iv.setFitWidth(140);
            iv.setFitHeight(96);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            Label noPreview = new Label("Aperçu indisponible");
            noPreview.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            StackPane previewPane = new StackPane(noPreview, iv);
            previewPane.setMinSize(140, 96);
            previewPane.setMaxSize(140, 96);
            previewPane.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8;");
            loadPreviewInto(iv, noPreview, o.imageUrl(), 140, 96);

            String shortTitle = o.title() == null ? "Produit" : truncate(o.title(), 52);
            Label t = new Label(shortTitle);
            t.setWrapText(true);
            t.setMaxWidth(140);
            t.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #334155;");
            Label p = new Label(String.format(Locale.FRENCH, "Prix estimé: %.2f DT", o.price()));
            p.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");
            Label zoomHint = new Label("Appuyez pour agrandir");
            zoomHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            Button selectBtn = new Button("Sélectionner");
            selectBtn.setMnemonicParsing(false);
            selectBtn.setVisible(false);
            selectBtn.setManaged(false);
            selectBtn.setStyle(
                "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 700; "
                    + "-fx-background-radius: 8; -fx-padding: 6 10; -fx-cursor: hand;"
            );

            VBox card = new VBox(5, previewPane, p, t, zoomHint, selectBtn);
            card.setStyle(
                "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #e2e8f0; "
                    + "-fx-border-radius: 10; -fx-padding: 6; -fx-cursor: hand;"
            );
            previewPane.setOnMouseClicked(e -> showImagePreviewInApp(o.imageUrl(), "Produit similaire"));
            card.setOnMouseClicked(e -> {
                markSelectedCard(grid, card);
                if (e.getClickCount() >= 2) {
                    showImagePreviewInApp(o.imageUrl(), "Produit similaire");
                }
            });
            selectBtn.setOnAction(e -> {
                if (onSelectOption != null) {
                    onSelectOption.accept(o);
                }
                e.consume();
            });
            grid.getChildren().add(card);
        }

        VBox bubble = new VBox(8, title, grid);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 560 : 360);
        bubble.setStyle(
            "-fx-background-color: #f8fafc; -fx-background-radius: 16; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 16; -fx-border-width: 1; "
                + "-fx-padding: 10 12 10 12;"
        );
        HBox row = mode.visualChatbotTheme()
            ? new HBox(8, avatarChip("A", false), bubble)
            : new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        scrollChatToBottom();
    }

    private void loadPreviewInto(ImageView view, Label fallback, String url, double w, double h) {
        if (url == null || url.isBlank()) {
            fallback.setVisible(true);
            return;
        }
        fallback.setVisible(true);
        executor.submit(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "image/*,*/*;q=0.8")
                    .GET()
                    .build();
                HttpResponse<byte[]> resp = imageHttp.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null || resp.body().length == 0) {
                    return;
                }
                Image img = new Image(new ByteArrayInputStream(resp.body()), w, h, true, true);
                if (img.isError()) {
                    return;
                }
                Platform.runLater(() -> {
                    view.setImage(img);
                    fallback.setVisible(false);
                });
            } catch (Exception ignored) {
                // fallback stays visible
            }
        });
    }

    private void showImagePreviewInApp(String imageUrl, String title) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        Label loading = new Label("Chargement de l'image...");
        loading.setStyle("-fx-font-size: 14px; -fx-text-fill: #475569;");
        StackPane center = new StackPane(loading);
        center.setPadding(new Insets(16));
        center.setStyle("-fx-background-color: #0f172a;");

        BorderPane root = new BorderPane(center);
        root.setStyle("-fx-background-color: #0f172a;");
        Scene scene = new Scene(root, 980, 680);
        Stage stage = new Stage();
        Window owner = chatScroll != null && chatScroll.getScene() != null ? chatScroll.getScene().getWindow() : null;
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();

        executor.submit(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "image/*,*/*;q=0.8")
                    .GET()
                    .build();
                HttpResponse<byte[]> resp = imageHttp.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null || resp.body().length == 0) {
                    return;
                }
                Image img = new Image(new ByteArrayInputStream(resp.body()));
                if (img.isError()) {
                    return;
                }
                Platform.runLater(() -> {
                    ImageView big = new ImageView(img);
                    big.setPreserveRatio(true);
                    big.setSmooth(true);
                    big.setFitWidth(940);
                    big.setFitHeight(640);
                    StackPane imgWrap = new StackPane(big);
                    imgWrap.setPadding(new Insets(14));
                    imgWrap.setStyle("-fx-background-color: #0f172a;");
                    root.setCenter(imgWrap);
                });
            } catch (Exception ignored) {
                // keep loading text if failed
            }
        });
    }

    private void onSendChat() {
        if (!GroqChatCompletionService.hasApiKeyConfigured()) {
            alert(Alert.AlertType.WARNING, "Configuration", "Clé API Groq absente. Configurez CHAT_API_KEY ou GROQ_API_KEY puis relancez l’app.");
            return;
        }
        String msg = GroqChatCompletionService.normalizeUserChatContent(chatInputField.getText());
        if (msg.length() < 2) {
            if (wizard != null) {
                // Pendant le wizard, certaines étapes se valident par clic (cartes). On ignore juste les envois vides/courts.
                return;
            }
            alert(Alert.AlertType.WARNING, "Message", "Saisissez au moins 2 caractères.");
            return;
        }
        chatInputField.clear();
        appendUserBubble(msg);
        if (handleProductCreationWizard(msg)) {
            return;
        }
        chatHistory.add(new GroqChatMessage("user", msg));
        sendChatBtn.setDisable(true);
        executor.submit(() -> {
            try {
                List<GroqChatMessage> snap = GroqChatCompletionService.trimHistory(new ArrayList<>(chatHistory), 32);
                String reply = groqService.complete(snap, groqContext);
                List<Product> suggestions = fetchCatalogSuggestionsForChat(msg, 6);
                Platform.runLater(() -> {
                    lastAssistantText = reply;
                    appendAssistantBubble(reply, true);
                    appendCatalogSuggestionsBubble(suggestions);
                    appendAssistantBtn.setDisable(false);
                    sendChatBtn.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    appendErrorBubble(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                    sendChatBtn.setDisable(false);
                });
            }
        });
    }

    private List<Product> fetchCatalogSuggestionsForChat(String userMsg, int limit) {
        if (groqContext != GroqAssistantContext.CATALOGUE_BOUTIQUE
            && groqContext != GroqAssistantContext.UNIVERSAL_ASSISTANT) {
            return List.of();
        }
        String q = userMsg == null ? "" : userMsg.trim();
        if (q.length() < 2 && groqContext != GroqAssistantContext.CATALOGUE_BOUTIQUE) {
            return List.of();
        }
        if (isGreetingOnly(q)) {
            return List.of();
        }
        if (!shouldShowSuggestionsForMessage(q)) {
            return List.of();
        }
        try {
            boolean guest = AppState.getCurrentUser() == null;
            List<Product> published = guest
                ? productService.findPublishedCatalogByAdmin()
                : productService.findPublishedCatalog();
            if (published.isEmpty()) {
                return List.of();
            }
            Map<Integer, Product> byId = new HashMap<>();
            for (Product p : published) {
                byId.put(p.getId(), p);
            }

            LinkedHashSet<Integer> ids = new LinkedHashSet<>();
            if (q.length() >= 2 && elasticsearch.isUsable()) {
                ids.addAll(elasticsearch.searchPublicProductIds(q, "", 0, Double.POSITIVE_INFINITY, guest, limit * 3));
            }
            if (ids.isEmpty() && q.length() >= 2) {
                for (Product p : published) {
                    String text = ((p.getNom() == null ? "" : p.getNom()) + " " + (p.getDescription() == null ? "" : p.getDescription()))
                        .toLowerCase(Locale.FRENCH);
                    if (text.contains(q.toLowerCase(Locale.FRENCH))) {
                        ids.add(p.getId());
                    }
                    if (ids.size() >= limit * 3) {
                        break;
                    }
                }
            }
            if (ids.isEmpty()) {
                ids.addAll(productService.findTopSellingProductIds(limit * 2));
            }

            List<Product> out = new ArrayList<>();
            for (Integer id : ids) {
                Product p = byId.get(id);
                if (p != null) {
                    out.add(p);
                }
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * Affiche des suggestions seulement quand le client exprime une intention produit/reco.
     */
    private static boolean shouldShowSuggestionsForMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String x = msg.toLowerCase(Locale.FRENCH).trim();
        return x.contains("suggest")
            || x.contains("propose")
            || x.contains("recommande")
            || x.contains("je cherche")
            || x.contains("je veux")
            || x.contains("produit")
            || x.contains("catalogue")
            || x.contains("montre")
            || x.contains("donne");
    }

    /**
     * Salutations pures : pas de cartes suggestions.
     */
    private static boolean isGreetingOnly(String msg) {
        if (msg == null) {
            return false;
        }
        String x = msg.toLowerCase(Locale.FRENCH)
            .replace("!", " ")
            .replace("?", " ")
            .replace(".", " ")
            .trim();
        if (x.isBlank()) {
            return false;
        }
        return x.matches("^(bonjour|bonsoir|salut|slt|cc|coucou|hello|hi|hey)(\\s+.*)?$")
            && !x.contains("suggest")
            && !x.contains("produit")
            && !x.contains("recommande")
            && !x.contains("cherche")
            && !x.contains("veux");
    }

    private void appendCatalogSuggestionsBubble(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        Label title = new Label("Suggestions produits du catalogue");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");

        FlowPane grid = new FlowPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPrefWrapLength(620);

        for (Product p : products) {
            ImageView iv = new ImageView();
            iv.setFitWidth(132);
            iv.setFitHeight(86);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            StackPane imagePane = new StackPane();
            imagePane.setMinSize(132, 86);
            imagePane.setMaxSize(132, 86);
            imagePane.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");

            String path = p.getPrimaryImagePath();
            Image img = path == null ? null : ProductImageLoader.loadForDisplay(path, 132, 86);
            if (img != null && !img.isError()) {
                iv.setImage(img);
                imagePane.getChildren().add(iv);
            } else {
                imagePane.getChildren().add(ProductImagePlaceholder.create(132, 86));
            }

            Label name = new Label(truncate(p.getNom() == null ? "Produit" : p.getNom(), 42));
            name.setWrapText(true);
            name.setMaxWidth(132);
            name.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #1f2937;");
            Label price = new Label(String.format(Locale.FRENCH, "%.2f DT", p.getPrix()));
            price.setStyle("-fx-font-size: 11px; -fx-text-fill: #1d4ed8; -fx-font-weight: 700;");
            Button choose = new Button("Choisir");
            choose.setMnemonicParsing(false);
            choose.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 11px; -fx-background-radius: 8;");
            choose.setOnAction(e -> {
                String txt = "Je choisis ce produit : " + (p.getNom() == null ? "Produit" : p.getNom());
                appendUserBubble(txt);
                chatHistory.add(new GroqChatMessage("user", txt));
                executor.submit(() -> {
                    try {
                        String reply = groqService.complete(
                            GroqChatCompletionService.trimHistory(new ArrayList<>(chatHistory), 32),
                            groqContext
                        );
                        Platform.runLater(() -> appendAssistantBubble(reply, true));
                    } catch (Exception ex) {
                        Platform.runLater(() -> appendErrorBubble(ex.getMessage() != null ? ex.getMessage() : "Erreur IA"));
                    }
                });
            });

            VBox card = new VBox(6, imagePane, name, price, choose);
            card.setStyle(
                "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-color: #dbe4f0; "
                    + "-fx-border-radius: 10; -fx-padding: 8;"
            );
            grid.getChildren().add(card);
        }

        VBox bubble = new VBox(8, title, grid);
        bubble.setMaxWidth(mode.visualChatbotTheme() ? 700 : 430);
        bubble.setStyle(
            "-fx-background-color: #eff6ff; -fx-background-radius: 16; "
                + "-fx-border-color: #bfdbfe; -fx-border-radius: 16; -fx-border-width: 1; "
                + "-fx-padding: 10 12 10 12;"
        );
        HBox row = mode.visualChatbotTheme()
            ? new HBox(8, avatarChip("A", false), bubble)
            : new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        messagesBox.getChildren().add(row);
        scrollChatToBottom();
    }

    private boolean handleProductCreationWizard(String msg) {
        if (groqContext != GroqAssistantContext.UNIVERSAL_ASSISTANT) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        boolean trigger = lower.contains("créer un produit")
            || lower.contains("creer un produit")
            || lower.contains("créer mon propre produit")
            || lower.contains("creer mon propre produit")
            || lower.contains("mon propre produit")
            || lower.contains("je veux un produit")
            || lower.contains("je veux le produit")
            || lower.contains("ajouter un produit")
            || lower.contains("nouveau produit");

        boolean directProductSentence = looksLikeDirectProductSentence(msg, lower);
        if (wizard == null && (trigger || directProductSentence)) {
            wizard = new ProductCreationWizard();
            if (!wizard.bootstrapFromInitialRequest(msg, lower)) {
                appendAssistantBubble(
                    "Parfait, on va créer le produit ensemble. Première question : quel est le nom du produit ?",
                    false
                );
            }
            return true;
        }
        if (wizard == null) {
            return false;
        }
        if (containsCancel(lower)) {
            wizard = null;
            appendAssistantBubble("Création annulée. Vous pouvez relancer en écrivant « créer un produit ».", false);
            return true;
        }
        return wizard.consume(msg);
    }

    private boolean containsCancel(String lower) {
        return lower.contains("annuler") || lower.contains("stop") || lower.contains("laisser tomber");
    }

    private static boolean containsProductTypeHint(String low) {
        if (low == null || low.isBlank()) {
            return false;
        }
        return low.contains("ordinateur")
            || low.contains("portable")
            || low.contains("laptop")
            || low.contains("table")
            || low.contains("chaise")
            || low.contains("pull")
            || low.contains("maillot")
            || low.contains("chargeur")
            || low.contains("casque")
            || low.contains("iphone")
            || low.contains("jean")
            || low.contains("pantalon")
            || low.contains("mug")
            || low.contains("tasse")
            || low.contains("bureau")
            || low.contains("lampe")
            || low.contains("canap")
            || containsWholeWordIgnoreCase(low, "lit")
            || low.contains("meuble")
            || low.contains("étagère")
            || low.contains("etagere")
            || low.contains("coussin")
            || low.contains("jouet")
            || low.contains("livre")
            || containsWholeWordIgnoreCase(low, "sac")
            || low.contains("montre")
            || low.contains("climatiseur")
            || low.contains("clim")
            || low.contains("frigo")
            || low.contains("réfrig")
            || low.contains("refrig")
            || low.contains("tv")
            || low.contains("télé")
            || low.contains("tele")
            || low.contains("ventilateur");
    }

    /**
     * Indices de description courte dans la phrase (couleur, taille, matière...).
     */
    private static boolean hasProductAttributeHint(String low) {
        if (low == null || low.isBlank()) {
            return false;
        }
        return low.contains("noir")
            || low.contains("noire")
            || low.contains("blanc")
            || low.contains("blanche")
            || low.contains("bleu")
            || low.contains("rouge")
            || low.contains("vert")
            || low.contains("jaune")
            || low.contains("grand")
            || low.contains("petit")
            || low.contains("taille")
            || low.contains("metal")
            || low.contains("métal")
            || low.contains("bois")
            || low.contains("plastique")
            || low.contains("2 places")
            || low.contains("3 places");
    }

    private static boolean hasBudgetOrPriceHint(String low) {
        if (low == null || low.isBlank()) {
            return false;
        }
        return low.contains("dt")
            || low.contains("dinar")
            || low.contains("dinars")
            || low.contains("budget")
            || low.contains("euro")
            || low.contains("tnd")
            || low.matches(".*\\b[0-9]{2,5}\\b.*");
    }

    /**
     * Phrase déjà exploitable pour images + prix (évite l’étape « donnez une description » après un nom générique).
     */
    private static boolean shouldSkipDescriptionStepForWizard(String rawMsg, String lowerMsg) {
        if (rawMsg == null || rawMsg.isBlank()) {
            return false;
        }
        String low = lowerMsg == null ? rawMsg.toLowerCase(Locale.ROOT) : lowerMsg;
        if (looksLikeDirectProductSentence(rawMsg, low)) {
            return true;
        }
        if (isSubstantialProductDescription(rawMsg)) {
            return true;
        }
        int words = rawMsg.trim().split("\\s+").length;
        if (containsProductTypeHint(low) && hasProductAttributeHint(low)) {
            return true;
        }
        if (containsProductTypeHint(low) && (hasBudgetOrPriceHint(low) || words >= 4)) {
            return true;
        }
        return containsProductTypeHint(low) && rawMsg.trim().length() >= 18;
    }

    private static boolean looksLikeDirectProductSentence(String rawMsg, String lowerMsg) {
        if (rawMsg == null || rawMsg.isBlank()) {
            return false;
        }
        String low = lowerMsg == null ? rawMsg.toLowerCase(Locale.ROOT) : lowerMsg;
        if (low.endsWith("?")) {
            return false;
        }
        boolean explicitCreateIntent = low.contains("je veux")
            || low.contains("créer")
            || low.contains("creer")
            || low.contains("ajouter")
            || low.contains("nouveau");
        boolean hasProductHint = containsProductTypeHint(low);
        boolean hasBudgetHint = hasBudgetOrPriceHint(low);
        boolean hasAttributeHint = hasProductAttributeHint(low);
        return explicitCreateIntent && (hasProductHint || hasBudgetHint || hasAttributeHint);
    }

    private static boolean isYes(String text) {
        if (text == null) {
            return false;
        }
        String x = text.trim().toLowerCase(Locale.ROOT);
        return x.equals("oui") || x.equals("ok") || x.equals("okay") || x.equals("valider") || x.equals("validé")
            || x.equals("parfait") || x.equals("go");
    }

    private static boolean isNo(String text) {
        if (text == null) {
            return false;
        }
        String x = text.trim().toLowerCase(Locale.ROOT);
        return x.equals("non")
            || x.equals("no")
            || x.equals("pas encore")
            || x.equals("pas maintenant")
            || x.contains("j'aime pas")
            || x.contains("j aime pas")
            || x.contains("je n'aime pas")
            || x.contains("je n aime pas")
            || x.contains("pas aime")
            || x.contains("pas aimer")
            || x.contains("aucun produit")
            || x.contains("aucune suggestion");
    }

    private static boolean isRejectingAllSuggestions(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String x = text.trim().toLowerCase(Locale.ROOT);
        return x.contains("aucun produit")
            || x.contains("aucune suggestion")
            || x.contains("rien me plait")
            || x.contains("rien ne me plait")
            || x.contains("j'aime pas")
            || x.contains("j aime pas")
            || x.contains("je n'aime pas")
            || x.contains("je n aime pas")
            || x.contains("pas aime")
            || x.contains("pas aimer");
    }

    /** Demande explicite d’afficher une autre série de cartes (photo + prix), sans rejeter violemment la liste actuelle. */
    private static boolean wantsAlternativeSuggestionBatch(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String x = text.trim().toLowerCase(Locale.FRENCH).replace('’', '\'');
        return x.contains("autres suggestions")
            || x.contains("autre suggestion")
            || x.contains("autres suggestion")
            || x.contains("d'autres suggestions")
            || x.contains("d autres suggestions")
            || x.contains("dautres suggestions")
            || x.contains("autres propositions")
            || x.contains("autre proposition")
            || x.contains("encore des suggestions")
            || x.contains("nouvelles suggestions")
            || x.contains("voir d'autres")
            || x.contains("voir d autres")
            || x.contains("voir dautres")
            || x.contains("changer les suggestions")
            || x.contains("changer de suggestions")
            || x.contains("renouveler les suggestions")
            || x.contains("une autre liste")
            || x.contains("autre liste de suggestions");
    }

    private static boolean wantsAiDescription(String text) {
        if (text == null) {
            return false;
        }
        String x = text.trim().toLowerCase(Locale.ROOT);
        return x.equals("1")
            || x.equals("ia")
            || x.equals("ai")
            || x.contains("description ia")
            || x.contains("description ai")
            || x.contains("amelioree")
            || x.contains("améliorée")
            || x.contains("suggérée")
            || x.contains("suggeree");
    }

    private static boolean wantsOwnDescription(String text) {
        if (text == null) {
            return false;
        }
        String x = text.trim().toLowerCase(Locale.ROOT);
        return x.equals("2")
            || x.contains("ma description")
            || x.contains("mon texte")
            || x.contains("description originale")
            || x.contains("description perso");
    }

    private static String normalizePriceInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String x = raw.trim().toLowerCase(Locale.ROOT)
            .replace("dt", "")
            .replace("dinars", "")
            .replace("dinar", "")
            .replace(" ", "");
        x = x.replace(',', '.');
        try {
            double p = Double.parseDouble(x);
            if (p <= 0) {
                return "";
            }
            return String.format(Locale.US, "%.2f", p);
        } catch (Exception ignored) {
            return raw.trim();
        }
    }

    private final class ProductCreationWizard {
        private String nom;
        private String description;
        private String aiSuggestedDescription;
        private final List<String> aiDescriptionOptions = new ArrayList<>();
        private String userShortDescription;
        private String categorie;
        private String prixText;
        private String selectedImageUrl;
        private String normalizedSearchIntent;
        private Double budgetDt;
        private boolean externalLinkMode = false;
        private int descriptionVariantIndex = 0;
        private int suggestionRetryCount = 0;
        /** Évite de reproposer les mêmes cartes quand l'utilisateur demande "autres suggestions". */
        private final java.util.Set<String> seenSuggestionKeys = new java.util.LinkedHashSet<>();
        private Step step = Step.NAME;

        private boolean bootstrapFromInitialRequest(String rawMsg, String lowerMsg) {
            String externalUrl = extractFirstHttpUrl(rawMsg);
            if (externalUrl != null && !externalUrl.isBlank()) {
                this.seenSuggestionKeys.clear();
                this.externalLinkMode = true;
                this.step = Step.CATEGORY_CLASSIFYING;
                sendChatBtn.setDisable(true);
                appendAssistantBubble("Parfait, je lis le lien du produit et je prépare la fiche automatiquement…", false);
                loadExternalProductAndContinue(externalUrl);
                return true;
            }
            String candidate = extractProductHintFromCreateRequest(rawMsg, lowerMsg);
            if ((candidate == null || candidate.isBlank()) && looksLikeDirectProductSentence(rawMsg, lowerMsg)) {
                candidate = rawMsg == null ? "" : rawMsg.trim();
            }
            if (candidate == null || candidate.isBlank()) {
                return false;
            }
            String cleaned = candidate.trim();
            this.nom = cleaned;
            this.userShortDescription = cleaned;
            this.seenSuggestionKeys.clear();
            this.externalLinkMode = false;
            this.step = Step.CATEGORY_CLASSIFYING;
            sendChatBtn.setDisable(true);
            classifyCategoryForDescriptionAsync();
            return true;
        }

        private void openAdminWithCurrentDraft(boolean openEditorDirectly) {
            AppState.setPendingAdminProductDraft(
                new AppState.AdminProductDraft(nom, description, categorie, prixText, null, selectedImageUrl)
            );
            if (openEditorDirectly) {
                AppState.requestOpenAdminProductEditor();
            }
            wizard = null;
            Platform.runLater(() -> {
                try {
                    MainApp.showAdminProducts();
                } catch (Exception ex) {
                    appendErrorBubble(ex.getMessage() != null ? ex.getMessage() : "Erreur d'ouverture admin.");
                }
            });
        }

        private void showReviewCardAndWaitChoice() {
            step = Step.CONFIRM;
            String catLabel = ProductFormUi.resolveCategoryChoice(categorie).getLabel();
            appendProductReviewCard(
                nom,
                catLabel,
                description,
                prixText,
                selectedImageUrl,
                () -> {
                    submitProductRequestForAdmin();
                },
                this::openQuickEditDialog
            );
        }

        private void submitProductRequestForAdmin() {
            String requestText = "Demande création produit via chatbot : " + safe(nom);
            String nomFinal = safe(nom);
            String descriptionFinal = safe(description);
            String categorieFinal = (categorie == null || categorie.isBlank()) ? ProductFormUi.DEFAULT_CATEGORY : categorie;
            String prixFinalText = normalizePriceInput(prixText);
            double tmpPrixFinal;
            try {
                tmpPrixFinal = prixFinalText.isBlank() ? fallbackEstimatedPriceDt(buildSuggestionQuery()) : Double.parseDouble(prixFinalText);
            } catch (Exception ex) {
                tmpPrixFinal = fallbackEstimatedPriceDt(buildSuggestionQuery());
            }
            final double prixFinal = tmpPrixFinal;

            Integer demandeurId = AppState.getCurrentUser() != null ? AppState.getCurrentUser().getId() : null;
            String extrasJson = null;
            if (selectedImageUrl != null && !selectedImageUrl.isBlank()) {
                extrasJson = new Gson().toJson(java.util.Map.of("imageUrl", selectedImageUrl.trim()));
            }
            DemandeProduitService demandeService = new DemandeProduitService();
            final String extrasJsonFinal = extrasJson;
            executor.submit(() -> {
                try {
                    demandeService.insertDemandeSimple(
                        requestText,
                        nomFinal,
                        descriptionFinal,
                        categorieFinal,
                        prixFinal,
                        budgetDt,
                        demandeurId,
                        extrasJsonFinal
                    );
                    Platform.runLater(() -> {
                        alert(
                            Alert.AlertType.INFORMATION,
                            "Fiche envoyée",
                            "Votre demande a été envoyée à l’admin.\n"
                                + "L’administrateur recevra une notification et pourra accepter ou rejeter votre fiche."
                        );
                        wizard = null;
                        appendAssistantBubble(
                            "Demande envoyée. Vous pouvez créer un autre produit quand vous voulez.",
                            false
                        );
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> alert(
                        Alert.AlertType.ERROR,
                        "Envoi demande",
                        ex.getMessage() != null ? ex.getMessage() : "Impossible d’envoyer la demande."
                    ));
                }
            });
        }

        private void openQuickEditDialog() {
            Window owner = ProductAssistantPanel.this.getScene() != null
                ? ProductAssistantPanel.this.getScene().getWindow()
                : null;
            Stage stage = new Stage();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle("Modifier le produit");
            Product existing = new Product();
            existing.setId(1);
            existing.setNom(safe(nom));
            existing.setDescription(safe(description));
            existing.setCategorie((categorie == null || categorie.isBlank()) ? ProductFormUi.DEFAULT_CATEGORY : categorie);
            try {
                existing.setPrix(Double.parseDouble(normalizePriceInput(prixText)));
            } catch (Exception ignored) {
                existing.setPrix(0.0);
            }
            existing.setImagePath(safe(selectedImageUrl));
            existing.setStock(1);

            javafx.collections.ObservableList<org.example.models.Stock> stocks;
            try {
                stocks = javafx.collections.FXCollections.observableArrayList(new StockService().findAll());
            } catch (Exception ex) {
                stocks = javafx.collections.FXCollections.observableArrayList();
            }
            ProductEditorPane editor = new ProductEditorPane(existing, stocks, msg ->
                alert(Alert.AlertType.WARNING, "Image", msg)
            );

            Button cancelBtn = new Button("Annuler");
            cancelBtn.setMnemonicParsing(false);
            cancelBtn.setStyle(
                "-fx-background-color: #cfd8e3; -fx-text-fill: #334155; -fx-font-weight: 700; "
                    + "-fx-background-radius: 12; -fx-padding: 10 16;"
            );
            cancelBtn.setOnAction(e -> stage.close());

            Button saveBtn = new Button("Envoyer la fiche");
            saveBtn.setMnemonicParsing(false);
            saveBtn.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; "
                    + "-fx-background-radius: 12; -fx-padding: 10 18;"
            );
            saveBtn.setOnAction(e -> {
                String newNom = editor.nomField.getText() == null ? "" : editor.nomField.getText().trim();
                if (newNom.length() < 2) {
                    alert(Alert.AlertType.WARNING, "Saisie", "Le nom doit contenir au moins 2 caractères.");
                    return;
                }
                String normalizedPrice = normalizePriceInput(editor.prixField.getText());
                if (normalizedPrice.isBlank()) {
                    alert(Alert.AlertType.WARNING, "Saisie", "Entrez un prix valide (> 0).");
                    return;
                }
                try {
                    double parsed = Double.parseDouble(normalizedPrice);
                    if (parsed <= 0) {
                        alert(Alert.AlertType.WARNING, "Saisie", "Entrez un prix valide (> 0).");
                        return;
                    }
                } catch (Exception ex) {
                    alert(Alert.AlertType.WARNING, "Saisie", "Entrez un prix valide (> 0).");
                    return;
                }

                ProductFormUi.ProductCategoryChoice selectedCategory = editor.categoryCombo.getValue();
                if (selectedCategory == null) {
                    alert(Alert.AlertType.WARNING, "Saisie", "Choisissez une catégorie.");
                    return;
                }

                nom = newNom;
                prixText = normalizedPrice;
                categorie = selectedCategory.getDbValue();
                description = editor.descriptionArea.getText() == null ? "" : editor.descriptionArea.getText().trim();
                String imageRaw = editor.imagePathField.getText() == null ? "" : editor.imagePathField.getText().trim();
                selectedImageUrl = firstImagePath(imageRaw);
                stage.close();
                appendAssistantBubble("Modification prise en compte. Vérifiez la fiche mise à jour.", false);
                showReviewCardAndWaitChoice();
            });

            HBox actions = new HBox(10, cancelBtn, saveBtn);
            actions.setAlignment(Pos.CENTER_RIGHT);
            Label subtitle = new Label("Formulaire complet (comme l'admin) avant envoi de la fiche.");
            subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
            VBox formSection = editor.createFormSection();
            formSection.setMaxWidth(Double.MAX_VALUE);

            VBox root = new VBox(
                12,
                subtitle,
                formSection,
                actions
            );
            root.setPadding(new Insets(18));
            root.setStyle(
                "-fx-background-color: white; -fx-background-radius: 14; "
                    + "-fx-border-color: #e5e7eb; -fx-border-radius: 14;"
            );

            ScrollPane scroller = new ScrollPane(root);
            scroller.setFitToWidth(true);
            scroller.setStyle("-fx-background-color: white;");
            Scene scene = new Scene(scroller, 900, 760);
            stage.setScene(scene);
            stage.showAndWait();
        }

        private String firstImagePath(String raw) {
            if (raw == null || raw.isBlank()) {
                return "";
            }
            String[] parts = raw.split("[;|\\n,]");
            for (String p : parts) {
                if (p != null) {
                    String t = p.trim();
                    if (!t.isBlank()) {
                        return t;
                    }
                }
            }
            return raw.trim();
        }

        private void loadExternalProductAndContinue(String url) {
            executor.submit(() -> {
                ExternalProductDraft draft = readExternalProductDraft(url);
                Platform.runLater(() -> {
                    if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this) {
                        sendChatBtn.setDisable(false);
                        return;
                    }
                    nom = (draft.name() == null || draft.name().isBlank()) ? guessNameFromUrl(url) : draft.name();
                    userShortDescription = (draft.description() == null || draft.description().isBlank())
                        ? ("Produit importé depuis un lien externe : " + nom)
                        : draft.description();
                    selectedImageUrl = draft.imageUrl() == null ? "" : draft.imageUrl().trim();
                    budgetDt = extractBudgetDt(userShortDescription);
                    appendAssistantBubble(
                        "Lien analysé : " + nom + ". Je prépare maintenant les suggestions (photo + prix + description).",
                        false
                    );
                    classifyCategoryForDescriptionAsync();
                });
            });
        }

        private ExternalProductDraft readExternalProductDraft(String url) {
            String fallbackName = guessNameFromUrl(url);
            String fallbackDesc = "Produit importé depuis un lien externe : " + fallbackName;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
                HttpResponse<String> resp = imageHttp.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    return new ExternalProductDraft(fallbackName, fallbackDesc, "");
                }
                String html = resp.body() == null ? "" : resp.body();
                String title = firstNonBlank(
                    extractMetaContent(html, "property", "og:title"),
                    extractMetaContent(html, "name", "twitter:title"),
                    extractTagTitle(html),
                    fallbackName
                );
                String desc = firstNonBlank(
                    extractMetaContent(html, "property", "og:description"),
                    extractMetaContent(html, "name", "description"),
                    fallbackDesc
                );
                String image = firstNonBlank(
                    extractMetaContent(html, "property", "og:image"),
                    extractMetaContent(html, "name", "twitter:image"),
                    ""
                );
                title = title == null ? fallbackName : title.trim().replaceAll("\\s+", " ");
                desc = desc == null ? fallbackDesc : desc.trim().replaceAll("\\s+", " ");
                if (title.length() > 90) {
                    title = title.substring(0, 90).trim();
                }
                if (desc.length() > 500) {
                    desc = desc.substring(0, 500).trim();
                }
                return new ExternalProductDraft(title, desc, image == null ? "" : image.trim());
            } catch (Exception ignored) {
                return new ExternalProductDraft(fallbackName, fallbackDesc, "");
            }
        }

        private String guessNameFromUrl(String url) {
            if (url == null || url.isBlank()) {
                return "Produit externe";
            }
            try {
                String path = URI.create(url).getPath();
                if (path == null || path.isBlank()) {
                    return "Produit externe";
                }
                String[] seg = path.split("/");
                for (int i = seg.length - 1; i >= 0; i--) {
                    String s = seg[i];
                    if (s != null && !s.isBlank()) {
                        s = s.replace('-', ' ').replace('_', ' ')
                            .replaceAll("\\.[a-zA-Z0-9]{2,5}$", "")
                            .replaceAll("\\s+", " ")
                            .trim();
                        if (!s.isBlank()) {
                            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return "Produit externe";
        }

        private String extractMetaContent(String html, String attrName, String attrValue) {
            if (html == null || html.isBlank()) {
                return null;
            }
            String regex = "(?is)<meta[^>]*" + Pattern.quote(attrName) + "\\s*=\\s*['\\\"]" + Pattern.quote(attrValue)
                + "['\\\"][^>]*content\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*>";
            Matcher m = Pattern.compile(regex).matcher(html);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }

        private String extractTagTitle(String html) {
            if (html == null || html.isBlank()) {
                return null;
            }
            Matcher m = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }

        private String firstNonBlank(String... vals) {
            if (vals == null) {
                return null;
            }
            for (String v : vals) {
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return null;
        }

        private boolean consume(String msg) {
            String t = msg == null ? "" : msg.trim();
            if (t.isEmpty()) {
                appendAssistantBubble("Je n’ai rien reçu. Donnez-moi une valeur pour continuer.", false);
                return true;
            }
            String lower = t.toLowerCase(Locale.ROOT);
            switch (step) {
                case NAME -> {
                    String externalUrl = extractFirstHttpUrl(t);
                    if (externalUrl != null && !externalUrl.isBlank()) {
                        seenSuggestionKeys.clear();
                        externalLinkMode = true;
                        step = Step.CATEGORY_CLASSIFYING;
                        sendChatBtn.setDisable(true);
                        appendAssistantBubble("Je récupère les infos du lien (nom, image, description) puis je génère la fiche…", false);
                        loadExternalProductAndContinue(externalUrl);
                        return true;
                    }
                    externalLinkMode = false;
                    nom = t;
                    if (shouldSkipDescriptionStepForWizard(t, lower)) {
                        userShortDescription = t;
                        step = Step.CATEGORY_CLASSIFYING;
                        sendChatBtn.setDisable(true);
                        appendAssistantBubble(
                            "Merci, j’ai bien noté votre idée. Je prépare des suggestions avec photos et prix…",
                            false
                        );
                        classifyCategoryForDescriptionAsync();
                    } else {
                        step = Step.DESCRIPTION;
                        appendAssistantBubble("Donne-moi une description du produit.", false);
                    }
                }
                case DESCRIPTION -> {
                    userShortDescription = t;
                    step = Step.CATEGORY_CLASSIFYING;
                    sendChatBtn.setDisable(true);
                    classifyCategoryForDescriptionAsync();
                }
                case DESCRIPTION_CHOICE -> {
                    if (wantsAiDescription(t)) {
                        description = aiSuggestedDescription;
                        step = Step.DESCRIPTION_REVIEW;
                        appendAssistantBubble(
                            "Parfait, vous avez sélectionné la description IA.\n\n"
                                + description
                                + "\n\nEst-ce que cette description vous convient ? (oui/non)\n"
                                + "Si vous voulez un ajustement précis, écrivez non + votre remarque.",
                            false
                        );
                        return true;
                    }
                    if (wantsOwnDescription(t)) {
                        description = appendDescriptionAdvisoryIfMissing(userShortDescription == null ? "" : userShortDescription.trim());
                        step = Step.DESCRIPTION_REVIEW;
                        appendAssistantBubble(
                            "Parfait, vous avez sélectionné votre description.\n\n"
                                + description
                                + "\n\nEst-ce que cette description vous convient ? (oui/non)\n"
                                + "Si vous voulez un ajustement précis, écrivez non + votre remarque.",
                            false
                        );
                        return true;
                    }
                    appendAssistantBubble("Cliquez sur une carte de description pour sélectionner votre choix.", false);
                }
                case DESCRIPTION_REVIEW -> {
                    if (isYes(t)) {
                        step = Step.AWAITING_CATEGORY_GROQ;
                        appendAssistantBubble("Je choisis la catégorie la plus adaptée avec Groq…", false);
                        sendChatBtn.setDisable(true);
                        triggerCategoryFromGroqAsync();
                        return true;
                    }
                    descriptionVariantIndex++;
                    String extraHint = isNo(t) ? null : t;
                    description = buildEnhancedDescription(userShortDescription, extraHint, categorie);
                    appendAssistantBubble(
                        "D’accord, je propose une autre version :\n\n"
                            + description
                            + "\n\nCette version vous convient-elle ? (oui/non)",
                        false
                    );
                }
                case AWAITING_CATEGORY_GROQ -> {
                    if (containsCancel(lower)) {
                        wizard = null;
                        appendAssistantBubble("Création annulée. Vous pouvez relancer en écrivant « créer un produit ».", false);
                        sendChatBtn.setDisable(false);
                        return true;
                    }
                    appendAssistantBubble("La catégorie est encore en cours d’analyse. Patientez un instant…", false);
                }
                case PRICE -> {
                    if (isRejectingAllSuggestions(t) || wantsAlternativeSuggestionBatch(t)) {
                        suggestionRetryCount++;
                        normalizedSearchIntent = inferSearchIntentForSuggestions();
                        String retryLabel = suggestionRetryCount > 1 ? "encore une autre série" : "une nouvelle série";
                        appendAssistantBubble(
                            "D'accord, je vous propose " + retryLabel + " de suggestions adaptées. "
                                + "Regardez ces nouvelles options.",
                            false
                        );
                        triggerIntegratedSuggestionsAsync();
                        return true;
                    }
                    appendAssistantBubble(
                        "Le prix se choisit depuis une carte photo + prix estimé. "
                            + "Cliquez d’abord sur une suggestion pour continuer, "
                            + "ou écrivez « autres suggestions » pour en voir d’autres.",
                        false
                    );
                }
                case CONFIRM -> {
                    if (isYes(t)) {
                        alert(
                            Alert.AlertType.INFORMATION,
                            "Fiche envoyée",
                            "La fiche est envoyée. Vous recevrez une notification quand le produit sera disponible."
                        );
                        openAdminWithCurrentDraft(false);
                    } else if (isNo(t)) {
                        openAdminWithCurrentDraft(true);
                    } else {
                        appendAssistantBubble("Utilisez les boutons « Envoyer la fiche » ou « Changer quelque chose ».", false);
                    }
                }
            }
            return true;
        }

        private boolean wantsPriceSuggestion(String text) {
            String x = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            return x.equals("suggest")
                || x.equals("suggere")
                || x.equals("suggérer")
                || x.contains("propose un prix")
                || x.contains("prix sugg")
                || x.contains("je sais pas");
        }

        private void triggerPriceSuggestionAsync() {
            if (!SerpApiPriceSuggestionService.hasApiKeyConfigured()) {
                appendAssistantBubble("Aucune clé API prix configurée. Entrez un prix manuel (> 0).", false);
                return;
            }
            String q = buildSuggestionQuery();
            appendAssistantBubble("Je cherche une fourchette de prix externe…", false);
            sendChatBtn.setDisable(true);
            executor.submit(() -> {
                try {
                    SerpApiPriceSuggestionService.PriceSuggestion s = priceSuggestionService.suggest(q);
                    Platform.runLater(() -> {
                        String hint = String.format(
                            Locale.FRENCH,
                            "Suggestion prix (sources externes, %d résultats): min %.2f, médiane %.2f, max %.2f. "
                                + "Vous pouvez saisir la médiane (%.2f) ou un autre prix.",
                            s.count(), s.min(), s.median(), s.max(), s.median()
                        );
                        appendAssistantBubble(hint, false);
                        sendChatBtn.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        appendAssistantBubble("Je n’ai pas pu récupérer la suggestion API. Entrez un prix manuel (> 0).", false);
                        sendChatBtn.setDisable(false);
                    });
                }
            });
        }

        private void triggerMarketOptionsAsync() {
            if (!SerpApiPriceSuggestionService.hasApiKeyConfigured()) {
                return;
            }
            String q = buildSuggestionQuery();
            executor.submit(() -> {
                try {
                    List<SerpApiPriceSuggestionService.ProductOption> options = priceSuggestionService.searchJumiaOptions(q, 4);
                    if (options.isEmpty()) {
                        options = priceSuggestionService.searchProductOptions(q, 4);
                    }
                    if (options.isEmpty()) {
                        return;
                    }
                    List<SerpApiPriceSuggestionService.ProductOption> finalOptions = options;
                    Platform.runLater(() -> appendMarketOptionsBubble(finalOptions, null));
                } catch (Exception ignored) {
                    // optionnel
                }
            });
        }

        private void triggerCategoryFromGroqAsync() {
            String nomSnap = nom == null ? "" : nom.trim();
            String descSnap = description == null ? "" : description.trim();
            String userPayload = "Nom du produit :\n" + (nomSnap.isBlank() ? "—" : nomSnap)
                + "\n\nDescription retenue :\n" + (descSnap.isBlank() ? "—" : descSnap);
            executor.submit(() -> {
                try {
                    String sys = buildProductCategoryClassifierSystemPrompt();
                    String line = groqService.completeWithSystem(
                        List.of(new GroqChatMessage("user", userPayload)),
                        sys,
                        0.18,
                        0.85,
                        80
                    );
                    String dbKey = mapGroqCategoryLineToDbKey(line);
                    ProductFormUi.ProductCategoryChoice choice = ProductFormUi.resolveCategoryChoice(dbKey);
                    Platform.runLater(() -> {
                        if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this) {
                            sendChatBtn.setDisable(false);
                            return;
                        }
                        categorie = choice.getDbValue();
                        step = Step.PRICE;
                        appendAssistantBubble(
                            "Catégorie choisie par Groq : « " + choice.getLabel() + " ».\n\n"
                                + "Voici 6 suggestions complètes (photo + prix + description). Choisissez ou modifiez.",
                            false
                        );
                        sendChatBtn.setDisable(false);
                        normalizedSearchIntent = inferSearchIntentForSuggestions();
                        triggerIntegratedSuggestionsAsync();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this) {
                            sendChatBtn.setDisable(false);
                            return;
                        }
                        categorie = ProductFormUi.DEFAULT_CATEGORY;
                        ProductFormUi.ProductCategoryChoice fallback = ProductFormUi.resolveCategoryChoice(categorie);
                        step = Step.PRICE;
                        String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                        appendAssistantBubble(
                            "Groq n’a pas pu classer le produit (" + truncate(err, 120) + "). "
                                + "Catégorie par défaut : « " + fallback.getLabel() + " ».\n\n"
                                + "Voici 6 suggestions complètes (photo + prix + description). Choisissez ou modifiez.",
                            false
                        );
                        sendChatBtn.setDisable(false);
                        normalizedSearchIntent = inferSearchIntentForSuggestions();
                        triggerIntegratedSuggestionsAsync();
                    });
                }
            });
        }

        private void classifyCategoryForDescriptionAsync() {
            String nomSnap = nom == null ? "" : nom.trim();
            String rawDesc = userShortDescription == null ? "" : userShortDescription.trim();
            String userPayload = "Nom du produit :\n" + (nomSnap.isBlank() ? "—" : nomSnap)
                + "\n\nDescription utilisateur :\n" + (rawDesc.isBlank() ? "—" : rawDesc);
            executor.submit(() -> {
                String dbKey = ProductFormUi.DEFAULT_CATEGORY;
                try {
                    String sys = buildProductCategoryClassifierSystemPrompt();
                    String line = groqService.completeWithSystem(
                        List.of(new GroqChatMessage("user", userPayload)),
                        sys,
                        0.16,
                        0.84,
                        80
                    );
                    dbKey = mapGroqCategoryLineToDbKey(line);
                } catch (Exception ignored) {
                    // fallback par défaut
                }
                String finalDbKey = dbKey;
                Platform.runLater(() -> {
                    if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this) {
                        sendChatBtn.setDisable(false);
                        return;
                    }
                    categorie = finalDbKey;
                    sendChatBtn.setDisable(true);
                });
                generateDescriptionOptionsWithGroqAsync(nomSnap, rawDesc, dbKey);
            });
        }

        private void generateDescriptionOptionsWithGroqAsync(String nomSnap, String rawDesc, String categoryDb) {
            executor.submit(() -> {
                List<String> groqOptions = new ArrayList<>();
                try {
                    String catLabel = ProductFormUi.resolveCategoryChoice(categoryDb).getLabel();
                    String sys = buildProductDescriptionSuggestionsSystemPrompt();
                    String user = "Produit: " + (nomSnap == null || nomSnap.isBlank() ? "—" : nomSnap.trim())
                        + "\nCatégorie: " + safe(catLabel)
                        + "\nContexte utilisateur: " + (rawDesc == null || rawDesc.isBlank() ? "—" : rawDesc.trim());
                    String reply = groqService.completeWithSystem(
                        List.of(new GroqChatMessage("user", user)),
                        sys,
                        0.55,
                        0.9,
                        500
                    );
                    groqOptions.addAll(parseGroqDescriptionBlocks(reply));
                } catch (Exception ignored) {
                    // fallback below
                }

                if (groqOptions.size() < 2) {
                    groqOptions.clear();
                    groqOptions.add(buildEnhancedDescription(rawDesc, null, categoryDb));
                    groqOptions.add(buildEnhancedDescriptionVariantFromUser(rawDesc, 1, categoryDb));
                    groqOptions.add(buildEnhancedDescriptionVariantFromUser(rawDesc, 2, categoryDb));
                }

                Platform.runLater(() -> {
                    if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this) {
                        sendChatBtn.setDisable(false);
                        return;
                    }
                    categorie = categoryDb;
                    aiDescriptionOptions.clear();
                    aiDescriptionOptions.addAll(groqOptions);
                    aiSuggestedDescription = aiDescriptionOptions.isEmpty() ? "" : aiDescriptionOptions.get(0);
                    description = aiSuggestedDescription;
                    step = Step.PRICE;
                    sendChatBtn.setDisable(false);
                    budgetDt = extractBudgetDt(nom + " " + userShortDescription);
                    if (externalLinkMode) {
                        if (prixText == null || prixText.isBlank()) {
                            prixText = String.format(Locale.US, "%.2f", fallbackEstimatedPriceDt(buildSuggestionQuery()));
                        }
                        appendAssistantBubble(
                            "J’ai préparé la fiche depuis votre lien. Vous pouvez l’envoyer à l’admin maintenant ou la modifier.",
                            false
                        );
                        showReviewCardAndWaitChoice();
                    } else {
                        appendAssistantBubble("Voici 6 suggestions complètes.", false);
                        normalizedSearchIntent = inferSearchIntentForSuggestions();
                        triggerIntegratedSuggestionsAsync();
                    }
                });
            });
        }

        private void triggerIntegratedSuggestionsAsync() {
            String q = buildSuggestionQuery();
            String pexelsQ = buildPexelsQuery(q);
            executor.submit(() -> {
                List<SuggestedBundle> bundles = new ArrayList<>();
                try {
                    if (SerpApiPriceSuggestionService.hasApiKeyConfigured()) {
                        List<SerpApiPriceSuggestionService.ProductOption> options = priceSuggestionService.searchJumiaOptions(q, 24);
                        if (options.isEmpty()) {
                            options = priceSuggestionService.searchProductOptions(q, 24);
                        }
                        options = filterOptionsByBudget(options, budgetDt);
                        for (var o : options) {
                            if (o == null) {
                                continue;
                            }
                            String desc = buildBundleDescriptionFromOption(o.title(), q);
                            String cleanTitle = sanitizeProductTitle(o.title(), q);
                            bundles.add(new SuggestedBundle(
                                cleanTitle,
                                o.imageUrl(),
                                normalizePriceToBudget(o.price() > 0 ? o.price() : fallbackEstimatedPriceDt(q), budgetDt),
                                desc
                            ));
                        }
                    }
                } catch (Exception ignored) {
                    // fallback below
                }

                if (bundles.isEmpty() && PexelsPhotoSearchService.hasApiKeyConfigured()) {
                    try {
                        List<PexelsHit> hits = pexelsService.search(pexelsQ, 20);
                        double fallback = normalizePriceToBudget(fallbackEstimatedPriceDt(q), budgetDt);
                        int rank = 1;
                        for (PexelsHit h : hits) {
                            String img = h.largeUrl() != null && !h.largeUrl().isBlank() ? h.largeUrl() : h.previewUrl();
                            String title = buildFallbackCatalogName(q, rank++);
                            String desc = buildBundleDescriptionFromOption(title, q);
                            bundles.add(new SuggestedBundle(title, img, fallback, desc));
                        }
                    } catch (Exception ignored) {
                        // no suggestions
                    }
                }

                bundles = filterNovelBundles(bundles, 6);
                if (bundles.size() < 6 && suggestionRetryCount > 0 && PexelsPhotoSearchService.hasApiKeyConfigured()) {
                    try {
                        List<PexelsHit> hits = pexelsService.search(pexelsQ + " alternative", 20);
                        double fallback = normalizePriceToBudget(fallbackEstimatedPriceDt(q), budgetDt);
                        int rank = 20;
                        List<SuggestedBundle> extra = new ArrayList<>();
                        for (PexelsHit h : hits) {
                            String img = h.largeUrl() != null && !h.largeUrl().isBlank() ? h.largeUrl() : h.previewUrl();
                            String title = buildFallbackCatalogName(q, rank++);
                            String desc = buildBundleDescriptionFromOption(title, q);
                            extra.add(new SuggestedBundle(title, img, fallback, desc));
                        }
                        List<SuggestedBundle> merged = new ArrayList<>(bundles);
                        merged.addAll(extra);
                        bundles = filterNovelBundles(merged, 6);
                    } catch (Exception ignored) {
                        // keep current list
                    }
                }

                if (bundles.isEmpty()) {
                    return;
                }
                final List<SuggestedBundle> finalBundles = bundles;
                Platform.runLater(() -> {
                    if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this) {
                        return;
                    }
                    appendIntegratedSuggestionCardsBubble(
                        finalBundles,
                        b -> {
                            if (b == null) {
                                return;
                            }
                            selectedImageUrl = b.imageUrl();
                            prixText = String.format(Locale.US, "%.2f", b.price());
                            description = b.description();
                            if (b.title() != null && !b.title().isBlank()) {
                                nom = b.title().trim();
                            }
                            showReviewCardAndWaitChoice();
                        }
                    );
                });
            });
        }

        private List<SuggestedBundle> filterNovelBundles(List<SuggestedBundle> raw, int limit) {
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<SuggestedBundle> out = new ArrayList<>();
            for (SuggestedBundle b : raw) {
                if (b == null) {
                    continue;
                }
                String key = suggestionKey(b);
                if (seenSuggestionKeys.contains(key)) {
                    continue;
                }
                seenSuggestionKeys.add(key);
                out.add(b);
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        }

        private String suggestionKey(SuggestedBundle b) {
            String title = b.title() == null ? "" : b.title().trim().toLowerCase(Locale.ROOT);
            String img = b.imageUrl() == null ? "" : b.imageUrl().trim().toLowerCase(Locale.ROOT);
            String price = String.format(Locale.ROOT, "%.2f", b.price());
            return title + "|" + img + "|" + price;
        }

        private String buildBundleDescriptionFromOption(String optionTitle, String query) {
            String title = sanitizeProductTitle(optionTitle, query);
            String intent = normalizedSearchIntent == null || normalizedSearchIntent.isBlank()
                ? query
                : normalizedSearchIntent;
            return String.format(
                Locale.FRENCH,
                "%s. Modèle suggéré pour %s, avec des caractéristiques proches de votre besoin.",
                title,
                safe(intent).replace("qui presente", "").replace("qui présente", "").trim()
            );
        }

        private String sanitizeProductTitle(String rawTitle, String query) {
            String t = rawTitle == null ? "" : rawTitle.trim();
            if (!t.isBlank()) {
                String low = t.toLowerCase(Locale.ROOT);
                if (!low.contains("qui presente") && !low.contains("qui présente")) {
                    return t;
                }
            }
            String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
            return buildFallbackCatalogName(q, 1);
        }

        private String buildFallbackCatalogName(String query, int rank) {
            String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
            String suffix = rank > 1 ? " " + rank : "";
            if (q.contains("chargeur") && q.contains("hp")) {
                return "Chargeur HP 65W original" + suffix;
            }
            boolean laptopIntent = q.contains("ordinateur")
                || q.contains("laptop")
                || q.contains("macbook")
                || q.contains("chromebook")
                || containsWholeWordIgnoreCase(q, "portable");
            if (laptopIntent) {
                if (q.contains("hp")) {
                    return "Ordinateur portable HP" + suffix;
                }
                return "Ordinateur portable" + suffix;
            }
            if (q.contains("chaise")) {
                if (q.contains("blanc")) {
                    return "Chaise blanche ergonomique" + suffix;
                }
                if (q.contains("bois") || q.contains("marron") || q.contains("maron")) {
                    return "Chaise en bois marron" + suffix;
                }
                return "Chaise moderne" + suffix;
            }
            if (containsWholeWordIgnoreCase(q, "table")) {
                if (q.contains("marron") || q.contains("maron") || q.contains("bois")) {
                    return "Table marron en bois" + suffix;
                }
                return "Table design" + suffix;
            }
            if (q.contains("pull") && q.contains("real madrid")) {
                return "Pull Real Madrid officiel" + suffix;
            }
            return "Produit suggéré" + suffix;
        }

        private void triggerImageSuggestionAsync() {
            String q = buildSuggestionQuery();
            String pexelsQ = buildPexelsQuery(q);
            executor.submit(() -> {
                try {
                    if (SerpApiPriceSuggestionService.hasApiKeyConfigured()) {
                        try {
                            List<SerpApiPriceSuggestionService.ProductOption> options = priceSuggestionService.searchJumiaOptions(q, 6);
                            if (options.isEmpty()) {
                                options = priceSuggestionService.searchProductOptions(q, 6);
                            }
                            if (!options.isEmpty()) {
                                List<SerpApiPriceSuggestionService.ProductOption> finalOptions = options;
                                Platform.runLater(() -> appendMarketOptionsBubble(finalOptions, selected -> {
                                    if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this || selected == null) {
                                        return;
                                    }
                                    selectedImageUrl = selected.imageUrl();
                                    prixText = String.format(Locale.US, "%.2f", selected.price());
                                    showReviewCardAndWaitChoice();
                                }));
                                return;
                            }
                        } catch (Exception ignored) {
                            // fallback Pexels
                        }
                    }
                    if (!PexelsPhotoSearchService.hasApiKeyConfigured()) {
                        return;
                    }
                    List<PexelsHit> hits = pexelsService.search(pexelsQ, 3);
                    if (hits.isEmpty()) {
                        return;
                    }
                    List<Double> estimated = new ArrayList<>();
                    if (SerpApiPriceSuggestionService.hasApiKeyConfigured()) {
                        try {
                            var options = priceSuggestionService.searchProductOptions(q, hits.size());
                            for (var o : options) {
                                estimated.add(o.price());
                            }
                        } catch (Exception ignored) {
                            // keep empty/partial estimates
                        }
                        // Si aucun prix par produit n'est disponible, on applique au moins une estimation médiane globale.
                        if (estimated.isEmpty()) {
                            try {
                                SerpApiPriceSuggestionService.PriceSuggestion s = priceSuggestionService.suggest(q);
                                if (s != null && s.median() > 0) {
                                    for (int i = 0; i < hits.size(); i++) {
                                        estimated.add(s.median());
                                    }
                                }
                            } catch (Exception ignored) {
                                // keep empty estimates
                            }
                        }
                    }
                    if (estimated.isEmpty()) {
                        double fallback = fallbackEstimatedPriceDt(q);
                        for (int i = 0; i < hits.size(); i++) {
                            estimated.add(fallback);
                        }
                    }
                    while (estimated.size() < hits.size()) {
                        estimated.add(estimated.isEmpty() ? fallbackEstimatedPriceDt(q) : estimated.get(0));
                    }
                    Platform.runLater(() -> {
                        appendPhotoSuggestionsBubble(hits, estimated, selectedPrice -> {
                            if (ProductAssistantPanel.this.wizard != ProductCreationWizard.this) {
                                return;
                            }
                            if (selectedPrice == null || selectedPrice <= 0) {
                                return;
                            }
                            prixText = String.format(Locale.US, "%.2f", selectedPrice);
                            showReviewCardAndWaitChoice();
                        });
                    });
                } catch (Exception ignored) {
                    // suggestion optionnelle
                }
            });
        }

        private String buildSuggestionQuery() {
            String catHint = categorie == null || categorie.isBlank() ? "" : categorie.replace('_', ' ');
            String intent = normalizedSearchIntent == null ? "" : normalizedSearchIntent.trim();
            if (!intent.isBlank()) {
                return (intent + " " + catHint).trim();
            }
            return (safe(nom) + " " + safe(userShortDescription) + " " + catHint).trim();
        }

        private String buildPexelsQuery(String query) {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (q.isBlank()) {
                return "product";
            }
            String color = "";
            if (q.contains("noir") || q.contains("black")) {
                color = "black ";
            } else if (q.contains("blanc") || q.contains("white")) {
                color = "white ";
            } else if (q.contains("rouge") || q.contains("red")) {
                color = "red ";
            } else if (q.contains("bleu") || q.contains("blue")) {
                color = "blue ";
            }

            if (q.contains("sac") || q.contains("bag") || q.contains("sac a dos") || q.contains("sac à dos")) {
                return (color + "bag fashion product").trim();
            }
            if (q.contains("chaise")) {
                return (color + "chair furniture product").trim();
            }
            if (q.contains("table")) {
                return (color + "table furniture product").trim();
            }
            if (q.contains("ordinateur") || q.contains("portable") || q.contains("laptop") || q.contains("pc")) {
                return (color + "laptop computer product").trim();
            }
            if (q.contains("pull") || q.contains("t-shirt") || q.contains("tshirt") || q.contains("maillot")) {
                return (color + "clothing product").trim();
            }
            return q;
        }

        private String inferSearchIntentForSuggestions() {
            String raw = userShortDescription == null ? "" : userShortDescription.trim();
            String n = nom == null ? "" : nom.trim();
            String seed = (n + " " + raw).trim();
            String low = seed.toLowerCase(Locale.ROOT);
            if ((low.equals("jean noir") || low.contains("jean noir")) && !low.contains("livre")) {
                return "pantalon en jean noir adulte";
            }
            String local = inferIntentWithRules(low);
            if (!local.isBlank()) {
                return local;
            }
            if (!GroqChatCompletionService.hasApiKeyConfigured()) {
                return seed;
            }
            try {
                String sys = """
                    Tu reformules une requête produit e-commerce pour la recherche d'images et de produits similaires.
                    Réponds avec UNE seule ligne courte (3 à 8 mots), précise et concrète.
                    Si "jean" désigne le vêtement, écris explicitement "pantalon en jean".
                    N'ajoute aucun commentaire.
                    """;
                String user = "Nom: " + (n.isBlank() ? "—" : n) + "\n"
                    + "Description: " + (raw.isBlank() ? "—" : raw) + "\n"
                    + "Catégorie: " + ProductFormUi.resolveCategoryChoice(categorie).getLabel();
                String line = groqService.completeWithSystem(
                    List.of(new GroqChatMessage("user", user)),
                    sys,
                    0.2,
                    0.85,
                    40
                );
                String cleaned = line == null ? "" : line.replace('\n', ' ').trim();
                return cleaned.isBlank() ? seed : cleaned;
            } catch (Exception ignored) {
                return seed;
            }
        }

        private String inferIntentWithRules(String lowerText) {
            if (lowerText == null || lowerText.isBlank()) {
                return "";
            }
            String t = lowerText
                .replace("je veux creer", "")
                .replace("je veux créer", "")
                .replace("je veux", "")
                .replace("qui presente", "")
                .replace("qui présente", "")
                .replace("genre", "")
                .replace("qq chose comme", "")
                .replace("un produit", "")
                .replace("mon propre produit", "")
                .replaceAll("\\s+", " ")
                .trim();

            boolean sportswear = t.contains("real madrid") || t.contains("fc barcelone") || t.contains("psg");
            if (sportswear && (t.contains("pull") || t.contains("maillot") || t.contains("tshirt") || t.contains("t-shirt"))) {
                if (t.contains("real madrid")) {
                    return "pull real madrid officiel adulte";
                }
                if (t.contains("fc barcelone")) {
                    return "pull fc barcelone officiel adulte";
                }
                if (t.contains("psg")) {
                    return "pull psg officiel adulte";
                }
            }
            if (t.contains("chargeur") && t.contains("hp")) {
                return "chargeur pc portable hp 65w original";
            }
            if (t.contains("sac") || t.contains("bag")) {
                if (t.contains("noir")) {
                    return "sac noir";
                }
                return "sac";
            }
            boolean laptopIntent = t.contains("ordinateur")
                || t.contains("laptop")
                || t.contains("macbook")
                || t.contains("chromebook")
                || containsWholeWordIgnoreCase(t, "portable");
            if (laptopIntent) {
                if (t.contains("hp")) {
                    return "ordinateur portable hp";
                }
                return "ordinateur portable";
            }
            if ((containsWholeWordIgnoreCase(t, "table") || containsWholeWordIgnoreCase(t, "bureau"))
                && (t.contains("marron") || t.contains("maron") || t.contains("bois"))) {
                return "table marron en bois";
            }
            if (t.contains("pull") && t.contains("real madrid")) {
                return "pull real madrid adulte";
            }
            return "";
        }

        private Double extractBudgetDt(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            String t = text.toLowerCase(Locale.ROOT).replace(',', '.');
            if (!(t.contains("budget") || t.contains("dinar") || t.contains("dinars") || t.contains("dt"))) {
                return null;
            }
            Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(t);
            while (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1));
                    if (v > 0) {
                        return v;
                    }
                } catch (Exception ignored) {
                    // next
                }
            }
            return null;
        }

        private List<SerpApiPriceSuggestionService.ProductOption> filterOptionsByBudget(
            List<SerpApiPriceSuggestionService.ProductOption> options,
            Double budget
        ) {
            if (options == null || options.isEmpty() || budget == null || budget <= 0) {
                return options == null ? List.of() : options;
            }
            double min = Math.max(1.0, budget * 0.7);
            double max = budget * 1.3;
            List<SerpApiPriceSuggestionService.ProductOption> inRange = new ArrayList<>();
            for (var o : options) {
                if (o == null || o.price() <= 0) {
                    continue;
                }
                if (o.price() >= min && o.price() <= max) {
                    inRange.add(o);
                }
            }
            return inRange.isEmpty() ? options : inRange;
        }

        private double normalizePriceToBudget(double price, Double budget) {
            if (budget == null || budget <= 0) {
                return price;
            }
            if (price <= 0) {
                return budget;
            }
            double min = Math.max(1.0, budget * 0.7);
            double max = budget * 1.3;
            if (price < min || price > max) {
                return budget;
            }
            return price;
        }

        private double fallbackEstimatedPriceDt(String query) {
            String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
            if (q.contains("jean") || q.contains("pantalon") || q.contains("pull") || q.contains("t-shirt")) {
                return 89.90;
            }
            if (q.contains("cafe") || q.contains("café") || q.contains("tasse") || q.contains("mug")) {
                return 24.90;
            }
            if (q.contains("chargeur") || q.contains("iphone") || q.contains("casque") || q.contains("ecouteur") || q.contains("écouteur")) {
                return 39.90;
            }
            if (q.contains("ordinateur") || q.contains("laptop") || q.contains("macbook") || containsWholeWordIgnoreCase(q, "portable")) {
                return 1199.00;
            }
            if (q.contains("chaise") || containsWholeWordIgnoreCase(q, "table") || containsWholeWordIgnoreCase(q, "bureau")) {
                return 149.00;
            }
            return 59.90;
        }

        private String buildEnhancedDescription(String shortDesc, String extraHint, String categoryDb) {
            String user = safe(shortDesc).equals("—") ? "" : shortDesc.trim();
            String extra = safe(extraHint).equals("—") ? "" : extraHint.trim();

            if (isSubstantialProductDescription(user)) {
                if (!extra.isBlank()) {
                    return (user + "\n\nPrécision : " + extra).trim();
                }
                String core = appendDescriptionAdvisoryIfMissing(user);
                if (descriptionVariantIndex > 0) {
                    return core
                        + "\n\nSi vous souhaitez une autre tournure, écrivez « non » suivi de votre consigne (ex. « non, plus court »).";
                }
                return core;
            }

            String base = ProductDescriptionSuggest.generate(safe(nom), safeCategory(categoryDb));
            return switch (descriptionVariantIndex % 3) {
                case 1 -> buildVariantOne(base, user, extra);
                case 2 -> buildVariantTwo(base, user, extra);
                default -> buildVariantZero(base, user, extra);
            };
        }

        private String buildEnhancedDescriptionVariantFromUser(String shortDesc, int variant, String categoryDb) {
            String base = ProductDescriptionSuggest.generate(safe(nom), safeCategory(categoryDb));
            String user = safe(shortDesc).equals("—") ? "" : shortDesc.trim();
            return switch (variant) {
                case 1 -> buildVariantOne(base, user, "");
                case 2 -> buildVariantTwo(base, user, "");
                default -> buildVariantZero(base, user, "");
            };
        }

        private String safeCategory(String rawDbCategory) {
            if (rawDbCategory == null || rawDbCategory.isBlank()) {
                return ProductFormUi.DEFAULT_CATEGORY;
            }
            return ProductFormUi.resolveCategoryChoice(rawDbCategory).getDbValue();
        }

        private String buildVariantZero(String base, String user, String extra) {
            StringBuilder sb = new StringBuilder();
            sb.append(base);
            if (!user.isBlank()) {
                sb.append("\n\nDescription utilisateur : ").append(user);
                if (!user.endsWith(".") && !user.endsWith("!") && !user.endsWith("?")) {
                    sb.append(".");
                }
            }
            if (!extra.isBlank()) {
                sb.append("\nAjustement demandé : ").append(extra);
                if (!extra.endsWith(".") && !extra.endsWith("!") && !extra.endsWith("?")) {
                    sb.append(".");
                }
            }
            return sb.toString().trim();
        }

        private String buildVariantOne(String base, String user, String extra) {
            StringBuilder sb = new StringBuilder();
            sb.append("Présentation proposée :\n");
            sb.append(base).append("\n");
            if (!user.isBlank()) {
                sb.append("\nDétails : ").append(user);
                if (!user.endsWith(".") && !user.endsWith("!") && !user.endsWith("?")) {
                    sb.append(".");
                }
            }
            if (!extra.isBlank()) {
                sb.append("\nAjustement pris en compte : ").append(extra);
                if (!extra.endsWith(".") && !extra.endsWith("!") && !extra.endsWith("?")) {
                    sb.append(".");
                }
            }
            return sb.toString().trim();
        }

        private String buildVariantTwo(String base, String user, String extra) {
            StringBuilder sb = new StringBuilder();
            sb.append("Produit : ").append(safe(nom)).append("\n\n");
            sb.append("Description : ").append(base);
            if (!user.isBlank()) {
                sb.append("\n\nÀ retenir : ").append(user);
                if (!user.endsWith(".") && !user.endsWith("!") && !user.endsWith("?")) {
                    sb.append(".");
                }
            }
            if (!extra.isBlank()) {
                sb.append("\nÀ corriger : ").append(extra);
                if (!extra.endsWith(".") && !extra.endsWith("!") && !extra.endsWith("?")) {
                    sb.append(".");
                }
            }
            return sb.toString().trim();
        }
    }

    private record ExternalProductDraft(String name, String description, String imageUrl) {
    }

    private static String extractFirstHttpUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            String url = m.group();
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith(";") || url.endsWith(")")) {
                url = url.substring(0, url.length() - 1);
            }
            return url.trim();
        }
        return null;
    }

    private enum Step {
        NAME,
        DESCRIPTION,
        CATEGORY_CLASSIFYING,
        DESCRIPTION_CHOICE,
        DESCRIPTION_REVIEW,
        AWAITING_CATEGORY_GROQ,
        PRICE,
        CONFIRM
    }

    private static String buildProductCategoryClassifierSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un classifieur pour le catalogue AutiCare (produits autisme / TDAH / accompagnement).\n");
        sb.append("Tu reçois le nom et la description d’un produit.\n");
        sb.append("Réponds par UNE SEULE ligne : l’identifiant EXACT (sans guillemets) parmi :\n");
        for (ProductFormUi.ProductCategoryChoice c : ProductFormUi.getProductCategories()) {
            sb.append(c.getDbValue()).append("  (").append(c.getLabel()).append(")\n");
        }
        sb.append("Choisis la catégorie la plus pertinente. Aucune autre phrase, aucune explication.");
        return sb.toString();
    }

    private static String extractProductHintFromCreateRequest(String rawMsg, String lowerMsg) {
        if (rawMsg == null || rawMsg.isBlank()) {
            return "";
        }
        String src = rawMsg.trim();
        String low = lowerMsg == null ? src.toLowerCase(Locale.ROOT) : lowerMsg;
        int idx = low.indexOf("produit");
        if (idx < 0) {
            String afterJeVeuxCreer = src.replaceFirst("(?i)^\\s*je\\s+veux\\s+(créer|creer)\\s+", "").trim();
            if (afterJeVeuxCreer.length() >= 2 && afterJeVeuxCreer.length() < src.length()) {
                return afterJeVeuxCreer;
            }
            return "";
        }
        int start = Math.min(src.length(), idx + "produit".length());
        String tail = src.substring(start).trim();
        tail = tail.replaceFirst("^[\\s:;,.\\-]+", "").trim();
        // Nettoyage de formulations communes
        tail = tail.replaceFirst("^(que\\s+)?je\\s+veux\\s+", "").trim();
        tail = tail.replaceFirst("^mon\\s+propre\\s+", "").trim();
        tail = tail.replaceFirst("^un\\s+", "").trim();
        tail = tail.replaceFirst("^une\\s+", "").trim();
        if (tail.length() < 2) {
            return "";
        }
        return tail;
    }

    /**
     * Interprète la sortie modèle (une ligne ou courte) et renvoie une clé {@code produit.categorie} connue.
     */
    /**
     * Texte déjà rédigé (plusieurs phrases, paragraphe, etc.) : on le garde comme corps de fiche plutôt que le modèle « nom + sensoriels ».
     */
    private static boolean isSubstantialProductDescription(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.length() >= 120) {
            return true;
        }
        if (t.length() < 50) {
            return false;
        }
        int sentenceEnds = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                sentenceEnds++;
            }
        }
        if (sentenceEnds >= 2) {
            return true;
        }
        return t.contains("\n") && t.length() >= 55;
    }

    private static String appendDescriptionAdvisoryIfMissing(String userText) {
        String u = userText.trim();
        if (u.isEmpty()) {
            return u;
        }
        String low = u.toLowerCase(Locale.FRENCH);
        if (low.contains("vérifiez que le matériau") || low.contains("verifiez que le materiau")) {
            return u;
        }
        return u + "\n\n"
            + "Vérifiez que le matériau et l’usage conviennent à la personne concernée ; adaptez le texte si besoin.";
    }

    private static String mapGroqCategoryLineToDbKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProductFormUi.DEFAULT_CATEGORY;
        }
        String t = raw.trim().replace('`', ' ').replace('*', ' ').trim();
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).trim();
        }
        String lower = t.toLowerCase(Locale.ROOT);
        for (ProductFormUi.ProductCategoryChoice c : ProductFormUi.getProductCategories()) {
            String k = c.getDbValue().toLowerCase(Locale.ROOT);
            if (lower.equals(k) || lower.contains(k)) {
                return c.getDbValue();
            }
        }
        for (ProductFormUi.ProductCategoryChoice c : ProductFormUi.getProductCategories()) {
            String lab = c.getLabel();
            if (lab != null && !lab.isBlank() && lower.contains(lab.toLowerCase(Locale.FRENCH))) {
                return c.getDbValue();
            }
        }
        return ProductFormUi.DEFAULT_CATEGORY;
    }

    private static String buildProductDescriptionSuggestionsSystemPrompt() {
        return """
            Tu es un rédacteur e-commerce.
            Génère exactement 3 descriptions produit réalistes et professionnelles, en français.
            Contraintes obligatoires :
            - 2 à 3 phrases maximum par description
            - ton naturel, clair, crédible (pas de blabla IA)
            - détails concrets si pertinents : compatibilité, fonctionnalités, matériaux, usage réel
            - évite les formulations vagues comme « pensé pour le confort » et « usage quotidien »
            Format de sortie strict :
            [DESC_1]
            texte...
            [/DESC_1]
            [DESC_2]
            texte...
            [/DESC_2]
            [DESC_3]
            texte...
            [/DESC_3]
            N'ajoute rien d'autre.
            """;
    }

    private static List<String> parseGroqDescriptionBlocks(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String t = raw.trim();
        for (int i = 1; i <= 3; i++) {
            String start = "[DESC_" + i + "]";
            String end = "[/DESC_" + i + "]";
            int s = t.indexOf(start);
            int e = t.indexOf(end);
            if (s >= 0 && e > s) {
                String block = t.substring(s + start.length(), e).trim();
                if (!block.isBlank()) {
                    out.add(block);
                }
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        String[] chunks = t.split("\\n\\s*\\n");
        for (String c : chunks) {
            String x = c == null ? "" : c.trim();
            if (!x.isBlank()) {
                out.add(x);
            }
            if (out.size() >= 3) {
                break;
            }
        }
        return out;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private StackPane buildAssistantHeaderAvatar() {
        StackPane host = new StackPane();
        host.setMinSize(72, 72);
        host.setPrefSize(72, 72);
        host.setMaxSize(72, 72);
        host.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 999; "
                + "-fx-border-color: #dbeafe; -fx-border-width: 1.0; -fx-border-radius: 999;"
        );

        Image avatar = loadImageFromLocalPath(CHATBOT_HEADER_AVATAR_FILE);
        if (avatar != null && !avatar.isError()) {
            ImageView iv = new ImageView(avatar);
            iv.setFitWidth(66);
            iv.setFitHeight(66);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            host.getChildren().add(iv);
        } else {
            Label fallback = new Label("🤖");
            fallback.setStyle("-fx-font-size: 24px;");
            host.getChildren().add(fallback);
        }
        return host;
    }

    private static Image loadImageFromLocalPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        try {
            Path p = Path.of(rawPath.trim());
            return new Image(p.toUri().toString(), false);
        } catch (Exception ex) {
            return null;
        }
    }

    private void onAppend() {
        if (onAppendAssistantText == null || lastAssistantText == null || lastAssistantText.isBlank()) {
            return;
        }
        onAppendAssistantText.accept(lastAssistantText);
    }

    private void onSearchUnsplash() {
        if (mode.hideUnsplashSection()) {
            return;
        }
        if (!UnsplashPhotoSearchService.hasAccessKeyConfigured()) {
            alert(Alert.AlertType.WARNING, "Unsplash", "Clé d’accès Unsplash absente.");
            return;
        }
        String q = unsplashKeywordField.getText() == null ? "" : unsplashKeywordField.getText().trim();
        if (q.length() < 2) {
            alert(Alert.AlertType.WARNING, "Recherche", "Au moins 2 caractères.");
            return;
        }
        searchPhotosBtn.setDisable(true);
        executor.submit(() -> {
            try {
                List<UnsplashHit> hits = unsplashService.search(q, 8);
                Platform.runLater(() -> {
                    unsplashResultsPane.getChildren().clear();
                    for (UnsplashHit h : hits) {
                        ImageView iv = new ImageView(new Image(h.thumbUrl(), 88, 88, true, true, true));
                        iv.setPreserveRatio(true);
                        Label cap = new Label(truncate(h.caption(), 36));
                        cap.setWrapText(true);
                        cap.setMaxWidth(92);
                        cap.setAlignment(Pos.TOP_CENTER);
                        cap.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
                        VBox card = new VBox(4, iv, cap);
                        card.setAlignment(Pos.TOP_CENTER);
                        card.setStyle("-fx-cursor: hand;");
                        card.setOnMouseClicked(ev -> openBrowser(h.pageUrl()));
                        unsplashResultsPane.getChildren().add(card);
                    }
                    searchPhotosBtn.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    alert(Alert.AlertType.ERROR, "Unsplash", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                    searchPhotosBtn.setDisable(false);
                });
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isBlank()) {
            return "Photo";
        }
        String t = s.trim().replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }

    private static void openBrowser(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // —
        }
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
