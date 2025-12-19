package com.aiinterview.controller;

import com.aiinterview.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    private Long userId = 1L;
    private Map<String, Object> mockProfile;

    @BeforeEach
    void setUp() {
        mockProfile = new HashMap<>();
        mockProfile.put("id", userId);
        mockProfile.put("username", "testuser");
        mockProfile.put("points", 1000);
        mockProfile.put("hasActiveSubscription", false);
        mockProfile.put("isInTrial", false);
    }

    @Test
    void testGetUserProfile_Success() throws Exception {
        when(userProfileService.getUserProfile(userId)).thenReturn(mockProfile);

        mockMvc.perform(get("/api/user/profile")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void testGetUserProfile_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetUserPoints_Success() throws Exception {
        when(userProfileService.getUserPoints(userId)).thenReturn(1000);

        mockMvc.perform(get("/api/user/points")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points").value(1000))
            .andExpect(jsonPath("$.isUnlimited").value(false));
    }

    @Test
    void testGetSubscriptionStatus_Success() throws Exception {
        Map<String, Object> status = new HashMap<>();
        status.put("hasActiveSubscription", false);
        status.put("isInTrial", false);
        status.put("pointsLimit", 1000);
        status.put("interviewsLimit", 5);

        when(userProfileService.getSubscriptionStatus(userId)).thenReturn(status);

        mockMvc.perform(get("/api/user/subscription-status")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasActiveSubscription").value(false))
            .andExpect(jsonPath("$.pointsLimit").value(1000));
    }

    @Test
    void testUpdateUserProfile_Success() throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("username", "newusername");

        when(userProfileService.updateUserProfile(eq(userId), any(Map.class)))
            .thenReturn(new com.aiinterview.model.User());

        mockMvc.perform(put("/api/user/profile")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newusername\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}

