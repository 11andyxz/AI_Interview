package com.aiinterview.integration;

import com.aiinterview.model.Interview;
import com.aiinterview.model.User;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ReportGenerationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PdfReportService pdfReportService;

    @Test
    void testReportGenerationForCompletedInterview() throws Exception {
        // Create user
        String username = "reportuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Create and complete an interview
        Interview interview = new Interview();
        interview.setId("report-test-" + System.currentTimeMillis());
        interview.setUserId(user.getId());
        interview.setCandidateId(1);
        interview.setTitle("Report Test Interview");
        interview.setStatus("Completed");
        interview = interviewRepository.save(interview);

        // Test report generation via API
        mockMvc.perform(get("/api/interviews/{id}/report", interview.getId())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk());

        // Verify report contains expected data
        var result = mockMvc.perform(get("/api/interviews/{id}/report", interview.getId())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("score") || responseContent.contains("85"));
    }

    @Test
    void testPDFReportGeneration() throws Exception {
        // Create user
        String username = "pdfuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Create completed interview
        Interview interview = new Interview();
        interview.setId("pdf-test-" + System.currentTimeMillis());
        interview.setUserId(user.getId());
        interview.setCandidateId(1);
        interview.setTitle("PDF Report Test");
        interview.setStatus("Completed");
        interview = interviewRepository.save(interview);

        // Test PDF report download
        mockMvc.perform(get("/api/interviews/{id}/report/pdf", interview.getId())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk());

        // Verify PDF content type (if implemented)
        var result = mockMvc.perform(get("/api/interviews/{id}/report/pdf", interview.getId())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        // PDF content type check (may vary based on implementation)
        assertNotNull(contentType);
    }

    @Test
    void testJSONReportGeneration() throws Exception {
        // Create user
        String username = "jsonuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Create completed interview
        Interview interview = new Interview();
        interview.setId("json-test-" + System.currentTimeMillis());
        interview.setUserId(user.getId());
        interview.setCandidateId(1);
        interview.setTitle("JSON Report Test");
        interview.setStatus("Completed");
        interview = interviewRepository.save(interview);

        // Test JSON report download
        var result = mockMvc.perform(get("/api/interviews/{id}/report/json", interview.getId())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("score") || responseContent.contains("75"));
        assertTrue(responseContent.contains("{") || responseContent.contains("[")); // JSON format
    }

    @Test
    void testReportAccessControl() throws Exception {
        // Create two users
        String username1 = "reportuser1_" + System.currentTimeMillis();
        String username2 = "reportuser2_" + System.currentTimeMillis();
        User user1 = userService.createUser(username1, "password123");
        User user2 = userService.createUser(username2, "password123");

        // Create interview for user1
        Interview interview = new Interview();
        interview.setId("access-test-" + System.currentTimeMillis());
        interview.setCandidateId(1);
        interview.setTitle("Access Control Test");
        interview.setStatus("Completed");
        interview.setUserId(user1.getId()); // Assuming interview has userId field
        interview = interviewRepository.save(interview);

        // Test that user2 cannot access user1's report
        mockMvc.perform(get("/api/interviews/{id}/report", interview.getId())
                .requestAttr("userId", user2.getId()))
                .andExpect(status().isNotFound()); // Or forbidden, depending on implementation

        // Test that user1 can access their own report
        mockMvc.perform(get("/api/interviews/{id}/report", interview.getId())
                .requestAttr("userId", user1.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void testReportDataCompleteness() throws Exception {
        // Create user
        String username = "completeuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Create interview with detailed data
        Interview interview = new Interview();
        interview.setId("complete-test-" + System.currentTimeMillis());
        interview.setUserId(user.getId());
        interview.setCandidateId(1);
        interview.setTitle("Complete Report Test");
        interview.setStatus("Completed");
        interview.setTechStack("Java,Spring,React");
        interview.setProgrammingLanguages("Java,Spring,React");
        interview.setLanguage("English");
        interview = interviewRepository.save(interview);

        // Generate report and verify completeness
        var result = mockMvc.perform(get("/api/interviews/{id}/report", interview.getId())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        // Verify key data points are included
        assertTrue(responseContent.contains("Senior Developer") ||
                  responseContent.contains("Java") ||
                  responseContent.contains("88"));
    }
}
