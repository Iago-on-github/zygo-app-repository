ALTER TABLE document_table
DROP CONSTRAINT document_table_document_type_check;

ALTER TABLE document_table
    ADD CONSTRAINT document_table_document_type_check
        CHECK (
            document_type IN (
                              'MEDICAL_REPORT',
                              'EMERGENCY_ACTION_PLAN',
                              'PRESCRIPTION',
                              'OTHER',
                              'IDENTITY_DOCUMENT',
                              'INSTITUTION_ENROLLMENT_PROOF'
                )
            );