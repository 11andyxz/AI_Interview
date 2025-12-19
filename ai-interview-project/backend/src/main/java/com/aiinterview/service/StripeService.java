package com.aiinterview.service;

import com.aiinterview.model.PaymentTransaction;
import com.aiinterview.model.UserSubscription;
import com.aiinterview.repository.PaymentTransactionRepository;
import com.aiinterview.repository.UserSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    
    @Value("${stripe.api.key.secret:}")
    private String stripeSecretKey;
    
    @Value("${stripe.api.key.publishable:}")
    private String stripePublishableKey;

    /**
     * Create subscription checkout session
     */
    public Map<String, Object> createCheckoutSession(Long userId, Integer planId, String successUrl, String cancelUrl) {
        // TODO: Implement Stripe checkout session creation
        // For now, return placeholder
        logger.warn("Stripe integration not fully configured. Please set stripe.api.key.secret in application.properties");
        
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", "placeholder_session_id");
        result.put("url", successUrl); // Placeholder
        result.put("message", "Stripe not configured. Please configure Stripe API keys.");
        return result;
    }

    /**
     * Handle Stripe webhook events
     */
    public void handleWebhook(String payload, String signature) {
        // TODO: Implement Stripe webhook handling
        // Verify signature and process events (subscription.created, subscription.updated, etc.)
        logger.info("Received Stripe webhook (not fully implemented)");
    }

    /**
     * Cancel subscription
     */
    public boolean cancelSubscription(String stripeSubscriptionId) {
        // TODO: Implement Stripe subscription cancellation
        Optional<UserSubscription> subscription = userSubscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
        if (subscription.isPresent()) {
            UserSubscription sub = subscription.get();
            sub.setStatus("cancelled");
            userSubscriptionRepository.save(sub);
            return true;
        }
        return false;
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
     * Check if Stripe is configured
     */
    public boolean isConfigured() {
        return stripeSecretKey != null && !stripeSecretKey.isEmpty();
    }
}

