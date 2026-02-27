package com.aiinterview.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Output Quality Monitor
 * Monitors AI response quality metrics: response length, vocabulary diversity, n-gram frequency distribution
 */
@Component
public class OutputQualityMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(OutputQualityMonitor.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Record output quality metrics for the response
     */
    public void recordOutputQuality(String response, String modelVersion, String endpoint) {
        try {
            // 1. Response length
            int responseLength = response.length();
            recordMetric("response_length", responseLength, modelVersion, endpoint);
            
            // 2. Vocabulary diversity (unique words / total words)
            double vocabularyDiversity = calculateVocabularyDiversity(response);
            recordMetric("vocabulary_diversity", vocabularyDiversity, modelVersion, endpoint);
            
            // 3. Average word length
            double avgWordLength = calculateAverageWordLength(response);
            recordMetric("avg_word_length", avgWordLength, modelVersion, endpoint);
            
            // 4. Sentence count
            int sentenceCount = countSentences(response);
            recordMetric("sentence_count", sentenceCount, modelVersion, endpoint);
            
            // 5. Average sentence length
            double avgSentenceLength = sentenceCount > 0 ? (double) responseLength / sentenceCount : 0;
            recordMetric("avg_sentence_length", avgSentenceLength, modelVersion, endpoint);
            
            // 6. Record n-gram frequency (for drift detection)
            Map<String, Integer> bigrams = extractNGrams(response, 2);
            Map<String, Integer> trigrams = extractNGrams(response, 3);
            
            // Store n-gram distribution in tags JSON field
            Map<String, Object> tags = new HashMap<>();
            tags.put("top_bigrams", getTopNGrams(bigrams, 10));
            tags.put("top_trigrams", getTopNGrams(trigrams, 10));
            
            String tagsJson = objectMapper.writeValueAsString(tags);
            
            String sql = "INSERT INTO ai_metrics_log (timestamp, metric_name, metric_value, model_version, endpoint, tags) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, 
                Instant.now().toString(),
                "ngram_distribution",
                0.0,
                modelVersion,
                endpoint,
                tagsJson
            );
            
        } catch (Exception e) {
            log.error("Failed to record output quality metrics", e);
        }
    }
    
    /**
     * Calculate vocabulary diversity (Type-Token Ratio)
     */
    private double calculateVocabularyDiversity(String text) {
        String[] words = text.toLowerCase()
            .replaceAll("[^a-z0-9\\s\\u4e00-\\u9fa5]", " ")
            .split("\\s+");
        
        if (words.length == 0) return 0.0;
        
        Set<String> uniqueWords = Arrays.stream(words)
            .filter(w -> !w.isEmpty())
            .collect(Collectors.toSet());
        
        return (double) uniqueWords.size() / words.length;
    }
    
    /**
     * Calculate average word length
     */
    private double calculateAverageWordLength(String text) {
        String[] words = text.replaceAll("[^a-z0-9\\s\\u4e00-\\u9fa5]", " ")
            .split("\\s+");
        
        if (words.length == 0) return 0.0;
        
        int totalLength = Arrays.stream(words)
            .filter(w -> !w.isEmpty())
            .mapToInt(String::length)
            .sum();
        
        long validWords = Arrays.stream(words)
            .filter(w -> !w.isEmpty())
            .count();
        
        return validWords > 0 ? (double) totalLength / validWords : 0.0;
    }
    
    /**
     * Count sentences
     */
    private int countSentences(String text) {
        // Split by period, question mark, exclamation mark (both English and Chinese)
        String[] sentences = text.split("[.!?。！？]+");
        return (int) Arrays.stream(sentences)
            .filter(s -> !s.trim().isEmpty())
            .count();
    }
    
    /**
     * Extract n-grams
     */
    private Map<String, Integer> extractNGrams(String text, int n) {
        String[] words = text.toLowerCase()
            .replaceAll("[^a-z0-9\\s\\u4e00-\\u9fa5]", " ")
            .split("\\s+");
        
        Map<String, Integer> ngramCounts = new HashMap<>();
        
        for (int i = 0; i <= words.length - n; i++) {
            if (Arrays.stream(words, i, i + n).allMatch(w -> !w.isEmpty())) {
                String ngram = String.join(" ", Arrays.copyOfRange(words, i, i + n));
                ngramCounts.put(ngram, ngramCounts.getOrDefault(ngram, 0) + 1);
            }
        }
        
        return ngramCounts;
    }
    
    /**
     * Get top N n-grams by frequency
     */
    private List<Map<String, Object>> getTopNGrams(Map<String, Integer> ngramCounts, int topN) {
        return ngramCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(topN)
            .map(entry -> {
                Map<String, Object> item = new HashMap<>();
                item.put("ngram", entry.getKey());
                item.put("count", entry.getValue());
                return item;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Check output quality drift
     * Compare recent 1 day with past 30 days metrics
     */
    public Map<String, Object> checkOutputQualityDrift() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Average response length for past 30 days
            String sql30Days = "SELECT AVG(metric_value) as avg_value FROM ai_metrics_log " +
                              "WHERE metric_name = 'response_length' " +
                              "AND timestamp >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
            
            Double avg30Days = jdbcTemplate.queryForObject(sql30Days, Double.class);
            
            // Average response length for recent 1 day
            String sql1Day = "SELECT AVG(metric_value) as avg_value FROM ai_metrics_log " +
                            "WHERE metric_name = 'response_length' " +
                            "AND timestamp >= DATE_SUB(NOW(), INTERVAL 1 DAY)";
            
            Double avg1Day = jdbcTemplate.queryForObject(sql1Day, Double.class);
            
            if (avg30Days != null && avg1Day != null && avg30Days > 0) {
                double changePercent = Math.abs(avg1Day - avg30Days) / avg30Days;
                
                result.put("metric", "response_length");
                result.put("baseline_30d", avg30Days);
                result.put("current_1d", avg1Day);
                result.put("change_percent", changePercent);
                result.put("drift_detected", changePercent > 0.3); // Over 30% is considered drift
                
                if (changePercent > 0.3) {
                    log.warn("Output quality drift detected: response_length changed by {}%", 
                            changePercent * 100);
                }
            }
            
            // Check vocabulary diversity drift
            checkMetricDrift("vocabulary_diversity", 0.2, result);
            
        } catch (Exception e) {
            log.error("Failed to check output quality drift", e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Check drift for a single metric
     */
    private void checkMetricDrift(String metricName, double threshold, Map<String, Object> result) {
        try {
            String sql30Days = "SELECT AVG(metric_value) FROM ai_metrics_log " +
                              "WHERE metric_name = ? AND timestamp >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
            
            String sql1Day = "SELECT AVG(metric_value) FROM ai_metrics_log " +
                            "WHERE metric_name = ? AND timestamp >= DATE_SUB(NOW(), INTERVAL 1 DAY)";
            
            Double avg30Days = jdbcTemplate.queryForObject(sql30Days, Double.class, metricName);
            Double avg1Day = jdbcTemplate.queryForObject(sql1Day, Double.class, metricName);
            
            if (avg30Days != null && avg1Day != null && avg30Days > 0) {
                double changePercent = Math.abs(avg1Day - avg30Days) / avg30Days;
                
                if (changePercent > threshold) {
                    result.put(metricName + "_drift", true);
                    result.put(metricName + "_change", changePercent);
                    log.warn("{} drift detected: changed by {}%", metricName, changePercent * 100);
                }
            }
        } catch (Exception e) {
            log.error("Failed to check drift for {}", metricName, e);
        }
    }
    
    /**
     * Record a single metric
     */
    private void recordMetric(String metricName, double value, String modelVersion, String endpoint) {
        try {
            String sql = "INSERT INTO ai_metrics_log (timestamp, metric_name, metric_value, model_version, endpoint) " +
                        "VALUES (?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, 
                Instant.now().toString(),
                metricName,
                value,
                modelVersion,
                endpoint
            );
        } catch (Exception e) {
            log.error("Failed to record metric {}", metricName, e);
        }
    }
}
