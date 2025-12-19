package com.aiinterview.controller;

import com.aiinterview.model.User;
import com.aiinterview.model.UserPreferences;
import com.aiinterview.service.UserPreferencesService;
import com.aiinterview.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private UserPreferencesService userPreferencesService;

    @InjectMocks
    private UserController userController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getUserProfile_Success() throws Exception {
        // Given
        Map<String, Object> profile = Map.of("id", 1L, "username", "testuser");
        when(userProfileService.getUserProfile(1L)).thenReturn(profile);

        // When & Then
        mockMvc.perform(get("/api/user/profile")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void getUserProfile_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUserProfile_Success() throws Exception {
        // Given
        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setUsername("updateduser");

        Map<String, Object> updates = Map.of("username", "updateduser");
        when(userProfileService.updateUserProfile(eq(1L), any())).thenReturn(updatedUser);

        // When & Then
        mockMvc.perform(put("/api/user/profile")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.id").value(1));
    }

    @Test
    void updateUserProfile_Error() throws Exception {
        // Given
        when(userProfileService.updateUserProfile(eq(1L), any()))
                .thenThrow(new RuntimeException("Update failed"));

        Map<String, Object> updates = Map.of("username", "updateduser");

        // When & Then
        mockMvc.perform(put("/api/user/profile")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserPoints_Success() throws Exception {
        // Given
        when(userProfileService.getUserPoints(1L)).thenReturn(100);

        // When & Then
        mockMvc.perform(get("/api/user/points")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(100))
                .andExpect(jsonPath("$.isUnlimited").value(false));
    }

    @Test
    void getUserPoints_Unlimited() throws Exception {
        // Given
        when(userProfileService.getUserPoints(1L)).thenReturn(Integer.MAX_VALUE);

        // When & Then
        mockMvc.perform(get("/api/user/points")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isUnlimited").value(true));
    }

    @Test
    void getSubscriptionStatus_Success() throws Exception {
        // Given
        Map<String, Object> status = Map.of("active", true, "plan", "premium");
        when(userProfileService.getSubscriptionStatus(1L)).thenReturn(status);

        // When & Then
        mockMvc.perform(get("/api/user/subscription-status")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.plan").value("premium"));
    }

    @Test
    void getUserStatistics_Success() throws Exception {
        // Given
        Map<String, Object> statistics = Map.of(
            "totalInterviews", 5,
            "averageScore", 85.5,
            "bestScore", 95
        );
        when(userProfileService.getUserStatistics(1L)).thenReturn(statistics);

        // When & Then
        mockMvc.perform(get("/api/user/statistics")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInterviews").value(5))
                .andExpect(jsonPath("$.averageScore").value(85.5));
    }

    @Test
    void getUserPreferences_Success() throws Exception {
        // Given
        UserPreferences preferences = new UserPreferences();
        preferences.setUserId(1L);
        preferences.setTheme("dark");
        when(userPreferencesService.getUserPreferences(1L)).thenReturn(preferences);

        // When & Then
        mockMvc.perform(get("/api/user/preferences")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.theme").value("dark"));
    }

    @Test
    void updateUserPreferences_Success() throws Exception {
        // Given
        UserPreferences updatedPrefs = new UserPreferences();
        updatedPrefs.setTheme("light");
        Map<String, Object> updates = Map.of("theme", "light");

        when(userPreferencesService.updateUserPreferences(eq(1L), any())).thenReturn(updatedPrefs);

        // When & Then
        mockMvc.perform(put("/api/user/preferences")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Preferences updated successfully"));
    }

    @Test
    void resetUserPreferences_Success() throws Exception {
        // Given
        UserPreferences resetPrefs = new UserPreferences();
        resetPrefs.setTheme("light"); // default theme
        when(userPreferencesService.resetUserPreferences(1L)).thenReturn(resetPrefs);

        // When & Then
        mockMvc.perform(post("/api/user/preferences/reset")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Preferences reset to defaults"));
    }
}