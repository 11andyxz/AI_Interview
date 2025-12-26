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

        ValidationResult validationResult = null;
        
        // Try up to MAX_RETRIES times
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Resume analysis attempt {}/{}", attempt, MAX_RETRIES);
                
                String prompt = generateAnalysisPrompt(resumeText);
                
                String aiResponse = openAiService.simpleChat(
                    "You are an expert HR professional and technical recruiter with extensive experience analyzing resumes.",
                    prompt
                ).block();

                if (aiResponse == null || aiResponse.trim().isEmpty()) {
                    logger.warn("OpenAI returned empty response on attempt {}", attempt);
                    continue;
                }

                // Extract and validate JSON
                String jsonResponse = extractJsonFromResponse(aiResponse);
                validationResult = validator.validate(jsonResponse);
                
                if (validationResult.isValid()) {
                    logger.info("Resume analysis successful on attempt {}", attempt);
                    return parseAnalysisResult(jsonResponse);
                } else {
                    logger.warn("Validation failed on attempt {}: {}", attempt, validationResult.getErrorMessage());
                }

            } catch (Exception e) {
                logger.error("Error on resume analysis attempt {}: {}", attempt, e.getMessage());
            }
        }

        // All retries failed - return fallback
        logger.error("Resume analysis failed after {} attempts. Errors: {}", 
                    MAX_RETRIES, validationResult != null ? validationResult.getErrorMessage() : "none");
        return createFallbackResult();
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
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

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
        // Find the first '{' and last '}'
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw new RuntimeException("No valid JSON object found in response: " + response);
        }

        return response.substring(startIndex, endIndex + 1);
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
