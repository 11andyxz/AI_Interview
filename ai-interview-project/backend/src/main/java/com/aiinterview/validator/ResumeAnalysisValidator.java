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

            // Support both current schema and legacy schema for compatibility.
            boolean currentSchema = root.has("level") || root.has("techStack") || root.has("experienceYears");
            if (currentSchema) {
                validateCurrentSchema(root, result);
            } else {
                validateLegacySchema(root, result);
            }
            
        } catch (Exception e) {
            result.addError("Invalid JSON: " + e.getMessage());
        }
        
        return result;
    }

    private void validateCurrentSchema(JsonNode root, ValidationResult result) {
        // Core fields are required for the current ResumeAnalysisResult DTO.
        result.requireField(root, "level");
        result.requireField(root, "techStack");
        result.requireField(root, "experienceYears");
        result.requireField(root, "skills");
        result.requireField(root, "mainSkillAreas");
        result.requireField(root, "education");
        result.requireField(root, "summary");

        result.validateEnum(root, "level", "junior", "mid", "senior");
        result.validateNumber(root, "experienceYears", 0, 60);
        result.validateArray(root, "techStack", 0, 30, 1, 80);
        result.validateArray(root, "skills", 0, 50, 1, 120);
        result.validateArray(root, "mainSkillAreas", 0, 20, 1, 120);
        result.validateString(root, "education", 1, 200);
        result.validateString(root, "summary", 1, 2000);
    }

    private void validateLegacySchema(JsonNode root, ValidationResult result) {
        result.requireField(root, "name");
        result.requireField(root, "yearsExperience");
        result.requireField(root, "coreSkills");
        result.requireField(root, "positionFit");
        result.requireField(root, "strengths");
        result.requireField(root, "weaknesses");
        result.requireField(root, "suggestedQuestions");

        result.validateString(root, "name", 2, 50);
        result.validateNumber(root, "yearsExperience", 0, 50);
        result.validateNumber(root, "positionFit", 0, 100);
        result.validateArray(root, "coreSkills", 1, 10, 2, 30);
        result.validateArray(root, "strengths", 2, 5, 10, 100);
        result.validateArray(root, "weaknesses", 1, 3, 10, 100);
        result.validateArray(root, "suggestedQuestions", 3, 5, 10, 80);
    }
}
