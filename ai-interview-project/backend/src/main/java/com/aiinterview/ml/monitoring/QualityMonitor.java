package com.aiinterview.ml.monitoring;

import com.aiinterview.ml.validation.*;
import com.aiinterview.monitoring.MLMetricsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Monitors and tracks ML output quality metrics in real-time
 * Integrates with MLMetricsCollector to persist quality data
 */
@Service
@Slf4j
public class QualityMonitor {
    
    @Autowired
    private MLMetricsCollector metricsCollector;
    
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");
    private static final Pattern CODE_PATTERN = Pattern.compile("```[\\s\\S]*?```|`[^`]+`");
    private static final Set<String> TECHNICAL_TERMS = Set.of(
        "algorithm", "api", "architecture", "async", "backend", "cache", "class",
        "database", "deployment", "docker", "frontend", "function", "git", "http",
        "interface", "java", "kubernetes", "microservice", "rest", "spring", "sql"
    );
    
    /**
     * Calculate and record quality metrics for AI output
     */
    public QualityMetrics calculateQualityMetrics(AIOutput output, ValidationContext context) {
        String content = extractContent(output);
        
        // Calculate individual metrics
        double completeness = calculateCompleteness(output, context);
        double tokenEfficiency = calculateTokenEfficiency(output, content);
        double vocabularyDiversity = calculateVocabularyDiversity(content);
        double repetitionScore = calculateRepetitionScore(content);
        double technicalAccuracy = calculateTechnicalAccuracy(content, context);
        double clarityScore = calculateClarityScore(content);
        
        // Record to database
        String endpoint = context.getRequestType();
        String modelVersion = context.getModelVersion() != null ? context.getModelVersion() : "gpt-4o-mini";
        
        Map<String, Object> tags = new HashMap<>();
        tags.put("request_type", endpoint);
        tags.put("role", context.getRole());
        
        metricsCollector.recordMetric("ml.output.completeness", completeness, modelVersion, endpoint, tags);
        metricsCollector.recordMetric("ml.token.efficiency", tokenEfficiency, modelVersion, endpoint, tags);
        metricsCollector.recordMetric("ml.vocabulary.diversity", vocabularyDiversity, modelVersion, endpoint, tags);
        metricsCollector.recordMetric("ml.repetition.score", repetitionScore, modelVersion, endpoint, tags);
        metricsCollector.recordMetric("ml.technical.accuracy", technicalAccuracy, modelVersion, endpoint, tags);
        metricsCollector.recordMetric("ml.clarity.score", clarityScore, modelVersion, endpoint, tags);
        
        log.info("Quality metrics calculated - completeness: {}, efficiency: {}, diversity: {}", 
                 completeness, tokenEfficiency, vocabularyDiversity);
        
        return new QualityMetrics(completeness, tokenEfficiency, vocabularyDiversity, 
                                  repetitionScore, technicalAccuracy, clarityScore);
    }
    
    /**
     * Record validation results and quality scores
     */
    public void recordValidationResults(AggregatedValidationResult result, ValidationContext context) {
        String endpoint = context.getRequestType();
        String modelVersion = context.getModelVersion() != null ? context.getModelVersion() : "gpt-4o-mini";
        
        Map<String, Object> tags = new HashMap<>();
        tags.put("request_type", endpoint);
        tags.put("role", context.getRole());
        
        // Overall validation pass/fail
        boolean passed = result.isPassed();
        metricsCollector.recordValidation(endpoint, modelVersion, passed);
        
        // Individual validator scores
        for (ValidationResult vr : result.getResults()) {
            String metricName = "ml.validator." + vr.getValidatorName().toLowerCase();
            metricsCollector.recordMetric(metricName, vr.getScore(), modelVersion, endpoint, tags);
        }
        
        // Record failure reasons if any
        if (!passed) {
            List<String> failures = result.getFailures();
            log.warn("Validation failures detected: {}", failures);
            
            tags.put("failures", String.join(", ", failures));
            metricsCollector.recordMetric("ml.validation.failure_count", failures.size(), modelVersion, endpoint, tags);
        }
    }
    
    /**
     * Calculate completeness score (0-1)
     * Checks if all required fields are present and non-empty
     */
    private double calculateCompleteness(AIOutput output, ValidationContext context) {
        int totalFields = 0;
        int completeFields = 0;
        
        // Check based on request type
        if ("RESUME_ANALYSIS".equals(context.getRequestType())) {
            totalFields = 5; // candidateInfo, skills, experience, assessment, interviewQuestions
            if (output.getCandidateInfo() != null && !output.getCandidateInfo().isEmpty()) completeFields++;
            if (output.getSkills() != null && !output.getSkills().isEmpty()) completeFields++;
            if (output.getExperience() != null && !output.getExperience().isEmpty()) completeFields++;
            if (output.getAssessment() != null && !output.getAssessment().isEmpty()) completeFields++;
            if (output.getInterviewQuestions() != null && !output.getInterviewQuestions().isEmpty()) completeFields++;
        } else if ("GENERATE_QUESTIONS".equals(context.getRequestType())) {
            totalFields = 1;
            if (output.getInterviewQuestions() != null && !output.getInterviewQuestions().isEmpty()) completeFields++;
        } else if ("SCORE_ANSWER".equals(context.getRequestType())) {
            totalFields = 4; // score, feedback, strengths, improvements
            if (output.getScore() != null) completeFields++;
            if (output.getFeedback() != null && !output.getFeedback().isEmpty()) completeFields++;
            if (output.getStrengths() != null && !output.getStrengths().isEmpty()) completeFields++;
            if (output.getImprovements() != null && !output.getImprovements().isEmpty()) completeFields++;
        }
        
        return totalFields > 0 ? (double) completeFields / totalFields : 1.0;
    }
    
    /**
     * Calculate token efficiency (quality per token)
     * Higher values indicate more information per token
     */
    private double calculateTokenEfficiency(AIOutput output, String content) {
        int tokenCount = estimateTokenCount(content);
        int informationUnits = countInformationUnits(content);
        
        return tokenCount > 0 ? (double) informationUnits / tokenCount : 0.0;
    }
    
    /**
     * Calculate vocabulary diversity (unique tokens / total tokens)
     */
    private double calculateVocabularyDiversity(String content) {
        List<String> words = extractWords(content);
        if (words.isEmpty()) return 0.0;
        
        long uniqueWords = words.stream().map(String::toLowerCase).distinct().count();
        return (double) uniqueWords / words.size();
    }
    
    /**
     * Calculate repetition score (lower is better, 1.0 = no repetition)
     */
    private double calculateRepetitionScore(String content) {
        List<String> words = extractWords(content);
        if (words.size() < 10) return 1.0;
        
        // Check for repeated phrases (3-grams)
        Map<String, Integer> trigramCounts = new HashMap<>();
        for (int i = 0; i < words.size() - 2; i++) {
            String trigram = words.get(i) + " " + words.get(i + 1) + " " + words.get(i + 2);
            trigramCounts.merge(trigram.toLowerCase(), 1, Integer::sum);
        }
        
        long repeatedTrigrams = trigramCounts.values().stream().filter(count -> count > 1).count();
        double repetitionRatio = (double) repeatedTrigrams / trigramCounts.size();
        
        return 1.0 - Math.min(repetitionRatio, 1.0);
    }
    
    /**
     * Calculate technical accuracy score
     * Based on presence of technical terms and code snippets
     */
    private double calculateTechnicalAccuracy(String content, ValidationContext context) {
        // For technical roles, expect technical content
        if (context.getRole() == null || !isTechnicalRole(context.getRole())) {
            return 1.0; // Not applicable
        }
        
        List<String> words = extractWords(content);
        long technicalTermCount = words.stream()
            .map(String::toLowerCase)
            .filter(TECHNICAL_TERMS::contains)
            .count();
        
        boolean hasCodeSnippets = CODE_PATTERN.matcher(content).find();
        
        double termScore = Math.min((double) technicalTermCount / 5, 1.0); // Expect at least 5 technical terms
        double codeScore = hasCodeSnippets ? 1.0 : 0.5;
        
        return (termScore * 0.7 + codeScore * 0.3);
    }
    
    /**
     * Calculate clarity score based on sentence length and structure
     */
    private double calculateClarityScore(String content) {
        String[] sentences = content.split("[.!?]+");
        if (sentences.length == 0) return 0.0;
        
        double avgWordCount = Arrays.stream(sentences)
            .mapToInt(s -> extractWords(s).size())
            .average()
            .orElse(0.0);
        
        // Ideal sentence length: 15-25 words
        double clarityScore;
        if (avgWordCount < 10 || avgWordCount > 30) {
            clarityScore = 0.6;
        } else if (avgWordCount >= 15 && avgWordCount <= 25) {
            clarityScore = 1.0;
        } else {
            clarityScore = 0.8;
        }
        
        return clarityScore;
    }
    
    // Helper methods
    
    private String extractContent(AIOutput output) {
        StringBuilder content = new StringBuilder();
        if (output.getFeedback() != null) content.append(output.getFeedback()).append(" ");
        if (output.getInterviewQuestions() != null) {
            output.getInterviewQuestions().forEach(q -> content.append(q).append(" "));
        }
        if (output.getAssessment() != null) content.append(output.getAssessment()).append(" ");
        return content.toString();
    }
    
    private List<String> extractWords(String text) {
        return WORD_PATTERN.matcher(text)
            .results()
            .map(mr -> mr.group())
            .filter(w -> w.length() > 2) // Filter out very short words
            .collect(Collectors.toList());
    }
    
    private int estimateTokenCount(String text) {
        // Rough estimation: 1 token ≈ 4 characters
        return Math.max(1, text.length() / 4);
    }
    
    private int countInformationUnits(String content) {
        // Count sentences, bullet points, code blocks as information units
        int sentences = content.split("[.!?]+").length;
        int bulletPoints = content.split("\\n[-*•]").length - 1;
        int codeBlocks = (int) CODE_PATTERN.matcher(content).results().count();
        
        return sentences + bulletPoints * 2 + codeBlocks * 5;
    }
    
    private boolean isTechnicalRole(String role) {
        String roleLower = role.toLowerCase();
        return roleLower.contains("engineer") || roleLower.contains("developer") || 
               roleLower.contains("architect") || roleLower.contains("programmer");
    }
    
    /**
     * Quality metrics data class
     */
    public static class QualityMetrics {
        private final double completeness;
        private final double tokenEfficiency;
        private final double vocabularyDiversity;
        private final double repetitionScore;
        private final double technicalAccuracy;
        private final double clarityScore;
        
        public QualityMetrics(double completeness, double tokenEfficiency, double vocabularyDiversity,
                             double repetitionScore, double technicalAccuracy, double clarityScore) {
            this.completeness = completeness;
            this.tokenEfficiency = tokenEfficiency;
            this.vocabularyDiversity = vocabularyDiversity;
            this.repetitionScore = repetitionScore;
            this.technicalAccuracy = technicalAccuracy;
            this.clarityScore = clarityScore;
        }
        
        public double getOverallScore() {
            return (completeness * 0.25 + tokenEfficiency * 0.15 + vocabularyDiversity * 0.15 +
                    repetitionScore * 0.15 + technicalAccuracy * 0.15 + clarityScore * 0.15);
        }
        
        // Getters
        public double getCompleteness() { return completeness; }
        public double getTokenEfficiency() { return tokenEfficiency; }
        public double getVocabularyDiversity() { return vocabularyDiversity; }
        public double getRepetitionScore() { return repetitionScore; }
        public double getTechnicalAccuracy() { return technicalAccuracy; }
        public double getClarityScore() { return clarityScore; }
    }
}
