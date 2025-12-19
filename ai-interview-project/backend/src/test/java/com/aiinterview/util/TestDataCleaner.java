package com.aiinterview.util;

import com.aiinterview.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class TestDataCleaner {
    
    private static final String TEST_PREFIX = "TEST_";
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private InterviewRepository interviewRepository;
    
    @Autowired
    private InterviewMessageRepository interviewMessageRepository;
    
    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;
    
    /**
     * Clean all test data (data with TEST_ prefix)
     */
    @Transactional
    public void cleanAllTestData() {
        // Clean users
        List<com.aiinterview.model.User> testUsers = userRepository.findAll().stream()
            .filter(u -> u.getUsername().startsWith(TEST_PREFIX))
            .toList();
        userRepository.deleteAll(testUsers);
        
        // Clean interviews and messages
        List<com.aiinterview.model.Interview> testInterviews = interviewRepository.findAll().stream()
            .filter(i -> i.getId().startsWith(TEST_PREFIX))
            .toList();
        for (com.aiinterview.model.Interview interview : testInterviews) {
            interviewMessageRepository.deleteByInterviewId(interview.getId());
        }
        interviewRepository.deleteAll(testInterviews);
        
        // Clean subscriptions
        List<com.aiinterview.model.UserSubscription> testSubscriptions = userSubscriptionRepository.findAll().stream()
            .filter(s -> {
                // Check if user is test user
                return userRepository.findById(s.getUserId())
                    .map(u -> u.getUsername().startsWith(TEST_PREFIX))
                    .orElse(false);
            })
            .toList();
        userSubscriptionRepository.deleteAll(testSubscriptions);
        
        // Clean transactions
        List<com.aiinterview.model.PaymentTransaction> testTransactions = paymentTransactionRepository.findAll().stream()
            .filter(t -> t.getTransactionId().startsWith(TEST_PREFIX))
            .toList();
        paymentTransactionRepository.deleteAll(testTransactions);
    }
    
    /**
     * Clean test data for specific interview
     */
    @Transactional
    public void cleanTestInterviewData(String interviewId) {
        if (interviewId.startsWith(TEST_PREFIX)) {
            interviewMessageRepository.deleteByInterviewId(interviewId);
            interviewRepository.deleteById(interviewId);
        }
    }
}

