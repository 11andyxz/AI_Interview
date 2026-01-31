-- ML Metrics Time-Series Storage Table
-- Run this script to create the ai_metrics_log table

USE ai_interview;

CREATE TABLE IF NOT EXISTS ai_metrics_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DOUBLE NOT NULL,
    model_version VARCHAR(50),
    endpoint VARCHAR(100),
    tags JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_timestamp (timestamp),
    INDEX idx_metric (metric_name, timestamp),
    INDEX idx_endpoint (endpoint, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Sample initial data for testing
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
('ai_cost_per_request', 0.12, 'gpt-4o-mini', '/api/ai/resume', '{"currency": "USD"}');

SELECT 'ai_metrics_log table created successfully!' as status;
