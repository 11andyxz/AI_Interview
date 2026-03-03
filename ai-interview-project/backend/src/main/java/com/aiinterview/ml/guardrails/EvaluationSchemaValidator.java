package com.aiinterview.ml.guardrails;

import com.aiinterview.validator.EvaluationResultValidator;
import com.aiinterview.validator.ValidationResult;
import org.springframework.stereotype.Component;

/**
 * Adapter that wraps the existing EvaluationResultValidator
 * into the OutputSchemaValidator interface for the guardrails pipeline.
 */
@Component
public class EvaluationSchemaValidator implements OutputSchemaValidator {

    private final EvaluationResultValidator delegate;

    public EvaluationSchemaValidator(EvaluationResultValidator delegate) {
        this.delegate = delegate;
    }

    @Override
    public ValidationResult validate(String jsonResponse) {
        return delegate.validate(jsonResponse);
    }

    @Override
    public String getExpectedSchema() {
        return """
            {
                "score": <0-100>,
                "rubricLevel": "<excellent|good|average|poor>",
                "technicalAccuracy": <0-10>,
                "depth": <0-10>,
                "experience": <0-10>,
                "communication": <0-10>,
                "strengths": ["string", ...],
                "improvements": ["string", ...],
                "followUpQuestions": ["string", ...]
            }
            Note: score must equal (technicalAccuracy + depth + experience + communication) * 2.5
            rubricLevel must match score range: excellent>=90, good>=75, average>=60, poor<60
            """;
    }
}
