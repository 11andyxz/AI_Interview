package com.aiinterview.service;

import com.aiinterview.model.UserSubscription;
import com.aiinterview.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {
    
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @InjectMocks
    private SubscriptionService subscriptionService;
    
    private UserSubscription activeSubscription;
    private UserSubscription trialSubscription;
    
    @BeforeEach
    void setUp() {
        activeSubscription = new UserSubscription();
        activeSubscription.setId(1L);
        activeSubscription.setUserId(1L);
        activeSubscription.setPlanId(1);
        activeSubscription.setStatus("active");
        activeSubscription.setStartDate(LocalDateTime.now().minusDays(5));
        activeSubscription.setEndDate(LocalDateTime.now().plusDays(25));
        
        trialSubscription = new UserSubscription();
        trialSubscription.setId(2L);
        trialSubscription.setUserId(2L);
        trialSubscription.setPlanId(1);
        trialSubscription.setStatus("trial");
        trialSubscription.setStartDate(LocalDateTime.now().minusDays(2));
        trialSubscription.setTrialEndDate(LocalDateTime.now().plusDays(5));
    }
    
    @Test
    void testHasActiveSubscription() {
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, "active"))
            .thenReturn(Optional.of(activeSubscription));
        
        boolean hasActive = subscriptionService.hasActiveSubscription(1L);
        
        assertTrue(hasActive);
    }
    
    @Test
    void testHasActiveSubscriptionNotFound() {
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, "active"))
            .thenReturn(Optional.empty());
        
        boolean hasActive = subscriptionService.hasActiveSubscription(1L);
        
        assertFalse(hasActive);
    }
    
    @Test
    void testIsInTrialPeriod() {
        when(userSubscriptionRepository.findByUserIdAndStatus(2L, "trial"))
            .thenReturn(Optional.of(trialSubscription));
        
        boolean inTrial = subscriptionService.isInTrialPeriod(2L);
        
        assertTrue(inTrial);
    }
    
    @Test
    void testStartTrial() {
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        UserSubscription trial = subscriptionService.startTrial(3L, 1);
        
        assertNotNull(trial);
        assertEquals("trial", trial.getStatus());
        assertEquals(3L, trial.getUserId());
        assertNotNull(trial.getTrialEndDate());
        verify(userSubscriptionRepository).save(any(UserSubscription.class));
    }
    
    @Test
    void testCanStartInterviewWithActiveSubscription() {
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, "active"))
            .thenReturn(Optional.of(activeSubscription));
        
        boolean canStart = subscriptionService.canStartInterview(1L);
        
        assertTrue(canStart);
    }
    
    @Test
    void testCanStartInterviewWithTrial() {
        when(userSubscriptionRepository.findByUserIdAndStatus(2L, "active"))
            .thenReturn(Optional.empty());
        when(userSubscriptionRepository.findByUserIdAndStatus(2L, "trial"))
            .thenReturn(Optional.of(trialSubscription));
        
        boolean canStart = subscriptionService.canStartInterview(2L);
        
        assertTrue(canStart);
    }
    
    @Test
    void testCanStartInterviewNoSubscription() {
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, "active"))
            .thenReturn(Optional.empty());
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, "trial"))
            .thenReturn(Optional.empty());
        
        boolean canStart = subscriptionService.canStartInterview(1L);
        
        assertFalse(canStart);
    }
}

