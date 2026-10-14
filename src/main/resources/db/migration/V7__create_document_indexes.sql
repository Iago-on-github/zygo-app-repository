
-- fila de revisão do admin (pendentes do Customer)
CREATE INDEX idx_document_customer_status
    ON document_table (customer_id, document_status);

-- documentos por dono
CREATE INDEX idx_document_student
    ON document_table (student_id) WHERE student_id IS NOT NULL;

CREATE INDEX idx_document_driver
    ON document_table (driver_id) WHERE driver_id IS NOT NULL;

-- job de vencimento: só documentos ativos com validade
CREATE INDEX idx_document_valid_until
    ON document_table (valid_until)
    WHERE valid_until IS NOT NULL
      AND document_status IN ('PENDING_REVIEW', 'APPROVED');