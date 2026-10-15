ALTER TABLE document_table
DROP CONSTRAINT IF EXISTS chk_document_category_rules;

ALTER TABLE document_table
    ADD CONSTRAINT chk_document_category_rules CHECK (
        (document_category = 'HEALTH'
            AND health_condition_id IS NOT NULL)
            OR
        (document_category = 'ENROLLMENT'
            AND health_condition_id IS NULL
            AND student_id IS NOT NULL)
        );

DROP INDEX IF EXISTS uq_document_active_condition_type;

CREATE UNIQUE INDEX uq_document_active_health_condition_type
    ON document_table (health_condition_id, document_type)
    WHERE document_category = 'HEALTH'
      AND document_status IN ('PENDING_REVIEW', 'APPROVED');

CREATE UNIQUE INDEX uq_document_active_enrollment_student_type
    ON document_table (student_id, document_type)
    WHERE document_category = 'ENROLLMENT'
      AND document_status IN ('PENDING_REVIEW', 'APPROVED');

DROP INDEX IF EXISTS idx_document_customer_status;

CREATE INDEX idx_document_customer_category_status
    ON document_table (customer_id, document_category, document_status);