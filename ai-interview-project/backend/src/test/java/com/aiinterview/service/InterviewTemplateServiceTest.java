package com.aiinterview.service;

import com.aiinterview.model.InterviewTemplate;
import com.aiinterview.repository.InterviewTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewTemplateServiceTest {

    @Mock
    private InterviewTemplateRepository templateRepository;

    @InjectMocks
    private InterviewTemplateService templateService;

    private InterviewTemplate mockTemplate1;
    private InterviewTemplate mockTemplate2;

    @BeforeEach
    void setUp() {
        mockTemplate1 = createMockTemplate(1L, "Template 1", "Java", "mid", true);
        mockTemplate2 = createMockTemplate(1L, "Template 2", "Python", "senior", false);
    }

    @Test
    void getUserTemplates_IncludesPublicAndOwned() {
        // Given
        List<InterviewTemplate> userTemplates = List.of(mockTemplate1);
        List<InterviewTemplate> publicTemplates = List.of(mockTemplate2);

        when(templateRepository.findByUserId(1L)).thenReturn(userTemplates);
        when(templateRepository.findByIsPublic(true)).thenReturn(publicTemplates);

        // When
        List<InterviewTemplate> result = templateService.getUserTemplates(1L);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void getUserOwnedTemplates_OnlyOwned() {
        // Given
        List<InterviewTemplate> userTemplates = List.of(mockTemplate1);
        when(templateRepository.findByUserId(1L)).thenReturn(userTemplates);

        // When
        List<InterviewTemplate> result = templateService.getUserOwnedTemplates(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Template 1", result.get(0).getName());
    }

    @Test
    void getTemplateById_Success() {
        // Given
        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate1));

        // When
        Optional<InterviewTemplate> result = templateService.getTemplateById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Template 1", result.get().getName());
    }

    @Test
    void createTemplate_Success() {
        // Given
        InterviewTemplate newTemplate = createMockTemplate(null, "New Template", "React", "mid", false);
        when(templateRepository.save(any(InterviewTemplate.class))).thenReturn(newTemplate);

        // When
        InterviewTemplate result = templateService.createTemplate(newTemplate);

        // Then
        assertNotNull(result);
        assertEquals("New Template", result.getName());
        verify(templateRepository).save(newTemplate);
    }

    @Test
    void updateTemplate_Success() {
        // Given
        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate1));
        when(templateRepository.save(any(InterviewTemplate.class))).thenReturn(mockTemplate1);

        Map<String, Object> updates = Map.of(
            "name", "Updated Template",
            "description", "Updated description",
            "isPublic", false
        );

        // When
        InterviewTemplate result = templateService.updateTemplate(1L, updates);

        // Then
        assertNotNull(result);
        verify(templateRepository).save(argThat(template ->
            "Updated Template".equals(template.getName()) &&
            "Updated description".equals(template.getDescription()) &&
            !template.getIsPublic()
        ));
    }

    @Test
    void updateTemplate_NotFound() {
        // Given
        when(templateRepository.findById(1L)).thenReturn(Optional.empty());

        Map<String, Object> updates = Map.of("name", "Updated");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            templateService.updateTemplate(1L, updates)
        );
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void deleteTemplate_Success() {
        // Given
        mockTemplate1.setUserId(1L);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate1));

        // When
        templateService.deleteTemplate(1L, 1L);

        // Then
        verify(templateRepository).delete(mockTemplate1);
    }

    @Test
    void deleteTemplate_WrongUser() {
        // Given
        mockTemplate1.setUserId(2L); // Different user
        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate1));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            templateService.deleteTemplate(1L, 1L)
        );
        assertTrue(exception.getMessage().contains("not found or access denied"));
    }

    @Test
    void incrementUsageCount_Success() {
        // Given
        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate1));
        when(templateRepository.save(any(InterviewTemplate.class))).thenReturn(mockTemplate1);

        // When
        templateService.incrementUsageCount(1L);

        // Then
        verify(templateRepository).save(argThat(template ->
            template.getUsageCount() == 1 // Assuming initial count was 0
        ));
    }

    @Test
    void getPopularTemplates_ReturnsLimitedResults() {
        // Given
        List<InterviewTemplate> templates = List.of(mockTemplate1, mockTemplate2);
        when(templateRepository.findByIsPublic(true)).thenReturn(templates);

        // When
        List<InterviewTemplate> result = templateService.getPopularTemplates(1);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void searchTemplates_ByTechStackAndLevel() {
        // Given
        List<InterviewTemplate> templates = List.of(mockTemplate1);
        when(templateRepository.findByTechStackAndLevel("Java", "mid")).thenReturn(templates);

        // When
        List<InterviewTemplate> result = templateService.searchTemplates("Java", "mid");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Java", result.get(0).getTechStack());
    }

    @Test
    void addQuestionsToSet_Success() {
        // Given
        List<String> existingQuestions = List.of("Question 1");
        mockTemplate1.setQuestions(existingQuestions);

        List<String> newQuestions = List.of("Question 2", "Question 3");

        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate1));
        when(templateRepository.save(any(InterviewTemplate.class))).thenReturn(mockTemplate1);

        // When
        InterviewTemplate result = templateService.addQuestionsToSet(1L, newQuestions);

        // Then
        assertNotNull(result);
        verify(templateRepository).save(argThat(template ->
            template.getQuestions().contains("Question 1") &&
            template.getQuestions().contains("Question 2") &&
            template.getQuestions().contains("Question 3")
        ));
    }

    @Test
    void removeQuestionsFromSet_Success() {
        // Given
        List<String> existingQuestions = List.of("Question 1", "Question 2", "Question 3");
        mockTemplate1.setQuestions(existingQuestions);

        List<String> questionsToRemove = List.of("Question 2");

        when(templateRepository.findById(1L)).thenReturn(Optional.of(mockTemplate1));
        when(templateRepository.save(any(InterviewTemplate.class))).thenReturn(mockTemplate1);

        // When
        InterviewTemplate result = templateService.removeQuestionsFromSet(1L, questionsToRemove);

        // Then
        assertNotNull(result);
        verify(templateRepository).save(argThat(template ->
            template.getQuestions().contains("Question 1") &&
            !template.getQuestions().contains("Question 2") &&
            template.getQuestions().contains("Question 3")
        ));
    }

    private InterviewTemplate createMockTemplate(Long id, String name, String techStack, String level, boolean isPublic) {
        InterviewTemplate template = new InterviewTemplate();
        template.setId(id);
        template.setName(name);
        template.setTechStack(techStack);
        template.setLevel(level);
        template.setIsPublic(isPublic);
        template.setUserId(1L);
        template.setRoleTitle(name + " Role");
        template.setQuestions(List.of("Sample question"));
        template.setUsageCount(0);
        return template;
    }
}
