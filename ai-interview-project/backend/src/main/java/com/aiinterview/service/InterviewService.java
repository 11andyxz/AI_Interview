package com.aiinterview.service;

import com.aiinterview.dto.CreateInterviewRequest;
import com.aiinterview.dto.ResumeAnalysisResult;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing interview operations
 */
@Service
public class InterviewService {

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Create a new interview
     */
    public Interview createInterview(CreateInterviewRequest request, Long userId) {
        Interview interview = new Interview();
        interview.setUserId(userId);
        interview.setCandidateId(request.getCandidateId());
        interview.setTitle(request.getPositionType());
        interview.setLanguage(request.getLanguage());
        interview.setTechStack(String.join(",", request.getProgrammingLanguages() != null ? request.getProgrammingLanguages() : List.of()));
        interview.setUseCustomKnowledge(request.isUseCustomKnowledge());
        interview.setInterviewType(request.getInterviewType() != null ? request.getInterviewType() : "general");

        // Handle resume-based interview
        if ("resume-based".equals(request.getInterviewType()) && request.getResumeId() != null) {
            interview.setResumeId(request.getResumeId());

            // Auto-fill fields from resume analysis if available
            autoFillFromResumeAnalysis(interview, request.getResumeId(), userId);
        }

        try {
            if (request.getProgrammingLanguages() != null) {
                interview.setProgrammingLanguages(objectMapper.writeValueAsString(request.getProgrammingLanguages()));
            }
        } catch (Exception e) {
            // ignore and leave null
        }

        return interviewRepository.save(interview);
    }

    /**
     * Auto-fill interview fields from resume analysis data
     */
    private void autoFillFromResumeAnalysis(Interview interview, Long resumeId, Long userId) {
        Optional<ResumeAnalysisResult> analysisOpt = resumeService.getResumeAnalysisData(resumeId, userId);

        if (analysisOpt.isPresent()) {
            ResumeAnalysisResult analysis = analysisOpt.get();

            // Auto-fill position type based on main skill areas
            if (interview.getTitle() == null || interview.getTitle().trim().isEmpty()) {
                if (analysis.getMainSkillAreas() != null && !analysis.getMainSkillAreas().isEmpty()) {
                    interview.setTitle(analysis.getMainSkillAreas().get(0) + " Developer");
                }
            }

            // Auto-fill tech stack from analysis
            if (analysis.getTechStack() != null && !analysis.getTechStack().isEmpty()) {
                String techStack = String.join(",", analysis.getTechStack());
                if (interview.getTechStack() == null || interview.getTechStack().trim().isEmpty()) {
                    interview.setTechStack(techStack);
                } else {
                    // Merge with existing tech stack
                    interview.setTechStack(interview.getTechStack() + "," + techStack);
                }

                // Also update programming languages JSON
                try {
                    interview.setProgrammingLanguages(objectMapper.writeValueAsString(analysis.getTechStack()));
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Get interviews by user ID
     */
    public List<Interview> getInterviewsByUserId(Long userId) {
        return interviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get interview by ID and user ID
     */
    public Optional<Interview> getInterviewByIdAndUserId(String id, Long userId) {
        return interviewRepository.findByIdAndUserId(id, userId);
    }

    /**
     * Check if user owns the interview
     */
    public boolean isInterviewOwnedByUser(String interviewId, Long userId) {
        return interviewRepository.findByIdAndUserId(interviewId, userId).isPresent();
    }

    /**
     * Update interview status
     */
    public Interview updateInterviewStatus(String id, String status, Long userId) {
        Optional<Interview> interviewOpt = getInterviewByIdAndUserId(id, userId);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found or access denied");
        }

        Interview interview = interviewOpt.get();
        interview.setStatus(status);
        return interviewRepository.save(interview);
    }

    /**
     * Delete interview
     */
    public void deleteInterview(String id, Long userId) {
        Optional<Interview> interviewOpt = getInterviewByIdAndUserId(id, userId);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found or access denied");
        }

        interviewRepository.delete(interviewOpt.get());
    }
}
