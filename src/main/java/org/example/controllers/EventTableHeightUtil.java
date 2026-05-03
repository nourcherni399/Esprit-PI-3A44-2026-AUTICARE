package org.example.controllers;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import org.example.models.Event;

/**
 * Évite les « lignes fantômes » sous les données : la hauteur du {@link TableView}
 * suit le nombre d’événements (au lieu d’un {@code prefHeight} fixe qui remplit de lignes vides).
 */
public final class EventTableHeightUtil {

    private static final double ROW = 40;
    private static final double HEADER = 29;
    private static final double MAX_BODY = 360;
    private static final double EMPTY_TABLE = 200;

    private EventTableHeightUtil() {
    }

    public static void bindHeightToItems(TableView<Event> table) {
        if (table == null) {
            return;
        }
        table.setFixedCellSize(ROW);
        table.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> {
            ObservableList<Event> items = table.getItems();
            int n = items == null ? 0 : items.size();
            if (n == 0) {
                return EMPTY_TABLE;
            }
            double body = Math.min(ROW * n, MAX_BODY);
            return HEADER + body + 6;
        }, table.getItems()));
        table.minHeightProperty().bind(table.prefHeightProperty());
    }
}
