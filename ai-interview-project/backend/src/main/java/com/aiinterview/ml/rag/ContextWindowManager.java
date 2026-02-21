package com.aiinterview.ml.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Context window management for RAG
 * Controls token budget and performs context compression
 */
@Slf4j
@Service
public class ContextWindowManager {
    
    // Token estimation: ~4 characters per token
    private static final double CHARS_PER_TOKEN = 4.0;
    
    /**
     * Select context within token budget
     * 
     * @param rankedResults Reranked search results
     * @param maxTokenBudget Maximum token budget
     * @param relevanceThreshold Minimum relevance score to include
     * @return Selected context snippets
     */
    public List<SearchResult> selectContext(List<SearchResult> rankedResults, int maxTokenBudget, double relevanceThreshold) {
        log.debug("Selecting context from {} results with budget {} tokens, threshold {}", 
            rankedResults.size(), maxTokenBudget, relevanceThreshold);
        
        if (rankedResults.isEmpty()) {
            return List.of();
        }
        
        List<SearchResult> selectedResults = new ArrayList<>();
        int totalTokens = 0;
        
        // Add results in order of relevance until budget exhausted
        for (SearchResult result : rankedResults) {
            // Filter by relevance threshold
            if (result.getScore() < relevanceThreshold) {
                log.debug("Skipping result with score {} below threshold {}", result.getScore(), relevanceThreshold);
                continue;
            }
            
            int resultTokens = estimateTokens(result.getContent());
            
            if (totalTokens + resultTokens <= maxTokenBudget) {
                selectedResults.add(result.toBuilder()
                    .tokenCount(resultTokens)
                    .build());
                totalTokens += resultTokens;
            } else {
                // Check if we can fit a compressed version
                int remainingTokens = maxTokenBudget - totalTokens;
                if (remainingTokens > 50) { // Minimum useful context
                    String compressed = compressToTokenLimit(result.getContent(), remainingTokens);
                    int compressedTokens = estimateTokens(compressed);
                    
                    selectedResults.add(result.toBuilder()
                        .content(compressed)
                        .tokenCount(compressedTokens)
                        .build());
                    totalTokens += compressedTokens;
                }
                break;
            }
        }
        
        log.info("Selected {} contexts, total tokens: {}/{}", selectedResults.size(), totalTokens, maxTokenBudget);
        return selectedResults;
    }
    
    /**
     * Compress context to fit within token budget
     * 
     * @param results Search results to compress
     * @param targetTokens Target token count
     * @return Compressed context as single string
     */
    public String compressContext(List<SearchResult> results, int targetTokens) {
        log.debug("Compressing {} results to {} tokens", results.size(), targetTokens);
        
        if (results.isEmpty()) {
            return "";
        }
        
        // Combine all content
        String combinedContext = results.stream()
            .map(SearchResult::getContent)
            .collect(Collectors.joining("\n\n"));
        
        int currentTokens = estimateTokens(combinedContext);
        
        if (currentTokens <= targetTokens) {
            return combinedContext;
        }
        
        // Apply compression strategies in order
        String compressed = combinedContext;
        
        // Strategy 1: Remove redundant whitespace
        compressed = normalizeWhitespace(compressed);
        if (estimateTokens(compressed) <= targetTokens) {
            return compressed;
        }
        
        // Strategy 2: Extract key sentences
        compressed = extractKeySentences(compressed, targetTokens);
        if (estimateTokens(compressed) <= targetTokens) {
            return compressed;
        }
        
        // Strategy 3: Truncate to fit
        compressed = compressToTokenLimit(compressed, targetTokens);
        
        log.info("Compressed context: {} -> {} tokens", currentTokens, estimateTokens(compressed));
        return compressed;
    }
    
    /**
     * Estimate token count from text
     * Uses simple heuristic: ~4 chars per token
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
    
    /**
     * Normalize whitespace in text
     */
    private String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
    
    /**
     * Extract key sentences based on relevance heuristics
     */
    private String extractKeySentences(String text, int maxTokens) {
        // Split into sentences
        String[] sentences = text.split("(?<=[.!?])\\s+");
        
        if (sentences.length == 0) {
            return text;
        }
        
        // Score sentences
        List<ScoredSentence> scored = new ArrayList<>();
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            double score = scoreSentence(sentence, i, sentences.length);
            scored.add(new ScoredSentence(sentence, score, i));
        }
        
        // Sort by score (descending)
        scored.sort(Comparator.comparingDouble(ScoredSentence::getScore).reversed());
        
        // Select top sentences within budget
        List<ScoredSentence> selected = new ArrayList<>();
        int tokens = 0;
        
        for (ScoredSentence ss : scored) {
            int sentenceTokens = estimateTokens(ss.getText());
            if (tokens + sentenceTokens <= maxTokens) {
                selected.add(ss);
                tokens += sentenceTokens;
            }
        }
        
        // Restore original order
        selected.sort(Comparator.comparingInt(ScoredSentence::getPosition));
        
        return selected.stream()
            .map(ScoredSentence::getText)
            .collect(Collectors.joining(" "));
    }
    
    /**
     * Score sentence relevance
     */
    private double scoreSentence(String sentence, int position, int totalSentences) {
        double score = 0.0;
        
        // Position bonus (first and last sentences often important)
        if (position == 0) {
            score += 2.0;
        } else if (position == totalSentences - 1) {
            score += 1.5;
        } else if (position < totalSentences * 0.3) {
            score += 1.0;
        }
        
        // Length bonus (prefer moderate length)
        int length = sentence.length();
        if (length > 50 && length < 200) {
            score += 1.0;
        } else if (length >= 200 && length < 400) {
            score += 0.5;
        }
        
        // Keyword bonus (common important terms)
        String lower = sentence.toLowerCase();
        if (lower.contains("important") || lower.contains("key") || 
            lower.contains("main") || lower.contains("primary")) {
            score += 1.0;
        }
        
        // Question/definition bonus
        if (sentence.contains("?") || lower.contains("is defined as") || 
            lower.contains("refers to")) {
            score += 0.5;
        }
        
        return score;
    }
    
    /**
     * Hard truncate text to token limit
     */
    private String compressToTokenLimit(String text, int maxTokens) {
        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        
        if (text.length() <= maxChars) {
            return text;
        }
        
        // Try to truncate at sentence boundary
        String truncated = text.substring(0, maxChars);
        int lastPeriod = truncated.lastIndexOf('.');
        
        if (lastPeriod > maxChars * 0.7) { // Only if we don't lose too much
            return text.substring(0, lastPeriod + 1);
        }
        
        return truncated + "...";
    }
    
    /**
     * Sentence with relevance score
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ScoredSentence {
        private String text;
        private double score;
        private int position;
    }
}
