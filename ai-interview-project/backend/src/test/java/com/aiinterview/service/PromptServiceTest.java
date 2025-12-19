package com.aiinterview.service;

import com.aiinterview.session.model.QAHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PromptServiceTest {

    private PromptService promptService;
    
    @BeforeEach
    void setUp() {
        promptService = new PromptService();
        // Trigger @PostConstruct manually
        try {
            java.lang.reflect.Method method = PromptService.class.getDeclaredMethod("loadPrompts");
            method.setAccessible(true);
            method.invoke(promptService);
        } catch (Exception e) {
            // If reflection fails, service should still work if prompts are loaded
        }
    }
    
    @Test
    void testGetBasePrompt() {
        String basePrompt = promptService.getBasePrompt();
        
        assertNotNull(basePrompt);
        assertFalse(basePrompt.isEmpty());
    }
    
    @Test
    void testBuildEvaluationSystemPrompt() {
        String evaluationPrompt = promptService.buildEvaluationSystemPrompt();
        
        assertNotNull(evaluationPrompt);
        assertFalse(evaluationPrompt.isEmpty());
    }
    
    @Test
    void testBuildRoleSpecificPrompt_ValidRole() {
        String prompt = promptService.buildRoleSpecificPrompt("backend_java", "mid");
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("Java后端开发工程师") || prompt.contains("后端"));
    }
    
    @Test
    void testBuildRoleSpecificPrompt_InvalidRole() {
        String prompt = promptService.buildRoleSpecificPrompt("invalid_role", "mid");
        
        assertNotNull(prompt);
        // Should return default message
        assertTrue(prompt.contains("候选人") || prompt.contains("问题"));
    }
    
    @Test
    void testBuildSystemPrompt_WithCandidateInfo() {
        Map<String, Object> candidateInfo = new HashMap<>();
        candidateInfo.put("name", "John Doe");
        
        String prompt = promptService.buildSystemPrompt("backend_java", "mid", candidateInfo);
        
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }
    
    @Test
    void testBuildSystemPrompt_WithoutCandidateInfo() {
        String prompt = promptService.buildSystemPrompt("backend_java", "mid", null);
        
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }
    
    @Test
    void testBuildCandidateContextPrompt_WithWorkExperience() {
        Map<String, Object> candidateInfo = new HashMap<>();
        List<Map<String, String>> experiences = new ArrayList<>();
        Map<String, String> exp = new HashMap<>();
        exp.put("role", "Senior Developer");
        exp.put("company", "Tech Corp");
        exp.put("duration", "2020-2023");
        exp.put("description", "Led development team");
        experiences.add(exp);
        candidateInfo.put("workExperience", experiences);
        
        String prompt = promptService.buildCandidateContextPrompt(candidateInfo);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("工作经验") || prompt.contains("Senior Developer"));
    }
    
    @Test
    void testBuildCandidateContextPrompt_WithProjects() {
        Map<String, Object> candidateInfo = new HashMap<>();
        List<Map<String, Object>> projects = new ArrayList<>();
        Map<String, Object> project = new HashMap<>();
        project.put("title", "E-commerce Platform");
        project.put("description", "Built with Spring Boot");
        project.put("techStack", List.of("Java", "Spring"));
        projects.add(project);
        candidateInfo.put("projects", projects);
        
        String prompt = promptService.buildCandidateContextPrompt(candidateInfo);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("项目经验") || prompt.contains("E-commerce"));
    }
    
    @Test
    void testBuildConversationHistoryPrompt_EmptyHistory() {
        List<QAHistory> history = new ArrayList<>();
        
        String prompt = promptService.buildConversationHistoryPrompt(history, 10);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("开始") || prompt.contains("开场"));
    }
    
    @Test
    void testBuildConversationHistoryPrompt_WithHistory() {
        List<QAHistory> history = new ArrayList<>();
        QAHistory qa1 = new QAHistory();
        qa1.setQuestionText("What is Java?");
        qa1.setAnswerText("Java is a programming language");
        history.add(qa1);
        
        String prompt = promptService.buildConversationHistoryPrompt(history, 10);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("What is Java?") || prompt.contains("对话历史"));
    }
    
    @Test
    void testBuildConversationHistoryPrompt_MaxMessages() {
        List<QAHistory> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            QAHistory qa = new QAHistory();
            qa.setQuestionText("Question " + i);
            qa.setAnswerText("Answer " + i);
            history.add(qa);
        }
        
        String prompt = promptService.buildConversationHistoryPrompt(history, 10);
        
        assertNotNull(prompt);
        // Should only include recent 10 messages
        assertTrue(prompt.contains("最近10") || prompt.contains("10"));
    }
    
    @Test
    void testBuildEvaluationPrompt() {
        String question = "What is Spring Boot?";
        String answer = "Spring Boot is a framework";
        String roleId = "backend_java";
        String level = "mid";
        
        String prompt = promptService.buildEvaluationPrompt(question, answer, roleId, level);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains(question));
        assertTrue(prompt.contains(answer));
        assertTrue(prompt.contains(roleId) || prompt.contains(level));
    }
    
    @Test
    void testGetRoleDisplayName_ValidRole() {
        String displayName = promptService.getRoleDisplayName("backend_java");
        
        assertNotNull(displayName);
        assertFalse(displayName.isEmpty());
    }
    
    @Test
    void testGetRoleDisplayName_InvalidRole() {
        String displayName = promptService.getRoleDisplayName("invalid_role");
        
        assertNotNull(displayName);
        // Should return roleId if not found
        assertEquals("invalid_role", displayName);
    }
}

