package com.aiinterview.service;

import com.aiinterview.model.User;
import com.aiinterview.model.UserPoints;
import com.aiinterview.repository.UserRepository;
import com.aiinterview.repository.UserPointsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserProfileService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserPointsRepository userPointsRepository;
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    /**
     * Get user profile with points and subscription status
     */
    public Map<String, Object> getUserProfile(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        
        User user = userOpt.get();
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        
        // Get points
        Integer points = getUserPoints(userId);
        profile.put("points", points);
        
        // Get subscription status
        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(userId);
        boolean isInTrial = subscriptionService.isInTrialPeriod(userId);
        profile.put("hasActiveSubscription", hasActiveSubscription);
        profile.put("isInTrial", isInTrial);
        
        // Get subscription info
        Optional<com.aiinterview.model.UserSubscription> subscriptionOpt = subscriptionService.getUserSubscription(userId);
        if (subscriptionOpt.isPresent()) {
            profile.put("subscription", subscriptionOpt.get());
        }
        
        return profile;
    }
    
    /**
     * Get user points (unlimited for Pro users)
     */
    public Integer getUserPoints(Long userId) {
        // Check if user has Pro subscription (unlimited points)
        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(userId);
        if (hasActiveSubscription) {
            // Pro users have unlimited points, return a high number
            return Integer.MAX_VALUE;
        }
        
        // Regular users: get actual points
        Optional<UserPoints> pointsOpt = userPointsRepository.findByUserId(userId);
        if (pointsOpt.isPresent()) {
            return pointsOpt.get().getPoints();
        }
        
        // Initialize with default points if not exists
        UserPoints userPoints = new UserPoints();
        userPoints.setUserId(userId);
        userPoints.setPoints(1000); // Default points
        userPointsRepository.save(userPoints);
        return 1000;
    }
    
    /**
     * Get subscription status and points limit
     */
    public Map<String, Object> getSubscriptionStatus(Long userId) {
        Map<String, Object> status = new HashMap<>();
        
        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(userId);
        boolean isInTrial = subscriptionService.isInTrialPeriod(userId);
        
        status.put("hasActiveSubscription", hasActiveSubscription);
        status.put("isInTrial", isInTrial);
        status.put("isPro", hasActiveSubscription); // Pro = active subscription
        
        if (hasActiveSubscription) {
            status.put("pointsLimit", "unlimited");
            status.put("interviewsLimit", "unlimited");
        } else {
            status.put("pointsLimit", getUserPoints(userId));
            status.put("interviewsLimit", 5); // Default limit for free users
        }
        
        Optional<com.aiinterview.model.UserSubscription> subscriptionOpt = subscriptionService.getUserSubscription(userId);
        if (subscriptionOpt.isPresent()) {
            status.put("subscription", subscriptionOpt.get());
        }
        
        return status;
    }
    
    /**
     * Update user profile
     */
    public User updateUserProfile(Long userId, Map<String, Object> updates) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        
        User user = userOpt.get();
        // For now, only username can be updated
        if (updates.containsKey("username")) {
            String newUsername = (String) updates.get("username");
            if (!user.getUsername().equals(newUsername) && userRepository.existsByUsername(newUsername)) {
                throw new RuntimeException("Username already exists");
            }
            user.setUsername(newUsername);
        }
        
        return userRepository.save(user);
    }
}

