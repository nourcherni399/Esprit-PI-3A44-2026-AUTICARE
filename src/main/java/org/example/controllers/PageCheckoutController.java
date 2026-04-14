package org.example.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.stage.Modality;
import org.example.models.CartLineView;
import org.example.models.CheckoutFormData;
import org.example.models.User;
import org.example.services.CartService;
import org.example.services.CustomerOrderService;
import org.example.services.OrderCheckoutService;
import org.example.services.OrderReceiptPdfService;
import org.example.utils.AppState;
import org.example.ui.product.ProductFormUi;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.function.UnaryOperator;

public class PageCheckoutController implements PublicShellAware {

    private static final String PAYMENT_CASH_ON_DELIVERY = "a_la_livraison";
    private static final String PAYMENT_CARD = "carte_bancaire";
    private static final Pattern EMAIL_ALLOWED_PATTERN = Pattern.compile(
        "^[^\\s@]+@(gmail\\.com|icloud\\.com|icloud\\.fr)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9\\s\\-+()]{8,}$");
    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("^\\d{5}$");

    private PublicShellController shell;
    private final CartService cartService = new CartService();
    private final OrderCheckoutService orderCheckoutService = new OrderCheckoutService();
    private final CustomerOrderService customerOrderService = new CustomerOrderService();
    private List<CartLineView> cartLines = List.of();

    @FXML
    private Label checkoutItemsLabel;
    @FXML
    private Label checkoutSubtotalLabel;
    @FXML
    private Label checkoutTotalLabel;
    @FXML
    private VBox checkoutSummaryLines;
    @FXML
    private TextField fullNameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField zipCodeField;
    @FXML
    private TextField cityField;
    @FXML
    private ComboBox<PaymentMethodOption> paymentCombo;
    @FXML
    private VBox cardFieldsBox;
    @FXML
    private TextField cardNumberField;
    @FXML
    private TextField cardExpiryField;
    @FXML
    private Label checkoutMessageLabel;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
        loadData();
    }

    @FXML
    private void initialize() {
        paymentCombo.getItems().setAll(
            new PaymentMethodOption("Paiement a la livraison", PAYMENT_CASH_ON_DELIVERY),
            new PaymentMethodOption("Carte bancaire", PAYMENT_CARD)
        );
        paymentCombo.getSelectionModel().selectFirst();
        paymentCombo.valueProperty().addListener((obs, oldV, newV) -> updateCardFieldsVisibility());
        ProductFormUi.styleStockCombo(paymentCombo);
        setupInputConstraints();
        updateCardFieldsVisibility();
    }

    private void setupInputConstraints() {
        // Nom / ville / adresse: limite simple de longueur.
        installLengthLimiter(fullNameField, 120);
        installLengthLimiter(addressField, 180);
        installLengthLimiter(cityField, 80);
        installLengthLimiter(emailField, 180);

        // Téléphone: uniquement chiffres + séparateurs usuels.
        installRegexFormatter(phoneField, text -> text.matches("[0-9\\s\\-+()]{0,20}"));
        // Code postal: 5 chiffres max.
        installRegexFormatter(zipCodeField, text -> text.matches("\\d{0,5}"));
        // Numéro carte: chiffres + espaces, max 19 chiffres (espaces ignorés).
        installRegexFormatter(cardNumberField, text -> {
            String digits = text.replaceAll("\\D", "");
            return digits.length() <= 19 && text.matches("[0-9\\s]{0,32}");
        });
        // Expiration: autorise saisie progressive de MM/YY.
        installRegexFormatter(cardExpiryField, text -> text.matches("^$|^\\d{0,2}$|^\\d{2}/?$|^\\d{2}/\\d{0,2}$"));
        cardExpiryField.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                cardExpiryField.setText(normalizeExpiry(cardExpiryField.getText()));
            }
        });
    }

    private static void installLengthLimiter(TextField field, int maxLen) {
        if (field == null || maxLen < 1) {
            return;
        }
        UnaryOperator<TextFormatter.Change> filter = change ->
            change.getControlNewText().length() <= maxLen ? change : null;
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private static void installRegexFormatter(TextField field, java.util.function.Predicate<String> accepted) {
        if (field == null || accepted == null) {
            return;
        }
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return accepted.test(next) ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private void loadData() {
        User user = AppState.getCurrentUser();
        if (user == null) {
            showWarning("Vous devez etre connecte pour finaliser la commande.");
            backToPanier();
            return;
        }
        prefillUser(user);
        try {
            cartLines = cartService.loadCartLinesForUser(user.getId());
        } catch (SQLException e) {
            cartLines = List.of();
            showError("Impossible de charger le panier: " + e.getMessage());
            backToPanier();
            return;
        }
        if (cartLines.isEmpty()) {
            showWarning("Votre panier est vide.");
            backToPanier();
            return;
        }
        rebuildSummary();
    }

    private void prefillUser(User user) {
        String fullName = ((user.getPrenom() != null ? user.getPrenom().trim() : "") + " "
            + (user.getNom() != null ? user.getNom().trim() : "")).trim();
        fullNameField.setText(fullName);
        emailField.setText(user.getEmail() != null ? user.getEmail() : "");
        phoneField.setText(user.getTelephone() != null ? user.getTelephone() : "");
        addressField.setText(user.getAdresse() != null ? user.getAdresse() : "");
    }

    private void rebuildSummary() {
        checkoutSummaryLines.getChildren().clear();
        for (CartLineView line : cartLines) {
            Label l = new Label(
                (line.product().getNom() != null ? line.product().getNom() : "Produit")
                    + " x " + line.quantity()
                    + " - " + String.format(Locale.FRENCH, "%.2f DT", line.lineTotal())
            );
            l.getStyleClass().add("checkout-summary-line");
            checkoutSummaryLines.getChildren().add(l);
        }
        int totalItems = CartService.totalItems(cartLines);
        double totalPrice = CartService.totalPrice(cartLines);
        checkoutItemsLabel.setText(String.valueOf(totalItems));
        checkoutSubtotalLabel.setText(String.format(Locale.FRENCH, "%.2f DT", totalPrice));
        checkoutTotalLabel.setText(String.format(Locale.FRENCH, "%.2f DT", totalPrice));
    }

    @FXML
    private void onBackToCart() {
        backToPanier();
    }

    /** Retour au catalogue pour ajouter d’autres articles sans quitter la coque publique. */
    @FXML
    private void onContinueShopping() {
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("produits");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onConfirmOrder() {
        if (!validateFormWithAlertPerField()) {
            return;
        }
        User user = AppState.getCurrentUser();
        if (user == null) {
            alertSaisie("Session invalide. Reconnectez-vous.");
            return;
        }
        CheckoutFormData formData = new CheckoutFormData(
            safeTrim(fullNameField.getText()),
            safeTrim(emailField.getText()),
            safeTrim(phoneField.getText()),
            safeTrim(addressField.getText()),
            safeTrim(zipCodeField.getText()),
            safeTrim(cityField.getText()),
            paymentCombo.getValue() != null ? paymentCombo.getValue().code() : PAYMENT_CASH_ON_DELIVERY
        );
        try {
            int commandeId = orderCheckoutService.placeOrder(user, formData);
            AppState.notifyCartChanged();
            int uid = user.getId();
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setHeaderText(null);
            ok.setTitle("Commande confirmée");
            Window ownerWin = getCheckoutOwnerWindow();
            if (ownerWin != null) {
                ok.initOwner(ownerWin);
                ok.initModality(Modality.WINDOW_MODAL);
            }
            ok.setContentText(
                "Votre commande n° " + commandeId + " est enregistrée.\n"
                    + "L’équipe administrateur a été notifiée de cette commande.\n\n"
                    + "Cliquez sur « Voir la facture (PDF) » uniquement si vous souhaitez ouvrir le bon / la facture."
            );
            ButtonType viewPdfBtn = new ButtonType("Voir la facture (PDF)", ButtonBar.ButtonData.APPLY);
            ButtonType saveAsBtn = new ButtonType("Enregistrer le PDF sous…", ButtonBar.ButtonData.HELP);
            ButtonType closeBtn = new ButtonType("OK", ButtonBar.ButtonData.CANCEL_CLOSE);
            ok.getButtonTypes().setAll(viewPdfBtn, saveAsBtn, closeBtn);
            Optional<ButtonType> choice = ok.showAndWait();
            if (choice.isPresent()) {
                if (choice.get() == viewPdfBtn) {
                    if (!openReceiptPdfInViewer(uid, commandeId)) {
                        Alert warn = new Alert(Alert.AlertType.WARNING);
                        warn.setTitle("Facture");
                        warn.setHeaderText(null);
                        warn.setContentText(
                            "Impossible d’ouvrir le PDF automatiquement. "
                                + "Utilisez « Enregistrer le PDF sous… » depuis Mes commandes ou réessayez plus tard."
                        );
                        if (ownerWin != null) {
                            warn.initOwner(ownerWin);
                            warn.initModality(Modality.WINDOW_MODAL);
                        }
                        warn.showAndWait();
                    }
                } else if (choice.get() == saveAsBtn) {
                    offerSaveReceiptPdf(uid, commandeId);
                }
            }
            backToPanier();
        } catch (SQLException ex) {
            alertErreurCommande(ex.getMessage() != null ? ex.getMessage() : "Impossible d’enregistrer la commande.");
        }
    }

    /** Une fenêtre par champ : en-tête = libellé du champ, contenu = message. */
    private void alertSaisieChamp(String libelleChamp, String message) {
        hideCheckoutMessage();
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Saisie");
        if (libelleChamp != null && !libelleChamp.isBlank()) {
            a.setHeaderText(libelleChamp);
        } else {
            a.setHeaderText(null);
        }
        a.setContentText(message != null ? message : "");
        Window owner = getCheckoutOwnerWindow();
        if (owner != null) {
            a.initOwner(owner);
            a.initModality(Modality.WINDOW_MODAL);
        }
        a.showAndWait();
    }

    private void alertSaisie(String message) {
        alertSaisieChamp(null, message);
    }

    private void alertErreurCommande(String message) {
        hideCheckoutMessage();
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Commande");
        a.setHeaderText(null);
        a.setContentText(message != null ? message : "Erreur.");
        Window owner = getCheckoutOwnerWindow();
        if (owner != null) {
            a.initOwner(owner);
            a.initModality(Modality.WINDOW_MODAL);
        }
        a.showAndWait();
    }

    private Window getCheckoutOwnerWindow() {
        if (checkoutSummaryLines != null && checkoutSummaryLines.getScene() != null) {
            return checkoutSummaryLines.getScene().getWindow();
        }
        if (fullNameField != null && fullNameField.getScene() != null) {
            return fullNameField.getScene().getWindow();
        }
        return null;
    }

    private void hideCheckoutMessage() {
        if (checkoutMessageLabel == null) {
            return;
        }
        checkoutMessageLabel.setText("");
        checkoutMessageLabel.setVisible(false);
        checkoutMessageLabel.setManaged(false);
    }

    /**
     * Génère un PDF temporaire et l’ouvre avec l’application par défaut — uniquement sur action utilisateur
     * (bouton « Voir la facture »), pas à la confirmation de commande.
     */
    private boolean openReceiptPdfInViewer(int userId, int commandeId) {
        try {
            var order = customerOrderService.findByIdForUser(commandeId, userId);
            if (order.isEmpty()) {
                return false;
            }
            var lines = customerOrderService.findLinesForUserOrder(commandeId, userId);
            Path tmp = Files.createTempFile("facture_auticare_" + commandeId + "_", ".pdf");
            tmp.toFile().deleteOnExit();
            OrderReceiptPdfService.writePdfToFile(order.get(), lines, tmp);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(tmp.toFile());
                return true;
            }
        } catch (IOException | SQLException ignored) {
            // —
        }
        return false;
    }

    /**
     * Contrôle chaque champ dans l’ordre du formulaire ; une fenêtre d’alerte s’ouvre pour chaque erreur.
     *
     * @return {@code true} si tout est valide
     */
    private boolean validateFormWithAlertPerField() {
        clearValidationState();
        String fullName = safeTrim(fullNameField.getText());
        String email = safeTrim(emailField.getText());
        String phone = safeTrim(phoneField.getText());
        String address = safeTrim(addressField.getText());
        String zip = safeTrim(zipCodeField.getText());
        String city = safeTrim(cityField.getText());

        if (fullName.length() < 3) {
            markInvalid(fullNameField);
            alertSaisieChamp("Nom complet", "Saisissez au moins 3 caractères.");
        }
        if (!EMAIL_ALLOWED_PATTERN.matcher(email).matches()) {
            markInvalid(emailField);
            alertSaisieChamp("Adresse e-mail", "Indiquez un domaine autorisé : gmail.com, icloud.com ou icloud.fr.");
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            markInvalid(phoneField);
            alertSaisieChamp("Téléphone", "Utilisez au moins 8 chiffres (espaces, +, - ou parenthèses autorisés).");
        }
        if (address.length() < 6) {
            markInvalid(addressField);
            alertSaisieChamp("Adresse", "Indiquez la rue et le numéro (au moins 6 caractères).");
        }
        if (!ZIP_CODE_PATTERN.matcher(zip).matches()) {
            markInvalid(zipCodeField);
            alertSaisieChamp("Code postal", "Le code postal doit contenir exactement 5 chiffres.");
        }
        if (city.length() < 2) {
            markInvalid(cityField);
            alertSaisieChamp("Ville", "Indiquez au moins 2 lettres.");
        }
        if (paymentCombo.getValue() == null) {
            markInvalid(paymentCombo);
            alertSaisieChamp("Mode de paiement", "Choisissez un mode de paiement dans la liste.");
        }

        String payment = paymentCombo.getValue() != null ? paymentCombo.getValue().code() : "";
        if (PAYMENT_CARD.equals(payment)) {
            String cardNumber = safeTrim(cardNumberField.getText());
            String expiry = normalizeExpiry(safeTrim(cardExpiryField.getText()));
            cardExpiryField.setText(expiry);
            if (!luhnCheck(cardNumber)) {
                markInvalid(cardNumberField);
                alertSaisieChamp("Numéro de carte", "Indiquez un numéro valide (13 à 19 chiffres, contrôle Luhn).");
            }
            if (!validateExpiry(expiry)) {
                markInvalid(cardExpiryField);
                alertSaisieChamp("Date d’expiration", "Utilisez le format MM/YY avec une date non expirée.");
            }
        }

        return !isAnyCheckoutFieldInvalid();
    }

    /** Détecte si une classe {@code checkout-invalid} est présente (après validation). */
    private boolean isAnyCheckoutFieldInvalid() {
        return hasInvalidStyle(fullNameField)
            || hasInvalidStyle(emailField)
            || hasInvalidStyle(phoneField)
            || hasInvalidStyle(addressField)
            || hasInvalidStyle(zipCodeField)
            || hasInvalidStyle(cityField)
            || hasInvalidStyle(paymentCombo)
            || hasInvalidStyle(cardNumberField)
            || hasInvalidStyle(cardExpiryField);
    }

    private static boolean hasInvalidStyle(javafx.scene.Node node) {
        return node != null && node.getStyleClass().contains("checkout-invalid");
    }

    private void updateCardFieldsVisibility() {
        boolean isCard = paymentCombo.getValue() != null && PAYMENT_CARD.equals(paymentCombo.getValue().code());
        cardFieldsBox.setManaged(isCard);
        cardFieldsBox.setVisible(isCard);
        if (!isCard) {
            cardNumberField.clear();
            cardExpiryField.clear();
        }
    }

    private void offerSaveReceiptPdf(int userId, int commandeId) {
        try {
            var order = customerOrderService.findByIdForUser(commandeId, userId);
            if (order.isEmpty()) {
                showError("Commande introuvable pour le reçu PDF.");
                return;
            }
            var lines = customerOrderService.findLinesForUserOrder(commandeId, userId);
            Window owner = checkoutSummaryLines != null && checkoutSummaryLines.getScene() != null
                ? checkoutSummaryLines.getScene().getWindow()
                : null;
            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer le bon de livraison (PDF)");
            fc.setInitialFileName(OrderReceiptPdfService.defaultFileName(commandeId));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            java.io.File dest = fc.showSaveDialog(owner);
            if (dest == null) {
                return;
            }
            OrderReceiptPdfService.writePdfToFile(order.get(), lines, dest.toPath());
            Alert done = new Alert(Alert.AlertType.INFORMATION);
            done.setHeaderText(null);
            done.setTitle("PDF");
            done.setContentText("Fichier enregistré :\n" + dest.getAbsolutePath());
            done.showAndWait();
        } catch (Exception ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Impossible de générer le PDF.");
        }
    }

    private void backToPanier() {
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("panier");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void clearValidationState() {
        clearInvalid(fullNameField);
        clearInvalid(emailField);
        clearInvalid(phoneField);
        clearInvalid(addressField);
        clearInvalid(zipCodeField);
        clearInvalid(cityField);
        clearInvalid(cardNumberField);
        clearInvalid(cardExpiryField);
        clearInvalid(paymentCombo);
    }

    private static void markInvalid(javafx.scene.Node node) {
        if (node != null && !node.getStyleClass().contains("checkout-invalid")) {
            node.getStyleClass().add("checkout-invalid");
        }
    }

    private static void clearInvalid(javafx.scene.Node node) {
        if (node != null) {
            node.getStyleClass().remove("checkout-invalid");
        }
    }

    private static String normalizeExpiry(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return digits.substring(0, 2) + "/" + digits.substring(2, 4);
        }
        return raw.trim();
    }

    private static boolean luhnCheck(String rawNumber) {
        String number = rawNumber == null ? "" : rawNumber.replaceAll("\\D", "");
        if (number.length() < 13 || number.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (alt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    private static boolean validateExpiry(String value) {
        if (value == null || !value.matches("^(\\d{2})/(\\d{2})$")) {
            return false;
        }
        int month = Integer.parseInt(value.substring(0, 2));
        int year = Integer.parseInt(value.substring(3, 5)) + 2000;
        if (month < 1 || month > 12) {
            return false;
        }
        java.time.YearMonth now = java.time.YearMonth.now();
        java.time.YearMonth exp = java.time.YearMonth.of(year, month);
        return !exp.isBefore(now);
    }

    private static String safeTrim(String x) {
        return x == null ? "" : x.trim();
    }

    private void showWarning(String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(null);
        a.setTitle("Commande");
        a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setTitle("Commande");
        a.setContentText(message != null ? message : "Erreur.");
        a.showAndWait();
    }

    private record PaymentMethodOption(String label, String code) {
        @Override
        public String toString() {
            return label;
        }
    }
}
