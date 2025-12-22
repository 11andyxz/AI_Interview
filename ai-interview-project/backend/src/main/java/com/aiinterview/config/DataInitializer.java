package com.aiinterview.config;

import com.aiinterview.model.User;
import com.aiinterview.repository.UserRepository;
import com.aiinterview.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        Optional<User> existingUser = userService.findByUsername("test");
        
        if (existingUser.isEmpty()) {
            // Create new test user with encrypted password
            try {
                userService.createUser("test", "123456");
                System.out.println("Test user created: username=test, password=123456");
            } catch (RuntimeException e) {
                System.out.println("Failed to create test user: " + e.getMessage());
            }
        } else {
            // Check if password is encrypted (BCrypt format starts with $2a$)
            User user = existingUser.get();
            String password = user.getPassword();
            
            if (password == null || !password.startsWith("$2a$")) {
                // Password is not encrypted, update it
                user.setPassword(userService.getPasswordEncoder().encode("123456"));
                userRepository.save(user);
                System.out.println("Test user password updated to BCrypt encrypted format");
            } else {
                System.out.println("Test user already exists with encrypted password");
            }
        }
    }
}

