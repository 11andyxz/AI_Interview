-- ============================================
-- AI Interview Database Schema
-- Database: ai_interview
-- ============================================

-- Drop table if exists (for clean setup)
DROP TABLE IF EXISTS `user`;

-- Create user table
CREATE TABLE `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(255) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert test user
INSERT INTO `user` (`username`, `password`) 
VALUES ('test', '123456');

