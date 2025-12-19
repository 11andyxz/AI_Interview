package com.aiinterview.service;

import com.aiinterview.model.PaymentTransaction;
import com.aiinterview.model.SubscriptionPlan;
import com.aiinterview.model.UserSubscription;
import com.aiinterview.repository.PaymentTransactionRepository;
import com.aiinterview.repository.SubscriptionPlanRepository;
import com.aiinterview.repository.UserSubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Value("${stripe.api.key.secret:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook.secret:}")
    private String stripeWebhookSecret;

    @Value("${stripe.api.key.publishable:}")
    private String stripePublishableKey;

    /**
     * Initialize Stripe with API key
     */
    private void initStripe() {
        if (stripeSecretKey != null && !stripeSecretKey.isEmpty()) {
            Stripe.apiKey = stripeSecretKey;
        }
    }

    /**
     * Create subscription checkout session
     */
    public Map<String, Object> createCheckoutSession(Long userId, Integer planId, String successUrl, String cancelUrl) {
        if (!isConfigured()) {
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Stripe is not configured. Please set stripe.api.key.secret in application.properties");
            return result;
        }

        try {
            initStripe();

            // Get subscription plan
            Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(planId);
            if (planOpt.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("error", "Subscription plan not found");
                return result;
            }
            SubscriptionPlan plan = planOpt.get();

            // Create Stripe checkout session
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .putMetadata("userId", userId.toString())
                .putMetadata("planId", planId.toString());

            // Add line items
            PriceData priceData = PriceData.builder()
                .setCurrency(plan.getCurrency().toLowerCase())
                .setUnitAmount(plan.getPrice().multiply(BigDecimal.valueOf(100)).longValue()) // Convert to cents
                .setProductData(
                    PriceData.ProductData.builder()
                        .setName(plan.getName())
                        .setDescription(plan.getDescription())
                        .build()
                )
                .build();

            LineItem lineItem = LineItem.builder()
                .setPriceData(priceData)
                .setQuantity(1L)
                .build();

            paramsBuilder.addLineItem(lineItem);

            SessionCreateParams params = paramsBuilder.build();
            Session session = Session.create(params);

            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", session.getId());
            result.put("url", session.getUrl());
            result.put("success", true);

            return result;

        } catch (StripeException e) {
            logger.error("Failed to create Stripe checkout session", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Failed to create checkout session: " + e.getMessage());
            return result;
        } catch (Exception e) {
            logger.error("Unexpected error creating checkout session", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Unexpected error: " + e.getMessage());
            return result;
        }
    }

    /**
     * Handle Stripe webhook events
     */
    public void handleWebhook(String payload, String signature) {
        if (!isConfigured() || stripeWebhookSecret == null || stripeWebhookSecret.isEmpty()) {
            logger.warn("Stripe webhook secret not configured, skipping webhook processing");
            return;
        }

        try {
            initStripe();

            // Verify webhook signature
            Event event = Webhook.constructEvent(payload, signature, stripeWebhookSecret);

            // Handle the event
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = null;

            if (deserializer.getObject().isPresent()) {
                stripeObject = deserializer.getObject().get();
            } else {
                logger.error("Failed to deserialize webhook event: {}", event.getId());
                return;
            }

            switch (event.getType()) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted((Session) stripeObject);
                    break;
                case "customer.subscription.created":
                    handleSubscriptionCreated((Subscription) stripeObject);
                    break;
                case "customer.subscription.updated":
                    handleSubscriptionUpdated((Subscription) stripeObject);
                    break;
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted((Subscription) stripeObject);
                    break;
                case "invoice.payment_succeeded":
                    handleInvoicePaymentSucceeded(stripeObject);
                    break;
                case "invoice.payment_failed":
                    handleInvoicePaymentFailed(stripeObject);
                    break;
                default:
                    logger.info("Unhandled event type: {}", event.getType());
            }

        } catch (SignatureVerificationException e) {
            logger.error("Webhook signature verification failed", e);
            throw new RuntimeException("Webhook signature verification failed", e);
        } catch (Exception e) {
            logger.error("Error processing webhook", e);
            throw new RuntimeException("Error processing webhook", e);
        }
    }

    private void handleCheckoutSessionCompleted(Session session) {
        try {
            String userId = session.getMetadata().get("userId");
            String planId = session.getMetadata().get("planId");

            if (userId == null || planId == null) {
                logger.error("Missing userId or planId in session metadata");
                return;
            }

            logger.info("Processing completed checkout session for user: {}, plan: {}", userId, planId);

            // The subscription will be created via the customer.subscription.created event
            // Save transaction record
            saveTransaction(
                Long.valueOf(userId),
                null, // Will be updated when subscription is created
                BigDecimal.valueOf(session.getAmountTotal() / 100.0), // Convert from cents
                session.getCurrency(),
                session.getId(),
                "completed"
            );

        } catch (Exception e) {
            logger.error("Error handling checkout session completed", e);
        }
    }

    private void handleSubscriptionCreated(Subscription subscription) {
        try {
            Customer customer = Customer.retrieve(subscription.getCustomer());
            String userId = customer.getMetadata().get("userId");

            if (userId == null) {
                logger.error("Missing userId in customer metadata");
                return;
            }

            // Get plan info from subscription metadata or price
            String planId = subscription.getMetadata().get("planId");
            if (planId == null && subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
                // Try to find plan by price
                planId = findPlanIdByPrice(subscription.getItems().getData().get(0).getPrice());
            }

            if (planId == null) {
                logger.error("Could not determine plan ID for subscription: {}", subscription.getId());
                return;
            }

            // Create user subscription record
            UserSubscription userSubscription = new UserSubscription();
            userSubscription.setUserId(Long.valueOf(userId));
            userSubscription.setPlanId(Integer.valueOf(planId));
            userSubscription.setStatus("active");
            userSubscription.setStartDate(LocalDateTime.now());
            userSubscription.setStripeSubscriptionId(subscription.getId());
            userSubscription.setPaymentMethod("stripe");

            // Set end date based on billing cycle (simplified)
            if ("month".equals(subscription.getItems().getData().get(0).getPrice().getRecurring().getInterval())) {
                userSubscription.setEndDate(LocalDateTime.now().plusMonths(1));
            } else if ("year".equals(subscription.getItems().getData().get(0).getPrice().getRecurring().getInterval())) {
                userSubscription.setEndDate(LocalDateTime.now().plusYears(1));
            }

            userSubscriptionRepository.save(userSubscription);

            // Update transaction record with subscription ID
            Optional<PaymentTransaction> transaction = paymentTransactionRepository
                .findByTransactionId(subscription.getLatestInvoice());
            transaction.ifPresent(t -> {
                t.setSubscriptionId(userSubscription.getId());
                paymentTransactionRepository.save(t);
            });

            logger.info("Created subscription for user: {}, plan: {}", userId, planId);

        } catch (Exception e) {
            logger.error("Error handling subscription created", e);
        }
    }

    private void handleSubscriptionUpdated(Subscription subscription) {
        try {
            Optional<UserSubscription> userSubOpt = userSubscriptionRepository
                .findByStripeSubscriptionId(subscription.getId());

            if (userSubOpt.isPresent()) {
                UserSubscription userSub = userSubOpt.get();
                userSub.setStatus(subscription.getStatus());
                userSubscriptionRepository.save(userSub);

                logger.info("Updated subscription status: {} for subscription: {}",
                    subscription.getStatus(), subscription.getId());
            }

        } catch (Exception e) {
            logger.error("Error handling subscription updated", e);
        }
    }

    private void handleSubscriptionDeleted(Subscription subscription) {
        try {
            Optional<UserSubscription> userSubOpt = userSubscriptionRepository
                .findByStripeSubscriptionId(subscription.getId());

            if (userSubOpt.isPresent()) {
                UserSubscription userSub = userSubOpt.get();
                userSub.setStatus("cancelled");
                userSub.setEndDate(LocalDateTime.now());
                userSubscriptionRepository.save(userSub);

                logger.info("Cancelled subscription: {}", subscription.getId());
            }

        } catch (Exception e) {
            logger.error("Error handling subscription deleted", e);
        }
    }

    private void handleInvoicePaymentSucceeded(StripeObject invoice) {
        logger.info("Invoice payment succeeded: {}", invoice.toString());
        // Could implement additional logic here
    }

    private void handleInvoicePaymentFailed(StripeObject invoice) {
        logger.error("Invoice payment failed: {}", invoice.toString());
        // Could implement additional logic here
    }

    /**
     * Cancel subscription
     */
    public boolean cancelSubscription(String stripeSubscriptionId) {
        if (!isConfigured()) {
            logger.warn("Stripe not configured, cannot cancel subscription");
            return false;
        }

        try {
            initStripe();

            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            Subscription canceledSubscription = subscription.cancel();

            // Update local record
            Optional<UserSubscription> userSubOpt = userSubscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId);
            if (userSubOpt.isPresent()) {
                UserSubscription userSub = userSubOpt.get();
                userSub.setStatus("cancelled");
                userSub.setEndDate(LocalDateTime.now());
                userSubscriptionRepository.save(userSub);
            }

            logger.info("Cancelled Stripe subscription: {}", stripeSubscriptionId);
            return true;

        } catch (StripeException e) {
            logger.error("Failed to cancel Stripe subscription: {}", stripeSubscriptionId, e);
            return false;
        }
    }

    /**
     * Save payment transaction
     */
    public PaymentTransaction saveTransaction(Long userId, Long subscriptionId, BigDecimal amount,
                                            String currency, String transactionId, String status) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUserId(userId);
        transaction.setSubscriptionId(subscriptionId);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setPaymentMethod("stripe");
        transaction.setTransactionId(transactionId);
        transaction.setStatus(status);
        return paymentTransactionRepository.save(transaction);
    }

    /**
     * Find plan ID by price (helper method)
     */
    private String findPlanIdByPrice(com.stripe.model.Price price) {
        try {
            BigDecimal stripePrice = BigDecimal.valueOf(price.getUnitAmount() / 100.0);
            String currency = price.getCurrency();

            // Find matching plan
            Optional<SubscriptionPlan> plan = subscriptionPlanRepository.findByIsActiveTrue()
                .stream()
                .filter(p -> p.getPrice().compareTo(stripePrice) == 0 &&
                           currency.equalsIgnoreCase(p.getCurrency()))
                .findFirst();

            return plan.map(p -> p.getId().toString()).orElse(null);

        } catch (Exception e) {
            logger.error("Error finding plan by price", e);
            return null;
        }
    }

    /**
     * Check if Stripe is configured
     */
    public boolean isConfigured() {
        return stripeSecretKey != null && !stripeSecretKey.isEmpty() &&
               stripeWebhookSecret != null && !stripeWebhookSecret.isEmpty();
    }

    /**
     * Get publishable key (for frontend)
     */
    public String getPublishableKey() {
        return stripePublishableKey;
    }
}

