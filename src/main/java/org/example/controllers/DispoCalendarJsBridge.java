package org.example.controllers;

import javafx.application.Platform;

/**
 * Pont JavaScript ↔ Java pour FullCalendar dans un {@link javafx.scene.web.WebView}.
 * La classe et les méthodes doivent être publiques pour que le moteur WebView puisse les exposer à window.javaDispo.
 */
public final class DispoCalendarJsBridge {

    private final MedecinDashboardController controller;

    public DispoCalendarJsBridge(MedecinDashboardController controller) {
        this.controller = controller;
    }

    public void onDateClick(String dateIso) {
        Platform.runLater(() -> controller.dispCalBridgeDateClick(dateIso));
    }

    public void onEventClick(String availabilityId, String hasLinkedFlag) {
        Platform.runLater(() -> controller.dispCalBridgeEventClick(availabilityId, hasLinkedFlag));
    }

    public void onEventMove(String availabilityId, String startIso, String endIso) {
        Platform.runLater(() -> controller.dispCalBridgeEventMove(availabilityId, startIso, endIso));
    }

    public void onEventContextMenu(String availabilityId) {
        Platform.runLater(() -> controller.dispCalBridgeContextMenu(availabilityId));
    }
}
