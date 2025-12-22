package com.aiinterview.config;

import com.aiinterview.model.User;
import com.aiinterview.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        // Check if test user already exists
        if (!userRepository.existsByUsername("test")) {
            User testUser = new User();
            testUser.setUsername("test");
            testUser.setPassword("123456");
            userRepository.save(testUser);
            System.out.println("Test user created: username=test, password=123456");
        } else {
            System.out.println("Test user already exists");
        }
    }
}

