package org.example.models;

public class Product {
    private int id;
    private String nom;
    private String description;
    private double prix;
    private String categorie;
    private int stock;
    private int stockId;
    private String imagePath;
    private boolean disponible = true;
    private boolean publie;
    private boolean valide = true;
    private boolean genereParIa;
    private String sku;
    private Integer seuilAlerte;
    private Integer userId;
    private String statutPublication;
    private Double noteMoyenne;
    /** Nom du stock (jointure liste / admin), optionnel. */
    private String stockNom;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPrix() { return prix; }
    public void setPrix(double prix) { this.prix = prix; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    /** Alias UI / recherche (même valeur que {@link #getImagePath()}). */
    public String getPrimaryImagePath() {
        return imagePath;
    }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public boolean isPublie() { return publie; }
    public void setPublie(boolean publie) { this.publie = publie; }
    public boolean isValide() { return valide; }
    public void setValide(boolean valide) { this.valide = valide; }
    public boolean isGenereParIa() { return genereParIa; }
    public void setGenereParIa(boolean genereParIa) { this.genereParIa = genereParIa; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getSeuilAlerte() { return seuilAlerte; }
    public void setSeuilAlerte(Integer seuilAlerte) { this.seuilAlerte = seuilAlerte; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getStatutPublication() { return statutPublication; }
    public void setStatutPublication(String statutPublication) { this.statutPublication = statutPublication; }
    public Double getNoteMoyenne() { return noteMoyenne; }
    public void setNoteMoyenne(Double noteMoyenne) { this.noteMoyenne = noteMoyenne; }
    public String getStockNom() { return stockNom; }
    public void setStockNom(String stockNom) { this.stockNom = stockNom; }

    @Override
    public String toString() {
        return nom + " - " + prix + " DT";
    }
}
