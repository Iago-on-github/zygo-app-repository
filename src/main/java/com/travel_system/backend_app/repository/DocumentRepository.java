package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.HealthCondition;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.enums.DocumentCategory;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.DocumentType;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findFirstByHealthConditionIdAndDocumentTypeAndDocumentStatusIn(UUID id, DocumentType documentType, List<DocumentStatus> activeStatuses);

    long countByDriverIdAndDocumentStatusIn(UUID id, List<DocumentStatus> activeStatuses);

    long countByStudentIdAndDocumentStatusIn(UUID id, List<DocumentStatus> activeStatuses);

    List<Document> findByStudentIdAndDocumentStatusInOrderByCreatedAtDesc(UUID id, List<DocumentStatus> visibleStatuses);

    List<Document> findByDriverIdAndDocumentStatusInOrderByCreatedAtDesc(UUID id, List<DocumentStatus> visibleStatuses);

    Optional<Document> findByIdAndDriverId(UUID documentId, UUID id);

    Optional<Document> findByIdAndStudentId(UUID documentId, UUID id);

    @Query("SELECT d FROM Document d WHERE d.documentStatus = :status ORDER BY d.createdAt ASC")
    Page<Document> findPendingDocsByUploadDateAsc(@Param("status") DocumentStatus status, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.studentId = :ownerId OR d.driverId = :ownerId")
    List<Document> findByOwner(@Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :documentId")
    Optional<Document> findByIdForUpdate(UUID documentId);

    List<Document> findByHealthConditionIdIn(List<UUID> healthConditionIds);

    /*
     * documentos ativos que vencem entre hoje e o limite informado:
     * - documentos sem validade (validUntil nulo) ficam de fora
     * - respeita o filtro de tenant quando ativo (em requisição, retorna apenas o Customer atual)
     * */
    @Query("""
    SELECT d FROM Document d
     WHERE d.documentStatus IN :statuses
       AND d.validUntil IS NOT NULL
       AND d.validUntil BETWEEN :today AND :limit
    """)
    List<Document> findExpiringForReminder(@Param("statuses") Collection<DocumentStatus> statuses,
                                           @Param("today") LocalDate today,
                                           @Param("limit") LocalDate limit);

    @Query("SELECT d FROM Document d WHERE d.documentStatus IN :statuses AND d.validUntil IS NOT NULL AND d.validUntil > :today")
    List<Document> findExpireForReminder(@Param("statuses") Collection<DocumentStatus> statuses, @Param("today") LocalDate today);

    @Query("SELECT d FROM Document d WHERE d.documentStatus = :status AND d.validUntil IS NOT NULL AND d.validUntil > :today")
    List<Document> findHealthValidDocuments(@Param("status") DocumentStatus status, @Param("today") LocalDate today);


    @Query("SELECT d FROM Document d WHERE d.studentId = :id OR d.driverId = :id")
    List<Document> findAllByOwnerId(UUID id);

    @Query("""
        SELECT d FROM Document d
        WHERE (d.studentId = :ownerId OR d.driverId = :ownerId)
          AND d.documentCategory = :category
        """)
    List<Document> findAllByOwnerIdAndCategory(@Param("ownerId") UUID ownerId, @Param("category") DocumentCategory category);}
