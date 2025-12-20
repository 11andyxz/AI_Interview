package com.aiinterview.service;

import com.aiinterview.dto.QAHistory;
import com.aiinterview.dto.ResumeAnalysisResult;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ObjectMapper objectMapper;

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

        // Interview type and resume information
        report.put("interviewType", interview.getInterviewType());
        if (interview.getResumeId() != null) {
            report.put("resumeId", interview.getResumeId());
            // Include resume analysis data if available
            try {
                var analysisOpt = resumeService.getResumeAnalysisData(interview.getResumeId(), interview.getUserId());
                if (analysisOpt.isPresent()) {
                    report.put("resumeAnalysis", analysisOpt.get());
                }
            } catch (Exception e) {
                // Ignore resume analysis errors in report generation
            }
        }

        // Conversation summary
        report.put("totalQuestions", history.size());
        report.put("conversationHistory", history);

        // Generate comprehensive feedback using OpenAI
        try {
            Map<String, Object> comprehensiveFeedback = generateComprehensiveFeedback(interview, history);
            report.put("comprehensiveFeedback", comprehensiveFeedback);
        } catch (Exception e) {
            // Fallback to basic feedback
            String feedback = interviewSessionService.buildFeedback(interviewId,
                interview.getTitle() != null ? interview.getTitle() : "general");
            report.put("feedback", feedback);
        }

        // Legacy feedback for backward compatibility
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

    /**
     * Generate comprehensive feedback with three dimensions using OpenAI
     */
    private Map<String, Object> generateComprehensiveFeedback(Interview interview, List<QAHistory> history) {
        try {
            // Build conversation transcript
            StringBuilder transcript = new StringBuilder();
            for (QAHistory qa : history) {
                transcript.append("Q: ").append(qa.getQuestionText()).append("\n");
                transcript.append("A: ").append(qa.getAnswerText()).append("\n\n");
            }

            // Get resume analysis data if available
            String resumeContext = "";
            if (interview.getResumeId() != null) {
                try {
                    var analysisOpt = resumeService.getResumeAnalysisData(interview.getResumeId(), interview.getUserId());
                    if (analysisOpt.isPresent()) {
                        ResumeAnalysisResult analysis = analysisOpt.get();
                        resumeContext = String.format(
                            "Resume Analysis: Level=%s, Tech Stack=%s, Experience=%d years, Skills=%s",
                            analysis.getLevel(),
                            analysis.getTechStack() != null ? analysis.getTechStack() : "N/A",
                            analysis.getExperienceYears(),
                            analysis.getSkills() != null ? analysis.getSkills() : "N/A"
                        );
                    }
                } catch (Exception e) {
                    // Ignore resume analysis errors
                }
            }

            // Generate comprehensive feedback prompt
            String prompt = String.format("""
                Analyze this interview conversation and provide comprehensive feedback in JSON format.
                Focus on three main dimensions: technical skills, resume content optimization, and interview performance.

                Interview Context:
                - Position: %s
                - Interview Type: %s
                - %s

                Conversation Transcript:
                %s

                Please provide detailed analysis in the following JSON structure:
                {
                    "overallAssessment": {
                        "score": 0-100,
                        "level": "excellent|good|average|poor",
                        "summary": "brief overall assessment"
                    },
                    "technicalSkills": {
                        "score": 0-100,
                        "strengths": ["strength1", "strength2"],
                        "weaknesses": ["weakness1", "weakness2"],
                        "recommendations": ["rec1", "rec2"],
                        "skillGaps": ["gap1", "gap2"]
                    },
                    "resumeContent": {
                        "score": 0-100,
                        "alignment": "how well resume matches interview performance",
                        "suggestedImprovements": ["improvement1", "improvement2"],
                        "missingElements": ["element1", "element2"]
                    },
                    "interviewPerformance": {
                        "score": 0-100,
                        "communication": "assessment of communication skills",
                        "structure": "assessment of answer structure",
                        "confidence": "assessment of confidence level",
                        "improvementAreas": ["area1", "area2"],
                        "tips": ["tip1", "tip2"]
                    },
                    "nextSteps": ["step1", "step2", "step3"]
                }

                Provide specific, actionable feedback. Be constructive and encouraging.
                Return ONLY valid JSON, no additional text or explanations.
                """,
                interview.getTitle(),
                interview.getInterviewType(),
                resumeContext,
                transcript.toString()
            );

            // Call OpenAI
            String aiResponse = openAiService.simpleChat(
                "You are an expert technical interviewer and career coach with extensive experience analyzing candidate performance.",
                prompt
            ).block();

            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                throw new RuntimeException("OpenAI returned empty response");
            }

            // Parse JSON response
            String jsonResponse = extractJsonFromResponse(aiResponse);
            return objectMapper.readValue(jsonResponse, Map.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate comprehensive feedback: " + e.getMessage(), e);
        }
    }

    /**
     * Extract JSON from AI response (handles cases where AI adds extra text)
     */
    private String extractJsonFromResponse(String response) {
        // Find the first '{' and last '}'
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw new RuntimeException("No valid JSON object found in response: " + response);
        }

        return response.substring(startIndex, endIndex + 1);
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

