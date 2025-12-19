package com.aiinterview.service;

import com.aiinterview.model.ApiKeyConfig;
import com.aiinterview.repository.ApiKeyConfigRepository;
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
class ApiKeyConfigServiceTest {
    
    @Mock
    private ApiKeyConfigRepository apiKeyConfigRepository;
    
    @InjectMocks
    private ApiKeyConfigService apiKeyConfigService;
    
    private ApiKeyConfig activeConfig;
    private ApiKeyConfig inactiveConfig;
    
    @BeforeEach
    void setUp() {
        activeConfig = new ApiKeyConfig("openai", "sk-test-key-123");
        activeConfig.setId(1);
        activeConfig.setIsActive(true);
        
        inactiveConfig = new ApiKeyConfig("openai", "sk-old-key-456");
        inactiveConfig.setId(2);
        inactiveConfig.setIsActive(false);
    }
    
    @Test
    void testGetActiveApiKey_Success() {
        when(apiKeyConfigRepository.findByServiceNameAndIsActive("openai", true))
            .thenReturn(Optional.of(activeConfig));
        
        Optional<String> result = apiKeyConfigService.getActiveApiKey("openai");
        
        assertTrue(result.isPresent());
        assertEquals("sk-test-key-123", result.get());
        verify(apiKeyConfigRepository).findByServiceNameAndIsActive("openai", true);
    }
    
    @Test
    void testGetActiveApiKey_NotFound() {
        when(apiKeyConfigRepository.findByServiceNameAndIsActive("openai", true))
            .thenReturn(Optional.empty());
        
        Optional<String> result = apiKeyConfigService.getActiveApiKey("openai");
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetActiveApiKeyConfig_Success() {
        when(apiKeyConfigRepository.findByServiceNameAndIsActive("openai", true))
            .thenReturn(Optional.of(activeConfig));
        
        Optional<ApiKeyConfig> result = apiKeyConfigService.getActiveApiKeyConfig("openai");
        
        assertTrue(result.isPresent());
        assertEquals(activeConfig, result.get());
    }
    
    @Test
    void testHasActiveApiKey_True() {
        when(apiKeyConfigRepository.existsByServiceNameAndIsActive("openai", true))
            .thenReturn(true);
        
        boolean result = apiKeyConfigService.hasActiveApiKey("openai");
        
        assertTrue(result);
        verify(apiKeyConfigRepository).existsByServiceNameAndIsActive("openai", true);
    }
    
    @Test
    void testHasActiveApiKey_False() {
        when(apiKeyConfigRepository.existsByServiceNameAndIsActive("openai", true))
            .thenReturn(false);
        
        boolean result = apiKeyConfigService.hasActiveApiKey("openai");
        
        assertFalse(result);
    }
    
    @Test
    void testSave() {
        when(apiKeyConfigRepository.save(any(ApiKeyConfig.class))).thenReturn(activeConfig);
        
        ApiKeyConfig result = apiKeyConfigService.save(activeConfig);
        
        assertNotNull(result);
        assertEquals(activeConfig, result);
        verify(apiKeyConfigRepository).save(activeConfig);
    }
    
    @Test
    void testFindAll() {
        List<ApiKeyConfig> configs = Arrays.asList(activeConfig, inactiveConfig);
        when(apiKeyConfigRepository.findAll()).thenReturn(configs);
        
        List<ApiKeyConfig> result = apiKeyConfigService.findAll();
        
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(apiKeyConfigRepository).findAll();
    }
    
    @Test
    void testDeactivateAllForService() {
        List<ApiKeyConfig> allConfigs = Arrays.asList(activeConfig, inactiveConfig);
        when(apiKeyConfigRepository.findAll()).thenReturn(allConfigs);
        when(apiKeyConfigRepository.save(any(ApiKeyConfig.class))).thenReturn(activeConfig);
        
        apiKeyConfigService.deactivateAllForService("openai");
        
        verify(apiKeyConfigRepository).findAll();
        verify(apiKeyConfigRepository).save(activeConfig);
        assertFalse(activeConfig.getIsActive());
    }
    
    @Test
    void testDeactivateAllForService_NoActiveKeys() {
        List<ApiKeyConfig> allConfigs = Arrays.asList(inactiveConfig);
        when(apiKeyConfigRepository.findAll()).thenReturn(allConfigs);
        
        apiKeyConfigService.deactivateAllForService("openai");
        
        verify(apiKeyConfigRepository).findAll();
        verify(apiKeyConfigRepository, never()).save(any());
    }
}

