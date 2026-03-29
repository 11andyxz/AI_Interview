package com.aiinterview.ml.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Platt Scaling Calibrator for probability calibration.
 * 
 * Platt scaling fits a logistic regression model on top of the raw prediction scores
 * to calibrate predicted probabilities, reducing Brier score and improving calibration.
 * 
 * Reference: Platt, J. (1999). "Probabilistic Outputs for Support Vector Machines"
 * 
 * Y = 1 / (1 + exp(A * score + B))
 * 
 * Where A and B are learned parameters that minimize log-loss on a validation set.
 * 
 * Week 17 P1 Task 3: Address weak slice RMSE (junior profiles, target 12.4 → ≤11.5)
 */
@Component
public class PlattCalibrator {
    
    private static final Logger logger = LoggerFactory.getLogger(PlattCalibrator.class);
    
    // Platt scaling parameters (learned from validation data)
    private double paramA = -1.0;  // Slope parameter
    private double paramB = 0.0;   // Intercept parameter
    
    // Training parameters
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 1e-6;
    private static final double LEARNING_RATE = 0.01;
    
    // Regularization to prevent overfitting
    private static final double L2_LAMBDA = 0.01;
    
    /**
     * Fit Platt scaling parameters using validation data.
     * 
     * @param rawScores Raw prediction scores (uncalibrated)
     * @param trueLabels True binary labels (0 or 1)
     */
    public void fit(List<Double> rawScores, List<Integer> trueLabels) {
        if (rawScores == null || trueLabels == null || rawScores.size() != trueLabels.size()) {
            throw new IllegalArgumentException("Raw scores and true labels must have same length");
        }
        
        if (rawScores.isEmpty()) {
            logger.warn("No data provided for Platt scaling calibration");
            return;
        }
        
        int n = rawScores.size();
        logger.info("Fitting Platt scaling with {} samples", n);
        
        // Initialize parameters
        double A = -1.0;
        double B = 0.0;
        
        // Gradient descent to minimize log-loss
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double lossSum = 0.0;
            double gradA = 0.0;
            double gradB = 0.0;
            
            for (int i = 0; i < n; i++) {
                double score = rawScores.get(i);
                int label = trueLabels.get(i);
                
                // Calibrated probability: p = 1 / (1 + exp(A*score + B))
                double z = A * score + B;
                double p = sigmoid(-z);  // Note: sigmoid(-z) = 1 / (1 + exp(z))
                
                // Clip to avoid log(0)
                p = Math.max(1e-15, Math.min(1.0 - 1e-15, p));
                
                // Log-loss contribution
                lossSum += -label * Math.log(p) - (1 - label) * Math.log(1 - p);
                
                // Gradients
                double error = p - label;
                gradA += error * score;
                gradB += error;
            }
            
            // Add L2 regularization
            double loss = lossSum / n + L2_LAMBDA * (A * A + B * B);
            gradA = gradA / n + 2 * L2_LAMBDA * A;
            gradB = gradB / n + 2 * L2_LAMBDA * B;
            
            // Check convergence
            double gradNorm = Math.sqrt(gradA * gradA + gradB * gradB);
            if (gradNorm < CONVERGENCE_THRESHOLD) {
                logger.info("Platt scaling converged at iteration {} (loss={:.4f})", iteration, loss);
                break;
            }
            
            // Update parameters
            A -= LEARNING_RATE * gradA;
            B -= LEARNING_RATE * gradB;
            
            if (iteration % 20 == 0) {
                logger.debug("Iteration {}: loss={:.4f}, A={:.4f}, B={:.4f}", iteration, loss, A, B);
            }
        }
        
        // Store learned parameters
        this.paramA = A;
        this.paramB = B;
        
        logger.info("Platt scaling fitted: A={:.4f}, B={:.4f}", this.paramA, this.paramB);
    }
    
    /**
     * Calibrate a raw score to a calibrated probability.
     * 
     * @param rawScore Raw prediction score
     * @return Calibrated probability (0.0 to 1.0)
     */
    public double calibrate(double rawScore) {
        double z = paramA * rawScore + paramB;
        return sigmoid(-z);
    }
    
    /**
     * Calibrate a batch of raw scores.
     * 
     * @param rawScores List of raw prediction scores
     * @return List of calibrated probabilities
     */
    public List<Double> calibrateBatch(List<Double> rawScores) {
        return rawScores.stream()
                .map(this::calibrate)
                .toList();
    }
    
    /**
     * Set parameters manually (for pre-trained calibrators).
     * 
     * @param A Slope parameter
     * @param B Intercept parameter
     */
    public void setParameters(double A, double B) {
        this.paramA = A;
        this.paramB = B;
        logger.info("Platt scaling parameters set manually: A={:.4f}, B={:.4f}", A, B);
    }
    
    /**
     * Get current parameters.
     * 
     * @return Array [A, B]
     */
    public double[] getParameters() {
        return new double[]{paramA, paramB};
    }
    
    /**
     * Evaluate calibration quality using Expected Calibration Error (ECE).
     * 
     * @param calibratedProbs Calibrated probabilities
     * @param trueLabels True binary labels (0 or 1)
     * @param nBins Number of bins for ECE calculation (default: 10)
     * @return Expected Calibration Error (lower is better)
     */
    public double evaluateCalibration(List<Double> calibratedProbs, List<Integer> trueLabels, int nBins) {
        if (calibratedProbs.size() != trueLabels.size()) {
            throw new IllegalArgumentException("Probabilities and labels must have same length");
        }
        
        int n = calibratedProbs.size();
        double[] binLower = new double[nBins];
        double[] binUpper = new double[nBins];
        int[] binCounts = new int[nBins];
        double[] binTrueFreq = new double[nBins];
        double[] binPredFreq = new double[nBins];
        
        // Initialize bins
        for (int i = 0; i < nBins; i++) {
            binLower[i] = (double) i / nBins;
            binUpper[i] = (double) (i + 1) / nBins;
        }
        
        // Assign samples to bins
        for (int i = 0; i < n; i++) {
            double prob = calibratedProbs.get(i);
            int label = trueLabels.get(i);
            
            // Find bin
            int binIndex = Math.min((int) (prob * nBins), nBins - 1);
            
            binCounts[binIndex]++;
            binTrueFreq[binIndex] += label;
            binPredFreq[binIndex] += prob;
        }
        
        // Compute ECE
        double ece = 0.0;
        for (int i = 0; i < nBins; i++) {
            if (binCounts[i] > 0) {
                double avgTrue = binTrueFreq[i] / binCounts[i];
                double avgPred = binPredFreq[i] / binCounts[i];
                double weight = (double) binCounts[i] / n;
                
                ece += weight * Math.abs(avgPred - avgTrue);
            }
        }
        
        logger.info("Expected Calibration Error (ECE): {:.4f}", ece);
        return ece;
    }
    
    /**
     * Sigmoid function: 1 / (1 + exp(-x))
     */
    private double sigmoid(double x) {
        // Numerically stable version
        if (x >= 0) {
            double z = Math.exp(-x);
            return 1.0 / (1.0 + z);
        } else {
            double z = Math.exp(x);
            return z / (1.0 + z);
        }
    }
    
    /**
     * Check if calibrator has been fitted.
     * 
     * @return true if parameters have been learned
     */
    public boolean isFitted() {
        // Default parameters are A=-1, B=0, so check if they've changed
        return paramA != -1.0 || paramB != 0.0;
    }
}
