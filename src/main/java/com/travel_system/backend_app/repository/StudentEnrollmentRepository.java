package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, UUID> {
    boolean existsByCourseId(UUID courseId);

    boolean existsByCourseInstitutionId(UUID institutionId);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM student_enrollment_table e
            JOIN institution_course_table c ON c.id = e.institution_course_id
            WHERE c.institution_id = :institutionId AND e.pool_of_enrollment = :poolOfEnrollment)
        """, nativeQuery = true)
    boolean existsPoolOfEnrollmentInInstitutionIgnoringTenant(@Param("institutionId") UUID institutionId, @Param("poolOfEnrollment") String poolOfEnrollment);
}
