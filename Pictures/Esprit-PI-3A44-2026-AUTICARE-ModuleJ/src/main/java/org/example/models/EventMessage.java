package org.example.models;

import java.time.LocalDateTime;

/** Message utilisateur lié à un événement (discussion avec l’organisateur). */
public class EventMessage {
    private int id;
    private int evenementId;
    private int expediteurUserId;
    private Integer destinataireUserId;
    private String corps;
    private LocalDateTime dateEnvoi;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEvenementId() {
        return evenementId;
    }

    public void setEvenementId(int evenementId) {
        this.evenementId = evenementId;
    }

    public int getExpediteurUserId() {
        return expediteurUserId;
    }

    public void setExpediteurUserId(int expediteurUserId) {
        this.expediteurUserId = expediteurUserId;
    }

    public String getCorps() {
        return corps;
    }

    public void setCorps(String corps) {
        this.corps = corps;
    }

    public Integer getDestinataireUserId() {
        return destinataireUserId;
    }

    public void setDestinataireUserId(Integer destinataireUserId) {
        this.destinataireUserId = destinataireUserId;
    }

    public LocalDateTime getDateEnvoi() {
        return dateEnvoi;
    }

    public void setDateEnvoi(LocalDateTime dateEnvoi) {
        this.dateEnvoi = dateEnvoi;
    }
}
