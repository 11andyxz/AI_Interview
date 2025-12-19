package com.aiinterview.service;

import com.aiinterview.model.User;
import com.aiinterview.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("$2a$10$encryptedPasswordHash"); // BCrypt hash
    }
    
    @Test
    void testFindByUsername() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        Optional<User> result = userService.findByUsername("testuser");
        
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        verify(userRepository).findByUsername("testuser");
    }
    
    @Test
    void testCreateUser() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });
        
        User newUser = userService.createUser("newuser", "password123");
        
        assertNotNull(newUser);
        assertEquals("newuser", newUser.getUsername());
        assertNotEquals("password123", newUser.getPassword()); // Should be encrypted
        assertTrue(newUser.getPassword().startsWith("$2a$")); // BCrypt hash format
        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void testCreateUserDuplicate() {
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);
        
        assertThrows(RuntimeException.class, () -> {
            userService.createUser("existinguser", "password123");
        });
        
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void testValidateUserSuccess() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        // Password encoder will match the hashed password
        // In real test, we'd need to set up the actual password encoder
        
        // Mock the password encoder behavior
        PasswordEncoder encoder = userService.getPasswordEncoder();
        String rawPassword = "testpassword";
        String hashedPassword = encoder.encode(rawPassword);
        testUser.setPassword(hashedPassword);
        
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        boolean isValid = userService.validateUser("testuser", rawPassword);
        
        assertTrue(isValid);
    }
    
    @Test
    void testValidateUserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());
        
        boolean isValid = userService.validateUser("nonexistent", "password");
        
        assertFalse(isValid);
    }
    
    @Test
    void testValidateUserWrongPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        boolean isValid = userService.validateUser("testuser", "wrongpassword");
        
        assertFalse(isValid);
    }
}

