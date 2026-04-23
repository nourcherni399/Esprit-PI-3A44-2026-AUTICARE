package org.example.models;

import java.time.LocalDateTime;

public class AdminNotificationItem {
    private int id;
    private String typeCode;
    private Integer evenementId;
    private int expediteurUserId;
    private String resume;
    private boolean lu;
    private LocalDateTime dateCreation;
    private String expediteurNom;
    private String evenementTitre;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public Integer getEvenementId() {
        return evenementId;
    }

    public void setEvenementId(Integer evenementId) {
        this.evenementId = evenementId;
    }

    public int getExpediteurUserId() {
        return expediteurUserId;
    }

    public void setExpediteurUserId(int expediteurUserId) {
        this.expediteurUserId = expediteurUserId;
    }

    public String getResume() {
        return resume;
    }

    public void setResume(String resume) {
        this.resume = resume;
    }

    public boolean isLu() {
        return lu;
    }

    public void setLu(boolean lu) {
        this.lu = lu;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public String getExpediteurNom() {
        return expediteurNom;
    }

    public void setExpediteurNom(String expediteurNom) {
        this.expediteurNom = expediteurNom;
    }

    public String getEvenementTitre() {
        return evenementTitre;
    }

    public void setEvenementTitre(String evenementTitre) {
        this.evenementTitre = evenementTitre;
    }
}
