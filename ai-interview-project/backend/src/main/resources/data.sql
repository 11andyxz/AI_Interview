-- Insert test user with BCrypt encrypted password (only if not exists)
-- Password: 123456
-- Note: If user already exists with plain text password, it needs to be updated via DataInitializer or manually
INSERT IGNORE INTO `user` (`username`, `password`) 
VALUES ('test', '$2a$10$Y7/QSWztMrGrys099pDDzuo.BifShDoCl.H9d82muW5aWQ9HkSXUu');

-- Insert default API key for OpenAI (only if not exists)
INSERT IGNORE INTO `api_key_config` (`service_name`, `api_key`, `is_active`)
VALUES ('openai', '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', 1);

-- Seed candidates if not exists
INSERT IGNORE INTO `candidate` (`id`, `name`, `email`, `phone`, `resume_text`, `skills`, `experience_years`, `education`, `status`)
VALUES
(1, 'Andy Zhang', 'andy@example.com', '1234567890',
 'Backend Developer with 3+ years experience in Java, Spring Boot, and distributed systems.',
 '["Java","Spring Boot","MySQL","Redis","Microservices"]', 3, 'Bachelor', 'pending'),
(2, 'Sarah Chen', 'sarah@example.com', '0987654321',
 'Frontend Developer specializing in React and TypeScript.',
 '["React","TypeScript","JavaScript","D3.js","Redux"]', 2, 'Bachelor', 'pending');

