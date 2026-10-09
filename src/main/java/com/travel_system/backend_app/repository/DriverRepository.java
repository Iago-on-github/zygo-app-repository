package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.dtos.response.DriverCnhExpirationDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.CnhCategory;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    @Query("SELECT d FROM Driver d WHERE d.userAccount.email = :email")
    Optional<Driver> findByEmail(String email);

    Page<Driver> findAllByStatus(GeneralStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE Driver d SET d.totalTrips = :newValueOfTotalTrips")
    void updateTotalTrips(@Param("newValueOfTotalTrips") int newValueOfTotalTrips);

    Optional<Driver> findByUserAccountId(@Param("userAccountId") UUID userAccountId);

    boolean existsByTelephone(@Param("telephone") String telephone);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM driver_table WHERE telephone = :telephone)", nativeQuery = true)
    boolean existsByTelephoneIgnoringTenant(@Param("telephone") String telephone);

    @Query("SELECT COUNT(*) FROM Driver d WHERE d.customerId = :customerId")
    long countDriverInThisCustomer(UUID customerId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM driver_table WHERE cpf = :cpf)", nativeQuery = true)
    boolean existsByCpfIgnoringTenant(String cpf);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM driver_table WHERE cnh.cnhNumber = :cnhNumber)", nativeQuery = true)
    boolean existsByCnhNumberIgnoringTenant(String cnhNumber);

    @Query(value = """
        SELECT d.* FROM driver_table d
        JOIN user_account_table ua ON ua.id = d.user_account_id
        LEFT JOIN address_table a ON a.id = d.address_id
        WHERE d.customer_id = :customerId
          AND (CAST(:email AS text) IS NULL OR ua.email = CAST(:email AS text))
          AND (CAST(:name AS text) IS NULL OR similarity(d.name, CAST(:name AS text)) > 0.3)
          AND (CAST(:lastName AS text) IS NULL OR similarity(d.last_name, CAST(:lastName AS text)) > 0.3)
          AND (CAST(:neighborhood AS text) IS NULL OR similarity(a.neighborhood, CAST(:neighborhood AS text)) > 0.3)
          AND (CAST(:areaOfActivity AS text) IS NULL OR similarity(d.area_of_activity, CAST(:areaOfActivity AS text)) > 0.3)
          AND (CAST(:shifts AS text[]) IS NULL OR EXISTS (
                SELECT 1 FROM driver_shifts ds
                WHERE ds.driver_id = d.id AND ds.driver_shifts = ANY(CAST(:shifts AS text[]))))
        ORDER BY
            COALESCE(similarity(d.name, CAST(:name AS text)), 0)
          + COALESCE(similarity(d.last_name, CAST(:lastName AS text)), 0)
          + COALESCE(similarity(a.neighborhood, CAST(:neighborhood AS text)), 0)
          + COALESCE(similarity(d.area_of_activity, CAST(:areaOfActivity AS text)), 0) DESC,
            d.name ASC
        """,
            countQuery = """
        SELECT COUNT(*) FROM driver_table d
        JOIN user_account_table ua ON ua.id = d.user_account_id
        LEFT JOIN address_table a ON a.id = d.address_id
        WHERE d.customer_id = :customerId
          AND (CAST(:email AS text) IS NULL OR ua.email = CAST(:email AS text))
          AND (CAST(:name AS text) IS NULL OR similarity(d.name, CAST(:name AS text)) > 0.3)
          AND (CAST(:lastName AS text) IS NULL OR similarity(d.last_name, CAST(:lastName AS text)) > 0.3)
          AND (CAST(:neighborhood AS text) IS NULL OR similarity(a.neighborhood, CAST(:neighborhood AS text)) > 0.3)
          AND (CAST(:areaOfActivity AS text) IS NULL OR similarity(d.area_of_activity, CAST(:areaOfActivity AS text)) > 0.3)
          AND (CAST(:shifts AS text[]) IS NULL OR EXISTS (
                SELECT 1 FROM driver_shifts ds
                WHERE ds.driver_id = d.id AND ds.driver_shifts = ANY(CAST(:shifts AS text[]))))
        """,
            nativeQuery = true)
    Page<Driver> findAllByOptionalParameters(
            @Param("customerId") UUID customerId,
            @Param("email") String email,
            @Param("name") String name,
            @Param("lastName") String lastName,
            @Param("neighborhood") String neighborhood,
            @Param("areaOfActivity") String areaOfActivity,
            @Param("shifts") String[] shifts,
            Pageable pageable);

    Optional<Driver> findByCpf(@Param("cpf") String cpf);

    @Query("SELECT d FROM Driver d WHERE d.cnh.cnhNumber = :cnhNumber")
    Optional<Driver> findByCnhNumber(@Param("cnhNumber") String cnhNumber);

    @Query("""
    SELECT new com.travel_system.backend_app.model.dtos.response.DriverCnhExpirationDTO(
        d.id, d.name, d.lastName, c.id, c.cnhExpirationDate)
    FROM Driver d JOIN d.cnh c
      WHERE c.cnhExpirationDate <= :thresholdDate
      AND EXISTS (
            SELECT 1 FROM Cnh c2 JOIN c2.cnhCategories category
            WHERE c2 = c AND category IN :cnhCategories)
    ORDER BY c.cnhExpirationDate ASC
    """)
    List<DriverCnhExpirationDTO> findDriversWithCnhExpiringUntil(
            @Param("cnhCategories") Set<CnhCategory> cnhCategories,
            @Param("thresholdDate") LocalDate thresholdDate);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM driver_table WHERE user_account_id = :userAccountId)", nativeQuery = true)
    boolean existsByUserAccountIdIgnoringTenant(@Param("userAccountId") UUID userAccountId);
}
