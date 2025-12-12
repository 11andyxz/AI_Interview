-- ============================================
-- AI Interview Database Schema
-- Database: ai_interview
-- ============================================

-- Drop table if exists (for clean setup)
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(255) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `user` (`username`, `password`) 
VALUES ('test', '123456');

-- Candidate table
DROP TABLE IF EXISTS `candidate`;
CREATE TABLE `candidate` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `resume_text` text,
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `skills` json DEFAULT NULL COMMENT '技能列表JSON',
  `experience_years` int DEFAULT NULL COMMENT '工作年限',
  `education` varchar(255) DEFAULT NULL COMMENT '学历',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态：pending/interviewed/hired/rejected',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Interview table
DROP TABLE IF EXISTS `interview`;
CREATE TABLE `interview` (
  `id` varchar(36) NOT NULL,
  `candidate_id` int NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `language` varchar(50) DEFAULT NULL,
  `tech_stack` varchar(255) DEFAULT NULL,
  `programming_languages` json DEFAULT NULL,
  `date` date DEFAULT NULL,
  `status` varchar(50) DEFAULT NULL,
  `use_custom_knowledge` tinyint(1) DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

