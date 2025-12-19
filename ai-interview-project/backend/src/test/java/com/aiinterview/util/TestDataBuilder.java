package com.aiinterview.util;

import com.aiinterview.model.*;
import com.aiinterview.dto.QAHistory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TestDataBuilder {
    
    private static final String TEST_PREFIX = "TEST_";
    
    public static User createTestUser(String username) {
        User user = new User();
        user.setUsername(TEST_PREFIX + username);
        user.setPassword("testPassword123");
        return user;
    }
    
    public static Candidate createTestCandidate(String name) {
        Candidate candidate = new Candidate();
        candidate.setName(TEST_PREFIX + name);
        candidate.setEmail(TEST_PREFIX + name.toLowerCase().replace(" ", ".") + "@test.com");
        candidate.setResumeText("Test resume text for " + name);
        candidate.setExperienceYears(5);
        candidate.setEducation("Bachelor's Degree");
        candidate.setStatus("pending");
        return candidate;
    }
    
    public static Interview createTestInterview(String candidateId) {
        Interview interview = new Interview();
        interview.setId(TEST_PREFIX + java.util.UUID.randomUUID().toString());
        interview.setCandidateId(Integer.parseInt(candidateId));
        interview.setTitle(TEST_PREFIX + "Backend Java Developer");
        interview.setLanguage("English");
        interview.setTechStack("Java, Spring Boot");
        interview.setDate(java.time.LocalDate.now());
        interview.setStatus("In Progress");
        return interview;
    }
    
    public static InterviewMessage createTestInterviewMessage(String interviewId, String userMessage, String aiMessage) {
        InterviewMessage message = new InterviewMessage();
        message.setInterviewId(interviewId);
        message.setUserMessage(userMessage);
        message.setAiMessage(aiMessage);
        message.setMessageType("chat");
        return message;
    }
    
    public static QAHistory createTestQAHistory(String question, String answer) {
        QAHistory qa = new QAHistory();
        qa.setQuestionText(question);
        qa.setAnswerText(answer);
        return qa;
    }
    
    public static SubscriptionPlan createTestSubscriptionPlan(String name, BigDecimal price) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(TEST_PREFIX + name);
        plan.setDescription("Test plan description");
        plan.setPrice(price);
        plan.setCurrency("USD");
        plan.setBillingCycle("monthly");
        plan.setIsActive(true);
        return plan;
    }
    
    public static UserSubscription createTestUserSubscription(Long userId, Integer planId) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStatus("trial");
        subscription.setStartDate(LocalDateTime.now());
        subscription.setTrialEndDate(LocalDateTime.now().plusDays(7));
        subscription.setPaymentMethod("stripe");
        return subscription;
    }
    
    public static PaymentTransaction createTestPaymentTransaction(Long userId, BigDecimal amount) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setCurrency("USD");
        transaction.setPaymentMethod("stripe");
        transaction.setTransactionId(TEST_PREFIX + java.util.UUID.randomUUID().toString());
        transaction.setStatus("success");
        return transaction;
    }
    
    public static UserNote createTestUserNote(Long userId, String title, String content) {
        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setTitle(TEST_PREFIX + title);
        note.setContent(content);
        note.setType("general");
        note.setCreatedAt(LocalDateTime.now());
        return note;
    }
    
    public static UserResume createTestUserResume(Long userId, String fileName) {
        UserResume resume = new UserResume();
        resume.setUserId(userId);
        resume.setOriginalFileName(TEST_PREFIX + fileName);
        resume.setResumeText("Test resume content for " + fileName);
        resume.setCreatedAt(LocalDateTime.now());
        return resume;
    }
    
    public static KnowledgeBase createTestKnowledgeBase(Long userId, String name) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(TEST_PREFIX + name);
        kb.setDescription("Test knowledge base description");
        kb.setContent("{\"skills\": [\"Java\", \"Spring\"]}");
        kb.setType("user");
        kb.setIsActive(true);
        kb.setCreatedAt(LocalDateTime.now());
        return kb;
    }
    
    public static KnowledgeBase createTestSystemKnowledgeBase(String name) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(null);
        kb.setName(TEST_PREFIX + name);
        kb.setDescription("System knowledge base");
        kb.setContent("{\"skills\": [\"Python\", \"Django\"]}");
        kb.setType("system");
        kb.setIsActive(true);
        kb.setCreatedAt(LocalDateTime.now());
        return kb;
    }
    
    public static MockInterview createTestMockInterview(Long userId, String title) {
        MockInterview mockInterview = new MockInterview();
        mockInterview.setUserId(userId);
        mockInterview.setTitle(TEST_PREFIX + title);
        mockInterview.setPositionType("Backend Developer");
        mockInterview.setProgrammingLanguages("[\\\"Java\\\", \\\"Spring\\\"]");
        mockInterview.setLanguage("English");
        mockInterview.setStatus("practice");
        mockInterview.setCreatedAt(LocalDateTime.now());
        return mockInterview;
    }
    
    public static ApiKeyConfig createTestApiKeyConfig(String serviceName, String apiKey) {
        ApiKeyConfig config = new ApiKeyConfig();
        config.setServiceName(serviceName);
        config.setApiKey(TEST_PREFIX + apiKey);
        config.setIsActive(true);
        config.setCreatedAt(LocalDateTime.now());
        return config;
    }
    
    public static InterviewTemplate createTestInterviewTemplate(Long userId, String name) {
        InterviewTemplate template = new InterviewTemplate();
        template.setUserId(userId);
        template.setName(TEST_PREFIX + name);
        template.setDescription("Test template description");
        template.setTechStack("Java");
        template.setLevel("mid");
        template.setIsPublic(false);
        template.setUsageCount(0);
        template.setCreatedAt(LocalDateTime.now());
        return template;
    }
    
    public static CustomQuestionSet createTestCustomQuestionSet(Long userId, String name) {
        CustomQuestionSet questionSet = new CustomQuestionSet();
        questionSet.setUserId(userId);
        questionSet.setName(TEST_PREFIX + name);
        questionSet.setDescription("Test question set");
        questionSet.setTechStack("Java");
        questionSet.setLevel("mid");
        questionSet.setIsPublic(false);
        questionSet.setUsageCount(0);
        questionSet.setQuestions(java.util.Arrays.asList("Question 1", "Question 2"));
        questionSet.setCreatedAt(LocalDateTime.now());
        return questionSet;
    }
    
    public static EvaluationResult createTestEvaluationResult() {
        EvaluationResult result = new EvaluationResult();
        result.setScore(85.0);
        result.setRubricLevel("good");
        result.setTechnicalAccuracy(8);
        result.setDepth(8);
        result.setExperience(9);
        result.setCommunication(8);
        result.setStrengths(java.util.Arrays.asList("Good knowledge", "Clear explanation"));
        result.setImprovements(java.util.Arrays.asList("Could be more detailed"));
        result.setFollowUpQuestions(java.util.Arrays.asList("Can you elaborate?"));
        return result;
    }
}

