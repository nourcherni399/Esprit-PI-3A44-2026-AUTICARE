package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.models.UserNotificationItem;
import org.example.services.UserNotificationService;
import org.example.utils.AppState;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class PageNotificationsController implements PublicShellAware {

    @FXML
    private VBox notificationsListBox;
    @FXML
    private Label notificationsEmptyLabel;

    private PublicShellController shell;
    private final UserNotificationService notificationService = new UserNotificationService();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @Override
    public void onShellReady() {
        refreshNotifications();
    }

    private void refreshNotifications() {
        if (notificationsListBox == null || notificationsEmptyLabel == null) {
            return;
        }
        notificationsListBox.getChildren().clear();
        var user = AppState.getCurrentUser();
        if (user == null) {
            notificationsEmptyLabel.setText("Connectez-vous pour voir vos notifications.");
            notificationsEmptyLabel.setVisible(true);
            notificationsEmptyLabel.setManaged(true);
            return;
        }
        try {
            List<UserNotificationItem> items = notificationService.listAllForUser(user.getId());
            if (items.isEmpty()) {
                notificationsEmptyLabel.setText("Aucune notification pour le moment.");
                notificationsEmptyLabel.setVisible(true);
                notificationsEmptyLabel.setManaged(true);
                return;
            }
            notificationsEmptyLabel.setVisible(false);
            notificationsEmptyLabel.setManaged(false);
            for (UserNotificationItem item : items) {
                notificationsListBox.getChildren().add(buildNotificationCard(item));
            }
        } catch (SQLException e) {
            notificationsEmptyLabel.setText("Impossible de charger les notifications.");
            notificationsEmptyLabel.setVisible(true);
            notificationsEmptyLabel.setManaged(true);
        }
    }

    private VBox buildNotificationCard(UserNotificationItem item) {
        Label icon = new Label(notificationIcon(item));
        icon.getStyleClass().add("public-notif-item-icon");

        Label title = new Label(notificationTitle(item));
        title.getStyleClass().add("public-notifications-page-title");
        title.setWrapText(true);

        VBox header = new VBox(6, icon, title);
        header.getStyleClass().add("public-notifications-page-header");

        String bodyText = item.getResume() != null ? item.getResume() : "Notification";
        Label body = new Label(bodyText);
        body.getStyleClass().add("public-notifications-page-body");
        body.setWrapText(true);

        String metaValue = item.getDateCreation() != null ? TS_FMT.format(item.getDateCreation()) : "";
        Label meta = new Label(metaValue);
        meta.getStyleClass().add("public-notifications-page-meta");

        VBox row = new VBox(10, header, body, meta);
        row.getStyleClass().addAll("public-notif-item", "public-notifications-page-item", notificationTypeStyleClass(item));
        VBox.setVgrow(row, Priority.NEVER);
        if (!item.isLu()) {
            row.getStyleClass().add("public-notifications-page-item-unread");
        }
        row.setOnMouseClicked(e -> onNotificationClick(item));
        return row;
    }

    private static String notificationTypeStyleClass(UserNotificationItem item) {
        String code = item != null && item.getTypeCode() != null ? item.getTypeCode() : "";
        return switch (code) {
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "public-notif-item-msg";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED -> "public-notif-item-refused";
            default -> "public-notif-item-accepted";
        };
    }

    private static String notificationIcon(UserNotificationItem item) {
        String code = item != null && item.getTypeCode() != null ? item.getTypeCode() : "";
        return switch (code) {
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "\u2709";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED -> "\u2716";
            default -> "\u2713";
        };
    }

    private static String notificationTitle(UserNotificationItem item) {
        String code = item != null && item.getTypeCode() != null ? item.getTypeCode() : "";
        return switch (code) {
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "Messages événements";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED,
                    UserNotificationService.TYPE_EVENT_REGISTRATION_ACCEPTED -> "Inscriptions événements";
            default -> "Notifications";
        };
    }

    private void onNotificationClick(UserNotificationItem item) {
        try {
            notificationService.markAsRead(item.getId());
            if (shell != null && item.getEvenementId() != null && item.getEvenementId() > 0) {
                AppState.setPendingPublicEventDetailId(item.getEvenementId());
                shell.loadPage("event-detail");
                return;
            }
            refreshNotifications();
        } catch (Exception ignored) {
            refreshNotifications();
        }
    }
}
