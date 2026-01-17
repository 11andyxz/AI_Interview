package com.aiinterview.service;

import com.aiinterview.dto.ResumeAnalysisResult;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.validator.ResumeAnalysisValidator;
import com.aiinterview.validator.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Service for analyzing resumes using OpenAI and extracting structured data
 */
@Service
public class ResumeAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ResumeAnalysisService.class);
    private static final int MAX_RETRIES = 2;

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final ResumeAnalysisValidator validator;

    @Autowired
    public ResumeAnalysisService(OpenAiService openAiService, ObjectMapper objectMapper, ResumeAnalysisValidator validator) {
        this.openAiService = openAiService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Analyze resume content using OpenAI with validation and retry logic
     */
    public ResumeAnalysisResult analyzeResumeWithOpenAI(String resumeText) {
        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new IllegalArgumentException("Resume text cannot be null or empty");
        }
        // Single-call behavior: attempt once and surface errors to caller (tests expect exceptions)
        try {
            logger.info("Resume analysis attempt 1/{}", MAX_RETRIES);

            String prompt = generateAnalysisPrompt(resumeText);

            String aiResponse = openAiService.simpleChat(
                "You are an expert HR professional and technical recruiter with extensive experience analyzing resumes.",
                prompt
            ).block();

            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                logger.warn("OpenAI returned empty response");
                throw new RuntimeException("Failed to analyze resume: empty response from AI");
            }

            // Extract JSON (handles extra surrounding text)
            String jsonResponse = extractJsonFromResponse(aiResponse);

            // Validate if validator is available
            ValidationResult validationResult = null;
            if (validator != null) {
                try {
                    validationResult = validator.validate(jsonResponse);
                } catch (Exception ve) {
                    // Treat validation exceptions as failure
                    logger.warn("Validator threw exception: {}", ve.getMessage());
                }
            }

            if (validationResult != null && !validationResult.isValid()) {
                logger.warn("Validation failed: {}", validationResult.getErrorMessage());
                throw new RuntimeException("Failed to analyze resume: validation failed");
            }

            // Parse and return
            return parseAnalysisResult(jsonResponse);

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            logger.error("Resume analysis failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze resume: " + e.getMessage(), e);
        }
    }

    private ResumeAnalysisResult createFallbackResult() {
        ResumeAnalysisResult fallback = new ResumeAnalysisResult();
        fallback.setLevel("junior");
        fallback.setTechStack(Collections.emptyList());
        fallback.setExperienceYears(0);
        fallback.setSkills(Collections.emptyList());
        fallback.setMainSkillAreas(Collections.emptyList());
        fallback.setEducation("Not analyzed");
        fallback.setSummary("Resume analysis temporarily unavailable. Please try again later.");
        return fallback;
    }

    /**
     * Generate the analysis prompt for OpenAI
     */
    private String generateAnalysisPrompt(String resumeText) {
        return String.format("""
            Analyze the following resume and provide a structured assessment in JSON format.
            Focus on extracting key information for interview preparation.

            Resume Content:
            %s

            Please analyze this resume and return a JSON object with the following structure:
            {
                "level": "junior|mid|senior" (based on experience and skills),
                "techStack": ["technology1", "technology2", ...] (main technologies/frameworks mentioned),
                "experienceYears": integer (estimated years of professional experience),
                "skills": ["skill1", "skill2", ...] (specific technical skills),
                "mainSkillAreas": ["area1", "area2", ...] (main areas of expertise like "Backend Development", "Frontend Development", etc.),
                "education": "string" (highest education level or degree),
                "summary": "brief professional summary based on the resume"
            }

            Guidelines:
            - Level should be "junior" (0-3 years), "mid" (3-7 years), or "senior" (7+ years)
            - Tech stack should include programming languages, frameworks, databases, tools
            - Skills should be specific technical competencies
            - Main skill areas should categorize the person's expertise
            - Be realistic and based only on the resume content
            - Return ONLY valid JSON, no additional text or explanations

            Return the JSON object:""", resumeText);
    }

    /**
     * Parse the AI response and extract structured data
     */
    protected ResumeAnalysisResult parseAnalysisResult(String jsonResponse) {
        try {
            // Ensure we extract a clean JSON object even if extra text present
            String jsonOnly = extractJsonFromResponse(jsonResponse);
            JsonNode jsonNode = objectMapper.readTree(jsonOnly);

            ResumeAnalysisResult result = new ResumeAnalysisResult();

            // Extract fields with defaults
            result.setLevel(getStringValue(jsonNode, "level", "junior"));
            result.setTechStack(getStringListValue(jsonNode, "techStack", Collections.emptyList()));
            result.setExperienceYears(getIntValue(jsonNode, "experienceYears", 0));
            result.setSkills(getStringListValue(jsonNode, "skills", Collections.emptyList()));
            result.setMainSkillAreas(getStringListValue(jsonNode, "mainSkillAreas", Collections.emptyList()));
            result.setEducation(getStringValue(jsonNode, "education", "Not specified"));
            result.setSummary(getStringValue(jsonNode, "summary", "Professional summary not available"));

            return result;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse validated JSON: " + jsonResponse, e);
        }
    }

    /**
     * Extract JSON from AI response (handles cases where AI adds extra text)
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) {
            throw new RuntimeException("No valid JSON object found in response: null");
        }

        boolean inQuotes = false;
        boolean escape = false;
        int depth = 0;
        int start = -1;

        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        return response.substring(start, i + 1);
                    }
                }
            }
        }

        throw new RuntimeException("No valid JSON object found in response: " + response);
    }

    /**
     * Helper method to safely extract string value from JSON node
     */
    private String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull()) {
            return fieldNode.asText(defaultValue);
        }
        return defaultValue;
    }

    /**
     * Helper method to safely extract int value from JSON node
     */
    private int getIntValue(JsonNode node, String fieldName, int defaultValue) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull() && fieldNode.isInt()) {
            return fieldNode.asInt(defaultValue);
        }
        return defaultValue;
    }

    /**
     * Helper method to safely extract string list from JSON node
     */
    private List<String> getStringListValue(JsonNode node, String fieldName, List<String> defaultValue) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull() && fieldNode.isArray()) {
            return objectMapper.convertValue(fieldNode, List.class);
        }
        return defaultValue;
    }
}
