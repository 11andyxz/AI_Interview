package com.aiinterview.ml.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates content quality using heuristics
 * Checks: length, readability, technical term usage, coherence
 * Priority: 2
 */
@Slf4j
@Component
public class ContentQualityValidator implements OutputValidator {
    
    private static final int MIN_QUESTION_LENGTH = 20;
    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MIN_ANSWER_LENGTH = 50;
    private static final int MAX_ANSWER_LENGTH = 2000;
    
    // Common technical terms for validation
    private static final Set<String> TECHNICAL_TERMS = Set.of(
        "algorithm", "data structure", "complexity", "api", "database",
        "framework", "architecture", "design pattern", "scalability",
        "optimization", "performance", "security", "testing", "deployment",
        "microservice", "rest", "async", "concurrent", "thread", "cache"
    );
    
    @Override
    public ValidationResult validate(AIOutput output, ValidationContext context) {
        double qualityScore = 1.0;
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        String content = extractMainContent(output, context);
        
        if (content == null || content.trim().isEmpty()) {
            return ValidationResult.fail(getName(), 0.0, "Empty content");
        }
        
        // 1. Length checks
        int length = content.length();
        if (context.getRequestType() == ValidationContext.RequestType.QUESTION_GENERATION) {
            if (length < MIN_QUESTION_LENGTH) {
                issues.add("Question too short: " + length + " chars");
                qualityScore -= 0.3;
            } else if (length > MAX_QUESTION_LENGTH) {
                warnings.add("Question very long: " + length + " chars");
                qualityScore -= 0.1;
            }
        } else if (context.getRequestType() == ValidationContext.RequestType.ANSWER_EVALUATION) {
            if (length < MIN_ANSWER_LENGTH) {
                warnings.add("Feedback quite brief");
                qualityScore -= 0.1;
            }
        }
        
        // 2. Vocabulary diversity (Type-Token Ratio)
        String[] words = content.toLowerCase().split("\\s+");
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        double vocabularyDiversity = (double) uniqueWords.size() / words.length;
        
        if (vocabularyDiversity < 0.4) {
            warnings.add("Low vocabulary diversity: " + String.format("%.2f", vocabularyDiversity));
            qualityScore -= 0.1;
        }
        
        // 3. Repetition detection (check for repeated phrases)
        if (hasExcessiveRepetition(content)) {
            issues.add("Excessive repetition detected");
            qualityScore -= 0.2;
        }
        
        // 4. Technical term usage (for technical content)
        if (isTechnicalContext(context)) {
            int technicalTermCount = countTechnicalTerms(content);
            if (technicalTermCount == 0) {
                warnings.add("No technical terms found");
                qualityScore -= 0.15;
            }
        }
        
        // 5. Sentence structure (very basic check)
        if (hasIncompletesentences(content)) {
            warnings.add("Possible incomplete sentences");
            qualityScore -= 0.05;
        }
        
        // 6. Special characters / formatting issues
        if (hasFormattingIssues(content)) {
            warnings.add("Formatting anomalies detected");
            qualityScore -= 0.05;
        }
        
        qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));
        
        ValidationResult result = ValidationResult.builder()
                .validatorName(getName())
                .score(qualityScore)
                .passed(qualityScore >= 0.6)
                .message(qualityScore >= 0.6 ? "Content quality acceptable" : "Content quality issues")
                .details(issues)
                .warnings(warnings)
                .build();
        
        return result;
    }
    
    private String extractMainContent(AIOutput output, ValidationContext context) {
        if (!output.isParsed()) {
            return output.getRawResponse();
        }
        
        // Extract main content based on request type
        Object content = switch (context.getRequestType()) {
            case QUESTION_GENERATION -> output.getField("question");
            case ANSWER_EVALUATION -> output.getField("strengths");
            case FOLLOW_UP_QUESTION -> output.getField("question");
            default -> output.getRawResponse();
        };
        
        return content != null ? content.toString() : output.getRawResponse();
    }
    
    private boolean hasExcessiveRepetition(String content) {
        // Check for repeated 3-grams
        String[] words = content.toLowerCase().split("\\s+");
        if (words.length < 3) return false;
        
        Set<String> trigrams = new HashSet<>();
        int repeatedCount = 0;
        
        for (int i = 0; i < words.length - 2; i++) {
            String trigram = words[i] + " " + words[i + 1] + " " + words[i + 2];
            if (!trigrams.add(trigram)) {
                repeatedCount++;
            }
        }
        
        return repeatedCount > words.length * 0.1; // >10% repetition
    }
    
    private int countTechnicalTerms(String content) {
        String lowerContent = content.toLowerCase();
        int count = 0;
        for (String term : TECHNICAL_TERMS) {
            if (lowerContent.contains(term)) {
                count++;
            }
        }
        return count;
    }
    
    private boolean isTechnicalContext(ValidationContext context) {
        return context.getRequestType() == ValidationContext.RequestType.QUESTION_GENERATION ||
               context.getRequestType() == ValidationContext.RequestType.RESUME_ANALYSIS;
    }
    
    private boolean hasIncompletesentences(String content) {
        // Very basic check: ends with proper punctuation
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return true;
        
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        return !(lastChar == '.' || lastChar == '?' || lastChar == '!');
    }
    
    private boolean hasFormattingIssues(String content) {
        // Check for excessive special characters
        long specialCharCount = content.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
                .count();
        
        return specialCharCount > content.length() * 0.2; // >20% special chars
    }
    
    @Override
    public String getName() {
        return "ContentQualityValidator";
    }
    
    @Override
    public int getPriority() {
        return 2;
    }
}
