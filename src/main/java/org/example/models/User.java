package org.example.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class User {
    private int id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String motDePasseHash;
    private Role role;
    private boolean actif;
    private String specialite;
    private String cabinet;
    private String relationParent;
    private LocalDate dateNaissance;
    private String adresse;
    /** Tarif consultation (DT), colonne {@code tarif_consultation} si migration appliquée. */
    private String tarifConsultation;
    private String sexe;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Chemin relatif sous {@code public/}, ex. {@code uploads/users/uuid.jpg} (aligné Symfony / colonne {@code image}). */
    private String image;

    public User() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getMotDePasseHash() { return motDePasseHash; }
    public void setMotDePasseHash(String motDePasseHash) { this.motDePasseHash = motDePasseHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public String getSpecialite() { return specialite; }
    public void setSpecialite(String specialite) { this.specialite = specialite; }
    public String getCabinet() { return cabinet; }
    public void setCabinet(String cabinet) { this.cabinet = cabinet; }
    public String getRelationParent() { return relationParent; }
    public void setRelationParent(String relationParent) { this.relationParent = relationParent; }
    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public String getTarifConsultation() { return tarifConsultation; }
    public void setTarifConsultation(String tarifConsultation) { this.tarifConsultation = tarifConsultation; }
    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    @Override
    public String toString() {
        return prenom + " " + nom + " (" + role + ")";
    }
}
