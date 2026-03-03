package com.aiinterview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * AI Interview Application - Main entry point for the Spring Boot application.
 * 
 * This application provides an AI-powered interview platform with features including:
 * - Real-time interview sessions via WebSocket
 * - OpenAI integration for intelligent questioning
 * - Resume analysis and parsing
 * - User authentication and authorization
 * - Subscription management
 * 
 * @author AI Interview Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class AiInterviewApplication {

    private static final Logger logger = LoggerFactory.getLogger(AiInterviewApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AiInterviewApplication.class);
        Environment env = app.run(args).getEnvironment();
        
        logApplicationStartup(env);
    }

    /**
     * Log application startup information with access URLs
     */
    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";
        
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("Could not determine host address", e);
        }

        logger.info("\n----------------------------------------------------------\n" +
                "Application '{}' is running! Access URLs:\n" +
                "  Local:      {}://localhost:{}{}\n" +
                "  External:   {}://{}:{}{}\n" +
                "  Profile(s): {}\n" +
                "----------------------------------------------------------",
                env.getProperty("spring.application.name"),
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath,
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles()
        );
    }
}

