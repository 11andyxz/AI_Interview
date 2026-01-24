package com.aiinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Service for collecting high-quality fine-tuning data from validated AI interactions.
 * Supports async logging, JSONL export in OpenAI format, and PII sanitization.
 */
@Service
public class FineTuneDataCollector {

    private static final String DEFAULT_EXPORT_DIR = "eval/fine_tune_data";
    private static final Pattern PII_EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern PII_PHONE_PATTERN = Pattern.compile("\\b\\d{3}-\\d{3}-\\d{4}\\b|\\b\\d{10}\\b|\\(\\d{3}\\)\\s*\\d{3}-\\d{4}");
    private static final Pattern PII_SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Collect a fine-tuning data point asynchronously
     */
    public CompletableFuture<Void> collectDataPoint(
            String prompt,
            String completion,
            double validationScore,
            Integer userRating,
            String endpoint,
            Map<String, Object> metadata) {
        
        return CompletableFuture.runAsync(() -> {
            try {
                // Only collect high-quality interactions
                if (validationScore >= 0.95 && !isRetryOrFallback(metadata)) {
                    
                    // Sanitize PII
                    String sanitizedPrompt = sanitizePII(prompt);
                    String sanitizedCompletion = sanitizePII(completion);
                    
                    // Create fine-tuning data point
                    FineTuningDataPoint dataPoint = new FineTuningDataPoint(
                            sanitizedPrompt,
                            sanitizedCompletion,
                            validationScore,
                            userRating,
                            endpoint,
                            Instant.now().toString(),
                            metadata
                    );
                    
                    // Write to JSONL file
                    appendToJsonlFile(dataPoint, endpoint);
                    
                } else {
                    // Log rejection reason
                    logRejection(validationScore, metadata);
                }
                
            } catch (Exception e) {
                // Best effort logging - don't fail the main request
                System.err.println("Error collecting fine-tune data: " + e.getMessage());
            }
        });
    }

    /**
     * Export collected data in OpenAI fine-tuning format
     */
    public String exportForFineTuning(String endpoint, String version) throws IOException {
        String inputFile = getDataFilePath(endpoint);
        String outputFile = String.format("%s/fine_tune_%s_%s.jsonl", DEFAULT_EXPORT_DIR, version, endpoint);
        
        ensureDirectoryExists(Paths.get(outputFile).getParent());
        
        if (!Files.exists(Paths.get(inputFile))) {
            throw new IOException("No data file found for endpoint: " + endpoint);
        }
        
        // Convert to OpenAI format and write
        try (FileWriter writer = new FileWriter(outputFile)) {
            Files.lines(Paths.get(inputFile))
                    .forEach(line -> {
                        try {
                            FineTuningDataPoint dataPoint = objectMapper.readValue(line, FineTuningDataPoint.class);
                            OpenAIFineTuneFormat openAIFormat = convertToOpenAIFormat(dataPoint);
                            writer.write(objectMapper.writeValueAsString(openAIFormat) + "\n");
                        } catch (Exception e) {
                            System.err.println("Error processing line: " + e.getMessage());
                        }
                    });
        }
        
        return outputFile;
    }

    /**
     * Get dataset statistics for monitoring
     */
    public DatasetStats getDatasetStats(String endpoint) throws IOException {
        String dataFile = getDataFilePath(endpoint);
        
        if (!Files.exists(Paths.get(dataFile))) {
            return new DatasetStats(endpoint, 0, 0.0, 0.0, 0, 0, 0);
        }
        
        long totalCount = 0;
        double totalValidationScore = 0.0;
        double totalUserRating = 0.0;
        int hasUserRatingCount = 0;
        int taskTypeCount = 0;
        int difficultyCount = 0;
        
        for (String line : Files.readAllLines(Paths.get(dataFile))) {
            try {
                FineTuningDataPoint dataPoint = objectMapper.readValue(line, FineTuningDataPoint.class);
                totalCount++;
                totalValidationScore += dataPoint.validationScore;
                
                if (dataPoint.userRating != null) {
                    totalUserRating += dataPoint.userRating;
                    hasUserRatingCount++;
                }
                
                if (dataPoint.metadata.containsKey("task_type")) {
                    taskTypeCount++;
                }
                
                if (dataPoint.metadata.containsKey("difficulty")) {
                    difficultyCount++;
                }
                
            } catch (Exception e) {
                System.err.println("Error parsing data point: " + e.getMessage());
            }
        }
        
        return new DatasetStats(
                endpoint,
                totalCount,
                totalCount > 0 ? totalValidationScore / totalCount : 0.0,
                hasUserRatingCount > 0 ? totalUserRating / hasUserRatingCount : 0.0,
                hasUserRatingCount,
                taskTypeCount,
                difficultyCount
        );
    }

    private String sanitizePII(String text) {
        if (text == null) return null;
        
        String sanitized = text;
        
        // Replace emails
        sanitized = PII_EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL_REDACTED]");
        
        // Replace phone numbers
        sanitized = PII_PHONE_PATTERN.matcher(sanitized).replaceAll("[PHONE_REDACTED]");
        
        // Replace SSNs
        sanitized = PII_SSN_PATTERN.matcher(sanitized).replaceAll("[SSN_REDACTED]");
        
        // Replace common name patterns (basic implementation)
        sanitized = sanitized.replaceAll("\\bMr\\.\\s+[A-Z][a-z]+\\b", "[NAME_REDACTED]");
        sanitized = sanitized.replaceAll("\\bMs\\.\\s+[A-Z][a-z]+\\b", "[NAME_REDACTED]");
        sanitized = sanitized.replaceAll("\\bDr\\.\\s+[A-Z][a-z]+\\b", "[NAME_REDACTED]");
        
        return sanitized;
    }

    private boolean isRetryOrFallback(Map<String, Object> metadata) {
        if (metadata == null) return false;
        
        Boolean isRetry = (Boolean) metadata.get("is_retry");
        Boolean isFallback = (Boolean) metadata.get("is_fallback");
        
        return Boolean.TRUE.equals(isRetry) || Boolean.TRUE.equals(isFallback);
    }

    private void appendToJsonlFile(FineTuningDataPoint dataPoint, String endpoint) throws IOException {
        String filePath = getDataFilePath(endpoint);
        Path path = Paths.get(filePath);
        
        ensureDirectoryExists(path.getParent());
        
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(objectMapper.writeValueAsString(dataPoint) + "\n");
        }
    }

    private void logRejection(double validationScore, Map<String, Object> metadata) {
        // Simple logging for rejected samples
        System.out.println(String.format("Rejected fine-tune sample: validation_score=%.3f, metadata=%s", 
                validationScore, metadata));
    }

    private String getDataFilePath(String endpoint) {
        return String.format("%s/%s_raw.jsonl", DEFAULT_EXPORT_DIR, endpoint);
    }

    private void ensureDirectoryExists(Path directory) throws IOException {
        if (directory != null && !Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    private OpenAIFineTuneFormat convertToOpenAIFormat(FineTuningDataPoint dataPoint) {
        return new OpenAIFineTuneFormat(
                dataPoint.prompt,
                dataPoint.completion
        );
    }

    // Data classes
    public static class FineTuningDataPoint {
        public String prompt;
        public String completion;
        public double validationScore;
        public Integer userRating;
        public String endpoint;
        public String timestamp;
        public Map<String, Object> metadata;

        public FineTuningDataPoint() {}

        public FineTuningDataPoint(String prompt, String completion, double validationScore, 
                                 Integer userRating, String endpoint, String timestamp, 
                                 Map<String, Object> metadata) {
            this.prompt = prompt;
            this.completion = completion;
            this.validationScore = validationScore;
            this.userRating = userRating;
            this.endpoint = endpoint;
            this.timestamp = timestamp;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
    }

    public static class OpenAIFineTuneFormat {
        public String prompt;
        public String completion;

        public OpenAIFineTuneFormat() {}

        public OpenAIFineTuneFormat(String prompt, String completion) {
            this.prompt = prompt;
            this.completion = completion;
        }
    }

    public static class DatasetStats {
        public String endpoint;
        public long totalSamples;
        public double avgValidationScore;
        public double avgUserRating;
        public int samplesWithRating;
        public int samplesWithTaskType;
        public int samplesWithDifficulty;

        public DatasetStats() {}

        public DatasetStats(String endpoint, long totalSamples, double avgValidationScore, 
                          double avgUserRating, int samplesWithRating, int samplesWithTaskType, 
                          int samplesWithDifficulty) {
            this.endpoint = endpoint;
            this.totalSamples = totalSamples;
            this.avgValidationScore = avgValidationScore;
            this.avgUserRating = avgUserRating;
            this.samplesWithRating = samplesWithRating;
            this.samplesWithTaskType = samplesWithTaskType;
            this.samplesWithDifficulty = samplesWithDifficulty;
        }
    }
}