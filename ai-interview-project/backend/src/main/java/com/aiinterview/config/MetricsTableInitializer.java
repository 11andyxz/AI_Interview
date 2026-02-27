package com.aiinterview.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initialize ai_metrics_log table on startup if it doesn't exist
 */
@Component
public class MetricsTableInitializer implements CommandLineRunner {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public void run(String... args) throws Exception {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS ai_metrics_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                timestamp TIMESTAMP NOT NULL,
                metric_name VARCHAR(100) NOT NULL,
                metric_value DOUBLE NOT NULL,
                model_version VARCHAR(50),
                endpoint VARCHAR(100),
                tags JSON,
                INDEX idx_timestamp (timestamp),
                INDEX idx_metric (metric_name, timestamp)
            )
            """;
        
        try {
            jdbcTemplate.execute(createTableSql);
            System.out.println("[MetricsTableInitializer] ai_metrics_log table created/verified successfully");
            
            // Insert sample data if table is empty
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_metrics_log", Long.class);
            if (count != null && count == 0) {
                String insertSampleData = """
                    INSERT INTO ai_metrics_log (metric_name, metric_value, model_version, endpoint, tags) VALUES
                    ('ai_request_count', 1547, 'gpt-4o-mini', '/api/ai/resume', '{"status": "success"}'),
                    ('ai_request_latency_ms', 1200, 'gpt-4o-mini', '/api/ai/resume', '{"percentile": "p50"}'),
                    ('ai_request_latency_ms', 3800, 'gpt-4o-mini', '/api/ai/resume', '{"percentile": "p95"}'),
                    ('ai_request_latency_ms', 5100, 'gpt-4o-mini', '/api/ai/resume', '{"percentile": "p99"}'),
                    ('ai_token_usage', 24680, 'gpt-4o-mini', '/api/ai/resume', '{"type": "total"}'),
                    ('ai_validation_pass_rate', 0.97, 'gpt-4o-mini', '/api/ai/resume', '{}'),
                    ('ai_retry_count', 34, 'gpt-4o-mini', '/api/ai/resume', '{"reason": "timeout"}'),
                    ('ai_fallback_triggered', 2, 'gpt-4o-mini', '/api/ai/resume', '{}'),
                    ('ai_quality_score', 0.91, 'gpt-4o-mini', '/api/ai/resume', '{}'),
                    ('ai_cost_per_request', 0.12, 'gpt-4o-mini', '/api/ai/resume', '{"currency": "USD"}')
                    """;
                jdbcTemplate.execute(insertSampleData);
                System.out.println("[MetricsTableInitializer] Sample metrics data inserted");
            }
        } catch (Exception e) {
            System.err.println("[MetricsTableInitializer] Failed to initialize metrics table: " + e.getMessage());
        }
    }
}
