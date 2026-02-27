package com.aiinterview.ml.rag;

import com.aiinterview.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reranking service for improving retrieval quality
 * Supports cross-encoder scoring and LLM-based reranking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankerService {
    
    private final OpenAiService openAiService;
    
    /**
     * Rerank search results using cross-encoder scoring
     * 
     * @param query User query
     * @param candidates Initial search results
     * @param finalTopK Number of results to return after reranking
     * @return Reranked results
     */
    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int finalTopK) {
        log.debug("Reranking {} candidates for query: {}", candidates.size(), query);
        
        if (candidates.isEmpty()) {
            return List.of();
        }
        
        // Calculate cross-encoder scores
        List<SearchResult> rerankedResults = candidates.stream()
            .map(result -> {
                double rerankScore = calculateCrossEncoderScore(query, result.getContent());
                return result.toBuilder()
                    .rerankScore(rerankScore)
                    .score(rerankScore) // Update final score
                    .build();
            })
            .sorted(Comparator.comparingDouble(SearchResult::getRerankScore).reversed())
            .limit(finalTopK)
            .collect(Collectors.toList());
        
        log.info("Reranking completed: {} -> {} results", candidates.size(), rerankedResults.size());
        return rerankedResults;
    }
    
    /**
     * Rerank using LLM to evaluate relevance
     * 
     * @param query User query
     * @param candidates Initial search results
     * @param finalTopK Number of results to return
     * @return LLM-reranked results
     */
    public List<SearchResult> llmRerank(String query, List<SearchResult> candidates, int finalTopK) {
        log.debug("LLM reranking {} candidates for query: {}", candidates.size(), query);
        
        if (candidates.isEmpty()) {
            return List.of();
        }
        
        // Build prompt for LLM to evaluate relevance
        StringBuilder prompt = new StringBuilder();
        prompt.append("Given the query: \"").append(query).append("\"\n\n");
        prompt.append("Rank the following passages by relevance (0-10 scale):\n\n");
        
        for (int i = 0; i < candidates.size(); i++) {
            SearchResult result = candidates.get(i);
            prompt.append(String.format("[%d] %s\n\n", i, truncate(result.getContent(), 200)));
        }
        
        prompt.append("Respond with JSON: {\"rankings\": [{\"index\": 0, \"score\": 8.5}, ...]}\n");
        
        try {
            // Call LLM for ranking
            String response = openAiService.simpleChat(
                "You are an AI that ranks search results by relevance.",
                prompt.toString()
            ).block();
            
            // Parse LLM response
            Map<Integer, Double> llmScores = parseLLMRankings(response);
            
            // Apply LLM scores and rerank
            List<SearchResult> rerankedResults = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                SearchResult result = candidates.get(i);
                Double llmScore = llmScores.getOrDefault(i, 5.0) / 10.0; // Normalize to 0-1
                
                SearchResult reranked = result.toBuilder()
                    .rerankScore(llmScore)
                    .score(llmScore)
                    .build();
                rerankedResults.add(reranked);
            }
            
            // Sort and limit
            rerankedResults = rerankedResults.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getRerankScore).reversed())
                .limit(finalTopK)
                .collect(Collectors.toList());
            
            log.info("LLM reranking completed: {} -> {} results", candidates.size(), rerankedResults.size());
            return rerankedResults;
            
        } catch (Exception e) {
            log.error("LLM reranking failed, falling back to original ranking: {}", e.getMessage());
            return candidates.stream().limit(finalTopK).collect(Collectors.toList());
        }
    }
    
    /**
     * Calculate cross-encoder score between query and document
     * Simple implementation using semantic similarity heuristics
     */
    private double calculateCrossEncoderScore(String query, String document) {
        // Normalize texts
        String normQuery = query.toLowerCase();
        String normDoc = document.toLowerCase();
        
        // Calculate multiple relevance signals
        double exactMatchScore = calculateExactMatchScore(normQuery, normDoc);
        double termOverlapScore = calculateTermOverlapScore(normQuery, normDoc);
        double lengthPenalty = calculateLengthPenalty(document);
        
        // Combine scores
        double finalScore = (exactMatchScore * 0.5) + (termOverlapScore * 0.3) + (lengthPenalty * 0.2);
        
        return Math.min(1.0, Math.max(0.0, finalScore));
    }
    
    /**
     * Calculate exact match score
     */
    private double calculateExactMatchScore(String query, String document) {
        if (document.contains(query)) {
            return 1.0;
        }
        
        // Check for partial matches
        String[] queryWords = query.split("\\s+");
        long matchCount = Arrays.stream(queryWords)
            .filter(word -> word.length() > 2)
            .filter(document::contains)
            .count();
        
        return queryWords.length > 0 ? (double) matchCount / queryWords.length : 0.0;
    }
    
    /**
     * Calculate term overlap score
     */
    private double calculateTermOverlapScore(String query, String document) {
        Set<String> queryTerms = new HashSet<>(Arrays.asList(query.split("\\W+")));
        Set<String> docTerms = new HashSet<>(Arrays.asList(document.split("\\W+")));
        
        queryTerms.removeIf(s -> s.length() <= 2);
        docTerms.removeIf(s -> s.length() <= 2);
        
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        
        long overlapCount = queryTerms.stream()
            .filter(docTerms::contains)
            .count();
        
        return (double) overlapCount / queryTerms.size();
    }
    
    /**
     * Calculate length penalty (prefer concise, relevant documents)
     */
    private double calculateLengthPenalty(String document) {
        int length = document.length();
        
        if (length < 100) return 0.5; // Too short
        if (length < 500) return 1.0; // Ideal length
        if (length < 1000) return 0.8; // Acceptable
        return 0.6; // Too long
    }
    
    /**
     * Parse LLM ranking response
     */
    private Map<Integer, Double> parseLLMRankings(String response) {
        Map<Integer, Double> rankings = new HashMap<>();
        
        try {
            // Simple JSON parsing (production should use Jackson)
            String jsonContent = response;
            if (response.contains("```json")) {
                jsonContent = response.substring(
                    response.indexOf("```json") + 7,
                    response.lastIndexOf("```")
                ).trim();
            }
            
            // Extract rankings array
            if (jsonContent.contains("\"rankings\"")) {
                // Parse each ranking entry
                String[] entries = jsonContent.split("\\{\"index\":");
                for (String entry : entries) {
                    if (!entry.contains("\"score\"")) continue;
                    
                    try {
                        int index = Integer.parseInt(entry.substring(0, entry.indexOf(",")).trim());
                        String scoreStr = entry.substring(entry.indexOf("\"score\":") + 8);
                        scoreStr = scoreStr.substring(0, scoreStr.indexOf("}")).trim();
                        double score = Double.parseDouble(scoreStr);
                        
                        rankings.put(index, score);
                    } catch (Exception e) {
                        log.debug("Failed to parse ranking entry: {}", entry);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM rankings: {}", e.getMessage());
        }
        
        return rankings;
    }
    
    /**
     * Truncate text to specified length
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
