package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Cnh;
import com.travel_system.backend_app.model.dtos.response.CnhResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DriverCnhExpirationDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.CnhCategory;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CnhRepository extends JpaRepository<Cnh, UUID> {

    @Query("""
       SELECT c FROM Cnh c
       WHERE c.customerId = :customerId
         AND (:cnhNumber IS NULL OR c.cnhNumber = :cnhNumber)
         AND (:filterByCategories = false OR EXISTS (
                SELECT 1 FROM Cnh c2 JOIN c2.cnhCategories category
                WHERE c2 = c AND category IN :cnhCategories))
         AND (:cnhExpirationDate IS NULL OR c.cnhExpirationDate = :cnhExpirationDate)
         AND (:cnhFirstIssueDate IS NULL OR c.cnhFirstIssueDate = :cnhFirstIssueDate)
       """)
    Page<Cnh> findAllByOptionalFilters(
            @Param("customerId") UUID customerId,
            @Param("cnhNumber") String cnhNumber,
            @Param("filterByCategories") boolean filterByCategories,
            @Param("cnhCategories") Set<CnhCategory> cnhCategories,
            @Param("cnhExpirationDate") LocalDate cnhExpirationDate,
            @Param("cnhFirstIssueDate") LocalDate cnhFirstIssueDate,
            Pageable pageable);

    Optional<Cnh> findByCnhNumber(@Param("cnhNumber") String cnhNumber);

    boolean existsByCnhNumber(@Param("cnhNumber") String cnhNumber);
}
