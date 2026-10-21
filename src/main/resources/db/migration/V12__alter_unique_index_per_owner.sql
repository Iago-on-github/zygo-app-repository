
DROP INDEX IF EXISTS idx_health_consent_student;
DROP INDEX IF EXISTS idx_health_consent_driver;

CREATE UNIQUE INDEX IF NOT EXISTS uq_health_consent_active_student
    ON health_consent_table (student_id) WHERE revoked_at IS NULL AND student_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_health_consent_active_driver
    ON health_consent_table (driver_id) WHERE revoked_at IS NULL AND driver_id IS NOT NULL;