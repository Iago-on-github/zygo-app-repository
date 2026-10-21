package com.travel_system.backend_app.repository;

import com.google.common.collect.Collections2;
import com.travel_system.backend_app.model.HealthCondition;
import com.travel_system.backend_app.model.enums.HealthDeclarationStatus;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthConditionRepository extends JpaRepository<HealthCondition, UUID> {
    @Query("""
    SELECT c FROM HealthCondition c
    JOIN FETCH c.declaration d
    WHERE c.id = :conditionId
      AND d.healthDeclarationStatus = :status
    """)
    Optional<HealthCondition> findByIdInDeclarationWithStatus(@Param("conditionId") UUID conditionId,
                                                              @Param("status") HealthDeclarationStatus status);

/*    @Query("SELECT hc FROM HealthCondition hc WHERE hc.declaration.hasCondition = :hasCondition AND hc.declaration.studentId")
    Optional<HealthCondition> findByStudentIdAndHasCondition(UUID studentId, boolean hasCondition);*/
}
