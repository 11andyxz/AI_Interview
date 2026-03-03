package com.aiinterview.ml.guardrails;

import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.OpenAiService;
import com.aiinterview.validator.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces schema-conformant structured output from LLM calls.
 *
 * Pipeline:
 * 1. Call LLM with JSON mode enabled
 * 2. Validate response against schema
 * 3. If invalid: construct repair prompt with validation errors, retry
 * 4. If still invalid after maxRetries: return fallback + log violation
 */
@Service
public class StructuredOutputEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(StructuredOutputEnforcer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenAiService openAiService;
    private final RepairPromptGenerator repairPromptGenerator;
    private final OutputConformanceTracker conformanceTracker;

    public StructuredOutputEnforcer(OpenAiService openAiService,
                                     RepairPromptGenerator repairPromptGenerator,
                                     OutputConformanceTracker conformanceTracker) {
        this.openAiService = openAiService;
        this.repairPromptGenerator = repairPromptGenerator;
        this.conformanceTracker = conformanceTracker;
    }

    /**
     * Execute an LLM call with schema enforcement and retry-with-repair.
     */
    public <T> Mono<StructuredOutput<T>> enforceSchema(List<OpenAiMessage> messages,
                                                         OutputSchemaValidator validator,
                                                         Class<T> outputType,
                                                         int maxRetries,
                                                         String endpoint,
                                                         String model) {
        long startTime = System.currentTimeMillis();

        return openAiService.chatWithJsonMode(messages)
                .flatMap(response -> {
                    String jsonContent = extractJson(response);
                    ValidationResult validation = validator.validate(jsonContent);

                    if (validation.isValid()) {
                        return parseAndReturn(jsonContent, outputType, startTime);
                    }

                    List<String> initialErrors = new ArrayList<>(validation.getErrors());
                    logger.info("Initial validation failed for {}: {}", endpoint, validation.getErrorMessage());

                    return attemptRepair(messages, jsonContent, initialErrors, validator,
                            outputType, maxRetries, 0, startTime);
                })
                .onErrorResume(error -> {
                    logger.error("LLM call failed for {}: {}", endpoint, error.getMessage());
                    long latency = System.currentTimeMillis() - startTime;
                    return Mono.just(StructuredOutput.fallback(null, List.of(error.getMessage()), latency));
                })
                .doOnNext(output -> {
                    ConformanceRecord record = output.toConformanceRecord(endpoint, model, null);
                    conformanceTracker.recordConformance(record);
                });
    }

    private <T> Mono<StructuredOutput<T>> attemptRepair(List<OpenAiMessage> originalMessages,
                                                          String malformedOutput,
                                                          List<String> initialErrors,
                                                          OutputSchemaValidator validator,
                                                          Class<T> outputType,
                                                          int maxRetries,
                                                          int attempt,
                                                          long startTime) {
        if (attempt >= maxRetries) {
            long latency = System.currentTimeMillis() - startTime;
            logger.warn("All {} repair attempts failed, using fallback", maxRetries);
            return Mono.just(StructuredOutput.fallback(null, initialErrors, latency));
        }

        List<OpenAiMessage> repairMessages = repairPromptGenerator.generateRepairPrompt(
                originalMessages, malformedOutput, initialErrors, validator.getExpectedSchema());

        return openAiService.chatWithJsonMode(repairMessages)
                .flatMap(response -> {
                    String jsonContent = extractJson(response);
                    ValidationResult validation = validator.validate(jsonContent);

                    if (validation.isValid()) {
                        long latency = System.currentTimeMillis() - startTime;
                        return parseResult(jsonContent, outputType)
                                .map(result -> StructuredOutput.repairedSuccess(
                                        result, attempt + 1, initialErrors, latency));
                    }

                    logger.info("Repair attempt {} failed: {}", attempt + 1, validation.getErrorMessage());
                    return attemptRepair(originalMessages, jsonContent, initialErrors,
                            validator, outputType, maxRetries, attempt + 1, startTime);
                });
    }

    private <T> Mono<StructuredOutput<T>> parseAndReturn(String json, Class<T> type, long startTime) {
        long latency = System.currentTimeMillis() - startTime;
        return parseResult(json, type)
                .map(result -> StructuredOutput.success(result, latency));
    }

    private <T> Mono<T> parseResult(String json, Class<T> type) {
        try {
            T result = objectMapper.readValue(json, type);
            return Mono.just(result);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to parse validated JSON: " + e.getMessage()));
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
