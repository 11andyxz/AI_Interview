package com.aiinterview.service;

import com.aiinterview.model.UserSubscription;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Test implementation of SubscriptionService for testing purposes
 * Used when Mockito cannot mock the original service
 */
@Component
public class TestSubscriptionService extends SubscriptionService {
    
    private boolean hasActiveSubscriptionResult = false;
    private boolean isInTrialPeriodResult = false;
    private Optional<UserSubscription> userSubscriptionResult = Optional.empty();
    
    public void setHasActiveSubscriptionResult(boolean result) {
        this.hasActiveSubscriptionResult = result;
    }
    
    public void setIsInTrialPeriodResult(boolean result) {
        this.isInTrialPeriodResult = result;
    }
    
    public void setUserSubscriptionResult(Optional<UserSubscription> result) {
        this.userSubscriptionResult = result;
    }
    
    @Override
    public boolean hasActiveSubscription(Long userId) {
        return hasActiveSubscriptionResult;
    }
    
    @Override
    public boolean isInTrialPeriod(Long userId) {
        return isInTrialPeriodResult;
    }
    
    @Override
    public Optional<UserSubscription> getUserSubscription(Long userId) {
        return userSubscriptionResult;
    }
}

