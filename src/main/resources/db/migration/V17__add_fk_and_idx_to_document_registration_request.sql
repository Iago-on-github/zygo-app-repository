-- =====================================================================
-- documentos de matrícula vinculados à solicitação (registration_request)
-- =====================================================================

-- vínculo do documento com a solicitação de matrícula
ALTER TABLE document_table ADD COLUMN IF NOT EXISTS registration_request_id UUID;

ALTER TABLE document_table
    ADD CONSTRAINT fk_document_registration_request
        FOREIGN KEY (registration_request_id) REFERENCES registration_request_table (id);

-- FK sem índice obriga o PostgreSQL a varrer a tabela em cada exclusão de solicitação
CREATE INDEX IF NOT EXISTS idx_document_registration_request
    ON document_table (registration_request_id);

/*
 * regras por categoria
 * - todo documento tem exatamente um titular (aluno ou motorista)
 * - HEALTH: vinculado a uma condição de saúde, nunca a uma solicitação de matrícula
 * - ENROLLMENT: vinculado a uma solicitação de matrícula, nunca a uma condição de saúde
 * */
ALTER TABLE document_table DROP CONSTRAINT IF EXISTS chk_document_category_rules;

ALTER TABLE document_table
    ADD CONSTRAINT chk_document_category_rules CHECK (
        num_nonnulls(student_id, driver_id) = 1
            AND (
            (document_category = 'HEALTH'
                AND health_condition_id IS NOT NULL
                AND registration_request_id IS NULL)
                OR
            (document_category = 'ENROLLMENT'
                AND registration_request_id IS NOT NULL
                AND health_condition_id IS NULL)
            )
        );

-- a regra antiga (um documento ativo por aluno + tipo) quebra na renovação
DROP INDEX IF EXISTS uq_document_active_enrollment_student_type;

-- um documento ativo por tipo dentro da mesma solicitação
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_active_registration_request_type
    ON document_table (registration_request_id, document_type)
    WHERE registration_request_id IS NOT NULL
    AND document_status IN ('PENDING_REVIEW', 'APPROVED');