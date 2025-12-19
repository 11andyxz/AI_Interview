package com.aiinterview.service;

import com.aiinterview.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceTest {
    
    @Mock
    private JwtConfig jwtConfig;
    
    @InjectMocks
    private JwtService jwtService;
    
    @BeforeEach
    void setUp() {
        when(jwtConfig.getSecret()).thenReturn("test-secret-key-min-256-bits-for-jwt-testing-purposes-only-change-in-production");
        when(jwtConfig.getExpiration()).thenReturn(86400000L); // 24 hours
        when(jwtConfig.getRefreshExpiration()).thenReturn(604800000L); // 7 days
    }
    
    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken(1L, "testuser");
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }
    
    @Test
    void testGenerateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(1L, "testuser");
        
        assertNotNull(refreshToken);
        assertFalse(refreshToken.isEmpty());
    }
    
    @Test
    void testValidateToken() {
        String token = jwtService.generateToken(1L, "testuser");
        
        assertTrue(jwtService.validateToken(token));
    }
    
    @Test
    void testValidateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(1L, "testuser");
        
        assertTrue(jwtService.validateRefreshToken(refreshToken));
    }
    
    @Test
    void testExtractUsername() {
        String token = jwtService.generateToken(1L, "testuser");
        
        String username = jwtService.extractUsername(token);
        
        assertEquals("testuser", username);
    }
    
    @Test
    void testExtractUserId() {
        String token = jwtService.generateToken(1L, "testuser");
        
        Long userId = jwtService.extractUserId(token);
        
        assertEquals(1L, userId);
    }
    
    @Test
    void testInvalidToken() {
        assertFalse(jwtService.validateToken("invalid.token.here"));
    }
}

