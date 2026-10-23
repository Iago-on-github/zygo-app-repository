package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.HealthConsentProperties;
import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.exceptions.OutdatedConsentTermException;
import com.travel_system.backend_app.exceptions.StudentNotAssociatedWithResponsibleAdultException;
import com.travel_system.backend_app.interfaces.mappers.response.HealthConsentResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.HealthConsentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.response.HealthConsentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.HealthConsentTermDTO;
import com.travel_system.backend_app.model.enums.DocumentCategory;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

/*
* Responsável por coletar a autorização do titular (ou dos responsável no caso de alunos menores de idade)
* para o tratamento dos dados de saúde
* */

@Service
public class HealthConsentService {

    private final HealthConsentProperties consentProperties;
    private final UserProfileResolverService userProfileResolverService;

    private final CustomerRepository customerRepository;
    private final ResponsibleAdultRepository responsibleAdultRepository;
    private final StudentRepository studentRepository;
    private final UserAccountRepository userAccountRepository;
    private final HealthConsentRepository healthConsentRepository;
    private final DocumentRepository documentRepository;
    private final HealthDeclarationRepository healthDeclarationRepository;

    private final HealthConsentResponseMapper healthConsentResponseMapper;

    private final ApplicationEventPublisher eventPublisher;

    public HealthConsentService(HealthConsentProperties consentProperties, UserProfileResolverService userProfileResolverService, CustomerRepository customerRepository, ResponsibleAdultRepository responsibleAdultRepository, StudentRepository studentRepository, UserAccountRepository userAccountRepository, HealthConsentRepository healthConsentRepository, DocumentRepository documentRepository, HealthDeclarationRepository healthDeclarationRepository, HealthConsentResponseMapper healthConsentResponseMapper, ApplicationEventPublisher eventPublisher) {
        this.consentProperties = consentProperties;
        this.userProfileResolverService = userProfileResolverService;
        this.customerRepository = customerRepository;
        this.responsibleAdultRepository = responsibleAdultRepository;
        this.studentRepository = studentRepository;
        this.userAccountRepository = userAccountRepository;
        this.healthConsentRepository = healthConsentRepository;
        this.documentRepository = documentRepository;
        this.healthDeclarationRepository = healthDeclarationRepository;
        this.healthConsentResponseMapper = healthConsentResponseMapper;
        this.eventPublisher = eventPublisher;
    }

    public HealthConsentTermDTO getCurrentTerm() {
        return new HealthConsentTermDTO(
                consentProperties.getTermVersion(),
                consentProperties.getPurpose(),
                consentProperties.getTermUrl()
        );
    }

    @Transactional
    public HealthConsentResponseDTO registerMyConsent(HealthConsentRequestDTO request) {
        System.out.println("consent: " + userProfileResolverService.resolveSelf().id() );
        return register(userProfileResolverService.resolveSelf(), request);
    }

    @Transactional
    public HealthConsentResponseDTO registerMinorStudentConsent(UUID studentId, HealthConsentRequestDTO request) {
        return register(userProfileResolverService.resolveForResponsible(studentId), request);
    }

    @Transactional(readOnly = true)
    public HealthConsentResponseDTO getMyConsent() {
        HealthOwner healthOwner = userProfileResolverService.resolveSelf();

        return healthConsentResponseMapper.toDTO(healthConsentRepository.findByOwnerId(healthOwner.id()).orElseThrow(() -> new EntityNotFoundException("Entidade não encontrada")));
    }

    @Transactional(readOnly = true)
    public HealthConsentResponseDTO getMinorStudentConsent(UUID studentId) {
        HealthOwner healthOwner = userProfileResolverService.resolveForResponsible(studentId);

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(getAuthenticatedUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Responsável não encontrado"));

        // verifica se o estudante informado faz parte do seu ciclo de estudantes
        boolean isStudentMatch = responsibleAdult.getStudents().stream().anyMatch(match -> match.getId().equals(studentId));

        if (!isStudentMatch) {
            throw new StudentNotAssociatedWithResponsibleAdultException("O estudante informado não está associado a você");
        }

        return healthConsentResponseMapper.toDTO(healthConsentRepository.findByOwnerId(healthOwner.id()).orElseThrow(() -> new EntityNotFoundException("Entidade não encontrada")));
    }

    @Transactional
    public void revokeMyConsent() {
        revoke(userProfileResolverService.resolveSelf());
    }

    @Transactional
    public void revokeMinorStudentConsent(UUID studentId) {
        revoke(userProfileResolverService.resolveForResponsible(studentId));
    }

    /*
     * Revogar o consentimento apaga todos os dados de saúde do dono:
     * documentos (linhas + arquivos), declaração e condições.
     * O consentimento em si é mantido com revokedAt, como prova do histórico.
     */
    private void revoke(HealthOwner owner) {
        HealthConsent consent = healthConsentRepository.findActiveByOwnerId(owner.id())
                .orElseThrow(() -> new EntityNotFoundException("Nenhum consentimento ativo encontrado"));

        // documentos de saúde (somente HEALTH, matrícula não é afetada)
        List<Document> documents = documentRepository.findAllByOwnerIdAndCategory(owner.id(), DocumentCategory.HEALTH);
        List<String> storageKeys = documents.stream().map(Document::getStorageKey).toList();

        documentRepository.deleteAll(documents);
        // flush antes de apagar a declaração, por causa da FK document -> health_condition
        documentRepository.flush();

        // declaração (as condições saem pelo orphanRemoval)
        healthDeclarationRepository.findByOwnerId(owner.id()).ifPresent(healthDeclarationRepository::delete);

        consent.setRevokedAt(Instant.now());

        // arquivos apagados do storage somente após o commit (listener AFTER_COMMIT)
        if (!storageKeys.isEmpty()) {
            eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(storageKeys));
        }
    }

    private HealthConsentResponseDTO register(HealthOwner owner, HealthConsentRequestDTO request) {
        // o app precisa ter exibido exatamente a versão vigente
        if (!consentProperties.getTermVersion().equals(request.termVersion())) {
            throw new OutdatedConsentTermException();
        }

        Optional<HealthConsent> existing = healthConsentRepository.findActiveByOwnerId(owner.id());

        if (existing.isPresent()) {
            HealthConsent active = existing.get();

            // já aceitou a versão atual -> idempotente
            if (active.getTermVersion().equals(consentProperties.getTermVersion())) {
                return healthConsentResponseMapper.toDTO(active);
            }

            /*
             * Aceite de uma versão nova: encerra o anterior SEM apagar os dados de saúde.
             * saveAndFlush antes do insert, por causa do índice único parcial (revoked_at IS NULL).
             */
            active.setRevokedAt(Instant.now());
            healthConsentRepository.saveAndFlush(active);
        }

        HealthConsent consent = new HealthConsent();

        if (owner.type() == OwnerType.STUDENT) {
            consent.setStudentId(owner.id());
        } else {
            consent.setDriverId(owner.id());
        }

        consent.setConsentedBy(getAuthenticatedUserAccountId());
        consent.setConsentedAt(Instant.now());
        consent.setTermVersion(consentProperties.getTermVersion());
        consent.setPurpose(consentProperties.getPurpose());
        consent.assignCustomer(getCurrentCustomer().getId());

        return healthConsentResponseMapper.toDTO(healthConsentRepository.save(consent));
    }

    public boolean hasActiveConsent(UUID ownerId) {
        return healthConsentRepository.findActiveByOwnerId(ownerId).isPresent();
    }

    private UUID getAuthenticatedUserAccountId() {
        String email = getAuthenticatedUserEmail();

        UserAccount user = userAccountRepository.findUserByEmail(email);

        if (user == null) {
            throw new AccessDeniedException("Acesso negado, user não existe.");
        }

        return user.getId();
    }

    // customer do usuário logado (via TenantContext)
    private Customer getCurrentCustomer() {
        return customerRepository.findById(getCurrentTenant())
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));
    }

}
