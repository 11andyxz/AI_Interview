-- Create response_feature_cache table for ML feature storage
CREATE TABLE response_feature_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    question_id VARCHAR(100) NOT NULL,
    response_text TEXT NOT NULL,
    
    -- Feature vector (JSON array)
    feature_vector JSON NOT NULL,
    
    -- Scores
    predicted_score DOUBLE,
    llm_score DOUBLE,
    prediction_error DOUBLE,
    
    -- Metadata
    feature_extraction_time_ms INT,
    model_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_session_question (session_id, question_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
