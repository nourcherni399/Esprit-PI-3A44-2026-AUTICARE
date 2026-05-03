package org.example.utils;

import org.example.models.Event;
import org.example.models.EventStatus;
import org.example.models.EventRegistration;
import org.example.models.Product;
import org.example.services.AiRecommendationService;
import org.example.services.CustomerOrderService;
import org.example.services.EventService;
import org.example.services.EventRegistrationService;
import org.example.services.ProductService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Libellés du bandeau « Actualités » (bas de fenêtre) : événements publiés à venir / en cours, sinon textes par défaut.
 */
public final class NewsTickerHeadlines {
    private static final Logger LOG = Logger.getLogger(NewsTickerHeadlines.class.getName());

    private static final List<String> FALLBACK = List.of(
            "AutiCare: plateforme pour accompagner les familles et enfants autistes.",
            "AutiCare: decouvrez des evenements, ressources et ateliers adaptes.",
            "AutiCare: trouvez des produits bien-etre et outils sensoriels.");

    private NewsTickerHeadlines() {
    }

    public static List<String> loadFromDatabase(EventService eventService) {
        return loadFromDatabase(eventService, null, null, null, null, null);
    }

    public static List<String> loadFromDatabase(
            EventService eventService,
            ProductService productService,
            EventRegistrationService registrationService,
            CustomerOrderService orderService,
            AiRecommendationService aiRecommendationService,
            Integer userId) {
        if (eventService == null) {
            return new ArrayList<>(FALLBACK);
        }
        try {
            List<Event> events = eventService.findPublishedPublic();
            LocalDateTime now = LocalDateTime.now();
            List<Event> upcomingPublished = events.stream()
                    .filter(e -> e.getStatut() == EventStatus.PUBLIE)
                    .filter(e -> e.getDateDebut() != null && e.getDateFin() != null)
                    .filter(e -> !e.getDateFin().isBefore(now))
                    .sorted(Comparator.comparing(Event::getDateDebut))
                    .collect(Collectors.toList());

            if (userId == null || userId <= 0) {
                return new ArrayList<>(FALLBACK);
            }

            List<String> fromDb = upcomingPublished.stream()
                    .map(Event::getTitre)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(12)
                    .collect(Collectors.toList());

            List<String> personalized = buildPersonalizedHeadlinesWithAi(
                    events,
                    upcomingPublished,
                    productService,
                    registrationService,
                    orderService,
                    aiRecommendationService,
                    userId);

            List<String> merged = new ArrayList<>(12);
            merged.addAll(personalized);
            for (String line : fromDb) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                if (!merged.contains(line)) {
                    merged.add(line);
                }
                if (merged.size() >= 12) {
                    break;
                }
            }
            if (!merged.isEmpty()) {
                return merged;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Ticker fallback activated.", e);
        }
        return new ArrayList<>(FALLBACK);
    }

    private static List<String> buildPersonalizedHeadlinesWithAi(
            List<Event> allPublished,
            List<Event> upcomingPublished,
            ProductService productService,
            EventRegistrationService registrationService,
            CustomerOrderService orderService,
            AiRecommendationService aiRecommendationService,
            Integer userId) throws SQLException {
        if (userId == null || userId <= 0 || upcomingPublished == null || upcomingPublished.isEmpty()
                || registrationService == null || orderService == null || productService == null) {
            return List.of();
        }

        Set<Integer> joinedEventIds = new LinkedHashSet<>();
        Map<String, Integer> joinedThemes = new LinkedHashMap<>();

        List<EventRegistration> registrations = registrationService.findByUserId(userId);
        if (registrations != null && !registrations.isEmpty()) {
            Map<Integer, Event> eventById = allPublished.stream()
                    .collect(Collectors.toMap(Event::getId, e -> e, (a, b) -> a));
            for (EventRegistration registration : registrations) {
                joinedEventIds.add(registration.getEvenementId());
                Event joined = eventById.get(registration.getEvenementId());
                if (joined == null) {
                    continue;
                }
                String theme = normalize(joined.getThematique());
                if (theme == null) {
                    continue;
                }
                joinedThemes.merge(theme, 1, Integer::sum);
            }
        }

        Map<String, Integer> topCategories = orderService.findTopPurchasedCategoriesForUser(userId, 3);
        Set<Integer> boughtProductIds = orderService.findPurchasedProductIdsForUser(userId);

        List<Product> availableProducts = productService.findPublishedCatalog().stream()
                .filter(Objects::nonNull)
                .filter(p -> !boughtProductIds.contains(p.getId()))
                .toList();

        List<Event> availableEvents = upcomingPublished.stream()
                .filter(e -> !joinedEventIds.contains(e.getId()))
                .toList();

        if (availableEvents.isEmpty() && availableProducts.isEmpty()) {
            return List.of();
        }

        if (aiRecommendationService != null && aiRecommendationService.isConfigured()) {
            AiRecommendationService.AiRecommendationInput input = new AiRecommendationService.AiRecommendationInput();
            input.userId = userId;
            input.excludeEventIds = new ArrayList<>(joinedEventIds);
            input.excludeProductIds = new ArrayList<>(boughtProductIds);
            input.preferredEventThemes = joinedThemes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .limit(3)
                    .toList();
            input.preferredProductCategories = topCategories.keySet().stream()
                    .map(NewsTickerHeadlines::normalize)
                    .filter(Objects::nonNull)
                    .limit(3)
                    .toList();

            Map<Integer, Event> eventById = new LinkedHashMap<>();
            for (Event event : availableEvents) {
                eventById.put(event.getId(), event);
                AiRecommendationService.EventCandidate candidate = new AiRecommendationService.EventCandidate();
                candidate.id = event.getId();
                candidate.title = safe(event.getTitre());
                candidate.theme = safe(event.getThematique());
                candidate.dateStart = event.getDateDebut() == null ? "" : event.getDateDebut().toString();
                input.availableEvents.add(candidate);
            }

            Map<Integer, Product> productById = new LinkedHashMap<>();
            for (Product product : availableProducts) {
                productById.put(product.getId(), product);
                AiRecommendationService.ProductCandidate candidate = new AiRecommendationService.ProductCandidate();
                candidate.id = product.getId();
                candidate.name = safe(product.getNom());
                candidate.category = safe(product.getCategorie());
                candidate.price = product.getPrix();
                input.availableProducts.add(candidate);
            }

            try {
                AiRecommendationService.AiRecommendationResult result = aiRecommendationService.recommend(input);
                List<String> aiLines = mapAiResultToTickerLines(
                        result, eventById, productById, joinedEventIds, boughtProductIds);
                if (!aiLines.isEmpty()) {
                    return aiLines;
                }
                LOG.fine("OpenAI returned no usable recommendations; using database fallback.");
            } catch (Exception e) {
                LOG.log(Level.FINE, "Unable to fetch AI recommendations; using database fallback.", e);
            }
        }

        return buildPersonalizedHeadlinesFromDatabase(
                upcomingPublished,
                joinedEventIds,
                joinedThemes,
                boughtProductIds,
                topCategories,
                productService);
    }

    /** Recommandations basées sur la base (thématiques / catégories d’achat) si l’IA est indisponible ou vide. */
    private static List<String> buildPersonalizedHeadlinesFromDatabase(
            List<Event> upcomingPublished,
            Set<Integer> joinedEventIds,
            Map<String, Integer> joinedThemes,
            Set<Integer> boughtProductIds,
            Map<String, Integer> topCategories,
            ProductService productService) throws SQLException {
        List<String> lines = new ArrayList<>();

        if (!joinedThemes.isEmpty()) {
            Set<String> preferredThemes = joinedThemes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<Event> recommendedEvents = upcomingPublished.stream()
                    .filter(e -> !joinedEventIds.contains(e.getId()))
                    .filter(e -> preferredThemes.contains(normalize(e.getThematique())))
                    .sorted(Comparator
                            .comparingInt((Event e) -> joinedThemes.getOrDefault(normalize(e.getThematique()), 0))
                            .reversed()
                            .thenComparing(Event::getDateDebut))
                    .limit(3)
                    .toList();
            for (Event event : recommendedEvents) {
                String theme = event.getThematique() == null || event.getThematique().isBlank()
                        ? "thematique"
                        : event.getThematique().trim();
                lines.add("IA evenement: " + safe(event.getTitre()) + " (" + theme + ")");
            }
        }

        if (!topCategories.isEmpty()) {
            Set<String> preferredCategories = topCategories.keySet().stream()
                    .map(NewsTickerHeadlines::normalize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<Product> recommendedProducts = productService.findPublishedCatalog().stream()
                    .filter(Objects::nonNull)
                    .filter(p -> !boughtProductIds.contains(p.getId()))
                    .filter(p -> preferredCategories.contains(normalize(p.getCategorie())))
                    .limit(3)
                    .toList();
            for (Product product : recommendedProducts) {
                String category = product.getCategorie() == null || product.getCategorie().isBlank()
                        ? "categorie"
                        : product.getCategorie().trim();
                lines.add("IA produit: " + safe(product.getNom()) + " (" + category + ")");
            }
        }

        return lines.stream().filter(s -> !s.isBlank()).distinct().limit(6).toList();
    }

    private static List<String> mapAiResultToTickerLines(
            AiRecommendationService.AiRecommendationResult result,
            Map<Integer, Event> eventById,
            Map<Integer, Product> productById,
            Set<Integer> excludedEventIds,
            Set<Integer> excludedProductIds) {
        if (result == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();

        for (AiRecommendationService.RecommendedItem item : result.events) {
            if (item == null || excludedEventIds.contains(item.id)) {
                continue;
            }
            Event event = eventById.get(item.id);
            if (event == null) {
                continue;
            }
            String theme = event.getThematique() == null || event.getThematique().isBlank()
                    ? "thematique"
                    : event.getThematique().trim();
            lines.add("IA evenement: " + safe(event.getTitre()) + " (" + theme + ")");
            if (lines.size() >= 3) {
                break;
            }
        }
        for (AiRecommendationService.RecommendedItem item : result.products) {
            if (item == null || excludedProductIds.contains(item.id)) {
                continue;
            }
            Product product = productById.get(item.id);
            if (product == null) {
                continue;
            }
            String category = product.getCategorie() == null || product.getCategorie().isBlank()
                    ? "categorie"
                    : product.getCategorie().trim();
            lines.add("IA produit: " + safe(product.getNom()) + " (" + category + ")");
            if (lines.size() >= 6) {
                break;
            }
        }

        return lines.stream().filter(s -> !s.isBlank()).distinct().toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.FRENCH);
    }
}
