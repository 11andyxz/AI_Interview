package com.aiinterview.ml.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates that question difficulty matches expected level
 * Priority: 5
 */
@Slf4j
@Component
public class DifficultyConsistencyValidator implements OutputValidator {
    
    // Difficulty indicators by level (1-5)
    private static final Map<Integer, Set<String>> DIFFICULTY_INDICATORS = Map.of(
        1, Set.of("what is", "define", "explain", "list", "basic", "introduction"),
        2, Set.of("how", "why", "compare", "describe", "implement"),
        3, Set.of("design", "optimize", "analyze", "evaluate", "trade-offs"),
        4, Set.of("architect", "scale", "performance", "distributed", "complex"),
        5, Set.of("system design", "large-scale", "production", "enterprise", "fault-tolerant")
    );
    
    // Complexity indicators
    private static final Set<String> HIGH_COMPLEXITY_TERMS = Set.of(
        "concurrent", "distributed", "scalability", "architecture",
        "performance", "optimization", "trade-off", "design pattern"
    );
    
    private static final Set<String> LOW_COMPLEXITY_TERMS = Set.of(
        "basic", "simple", "introduction", "beginner", "fundamental"
    );
    
    @Override
    public ValidationResult validate(AIOutput output, ValidationContext context) {
        if (context.getRequestType() != ValidationContext.RequestType.QUESTION_GENERATION) {
            return ValidationResult.pass(getName(), 1.0, "N/A for this request type");
        }
        
        Integer expectedDifficulty = context.getDifficultyLevel();
        if (expectedDifficulty == null) {
            return ValidationResult.pass(getName(), 1.0, "No difficulty level specified");
        }
        
        String content = extractQuestion(output);
        if (content == null || content.trim().isEmpty()) {
            return ValidationResult.fail(getName(), 0.0, "No question content");
        }
        
        String lowerContent = content.toLowerCase();
        
        // 1. Check declared difficulty (if present in output)
        Integer declaredDifficulty = extractDeclaredDifficulty(output);
        if (declaredDifficulty != null && Math.abs(declaredDifficulty - expectedDifficulty) > 1) {
            return ValidationResult.fail(
                getName(), 
                0.3, 
                String.format("Declared difficulty %d differs from expected %d by >1 level", 
                    declaredDifficulty, expectedDifficulty)
            );
        }
        
        // 2. Analyze content complexity
        int detectedLevel = detectDifficultyFromContent(lowerContent);
        int levelDifference = Math.abs(detectedLevel - expectedDifficulty);
        
        // 3. Calculate consistency score
        double consistencyScore;
        if (levelDifference == 0) {
            consistencyScore = 1.0;
        } else if (levelDifference == 1) {
            consistencyScore = 0.8; // Close enough
        } else if (levelDifference == 2) {
            consistencyScore = 0.5; // Noticeable difference
        } else {
            consistencyScore = 0.2; // Significant mismatch
        }
        
        List<String> details = new ArrayList<>();
        details.add("Expected difficulty: " + expectedDifficulty);
        details.add("Detected difficulty: " + detectedLevel);
        details.add("Difference: " + levelDifference + " levels");
        
        boolean passed = levelDifference <= 1;
        String message = passed ?
            "Difficulty level is consistent" :
            "Difficulty level mismatch";
        
        ValidationResult result = ValidationResult.builder()
                .validatorName(getName())
                .score(consistencyScore)
                .passed(passed)
                .message(message)
                .details(details)
                .build();
        
        if (!passed) {
            log.warn("Difficulty mismatch: expected={}, detected={}", expectedDifficulty, detectedLevel);
        }
        
        return result;
    }
    
    private String extractQuestion(AIOutput output) {
        if (!output.isParsed()) {
            return output.getRawResponse();
        }
        
        Object question = output.getField("question");
        return question != null ? question.toString() : output.getRawResponse();
    }
    
    private Integer extractDeclaredDifficulty(AIOutput output) {
        if (!output.isParsed()) {
            return null;
        }
        
        Object difficulty = output.getField("difficulty");
        if (difficulty instanceof Number) {
            return ((Number) difficulty).intValue();
        } else if (difficulty instanceof String) {
            try {
                return Integer.parseInt(difficulty.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }
    
    private int detectDifficultyFromContent(String content) {
        // Score each level based on keyword matches
        Map<Integer, Integer> levelScores = new HashMap<>();
        
        for (Map.Entry<Integer, Set<String>> entry : DIFFICULTY_INDICATORS.entrySet()) {
            int level = entry.getKey();
            int matches = 0;
            
            for (String indicator : entry.getValue()) {
                if (content.contains(indicator)) {
                    matches++;
                }
            }
            
            levelScores.put(level, matches);
        }
        
        // Find level with most matches
        int maxMatches = 0;
        int detectedLevel = 3; // Default to middle
        
        for (Map.Entry<Integer, Integer> entry : levelScores.entrySet()) {
            if (entry.getValue() > maxMatches) {
                maxMatches = entry.getValue();
                detectedLevel = entry.getKey();
            }
        }
        
        // Adjust based on complexity terms
        int highComplexityCount = 0;
        int lowComplexityCount = 0;
        
        for (String term : HIGH_COMPLEXITY_TERMS) {
            if (content.contains(term)) {
                highComplexityCount++;
            }
        }
        
        for (String term : LOW_COMPLEXITY_TERMS) {
            if (content.contains(term)) {
                lowComplexityCount++;
            }
        }
        
        if (highComplexityCount > lowComplexityCount + 1) {
            detectedLevel = Math.min(5, detectedLevel + 1);
        } else if (lowComplexityCount > highComplexityCount + 1) {
            detectedLevel = Math.max(1, detectedLevel - 1);
        }
        
        // Check question length (longer questions often more complex)
        if (content.length() > 300) {
            detectedLevel = Math.min(5, detectedLevel + 1);
        } else if (content.length() < 100) {
            detectedLevel = Math.max(1, detectedLevel - 1);
        }
        
        return detectedLevel;
    }
    
    @Override
    public String getName() {
        return "DifficultyConsistencyValidator";
    }
    
    @Override
    public int getPriority() {
        return 5;
    }
}
