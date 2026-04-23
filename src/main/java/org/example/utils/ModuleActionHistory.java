package org.example.utils;

import org.example.models.ModuleActionEntry;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Journal des actions sur les modules (mémoire applicative, max {@link #MAX_ENTRIES} entrées).
 */
public final class ModuleActionHistory {

    private static final int MAX_ENTRIES = 50;

    private static final Deque<ModuleActionEntry> ENTRIES = new ArrayDeque<>();

    private ModuleActionHistory() {
    }

    public static synchronized void record(String action, String moduleTitre, int moduleId) {
        String titre = moduleTitre != null ? moduleTitre : "—";
        ENTRIES.addFirst(new ModuleActionEntry(LocalDateTime.now(), action, titre, moduleId));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeLast();
        }
    }

    /**
     * @param max nombre maximum d'entrées (les plus récentes)
     */
    public static synchronized List<ModuleActionEntry> recent(int max) {
        List<ModuleActionEntry> out = new ArrayList<>(Math.min(max, ENTRIES.size()));
        int i = 0;
        for (ModuleActionEntry e : ENTRIES) {
            if (i++ >= max) {
                break;
            }
            out.add(e);
        }
        return out;
    }
}
