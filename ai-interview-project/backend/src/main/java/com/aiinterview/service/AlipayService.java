package com.aiinterview.service;

import com.aiinterview.model.PaymentTransaction;
import com.aiinterview.model.SubscriptionPlan;
import com.aiinterview.model.UserSubscription;
import com.aiinterview.repository.PaymentTransactionRepository;
import com.aiinterview.repository.SubscriptionPlanRepository;
import com.aiinterview.repository.UserSubscriptionRepository;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
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
import java.util.UUID;

@Service
public class AlipayService {

    private static final Logger logger = LoggerFactory.getLogger(AlipayService.class);

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Value("${alipay.app.id:}")
    private String appId;

    @Value("${alipay.merchant.private.key:}")
    private String merchantPrivateKey;

    @Value("${alipay.alipay.public.key:}")
    private String alipayPublicKey;

    @Value("${alipay.gateway.url:https://openapi.alipay.com/gateway.do}")
    private String gatewayUrl;

    @Value("${alipay.return.url:http://localhost:3000/payment/success}")
    private String returnUrl;

    @Value("${alipay.notify.url:http://localhost:8080/api/payment/alipay/notify}")
    private String notifyUrl;

    private AlipayClient alipayClient;

    /**
     * Initialize Alipay client
     */
    private void initAlipayClient() {
        if (alipayClient == null && isConfigured()) {
            alipayClient = new DefaultAlipayClient(
                gatewayUrl,
                appId,
                merchantPrivateKey,
                "json",
                "UTF-8",
                alipayPublicKey,
                "RSA2"
            );
        }
    }

    /**
     * Create subscription order
     */
    public Map<String, Object> createSubscriptionOrder(Long userId, Integer planId, BigDecimal amount, String currency) {
        if (!isConfigured()) {
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Alipay is not configured. Please set alipay configuration in application.properties");
            return result;
        }

        try {
            initAlipayClient();

            // Get subscription plan
            Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(planId);
            if (planOpt.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("error", "Subscription plan not found");
                return result;
            }
            SubscriptionPlan plan = planOpt.get();

            // Generate unique order ID
            String orderId = "SUB_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

            // Create Alipay payment request
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setReturnUrl(returnUrl);
            request.setNotifyUrl(notifyUrl);

            // Set business parameters
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderId);
            bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
            bizContent.put("total_amount", amount.toString());
            bizContent.put("currency", currency);
            bizContent.put("subject", plan.getName() + " Subscription");
            bizContent.put("body", plan.getDescription());

            // Add custom parameters for tracking
            bizContent.put("passback_params", "userId=" + userId + "&planId=" + planId);

            request.setBizContent(com.alibaba.fastjson.JSON.toJSONString(bizContent));

            // Execute request
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);

            // Save transaction record
            saveTransaction(userId, null, amount, currency, orderId, "pending");

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderId);
            result.put("paymentUrl", response.getBody()); // This contains the payment form HTML
            result.put("success", true);

            return result;

        } catch (AlipayApiException e) {
            logger.error("Failed to create Alipay order", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Failed to create Alipay order: " + e.getMessage());
            return result;
        } catch (Exception e) {
            logger.error("Unexpected error creating Alipay order", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Unexpected error: " + e.getMessage());
            return result;
        }
    }

    /**
     * Handle Alipay payment callback
     */
    public boolean handlePaymentCallback(Map<String, String> params) {
        try {
            logger.info("Processing Alipay callback: {}", params);

            String orderId = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");
            String totalAmount = params.get("total_amount");

            if (orderId == null || tradeStatus == null) {
                logger.error("Missing required parameters in Alipay callback");
                return false;
            }

            // Verify the callback (simplified - in production, verify signature)
            if (!isValidCallback(params)) {
                logger.error("Invalid Alipay callback signature");
                return false;
            }

            // Update transaction status
            Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository
                .findByTransactionId(orderId);
            if (transactionOpt.isPresent()) {
                PaymentTransaction transaction = transactionOpt.get();

                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    transaction.setStatus("completed");

                    // Create subscription if payment successful
                    createSubscriptionFromPayment(transaction, params);

                } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                    transaction.setStatus("failed");
                }

                paymentTransactionRepository.save(transaction);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error processing Alipay callback", e);
            return false;
        }
    }

    /**
     * Create subscription from successful payment
     */
    private void createSubscriptionFromPayment(PaymentTransaction transaction, Map<String, String> params) {
        try {
            // Extract userId and planId from passback_params
            String passbackParams = params.get("passback_params");
            if (passbackParams == null) {
                logger.error("Missing passback_params in Alipay callback");
                return;
            }

            String[] paramPairs = passbackParams.split("&");
            Long userId = null;
            Integer planId = null;

            for (String pair : paramPairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    if ("userId".equals(keyValue[0])) {
                        userId = Long.valueOf(keyValue[1]);
                    } else if ("planId".equals(keyValue[0])) {
                        planId = Integer.valueOf(keyValue[1]);
                    }
                }
            }

            if (userId == null || planId == null) {
                logger.error("Missing userId or planId in passback_params");
                return;
            }

            // Create user subscription
            UserSubscription subscription = new UserSubscription();
            subscription.setUserId(userId);
            subscription.setPlanId(planId);
            subscription.setStatus("active");
            subscription.setStartDate(LocalDateTime.now());
            subscription.setPaymentMethod("alipay");
            subscription.setAlipaySubscriptionId(params.get("trade_no"));

            // Set end date based on plan (simplified)
            Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(planId);
            if (planOpt.isPresent()) {
                SubscriptionPlan plan = planOpt.get();
                if ("monthly".equalsIgnoreCase(plan.getBillingCycle())) {
                    subscription.setEndDate(LocalDateTime.now().plusMonths(1));
                } else if ("yearly".equalsIgnoreCase(plan.getBillingCycle())) {
                    subscription.setEndDate(LocalDateTime.now().plusYears(1));
                }
            }

            UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);

            // Update transaction with subscription ID
            transaction.setSubscriptionId(savedSubscription.getId());
            paymentTransactionRepository.save(transaction);

            logger.info("Created Alipay subscription for user: {}, plan: {}", userId, planId);

        } catch (Exception e) {
            logger.error("Error creating subscription from Alipay payment", e);
        }
    }

    /**
     * Query order status
     */
    public String queryOrderStatus(String orderId) {
        if (!isConfigured()) {
            return "unknown";
        }

        try {
            initAlipayClient();

            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            Map<String, String> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderId);
            request.setBizContent(com.alibaba.fastjson.JSON.toJSONString(bizContent));

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                return response.getTradeStatus();
            } else {
                logger.error("Failed to query Alipay order: {}", response.getMsg());
                return "unknown";
            }

        } catch (AlipayApiException e) {
            logger.error("Error querying Alipay order status", e);
            return "unknown";
        }
    }

    /**
     * Validate callback signature (simplified)
     */
    private boolean isValidCallback(Map<String, String> params) {
        // In production, implement proper signature verification
        // For now, just check if required fields are present
        return params.containsKey("out_trade_no") &&
               params.containsKey("trade_status") &&
               params.containsKey("total_amount");
    }

    /**
     * Cancel subscription (Alipay doesn't have direct subscription cancellation like Stripe)
     */
    public boolean cancelSubscription(String alipaySubscriptionId) {
        // Alipay doesn't support subscription cancellation like Stripe
        // We can only mark the local subscription as cancelled
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
            .findByAlipaySubscriptionId(alipaySubscriptionId);

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();
            subscription.setStatus("cancelled");
            subscription.setEndDate(LocalDateTime.now());
            userSubscriptionRepository.save(subscription);
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
        transaction.setPaymentMethod("alipay");
        transaction.setTransactionId(transactionId);
        transaction.setStatus(status);
        return paymentTransactionRepository.save(transaction);
    }

    /**
     * Check if Alipay is configured
     */
    public boolean isConfigured() {
        return appId != null && !appId.isEmpty()
            && merchantPrivateKey != null && !merchantPrivateKey.isEmpty()
            && alipayPublicKey != null && !alipayPublicKey.isEmpty();
    }
}

