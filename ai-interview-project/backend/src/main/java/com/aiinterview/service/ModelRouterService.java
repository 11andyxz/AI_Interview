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
package com.aiinterview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

/**
 * Minimal Model Router Service (MVP)
 * - Supports weighted routing between model keys (e.g., "gpt-4o-mini", "gpt-3.5-turbo")
 * - Returns chosen model key and logs an inference record via logger (TODO: persist to DB)
 */
@Service
public class ModelRouterService {
    private static final Logger logger = LoggerFactory.getLogger(ModelRouterService.class);
    private final Random random = new Random();

    /**
     * Choose a model based on weights. weights map: modelKey -> weight (relative)
     */
    public String chooseModel(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) return null;
        double total = 0.0;
        for (double w : weights.values()) total += Math.max(0.0, w);
        if (total <= 0.0) return weights.keySet().iterator().next();

        // build cumulative map
        NavigableMap<Double, String> map = new TreeMap<>();
        double cumulative = 0.0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            double w = Math.max(0.0, e.getValue());
            if (w == 0.0) continue;
            cumulative += w;
            map.put(cumulative, e.getKey());
        }
        double r = random.nextDouble() * cumulative;
        Map.Entry<Double, String> entry = map.higherEntry(r);
        return entry != null ? entry.getValue() : map.firstEntry().getValue();
    }

    /**
     * Log inference call metadata. In MVP this logs via application logger; later persist to `model_inference_log` table.
     */
    public void logInference(String requestId, String modelVersion, long latencyMs, int tokensUsed, double estimatedCost, String endpoint) {
        logger.info("model_inference|requestId={} | model={} | latency_ms={} | tokens_used={} | est_cost={} | endpoint={}",
                requestId, modelVersion, latencyMs, tokensUsed, estimatedCost, endpoint);
        // Minimal persistence (MVP): append to a CSV under repo root `eval/model_inference_log.csv`
        // Columns: timestamp,requestId,model_version,latency_ms,tokens_used,estimated_cost,endpoint
        String csvPath = "eval/model_inference_log.csv";
        File f = new File(csvPath);
        boolean writeHeader = !f.exists();
        String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
        String line = String.format("%s,%s,%s,%d,%d,%.6f,%s\n", ts, requestId, modelVersion, latencyMs, tokensUsed, estimatedCost, endpoint == null ? "" : endpoint.replaceAll(",", " "));
        synchronized (ModelRouterService.class) {
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(new FileWriter(f, true));
                if (writeHeader) {
                    bw.write("timestamp,requestId,model_version,latency_ms,tokens_used,estimated_cost,endpoint\n");
                }
                bw.write(line);
            } catch (IOException ioe) {
                logger.warn("Failed to persist inference log to {}: {}", csvPath, ioe.getMessage());
            } finally {
                if (bw != null) {
                    try { bw.close(); } catch (IOException ignore) {}
                }
            }
        }
    }
}
