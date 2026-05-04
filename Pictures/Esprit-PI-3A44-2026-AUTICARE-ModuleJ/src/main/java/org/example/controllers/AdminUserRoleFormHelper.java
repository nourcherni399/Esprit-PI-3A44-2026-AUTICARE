package org.example.controllers;

import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.example.models.Role;
import org.example.models.User;

import java.time.LocalDate;

/**
 * Sections conditionnelles selon le rôle (formulaires admin ajout / édition utilisateur).
 */
public final class AdminUserRoleFormHelper {

    private AdminUserRoleFormHelper() {
    }

    public static void updateSectionVisibility(
            Role r,
            VBox parentBox,
            VBox patientBox,
            VBox medecinBox,
            Label telephoneLabel,
            TextField telephoneField) {
        if (r == null) {
            setBox(parentBox, false);
            setBox(patientBox, false);
            setBox(medecinBox, false);
            if (telephoneLabel != null) {
                telephoneLabel.setVisible(true);
                telephoneLabel.setManaged(true);
            }
            if (telephoneField != null) {
                telephoneField.setVisible(true);
                telephoneField.setManaged(true);
            }
            return;
        }
        boolean p = r == Role.PARENT;
        boolean pt = r == Role.PATIENT;
        boolean m = r == Role.MEDECIN;
        setBox(parentBox, p);
        setBox(patientBox, pt);
        setBox(medecinBox, m);
        boolean showMainTel = r != Role.MEDECIN;
        if (telephoneLabel != null) {
            telephoneLabel.setVisible(showMainTel);
            telephoneLabel.setManaged(showMainTel);
        }
        if (telephoneField != null) {
            telephoneField.setVisible(showMainTel);
            telephoneField.setManaged(showMainTel);
        }
    }

    private static void setBox(VBox box, boolean show) {
        if (box == null) {
            return;
        }
        box.setVisible(show);
        box.setManaged(show);
    }

    /** Applique les champs spécifiques au rôle sur {@code u} (téléphone général déjà posé pour les rôles non-médecin). */
    public static void applyRoleFields(
            User u,
            Role r,
            TextField relationField,
            DatePicker dateNaissancePicker,
            ComboBox<String> sexeCombo,
            TextField patientAdresseField,
            TextField specialiteField,
            TextField cabinetNameField,
            TextField cabinetAdresseField,
            TextField cabinetTelField,
            TextField mainTelephoneField) {
        String mainTel = trim(mainTelephoneField);
        switch (r) {
            case PARENT -> {
                u.setRelationParent(trim(relationField));
                u.setDateNaissance(null);
                u.setSexe(null);
                u.setAdresse(null);
                u.setSpecialite(null);
                u.setCabinet(null);
            }
            case PATIENT -> {
                u.setRelationParent(null);
                LocalDate dn = dateNaissancePicker != null ? dateNaissancePicker.getValue() : null;
                u.setDateNaissance(dn);
                u.setSexe(sexeToDb(sexeCombo));
                u.setAdresse(trim(patientAdresseField));
                u.setSpecialite(null);
                u.setCabinet(null);
            }
            case MEDECIN -> {
                u.setRelationParent(null);
                u.setDateNaissance(null);
                u.setSexe(null);
                u.setSpecialite(trim(specialiteField));
                u.setCabinet(trim(cabinetNameField));
                u.setAdresse(trim(cabinetAdresseField));
                String cab = trim(cabinetTelField);
                u.setTelephone(cab.isEmpty() ? mainTel : cab);
            }
            default -> {
                u.setRelationParent(null);
                u.setDateNaissance(null);
                u.setSexe(null);
                u.setAdresse(null);
                u.setSpecialite(null);
                u.setCabinet(null);
            }
        }
    }

    public static void fillPatientFields(User u, DatePicker dp, ComboBox<String> sexeCombo, TextField adresseField) {
        if (dp != null) {
            dp.setValue(u.getDateNaissance());
        }
        selectSexe(sexeCombo, u.getSexe());
        if (adresseField != null) {
            adresseField.setText(u.getAdresse() != null ? u.getAdresse() : "");
        }
    }

    public static void fillMedecinFields(User u, TextField spec, TextField cabName, TextField cabAddr, TextField cabTel) {
        if (spec != null) {
            spec.setText(u.getSpecialite() != null ? u.getSpecialite() : "");
        }
        if (cabName != null) {
            cabName.setText(u.getCabinet() != null ? u.getCabinet() : "");
        }
        if (cabAddr != null) {
            cabAddr.setText(u.getAdresse() != null ? u.getAdresse() : "");
        }
        if (cabTel != null) {
            cabTel.setText(u.getTelephone() != null ? u.getTelephone() : "");
        }
    }

    public static void selectSexe(ComboBox<String> sexeCombo, String sexe) {
        if (sexeCombo == null) {
            return;
        }
        if (sexe == null || sexe.isBlank()) {
            sexeCombo.getSelectionModel().clearSelection();
            return;
        }
        String s = sexe.trim().toLowerCase();
        if (s.startsWith("f") || s.contains("femme")) {
            sexeCombo.getSelectionModel().select("Femme");
        } else if (s.startsWith("h") || s.contains("homme") || s.equals("m") || s.equals("masculin")) {
            sexeCombo.getSelectionModel().select("Homme");
        } else {
            sexeCombo.getSelectionModel().clearSelection();
        }
    }

    private static String sexeToDb(ComboBox<String> sexeCombo) {
        if (sexeCombo == null) {
            return null;
        }
        String v = sexeCombo.getSelectionModel().getSelectedItem();
        if (v == null || v.isBlank()) {
            return null;
        }
        return v;
    }

    public static String trim(TextField f) {
        if (f == null || f.getText() == null) {
            return "";
        }
        return f.getText().trim();
    }
}
