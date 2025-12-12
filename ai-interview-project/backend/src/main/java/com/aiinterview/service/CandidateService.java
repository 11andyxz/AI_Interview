package com.aiinterview.service;

import com.aiinterview.model.Candidate;
import com.aiinterview.repository.CandidateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final ObjectMapper objectMapper;

    public CandidateService(CandidateRepository candidateRepository, ObjectMapper objectMapper) {
        this.candidateRepository = candidateRepository;
        this.objectMapper = objectMapper;
    }

    public List<Candidate> findAll() {
        return candidateRepository.findAll();
    }

    public Optional<Candidate> findById(Integer id) {
        return candidateRepository.findById(id);
    }

    /**
     * Build a lightweight knowledge base for an interview session using candidate profile.
     */
    public Map<String, Object> buildKnowledgeBase(Candidate candidate,
                                                  String positionType,
                                                  List<String> programmingLanguages,
                                                  String language) {
        List<String> skills = parseSkills(candidate.getSkills());
        String summary = summarizeResume(candidate.getResumeText());

        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(Map.of(
            "id", "q_" + candidate.getId() + "_001",
            "text", "Based on your experience, how did you apply " +
                    (programmingLanguages != null && !programmingLanguages.isEmpty()
                        ? String.join(", ", programmingLanguages)
                        : "these technologies") + " in past projects?",
            "type", "technical",
            "difficulty", "medium",
            "skills", skills
        ));
        questions.add(Map.of(
            "id", "q_" + candidate.getId() + "_002",
            "text", "Can you walk me through a challenging problem you solved in " +
                    (StringUtils.hasText(positionType) ? positionType : "your recent role") + "?",
            "type", "behavioral",
            "difficulty", "medium",
            "skills", skills
        ));

        return Map.of(
            "candidateId", candidate.getId(),
            "candidateName", candidate.getName(),
            "positionType", positionType,
            "language", language,
            "skills", skills,
            "summary", summary,
            "questions", questions,
            "personalized", true
        );
    }

    private List<String> parseSkills(String skillsJson) {
        if (!StringUtils.hasText(skillsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(skillsJson, new TypeReference<>() {});
        } catch (Exception e) {
            // Fallback: store as single string if parsing fails
            return List.of(skillsJson);
        }
    }

    private String summarizeResume(String resumeText) {
        if (!StringUtils.hasText(resumeText)) {
            return "No resume summary available.";
        }
        // Lightweight summary: truncate to keep response small
        String trimmed = resumeText.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }
}

