package com.aiinterview.ml.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AOP Interceptor for automatic LLM call metric capture
 * Wraps all OpenAI service calls to collect observability metrics
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LlmCallInterceptor {
    
    private final MlMetricsCollector metricsCollector;
    private final OnlineQualitySampler qualitySampler;
    private final ObjectMapper objectMapper;
    
    /**
     * Pointcut for OpenAI service methods
     * Matches OpenAiService.chat() and other LLM call methods
     */
    @Pointcut("execution(* com.aiinterview.service.OpenAiService.chat(..)) || " +
              "execution(* com.aiinterview.service.OpenAiService.chatStream(..)) || " +
              "execution(* com.aiinterview.ml.rag.RagQuestionGenerator.callOpenAI(..)) || " +
              "execution(* com.aiinterview.ml.rag.RagAnswerEvaluator.callOpenAI(..))")
    public void openAiServiceMethods() {}
    
    /**
     * Combined pointcut for all LLM operations
     */
    @Pointcut("openAiServiceMethods()")
    public void llmOperations() {}
    
    /**
     * Around advice to capture metrics for all LLM calls
     */
    @Around("llmOperations()")
    public Object captureMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        String endpoint = determineEndpoint(joinPoint);
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();
        
        LlmCallMetric metric = LlmCallMetric.builder()
            .id(UUID.randomUUID().toString())
            .endpoint(endpoint)
            .createdAt(LocalDateTime.now())
            .build();
        
        Object result = null;
        Throwable caughtException = null;
        
        try {
            // Execute the actual method
            result = joinPoint.proceed();
            
            // Extract metrics from result
            extractMetricsFromResult(metric, result, joinPoint.getArgs());
            
        } catch (Throwable e) {
            caughtException = e;
            
            // Record error
            metric.setErrorType(e.getClass().getSimpleName());
            metric.setValidationPassed(false);
            
            log.error("LLM call failed: endpoint={}, method={}, error={}",
                     endpoint, methodName, e.getMessage());
        } finally {
            // Calculate latency
            long endTime = System.currentTimeMillis();
            metric.setLatencyMs((long) (endTime - startTime));
            
            // Sample and evaluate quality if configured
            if (qualitySampler.shouldSample(endpoint) && caughtException == null) {
                try {
                    qualitySampler.evaluateSample(metric);
                    // Quality score and validation result are set in metric by evaluateSample
                } catch (Exception e) {
                    log.warn("Failed to evaluate sample quality: {}", e.getMessage());
                }
            }
            
            // Record metric asynchronously (don't block main flow)
            try {
                metricsCollector.recordLlmCall(metric);
            } catch (Exception e) {
                log.error("Failed to record LLM metric: {}", e.getMessage());
            }
        }
        
        // Re-throw exception if one occurred
        if (caughtException != null) {
            throw caughtException;
        }
        
        return result;
    }
    
    /**
     * Determine endpoint from method signature
     */
    private String determineEndpoint(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        // Map to logical endpoint names
        if (methodName.contains("score") || methodName.contains("Score")) {
            return "interview_scoring";
        } else if (methodName.contains("generate") || methodName.contains("Generate")) {
            return "question_generation";
        } else if (methodName.contains("summarize") || methodName.contains("Summarize")) {
            return "summarization";
        } else if (methodName.contains("analyze") || methodName.contains("Analyze")) {
            return "analysis";
        } else if (className.contains("OpenAi")) {
            return "openai_" + methodName.toLowerCase();
        }
        
        return "unknown_endpoint";
    }
    
    /**
     * Extract metrics from method result and arguments
     */
    private void extractMetricsFromResult(LlmCallMetric metric, Object result, Object[] args) {
        try {
            // Try to extract from common response patterns
            if (result == null) {
                return;
            }
            
            // Check if result has OpenAI usage information
            // This would be customized based on your actual OpenAI client implementation
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                extractFromMap(metric, resultMap);
            } else {
                // Try to extract using reflection or custom logic
                extractUsingReflection(metric, result);
            }
            
            // Extract prompt information from args if available
            if (args != null && args.length > 0) {
                extractFromArgs(metric, args);
            }
            
            // Set default model if not already set
            if (metric.getModel() == null) {
                metric.setModel("gpt-3.5-turbo");  // Default model
            }
            
            // Calculate cost if tokens are known
            if (metric.getInputTokens() != null && metric.getOutputTokens() != null) {
                double cost = calculateCost(
                    metric.getModel(),
                    metric.getInputTokens(),
                    metric.getOutputTokens()
                );
                metric.setCostUsd(cost);
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract metrics from result: {}", e.getMessage());
        }
    }
    
    /**
     * Extract metrics from Map result (common OpenAI response format)
     */
    private void extractFromMap(LlmCallMetric metric, Map<String, Object> resultMap) {
        // Extract usage information
        if (resultMap.containsKey("usage")) {
            Object usage = resultMap.get("usage");
            if (usage instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> usageMap = (Map<String, Object>) usage;
                
                if (usageMap.containsKey("prompt_tokens")) {
                    metric.setInputTokens(((Number) usageMap.get("prompt_tokens")).intValue());
                }
                if (usageMap.containsKey("completion_tokens")) {
                    metric.setOutputTokens(((Number) usageMap.get("completion_tokens")).intValue());
                }
                if (usageMap.containsKey("total_tokens")) {
                    // Verify total matches
                }
            }
        }
        
        // Extract model
        if (resultMap.containsKey("model")) {
            metric.setModel((String) resultMap.get("model"));
        }
        
        // Extract response content for metadata
        if (resultMap.containsKey("choices")) {
            try {
                String metadata = objectMapper.writeValueAsString(resultMap.get("choices"));
                metric.setMetadata(metadata.substring(0, Math.min(metadata.length(), 1000)));
            } catch (Exception e) {
                log.debug("Failed to serialize choices: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Extract metrics using reflection
     */
    private void extractUsingReflection(LlmCallMetric metric, Object result) {
        try {
            Class<?> resultClass = result.getClass();
            
            // Try common getter methods
            tryExtractField(metric, result, resultClass, "getUsage", "usage");
            tryExtractField(metric, result, resultClass, "getModel", "model");
            tryExtractField(metric, result, resultClass, "getTokens", "tokens");
            
        } catch (Exception e) {
            log.debug("Failed to extract using reflection: {}", e.getMessage());
        }
    }
    
    /**
     * Try to extract a field using reflection
     */
    private void tryExtractField(LlmCallMetric metric, Object result, Class<?> resultClass,
                                 String methodName, String fieldType) {
        try {
            var method = resultClass.getMethod(methodName);
            Object value = method.invoke(result);
            
            if (value != null && "usage".equals(fieldType)) {
                // Extract token counts from usage object
                extractTokensFromUsage(metric, value);
            } else if (value != null && "model".equals(fieldType)) {
                metric.setModel(value.toString());
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, skip
        } catch (Exception e) {
            log.debug("Failed to extract field {}: {}", fieldType, e.getMessage());
        }
    }
    
    /**
     * Extract token counts from usage object
     */
    private void extractTokensFromUsage(LlmCallMetric metric, Object usage) {
        try {
            Class<?> usageClass = usage.getClass();
            
            try {
                var promptTokensMethod = usageClass.getMethod("promptTokens");
                metric.setInputTokens((Integer) promptTokensMethod.invoke(usage));
            } catch (NoSuchMethodException e) {
                // Try alternative method names
                try {
                    var inputMethod = usageClass.getMethod("getPromptTokens");
                    metric.setInputTokens((Integer) inputMethod.invoke(usage));
                } catch (NoSuchMethodException ignored) {}
            }
            
            try {
                var completionTokensMethod = usageClass.getMethod("completionTokens");
                metric.setOutputTokens((Integer) completionTokensMethod.invoke(usage));
            } catch (NoSuchMethodException e) {
                try {
                    var outputMethod = usageClass.getMethod("getCompletionTokens");
                    metric.setOutputTokens((Integer) outputMethod.invoke(usage));
                } catch (NoSuchMethodException ignored) {}
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract tokens from usage: {}", e.getMessage());
        }
    }
    
    /**
     * Extract information from method arguments
     */
    private void extractFromArgs(LlmCallMetric metric, Object[] args) {
        for (Object arg : args) {
            if (arg == null) continue;
            
            // Check for temperature parameter
            if (arg instanceof Double || arg instanceof Float) {
                double value = ((Number) arg).doubleValue();
                if (value >= 0.0 && value <= 2.0) {
                    metric.setTemperature(value);
                }
            }
            
            // Check for max tokens parameter
            if (arg instanceof Integer) {
                int value = (Integer) arg;
                if (value > 0 && value <= 16000) {
                    metric.setMaxTokens(value);
                }
            }
            
            // Check for string arguments (might be prompts)
            if (arg instanceof String) {
                String str = (String) arg;
                if (str.length() > 100 && metric.getMetadata() == null) {
                    // Store truncated prompt in metadata
                    metric.setMetadata("prompt_length=" + str.length());
                }
            }
        }
    }
    
    /**
     * Calculate cost based on OpenAI pricing
     */
    private double calculateCost(String model, int inputTokens, int outputTokens) {
        // OpenAI pricing as of 2024 (per 1K tokens)
        double inputCostPer1K;
        double outputCostPer1K;
        
        if (model == null) {
            model = "gpt-3.5-turbo";
        }
        
        if (model.contains("gpt-4-turbo") || model.contains("gpt-4-1106")) {
            inputCostPer1K = 0.01;
            outputCostPer1K = 0.03;
        } else if (model.contains("gpt-4")) {
            inputCostPer1K = 0.03;
            outputCostPer1K = 0.06;
        } else if (model.contains("gpt-3.5-turbo")) {
            inputCostPer1K = 0.0015;
            outputCostPer1K = 0.002;
        } else {
            // Default to gpt-3.5 pricing
            inputCostPer1K = 0.0015;
            outputCostPer1K = 0.002;
        }
        
        double inputCost = (inputTokens / 1000.0) * inputCostPer1K;
        double outputCost = (outputTokens / 1000.0) * outputCostPer1K;
        
        return inputCost + outputCost;
    }
}
