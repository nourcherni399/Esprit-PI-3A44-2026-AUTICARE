package org.example.utils;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Bandeau d’étapes du parcours RDV public (coches pour les étapes terminées).
 */
public final class RdvPublicBookingStepper {

    private static final String[] STEPS = {"Créneau", "Type", "Vos informations", "Confirmation"};

    private RdvPublicBookingStepper() {
    }

    /**
     * @param activeStepIndex index de l’étape courante (0 = Créneau, 1 = Type, …). Les étapes d’index
     *                        strictement inférieur sont affichées comme complétées (✓).
     */
    public static void fill(HBox stepperBox, int activeStepIndex) {
        if (stepperBox == null) {
            return;
        }
        stepperBox.getChildren().clear();
        for (int i = 0; i < STEPS.length; i++) {
            if (i > 0) {
                Label sep = new Label("·");
                sep.getStyleClass().add("rdv-step-sep");
                stepperBox.getChildren().add(sep);
            }
            HBox cell = new HBox(8);
            cell.setAlignment(Pos.CENTER_LEFT);
            cell.getStyleClass().add("rdv-step-cell");
            if (i < activeStepIndex) {
                Label check = new Label("✓");
                check.getStyleClass().add("rdv-step-num-done");
                Label cap = new Label(STEPS[i]);
                cap.getStyleClass().add("rdv-step-text-done");
                cell.getChildren().addAll(check, cap);
            } else if (i == activeStepIndex) {
                Label num = new Label(String.valueOf(i + 1));
                num.getStyleClass().add("rdv-step-num-active");
                Label cap = new Label(STEPS[i]);
                cap.getStyleClass().add("rdv-step-text-active");
                cell.getChildren().addAll(num, cap);
            } else {
                Label num = new Label(String.valueOf(i + 1));
                num.getStyleClass().add("rdv-step-num");
                Label cap = new Label(STEPS[i]);
                cap.getStyleClass().add("rdv-step-text");
                cell.getChildren().addAll(num, cap);
            }
            stepperBox.getChildren().add(cell);
        }
    }
}
