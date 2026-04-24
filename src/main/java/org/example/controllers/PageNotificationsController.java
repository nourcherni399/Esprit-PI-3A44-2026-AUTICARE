package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
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

    private HBox buildNotificationCard(UserNotificationItem item) {
        String type = item.getTypeCode() != null ? item.getTypeCode() : "";
        String glyph = notificationGlyphForType(type);
        String variant = notificationVariantStyleClass(type);

        Label icon = new Label(glyph);
        icon.getStyleClass().add("public-notifications-card-icon");

        Label body = new Label(item.getResume() != null ? item.getResume() : "Notification");
        body.getStyleClass().add("public-notifications-card-text");
        body.setWrapText(true);

        String metaValue = item.getDateCreation() != null ? TS_FMT.format(item.getDateCreation()) : "";
        Label meta = new Label(metaValue);
        meta.getStyleClass().add("public-notifications-card-meta");

        VBox textBox = new VBox(4, body, meta);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox row = new HBox(10, icon, textBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("public-notifications-card");
        row.getStyleClass().add(variant);
        if (!item.isLu()) {
            row.getStyleClass().add("public-notifications-card-unread");
        }
        row.setOnMouseClicked(e -> onNotificationClick(item));
        return row;
    }

    private static String notificationGlyphForType(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return "\u2713";
        }
        return switch (typeCode) {
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED,
                 UserNotificationService.TYPE_RDV_REFUSED,
                 UserNotificationService.TYPE_RDV_CANCELLED -> "\u2716";
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "\u2709";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_PENDING -> "\u23F3";
            case UserNotificationService.TYPE_RDV_ACCEPTED -> "\u2713";
            default -> "\u2713";
        };
    }

    /** Classe CSS supplémentaire sur la carte (couleur selon le type). */
    private static String notificationVariantStyleClass(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return "public-notifications-card--ok";
        }
        return switch (typeCode) {
            case UserNotificationService.TYPE_EVENT_REGISTRATION_REFUSED,
                 UserNotificationService.TYPE_RDV_REFUSED,
                 UserNotificationService.TYPE_RDV_CANCELLED -> "public-notifications-card--refused";
            case UserNotificationService.TYPE_EVENT_MESSAGE_REPLY -> "public-notifications-card--msg";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_PENDING -> "public-notifications-card--pending";
            case UserNotificationService.TYPE_EVENT_REGISTRATION_ACCEPTED,
                 UserNotificationService.TYPE_RDV_ACCEPTED -> "public-notifications-card--ok";
            default -> "public-notifications-card--ok";
        };
    }

    private void onNotificationClick(UserNotificationItem item) {
        try {
            notificationService.markAsRead(item.getId());
            String code = item.getTypeCode() != null ? item.getTypeCode() : "";
            if (shell != null && (UserNotificationService.TYPE_RDV_ACCEPTED.equals(code)
                    || UserNotificationService.TYPE_RDV_REFUSED.equals(code)
                    || UserNotificationService.TYPE_RDV_CANCELLED.equals(code))) {
                shell.loadPage("rdv");
                return;
            }
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
