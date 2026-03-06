-- Create candidate_skill_profile table for longitudinal skill tracking
CREATE TABLE candidate_skill_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100),
    role_id BIGINT,
    
    -- Cumulative metrics
    cumulative_score DOUBLE NOT NULL DEFAULT 0.0,
    question_count INT NOT NULL DEFAULT 0,
    
    -- Score trend tracking (JSON array of recent scores)
    score_trend JSON,
    
    -- Statistical measures
    score_mean DOUBLE,
    score_std DOUBLE,
    confidence DOUBLE,
    
    -- Prediction metrics
    predicted_final_score DOUBLE,
    pass_probability DOUBLE,
    score_stability DOUBLE,
    
    -- Early stopping decision
    early_stopping_triggered BOOLEAN DEFAULT FALSE,
    early_stopping_reason VARCHAR(50),
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Indexes
    UNIQUE KEY uk_session (session_id),
    INDEX idx_user_role (user_id, role_id),
    INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
