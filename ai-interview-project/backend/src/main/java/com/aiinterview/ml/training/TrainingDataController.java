package com.aiinterview.ml.training;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for training data collection and feedback
 */
@Slf4j
@RestController
@RequestMapping("/api/ml/training")
@RequiredArgsConstructor
public class TrainingDataController {
    
    private final TrainingDataCollector collector;
    private final TrainingDataExporter exporter;
    private final FeedbackRepository feedbackRepository;
    
    /**
     * Collect quality training examples in date range
     */
    @GetMapping("/examples")
    public ResponseEntity<List<TrainingExample>> getTrainingExamples(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        log.info("GET /api/ml/training/examples: from={}, to={}", from, to);
        
        List<TrainingExample> examples = collector.collectQualityPairs(from, to);
        
        return ResponseEntity.ok(examples);
    }
    
    /**
     * Collect preference pairs for RLHF/DPO
     */
    @GetMapping("/preferences")
    public ResponseEntity<List<PreferencePair>> getPreferencePairs() {
        log.info("GET /api/ml/training/preferences");
        
        List<PreferencePair> pairs = collector.collectPreferencePairs();
        
        return ResponseEntity.ok(pairs);
    }
    
    /**
     * Export training data to JSONL format
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, String>> exportTrainingData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        log.info("POST /api/ml/training/export: from={}, to={}", from, to);
        
        try {
            Path exportPath = exporter.exportTrainingData(from, to);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "export_path", exportPath.toString(),
                "message", "Training data exported successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to export training data", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
                ));
        }
    }
    
    /**
     * Export preference pairs
     */
    @PostMapping("/export/preferences")
    public ResponseEntity<Map<String, String>> exportPreferences() {
        log.info("POST /api/ml/training/export/preferences");
        
        try {
            Path exportPath = exporter.exportPreferencePairs();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "export_path", exportPath.toString(),
                "message", "Preference pairs exported successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to export preference pairs", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
                ));
        }
    }
    
    /**
     * Submit human feedback on AI response
     */
    @PostMapping("/feedback")
    public ResponseEntity<FeedbackRecord> submitFeedback(@RequestBody FeedbackRecord feedback) {
        log.info("POST /api/ml/training/feedback: type={}, interactionId={}", 
                 feedback.getFeedbackType(), feedback.getInteractionId());
        
        FeedbackRecord saved = feedbackRepository.save(feedback);
        
        return ResponseEntity.ok(saved);
    }
    
    /**
     * Get feedback for specific interaction
     */
    @GetMapping("/feedback/{interactionId}")
    public ResponseEntity<List<FeedbackRecord>> getFeedback(@PathVariable Long interactionId) {
        log.info("GET /api/ml/training/feedback/{}", interactionId);
        
        List<FeedbackRecord> feedback = feedbackRepository.findByInteractionId(interactionId);
        
        return ResponseEntity.ok(feedback);
    }
    
    /**
     * Get feedback statistics
     */
    @GetMapping("/feedback/stats")
    public ResponseEntity<Map<String, Object>> getFeedbackStats(
            @RequestParam(defaultValue = "7") int days) {
        
        log.info("GET /api/ml/training/feedback/stats: days={}", days);
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        Long thumbsUp = feedbackRepository.countByTypeAndSince("thumbs_up", since);
        Long thumbsDown = feedbackRepository.countByTypeAndSince("thumbs_down", since);
        Long flags = feedbackRepository.countByTypeAndSince("flag", since);
        Long corrections = feedbackRepository.countByTypeAndSince("correction", since);
        
        Map<String, Object> stats = Map.of(
            "period_days", days,
            "thumbs_up", thumbsUp,
            "thumbs_down", thumbsDown,
            "flags", flags,
            "corrections", corrections,
            "total", thumbsUp + thumbsDown + flags + corrections
        );
        
        return ResponseEntity.ok(stats);
    }
}
