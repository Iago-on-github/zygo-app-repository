ALTER TABLE user_account_table
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP;

ALTER TABLE user_account_table
    ADD COLUMN IF NOT EXISTS inactive_at TIMESTAMP;

UPDATE user_account_table
    SET last_activity_at = CURRENT_TIMESTAMP WHERE last_activity_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_account_last_activity_at_active
    ON user_account_table (last_activity_at) WHERE inactive_at IS NULL;
