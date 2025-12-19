package com.aiinterview.service;

import com.aiinterview.model.CustomQuestionSet;
import com.aiinterview.repository.CustomQuestionSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomQuestionSetServiceTest {
    
    @Mock
    private CustomQuestionSetRepository questionSetRepository;
    
    @InjectMocks
    private CustomQuestionSetService questionSetService;
    
    private CustomQuestionSet testQuestionSet;
    private CustomQuestionSet publicQuestionSet;
    
    @BeforeEach
    void setUp() {
        testQuestionSet = new CustomQuestionSet();
        testQuestionSet.setId(1L);
        testQuestionSet.setUserId(100L);
        testQuestionSet.setName("Java Questions");
        testQuestionSet.setDescription("Java interview questions");
        testQuestionSet.setTechStack("Java");
        testQuestionSet.setLevel("mid");
        testQuestionSet.setIsPublic(false);
        testQuestionSet.setUsageCount(0);
        testQuestionSet.setQuestions(Arrays.asList("What is Java?", "Explain OOP"));
        
        publicQuestionSet = new CustomQuestionSet();
        publicQuestionSet.setId(2L);
        publicQuestionSet.setUserId(200L);
        publicQuestionSet.setName("Public Questions");
        publicQuestionSet.setIsPublic(true);
        publicQuestionSet.setUsageCount(5);
    }
    
    @Test
    void testGetUserQuestionSets() {
        List<CustomQuestionSet> userSets = Arrays.asList(testQuestionSet);
        List<CustomQuestionSet> publicSets = Arrays.asList(publicQuestionSet);
        
        when(questionSetRepository.findByUserId(100L)).thenReturn(userSets);
        when(questionSetRepository.findByIsPublic(true)).thenReturn(publicSets);
        
        List<CustomQuestionSet> result = questionSetService.getUserQuestionSets(100L);
        
        assertNotNull(result);
        assertTrue(result.size() >= 2);
        verify(questionSetRepository).findByUserId(100L);
        verify(questionSetRepository).findByIsPublic(true);
    }
    
    @Test
    void testGetUserOwnedQuestionSets() {
        List<CustomQuestionSet> userSets = Arrays.asList(testQuestionSet);
        when(questionSetRepository.findByUserId(100L)).thenReturn(userSets);
        
        List<CustomQuestionSet> result = questionSetService.getUserOwnedQuestionSets(100L);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testQuestionSet, result.get(0));
        verify(questionSetRepository).findByUserId(100L);
    }
    
    @Test
    void testGetQuestionSetById_Success() {
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        
        Optional<CustomQuestionSet> result = questionSetService.getQuestionSetById(1L);
        
        assertTrue(result.isPresent());
        assertEquals(testQuestionSet, result.get());
    }
    
    @Test
    void testGetQuestionSetById_NotFound() {
        when(questionSetRepository.findById(999L)).thenReturn(Optional.empty());
        
        Optional<CustomQuestionSet> result = questionSetService.getQuestionSetById(999L);
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void testCreateQuestionSet() {
        when(questionSetRepository.save(any(CustomQuestionSet.class))).thenReturn(testQuestionSet);
        
        CustomQuestionSet result = questionSetService.createQuestionSet(testQuestionSet);
        
        assertNotNull(result);
        assertEquals(testQuestionSet, result);
        verify(questionSetRepository).save(testQuestionSet);
    }
    
    @Test
    void testUpdateQuestionSet_Success() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "Updated Name");
        updates.put("description", "Updated Description");
        
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        when(questionSetRepository.save(any(CustomQuestionSet.class))).thenReturn(testQuestionSet);
        
        CustomQuestionSet result = questionSetService.updateQuestionSet(1L, updates);
        
        assertNotNull(result);
        assertEquals("Updated Name", result.getName());
        assertEquals("Updated Description", result.getDescription());
        verify(questionSetRepository).findById(1L);
        verify(questionSetRepository).save(any(CustomQuestionSet.class));
    }
    
    @Test
    void testUpdateQuestionSet_NotFound() {
        Map<String, Object> updates = new HashMap<>();
        when(questionSetRepository.findById(999L)).thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, () -> {
            questionSetService.updateQuestionSet(999L, updates);
        });
    }
    
    @Test
    void testUpdateQuestionSet_WithAllFields() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "New Name");
        updates.put("description", "New Description");
        updates.put("techStack", "Python");
        updates.put("level", "senior");
        updates.put("isPublic", true);
        updates.put("questions", Arrays.asList("Q1", "Q2"));
        updates.put("tags", Arrays.asList("tag1", "tag2"));
        
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        when(questionSetRepository.save(any(CustomQuestionSet.class))).thenReturn(testQuestionSet);
        
        CustomQuestionSet result = questionSetService.updateQuestionSet(1L, updates);
        
        assertNotNull(result);
        verify(questionSetRepository).save(any(CustomQuestionSet.class));
    }
    
    @Test
    void testDeleteQuestionSet_Success() {
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        doNothing().when(questionSetRepository).delete(any(CustomQuestionSet.class));
        
        questionSetService.deleteQuestionSet(1L, 100L);
        
        verify(questionSetRepository).findById(1L);
        verify(questionSetRepository).delete(testQuestionSet);
    }
    
    @Test
    void testDeleteQuestionSet_NotFound() {
        when(questionSetRepository.findById(999L)).thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, () -> {
            questionSetService.deleteQuestionSet(999L, 100L);
        });
    }
    
    @Test
    void testDeleteQuestionSet_WrongUser() {
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        
        assertThrows(RuntimeException.class, () -> {
            questionSetService.deleteQuestionSet(1L, 999L); // Different user ID
        });
    }
    
    @Test
    void testIncrementUsageCount() {
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        when(questionSetRepository.save(any(CustomQuestionSet.class))).thenReturn(testQuestionSet);
        
        questionSetService.incrementUsageCount(1L);
        
        assertEquals(1, testQuestionSet.getUsageCount());
        verify(questionSetRepository).findById(1L);
        verify(questionSetRepository).save(testQuestionSet);
    }
    
    @Test
    void testIncrementUsageCount_NotFound() {
        when(questionSetRepository.findById(999L)).thenReturn(Optional.empty());
        
        questionSetService.incrementUsageCount(999L);
        
        verify(questionSetRepository, never()).save(any());
    }
    
    @Test
    void testGetPopularQuestionSets() {
        List<CustomQuestionSet> publicSets = Arrays.asList(publicQuestionSet, testQuestionSet);
        when(questionSetRepository.findByIsPublic(true)).thenReturn(publicSets);
        
        List<CustomQuestionSet> result = questionSetService.getPopularQuestionSets(10);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should be sorted by usage count descending
        verify(questionSetRepository).findByIsPublic(true);
    }
    
    @Test
    void testSearchQuestionSets_ByTechStackAndLevel() {
        List<CustomQuestionSet> sets = Arrays.asList(testQuestionSet);
        when(questionSetRepository.findByTechStackAndLevel("Java", "mid")).thenReturn(sets);
        
        List<CustomQuestionSet> result = questionSetService.searchQuestionSets("Java", "mid");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(questionSetRepository).findByTechStackAndLevel("Java", "mid");
    }
    
    @Test
    void testSearchQuestionSets_ByTechStackOnly() {
        when(questionSetRepository.findByTechStackAndLevel("Java", "mid")).thenReturn(Arrays.asList(testQuestionSet));
        
        List<CustomQuestionSet> result = questionSetService.searchQuestionSets("Java", null);
        
        assertNotNull(result);
        verify(questionSetRepository).findByTechStackAndLevel("Java", "mid");
    }
    
    @Test
    void testAddQuestionsToSet() {
        List<String> newQuestions = Arrays.asList("New Question 1", "New Question 2");
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        when(questionSetRepository.save(any(CustomQuestionSet.class))).thenReturn(testQuestionSet);
        
        CustomQuestionSet result = questionSetService.addQuestionsToSet(1L, newQuestions);
        
        assertNotNull(result);
        assertTrue(result.getQuestions().contains("New Question 1"));
        verify(questionSetRepository).save(any(CustomQuestionSet.class));
    }
    
    @Test
    void testRemoveQuestionsFromSet() {
        List<String> questionsToRemove = Arrays.asList("What is Java?");
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(testQuestionSet));
        when(questionSetRepository.save(any(CustomQuestionSet.class))).thenReturn(testQuestionSet);
        
        CustomQuestionSet result = questionSetService.removeQuestionsFromSet(1L, questionsToRemove);
        
        assertNotNull(result);
        verify(questionSetRepository).save(any(CustomQuestionSet.class));
    }
}

