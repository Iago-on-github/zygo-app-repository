ALTER TABLE customer_table
    ADD COLUMN IF NOT EXISTS health_declaration_request_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS student_enrollment_request_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS customer_enrollment_required_type (
                                                                 customer_id   UUID        NOT NULL REFERENCES customer_table (id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    PRIMARY KEY (customer_id, document_type));