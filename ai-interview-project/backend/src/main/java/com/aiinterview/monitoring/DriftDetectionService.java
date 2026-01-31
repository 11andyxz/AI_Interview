package com.aiinterview.monitoring;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Automated drift detection service
 * Runs hourly to detect distribution changes
 */
@Service
public class DriftDetectionService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private AlertService alertService;
    
    /**
     * Run drift detection every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void detectDrift() {
        System.out.println("[DriftDetection] Running hourly drift check at " + LocalDateTime.now());
        
        // Get current week distribution
        List<Double> currentWeek = getMetricDistribution("ai_validation_pass_rate", 7);
        
        // Get baseline (30 days)
        List<Double> baseline = getMetricDistribution("ai_validation_pass_rate", 30);
        
        if (currentWeek.size() < 10 || baseline.size() < 10) {
            System.out.println("[DriftDetection] Insufficient data for comparison");
            return;
        }
        
        // Calculate KL divergence
        double klDivergence = calculateKLDivergence(currentWeek, baseline);
        
        System.out.println("[DriftDetection] KL Divergence: " + klDivergence);
        
        // Check thresholds
        if (klDivergence > 0.15) {
            alertService.sendAlert("drift_critical", "KL divergence " + klDivergence + " exceeds critical threshold", "warning");
        } else if (klDivergence > 0.1) {
            alertService.sendAlert("drift_warning", "KL divergence " + klDivergence + " above baseline", "info");
        }
        
        // Check input distribution drift (prompt length)
        checkPromptLengthDrift();
        
        // Check output quality drift
        checkQualityDrift();
    }
    
    private List<Double> getMetricDistribution(String metricName, int daysBack) {
        String sql = "SELECT metric_value FROM ai_metrics_log WHERE metric_name = ? AND timestamp >= DATE_SUB(NOW(), INTERVAL ? DAY) ORDER BY timestamp";
        return jdbcTemplate.queryForList(sql, Double.class, metricName, daysBack);
    }
    
    private double calculateKLDivergence(List<Double> p, List<Double> q) {
        // Simplified KL divergence calculation
        // Production: use proper histogram binning
        double epsilon = 1e-10;
        double sumP = p.stream().mapToDouble(Double::doubleValue).sum() + epsilon;
        double sumQ = q.stream().mapToDouble(Double::doubleValue).sum() + epsilon;
        
        double divergence = 0.0;
        int minSize = Math.min(p.size(), q.size());
        
        for (int i = 0; i < minSize; i++) {
            double pNorm = (p.get(i) + epsilon) / sumP;
            double qNorm = (q.get(i) + epsilon) / sumQ;
            divergence += pNorm * Math.log(pNorm / qNorm);
        }
        
        return divergence;
    }
    
    private void checkPromptLengthDrift() {
        // Check if prompt length distribution changed significantly
        // Simplified: compare average prompt length
        String sql = "SELECT AVG(metric_value) FROM ai_metrics_log WHERE metric_name = 'prompt_length' AND timestamp >= DATE_SUB(NOW(), INTERVAL 7 DAY)";
        Double currentAvg = jdbcTemplate.queryForObject(sql, Double.class);
        
        sql = "SELECT AVG(metric_value) FROM ai_metrics_log WHERE metric_name = 'prompt_length' AND timestamp >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
        Double baselineAvg = jdbcTemplate.queryForObject(sql, Double.class);
        
        if (currentAvg != null && baselineAvg != null) {
            double change = Math.abs(currentAvg - baselineAvg) / baselineAvg;
            if (change > 0.3) {
                alertService.sendAlert("prompt_length_drift", "Prompt length changed by " + (change * 100) + "%", "warning");
            }
        }
    }
    
    private void checkQualityDrift() {
        // Check if quality score distribution drifted
        String sql = "SELECT AVG(metric_value) FROM ai_metrics_log WHERE metric_name = 'ai_quality_score' AND timestamp >= DATE_SUB(NOW(), INTERVAL 7 DAY)";
        Double currentQuality = jdbcTemplate.queryForObject(sql, Double.class);
        
        sql = "SELECT AVG(metric_value) FROM ai_metrics_log WHERE metric_name = 'ai_quality_score' AND timestamp >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
        Double baselineQuality = jdbcTemplate.queryForObject(sql, Double.class);
        
        if (currentQuality != null && baselineQuality != null && currentQuality < baselineQuality * 0.9) {
            alertService.sendAlert("quality_degradation", "Quality score dropped from " + baselineQuality + " to " + currentQuality, "critical");
        }
    }
    
    /**
     * Manual drift check (for testing)
     */
    public Map<String, Object> runDriftCheck() {
        Map<String, Object> result = new HashMap<>();
        
        List<Double> currentWeek = getMetricDistribution("ai_validation_pass_rate", 7);
        List<Double> baseline = getMetricDistribution("ai_validation_pass_rate", 30);
        
        double klDivergence = calculateKLDivergence(currentWeek, baseline);
        result.put("kl_divergence", klDivergence);
        result.put("threshold", 0.1);
        result.put("status", klDivergence > 0.1 ? "DRIFT_DETECTED" : "OK");
        result.put("timestamp", LocalDateTime.now());
        
        // Chi-square test
        if (currentWeek.size() >= 10 && baseline.size() >= 10) {
            double chiSquarePValue = performChiSquareTest(currentWeek, baseline);
            result.put("chi_square_p_value", chiSquarePValue);
            result.put("chi_square_significant", chiSquarePValue < 0.05);
        }
        
        // Kolmogorov-Smirnov test
        if (currentWeek.size() >= 10 && baseline.size() >= 10) {
            double ksPValue = performKSTest(currentWeek, baseline);
            result.put("ks_p_value", ksPValue);
            result.put("ks_significant", ksPValue < 0.05);
        }
        
        return result;
    }
    
    /**
     * Chi-square test for categorical distribution comparison
     * Tests whether two distributions are significantly different
     */
    private double performChiSquareTest(List<Double> observed, List<Double> expected) {
        try {
            // Convert to histograms with 10 bins
            int numBins = 10;
            long[] observedCounts = createHistogram(observed, numBins);
            double[] expectedCounts = createHistogramDouble(expected, numBins);
            
            // Ensure no zero counts (add small epsilon)
            for (int i = 0; i < numBins; i++) {
                if (expectedCounts[i] == 0) {
                    expectedCounts[i] = 1.0;
                }
                if (observedCounts[i] == 0) {
                    observedCounts[i] = 1;
                }
            }
            
            ChiSquareTest chiTest = new ChiSquareTest();
            return chiTest.chiSquareTest(expectedCounts, observedCounts);
            
        } catch (Exception e) {
            System.err.println("[DriftDetection] Chi-square test failed: " + e.getMessage());
            return 1.0; // Return 1.0 (not significant) on error
        }
    }
    
    /**
     * Kolmogorov-Smirnov test for continuous distribution comparison
     * More powerful than chi-square for detecting distribution shifts
     */
    private double performKSTest(List<Double> sample1, List<Double> sample2) {
        try {
            KolmogorovSmirnovTest ksTest = new KolmogorovSmirnovTest();
            
            double[] array1 = sample1.stream().mapToDouble(Double::doubleValue).toArray();
            double[] array2 = sample2.stream().mapToDouble(Double::doubleValue).toArray();
            
            // Returns p-value: low value (< 0.05) means distributions are different
            return ksTest.kolmogorovSmirnovTest(array1, array2);
            
        } catch (Exception e) {
            System.err.println("[DriftDetection] KS test failed: " + e.getMessage());
            return 1.0; // Return 1.0 (not significant) on error
        }
    }
    
    /**
     * Create histogram from data for chi-square test
     */
    private long[] createHistogram(List<Double> data, int numBins) {
        long[] histogram = new long[numBins];
        
        if (data.isEmpty()) return histogram;
        
        double min = data.stream().min(Double::compare).orElse(0.0);
        double max = data.stream().max(Double::compare).orElse(1.0);
        double binWidth = (max - min) / numBins;
        
        if (binWidth == 0) binWidth = 1.0;
        
        for (Double value : data) {
            int bin = (int) ((value - min) / binWidth);
            if (bin >= numBins) bin = numBins - 1;
            if (bin < 0) bin = 0;
            histogram[bin]++;
        }
        
        return histogram;
    }
    
    /**
     * Create histogram (double) for expected counts
     */
    private double[] createHistogramDouble(List<Double> data, int numBins) {
        double[] histogram = new double[numBins];
        
        if (data.isEmpty()) return histogram;
        
        double min = data.stream().min(Double::compare).orElse(0.0);
        double max = data.stream().max(Double::compare).orElse(1.0);
        double binWidth = (max - min) / numBins;
        
        if (binWidth == 0) binWidth = 1.0;
        
        for (Double value : data) {
            int bin = (int) ((value - min) / binWidth);
            if (bin >= numBins) bin = numBins - 1;
            if (bin < 0) bin = 0;
            histogram[bin]++;
        }
        
        return histogram;
    }
}
