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
 * RAG-powered question generator
 * 
 * Retrieves relevant context from vector store and generates
 * contextually appropriate interview questions.
 */
@Slf4j
@Service
public class RagQuestionGenerator {
    
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
     * Generate next interview question based on context
     * 
     * @param interviewId Interview session ID
     * @param roleId Role/position ID (backend_java, frontend_react, etc.)
     * @param level Difficulty level (junior, mid, senior)
     * @param history Conversation history
     * @param resumeContext Resume summary/context
     * @return Generated question
     */
    public Mono<GeneratedQuestion> generateNextQuestion(
            String interviewId,
            String roleId,
            String level,
            List<QAHistory> history,
            String resumeContext) {
        
        return Mono.fromCallable(() -> {
            try {
                // Build search query from context
                String searchQuery = buildSearchQuery(roleId, level, history, resumeContext);
                
                // Retrieve relevant context from vector store
                float[] queryEmbedding = embeddingService.generateEmbedding(searchQuery);
                
                Map<String, String> filters = new HashMap<>();
                if (roleId != null) {
                    filters.put("role", roleId);
                }
                // Don't filter by difficulty - allow broader context
                
                List<SearchResult> retrievedContext = vectorStore.search(queryEmbedding, topK, filters);
                
                // Build augmented prompt
                String prompt = buildQuestionPrompt(roleId, level, history, resumeContext, retrievedContext);
                
                // Call LLM
                String response = callOpenAI(prompt, 0.7);
                
                // Parse response
                GeneratedQuestion question = parseQuestionResponse(response, retrievedContext);
                question.setDifficulty(level);
                
                log.info("Generated question for interview: {}, role: {}, level: {}", 
                    interviewId, roleId, level);
                
                return question;
                
            } catch (Exception e) {
                log.error("Failed to generate question", e);
                // Return fallback question
                return GeneratedQuestion.builder()
                    .question("Tell me about your experience with " + roleId + ".")
                    .type("general")
                    .difficulty(level)
                    .confidence(0.5)
                    .build();
            }
        });
    }
    
    /**
     * Build search query from interview context
     */
    private String buildSearchQuery(String roleId, String level, List<QAHistory> history, String resumeContext) {
        StringBuilder query = new StringBuilder();
        
        query.append("Role: ").append(roleId != null ? roleId : "general").append(". ");
        query.append("Level: ").append(level != null ? level : "mid").append(". ");
        
        if (resumeContext != null && !resumeContext.isEmpty()) {
            query.append("Resume: ").append(resumeContext.substring(0, Math.min(200, resumeContext.length()))).append(". ");
        }
        
        if (history != null && !history.isEmpty()) {
            query.append("Previous topics: ");
            history.stream()
                .limit(3)
                .forEach(qa -> query.append(qa.getQuestion()).append("; "));
        }
        
        return query.toString();
    }
    
    /**
     * Build augmented prompt with retrieved context
     */
    private String buildQuestionPrompt(
            String roleId,
            String level,
            List<QAHistory> history,
            String resumeContext,
            List<SearchResult> retrievedContext) {
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert technical interviewer. Generate a relevant interview question.\n\n");
        
        // Add role and level
        prompt.append("**Interview Context:**\n");
        prompt.append("- Role: ").append(roleId != null ? roleId : "Software Engineer").append("\n");
        prompt.append("- Level: ").append(level != null ? level : "mid").append("\n\n");
        
        // Add resume context
        if (resumeContext != null && !resumeContext.isEmpty()) {
            prompt.append("**Candidate Background:**\n");
            prompt.append(resumeContext.substring(0, Math.min(500, resumeContext.length()))).append("\n\n");
        }
        
        // Add conversation history
        if (history != null && !history.isEmpty()) {
            prompt.append("**Previous Questions:**\n");
            history.forEach(qa -> {
                prompt.append("- ").append(qa.getQuestion()).append("\n");
            });
            prompt.append("\n");
        }
        
        // Add retrieved context
        if (retrievedContext != null && !retrievedContext.isEmpty()) {
            prompt.append("**Relevant Examples/Context:**\n");
            retrievedContext.forEach(result -> {
                prompt.append("- ").append(result.getContent().substring(0, Math.min(200, result.getContent().length())));
                prompt.append(" (similarity: ").append(String.format("%.2f", result.getScore())).append(")\n");
            });
            prompt.append("\n");
        }
        
        // Add instructions
        prompt.append("**Instructions:**\n");
        prompt.append("Generate ONE interview question that:\n");
        prompt.append("1. Matches the candidate's background and experience level\n");
        prompt.append("2. Is different from previous questions\n");
        prompt.append("3. Tests practical knowledge relevant to the role\n");
        prompt.append("4. Is clear and specific\n\n");
        
        prompt.append("Respond in JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"question\": \"<the question>\",\n");
        prompt.append("  \"type\": \"<technical|behavioral|system_design>\",\n");
        prompt.append("  \"expectedAnswer\": \"<key points to look for>\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse LLM response into GeneratedQuestion
     */
    private GeneratedQuestion parseQuestionResponse(String response, List<SearchResult> context) {
        try {
            // Try to extract JSON from response
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
            
            return GeneratedQuestion.builder()
                .question((String) parsed.get("question"))
                .type((String) parsed.getOrDefault("type", "technical"))
                .expectedAnswer((String) parsed.get("expectedAnswer"))
                .context(context.stream()
                    .map(SearchResult::getContent)
                    .limit(3)
                    .collect(Collectors.joining("; ")))
                .contextSource("vector_store")
                .confidence(0.85)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to parse question response", e);
            
            // Fallback: treat entire response as question
            return GeneratedQuestion.builder()
                .question(response.trim())
                .type("general")
                .confidence(0.6)
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
            requestBody.put("max_tokens", 500);
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
