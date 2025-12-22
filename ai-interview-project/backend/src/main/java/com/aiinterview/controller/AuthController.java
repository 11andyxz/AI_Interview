package com.aiinterview.controller;

import com.aiinterview.model.User;
import com.aiinterview.service.JwtService;
import com.aiinterview.service.SubscriptionService;
import com.aiinterview.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SubscriptionService subscriptionService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        System.out.println("Login attempt for user: " + credentials.get("username"));
        String username = credentials.get("username");
        String password = credentials.get("password");

        Map<String, Object> response = new HashMap<>();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        boolean isValid = userService.validateUser(username, password);
        
        if (isValid) {
            Optional<User> user = userService.findByUsername(username);
            User userObj = user.get();
            
            // Generate JWT tokens
            String accessToken = jwtService.generateToken(userObj.getId(), username);
            String refreshToken = jwtService.generateRefreshToken(userObj.getId(), username);
            
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("user", Map.of(
                "id", userObj.getId(),
                "username", userObj.getUsername()
            ));
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        Map<String, Object> response = new HashMap<>();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            User user = userService.createUser(username, password);

            // Start trial period for new user (7 days)
            try {
                System.out.println("Starting trial for user: " + user.getId());
                var trialSubscription = subscriptionService.startTrial(user.getId(), 1); // Plan ID 1 = Pro plan
                System.out.println("Trial started successfully: " + trialSubscription.getId());
            } catch (Exception e) {
                // Log error but don't fail registration
                System.err.println("Failed to start trial for user " + user.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }

            // Generate JWT tokens for new user
            String accessToken = jwtService.generateToken(user.getId(), username);
            String refreshToken = jwtService.generateRefreshToken(user.getId(), username);

            response.put("success", true);
            response.put("message", "Registration successful");
            response.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername()
            ));
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        Map<String, Object> response = new HashMap<>();
        
        if (refreshToken == null || !jwtService.validateRefreshToken(refreshToken)) {
            response.put("success", false);
            response.put("message", "Invalid or expired refresh token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        Long userId = jwtService.extractUserId(refreshToken);
        String username = jwtService.extractUsername(refreshToken);
        
        String newAccessToken = jwtService.generateToken(userId, username);
        
        response.put("success", true);
        response.put("accessToken", newAccessToken);
        response.put("tokenType", "Bearer");
        return ResponseEntity.ok(response);
    }
}
