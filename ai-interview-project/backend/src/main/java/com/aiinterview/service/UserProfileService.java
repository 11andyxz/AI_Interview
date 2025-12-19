package com.aiinterview.service;

import com.aiinterview.model.Interview;
import com.aiinterview.model.User;
import com.aiinterview.model.UserPoints;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.repository.UserPointsRepository;
import com.aiinterview.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserProfileService {
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPointsRepository userPointsRepository;

    @Autowired
    private InterviewRepository interviewRepository;

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
     * Get user statistics for dashboard
     */
    public Map<String, Object> getUserStatistics(Long userId) {
        Map<String, Object> statistics = new HashMap<>();

        // Get all user interviews
        List<Interview> interviews = interviewRepository.findByCandidateId(userId);

        // Basic stats
        long totalInterviews = interviews.size();
        long completedInterviews = interviews.stream()
            .filter(i -> "Completed".equals(i.getStatus()))
            .count();
        long inProgressInterviews = interviews.stream()
            .filter(i -> "In Progress".equals(i.getStatus()))
            .count();

        statistics.put("totalInterviews", totalInterviews);
        statistics.put("completedInterviews", completedInterviews);
        statistics.put("inProgressInterviews", inProgressInterviews);

        // Calculate average duration (in minutes)
        double avgDuration = interviews.stream()
            .filter(i -> i.getDurationSeconds() != null)
            .mapToInt(i -> i.getDurationSeconds())
            .average()
            .orElse(0.0) / 60.0; // Convert to minutes
        statistics.put("averageDuration", Math.round(avgDuration * 100.0) / 100.0);

        // Interview frequency by month (last 6 months)
        Map<String, Integer> interviewFrequency = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = now.minusMonths(i);
            String monthKey = month.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            long count = interviews.stream()
                .filter(interview -> {
                    LocalDate interviewDate = interview.getDate() != null ?
                        interview.getDate() : interview.getCreatedAt().toLocalDate();
                    return interviewDate.getYear() == month.getYear() &&
                           interviewDate.getMonth() == month.getMonth();
                })
                .count();
            interviewFrequency.put(monthKey, (int) count);
        }
        statistics.put("interviewFrequency", interviewFrequency);

        // Top skills practiced (based on tech stack)
        Map<String, Integer> skillFrequency = new HashMap<>();
        interviews.forEach(interview -> {
            if (interview.getTechStack() != null) {
                String[] skills = interview.getTechStack().split(",");
                for (String skill : skills) {
                    skill = skill.trim();
                    skillFrequency.put(skill, skillFrequency.getOrDefault(skill, 0) + 1);
                }
            }
        });

        // Get top 5 skills
        List<Map.Entry<String, Integer>> topSkills = skillFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        Map<String, Integer> topSkillsMap = new LinkedHashMap<>();
        topSkills.forEach(entry -> topSkillsMap.put(entry.getKey(), entry.getValue()));
        statistics.put("topSkills", topSkillsMap);

        // Recent activity
        List<Interview> recentInterviews = interviews.stream()
            .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
            .limit(3)
            .collect(Collectors.toList());

        List<Map<String, Object>> recentActivity = recentInterviews.stream()
            .map(interview -> {
                Map<String, Object> activity = new HashMap<>();
                activity.put("id", interview.getId());
                activity.put("title", interview.getTitle());
                activity.put("status", interview.getStatus());
                activity.put("date", interview.getUpdatedAt());
                return activity;
            })
            .collect(Collectors.toList());

        statistics.put("recentActivity", recentActivity);

        // Best performing interview (placeholder - would need actual evaluation data)
        statistics.put("bestScore", totalInterviews > 0 ? 85 : 0); // Placeholder

        return statistics;
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

