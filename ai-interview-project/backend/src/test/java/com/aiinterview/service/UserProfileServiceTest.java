package com.aiinterview.service;

import com.aiinterview.model.User;
import com.aiinterview.model.UserPoints;
import com.aiinterview.repository.UserRepository;
import com.aiinterview.repository.UserPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPointsRepository userPointsRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private UserProfileService userProfileService;

    private User testUser;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("testuser");
        testUser.setPassword("hashedpassword");
    }

    @Test
    void testGetUserProfile_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(false);
        when(subscriptionService.isInTrialPeriod(userId)).thenReturn(false);
        when(subscriptionService.getUserSubscription(userId)).thenReturn(Optional.empty());
        when(userPointsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        Map<String, Object> profile = userProfileService.getUserProfile(userId);

        assertNotNull(profile);
        assertEquals(userId, profile.get("id"));
        assertEquals("testuser", profile.get("username"));
        assertFalse((Boolean) profile.get("hasActiveSubscription"));
        assertFalse((Boolean) profile.get("isInTrial"));
    }

    @Test
    void testGetUserProfile_UserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userProfileService.getUserProfile(userId);
        });
    }

    @Test
    void testGetUserPoints_WithActiveSubscription() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);

        Integer points = userProfileService.getUserPoints(userId);

        assertEquals(Integer.MAX_VALUE, points);
        verify(userPointsRepository, never()).findByUserId(any());
    }

    @Test
    void testGetUserPoints_WithoutSubscription_ExistingPoints() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(false);
        UserPoints userPoints = new UserPoints();
        userPoints.setUserId(userId);
        userPoints.setPoints(1000);
        when(userPointsRepository.findByUserId(userId)).thenReturn(Optional.of(userPoints));

        Integer points = userProfileService.getUserPoints(userId);

        assertEquals(1000, points);
    }

    @Test
    void testGetUserPoints_WithoutSubscription_NoExistingPoints() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(false);
        when(userPointsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userPointsRepository.save(any(UserPoints.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Integer points = userProfileService.getUserPoints(userId);

        assertEquals(1000, points); // Default points
        verify(userPointsRepository).save(any(UserPoints.class));
    }

    @Test
    void testGetSubscriptionStatus_WithActiveSubscription() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(subscriptionService.isInTrialPeriod(userId)).thenReturn(false);
        when(subscriptionService.getUserSubscription(userId)).thenReturn(Optional.empty());

        Map<String, Object> status = userProfileService.getSubscriptionStatus(userId);

        assertTrue((Boolean) status.get("hasActiveSubscription"));
        assertFalse((Boolean) status.get("isInTrial"));
        assertEquals("unlimited", status.get("pointsLimit"));
        assertEquals("unlimited", status.get("interviewsLimit"));
    }

    @Test
    void testGetSubscriptionStatus_WithoutSubscription() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(false);
        when(subscriptionService.isInTrialPeriod(userId)).thenReturn(false);
        when(subscriptionService.getUserSubscription(userId)).thenReturn(Optional.empty());
        when(userPointsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userPointsRepository.save(any(UserPoints.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> status = userProfileService.getSubscriptionStatus(userId);

        assertFalse((Boolean) status.get("hasActiveSubscription"));
        assertEquals(1000, status.get("pointsLimit"));
        assertEquals(5, status.get("interviewsLimit"));
    }

    @Test
    void testUpdateUserProfile_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("newusername")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", "newusername");

        User updated = userProfileService.updateUserProfile(userId, updates);

        assertEquals("newusername", updated.getUsername());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testUpdateUserProfile_UserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userProfileService.updateUserProfile(userId, new HashMap<>());
        });
    }

    @Test
    void testUpdateUserProfile_UsernameAlreadyExists() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", "existinguser");

        assertThrows(RuntimeException.class, () -> {
            userProfileService.updateUserProfile(userId, updates);
        });
    }
}

