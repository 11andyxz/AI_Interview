package com.aiinterview.ml.rag;

import com.aiinterview.ml.embedding.EmbeddingService;
import com.aiinterview.ml.embedding.SearchResult;
import com.aiinterview.ml.embedding.VectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG-powered answer evaluator
 * 
 * Retrieves golden examples and technical concepts to provide
 * grounded, consistent evaluation with reduced hallucination.
 */
@Slf4j
@Service
public class RagAnswerEvaluator {
    
    @Autowired
    private VectorStore vectorStore;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiUrl;
    
    @Value("${openai.api.model:gpt-3.5-turbo-1106}")
    private String model;
    
    @Value("${ml.rag.top-k:5}")
    private int topK;
    
    /**
     * Evaluate answer with retrieved context
     * 
     * @param question Interview question
     * @param answer Candidate's answer
     * @param roleId Role (backend_java, frontend_react, etc.)
     * @param level Difficulty level (junior, mid, senior)
     * @return Evaluation result
     */
    public Mono<EvaluationResult> evaluateWithContext(
            String question,
            String answer,
            String roleId,
            String level) {
        
        return Mono.fromCallable(() -> {
            try {
                // Build search query
                String searchQuery = question + " " + answer;
                
                // Retrieve golden examples and concepts
                float[] queryEmbedding = embeddingService.generateEmbedding(searchQuery);
                
                Map<String, String> filters = new HashMap<>();
                filters.put("category", "golden_example");
                if (roleId != null) {
                    filters.put("role", roleId);
                }
                
                List<SearchResult> goldenExamples = vectorStore.search(queryEmbedding, topK, filters);
                
                // Build evaluation prompt with context
                String prompt = buildEvaluationPrompt(question, answer, level, goldenExamples);
                
                // Call LLM with lower temperature for consistency
                String response = callOpenAI(prompt, 0.3);
                
                // Parse evaluation result
                EvaluationResult result = parseEvaluationResponse(response, goldenExamples);
                
                log.info("Evaluated answer for question: {} (score: {})", 
                    question.substring(0, Math.min(50, question.length())), result.getScore());
                
                return result;
                
            } catch (Exception e) {
                log.error("Failed to evaluate answer", e);
                
                // Return fallback evaluation
                return EvaluationResult.builder()
                    .score(5.0)
                    .passed(false)
                    .feedback("Unable to evaluate answer due to technical error. Please try again.")
                    .confidence(0.3)
                    .build();
            }
        });
    }
    
    /**
     * Build evaluation prompt with golden examples
     */
    private String buildEvaluationPrompt(
            String question,
            String answer,
            String level,
            List<SearchResult> goldenExamples) {
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert technical interviewer evaluating a candidate's answer.\n\n");
        
        // Add question and answer
        prompt.append("**Question:**\n");
        prompt.append(question).append("\n\n");
        
        prompt.append("**Candidate's Answer:**\n");
        prompt.append(answer).append("\n\n");
        
        prompt.append("**Level:**\n");
        prompt.append(level != null ? level : "mid").append("\n\n");
        
        // Add golden examples for reference
        if (goldenExamples != null && !goldenExamples.isEmpty()) {
            prompt.append("**Reference Examples (for context, not exact match required):**\n");
            goldenExamples.stream()
                .limit(3)
                .forEach(example -> {
                    prompt.append("- ").append(example.getContent().substring(0, Math.min(300, example.getContent().length())));
                    prompt.append(" (relevance: ").append(String.format("%.2f", example.getScore())).append(")\n");
                });
            prompt.append("\n");
        }
        
        // Add evaluation criteria
        prompt.append("**Evaluation Criteria:**\n");
        prompt.append("1. **Technical Accuracy** (0-10): Correctness of technical content\n");
        prompt.append("2. **Completeness** (0-10): Coverage of key points\n");
        prompt.append("3. **Clarity** (0-10): Clear explanation and structure\n");
        prompt.append("4. **Depth** (0-10): Level of detail and understanding\n\n");
        
        // Add instructions
        prompt.append("**Instructions:**\n");
        prompt.append("1. Evaluate based on the criteria above\n");
        prompt.append("2. Be consistent with similar answers\n");
        prompt.append("3. Use the reference examples as guidance, but evaluate the actual answer\n");
        prompt.append("4. Provide constructive feedback\n\n");
        
        prompt.append("Respond in JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"score\": <0-10, overall score>,\n");
        prompt.append("  \"passed\": <true if score >= 6.0>,\n");
        prompt.append("  \"feedback\": \"<brief constructive feedback>\",\n");
        prompt.append("  \"strengths\": [\"<strength 1>\", \"<strength 2>\"],\n");
        prompt.append("  \"improvements\": [\"<area 1>\", \"<area 2>\"],\n");
        prompt.append("  \"dimensionScores\": {\n");
        prompt.append("    \"technical_accuracy\": <0-10>,\n");
        prompt.append("    \"completeness\": <0-10>,\n");
        prompt.append("    \"clarity\": <0-10>,\n");
        prompt.append("    \"depth\": <0-10>\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse LLM response into EvaluationResult
     */
    private EvaluationResult parseEvaluationResponse(String response, List<SearchResult> context) {
        try {
            // Extract JSON from response
            String jsonStr = response;
            if (response.contains("```json")) {
                int start = response.indexOf("```json") + 7;
                int end = response.indexOf("```", start);
                jsonStr = response.substring(start, end).trim();
            } else if (response.contains("{")) {
                int start = response.indexOf("{");
                int end = response.lastIndexOf("}") + 1;
                jsonStr = response.substring(start, end);
            }
            
            Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);
            
            // Parse score (can be Integer or Double)
            Object scoreObj = parsed.get("score");
            Double score = scoreObj instanceof Integer ? 
                ((Integer) scoreObj).doubleValue() : 
                (Double) scoreObj;
            
            // Parse dimension scores
            Map<String, Object> dimensionScoresRaw = (Map<String, Object>) parsed.get("dimensionScores");
            Map<String, Double> dimensionScores = new HashMap<>();
            if (dimensionScoresRaw != null) {
                dimensionScoresRaw.forEach((key, value) -> {
                    Double dimScore = value instanceof Integer ? 
                        ((Integer) value).doubleValue() : 
                        (Double) value;
                    dimensionScores.put(key, dimScore);
                });
            }
            
            return EvaluationResult.builder()
                .score(score)
                .passed((Boolean) parsed.getOrDefault("passed", score >= 6.0))
                .feedback((String) parsed.get("feedback"))
                .strengths((List<String>) parsed.get("strengths"))
                .improvements((List<String>) parsed.get("improvements"))
                .dimensionScores(dimensionScores)
                .context(context.stream()
                    .map(SearchResult::getContent)
                    .limit(2)
                    .collect(Collectors.joining("; ")))
                .confidence(0.85)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to parse evaluation response", e);
            
            // Fallback parsing
            return EvaluationResult.builder()
                .score(5.0)
                .passed(false)
                .feedback("Unable to parse evaluation. Raw response: " + response)
                .confidence(0.4)
                .build();
        }
    }
    
    /**
     * Call OpenAI API
     */
    private String callOpenAI(String prompt, double temperature) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", 600);
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(openaiUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            
            throw new RuntimeException("Failed to get response from OpenAI");
            
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            throw new RuntimeException("OpenAI API error", e);
        }
    }
}
