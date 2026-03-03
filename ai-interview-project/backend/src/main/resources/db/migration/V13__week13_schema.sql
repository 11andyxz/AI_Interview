-- Week 13 Schema Migration
-- Experiment framework + Adaptive difficulty + Output conformance tracking

-- Week 11 Prerequisite: Experiment Framework
CREATE TABLE IF NOT EXISTS experiment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    traffic_percentage DOUBLE DEFAULT 50.0,
    min_sample_size INT DEFAULT 100,
    baseline_variant VARCHAR(50) DEFAULT 'baseline',
    treatment_variant VARCHAR(50) DEFAULT 'variant',
    baseline_config TEXT,
    treatment_config TEXT,
    target_endpoint VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    INDEX idx_status (status),
    INDEX idx_endpoint_status (target_endpoint, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS experiment_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id BIGINT NOT NULL,
    variant VARCHAR(50) NOT NULL,
    quality_score DOUBLE,
    latency_ms BIGINT,
    token_count INT,
    session_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_experiment_variant (experiment_id, variant),
    INDEX idx_experiment_id (experiment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Task 1: Prompt Versioning
CREATE TABLE IF NOT EXISTS prompt_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_key VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    description VARCHAR(500),
    is_active TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prompt_key_version (prompt_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Task 2: Question Difficulty Calibration (IRT parameters)
CREATE TABLE IF NOT EXISTS question_difficulty_calibration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id VARCHAR(100) NOT NULL,
    role_id VARCHAR(100) NOT NULL,
    difficulty_b DOUBLE DEFAULT 0.0,
    discrimination_a DOUBLE DEFAULT 1.0,
    response_count INT DEFAULT 0,
    mean_score DOUBLE DEFAULT 0.0,
    score_variance DOUBLE DEFAULT 0.25,
    last_calibrated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_question_role (question_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Task 3: Output Conformance Tracking
CREATE TABLE IF NOT EXISTS llm_output_conformance (
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
