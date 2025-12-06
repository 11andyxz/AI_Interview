package com.aiinterview.controller;

import com.aiinterview.model.User;
import com.aiinterview.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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

    // Debug endpoint to check path
    @RequestMapping("/**")
    public ResponseEntity<String> debug(HttpServletRequest request) {
        System.out.println("Received request: " + request.getMethod() + " " + request.getRequestURI());
        return ResponseEntity.ok("Received: " + request.getRequestURI());
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        System.out.println("Login attempt for user: " + credentials.get("username"));
        String username = credentials.get("username");
        String password = credentials.get("password");

        Map<String, Object> response = new HashMap<>();

        if (username == null || password == null) {
            response.put("success", false);
            response.put("message", "Username and password cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        boolean isValid = userService.validateUser(username, password);
        
        if (isValid) {
            Optional<User> user = userService.findByUsername(username);
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("user", Map.of(
                "id", user.get().getId(),
                "username", user.get().getUsername()
            ));
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

        if (username == null || password == null) {
            response.put("success", false);
            response.put("message", "Username and password cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            User user = userService.createUser(username, password);
            response.put("success", true);
            response.put("message", "Registration successful");
            response.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername()
            ));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
