package com.aiinterview.service;

import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    private KnowledgeBase userKb;
    private KnowledgeBase systemKb;
    private Long kbId = 1L;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        userKb = new KnowledgeBase();
        userKb.setId(kbId);
        userKb.setUserId(userId);
        userKb.setType("user");
        userKb.setName("User KB");
        userKb.setIsActive(true);

        systemKb = new KnowledgeBase();
        systemKb.setId(2L);
        systemKb.setUserId(null);
        systemKb.setType("system");
        systemKb.setName("System KB");
        systemKb.setIsActive(true);
    }

    @Test
    void testGetKnowledgeBases_AllTypes() {
        when(knowledgeBaseRepository.findByUserIdAndTypeAndIsActiveTrueOrderByCreatedAtDesc(userId, "user"))
            .thenReturn(Arrays.asList(userKb));
        when(knowledgeBaseRepository.findByTypeAndIsActiveTrueOrderByCreatedAtDesc("system"))
            .thenReturn(Arrays.asList(systemKb));

        List<KnowledgeBase> result = knowledgeBaseService.getKnowledgeBases(userId, null);

        assertEquals(2, result.size());
    }

    @Test
    void testGetKnowledgeBases_UserType() {
        when(knowledgeBaseRepository.findByUserIdAndTypeAndIsActiveTrueOrderByCreatedAtDesc(userId, "user"))
            .thenReturn(Arrays.asList(userKb));

        List<KnowledgeBase> result = knowledgeBaseService.getKnowledgeBases(userId, "user");

        assertEquals(1, result.size());
        assertEquals(userKb, result.get(0));
    }

    @Test
    void testGetKnowledgeBases_SystemType() {
        when(knowledgeBaseRepository.findByTypeAndIsActiveTrueOrderByCreatedAtDesc("system"))
            .thenReturn(Arrays.asList(systemKb));

        List<KnowledgeBase> result = knowledgeBaseService.getKnowledgeBases(userId, "system");

        assertEquals(1, result.size());
        assertEquals(systemKb, result.get(0));
    }

    @Test
    void testGetSystemKnowledgeBases() {
        when(knowledgeBaseRepository.findByTypeOrderByCreatedAtDesc("system"))
            .thenReturn(Arrays.asList(systemKb));

        List<KnowledgeBase> result = knowledgeBaseService.getSystemKnowledgeBases();

        assertEquals(1, result.size());
        assertEquals(systemKb, result.get(0));
    }

    @Test
    void testGetKnowledgeBaseById_UserKb() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(userKb));

        Optional<KnowledgeBase> result = knowledgeBaseService.getKnowledgeBaseById(kbId, userId);

        assertTrue(result.isPresent());
        assertEquals(userKb, result.get());
    }

    @Test
    void testGetKnowledgeBaseById_SystemKb() {
        when(knowledgeBaseRepository.findById(2L)).thenReturn(Optional.of(systemKb));

        Optional<KnowledgeBase> result = knowledgeBaseService.getKnowledgeBaseById(2L, userId);

        assertTrue(result.isPresent());
        assertEquals(systemKb, result.get());
    }

    @Test
    void testGetKnowledgeBaseById_WrongUser() {
        Long otherUserId = 2L;
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(userKb));

        Optional<KnowledgeBase> result = knowledgeBaseService.getKnowledgeBaseById(kbId, otherUserId);

        assertFalse(result.isPresent());
    }

    @Test
    void testCreateKnowledgeBase() {
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBase created = knowledgeBaseService.createKnowledgeBase(
            userId, "New KB", "Description", "{}");

        assertNotNull(created);
        assertEquals(userId, created.getUserId());
        assertEquals("user", created.getType());
        assertEquals("New KB", created.getName());
        assertTrue(created.getIsActive());
        verify(knowledgeBaseRepository).save(any(KnowledgeBase.class));
    }

    @Test
    void testUpdateKnowledgeBase_UserKb() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(userKb));
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBase updated = knowledgeBaseService.updateKnowledgeBase(
            kbId, userId, "Updated Name", "Updated Desc", "{}");

        assertEquals("Updated Name", updated.getName());
        verify(knowledgeBaseRepository).save(any(KnowledgeBase.class));
    }

    @Test
    void testUpdateKnowledgeBase_SystemKb_ShouldFail() {
        when(knowledgeBaseRepository.findById(2L)).thenReturn(Optional.of(systemKb));

        assertThrows(RuntimeException.class, () -> {
            knowledgeBaseService.updateKnowledgeBase(2L, userId, "Name", "Desc", "{}");
        });
    }

    @Test
    void testDeleteKnowledgeBase_UserKb() {
        when(knowledgeBaseRepository.findById(kbId)).thenReturn(Optional.of(userKb));
        doNothing().when(knowledgeBaseRepository).delete(any(KnowledgeBase.class));

        boolean deleted = knowledgeBaseService.deleteKnowledgeBase(kbId, userId);

        assertTrue(deleted);
        verify(knowledgeBaseRepository).delete(userKb);
    }

    @Test
    void testDeleteKnowledgeBase_SystemKb_ShouldFail() {
        when(knowledgeBaseRepository.findById(2L)).thenReturn(Optional.of(systemKb));

        assertThrows(RuntimeException.class, () -> {
            knowledgeBaseService.deleteKnowledgeBase(2L, userId);
        });
    }
}

