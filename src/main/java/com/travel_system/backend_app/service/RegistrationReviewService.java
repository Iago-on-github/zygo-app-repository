package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.DocumentStorageProperties;
import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.events.documents.RegistrationApprovedEvent;
import com.travel_system.backend_app.events.documents.RegistrationRejectedEvent;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.PrivateStorageService;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.dtos.request.RejectRegistrationDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RequestPhotoResponseDTO;
import com.travel_system.backend_app.model.dtos.response.TransportRegistrationReviewDTO;
import com.travel_system.backend_app.model.dtos.response.TransportRegistrationReviewDetailsDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.utils.RegistrationNumberGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.ws.rs.core.Request;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

/*
* responsável por gerenciar toda a parte dos registros no sistema
* */

@Service
public class RegistrationReviewService {

    private final DocumentStorageProperties documentStorageProperties;

    private final RegistrationRequestRepository registrationRequestRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final AdministratorRepository administratorRepository;
    private final CustomerTermRepository customerTermRepository;
    private final CustomerRepository customerRepository;
    private final DocumentRepository documentRepository;

    private final RegistrationDocumentLifecycleService registrationDocumentLifecycleService;
    private final RegistrationNumberGenerator registrationNumberGenerator;
    private final PrivateStorageService privateStorageService;

    private final SensitiveDataAccessLogService sensitiveDataAccessLogService;

    private final ApplicationEventPublisher eventPublisher;

    public RegistrationReviewService(DocumentStorageProperties documentStorageProperties, RegistrationRequestRepository registrationRequestRepository, StudentRepository studentRepository, DriverRepository driverRepository, AdministratorRepository administratorRepository, CustomerTermRepository customerTermRepository, CustomerRepository customerRepository, DocumentRepository documentRepository, RegistrationDocumentLifecycleService registrationDocumentLifecycleService, RegistrationNumberGenerator registrationNumberGenerator, PrivateStorageService privateStorageService, SensitiveDataAccessLogService sensitiveDataAccessLogService, ApplicationEventPublisher eventPublisher) {
        this.documentStorageProperties = documentStorageProperties;
        this.registrationRequestRepository = registrationRequestRepository;
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.administratorRepository = administratorRepository;
        this.customerTermRepository = customerTermRepository;
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.registrationDocumentLifecycleService = registrationDocumentLifecycleService;
        this.registrationNumberGenerator = registrationNumberGenerator;
        this.privateStorageService = privateStorageService;
        this.sensitiveDataAccessLogService = sensitiveDataAccessLogService;
        this.eventPublisher = eventPublisher;
    }

    /*
    * mostrar ao admin o que está esperando para análise
    * busca tudo o que está em UNDER_REVIEW ordenados por submittedAt
    *
    * retorna apenas: id da solicitação, tipo, nome do titular, submittedAt e o número da matricula (que é null se for a primeira)
    * */
    @Transactional(readOnly = true)
    public Page<TransportRegistrationReviewDTO> reviewQueue(Pageable pageable) {
        getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());

        Page<RegistrationRequest> allToReview = registrationRequestRepository.findAllToReview(RegistrationRequestStatus.UNDER_REVIEW, pageable);

        if (allToReview.isEmpty()) {
            return Page.empty(pageable);
        }

        Set<UUID> studentIds = allToReview.stream()
                .filter(tr -> tr.getTransportRegistration().getStudentId() != null)
                .map(id -> id.getTransportRegistration().getStudentId()).collect(Collectors.toSet());

        Set<UUID> driverIds = allToReview.stream()
                .filter(tr -> tr.getTransportRegistration().getDriverId() != null)
                .map(id -> id.getTransportRegistration().getDriverId()).collect(Collectors.toSet());

        List<Student> students = studentRepository.findAllById(studentIds);

        List<Driver> drivers = driverRepository.findAllById(driverIds);

        Map<UUID, String> studentsData = students.stream().collect(Collectors.toMap(Student::getId,
                student -> Stream.of(student.getName(), student.getLastName()).filter(Objects::nonNull)
                        .collect(Collectors.joining(" "))));

        Map<UUID, String> driversData = drivers.stream().collect(Collectors.toMap(Driver::getId,
                driver -> Stream.of(driver.getName(), driver.getLastName()).filter(Objects::nonNull)
                .collect(Collectors.joining(" "))));

        return allToReview.map(request -> {
            TransportRegistration registration = request.getTransportRegistration();

            // nome do titular
            boolean isStudent = registration.getStudentId() != null;

            String ownerName = isStudent ? studentsData.get(registration.getStudentId()) : driversData.get(registration.getDriverId());

            return new TransportRegistrationReviewDTO(
                    request.getId(),
                    request.getType(),
                    ownerName,
                    request.getSubmittedAt(),
                    registration.getRegistrationNumber(),
                    isStudent ? OwnerType.STUDENT : OwnerType.DRIVER
            );
        });
    }

    /*
    * reune tudo o que o admin precisa para decidir
    *
    * o resultado pode ser geral, sem filtro por tipo. Solicitações antigas aqui são tratadas como histórico
    * */
    @Transactional(readOnly = true)
    public TransportRegistrationReviewDetailsDTO getRequestDetail(UUID requestId) {
        Administrator administrator = getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());

        RegistrationRequest request = registrationRequestRepository.findById(requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhum registro encontrado"));

        TransportRegistration registration = request.getTransportRegistration();

        String ownerName;
        boolean isStudent = registration.getStudentId() != null;

        OwnerBuilt ownerBuilt = buildOwner(registration.getStudentId(), registration.getDriverId());

        // verifica se já existe uma foto pendente e se a matricula ja tem uma
        boolean hasPendingPhoto = request.getPendingPhotoStorageKey() != null;
        boolean hasRegistrationPhoto = registration.getPhotoStorageKey() != null;

        // gera log de acesso para operação sensível
        sensitiveDataAccessLogService.record(administrator.getUserAccount().getId(), ownerBuilt, ResourceType.TRANSPORT_REGISTRATION, request.getId(), Action.VIEW, null);

        return buildRegistrationDetailsResponseDTO(request, registration, hasPendingPhoto, hasRegistrationPhoto, ownerBuilt);
    }

    /*
    * download de um documento
    *
    * os documentos só podem ser baixados enquanto há solicitação aberta
    * durante a revisão os documentos são apagados
    * */
    @Transactional(readOnly = true)
    public DocumentDownloadResponseDTO downloadRequestDocument(UUID requestId, UUID documentId) {
        Administrator administrator = getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());

        RegistrationRequest request = registrationRequestRepository.findById(requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhum registro encontrado"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado"));

        // verifica se o doc informado existe na request
        if (!requestId.equals(document.getRegistrationRequestId())) {
            throw new DocumentNotFoundException("Documento não encontrado");
        }

        // verifica se a solicitação ainda está aberta
        if (request.getStatus() != RegistrationRequestStatus.UNDER_REVIEW) {
            throw new DocumentNotFoundException("O registro nao está em processo de revisão");
        }

        OwnerBuilt ownerBuilt = buildOwner(request.getTransportRegistration().getStudentId(), request.getTransportRegistration().getDriverId());

        String url = privateStorageService.generateDownloadUrl(document.getStorageKey(), document.getOriginalFilename());

        // registra log de acesso sensível
        sensitiveDataAccessLogService.record(administrator.getUserAccount().getId(), ownerBuilt, ResourceType.REGISTRATION_DOCUMENT, document.getId(), Action.DOWNLOAD, null);

        return new DocumentDownloadResponseDTO(url, Instant.now().plus(documentStorageProperties.getDownloadUrlTtl()));
    }

    // admin vê a foto pendente da solicitação
    // se nao houver verifica a atual da matrícula
    @Transactional(readOnly = true)
    public RequestPhotoResponseDTO getRequestedPhoto(UUID requestId) {
        Administrator administrator = getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());

        RegistrationRequest request = registrationRequestRepository.findById(requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Nenhum registro encontrado"));

        OwnerBuilt ownerBuilt = buildOwner(request.getTransportRegistration().getStudentId(), request.getTransportRegistration().getDriverId());

        if (request.getPendingPhotoStorageKey() == null) {
            String photoStorageKey = request.getTransportRegistration().getPhotoStorageKey();

            if (photoStorageKey != null) {
                String url = privateStorageService.generatePresignPhotoUrl(request.getTransportRegistration().getPhotoStorageKey());

                // log de acesso
                sensitiveDataAccessLogService.record(administrator.getUserAccount().getId(), ownerBuilt, ResourceType.REGISTRATION_PHOTO, request.getTransportRegistration().getId(), Action.VIEW, null);

                return new RequestPhotoResponseDTO(url, Instant.now().plus(documentStorageProperties.getDownloadUrlTtl()), false);
            }

            // se nao tiver
            throw new RegistrationPhotoNotFoundException("Nenhuma foto encontrada");
        }

        // log de acesso
        sensitiveDataAccessLogService.record(administrator.getUserAccount().getId(), ownerBuilt, ResourceType.REGISTRATION_PHOTO, request.getId(), Action.VIEW, null);

        String url = privateStorageService.generatePresignPhotoUrl(request.getPendingPhotoStorageKey());

        return new RequestPhotoResponseDTO(url, Instant.now().plus(documentStorageProperties.getDownloadUrlTtl()), true);
    }

    /*
    * conclui a análise e efetiva a matrícula, a renovação ou a alteração
    * */
    @Transactional
    public TransportRegistrationReviewDetailsDTO approve(UUID requestId) {
        Administrator administrator = getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());

        // busca com lock
        RegistrationRequest request = registrationRequestRepository
                .findByIdForUpdate(requestId).orElseThrow(() -> new RegistrationNotFoundException("Nenhuma solicitação encontrada"));

        if (request.getStatus() != RegistrationRequestStatus.UNDER_REVIEW) {
            throw new RegistrationNotUnderReviewException("O registro não está em revisão");
        }

        TransportRegistration registration = request.getTransportRegistration();

        // verifica se o status da matrícula é compatível com o tipo
        if (request.getType() == RegistrationRequestType.INITIAL && registration.getStatus() != TransportRegistrationStatus.PENDING) {
            throw new RegistrationStatusNotCompatible("A Request é inicial, mas registro não está pendente");
        } else if (request.getType() == RegistrationRequestType.RENEWAL && registration.getStatus() != TransportRegistrationStatus.ACTIVE) {
            throw new RegistrationStatusNotCompatible("A Request é para renovação, mas registro não está ativo");
        }

        CustomerTerm customerTerm = resolveTargetTerm(request);

        // caso ainda não tenha gera um número de matrícula
        String registrationNumber = registration.getRegistrationNumber() != null
                ? registration.getRegistrationNumber()
                : registrationNumberGenerator.generate(administrator.getCustomerId());

        // atualiza a matrícula
        TransportRegistration enrollmentUpdate = enrollmentUpdate(request, customerTerm, registrationNumber);

        // atualiza a solicitação
        request.setStatus(RegistrationRequestStatus.APPROVED);

        if (request.getType() == RegistrationRequestType.UPDATE) {
            request.setTargetTerm(request.getTargetTerm());
        }

        request.setTargetTerm(customerTerm);
        request.setReviewedBy(administrator.getUserAccount().getId());
        request.setReviewedAt(Instant.now());

        // grava a auditoria dos documentos e os apaga
        registrationDocumentLifecycleService.closeReviewedDocuments(request, administrator.getUserAccount().getId(), RegistrationReviewOutcome.APPROVED);

        OwnerBuilt ownerBuilt = buildOwner(enrollmentUpdate.getStudentId(), enrollmentUpdate.getDriverId());

        // evento para notificar o títular
        LocalDate endTerm = request.getTargetTerm() != null ? request.getTargetTerm().getEndTerm() : null;
        eventPublisher.publishEvent(new RegistrationApprovedEvent(request.getId(), registrationNumber, request.getCustomerId(), ownerBuilt, request.getType(), endTerm));

        // verifica se já existe uma foto pendente e se a matricula ja tem uma
        boolean hasPendingPhoto = request.getPendingPhotoStorageKey() != null;
        boolean hasRegistrationPhoto = registration.getPhotoStorageKey() != null;

        return buildRegistrationDetailsResponseDTO(request, enrollmentUpdate, hasPendingPhoto, hasRegistrationPhoto, ownerBuilt);
    }

    /*
    * 'devolve' a solicitação ao usuário com o motivo de rejeição para que ele consiga corrigir
    * */
    @Transactional
    public TransportRegistrationReviewDetailsDTO reject(UUID requestId, RejectRegistrationDTO dto) {
        Administrator administrator = getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());

        // busca com lock
        RegistrationRequest request = registrationRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Registro não encontrado"));

        if (request.getStatus() != RegistrationRequestStatus.UNDER_REVIEW) {
            throw new RegistrationNotUnderReviewException("O registro não está na etapa de revisão");
        }

        // atualiza os dados
        request.setRejectionReason(dto.rejectionReason());
        request.setStatus(RegistrationRequestStatus.REJECTED);
        request.setReviewedBy(administrator.getUserAccount().getId());
        request.setReviewedAt(Instant.now());

        // apaga e grava auditoria
        registrationDocumentLifecycleService.closeReviewedDocuments(request, administrator.getUserAccount().getId(), RegistrationReviewOutcome.REJECTED);

        // foto pendente é apagada após o commit, mas a atual nao muda
        if (request.getPendingPhotoStorageKey() != null) {
            eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(List.of(request.getPendingPhotoStorageKey())));

            request.setPendingPhotoStorageKey(null);
        }

        TransportRegistration registration = request.getTransportRegistration();

        OwnerBuilt ownerBuilt = buildOwner(registration.getStudentId(), registration.getDriverId());

        // evento para notificação dos titulares/responsáveis
        eventPublisher.publishEvent(new RegistrationRejectedEvent(request.getId(), registration.getId(), request.getCustomerId(), ownerBuilt, dto.rejectionReason()));

        // sempre será pendente a foto nova pois foi apagada. A atual, se houver, nao muda
        boolean hasPendingPhoto = false;
        boolean hasRegistrationPhoto = registration.getPhotoStorageKey() != null;

        return buildRegistrationDetailsResponseDTO(request, registration, hasPendingPhoto, hasRegistrationPhoto, ownerBuilt);
    }

    private TransportRegistration enrollmentUpdate(RegistrationRequest request, CustomerTerm resolvedCustomerTerm, String registrationNumber) {
        TransportRegistration updateRegistration = request.getTransportRegistration();

        updateRegistration.setIdentityNumber(request.getIdentityNumber());
        updateRegistration.setIdentityIssuer(request.getIdentityIssuer());
        updateRegistration.setIdentityIssuedAt(request.getIdentityIssuedAt());
        updateRegistration.setMotherName(request.getMotherName());
        updateRegistration.setFatherName(request.getFatherName());

        updateRegistration.setRegistrationNumber(registrationNumber);
        updateRegistration.setStatus(TransportRegistrationStatus.ACTIVE);

        String previousPhotoKey = updateRegistration.getPhotoStorageKey();

        updateRegistration.setPhotoStorageKey(request.getPendingPhotoStorageKey());
        request.setPendingPhotoStorageKey(null);

        if (previousPhotoKey != null) {
            eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(List.of(previousPhotoKey)));
        }

        updateRegistration.setCustomerTerm(resolvedCustomerTerm);
        updateRegistration.setDeclaredBy(request.getSubmittedBy());

        Instant now = Instant.now();

        if (updateRegistration.getFirstApprovedAt() == null) {
            updateRegistration.setFirstApprovedAt(now);
        }
        updateRegistration.setLastApprovedAt(now);

        // zera o lastReminderStage e o adminBlockWarningSentAt por conta da mudança de validade
        // no update continua o memsmo
        if (request.getType() == RegistrationRequestType.INITIAL || request.getType() == RegistrationRequestType.RENEWAL) {
            updateRegistration.setLastReminderStage(null);
            updateRegistration.setAdminBlockWarningSentAt(null);
        }

        return updateRegistration;

    }

    // responsável por validar os períodos conforme o tipo de matrícula
    private CustomerTerm resolveTargetTerm(RegistrationRequest request) {
        return switch (request.getType()) {
            case INITIAL -> customerTermRepository.findByCurrentDate(today())
                    .or(() -> customerTermRepository.findFirstByStartTermAfterOrderByStartTermAsc(today()))
                    .orElseThrow(() -> new CustomerTermAbsencePeriodException("Cadastre um período antes de continuar"));
            case RENEWAL -> // busca o período mais próximo
                    customerTermRepository.findFirstByStartTermAfterOrderByStartTermAsc(request.getTargetTerm().getEndTerm())
                            .orElseThrow(() -> new CustomerTermAbsencePeriodException("Cadastre um período antes de continuar"));
            case UPDATE -> // se for update retorna o período da própria matrícula, sem altertações
                    request.getTargetTerm();
        };
    }

    private List<TransportRegistrationReviewDetailsDTO.RegistrationReqDocuments> getDocumentsByRequest(UUID requestId) {
        // pega os documentos da request específica
        return documentRepository.findAllByRegistrationRequestId(requestId).stream()
                .map(dto -> new TransportRegistrationReviewDetailsDTO.RegistrationReqDocuments(
                        dto.getId(),
                        dto.getDocumentType(),
                        dto.getCreatedAt()
                )).toList();
    }

    private TransportRegistrationReviewDetailsDTO buildRegistrationDetailsResponseDTO(RegistrationRequest request, TransportRegistration registration, boolean hasPendingPhoto, boolean hasRegistrationPhoto, OwnerBuilt ownerBuilt) {
        return new TransportRegistrationReviewDetailsDTO(
                request.getId(),
                request.getType(),
                request.getStatus(),
                request.getSubmittedAt(),
                request.getReviewedBy(),
                request.getReviewedAt(),
                request.getRejectionReason(),

                request.getIdentityNumber(),
                request.getIdentityIssuer(),
                request.getIdentityIssuedAt(),
                request.getMotherName(),
                request.getFatherName(),
                request.getInstitutionCourseId(),
                request.getPoolOfEnrollment(),
                Set.copyOf(request.getShifts()),
                getDocumentsByRequest(request.getId()),

                hasPendingPhoto,
                hasRegistrationPhoto,

                registration.getRegistrationNumber(),
                registration.getStatus(),
                registration.getCustomerTerm() != null ? registration.getCustomerTerm().getEndTerm() : null,

                ownerName(registration),
                ownerBuilt.type()
        );
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private String ownerName(TransportRegistration registration) {
        boolean isStudent = registration.getStudentId() != null;
        if (isStudent) {
            return studentRepository.findById(registration.getStudentId())
                    .map(student -> Stream.of(student.getName(), student.getLastName())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(" "))).orElse(null);


        } else {
            return driverRepository.findById(registration.getDriverId())
                    .map(driver -> Stream.of(driver.getName(), driver.getLastName())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(" "))).orElse(null);

        }
    }

    private OwnerBuilt buildOwner(UUID studentId, UUID driverId) {
        if (studentId != null) {
            return new OwnerBuilt(OwnerType.STUDENT, studentId);
        }

        return new OwnerBuilt(OwnerType.DRIVER, driverId);
    }

    private Administrator getAuthenticatedActiveAdministrator(String email) {
        Administrator administrator = administratorRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Admin não encontrado com o email: " + email));

        if (administrator.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Administrador inativo no sistema");
        }

        UUID admCustomerId = administrator.getCustomerId();
        UUID customerId = TenantContext.getCurrentTenant();

        if (!admCustomerId.equals(customerId)) {
            throw new CustomerMismatchException("Customers não são iguais, nao é possível prosseguir");
        }

        return administrator;
    }
}
