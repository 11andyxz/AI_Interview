package com.aiinterview.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple in-memory weight manager for routing. Thread-safe.
 */
public class WeightManager {
    private final Map<String, Double> weights = new HashMap<>();

    public WeightManager() {
        // default weights: 60% baseline, 30% current production candidate, 10% quality benchmark
        weights.put("gpt-3.5-turbo", 0.6);    // baseline
        weights.put("gpt-4o-mini", 0.3);      // current production candidate  
        weights.put("gpt-4-turbo", 0.1);      // quality benchmark
    }

    public synchronized Map<String, Double> getWeights() {
        return Collections.unmodifiableMap(new HashMap<>(weights));
    }

    public synchronized void setWeights(Map<String, Double> newWeights) {
        weights.clear();
        weights.putAll(newWeights);
    }
}
