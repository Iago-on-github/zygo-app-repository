package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.InstitutionCourse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstitutionCourseRepository extends JpaRepository<InstitutionCourse, UUID> {
    Optional<InstitutionCourse> findByIdAndInstitutionId(UUID id, UUID institutionId);

    @Query(value = """
        SELECT * FROM institution_course_table
        WHERE id IN (:courseIds)
          AND institution_id = :institutionId
          AND customer_id = :customerId
          AND status = 'ACTIVE'
        """, nativeQuery = true)
    List<InstitutionCourse> findActiveByIdsAndInstitutionIgnoringTenant(@Param("courseIds") Collection<UUID> courseIds, @Param("institutionId") UUID institutionId, @Param("customerId") UUID customerId);

    Page<InstitutionCourse> findAllByInstitutionId(@Param("institutionId") UUID institutionId, Pageable pageable);

    @Query(value = "SELECT * FROM institution_course_table WHERE customer_id = :customerId AND status = 'ACTIVE' ORDER BY name", nativeQuery = true)
    List<InstitutionCourse> findActiveByCustomerIdIgnoringTenant(@Param("customerId") UUID customerId);
}
