package com.travel_system.backend_app.service;

/*
* Upload do documento e da foto: valida a solicitação, monta a chave e define os callbacks
* */

import com.travel_system.backend_app.exceptions.HasTransportRegistrationException;
import com.travel_system.backend_app.exceptions.TransportRegistrationNotFoundException;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.dtos.request.RegistrationOwner;
import com.travel_system.backend_app.model.dtos.request.RegistrationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RegistrationChecklistItemDTO;
import com.travel_system.backend_app.model.dtos.response.RegistrationRejectionDTO;
import com.travel_system.backend_app.model.dtos.response.RegistrationRequestSummaryDTO;
import com.travel_system.backend_app.model.dtos.response.TransportRegistrationResponseDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.RegistrationRequestRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.TransportRegistrationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class TransportRegistrationService {

    private final TransportRegistrationRepository transportRegistrationRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;

    private final S3PrivateStorageService privateStorageService;
    private final UserProfileResolverService userProfileResolverService;

    // status em que a solicitação ainda está aberta
    private static final Set<RegistrationRequestStatus> OPEN_REQUEST_STATUSES = EnumSet.of(RegistrationRequestStatus.DRAFT, RegistrationRequestStatus.UNDER_REVIEW);

    public TransportRegistrationService(TransportRegistrationRepository transportRegistrationRepository, RegistrationRequestRepository registrationRequestRepository, DocumentRepository documentRepository, StudentRepository studentRepository, S3PrivateStorageService privateStorageService, UserProfileResolverService userProfileResolverService) {
        this.transportRegistrationRepository = transportRegistrationRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.privateStorageService = privateStorageService;
        this.userProfileResolverService = userProfileResolverService;
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

    /*
    * responsável por guardar o que o user ja digitou no formulário de matricula, mas nao envia ao admin
    *
    * */
    private TransportRegistrationResponseDTO saveDraft(OwnerBuilt ownerBuilt) {
        // busca a matrícula atual + a solicitação em aberto com uso de lock
        transportRegistrationRepository.findCurrentByOwnerId(ownerBuilt.id(), TransportRegistrationStatus.CLOSED)
                .orElseThrow(() -> new TransportRegistrationNotFoundException("Nenhum registro encontrado"));

        registrationRequestRepository.findOpenByRegistrationIdWithLock();
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
}
