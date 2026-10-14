package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.DocumentStorageProperties;
import com.travel_system.backend_app.exceptions.DocumentProcessingException;
import com.travel_system.backend_app.interfaces.PrivateStorageService;
import com.travel_system.backend_app.interfaces.mappers.response.DocumentResponseMapper;
import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.request.DocumentUploadRequest;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.DocumentType;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.ResponsibleAdultRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class DocumentService {

    private final DocumentStorageProperties documentStorageProperties;

    private final UserAccountRepository userAccountRepository;
    private final DocumentRepository documentRepository;

    private final PrivateStorageService privateStorageService;
    private final UserProfileResolverService userProfileResolverService;
    private final DocumentPersistenceService documentProcessingService;

    private final DocumentResponseMapper documentResponseMapper;

    List<String> VISIBLE_STATUSES = List.of("PENDING_REVIEW", "APPROVED", "REJECTED");

    public DocumentService(DocumentStorageProperties documentStorageProperties, UserAccountRepository userAccountRepository, DocumentRepository documentRepository, ResponsibleAdultRepository responsibleAdultRepository, PrivateStorageService privateStorageService, UserProfileResolverService userProfileResolverService, DocumentPersistenceService documentProcessingService, DocumentResponseMapper documentResponseMapper) {
        this.documentStorageProperties = documentStorageProperties;
        this.userAccountRepository = userAccountRepository;
        this.documentRepository = documentRepository;
        this.privateStorageService = privateStorageService;
        this.userProfileResolverService = userProfileResolverService;
        this.documentProcessingService = documentProcessingService;
        this.documentResponseMapper = documentResponseMapper;
    }

    // aluno maior ou motorista: os próprios documentos
    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> myDocuments() {
        return listByOwner(userProfileResolverService.resolveSelf());
    }

    // responsável: documentos de um aluno menor vinculado
    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> studentDocuments(UUID studentId) {
        return listByOwner(userProfileResolverService.resolveForResponsible(studentId));
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
        return generateDownload(userProfileResolverService.resolveSelf(), documentId);
    }

    @Transactional(readOnly = true)
    public DocumentDownloadResponseDTO downloadForStudent(UUID documentId) {

    }

    private DocumentDownloadResponseDTO generateDownload(HealthOwner context, UUID documentId) {

        Document document = (context.owner() == OwnerType.STUDENT
                ? documentRepository.findByIdAndStudentId(documentId, context.id())
                : documentRepository.findByIdAndDriverId(documentId, context.id()))
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado"));

        if (document.getDocumentStatus() == DocumentStatus.REPLACED) {
            throw new EntityNotFoundException("Documento não encontrado");
        }

        String url = privateStorageService.generateDownloadUrl(document.getStorageKey(), document.getOriginalFilename());

        // TODO: registrar acesso (SensitiveDataAccessLog) com context.actorAccountId() e documentId

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
    private List<DocumentResponseDTO> listByOwner(HealthOwner owner) {
        List<Document> documents = owner.owner() == OwnerType.STUDENT
                ? documentRepository.findByStudentIdAndDocumentStatusInOrderByCreatedAtDesc(owner.id(), VISIBLE_STATUSES)
                : documentRepository.findByDriverIdAndDocumentStatusInOrderByCreatedAtDesc(owner.id(), VISIBLE_STATUSES);

        return documents.stream().map(documentResponseMapper::toDTO).toList();
    }
}
