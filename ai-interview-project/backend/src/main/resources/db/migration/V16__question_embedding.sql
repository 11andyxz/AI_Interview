-- Question Embedding & Topic Clustering for Semantic Search
CREATE TABLE question_embedding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id VARCHAR(100) NOT NULL,
    role_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    embedding BLOB NOT NULL,
    cluster_id INT DEFAULT -1,
    cluster_label VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_question_role_emb (question_id, role_id),
    INDEX idx_cluster (cluster_id),
    INDEX idx_role_cluster (role_id, cluster_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
