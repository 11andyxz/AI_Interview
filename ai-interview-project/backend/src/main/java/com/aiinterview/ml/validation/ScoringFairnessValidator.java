package com.aiinterview.ml.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates scoring fairness and consistency
 * Checks for score distribution, bias, and justification
 * Priority: 6
 */
@Slf4j
@Component
public class ScoringFairnessValidator implements OutputValidator {
    
    private static final double MIN_VALID_SCORE = 0.0;
    private static final double MAX_VALID_SCORE = 10.0;
    private static final int MIN_FEEDBACK_LENGTH = 30;
    
    @Override
    public ValidationResult validate(AIOutput output, ValidationContext context) {
        if (context.getRequestType() != ValidationContext.RequestType.ANSWER_EVALUATION) {
            return ValidationResult.pass(getName(), 1.0, "N/A for this request type");
        }
        
        if (!output.isParsed()) {
            return ValidationResult.fail(getName(), 0.0, "Unable to parse scoring output");
        }
        
        double fairnessScore = 1.0;
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 1. Extract and validate score
        Double score = extractScore(output);
        if (score == null) {
            return ValidationResult.fail(getName(), 0.0, "No score found");
        }
        
        if (score < MIN_VALID_SCORE || score > MAX_VALID_SCORE) {
            issues.add("Score out of valid range: " + score);
            fairnessScore -= 0.5;
        }
        
        // 2. Check for justification/feedback
        String strengths = extractField(output, "strengths");
        String improvements = extractField(output, "improvements");
        
        if (strengths == null || strengths.length() < MIN_FEEDBACK_LENGTH) {
            warnings.add("Insufficient strengths feedback");
            fairnessScore -= 0.1;
        }
        
        if (improvements == null || improvements.length() < MIN_FEEDBACK_LENGTH) {
            warnings.add("Insufficient improvement feedback");
            fairnessScore -= 0.1;
        }
        
        // 3. Check score-feedback consistency
        if (!isScoreConsistentWithFeedback(score, strengths, improvements)) {
            issues.add("Score not consistent with feedback");
            fairnessScore -= 0.3;
        }
        
        // 4. Check for extreme scores without justification
        if (isExtremeScore(score)) {
            if ((strengths == null || strengths.length() < 50) && 
                (improvements == null || improvements.length() < 50)) {
                warnings.add("Extreme score needs more detailed justification");
                fairnessScore -= 0.15;
            }
        }
        
        // 5. Check for balanced feedback (not all positive or all negative)
        if (!hasBalancedFeedback(strengths, improvements, score)) {
            warnings.add("Feedback could be more balanced");
            fairnessScore -= 0.05;
        }
        
        // 6. Check for specific technical points (not vague)
        if (hasvagueFeedback(strengths, improvements)) {
            warnings.add("Feedback could be more specific");
            fairnessScore -= 0.1;
        }
        
        fairnessScore = Math.max(0.0, Math.min(1.0, fairnessScore));
        boolean passed = fairnessScore >= 0.7;
        
        String message = passed ?
            "Scoring appears fair and well-justified" :
            "Scoring fairness concerns detected";
        
        ValidationResult result = ValidationResult.builder()
                .validatorName(getName())
                .score(fairnessScore)
                .passed(passed)
                .message(message)
                .details(issues)
                .warnings(warnings)
                .build();
        
        result.addDetail("Evaluated score: " + score);
        
        return result;
    }
    
    private Double extractScore(AIOutput output) {
        Object scoreObj = output.getField("score");
        if (scoreObj instanceof Number) {
            return ((Number) scoreObj).doubleValue();
        } else if (scoreObj instanceof String) {
            try {
                return Double.parseDouble(scoreObj.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private String extractField(AIOutput output, String fieldName) {
        Object field = output.getField(fieldName);
        return field != null ? field.toString() : null;
    }
    
    private boolean isScoreConsistentWithFeedback(double score, String strengths, String improvements) {
        // High score (>7) should have more strengths than improvements
        if (score > 7.0) {
            int strengthsLength = strengths != null ? strengths.length() : 0;
            int improvementsLength = improvements != null ? improvements.length() : 0;
            
            if (improvementsLength > strengthsLength * 1.5) {
                return false; // Too many improvements for a high score
            }
        }
        
        // Low score (<4) should have more improvements than strengths
        if (score < 4.0) {
            int strengthsLength = strengths != null ? strengths.length() : 0;
            int improvementsLength = improvements != null ? improvements.length() : 0;
            
            if (strengthsLength > improvementsLength * 1.5) {
                return false; // Too many strengths for a low score
            }
        }
        
        return true;
    }
    
    private boolean isExtremeScore(double score) {
        return score <= 2.0 || score >= 9.0;
    }
    
    private boolean hasBalancedFeedback(String strengths, String improvements, double score) {
        // Middle scores (4-7) should have both strengths and improvements
        if (score >= 4.0 && score <= 7.0) {
            boolean hasStrengths = strengths != null && strengths.length() > 20;
            boolean hasImprovements = improvements != null && improvements.length() > 20;
            
            return hasStrengths && hasImprovements;
        }
        
        return true; // For extreme scores, balanced is not as critical
    }
    
    private boolean hasvagueFeedback(String strengths, String improvements) {
        // Check for vague phrases
        String[] vaguePhrases = {
            "good job", "well done", "needs improvement", "could be better",
            "nice work", "not bad", "okay", "fine"
        };
        
        String combined = (strengths != null ? strengths : "") + " " + 
                         (improvements != null ? improvements : "");
        String lowerCombined = combined.toLowerCase();
        
        int vagueCount = 0;
        for (String phrase : vaguePhrases) {
            if (lowerCombined.contains(phrase)) {
                vagueCount++;
            }
        }
        
        // Check for specific technical terms (good sign)
        String[] specificTerms = {
            "algorithm", "complexity", "implementation", "optimization",
            "code quality", "test coverage", "edge case", "design pattern"
        };
        
        int specificCount = 0;
        for (String term : specificTerms) {
            if (lowerCombined.contains(term)) {
                specificCount++;
            }
        }
        
        // Vague if many vague phrases and few specific terms
        return vagueCount > 2 && specificCount == 0;
    }
    
    @Override
    public String getName() {
        return "ScoringFairnessValidator";
    }
    
    @Override
    public int getPriority() {
        return 6;
    }
}
