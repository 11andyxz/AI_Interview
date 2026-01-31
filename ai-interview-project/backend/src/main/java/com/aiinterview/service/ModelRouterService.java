package com.aiinterview.service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Minimal model router with weighted traffic splitting and CSV logging.
 * This is a conservative, drop-in helper; replace CSV logging with DB persistence in production.
 */
public class ModelRouterService {

    private final Random rng = new Random();

    public String chooseModel(Map<String, Double> weights) {
        double total = 0.0;
        for (Double v : weights.values()) total += v;
        double r = rng.nextDouble() * total;
        double upto = 0.0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            upto += e.getValue();
            if (r <= upto) return e.getKey();
        }
        // fallback to first
        return weights.keySet().iterator().next();
    }

    /**
     * Append an inference log row to a CSV file. Fields: timestamp,request_id,model_version,latency_ms,tokens,estimated_cost,validator_pass
     * In production, replace with JDBC insert into model_inference_log table.
     */
    public synchronized void logInference(String csvPath, String requestId, String modelVersion, long latencyMs, int tokens, double cost, Integer validatorPass) {
        try {
            if (!Files.exists(Paths.get(csvPath))) {
                try (FileWriter hdr = new FileWriter(csvPath, true)) {
                    hdr.append("timestamp,request_id,model_version,latency_ms,tokens,estimated_cost,validator_pass\n");
                }
            }
            try (FileWriter fw = new FileWriter(csvPath, true)) {
                String line = String.format("%s,%s,%s,%d,%d,%.6f,%s\n", Instant.now().toString(), requestId, modelVersion, latencyMs, tokens, cost, validatorPass==null?"":validatorPass.toString());
                fw.append(line);
            }
        } catch (IOException ex) {
            // best-effort logging
            ex.printStackTrace();
        }
    }

    // Example convenience method: pick model, log, and return chosen model
    public String routeAndLog(Map<String, Double> weights, String csvPath, String requestId, long latencyMs, int tokens, double cost, Integer validatorPass) {
        String chosen = chooseModel(weights);
        logInference(csvPath, requestId, chosen, latencyMs, tokens, cost, validatorPass);
        return chosen;
    }
}

