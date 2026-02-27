package com.aiinterview.ml.adaptive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Candidate ability estimator using 2PL IRT with Bayesian EAP updating
 * Implements Expected A Posteriori (EAP) estimation with numerical integration
 */
@Slf4j
@Service
public class CandidateAbilityEstimator {
    
    private static final double THETA_MIN = -3.0;
    private static final double THETA_MAX = 3.0;
    private static final int QUADRATURE_POINTS = 61;
    private static final double PRIOR_MEAN = 0.0;
    private static final double PRIOR_SD = 1.0;
    
    /**
     * Estimate ability from complete response history
     * Uses EAP with Gaussian quadrature
     */
    public AbilityEstimate estimateAbility(List<ResponseRecord> responseHistory) {
        if (responseHistory == null || responseHistory.isEmpty()) {
            return AbilityEstimate.createPrior();
        }
        
        log.debug("Estimating ability from {} responses", responseHistory.size());
        
        // Setup quadrature points
        double[] thetaPoints = createQuadraturePoints();
        double[] posterior = new double[thetaPoints.length];
        
        // Compute posterior at each theta point
        for (int i = 0; i < thetaPoints.length; i++) {
            double theta = thetaPoints[i];
            posterior[i] = computePosterior(theta, responseHistory);
        }
        
        // Normalize posterior
        double sum = 0.0;
        for (double p : posterior) {
            sum += p;
        }
        for (int i = 0; i < posterior.length; i++) {
            posterior[i] /= sum;
        }
        
        // Compute EAP estimate
        double thetaEAP = 0.0;
        for (int i = 0; i < thetaPoints.length; i++) {
            thetaEAP += thetaPoints[i] * posterior[i];
        }
        
        // Compute variance
        double variance = 0.0;
        for (int i = 0; i < thetaPoints.length; i++) {
            double diff = thetaPoints[i] - thetaEAP;
            variance += diff * diff * posterior[i];
        }
        
        double standardError = Math.sqrt(variance);
        
        log.debug("Ability estimate: theta={}, SE={}", thetaEAP, standardError);
        
        return AbilityEstimate.builder()
            .theta(thetaEAP)
            .standardError(standardError)
            .confidenceLower(thetaEAP - 1.96 * standardError)
            .confidenceUpper(thetaEAP + 1.96 * standardError)
            .responsesUsed(responseHistory.size())
            .build();
    }
    
    /**
     * Update ability estimate with new response (incremental)
     * More efficient than recomputing from scratch
     */
    public AbilityEstimate updateAbility(
            AbilityEstimate prior,
            ResponseRecord newResponse) {
        
        if (prior == null) {
            prior = AbilityEstimate.createPrior();
        }
        
        log.debug("Updating ability: prior theta={}, SE={}", 
                  prior.getTheta(), prior.getStandardError());
        
        // Setup quadrature points
        double[] thetaPoints = createQuadraturePoints();
        double[] posterior = new double[thetaPoints.length];
        
        // Compute posterior combining prior and new likelihood
        for (int i = 0; i < thetaPoints.length; i++) {
            double theta = thetaPoints[i];
            
            // Prior density (Gaussian around prior estimate)
            double priorDensity = gaussianDensity(
                theta, 
                prior.getTheta(), 
                prior.getStandardError()
            );
            
            // Likelihood from new response
            double likelihood = computeLikelihood(theta, newResponse);
            
            posterior[i] = priorDensity * likelihood;
        }
        
        // Normalize
        double sum = 0.0;
        for (double p : posterior) {
            sum += p;
        }
        if (sum > 0) {
            for (int i = 0; i < posterior.length; i++) {
                posterior[i] /= sum;
            }
        }
        
        // Compute updated EAP
        double thetaEAP = 0.0;
        for (int i = 0; i < thetaPoints.length; i++) {
            thetaEAP += thetaPoints[i] * posterior[i];
        }
        
        // Compute variance
        double variance = 0.0;
        for (int i = 0; i < thetaPoints.length; i++) {
            double diff = thetaPoints[i] - thetaEAP;
            variance += diff * diff * posterior[i];
        }
        
        double standardError = Math.sqrt(variance);
        
        log.debug("Updated ability: theta={}, SE={}", thetaEAP, standardError);
        
        return AbilityEstimate.builder()
            .theta(thetaEAP)
            .standardError(standardError)
            .confidenceLower(thetaEAP - 1.96 * standardError)
            .confidenceUpper(thetaEAP + 1.96 * standardError)
            .responsesUsed(prior.getResponsesUsed() + 1)
            .build();
    }
    
    /**
     * Compute posterior probability at given theta
     * Combines prior and likelihood from all responses
     */
    private double computePosterior(double theta, List<ResponseRecord> responses) {
        // Prior (standard normal)
        double logPosterior = logGaussianDensity(theta, PRIOR_MEAN, PRIOR_SD);
        
        // Likelihood from each response
        for (ResponseRecord response : responses) {
            logPosterior += logLikelihood(theta, response);
        }
        
        return Math.exp(logPosterior);
    }
    
    /**
     * Compute likelihood of response given ability
     * Uses 2PL IRT model
     */
    private double computeLikelihood(double theta, ResponseRecord response) {
        double a = response.getDiscriminationA();
        double b = response.getDifficultyB();
        double score = response.getScore();
        
        // 2PL probability
        double z = a * (theta - b);
        double p = 1.0 / (1.0 + Math.exp(-z));
        
        // Likelihood (treat score as probability of correctness)
        return Math.pow(p, score) * Math.pow(1.0 - p, 1.0 - score);
    }
    
    /**
     * Compute log-likelihood for numerical stability
     */
    private double logLikelihood(double theta, ResponseRecord response) {
        double a = response.getDiscriminationA();
        double b = response.getDifficultyB();
        double score = response.getScore();
        
        double z = a * (theta - b);
        double p = 1.0 / (1.0 + Math.exp(-z));
        
        // Clamp probability to avoid log(0)
        p = Math.max(0.0001, Math.min(0.9999, p));
        
        return score * Math.log(p) + (1.0 - score) * Math.log(1.0 - p);
    }
    
    /**
     * Create evenly spaced quadrature points for numerical integration
     */
    private double[] createQuadraturePoints() {
        double[] points = new double[QUADRATURE_POINTS];
        double step = (THETA_MAX - THETA_MIN) / (QUADRATURE_POINTS - 1);
        
        for (int i = 0; i < QUADRATURE_POINTS; i++) {
            points[i] = THETA_MIN + i * step;
        }
        
        return points;
    }
    
    /**
     * Gaussian probability density
     */
    private double gaussianDensity(double x, double mean, double sd) {
        double z = (x - mean) / sd;
        return Math.exp(-0.5 * z * z) / (sd * Math.sqrt(2.0 * Math.PI));
    }
    
    /**
     * Log Gaussian density for numerical stability
     */
    private double logGaussianDensity(double x, double mean, double sd) {
        double z = (x - mean) / sd;
        return -0.5 * z * z - Math.log(sd * Math.sqrt(2.0 * Math.PI));
    }
}
