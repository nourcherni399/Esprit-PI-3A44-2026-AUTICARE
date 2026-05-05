package org.example.services;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight fallback AI service used by RDV flows.
 * Keeps PageRdvController operational even when external AI providers are not configured.
 */
public class MultiAiProviderService {

    public record RdvPlannerCandidate(
            int availabilityId,
            int doctorId,
            String doctorName,
            String specialty,
            String cabinet,
            String address,
            String slotStartIso
    ) {}

    public record AiRdvRankResult(
            List<Integer> availabilityIds,
            String shortSummary
    ) {}

    public record ImageGenResult(
            String dataUrl,
            String error
    ) {}

    public String chatRdv(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "Je suis votre assistant RDV. Donnez-moi votre besoin (date, heure, specialite, lieu).";
        }
        return "Merci. J'ai bien pris en compte votre demande: " + prompt.trim();
    }

    public AiRdvRankResult rankRdvCandidates(
            List<RdvPlannerCandidate> candidates,
            String desiredIso,
            String preferredSpecialty,
            String preferredLieu
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new AiRdvRankResult(List.of(), "");
        }
        LocalDateTime desired = tryParse(desiredIso);
        String wantedSpec = preferredSpecialty == null ? "" : preferredSpecialty.trim().toLowerCase();
        String wantedLieu = preferredLieu == null ? "" : preferredLieu.trim().toLowerCase();

        List<RdvPlannerCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingLong(c -> score(c, desired, wantedSpec, wantedLieu)));

        List<Integer> ids = ranked.stream()
                .map(RdvPlannerCandidate::availabilityId)
                .limit(5)
                .toList();

        String summary = "Classement automatique termine: "
                + ids.size()
                + " disponibilite(s) proche(s) de vos preferences.";
        return new AiRdvRankResult(ids, summary);
    }

    public ImageGenResult generateInkblotImageWithDebug(String prompt, int plate) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 360 220'>"
                + "<rect width='360' height='220' fill='#ffffff'/>"
                + "<ellipse cx='120' cy='110' rx='70' ry='54' fill='#1d1d1d'/>"
                + "<ellipse cx='240' cy='110' rx='70' ry='54' fill='#1d1d1d'/>"
                + "<circle cx='180' cy='110' r='18' fill='#0f0f0f'/>"
                + "<text x='180' y='206' text-anchor='middle' font-size='10' fill='#555'>"
                + "Fallback inkblot " + (plate + 1)
                + "</text>"
                + "</svg>";
        String dataUrl = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new ImageGenResult(dataUrl, "");
    }

    private static LocalDateTime tryParse(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(iso.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static long score(
            RdvPlannerCandidate c,
            LocalDateTime desired,
            String wantedSpec,
            String wantedLieu
    ) {
        long score = 0L;
        LocalDateTime slot = tryParse(c.slotStartIso());
        if (desired != null && slot != null) {
            score += Math.abs(Duration.between(desired, slot).toMinutes());
        } else {
            score += 10_000;
        }
        String spec = c.specialty() == null ? "" : c.specialty().toLowerCase();
        if (!wantedSpec.isBlank() && !spec.contains(wantedSpec)) {
            score += 2_000;
        }
        String place = ((c.cabinet() == null ? "" : c.cabinet()) + " "
                + (c.address() == null ? "" : c.address())).toLowerCase();
        if (!wantedLieu.isBlank() && !place.contains(wantedLieu)) {
            score += 1_000;
        }
        return score;
    }
}
