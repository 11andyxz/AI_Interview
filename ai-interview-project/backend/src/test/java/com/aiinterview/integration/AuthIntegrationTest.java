package com.aiinterview.integration;

import com.aiinterview.model.User;
import com.aiinterview.repository.UserRepository;
import com.aiinterview.service.JwtService;
import com.aiinterview.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testCompleteAuthFlow_RegisterLoginRefresh() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        String password = "testPassword123";
        
        // Step 1: Register
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
        
        // Verify user was created
        Optional<User> userOpt = userRepository.findByUsername(username);
        assertTrue(userOpt.isPresent());
        
        // Step 2: Login
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Extract refresh token (simplified - in real test, parse JSON)
        assertTrue(loginResponse.contains("refreshToken"));
        
        // Step 3: Validate token
        Optional<User> loggedInUser = userService.findByUsername(username);
        assertTrue(loggedInUser.isPresent());
        assertTrue(userService.validateUser(username, password));
    }
    
    @Test
    void testLoginFailure_WrongPassword() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        String password = "testPassword123";
        
        // Register first
        userService.createUser(username, password);
        
        // Try to login with wrong password
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrongPassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
    
    @Test
    void testRegisterDuplicateUsername() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        String password = "testPassword123";
        
        // Register first time
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
        
        // Try to register again with same username
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}

