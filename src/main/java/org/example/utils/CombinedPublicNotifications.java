package org.example.utils;

import org.example.models.Appointment;
import org.example.models.Role;
import org.example.models.User;
import org.example.models.UserNotificationItem;
import org.example.services.AppointmentService;
import org.example.services.UserNotificationService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fusionne notifications événements ({@code notifications_user}) et réponses RDV (patient)
 * pour une seule icône / un seul menu.
 */
public final class CombinedPublicNotifications {

    private static final int FETCH_EACH = 12;
    private static final int MENU_MAX = 8;

    public enum MergedKind {
        EVENT,
        RDV
    }

    public record MergedPreview(
            MergedKind kind,
            UserNotificationItem eventItem,
            Appointment rdv,
            LocalDateTime sortTime
    ) {
    }

    private CombinedPublicNotifications() {
    }

    public static int totalUnread(User user, UserNotificationService userNotif, AppointmentService appointments)
            throws SQLException {
        if (user == null) {
            return 0;
        }
        int n = userNotif.countUnreadForUser(user.getId());
        if (user.getRole() == Role.PATIENT || user.getRole() == Role.PARENT) {
            n += appointments.countUnreadPatientDecisions(user.getId());
        }
        return n;
    }

    /**
     * Aperçu mélangé pour le menu déroulant (événements + RDV non lus pour patient/parent).
     */
    public static List<MergedPreview> buildMenuPreview(User user, UserNotificationService userNotif,
            AppointmentService appointments) throws SQLException {
        List<MergedPreview> out = new ArrayList<>();
        if (user == null) {
            return out;
        }
        List<UserNotificationItem> events = userNotif.listLatestForUser(user.getId(), FETCH_EACH);
        for (UserNotificationItem it : events) {
            if (!it.isLu()) {
                LocalDateTime t = it.getDateCreation();
                out.add(new MergedPreview(MergedKind.EVENT, it, null, t));
            }
        }
        if (user.getRole() == Role.PATIENT || user.getRole() == Role.PARENT) {
            for (Appointment a : appointments.findUnreadPatientDecisionsOrdered(user.getId(), FETCH_EACH)) {
                LocalDateTime t = a.getDateHeure();
                out.add(new MergedPreview(MergedKind.RDV, null, a, t));
            }
        }
        out.sort(Comparator.comparing(MergedPreview::sortTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        if (out.size() > MENU_MAX) {
            return new ArrayList<>(out.subList(0, MENU_MAX));
        }
        return out;
    }
}
