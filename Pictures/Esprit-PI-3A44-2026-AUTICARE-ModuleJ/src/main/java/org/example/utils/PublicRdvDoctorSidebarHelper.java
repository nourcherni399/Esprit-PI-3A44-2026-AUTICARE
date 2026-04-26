package org.example.utils;

import javafx.scene.control.Label;
import org.example.models.User;
import org.example.services.UserService;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

/**
 * Remplit la colonne « fiche médecin » du parcours RDV public (plusieurs étapes).
 */
public final class PublicRdvDoctorSidebarHelper {

    private PublicRdvDoctorSidebarHelper() {
    }

    public static void populate(
            int doctorId,
            String fallbackName,
            Label sidebarName,
            Label avatarInitials,
            Label bio,
            Label phone,
            Label email) {
        String name = fallbackName != null ? fallbackName : "";
        if (sidebarName != null) {
            sidebarName.setText(name.isBlank() ? "Professionnel" : name);
        }
        if (avatarInitials != null) {
            avatarInitials.setText(initialsFromDisplayName(name));
        }
        if (doctorId <= 0) {
            applyDefaultCopy(name, sidebarName, avatarInitials, bio, phone, email);
            return;
        }
        try {
            Optional<User> u = new UserService().findById(doctorId);
            if (u.isEmpty()) {
                applyDefaultCopy(name, sidebarName, avatarInitials, bio, phone, email);
                return;
            }
            User med = u.get();
            String display = formatDrName(med);
            if (sidebarName != null) {
                sidebarName.setText(display);
            }
            if (avatarInitials != null) {
                avatarInitials.setText(initialsForUser(med));
            }
            if (phone != null) {
                String tel = med.getTelephone() != null && !med.getTelephone().isBlank()
                        ? med.getTelephone()
                        : "—";
                phone.setText("📞 " + tel);
            }
            if (email != null) {
                String em = med.getEmail() != null ? med.getEmail() : "—";
                email.setText("✉ " + em);
            }
            if (bio != null) {
                String spec = med.getSpecialite() != null && !med.getSpecialite().isBlank()
                        ? med.getSpecialite()
                        : "accompagnement des patients";
                String cab = med.getCabinet() != null && !med.getCabinet().isBlank()
                        ? med.getCabinet()
                        : "non renseigné";
                bio.setText("Praticien — " + spec + ". Cabinet : " + cab + ".");
            }
        } catch (SQLException e) {
            applyDefaultCopy(name, sidebarName, avatarInitials, bio, phone, email);
        }
    }

    private static void applyDefaultCopy(
            String fallbackName,
            Label sidebarName,
            Label avatarInitials,
            Label bio,
            Label phone,
            Label email) {
        if (phone != null) {
            phone.setText("📞 —");
        }
        if (email != null) {
            email.setText("✉ —");
        }
        if (bio != null) {
            bio.setText("Praticien accompagnant les personnes avec TSA. Cabinet : non renseigné.");
        }
        if (avatarInitials != null && (fallbackName == null || fallbackName.isBlank())) {
            avatarInitials.setText("DR");
        } else if (avatarInitials != null) {
            avatarInitials.setText(initialsFromDisplayName(fallbackName));
        }
    }

    private static String formatDrName(User med) {
        String p = med.getPrenom() != null ? med.getPrenom().trim() : "";
        String n = med.getNom() != null ? med.getNom().trim() : "";
        if (!p.isEmpty() && !n.isEmpty()) {
            return "Dr. " + n + " " + p;
        }
        if (!n.isEmpty()) {
            return "Dr. " + n;
        }
        if (!p.isEmpty()) {
            return "Dr. " + p;
        }
        return med.getEmail() != null ? med.getEmail() : "Médecin";
    }

    private static String initialsForUser(User med) {
        String p = med.getPrenom() != null ? med.getPrenom().trim() : "";
        String n = med.getNom() != null ? med.getNom().trim() : "";
        if (!p.isEmpty() && !n.isEmpty()) {
            return ("" + n.charAt(0) + p.charAt(0)).toUpperCase(Locale.ROOT);
        }
        if (!n.isEmpty() && n.length() >= 2) {
            return n.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        if (!p.isEmpty() && p.length() >= 2) {
            return p.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return "DR";
    }

    private static String initialsFromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "DR";
        }
        String[] parts = displayName.replace("Dr.", "").replace("Dr", "").trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        if (parts.length == 1 && parts[0].length() >= 2) {
            return parts[0].substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return "DR";
    }
}
