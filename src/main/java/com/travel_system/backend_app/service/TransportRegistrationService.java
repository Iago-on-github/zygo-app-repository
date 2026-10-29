package com.travel_system.backend_app.service;

/*
* Upload do documento e da foto: valida a solicitação, monta a chave e define os callbacks
* */

import com.itextpdf.text.DocumentException;
import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.events.documents.RegistrationSubmittedEvent;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.*;
import com.travel_system.backend_app.model.dtos.security.ProcessedDocument;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.apache.tika.Tika;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class TransportRegistrationService {

    private final TransportRegistrationRepository transportRegistrationRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;
    private final CustomerTermRepository customerTermRepository;
    private final CustomerRepository customerRepository;
    private final UserAccountRepository userAccountRepository;

    private final RegistrationDocumentLifecycleService registrationDocumentLifecycleService;

    private final DocumentProcessingService documentProcessingService;
    private final DocumentPersistenceService documentPersistenceService;

    private final ImageProcessingService imageProcessingService;

    private final S3PrivateStorageService privateStorageService;
    private final UserProfileResolverService userProfileResolverService;

    private final Tika tika = new Tika();

    // formatos aceitos no envio (imagens)
    private static final Set<String> ACCEPTED_INPUT_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
    private static final String PDF = "application/pdf";

    // mapeia cada tipo de documento exigido para o código de pendência correspondente
    private static final Map<DocumentType, RegistrationMissingItem> MISSING_ITEM_BY_DOCUMENT_TYPE = Map.of(
            DocumentType.IDENTITY_DOCUMENT, RegistrationMissingItem.DOCUMENT_IDENTITY,
            DocumentType.INSTITUTION_ENROLLMENT_PROOF, RegistrationMissingItem.DOCUMENT_INSTITUTION_PROOF
    );

    private static final String ZONE = "America/Sao_Paulo";
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // status em que a solicitação ainda está aberta
    private static final Set<RegistrationRequestStatus> OPEN_REQUEST_STATUSES = EnumSet.of(RegistrationRequestStatus.DRAFT, RegistrationRequestStatus.UNDER_REVIEW);

    private final ApplicationEventPublisher eventPublisher;

    public TransportRegistrationService(TransportRegistrationRepository transportRegistrationRepository, RegistrationRequestRepository registrationRequestRepository, DocumentRepository documentRepository, StudentRepository studentRepository, CustomerTermRepository customerTermRepository, CustomerRepository customerRepository, UserAccountRepository userAccountRepository, RegistrationDocumentLifecycleService registrationDocumentLifecycleService, DocumentProcessingService documentProcessingService, DocumentPersistenceService documentPersistenceService, ImageProcessingService imageProcessingService, S3PrivateStorageService privateStorageService, UserProfileResolverService userProfileResolverService, ApplicationEventPublisher eventPublisher) {
        this.transportRegistrationRepository = transportRegistrationRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.customerTermRepository = customerTermRepository;
        this.customerRepository = customerRepository;
        this.userAccountRepository = userAccountRepository;
        this.registrationDocumentLifecycleService = registrationDocumentLifecycleService;
        this.documentProcessingService = documentProcessingService;
        this.documentPersistenceService = documentPersistenceService;
        this.imageProcessingService = imageProcessingService;
        this.privateStorageService = privateStorageService;
        this.userProfileResolverService = userProfileResolverService;
        this.eventPublisher = eventPublisher;
    }

    /*
    * consultas dividadas entre métodos "mine" e "forStudent" para estudantes menores de idade
    * */

    // aluno maior ou motorista retorna a própria matrícula
    @Transactional(readOnly = true)
    public TransportRegistrationResponseDTO consultMine() {
        return consult(userProfileResolverService.resolveSelf());
    }

    // responsável retorna matrícula de um aluno menor vinculado
    @Transactional(readOnly = true)
    public TransportRegistrationResponseDTO consultForStudent(UUID studentId) {
        return consult(userProfileResolverService.resolveForResponsible(studentId));
    }

    @Transactional
    public TransportRegistrationResponseDTO startMineRegistration() {
        return startRegistration(userProfileResolverService.resolveSelf());
    }

    @Transactional
    public TransportRegistrationResponseDTO startRegistrationForStudent(UUID studentId) {
        return startRegistration(userProfileResolverService.resolveForResponsible(studentId));
    }

    @Transactional
    public TransportRegistrationResponseDTO saveMineDraft(SaveDraftDTO saveDraftDTO) {
        return saveDraft(userProfileResolverService.resolveSelf(), saveDraftDTO);
    }

    @Transactional
    public TransportRegistrationResponseDTO saveDraftForStudent(UUID studentId, SaveDraftDTO saveDraftDTO) {
        return saveDraft(userProfileResolverService.resolveForResponsible(studentId), saveDraftDTO);
    }

    @Transactional
    public TransportRegistrationResponseDTO uploadDocument(MultipartFile file, DocumentType documentType) {
        return uploadDocument(file, documentType, userProfileResolverService.resolveSelf());
    }

    @Transactional
    public TransportRegistrationResponseDTO uploadDocumentForStudent(UUID studentId, MultipartFile file, DocumentType documentType) {
        return uploadDocument(file, documentType, userProfileResolverService.resolveForResponsible(studentId));
    }

    @Transactional
    public TransportRegistrationResponseDTO uploadMinePhoto(MultipartFile file) {
        return uploadPhoto(file, userProfileResolverService.resolveSelf());
    }

    @Transactional
    public TransportRegistrationResponseDTO uploadStudentPhoto(UUID studentId, MultipartFile file) {
        return uploadPhoto(file, userProfileResolverService.resolveForResponsible(studentId));
    }

    @Transactional
    public TransportRegistrationResponseDTO submitMine() {
        return submit(userProfileResolverService.resolveSelf());
    }

    @Transactional
    public TransportRegistrationResponseDTO submitForStudent(UUID studentId) {
        return submit(userProfileResolverService.resolveForResponsible(studentId));
    }

    @Transactional
    public TransportRegistrationResponseDTO cancelMine() {
        return cancel(userProfileResolverService.resolveSelf());
    }

    @Transactional
    public TransportRegistrationResponseDTO cancelForStudent(UUID studentId) {
        return cancel(userProfileResolverService.resolveForResponsible(studentId));
    }

    @Transactional
    public TransportRegistrationResponseDTO startRenewalMine() {
        return startRenewal(userProfileResolverService.resolveSelf());
    }

    @Transactional
    public TransportRegistrationResponseDTO startRenewalForStudent(UUID studentId) {
        return startRenewal(userProfileResolverService.resolveForResponsible(studentId));
    }

    // aluno maior ou motorista
    @Transactional
    public TransportRegistrationResponseDTO startUpdateMine() {
        return startUpdate(userProfileResolverService.resolveSelf());
    }

    // responsável por um aluno menor vinculado
    @Transactional
    public TransportRegistrationResponseDTO startUpdateForStudent(UUID studentId) {
        return startUpdate(userProfileResolverService.resolveForResponsible(studentId));
    }

    @Transactional
    public TransportRegistrationResponseDTO resubmitMine() {
        return resubmitAfterRejection(userProfileResolverService.resolveSelf());
    }

    // responsável por um aluno menor vinculado
    @Transactional
    public TransportRegistrationResponseDTO resubmitForStudent(UUID studentId) {
        return resubmitAfterRejection(userProfileResolverService.resolveForResponsible(studentId));
    }

    /*
    * PRIVATE METHODS
    * PRIVATE METHODS
    * PRIVATE METHODS
    * */

    /*
    * permite criar uma nova solicitação corrigindo apenas o que foi dito como incorreto pelo administrador (rejectCause)
    * não opera sob a solicitação rejeitada pq ela fica como log, sempre cria uma nova
    * */
    private TransportRegistrationResponseDTO resubmitAfterRejection(OwnerBuilt owner) {
        // busca a matrícula atual
        TransportRegistration registration = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhuma matrícula encontrada"));

        // verifica se já não há solicitação aberta e busca a última
        RegistrationRequest oldRequest = registrationRequestRepository.findFirstByTransportRegistration_IdOrderByCreatedAtDesc(registration.getId())
                .filter(status -> status.getStatus() == RegistrationRequestStatus.REJECTED)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhuma solicitação rejeitada"));

        Customer customer = getCurrentCustomer();

        // regras de tipo
        if (oldRequest.getType() == RegistrationRequestType.UPDATE) {
            if (today().isBefore(renewalWindowStart(registration, customer))) {
                throw new RegistrationNotUpdatableException(UpdateBlockReason.RENEWAL_PERIOD, "O período de renovação está aberto. Atualize os seus dados pela renovação");
            }
        }

        if (oldRequest.getType() == RegistrationRequestType.RENEWAL) {
            RenewalEligibility eligibility = evaluateRenewal(registration, false, customer);

            if (!eligibility.canRenew()) {
                throw new RegistrationNotRenewableException(eligibility.blockReason(), renewalBlockMessage(eligibility));
            }
        }

        // cria uma nova solicitação em draft
        // copia a filiação e identificação da rejeitada anteriormente
        RegistrationRequest newRequest = new RegistrationRequest();
        newRequest.setStatus(RegistrationRequestStatus.DRAFT);
        newRequest.setTransportRegistration(oldRequest.getTransportRegistration());
        newRequest.setType(oldRequest.getType());

        newRequest.setIdentityNumber(oldRequest.getIdentityNumber());
        newRequest.setIdentityIssuer(oldRequest.getIdentityIssuer());
        newRequest.setIdentityIssuedAt(oldRequest.getIdentityIssuedAt());

        newRequest.setMotherName(oldRequest.getMotherName());
        newRequest.setFatherName(oldRequest.getFatherName());

        // dados atuais do perfil (somente alunos)
        if (owner.type() == OwnerType.STUDENT) {
            Student student = studentRepository.findById(owner.id())
                    .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado"));

            student.getEnrollments().stream()
                    .filter(enrollment -> enrollment.getStatus() == GeneralStatus.ACTIVE)
                    .findFirst()
                    .ifPresent(enrollment -> {
                        newRequest.setInstitutionCourseId(enrollment.getCourse().getId());
                        newRequest.setPoolOfEnrollment(enrollment.getPoolOfEnrollment());
                    });

            newRequest.getShifts().addAll(student.getStudentShift());
        }
        // a fto é pendente/nula
        newRequest.setPendingPhotoStorageKey(null);
        newRequest.assignCustomer(customer.getId());

        try {
            registrationRequestRepository.saveAndFlush(newRequest);
        } catch (DataIntegrityViolationException e) {
            throw new RegistrationNotUpdatableException(UpdateBlockReason.OPEN_REQUEST, "Já existe uma solicitação em andamento");
        }

        return consult(owner);
    }

    /*
     * abre uma alteração dos dados de identificação durante o ciclo
     * */
    private TransportRegistrationResponseDTO startUpdate(OwnerBuilt owner) {
        TransportRegistration registration = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhuma matrícula encontrada"));

        // somente matrícula ativa
        if (registration.getStatus() != TransportRegistrationStatus.ACTIVE || registration.getCustomerTerm() == null) {
            throw new RegistrationNotUpdatableException(UpdateBlockReason.NOT_ACTIVE, "Somente uma matrícula ativa pode ser alterada");
        }

        // uma solicitação por vez
        boolean hasOpenRequest = registrationRequestRepository
                .findOpenByRegistrationId(registration.getId(), OPEN_REQUEST_STATUSES)
                .isPresent();

        if (hasOpenRequest) {
            throw new RegistrationNotUpdatableException(UpdateBlockReason.OPEN_REQUEST, "Já existe uma solicitação em andamento");
        }

        // a partir da abertura da janela (inclui a carência), os dados são atualizados pela renovação
        LocalDate windowStart = renewalWindowStart(registration, getCurrentCustomer());

        if (!today().isBefore(windowStart)) {
            throw new RegistrationNotUpdatableException(UpdateBlockReason.RENEWAL_PERIOD,
                    "O período de renovação está aberto. Atualize os seus dados pela renovação");
        }

        RegistrationRequest request = newRequestFrom(registration, RegistrationRequestType.UPDATE, owner);

        try {
            registrationRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new RegistrationNotUpdatableException(UpdateBlockReason.OPEN_REQUEST, "Já existe uma solicitação em andamento");
        }

        return consult(owner);
    }

    /*
     * abre a rematrícula para o próximo período
     * */
    private TransportRegistrationResponseDTO startRenewal(OwnerBuilt owner) {
        TransportRegistration registration = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhuma matrícula encontrada"));

        boolean hasOpenRequest = registrationRequestRepository
                .findOpenByRegistrationId(registration.getId(), OPEN_REQUEST_STATUSES)
                .isPresent();

        // mesmas regras exibidas na consulta (canRenew / renewalAvailableFrom)
        RenewalEligibility eligibility = evaluateRenewal(registration, hasOpenRequest, getCurrentCustomer());

        if (!eligibility.canRenew()) {
            throw new RegistrationNotRenewableException(eligibility.blockReason(), renewalBlockMessage(eligibility));
        }

        RegistrationRequest request = newRequestFrom(registration, RegistrationRequestType.RENEWAL, owner);

        try {
            registrationRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new RegistrationNotRenewableException(RenewalBlockReason.OPEN_REQUEST, "Já existe uma solicitação em andamento");
        }

        return consult(owner);
    }

    /*
     * avalia se a matrícula pode ser renovada hoje
     * */
    private RenewalEligibility evaluateRenewal(TransportRegistration registration, boolean hasOpenRequest, Customer customer) {
        if (registration.getStatus() != TransportRegistrationStatus.ACTIVE || registration.getCustomerTerm() == null) {
            return RenewalEligibility.blocked(RenewalBlockReason.NOT_ACTIVE, null);
        }

        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        LocalDate termEnd = registration.getCustomerTerm().getEndTerm();

        LocalDate windowStart = termEnd.minusDays(customer.getRegistrationRenewalWindowDays());
        LocalDate graceEnd = termEnd.plusDays(customer.getRegistrationGracePeriodDays());

        if (hasOpenRequest) {
            return RenewalEligibility.blocked(RenewalBlockReason.OPEN_REQUEST, windowStart);
        }

        if (today.isAfter(graceEnd)) {
            return RenewalEligibility.blocked(RenewalBlockReason.GRACE_EXPIRED, windowStart);
        }

        // janela ainda não abriu
        if (today.isBefore(windowStart)) {
            return RenewalEligibility.blocked(RenewalBlockReason.OUTSIDE_WINDOW, windowStart);
        }

        // sem o próximo período não há para onde renovar
        boolean hasNextTerm = customerTermRepository.findFirstByStartTermAfterOrderByStartTermAsc(termEnd).isPresent();

        if (!hasNextTerm) {
            return RenewalEligibility.blocked(RenewalBlockReason.NEXT_TERM_MISSING, windowStart);
        }

        return RenewalEligibility.allowed(windowStart);
    }

    /*
     * monta uma nova solicitação a partir da matrícula e do perfil
     * */
    private RegistrationRequest newRequestFrom(TransportRegistration registration, RegistrationRequestType type, OwnerBuilt owner) {
        RegistrationRequest request = new RegistrationRequest();

        request.assignCustomer(registration.getCustomerId());
        request.setTransportRegistration(registration);
        request.setType(type);
        request.setStatus(RegistrationRequestStatus.DRAFT);

        // dados aprovados da matrícula
        request.setIdentityNumber(registration.getIdentityNumber());
        request.setIdentityIssuer(registration.getIdentityIssuer());
        request.setIdentityIssuedAt(registration.getIdentityIssuedAt());
        request.setMotherName(registration.getMotherName());
        request.setFatherName(registration.getFatherName());

        // dados atuais do perfil (somente alunos)
        if (owner.type() == OwnerType.STUDENT) {
            Student student = studentRepository.findById(owner.id())
                    .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado"));

            student.getEnrollments().stream()
                    .filter(enrollment -> enrollment.getStatus() == GeneralStatus.ACTIVE)
                    .findFirst()
                    .ifPresent(enrollment -> {
                        request.setInstitutionCourseId(enrollment.getCourse().getId());
                        request.setPoolOfEnrollment(enrollment.getPoolOfEnrollment());
                    });

            // cópia: a coleção do Student é gerenciada pelo Hibernate e não pode ser compartilhada
            request.getShifts().addAll(student.getStudentShift());
        }

        return request;
    }

    /*
     * ação de enviar ao admin a solicitação completa
     * - exige a solicitação aberta em DRAFT (o lock impede concorrência com uploads em andamento)
     * - atualiza a cópia da instituição/curso/turnos (alunos) para o admin analisar a informação vigente
     * - valida tudo de uma vez e devolve a lista completa do que falta
     * */
    private TransportRegistrationResponseDTO submit(OwnerBuilt owner) {
        // busca a matrícula atual + a solicitação em aberto com uso de lock
        TransportRegistration transportRegistration = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhuma matrícula encontrada"));

        RegistrationRequest request = registrationRequestRepository.findOpenByRegistrationIdForUpdate(transportRegistration.getId(), OPEN_REQUEST_STATUSES)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhuma solicitação aberta"));

        // so permite caso a solicitação ainda esteja em DRAFT
        if (request.getStatus() != RegistrationRequestStatus.DRAFT) {
            throw new RegistrationRequestNotEditableException("A solicitação está em análise e não pode ser alterada");
        }

        /*
         * validações que não podem parar em nenhum erro, deve juntar tudo e informar o que falta
         * */
        List<RegistrationMissingItem> missingItems = new ArrayList<>();

        // realiza novamente a cópia dos dados dos alunos p/ defensividade
        if (owner.type() == OwnerType.STUDENT) {
            Student student = studentRepository.findById(owner.id())
                    .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado"));

            StudentEnrollment studentEnrollment = student.getEnrollments().stream()
                    .filter(enrollment -> enrollment.getStatus() == GeneralStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);

            if (studentEnrollment == null) {
                missingItems.add(RegistrationMissingItem.INSTITUTION);
            } else {
                request.setInstitutionCourseId(studentEnrollment.getCourse().getId());
                request.setPoolOfEnrollment(studentEnrollment.getPoolOfEnrollment());
            }

            // altera o conteúdo da coleção gerenciada pelo Hibernate em vez de trocar a referência
            request.getShifts().clear();
            request.getShifts().addAll(student.getStudentShift());
        }

        // verificação dos campos demais campos obrigatórios
        if (isBlank(request.getIdentityNumber())) {
            missingItems.add(RegistrationMissingItem.IDENTITY_NUMBER);
        }
        if (isBlank(request.getIdentityIssuer())) {
            missingItems.add(RegistrationMissingItem.IDENTITY_ISSUER);
        }
        if (request.getIdentityIssuedAt() == null) {
            missingItems.add(RegistrationMissingItem.IDENTITY_ISSUED_AT);
        } else if (request.getIdentityIssuedAt().isAfter(LocalDate.now(ZoneId.of(ZONE)))) {
            missingItems.add(RegistrationMissingItem.IDENTITY_ISSUED_AT_IN_FUTURE);
        }
        if (isBlank(request.getMotherName())) {
            missingItems.add(RegistrationMissingItem.MOTHER_NAME);
        }

        // verifica se há um documento ativo para cada tipo de solcitação
        Set<DocumentType> sentTypes = documentRepository.findAllByRegistrationRequestId(request.getId()).stream()
                .filter(document -> document.getDocumentStatus() == DocumentStatus.PENDING_REVIEW)
                .map(Document::getDocumentType)
                .collect(Collectors.toSet());

        for (DocumentType requiredType : requiredDocumentTypes(request.getType(), owner.type())) {
            if (!sentTypes.contains(requiredType)) {
                missingItems.add(MISSING_ITEM_BY_DOCUMENT_TYPE.get(requiredType));
            }
        }

        // a foto é obrigatória na INITIAL (primeira matrícula) nas demais, a atual da matrícula é mantida (se existir)
        boolean hasPendingPhoto = request.getPendingPhotoStorageKey() != null;
        boolean hasCurrentPhoto = transportRegistration.getPhotoStorageKey() != null;

        boolean photoMissing = request.getType() == RegistrationRequestType.INITIAL ? !hasPendingPhoto : !hasPendingPhoto && !hasCurrentPhoto;

        if (photoMissing) {
            missingItems.add(RegistrationMissingItem.PHOTO);
        }

        // se incompleta vai devolver tudo oq falta de uma vez
        if (!missingItems.isEmpty()) {
            throw new RegistrationIncompleteException(missingItems);
        }

        // se completo envia direto para análise por parte do administrador
        request.setStatus(RegistrationRequestStatus.UNDER_REVIEW);
        request.setSubmittedBy(getAuthenticatedUserAccountId());
        request.setSubmittedAt(Instant.now());

        // notificação dos admins do customer
        eventPublisher.publishEvent(new RegistrationSubmittedEvent(
                request.getId(),
                transportRegistration.getId(),
                request.getCustomerId(),
                owner,
                request.getType()
        ));

        return consult(owner);
    }

    /*
    * upload de foto 3x4 apenas. Documentos não são aceitos
    * */
    private TransportRegistrationResponseDTO uploadPhoto(MultipartFile file, OwnerBuilt owner) {
        // busca a matrícula atual + a solicitação em aberto com uso de lock
        TransportRegistration transportRegistration = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhum registro encontrado"));

        // busca o registro
        RegistrationRequest registrationRequest = registrationRequestRepository.findOpenByRegistrationId(transportRegistration.getId(), OPEN_REQUEST_STATUSES)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhum registro encontrado"));

        byte[] bytes = imageProcessingService.convertImageToJPEG(file, ImageUploadPolicy.REGISTRATION_PHOTO);

        ProcessedDocument processedDocument = new ProcessedDocument(bytes, "image/jpeg", bytes.length, null);

        String key = generatePhotoKey(registrationRequest.getCustomerId(), owner, registrationRequest.getId());

        documentPersistenceService.storeAndPersist(processedDocument.content(), key, processedDocument.contentType(), () -> {
            RegistrationRequest request = registrationRequestRepository.findOpenByRegistrationIdForUpdate(transportRegistration.getId(), OPEN_REQUEST_STATUSES)
                    .orElseThrow(() -> new RegistrationNotFoundException("Nenhuma solicitação encontrada"));

            if (request.getStatus() != RegistrationRequestStatus.DRAFT) {
                throw new RegistrationRequestNotEditableException("A solicitação está em análise e não pode ser alterada");
            }

            String pendingPhotoStorageKey = request.getPendingPhotoStorageKey();

            request.setPendingPhotoStorageKey(key);

            if (pendingPhotoStorageKey != null) {
                eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(List.of(pendingPhotoStorageKey)));
            }

            return registrationRequestRepository.save(request);
        });

        return consult(owner);
    }

    /*
     * desistência de uma renovação ou alteração que ainda não foi enviada
     * */
    private TransportRegistrationResponseDTO cancel(OwnerBuilt owner) {
        // busca a matrícula atual + a solicitação em aberto com uso de lock
        TransportRegistration transportRegistration = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhuma matrícula encontrada"));

        RegistrationRequest request = registrationRequestRepository.findOpenByRegistrationIdForUpdate(transportRegistration.getId(), OPEN_REQUEST_STATUSES)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhuma solicitação aberta para cancelar"));

        // em análise: somente o admin pode concluir
        if (request.getStatus() != RegistrationRequestStatus.DRAFT) {
            throw new RegistrationRequestNotEditableException("A solicitação está em análise e não pode ser cancelada");
        }

        // a primeira matrícula não pode ser cancelada: o usuário pode corrigir o rascunho e reenviar os arquivos
        if (request.getType() == RegistrationRequestType.INITIAL) {
            throw new RegistrationRequestNotCancelableException("A solicitação da primeira matrícula não pode ser cancelada");
        }

        registrationDocumentLifecycleService.cancelRequest(request);

        return consult(owner);
    }

    /*
    * upload de uma imagem ou documento em si
    * a validação acontece pelo tika e o redirecionamento para o processamento correto acontece via detecção do tipo
    * */
    private TransportRegistrationResponseDTO uploadDocument(MultipartFile file, DocumentType documentType, OwnerBuilt owner) {
        // busca a matrícula atual + a solicitação em aberto com uso de lock
        TransportRegistration transportRegistration = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhum registro encontrado"));

        // busca o registro
        RegistrationRequest registrationRequest = registrationRequestRepository.findOpenByRegistrationId(transportRegistration.getId(), OPEN_REQUEST_STATUSES)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhum registro encontrado"));

        /*
         * valdações
         * */

        // so permite caso a solicitação ainda esteja em DRAFT
        if (registrationRequest.getStatus() != RegistrationRequestStatus.DRAFT) {
            throw new RegistrationRequestNotEditableException("A solicitação está em análise e não pode ser alterada");
        }

        Set<DocumentType> documentTypes = requiredDocumentTypes(registrationRequest.getType(), owner.type());

        // valida categoria
        if (documentType.getCategory() != DocumentCategory.ENROLLMENT) {
            throw new DocumentValidationException("O Documento precisa ser do tipo ENROLLMENT");
        }

        boolean isDocumentType = documentTypes.stream().anyMatch(type -> type == documentType);

        if (!isDocumentType) {
            throw new DocumentValidationException("O tipo de documento não corresponde aos tipos esperados");
        }

        // valida com TIKA para verificar o tipo de documento e encaminha o processo conforme
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new DocumentProcessingException("Ocorreu um erro durante o processamento do documento.");
        }

        String detect = tika.detect(bytes);


        ProcessedDocument processed;
        String extension;

        // verifica imagens
        if (ACCEPTED_INPUT_CONTENT_TYPES.contains(detect)) {
            byte[] jpeg = imageProcessingService.convertImageToJPEG(file, ImageUploadPolicy.REGISTRATION_DOCUMENT);

            processed = new ProcessedDocument(jpeg, "image/jpeg", jpeg.length, documentProcessingService.sha256(jpeg));
            extension = "jpg";

        } else if (detect.equals(PDF)) {
            processed = documentProcessingService.process(file);
            extension = "pdf";
        } else {
            throw new UnsupportedMediaTypeException("Formato não suportado. Envie PDF, JPEG ou PNG");
        }

        String key = generateDocKey(getCurrentTenant(), owner, registrationRequest.getId(), extension);

        documentPersistenceService.storeAndPersist(processed.content(), key, processed.contentType(), () -> {
            RegistrationRequest request = registrationRequestRepository.findOpenByRegistrationIdForUpdate(transportRegistration.getId(), OPEN_REQUEST_STATUSES)
                    .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhuma solicitação encontrada"));

            if (request.getStatus() != RegistrationRequestStatus.DRAFT) {
                throw new RegistrationRequestNotEditableException("A solicitação está em análise e não pode ser alterada");
            }

            registrationDocumentLifecycleService.discardPreviousOfType(registrationRequest.getId(), documentType);

            Document document = new Document();
            document.setDocumentCategory(DocumentCategory.ENROLLMENT);
            document.setDocumentStatus(DocumentStatus.PENDING_REVIEW);
            if (owner.type() == OwnerType.STUDENT) {
                document.setStudentId(owner.id());
            } else {
                document.setDriverId(owner.id());
            }
            document.setRegistrationRequestId(request.getId());
            document.setDocumentType(documentType);
            document.setStorageKey(key);
            document.setContentType(processed.contentType());
            document.setSizeBytes(processed.sizeBytes());
            document.setSha256(processed.sha256());
            document.setOriginalFilename(file.getOriginalFilename() + extension);
            document.setUploadedBy(getAuthenticatedUserAccountId());
            document.assignCustomer(getCurrentTenant());

            return documentRepository.save(document);
        });

        return consult(owner);
    }

    /*
     * responsável por guardar o que o user ja digitou no formulário de matricula, mas nao envia ao admin
     *
     * */
    private TransportRegistrationResponseDTO saveDraft(OwnerBuilt ownerBuilt, SaveDraftDTO dto) {
        // busca a matrícula atual + a solicitação em aberto com uso de lock
        TransportRegistration transportRegistration = transportRegistrationRepository.findCurrentByOwnerId(ownerBuilt.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhum registro encontrado"));

        // busca o registro ativo pelo user e com lock
        RegistrationRequest openRequest = registrationRequestRepository.findOpenByRegistrationIdForUpdate(transportRegistration.getId(), OPEN_REQUEST_STATUSES)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhuma requisição de registro aberta para esse usuário"));

        // verifica se já nao está em análise
        if (openRequest.getStatus() != RegistrationRequestStatus.DRAFT) {
            throw new RegistrationRequestNotEditableException("A solicitação está em análise e não pode ser alterada");
        }

        // salva os dados
        openRequest.setIdentityNumber(dto.identityNumber());
        openRequest.setIdentityIssuer(dto.identityIssuer());
        openRequest.setIdentityIssuedAt(dto.identityIssuedAt());
        openRequest.setMotherName(dto.motherName());
        openRequest.setFatherName(dto.fatherName());

        registrationRequestRepository.save(openRequest);

        return consult(ownerBuilt);
    }

    /*
    * cria a matrícula do student ou motorista
    * - precisa estar sem matrícula ou com a última em CLOSED
    * - caso já esteja com matrícula retorna 409
    * */
    private TransportRegistrationResponseDTO startRegistration(OwnerBuilt ownerBuilt) {
        // caso tenha matrícula lança exception
        if (transportRegistrationRepository.findByOwnerId(ownerBuilt.id()).isPresent()) {
            throw new HasTransportRegistrationException("Usuário já possui uma matrícula");
        }

        // cria o registro a partir do perfil
        TransportRegistration registration = new TransportRegistration();

        registration.setStatus(TransportRegistrationStatus.PENDING);
        registration.assignCustomer(getCurrentTenant());

        // seta o owner
        boolean isStudent = false;
        if (ownerBuilt.type() == OwnerType.STUDENT) {
            registration.setStudentId(ownerBuilt.id());
            isStudent = true;
        } else {
            registration.setDriverId(ownerBuilt.id());
        }

        // cria a request do registro
        RegistrationRequest request = new RegistrationRequest();
        request.setTransportRegistration(registration);
        request.setStatus(RegistrationRequestStatus.DRAFT);
        request.setType(RegistrationRequestType.INITIAL);

        request.assignCustomer(getCurrentTenant());

        // se for estudante preenche com os dados específicos dele
        if (isStudent) {
            Student student = studentRepository.findById(ownerBuilt.id())
                    .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado"));

            // por mais que haja possibilidae do estudante estar em mais de um curso, não é necessário adicionar todos
            StudentEnrollment studentEnrollment = student.getEnrollments().stream().findFirst()
                    .filter(se -> se.getStatus() == GeneralStatus.ACTIVE)
                    .orElse(null);

            if (studentEnrollment != null) {
                request.setInstitutionCourseId( studentEnrollment.getCourse().getId());
                request.setPoolOfEnrollment(studentEnrollment.getPoolOfEnrollment());
            }

            request.setShifts(new HashSet<>(student.getStudentShift()));
        }


        // evita concorrência
        try {
            transportRegistrationRepository.saveAndFlush(registration);
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("Ocorreu um erro ao salvar o registro");
        }

        registrationRequestRepository.save(request);

        return consult(ownerBuilt);
    }

    /*
     * consulta a situação da matrícula do titular
     * - sem matrícula atual: devolve a última encerrada (CLOSED) para o app oferecer "iniciar nova matrícula"
     * - sem solicitação aberta: estado normal de uma matrícula em dia (checklist vazio)
     * - se a última solicitação foi rejeitada, informa o motivo
     * - com solicitação aberta: monta o checklist de documentos e a situação da foto
     * */
    private TransportRegistrationResponseDTO consult(OwnerBuilt owner) {
        Optional<TransportRegistration> current = transportRegistrationRepository.findCurrentByOwnerId(owner.id(), TransportRegistrationStatus.CLOSED);

        // se sem matrícula busca a última encerrada
        if (current.isEmpty()) {
            TransportRegistration closed = transportRegistrationRepository.findLatestByOwnerIdAndStatus(owner.id(), TransportRegistrationStatus.CLOSED)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhuma matrícula encontrada")); // se nunca teve matrícula

            return toResponse(closed, null, List.of(), null);
        }

        TransportRegistration registration = current.get();

        // busca a solicitação aberta
        RegistrationRequest openRequest = registrationRequestRepository.findOpenByRegistrationId(registration.getId(), OPEN_REQUEST_STATUSES)
                .orElse(null);

        // sem solicitação aberta: informa a rejeição, se a última solicitação foi rejeitada
        if (openRequest == null) {
            RegistrationRejectionDTO lastRejection = registrationRequestRepository
                    .findFirstByTransportRegistration_IdOrderByCreatedAtDesc(registration.getId())
                    .filter(last -> last.getStatus() == RegistrationRequestStatus.REJECTED)
                    .map(this::toRejection)
                    .orElse(null);

            return toResponse(registration, null, List.of(), lastRejection);
        }

        return toResponse(registration, openRequest, buildChecklist(openRequest, owner.type()), null);
    }

    /*
     * checklist dos documentos exigidos pela solicitação
     * - um item por tipo exigido, indicando se já foi enviado
     * */
    private List<RegistrationChecklistItemDTO> buildChecklist(RegistrationRequest request, OwnerType ownerType) {
        // documentos já enviados, indexados por tipo (o índice único garante um ativo por tipo)
        Map<DocumentType, Document> sentByType = documentRepository.findAllByRegistrationRequestId(request.getId())
                .stream()
                .collect(Collectors.toMap(
                        Document::getDocumentType,
                        Function.identity(),
                        // por segurança, em caso de duplicidade mantém o mais recente
                        (first, second) -> first.getCreatedAt().isAfter(second.getCreatedAt()) ? first : second));

        return requiredDocumentTypes(request.getType(), ownerType).stream()
                .map(type -> {
                    Document sent = sentByType.get(type);

                    return new RegistrationChecklistItemDTO(
                            type,
                            sent != null,
                            sent != null ? sent.getId() : null,
                            sent != null ? sent.getCreatedAt() : null);
                })
                .toList();
    }

    /*
     * monta a resposta da consulta
     * - validade: fim do período (null enquanto a matrícula não tiver período)
     * - foto: a pendente da solicitação aberta tem prioridade sobre a atual da matrícula
     * */
    private TransportRegistrationResponseDTO toResponse(TransportRegistration registration, RegistrationRequest openRequest, List<RegistrationChecklistItemDTO> checklist, RegistrationRejectionDTO lastRejection) {

        LocalDate validUntil = registration.getCustomerTerm() != null
                ? registration.getCustomerTerm().getEndTerm()
                : null;

        String pendingPhotoKey = openRequest != null ? openRequest.getPendingPhotoStorageKey() : null;
        String photoKey = pendingPhotoKey != null ? pendingPhotoKey : registration.getPhotoStorageKey();

        // URL temporária somente quando há foto (o titular vê os próprios dados: sem log de acesso)
        String photoUrl = photoKey != null ? privateStorageService.generatePresignPhotoUrl(photoKey) : null;

        // foto obrigatória apenas na primeira matrícula; na renovação/alteração a atual é mantida
        boolean photoRequired = openRequest != null && openRequest.getType() == RegistrationRequestType.INITIAL;
        boolean photoSent = pendingPhotoKey != null;

        return new TransportRegistrationResponseDTO(
                registration.getId(),
                registration.getRegistrationNumber(),
                registration.getStatus(),
                validUntil,
                photoUrl,
                openRequest != null ? toSummary(openRequest) : null,
                checklist,
                photoRequired,
                photoSent,
                lastRejection
        );
    }

    /*
     * documentos exigidos por tipo de solicitação e tipo de titular
     * - também usado na validação do upload e no envio para análise
     * */
    private Set<DocumentType> requiredDocumentTypes(RegistrationRequestType requestType, OwnerType ownerType) {
        if (requestType == RegistrationRequestType.UPDATE) {
            return EnumSet.of(DocumentType.IDENTITY_DOCUMENT);
        }

        // INITIAL e RENEWAL
        return ownerType == OwnerType.STUDENT ? EnumSet.of(DocumentType.IDENTITY_DOCUMENT, DocumentType.INSTITUTION_ENROLLMENT_PROOF) : EnumSet.of(DocumentType.IDENTITY_DOCUMENT);
    }

    // resumo da solicitação para o app montar o formulário
    private RegistrationRequestSummaryDTO toSummary(RegistrationRequest request) {
        return new RegistrationRequestSummaryDTO(
                request.getId(),
                request.getType(),
                request.getStatus(),

                request.getIdentityNumber(),
                request.getIdentityIssuer(),
                request.getIdentityIssuedAt(),
                request.getMotherName(),
                request.getFatherName(),

                request.getInstitutionCourseId(),
                request.getPoolOfEnrollment(),
                Set.copyOf(request.getShifts()),

                request.getRejectionReason(),
                request.getSubmittedAt());
    }

    // motivo da última rejeição
    private RegistrationRejectionDTO toRejection(RegistrationRequest request) {
        return new RegistrationRejectionDTO(
                request.getId(),
                request.getType(),
                request.getRejectionReason(),
                request.getReviewedAt());
    }

    private String generateDocKey(UUID customerId, OwnerBuilt owner, UUID requestId, String extension) {
        return "customers/ " + customerId + "/registrations/" + requestId + "/" + owner.id() + "." + extension;
    }

    private String generatePhotoKey(UUID customerId, OwnerBuilt owner, UUID requestId) {
        return "customers/" + customerId + "/registration-photos/" + requestId + "/" + owner.id() + ".jpg";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // mensagem amigável para cada motivo de bloqueio
    private String renewalBlockMessage(RenewalEligibility eligibility) {
        return switch (eligibility.blockReason()) {
            case NOT_ACTIVE -> "Somente uma matrícula ativa pode ser renovada";
            case OPEN_REQUEST -> "Já existe uma solicitação em andamento";
            case GRACE_EXPIRED -> "O prazo de renovação terminou. Será necessário realizar uma nova matrícula";
            case OUTSIDE_WINDOW -> "A renovação abre em " + eligibility.availableFrom().format(BR_DATE);
            case NEXT_TERM_MISSING -> "O próximo período ainda não foi cadastrado pela secretaria";
        };
    }


    // início da janela de renovação: fim do período atual − dias de antecedência configurados no Customer
    private LocalDate renewalWindowStart(TransportRegistration registration, Customer customer) {
        return registration.getCustomerTerm().getEndTerm()
                .minusDays(customer.getRegistrationRenewalWindowDays());
    }

    // customer do usuário logado (via TenantContext)
    private Customer getCurrentCustomer() {
        return customerRepository.findById(getCurrentTenant())
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(ZONE));
    }

    private UUID getAuthenticatedUserAccountId() {
        String email = getAuthenticatedUserEmail();

        UserAccount user = userAccountRepository.findUserByEmail(email);

        if (user == null) {
            throw new AccessDeniedException("Acesso negado, user não existe.");
        }

        return user.getId();
    }
}
