package com.aiinterview.ml.optimization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Token optimization service to reduce API costs
 * 
 * Techniques:
 * 1. Prompt compression - Remove redundant instructions
 * 2. Context window management - Sliding window for multi-turn
 * 3. Output length control - Strict max_tokens per use case
 */
@Slf4j
@Service
public class TokenOptimizer {
    
    private static final int MAX_CONTEXT_TURNS = 5;
    private static final int SUMMARY_TRIGGER_TURNS = 10;
    
    // Common redundant phrases to remove
    private static final Pattern[] REDUNDANT_PATTERNS = {
        Pattern.compile("\\s+"), // Multiple spaces
        Pattern.compile("Please note that\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("It is important to\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("As mentioned (before|earlier|previously),?\\s+", Pattern.CASE_INSENSITIVE)
    };
    
    /**
     * Compress prompt by removing redundant text
     * 
     * @param prompt Original prompt
     * @return Compressed prompt
     */
    public String compressPrompt(String prompt) {
        String compressed = prompt;
        
        // Remove redundant patterns
        for (Pattern pattern : REDUNDANT_PATTERNS) {
            compressed = pattern.matcher(compressed).replaceAll(" ");
        }
        
        // Normalize whitespace
        compressed = compressed.trim().replaceAll("\\s+", " ");
        
        int originalLength = prompt.length();
        int compressedLength = compressed.length();
        int savedChars = originalLength - compressedLength;
        
        if (savedChars > 0) {
            log.debug("Compressed prompt: {} chars -> {} chars (saved {})", 
                     originalLength, compressedLength, savedChars);
        }
        
        return compressed;
    }
    
    /**
     * Optimize prompt with conversation history using sliding window
     * 
     * @param prompt Current prompt
     * @param history Conversation history
     * @return Optimized prompt with managed context
     */
    public String compressPromptWithHistory(String prompt, ConversationHistory history) {
        if (history == null || history.getTurnCount() == 0) {
            return compressPrompt(prompt);
        }
        
        StringBuilder optimized = new StringBuilder();
        
        // If conversation is long, summarize older context
        if (history.getTurnCount() > SUMMARY_TRIGGER_TURNS) {
            String summary = summarizeOldContext(history);
            optimized.append("[Previous context summary: ").append(summary).append("]\n\n");
            
            // Keep only last 5 turns in full
            List<ConversationHistory.Turn> recentTurns = history.getLastNTurns(MAX_CONTEXT_TURNS);
            for (ConversationHistory.Turn turn : recentTurns) {
                optimized.append(turn.getRole()).append(": ")
                         .append(turn.getContent()).append("\n");
            }
        } else {
            // Keep all turns for short conversations
            for (ConversationHistory.Turn turn : history.getTurns()) {
                optimized.append(turn.getRole()).append(": ")
                         .append(turn.getContent()).append("\n");
            }
        }
        
        // Add current prompt
        optimized.append("\nCurrent: ").append(compressPrompt(prompt));
        
        log.debug("Optimized conversation context: {} turns -> {} chars", 
                 history.getTurnCount(), optimized.length());
        
        return optimized.toString();
    }
    
    /**
     * Summarize old conversation context
     */
    private String summarizeOldContext(ConversationHistory history) {
        // Get all turns except last 5
        int oldTurnsCount = history.getTurnCount() - MAX_CONTEXT_TURNS;
        if (oldTurnsCount <= 0) {
            return "";
        }
        
        List<ConversationHistory.Turn> oldTurns = history.getTurns()
            .subList(0, oldTurnsCount);
        
        // Simple summarization: extract key points
        StringBuilder summary = new StringBuilder();
        summary.append("Discussed ").append(oldTurnsCount).append(" questions covering ");
        
        // Extract topics (simplified - in production, use NLP)
        long userTurns = oldTurns.stream().filter(t -> "user".equals(t.getRole())).count();
        summary.append(userTurns).append(" topics");
        
        return summary.toString();
    }
    
    /**
     * Calculate optimal max_tokens for a request type
     * 
     * @param type Request type
     * @return Recommended max_tokens value
     */
    public int calculateOptimalMaxTokens(RequestType type) {
        return type.getRecommendedMaxTokens();
    }
    
    /**
     * Calculate optimal max_tokens based on input length
     * 
     * For longer inputs, may need more output tokens
     */
    public int calculateOptimalMaxTokens(RequestType type, int inputTokens) {
        int baseTokens = type.getRecommendedMaxTokens();
        
        // For very long inputs, allow slightly more output
        if (inputTokens > 2000) {
            return (int) (baseTokens * 1.2);
        } else if (inputTokens > 1000) {
            return (int) (baseTokens * 1.1);
        }
        
        return baseTokens;
    }
    
    /**
     * Estimate token count for text (rough approximation)
     * 
     * More accurate: use tiktoken library
     * Approximation: 1 token ≈ 4 characters for English
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Rough estimation: 4 chars per token
        // For Chinese: ~1.5 chars per token
        int charCount = text.length();
        
        // Check if text contains significant Chinese characters
        long chineseChars = text.chars()
            .filter(c -> c >= 0x4E00 && c <= 0x9FFF)
            .count();
        
        if (chineseChars > charCount * 0.3) {
            // Predominantly Chinese text
            return (int) (charCount / 1.5);
        } else {
            // Predominantly English text
            return (int) (charCount / 4.0);
        }
    }
    
    /**
     * Check if output should be penalized for verbosity
     */
    public boolean isVerbose(String output, int maxTokens) {
        int actualTokens = estimateTokenCount(output);
        
        // Consider verbose if uses > 90% of max_tokens
        return actualTokens > maxTokens * 0.9;
    }
    
    /**
     * Calculate token savings from optimization
     */
    public TokenSavings calculateSavings(String original, String optimized) {
        int originalTokens = estimateTokenCount(original);
        int optimizedTokens = estimateTokenCount(optimized);
        int savedTokens = originalTokens - optimizedTokens;
        double savingsPercent = originalTokens > 0 
            ? (double) savedTokens / originalTokens * 100 
            : 0.0;
        
        return TokenSavings.builder()
            .originalTokens(originalTokens)
            .optimizedTokens(optimizedTokens)
            .savedTokens(savedTokens)
            .savingsPercent(savingsPercent)
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TokenSavings {
        private int originalTokens;
        private int optimizedTokens;
        private int savedTokens;
        private double savingsPercent;
    }
}
