package com.aiinterview.ml.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Detects inappropriate, biased, or toxic content
 * Priority: 3
 */
@Slf4j
@Component
public class ToxicityValidator implements OutputValidator {
    
    // Inappropriate words/phrases (simplified list - in production use external API)
    private static final Set<String> INAPPROPRIATE_TERMS = Set.of(
        // Profanity (mild examples for demonstration)
        "damn", "hell", "crap",
        // Discriminatory terms
        "stupid", "idiot", "dumb",
        // Biased language
        "guys only", "girls only", "too old", "too young"
    );
    
    // Biased phrases
    private static final Set<String> BIASED_PHRASES = Set.of(
        "not suitable for women",
        "too old for this role",
        "young people only",
        "native speakers only",
        "must be male",
        "must be female",
        "fresh graduates preferred"
    );
    
    // Personal/sensitive questions that should be avoided
    private static final Set<String> INAPPROPRIATE_QUESTIONS = Set.of(
        "are you married",
        "do you have children",
        "what is your age",
        "what is your religion",
        "are you pregnant",
        "what is your sexual orientation",
        "where are you from originally"
    );
    
    @Override
    public ValidationResult validate(AIOutput output, ValidationContext context) {
        String content = output.getRawResponse();
        if (content == null || content.trim().isEmpty()) {
            return ValidationResult.pass(getName(), 1.0, "Empty content - no toxicity");
        }
        
        String lowerContent = content.toLowerCase();
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double toxicityScore = 0.0;
        
        // 1. Check for inappropriate terms
        for (String term : INAPPROPRIATE_TERMS) {
            if (lowerContent.contains(term)) {
                violations.add("Inappropriate term: " + term);
                toxicityScore += 0.3;
            }
        }
        
        // 2. Check for biased phrases
        for (String phrase : BIASED_PHRASES) {
            if (lowerContent.contains(phrase)) {
                violations.add("Biased phrase: " + phrase);
                toxicityScore += 0.5;
            }
        }
        
        // 3. Check for inappropriate personal questions
        for (String question : INAPPROPRIATE_QUESTIONS) {
            if (lowerContent.contains(question)) {
                violations.add("Inappropriate question: " + question);
                toxicityScore += 0.4;
            }
        }
        
        // 4. Check for gender-specific pronouns in questions (potential bias)
        if (context.getRequestType() == ValidationContext.RequestType.QUESTION_GENERATION) {
            if (lowerContent.matches(".*\\b(he|him|his|she|her)\\b.*")) {
                warnings.add("Gender-specific pronouns found - consider using gender-neutral language");
            }
        }
        
        // 5. Check for age-related bias
        if (lowerContent.contains("years old") || lowerContent.contains("age requirement")) {
            warnings.add("Age-related content detected");
        }
        
        // 6. Check for cultural bias
        if (hasculturalBias(lowerContent)) {
            warnings.add("Potential cultural bias detected");
        }
        
        // Calculate final score (0 = toxic, 1 = clean)
        double cleanScore = Math.max(0.0, 1.0 - toxicityScore);
        boolean passed = toxicityScore < 0.1; // Allow very minor issues
        
        ValidationResult result = ValidationResult.builder()
                .validatorName(getName())
                .score(cleanScore)
                .passed(passed)
                .message(passed ? "No toxicity detected" : "Toxicity issues found")
                .details(violations)
                .warnings(warnings)
                .build();
        
        if (!violations.isEmpty()) {
            log.warn("Toxicity violations detected: {}", violations);
        }
        
        return result;
    }
    
    private boolean hasculturalBias(String content) {
        // Check for assumptions about cultural norms
        String[] biasIndicators = {
            "everyone knows", "obviously", "common knowledge",
            "traditional family", "normal people"
        };
        
        for (String indicator : biasIndicators) {
            if (content.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public String getName() {
        return "ToxicityValidator";
    }
    
    @Override
    public int getPriority() {
        return 3;
    }
}
