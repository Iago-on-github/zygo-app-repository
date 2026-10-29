ALTER TABLE sensitive_access_log_table
    DROP CONSTRAINT sensitive_access_log_table_resource_type_check;

ALTER TABLE sensitive_access_log_table
    ADD CONSTRAINT sensitive_access_log_table_resource_type_check
        CHECK (
            resource_type IN (
                'DOCUMENT',
                'HEALTH_DECLARATION',
                'TRIP_HEALTH_SUMMARY',
                'TRANSPORT_REGISTRATION',
                'REGISTRATION_DOCUMENT',
                'REGISTRATION_PHOTO'
            )
        );