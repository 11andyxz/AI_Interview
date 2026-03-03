package com.aiinterview.ml.gateway;

public class ExperimentReport {
    private Long experimentId;
    private String experimentName;
    private String status;
    private long baselineSampleSize;
    private long treatmentSampleSize;
    private double baselineMeanQuality;
    private double treatmentMeanQuality;
    private double qualityDelta;
    private double pValue;
    private boolean statisticallySignificant;
    private String recommendation;

    public Long getExperimentId() { return experimentId; }
    public void setExperimentId(Long experimentId) { this.experimentId = experimentId; }

    public String getExperimentName() { return experimentName; }
    public void setExperimentName(String experimentName) { this.experimentName = experimentName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getBaselineSampleSize() { return baselineSampleSize; }
    public void setBaselineSampleSize(long baselineSampleSize) { this.baselineSampleSize = baselineSampleSize; }

    public long getTreatmentSampleSize() { return treatmentSampleSize; }
    public void setTreatmentSampleSize(long treatmentSampleSize) { this.treatmentSampleSize = treatmentSampleSize; }

    public double getBaselineMeanQuality() { return baselineMeanQuality; }
    public void setBaselineMeanQuality(double baselineMeanQuality) { this.baselineMeanQuality = baselineMeanQuality; }

    public double getTreatmentMeanQuality() { return treatmentMeanQuality; }
    public void setTreatmentMeanQuality(double treatmentMeanQuality) { this.treatmentMeanQuality = treatmentMeanQuality; }

    public double getQualityDelta() { return qualityDelta; }
    public void setQualityDelta(double qualityDelta) { this.qualityDelta = qualityDelta; }

    public double getpValue() { return pValue; }
    public void setpValue(double pValue) { this.pValue = pValue; }

    public boolean isStatisticallySignificant() { return statisticallySignificant; }
    public void setStatisticallySignificant(boolean statisticallySignificant) { this.statisticallySignificant = statisticallySignificant; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}
