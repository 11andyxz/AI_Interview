package com.aiinterview.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Validator for Answer Evaluation output (Schema #4)
 */
@Component
public class EvaluationResultValidator {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ValidationResult validate(String jsonResponse) {
        ValidationResult result = new ValidationResult() {};
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            // Required fields
            result.requireField(root, "score");
            result.requireField(root, "rubricLevel");
            result.requireField(root, "technicalAccuracy");
            result.requireField(root, "depth");
            result.requireField(root, "experience");
            result.requireField(root, "communication");
            result.requireField(root, "strengths");
            result.requireField(root, "improvements");
            result.requireField(root, "followUpQuestions");
            
            // Validate numbers
            result.validateNumber(root, "score", 0, 100);
            result.validateNumber(root, "technicalAccuracy", 0, 10);
            result.validateNumber(root, "depth", 0, 10);
            result.validateNumber(root, "experience", 0, 10);
            result.validateNumber(root, "communication", 0, 10);
            
            // Validate enum
            result.validateEnum(root, "rubricLevel", "excellent", "good", "average", "poor");
            
            // Validate arrays
            result.validateArray(root, "strengths", 1, 3, 10, 50);
            result.validateArray(root, "improvements", 1, 3, 10, 50);
            result.validateArray(root, "followUpQuestions", 0, 2, 10, 80);
            
            // Validate score calculation
            if (root.has("score") && root.has("technicalAccuracy") && 
                root.has("depth") && root.has("experience") && root.has("communication")) {
                int score = root.get("score").asInt();
                int sum = root.get("technicalAccuracy").asInt() + 
                         root.get("depth").asInt() + 
                         root.get("experience").asInt() + 
                         root.get("communication").asInt();
                int expected = (int)(sum * 2.5);
                if (score != expected) {
                    result.addError("score must equal (dimensions) × 2.5, expected: " + expected);
                }
            }
            
            // Validate rubricLevel vs score range
            if (root.has("score") && root.has("rubricLevel")) {
                int score = root.get("score").asInt();
                String level = root.get("rubricLevel").asText();
                boolean valid = ("excellent".equals(level) && score >= 90) ||
                               ("good".equals(level) && score >= 75 && score < 90) ||
                               ("average".equals(level) && score >= 60 && score < 75) ||
                               ("poor".equals(level) && score < 60);
                if (!valid) {
                    result.addError("rubricLevel '" + level + "' doesn't match score " + score);
                }
            }
            
        } catch (Exception e) {
            result.addError("Invalid JSON: " + e.getMessage());
        }
        
        return result;
    }
}
