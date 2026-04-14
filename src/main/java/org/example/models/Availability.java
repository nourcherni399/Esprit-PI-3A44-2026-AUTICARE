package org.example.models;

import java.time.LocalDateTime;

public class Availability {
    private int id;
    private int medecinId;
    private LocalDateTime debut;
    private LocalDateTime fin;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getMedecinId() { return medecinId; }
    public void setMedecinId(int medecinId) { this.medecinId = medecinId; }
    public LocalDateTime getDebut() { return debut; }
    public void setDebut(LocalDateTime debut) { this.debut = debut; }
    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }

    @Override
    public String toString() {
        return "Medecin " + medecinId + " : " + debut + " -> " + fin;
    }
}
