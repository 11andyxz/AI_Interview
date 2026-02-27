package com.aiinterview.ml.optimization;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents conversation history for multi-turn interviews
 */
@Data
@Builder
public class ConversationHistory {
    
    @Data
    @Builder
    public static class Turn {
        private String role; // "user" or "assistant"
        private String content;
        private LocalDateTime timestamp;
        private int tokenCount;
    }
    
    private String sessionId;
    private List<Turn> turns;
    private int totalTokens;
    
    /**
     * Add a new turn to the conversation
     */
    public void addTurn(String role, String content, int tokenCount) {
        if (turns == null) {
            turns = new ArrayList<>();
        }
        
        Turn turn = Turn.builder()
            .role(role)
            .content(content)
            .timestamp(LocalDateTime.now())
            .tokenCount(tokenCount)
            .build();
        
        turns.add(turn);
        totalTokens += tokenCount;
    }
    
    /**
     * Get the last N turns
     */
    public List<Turn> getLastNTurns(int n) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        
        int start = Math.max(0, turns.size() - n);
        return turns.subList(start, turns.size());
    }
    
    /**
     * Calculate total tokens in last N turns
     */
    public int getTokensInLastNTurns(int n) {
        return getLastNTurns(n).stream()
            .mapToInt(Turn::getTokenCount)
            .sum();
    }
    
    /**
     * Get number of turns
     */
    public int getTurnCount() {
        return turns != null ? turns.size() : 0;
    }
    
    /**
     * Clear old turns to save memory
     */
    public void clearOldTurns(int keepLastN) {
        if (turns != null && turns.size() > keepLastN) {
            List<Turn> toRemove = turns.subList(0, turns.size() - keepLastN);
            int removedTokens = toRemove.stream().mapToInt(Turn::getTokenCount).sum();
            toRemove.clear();
            totalTokens -= removedTokens;
        }
    }
}
