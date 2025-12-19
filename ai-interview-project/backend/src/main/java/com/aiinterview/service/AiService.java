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

    /**
     * Analyze resume content and extract key information
     */
    public String analyzeResumeContent(String resumeText) {
        if (resumeText == null || resumeText.trim().isEmpty()) {
            return "No resume content available for analysis.";
        }

        // This is a simplified analysis - in a real implementation, you would:
        // 1. Use NLP to extract entities (skills, experience, education)
        // 2. Analyze technical proficiency levels
        // 3. Generate insights about candidate fit for various roles

        StringBuilder analysis = new StringBuilder();
        analysis.append("Resume Analysis Results:\n\n");

        // Extract potential skills (basic keyword matching)
        String[] technicalSkills = {"Java", "Python", "JavaScript", "React", "Spring", "SQL", "Git", "Docker", "AWS", "Kubernetes"};
        String[] foundSkills = java.util.Arrays.stream(technicalSkills)
            .filter(skill -> resumeText.toLowerCase().contains(skill.toLowerCase()))
            .toArray(String[]::new);

        if (foundSkills.length > 0) {
            analysis.append("Technical Skills Identified: ").append(String.join(", ", foundSkills)).append("\n\n");
        }

        // Extract potential experience areas
        String[] experienceAreas = {"Backend Development", "Frontend Development", "Full Stack", "API Development", "Database", "System Design"};
        String[] foundExperiences = java.util.Arrays.stream(experienceAreas)
            .filter(area -> resumeText.toLowerCase().contains(area.toLowerCase()) ||
                   resumeText.toLowerCase().contains(area.toLowerCase().replace(" ", "")))
            .toArray(String[]::new);

        if (foundExperiences.length > 0) {
            analysis.append("Experience Areas: ").append(String.join(", ", foundExperiences)).append("\n\n");
        }

        // Basic content analysis
        String[] lines = resumeText.split("\n");
        int totalLines = lines.length;
        analysis.append("Resume Statistics:\n");
        analysis.append("- Total content lines: ").append(totalLines).append("\n");
        analysis.append("- Estimated experience level: ").append(estimateExperienceLevel(resumeText)).append("\n\n");

        analysis.append("Recommendations:\n");
        analysis.append("- Consider highlighting key achievements and quantifiable results\n");
        analysis.append("- Ensure technical skills are clearly listed and up-to-date\n");
        analysis.append("- Include specific examples of project work and technologies used\n");

        return analysis.toString();
    }

    /**
     * Estimate experience level based on resume content
     */
    private String estimateExperienceLevel(String resumeText) {
        String lowerText = resumeText.toLowerCase();

        // Count keywords that indicate seniority
        int seniorKeywords = 0;
        String[] seniorIndicators = {"senior", "lead", "architect", "principal", "manager", "director", "10+", "years"};

        for (String keyword : seniorIndicators) {
            if (lowerText.contains(keyword)) {
                seniorKeywords++;
            }
        }

        // Count technologies and frameworks
        int techCount = 0;
        String[] technologies = {"java", "python", "javascript", "react", "spring", "docker", "kubernetes", "aws", "azure"};

        for (String tech : technologies) {
            if (lowerText.contains(tech)) {
                techCount++;
            }
        }

        if (seniorKeywords >= 2 || techCount >= 5) {
            return "Senior Level";
        } else if (techCount >= 3) {
            return "Mid Level";
        } else {
            return "Junior Level";
        }
    }
}

