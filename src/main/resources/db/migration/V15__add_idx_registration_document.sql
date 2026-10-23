CREATE INDEX IF NOT EXISTS idx_registration_document_audit_request
    ON registration_document_audit_table (registration_request_id) WHERE registration_request_id IS NOT NULL;