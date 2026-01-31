package com.aiinterview.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Semantic Drift Detection using OpenAI Embeddings and K-means Clustering
 * 
 * Monitors semantic shifts in AI responses by:
 * 1. Getting embeddings for responses using OpenAI API
 * 2. Clustering embeddings with K-means (k=5)
 * 3. Comparing cluster centers: current week vs 30-day baseline
 * 4. Alert if average center distance > threshold (0.15)
 */
@Service
public class SemanticDriftDetector {
    private static final Logger logger = LoggerFactory.getLogger(SemanticDriftDetector.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private WebClient openAiWebClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;
    
    private static final int NUM_CLUSTERS = 5;
    private static final double DRIFT_THRESHOLD = 0.15;
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.001;
    
    /**
     * Get embedding vector from OpenAI API
     */
    public double[] getEmbedding(String text) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);
            
            String response = openAiWebClient.post()
                .uri("/embeddings")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");
            
            double[] embedding = new double[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = embeddingNode.get(i).asDouble();
            }
            
            return embedding;
        } catch (Exception e) {
            logger.error("Failed to get embedding: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Fetch recent AI responses for semantic analysis
     */
    public List<String> fetchResponses(int days) {
        String sql = "SELECT response FROM ai_responses WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY) LIMIT 500";
        try {
            return jdbcTemplate.queryForList(sql, String.class, days);
        } catch (Exception e) {
            logger.warn("ai_responses table may not exist, using mock data");
            return Collections.emptyList();
        }
    }
    
    /**
     * K-means clustering algorithm
     * Returns cluster centers
     */
    public List<double[]> kMeansClustering(List<double[]> embeddings, int k) {
        if (embeddings.isEmpty()) {
            return Collections.emptyList();
        }
        
        int dimensions = embeddings.get(0).length;
        Random random = new Random(42); // Fixed seed for reproducibility
        
        // Initialize centers randomly
        List<double[]> centers = new ArrayList<>();
        for (int i = 0; i < k && i < embeddings.size(); i++) {
            centers.add(embeddings.get(random.nextInt(embeddings.size())).clone());
        }
        
        // Iterate until convergence
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // Assign points to nearest center
            Map<Integer, List<double[]>> clusters = new HashMap<>();
            for (int i = 0; i < k; i++) {
                clusters.put(i, new ArrayList<>());
            }
            
            for (double[] embedding : embeddings) {
                int nearestCenter = findNearestCenter(embedding, centers);
                clusters.get(nearestCenter).add(embedding);
            }
            
            // Update centers
            List<double[]> newCenters = new ArrayList<>();
            double maxShift = 0.0;
            
            for (int i = 0; i < k; i++) {
                List<double[]> cluster = clusters.get(i);
                if (cluster.isEmpty()) {
                    newCenters.add(centers.get(i)); // Keep old center
                } else {
                    double[] newCenter = computeCentroid(cluster, dimensions);
                    newCenters.add(newCenter);
                    double shift = euclideanDistance(centers.get(i), newCenter);
                    maxShift = Math.max(maxShift, shift);
                }
            }
            
            centers = newCenters;
            
            // Check convergence
            if (maxShift < CONVERGENCE_THRESHOLD) {
                logger.info("K-means converged after {} iterations", iter + 1);
                break;
            }
        }
        
        return centers;
    }
    
    /**
     * Find nearest cluster center for a point
     */
    private int findNearestCenter(double[] point, List<double[]> centers) {
        int nearest = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < centers.size(); i++) {
            double distance = euclideanDistance(point, centers.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                nearest = i;
            }
        }
        
        return nearest;
    }
    
    /**
     * Compute centroid of a cluster
     */
    private double[] computeCentroid(List<double[]> points, int dimensions) {
        double[] centroid = new double[dimensions];
        
        for (double[] point : points) {
            for (int i = 0; i < dimensions; i++) {
                centroid[i] += point[i];
            }
        }
        
        for (int i = 0; i < dimensions; i++) {
            centroid[i] /= points.size();
        }
        
        return centroid;
    }
    
    /**
     * Calculate Euclidean distance between two vectors
     */
    private double euclideanDistance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same dimensions");
        }
        
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        
        return Math.sqrt(sum);
    }
    
    /**
     * Calculate average distance between two sets of cluster centers
     */
    public double calculateCenterDistance(List<double[]> centers1, List<double[]> centers2) {
        if (centers1.size() != centers2.size()) {
            logger.warn("Cluster sizes differ: {} vs {}", centers1.size(), centers2.size());
            return 0.0;
        }
        
        double totalDistance = 0.0;
        for (int i = 0; i < centers1.size(); i++) {
            totalDistance += euclideanDistance(centers1.get(i), centers2.get(i));
        }
        
        return totalDistance / centers1.size();
    }
    
    /**
     * Detect semantic drift: compare current week vs 30-day baseline
     */
    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
    public void detectSemanticDrift() {
        logger.info("Starting semantic drift detection...");
        
        try {
            // Fetch responses
            List<String> currentResponses = fetchResponses(7); // Last 7 days
            List<String> baselineResponses = fetchResponses(30); // Last 30 days
            
            if (currentResponses.isEmpty() || baselineResponses.isEmpty()) {
                logger.warn("Insufficient data for semantic drift detection");
                return;
            }
            
            // Sample for efficiency (max 100 responses each)
            currentResponses = sampleList(currentResponses, 100);
            baselineResponses = sampleList(baselineResponses, 100);
            
            // Get embeddings
            logger.info("Generating embeddings for {} current and {} baseline responses", 
                currentResponses.size(), baselineResponses.size());
            
            List<double[]> currentEmbeddings = currentResponses.stream()
                .map(this::getEmbedding)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            List<double[]> baselineEmbeddings = baselineResponses.stream()
                .map(this::getEmbedding)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            if (currentEmbeddings.size() < NUM_CLUSTERS || baselineEmbeddings.size() < NUM_CLUSTERS) {
                logger.warn("Not enough embeddings for clustering");
                return;
            }
            
            // Perform clustering
            logger.info("Clustering current embeddings...");
            List<double[]> currentCenters = kMeansClustering(currentEmbeddings, NUM_CLUSTERS);
            
            logger.info("Clustering baseline embeddings...");
            List<double[]> baselineCenters = kMeansClustering(baselineEmbeddings, NUM_CLUSTERS);
            
            // Calculate drift
            double driftDistance = calculateCenterDistance(currentCenters, baselineCenters);
            
            logger.info("Semantic drift distance: {}", driftDistance);
            
            // Store metric
            String sql = "INSERT INTO ai_metrics_log (timestamp, metric_name, metric_value, model_version, endpoint, tags) " +
                        "VALUES (?, 'semantic_drift', ?, 'current', 'all', ?)";
            jdbcTemplate.update(sql, 
                LocalDateTime.now().toString(),
                driftDistance,
                "{\"type\":\"embedding_based\",\"k\":\"" + NUM_CLUSTERS + "\"}");
            
            // Alert if drift exceeds threshold
            if (driftDistance > DRIFT_THRESHOLD) {
                logger.warn("⚠️ SEMANTIC DRIFT DETECTED: {} > threshold {}", driftDistance, DRIFT_THRESHOLD);
                // AlertService will pick this up from metrics
            }
            
        } catch (Exception e) {
            logger.error("Semantic drift detection failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sample a list to reduce size
     */
    private <T> List<T> sampleList(List<T> list, int maxSize) {
        if (list.size() <= maxSize) {
            return list;
        }
        
        List<T> sampled = new ArrayList<>();
        Random random = new Random(42);
        Set<Integer> indices = new HashSet<>();
        
        while (indices.size() < maxSize) {
            indices.add(random.nextInt(list.size()));
        }
        
        for (int idx : indices) {
            sampled.add(list.get(idx));
        }
        
        return sampled;
    }
    
    /**
     * Manual trigger for testing
     */
    public Map<String, Object> runSemanticDriftCheck() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<String> currentResponses = fetchResponses(7);
            List<String> baselineResponses = fetchResponses(30);
            
            currentResponses = sampleList(currentResponses, 50);
            baselineResponses = sampleList(baselineResponses, 50);
            
            List<double[]> currentEmbeddings = currentResponses.stream()
                .map(this::getEmbedding)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            List<double[]> baselineEmbeddings = baselineResponses.stream()
                .map(this::getEmbedding)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            if (currentEmbeddings.isEmpty() || baselineEmbeddings.isEmpty()) {
                result.put("status", "insufficient_data");
                result.put("message", "Not enough responses for semantic drift analysis");
                return result;
            }
            
            List<double[]> currentCenters = kMeansClustering(currentEmbeddings, NUM_CLUSTERS);
            List<double[]> baselineCenters = kMeansClustering(baselineEmbeddings, NUM_CLUSTERS);
            
            double driftDistance = calculateCenterDistance(currentCenters, baselineCenters);
            
            result.put("status", "success");
            result.put("semantic_drift_distance", driftDistance);
            result.put("threshold", DRIFT_THRESHOLD);
            result.put("drift_detected", driftDistance > DRIFT_THRESHOLD);
            result.put("current_samples", currentEmbeddings.size());
            result.put("baseline_samples", baselineEmbeddings.size());
            result.put("num_clusters", NUM_CLUSTERS);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        return result;
    }
}
