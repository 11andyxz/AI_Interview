package com.aiinterview.ml.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Keyword-based search using BM25 algorithm
 * Complements semantic search for hybrid retrieval
 */
@Slf4j
@Service
public class KeywordSearchService {
    
    // In-memory inverted index
    private final Map<String, Map<String, Double>> invertedIndex = new ConcurrentHashMap<>();
    private final Map<String, DocumentInfo> documents = new ConcurrentHashMap<>();
    
    // BM25 parameters
    private static final double K1 = 1.5;
    private static final double B = 0.75;
    
    /**
     * Perform keyword-based search using BM25 scoring
     * 
     * @param query Search query
     * @param topK Number of results to return
     * @return Ranked search results
     */
    public List<SearchResult> keywordSearch(String query, int topK) {
        log.debug("Keyword search: query='{}', topK={}", query, topK);
        
        if (documents.isEmpty()) {
            log.warn("No documents indexed for keyword search");
            return List.of();
        }
        
        // Tokenize query
        List<String> queryTerms = tokenize(query);
        
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        
        // Calculate BM25 scores
        Map<String, Double> scores = new HashMap<>();
        double avgDocLength = documents.values().stream()
            .mapToInt(DocumentInfo::getLength)
            .average()
            .orElse(0.0);
        
        for (String term : queryTerms) {
            Map<String, Double> postings = invertedIndex.get(term.toLowerCase());
            if (postings == null) continue;
            
            double idf = calculateIDF(term);
            
            for (Map.Entry<String, Double> entry : postings.entrySet()) {
                String docId = entry.getKey();
                double tf = entry.getValue();
                
                DocumentInfo docInfo = documents.get(docId);
                if (docInfo == null) continue;
                
                double docLength = docInfo.getLength();
                double normalizedTF = tf / (K1 * ((1 - B) + B * (docLength / avgDocLength)) + tf);
                double bm25 = idf * normalizedTF;
                
                scores.merge(docId, bm25, Double::sum);
            }
        }
        
        // Normalize scores and create results
        double maxScore = scores.values().stream().max(Double::compare).orElse(1.0);
        
        List<SearchResult> results = scores.entrySet().stream()
            .map(entry -> {
                DocumentInfo docInfo = documents.get(entry.getKey());
                return SearchResult.builder()
                    .id(entry.getKey())
                    .content(docInfo.getContent())
                    .score(entry.getValue() / maxScore) // Normalize to 0-1
                    .keywordScore(entry.getValue() / maxScore)
                    .metadata(docInfo.getMetadata())
                    .tokenCount(docInfo.getLength())
                    .build();
            })
            .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
            .limit(topK)
            .collect(Collectors.toList());
        
        log.info("Keyword search completed: {} results", results.size());
        return results;
    }
    
    /**
     * Index a document for keyword search
     * 
     * @param id Document ID
     * @param content Document content
     * @param metadata Document metadata
     */
    public void indexDocument(String id, String content, Map<String, String> metadata) {
        // Tokenize content
        List<String> tokens = tokenize(content);
        
        // Store document info
        documents.put(id, new DocumentInfo(id, content, tokens.size(), metadata));
        
        // Build inverted index
        Map<String, Integer> termFrequencies = new HashMap<>();
        for (String token : tokens) {
            String term = token.toLowerCase();
            termFrequencies.merge(term, 1, Integer::sum);
        }
        
        for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
            String term = entry.getKey();
            double tf = entry.getValue();
            
            invertedIndex.computeIfAbsent(term, k -> new ConcurrentHashMap<>())
                        .put(id, tf);
        }
        
        log.debug("Indexed document: id={}, tokens={}", id, tokens.size());
    }
    
    /**
     * Tokenize text into words
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        
        // Simple tokenization: split on non-word characters
        Pattern pattern = Pattern.compile("\\w+");
        Matcher matcher = pattern.matcher(text.toLowerCase());
        
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 1) { // Skip single characters
                tokens.add(token);
            }
        }
        
        return tokens;
    }
    
    /**
     * Calculate IDF (Inverse Document Frequency)
     */
    private double calculateIDF(String term) {
        int docCount = documents.size();
        Map<String, Double> postings = invertedIndex.get(term.toLowerCase());
        
        if (postings == null || postings.isEmpty()) {
            return 0.0;
        }
        
        int docFreq = postings.size();
        return Math.log((docCount - docFreq + 0.5) / (docFreq + 0.5) + 1.0);
    }
    
    /**
     * Document information for keyword search
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class DocumentInfo {
        private String id;
        private String content;
        private int length;
        private Map<String, String> metadata;
    }
}
