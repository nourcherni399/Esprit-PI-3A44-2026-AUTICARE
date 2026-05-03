package org.example.controllers;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.example.models.Availability;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formulaire « Nouvelle disponibilité » / modification (carte centrée, champs date-heure-durée).
 */
public class MedecinDisponibiliteDialogController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FunctionalInterface
    public interface SaveCallback {
        void onSave(LocalDateTime debut, LocalDateTime fin) throws Exception;
    }

    @FXML
    private Label titleLabel;
    @FXML
    private DatePicker datePicker;
    @FXML
    private ComboBox<String> startTimeCombo;
    @FXML
    private ComboBox<String> endTimeCombo;
    @FXML
    private Spinner<Integer> durationSpinner;
    @FXML
    private CheckBox disponibleCheckBox;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    private Stage stage;
    private SaveCallback saveCallback;
    private boolean syncing;
    private ChangeListener<String> startListener;
    private ChangeListener<String> endListener;
    private ChangeListener<Integer> durationListener;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        if (durationSpinner != null) {
            durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 12 * 60, 30, 15));
        }
        if (datePicker != null) {
            datePicker.setPromptText("jj/mm/aaaa");
            datePicker.setConverter(new StringConverter<>() {
                @Override
                public String toString(LocalDate d) {
                    return d == null ? "" : d.format(DATE_FR);
                }

                @Override
                public LocalDate fromString(String s) {
                    if (s == null || s.isBlank()) {
                        return null;
                    }
                    try {
                        return LocalDate.parse(s.trim(), DATE_FR);
                    } catch (Exception e) {
                        return null;
                    }
                }
            });
            datePicker.valueProperty().addListener((obs, prev, cur) -> {
                if (datePickerProgrammaticUpdate) {
                    return;
                }
                syncDatePickerEditor();
            });
        }
    }

    /** Évite que le listener ne réagisse pendant {@code prepare} (boucles / texte incohérent). */
    private boolean datePickerProgrammaticUpdate;

    /**
     * À appeler après {@code FXMLLoader.load()} : remplit le formulaire et définit l’action d’enregistrement.
     */
    public void prepare(Availability existing, LocalDate defaultDate, SaveCallback callback) {
        this.saveCallback = callback;
        List<String> slots = quarterHourSlots();
        startTimeCombo.getItems().setAll(slots);
        endTimeCombo.getItems().setAll(slots);

        if (titleLabel != null) {
            titleLabel.setText(existing == null ? "Nouvelle disponibilité" : "Modifier la disponibilité");
        }

        detachListeners();
        syncing = true;
        if (existing != null) {
            datePicker.setValue(existing.getDebut().toLocalDate());
            startTimeCombo.setValue(existing.getDebut().toLocalTime().format(TIME_FMT));
            endTimeCombo.setValue(existing.getFin().toLocalTime().format(TIME_FMT));
            int mins = (int) ChronoUnit.MINUTES.between(existing.getDebut(), existing.getFin());
            durationSpinner.getValueFactory().setValue(clampDuration(mins));
        } else {
            datePicker.setValue(defaultDate != null ? defaultDate : LocalDate.now());
            startTimeCombo.setValue("09:00");
            endTimeCombo.setValue("09:30");
            durationSpinner.getValueFactory().setValue(30);
        }
        if (disponibleCheckBox != null) {
            disponibleCheckBox.setSelected(true);
        }
        syncDatePickerEditor();
        syncing = false;
        attachListeners();
    }

    /** Le champ texte du DatePicker ne suit pas toujours {@code setValue} : on force l’affichage (jj/mm/aaaa). */
    private void syncDatePickerEditor() {
        if (datePicker == null) {
            return;
        }
        LocalDate v = datePicker.getValue();
        if (datePicker.getEditor() != null) {
            datePicker.getEditor().setText(v != null ? datePicker.getConverter().toString(v) : "");
        }
    }

    private void attachListeners() {
        startListener = (o, a, b) -> {
            if (!syncing) {
                applyDurationToEnd();
            }
        };
        durationListener = (o, a, b) -> {
            if (!syncing) {
                applyDurationToEnd();
            }
        };
        endListener = (o, a, b) -> {
            if (!syncing) {
                applyEndToDuration();
            }
        };
        startTimeCombo.valueProperty().addListener(startListener);
        endTimeCombo.valueProperty().addListener(endListener);
        durationSpinner.valueProperty().addListener(durationListener);
    }

    private void detachListeners() {
        if (startTimeCombo != null && startListener != null) {
            startTimeCombo.valueProperty().removeListener(startListener);
        }
        if (endTimeCombo != null && endListener != null) {
            endTimeCombo.valueProperty().removeListener(endListener);
        }
        if (durationSpinner != null && durationListener != null) {
            durationSpinner.valueProperty().removeListener(durationListener);
        }
    }

    private void applyDurationToEnd() {
        LocalTime start = parseTime(startTimeCombo.getValue());
        Integer d = durationSpinner.getValue();
        if (start == null || d == null) {
            return;
        }
        LocalTime end = start.plusMinutes(d);
        if (end.isBefore(start) || end.equals(start)) {
            end = start.plusMinutes(15);
        }
        syncing = true;
        endTimeCombo.setValue(end.format(TIME_FMT));
        syncing = false;
    }

    private void applyEndToDuration() {
        LocalTime start = parseTime(startTimeCombo.getValue());
        LocalTime end = parseTime(endTimeCombo.getValue());
        if (start == null || end == null || !end.isAfter(start)) {
            return;
        }
        int mins = (int) ChronoUnit.MINUTES.between(start, end);
        syncing = true;
        durationSpinner.getValueFactory().setValue(clampDuration(mins));
        syncing = false;
    }

    private static int clampDuration(int mins) {
        int v = Math.max(15, Math.min(12 * 60, mins));
        return (v / 15) * 15;
    }

    @FXML
    private void onSave() {
        if (disponibleCheckBox != null && !disponibleCheckBox.isSelected()) {
            alert(Alert.AlertType.INFORMATION, "Disponibilité",
                    "Cochez « Disponible » pour enregistrer un créneau ouvert aux rendez-vous.");
            return;
        }
        LocalDate d = resolveDateFromDatePicker();
        if (d == null) {
            alert(Alert.AlertType.WARNING, "Saisie incomplète", "Indiquez une date valide (jj/mm/aaaa).");
            return;
        }
        if (datePicker != null && !d.equals(datePicker.getValue())) {
            datePicker.setValue(d);
        }
        LocalTime t0 = parseTime(startTimeCombo.getValue());
        LocalTime t1 = parseTime(endTimeCombo.getValue());
        if (t0 == null || t1 == null) {
            alert(Alert.AlertType.WARNING, "Saisie incomplète", "Indiquez l’heure de début et de fin (--:--).");
            return;
        }
        LocalDateTime debut = LocalDateTime.of(d, t0);
        LocalDateTime fin = LocalDateTime.of(d, t1);
        if (!fin.isAfter(debut)) {
            alert(Alert.AlertType.WARNING, "Saisie invalide", "L’heure de fin doit être après l’heure de début.");
            return;
        }
        if (saveCallback == null) {
            close();
            return;
        }
        try {
            saveCallback.onSave(debut, fin);
            close();
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Enregistrement", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
        detachListeners();
        if (stage != null) {
            stage.close();
        }
    }

    /**
     * Date réellement choisie : si l’utilisateur tape dans le champ du DatePicker, {@code getValue()}
     * peut rester sur l’ancienne date tant que la valeur n’est pas « commitée » — d’où des doublons
     * fantômes (ex. créneau enregistré le 11 alors que l’écran affiche le 12).
     */
    private LocalDate resolveDateFromDatePicker() {
        if (datePicker == null) {
            return null;
        }
        if (datePicker.getEditor() != null) {
            String raw = datePicker.getEditor().getText();
            if (raw != null && !raw.isBlank()) {
                LocalDate parsed = datePicker.getConverter().fromString(raw.trim());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return datePicker.getValue();
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank() || s.contains("-")) {
            return null;
        }
        try {
            return LocalTime.parse(s.trim(), TIME_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> quarterHourSlots() {
        List<String> out = new ArrayList<>();
        for (int h = 0; h <= 23; h++) {
            for (int m : new int[]{0, 15, 30, 45}) {
                if (h == 23 && m > 45) {
                    break;
                }
                out.add(String.format(Locale.ROOT, "%02d:%02d", h, m));
            }
        }
        return out;
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
