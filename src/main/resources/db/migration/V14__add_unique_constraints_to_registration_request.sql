CREATE UNIQUE INDEX IF NOT EXISTS uq_registration_request_open
    ON registration_request_table (transport_registration_id)
    WHERE status IN ('DRAFT', 'UNDER_REVIEW');

CREATE INDEX IF NOT EXISTS idx_registration_request_review_queue
    ON registration_request_table (customer_id, status, submitted_at);

CREATE INDEX IF NOT EXISTS idx_registration_request_registration
    ON registration_request_table (transport_registration_id, created_at);

CREATE INDEX IF NOT EXISTS idx_registration_request_term_status
    ON registration_request_table (target_term_id, status);

