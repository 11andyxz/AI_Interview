package com.aiinterview.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Validator for Resume Analysis output (Schema #1)
 */
@Component
public class ResumeAnalysisValidator {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ValidationResult validate(String jsonResponse) {
        ValidationResult result = new ValidationResult() {};
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            // Required fields
            result.requireField(root, "name");
            result.requireField(root, "yearsExperience");
            result.requireField(root, "coreSkills");
            result.requireField(root, "positionFit");
            result.requireField(root, "strengths");
            result.requireField(root, "weaknesses");
            result.requireField(root, "suggestedQuestions");
            
            // Validate fields
            result.validateString(root, "name", 2, 50);
            result.validateNumber(root, "yearsExperience", 0, 50);
            result.validateNumber(root, "positionFit", 0, 100);
            result.validateArray(root, "coreSkills", 1, 10, 2, 30);
            result.validateArray(root, "strengths", 2, 5, 10, 100);
            result.validateArray(root, "weaknesses", 1, 3, 10, 100);
            result.validateArray(root, "suggestedQuestions", 3, 5, 10, 80);
            
        } catch (Exception e) {
            result.addError("Invalid JSON: " + e.getMessage());
        }
        
        return result;
    }
}
