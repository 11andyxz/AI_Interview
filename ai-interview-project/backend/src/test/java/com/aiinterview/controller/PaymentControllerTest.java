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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @MockBean
    private UserSubscriptionRepository userSubscriptionRepository;

    @MockBean
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private StripeService stripeService;

    @MockBean
    private AlipayService alipayService;

    @MockBean
    private SubscriptionService subscriptionService;

    @Autowired
    private ObjectMapper objectMapper;

    private SubscriptionPlan testPlan;
    private UserSubscription testSubscription;
    private PaymentTransaction testTransaction;
    private Long userId = 1L;
    private Integer planId = 1;

    @BeforeEach
    void setUp() {
        testPlan = new SubscriptionPlan();
        testPlan.setId(planId);
        testPlan.setName("Basic Plan");
        testPlan.setDescription("Basic subscription plan");
        testPlan.setPrice(new BigDecimal("9.99"));
        testPlan.setCurrency("USD");
        testPlan.setBillingCycle("monthly");
        testPlan.setIsActive(true);

        testSubscription = new UserSubscription();
        testSubscription.setId(1L);
        testSubscription.setUserId(userId);
        testSubscription.setPlanId(planId);
        testSubscription.setStatus("active");
        testSubscription.setStartDate(LocalDateTime.now());
        testSubscription.setStripeSubscriptionId("sub_test123");

        testTransaction = new PaymentTransaction();
        testTransaction.setId(1L);
        testTransaction.setUserId(userId);
        testTransaction.setAmount(new BigDecimal("9.99"));
        testTransaction.setCurrency("USD");
        testTransaction.setPaymentMethod("stripe");
        testTransaction.setStatus("success");
        testTransaction.setTransactionId("txn_test123");
    }

    @Test
    void testGetPlans_Success() throws Exception {
        when(subscriptionPlanRepository.findByIsActiveTrue())
            .thenReturn(Arrays.asList(testPlan));

        mockMvc.perform(get("/api/payment/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(planId))
            .andExpect(jsonPath("$[0].name").value("Basic Plan"))
            .andExpect(jsonPath("$[0].price").value(9.99));
    }

    @Test
    void testGetPlans_Empty() throws Exception {
        when(subscriptionPlanRepository.findByIsActiveTrue())
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/payment/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testCreateCheckout_Stripe_Success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId);
        request.put("planId", planId);
        request.put("paymentMethod", "stripe");
        request.put("successUrl", "http://localhost:3000/success");
        request.put("cancelUrl", "http://localhost:3000/cancel");

        Map<String, Object> stripeResult = new HashMap<>();
        stripeResult.put("sessionId", "session_test123");
        stripeResult.put("url", "https://checkout.stripe.com/test");

        when(stripeService.isConfigured()).thenReturn(true);
        when(stripeService.createCheckoutSession(eq(userId), eq(planId), anyString(), anyString()))
            .thenReturn(stripeResult);

        mockMvc.perform(post("/api/payment/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("session_test123"))
            .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void testCreateCheckout_Stripe_NotConfigured() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId);
        request.put("planId", planId);
        request.put("paymentMethod", "stripe");

        when(stripeService.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/payment/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Stripe is not configured"));
    }

    @Test
    void testCreateCheckout_Alipay_Success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId);
        request.put("planId", planId);
        request.put("paymentMethod", "alipay");

        Map<String, Object> alipayResult = new HashMap<>();
        alipayResult.put("orderId", "order_test123");
        alipayResult.put("paymentUrl", "https://alipay.com/test");

        when(alipayService.isConfigured()).thenReturn(true);
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(testPlan));
        when(alipayService.createSubscriptionOrder(eq(userId), eq(planId), any(), anyString()))
            .thenReturn(alipayResult);

        mockMvc.perform(post("/api/payment/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("order_test123"));
    }

    @Test
    void testCreateCheckout_Alipay_NotConfigured() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId);
        request.put("planId", planId);
        request.put("paymentMethod", "alipay");

        when(alipayService.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/payment/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Alipay is not configured"));
    }

    @Test
    void testCreateCheckout_InvalidPaymentMethod() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId);
        request.put("planId", planId);
        request.put("paymentMethod", "invalid");

        mockMvc.perform(post("/api/payment/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid payment method"));
    }

    @Test
    void testGetUserSubscriptions_Success() throws Exception {
        when(userSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Arrays.asList(testSubscription));

        mockMvc.perform(get("/api/payment/subscriptions")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].status").value("active"));
    }

    @Test
    void testGetUserSubscriptions_Empty() throws Exception {
        when(userSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/payment/subscriptions")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testCancelSubscription_Stripe_Success() throws Exception {
        Long subscriptionId = 1L;
        when(userSubscriptionRepository.findById(subscriptionId))
            .thenReturn(Optional.of(testSubscription));
        when(stripeService.cancelSubscription("sub_test123")).thenReturn(true);

        mockMvc.perform(post("/api/payment/subscriptions/{subscriptionId}/cancel", subscriptionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testCancelSubscription_Alipay_Success() throws Exception {
        Long subscriptionId = 1L;
        testSubscription.setStripeSubscriptionId(null);
        testSubscription.setAlipaySubscriptionId("alipay_sub123");
        when(userSubscriptionRepository.findById(subscriptionId))
            .thenReturn(Optional.of(testSubscription));

        mockMvc.perform(post("/api/payment/subscriptions/{subscriptionId}/cancel", subscriptionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testCancelSubscription_NotFound() throws Exception {
        Long subscriptionId = 999L;
        when(userSubscriptionRepository.findById(subscriptionId))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/payment/subscriptions/{subscriptionId}/cancel", subscriptionId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetPaymentHistory_Success() throws Exception {
        when(paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Arrays.asList(testTransaction));

        mockMvc.perform(get("/api/payment/history")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].amount").value(9.99))
            .andExpect(jsonPath("$[0].status").value("success"));
    }

    @Test
    void testGetPaymentHistory_Empty() throws Exception {
        when(paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/payment/history")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testHandleStripeWebhook_Success() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        String signature = "test_signature";

        mockMvc.perform(post("/api/payment/webhook/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"));
    }

    @Test
    void testHandleAlipayCallback_Success() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("out_trade_no", "order123");

        when(alipayService.handlePaymentCallback(any(Map.class))).thenReturn(true);

        mockMvc.perform(post("/api/payment/webhook/alipay")
                .param("trade_status", "TRADE_SUCCESS")
                .param("out_trade_no", "order123"))
            .andExpect(status().isOk())
            .andExpect(content().string("success"));
    }

    @Test
    void testHandleAlipayCallback_Failure() throws Exception {
        when(alipayService.handlePaymentCallback(any(Map.class))).thenReturn(false);

        mockMvc.perform(post("/api/payment/webhook/alipay")
                .param("trade_status", "TRADE_FAILED"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("fail"));
    }
}

