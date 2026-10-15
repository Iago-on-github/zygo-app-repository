CREATE UNIQUE INDEX idx_unique_customer_owner_access_sensitive_data_log
    ON sensitive_access_log_table (customer_id, owner_id, access_at) WHERE customer_id IS NOT NULL
        AND owner_id IS NOT NULL
        AND access_at IS NOT NULL