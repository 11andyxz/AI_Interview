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
 '["React","TypeScript","JavaScript","D3.js","Redux"]', 2, 'Bachelor', 'pending'),
(3, 'Backend Developer', 'backend@example.com', '1111111111',
 'Experienced Backend Developer with expertise in server-side development, API design, and system architecture. Strong background in building scalable applications and optimizing database performance. Proficient in multiple programming languages and frameworks.',
 '["Java","Python","Node.js","Spring Boot","Django","PostgreSQL","MongoDB","Redis","Docker","Kubernetes","Microservices","RESTful API"]', 5, 'Bachelor', 'pending'),
(4, 'Frontend Developer', 'frontend@example.com', '2222222222',
 'Creative Frontend Developer specializing in modern web technologies and user experience design. Passionate about building responsive and interactive web applications. Strong focus on performance optimization and accessibility.',
 '["React","Vue.js","Angular","TypeScript","JavaScript","HTML5","CSS3","Sass","Webpack","Next.js","Redux","GraphQL"]', 4, 'Bachelor', 'pending'),
(5, 'Full Stack Developer', 'fullstack@example.com', '3333333333',
 'Versatile Full Stack Developer with comprehensive knowledge of both frontend and backend technologies. Experienced in end-to-end application development, from UI/UX design to database optimization and cloud deployment.',
 '["JavaScript","TypeScript","React","Node.js","Express","Python","Django","PostgreSQL","MongoDB","AWS","Docker","CI/CD"]', 6, 'Master', 'pending'),
(6, 'AI Engineer', 'ai@example.com', '4444444444',
 'AI Engineer with deep expertise in artificial intelligence, machine learning, and neural networks. Experienced in developing intelligent systems and AI applications. Strong background in deep learning frameworks and model deployment.',
 '["Python","TensorFlow","PyTorch","Keras","Scikit-learn","Deep Learning","Neural Networks","NLP","Computer Vision","MLOps","AWS SageMaker"]', 4, 'Master', 'pending'),
(7, 'Machine Learning Engineer', 'ml@example.com', '5555555555',
 'Machine Learning Engineer specializing in developing and deploying ML models at scale. Strong background in data science, model optimization, and production ML systems. Experienced in feature engineering, model training, and MLOps practices.',
 '["Python","R","TensorFlow","PyTorch","Scikit-learn","Pandas","NumPy","Apache Spark","Kubernetes","MLflow","Feature Engineering","Model Deployment"]', 5, 'Master', 'pending');

