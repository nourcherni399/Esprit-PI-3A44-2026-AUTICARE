package org.example.services;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.example.models.CheckoutFormData;
import org.example.models.User;

import java.awt.Desktop;
import java.net.URI;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service Stripe Checkout (paiement carte via page hébergée Stripe).
 */
public class StripeCheckoutService {

    private static final String DEFAULT_CURRENCY = "eur";
    private static final String DEFAULT_SUCCESS_URL = "https://example.com/payment/success?session_id={CHECKOUT_SESSION_ID}";
    private static final String DEFAULT_CANCEL_URL = "https://example.com/payment/cancel";

    public StripeCheckoutResult createSessionAndOpenBrowser(CheckoutFormData form, User user, double totalAmount)
        throws StripeException {
        String apiKey = resolveSecretKey();
        Stripe.apiKey = apiKey;

        long amountInMinorUnit = toMinorUnit(totalAmount);
        String currency = resolveCurrency();
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(resolveSuccessUrl())
            .setCancelUrl(resolveCancelUrl())
            .setCustomerEmail(form.email())
            .putMetadata("app_user_id", String.valueOf(user.getId()))
            .putMetadata("checkout_nom", safe(form.nom()))
            .putMetadata("checkout_mode", safe(form.modePayment()))
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency)
                            .setUnitAmount(amountInMinorUnit)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("Commande AutiCare")
                                    .setDescription("Paiement de commande en ligne")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build();

        Session session = Session.create(params);
        openBrowser(session.getUrl());
        return new StripeCheckoutResult(session.getId(), session.getPaymentIntent());
    }

    public boolean isSessionPaid(String sessionId) throws StripeException {
        String apiKey = resolveSecretKey();
        Stripe.apiKey = apiKey;
        Session session = Session.retrieve(sessionId);
        return "paid".equalsIgnoreCase(session.getPaymentStatus());
    }

    public String resolvePaymentIntent(String sessionId) throws StripeException {
        String apiKey = resolveSecretKey();
        Stripe.apiKey = apiKey;
        Session session = Session.retrieve(sessionId);
        return session.getPaymentIntent();
    }

    private static void openBrowser(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
            // L'utilisateur peut copier-coller l'URL si le navigateur n'est pas ouvert automatiquement.
        }
    }

    private static String resolveSecretKey() {
        String fromSystem = System.getProperty("stripe.secret.key");
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem.trim();
        }
        String fromEnv = System.getenv("STRIPE_SECRET_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromLocalProps = AssistantSecretsLoader.get("stripe.secret.key");
        if (fromLocalProps != null && !fromLocalProps.isBlank()) {
            return fromLocalProps.trim();
        }
        throw new IllegalStateException("Clé Stripe absente. Configurez STRIPE_SECRET_KEY ou stripe.secret.key.");
    }

    private static String resolveCurrency() {
        String fromSystem = System.getProperty("stripe.currency");
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem.trim().toLowerCase();
        }
        String fromEnv = System.getenv("STRIPE_CURRENCY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim().toLowerCase();
        }
        String fromLocalProps = AssistantSecretsLoader.get("stripe.currency");
        if (fromLocalProps != null && !fromLocalProps.isBlank()) {
            return fromLocalProps.trim().toLowerCase();
        }
        return DEFAULT_CURRENCY;
    }

    private static String resolveSuccessUrl() {
        String fromSystem = System.getProperty("stripe.checkout.success.url");
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem.trim();
        }
        String fromEnv = System.getenv("STRIPE_CHECKOUT_SUCCESS_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromLocalProps = AssistantSecretsLoader.get("stripe.checkout.success.url");
        if (fromLocalProps != null && !fromLocalProps.isBlank()) {
            return fromLocalProps.trim();
        }
        return DEFAULT_SUCCESS_URL;
    }

    private static String resolveCancelUrl() {
        String fromSystem = System.getProperty("stripe.checkout.cancel.url");
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem.trim();
        }
        String fromEnv = System.getenv("STRIPE_CHECKOUT_CANCEL_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromLocalProps = AssistantSecretsLoader.get("stripe.checkout.cancel.url");
        if (fromLocalProps != null && !fromLocalProps.isBlank()) {
            return fromLocalProps.trim();
        }
        return DEFAULT_CANCEL_URL;
    }

    private static long toMinorUnit(double amount) {
        int multiplier = resolveMultiplier();
        BigDecimal value = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(multiplier));
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static int resolveMultiplier() {
        String fromSystem = System.getProperty("stripe.amount.multiplier");
        String fromEnv = System.getenv("STRIPE_AMOUNT_MULTIPLIER");
        String fromLocalProps = AssistantSecretsLoader.get("stripe.amount.multiplier");
        String raw = fromSystem;
        if (raw == null || raw.isBlank()) {
            raw = fromEnv;
        }
        if (raw == null || raw.isBlank()) {
            raw = fromLocalProps;
        }
        if (raw == null || raw.isBlank()) {
            return 100;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : 100;
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record StripeCheckoutResult(String sessionId, String paymentIntentId) {
    }
}
