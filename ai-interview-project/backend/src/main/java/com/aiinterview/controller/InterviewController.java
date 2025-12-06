package com.aiinterview.controller;

import com.aiinterview.model.Interview;
import com.aiinterview.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final AiService aiService;

    public InterviewController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping
    public List<Interview> getAllInterviews() {
        // Mock data mimicking the screenshot
        List<Interview> list = new ArrayList<>();
        list.add(new Interview(UUID.randomUUID().toString(), "Internet/AI / Artificial Intelligence", "English", "JavaScript,Python,Java,Kotlin", LocalDate.of(2025, 11, 25), "Completed"));
        list.add(new Interview(UUID.randomUUID().toString(), "Internet/AI / Artificial Intelligence", "English", "JavaScript,Python,Java,Kotlin", LocalDate.of(2025, 11, 25), "Completed"));
        list.add(new Interview(UUID.randomUUID().toString(), "Internet/AI / Artificial Intelligence", "English", "JavaScript,Python,Java,Kotlin", LocalDate.of(2025, 11, 24), "Completed"));
        list.add(new Interview(UUID.randomUUID().toString(), "Internet/AI / Backend Development", "English", "Java, Spring Boot, SQL", LocalDate.of(2025, 5, 8), "Completed"));
        return list;
    }

    @PostMapping("/start")
    public Interview startAiInterview(@RequestBody String jobRole) {
        // Call AI Service to prepare session context (simulated)
        // In a real app, we would save these questions to the database linked to the interview ID
        List<String> questions = aiService.generateInterviewQuestions(jobRole);
        System.out.println("Generated questions for " + jobRole + ": " + questions);
        
        // Return a new interview object
        Interview newInterview = new Interview(
            UUID.randomUUID().toString(),
            jobRole != null && !jobRole.isEmpty() ? jobRole : "New AI Interview",
            "English",
            "React, Java, Spring", // This could also be dynamic based on role
            LocalDate.now(),
            "In Progress"
        );
        
        return newInterview;
    }
    
    @PostMapping("/{id}/chat")
    public String chatWithAi(@PathVariable String id, @RequestBody String userMessage) {
        // Implement real chat logic passing message to Service
        return aiService.generateAiResponse(userMessage);
    }
}
