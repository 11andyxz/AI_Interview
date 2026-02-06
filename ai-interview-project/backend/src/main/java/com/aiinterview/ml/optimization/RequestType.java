package com.aiinterview.ml.optimization;

/**
 * Types of AI requests for token optimization
 */
public enum RequestType {
    RESUME_ANALYSIS("Resume Analysis", 400),
    QUESTION_GENERATION("Question Generation", 300),
    ANSWER_EVALUATION("Answer Evaluation", 350),
    FOLLOW_UP_QUESTION("Follow-up Question", 200),
    MULTI_TURN_CONVERSATION("Multi-turn Conversation", 500);
    
    private final String displayName;
    private final int recommendedMaxTokens;
    
    RequestType(String displayName, int recommendedMaxTokens) {
        this.displayName = displayName;
        this.recommendedMaxTokens = recommendedMaxTokens;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getRecommendedMaxTokens() {
        return recommendedMaxTokens;
    }
}
