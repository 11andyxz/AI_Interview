package com.aiinterview.controller;

import com.aiinterview.model.PaymentTransaction;
import com.aiinterview.model.SubscriptionPlan;
import com.aiinterview.model.UserSubscription;
import com.aiinterview.repository.PaymentTransactionRepository;
import com.aiinterview.repository.SubscriptionPlanRepository;
import com.aiinterview.repository.UserSubscriptionRepository;
import com.aiinterview.service.AlipayService;
import com.aiinterview.service.StripeService;
import com.aiinterview.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "http://localhost:3000")
public class PaymentController {
    
    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;
    
    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;
    
    @Autowired
    private StripeService stripeService;
    
    @Autowired
    private AlipayService alipayService;
    
    @Autowired
    private SubscriptionService subscriptionService;

    /**
     * Get all available subscription plans
     */
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getPlans() {
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findByIsActiveTrue();
        return ResponseEntity.ok(plans);
    }

    /**
     * Create checkout session
     */
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> createCheckout(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Integer planId = Integer.valueOf(request.get("planId").toString());
        String paymentMethod = request.getOrDefault("paymentMethod", "stripe").toString();
        String successUrl = request.getOrDefault("successUrl", "http://localhost:3000/payment/success").toString();
        String cancelUrl = request.getOrDefault("cancelUrl", "http://localhost:3000/payment/cancel").toString();
        
        Map<String, Object> result = new HashMap<>();
        
        if ("stripe".equals(paymentMethod)) {
            if (!stripeService.isConfigured()) {
                result.put("error", "Stripe is not configured");
                return ResponseEntity.badRequest().body(result);
            }
            result = stripeService.createCheckoutSession(userId, planId, successUrl, cancelUrl);
        } else if ("alipay".equals(paymentMethod)) {
            if (!alipayService.isConfigured()) {
                result.put("error", "Alipay is not configured");
                return ResponseEntity.badRequest().body(result);
            }
            Optional<SubscriptionPlan> plan = subscriptionPlanRepository.findById(planId);
            if (plan.isPresent()) {
                result = alipayService.createSubscriptionOrder(userId, planId, plan.get().getPrice(), plan.get().getCurrency());
            }
        } else {
            result.put("error", "Invalid payment method");
            return ResponseEntity.badRequest().body(result);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get user subscriptions
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<List<UserSubscription>> getUserSubscriptions(@RequestParam Long userId) {
        List<UserSubscription> subscriptions = userSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(subscriptions);
    }

    /**
     * Cancel subscription
     */
    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelSubscription(@PathVariable Long subscriptionId) {
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository.findById(subscriptionId);
        if (subscriptionOpt.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Subscription not found");
            return ResponseEntity.notFound().build();
        }
        
        UserSubscription subscription = subscriptionOpt.get();
        boolean cancelled = false;
        
        if (subscription.getStripeSubscriptionId() != null) {
            cancelled = stripeService.cancelSubscription(subscription.getStripeSubscriptionId());
        } else if (subscription.getAlipaySubscriptionId() != null) {
            cancelled = alipayService.cancelSubscription(subscription.getAlipaySubscriptionId());
        } else {
            subscription.setStatus("cancelled");
            userSubscriptionRepository.save(subscription);
            cancelled = true;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", cancelled);
        return ResponseEntity.ok(result);
    }

    /**
     * Get payment history
     */
    @GetMapping("/history")
    public ResponseEntity<List<PaymentTransaction>> getPaymentHistory(@RequestParam Long userId) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Stripe webhook endpoint
     */
    @PostMapping("/webhook/stripe")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload, 
                                                     @RequestHeader("Stripe-Signature") String signature) {
        stripeService.handleWebhook(payload, signature);
        return ResponseEntity.ok("OK");
    }

    /**
     * Alipay callback endpoint
     */
    @PostMapping("/webhook/alipay")
    public ResponseEntity<String> handleAlipayCallback(@RequestParam Map<String, String> params) {
        boolean success = alipayService.handlePaymentCallback(params);
        if (success) {
            return ResponseEntity.ok("success");
        }
        return ResponseEntity.badRequest().body("fail");
    }
}

