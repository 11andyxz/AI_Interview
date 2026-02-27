package com.aiinterview.ml.guardrails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Example JSON schema validator for interview questions
 * Validates structure, required fields, and data types
 */
@Slf4j
public class InterviewQuestionValidator implements OutputSchemaValidator {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public ValidationResult validate(String output) {
        List<String> errors = new ArrayList<>();
        
        try {
            // Parse JSON
            JsonNode root = objectMapper.readTree(output);
            
            // Check required fields
            if (!root.has("question")) {
                errors.add("Missing required field: question");
            } else if (!root.get("question").isTextual() || root.get("question").asText().isEmpty()) {
                errors.add("Field 'question' must be a non-empty string");
            }
            
            if (!root.has("difficulty")) {
                errors.add("Missing required field: difficulty");
            } else {
                String difficulty = root.get("difficulty").asText();
                if (!List.of("easy", "medium", "hard").contains(difficulty.toLowerCase())) {
                    errors.add("Field 'difficulty' must be one of: easy, medium, hard");
                }
            }
            
            if (!root.has("expectedAnswer")) {
                errors.add("Missing required field: expectedAnswer");
            } else if (!root.get("expectedAnswer").isTextual()) {
                errors.add("Field 'expectedAnswer' must be a string");
            }
            
            if (!root.has("keywords")) {
                errors.add("Missing required field: keywords");
            } else if (!root.get("keywords").isArray()) {
                errors.add("Field 'keywords' must be an array");
            } else if (root.get("keywords").size() == 0) {
                errors.add("Field 'keywords' must contain at least one item");
            }
            
            // Check optional fields
            if (root.has("followUpQuestions")) {
                if (!root.get("followUpQuestions").isArray()) {
                    errors.add("Field 'followUpQuestions' must be an array");
                }
            }
            
            if (errors.isEmpty()) {
                return ValidationResult.success();
            } else {
                String repairHint = "Ensure all required fields (question, difficulty, expectedAnswer, keywords) " +
                                   "are present with correct types. Difficulty must be easy/medium/hard.";
                return ValidationResult.failure(errors, repairHint);
            }
            
        } catch (Exception e) {
            log.error("JSON parsing error", e);
            errors.add("Invalid JSON format: " + e.getMessage());
            return ValidationResult.failure(errors, "Output must be valid JSON");
        }
    }
    
    @Override
    public String getSchemaDescription() {
        return """
            {
              "question": "string (required, non-empty)",
              "difficulty": "string (required, one of: easy, medium, hard)",
              "expectedAnswer": "string (required)",
              "keywords": ["string", "..."] (required, at least one item),
              "followUpQuestions": ["string", "..."] (optional)
            }
            """;
    }
    
    @Override
    public String getOutputTypeName() {
        return "InterviewQuestion";
    }
}
