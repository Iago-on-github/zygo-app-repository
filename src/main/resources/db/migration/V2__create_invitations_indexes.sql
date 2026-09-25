CREATE INDEX IF NOT EXISTS idx_invitation_invited_user_status
    ON invitation_table (invited_user_account_id, invitation_status);

CREATE INDEX IF NOT EXISTS idx_invitation_customer_status_created
    ON invitation_table (customer_id, invitation_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_invitation_pending_expires_at
    ON invitation_table (expires_at) WHERE invitation_status = 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS ux_invitation_pending_customer_user
    ON invitation_table (customer_id, invited_user_account_id) WHERE invitation_status = 'PENDING';