package com.aiinterview.ml.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates multiple validators and aggregates results
 */
@Slf4j
@Service
public class ValidationPipeline {
    
    private final List<OutputValidator> validators;
    
    @Autowired
    public ValidationPipeline(List<OutputValidator> validators) {
        // Sort validators by priority
        this.validators = validators.stream()
                .sorted(Comparator.comparingInt(OutputValidator::getPriority))
                .collect(Collectors.toList());
        
        log.info("ValidationPipeline initialized with {} validators", validators.size());
    }
    
    /**
     * Run all validators and return aggregated results
     */
    public AggregatedValidationResult validateAll(AIOutput output, ValidationContext context) {
        List<ValidationResult> results = new ArrayList<>();
        boolean allPassed = true;
        double totalScore = 0.0;
        int scoredValidators = 0;
        
        for (OutputValidator validator : validators) {
            try {
                ValidationResult result = validator.validate(output, context);
                results.add(result);
                
                if (!result.isPassed()) {
                    allPassed = false;
                    log.warn("Validator {} failed: {}", validator.getName(), result.getMessage());
                }
                
                totalScore += result.getScore();
                scoredValidators++;
                
            } catch (Exception e) {
                log.error("Validator {} threw exception", validator.getName(), e);
                ValidationResult errorResult = ValidationResult.fail(
                    validator.getName(),
                    0.0,
                    "Validator error: " + e.getMessage()
                );
                results.add(errorResult);
                allPassed = false;
            }
        }
        
        double averageScore = scoredValidators > 0 ? totalScore / scoredValidators : 0.0;
        
        return AggregatedValidationResult.builder()
                .overallPassed(allPassed)
                .averageScore(averageScore)
                .individualResults(results)
                .build();
    }
    
    /**
     * Run only critical validators (structure, toxicity)
     */
    public AggregatedValidationResult validateCriticalOnly(AIOutput output, ValidationContext context) {
        List<String> criticalValidators = Arrays.asList("StructureValidator", "ToxicityValidator");
        
        List<ValidationResult> results = new ArrayList<>();
        boolean allPassed = true;
        double totalScore = 0.0;
        int scoredValidators = 0;
        
        for (OutputValidator validator : validators) {
            if (!criticalValidators.contains(validator.getName())) {
                continue;
            }
            
            try {
                ValidationResult result = validator.validate(output, context);
                results.add(result);
                
                if (!result.isPassed()) {
                    allPassed = false;
                }
                
                totalScore += result.getScore();
                scoredValidators++;
                
            } catch (Exception e) {
                log.error("Validator {} threw exception", validator.getName(), e);
                allPassed = false;
            }
        }
        
        double averageScore = scoredValidators > 0 ? totalScore / scoredValidators : 0.0;
        
        return AggregatedValidationResult.builder()
                .overallPassed(allPassed)
                .averageScore(averageScore)
                .individualResults(results)
                .build();
    }
}
