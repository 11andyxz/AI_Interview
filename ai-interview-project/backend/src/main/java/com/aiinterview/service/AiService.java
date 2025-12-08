package com.aiinterview.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class AiService {

    private static final String[] GENERIC_RESPONSES = {
        "That's a great answer. Can you elaborate on the technical challenges you faced?",
        "Interesting point. How would you approach this if the requirements changed?",
        "Good. Let's move on to the next topic. How comfortable are you with system design?",
        "I see. Could you give me a specific example from your past experience?",
        "Thank you. Now, let's discuss your preferred way of working in a team."
    };


    private final Random random = new Random();

    /**
     * Simulates generating interview questions based on the role.
     * In a real scenario, this would call an LLM API.
     */
    public List<String> generateInterviewQuestions(String role) {
        // Mock logic: return specific questions based on keywords in the role
        List<String> questions = new ArrayList<>();
        String normalizedRole = role != null ? role.toLowerCase() : "";

        if (normalizedRole.contains("java") || normalizedRole.contains("backend")) {
            questions.add("Can you explain the difference between JDK, JRE, and JVM?");
            questions.add("How does HashMap work internally in Java?");
            questions.add("What are the key features of Java 8+?");
            questions.add("Explain the Spring Boot dependency injection mechanism.");
        } else if (normalizedRole.contains("frontend") || normalizedRole.contains("react") || normalizedRole.contains("web")) {
            questions.add("What is the Virtual DOM and how does it work?");
            questions.add("Explain the difference between state and props in React.");
            questions.add("What are the advantages of using TypeScript?");
            questions.add("How do you handle performance optimization in a web app?");
        } else if (normalizedRole.contains("python") || normalizedRole.contains("ai") || normalizedRole.contains("data")) {
            questions.add("Explain the difference between a list and a tuple in Python.");
            questions.add("What is a decorator and how is it used?");
            questions.add("Describe a machine learning project you have worked on.");
            questions.add("How do you handle missing data in a dataset?");
        } else {
            // General behavioral questions
            questions.add("Tell me about yourself.");
            questions.add("What do you consider your greatest professional achievement?");
            questions.add("Describe a challenging situation you faced at work and how you dealt with it.");
        }
        
        return questions;
    }

    /**
     * Simulates a chat response from the AI Interviewer.
     */
    /* public String generateAiResponse(String userMessage) {
        // Simple mock responses to make the interaction feel dynamic
        String[] genericResponses = {
            "That's a great answer. Can you elaborate on the technical challenges you faced?",
            "Interesting point. How would you approach this if the requirements changed?",
            "Good. Let's move on to the next topic. How comfortable are you with system design?",
            "I see. Could you give me a specific example from your past experience?",
            "Thank you. Now, let's discuss your preferred way of working in a team."
        };
        
        return genericResponses[random.nextInt(genericResponses.length)];
    }*/
    public String generateAiResponse(String userMessage) {
        return GENERIC_RESPONSES[random.nextInt(GENERIC_RESPONSES.length)];
    }


    // TO DO: AI Audio Processing
    public String analyzeVoiceResponse(byte[] audioData) {
        // TO DO: Send audio to Speech-to-Text service (e.g., Whisper)
        // TO DO: Analyze sentiment and technical accuracy of the transcript
        return "Voice analysis feature is coming soon...";
    }

    /**
     * Mock resume summarization for MVP.
     */
    public String generateResumeSummary(String resumeText, String jobDescription) {
        return "Mock summary: This candidate has relevant experience based on the provided resume.";
    }
}

