package com.aiinterview.ml.nlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Loads and provides access to technical term dictionaries for different domains.
 * Used by ResponseFeatureExtractor to compute technical term density features.
 */
@Component
public class TechnicalTermDictionary {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private Set<String> backendTerms;
    private Set<String> frontendTerms;
    private Set<String> generalTerms;
    private Set<String> allTerms;
    
    @PostConstruct
    public void init() throws IOException {
        this.backendTerms = loadTerms("ml/dictionaries/backend_java.json");
        this.frontendTerms = loadTerms("ml/dictionaries/frontend_react.json");
        this.generalTerms = loadTerms("ml/dictionaries/general.json");
        
        // Combine all terms for general technical term checking
        this.allTerms = new HashSet<>();
        this.allTerms.addAll(backendTerms);
        this.allTerms.addAll(frontendTerms);
        this.allTerms.addAll(generalTerms);
    }
    
    /**
     * Load terms from a JSON dictionary file
     */
    private Set<String> loadTerms(String resourcePath) throws IOException {
        Set<String> terms = new HashSet<>();
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode termsArray = root.get("terms");
            
            if (termsArray != null && termsArray.isArray()) {
                for (JsonNode termNode : termsArray) {
                    // Store terms in lowercase for case-insensitive matching
                    terms.add(termNode.asText().toLowerCase());
                }
            }
        }
        
        return terms;
    }
    
    /**
     * Check if a term is a backend-related technical term
     */
    public boolean isBackendTerm(String term) {
        return backendTerms.contains(term.toLowerCase());
    }
    
    /**
     * Check if a term is a frontend-related technical term
     */
    public boolean isFrontendTerm(String term) {
        return frontendTerms.contains(term.toLowerCase());
    }
    
    /**
     * Check if a term is a general programming term
     */
    public boolean isGeneralTerm(String term) {
        return generalTerms.contains(term.toLowerCase());
    }
    
    /**
     * Check if a term is any technical term (from any dictionary)
     */
    public boolean isTechnicalTerm(String term) {
        return allTerms.contains(term.toLowerCase());
    }
    
    /**
     * Get all backend terms
     */
    public Set<String> getBackendTerms() {
        return Collections.unmodifiableSet(backendTerms);
    }
    
    /**
     * Get all frontend terms
     */
    public Set<String> getFrontendTerms() {
        return Collections.unmodifiableSet(frontendTerms);
    }
    
    /**
     * Get all general terms
     */
    public Set<String> getGeneralTerms() {
        return Collections.unmodifiableSet(generalTerms);
    }
    
    /**
     * Get all technical terms
     */
    public Set<String> getAllTerms() {
        return Collections.unmodifiableSet(allTerms);
    }
    
    /**
     * Count technical terms in text
     */
    public int countTechnicalTerms(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Simple word tokenization (split by whitespace and punctuation)
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        int count = 0;
        
        for (String word : words) {
            if (!word.isEmpty() && isTechnicalTerm(word)) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Get technical term density (ratio of technical terms to total words)
     */
    public double getTechnicalTermDensity(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        if (words.length == 0) {
            return 0.0;
        }
        
        int technicalCount = 0;
        int totalWords = 0;
        
        for (String word : words) {
            if (!word.isEmpty()) {
                totalWords++;
                if (isTechnicalTerm(word)) {
                    technicalCount++;
                }
            }
        }
        
        return totalWords > 0 ? (double) technicalCount / totalWords : 0.0;
    }
}
