package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.DocumentType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    boolean existsByStudentId(UUID studentId);

    Optional<Document> findFirstByHealthConditionIdAndDocumentTypeAndDocumentStatusIn(UUID id, DocumentType documentType, List<DocumentStatus> activeStatuses);

    long countByDriverIdAndDocumentStatusIn(UUID id, List<DocumentStatus> activeStatuses);

    long countByStudentIdAndDocumentStatusIn(UUID id, List<DocumentStatus> activeStatuses);

    List<Document> findByStudentIdAndDocumentStatusInOrderByCreatedAtDesc(UUID id, List<String> visibleStatuses);

    List<Document> findByDriverIdAndDocumentStatusInOrderByCreatedAtDesc(UUID id, List<String> visibleStatuses);

    Optional<Document> findByIdAndDriverId(UUID documentId, UUID id);

    Optional<Document> findByIdAndStudentId(UUID documentId, UUID id);
}
