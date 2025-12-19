-- ============================================
-- Migration: Add user_id column to interview table
-- Date: 2025-12-19
-- Description: Add user ownership to interviews for data security
-- ============================================

-- Update existing interviews to assign them to the default test user (id=1)
-- This is a data migration for existing records
UPDATE `interview` SET `user_id` = 1 WHERE `user_id` IS NULL OR `user_id` = 0;

-- Add user_id column to interview table (nullable first)
ALTER TABLE `interview`
ADD COLUMN `user_id` BIGINT NULL AFTER `id`;

-- Set default user_id for existing records
UPDATE `interview` SET `user_id` = 1 WHERE `user_id` IS NULL;

-- Now make it NOT NULL
ALTER TABLE `interview`
MODIFY COLUMN `user_id` BIGINT NOT NULL;

-- Add foreign key constraint
ALTER TABLE `interview`
ADD CONSTRAINT `fk_interview_user_id`
FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
ON DELETE CASCADE ON UPDATE CASCADE;

-- Add index for performance
CREATE INDEX `idx_interview_user_id` ON `interview` (`user_id`);

COMMIT;
