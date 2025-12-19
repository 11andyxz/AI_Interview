package com.aiinterview.controller;

import com.aiinterview.model.User;
import com.aiinterview.service.JwtService;
import com.aiinterview.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private JwtService jwtService;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("$2a$10$encrypted");
    }
    
    @Test
    void testLoginSuccess() throws Exception {
        when(userService.validateUser("testuser", "password123")).thenReturn(true);
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(1L, "testuser")).thenReturn("test-access-token");
        when(jwtService.generateRefreshToken(1L, "testuser")).thenReturn("test-refresh-token");
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }
    
    @Test
    void testLoginFailure() throws Exception {
        when(userService.validateUser("testuser", "wrongpassword")).thenReturn(false);
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
    
    @Test
    void testRegisterSuccess() throws Exception {
        User newUser = new User();
        newUser.setId(2L);
        newUser.setUsername("newuser");
        newUser.setPassword("$2a$10$encrypted");
        
        when(userService.createUser("newuser", "password123")).thenReturn(newUser);
        when(jwtService.generateToken(2L, "newuser")).thenReturn("test-access-token");
        when(jwtService.generateRefreshToken(2L, "newuser")).thenReturn("test-refresh-token");
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists());
    }
    
    @Test
    void testRefreshToken_Success() throws Exception {
        String refreshToken = "valid-refresh-token";
        when(jwtService.validateRefreshToken(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn(1L);
        when(jwtService.extractUsername(refreshToken)).thenReturn("testuser");
        when(jwtService.generateToken(1L, "testuser")).thenReturn("new-access-token");
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
    
    @Test
    void testRefreshToken_InvalidToken() throws Exception {
        String refreshToken = "invalid-refresh-token";
        when(jwtService.validateRefreshToken(refreshToken)).thenReturn(false);
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }
    
    @Test
    void testLogin_EmptyCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username and password cannot be empty"));
    }
    
    @Test
    void testRegister_EmptyCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username and password cannot be empty"));
    }
    
    @Test
    void testRegister_UsernameExists() throws Exception {
        when(userService.createUser("existinguser", "password123"))
            .thenThrow(new RuntimeException("Username already exists"));
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"existinguser\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }
}

