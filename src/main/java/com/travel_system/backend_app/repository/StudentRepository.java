package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.StudentTokensDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.Shift;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.Size;
import java.util.*;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    @Query("SELECT s FROM Student s WHERE s.userAccount.email = :email")
    Optional<Student> findByEmail(@Param("email") String email);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM student_table WHERE telephone = :telephone)", nativeQuery = true)
    boolean existsByTelephoneIgnoringTenant(@Param("telephone") String telephone);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM user_account_table WHERE email = :email)", nativeQuery = true)
    boolean existsByEmailIgnoringTenant(@Param("email") String email);

    @Query(value = "SELECT COUNT(*) FROM student_table WHERE customer_id = :customerId", nativeQuery = true)
    long countStudentsInThisCustomer(@Param("customerId") UUID customerId);

/*    Optional<Student> findByEmailOrTelephoneAndIdNot(String email, String telephone, UUID id);

    Optional<Student> findByEmailOrTelephone(String email, String telephone);*/

    Page<Student> findAllByStatus(GeneralStatus status, Pageable pageable);

//    Set<String> findByCustomerId(UUID customerId);

    Optional<Student> findByUserAccountId(@Param("userAccountId") UUID userAccountId);

    boolean existsByTelephone(@Param("telephone") String telephone);

    long countByResponsibleAdultId(@Param("responsibleAdultId") UUID responsibleAdultId);

    @Query(value = """
        SELECT s.* FROM student_table s
        JOIN user_account_table ua ON ua.id = s.user_account_id
        LEFT JOIN address_table a ON a.id = s.address_id
        WHERE s.customer_id = :customerId
          AND (CAST(:email AS text) IS NULL OR ua.email = CAST(:email AS text))
          AND (CAST(:name AS text) IS NULL OR similarity(s.name, CAST(:name AS text)) > 0.3)
          AND (CAST(:lastName AS text) IS NULL OR similarity(s.last_name, CAST(:lastName AS text)) > 0.3)
          AND (CAST(:neighborhood AS text) IS NULL OR similarity(a.neighborhood, CAST(:neighborhood AS text)) > 0.3)
          AND ((CAST(:institutionName AS text) IS NULL AND CAST(:institutionType AS text) IS NULL) OR EXISTS (
                SELECT 1 FROM student_enrollment_table e
                JOIN institution_course_table c ON c.id = e.institution_course_id
                JOIN institution_table i ON i.id = c.institution_id
                WHERE e.student_id = s.id
                  AND e.status = 'ACTIVE'
                  AND (CAST(:institutionName AS text) IS NULL
                       OR similarity(i.institution_name, CAST(:institutionName AS text)) > 0.3)
                  AND (CAST(:institutionType AS text) IS NULL
                       OR i.institution_type = CAST(:institutionType AS text))))
          AND (CAST(:shift AS text) IS NULL OR EXISTS (
                SELECT 1 FROM student_shifts ss
                WHERE ss.student_id = s.id AND ss.student_shift = CAST(:shift AS text)))
        ORDER BY
            COALESCE(similarity(s.name, CAST(:name AS text)), 0)
          + COALESCE(similarity(s.last_name, CAST(:lastName AS text)), 0)
          + COALESCE(similarity(a.neighborhood, CAST(:neighborhood AS text)), 0)
          + COALESCE((SELECT MAX(similarity(i.institution_name, CAST(:institutionName AS text)))
                      FROM student_enrollment_table e
                      JOIN institution_course_table c ON c.id = e.institution_course_id
                      JOIN institution_table i ON i.id = c.institution_id
                      WHERE e.student_id = s.id AND e.status = 'ACTIVE'), 0) DESC,
            s.name ASC
        """,
            countQuery = """
        SELECT COUNT(*) FROM student_table s
        JOIN user_account_table ua ON ua.id = s.user_account_id
        LEFT JOIN address_table a ON a.id = s.address_id
        WHERE s.customer_id = :customerId
          AND (CAST(:email AS text) IS NULL OR ua.email = CAST(:email AS text))
          AND (CAST(:name AS text) IS NULL OR similarity(s.name, CAST(:name AS text)) > 0.3)
          AND (CAST(:lastName AS text) IS NULL OR similarity(s.last_name, CAST(:lastName AS text)) > 0.3)
          AND (CAST(:neighborhood AS text) IS NULL OR similarity(a.neighborhood, CAST(:neighborhood AS text)) > 0.3)
          AND ((CAST(:institutionName AS text) IS NULL AND CAST(:institutionType AS text) IS NULL) OR EXISTS (
                SELECT 1 FROM student_enrollment_table e
                JOIN institution_course_table c ON c.id = e.institution_course_id
                JOIN institution_table i ON i.id = c.institution_id
                WHERE e.student_id = s.id
                  AND e.status = 'ACTIVE'
                  AND (CAST(:institutionName AS text) IS NULL
                       OR similarity(i.institution_name, CAST(:institutionName AS text)) > 0.3)
                  AND (CAST(:institutionType AS text) IS NULL
                       OR i.institution_type = CAST(:institutionType AS text))))
          AND (CAST(:shift AS text) IS NULL OR EXISTS (
                SELECT 1 FROM student_shifts ss
                WHERE ss.student_id = s.id AND ss.student_shift = CAST(:shift AS text)))
        """,
            nativeQuery = true)
    Page<Student> findAllByOptionalFilters(
            @Param("customerId") UUID customerId,
            @Param("email") String email,
            @Param("name") String name,
            @Param("lastName") String lastName,
            @Param("neighborhood") String neighborhood,
            @Param("institutionName") String institutionName,
            @Param("institutionType") String institutionType,
            @Param("shift") String shift,
            Pageable pageable);

    @Query(value = """
        SELECT s.* FROM student_table s
        WHERE s.customer_id = :customerId
          AND EXISTS (
                SELECT 1 FROM student_enrollment_table e
                JOIN institution_course_table c ON c.id = e.institution_course_id
                WHERE e.student_id = s.id
                  AND e.status = 'ACTIVE'
                  AND c.institution_id = :institutionId
                  AND (:filterByCourses = false OR c.id IN (:courseIds)))
        ORDER BY s.name ASC
        """,
            countQuery = """
        SELECT COUNT(*) FROM student_table s
        WHERE s.customer_id = :customerId
          AND EXISTS (
                SELECT 1 FROM student_enrollment_table e
                JOIN institution_course_table c ON c.id = e.institution_course_id
                WHERE e.student_id = s.id
                  AND e.status = 'ACTIVE'
                  AND c.institution_id = :institutionId
                  AND (:filterByCourses = false OR c.id IN (:courseIds)))
        """,
            nativeQuery = true)
    Page<Student> findAllByInstitution(
            @Param("customerId") UUID customerId,
            @Param("institutionId") UUID institutionId,
            @Param("filterByCourses") boolean filterByCourses,
            @Param("courseIds") Collection<UUID> courseIds,
            Pageable pageable);

    Optional<Student> findByCpf(@Param("cpf") String cpf);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM student_table WHERE user_account_id = :userAccountId)", nativeQuery = true)
    boolean existsByUserAccountIdIgnoringTenant(@Param("userAccountId") UUID userAccountId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM student_table WHERE cpf = :cpf)", nativeQuery = true)
    boolean existsByCpfIgnoringTenant(String cpf);
}
