package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.utils.AppState;
import org.example.utils.PublicRdvDoctorSidebarHelper;
import org.example.utils.RdvPublicBookingStepper;

/**
 * Étape « Type de consultation » après le choix du créneau.
 */
public class PageRdvTypeController implements PublicShellAware {

    private PublicShellController shell;

    @FXML
    private HBox typeStepperBox;
    @FXML
    private Label typeAvatarInitials;
    @FXML
    private Label typeSidebarName;
    @FXML
    private Label typeSidebarBio;
    @FXML
    private Label typeSidebarPhone;
    @FXML
    private Label typeSidebarEmail;
    @FXML
    private ComboBox<String> typeVoiceLang;
    @FXML
    private VBox typeOptionsBox;
    @FXML
    private ComboBox<String> typeMotifCombo;

    private ToggleGroup consultTypeGroup;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        buildStepper();
        if (typeVoiceLang != null) {
            typeVoiceLang.getItems().setAll("Français", "English");
            typeVoiceLang.getSelectionModel().selectFirst();
        }
        PublicRdvDoctorSidebarHelper.populate(
                AppState.getPendingPublicRdvDoctorId(),
                AppState.getPendingPublicRdvDoctorName(),
                typeSidebarName,
                typeAvatarInitials,
                typeSidebarBio,
                typeSidebarPhone,
                typeSidebarEmail);
        buildConsultTypeOptions();
        if (typeMotifCombo != null) {
            typeMotifCombo.getItems().setAll(
                    "Diagnostic TSA",
                    "Évaluation initiale",
                    "Bilan / orientation",
                    "Suivi thérapeutique",
                    "Urgence / besoin immédiat",
                    "Autre");
        }
    }

    private void buildStepper() {
        RdvPublicBookingStepper.fill(typeStepperBox, 1);
    }

    private void buildConsultTypeOptions() {
        if (typeOptionsBox == null) {
            return;
        }
        typeOptionsBox.getChildren().clear();
        consultTypeGroup = new ToggleGroup();
        String[][] rows = {
                {"Première consultation", "60 min"},
                {"Bilan complet", "90 min"},
                {"Consultation de suivi", "45 min"},
                {"Consultation urgente", "30 min"}
        };
        for (String[] row : rows) {
            HBox line = new HBox(14);
            line.setAlignment(Pos.CENTER_LEFT);
            line.getStyleClass().add("rdv-type-option");
            RadioButton rb = new RadioButton();
            rb.setToggleGroup(consultTypeGroup);
            rb.setMnemonicParsing(false);
            rb.getStyleClass().add("rdv-type-option-radio");
            VBox textCol = new VBox(0);
            Label title = new Label(row[0]);
            title.getStyleClass().add("rdv-type-option-title");
            Label dur = new Label("Durée : " + row[1]);
            dur.getStyleClass().add("rdv-type-option-dur");
            textCol.getChildren().addAll(title, dur);
            line.getChildren().addAll(rb, textCol);
            line.setOnMouseClicked(e -> rb.setSelected(true));
            typeOptionsBox.getChildren().add(line);
        }
        consultTypeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            for (var n : typeOptionsBox.getChildren()) {
                if (n instanceof HBox h) {
                    h.getStyleClass().remove("rdv-type-option-selected");
                }
            }
            if (newT instanceof RadioButton rb && rb.getParent() instanceof HBox h) {
                h.getStyleClass().add("rdv-type-option-selected");
            }
        });
        if (!typeOptionsBox.getChildren().isEmpty()
                && typeOptionsBox.getChildren().get(0) instanceof HBox first
                && !first.getChildren().isEmpty()
                && first.getChildren().get(0) instanceof RadioButton rb) {
            rb.setSelected(true);
            first.getStyleClass().add("rdv-type-option-selected");
        }
    }

    @FXML
    private void onVoiceAssist() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Assistant vocal");
        a.setHeaderText(null);
        a.setContentText("Fonction « Parler pour prendre RDV » : branchement à prévoir (reconnaissance vocale / API).");
        a.showAndWait();
    }

    @FXML
    private void onBackToList() {
        AppState.clearPendingPublicRdvBooking();
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv");
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onRetourCreneau() {
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv-booking");
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onContinuer() {
        Toggle sel = consultTypeGroup != null ? consultTypeGroup.getSelectedToggle() : null;
        if (sel == null) {
            return;
        }
        if (typeMotifCombo == null || typeMotifCombo.getSelectionModel().getSelectedItem() == null) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Motif");
            a.setHeaderText(null);
            a.setContentText("Veuillez sélectionner un motif de consultation.");
            a.showAndWait();
            return;
        }
        RadioButton rb = (RadioButton) sel;
        String typeLabel = "";
        if (rb.getParent() instanceof HBox h && h.getChildren().size() > 1
                && h.getChildren().get(1) instanceof VBox col
                && !col.getChildren().isEmpty()
                && col.getChildren().get(0) instanceof Label l) {
            typeLabel = l.getText();
        }
        String motif = typeMotifCombo.getSelectionModel().getSelectedItem();
        AppState.setPendingPublicRdvConsultTypeLabel(typeLabel);
        AppState.setPendingPublicRdvMotif(motif);
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv-info");
        } catch (Exception ignored) {
        }
    }
}
