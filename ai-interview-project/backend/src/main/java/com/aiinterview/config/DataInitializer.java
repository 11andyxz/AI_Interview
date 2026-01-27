package com.aiinterview.config;

import com.aiinterview.model.User;
import com.aiinterview.repository.UserRepository;
import com.aiinterview.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * DataInitializer - Creates default test data on application startup.
 * Only runs when not in test profile.
 */
@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;
    private final UserRepository userRepository;

    // Constructor injection (recommended over field injection)
    public DataInitializer(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing default data...");
        
        Optional<User> existingUser = userService.findByUsername("test");
        
        if (existingUser.isEmpty()) {
            // Create new test user with encrypted password
            try {
                userService.createUser("test", "123456");
                logger.info("Test user created: username=test, password=123456");
                logger.warn("SECURITY WARNING: Default test user is active. Please change password in production!");
            } catch (RuntimeException e) {
                logger.error("Failed to create test user: {}", e.getMessage());
            }
        } else {
            // Check if password is encrypted (BCrypt format starts with $2a$ or $2b$)
            User user = existingUser.get();
            String password = user.getPassword();
            
            if (password == null || !(password.startsWith("$2a$") || password.startsWith("$2b$"))) {
                // Password is not encrypted, update it
                user.setPassword(userService.getPasswordEncoder().encode("123456"));
                userRepository.save(user);
                logger.info("Test user password updated to BCrypt encrypted format");
            } else {
                logger.debug("Test user already exists with encrypted password");
            }
        }
        
        logger.info("Data initialization completed");
    }
}

