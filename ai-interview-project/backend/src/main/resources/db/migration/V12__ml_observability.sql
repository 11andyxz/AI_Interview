-- V12: ML Observability Tables
-- Tables for LLM call metrics, quality tracking, and cost monitoring

CREATE TABLE IF NOT EXISTS llm_call_metrics (
    id VARCHAR(36) PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    model VARCHAR(100),
    input_tokens INT,
    output_tokens INT,
    cost_usd DECIMAL(10, 6),
    latency_ms INT,
    quality_score DOUBLE,
    validation_passed BOOLEAN DEFAULT FALSE,
    error_type VARCHAR(100),
    metadata TEXT,
    temperature DOUBLE,
    max_tokens INT,
    prompt_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_endpoint_created (endpoint, created_at),
    INDEX idx_model_created (model, created_at),
    INDEX idx_created_at (created_at),
    INDEX idx_quality_score (quality_score),
    INDEX idx_validation_passed (validation_passed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Evaluation metrics table for offline quality assessments
CREATE TABLE IF NOT EXISTS evaluation_metrics (
    id VARCHAR(36) PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    evaluation_type VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_data TEXT,
    output_data TEXT,
    expected_output TEXT,
    quality_score DOUBLE,
    passed BOOLEAN,
    validator_name VARCHAR(50),
    error_message TEXT,
    metadata JSON,
    prompt_version VARCHAR(50),
    temperature DOUBLE,
    max_tokens INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    
    INDEX idx_eval_endpoint_created (endpoint, created_at),
    INDEX idx_eval_created_at (created_at),
    INDEX idx_eval_type (evaluation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Partition table by month for efficient time-series queries
-- Note: Partitioning can be enabled in production for large-scale deployments
-- ALTER TABLE llm_call_metrics PARTITION BY RANGE (UNIX_TIMESTAMP(created_at)) (
--     PARTITION p_current VALUES LESS THAN (UNIX_TIMESTAMP('2024-02-01')),
--     PARTITION p_future VALUES LESS THAN MAXVALUE
-- );

CREATE TABLE IF NOT EXISTS quality_baselines (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    endpoint VARCHAR(100) NOT NULL,
    mean_quality DOUBLE NOT NULL,
    std_deviation DOUBLE NOT NULL,
    sample_size INT NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_endpoint (endpoint),
    INDEX idx_computed_at (computed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quality_alerts (
    id VARCHAR(36) PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    current_value DOUBLE,
    threshold_value DOUBLE,
    p_value DOUBLE,
    triggered_at TIMESTAMP NOT NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    
    INDEX idx_triggered_at (triggered_at),
    INDEX idx_acknowledged (acknowledged),
    INDEX idx_severity (severity),
    INDEX idx_endpoint (endpoint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS budget_limits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period VARCHAR(20) NOT NULL,
    limit_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default budget limit
INSERT INTO budget_limits (period, limit_amount) VALUES ('monthly', 1000.00);

CREATE TABLE IF NOT EXISTS golden_test_cases (
    id VARCHAR(36) PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    input_data TEXT NOT NULL,
    expected_output TEXT,
    min_quality_threshold DOUBLE NOT NULL DEFAULT 80.0,
    config JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_endpoint (endpoint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS golden_test_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    test_case_id VARCHAR(36) NOT NULL,
    endpoint VARCHAR(100) NOT NULL,
    quality_score DOUBLE NOT NULL,
    expected_min_quality DOUBLE NOT NULL,
    passed BOOLEAN NOT NULL,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_test_case_id (test_case_id),
    INDEX idx_executed_at (executed_at),
    INDEX idx_passed (passed),
    FOREIGN KEY (test_case_id) REFERENCES golden_test_cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create view for daily cost summary
CREATE OR REPLACE VIEW v_daily_cost_summary AS
SELECT 
    DATE(created_at) as date,
    endpoint,
    model,
    COUNT(*) as total_calls,
    SUM(input_tokens + output_tokens) as total_tokens,
    SUM(cost_usd) as total_cost,
    AVG(cost_usd) as avg_cost_per_call,
    AVG(latency_ms) as avg_latency_ms,
    AVG(quality_score) as avg_quality_score,
    SUM(CASE WHEN validation_passed = TRUE THEN 1 ELSE 0 END) as passed_calls,
    SUM(CASE WHEN validation_passed = FALSE THEN 1 ELSE 0 END) as failed_calls
FROM llm_call_metrics
GROUP BY DATE(created_at), endpoint, model;

-- Create view for hourly quality trends
CREATE OR REPLACE VIEW v_hourly_quality_trends AS
SELECT 
    DATE_FORMAT(created_at, '%Y-%m-%d %H:00:00') as hour,
    endpoint,
    COUNT(*) as total_calls,
    AVG(quality_score) as avg_quality,
    MIN(quality_score) as min_quality,
    MAX(quality_score) as max_quality,
    STDDEV(quality_score) as std_dev_quality,
    AVG(latency_ms) as avg_latency_ms,
    SUM(CASE WHEN validation_passed = TRUE THEN 1 ELSE 0 END) / COUNT(*) * 100 as success_rate
FROM llm_call_metrics
WHERE quality_score IS NOT NULL
GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:00:00'), endpoint;

-- Create view for endpoint health dashboard
CREATE OR REPLACE VIEW v_endpoint_health AS
SELECT 
    endpoint,
    COUNT(*) as total_calls_24h,
    AVG(quality_score) as avg_quality_24h,
    AVG(latency_ms) as avg_latency_24h,
    SUM(cost_usd) as total_cost_24h,
    SUM(CASE WHEN validation_passed = TRUE THEN 1 ELSE 0 END) / COUNT(*) * 100 as success_rate_24h,
    MAX(created_at) as last_call_at
FROM llm_call_metrics
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY endpoint;
