package org.example.utils;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.CustomerOrderService;
import org.example.services.DemandeProduitService;
import org.example.services.NotificationService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cloche admin : nouvelles commandes + demandes produit (approuver / rejeter).
 */
public final class AdminNotificationBellHelper {

    private static final NotificationService NOTIFICATION_SERVICE = new NotificationService();
    private static final DemandeProduitService DEMANDE_SERVICE = new DemandeProduitService();
    private static final CustomerOrderService ORDER_SERVICE = new CustomerOrderService();

    private AdminNotificationBellHelper() {
    }

    public static void attach(Label bellLabel) {
        if (bellLabel == null) {
            return;
        }
        bellLabel.getStyleClass().add("admin-bell-label");
        refreshCount(bellLabel);
        bellLabel.setOnMouseClicked(e -> showUnreadAndMarkRead(bellLabel));
    }

    public static void refreshCount(Label bellLabel) {
        if (bellLabel == null) {
            return;
        }
        User current = AppState.getCurrentUser();
        if (current == null || current.getRole() != Role.ADMIN) {
            bellLabel.setText("🔔");
            return;
        }
        try {
            int nOrders = NOTIFICATION_SERVICE.findUnreadOrderNotificationsForAdmin(current.getId()).size();
            int nDemandes = NOTIFICATION_SERVICE.findUnreadDemandeNotificationsForAdmin(current.getId()).size();
            int count = nOrders + nDemandes;
            bellLabel.setText(count > 0 ? "🔔 " + count : "🔔");
        } catch (Exception ex) {
            bellLabel.setText("🔔");
        }
    }

    private static void showUnreadAndMarkRead(Label bellLabel) {
        User current = AppState.getCurrentUser();
        if (current == null || current.getRole() != Role.ADMIN) {
            return;
        }
        try {
            List<NotificationService.OrderNotificationView> orderNotifs =
                NOTIFICATION_SERVICE.findUnreadOrderNotificationsForAdmin(current.getId());
            List<NotificationService.DemandeNotificationView> demandeNotifs =
                NOTIFICATION_SERVICE.findUnreadDemandeNotificationsForAdmin(current.getId());

            if (orderNotifs.isEmpty() && demandeNotifs.isEmpty()) {
                Alert empty = new Alert(Alert.AlertType.INFORMATION);
                empty.setTitle("Notifications");
                empty.setHeaderText(null);
                empty.setContentText("Aucune nouvelle notification.");
                empty.showAndWait();
                refreshCount(bellLabel);
                return;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            List<Integer> orderIds = new ArrayList<>();
            for (NotificationService.OrderNotificationView n : orderNotifs) {
                orderIds.add(n.notificationId());
            }
            List<Integer> demandeNotifIds = new ArrayList<>();
            for (NotificationService.DemandeNotificationView n : demandeNotifs) {
                demandeNotifIds.add(n.notificationId());
            }

            Set<Integer> readAlready = new HashSet<>();

            VBox root = new VBox(14);
            root.setStyle("-fx-padding: 4;");

            if (!demandeNotifs.isEmpty()) {
                Label secDem = new Label("Demandes produit");
                secDem.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #111827;");
                root.getChildren().add(secDem);
                for (NotificationService.DemandeNotificationView n : demandeNotifs) {
                    root.getChildren().add(buildDemandeCard(n, current, fmt, readAlready, bellLabel));
                }
            }

            if (!orderNotifs.isEmpty()) {
                Label secOrd = new Label("Nouvelles commandes");
                secOrd.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #111827;");
                root.getChildren().add(secOrd);
                for (NotificationService.OrderNotificationView n : orderNotifs) {
                    root.getChildren().add(buildOrderCard(n, fmt, readAlready, bellLabel));
                }
            }

            Label intro = new Label("Notifications non lues");
            intro.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");

            ScrollPane scroll = new ScrollPane(root);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(320);
            scroll.setStyle("-fx-background-color: transparent;");

            VBox content = new VBox(10, intro, scroll);
            content.setStyle("-fx-padding: 6 0 0 0;");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Notifications");
            alert.setHeaderText(
                "Total : " + (demandeNotifs.size() + orderNotifs.size())
                    + " (demandes : " + demandeNotifs.size() + ", commandes : " + orderNotifs.size() + ")"
            );
            alert.getButtonTypes().setAll(
                new ButtonType("Fermer et marquer tout comme lu", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL
            );
            alert.getDialogPane().setContent(content);
            alert.getDialogPane().setPrefWidth(580);

            ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
            if (choice.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                List<Integer> toMark = new ArrayList<>();
                for (Integer id : orderIds) {
                    if (!readAlready.contains(id)) {
                        toMark.add(id);
                    }
                }
                for (Integer id : demandeNotifIds) {
                    if (!readAlready.contains(id)) {
                        toMark.add(id);
                    }
                }
                NOTIFICATION_SERVICE.markNotificationsAsRead(toMark);
            }
            refreshCount(bellLabel);
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Notifications");
            err.setHeaderText(null);
            err.setContentText("Impossible de charger les notifications : " + ex.getMessage());
            err.showAndWait();
        }
    }

    private static VBox buildDemandeCard(
        NotificationService.DemandeNotificationView n,
        User admin,
        DateTimeFormatter fmt,
        Set<Integer> readAlready,
        Label bellLabel
    ) {
        VBox card = new VBox(8);
        card.setStyle(
            "-fx-background-color: #f0fdf4; -fx-background-radius: 10; -fx-border-radius: 10; "
                + "-fx-border-color: #bbf7d0; -fx-border-width: 1; -fx-padding: 10 12 10 12;"
        );

        HBox rowTop = new HBox(8);
        Label title = new Label("Demande produit #" + n.demandeId());
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #14532d;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String date = n.createdAt() != null ? n.createdAt().format(fmt) : "";
        Label dateLine = new Label(date.isBlank() ? "" : date);
        dateLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");
        rowTop.getChildren().addAll(title, spacer, dateLine);

        String nom = n.nomProduit() != null ? n.nomProduit() : "—";
        Label nomLbl = new Label("Titre : " + truncate(nom, 80));
        nomLbl.setWrapText(true);
        nomLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");

        String excerpt = n.demandeClientText() != null ? n.demandeClientText() : "";
        Label body = new Label(truncate(excerpt, 280));
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");

        boolean pending = DemandeProduitService.STATUT_EN_ATTENTE.equals(n.statutDemande());
        Label st = new Label(
            pending ? "Statut : en attente de validation" : "Statut : " + (n.statutDemande() != null ? n.statutDemande() : "—")
        );
        st.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        HBox actions = new HBox(10);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Button approve = new Button("Approuver");
        approve.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 14;");
        Button reject = new Button("Rejeter");
        reject.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 14;");
        approve.setDisable(!pending);
        reject.setDisable(!pending);

        approve.setOnAction(ev -> {
            try {
                DEMANDE_SERVICE.approuver(n.demandeId(), admin.getId());
                NOTIFICATION_SERVICE.markNotificationsAsRead(List.of(n.notificationId()));
                readAlready.add(n.notificationId());
                card.setVisible(false);
                card.setManaged(false);
                refreshCount(bellLabel);
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });
        reject.setOnAction(ev -> {
            try {
                DEMANDE_SERVICE.rejeter(n.demandeId(), admin.getId());
                NOTIFICATION_SERVICE.markNotificationsAsRead(List.of(n.notificationId()));
                readAlready.add(n.notificationId());
                card.setVisible(false);
                card.setManaged(false);
                refreshCount(bellLabel);
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });
        actions.getChildren().addAll(approve, reject);

        card.getChildren().addAll(rowTop, nomLbl, body, st, actions);
        return card;
    }

    private static VBox buildOrderCard(
        NotificationService.OrderNotificationView n,
        DateTimeFormatter fmt,
        Set<Integer> readAlready,
        Label bellLabel
    ) {
        String client = n.clientNom() != null ? n.clientNom() : "Client";
        String date = n.createdAt() != null ? n.createdAt().format(fmt) : "";
        VBox card = new VBox(6);
        card.setStyle(
            "-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-border-radius: 10; "
                + "-fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-padding: 10 12 10 12;"
        );

        HBox rowTop = new HBox(8);
        Label title = new Label("Commande #" + n.commandeId());
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #111827;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amount = new Label(String.format(Locale.FRENCH, "%.2f DT", n.total()));
        amount.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #1f2937;");
        rowTop.getChildren().addAll(title, spacer, amount);

        Label clientLine = new Label("Client : " + client);
        clientLine.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        Label dateLine = new Label(date.isBlank() ? "" : "Date : " + date);
        dateLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");
        Label stLine = new Label("Statut actuel : " + (n.statutCommande() != null ? n.statutCommande() : "—"));
        stLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        VBox linesBox = new VBox(4);
        linesBox.setStyle("-fx-padding: 4 0 0 0;");
        Label detailTitle = new Label("Détail (après vente) :");
        detailTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #374151;");
        linesBox.getChildren().add(detailTitle);
        try {
            for (NotificationService.OrderLineDetailRow r : NOTIFICATION_SERVICE.findOrderLineDetails(n.commandeId())) {
                String empl = "";
                if (r.stockLocationName() != null && !r.stockLocationName().isBlank()) {
                    empl = " | Empl. « " + r.stockLocationName() + " »";
                }
                String one = String.format(
                    Locale.FRENCH,
                    "• %s  ×%d  %,.2f DT  (reste à vendre : %d)%s",
                    r.productName() != null ? r.productName() : "—",
                    r.quantityOrdered(),
                    r.lineTotal(),
                    r.remainingSellableQuantity(),
                    empl
                );
                Label li = new Label(one);
                li.setWrapText(true);
                li.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
                linesBox.getChildren().add(li);
            }
        } catch (Exception ignored) {
            Label err = new Label("(Détail des lignes indisponible.)");
            err.setStyle("-fx-font-size: 11px; -fx-text-fill: #9ca3af;");
            linesBox.getChildren().add(err);
        }

        boolean canApprove = CustomerOrderService.isEnAttenteCommande(n.statutCommande());
        boolean canCancel = CustomerOrderService.isCommandeCancellableByAdmin(n.statutCommande());
        HBox ordActions = new HBox(10);
        ordActions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Button approveOrd = new Button("Approuver");
        approveOrd.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 14;");
        Button cancelOrd = new Button("Annuler la commande");
        cancelOrd.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 14;");
        approveOrd.setDisable(!canApprove);
        cancelOrd.setDisable(!canCancel);
        approveOrd.setOnAction(ev -> {
            try {
                ORDER_SERVICE.advanceOrderStatut(false, n.commandeId());
                NOTIFICATION_SERVICE.markNotificationsAsRead(List.of(n.notificationId()));
                readAlready.add(n.notificationId());
                card.setVisible(false);
                card.setManaged(false);
                refreshCount(bellLabel);
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });
        cancelOrd.setOnAction(ev -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Annuler la commande n° " + n.commandeId() + " ?");
            confirm.setContentText("Le client sera notifié. Cette action est définitive pour cette commande.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
            try {
                ORDER_SERVICE.annulerCommandeParAdmin(n.commandeId());
                NOTIFICATION_SERVICE.markNotificationsAsRead(List.of(n.notificationId()));
                readAlready.add(n.notificationId());
                card.setVisible(false);
                card.setManaged(false);
                refreshCount(bellLabel);
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Erreur.");
                a.showAndWait();
            }
        });
        ordActions.getChildren().addAll(approveOrd, cancelOrd);

        card.getChildren().addAll(rowTop, clientLine, dateLine, stLine, linesBox, ordActions);
        return card;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(0, max - 1)).trim() + "…";
    }
}
