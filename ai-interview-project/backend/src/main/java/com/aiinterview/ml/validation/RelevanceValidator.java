package com.aiinterview.ml.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates question/answer relevance to role and tech stack
 * Priority: 4
 */
@Slf4j
@Component
public class RelevanceValidator implements OutputValidator {
    
    // Tech stack keywords by category
    private static final Map<String, Set<String>> TECH_KEYWORDS = Map.of(
        "java", Set.of("java", "spring", "hibernate", "maven", "junit", "jvm", "jdbc"),
        "python", Set.of("python", "django", "flask", "pandas", "numpy", "pytest"),
        "javascript", Set.of("javascript", "react", "vue", "angular", "node", "express", "typescript"),
        "database", Set.of("sql", "mysql", "postgresql", "mongodb", "redis", "database"),
        "devops", Set.of("docker", "kubernetes", "jenkins", "ci/cd", "aws", "azure", "gcp"),
        "frontend", Set.of("html", "css", "react", "vue", "angular", "webpack", "responsive"),
        "backend", Set.of("api", "rest", "microservice", "spring", "express", "django")
    );
    
    // Role-specific keywords
    private static final Map<String, Set<String>> ROLE_KEYWORDS = Map.of(
        "backend", Set.of("api", "database", "server", "microservice", "rest", "scalability"),
        "frontend", Set.of("ui", "ux", "responsive", "component", "state management", "css"),
        "fullstack", Set.of("frontend", "backend", "database", "api", "deployment"),
        "devops", Set.of("deployment", "ci/cd", "infrastructure", "monitoring", "automation"),
        "data", Set.of("data", "analysis", "pipeline", "etl", "analytics", "visualization")
    );
    
    @Override
    public ValidationResult validate(AIOutput output, ValidationContext context) {
        if (context.getRequestType() != ValidationContext.RequestType.QUESTION_GENERATION &&
            context.getRequestType() != ValidationContext.RequestType.RESUME_ANALYSIS) {
            // Relevance validation only applies to questions and resume analysis
            return ValidationResult.pass(getName(), 1.0, "N/A for this request type");
        }
        
        String content = extractContent(output);
        if (content == null || content.trim().isEmpty()) {
            return ValidationResult.fail(getName(), 0.0, "No content to validate");
        }
        
        String lowerContent = content.toLowerCase();
        double relevanceScore = 0.0;
        List<String> matchedKeywords = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 1. Check tech stack relevance
        if (context.getTechStack() != null && context.getTechStack().length > 0) {
            int techMatches = 0;
            int totalTechTerms = 0;
            
            for (String tech : context.getTechStack()) {
                Set<String> keywords = TECH_KEYWORDS.get(tech.toLowerCase());
                if (keywords != null) {
                    totalTechTerms += keywords.size();
                    for (String keyword : keywords) {
                        if (lowerContent.contains(keyword)) {
                            techMatches++;
                            matchedKeywords.add(keyword);
                        }
                    }
                }
            }
            
            if (totalTechTerms > 0) {
                double techRelevance = (double) techMatches / totalTechTerms;
                relevanceScore += techRelevance * 0.6; // 60% weight
                
                if (techMatches == 0) {
                    warnings.add("No tech stack keywords found");
                }
            }
        } else {
            relevanceScore += 0.6; // No tech stack specified - give benefit of doubt
        }
        
        // 2. Check role relevance
        if (context.getTargetRole() != null && !context.getTargetRole().isEmpty()) {
            String roleLower = context.getTargetRole().toLowerCase();
            int roleMatches = 0;
            int totalRoleTerms = 0;
            
            for (Map.Entry<String, Set<String>> entry : ROLE_KEYWORDS.entrySet()) {
                if (roleLower.contains(entry.getKey())) {
                    Set<String> roleKeywords = entry.getValue();
                    totalRoleTerms = roleKeywords.size();
                    
                    for (String keyword : roleKeywords) {
                        if (lowerContent.contains(keyword)) {
                            roleMatches++;
                            matchedKeywords.add(keyword);
                        }
                    }
                }
            }
            
            if (totalRoleTerms > 0) {
                double roleRelevance = (double) roleMatches / totalRoleTerms;
                relevanceScore += roleRelevance * 0.4; // 40% weight
                
                if (roleMatches == 0) {
                    warnings.add("No role-specific keywords found");
                }
            } else {
                relevanceScore += 0.4; // No specific role matched - give benefit of doubt
            }
        } else {
            relevanceScore += 0.4; // No role specified
        }
        
        // Ensure score is between 0 and 1
        relevanceScore = Math.max(0.0, Math.min(1.0, relevanceScore));
        
        // Pass threshold: 0.5 (at least some relevance)
        boolean passed = relevanceScore >= 0.5;
        
        String message = passed ? 
            "Content is relevant to role and tech stack" :
            "Low relevance to specified role/tech stack";
        
        ValidationResult result = ValidationResult.builder()
                .validatorName(getName())
                .score(relevanceScore)
                .passed(passed)
                .message(message)
                .warnings(warnings)
                .build();
        
        if (!matchedKeywords.isEmpty()) {
            result.addDetail("Matched keywords: " + String.join(", ", matchedKeywords));
        }
        
        return result;
    }
    
    private String extractContent(AIOutput output) {
        if (!output.isParsed()) {
            return output.getRawResponse();
        }
        
        // Try to extract question or main content
        Object question = output.getField("question");
        if (question != null) {
            return question.toString();
        }
        
        Object questions = output.getField("interviewQuestions");
        if (questions != null) {
            return questions.toString();
        }
        
        return output.getRawResponse();
    }
    
    @Override
    public String getName() {
        return "RelevanceValidator";
    }
    
    @Override
    public int getPriority() {
        return 4;
    }
}
