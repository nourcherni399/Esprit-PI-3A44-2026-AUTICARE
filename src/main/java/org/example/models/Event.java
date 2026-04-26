package org.example.models;

import java.time.LocalDateTime;

public class Event {
    private int id;
    private String titre;
    private String description;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String lieu;
    /** Présentiel, En ligne, Hybride (optionnel). */
    private String modeEvenement;
    /** Lien de partage Google Maps (optionnel). */
    private String lienGoogleMaps;
    /** Lien réunion Zoom / visio (En ligne, Hybride). */
    private String lienZoomVisio;
    /** Thématique (ex. « Famille & Loisirs ») — affichage cartes par thème côté public. */
    private String thematique;
    private Double latitude;
    private Double longitude;
    private int placesMax;
    private EventStatus statut;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }
    public String getLieu() { return lieu; }
    public void setLieu(String lieu) { this.lieu = lieu; }
    public String getModeEvenement() { return modeEvenement; }
    public void setModeEvenement(String modeEvenement) { this.modeEvenement = modeEvenement; }
    public String getLienGoogleMaps() { return lienGoogleMaps; }
    public void setLienGoogleMaps(String lienGoogleMaps) { this.lienGoogleMaps = lienGoogleMaps; }
    public String getLienZoomVisio() { return lienZoomVisio; }
    public void setLienZoomVisio(String lienZoomVisio) { this.lienZoomVisio = lienZoomVisio; }
    public String getThematique() { return thematique; }
    public void setThematique(String thematique) { this.thematique = thematique; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public int getPlacesMax() { return placesMax; }
    public void setPlacesMax(int placesMax) { this.placesMax = placesMax; }
    public EventStatus getStatut() { return statut; }
    public void setStatut(EventStatus statut) { this.statut = statut; }

    @Override
    public String toString() {
        return titre + " (" + dateDebut + ")";
    }
}