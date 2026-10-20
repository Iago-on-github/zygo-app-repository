package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.DocumentStorageProperties;
import com.travel_system.backend_app.events.documents.DocumentApprovedEvent;
import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.events.documents.DocumentRejectedEvent;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.PrivateStorageService;
import com.travel_system.backend_app.interfaces.mappers.response.DocumentResponseMapper;
import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.DocumentRejectDTO;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.enums.Action;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.ResourceType;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.TransactionScoped;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

@Service
public class DocumentReviewService {

    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;

    private final UserProfileResolverService userProfileResolverService;
    private final PrivateStorageService privateStorageService;
    private final SensitiveDataAccessLogService sensitiveDataAccessLogService;

    private final DocumentStorageProperties documentStorageProperties;

    private final ApplicationEventPublisher eventPublisher;

    private final DocumentResponseMapper documentResponseMapper;

    public DocumentReviewService(DocumentRepository documentRepository, StudentRepository studentRepository, DriverRepository driverRepository, UserProfileResolverService userProfileResolverService, PrivateStorageService privateStorageService, SensitiveDataAccessLogService sensitiveDataAccessLogService, DocumentStorageProperties documentStorageProperties, ApplicationEventPublisher eventPublisher, DocumentResponseMapper documentResponseMapper) {
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.userProfileResolverService = userProfileResolverService;
        this.privateStorageService = privateStorageService;
        this.sensitiveDataAccessLogService = sensitiveDataAccessLogService;
        this.documentStorageProperties = documentStorageProperties;
        this.eventPublisher = eventPublisher;
        this.documentResponseMapper = documentResponseMapper;
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponseDTO> listPendingDocuments(Pageable pageable) {
        return documentRepository.findPendingDocsByUploadDateAsc(DocumentStatus.PENDING_REVIEW, pageable).map(documentResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> findDocumentsByOwner(UUID ownerId) {
        return documentRepository.findByOwner(ownerId).stream().map(documentResponseMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDownloadResponseDTO downloadDocument(UUID documentId) {
        UUID admId = userProfileResolverService.resolveAdminForDocuments(documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado"));

        if (document.getDocumentStatus() == DocumentStatus.REJECTED || document.getDocumentStatus() == DocumentStatus.REPLACED) {
            throw new DocumentConflictException("Documentos reijeitados ou substituídos não podem ser baixados");
        }

        // gera url
        String url = privateStorageService.generateDownloadUrl(document.getStorageKey(), document.getOriginalFilename());

        HealthOwner owner;
        if (document.getStudentId() != null) {
            owner = new HealthOwner(OwnerType.STUDENT, document.getStudentId());
        } else {
            owner = new HealthOwner(OwnerType.DRIVER, document.getDriverId());
        }

        // registra acesso de dado sensível
        sensitiveDataAccessLogService.record(admId, owner, ResourceType.DOCUMENT, documentId, Action.DOWNLOAD, null);

        return new DocumentDownloadResponseDTO(url, Instant.now().plus(documentStorageProperties.getDownloadUrlTtl()));
    }

    @Transactional
    public void approveDocument(UUID documentId) {
        UUID adminId = userProfileResolverService.resolveAdminForDocuments(documentId);

        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado"));

        // exception se documento não pendente
        if (!(document.getDocumentStatus() == DocumentStatus.PENDING_REVIEW)) {
            throw new DocumentConflictException("O documento não está pendente");
        }

        // se documento já passou a validade
        LocalDate validUntil = document.getValidUntil();
        if (validUntil != null && !validUntil.isAfter(LocalDate.now())) {
            throw new DocumentConflictException("A validade do documento está expirada");
        }

        // realiza o update
        Instant reviewedAt = Instant.now();

        document.setDocumentStatus(DocumentStatus.APPROVED);
        document.setReviewedBy(adminId);
        document.setReviewedAt(reviewedAt);

        documentRepository.save(document);

        // publica evento para notificações
        UUID userAccountId = getUserAccountId(document.getStudentId(), document.getDriverId());

        DocumentApprovedEvent documentApprovedEvent = new DocumentApprovedEvent(document.getId(), document.getCustomerId(), userAccountId, adminId, reviewedAt, document.getValidUntil());

        eventPublisher.publishEvent(documentApprovedEvent);
    }

    @Transactional
    public void rejectDocument(UUID documentId, DocumentRejectDTO dto) {
        UUID adminId = userProfileResolverService.resolveAdminForDocuments(documentId);

        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado"));

        // exception se documento não pendente
        if (!(document.getDocumentStatus() == DocumentStatus.PENDING_REVIEW)) {
            throw new DocumentConflictException("O documento não está pendente");
        }

        // aqui é permtido rejeitar um documento cujo prazo de validade já expirou

        // realiza o update
        Instant reviewedAt = Instant.now();

        document.setDocumentStatus(DocumentStatus.REJECTED);
        document.setRejectionReason(dto.rejectReason());
        document.setReviewedBy(adminId);
        document.setReviewedAt(reviewedAt);

        documentRepository.save(document);

        // publica evento para notificações
        UUID userAccountId = getUserAccountId(document.getStudentId(), document.getDriverId());

        DocumentRejectedEvent documentRejectedEvent = new DocumentRejectedEvent(documentId, document.getCustomerId(), userAccountId, adminId, reviewedAt);

        eventPublisher.publishEvent(documentRejectedEvent);

        /*
        * publica evento p/  documentos rejeitados q serão apagados via job com retry
        * */
        DocumentFilesDeletionRequestedEvent documentFilesDeletionRequestedEvent = new DocumentFilesDeletionRequestedEvent(List.of(document.getStorageKey()));

        eventPublisher.publishEvent(documentFilesDeletionRequestedEvent);
    }

    private UUID getUserAccountId(UUID studentId, UUID driverId) {
        if (studentId != null) {
            Student student = studentRepository.findById(studentId).orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado"));

            return student.getUserAccount().getId();
        }

        Driver driver = driverRepository.findById(driverId).orElseThrow(() -> new EntityNotFoundException("Driver não encontrado"));

        return driver.getUserAccount().getId();
    }
}

/*
* GUIDE
* usado pelo administrador para gerenciar a parte de documentos
* */
