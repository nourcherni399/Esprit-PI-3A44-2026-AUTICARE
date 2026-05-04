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

import java.util.List;

/**
 * Étape « Type de consultation » après le choix du créneau.
 */
public class PageRdvTypeController implements PublicShellAware {

    private static final int MOTIF_MAX_LEN = 255;

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
            typeMotifCombo.setEditable(true);
            refreshMotifSuggestionsForSelection();
        }
    }

    /** Texte saisi ou choisi (ComboBox éditable : l'éditeur prime). */
    private static String resolveMotifText(ComboBox<String> combo) {
        if (combo == null) {
            return "";
        }
        if (combo.getEditor() != null && combo.getEditor().getText() != null) {
            String t = combo.getEditor().getText().trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        String v = combo.getValue();
        return v != null ? v.trim() : "";
    }

    private static String resolveConsultTypeLabel(Toggle selectedToggle) {
        if (!(selectedToggle instanceof RadioButton rb)) {
            return "";
        }
        if (!(rb.getParent() instanceof HBox h) || h.getChildren().size() <= 1) {
            return "";
        }
        if (!(h.getChildren().get(1) instanceof VBox col) || col.getChildren().isEmpty()) {
            return "";
        }
        if (!(col.getChildren().get(0) instanceof Label l)) {
            return "";
        }
        return l.getText() != null ? l.getText().trim() : "";
    }

    private static List<String> suggestionsForConsultType(String typeLabel) {
        return switch (typeLabel) {
            case "Première consultation" -> List.of(
                    "Premier rendez-vous",
                    "Évaluation initiale",
                    "Comprendre les symptômes",
                    "Orientation / avis médical",
                    "Bilan de départ");
            case "Bilan complet" -> List.of(
                    "Bilan complet",
                    "Bilan neuro-développemental",
                    "Bilan comportemental",
                    "Réévaluation de la situation",
                    "Synthèse et orientation");
            case "Consultation de suivi" -> List.of(
                    "Suivi thérapeutique",
                    "Ajustement du traitement",
                    "Suivi post-consultation",
                    "Suivi scolaire / familial",
                    "Renouvellement document médical");
            case "Consultation urgente" -> List.of(
                    "Situation urgente",
                    "Crise ou aggravation",
                    "Soutien immédiat",
                    "Besoin de consultation rapide",
                    "Autre urgence");
            default -> List.of(
                    "Premier rendez-vous",
                    "Suivi ou contrôle",
                    "Bilan / orientation",
                    "Question ponctuelle",
                    "Document médical");
        };
    }

    private void refreshMotifSuggestionsForSelection() {
        if (typeMotifCombo == null) {
            return;
        }
        String currentText = resolveMotifText(typeMotifCombo);
        String selectedType = resolveConsultTypeLabel(consultTypeGroup != null ? consultTypeGroup.getSelectedToggle() : null);
        List<String> suggestions = suggestionsForConsultType(selectedType);
        typeMotifCombo.getItems().setAll(suggestions);
        if (currentText.isBlank()) {
            typeMotifCombo.setValue(null);
            if (typeMotifCombo.getEditor() != null) {
                typeMotifCombo.getEditor().clear();
            }
            return;
        }
        if (typeMotifCombo.getEditor() != null) {
            typeMotifCombo.getEditor().setText(currentText);
            typeMotifCombo.getEditor().positionCaret(currentText.length());
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
            refreshMotifSuggestionsForSelection();
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
        String motif = resolveMotifText(typeMotifCombo);
        if (motif.isBlank()) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Motif");
            a.setHeaderText(null);
            a.setContentText("Indiquez en quelques mots le motif de votre consultation (vous pouvez "
                    + "choisir une suggestion ou écrire librement).");
            a.showAndWait();
            return;
        }
        if (motif.length() > MOTIF_MAX_LEN) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Motif");
            a.setHeaderText(null);
            a.setContentText("Le motif est trop long (" + motif.length() + " caractères). "
                    + "Réduisez à " + MOTIF_MAX_LEN + " caractères maximum.");
            a.showAndWait();
            return;
        }
        String typeLabel = resolveConsultTypeLabel(sel);
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