package com.aiinterview.ml.guardrails;

import com.aiinterview.validator.ValidationResult;

/**
 * Interface for schema validators used in the structured output enforcement pipeline.
 * Adapts existing validators (EvaluationResultValidator, InterviewQuestionValidator)
 * to a common interface.
 */
public interface OutputSchemaValidator {
    ValidationResult validate(String jsonResponse);
    String getExpectedSchema();
}
