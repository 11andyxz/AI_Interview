package com.aiinterview.ml.guardrails;

import com.aiinterview.validator.InterviewQuestionValidator;
import com.aiinterview.validator.ValidationResult;
import org.springframework.stereotype.Component;

/**
 * Adapter that wraps the existing InterviewQuestionValidator
 * into the OutputSchemaValidator interface for the guardrails pipeline.
 */
@Component
public class QuestionSchemaValidator implements OutputSchemaValidator {

    private final InterviewQuestionValidator delegate;

    public QuestionSchemaValidator(InterviewQuestionValidator delegate) {
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
                "action": "<next-question|follow-up|summary>",
                "question": "<string, 10-200 chars, ending with ？> or null if action=summary",
                "reasoning": "<string, 10-50 chars>",
                "expectedDepth": "<brief|detailed|in-depth>"
            }
            """;
    }
}
