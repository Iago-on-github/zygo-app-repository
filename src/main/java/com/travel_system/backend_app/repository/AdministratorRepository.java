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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdministratorRepository extends JpaRepository<Administrator, UUID> {

    @Query("SELECT adm FROM Administrator adm WHERE adm.userAccount.email = :email")
    Optional<Administrator> findByEmail(@Param("email") String email);

    Page<Administrator> findByStatus(GeneralStatus generalStatus, Pageable pageable);

    Optional<Administrator> findByTelephone(String telephone);

    @Query("SELECT a FROM Administrator a WHERE a.customerId IS NOT NULL")
    Page<Administrator> findAllWithCustomerId(Pageable pageable);

    @Query("SELECT a FROM Administrator a WHERE a.status = :status AND a.customerId IS NOT NULL")
    Page<Administrator> findByStatusWithCustomerId(GeneralStatus status, Pageable pageable);

    boolean existsByTelephone(@Param("telephone") String telephone);

    boolean existsByCpf(@Param("cpf") String cpf);

    Optional<Administrator> findByUserAccountId(UUID userAccountId);
}
