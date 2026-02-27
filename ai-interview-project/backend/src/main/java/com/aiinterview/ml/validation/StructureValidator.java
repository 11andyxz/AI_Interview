package com.aiinterview.ml.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates JSON structure and required fields
 * Priority: 1 (highest - must run first)
 */
@Slf4j
@Component
public class StructureValidator implements OutputValidator {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Required fields by request type
    private static final Map<ValidationContext.RequestType, Set<String>> REQUIRED_FIELDS = Map.of(
        ValidationContext.RequestType.RESUME_ANALYSIS, Set.of(
            "candidateInfo", "skills", "experience", "assessment", "interviewQuestions"
        ),
        ValidationContext.RequestType.QUESTION_GENERATION, Set.of(
            "question", "expectedAnswer", "difficulty", "tags"
        ),
        ValidationContext.RequestType.ANSWER_EVALUATION, Set.of(
            "score", "strengths", "improvements", "technicalAccuracy"
        ),
        ValidationContext.RequestType.FOLLOW_UP_QUESTION, Set.of(
            "question", "rationale"
        )
    );
    
    @Override
    public ValidationResult validate(AIOutput output, ValidationContext context) {
        ValidationResult result = ValidationResult.builder()
                .validatorName(getName())
                .build();
        
        // Check if output is parseable JSON
        if (output.getRawResponse() == null || output.getRawResponse().trim().isEmpty()) {
            return ValidationResult.fail(getName(), 0.0, "Empty response");
        }
        
        try {
            // Try to parse JSON
            JsonNode jsonNode = objectMapper.readTree(output.getRawResponse());
            
            // Verify required fields exist
            Set<String> requiredFields = REQUIRED_FIELDS.getOrDefault(
                context.getRequestType(), Collections.emptySet()
            );
            
            List<String> missingFields = new ArrayList<>();
            for (String field : requiredFields) {
                if (!jsonNode.has(field)) {
                    missingFields.add(field);
                }
            }
            
            if (!missingFields.isEmpty()) {
                result.setDetails(missingFields);
                return ValidationResult.fail(
                    getName(), 
                    0.5, 
                    "Missing required fields: " + String.join(", ", missingFields)
                );
            }
            
            // Check for empty required fields
            List<String> emptyFields = new ArrayList<>();
            for (String field : requiredFields) {
                JsonNode fieldNode = jsonNode.get(field);
                if (fieldNode.isNull() || 
                    (fieldNode.isTextual() && fieldNode.asText().trim().isEmpty()) ||
                    (fieldNode.isArray() && fieldNode.size() == 0)) {
                    emptyFields.add(field);
                }
            }
            
            if (!emptyFields.isEmpty()) {
                result.setWarnings(emptyFields);
                result.addWarning("Empty fields: " + String.join(", ", emptyFields));
            }
            
            // Calculate score
            double completeness = 1.0 - (emptyFields.size() * 0.1);
            completeness = Math.max(0.7, completeness); // Min 0.7 if structure is valid
            
            return ValidationResult.pass(
                getName(), 
                completeness,
                "Valid JSON structure with all required fields"
            );
            
        } catch (Exception e) {
            log.error("JSON parsing failed", e);
            return ValidationResult.fail(
                getName(), 
                0.0, 
                "Invalid JSON: " + e.getMessage()
            );
        }
    }
    
    @Override
    public String getName() {
        return "StructureValidator";
    }
    
    @Override
    public int getPriority() {
        return 1; // Highest priority
    }
}
