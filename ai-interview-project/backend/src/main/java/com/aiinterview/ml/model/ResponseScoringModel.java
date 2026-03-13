package com.aiinterview.ml.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

/**
 * Gradient Boosted Regression Trees (GBRT) model for response scoring.
 * Pure Java implementation without external ML libraries.
 */
@Component
@ConditionalOnProperty(name = "ml.nlp.enabled", havingValue = "true", matchIfMissing = false)
public class ResponseScoringModel implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ResponseScoringModel.class);
    
    // Model hyperparameters
    private static final int N_ESTIMATORS = 50;      // Number of trees
    private static final int MAX_DEPTH = 5;          // Max tree depth
    private static final double LEARNING_RATE = 0.1; // Shrinkage parameter
    private static final double L2_LAMBDA = 1.0;     // L2 regularization
    private static final int MIN_SAMPLES_LEAF = 5;   // Min samples per leaf
    
    private List<DecisionTree> trees;
    private double initialPrediction;
    private String modelVersion;
    private boolean isTrained;
    
    // Training statistics
    private double trainR2;
    private double trainRmse;
    private Map<Integer, Double> featureImportance;
    
    public ResponseScoringModel() {
        this.trees = new ArrayList<>();
        this.isTrained = false;
        this.modelVersion = "v1.0";
    }
    
    /**
     * Train the GBRT model on labeled data.
     * 
     * @param features List of feature vectors
     * @param scores List of ground truth scores (0-100)
     */
    public void train(List<double[]> features, List<Double> scores) {
        if (features.size() != scores.size()) {
            throw new IllegalArgumentException("Features and scores must have same size");
        }
        
        if (features.isEmpty()) {
            throw new IllegalArgumentException("Training data cannot be empty");
        }
        
        logger.info("Starting GBRT training with {} samples, {} features", 
                   features.size(), features.get(0).length);
        
        long startTime = System.currentTimeMillis();
        
        // Initialize predictions with mean
        this.initialPrediction = scores.stream().mapToDouble(Double::doubleValue).average().orElse(50.0);
        
        // Initialize residuals
        List<Double> predictions = new ArrayList<>(Collections.nCopies(scores.size(), initialPrediction));
        List<Double> residuals = new ArrayList<>();
        
        for (int i = 0; i < scores.size(); i++) {
            residuals.add(scores.get(i) - predictions.get(i));
        }
        
        // Build trees iteratively
        this.trees.clear();
        this.featureImportance = new HashMap<>();
        
        for (int treeIdx = 0; treeIdx < N_ESTIMATORS; treeIdx++) {
            // Train tree on residuals
            DecisionTree tree = new DecisionTree(MAX_DEPTH, L2_LAMBDA, MIN_SAMPLES_LEAF);
            tree.train(features, residuals);
            this.trees.add(tree);
            
            // Update predictions and residuals
            for (int i = 0; i < features.size(); i++) {
                double treePrediction = tree.predict(features.get(i));
                predictions.set(i, predictions.get(i) + LEARNING_RATE * treePrediction);
                residuals.set(i, scores.get(i) - predictions.get(i));
            }
            
            // Log progress every 10 trees
            if ((treeIdx + 1) % 10 == 0) {
                double currentRmse = computeRmse(predictions, scores);
                logger.info("Tree {}/{} trained, RMSE: {:.2f}", treeIdx + 1, N_ESTIMATORS, currentRmse);
            }
        }
        
        // Compute final statistics
        this.trainRmse = computeRmse(predictions, scores);
        this.trainR2 = computeR2(predictions, scores);
        
        long trainingTime = System.currentTimeMillis() - startTime;
        
        logger.info("GBRT training complete in {}ms", trainingTime);
        logger.info("Training RMSE: {:.2f}, R²: {:.4f}", trainRmse, trainR2);
        
        this.isTrained = true;
    }
    
    /**
     * Predict score for a single response.
     * 
     * @param features Feature vector extracted from response
     * @return Predicted score (0-100)
     */
    public double predict(double[] features) {
        if (!isTrained) {
            throw new IllegalStateException("Model has not been trained");
        }
        
        double prediction = initialPrediction;
        
        for (DecisionTree tree : trees) {
            prediction += LEARNING_RATE * tree.predict(features);
        }
        
        // Clip to valid range [0, 100]
        return Math.max(0.0, Math.min(100.0, prediction));
    }
    
    /**
     * Batch prediction for multiple responses
     */
    public List<Double> predictBatch(List<double[]> featuresList) {
        List<Double> predictions = new ArrayList<>();
        for (double[] features : featuresList) {
            predictions.add(predict(features));
        }
        return predictions;
    }
    
    /**
     * Compute RMSE between predictions and ground truth
     */
    private double computeRmse(List<Double> predictions, List<Double> actual) {
        double sumSquaredError = 0.0;
        
        for (int i = 0; i < predictions.size(); i++) {
            double error = predictions.get(i) - actual.get(i);
            sumSquaredError += error * error;
        }
        
        return Math.sqrt(sumSquaredError / predictions.size());
    }
    
    /**
     * Compute R² (coefficient of determination)
     */
    private double computeR2(List<Double> predictions, List<Double> actual) {
        double mean = actual.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double ssTot = 0.0;  // Total sum of squares
        double ssRes = 0.0;  // Residual sum of squares
        
        for (int i = 0; i < actual.size(); i++) {
            double actualVal = actual.get(i);
            double predVal = predictions.get(i);
            
            ssTot += (actualVal - mean) * (actualVal - mean);
            ssRes += (actualVal - predVal) * (actualVal - predVal);
        }
        
        if (ssTot == 0.0) return 0.0;
        
        return 1.0 - (ssRes / ssTot);
    }
    
    /**
     * Compute feature importance based on tree splits
     */
    public Map<String, Double> getFeatureImportance(String[] featureNames) {
        if (!isTrained) {
            throw new IllegalStateException("Model has not been trained");
        }
        
        // Simplified feature importance (could be enhanced with proper gain tracking)
        Map<String, Double> importance = new HashMap<>();
        
        for (int i = 0; i < featureNames.length; i++) {
            importance.put(featureNames[i], 1.0 / featureNames.length);
        }
        
        return importance;
    }
    
    /**
     * Save model to file
     */
    public void save(String filePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(this);
            logger.info("Model saved to {}", filePath);
        }
    }
    
    /**
     * Load model from file
     */
    public static ResponseScoringModel load(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            ResponseScoringModel model = (ResponseScoringModel) ois.readObject();
            logger.info("Model loaded from {}", filePath);
            return model;
        }
    }
    
    /**
     * Evaluate model on test data
     */
    public EvaluationMetrics evaluate(List<double[]> features, List<Double> scores) {
        if (!isTrained) {
            throw new IllegalStateException("Model has not been trained");
        }
        
        List<Double> predictions = predictBatch(features);
        
        double rmse = computeRmse(predictions, scores);
        double r2 = computeR2(predictions, scores);
        
        // Compute MAE (Mean Absolute Error)
        double sumAbsError = 0.0;
        for (int i = 0; i < predictions.size(); i++) {
            sumAbsError += Math.abs(predictions.get(i) - scores.get(i));
        }
        double mae = sumAbsError / predictions.size();
        
        return new EvaluationMetrics(rmse, r2, mae);
    }
    
    /**
     * Check if model meets acceptance criteria
     */
    public boolean meetsAcceptanceCriteria() {
        return isTrained && trainR2 > 0.5 && trainRmse < 15.0;
    }
    
    // Getters
    
    public boolean isTrained() {
        return isTrained;
    }
    
    public double getTrainR2() {
        return trainR2;
    }
    
    public double getTrainRmse() {
        return trainRmse;
    }
    
    public String getModelVersion() {
        return modelVersion;
    }
    
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
    
    public int getTreeCount() {
        return trees.size();
    }
    
    /**
     * Evaluation metrics container
     */
    public static class EvaluationMetrics {
        public final double rmse;
        public final double r2;
        public final double mae;
        
        public EvaluationMetrics(double rmse, double r2, double mae) {
            this.rmse = rmse;
            this.r2 = r2;
            this.mae = mae;
        }
        
        @Override
        public String toString() {
            return String.format("RMSE=%.2f, R²=%.4f, MAE=%.2f", rmse, r2, mae);
        }
    }
}
