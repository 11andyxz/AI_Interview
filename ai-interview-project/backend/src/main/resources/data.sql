-- Insert test user (only if not exists)
INSERT IGNORE INTO `user` (`username`, `password`) 
VALUES ('test', '123456');

-- Seed candidates if not exists
INSERT IGNORE INTO `candidate` (`id`, `name`, `email`, `phone`, `resume_text`, `skills`, `experience_years`, `education`, `status`)
VALUES
(1, 'Andy Zhang', 'andy@example.com', '1234567890',
 'Backend Developer with 3+ years experience in Java, Spring Boot, and distributed systems.',
 '["Java","Spring Boot","MySQL","Redis","Microservices"]', 3, 'Bachelor', 'pending'),
(2, 'Sarah Chen', 'sarah@example.com', '0987654321',
 'Frontend Developer specializing in React and TypeScript.',
 '["React","TypeScript","JavaScript","D3.js","Redux"]', 2, 'Bachelor', 'pending');

