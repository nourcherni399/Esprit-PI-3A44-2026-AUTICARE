package org.example.utils;

/**
 * Sépare dans {@code rendez_vous.notes} le bloc « fiche demande patient » et la note rédigée par le médecin.
 */
public final class RdvNotesFormat {

    /** Ajouté entre la fiche patient et le texte saisi par le médecin (édition depuis le tableau des notes). */
    public static final String MEDECIN_SECTION_SEP = "\n---\nNote du médecin :\n";

    private RdvNotesFormat() {
    }

    public static String extractMedecinNote(String fullNotes) {
        if (fullNotes == null || fullNotes.isBlank()) {
            return "";
        }
        int i = fullNotes.indexOf(MEDECIN_SECTION_SEP);
        if (i < 0) {
            return "";
        }
        return fullNotes.substring(i + MEDECIN_SECTION_SEP.length()).trim();
    }

    /** Partie avant le séparateur (fiche demande), ou tout le texte s’il n’y a pas encore de note médecin. */
    public static String extractPatientBloc(String fullNotes) {
        if (fullNotes == null) {
            return "";
        }
        String t = fullNotes.trim();
        if (t.isEmpty()) {
            return "";
        }
        int i = t.indexOf(MEDECIN_SECTION_SEP);
        if (i < 0) {
            return t;
        }
        return t.substring(0, i).trim();
    }

    /**
     * Conserve la fiche patient et remplace (ou supprime) uniquement la partie médecin.
     *
     * @param currentFull   contenu actuel du champ {@code notes}
     * @param newMedecinNote nouveau texte médecin (peut être vide pour retirer la note)
     */
    public static String mergePatientBlocWithMedecinNote(String currentFull, String newMedecinNote) {
        String patient = extractPatientBloc(currentFull != null ? currentFull : "");
        String med = newMedecinNote != null ? newMedecinNote.trim() : "";
        if (med.isEmpty()) {
            return patient.isEmpty() ? "" : patient;
        }
        if (patient.isEmpty()) {
            return med;
        }
        return patient + MEDECIN_SECTION_SEP + med;
    }
}
