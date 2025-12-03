package com.aiinterview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Interview {
    private String id;
    private String title; // e.g., "Internet / AI / Artificial Intelligence"
    private String language; // e.g., "English"
    private String techStack; // e.g., "JavaScript, Python, Java, Kotlin"
    private LocalDate date;
    private String status; // e.g., "Completed", "Scheduled"
}

