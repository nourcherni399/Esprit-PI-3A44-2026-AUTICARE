package org.example.controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.example.models.User;
import org.example.services.DemandeProduitService;
import org.example.ui.product.ProductFormUi;
import org.example.utils.AppState;
import org.example.utils.ProductDescriptionSuggest;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Formulaire public « demande produit » (équivalent Symfony création de demande côté utilisateur).
 */
public class PageDemandeProduitController implements PublicShellAware {

    @FXML
    private TextArea demandeArea;
    @FXML
    private TextField budgetField;
    @FXML
    private ComboBox<ProductFormUi.ProductCategoryChoice> categorieCombo;
    @FXML
    private Button submitBtn;

    private final DemandeProduitService demandeService = new DemandeProduitService();

    @Override
    public void setPublicShell(PublicShellController shell) {
        // —
    }

    @FXML
    private void initialize() {
        categorieCombo.setItems(FXCollections.observableArrayList(ProductFormUi.getProductCategories()));
        categorieCombo.setValue(ProductFormUi.getDefaultCategoryChoice());
        submitBtn.setOnAction(e -> onSubmit());
    }

    private void onSubmit() {
        User u = AppState.getCurrentUser();
        if (u == null) {
            alert(Alert.AlertType.WARNING, "Connexion", "Connectez-vous pour envoyer une demande produit.");
            return;
        }
        String raw = demandeArea.getText() == null ? "" : demandeArea.getText().trim();
        if (raw.length() < 10) {
            alert(Alert.AlertType.WARNING, "Saisie", "Décrivez votre besoin en au moins quelques phrases (10 caractères minimum).");
            return;
        }
        ProductFormUi.ProductCategoryChoice cat = categorieCombo.getValue();
        if (cat == null) {
            alert(Alert.AlertType.WARNING, "Saisie", "Choisissez une catégorie.");
            return;
        }
        String categorieDb = cat.getDbValue();
        String nomSuggere = suggestNom(raw);
        String desc = ProductDescriptionSuggest.generate(nomSuggere, categorieDb);
        double prixEstime = 49.99;
        Double budget = null;
        String b = budgetField.getText() == null ? "" : budgetField.getText().trim().replace(",", ".");
        if (!b.isEmpty()) {
            try {
                budget = Double.parseDouble(b);
                if (budget > 0) {
                    prixEstime = Math.min(99999, budget);
                }
            } catch (NumberFormatException ignored) {
                alert(Alert.AlertType.WARNING, "Budget", "Budget invalide (nombre attendu).");
                return;
            }
        }
        try {
            demandeService.insertDemandeSimple(raw, nomSuggere, desc, categorieDb, prixEstime, budget, u.getId());
            alert(Alert.AlertType.INFORMATION, "Demande envoyée", "Votre demande a été enregistrée. L’équipe pourra la traiter depuis l’administration.");
            demandeArea.clear();
            budgetField.clear();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Erreur", ex.getMessage());
        }
    }

    private static String suggestNom(String raw) {
        String oneLine = raw.replace('\n', ' ').trim();
        if (oneLine.length() <= 80) {
            return oneLine;
        }
        return oneLine.substring(0, 77).trim() + "…";
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
