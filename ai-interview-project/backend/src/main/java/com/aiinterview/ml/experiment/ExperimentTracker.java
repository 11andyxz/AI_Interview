package com.aiinterview.ml.experiment;

import com.aiinterview.ml.experiment.model.Experiment;
import com.aiinterview.ml.experiment.model.ExperimentMetric;
import com.aiinterview.ml.experiment.repository.ExperimentMetricRepository;
import com.aiinterview.ml.experiment.repository.ExperimentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ExperimentTracker {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentTracker.class);

    private final ExperimentRepository experimentRepository;
    private final ExperimentMetricRepository metricRepository;

    public ExperimentTracker(ExperimentRepository experimentRepository,
                             ExperimentMetricRepository metricRepository) {
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
    }

    public Optional<Experiment> getActiveExperiment(String endpoint) {
        List<Experiment> active = experimentRepository.findByStatusAndTargetEndpoint("running", endpoint);
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }

    /**
     * Deterministic variant assignment using SHA-256 hash of sessionId + experimentId.
     * Ensures the same session always gets the same variant.
     */
    public String assignVariant(Experiment experiment, String sessionId) {
        String hashInput = sessionId + ":" + experiment.getId();
        int hashValue = Math.abs(sha256Hash(hashInput));
        double bucket = (hashValue % 10000) / 100.0;
        return bucket < experiment.getTrafficPercentage()
                ? experiment.getTreatmentVariant()
                : experiment.getBaselineVariant();
    }

    public void recordMetric(Long experimentId, String variant, String sessionId,
                             double qualityScore, long latencyMs, int tokenCount) {
        ExperimentMetric metric = new ExperimentMetric();
        metric.setExperimentId(experimentId);
        metric.setVariant(variant);
        metric.setSessionId(sessionId);
        metric.setQualityScore(qualityScore);
        metric.setLatencyMs(latencyMs);
        metric.setTokenCount(tokenCount);
        metricRepository.save(metric);
    }

    /**
     * Welch's t-test for comparing two experiment variants.
     * Returns p-value for difference in quality scores.
     */
    public double welchTTest(Long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) return 1.0;

        List<ExperimentMetric> baselineMetrics =
                metricRepository.findByExperimentIdAndVariant(experimentId, experiment.getBaselineVariant());
        List<ExperimentMetric> treatmentMetrics =
                metricRepository.findByExperimentIdAndVariant(experimentId, experiment.getTreatmentVariant());

        if (baselineMetrics.size() < 2 || treatmentMetrics.size() < 2) return 1.0;

        double[] baselineScores = baselineMetrics.stream().mapToDouble(ExperimentMetric::getQualityScore).toArray();
        double[] treatmentScores = treatmentMetrics.stream().mapToDouble(ExperimentMetric::getQualityScore).toArray();

        double meanA = mean(baselineScores);
        double meanB = mean(treatmentScores);
        double varA = variance(baselineScores, meanA);
        double varB = variance(treatmentScores, meanB);
        int nA = baselineScores.length;
        int nB = treatmentScores.length;

        double se = Math.sqrt(varA / nA + varB / nB);
        if (se == 0) return 1.0;

        double t = (meanB - meanA) / se;

        double numerator = Math.pow(varA / nA + varB / nB, 2);
        double denominator = Math.pow(varA / nA, 2) / (nA - 1) + Math.pow(varB / nB, 2) / (nB - 1);
        double df = numerator / denominator;

        return approximatePValue(Math.abs(t), df);
    }

    public boolean hasMinSampleSize(Long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) return false;
        long baselineCount = metricRepository.countByExperimentIdAndVariant(experimentId, experiment.getBaselineVariant());
        long treatmentCount = metricRepository.countByExperimentIdAndVariant(experimentId, experiment.getTreatmentVariant());
        return baselineCount >= experiment.getMinSampleSize() && treatmentCount >= experiment.getMinSampleSize();
    }

    public Experiment startExperiment(Long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();
        experiment.setStatus("running");
        experiment.setStartedAt(LocalDateTime.now());
        return experimentRepository.save(experiment);
    }

    public Experiment stopExperiment(Long experimentId, String finalStatus) {
        Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();
        experiment.setStatus(finalStatus);
        experiment.setEndedAt(LocalDateTime.now());
        return experimentRepository.save(experiment);
    }

    private int sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16) | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            return input.hashCode();
        }
    }

    private double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private double variance(double[] values, double mean) {
        double sum = 0;
        for (double v : values) sum += (v - mean) * (v - mean);
        return sum / (values.length - 1);
    }

    /**
     * Approximate two-tailed p-value using the t-distribution.
     * Uses the regularized incomplete beta function approximation.
     */
    private double approximatePValue(double t, double df) {
        double x = df / (df + t * t);
        double a = df / 2.0;
        double b = 0.5;
        double betaIncomplete = regularizedIncompleteBeta(x, a, b);
        return betaIncomplete;
    }

    private double regularizedIncompleteBeta(double x, double a, double b) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double result = 1.0;
        double term = 1.0;
        for (int i = 1; i <= 200; i++) {
            term *= x * (a + b + i - 1) * (a + i - 1) / ((a + 2 * i - 1) * (a + 2 * i) * i);
            result += term;
            if (Math.abs(term) < 1e-10) break;
        }
        double logBeta = lgamma(a) + lgamma(b) - lgamma(a + b);
        return Math.exp(a * Math.log(x) + b * Math.log(1 - x) - logBeta) * result / a;
    }

    private double lgamma(double x) {
        double[] c = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double cj : c) ser += cj / ++y;
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}
