package com.aiinterview.monitoring;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;

/**
 * AOP aspect for automatic metrics instrumentation
 * Intercepts AI service calls to collect metrics
 */
@Aspect
@Component
public class MetricsAspect {
    
    @Autowired
    private MLMetricsCollector metricsCollector;
    
    /**
     * Intercept all AI service method calls
     * Fixed to use AiService and OpenAiService (not AIService/OpenAIService) to match actual class names
     */
    @Around("execution(* com.aiinterview.service.AiService.*(..)) || execution(* com.aiinterview.service.OpenAiService.*(..))")
    public Object trackAIServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        boolean success = false;
        
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Exception e) {
            success = false;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Record metrics
            String endpoint = "/api/ai/" + methodName;
            String modelVersion = "gpt-4o-mini"; // Should extract from actual call
            int estimatedTokens = 500; // Should calculate from actual response
            
            metricsCollector.recordRequest(endpoint, modelVersion, duration, estimatedTokens, success);
        }
    }
    
    /**
     * Intercept validation calls
     */
    @AfterReturning(pointcut = "execution(* com.aiinterview.validator.*.validate(..))", returning = "result")
    public void trackValidation(Object result) {
        boolean passed = result != null && (Boolean) result;
        metricsCollector.recordValidation("/api/validation", "validator", passed);
    }
}
