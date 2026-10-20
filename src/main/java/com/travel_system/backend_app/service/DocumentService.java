package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.DocumentStorageProperties;
import com.travel_system.backend_app.exceptions.DocumentNotFoundException;
import com.travel_system.backend_app.interfaces.PrivateStorageService;
import com.travel_system.backend_app.interfaces.mappers.response.DocumentResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.DocumentUploadRequest;
import com.travel_system.backend_app.model.dtos.request.HealthAccessContext;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.ResponsibleAdultRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class DocumentService {

    private final DocumentStorageProperties documentStorageProperties;

    private final UserAccountRepository userAccountRepository;
    private final DocumentRepository documentRepository;

    private final PrivateStorageService privateStorageService;
    private final UserProfileResolverService userProfileResolverService;
    private final DocumentPersistenceService documentProcessingService;
    private final SensitiveDataAccessLogService sensitiveDataAccessLogService;

    private final DocumentResponseMapper documentResponseMapper;

    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(DocumentStorageProperties documentStorageProperties, UserAccountRepository userAccountRepository, DocumentRepository documentRepository, ResponsibleAdultRepository responsibleAdultRepository, PrivateStorageService privateStorageService, UserProfileResolverService userProfileResolverService, DocumentPersistenceService documentProcessingService, SensitiveDataAccessLogService sensitiveDataAccessLogService, DocumentResponseMapper documentResponseMapper, ApplicationEventPublisher eventPublisher) {
        this.documentStorageProperties = documentStorageProperties;
        this.userAccountRepository = userAccountRepository;
        this.documentRepository = documentRepository;
        this.privateStorageService = privateStorageService;
        this.userProfileResolverService = userProfileResolverService;
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

        return documentProcessingService.upload(healthOwner, getAuthenticatedUserAccountId(), request, file);
    }

    // responsável por um menor de idade realizando o upload por ele
    public DocumentResponseDTO uploadForStudent(UUID studentId, DocumentUploadRequest request, MultipartFile file) {
        HealthOwner healthOwner = userProfileResolverService.resolveForResponsible(studentId);

        return documentProcessingService.upload(healthOwner, getAuthenticatedUserAccountId(), request, file);
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

        if (document.getDocumentStatus() == DocumentStatus.REPLACED) {
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
     *  lista os documentos de um dono:
     * - ativos (PENDING_REVIEW, APPROVED) e REJECTED (para o usuário ver o motivo e reenviar)
     * - REPLACED e EXPIRED ficam fora da listagem padrão
     * */
    private List<DocumentResponseDTO> listByOwner(HealthOwner owner, List<DocumentStatus> statuses) {
        List<String> strStatuses = statuses.stream().map(Enum::toString).toList();

        List<Document> documents = owner.type() == OwnerType.STUDENT
                ? documentRepository.findByStudentIdAndDocumentStatusInOrderByCreatedAtDesc(owner.id(), strStatuses)
                : documentRepository.findByDriverIdAndDocumentStatusInOrderByCreatedAtDesc(owner.id(), strStatuses);

        return documents.stream().map(documentResponseMapper::toDTO).toList();
    }
}
