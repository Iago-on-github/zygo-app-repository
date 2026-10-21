package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.HealthDeclaration;
import com.travel_system.backend_app.model.enums.HealthDeclarationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthDeclarationRepository extends JpaRepository<HealthDeclaration, UUID> {

/*
    // declaração mais recente do aluno (CURRENT ou OUTDATED), com lock para evitar confirmação dupla
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HealthDeclaration> findFirstByStudentIdAndHealthDeclarationStatusInOrderByDeclaredAtDesc(UUID studentId, Collection<HealthDeclarationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HealthDeclaration> findFirstByDriverIdAndHealthDeclarationStatusInOrderByDeclaredAtDesc(UUID driverId, Collection<HealthDeclarationStatus> statuses);
*/
    @Query("SELECT COUNT(d) > 0 FROM HealthDeclaration d WHERE d.studentId = :ownerId OR d.driverId = :ownerId")
    boolean existsByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT d FROM HealthDeclaration d WHERE d.studentId = :ownerId OR d.driverId = :ownerId")
    Optional<HealthDeclaration> findByOwnerId(@Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM HealthDeclaration d WHERE d.studentId = :ownerId OR d.driverId = :ownerId")
    Optional<HealthDeclaration> findByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

    /*
     * Declarações vigentes que não pertencem ao período atual.
     * LEFT JOIN: declarações feitas sem período (term nulo) também precisam ser revalidadas
     * quando o primeiro período do Customer começa.
     */
    @Query("""
        SELECT d FROM HealthDeclaration d
        LEFT JOIN d.customerTerm t
        WHERE d.customerId = :customerId
          AND d.healthDeclarationStatus = :status
          AND (t IS NULL OR t.id <> :currentTermId)
        """)
    List<HealthDeclaration> findOutsideCurrentTerm(@Param("customerId") UUID customerId, @Param("status") HealthDeclarationStatus status, @Param("currentTermId") UUID currentTermId);

    /*
     * Declarações dos alunos da viagem que têm condições, já com as condições carregadas.
     * JOIN FETCH (inner) descarta automaticamente quem não tem condição.
     * Sem filtro de status: OUTDATED também entra (informação antiga é melhor que nenhuma).
     */
    @Query("""
        SELECT DISTINCT d FROM HealthDeclaration d
        JOIN FETCH d.conditions
        WHERE d.studentId IN :studentIds
          AND d.hasCondition = true
        """)
    List<HealthDeclaration> findWithConditionsByStudentIdIn(@Param("studentIds") Collection<UUID> studentIds);

}
