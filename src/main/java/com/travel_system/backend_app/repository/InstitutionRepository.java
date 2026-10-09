package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Institution;
import com.travel_system.backend_app.model.dtos.invitation.student.InstitutionCatalogResponseDTO;
import com.travel_system.backend_app.model.dtos.response.InstitutionResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    /*
     * Busca instituições com filtros opcionais.
     * Nome e curso toleram erros de digitação (similaridade acima de 0.3).
     * A ordenação prioriza as instituições mais parecidas com o nome e o curso buscados.
     */
    @Query(value = """
        SELECT i.* FROM institution_table i
        WHERE i.customer_id = :customerId
          AND (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))
          AND (CAST(:institutionName AS text) IS NULL
               OR similarity(i.institution_name, CAST(:institutionName AS text)) > 0.3)
          AND (CAST(:institutionType AS text) IS NULL
               OR i.institution_type = CAST(:institutionType AS text))
          AND (CAST(:course AS text) IS NULL OR EXISTS (
                SELECT 1 FROM institution_course_table ic
                WHERE ic.institution_id = i.id
                  AND ic.status = 'ACTIVE'
                  AND similarity(ic.name, CAST(:course AS text)) > 0.3))
        ORDER BY
            COALESCE(similarity(i.institution_name, CAST(:institutionName AS text)), 0)
          + COALESCE((SELECT MAX(similarity(ic.name, CAST(:course AS text)))
                      FROM institution_course_table ic
                      WHERE ic.institution_id = i.id AND ic.status = 'ACTIVE'), 0) DESC,
            i.institution_name ASC
        """,
            countQuery = """
        SELECT COUNT(*) FROM institution_table i
        WHERE i.customer_id = :customerId
          AND (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))
          AND (CAST(:institutionName AS text) IS NULL
               OR similarity(i.institution_name, CAST(:institutionName AS text)) > 0.3)
          AND (CAST(:institutionType AS text) IS NULL
               OR i.institution_type = CAST(:institutionType AS text))
          AND (CAST(:course AS text) IS NULL OR EXISTS (
                SELECT 1 FROM institution_course_table ic
                WHERE ic.institution_id = i.id
                  AND ic.status = 'ACTIVE'
                  AND similarity(ic.name, CAST(:course AS text)) > 0.3))
        """,
            nativeQuery = true)
    Page<Institution> findAllByOptionalFilters(
            @Param("customerId") UUID customerId,
            @Param("status") String status,
            @Param("institutionName") String institutionName,
            @Param("institutionType") String institutionType,
            @Param("course") String course,
            Pageable pageable);

    @Query("""
    SELECT DISTINCT i FROM StudentEnrollment e
    JOIN e.course c
    JOIN c.institution i
    WHERE e.student.id = :studentId AND e.status = :status
    ORDER BY i.institutionName
    """)
    List<Institution> findAllByStudentId(@Param("studentId") UUID studentId,
                                         @Param("status") GeneralStatus status);

    @Query(value = "SELECT * FROM institution_table WHERE id = :id AND customer_id = :customerId", nativeQuery = true)
    Optional<Institution> findByIdAndCustomerIdIgnoringTenant(@Param("id") UUID id, @Param("customerId") UUID customerId);

    @Query(value = """
        SELECT * FROM institution_table
        WHERE id = :id AND customer_id = :customerId AND status = 'ACTIVE'
        """, nativeQuery = true)
    Optional<Institution> findActiveByIdAndCustomerIdIgnoringTenant(@Param("id") UUID id, @Param("customerId") UUID customerId);

    @Query(value = "SELECT * FROM institution_table WHERE customer_id = :customerId AND status = 'ACTIVE' ORDER BY institution_name", nativeQuery = true)
    List<Institution> findActiveByCustomerIdIgnoringTenant(@Param("customerId") UUID customerId);

}
