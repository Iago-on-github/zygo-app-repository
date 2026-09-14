package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.response.StudentResponsibleAdultDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ResponsibleAdultRepository extends JpaRepository<ResponsibleAdult, UUID> {
    Optional<ResponsibleAdult> findByUserAccountId(@Param("userAccountId") UUID userAccountId);

    @Query("SELECT ra FROM ResponsibleAdult ra WHERE ra.userAccount.email = :email")
    Optional<ResponsibleAdult> findByEmail(@Param("email") String email);

    Page<ResponsibleAdult> findByName(@Param("responsibleAdultName") String responsibleAdultName, Pageable pageable);

    Optional<ResponsibleAdult> findByCpf(@Param("responsibleAdultCpf") String responsibleAdultCpf);

    Optional<ResponsibleAdult> findByStudentsId(@Param("studentId") UUID studentId);

    @Query("""
        SELECT new com.travel_system.backend_app.model.dtos.response.StudentResponsibleAdultDTO(
                s.id,
                s.studentRelationshipType
                )
        FROM ResponsibleAdult ra
        JOIN ra.students s
        WHERE ra.id = :responsibleAdultId
        """)
    Set<StudentResponsibleAdultDTO> findStudentsById(@Param("responsibleAdultId") UUID responsibleAdultId);

    @Query("SELECT COUNT(ra) > 0 FROM ResponsibleAdult ra WHERE ra.userAccount.email = :email")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByCpf(@Param("cpf") String cpf);

    boolean existsByTelephone(@Param("telephone") String telephone);
}
