package org.example.controllers;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.function.UnaryOperator;

public class PageCheckoutController implements PublicShellAware {

    private static final String PAYMENT_CASH_ON_DELIVERY = "a_la_livraison";
    private static final String PAYMENT_CARD = "carte_bancaire";
    private static final Pattern EMAIL_ALLOWED_PATTERN = Pattern.compile(
        "^[^\\s@]+@(gmail\\.com|icloud\\.com|icloud\\.fr)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final String DEFAULT_PHONE_REGION = "TN";
    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("^\\d{5}$");
    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();
    private static final List<PhoneCountryOption> PHONE_COUNTRIES = List.of(
        new PhoneCountryOption("Tunisie (+216)", "TN"),
        new PhoneCountryOption("France (+33)", "FR"),
        new PhoneCountryOption("Algerie (+213)", "DZ"),
        new PhoneCountryOption("Maroc (+212)", "MA")
    );

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
    private ComboBox<PhoneCountryOption> phoneCountryCombo;
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
        phoneCountryCombo.getItems().setAll(PHONE_COUNTRIES);
        phoneCountryCombo.getSelectionModel().select(
            PHONE_COUNTRIES.stream().filter(c -> DEFAULT_PHONE_REGION.equals(c.regionCode())).findFirst().orElse(PHONE_COUNTRIES.get(0))
        );
        ProductFormUi.styleStockCombo(phoneCountryCombo);
        setupInputConstraints();
        updateCardFieldsVisibility();
    }

    private void setupInputConstraints() {
        // Nom / ville / adresse: limite simple de longueur.
        installLengthLimiter(fullNameField, 120);
        installLengthLimiter(addressField, 180);
        installLengthLimiter(cityField, 80);
        installLengthLimiter(emailField, 180);

        // Téléphone local: uniquement chiffres + espaces (l'indicatif est choisi dans la liste des pays).
        installRegexFormatter(phoneField, text -> text.matches("[0-9\\s]{0,20}"));
        // Code postal: 5 chiffres max.
        installRegexFormatter(zipCodeField, text -> text.matches("\\d{0,5}"));
        // Paiement carte : saisie locale + contrôle Luhn (pas de page Stripe).
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
        prefillPhone(user.getTelephone());
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
            normalizePhoneForStorage(safeTrim(phoneField.getText()), getSelectedRegionCode()),
            safeTrim(addressField.getText()),
            safeTrim(zipCodeField.getText()),
            safeTrim(cityField.getText()),
            paymentCombo.getValue() != null ? paymentCombo.getValue().code() : PAYMENT_CASH_ON_DELIVERY,
            null
        );
        String cardLast4ForExcel = null;
        try {
            if (PAYMENT_CARD.equals(formData.modePayment())) {
                LocalCardConfirm cardOk = confirmLocalCardPayment();
                if (cardOk == null) {
                    return;
                }
                cardLast4ForExcel = cardOk.last4();
                formData = new CheckoutFormData(
                    formData.nom(),
                    formData.email(),
                    formData.telephone(),
                    formData.adresse(),
                    formData.codePostal(),
                    formData.ville(),
                    formData.modePayment(),
                    cardOk.paymentReference()
                );
            }
            OrderCheckoutService.PlaceOrderResult placeOrderResult = orderCheckoutService.placeOrder(user, formData);
            AppState.notifyCartChanged();

            if (PAYMENT_CARD.equals(formData.modePayment())) {
                promptSaveAndOpenPaymentExcel(formData, placeOrderResult.commandeId(), formData.stripePaymentIntent(), cardLast4ForExcel);
            }

            Alert emailInfo = new Alert(placeOrderResult.emailSent() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
            emailInfo.setTitle("E-mail de confirmation");
            emailInfo.setHeaderText(null);
            String validationMsg = "Votre commande est enregistrée et transmise à l’équipe pour validation. "
                + "Vous recevrez une notification lorsque la commande sera acceptée ou refusée.\n\n";
            if (placeOrderResult.emailSent()) {
                emailInfo.setContentText(validationMsg + "E-mail de confirmation envoyé à : " + formData.email());
            } else {
                String reason = placeOrderResult.emailErrorMessage() != null && !placeOrderResult.emailErrorMessage().isBlank()
                    ? placeOrderResult.emailErrorMessage()
                    : "Cause inconnue";
                emailInfo.setContentText(
                    validationMsg
                        + "Commande enregistrée, mais l'e-mail de confirmation n'a pas pu être envoyé.\n"
                        + "Détail SMTP: " + reason + "\n"
                        + "Vérifiez la configuration SMTP puis réessayez."
                );
            }
            Window ownerWin = getCheckoutOwnerWindow();
            if (ownerWin != null) {
                emailInfo.initOwner(ownerWin);
                emailInfo.initModality(Modality.WINDOW_MODAL);
            }
            emailInfo.showAndWait();
            backToPanier();
        } catch (SQLException ex) {
            alertErreurCommande(ex.getMessage() != null ? ex.getMessage() : "Impossible d’enregistrer la commande.");
        }
    }

    private record LocalCardConfirm(String paymentReference, String last4) {
    }

    /**
     * Paiement carte sans Stripe : enregistrement local + référence stockée en base (champ {@code stripe_payment_intent}).
     */
    private LocalCardConfirm confirmLocalCardPayment() {
        String digits = safeTrim(cardNumberField.getText()).replaceAll("\\D", "");
        if (!luhnCheck(digits)) {
            markInvalid(cardNumberField);
            alertSaisieChamp("Numéro de carte", "Numéro de carte invalide.");
            return null;
        }
        cardExpiryField.setText(normalizeExpiry(safeTrim(cardExpiryField.getText())));
        if (!validateExpiry(cardExpiryField.getText())) {
            markInvalid(cardExpiryField);
            alertSaisieChamp("Expiration", "Date MM/AA invalide ou carte expirée.");
            return null;
        }
        clearInvalid(cardNumberField);
        clearInvalid(cardExpiryField);
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : "****";
        String ref = "cb_auticare_" + last4 + "_" + System.currentTimeMillis();
        return new LocalCardConfirm(ref, last4);
    }

    private void promptSaveAndOpenPaymentExcel(
        CheckoutFormData form,
        int commandeId,
        String paymentReference,
        String last4
    ) {
        Window owner = getCheckoutOwnerWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Enregistrer le reçu de paiement (Excel)");
        fc.setInitialFileName(
            "paiement_carte_" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").format(LocalDateTime.now()) + ".xlsx"
        );
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        java.io.File dest = fc.showSaveDialog(owner);
        if (dest == null) {
            return;
        }
        try {
            writeCheckoutExcel(dest.toPath(), commandeId, paymentReference, last4);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dest);
            }
        } catch (Exception ex) {
            showWarning("La commande est enregistrée, mais l’export Excel n’a pas pu être ouvert : "
                + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
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
            Path tmp = Files.createTempFile("facture_auticare_", ".pdf");
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
        if (!isValidPhoneNumber(phone, getSelectedRegionCode())) {
            markInvalid(phoneField);
            markInvalid(phoneCountryCombo);
            alertSaisieChamp("Téléphone", "Numéro invalide pour le pays sélectionné.");
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
            cardExpiryField.setText(normalizeExpiry(safeTrim(cardExpiryField.getText())));
            String digits = safeTrim(cardNumberField.getText()).replaceAll("\\D", "");
            if (!luhnCheck(digits)) {
                markInvalid(cardNumberField);
                alertSaisieChamp("Numéro de carte", "Indiquez un numéro de carte valide (contrôle Luhn).");
            }
            if (!validateExpiry(cardExpiryField.getText())) {
                markInvalid(cardExpiryField);
                alertSaisieChamp("Expiration", "Indiquez une date MM/AA valide (carte non expirée).");
            }
        }

        return !isAnyCheckoutFieldInvalid();
    }

    /** Détecte si une classe {@code checkout-invalid} est présente (après validation). */
    private boolean isAnyCheckoutFieldInvalid() {
        return hasInvalidStyle(fullNameField)
            || hasInvalidStyle(emailField)
            || hasInvalidStyle(phoneField)
            || hasInvalidStyle(phoneCountryCombo)
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

    private String getSelectedRegionCode() {
        if (phoneCountryCombo == null || phoneCountryCombo.getValue() == null) {
            return DEFAULT_PHONE_REGION;
        }
        return phoneCountryCombo.getValue().regionCode();
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
            fc.setInitialFileName(OrderReceiptPdfService.defaultFileName());
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

    @FXML
    private void onExportCheckoutExcel() {
        if (cartLines == null || cartLines.isEmpty()) {
            showWarning("Aucun article à exporter.");
            return;
        }
        Window owner = getCheckoutOwnerWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter le checkout en Excel");
        fc.setInitialFileName("checkout_auticare.xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        java.io.File dest = fc.showSaveDialog(owner);
        if (dest == null) {
            return;
        }
        try {
            writeCheckoutExcel(dest.toPath(), null, null, null);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Export Excel");
            ok.setHeaderText(null);
            ok.setContentText("Fichier Excel enregistré :\n" + dest.getAbsolutePath());
            if (owner != null) {
                ok.initOwner(owner);
                ok.initModality(Modality.WINDOW_MODAL);
            }
            ok.showAndWait();
        } catch (Exception ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Export Excel impossible.");
        }
    }

    private void writeCheckoutExcel(Path target, Integer commandeId, String paymentReference, String cardLast4) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             OutputStream os = Files.newOutputStream(target)) {
            XSSFSheet sheet = wb.createSheet("Checkout");
            int rowIdx = 0;

            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue(
                paymentReference != null ? "Reçu de paiement — carte bancaire" : "Récapitulatif checkout"
            );

            if (paymentReference != null) {
                Row pay1 = sheet.createRow(rowIdx++);
                pay1.createCell(0).setCellValue("Statut");
                pay1.createCell(1).setCellValue("Payé par carte bancaire (AutiCare, sans redirection Stripe)");
                Row pay2 = sheet.createRow(rowIdx++);
                pay2.createCell(0).setCellValue("Référence paiement");
                pay2.createCell(1).setCellValue(paymentReference);
                if (cardLast4 != null && !cardLast4.isBlank()) {
                    Row pay3 = sheet.createRow(rowIdx++);
                    pay3.createCell(0).setCellValue("Carte (4 derniers chiffres)");
                    pay3.createCell(1).setCellValue(cardLast4);
                }
                if (commandeId != null && commandeId > 0) {
                    Row pay4 = sheet.createRow(rowIdx++);
                    pay4.createCell(0).setCellValue("Commande enregistrée");
                    pay4.createCell(1).setCellValue(commandeId);
                }
                Row pay5 = sheet.createRow(rowIdx++);
                pay5.createCell(0).setCellValue("Date");
                pay5.createCell(1).setCellValue(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH))
                );
                Row pay6 = sheet.createRow(rowIdx++);
                pay6.createCell(0).setCellValue("Montant total (DT)");
                pay6.createCell(1).setCellValue(CartService.totalPrice(cartLines));
            } else {
                rowIdx++;
            }

            rowIdx++;
            Row customerHeader = sheet.createRow(rowIdx++);
            customerHeader.createCell(0).setCellValue("Client");
            customerHeader.createCell(1).setCellValue("Email");
            customerHeader.createCell(2).setCellValue("Téléphone");
            customerHeader.createCell(3).setCellValue("Adresse");
            customerHeader.createCell(4).setCellValue("Code postal");
            customerHeader.createCell(5).setCellValue("Ville");
            customerHeader.createCell(6).setCellValue("Paiement");

            Row customer = sheet.createRow(rowIdx++);
            customer.createCell(0).setCellValue(safeTrim(fullNameField.getText()));
            customer.createCell(1).setCellValue(safeTrim(emailField.getText()));
            customer.createCell(2).setCellValue(normalizePhoneForStorage(safeTrim(phoneField.getText()), getSelectedRegionCode()));
            customer.createCell(3).setCellValue(safeTrim(addressField.getText()));
            customer.createCell(4).setCellValue(safeTrim(zipCodeField.getText()));
            customer.createCell(5).setCellValue(safeTrim(cityField.getText()));
            customer.createCell(6).setCellValue(paymentCombo.getValue() != null ? paymentCombo.getValue().toString() : "");

            rowIdx++;
            Row head = sheet.createRow(rowIdx++);
            head.createCell(0).setCellValue("Produit");
            head.createCell(1).setCellValue("Quantité");
            head.createCell(2).setCellValue("Prix unitaire (DT)");
            head.createCell(3).setCellValue("Sous-total (DT)");

            for (CartLineView line : cartLines) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(line.product() != null && line.product().getNom() != null ? line.product().getNom() : "Produit");
                r.createCell(1).setCellValue(line.quantity());
                r.createCell(2).setCellValue(line.unitPrice());
                r.createCell(3).setCellValue(line.lineTotal());
            }

            Row total = sheet.createRow(rowIdx);
            total.createCell(2).setCellValue("Total (DT)");
            total.createCell(3).setCellValue(CartService.totalPrice(cartLines));

            for (int i = 0; i <= 6; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(os);
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
        clearInvalid(phoneCountryCombo);
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

    private static boolean isValidPhoneNumber(String raw, String regionCode) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            String region = regionCode == null || regionCode.isBlank() ? DEFAULT_PHONE_REGION : regionCode;
            Phonenumber.PhoneNumber phone = PHONE_UTIL.parse(raw, region);
            if (!PHONE_UTIL.isValidNumber(phone)) {
                return false;
            }
            if (raw.trim().startsWith("+")) {
                return true;
            }
            return PHONE_UTIL.isValidNumberForRegion(phone, region);
        } catch (NumberParseException ignored) {
            return false;
        }
    }

    private static String normalizePhoneForStorage(String raw, String regionCode) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            String region = regionCode == null || regionCode.isBlank() ? DEFAULT_PHONE_REGION : regionCode;
            Phonenumber.PhoneNumber phone = PHONE_UTIL.parse(raw, region);
            if (PHONE_UTIL.isValidNumber(phone)) {
                return PHONE_UTIL.format(phone, PhoneNumberUtil.PhoneNumberFormat.E164);
            }
        } catch (NumberParseException ignored) {
            // fallback
        }
        return raw.trim();
    }

    private void prefillPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            phoneField.setText("");
            return;
        }
        try {
            Phonenumber.PhoneNumber parsed = PHONE_UTIL.parse(rawPhone, DEFAULT_PHONE_REGION);
            if (!PHONE_UTIL.isValidNumber(parsed)) {
                phoneField.setText(rawPhone);
                return;
            }
            String region = PHONE_UTIL.getRegionCodeForNumber(parsed);
            if (region != null && phoneCountryCombo != null) {
                PHONE_COUNTRIES.stream()
                    .filter(c -> region.equalsIgnoreCase(c.regionCode()))
                    .findFirst()
                    .ifPresent(c -> phoneCountryCombo.getSelectionModel().select(c));
            }
            phoneField.setText(String.valueOf(parsed.getNationalNumber()));
        } catch (NumberParseException ignored) {
            phoneField.setText(rawPhone);
        }
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

    private record PhoneCountryOption(String label, String regionCode) {
        @Override
        public String toString() {
            return label;
        }
    }
}
