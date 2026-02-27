-- Structured Output Guardrails: LLM Output Conformance Tracking
CREATE TABLE llm_output_conformance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    model VARCHAR(50) NOT NULL,
    prompt_version VARCHAR(20),
    initial_valid TINYINT(1) NOT NULL,
    repair_attempts INT DEFAULT 0,
    final_valid TINYINT(1) NOT NULL,
    used_fallback TINYINT(1) DEFAULT 0,
    validation_errors TEXT,
    total_latency_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_endpoint_created (endpoint, created_at),
    INDEX idx_model_valid (model, initial_valid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
