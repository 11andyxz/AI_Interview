package com.aiinterview.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validator for Interview Question output (Schema #2 & #3)
 */
@Component
public class InterviewQuestionValidator {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ValidationResult validate(String jsonResponse) {
        return validate(jsonResponse, null);
    }
    
    public ValidationResult validate(String jsonResponse, List<String> previousQuestions) {
        ValidationResult result = new ValidationResult() {};
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            // Required fields
            result.requireField(root, "action");
            result.requireField(root, "question");
            result.requireField(root, "reasoning");
            result.requireField(root, "expectedDepth");
            
            // Validate enums
            result.validateEnum(root, "action", "next-question", "follow-up", "summary");
            result.validateEnum(root, "expectedDepth", "brief", "detailed", "in-depth");
            
            // Validate question (conditional)
            if (root.has("action") && root.has("question")) {
                String action = root.get("action").asText();
                boolean questionIsNull = root.get("question").isNull();
                
                if ("summary".equals(action) && !questionIsNull) {
                    result.addError("question must be null when action='summary'");
                } else if (!"summary".equals(action)) {
                    if (questionIsNull) {
                        result.addError("question required when action='" + action + "'");
                    } else {
                        String question = root.get("question").asText();
                        if (question.length() < 10 || question.length() > 200) {
                            result.addError("question must be 10-200 characters");
                        }
                        if (!question.endsWith("？")) {
                            result.addError("question must end with ？");
                        }
                        if (previousQuestions != null && previousQuestions.contains(question)) {
                            result.addError("question duplicates previous");
                        }
                    }
                }
            }
            
            // Validate reasoning
            result.validateString(root, "reasoning", 10, 50);
            
        } catch (Exception e) {
            result.addError("Invalid JSON: " + e.getMessage());
        }
        
        return result;
    }
}
