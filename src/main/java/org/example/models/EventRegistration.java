package org.example.models;

import java.time.LocalDateTime;

public class EventRegistration {
    private int id;
    private int evenementId;
    private int utilisateurId;
    private RegistrationStatus statut;
    private LocalDateTime dateInscription;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getEvenementId() { return evenementId; }
    public void setEvenementId(int evenementId) { this.evenementId = evenementId; }
    public int getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(int utilisateurId) { this.utilisateurId = utilisateurId; }
    public RegistrationStatus getStatut() { return statut; }
    public void setStatut(RegistrationStatus statut) { this.statut = statut; }
    public LocalDateTime getDateInscription() { return dateInscription; }
    public void setDateInscription(LocalDateTime dateInscription) { this.dateInscription = dateInscription; }

    @Override
    public String toString() {
        return "Evt " + evenementId + " / User " + utilisateurId + " - " + statut;
    }
}
