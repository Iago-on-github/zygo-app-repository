package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Administrator;
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

    @Query(value = "SELECT EXISTS (SELECT 1 FROM user_account_table WHERE email = :email)", nativeQuery = true)
    boolean existsByEmailIgnoringTenant(@Param("email") String email);

    @Query(value = "SELECT * FROM administrator_table WHERE user_account_id IN (:userAccountIds)", nativeQuery = true)
    List<Administrator> findAllByUserAccountIdInIgnoringTenant(@Param("userAccountIds") Collection<UUID> userAccountIds);

    @Query("SELECT COUNT(s) > 0 FROM Administrator adm WHERE adm.customerId = :customerId")
    int countAdministratorsInThisCustomer(UUID customerId);

    Optional<Administrator> findByUserAccountId(UUID userAccountId);

    List<Administrator> findAllByUserAccountIdIn(Collection<UUID> userAccountIds);

}
