package com.aiinterview.service;

import com.aiinterview.model.MockInterview;
import com.aiinterview.model.MockInterviewMessage;
import com.aiinterview.repository.MockInterviewRepository;
import com.aiinterview.repository.MockInterviewMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MockInterviewService {
    
    @Autowired
    private MockInterviewRepository mockInterviewRepository;
    
    @Autowired
    private MockInterviewMessageRepository messageRepository;
    
    /**
     * Get all mock interviews for a user
     */
    public List<MockInterview> getUserMockInterviews(Long userId, String status) {
        if (status != null && !status.isEmpty()) {
            return mockInterviewRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        }
        return mockInterviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get mock interview by ID
     */
    public Optional<MockInterview> getMockInterviewById(String id, Long userId) {
        return mockInterviewRepository.findByIdAndUserId(id, userId);
    }
    
    /**
     * Create a new mock interview
     */
    public MockInterview createMockInterview(Long userId, String title, String positionType, 
                                            String programmingLanguages, String language) {
        MockInterview mockInterview = new MockInterview();
        mockInterview.setId(UUID.randomUUID().toString());
        mockInterview.setUserId(userId);
        mockInterview.setTitle(title);
        mockInterview.setPositionType(positionType);
        mockInterview.setProgrammingLanguages(programmingLanguages);
        mockInterview.setLanguage(language);
        mockInterview.setStatus("practice");
        mockInterview.setCurrentQuestionIndex(0);
        return mockInterviewRepository.save(mockInterview);
    }
    
    /**
     * Get messages for a mock interview
     */
    public List<MockInterviewMessage> getMockInterviewMessages(String mockInterviewId) {
        return messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(mockInterviewId);
    }
    
    /**
     * Add message to mock interview
     */
    public MockInterviewMessage addMessage(String mockInterviewId, String questionText, String answerText) {
        MockInterviewMessage message = new MockInterviewMessage();
        message.setMockInterviewId(mockInterviewId);
        message.setQuestionText(questionText);
        message.setAnswerText(answerText);
        return messageRepository.save(message);
    }
    
    /**
     * Retry mock interview (reset to practice mode)
     */
    public MockInterview retryMockInterview(String id, Long userId) {
        Optional<MockInterview> mockOpt = getMockInterviewById(id, userId);
        if (mockOpt.isEmpty()) {
            throw new RuntimeException("Mock interview not found");
        }
        
        MockInterview mockInterview = mockOpt.get();
        mockInterview.setStatus("practice");
        mockInterview.setCurrentQuestionIndex(0);
        mockInterview.setScore(null);
        mockInterview.setFeedback(null);
        mockInterview.setStartedAt(LocalDateTime.now());
        mockInterview.setEndedAt(null);
        
        // Delete existing messages
        List<MockInterviewMessage> messages = getMockInterviewMessages(id);
        messageRepository.deleteAll(messages);
        
        return mockInterviewRepository.save(mockInterview);
    }
    
    /**
     * Get hints for a question (practice mode)
     */
    public String getHint(String mockInterviewId, Long questionIndex) {
        // Get the mock interview to understand the context
        Optional<MockInterview> mockOpt = mockInterviewRepository.findById(mockInterviewId);
        if (mockOpt.isEmpty()) {
            return "Unable to provide hint: interview not found.";
        }

        MockInterview mockInterview = mockOpt.get();

        // Get current question from messages
        List<MockInterviewMessage> messages = getMockInterviewMessages(mockInterviewId);
        if (messages.isEmpty() || questionIndex >= messages.size()) {
            return "Think about fundamental concepts in " + mockInterview.getPositionType() + " development.";
        }

        MockInterviewMessage currentQuestion = messages.get(questionIndex.intValue());
        String questionText = currentQuestion.getQuestionText();

        // Generate contextual hints based on question content and position type
        return generateContextualHint(questionText, mockInterview.getPositionType(), mockInterview.getProgrammingLanguages());
    }

    /**
     * Generate contextual hint based on question and context
     */
    private String generateContextualHint(String questionText, String positionType, String programmingLanguages) {
        if (questionText == null || questionText.isEmpty()) {
            return "Consider the core principles and best practices for " + positionType + " development.";
        }

        String lowerQuestion = questionText.toLowerCase();

        // Common interview topics and their hints
        if (lowerQuestion.contains("algorithm") || lowerQuestion.contains("complexity")) {
            return "Consider time and space complexity. Think about Big O notation and optimal solutions.";
        }

        if (lowerQuestion.contains("database") || lowerQuestion.contains("sql")) {
            return "Think about normalization, indexing, and query optimization. Consider ACID properties.";
        }

        if (lowerQuestion.contains("security") || lowerQuestion.contains("authentication")) {
            return "Consider common vulnerabilities, secure coding practices, and industry standards.";
        }

        if (lowerQuestion.contains("design") || lowerQuestion.contains("architecture")) {
            return "Think about scalability, maintainability, and design patterns. Consider SOLID principles.";
        }

        if (lowerQuestion.contains("testing") || lowerQuestion.contains("test")) {
            return "Consider unit tests, integration tests, and edge cases. Think about test coverage.";
        }

        if (lowerQuestion.contains("performance") || lowerQuestion.contains("optimization")) {
            return "Consider bottlenecks, caching, and profiling. Think about trade-offs between speed and resources.";
        }

        if (lowerQuestion.contains("concurrency") || lowerQuestion.contains("thread")) {
            return "Consider race conditions, synchronization, and thread safety. Think about locks and atomic operations.";
        }

        // Default contextual hint based on position type
        if ("backend".equalsIgnoreCase(positionType) || positionType.toLowerCase().contains("backend")) {
            return "Focus on server-side logic, APIs, databases, and scalability. Consider error handling and logging.";
        }

        if ("frontend".equalsIgnoreCase(positionType) || positionType.toLowerCase().contains("frontend")) {
            return "Consider user experience, browser compatibility, and responsive design. Think about state management.";
        }

        if ("fullstack".equalsIgnoreCase(positionType) || positionType.toLowerCase().contains("full")) {
            return "Think about both client and server perspectives. Consider end-to-end user experience.";
        }

        // Generic hint
        return "Break down the problem into smaller parts. Consider edge cases, best practices, and real-world applications.";
    }
    
    /**
     * Complete mock interview
     */
    public MockInterview completeMockInterview(String id, Long userId, java.math.BigDecimal score, String feedback) {
        Optional<MockInterview> mockOpt = getMockInterviewById(id, userId);
        if (mockOpt.isEmpty()) {
            throw new RuntimeException("Mock interview not found");
        }
        
        MockInterview mockInterview = mockOpt.get();
        mockInterview.setStatus("completed");
        mockInterview.setScore(score);
        mockInterview.setFeedback(feedback);
        mockInterview.setEndedAt(LocalDateTime.now());
        return mockInterviewRepository.save(mockInterview);
    }
}

