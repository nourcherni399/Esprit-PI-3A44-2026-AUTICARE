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

    @Override
    public String toString() {
        return "RDV #" + id + " M" + medecinId + " / P" + patientId + " - " + dateHeure;
    }
}
