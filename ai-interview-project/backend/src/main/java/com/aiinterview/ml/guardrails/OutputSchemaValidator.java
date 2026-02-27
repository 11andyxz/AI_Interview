package com.aiinterview.ml.guardrails;

import java.util.List;

/**
 * Interface for validating LLM output against a schema
 * Implementations define domain-specific validation rules
 */
public interface OutputSchemaValidator {
    
    /**
     * Validate output against schema
     * 
     * @param output Raw LLM output string
     * @return Validation result with errors if any
     */
    ValidationResult validate(String output);
    
    /**
     * Get schema description for repair prompts
     * 
     * @return Human-readable schema description
     */
    String getSchemaDescription();
    
    /**
     * Get expected output type name
     * 
     * @return Type name for logging
     */
    String getOutputTypeName();
    
    /**
     * Validation result
     */
    class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final String repairHint;
        
        public ValidationResult(boolean valid, List<String> errors, String repairHint) {
            this.valid = valid;
            this.errors = errors != null ? errors : List.of();
            this.repairHint = repairHint;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), null);
        }
        
        public static ValidationResult failure(List<String> errors, String repairHint) {
            return new ValidationResult(false, errors, repairHint);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public String getRepairHint() {
            return repairHint;
        }
    }
}
