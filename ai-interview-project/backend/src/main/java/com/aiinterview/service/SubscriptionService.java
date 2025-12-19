package com.aiinterview.service;

import com.aiinterview.model.UserSubscription;
import com.aiinterview.repository.UserSubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SubscriptionService {
    
    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @Value("${subscription.trial.days:7}")
    private int trialDays;

    /**
     * Check if user has active subscription
     */
    public boolean hasActiveSubscription(Long userId) {
        Optional<UserSubscription> subscription = userSubscriptionRepository.findByUserIdAndStatus(userId, "active");
        if (subscription.isEmpty()) {
            return false;
        }
        
        UserSubscription sub = subscription.get();
        // Check if subscription is still valid
        if (sub.getEndDate() != null && sub.getEndDate().isBefore(LocalDateTime.now())) {
            // Subscription expired
            sub.setStatus("expired");
            userSubscriptionRepository.save(sub);
            return false;
        }
        
        return true;
    }

    /**
     * Check if user is in trial period
     */
    public boolean isInTrialPeriod(Long userId) {
        Optional<UserSubscription> subscription = userSubscriptionRepository.findByUserIdAndStatus(userId, "trial");
        if (subscription.isEmpty()) {
            return false;
        }
        
        UserSubscription sub = subscription.get();
        if (sub.getTrialEndDate() != null && sub.getTrialEndDate().isAfter(LocalDateTime.now())) {
            return true;
        }
        
        // Trial expired
        if (sub.getTrialEndDate() != null && sub.getTrialEndDate().isBefore(LocalDateTime.now())) {
            sub.setStatus("expired");
            userSubscriptionRepository.save(sub);
        }
        
        return false;
    }

    /**
     * Start trial period for new user
     */
    @Transactional
    public UserSubscription startTrial(Long userId, Integer planId) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStatus("trial");
        subscription.setStartDate(LocalDateTime.now());
        subscription.setTrialEndDate(LocalDateTime.now().plusDays(trialDays));
        return userSubscriptionRepository.save(subscription);
    }

    /**
     * Verify interview permission (unlimited for pro plan)
     */
    public boolean canStartInterview(Long userId) {
        // Check active subscription
        if (hasActiveSubscription(userId)) {
            return true;
        }
        
        // Check trial period
        if (isInTrialPeriod(userId)) {
            return true;
        }
        
        return false;
    }

    /**
     * Get user's subscription
     */
    public Optional<UserSubscription> getUserSubscription(Long userId) {
        // Try active first
        Optional<UserSubscription> active = userSubscriptionRepository.findByUserIdAndStatus(userId, "active");
        if (active.isPresent()) {
            return active;
        }
        
        // Try trial
        return userSubscriptionRepository.findByUserIdAndStatus(userId, "trial");
    }
}

