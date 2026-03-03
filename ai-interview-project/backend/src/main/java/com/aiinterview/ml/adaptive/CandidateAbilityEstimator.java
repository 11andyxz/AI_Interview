package com.aiinterview.ml.adaptive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Estimates candidate ability (theta) using the 2-Parameter Logistic IRT model.
 *
 * The 2PL model: P(correct | theta, a, b) = 1 / (1 + exp(-a * (theta - b)))
 * where a = discrimination, b = difficulty, theta = ability.
 *
 * Two estimation methods:
 * - MLE via Newton-Raphson on log-likelihood (used when responses >= 5)
 * - EAP (Expected A Posteriori) Bayesian updating with standard normal prior
 */
@Service
public class CandidateAbilityEstimator {

    private static final Logger logger = LoggerFactory.getLogger(CandidateAbilityEstimator.class);

    private static final double THETA_MIN = -3.0;
    private static final double THETA_MAX = 3.0;
    private static final int MAX_NEWTON_ITERATIONS = 50;
    private static final double CONVERGENCE_THRESHOLD = 1e-6;
    private static final int EAP_QUADRATURE_POINTS = 61;

    /**
     * Estimate ability from full response history using MLE (Newton-Raphson) for 5+ responses,
     * or EAP for fewer responses where MLE may be unstable.
     */
    public AbilityEstimate estimateAbility(List<ResponseRecord> responseHistory) {
        if (responseHistory == null || responseHistory.isEmpty()) {
            return AbilityEstimate.prior();
        }

        if (responseHistory.size() < 5) {
            return estimateEAP(responseHistory);
        }
        return estimateMLE(responseHistory);
    }

    /**
     * Incremental Bayesian update: combine prior with a new response using EAP.
     */
    public AbilityEstimate updateAbility(AbilityEstimate prior, ResponseRecord newResponse) {
        double priorTheta = prior.getTheta();
        double priorSE = prior.getStandardError();

        double[] quadPoints = new double[EAP_QUADRATURE_POINTS];
        double[] weights = new double[EAP_QUADRATURE_POINTS];
        double step = (THETA_MAX - THETA_MIN) / (EAP_QUADRATURE_POINTS - 1);

        double numerator = 0;
        double denominator = 0;
        double secondMoment = 0;

        for (int i = 0; i < EAP_QUADRATURE_POINTS; i++) {
            double theta = THETA_MIN + i * step;
            quadPoints[i] = theta;

            double priorDensity = gaussianDensity(theta, priorTheta, priorSE);
            double likelihood = irtLikelihoodSingle(theta, newResponse);
            double posterior = priorDensity * likelihood;

            weights[i] = posterior;
            numerator += theta * posterior;
            denominator += posterior;
            secondMoment += theta * theta * posterior;
        }

        if (denominator == 0) return prior;

        double eapTheta = numerator / denominator;
        double eapVariance = (secondMoment / denominator) - (eapTheta * eapTheta);
        double eapSE = Math.sqrt(Math.max(eapVariance, 0.01));

        eapTheta = Math.max(THETA_MIN, Math.min(THETA_MAX, eapTheta));

        return new AbilityEstimate(eapTheta, eapSE, prior.getResponsesUsed() + 1);
    }

    /**
     * MLE via Newton-Raphson on the log-likelihood of the 2PL model.
     */
    private AbilityEstimate estimateMLE(List<ResponseRecord> responses) {
        double theta = 0.0;

        for (int iter = 0; iter < MAX_NEWTON_ITERATIONS; iter++) {
            double firstDeriv = 0;
            double secondDeriv = 0;

            for (ResponseRecord r : responses) {
                double p = irtProbability(theta, r.getDiscrimination(), r.getDifficulty());
                double a = r.getDiscrimination();
                double u = r.getNormalizedScore();

                firstDeriv += a * (u - p);
                secondDeriv -= a * a * p * (1 - p);
            }

            if (Math.abs(secondDeriv) < 1e-12) break;

            double delta = firstDeriv / secondDeriv;
            theta -= delta;
            theta = Math.max(THETA_MIN, Math.min(THETA_MAX, theta));

            if (Math.abs(delta) < CONVERGENCE_THRESHOLD) break;
        }

        double information = fisherInformation(theta, responses);
        double se = information > 0 ? 1.0 / Math.sqrt(information) : 1.0;

        return new AbilityEstimate(theta, se, responses.size());
    }

    /**
     * EAP estimation with standard normal prior.
     * More stable than MLE for short response sequences.
     */
    private AbilityEstimate estimateEAP(List<ResponseRecord> responses) {
        double step = (THETA_MAX - THETA_MIN) / (EAP_QUADRATURE_POINTS - 1);

        double numerator = 0;
        double denominator = 0;
        double secondMoment = 0;

        for (int i = 0; i < EAP_QUADRATURE_POINTS; i++) {
            double theta = THETA_MIN + i * step;
            double prior = gaussianDensity(theta, 0, 1);
            double likelihood = 1.0;

            for (ResponseRecord r : responses) {
                likelihood *= irtLikelihoodSingle(theta, r);
            }

            double posterior = prior * likelihood;
            numerator += theta * posterior;
            denominator += posterior;
            secondMoment += theta * theta * posterior;
        }

        if (denominator == 0) return AbilityEstimate.prior();

        double eapTheta = numerator / denominator;
        double eapVariance = (secondMoment / denominator) - (eapTheta * eapTheta);
        double eapSE = Math.sqrt(Math.max(eapVariance, 0.01));

        return new AbilityEstimate(eapTheta, eapSE, responses.size());
    }

    /**
     * 2PL IRT probability: P(correct | theta, a, b) = 1 / (1 + exp(-a * (theta - b)))
     */
    double irtProbability(double theta, double a, double b) {
        double exponent = -a * (theta - b);
        if (exponent > 500) return 0.0;
        if (exponent < -500) return 1.0;
        return 1.0 / (1.0 + Math.exp(exponent));
    }

    private double irtLikelihoodSingle(double theta, ResponseRecord r) {
        double p = irtProbability(theta, r.getDiscrimination(), r.getDifficulty());
        double u = r.getNormalizedScore();
        return Math.pow(p, u) * Math.pow(1 - p, 1 - u);
    }

    /**
     * Fisher Information at theta: sum of a_i^2 * P_i * (1 - P_i)
     */
    double fisherInformation(double theta, List<ResponseRecord> responses) {
        double info = 0;
        for (ResponseRecord r : responses) {
            double p = irtProbability(theta, r.getDiscrimination(), r.getDifficulty());
            double a = r.getDiscrimination();
            info += a * a * p * (1 - p);
        }
        return info;
    }

    private double gaussianDensity(double x, double mean, double sd) {
        double z = (x - mean) / sd;
        return Math.exp(-0.5 * z * z) / (sd * Math.sqrt(2 * Math.PI));
    }
}
