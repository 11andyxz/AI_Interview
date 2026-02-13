package com.aiinterview.ml.rag;

import com.aiinterview.dto.QAHistory;
import com.aiinterview.ml.indexing.KnowledgeIndexer;
import com.aiinterview.model.Interview;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Integration adapter for RAG pipeline with existing services
 * 
 * This service provides RAG-enhanced alternatives to existing interview logic:
 * - Question generation: Uses RagQuestionGenerator instead of random selection
 * - Answer evaluation: Uses RagAnswerEvaluator with golden examples
 * - Resume indexing: Indexes resumes after analysis for retrieval
 * 
 * Usage Examples in existing services:
 * 
 * 1. In InterviewSessionService.pickNextQuestion():
 *    Replace: return knowledgeBaseService.getRandomQuestion(role, level);
 *    With:    return ragIntegration.generateNextQuestion(interview, history);
 * 
 * 2. In AiService.evaluateAnswer():
 *    Replace: return openAiService.callDirectly(prompt);
 *    With:    return ragIntegration.evaluateAnswer(question, answer, role, level);
 * 
 * 3. In ResumeService.analyzeResume():
 *    After:   String analysis = aiService.analyzeResume(resume);
 *    Add:     ragIntegration.indexResumeAnalysis(resumeId, analysis);
 */
@Slf4j
@Service
public class RagIntegrationService {
    
    @Autowired
    private RagQuestionGenerator questionGenerator;
    
    @Autowired
    private RagAnswerEvaluator answerEvaluator;
    
    @Autowired
    private KnowledgeIndexer knowledgeIndexer;
    
    @Value("${ml.rag.enabled:true}")
    private boolean ragEnabled;
    
    /**
     * Generate next interview question using RAG
     * 
     * Integration point for InterviewSessionService.pickNextQuestion()
     * 
     * @param interview Interview entity
     * @param history Conversation history
     * @return Generated question
     */
    public Mono<GeneratedQuestion> generateNextQuestion(
            Interview interview,
            List<QAHistory> history) {
        
        if (!ragEnabled) {
            return Mono.error(new UnsupportedOperationException("RAG is disabled"));
        }
        
        try {
            // Convert QAHistory to RAG format
            List<com.aiinterview.ml.rag.QAHistory> ragHistory = history.stream()
                .map(qa -> com.aiinterview.ml.rag.QAHistory.builder()
                    .question(qa.getQuestion())
                    .answer(qa.getAnswer())
                    .difficulty(qa.getDifficulty())
                    .score(qa.getScore())
                    .build())
                .collect(Collectors.toList());
            
            // Get resume context (if available)
            String resumeContext = interview.getResumeContext();
            
            // Generate with RAG
            return questionGenerator.generateNextQuestion(
                interview.getId(),
                interview.getRoleId(),
                interview.getLevel(),
                ragHistory,
                resumeContext
            );
            
        } catch (Exception e) {
            log.error("Failed to generate RAG question, falling back", e);
            return Mono.error(e);
        }
    }
    
    /**
     * Evaluate answer using RAG with golden examples
     * 
     * Integration point for AiService.evaluateAnswer()
     * 
     * @param question Interview question
     * @param answer Candidate's answer
     * @param roleId Role ID
     * @param level Difficulty level
     * @return Evaluation result
     */
    public Mono<EvaluationResult> evaluateAnswer(
            String question,
            String answer,
            String roleId,
            String level) {
        
        if (!ragEnabled) {
            return Mono.error(new UnsupportedOperationException("RAG is disabled"));
        }
        
        return answerEvaluator.evaluateWithContext(question, answer, roleId, level);
    }
    
    /**
     * Index resume analysis for future retrieval
     * 
     * Integration point for ResumeService.analyzeResume()
     * Call this after analyzing a resume to enable similar-resume retrieval
     * 
     * @param resumeId Resume ID
     * @param analysisResult Resume analysis result (text or JSON)
     */
    public void indexResumeAnalysis(Long resumeId, String analysisResult) {
        if (!ragEnabled) {
            log.debug("RAG disabled, skipping resume indexing");
            return;
        }
        
        try {
            knowledgeIndexer.indexResume(resumeId, analysisResult);
            log.info("Indexed resume analysis for resume {}", resumeId);
        } catch (Exception e) {
            log.error("Failed to index resume {}", resumeId, e);
        }
    }
    
    /**
     * Check if RAG is enabled and ready
     */
    public boolean isReady() {
        return ragEnabled && knowledgeIndexer != null;
    }
}
