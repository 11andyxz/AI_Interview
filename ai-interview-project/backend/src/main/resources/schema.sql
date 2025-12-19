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
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `candidate_id` int NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `language` varchar(50) DEFAULT NULL,
  `tech_stack` varchar(255) DEFAULT NULL,
  `programming_languages` json DEFAULT NULL,
  `date` date DEFAULT NULL,
  `status` varchar(50) DEFAULT NULL,
  `use_custom_knowledge` tinyint(1) DEFAULT 0,
  `started_at` timestamp NULL COMMENT '面试开始时间',
  `ended_at` timestamp NULL COMMENT '面试结束时间',
  `duration_seconds` int DEFAULT NULL COMMENT '面试时长（秒）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_candidate_id` (`candidate_id`),
  INDEX `idx_user_id` (`user_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- API Key Config table
DROP TABLE IF EXISTS `api_key_config`;
CREATE TABLE `api_key_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `service_name` varchar(50) NOT NULL COMMENT '服务名称，如 openai',
  `api_key` varchar(500) NOT NULL COMMENT 'API密钥',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否激活',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_active` (`service_name`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Interview Message table (for conversation history persistence)
DROP TABLE IF EXISTS `interview_message`;
CREATE TABLE `interview_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `interview_id` varchar(36) NOT NULL COMMENT '面试ID',
  `user_message` text COMMENT '用户消息',
  `ai_message` text COMMENT 'AI回答',
  `message_type` varchar(20) DEFAULT 'chat' COMMENT '消息类型：chat/evaluation',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_interview_id` (`interview_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Subscription Plan table
DROP TABLE IF EXISTS `subscription_plan`;
CREATE TABLE `subscription_plan` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '套餐名称',
  `description` text COMMENT '套餐描述',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `currency` varchar(10) DEFAULT 'USD' COMMENT '货币',
  `billing_cycle` varchar(20) NOT NULL COMMENT '计费周期：monthly/yearly',
  `features` json COMMENT '功能特性JSON',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否激活',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User Subscription table
DROP TABLE IF EXISTS `user_subscription`;
CREATE TABLE `user_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `plan_id` int NOT NULL COMMENT '套餐ID',
  `status` varchar(20) NOT NULL COMMENT '状态：active/cancelled/expired/trial',
  `start_date` timestamp NOT NULL COMMENT '开始时间',
  `end_date` timestamp NULL COMMENT '结束时间',
  `trial_end_date` timestamp NULL COMMENT '试用期结束时间',
  `stripe_subscription_id` varchar(255) NULL COMMENT 'Stripe订阅ID',
  `alipay_subscription_id` varchar(255) NULL COMMENT '支付宝订阅ID',
  `payment_method` varchar(20) NULL COMMENT '支付方式：stripe/alipay',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`plan_id`) REFERENCES `subscription_plan`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payment Transaction table
DROP TABLE IF EXISTS `payment_transaction`;
CREATE TABLE `payment_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `subscription_id` BIGINT NULL COMMENT '订阅ID',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `currency` varchar(10) DEFAULT 'USD' COMMENT '货币',
  `payment_method` varchar(20) NOT NULL COMMENT '支付方式：stripe/alipay',
  `transaction_id` varchar(255) NOT NULL COMMENT '第三方交易ID',
  `status` varchar(20) NOT NULL COMMENT '状态：pending/success/failed/refunded',
  `metadata` json NULL COMMENT '额外信息JSON',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_transaction_id` (`transaction_id`),
  INDEX `idx_status` (`status`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User Notes table (for My Notes feature)
DROP TABLE IF EXISTS `user_note`;
CREATE TABLE `user_note` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `type` varchar(20) NOT NULL DEFAULT 'general' COMMENT '笔记类型：interview/general',
  `title` varchar(255) NOT NULL COMMENT '笔记标题',
  `content` text COMMENT '笔记内容',
  `interview_id` varchar(36) NULL COMMENT '关联的面试ID（如果是面试笔记）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_type` (`type`),
  INDEX `idx_interview_id` (`interview_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User Resume table (for My Resume feature)
DROP TABLE IF EXISTS `user_resume`;
CREATE TABLE `user_resume` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_path` varchar(500) NOT NULL COMMENT '文件路径',
  `file_size` BIGINT COMMENT '文件大小（字节）',
  `file_type` varchar(50) COMMENT '文件类型',
  `resume_text` text COMMENT '简历文本内容',
  `analyzed` tinyint(1) DEFAULT 0 COMMENT '是否已分析',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Knowledge Base table (for Knowledge Base feature)
DROP TABLE IF EXISTS `knowledge_base`;
CREATE TABLE `knowledge_base` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NULL COMMENT '用户ID（NULL表示系统提供）',
  `type` varchar(20) NOT NULL DEFAULT 'user' COMMENT '类型：user/system',
  `name` varchar(255) NOT NULL COMMENT '知识库名称',
  `description` text COMMENT '描述',
  `content` json COMMENT '知识库内容JSON',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否激活',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_type` (`type`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mock Interview table (for Mock Interview feature)
DROP TABLE IF EXISTS `mock_interview`;
CREATE TABLE `mock_interview` (
  `id` varchar(36) NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `title` varchar(255) DEFAULT NULL COMMENT '面试标题',
  `position_type` varchar(255) DEFAULT NULL COMMENT '职位类型',
  `programming_languages` json DEFAULT NULL COMMENT '编程语言',
  `language` varchar(50) DEFAULT NULL COMMENT '面试语言',
  `status` varchar(50) DEFAULT 'practice' COMMENT '状态：practice/completed',
  `current_question_index` int DEFAULT 0 COMMENT '当前问题索引',
  `score` decimal(5,2) NULL COMMENT '得分',
  `feedback` text COMMENT '反馈',
  `started_at` timestamp NULL COMMENT '开始时间',
  `ended_at` timestamp NULL COMMENT '结束时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mock Interview Message table
DROP TABLE IF EXISTS `mock_interview_message`;
CREATE TABLE `mock_interview_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `mock_interview_id` varchar(36) NOT NULL COMMENT '模拟面试ID',
  `question_text` text COMMENT '问题文本',
  `answer_text` text COMMENT '回答文本',
  `hint_shown` tinyint(1) DEFAULT 0 COMMENT '是否显示过提示',
  `score` decimal(5,2) NULL COMMENT '该问题得分',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_mock_interview_id` (`mock_interview_id`),
  FOREIGN KEY (`mock_interview_id`) REFERENCES `mock_interview`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User Points table (for points tracking)
DROP TABLE IF EXISTS `user_points`;
CREATE TABLE `user_points` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `points` INT NOT NULL DEFAULT 0 COMMENT '积分',
  `last_updated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

