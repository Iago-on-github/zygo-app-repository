ALTER TABLE sensitive_operations_table
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;

ALTER TABLE sensitive_operations_table
    ALTER COLUMN payload DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sensitive_operations_status_expires_at
    ON sensitive_operations_table (sensitive_operation_status, expires_at);

ALTER TABLE sensitive_operations_table
DROP CONSTRAINT IF EXISTS sensitive_operations_table_sensitive_operation_type_check;

ALTER TABLE sensitive_operations_table
    ADD CONSTRAINT sensitive_operations_table_sensitive_operation_type_check
        CHECK (sensitive_operation_type IN (
                                            'CREATE_PLATFORM_ADMIN',
                                            'UPDATE_PLATFORM_ADMIN',
                                            'DELETE_PLATFORM_ADMIN',
                                            'CHANGE_CUSTOMER_PLAN'
            ));