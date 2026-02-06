package com.aiinterview.ml.validation;

/**
 * Base interface for ML output validators
 * Each validator checks a specific quality dimension of AI-generated outputs
 */
public interface OutputValidator {
    /**
     * Validates an AI output against specific quality criteria
     * 
     * @param output The AI-generated output to validate
     * @param context Additional context for validation (e.g., user role, difficulty level)
     * @return ValidationResult containing pass/fail status, score, and details
     */
    ValidationResult validate(AIOutput output, ValidationContext context);
    
    /**
     * Get the name of this validator (e.g., "StructureValidator")
     */
    String getName();
    
    /**
     * Get the priority of this validator (lower number = higher priority)
     * Structure validation should run first (priority 1), others can be 2-5
     */
    int getPriority();
}
