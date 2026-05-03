package org.example.ui.product;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.services.GroqAssistantContext;

/**
 * Fenêtre modale « Assistant AutiCare » pour la création de produit (trois colonnes + chat Groq).
 */
public final class ProductCreationChatStage {

    private ProductCreationChatStage() {
    }

    public static void show(Window owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Assistant AutiCare — Création de produit");
        stage.setMinWidth(1180);
        stage.setMinHeight(620);

        ProductAssistantPanel chat = new ProductAssistantPanel(
            GroqAssistantContext.UNIVERSAL_ASSISTANT,
            null,
            null,
            AssistantPanelMode.modalProductCreation(
                null,
                null
            )
        );
        VBox.setVgrow(chat, Priority.ALWAYS);

        Button newChatBtn = new Button("+ Nouveau chat");
        newChatBtn.setMaxWidth(Double.MAX_VALUE);
        newChatBtn.setMnemonicParsing(false);
        newChatBtn.setStyle(
            "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; "
                + "-fx-background-radius: 8; -fx-padding: 12 14; -fx-cursor: hand;"
        );

        Label convHeader = new Label("VOS CONVERSATIONS");
        convHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #475569;");

        ListView<String> convList = new ListView<>(FXCollections.observableArrayList("Bonjour"));
        convList.setPrefHeight(360);
        convList.setMinHeight(120);
        convList.setPlaceholder(new Label("Aucune conversation"));
        convList.setStyle(
            "-fx-background-color: #f8fafc; -fx-control-inner-background: #f8fafc; "
                + "-fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;"
        );
        convList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                    return;
                }
                setText(item);
                setGraphic(null);
                setStyle(isSelected()
                    ? "-fx-background-color: #dbeafe; -fx-text-fill: #1e3a8a; -fx-font-weight: 700; "
                        + "-fx-background-radius: 8; -fx-padding: 9 10;"
                    : "-fx-background-color: #ffffff; -fx-text-fill: #0f172a; "
                        + "-fx-background-radius: 8; -fx-padding: 9 10;");
            }
        });
        convList.getSelectionModel().selectFirst();

        final int[] convCounter = {1};
        newChatBtn.setOnAction(e -> {
            chat.resetConversation();
            convCounter[0]++;
            convList.getItems().add(0, "Conversation " + convCounter[0]);
            convList.getSelectionModel().select(0);
        });

        VBox leftCol = new VBox(12, newChatBtn, new Separator(), convHeader, convList);
        leftCol.setPadding(new Insets(12));
        leftCol.setPrefWidth(220);
        leftCol.setMinWidth(200);
        leftCol.setMaxWidth(240);
        leftCol.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-border-color: #e2e8f0; "
                + "-fx-border-radius: 12; -fx-border-width: 1;"
        );
        VBox.setVgrow(convList, Priority.ALWAYS);

        VBox centerCard = new VBox(0, chat);
        centerCard.setPadding(new Insets(16));
        centerCard.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-border-color: #e2e8f0; "
                + "-fx-border-radius: 12; -fx-border-width: 1;"
        );
        centerCard.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(centerCard, Priority.ALWAYS);

        HBox mainRow = new HBox(16, leftCol, centerCard);
        mainRow.setPadding(new Insets(16));
        mainRow.setFillHeight(true);
        HBox.setHgrow(centerCard, Priority.ALWAYS);

        BorderPane root = new BorderPane(mainRow);
        root.setStyle("-fx-background-color: #eef2f7;");

        Scene scene = new Scene(root, 1260, 760);
        stage.setScene(scene);
        stage.setOnHidden(e -> chat.shutdown());
        stage.show();
    }

}
