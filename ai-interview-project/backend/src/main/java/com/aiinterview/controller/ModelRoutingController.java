package com.aiinterview.controller;

import com.aiinterview.service.ModelRouterService;
import com.aiinterview.service.WeightManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/router")
public class ModelRoutingController {

    private final WeightManager weightManager = new WeightManager();
    private final ModelRouterService router = new ModelRouterService();

    @GetMapping("/weights")
    public ResponseEntity<Map<String, Double>> getWeights() {
        return ResponseEntity.ok(weightManager.getWeights());
    }

    @PutMapping("/weights")
    public ResponseEntity<Void> setWeights(@RequestBody Map<String, Double> newWeights) {
        weightManager.setWeights(newWeights);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/route")
    public ResponseEntity<RouteResponse> routeAndLog(@RequestBody RouteRequest req) {
        // choose model and append CSV log (path relative to app working dir)
        String csvPath = "eval/model_inference_log.csv";
        String chosen = router.routeAndLog(weightManager.getWeights(), csvPath, req.requestId, req.latencyMs == null ? 0L : req.latencyMs, req.tokens == null ? 0 : req.tokens, req.estimatedCost == null ? 0.0 : req.estimatedCost, req.validatorPass);
        return ResponseEntity.ok(new RouteResponse(chosen));
    }

    public static class RouteRequest {
        public String requestId;
        public Long latencyMs;
        public Integer tokens;
        public Double estimatedCost;
        public Integer validatorPass;
    }

    public static class RouteResponse {
        public String model;
        public RouteResponse(String model) { this.model = model; }
    }
}
