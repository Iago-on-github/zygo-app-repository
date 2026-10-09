package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.*;

@Repository
public interface AdministratorRepository extends JpaRepository<Administrator, UUID> {

    @Query("SELECT adm FROM Administrator adm WHERE adm.userAccount.email = :email")
    Optional<Administrator> findByEmail(@Param("email") String email);

    Page<Administrator> findByStatus(GeneralStatus generalStatus, Pageable pageable);

    @Query("SELECT a FROM Administrator a WHERE a.status = :status AND a.customerId IS NOT NULL")
    Page<Administrator> findByStatusWithCustomerId(GeneralStatus status, Pageable pageable);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM administrator_table WHERE cpf = :cpf)", nativeQuery = true)
    boolean existsByCpfIgnoringTenant(@Param("cpf") String cpf);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM administrator_table WHERE telephone = :telephone)", nativeQuery = true)
    boolean existsByTelephoneIgnoringTenant(@Param("telephone") String telephone);

    @Query(value = "SELECT * FROM administrator_table WHERE user_account_id IN (:userAccountIds)", nativeQuery = true)
    List<Administrator> findAllByUserAccountIdInIgnoringTenant(@Param("userAccountIds") Collection<UUID> userAccountIds);

    @Query("SELECT COUNT(*) FROM Administrator adm WHERE adm.customerId = :customerId")
    int countAdministratorsInThisCustomer(UUID customerId);

    Optional<Administrator> findByUserAccountId(UUID userAccountId);

    List<Administrator> findAllByUserAccountIdIn(Collection<UUID> userAccountIds);

    @Query("SELECT COUNT (*) FROM Administrator adm WHERE adm.status = :status")
    int countActiveAdministrators(@Param("status") GeneralStatus status);

    @Query(value = """
        SELECT adm.* FROM administrator_table adm
        JOIN user_account_table ua ON ua.id = adm.user_account_id
        LEFT JOIN address_table a ON a.id = adm.address_id
        WHERE adm.customer_id = :customerId
          AND (CAST(:email AS text) IS NULL OR ua.email = CAST(:email AS text))
          AND (CAST(:name AS text) IS NULL OR similarity(adm.name, CAST(:name AS text)) > 0.3)
          AND (CAST(:lastName AS text) IS NULL OR similarity(adm.last_name, CAST(:lastName AS text)) > 0.3)
          AND (CAST(:neighborhood AS text) IS NULL OR similarity(a.neighborhood, CAST(:neighborhood AS text)) > 0.3)
          AND (CAST(:jobTitle AS text) IS NULL OR similarity(adm.job_title, CAST(:jobTitle AS text)) > 0.3)
        ORDER BY
            COALESCE(similarity(adm.name, CAST(:name AS text)), 0)
          + COALESCE(similarity(adm.last_name, CAST(:lastName AS text)), 0)
          + COALESCE(similarity(a.neighborhood, CAST(:neighborhood AS text)), 0)
          + COALESCE(similarity(adm.job_title, CAST(:jobTitle AS text)), 0) DESC,
            adm.name ASC
        """,
            countQuery = """
        SELECT COUNT(*) FROM administrator_table adm
        JOIN user_account_table ua ON ua.id = adm.user_account_id
        LEFT JOIN address_table a ON a.id = adm.address_id
        WHERE adm.customer_id = :customerId
          AND (CAST(:email AS text) IS NULL OR ua.email = CAST(:email AS text))
          AND (CAST(:name AS text) IS NULL OR similarity(adm.name, CAST(:name AS text)) > 0.3)
          AND (CAST(:lastName AS text) IS NULL OR similarity(adm.last_name, CAST(:lastName AS text)) > 0.3)
          AND (CAST(:neighborhood AS text) IS NULL OR similarity(a.neighborhood, CAST(:neighborhood AS text)) > 0.3)
          AND (CAST(:jobTitle AS text) IS NULL OR similarity(adm.job_title, CAST(:jobTitle AS text)) > 0.3)
        """,
            nativeQuery = true)
    Page<Administrator> findAllByOptionalFilters(
            @Param("customerId") UUID customerId,
            @Param("email") String email,
            @Param("name") String name,
            @Param("lastName") String lastName,
            @Param("neighborhood") String neighborhood,
            @Param("jobTitle") String jobTitle,
            Pageable pageable);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM administrator_table WHERE user_account_id = :userAccountId)", nativeQuery = true)
    boolean existsByUserAccountIdIgnoringTenant(@Param("userAccountId") UUID userAccountId);
}
