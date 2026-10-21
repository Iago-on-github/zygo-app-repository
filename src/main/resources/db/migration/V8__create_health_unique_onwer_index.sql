-- healthDeclaration
CREATE UNIQUE INDEX IF NOT EXISTS uq_health_declaration_current_student
    ON health_declaration_table (student_id) WHERE student_id IS NOT NULL AND health_declaration_status = 'CURRENT';

CREATE UNIQUE INDEX IF NOT EXISTS uq_health_declaration_current_driver
    ON health_declaration_table (driver_id) WHERE driver_id IS NOT NULL AND health_declaration_status = 'CURRENT';

-- healthCondition (nao possui refs diretas a student/driver, herda ref única do declaration)
CREATE INDEX IF NOT EXISTS idx_health_condition_declaration
    ON health_condition_table (declaration_id);

-- healthConsent
CREATE UNIQUE INDEX IF NOT EXISTS idx_health_consent_student
    ON health_consent_table (student_id) WHERE student_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_health_consent_driver
    ON health_consent_table (driver_id) WHERE driver_id IS NOT NULL;