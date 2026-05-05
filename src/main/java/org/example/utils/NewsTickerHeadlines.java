package org.example.utils;

import org.example.models.Event;
import org.example.models.EventStatus;
import org.example.services.EventService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Libellés du bandeau « Actualités » (bas de fenêtre) : événements publiés à venir / en cours, sinon textes par défaut.
 */
public final class NewsTickerHeadlines {

    private static final List<String> FALLBACK = List.of(
            "Move Your Body 1.0 : Quand sport et créativité s'unissent",
            "Hackathon H12 Innovation : innover pour l'inclusion",
            "Ateliers sensoriels : découverte des outils d'apaisement");

    private NewsTickerHeadlines() {
    }

    public static List<String> loadFromDatabase(EventService eventService) {
        if (eventService == null) {
            return new ArrayList<>(FALLBACK);
        }
        try {
            List<Event> events = eventService.findPublishedPublic();
            LocalDateTime now = LocalDateTime.now();
            List<String> fromDb = events.stream()
                    .filter(e -> e.getStatut() == EventStatus.PUBLIE)
                    .filter(e -> e.getDateDebut() != null && e.getDateFin() != null)
                    .filter(e -> !e.getDateFin().isBefore(now))
                    .sorted(Comparator.comparing(Event::getDateDebut))
                    .map(Event::getTitre)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(12)
                    .collect(Collectors.toList());
            if (!fromDb.isEmpty()) {
                return fromDb;
            }
        } catch (SQLException ignored) {
            // Repli : FALLBACK
        }
        return new ArrayList<>(FALLBACK);
    }
}
