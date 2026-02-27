-- Adaptive Interview Difficulty: Question Calibration for IRT
CREATE TABLE question_difficulty_calibration (
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
