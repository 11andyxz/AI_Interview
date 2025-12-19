package com.aiinterview.integration;

import com.aiinterview.model.Interview;
import com.aiinterview.model.User;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.service.InterviewSessionService;
import com.aiinterview.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

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
    private InterviewSessionService interviewSessionService;
    
    @Test
    void testCompleteInterviewFlow() throws Exception {
        // Create user
        String username = "testuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");
        
        // Create interview
        Interview interview = new Interview();
        interview.setId("interview-" + System.currentTimeMillis());
        interview.setCandidateId(1);
        interview.setTitle("Backend Java Developer");
        interview.setStatus("In Progress");
        interview = interviewRepository.save(interview);
        
        assertNotNull(interview);
        assertEquals("In Progress", interview.getStatus());
        
        // Verify interview exists
        assertTrue(interviewRepository.findById(interview.getId()).isPresent());
    }
}

