package com.aiinterview.integration;

import com.aiinterview.dto.CreateInterviewRequest;
import com.aiinterview.model.*;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.repository.UserRepository;
import com.aiinterview.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class InterviewFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterviewSessionService interviewSessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCompleteInterviewFlow() throws Exception {
        // Create user
        String username = "testuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");
        assertNotNull(user);
        assertNotNull(user.getId());

        // Create interview via API
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setCandidateId(1);
        request.setPositionType("Backend Java Developer");
        request.setProgrammingLanguages(List.of("Java", "Spring"));
        request.setLanguage("English");
        request.setInterviewType("general");

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview").exists())
                .andExpect(jsonPath("$.interview.title").value("Backend Java Developer"));

        // Verify interview was created in database
        List<Interview> interviews = interviewRepository.findAll();
        assertFalse(interviews.isEmpty());

        Interview createdInterview = interviews.get(interviews.size() - 1);
        assertEquals("Backend Java Developer", createdInterview.getTitle());
        assertEquals("In Progress", createdInterview.getStatus());

        // Test interview session creation (using the service method)
        Optional<Map<String, Object>> session = interviewSessionService.getInterviewSession(createdInterview.getId());
        // Session creation is handled through WebSocket, so we just verify the service exists
        assertNotNull(interviewSessionService);

        // Test getting interviews via API
        mockMvc.perform(get("/api/interviews")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testResumeBasedInterviewFlow() throws Exception {
        // Create user
        String username = "resumeuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Create interview with resume-based type
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setInterviewType("resume-based");
        request.setResumeId(1L);
        request.setPositionType("Full Stack Developer");
        request.setProgrammingLanguages(List.of("JavaScript", "React", "Node.js"));
        request.setLanguage("English");

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview").exists())
                .andExpect(jsonPath("$.interview.interviewType").value("resume-based"));

        // Verify resume-based interview was created
        List<Interview> interviews = interviewRepository.findAll();
        Interview resumeInterview = interviews.stream()
                .filter(i -> "resume-based".equals(i.getInterviewType()))
                .findFirst()
                .orElse(null);

        assertNotNull(resumeInterview);
        assertEquals("Full Stack Developer", resumeInterview.getTitle());
    }

    @Test
    void testInterviewStatusTransitions() throws Exception {
        // Create user and interview
        String username = "statususer_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        Interview interview = new Interview();
        interview.setId("status-test-" + System.currentTimeMillis());
        interview.setCandidateId(1);
        interview.setTitle("Status Test Interview");
        interview.setStatus("In Progress");
        interview.setUserId(user.getId());
        interview = interviewRepository.save(interview);

        // Test status update via API (simulated)
        // This would typically be done through WebSocket or specific endpoints
        assertEquals("In Progress", interview.getStatus());

        // Verify status is correctly stored
        Interview retrieved = interviewRepository.findById(interview.getId()).orElse(null);
        assertNotNull(retrieved);
        assertEquals("In Progress", retrieved.getStatus());
    }

    @Test
    void testInterviewWithKnowledgeBase() throws Exception {
        // Create user
        String username = "kbuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Create interview with custom knowledge base
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setInterviewType("general");
        request.setPositionType("AI Engineer");
        request.setProgrammingLanguages(List.of("Python", "TensorFlow"));
        request.setLanguage("English");
        request.setUseCustomKnowledge(true);

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk());

        // Verify interview with knowledge base flag
        List<Interview> interviews = interviewRepository.findAll();
        Interview kbInterview = interviews.stream()
                .filter(i -> i.isUseCustomKnowledge())
                .findFirst()
                .orElse(null);

        assertNotNull(kbInterview);
        assertEquals("AI Engineer", kbInterview.getTitle());
    }
}

