ALTER TABLE transport_registration_table ADD CONSTRAINT chk_transport_registration_owner
        CHECK (num_nonnulls(student_id, driver_id) = 1);

ALTER TABLE transport_registration_table ADD CONSTRAINT chk_transport_registration_active
        CHECK (status <> 'ACTIVE' OR (registration_number IS NOT NULL AND customer_term_id IS NOT NULL));

CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_registration_number
    ON transport_registration_table (customer_id, registration_number) WHERE registration_number IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_registration_student_id
    ON transport_registration_table (student_id) WHERE student_id IS NOT NULL AND status <> 'CLOSED';

CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_registration_driver_id
    ON transport_registration_table (driver_id) WHERE driver_id IS NOT NULL AND status <> 'CLOSED';

CREATE INDEX IF NOT EXISTS idx_transport_registration_customer_status_term
    ON transport_registration_table (customer_id, status, customer_term_id);

-- fk
ALTER TABLE transport_registration_table ADD CONSTRAINT fk_transport_registration_customer_term
        FOREIGN KEY (customer_term_id) REFERENCES customer_term_table (id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_transport_registration_customer_term
    ON transport_registration_table (customer_term_id);
