USE ai_interview;

-- ============================
-- Candidate Table + Seed Data
-- ============================

CREATE TABLE IF NOT EXISTS candidate (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100),
    resume_text TEXT
);

INSERT INTO candidate (name, email, resume_text) VALUES
('Alice Johnson', 'alice@example.com', 'Experienced software engineer skilled in Java, Python, and AI.'),
('Bob Chen', 'bob@example.com', 'Data analyst with strong SQL, statistics, and machine learning background.'),
('Charlie Kim', 'charlie@example.com', 'Full-stack developer with React, Node.js, and system design expertise.');


-- ============================
-- Interview Metadata Table
-- ============================

CREATE TABLE IF NOT EXISTS interview (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255),
    language VARCHAR(50),
    tech_stack VARCHAR(255),
    date DATE,
    status VARCHAR(50)
);


-- ============================
-- Interview Message Table
-- ============================

CREATE TABLE IF NOT EXISTS interview_message (
    id INT AUTO_INCREMENT PRIMARY KEY,
    interview_id VARCHAR(36),
    sender ENUM('user','assistant'),
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_interview_id ON interview_message(interview_id);
