package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.StudentTokensDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByEmail(String email);

    Optional<Student> findByTelephone(String telephone);

    Optional<Student> findByEmailOrTelephoneAndIdNot(String email, String telephone, UUID id);

    Optional<Student> findByEmailOrTelephone(String email, String telephone);

    Page<Student> findAllByStatus(GeneralStatus status, Pageable pageable);

    Set<String> findByCustomerId(UUID customerId);
}
