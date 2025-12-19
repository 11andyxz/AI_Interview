package com.aiinterview.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PaymentIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testPaymentFlow_StripeNotConfigured() throws Exception {
        // Test that payment endpoints handle missing configuration gracefully
        mockMvc.perform(get("/api/payment/stripe/status")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk());
    }
}

