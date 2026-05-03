package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.services.LanguageToolService;
import org.example.services.LanguageToolService.SpellMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SpellCheckDialogController {

    @FXML private BorderPane dialogRoot;
    @FXML private VBox dialogCenter;
    @FXML private Label statusLabel;
    @FXML private VBox correctionsSection;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox correctionsContainer;
    @FXML private Label noErrorsLabel;
    @FXML private Button applyBtn;

    private Stage stage;
    private String originalText;
    private List<SpellMatch> matches;
    private final List<String> selectedSuggestions = new ArrayList<>();
    private Consumer<String> onApplyCallback;
    private String applyButtonLabel = "Appliquer et modifier l'article";

    public void setStage(Stage stage) {
        this.stage = stage;
        // Bind center VBox and scrollPane to stage height so they grow with the window
        if (stage != null && stage.getScene() != null) {
            bindToScene();
        }
    }

    private void bindToScene() {
        javafx.scene.Scene scene = stage.getScene();
        // ScrollPane fills the CENTER of the BorderPane, which fills the scene minus header/footer
        scrollPane.prefHeightProperty().bind(
            scene.heightProperty().subtract(180)
        );
        scrollPane.maxHeightProperty().bind(
            scene.heightProperty().subtract(180)
        );
    }

    public void setApplyButtonLabel(String label) {
        this.applyButtonLabel = label;
        if (applyBtn != null) applyBtn.setText(label);
    }

    public void setOnApplyCallback(Consumer<String> callback) {
        this.onApplyCallback = callback;
    }

    public void showLoading() {
        hide(correctionsSection);
        hide(noErrorsLabel);
        hide(applyBtn);
        show(statusLabel);
        statusLabel.setText("Vérification en cours…");
    }

    public void showResults(String text, List<SpellMatch> spellMatches) {
        this.originalText = text;
        this.matches = spellMatches;
        this.selectedSuggestions.clear();

        hide(statusLabel);
        correctionsContainer.getChildren().clear();

        if (spellMatches == null || spellMatches.isEmpty()) {
            hide(correctionsSection);
            show(noErrorsLabel);
            hide(applyBtn);
        } else {
            show(correctionsSection);
            hide(noErrorsLabel);
            applyBtn.setText(applyButtonLabel);
            show(applyBtn);

            // Initialize selected suggestions (default = best/first)
            for (SpellMatch m : spellMatches) {
                selectedSuggestions.add(m.bestSuggestion());
            }

            // Display in reading order (ascending offset)
            List<SpellMatch> displayOrder = spellMatches.stream()
                    .sorted((a, b) -> Integer.compare(a.offset(), b.offset()))
                    .toList();

            for (SpellMatch m : displayOrder) {
                int matchIdx = spellMatches.indexOf(m);
                correctionsContainer.getChildren().add(buildCorrectionRow(m, matchIdx));
            }
        }
    }

    public void showError(String errorMsg) {
        hide(correctionsSection);
        hide(noErrorsLabel);
        hide(applyBtn);
        show(statusLabel);
        statusLabel.setText("Erreur : " + errorMsg);
    }

    private VBox buildCorrectionRow(SpellMatch m, int matchIdx) {
        VBox row = new VBox(6);
        row.getStyleClass().add("spell-correction-row");
        row.setPadding(new Insets(8, 10, 8, 10));

        // ── Context snippet: "...avant [MOT] après..." ──
        if (originalText != null) {
            HBox contextBox = buildContextSnippet(m);
            row.getChildren().add(contextBox);
        }

        // ── Word → suggestion chips ──
        HBox topLine = new HBox(8);
        topLine.setAlignment(Pos.CENTER_LEFT);

        Label originalLbl = new Label(m.original());
        originalLbl.getStyleClass().add("spell-correction-original");
        originalLbl.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

        Label arrowLbl = new Label("→");
        arrowLbl.getStyleClass().add("spell-correction-arrow");
        arrowLbl.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

        FlowPane chips = new FlowPane(6, 4);
        chips.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(chips, Priority.ALWAYS);

        List<Button> chipButtons = new ArrayList<>();
        for (int si = 0; si < m.suggestions().size(); si++) {
            String sug = m.suggestions().get(si);
            Button chip = new Button(sug);
            chip.getStyleClass().add("spell-chip");
            if (si == 0) chip.getStyleClass().add("spell-chip-selected");
            chip.setOnAction(e -> {
                chipButtons.forEach(b -> b.getStyleClass().remove("spell-chip-selected"));
                chip.getStyleClass().add("spell-chip-selected");
                selectedSuggestions.set(matchIdx, sug);
            });
            chipButtons.add(chip);
            chips.getChildren().add(chip);
        }

        topLine.getChildren().addAll(originalLbl, arrowLbl, chips);

        // ── Message ──
        Label msgLbl = new Label(m.message());
        msgLbl.getStyleClass().add("spell-correction-message");
        msgLbl.setWrapText(true);

        row.getChildren().addAll(topLine, msgLbl);
        return row;
    }

    /**
     * Builds a context snippet line:
     *   "...texte avant [MOT_ERRONÉ] texte après..."
     * The erroneous word is shown in red, surrounding text in grey.
     */
    private HBox buildContextSnippet(SpellMatch m) {
        final int CONTEXT_CHARS = 40;
        int start = m.offset();
        int end = m.offset() + m.length();
        int len = originalText.length();

        int beforeStart = Math.max(0, start - CONTEXT_CHARS);
        int afterEnd    = Math.min(len, end + CONTEXT_CHARS);

        String before = originalText.substring(beforeStart, start)
                .replace('\n', ' ').stripLeading();
        String after  = originalText.substring(end, afterEnd)
                .replace('\n', ' ').stripTrailing();

        HBox snippet = new HBox(0);
        snippet.setAlignment(Pos.CENTER_LEFT);
        snippet.getStyleClass().add("spell-context-snippet");

        if (!before.isBlank()) {
            Label beforeLbl = new Label(before);
            beforeLbl.getStyleClass().add("spell-context-text");
            beforeLbl.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            snippet.getChildren().add(beforeLbl);
        }

        Label wordLbl = new Label(m.original());
        wordLbl.getStyleClass().add("spell-context-word");
        wordLbl.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        snippet.getChildren().add(wordLbl);

        if (!after.isBlank()) {
            Label afterLbl = new Label(after);
            afterLbl.getStyleClass().add("spell-context-text");
            snippet.getChildren().add(afterLbl);
        }

        return snippet;
    }

    @FXML
    private void onApply() {
        if (originalText == null || matches == null || matches.isEmpty()) {
            closeDialog();
            return;
        }
        String corrected = LanguageToolService.applyCorrections(originalText, matches, selectedSuggestions);
        if (onApplyCallback != null) onApplyCallback.accept(corrected);
        closeDialog();
    }

    @FXML
    private void onClose() {
        closeDialog();
    }

    private void closeDialog() {
        if (stage != null) stage.close();
    }

    private void show(javafx.scene.Node node) {
        node.setVisible(true);
        node.setManaged(true);
    }

    private void hide(javafx.scene.Node node) {
        node.setVisible(false);
        node.setManaged(false);
    }
}
