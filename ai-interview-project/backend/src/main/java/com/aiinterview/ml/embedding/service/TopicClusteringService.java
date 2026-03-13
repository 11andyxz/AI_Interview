package com.aiinterview.ml.embedding.service;

import com.aiinterview.ml.embedding.entity.QuestionEmbedding;
import com.aiinterview.ml.embedding.repository.QuestionEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Topic clustering service using K-means++ algorithm
 * Optimizes K using Silhouette score and labels clusters with TF-IDF
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ml.embedding.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class TopicClusteringService {
    
    private final QuestionEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    
    private static final int MIN_K = 3;
    private static final int MAX_K = 10;
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 1e-4;
    private static final double MIN_SILHOUETTE_SCORE = 0.3;
    
    /**
     * Cluster questions for a role with automatic K optimization
     */
    @Transactional
    public ClusteringResult clusterQuestions(String roleId) {
        log.info("Starting clustering for role: {}", roleId);
        
        Long roleIdLong = Long.parseLong(roleId);
        List<QuestionEmbedding> embeddings = embeddingRepository.findByRoleId(roleIdLong);
        
        if (embeddings.size() < MIN_K) {
            log.warn("Insufficient questions for clustering: {} < {}", embeddings.size(), MIN_K);
            return ClusteringResult.builder()
                .optimalK(1)
                .silhouetteScore(0.0)
                .questionsCount(embeddings.size())
                .clusters(Collections.emptyList())
                .build();
        }
        
        // Extract vectors
        List<double[]> vectors = embeddings.stream()
            .map(QuestionEmbedding::getEmbeddingVector)
            .collect(Collectors.toList());
        
        // Find optimal K
        int optimalK = findOptimalK(vectors);
        log.info("Optimal K for role {}: {}", roleId, optimalK);
        
        // Perform K-means++ clustering
        KMeansResult kmeansResult = kMeansPlusPlus(vectors, optimalK);
        
        // Assign clusters to database entities
        for (int i = 0; i < embeddings.size(); i++) {
            QuestionEmbedding embedding = embeddings.get(i);
            embedding.setClusterId(kmeansResult.assignments[i]);
        }
        
        // Generate cluster labels using TF-IDF
        Map<Integer, String> clusterLabels = generateClusterLabels(embeddings, kmeansResult.assignments, optimalK);
        
        for (QuestionEmbedding embedding : embeddings) {
            embedding.setClusterLabel(clusterLabels.get(embedding.getClusterId()));
        }
        
        embeddingRepository.saveAll(embeddings);
        
        // Build result
        List<ClusterInfo> clusters = new ArrayList<>();
        for (int k = 0; k < optimalK; k++) {
            int finalK = k;
            List<String> questionIds = embeddings.stream()
                .filter(e -> e.getClusterId() == finalK)
                .map(QuestionEmbedding::getQuestionId)
                .collect(Collectors.toList());
            
            clusters.add(ClusterInfo.builder()
                .clusterId(k)
                .label(clusterLabels.get(k))
                .size(questionIds.size())
                .questionIds(questionIds)
                .build());
        }
        
        return ClusteringResult.builder()
            .optimalK(optimalK)
            .silhouetteScore(kmeansResult.silhouetteScore)
            .questionsCount(embeddings.size())
            .clusters(clusters)
            .build();
    }
    
    /**
     * Find optimal K using Silhouette score
     */
    private int findOptimalK(List<double[]> vectors) {
        int maxK = Math.min(MAX_K, vectors.size() / 2);
        double bestScore = -1.0;
        int bestK = MIN_K;
        
        for (int k = MIN_K; k <= maxK; k++) {
            KMeansResult result = kMeansPlusPlus(vectors, k);
            double score = result.silhouetteScore;
            
            log.debug("K={}, Silhouette={}", k, score);
            
            if (score > bestScore) {
                bestScore = score;
                bestK = k;
            }
        }
        
        log.info("Best K: {}, Silhouette: {}", bestK, bestScore);
        
        if (bestScore < MIN_SILHOUETTE_SCORE) {
            log.warn("Best Silhouette score {} below threshold {}", bestScore, MIN_SILHOUETTE_SCORE);
        }
        
        return bestK;
    }
    
    /**
     * K-means++ clustering algorithm
     */
    private KMeansResult kMeansPlusPlus(List<double[]> vectors, int k) {
        int n = vectors.size();
        int dim = vectors.get(0).length;
        
        // Initialize centroids using K-means++
        double[][] centroids = initializeCentroidsPlusPlus(vectors, k);
        int[] assignments = new int[n];
        boolean converged = false;
        int iteration = 0;
        
        while (!converged && iteration < MAX_ITERATIONS) {
            // Assignment step
            int[] newAssignments = new int[n];
            for (int i = 0; i < n; i++) {
                newAssignments[i] = findNearestCentroid(vectors.get(i), centroids);
            }
            
            // Check convergence
            converged = Arrays.equals(assignments, newAssignments);
            assignments = newAssignments;
            
            if (converged) break;
            
            // Update step
            double[][] newCentroids = new double[k][dim];
            int[] counts = new int[k];
            
            for (int i = 0; i < n; i++) {
                int cluster = assignments[i];
                counts[cluster]++;
                for (int j = 0; j < dim; j++) {
                    newCentroids[cluster][j] += vectors.get(i)[j];
                }
            }
            
            for (int i = 0; i < k; i++) {
                if (counts[i] > 0) {
                    for (int j = 0; j < dim; j++) {
                        newCentroids[i][j] /= counts[i];
                    }
                } else {
                    // Re-initialize empty cluster
                    newCentroids[i] = vectors.get(new Random().nextInt(n)).clone();
                }
            }
            
            // Check centroid convergence
            double maxCentroidShift = 0.0;
            for (int i = 0; i < k; i++) {
                double shift = euclideanDistance(centroids[i], newCentroids[i]);
                maxCentroidShift = Math.max(maxCentroidShift, shift);
            }
            
            centroids = newCentroids;
            iteration++;
            
            if (maxCentroidShift < CONVERGENCE_THRESHOLD) {
                converged = true;
            }
        }
        
        log.debug("K-means converged after {} iterations", iteration);
        
        // Compute Silhouette score
        double silhouetteScore = computeSilhouetteScore(vectors, assignments, k);
        
        return new KMeansResult(centroids, assignments, silhouetteScore);
    }
    
    /**
     * K-means++ initialization
     */
    private double[][] initializeCentroidsPlusPlus(List<double[]> vectors, int k) {
        int n = vectors.size();
        int dim = vectors.get(0).length;
        double[][] centroids = new double[k][dim];
        Random random = new Random();
        
        // Choose first centroid randomly
        int firstIdx = random.nextInt(n);
        centroids[0] = vectors.get(firstIdx).clone();
        
        // Choose remaining centroids
        for (int i = 1; i < k; i++) {
            double[] distances = new double[n];
            double totalDistance = 0.0;
            
            // Compute distance to nearest centroid
            for (int j = 0; j < n; j++) {
                double minDist = Double.MAX_VALUE;
                for (int c = 0; c < i; c++) {
                    double dist = euclideanDistance(vectors.get(j), centroids[c]);
                    minDist = Math.min(minDist, dist);
                }
                distances[j] = minDist * minDist; // Squared distance
                totalDistance += distances[j];
            }
            
            // Choose next centroid with probability proportional to distance
            double threshold = random.nextDouble() * totalDistance;
            double cumulative = 0.0;
            int selectedIdx = 0;
            
            for (int j = 0; j < n; j++) {
                cumulative += distances[j];
                if (cumulative >= threshold) {
                    selectedIdx = j;
                    break;
                }
            }
            
            centroids[i] = vectors.get(selectedIdx).clone();
        }
        
        return centroids;
    }
    
    /**
     * Find nearest centroid to a vector
     */
    private int findNearestCentroid(double[] vector, double[][] centroids) {
        int nearest = 0;
        double minDist = euclideanDistance(vector, centroids[0]);
        
        for (int i = 1; i < centroids.length; i++) {
            double dist = euclideanDistance(vector, centroids[i]);
            if (dist < minDist) {
                minDist = dist;
                nearest = i;
            }
        }
        
        return nearest;
    }
    
    /**
     * Compute Silhouette score
     */
    private double computeSilhouetteScore(List<double[]> vectors, int[] assignments, int k) {
        int n = vectors.size();
        double totalSilhouette = 0.0;
        
        for (int i = 0; i < n; i++) {
            int cluster = assignments[i];
            double[] vector = vectors.get(i);
            
            // Compute average distance to points in same cluster (a)
            double a = 0.0;
            int sameClusterCount = 0;
            for (int j = 0; j < n; j++) {
                if (i != j && assignments[j] == cluster) {
                    a += euclideanDistance(vector, vectors.get(j));
                    sameClusterCount++;
                }
            }
            a = sameClusterCount > 0 ? a / sameClusterCount : 0.0;
            
            // Compute average distance to nearest other cluster (b)
            double b = Double.MAX_VALUE;
            for (int otherCluster = 0; otherCluster < k; otherCluster++) {
                if (otherCluster == cluster) continue;
                
                double avgDist = 0.0;
                int count = 0;
                for (int j = 0; j < n; j++) {
                    if (assignments[j] == otherCluster) {
                        avgDist += euclideanDistance(vector, vectors.get(j));
                        count++;
                    }
                }
                
                if (count > 0) {
                    avgDist /= count;
                    b = Math.min(b, avgDist);
                }
            }
            
            // Silhouette coefficient
            double s = (b - a) / Math.max(a, b);
            totalSilhouette += s;
        }
        
        return totalSilhouette / n;
    }
    
    /**
     * Generate cluster labels using TF-IDF
     */
    private Map<Integer, String> generateClusterLabels(
            List<QuestionEmbedding> embeddings, 
            int[] assignments, 
            int k) {
        
        Map<Integer, String> labels = new HashMap<>();
        
        for (int cluster = 0; cluster < k; cluster++) {
            // Get all questions in this cluster
            List<String> clusterTexts = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                if (assignments[i] == cluster) {
                    clusterTexts.add(embeddings.get(i).getQuestionText());
                }
            }
            
            if (clusterTexts.isEmpty()) {
                labels.put(cluster, "Cluster " + cluster);
                continue;
            }
            
            // Extract top TF-IDF terms
            Map<String, Double> tfidf = computeTFIDF(clusterTexts, embeddings);
            
            // Get top 3 terms
            String label = tfidf.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
            
            labels.put(cluster, label.isEmpty() ? "Cluster " + cluster : label);
        }
        
        return labels;
    }
    
    /**
     * Compute TF-IDF for cluster texts
     */
    private Map<String, Double> computeTFIDF(List<String> clusterTexts, List<QuestionEmbedding> allEmbeddings) {
        Map<String, Double> tfidf = new HashMap<>();
        
        // Tokenize cluster texts
        Map<String, Integer> termFreq = new HashMap<>();
        int totalTerms = 0;
        
        for (String text : clusterTexts) {
            String[] tokens = tokenize(text);
            for (String token : tokens) {
                termFreq.merge(token, 1, Integer::sum);
                totalTerms++;
            }
        }
        
        // Compute document frequency for all embeddings
        Map<String, Integer> docFreq = new HashMap<>();
        int totalDocs = allEmbeddings.size();
        
        for (QuestionEmbedding embedding : allEmbeddings) {
            Set<String> uniqueTokens = new HashSet<>(Arrays.asList(tokenize(embedding.getQuestionText())));
            for (String token : uniqueTokens) {
                docFreq.merge(token, 1, Integer::sum);
            }
        }
        
        // Compute TF-IDF
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            double tf = (double) entry.getValue() / totalTerms;
            double idf = Math.log((double) totalDocs / (1 + docFreq.getOrDefault(term, 0)));
            tfidf.put(term, tf * idf);
        }
        
        return tfidf;
    }
    
    /**
     * Simple tokenization
     */
    private String[] tokenize(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .split("\\s+");
    }
    
    /**
     * Euclidean distance between two vectors
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
    
    /**
     * K-means result holder
     */
    private static class KMeansResult {
        double[][] centroids;
        int[] assignments;
        double silhouetteScore;
        
        KMeansResult(double[][] centroids, int[] assignments, double silhouetteScore) {
            this.centroids = centroids;
            this.assignments = assignments;
            this.silhouetteScore = silhouetteScore;
        }
    }
    
    /**
     * Clustering result DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ClusteringResult {
        private int optimalK;
        private double silhouetteScore;
        private int questionsCount;
        private List<ClusterInfo> clusters;
    }
    
    /**
     * Cluster info DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ClusterInfo {
        private int clusterId;
        private String label;
        private int size;
        private List<String> questionIds;
    }
}
