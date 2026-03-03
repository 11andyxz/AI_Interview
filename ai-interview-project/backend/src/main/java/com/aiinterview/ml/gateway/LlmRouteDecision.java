package com.aiinterview.ml.gateway;

public class LlmRouteDecision {
    private String experimentId;
    private String variant;
    private String model;
    private String promptTemplate;
    private boolean useRag;
    private int ragTopK;
    private double temperature;

    public LlmRouteDecision() {
        this.model = "gpt-3.5-turbo";
        this.temperature = 0.7;
        this.ragTopK = 5;
    }

    public static LlmRouteDecision defaultRoute(String model, double temperature) {
        LlmRouteDecision decision = new LlmRouteDecision();
        decision.setModel(model);
        decision.setTemperature(temperature);
        return decision;
    }

    public boolean isInExperiment() {
        return experimentId != null;
    }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }

    public boolean isUseRag() { return useRag; }
    public void setUseRag(boolean useRag) { this.useRag = useRag; }

    public int getRagTopK() { return ragTopK; }
    public void setRagTopK(int ragTopK) { this.ragTopK = ragTopK; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}
