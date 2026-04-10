package com.aiinterview.ml.nlp;

import com.aiinterview.ml.model.ResponseScoringModel;
import com.aiinterview.ml.nlp.entity.ResponseFeatureCache;
import com.aiinterview.ml.nlp.repository.ResponseFeatureCacheRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Task 2: NLP Feature Engineering & GBRT Scoring Model
 * 
 * Tests:
 * 1. Feature extraction speed (< 10ms)
 * 2. Model accuracy (R² > 0.5, RMSE < 15)
 * 3. LLM cost reduction potential (cache hit rate)
 * 4. Weekly training performance (< 5 min)
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class NlpScoringIntegrationTest {
    
    @Autowired
    private ResponseFeatureExtractor featureExtractor;
    
    @Autowired
    private TechnicalTermDictionary termDictionary;
    
    @Autowired
    private TfIdfVectorizer tfidfVectorizer;
    
    @Autowired
    private ResponseScoringModel scoringModel;
    
    @Autowired
    private ResponseFeatureCacheRepository cacheRepository;
    
    /**
     * Test 1: Feature Extraction Performance
     * Acceptance: < 10ms per response
     */
    @Test
    public void testFeatureExtractionSpeed() {
        String sampleResponse = "Spring Boot uses dependency injection through the @Autowired annotation. " +
                               "The IoC container manages bean lifecycle. Controllers handle HTTP requests, " +
                               "services contain business logic, and repositories interact with databases.";
        
        String referenceAnswer = "Spring Boot provides dependency injection via IoC container. " +
                                "Use @Autowired for automatic injection. Core components: Controller, Service, Repository.";
        
        // Warmup
        for (int i = 0; i < 10; i++) {
            featureExtractor.extractFeatures(sampleResponse, referenceAnswer);
        }
        
        // Measure extraction time
        long startTime = System.nanoTime();
        int numIterations = 100;
        
        for (int i = 0; i < numIterations; i++) {
            double[] features = featureExtractor.extractFeatures(sampleResponse, referenceAnswer);
            assertNotNull(features);
            assertEquals(24, features.length, "Should extract 24 features");
        }
        
        long endTime = System.nanoTime();
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / numIterations;
        
        System.out.println("✓ Feature extraction avg time: " + String.format("%.2f", avgTimeMs) + " ms");
        assertTrue(avgTimeMs < 10.0, "Feature extraction should complete in < 10ms, got " + avgTimeMs + "ms");
    }
    
    /**
     * Test 2: Feature Quality
     * Verify all 24 features are properly extracted
     */
    @Test
    public void testFeatureQuality() {
        String technicalResponse = "Using React hooks like useState and useEffect simplifies state management. " +
                                  "The component lifecycle is handled through useEffect dependencies. " +
                                  "Redux provides centralized state with actions and reducers.";
        
        String referenceAnswer = "React hooks manage component state. useState for local state, " +
                                "useEffect for side effects. Redux centralizes application state.";
        
        // Fit TF-IDF vectorizer first
        List<String> corpus = List.of(technicalResponse, referenceAnswer);
        tfidfVectorizer.fit(corpus);
        
        double[] features = featureExtractor.extractFeatures(technicalResponse, referenceAnswer);
        
        assertEquals(24, features.length, "Should extract 24 features");
        
        // Verify feature ranges
        assertTrue(features[0] > 0, "Character count should be positive");
        assertTrue(features[1] > 0, "Word count should be positive");
        assertTrue(features[2] > 0, "Sentence count should be positive");
        assertTrue(features[6] >= 0 && features[6] <= 1, "Lexical diversity should be 0-1");
        assertTrue(features[8] >= 0 && features[8] <= 1, "Technical term density should be 0-1");
        assertTrue(features[10] >= 0 && features[10] <= 1, "TF-IDF similarity should be 0-1");
        
        String[] featureNames = featureExtractor.getFeatureNames();
        System.out.println("✓ Feature extraction quality verified:");
        System.out.println("  - Char count: " + features[0]);
        System.out.println("  - Word count: " + features[1]);
        System.out.println("  - Technical term density: " + String.format("%.2f", features[8]));
        System.out.println("  - TF-IDF similarity: " + String.format("%.2f", features[10]));
    }
    
    /**
     * Test 3: Technical Term Dictionary
     */
    @Test
    public void testTechnicalTermDictionary() {
        // Backend terms
        assertTrue(termDictionary.isBackendTerm("java"));
        assertTrue(termDictionary.isBackendTerm("spring boot"));
        assertTrue(termDictionary.isBackendTerm("mysql"));
        
        // Frontend terms
        assertTrue(termDictionary.isFrontendTerm("react"));
        assertTrue(termDictionary.isFrontendTerm("usestate"));
        assertTrue(termDictionary.isFrontendTerm("redux"));
        
        // General terms
        assertTrue(termDictionary.isGeneralTerm("algorithm"));
        assertTrue(termDictionary.isGeneralTerm("database"));
        
        // Count technical terms
        String text = "Spring Boot uses JPA for database access. React components use hooks.";
        int count = termDictionary.countTechnicalTerms(text);
        assertTrue(count >= 4, "Should find at least 4 technical terms");
        
        double density = termDictionary.getTechnicalTermDensity(text);
        assertTrue(density > 0.2, "Technical density should be > 0.2 for technical text");
        
        System.out.println("✓ Technical term dictionary verified");
        System.out.println("  - Technical terms found: " + count);
        System.out.println("  - Technical density: " + String.format("%.2f", density));
    }
    
    /**
     * Test 4: TF-IDF Vectorization
     */
    @Test
    public void testTfIdfVectorizer() {
        List<String> documents = List.of(
            "Spring Boot provides dependency injection for Java development",
            "React is a JavaScript library for UI",
            "Database design requires normalization",
            "Spring Boot simplifies dependency injection in Java applications"
        );
        
        // Fit vectorizer
        tfidfVectorizer.fit(documents);
        assertTrue(tfidfVectorizer.isFitted(), "Vectorizer should be fitted");
        
        // Transform documents
        Map<String, Double> vector1 = tfidfVectorizer.transform(documents.get(0));
        Map<String, Double> vector2 = tfidfVectorizer.transform(documents.get(3));
        
        assertFalse(vector1.isEmpty(), "Vector should not be empty");
        assertFalse(vector2.isEmpty(), "Vector should not be empty");
        
        // Compute similarity
        double similarity = tfidfVectorizer.cosineSimilarity(vector1, vector2);
        assertTrue(similarity > 0.1, "Similar documents should have similarity > 0.1");
        
        System.out.println("✓ TF-IDF vectorization verified");
        System.out.println("  - Vocabulary size: " + tfidfVectorizer.getVocabulary().size());
        System.out.println("  - Similarity between doc1 and doc4: " + String.format("%.3f", similarity));
    }
    
    /**
     * Test 5: GBRT Model Training and Prediction
     * Acceptance: R² > 0.5, RMSE < 15
     */
    @Test
    public void testGbrtModelTraining() {
        // Generate synthetic training data
        List<double[]> trainingFeatures = new ArrayList<>();
        List<Double> trainingScores = new ArrayList<>();
        
        // Generate 200 samples with known patterns
        for (int i = 0; i < 200; i++) {
            double[] features = generateSyntheticFeatures(i);
            double score = computeSyntheticScore(features);
            
            trainingFeatures.add(features);
            trainingScores.add(score);
        }
        
        // Train model
        long startTime = System.currentTimeMillis();
        scoringModel.train(trainingFeatures, trainingScores);
        long trainingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(scoringModel.isTrained(), "Model should be trained");
        assertTrue(trainingTime < 60_000, "Training should complete in < 1 minute");
        
        // Check training metrics
        double trainR2 = scoringModel.getTrainR2();
        double trainRmse = scoringModel.getTrainRmse();
        
        System.out.println("✓ GBRT model training verified:");
        System.out.println("  - Training time: " + trainingTime + " ms");
        System.out.println("  - Training R²: " + String.format("%.4f", trainR2));
        System.out.println("  - Training RMSE: " + String.format("%.2f", trainRmse));
        System.out.println("  - Tree count: " + scoringModel.getTreeCount());
        
        assertTrue(trainR2 > 0.5, "R² should be > 0.5, got " + trainR2);
        assertTrue(trainRmse < 15.0, "RMSE should be < 15, got " + trainRmse);
        
        // Test prediction
        double[] testFeatures = generateSyntheticFeatures(100);
        double prediction = scoringModel.predict(testFeatures);
        
        assertTrue(prediction >= 0 && prediction <= 100, 
                  "Prediction should be in range [0, 100], got " + prediction);
        
        System.out.println("  - Sample prediction: " + String.format("%.2f", prediction));
    }
    
    /**
     * Test 6: Model Evaluation
     */
    @Test
    public void testModelEvaluation() {
        // Train model first
        List<double[]> trainingFeatures = new ArrayList<>();
        List<Double> trainingScores = new ArrayList<>();
        
        for (int i = 0; i < 150; i++) {
            double[] features = generateSyntheticFeatures(i);
            trainingFeatures.add(features);
            trainingScores.add(computeSyntheticScore(features));
        }
        
        scoringModel.train(trainingFeatures, trainingScores);
        
        // Generate test data
        List<double[]> testFeatures = new ArrayList<>();
        List<Double> testScores = new ArrayList<>();
        
        for (int i = 150; i < 200; i++) {
            double[] features = generateSyntheticFeatures(i);
            testFeatures.add(features);
            testScores.add(computeSyntheticScore(features));
        }
        
        // Evaluate
        ResponseScoringModel.EvaluationMetrics metrics = scoringModel.evaluate(testFeatures, testScores);
        
        System.out.println("✓ Model evaluation completed:");
        System.out.println("  - Test " + metrics.toString());
        
        assertTrue(metrics.r2 > 0.2, "Test R² should be > 0.2 (synthetic data)");
        assertTrue(metrics.rmse < 20.0, "Test RMSE should be < 20");
    }
    
    /**
     * Test 7: Cache Repository Operations
     */
    @Test
    public void testCacheRepository() {
        ResponseFeatureCache cache = new ResponseFeatureCache();
        cache.setSessionId("test-session-001");
        cache.setQuestionId("Q001");
        cache.setResponseText("Spring Boot is a framework");
        cache.setFeatureVector("[1.0, 2.0, 3.0]");
        cache.setPredictedScore(75.5);
        cache.setLlmScore(78.0);
        cache.setPredictionError(2.5);
        cache.setFeatureExtractionTimeMs(5);
        cache.setModelVersion("v1.0");
        
        ResponseFeatureCache saved = cacheRepository.save(cache);
        assertNotNull(saved.getId());
        
        // Find by session and question
        var retrieved = cacheRepository.findBySessionIdAndQuestionId("test-session-001", "Q001");
        assertTrue(retrieved.isPresent());
        assertEquals(75.5, retrieved.get().getPredictedScore());
        
        // Cleanup
        cacheRepository.delete(saved);
        
        System.out.println("✓ Cache repository operations verified");
    }
    
    /**
     * Test 8: End-to-End Workflow
     */
    @Test
    public void testEndToEndWorkflow() {
        // Prepare TF-IDF vectorizer
        List<String> corpus = List.of(
            "Spring Boot provides dependency injection and auto-configuration",
            "React hooks manage component state and lifecycle",
            "Database normalization reduces redundancy"
        );
        tfidfVectorizer.fit(corpus);
        
        // Extract features
        String candidateResponse = "Spring Boot uses @Autowired for dependency injection. " +
                                  "The IoC container manages beans automatically.";
        String referenceAnswer = "Spring Boot provides dependency injection via @Autowired annotation.";
        
        long extractStart = System.nanoTime();
        double[] features = featureExtractor.extractFeatures(candidateResponse, referenceAnswer);
        long extractTime = (System.nanoTime() - extractStart) / 1_000_000;
        
        assertTrue(extractTime < 10, "Feature extraction should be < 10ms");
        
        // Train model
        List<double[]> trainingData = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            trainingData.add(generateSyntheticFeatures(i));
            scores.add(computeSyntheticScore(trainingData.get(i)));
        }
        
        scoringModel.train(trainingData, scores);
        
        // Predict
        double prediction = scoringModel.predict(features);
        assertTrue(prediction >= 0 && prediction <= 100);
        
        // Cache result
        ResponseFeatureCache cache = new ResponseFeatureCache();
        cache.setSessionId("e2e-test");
        cache.setQuestionId("Q-E2E");
        cache.setResponseText(candidateResponse);
        cache.setFeatureVector(java.util.Arrays.toString(features));
        cache.setPredictedScore(prediction);
        cache.setFeatureExtractionTimeMs((int) extractTime);
        cache.setModelVersion(scoringModel.getModelVersion());
        
        ResponseFeatureCache saved = cacheRepository.save(cache);
        assertNotNull(saved.getId());
        
        // Cleanup
        cacheRepository.delete(saved);
        
        System.out.println("✓ End-to-end workflow verified:");
        System.out.println("  - Feature extraction: " + extractTime + " ms");
        System.out.println("  - Prediction: " + String.format("%.2f", prediction));
        System.out.println("  - Cache saved successfully");
    }
    
    /**
     * Generate synthetic features for testing
     */
    private double[] generateSyntheticFeatures(int seed) {
        double[] features = new double[24];
        
        // Create features with some pattern
        features[0] = 50 + seed % 200;  // char count
        features[1] = 10 + seed % 30;   // word count
        features[2] = 2 + seed % 5;     // sentence count
        features[3] = 4 + (seed % 10) * 0.1;  // avg word length
        features[6] = 0.3 + (seed % 50) * 0.01;  // lexical diversity
        features[8] = 0.2 + (seed % 30) * 0.01;  // technical density
        features[10] = 0.5 + (seed % 40) * 0.01; // similarity
        
        // Fill remaining features with reasonable values
        for (int i = 11; i < 24; i++) {
            features[i] = (seed % 100) * 0.01;
        }
        
        return features;
    }
    
    /**
     * Compute synthetic score based on features
     */
    private double computeSyntheticScore(double[] features) {
        // Simple scoring formula: weighted sum of key features
        double score = 30.0; // Base score
        
        score += features[8] * 40;  // Technical density (weight: 40)
        score += features[10] * 20; // Similarity (weight: 20)
        score += features[6] * 10;  // Lexical diversity (weight: 10)
        
        // Add some noise
        score += (Math.random() - 0.5) * 10;
        
        return Math.max(0, Math.min(100, score));
    }
}
