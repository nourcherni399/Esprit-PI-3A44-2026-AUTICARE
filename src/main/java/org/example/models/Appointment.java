package org.example.models;

import java.time.LocalDateTime;

public class Appointment {
    private int id;
    private int medecinId;
    private int patientId;
    private LocalDateTime dateHeure;
    private String motif;
    private AppointmentStatus status;
    private String notes;
    /** Si false, le patient doit voir une notification (réponse du médecin). */
    private boolean patientReponseLue = true;
    /** Si false, le médecin n’a pas encore ouvert la demande {@link AppointmentStatus#EN_ATTENTE} (cloche). */
    private boolean medecinDemandeLue = true;
    /** Colonnes optionnelles {@code nom} / {@code prenom} sur {@code rendez_vous} (ex. schéma Symfony). */
    private String patientNom;
    private String patientPrenom;
    /** Lien vers {@code disponibilite.id} si la prise de RDV vient du parcours public (créneau choisi). */
    private int disponibiliteId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getMedecinId() { return medecinId; }
    public void setMedecinId(int medecinId) { this.medecinId = medecinId; }
    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }
    public LocalDateTime getDateHeure() { return dateHeure; }
    public void setDateHeure(LocalDateTime dateHeure) { this.dateHeure = dateHeure; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isPatientReponseLue() {
        return patientReponseLue;
    }

    public void setPatientReponseLue(boolean patientReponseLue) {
        this.patientReponseLue = patientReponseLue;
    }

    public boolean isMedecinDemandeLue() {
        return medecinDemandeLue;
    }

    public void setMedecinDemandeLue(boolean medecinDemandeLue) {
        this.medecinDemandeLue = medecinDemandeLue;
    }

    public String getPatientNom() {
        return patientNom;
    }

    public void setPatientNom(String patientNom) {
        this.patientNom = patientNom;
    }

    public String getPatientPrenom() {
        return patientPrenom;
    }

    public void setPatientPrenom(String patientPrenom) {
        this.patientPrenom = patientPrenom;
    }

    public int getDisponibiliteId() {
        return disponibiliteId;
    }

    public void setDisponibiliteId(int disponibiliteId) {
        this.disponibiliteId = disponibiliteId;
    }

    @Override
    public String toString() {
        return "RDV #" + id + " M" + medecinId + " / P" + patientId + " - " + dateHeure;
    }
}