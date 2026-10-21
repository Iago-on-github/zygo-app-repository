package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.events.documents.DocumentUploadedEvent;
import com.travel_system.backend_app.exceptions.DocumentConflictException;
import com.travel_system.backend_app.exceptions.DocumentValidationException;
import com.travel_system.backend_app.exceptions.EntityLimitExceededException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.PrivateStorageService;
import com.travel_system.backend_app.interfaces.mappers.response.DocumentResponseMapper;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.HealthCondition;
import com.travel_system.backend_app.model.HealthDeclaration;
import com.travel_system.backend_app.model.dtos.request.DocumentUploadRequest;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.dtos.security.ProcessedDocument;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.HealthConditionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@Service
public class DocumentPersistenceService {
    private final Logger log = LoggerFactory.getLogger(DocumentPersistenceService.class);

    // status considerados "ativos" (contam para unicidade e para o limite do plano)
    private static final List<DocumentStatus> ACTIVE_STATUSES = List.of(DocumentStatus.PENDING_REVIEW, DocumentStatus.APPROVED);

    // tipos de documento que exigem data de validade
    private static final Set<DocumentType> TYPES_REQUIRING_VALIDITY = EnumSet.of(DocumentType.PRESCRIPTION, DocumentType.MEDICAL_REPORT);

    private final DocumentRepository documentRepository;
    private final HealthConditionRepository healthConditionRepository;
    private final CustomerRepository customerRepository;
    private final DocumentProcessingService documentProcessingService;
    private final PrivateStorageService privateStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    private final DocumentResponseMapper documentResponseMapper;

    public DocumentPersistenceService(DocumentRepository documentRepository, HealthConditionRepository healthConditionRepository, CustomerRepository customerRepository, DocumentProcessingService documentProcessingService, PrivateStorageService privateStorageService, ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager, DocumentResponseMapper documentResponseMapper) {
        this.documentRepository = documentRepository;
        this.healthConditionRepository = healthConditionRepository;
        this.customerRepository = customerRepository;
        this.documentProcessingService = documentProcessingService;
        this.privateStorageService = privateStorageService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate =  new TransactionTemplate(transactionManager);
        this.documentResponseMapper = documentResponseMapper;
    }

    /*
     *  responsável pelo upload completo de um documento:
     * - o dono (HealthOwner) já chega resolvido e validado
     * - validações e processamento (antivírus) acontecem FORA de transação
     * - somente a gravação no banco é transacional (TransactionTemplate)
     * - se o banco falhar, o arquivo já enviado ao storage é removido (compensação)
     * */
    public DocumentResponseDTO upload(HealthOwner owner, UUID actorAccountId, DocumentUploadRequest request, MultipartFile file) {
        UUID customerId = TenantContext.getCurrentTenant();

        // valida condição, compatibilidade do tipo, validade e limite do plano
        HealthCondition condition = validateUpload(owner, request, customerId);

        // valida tamanho, tipo real e antivírus (lança exception se inválido)
        ProcessedDocument processed = documentProcessingService.process(file);

        // gera chave única e envia ao storage privado
        String storageKey = generateKey(customerId, owner);
        privateStorageService.upload(processed.content(), storageKey, processed.contentType());

        try {
            Document saved = transactionTemplate.execute(status ->
                    persist(owner, actorAccountId, request, condition, processed, storageKey, file.getOriginalFilename(), customerId));

            return documentResponseMapper.toDTO(saved);

        } catch (DataIntegrityViolationException e) {
            log.error("Violação de integridade no upload: {}", e.getMostSpecificCause().getMessage());
            // upload simultâneo para a mesma condição e tipo (índice único)
            removeUploadedFile(storageKey);
            throw new DocumentConflictException("Já existe um documento ativo desse tipo para essa condição", e);

        } catch (RuntimeException e) {
            removeUploadedFile(storageKey);
            throw e;
        }
    }

    /*
     *  parte transacional do upload:
     * - marca o documento ativo anterior (mesma condição e tipo) como REPLACED
     * - salva o novo documento como PENDING_REVIEW
     * - publica os eventos (executados somente após o commit)
     * */
    private Document persist(HealthOwner owner, UUID actorAccountId, DocumentUploadRequest request, HealthCondition condition, ProcessedDocument processed, String storageKey, String originalFilename, UUID customerId) {

        List<String> keysToDelete = new ArrayList<>();

        documentRepository.findFirstByHealthConditionIdAndDocumentTypeAndDocumentStatusIn(
                        condition.getId(), request.documentType(), ACTIVE_STATUSES)
                .ifPresent(previous -> {
                    previous.setDocumentStatus(DocumentStatus.REPLACED);

                    // flush obrigatório do Hibernate p/ executar INSERTs antes de UPDATEs,
                    // e o novo documento violaria o índice único se o anterior ainda estivesse ativo
                    documentRepository.saveAndFlush(previous);

                    keysToDelete.add(previous.getStorageKey());
                });

        Document document = new Document();
        document.assignCustomer(customerId);

        if (owner.type() == OwnerType.STUDENT) {
            document.setStudentId(owner.id());
        } else {
            document.setDriverId(owner.id());
        }

        document.setHealthConditionId(condition.getId());
        document.setDocumentType(request.documentType());
        document.setDocumentCategory(document.getDocumentType().getCategory());
        document.setDocumentStatus(DocumentStatus.PENDING_REVIEW);
        document.setStorageKey(storageKey);
        document.setOriginalFilename(sanitizeFilename(originalFilename));
        document.setContentType(processed.contentType());
        document.setSizeBytes(processed.sizeBytes());
        document.setSha256(processed.sha256());
        document.setValidUntil(request.validUntil());
        document.setUploadedBy(actorAccountId);

        // a violação do índice único aparece aqui, dentro do try do upload
        Document saved = documentRepository.saveAndFlush(document);

        // notifica o admin de que há um documento novo para revisão
        eventPublisher.publishEvent(new DocumentUploadedEvent(saved.getId(), customerId));

        // arquivos a serem removidos do storage APÓS o commit (ex.: documento substituído)
        if (!keysToDelete.isEmpty()) {
            eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(keysToDelete));
        }

        return saved;
    }

    /*
     *  validações de negócio do upload (sem transação):
     * - a condição pertence à declaração vigente do dono
     * - a condição exige comprovação e aceita esse tipo de documento
     * - validade obrigatória para tipos que vencem, e sempre no futuro
     * - limite de documentos ativos do plano
     * */
    private HealthCondition validateUpload(HealthOwner owner, DocumentUploadRequest request, UUID customerId) {
        HealthCondition condition = healthConditionRepository.findByIdInDeclarationWithStatus(request.healthConditionId(), HealthDeclarationStatus.CURRENT)
                .filter(c -> belongsTo(c.getDeclaration(), owner))
                .orElseThrow(() -> new EntityNotFoundException("Condição não encontrada"));

        if (!condition.getType().isRequiresMedicalReport()) {
            throw new DocumentValidationException("Essa condição não exige documento comprobatório");
        }

        LocalDate validUntil = request.validUntil();
        if (TYPES_REQUIRING_VALIDITY.contains(request.documentType()) && validUntil == null) {
            throw new DocumentValidationException("A data de validade é obrigatória para esse tipo de documento");
        }
        if (validUntil != null && !validUntil.isAfter(LocalDate.now())) {
            throw new DocumentValidationException("A data de validade deve ser futura");
        }

        validatePlanLimit(owner, customerId);

        return condition;
    }

    // verifica o limite de documentos ativos por dono conforme o plano do Customer
    private void validatePlanLimit(HealthOwner owner, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer não encontrado"));

        long activeDocuments = owner.type() == OwnerType.STUDENT
                ? documentRepository.countByStudentIdAndDocumentStatusIn(owner.id(), ACTIVE_STATUSES)
                : documentRepository.countByDriverIdAndDocumentStatusIn(owner.id(), ACTIVE_STATUSES);

        if (activeDocuments >= customer.getPlan().getMaxHealthDocumentsPerOwner()) {
            throw new EntityLimitExceededException("Limite de documentos do plano atingido");
        }
    }

    // confere se a declaração pertence ao dono informado
    private boolean belongsTo(HealthDeclaration declaration, HealthOwner owner) {
        return owner.type() == OwnerType.STUDENT
                ? owner.id().equals(declaration.getStudentId())
                : owner.id().equals(declaration.getDriverId());
    }

    // chave única por arquivo: nunca sobrescreve um documento existente
    private String generateKey(UUID customerId, HealthOwner owner) {
        String ownerFolder = owner.type() == OwnerType.STUDENT ? "students" : "drivers";

        return "customers/" + customerId
                + "/documents/" + ownerFolder
                + "/" + owner.id()
                + "/" + UUID.randomUUID() + ".pdf";
    }

    // mantém apenas o nome do arquivo (sem caminho) e limita o tamanho
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "documento.pdf";
        }

        String name = Paths.get(originalFilename).getFileName().toString();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    // compensação: remove o arquivo do storage quando a gravação no banco falha
    private void removeUploadedFile(String storageKey) {
        try {
            privateStorageService.delete(storageKey);
        } catch (RuntimeException e) {
            // não mascara o erro original; o arquivo órfão fica registrado para limpeza
            log.error("[upload] Falha ao remover arquivo órfão do storage. Key: {}", storageKey, e);
        }
    }

}
