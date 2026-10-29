package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.RegistrationNumberSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RegistrationNumberSequenceRepository extends JpaRepository<RegistrationNumberSequence, UUID> {

    @Query(value = """
        INSERT INTO registration_number_sequence_table (customer_id, last_value)
        VALUES (:customerId, 1)
        ON CONFLICT (customer_id) DO UPDATE SET last_value = registration_number_sequence_table.last_value + 1
        RETURNING last_value
        """, nativeQuery = true)
    Long nextValue(@Param("customerId") UUID customerId);
}
