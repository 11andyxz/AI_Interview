package com.aiinterview.service;

import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportService {
    
    @Autowired
    private InterviewRepository interviewRepository;
    
    @Autowired
    private InterviewSessionService interviewSessionService;

    /**
     * Generate interview report (JSON format)
     */
    public Map<String, Object> generateReport(String interviewId) {
        Optional<Interview> interviewOpt = interviewRepository.findById(interviewId);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found");
        }
        
        Interview interview = interviewOpt.get();
        List<QAHistory> history = interviewSessionService.getChatHistory(interviewId);
        
        Map<String, Object> report = new HashMap<>();
        report.put("interviewId", interviewId);
        report.put("title", interview.getTitle());
        report.put("status", interview.getStatus());
        report.put("date", interview.getDate());
        report.put("createdAt", interview.getCreatedAt());
        report.put("updatedAt", interview.getUpdatedAt());
        
        // Conversation summary
        report.put("totalQuestions", history.size());
        report.put("conversationHistory", history);
        
        // Generate feedback
        String feedback = interviewSessionService.buildFeedback(interviewId, 
            interview.getTitle() != null ? interview.getTitle() : "general");
        report.put("feedback", feedback);
        
        // Calculate statistics
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalExchanges", history.size());
        statistics.put("averageAnswerLength", calculateAverageAnswerLength(history));
        report.put("statistics", statistics);
        
        report.put("generatedAt", LocalDateTime.now());
        
        return report;
    }

    private double calculateAverageAnswerLength(List<QAHistory> history) {
        if (history.isEmpty()) {
            return 0.0;
        }
        double total = history.stream()
            .filter(qa -> qa.getAnswerText() != null)
            .mapToInt(qa -> qa.getAnswerText().length())
            .sum();
        return total / history.size();
    }
}

