package com.aiinterview.ml.nlp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Local implementation of TF-IDF vectorization.
 * Computes Term Frequency-Inverse Document Frequency vectors for text similarity.
 */
@Component
@ConditionalOnProperty(name = "ml.nlp.enabled", havingValue = "true", matchIfMissing = false)
public class TfIdfVectorizer {
    
    // IDF values: term -> idf score
    private Map<String, Double> idfMap;
    
    // Total number of documents used for fitting
    private int documentCount;
    
    // Vocabulary (all unique terms)
    private Set<String> vocabulary;
    
    /**
     * Fit the vectorizer on a corpus of documents to compute IDF values.
     * 
     * @param documents List of training documents
     */
    public void fit(List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Documents list cannot be null or empty");
        }
        
        this.documentCount = documents.size();
        this.vocabulary = new HashSet<>();
        
        // Count document frequency for each term
        Map<String, Integer> documentFrequency = new HashMap<>();
        
        for (String doc : documents) {
            Set<String> termsInDoc = new HashSet<>(tokenize(doc));
            
            for (String term : termsInDoc) {
                vocabulary.add(term);
                documentFrequency.put(term, documentFrequency.getOrDefault(term, 0) + 1);
            }
        }
        
        // Compute IDF: log(N / (1 + df))
        this.idfMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            String term = entry.getKey();
            int df = entry.getValue();
            double idf = Math.log((double) documentCount / (1 + df));
            idfMap.put(term, idf);
        }
    }
    
    /**
     * Transform a document into a TF-IDF vector (as a map of term -> tfidf score).
     * 
     * @param document The input document text
     * @return Map of term to TF-IDF score
     */
    public Map<String, Double> transform(String document) {
        if (idfMap == null) {
            throw new IllegalStateException("Vectorizer must be fitted before transform");
        }
        
        List<String> terms = tokenize(document);
        Map<String, Double> tfidfVector = new HashMap<>();
        
        if (terms.isEmpty()) {
            return tfidfVector;
        }
        
        // Count term frequency
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String term : terms) {
            termFrequency.put(term, termFrequency.getOrDefault(term, 0) + 1);
        }
        
        // Compute TF-IDF: tf * idf
        int totalTerms = terms.size();
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int count = entry.getValue();
            
            // TF = count / total_terms
            double tf = (double) count / totalTerms;
            
            // Get IDF (default to 0 if term not in vocabulary)
            double idf = idfMap.getOrDefault(term, 0.0);
            
            // TF-IDF
            double tfidf = tf * idf;
            tfidfVector.put(term, tfidf);
        }
        
        return tfidfVector;
    }
    
    /**
     * Compute cosine similarity between two TF-IDF vectors.
     * 
     * @param vector1 First TF-IDF vector (term -> score map)
     * @param vector2 Second TF-IDF vector (term -> score map)
     * @return Cosine similarity score (0 to 1)
     */
    public double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        if (vector1.isEmpty() || vector2.isEmpty()) {
            return 0.0;
        }
        
        // Compute dot product
        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : vector1.entrySet()) {
            String term = entry.getKey();
            if (vector2.containsKey(term)) {
                dotProduct += entry.getValue() * vector2.get(term);
            }
        }
        
        // Compute magnitudes
        double magnitude1 = Math.sqrt(vector1.values().stream()
                .mapToDouble(v -> v * v)
                .sum());
        
        double magnitude2 = Math.sqrt(vector2.values().stream()
                .mapToDouble(v -> v * v)
                .sum());
        
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (magnitude1 * magnitude2);
    }
    
    /**
     * Tokenize text into terms (lowercase, split by whitespace and punctuation)
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Split by whitespace and punctuation, convert to lowercase
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        
        List<String> tokens = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                tokens.add(word);
            }
        }
        
        return tokens;
    }
    
    /**
     * Get the vocabulary (all unique terms from training corpus)
     */
    public Set<String> getVocabulary() {
        return vocabulary != null ? Collections.unmodifiableSet(vocabulary) : Collections.emptySet();
    }
    
    /**
     * Get IDF value for a specific term
     */
    public double getIdf(String term) {
        return idfMap != null ? idfMap.getOrDefault(term.toLowerCase(), 0.0) : 0.0;
    }
    
    /**
     * Check if the vectorizer has been fitted
     */
    public boolean isFitted() {
        return idfMap != null;
    }
}
