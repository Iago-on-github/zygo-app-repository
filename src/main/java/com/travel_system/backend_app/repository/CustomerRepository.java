package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByCnpj(@Param("cnpj") String cnpj);

    Optional<Customer> findBySlug(String slug);

    @Query("SELECT c.city.id FROM Customer c WHERE c.id = :customerId")
    Optional<UUID> findCityIdByCustomerId(@Param("customerId") UUID customerId);

    boolean existsByCnpj(@Param("cnpj") String cnpj);

    List<Customer> findAllByStatus(@Param("status") GeneralStatus status);

    @Query("SELECT c FROM Customer c WHERE (:name IS NULL OR c.name = :name)" +
            "AND (:cnpj IS NULL OR c.cnpj = :cnpj)" +
            "AND (:contactEmail IS NULL OR c.contactEmail = :contactEmail)" +
            "AND (:contactTelephone IS NULL OR c.contactTelephone = :contactTelephone)" +
            "AND (:status IS NULL OR c.status = :status)" +
            "AND (:clientSector IS NULL OR c.clientSector = :clientSector)" +
            "AND (:plan IS NULL OR c.plan = :plan)")
    Page<Customer> findAllByOptionalFilters(
            @Param("name") String name,
            @Param("cnpj") String cnpj,
            @Param("contactEmail") String contactEmail,
            @Param("contactTelephone") String contactTelephone,
            @Param("status") GeneralStatus status,
            @Param("clientSector") ClientSector clientSector,
            @Param("plan") CustomerPlan plan,
            Pageable pageable);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM customer_table WHERE cnpj = :cnpj)", nativeQuery = true)
    boolean existsByCnpjIgnoringTenant(@Param("cnpj") String cnpj);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM customer_table WHERE contact_email = :email)", nativeQuery = true)
    boolean existsByContactEmailIgnoringTenant(@Param("email") String email);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM customer_table WHERE contact_telephone = :telephone)", nativeQuery = true)
    boolean existsByContactTelephoneIgnoringTenant(@Param("telephone") String telephone);
}
