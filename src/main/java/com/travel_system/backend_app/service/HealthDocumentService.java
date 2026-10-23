package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.DocumentStorageProperties;
import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.events.documents.HealthDocumentUploadedEvent;
import com.travel_system.backend_app.exceptions.DocumentConflictException;
import com.travel_system.backend_app.exceptions.DocumentNotFoundException;
import com.travel_system.backend_app.exceptions.DocumentValidationException;
import com.travel_system.backend_app.exceptions.EntityLimitExceededException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.PrivateStorageService;
import com.travel_system.backend_app.interfaces.mappers.response.DocumentResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.DocumentUploadRequest;
import com.travel_system.backend_app.model.dtos.request.HealthAccessContext;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.dtos.security.ProcessedDocument;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class HealthDocumentService {
    private final Logger log = LoggerFactory.getLogger(HealthDocumentService.class);

    private final DocumentStorageProperties documentStorageProperties;

    private final UserAccountRepository userAccountRepository;
    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final HealthConditionRepository healthConditionRepository;

    private final PrivateStorageService privateStorageService;
    private final UserProfileResolverService userProfileResolverService;
    private final DocumentPersistenceService documentPersistenceService;
    private final DocumentProcessingService documentProcessingService;
    private final SensitiveDataAccessLogService sensitiveDataAccessLogService;

    private final DocumentResponseMapper documentResponseMapper;

    private final ApplicationEventPublisher eventPublisher;

    private static final List<DocumentStatus> DEFAULT_LIST_STATUSES = List.of(DocumentStatus.PENDING_REVIEW, DocumentStatus.APPROVED, DocumentStatus.REJECTED);

    // status considerados "ativos" (contam para unicidade e para o limite do plano)
    private static final List<DocumentStatus> ACTIVE_STATUSES = List.of(DocumentStatus.PENDING_REVIEW, DocumentStatus.APPROVED);

    // tipos de documento que exigem data de validade
    private static final Set<DocumentType> TYPES_REQUIRING_VALIDITY = EnumSet.of(DocumentType.PRESCRIPTION, DocumentType.MEDICAL_REPORT);

    public HealthDocumentService(DocumentStorageProperties documentStorageProperties, UserAccountRepository userAccountRepository, DocumentRepository documentRepository, ResponsibleAdultRepository responsibleAdultRepository, CustomerRepository customerRepository, HealthConditionRepository healthConditionRepository, PrivateStorageService privateStorageService, UserProfileResolverService userProfileResolverService, DocumentPersistenceService documentPersistenceService, DocumentProcessingService documentProcessingService, SensitiveDataAccessLogService sensitiveDataAccessLogService, DocumentResponseMapper documentResponseMapper, ApplicationEventPublisher eventPublisher) {
        this.documentStorageProperties = documentStorageProperties;
        this.userAccountRepository = userAccountRepository;
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
        this.healthConditionRepository = healthConditionRepository;
        this.privateStorageService = privateStorageService;
        this.userProfileResolverService = userProfileResolverService;
        this.documentPersistenceService = documentPersistenceService;
        this.documentProcessingService = documentProcessingService;
        this.sensitiveDataAccessLogService = sensitiveDataAccessLogService;
        this.documentResponseMapper = documentResponseMapper;
        this.eventPublisher = eventPublisher;
    }

    // aluno maior ou motorista: os próprios documentos
    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> myDocuments(List<DocumentStatus> statuses) {
        return listByOwner(userProfileResolverService.resolveSelf(), statuses);
    }

    // responsável: documentos de um aluno menor vinculado
    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> studentDocuments(UUID studentId, List<DocumentStatus> statuses) {
        return listByOwner(userProfileResolverService.resolveForResponsible(studentId), statuses);
    }

    // fazer upload de documentos de forma autônoma (aluno maior de idade ou motorista)
    public DocumentResponseDTO uploadMine(DocumentUploadRequest request, MultipartFile file) {
        HealthOwner healthOwner = userProfileResolverService.resolveSelf();

        return uploadHealthDocument(healthOwner, getAuthenticatedUserAccountId(), request, file);
    }

    // responsável por um menor de idade realizando o upload por ele
    public DocumentResponseDTO uploadForStudent(UUID studentId, DocumentUploadRequest request, MultipartFile file) {
        HealthOwner healthOwner = userProfileResolverService.resolveForResponsible(studentId);

        return uploadHealthDocument(healthOwner, getAuthenticatedUserAccountId(), request, file);
    }

    // aluno maior de idade ou motorista
    @Transactional(readOnly = true)
    public DocumentDownloadResponseDTO downloadMine(UUID documentId) {
        return generateDownload(new HealthAccessContext(getAuthenticatedUserAccountId(), userProfileResolverService.resolveSelf()), documentId);
    }

    @Transactional(readOnly = true)
    public DocumentDownloadResponseDTO downloadForStudent(UUID studentId, UUID documentId) {
        return generateDownload(new HealthAccessContext(getAuthenticatedUserAccountId(), userProfileResolverService.resolveForResponsible(studentId)), documentId);
    }

    private DocumentDownloadResponseDTO generateDownload(HealthAccessContext context, UUID documentId) {

        Document document = (context.dtoOwner().type() == OwnerType.STUDENT
                ? documentRepository.findByIdAndStudentId(documentId, context.dtoOwner().id())
                : documentRepository.findByIdAndDriverId(documentId, context.dtoOwner().id()))
                .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado"));

        if (document.getDocumentStatus() == DocumentStatus.REPLACED || document.getDocumentStatus() == DocumentStatus.REJECTED) {
            throw new DocumentNotFoundException("Documento não encontrado");
        }

        String url = privateStorageService.generateDownloadUrl(document.getStorageKey(), document.getOriginalFilename());

        // registra acesso de dado sensível
        sensitiveDataAccessLogService.record(context.actorAccountId(), context.dtoOwner(), ResourceType.DOCUMENT, documentId, Action.DOWNLOAD, null);

        return new DocumentDownloadResponseDTO(url, Instant.now().plus(documentStorageProperties.getDownloadUrlTtl()));
    }

    private UUID getAuthenticatedUserAccountId() {
        String email = getAuthenticatedUserEmail();

        UserAccount user = userAccountRepository.findUserByEmail(email);

        if (user == null) {
            throw new AccessDeniedException("Acesso negado, user não existe.");
        }

        return user.getId();
    }

    /*
     * lista os documentos de saúde de um dono
     * - sem filtro: ativos (PENDING_REVIEW, APPROVED) e REJECTED
     * - REPLACED e EXPIRED ficam fora da listagem padrão
     * - documentos de matrícula (ENROLLMENT) nunca aparecem aqui
     * */
    private List<DocumentResponseDTO> listByOwner(HealthOwner owner, List<DocumentStatus> statuses) {
        List<DocumentStatus> filter = (statuses == null || statuses.isEmpty()) ? DEFAULT_LIST_STATUSES : statuses;

        return documentRepository.findByOwnerAndCategoryAndStatusIn(owner.id(), DocumentCategory.HEALTH, filter)
                .stream()
                .map(documentResponseMapper::toDTO)
                .toList();
    }

    /*
    * parte de validação de upload para os documentos de saude
    *
    * */

    /*
     *  responsável pelo upload completo de um documento:
     * - o dono (HealthOwner) já chega resolvido e validado
     * - validações e processamento (antivírus) acontecem FORA de transação
     * - somente a gravação no banco é transacional (TransactionTemplate)
     * - se o banco falhar, o arquivo já enviado ao storage é removido (compensação)
     * */
    private DocumentResponseDTO uploadHealthDocument(HealthOwner owner, UUID actorAccountId, DocumentUploadRequest request, MultipartFile file) {
        UUID customerId = TenantContext.getCurrentTenant();

        // valida condição, compatibilidade do tipo, validade e limite do plano
        HealthCondition condition = validateUpload(owner, request, customerId);

        // valida tamanho, tipo real e antivírus (lança exception se inválido)
        ProcessedDocument processed = documentProcessingService.process(file);

        String storageKey = generateKey(customerId, owner);

        try {
            Document saved = documentPersistenceService.storeAndPersist(processed.content(), storageKey, processed.contentType(),
                    () -> persist(owner, actorAccountId, request, condition, processed, storageKey, file.getOriginalFilename(), customerId));

            return documentResponseMapper.toDTO(saved);

        } catch (DataIntegrityViolationException e) {
            // o arquivo já foi removido pelo storeAndPersist e aqui só traduz o erro
            log.error("Violação de integridade no upload: {}", e.getMostSpecificCause().getMessage());
            throw new DocumentConflictException("Já existe um documento ativo desse tipo para essa condição", e);
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
        eventPublisher.publishEvent(new HealthDocumentUploadedEvent(saved.getId(), customerId));

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


        /*
        * verifica o limite apenas se for uma nova inserção, caso seja substituição não interfere
        * */
        boolean isReplacement = documentRepository.findActiveByHealthConditionIdAndType(request.healthConditionId(), request.documentType(), ACTIVE_STATUSES)
                .isPresent();

        if (!isReplacement) {
            validatePlanLimit(owner, customerId);
        }

        return condition;
    }

    /*
     * verifica o limite de documentos de saúde ativos por dono conforme o plano do Customer
     * - documentos de matrícula (ENROLLMENT) não contam: são temporários e apagados após a revisão
     * */
    private void validatePlanLimit(HealthOwner owner, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer não encontrado"));

        long activeDocuments = documentRepository.countByOwnerAndCategoryAndStatusIn(owner.id(), DocumentCategory.HEALTH, ACTIVE_STATUSES);

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
}
