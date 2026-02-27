package com.aiinterview.ml.gateway;

import com.aiinterview.ml.experiment.Experiment;
import com.aiinterview.ml.experiment.ExperimentRepository;
import com.aiinterview.ml.experiment.ExperimentTracker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Experiment Gateway
 * Tests experiment launch, routing, lifecycle management, and prompt versioning
 */
@SpringBootTest
@Transactional
public class ExperimentGatewayIntegrationTest {
    
    @Autowired
    private ExperimentAwareLlmRouter router;
    
    @Autowired
    private ExperimentLifecycleManager lifecycleManager;
    
    @Autowired
    private ExperimentTemplates templates;
    
    @Autowired
    private PromptVersionRepository promptVersionRepository;
    
    @Autowired
    private ExperimentRepository experimentRepository;
    
    @Autowired
    private ExperimentTracker experimentTracker;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    public void setUp() {
        promptVersionRepository.deleteAll();
        experimentRepository.deleteAll();
    }
    
    @Test
    public void testPromptVersionManagement() {
        PromptVersion v1 = PromptVersion.builder()
            .promptKey("question_generation")
            .version("v1")
            .content("Generate interview question for {role}")
            .description("Baseline version")
            .isActive(true)
            .build();
        
        PromptVersion saved = promptVersionRepository.save(v1);
        assertNotNull(saved.getId());
        
        PromptVersion fetched = promptVersionRepository
            .findByPromptKeyAndVersion("question_generation", "v1")
            .orElse(null);
        
        assertNotNull(fetched);
        assertEquals("v1", fetched.getVersion());
        assertEquals("Generate interview question for {role}", fetched.getContent());
    }
    
    @Test
    public void testExperimentTemplateCreation() {
        ExperimentTemplate template = templates.promptVariantTemplate(
            "question_generation", "v1", "v2"
        );
        
        assertNotNull(template);
        assertEquals("prompt_variant", template.getTemplateType());
        assertEquals(0.5, template.getTrafficSplit());
        assertEquals(100, template.getMinSampleSize());
        assertEquals(0.05, template.getRollbackPValue());
    }
    
    @Test
    public void testExperimentLaunch() throws Exception {
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("temperature", 0.7);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o-mini");
        variantConfig.put("temperature", 0.9);
        
        ExperimentTemplate template = ExperimentTemplate.builder()
            .templateType("parameter_tuning")
            .name("Temperature Test")
            .targetEndpoint("question_generation")
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
        
        Experiment experiment = lifecycleManager.launchExperiment(template);
        
        assertNotNull(experiment);
        assertNotNull(experiment.getId());
        assertEquals("active", experiment.getStatus());
        assertEquals(0.5, experiment.getTrafficSplit());
        assertNotNull(experiment.getStartedAt());
    }
    
    @Test
    public void testRouterWithoutExperiment() {
        LlmRequest request = LlmRequest.builder()
            .requestType("question_generation")
            .requestId("test_req_1")
            .sessionId("test_session_1")
            .build();
        
        LlmRouteDecision decision = router.route(request);
        
        assertNotNull(decision);
        assertNull(decision.getExperimentId());
        assertFalse(decision.isExperiment());
        assertEquals("gpt-3.5-turbo", decision.getModel());
        assertEquals(0.7, decision.getTemperature(), 0.01);
    }
    
    @Test
    public void testRouterWithActiveExperiment() throws Exception {
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("temperature", 0.7);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o-mini");
        variantConfig.put("temperature", 0.9);
        
        ExperimentTemplate template = ExperimentTemplate.builder()
            .templateType("parameter_tuning")
            .name("Temperature Test")
            .targetEndpoint("question_generation")
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
        
        Experiment experiment = lifecycleManager.launchExperiment(template);
        
        LlmRequest request = LlmRequest.builder()
            .requestType("question_generation")
            .requestId("test_req_2")
            .sessionId("test_session_2")
            .build();
        
        LlmRouteDecision decision = router.route(request);
        
        assertNotNull(decision);
        assertNotNull(decision.getExperimentId());
        assertTrue(decision.isExperiment());
        assertNotNull(decision.getVariant());
        assertTrue(decision.getVariant().equals("baseline") || decision.getVariant().equals("variant"));
    }
    
    @Test
    public void testDeterministicTrafficSplitting() throws Exception {
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("temperature", 0.7);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o-mini");
        variantConfig.put("temperature", 0.9);
        
        ExperimentTemplate template = ExperimentTemplate.builder()
            .templateType("parameter_tuning")
            .name("Deterministic Test")
            .targetEndpoint("question_generation")
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
        
        Experiment experiment = lifecycleManager.launchExperiment(template);
        
        LlmRequest request1 = LlmRequest.builder()
            .requestType("question_generation")
            .requestId("deterministic_req_1")
            .build();
        
        LlmRouteDecision decision1 = router.route(request1);
        LlmRouteDecision decision2 = router.route(request1);
        
        assertEquals(decision1.getVariant(), decision2.getVariant(),
            "Same request ID should consistently get same variant");
    }
    
    @Test
    public void testExperimentReport() throws Exception {
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("temperature", 0.7);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o-mini");
        variantConfig.put("temperature", 0.9);
        
        ExperimentTemplate template = ExperimentTemplate.builder()
            .templateType("parameter_tuning")
            .name("Report Test")
            .targetEndpoint("question_generation")
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
        
        Experiment experiment = lifecycleManager.launchExperiment(template);
        
        ExperimentReport report = lifecycleManager.generateReport(experiment.getId());
        
        assertNotNull(report);
        assertEquals(experiment.getId(), report.getExperimentId());
        assertEquals("Report Test", report.getExperimentName());
        assertNotNull(report.getBaselineMetrics());
        assertNotNull(report.getVariantMetrics());
    }
    
    @Test
    public void testModelComparisonTemplate() {
        ExperimentTemplate template = templates.modelComparisonTemplate(
            "question_generation", 
            "gpt-4o-mini", 
            "gpt-4o"
        );
        
        assertNotNull(template);
        assertEquals("model_comparison", template.getTemplateType());
        assertTrue(template.getName().contains("Model Comparison"));
    }
    
    @Test
    public void testRagOptimizationTemplate() {
        ExperimentTemplate template = templates.ragOptimizationTemplate(
            "question_generation", 
            5, 
            10
        );
        
        assertNotNull(template);
        assertEquals("rag_optimization", template.getTemplateType());
        assertTrue(template.getName().contains("RAG TopK Test"));
    }
    
    @Test
    public void testConfigResolution() throws Exception {
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("temperature", 0.7);
        baselineConfig.put("maxTokens", 500);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o");
        variantConfig.put("temperature", 0.9);
        variantConfig.put("maxTokens", 1000);
        
        ExperimentTemplate template = ExperimentTemplate.builder()
            .templateType("model_comparison")
            .name("Config Resolution Test")
            .targetEndpoint("question_generation")
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .build();
        
        Experiment experiment = lifecycleManager.launchExperiment(template);
        
        LlmConfig baselineResolved = router.resolveConfig(experiment.getId(), "baseline");
        LlmConfig variantResolved = router.resolveConfig(experiment.getId(), "variant");
        
        assertEquals("gpt-4o-mini", baselineResolved.getModel());
        assertEquals(0.7, baselineResolved.getTemperature());
        assertEquals(500, baselineResolved.getMaxTokens());
        
        assertEquals("gpt-4o", variantResolved.getModel());
        assertEquals(0.9, variantResolved.getTemperature());
        assertEquals(1000, variantResolved.getMaxTokens());
    }
}
