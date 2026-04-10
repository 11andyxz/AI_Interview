-- Week 14 Task 1: Topic Coverage Tracking
-- Tracks topic diversity metrics for interview sessions using Shannon entropy

CREATE TABLE topic_coverage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    role_id BIGINT NOT NULL,
    cluster_distribution JSON COMMENT 'Map of cluster_id -> question_count',
    total_questions INT DEFAULT 0 COMMENT 'Total questions asked',
    clusters_covered INT DEFAULT 0 COMMENT 'Number of unique clusters covered',
    total_clusters INT DEFAULT 0 COMMENT 'Total clusters available for role',
    coverage_ratio DOUBLE COMMENT 'clusters_covered / total_clusters',
    shannon_entropy DOUBLE COMMENT 'H = -Σ(p_i * log(p_i)) topic diversity metric',
    max_entropy DOUBLE COMMENT 'Maximum entropy = log(total_clusters)',
    normalized_entropy DOUBLE COMMENT 'shannon_entropy / max_entropy, range [0,1]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_role (session_id, role_id),
    UNIQUE KEY uk_session_role (session_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Topic coverage metrics per interview session';
