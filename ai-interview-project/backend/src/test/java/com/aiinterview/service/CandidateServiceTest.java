package com.aiinterview.service;

import com.aiinterview.model.Candidate;
import com.aiinterview.repository.CandidateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {
    
    @Mock
    private CandidateRepository candidateRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private CandidateService candidateService;
    
    private Candidate testCandidate;
    
    @BeforeEach
    void setUp() {
        testCandidate = new Candidate();
        testCandidate.setId(1);
        testCandidate.setName("John Doe");
        testCandidate.setEmail("john@example.com");
        testCandidate.setResumeText("Java developer with 5 years experience in Spring Boot");
        testCandidate.setSkills("[\"Java\", \"Spring Boot\", \"MySQL\"]");
    }
    
    @Test
    void testFindAll() {
        List<Candidate> candidates = Arrays.asList(testCandidate);
        when(candidateRepository.findAll()).thenReturn(candidates);
        
        List<Candidate> result = candidateService.findAll();
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testCandidate, result.get(0));
        verify(candidateRepository).findAll();
    }
    
    @Test
    void testFindById_Success() {
        when(candidateRepository.findById(1)).thenReturn(Optional.of(testCandidate));
        
        Optional<Candidate> result = candidateService.findById(1);
        
        assertTrue(result.isPresent());
        assertEquals(testCandidate, result.get());
        verify(candidateRepository).findById(1);
    }
    
    @Test
    void testFindById_NotFound() {
        when(candidateRepository.findById(999)).thenReturn(Optional.empty());
        
        Optional<Candidate> result = candidateService.findById(999);
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void testBuildKnowledgeBase_WithValidData() throws Exception {
        List<String> programmingLanguages = Arrays.asList("Java", "Spring Boot");
        String positionType = "Backend Developer";
        String language = "English";
        
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(Arrays.asList("Java", "Spring Boot", "MySQL"));
        
        Map<String, Object> result = candidateService.buildKnowledgeBase(
            testCandidate, positionType, programmingLanguages, language
        );
        
        assertNotNull(result);
        assertEquals(1, result.get("candidateId"));
        assertEquals("John Doe", result.get("candidateName"));
        assertEquals(positionType, result.get("positionType"));
        assertEquals(language, result.get("language"));
        assertTrue((Boolean) result.get("personalized"));
        assertNotNull(result.get("skills"));
        assertNotNull(result.get("summary"));
        assertNotNull(result.get("questions"));
    }
    
    @Test
    void testBuildKnowledgeBase_WithNullSkills() throws Exception {
        testCandidate.setSkills(null);
        List<String> programmingLanguages = Arrays.asList("Java");
        
        Map<String, Object> result = candidateService.buildKnowledgeBase(
            testCandidate, "Backend Developer", programmingLanguages, "English"
        );
        
        assertNotNull(result);
        assertNotNull(result.get("skills"));
    }
    
    @Test
    void testBuildKnowledgeBase_WithEmptySkills() throws Exception {
        testCandidate.setSkills("");
        
        Map<String, Object> result = candidateService.buildKnowledgeBase(
            testCandidate, "Backend Developer", Arrays.asList("Java"), "English"
        );
        
        assertNotNull(result);
        assertNotNull(result.get("skills"));
    }
    
    @Test
    void testBuildKnowledgeBase_WithInvalidJsonSkills() throws Exception {
        testCandidate.setSkills("invalid json");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenThrow(new Exception("Invalid JSON"));
        
        Map<String, Object> result = candidateService.buildKnowledgeBase(
            testCandidate, "Backend Developer", Arrays.asList("Java"), "English"
        );
        
        assertNotNull(result);
        // Should fallback to single string
        assertNotNull(result.get("skills"));
    }
    
    @Test
    void testBuildKnowledgeBase_WithNullResumeText() {
        testCandidate.setResumeText(null);
        
        Map<String, Object> result = candidateService.buildKnowledgeBase(
            testCandidate, "Backend Developer", Arrays.asList("Java"), "English"
        );
        
        assertNotNull(result);
        assertTrue(result.get("summary").toString().contains("No resume summary"));
    }
    
    @Test
    void testBuildKnowledgeBase_WithLongResumeText() {
        String longResume = "A".repeat(500);
        testCandidate.setResumeText(longResume);
        
        Map<String, Object> result = candidateService.buildKnowledgeBase(
            testCandidate, "Backend Developer", Arrays.asList("Java"), "English"
        );
        
        assertNotNull(result);
        String summary = (String) result.get("summary");
        assertTrue(summary.length() <= 303); // 300 + "..."
    }
}

