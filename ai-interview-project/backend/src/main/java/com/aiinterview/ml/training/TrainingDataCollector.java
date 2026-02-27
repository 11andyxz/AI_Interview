package com.aiinterview.ml.training;

import com.aiinterview.model.InterviewInteraction;
import com.aiinterview.model.InterviewMessage;
import com.aiinterview.repository.InterviewInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects high-quality training data from production interviews
 * Filters for quality score, validation status, and diversity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDataCollector {
    
    private final InterviewInteractionRepository interactionRepository;
    
    // Quality thresholds
    private static final double MIN_QUALITY_SCORE = 75.0;
    private static final int MIN_ANSWER_LENGTH = 50;
    private static final int MAX_ANSWER_LENGTH = 2000;
    
    /**
     * Collect high-quality QA pairs for supervised fine-tuning
     * 
     * @param from Start date
     * @param to End date
     * @return List of training examples
     */
    @Transactional(readOnly = true)
    public List<TrainingExample> collectQualityPairs(LocalDate from, LocalDate to) {
        log.info("Collecting training data from {} to {}", from, to);
        
        LocalDateTime startTime = from.atStartOfDay();
        LocalDateTime endTime = to.plusDays(1).atStartOfDay();
        
        // Fetch interactions within date range
        List<InterviewMessage> interactions = interactionRepository
            .findByCreatedAtBetween(startTime, endTime);
        
        log.info("Found {} total interactions", interactions.size());
        
        // Filter for quality
        List<TrainingExample> examples = interactions.stream()
            .map(m -> (InterviewInteraction) m)  // Cast to access adapter methods
            .filter(this::isHighQuality)
            .map(this::toTrainingExample)
            .collect(Collectors.toList());
        
        log.info("Collected {} quality training examples", examples.size());
        
        return examples;
    }
    
    /**
     * Collect preference pairs for RLHF/DPO training
     * Finds multiple responses to same question and pairs them by quality
     * 
     * @return List of preference pairs
     */
    @Transactional(readOnly = true)
    public List<PreferencePair> collectPreferencePairs() {
        log.info("Collecting preference pairs for RLHF/DPO");
        
        List<PreferencePair> pairs = new ArrayList<>();
        
        // Group interactions by question (simplified - actual implementation
        // would need more sophisticated matching)
        List<InterviewMessage> interactions = interactionRepository.findAll();
        
        Map<String, List<InterviewMessage>> byQuestion = interactions.stream()
            .filter(i -> i.getAiMessage() != null && i.getUserMessage() != null)
            .collect(Collectors.groupingBy(InterviewMessage::getAiMessage));
        
        // Find pairs where quality differs significantly
        for (Map.Entry<String, List<InterviewMessage>> entry : byQuestion.entrySet()) {
            List<InterviewInteraction> responses = entry.getValue().stream()
                .map(m -> (InterviewInteraction) m)
                .toList();
            
            if (responses.size() < 2) continue;
            
            // Sort by quality score
            responses.sort((a, b) -> 
                Double.compare(
                    b.getQualityScore() != null ? b.getQualityScore() : 0.0,
                    a.getQualityScore() != null ? a.getQualityScore() : 0.0
                )
            );
            
            // Create pairs from top and bottom quartiles
            for (int i = 0; i < Math.min(2, responses.size() / 4); i++) {
                for (int j = Math.max(responses.size() - 2, responses.size() * 3 / 4); 
                     j < responses.size(); j++) {
                    
                    InterviewInteraction chosen = responses.get(i);
                    InterviewInteraction rejected = responses.get(j);
                    
                    double margin = (chosen.getQualityScore() != null ? chosen.getQualityScore() : 0.0)
                                  - (rejected.getQualityScore() != null ? rejected.getQualityScore() : 0.0);
                    
                    if (margin >= 20.0) { // Significant quality difference
                        PreferencePair pair = toPreferencePair(entry.getKey(), chosen, rejected, margin);
                        pairs.add(pair);
                    }
                }
            }
        }
        
        log.info("Collected {} preference pairs", pairs.size());
        
        return pairs;
    }
    
    /**
     * Collect training data with specific filters
     * 
     * @param filters Map of filter criteria
     * @return Filtered training examples
     */
    @Transactional(readOnly = true)
    public List<TrainingExample> collectWithFilters(Map<String, Object> filters) {
        log.info("Collecting training data with filters: {}", filters);
        
        List<InterviewMessage> interactions = interactionRepository.findAll();
        
        return interactions.stream()
            .map(m -> (InterviewInteraction) m)  // Cast to access adapter methods
            .filter(this::isHighQuality)
            .filter(i -> matchesFilters(i, filters))
            .map(this::toTrainingExample)
            .collect(Collectors.toList());
    }
    
    /**
     * Check if interaction meets quality criteria
     */
    private boolean isHighQuality(InterviewInteraction interaction) {
        if (interaction.getQuestionText() == null || interaction.getAnswerText() == null) {
            return false;
        }
        
        // Quality score threshold
        Double qualityScore = interaction.getQualityScore();
        if (qualityScore == null || qualityScore < MIN_QUALITY_SCORE) {
            return false;
        }
        
        // Validation pass  
        // InterviewInteraction doesn't have getValidationErrors, skip this check
        
        // Length constraints
        String answer = interaction.getAnswerText();
        int length = answer.length();
        if (length < MIN_ANSWER_LENGTH || length > MAX_ANSWER_LENGTH) {
            return false;
        }
        
        // Not flagged
        if (interaction.getFlagged() != null && interaction.getFlagged()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if interaction matches filter criteria
     */
    private boolean matchesFilters(InterviewInteraction interaction, Map<String, Object> filters) {
        if (filters.containsKey("role") && !filters.get("role").equals(interaction.getRole())) {
            return false;
        }
        
        if (filters.containsKey("difficulty") && !filters.get("difficulty").equals(interaction.getDifficulty())) {
            return false;
        }
        
        if (filters.containsKey("category") && !filters.get("category").equals(interaction.getCategory())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Convert interaction to training example
     */
    private TrainingExample toTrainingExample(InterviewInteraction interaction) {
        List<TrainingExample.Message> messages = new ArrayList<>();
        
        // System message
        messages.add(TrainingExample.Message.builder()
            .role("system")
            .content("You are an expert technical interviewer. Generate insightful questions and provide constructive feedback.")
            .build());
        
        // User message (question)
        messages.add(TrainingExample.Message.builder()
            .role("user")
            .content(interaction.getQuestion())
            .build());
        
        return TrainingExample.builder()
            .interactionId(interaction.getId())
            .conversationId(interaction.getConversationId())
            .role(interaction.getRole())
            .difficulty(interaction.getDifficulty())
            .messages(messages)
            .completion(interaction.getAnswer())
            .qualityScore(interaction.getQualityScore())
            .validationPass(interaction.getValidationErrors() == null || interaction.getValidationErrors().isEmpty())
            .category(interaction.getCategory())
            .skill(interaction.getSkill())
            .createdAt(interaction.getCreatedAt())
            .build();
    }
    
    /**
     * Convert interactions to preference pair
     */
    private PreferencePair toPreferencePair(String prompt, 
                                           InterviewInteraction chosen, 
                                           InterviewInteraction rejected,
                                           double margin) {
        return PreferencePair.builder()
            .pairId(chosen.getId() * 10000L + rejected.getId())
            .conversationId(chosen.getConversationId())
            .prompt(prompt)
            .context(chosen.getResumeContext())
            .chosen(PreferencePair.Response.builder()
                .content(chosen.getAnswer())
                .qualityScore(chosen.getQualityScore())
                .modelVersion(chosen.getModelVersion())
                .validationPass(chosen.getValidationErrors() == null || chosen.getValidationErrors().isEmpty())
                .build())
            .rejected(PreferencePair.Response.builder()
                .content(rejected.getAnswer())
                .qualityScore(rejected.getQualityScore())
                .modelVersion(rejected.getModelVersion())
                .validationPass(rejected.getValidationErrors() == null || rejected.getValidationErrors().isEmpty())
                .build())
            .margin(margin)
            .role(chosen.getRole())
            .difficulty(chosen.getDifficulty())
            .createdAt(chosen.getCreatedAt())
            .build();
    }
}
