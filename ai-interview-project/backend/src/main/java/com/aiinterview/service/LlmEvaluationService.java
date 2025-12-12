package com.aiinterview.service;

import com.aiinterview.model.EvaluationResult;
import com.aiinterview.model.openai.OpenAiMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class LlmEvaluationService {

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private PromptService promptService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Evaluate candidate's answer using LLM
     */
    public Mono<EvaluationResult> evaluateAnswer(String question, String answer, 
                                                  String roleId, String level) {
        // Build evaluation prompt
        String systemPrompt = promptService.buildEvaluationSystemPrompt();
        String userPrompt = promptService.buildEvaluationPrompt(question, answer, roleId, level);

        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemPrompt),
            new OpenAiMessage("user", userPrompt)
        );

        return openAiService.chat(messages)
            .map(this::parseEvaluationResult)
            .onErrorResume(error -> {
                System.err.println("Evaluation error: " + error.getMessage());
                return Mono.just(createFallbackEvaluation(answer));
            });
    }

    /**
     * Parse evaluation result from JSON string
     */
    private EvaluationResult parseEvaluationResult(String jsonResponse) {
        try {
            // Try to extract JSON from the response if it contains additional text
            String jsonContent = extractJson(jsonResponse);
            return objectMapper.readValue(jsonContent, EvaluationResult.class);
        } catch (Exception e) {
            System.err.println("Failed to parse evaluation result: " + e.getMessage());
            System.err.println("Response was: " + jsonResponse);
            return createFallbackEvaluation(null);
        }
    }

    /**
     * Extract JSON from response that might contain additional text
     */
    private String extractJson(String response) {
        // Find first { and last }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return response;
    }

    /**
     * Create fallback evaluation when AI service fails
     */
    private EvaluationResult createFallbackEvaluation(String answer) {
        EvaluationResult result = new EvaluationResult();
        
        // Simple length-based scoring as fallback
        int length = answer != null ? answer.length() : 0;
        
        if (length > 200) {
            result.setScore(70.0);
            result.setRubricLevel("good");
            result.setTechnicalAccuracy(7);
            result.setDepth(7);
            result.setExperience(7);
            result.setCommunication(7);
        } else if (length > 100) {
            result.setScore(60.0);
            result.setRubricLevel("average");
            result.setTechnicalAccuracy(6);
            result.setDepth(6);
            result.setExperience(6);
            result.setCommunication(6);
        } else {
            result.setScore(50.0);
            result.setRubricLevel("poor");
            result.setTechnicalAccuracy(5);
            result.setDepth(5);
            result.setExperience(5);
            result.setCommunication(5);
        }
        
        result.setStrengths(List.of("回答了问题"));
        result.setImprovements(List.of("可以提供更多细节", "建议结合实际项目经验"));
        result.setFollowUpQuestions(List.of("能否详细解释一下？", "在实际项目中如何应用？"));
        
        return result;
    }

    /**
     * Batch evaluate multiple answers (for final feedback)
     */
    public String generateOverallFeedback(List<String> questions, List<String> answers, 
                                         String roleId, String level) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("面试总结：\n\n");
        
        // This could be enhanced to use LLM for comprehensive feedback
        feedback.append("候选人在本次面试中回答了 ").append(questions.size()).append(" 个问题。\n");
        feedback.append("建议进一步评估候选人的技术深度和实践经验。\n");
        
        return feedback.toString();
    }
}

