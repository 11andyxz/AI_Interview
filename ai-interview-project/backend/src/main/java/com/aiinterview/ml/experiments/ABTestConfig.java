package com.aiinterview.ml.experiments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Configuration for A/B testing experiments
 */
@Configuration
public class ABTestConfig {
    
    @Value("${ml.ab-test.enabled:true}")
    private boolean abTestEnabled;
    
    @Value("${ml.ab-test.resume-analysis.enabled:true}")
    private boolean resumeAnalysisExperimentEnabled;
    
    @Value("${ml.ab-test.question-generation.enabled:false}")
    private boolean questionGenerationExperimentEnabled;
    
    @Value("${ml.ab-test.answer-evaluation.enabled:false}")
    private boolean answerEvaluationExperimentEnabled;
    
    @Bean
    public PromptRouter promptRouter() {
        PromptRouter router = new PromptRouter();
        
        if (!abTestEnabled) {
            return router;
        }
        
        // Resume Analysis Experiment: v1.0 baseline vs v1.2 few-shot
        if (resumeAnalysisExperimentEnabled) {
            Experiment resumeExperiment = Experiment.builder()
                .name("resume_analysis_v1.2_rollout")
                .scenario("resume_analysis")
                .variants(Map.of(
                    "v1.0_baseline", 0.5,    // Control group
                    "v1.2_few_shot", 0.5     // Treatment group
                ))
                .primaryMetric("quality_score")
                .minimumSamples(100)
                .startDate(LocalDateTime.of(2026, 2, 5, 0, 0))
                .endDate(LocalDateTime.of(2026, 2, 12, 23, 59))
                .status(Experiment.ExperimentStatus.ACTIVE)
                .build();
            
            router.registerExperiment("resume_analysis", resumeExperiment);
        }
        
        // Question Generation Experiment: v2.0 baseline vs v2.2 context-enhanced
        if (questionGenerationExperimentEnabled) {
            Experiment questionExperiment = Experiment.builder()
                .name("question_gen_context_enhancement")
                .scenario("question_generation")
                .variants(Map.of(
                    "v2.0_baseline", 0.6,           // Control group
                    "v2.2_context_enhanced", 0.4    // Treatment group
                ))
                .primaryMetric("quality_score")
                .minimumSamples(150)
                .startDate(LocalDateTime.of(2026, 2, 5, 0, 0))
                .endDate(LocalDateTime.of(2026, 2, 12, 23, 59))
                .status(Experiment.ExperimentStatus.ACTIVE)
                .build();
            
            router.registerExperiment("question_generation", questionExperiment);
        }
        
        // Answer Evaluation Experiment: v3.0 baseline vs v3.1 rubric-based
        if (answerEvaluationExperimentEnabled) {
            Experiment evaluationExperiment = Experiment.builder()
                .name("rubric_based_evaluation_rollout")
                .scenario("answer_evaluation")
                .variants(Map.of(
                    "v3.0_baseline", 0.5,
                    "v3.1_rubric_based", 0.5
                ))
                .primaryMetric("pass_rate")
                .minimumSamples(200)
                .startDate(LocalDateTime.of(2026, 2, 3, 0, 0))
                .endDate(LocalDateTime.of(2026, 2, 10, 23, 59))
                .status(Experiment.ExperimentStatus.ACTIVE)
                .build();
            
            router.registerExperiment("answer_evaluation", evaluationExperiment);
        }
        
        return router;
    }
    
    @Bean
    public PromptVersionLoader promptVersionLoader() {
        return new PromptVersionLoader();
    }
    
    @Bean
    public ExperimentMetricsCollector experimentMetricsCollector() {
        return new ExperimentMetricsCollector();
    }
}
