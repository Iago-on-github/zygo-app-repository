package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findAllByActive(Boolean active);

    Optional<Customer> findByCnpj(@Param("cnpj") String cnpj);

    Optional<Customer> findBySlug(String slug);

    // recuper o Id da City pelo ID do customer
    Optional<UUID> findCityIdById(UUID customerId);
}
