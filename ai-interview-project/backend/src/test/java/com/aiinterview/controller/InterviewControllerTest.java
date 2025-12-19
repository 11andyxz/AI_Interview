package com.aiinterview.controller;

import com.aiinterview.dto.CreateInterviewRequest;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InterviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiService aiService;

    @Mock
    private InterviewRepository interviewRepository;

    @Mock
    private CandidateService candidateService;

    @Mock
    private InterviewSessionService interviewSessionService;

    @Mock
    private ReportService reportService;

    @Mock
    private PdfReportService pdfReportService;

    @Mock
    private LlmEvaluationService llmEvaluationService;

    @Mock
    private AudioService audioService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InterviewController interviewController;

    @BeforeEach
    void setUp() {
        // Create a mock interceptor that adds userId to request
        HandlerInterceptor authInterceptor = new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                   jakarta.servlet.http.HttpServletResponse response,
                                   Object handler) {
                request.setAttribute("userId", 1L);
                request.setAttribute("username", "testuser");
                return true;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(interviewController)
                .addInterceptors(authInterceptor)
                .build();
        objectMapper = new ObjectMapper();

        // Mock objectMapper behavior
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"Java\",\"Spring\"]");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    void getInterviews_Success() throws Exception {
        // Given
        List<Interview> interviews = List.of(createMockInterview());
        // Mock the service call - using a simplified approach
        when(interviewRepository.findAll()).thenReturn(interviews);

        // When & Then
        mockMvc.perform(get("/api/interviews")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk());
    }

    @Test
    void createInterview_Success() throws Exception {
        // Given
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setPositionType("Software Engineer");
        request.setCandidateId(1);
        request.setLanguage("English");
        request.setProgrammingLanguages(List.of("Java", "Spring"));

        var candidate = new com.aiinterview.model.Candidate();
        candidate.setId(1);
        candidate.setName("John Doe");

        Interview createdInterview = createMockInterview();

        when(candidateService.findById(1)).thenReturn(java.util.Optional.of(candidate));
        when(interviewRepository.save(any(Interview.class))).thenReturn(createdInterview);

        // When & Then
        mockMvc.perform(post("/api/interviews")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview.id").value("test-interview-id"));
    }

    @Test
    void getInterviewById_Success() throws Exception {
        // Given
        Interview interview = createMockInterview();
        when(interviewRepository.findById("test-id")).thenReturn(java.util.Optional.of(interview));

        // When & Then
        mockMvc.perform(get("/api/interviews/test-id")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk());
    }

    @Test
    void updateInterview_Success() throws Exception {
        // Given
        Interview interview = createMockInterview();
        when(interviewRepository.findById("test-id")).thenReturn(java.util.Optional.of(interview));
        when(interviewRepository.save(any(Interview.class))).thenReturn(interview);

        Map<String, Object> updates = Map.of("status", "Completed");

        // When & Then
        mockMvc.perform(put("/api/interviews/test-id")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteInterview_Success() throws Exception {
        // Given
        Interview interview = createMockInterview();
        when(interviewRepository.findById("test-id")).thenReturn(java.util.Optional.of(interview));

        // When & Then
        mockMvc.perform(delete("/api/interviews/test-id")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Interview deleted successfully"));
    }

    @Test
    void getInterviewReport_Success() throws Exception {
        // Given
        Map<String, Object> report = Map.of(
            "score", 85,
            "totalQuestions", 10,
            "conversationHistory", List.of()
        );
        when(reportService.generateReport("test-id")).thenReturn(report);

        // When & Then
        mockMvc.perform(get("/api/interviews/test-id/report")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85));
    }

    @Test
    void getInterviewReportJson_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/interviews/test-id/report/json")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk());
    }

    @Test
    void downloadInterviewReport_Success() throws Exception {
        // Given
        byte[] pdfData = "PDF content".getBytes();
        when(pdfReportService.generatePdfReport("test-id")).thenReturn(pdfData);

        // When & Then
        mockMvc.perform(get("/api/interviews/test-id/report/download")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"interview-report-test-id.pdf\""));
    }

    @Test
    void compareInterviews_Success() throws Exception {
        // Given
        List<String> interviewIds = List.of("id1", "id2");
        Map<String, Object> request = Map.of("interviewIds", interviewIds);

        // When & Then
        mockMvc.perform(post("/api/interviews/compare")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void uploadRecording_Success() throws Exception {
        // Given
        MockMultipartFile audioFile = new MockMultipartFile(
            "audio",
            "recording.webm",
            "audio/webm",
            "audio content".getBytes()
        );

        var recording = new com.aiinterview.model.InterviewRecording();
        recording.setId(1L);
        when(audioService.saveAudioFile(eq(audioFile), eq("test-id"), eq(1L))).thenReturn(recording);

        // When & Then
        mockMvc.perform(multipart("/api/interviews/test-id/recording")
                .file(audioFile)
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recording.id").value(1));
    }

    @Test
    void getRecordings_Success() throws Exception {
        // Given
        var recording = new com.aiinterview.model.InterviewRecording();
        recording.setId(1L);
        List<com.aiinterview.model.InterviewRecording> recordings = List.of(recording);

        when(audioService.getRecordingsForInterview("test-id")).thenReturn(recordings);

        // When & Then
        mockMvc.perform(get("/api/interviews/test-id/recordings")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void downloadRecording_Success() throws Exception {
        // Given
        var recording = new com.aiinterview.model.InterviewRecording();
        recording.setId(1L);
        recording.setUserId(1L);
        recording.setOriginalFilename("recording.webm");

        byte[] audioData = "audio content".getBytes();

        when(audioService.getRecordingById(1L)).thenReturn(java.util.Optional.of(recording));
        when(audioService.getAudioFile(1L)).thenReturn(audioData);

        // When & Then
        mockMvc.perform(get("/api/interviews/recording/1/download")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/webm"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"recording.webm\""));
    }

    private Interview createMockInterview() {
        Interview interview = new Interview();
        interview.setId("test-interview-id");
        interview.setTitle("Test Interview");
        interview.setStatus("In Progress");
        return interview;
    }
}