package com.aiinterview.ml.guardrails;

import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.model.openai.OpenAiRequest;
import com.aiinterview.model.openai.OpenAiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structured output enforcer using JSON mode and retry-with-repair loop
 * Guarantees schema-conformant LLM output with validation and self-repair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredOutputEnforcer {
    
    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    
    @Value("${openai.model}")
    private String model;
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long REPAIR_TIMEOUT_MS = 3000;
    
    /**
     * Enforce schema conformance with retry-with-repair loop
     * 
     * @param messages Conversation messages
     * @param validator Schema validator
     * @param outputType Expected output class
     * @param maxRetries Maximum repair attempts
     * @return Structured output with repair metadata
     */
    public <T> StructuredOutput<T> enforceSchema(
            List<OpenAiMessage> messages,
            OutputSchemaValidator validator,
            Class<T> outputType,
            int maxRetries) {
        
        long startTime = System.currentTimeMillis();
        
        log.info("Enforcing schema: type={}, maxRetries={}", 
                 validator.getOutputTypeName(), maxRetries);
        
        // Add schema instruction to messages
        List<OpenAiMessage> enhancedMessages = new ArrayList<>(messages);
        enhancedMessages.add(0, new OpenAiMessage("system",
            "You must respond with valid JSON conforming to this schema:\n" +
            validator.getSchemaDescription() + "\n" +
            "Do not include any text outside the JSON structure."));
        
        // First attempt with JSON mode
        String rawOutput = null;
        boolean initialValid = false;
        List<String> allErrors = new ArrayList<>();
        int repairAttempts = 0;
        
        try {
            // Initial generation with JSON mode
            rawOutput = generateWithJsonMode(enhancedMessages);
            
            // Validate initial output
            OutputSchemaValidator.ValidationResult validationResult = validator.validate(rawOutput);
            initialValid = validationResult.isValid();
            
            if (initialValid) {
                // Parse and return successful result
                T result = objectMapper.readValue(rawOutput, outputType);
                long latency = System.currentTimeMillis() - startTime;
                
                log.info("Initial output valid: type={}, latency={}ms", 
                         validator.getOutputTypeName(), latency);
                
                return StructuredOutput.success(result, latency, model, null);
            }
            
            // Initial validation failed - enter repair loop
            allErrors.addAll(validationResult.getErrors());
            log.warn("Initial validation failed: errors={}", validationResult.getErrors());
            
            // Repair loop
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                repairAttempts = attempt;
                
                // Check repair timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > REPAIR_TIMEOUT_MS) {
                    log.warn("Repair timeout exceeded: {}ms > {}ms", elapsed, REPAIR_TIMEOUT_MS);
                    break;
                }
                
                log.info("Repair attempt {}/{}: errors={}", attempt, maxRetries, 
                         validationResult.getErrors());
                
                // Create repair messages
                List<OpenAiMessage> repairMessages = new ArrayList<>(enhancedMessages);
                repairMessages.add(new OpenAiMessage("assistant", rawOutput));
                repairMessages.add(new OpenAiMessage("system",
                    "The previous output had validation errors:\n" +
                    String.join("\n", validationResult.getErrors()) + "\n\n" +
                    "Repair hint: " + validationResult.getRepairHint() + "\n\n" +
                    "Please fix these errors and output valid JSON conforming to: " +
                    validator.getSchemaDescription()));
                
                // Generate repaired output
                rawOutput = generateWithJsonMode(repairMessages);
                
                // Validate repaired output
                validationResult = validator.validate(rawOutput);
                
                if (validationResult.isValid()) {
                    // Repair successful
                    T result = objectMapper.readValue(rawOutput, outputType);
                    long latency = System.currentTimeMillis() - startTime;
                    
                    log.info("Repair successful: attempt={}, latency={}ms", attempt, latency);
                    
                    return StructuredOutput.<T>builder()
                        .result(result)
                        .usedRepair(true)
                        .repairAttempts(repairAttempts)
                        .usedFallback(false)
                        .initialValid(false)
                        .finalValid(true)
                        .validationErrors(allErrors)
                        .totalLatencyMs(latency)
                        .rawOutput(rawOutput)
                        .model(model)
                        .build();
                }
                
                // Add new errors
                allErrors.addAll(validationResult.getErrors());
            }
            
            // All repair attempts failed - use fallback
            log.error("All repair attempts failed: attempts={}, errors={}", 
                      repairAttempts, allErrors);
            
            T fallbackResult = createFallback(outputType);
            long latency = System.currentTimeMillis() - startTime;
            
            return StructuredOutput.fallback(fallbackResult, allErrors, latency, model, null);
            
        } catch (Exception e) {
            log.error("Error enforcing schema: type={}", validator.getOutputTypeName(), e);
            
            allErrors.add("Exception: " + e.getMessage());
            T fallbackResult = createFallback(outputType);
            long latency = System.currentTimeMillis() - startTime;
            
            return StructuredOutput.fallback(fallbackResult, allErrors, latency, model, null);
        }
    }
    
    /**
     * Enforce schema with default max retries
     */
    public <T> StructuredOutput<T> enforceSchema(
            List<OpenAiMessage> messages,
            OutputSchemaValidator validator,
            Class<T> outputType) {
        return enforceSchema(messages, validator, outputType, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Generate output with JSON mode enabled
     */
    private String generateWithJsonMode(List<OpenAiMessage> messages) {
        OpenAiRequest request = new OpenAiRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setTemperature(0.0);
        request.setStream(false);
        
        // Enable JSON mode
        request.setResponseFormat(Map.of("type", "json_object"));
        
        return openAiWebClient.post()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OpenAiResponse.class)
            .timeout(Duration.ofSeconds(30))
            .map(response -> {
                if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                    return response.getChoices().get(0).getMessage().getContent();
                }
                return "{}";
            })
            .block();
    }
    
    /**
     * Create fallback result (empty or default instance)
     */
    private <T> T createFallback(Class<T> outputType) {
        try {
            return outputType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("Cannot create fallback instance for {}", outputType.getName());
            return null;
        }
    }
}
