package com.aiinterview.ml.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generated interview question with metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedQuestion {
    
    /** The interview question text */
    private String question;
    
    /** Question type (technical, behavioral, system_design, etc.) */
    private String type;
    
    /** Difficulty level (junior, mid, senior) */
    private String difficulty;
    
    /** Expected answer or key points */
    private String expectedAnswer;
    
    /** Follow-up questions (optional) */
    private List<String> followUpQuestions;
    
    /** Context used for generation */
    private String context;
    
    /** Source of context (resume, golden_example, etc.) */
    private String contextSource;
    
    /** Confidence score (0.0 to 1.0) */
    private Double confidence;
}
