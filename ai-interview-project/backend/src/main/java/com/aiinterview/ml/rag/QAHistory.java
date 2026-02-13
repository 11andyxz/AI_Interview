package com.aiinterview.ml.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QA history entry for context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QAHistory {
    
    /** Question asked */
    private String question;
    
    /** Candidate's answer */
    private String answer;
    
    /** Question difficulty level */
    private String difficulty;
    
    /** Score received (if evaluated) */
    private Double score;
}
