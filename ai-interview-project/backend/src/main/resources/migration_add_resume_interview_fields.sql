-- ============================================
-- Migration: Add Resume and Interview Type Fields
-- Version: 1.1.0
-- Date: 2025-12-20
-- Description: Add fields to support resume-based interviews
-- ============================================

-- Add analysis_data field to user_resume table for storing structured analysis results
ALTER TABLE `user_resume`
ADD COLUMN `analysis_data` JSON COMMENT 'Structure analysis data from AI (level, techStack, skills, etc.)' AFTER `analysis_result`;

-- Add resume_id and interview_type fields to interview table
ALTER TABLE `interview`
ADD COLUMN `resume_id` BIGINT COMMENT 'Associated resume ID for resume-based interviews' AFTER `candidate_id`,
ADD COLUMN `interview_type` VARCHAR(20) DEFAULT 'general' COMMENT 'Interview type: general or resume-based' AFTER `use_custom_knowledge`;

-- Add resume_id and interview_type fields to mock_interview table
ALTER TABLE `mock_interview`
ADD COLUMN `resume_id` BIGINT COMMENT 'Associated resume ID for resume-based interviews' AFTER `user_id`,
ADD COLUMN `interview_type` VARCHAR(20) DEFAULT 'general' COMMENT 'Interview type: general or resume-based' AFTER `language`;

-- Add foreign key constraints for resume_id fields
ALTER TABLE `interview`
ADD CONSTRAINT `fk_interview_resume_id`
FOREIGN KEY (`resume_id`) REFERENCES `user_resume`(`id`) ON DELETE SET NULL;

ALTER TABLE `mock_interview`
ADD CONSTRAINT `fk_mock_interview_resume_id`
FOREIGN KEY (`resume_id`) REFERENCES `user_resume`(`id`) ON DELETE SET NULL;

-- Add indexes for the new fields
CREATE INDEX `idx_interview_resume_id` ON `interview` (`resume_id`);
CREATE INDEX `idx_interview_type` ON `interview` (`interview_type`);
CREATE INDEX `idx_mock_interview_resume_id` ON `mock_interview` (`resume_id`);
CREATE INDEX `idx_mock_interview_type` ON `mock_interview` (`interview_type`);

-- Add check constraints for interview_type values
ALTER TABLE `interview`
ADD CONSTRAINT `chk_interview_type`
CHECK (`interview_type` IN ('general', 'resume-based'));

ALTER TABLE `mock_interview`
ADD CONSTRAINT `chk_mock_interview_type`
CHECK (`interview_type` IN ('general', 'resume-based'));

-- Update existing records to have default interview_type
UPDATE `interview` SET `interview_type` = 'general' WHERE `interview_type` IS NULL;
UPDATE `mock_interview` SET `interview_type` = 'general' WHERE `interview_type` IS NULL;
