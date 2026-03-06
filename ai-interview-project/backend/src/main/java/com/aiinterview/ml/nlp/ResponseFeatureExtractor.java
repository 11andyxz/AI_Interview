package com.aiinterview.ml.nlp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Extracts NLP features from candidate responses for ML scoring.
 * Generates 20+ numerical features covering length, vocabulary, technical terms, 
 * readability, confidence, and specificity metrics.
 */
@Component
public class ResponseFeatureExtractor {
    
    @Autowired
    private TechnicalTermDictionary termDictionary;
    
    @Autowired
    private TfIdfVectorizer tfidfVectorizer;
    
    // Patterns for feature extraction
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");
    
    // Hedge words indicating uncertainty
    private static final Set<String> HEDGE_WORDS = Set.of(
        "maybe", "perhaps", "possibly", "probably", "might", "could", "would",
        "seems", "appears", "think", "guess", "assume", "believe", "suppose"
    );
    
    // Confidence words indicating certainty
    private static final Set<String> CONFIDENCE_WORDS = Set.of(
        "definitely", "certainly", "absolutely", "clearly", "obviously",
        "always", "never", "must", "will", "ensure", "guarantee"
    );
    
    // Abstract/vague words
    private static final Set<String> ABSTRACT_WORDS = Set.of(
        "thing", "stuff", "something", "anything", "everything",
        "somehow", "somewhere", "sometime", "someone", "anyone"
    );
    
    /**
     * Extract all features from a candidate response.
     * 
     * @param responseText The candidate's response text
     * @param referenceAnswer Optional reference answer for TF-IDF similarity
     * @return Array of feature values (20+ features)
     */
    public double[] extractFeatures(String responseText, String referenceAnswer) {
        if (responseText == null || responseText.isEmpty()) {
            return new double[24]; // Return zero vector
        }
        
        List<Double> features = new ArrayList<>();
        
        // Length features (4 features)
        features.add((double) responseText.length()); // 1. Character count
        int wordCount = countWords(responseText);
        features.add((double) wordCount); // 2. Word count
        int sentenceCount = countSentences(responseText);
        features.add((double) sentenceCount); // 3. Sentence count
        features.add(wordCount > 0 ? (double) responseText.length() / wordCount : 0.0); // 4. Avg word length
        
        // Vocabulary richness features (3 features)
        Set<String> uniqueWords = getUniqueWords(responseText);
        features.add((double) uniqueWords.size()); // 5. Unique word count
        features.add(wordCount > 0 ? (double) uniqueWords.size() / wordCount : 0.0); // 6. Vocabulary richness ratio
        features.add(computeLexicalDiversity(responseText)); // 7. Lexical diversity (type-token ratio)
        
        // Technical term features (3 features)
        int technicalTermCount = termDictionary.countTechnicalTerms(responseText);
        features.add((double) technicalTermCount); // 8. Technical term count
        features.add(termDictionary.getTechnicalTermDensity(responseText)); // 9. Technical term density
        features.add(computeTechnicalTermRelevance(responseText)); // 10. Technical relevance score
        
        // TF-IDF similarity features (2 features)
        if (referenceAnswer != null && !referenceAnswer.isEmpty() && tfidfVectorizer.isFitted()) {
            Map<String, Double> responseVector = tfidfVectorizer.transform(responseText);
            Map<String, Double> referenceVector = tfidfVectorizer.transform(referenceAnswer);
            double similarity = tfidfVectorizer.cosineSimilarity(responseVector, referenceVector);
            features.add(similarity); // 11. TF-IDF similarity to reference
            features.add(computeSemanticCoverage(responseVector, referenceVector)); // 12. Semantic coverage
        } else {
            features.add(0.0); // 11. No similarity available
            features.add(0.0); // 12. No coverage available
        }
        
        // Readability features (3 features)
        features.add(computeFleschReadingEase(responseText, wordCount, sentenceCount)); // 13. Flesch Reading Ease
        features.add(computeGunningFog(responseText, wordCount, sentenceCount)); // 14. Gunning Fog Index
        features.add(sentenceCount > 0 ? (double) wordCount / sentenceCount : 0.0); // 15. Avg sentence length
        
        // Confidence features (3 features)
        features.add(countHedgeWords(responseText)); // 16. Hedge word count
        features.add(countConfidenceWords(responseText)); // 17. Confidence word count
        features.add(computeConfidenceScore(responseText)); // 18. Overall confidence score
        
        // Specificity features (3 features)
        features.add(countAbstractWords(responseText)); // 19. Abstract word count
        features.add(computeSpecificityScore(responseText)); // 20. Specificity score
        features.add(computeConcreteExampleRatio(responseText)); // 21. Example/code snippet ratio
        
        // Structural features (3 features)
        features.add(hasCodeSnippet(responseText) ? 1.0 : 0.0); // 22. Contains code
        features.add(hasList(responseText) ? 1.0 : 0.0); // 23. Contains list/enumeration
        features.add(computeStructuralComplexity(responseText)); // 24. Structural complexity
        
        return features.stream().mapToDouble(Double::doubleValue).toArray();
    }
    
    /**
     * Count words in text
     */
    private int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        String[] words = text.split("\\s+");
        return (int) Arrays.stream(words).filter(w -> !w.isEmpty()).count();
    }
    
    /**
     * Count sentences in text
     */
    private int countSentences(String text) {
        if (text == null || text.isEmpty()) return 0;
        String[] sentences = SENTENCE_PATTERN.split(text);
        return Math.max(1, (int) Arrays.stream(sentences).filter(s -> !s.trim().isEmpty()).count());
    }
    
    /**
     * Get unique words (lowercase)
     */
    private Set<String> getUniqueWords(String text) {
        Set<String> unique = new HashSet<>();
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        for (String word : words) {
            if (!word.isEmpty()) {
                unique.add(word);
            }
        }
        return unique;
    }
    
    /**
     * Compute lexical diversity (type-token ratio)
     */
    private double computeLexicalDiversity(String text) {
        int wordCount = countWords(text);
        if (wordCount == 0) return 0.0;
        Set<String> uniqueWords = getUniqueWords(text);
        return (double) uniqueWords.size() / wordCount;
    }
    
    /**
     * Compute technical term relevance score (weighted by term importance)
     */
    private double computeTechnicalTermRelevance(String text) {
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        double relevanceScore = 0.0;
        
        for (String word : words) {
            if (termDictionary.isBackendTerm(word)) {
                relevanceScore += 1.0; // Backend terms highly relevant
            } else if (termDictionary.isFrontendTerm(word)) {
                relevanceScore += 0.8; // Frontend terms moderately relevant
            } else if (termDictionary.isGeneralTerm(word)) {
                relevanceScore += 0.5; // General terms less relevant
            }
        }
        
        return words.length > 0 ? relevanceScore / words.length : 0.0;
    }
    
    /**
     * Compute semantic coverage (how much of reference is covered by response)
     */
    private double computeSemanticCoverage(Map<String, Double> responseVector, Map<String, Double> referenceVector) {
        if (referenceVector.isEmpty()) return 0.0;
        
        int coveredTerms = 0;
        for (String term : referenceVector.keySet()) {
            if (responseVector.containsKey(term)) {
                coveredTerms++;
            }
        }
        
        return (double) coveredTerms / referenceVector.size();
    }
    
    /**
     * Compute Flesch Reading Ease score
     * Formula: 206.835 - 1.015 * (words/sentences) - 84.6 * (syllables/words)
     */
    private double computeFleschReadingEase(String text, int wordCount, int sentenceCount) {
        if (wordCount == 0 || sentenceCount == 0) return 0.0;
        
        int syllableCount = countSyllables(text);
        double avgWordsPerSentence = (double) wordCount / sentenceCount;
        double avgSyllablesPerWord = (double) syllableCount / wordCount;
        
        return 206.835 - 1.015 * avgWordsPerSentence - 84.6 * avgSyllablesPerWord;
    }
    
    /**
     * Compute Gunning Fog Index
     * Formula: 0.4 * ((words/sentences) + 100 * (complex_words/words))
     */
    private double computeGunningFog(String text, int wordCount, int sentenceCount) {
        if (wordCount == 0 || sentenceCount == 0) return 0.0;
        
        int complexWords = countComplexWords(text);
        double avgWordsPerSentence = (double) wordCount / sentenceCount;
        double complexWordRatio = (double) complexWords / wordCount;
        
        return 0.4 * (avgWordsPerSentence + 100 * complexWordRatio);
    }
    
    /**
     * Count syllables in text (approximate)
     */
    private int countSyllables(String text) {
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        int totalSyllables = 0;
        
        for (String word : words) {
            if (!word.isEmpty()) {
                totalSyllables += countSyllablesInWord(word);
            }
        }
        
        return totalSyllables;
    }
    
    /**
     * Count syllables in a single word (approximate)
     */
    private int countSyllablesInWord(String word) {
        word = word.toLowerCase().replaceAll("[^a-z]", "");
        if (word.length() <= 3) return 1;
        
        int syllables = 0;
        boolean previousWasVowel = false;
        
        for (char c : word.toCharArray()) {
            boolean isVowel = "aeiouy".indexOf(c) != -1;
            if (isVowel && !previousWasVowel) {
                syllables++;
            }
            previousWasVowel = isVowel;
        }
        
        // Adjust for silent 'e'
        if (word.endsWith("e")) {
            syllables--;
        }
        
        return Math.max(1, syllables);
    }
    
    /**
     * Count complex words (3+ syllables)
     */
    private int countComplexWords(String text) {
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        int complexCount = 0;
        
        for (String word : words) {
            if (!word.isEmpty() && countSyllablesInWord(word) >= 3) {
                complexCount++;
            }
        }
        
        return complexCount;
    }
    
    /**
     * Count hedge words
     */
    private double countHedgeWords(String text) {
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        long count = Arrays.stream(words).filter(HEDGE_WORDS::contains).count();
        return (double) count;
    }
    
    /**
     * Count confidence words
     */
    private double countConfidenceWords(String text) {
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        long count = Arrays.stream(words).filter(CONFIDENCE_WORDS::contains).count();
        return (double) count;
    }
    
    /**
     * Compute overall confidence score
     */
    private double computeConfidenceScore(String text) {
        double hedgeCount = countHedgeWords(text);
        double confidenceCount = countConfidenceWords(text);
        int wordCount = countWords(text);
        
        if (wordCount == 0) return 0.5;
        
        double hedgeRatio = hedgeCount / wordCount;
        double confidenceRatio = confidenceCount / wordCount;
        
        return 0.5 + (confidenceRatio - hedgeRatio) * 5.0; // Scale to 0-1 range
    }
    
    /**
     * Count abstract/vague words
     */
    private double countAbstractWords(String text) {
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        long count = Arrays.stream(words).filter(ABSTRACT_WORDS::contains).count();
        return (double) count;
    }
    
    /**
     * Compute specificity score (inverse of abstract word ratio)
     */
    private double computeSpecificityScore(String text) {
        double abstractCount = countAbstractWords(text);
        int wordCount = countWords(text);
        
        if (wordCount == 0) return 0.0;
        
        double abstractRatio = abstractCount / wordCount;
        return Math.max(0.0, 1.0 - abstractRatio * 10.0);
    }
    
    /**
     * Compute ratio of concrete examples/code snippets
     */
    private double computeConcreteExampleRatio(String text) {
        boolean hasCode = hasCodeSnippet(text);
        boolean hasList = hasList(text);
        boolean hasNumbers = text.matches(".*\\d+.*");
        
        int concreteIndicators = (hasCode ? 1 : 0) + (hasList ? 1 : 0) + (hasNumbers ? 1 : 0);
        return concreteIndicators / 3.0;
    }
    
    /**
     * Check if text contains code snippet
     */
    private boolean hasCodeSnippet(String text) {
        return text.contains("```") || 
               text.contains("()") || 
               text.contains("{}") ||
               text.contains("[];") ||
               text.matches(".*\\w+\\(\\).*");
    }
    
    /**
     * Check if text contains list/enumeration
     */
    private boolean hasList(String text) {
        return text.matches(".*\\d+[.)].+") || // Numbered list
               text.matches(".*[-*•].+") ||     // Bullet list
               text.toLowerCase().matches(".*(first|second|third|finally).+");
    }
    
    /**
     * Compute structural complexity based on organization indicators
     */
    private double computeStructuralComplexity(String text) {
        double complexity = 0.0;
        
        if (hasCodeSnippet(text)) complexity += 0.3;
        if (hasList(text)) complexity += 0.3;
        if (countSentences(text) >= 3) complexity += 0.2;
        if (text.contains(":") || text.contains(";")) complexity += 0.1;
        if (text.matches(".*\\n\\n.*")) complexity += 0.1; // Paragraph breaks
        
        return Math.min(1.0, complexity);
    }
    
    /**
     * Get feature count
     */
    public int getFeatureCount() {
        return 24;
    }
    
    /**
     * Get feature names for debugging/analysis
     */
    public String[] getFeatureNames() {
        return new String[]{
            "char_count", "word_count", "sentence_count", "avg_word_length",
            "unique_word_count", "vocab_richness", "lexical_diversity",
            "technical_term_count", "technical_term_density", "technical_relevance",
            "tfidf_similarity", "semantic_coverage",
            "flesch_reading_ease", "gunning_fog", "avg_sentence_length",
            "hedge_word_count", "confidence_word_count", "confidence_score",
            "abstract_word_count", "specificity_score", "concrete_example_ratio",
            "has_code", "has_list", "structural_complexity"
        };
    }
}
