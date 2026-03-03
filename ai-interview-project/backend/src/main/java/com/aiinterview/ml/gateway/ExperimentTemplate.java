package com.aiinterview.ml.gateway;

public class ExperimentTemplate {
    private String name;
    private String description;
    private String targetEndpoint;
    private double trafficPercentage;
    private int minSampleSize;
    private String baselinePromptKey;
    private String baselinePromptVersion;
    private String treatmentPromptKey;
    private String treatmentPromptVersion;
    private String baselineModel;
    private String treatmentModel;

    public ExperimentTemplate() {
        this.trafficPercentage = 50.0;
        this.minSampleSize = 100;
    }

    public static ExperimentTemplate promptVariant(String name, String endpoint,
                                                    String promptKey, String baselineVersion,
                                                    String treatmentVersion) {
        ExperimentTemplate t = new ExperimentTemplate();
        t.setName(name);
        t.setTargetEndpoint(endpoint);
        t.setBaselinePromptKey(promptKey);
        t.setBaselinePromptVersion(baselineVersion);
        t.setTreatmentPromptKey(promptKey);
        t.setTreatmentPromptVersion(treatmentVersion);
        return t;
    }

    public static ExperimentTemplate modelComparison(String name, String endpoint,
                                                      String baselineModel, String treatmentModel) {
        ExperimentTemplate t = new ExperimentTemplate();
        t.setName(name);
        t.setTargetEndpoint(endpoint);
        t.setBaselineModel(baselineModel);
        t.setTreatmentModel(treatmentModel);
        return t;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetEndpoint() { return targetEndpoint; }
    public void setTargetEndpoint(String targetEndpoint) { this.targetEndpoint = targetEndpoint; }

    public double getTrafficPercentage() { return trafficPercentage; }
    public void setTrafficPercentage(double trafficPercentage) { this.trafficPercentage = trafficPercentage; }

    public int getMinSampleSize() { return minSampleSize; }
    public void setMinSampleSize(int minSampleSize) { this.minSampleSize = minSampleSize; }

    public String getBaselinePromptKey() { return baselinePromptKey; }
    public void setBaselinePromptKey(String baselinePromptKey) { this.baselinePromptKey = baselinePromptKey; }

    public String getBaselinePromptVersion() { return baselinePromptVersion; }
    public void setBaselinePromptVersion(String baselinePromptVersion) { this.baselinePromptVersion = baselinePromptVersion; }

    public String getTreatmentPromptKey() { return treatmentPromptKey; }
    public void setTreatmentPromptKey(String treatmentPromptKey) { this.treatmentPromptKey = treatmentPromptKey; }

    public String getTreatmentPromptVersion() { return treatmentPromptVersion; }
    public void setTreatmentPromptVersion(String treatmentPromptVersion) { this.treatmentPromptVersion = treatmentPromptVersion; }

    public String getBaselineModel() { return baselineModel; }
    public void setBaselineModel(String baselineModel) { this.baselineModel = baselineModel; }

    public String getTreatmentModel() { return treatmentModel; }
    public void setTreatmentModel(String treatmentModel) { this.treatmentModel = treatmentModel; }
}
