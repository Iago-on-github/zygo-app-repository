package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.*;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * responsável por gerenciar a visualização e o tempo de vida dos documentos sensíveis de registro no sistema
 * os documentos são requisitados mas, após serem processados (manualmente) devem ser completamente apagados do sistema
 * */

/*
 * ciclo de vida dos documentos de matrícula para a categoria ENROLLMENT
 * - os documentos existem apenas enquanto a solicitação está aberta (DRAFT / UNDER_REVIEW)
 * - revisão concluída (aprovação ou rejeição) grava a auditoria e apaga os documentos
 * - cancelamento (usuário ou limpeza de rascunhos) apaga os documentos sem auditoria
 * - reenvio do mesmo tipo na mesma solicitação o anterior é apagado na hora (não há motivo para guardá-lo)
 * - os arquivos são removidos do storage somente após o commit (listener AFTER_COMMIT)
 * */

@Component
public class RegistrationDocumentLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationDocumentLifecycleService.class);

    private static final Set<DocumentStatus> ACTIVE_STATUSES = EnumSet.of(DocumentStatus.PENDING_REVIEW, DocumentStatus.APPROVED);

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final RegistrationDocumentAuditRepository registrationDocumentAuditRepository;
    private final TransportRegistrationRepository transportRegistrationRepository;
    private final CustomerTermRepository customerTermRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final TransactionTemplate transactionTemplate;

    private static final List<RegistrationRequestStatus> OPEN_REQUEST_STATUSES = List.of(RegistrationRequestStatus.DRAFT, RegistrationRequestStatus.UNDER_REVIEW);

    // rascunhos sem atividade por mais tempo que isso são cancelados
    private static final Duration ABANDONED_DRAFT_AFTER = Duration.ofDays(30);

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    public RegistrationDocumentLifecycleService(DocumentRepository documentRepository, CustomerRepository customerRepository, RegistrationRequestRepository registrationRequestRepository, RegistrationDocumentAuditRepository registrationDocumentAuditRepository, TransportRegistrationRepository transportRegistrationRepository, CustomerTermRepository customerTermRepository, ApplicationEventPublisher eventPublisher, TransactionTemplate transactionTemplate) {
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.registrationDocumentAuditRepository = registrationDocumentAuditRepository;
        this.transportRegistrationRepository = transportRegistrationRepository;
        this.customerTermRepository = customerTermRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /*
     * chamado na aprovação e na rejeição da solicitação e
     * - grava um registro de auditoria por documento (sem nenhum dado do conteúdo)
     * - apaga os documentos (linhas + arquivos)
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeReviewedDocuments(RegistrationRequest request, UUID reviewerAccountId, RegistrationReviewOutcome outcome) {
        List<Document> documents = documentRepository.findAllByRegistrationRequestId(request.getId());

        if (documents.isEmpty()) {
            return;
        }

        Instant reviewedAt = Instant.now();

        // auditoria
        List<RegistrationDocumentAudit> audits = documents.stream().map(document -> toAudit(document, reviewerAccountId, outcome, reviewedAt))
                .toList();

        registrationDocumentAuditRepository.saveAll(audits);

        deleteDocuments(documents);
    }

    /*
     * chamado no cancelamento da solicitação (pelo usuário ou pelo job de rascunhos abandonados)
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardRequestDocuments(UUID registrationRequestId) {
        deleteDocuments(documentRepository.findAllByRegistrationRequestId(registrationRequestId));
    }

    /*
     * chamado no upload, antes de inserir o novo documento
     * - apaga o documento ativo do mesmo tipo na mesma solicitação
     * - o flush garante que a exclusão chegue ao banco antes do insert (índice único parcial)
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardPreviousOfType(UUID registrationRequestId, DocumentType documentType) {
        documentRepository.findActiveByRegistrationRequestIdAndDocumentTypeAndDocumentStatusIn(registrationRequestId, documentType, ACTIVE_STATUSES)
                .ifPresent(previous -> deleteDocuments(List.of(previous)));
    }

    /*
     * cancela uma solicitação em DRAFT
     * - usado pelo cancelamento do usuário e pelo job de rascunhos abandonados
     * - apaga documentos (sem auditoria) e a foto pendente; mantém a solicitação como histórico (CANCELLED)
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelRequest(RegistrationRequest request) {
        discardRequestDocuments(request.getId());

        String pendingPhotoKey = request.getPendingPhotoStorageKey();

        if (pendingPhotoKey != null) {
            request.setPendingPhotoStorageKey(null);
            eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(List.of(pendingPhotoKey)));
        }

        request.setStatus(RegistrationRequestStatus.CANCELLED);
    }

    /*
     * cancela renovações/alterações abandonadas em DRAFT
     * - libera a matrícula para abrir uma nova solicitação no futuro
     * - apaga documentos e foto pendente (dados sensíveis não ficam parados no storage)
     * */
    public void cancelAbandonedDrafts() {
        Instant cutoff = Instant.now().minus(ABANDONED_DRAFT_AFTER);

        for (UUID customerId : customerRepository.findAllIds()) {
            try {
                TenantContext.setCurrentTenant(customerId);

                transactionTemplate.executeWithoutResult(status -> cancelAbandonedDraftsForCustomer(customerId, cutoff));

            } catch (Exception e) {
                log.error("[cancelAbandonedDrafts] Falha ao processar o Customer {}", customerId, e);
            } finally {
                // limpa o customerId para não vazar para o próximo Customer
                TenantContext.removeCurrentTenant();
            }
        }
    }

    /*
     * percorre os Customers e processa as matrículas ativas
     * - fase 1: leitura (uma transação por Customer) → fotografias
     * - fase 2: decisão + ação (uma transação por matrícula: o erro de uma não afeta as demais)
     * - fase 3: alerta ao admin quando o próximo período não foi cadastrado
     * */
    public void processRegistrations() {
        LocalDate today = LocalDate.now(ZONE_ID);

        for (UUID customerId : customerRepository.findAllIds()) {
            try {
                TenantContext.setCurrentTenant(customerId);

                processCustomer(customerId, today);

            } catch (Exception e) {
                log.error("[processRegistrations] Falha ao processar o Customer {}", customerId, e);
            } finally {
                TenantContext.removeCurrentTenant();
            }
        }
    }

    private void processCustomer(UUID customerId, LocalDate today) {
        // leitura
        CustomerLifecycleData data = transactionTemplate.execute(status -> loadCustomerData(customerId, today));

        if (data == null) {
            return;
        }

        // decisão + ação, cada matrícula na própria transação
        for (RegistrationSnapshot snapshot : data.snapshots()) {
            RegistrationLifecycleAction action = decideAction(snapshot, data.settings(), today);

            if (action == RegistrationLifecycleAction.NONE) {
                continue;
            }

            try {
                transactionTemplate.executeWithoutResult(status -> applyAction(snapshot, action, data.settings(), customerId));
            } catch (Exception e) {
                log.error("[processRegistrations] Falha ao aplicar {} na matrícula {}", action, snapshot.registrationId(), e);
            }
        }

        // matrículas que não conseguem renovar porque o próximo período não existe
        if (data.nextTermMissingCount() > 0) {
            transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new NextTermMissingEvent(customerId, data.nextTermMissingCount())));
        }
    }

    /*
     * carrega configurações, matrículas ativas e solicitações abertas, e monta o dto de snapshot
     * */
    private CustomerLifecycleData loadCustomerData(UUID customerId, LocalDate today) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        RegistrationSettings settings = new RegistrationSettings(
                customer.getRegistrationGracePeriodDays(),
                customer.getRegistrationRenewalWindowDays(),
                customer.getRegistrationBlockWarningDays());

        // apenas as matrículas ativas, já com o período carregado
        List<TransportRegistration> registrations = transportRegistrationRepository.findActiveWithTermByCustomerId(customerId, TransportRegistrationStatus.ACTIVE);

        if (registrations.isEmpty()) {
            return new CustomerLifecycleData(settings, List.of(), 0);
        }

        List<UUID> registrationIds = registrations.stream().map(TransportRegistration::getId).toList();

        // solicitações abertas indexadas pelo id da matrícula (no máximo uma por matrícula)
        Map<UUID, RegistrationRequest> openRequests = registrationRequestRepository.findOpenByRegistrationIds(registrationIds, OPEN_REQUEST_STATUSES).stream()
                .collect(Collectors.toMap(request -> request.getTransportRegistration().getId(), Function.identity()));

        // uma consulta por data de fim distinta (muitas matrículas compartilham o mesmo período)
        Map<LocalDate, Boolean> nextTermByTermEnd = new HashMap<>();

        List<RegistrationSnapshot> snapshots = new ArrayList<>();
        int nextTermMissingCount = 0;

        for (TransportRegistration registration : registrations) {
            LocalDate termEnd = registration.getCustomerTerm().getEndTerm();

            boolean hasNextTerm = nextTermByTermEnd.computeIfAbsent(termEnd,
                    end -> customerTermRepository.existsByCustomerIdAndStartTermAfter(customerId, end));

            // janela aberta e sem próximo período o titular não consegue renovar (notificação ao admin)
            LocalDate windowStart = termEnd.minusDays(settings.renewalWindowDays());

            if (!hasNextTerm && !today.isBefore(windowStart)) {
                nextTermMissingCount++;
            }

            RegistrationRequest openRequest = openRequests.get(registration.getId());

            snapshots.add(new RegistrationSnapshot(
                    registration.getId(),
                    registration.getLastReminderStage(),
                    registration.getAdminBlockWarningSentAt() != null,
                    termEnd,
                    openRequest != null ? openRequest.getType() : null,
                    openRequest != null ? openRequest.getStatus() : null,
                    hasNextTerm
            ));
        }

        return new CustomerLifecycleData(settings, snapshots, nextTermMissingCount);
    }

    /*
     * decide a ação da matrícula (não acessa o banco)
     * - avaliada da mais urgente para a menos urgente; devolve a primeira que se aplicar
     * - se renovação em análise o usuário já fez a parte dele
     * - se sem próximo período o usuário não consegue renovar e apenas o admin é alertado posteriormente
     * */
    private RegistrationLifecycleAction decideAction(RegistrationSnapshot snapshot, RegistrationSettings settings, LocalDate today) {
        if (snapshot.openRequestStatus() == RegistrationRequestStatus.UNDER_REVIEW || !snapshot.hasNextTerm()) {
            return RegistrationLifecycleAction.NONE;
        }

        LocalDate termEnd = snapshot.termEnd();
        // configs customer
        LocalDate windowStart = termEnd.minusDays(settings.renewalWindowDays());
        LocalDate graceEnd = termEnd.plusDays(settings.gracePeriodDays());
        LocalDate blockWarningStart = graceEnd.minusDays(settings.blockWarningDays());

        int stage = snapshot.reminderStage();

        // se o período de carência estiver esgotado
        if (today.isAfter(graceEnd)) {
            return RegistrationLifecycleAction.CLOSE;
        }

        // bloqueio se aproximando (titular e admin uma vez)
        if (!today.isBefore(blockWarningStart) && (stage < RegistrationReminderStage.BLOCK_WARNING.getValue() || !snapshot.adminBlockWarningSent())) {
            return RegistrationLifecycleAction.BLOCK_WARNING;
        }

        // se o período é encerrado começa o período de carência
        if (today.isAfter(termEnd)) {
            return stage < RegistrationReminderStage.GRACE_STARTED.getValue()
                    ? RegistrationLifecycleAction.GRACE_STARTED
                    : RegistrationLifecycleAction.NONE;
        }

        // se nenhuma acima se aplica o período ainda vigente
        // avisos só com a janela de renovação aberta
        if (today.isBefore(windowStart)) {
            return RegistrationLifecycleAction.NONE;
        }

        // lembretes de vencimento (<= não perde o aviso se o job não rodar em algum dia)
        long daysLeft = ChronoUnit.DAYS.between(today, termEnd);

        if (daysLeft <= 1 && stage < RegistrationReminderStage.DAYS_1.getValue()) {
            return RegistrationLifecycleAction.EXPIRING_1;
        }
        if (daysLeft <= 7 && stage < RegistrationReminderStage.DAYS_7.getValue()) {
            return RegistrationLifecycleAction.EXPIRING_7;
        }
        if (daysLeft <= 30 && stage < RegistrationReminderStage.DAYS_30.getValue()) {
            return RegistrationLifecycleAction.EXPIRING_30;
        }

        // janela aberta aplicável apenas se o usuário ainda não começou a renovação
        if (stage < RegistrationReminderStage.WINDOW_OPENED.getValue() && snapshot.openRequestType() == null) {
            return RegistrationLifecycleAction.WINDOW_OPENED;
        }

        return RegistrationLifecycleAction.NONE;
    }

    /*
     * aplica a ação dentro da transação da matrícula
     * - busca com lock e confere novamente se o usuário pode ter agido entre a leitura e agora
     * */
    private void applyAction(RegistrationSnapshot snapshot, RegistrationLifecycleAction action, RegistrationSettings settings, UUID customerId) {
        TransportRegistration registration = transportRegistrationRepository.findByIdForUpdate(snapshot.registrationId()).orElse(null);

        if (registration == null || registration.getStatus() != TransportRegistrationStatus.ACTIVE) {
            return;
        }

        RegistrationRequest openRequest = registrationRequestRepository.findOpenByRegistrationIdForUpdate(registration.getId(), OPEN_REQUEST_STATUSES)
                .orElse(null);

        // se renovação enviada entre a leitura e o lock
        if (openRequest != null && openRequest.getStatus() == RegistrationRequestStatus.UNDER_REVIEW) {
            return;
        }

        OwnerBuilt owner = buildOwner(registration);
        LocalDate termEnd = registration.getCustomerTerm().getEndTerm();
        LocalDate graceEnd = termEnd.plusDays(settings.gracePeriodDays());
        int currentStage = RegistrationReminderStage.valueOf(String.valueOf(registration.getLastReminderStage())).getValue();

        switch (action) {
            case CLOSE -> {
                // renovação/alteração começada e abandonada apaga documentos e foto pendente
                if (openRequest != null) {
                    cancelRequest(openRequest);
                }

                registration.setStatus(TransportRegistrationStatus.CLOSED);
                registration.setClosedReason(RegistrationClosedReason.NOT_RENEWED);
                registration.setClosedAt(Instant.now());

                // evento para notificação
                eventPublisher.publishEvent(new RegistrationClosedEvent(registration.getId(), customerId, owner, registration.getRegistrationNumber()));
            }

            case BLOCK_WARNING -> {
                boolean notifyOwner = currentStage < RegistrationReminderStage.BLOCK_WARNING.getValue();
                boolean notifyAdmin = registration.getAdminBlockWarningSentAt() == null;

                if (!notifyOwner && !notifyAdmin) {
                    return;
                }

                if (notifyOwner) {
                    registration.setLastReminderStage(RegistrationReminderStage.BLOCK_WARNING.getValue());
                }
                if (notifyAdmin) {
                    registration.setAdminBlockWarningSentAt(Instant.now());
                }

                // envia notificação para o admin apenas uma vez quando é compatível com o bloqueio
                // envia notificação para o dono sempre que o estagio atual for menor que o período de aviso de bloqueio
                eventPublisher.publishEvent(new RegistrationBlockWarningEvent(registration.getId(), customerId, owner, registration.getRegistrationNumber(), graceEnd, notifyOwner, notifyAdmin));
            }

            // avisos ao owner grava o estágio e publica o evento correspondente
            default -> {
                int targetStage = action.getStage().getValue();

                if (currentStage >= targetStage) {
                    return;
                }

                registration.setLastReminderStage(targetStage);

                /*
                * publica eventos com base na ação p/ notificação do dono
                * */
                eventPublisher.publishEvent(
                        switch (action) {
                            case GRACE_STARTED -> new RegistrationGraceStartedEvent(registration.getId(), customerId, owner, termEnd, graceEnd);
                            case WINDOW_OPENED -> new RegistrationRenewalWindowOpenedEvent(registration.getId(), customerId, owner, termEnd);

                            default -> new RegistrationExpiringEvent(registration.getId(), customerId, owner, termEnd, ChronoUnit.DAYS.between(LocalDate.now(ZONE_ID), termEnd), openRequest != null);
                    }
                );
            }
        }
    }


    private void cancelAbandonedDraftsForCustomer(UUID customerId, Instant cutoff) {
        List<UUID> abandonedIds = registrationRequestRepository.findAbandonedDraftIds(
                customerId, RegistrationRequestStatus.DRAFT, RegistrationRequestType.INITIAL, cutoff);

        for (UUID requestId : abandonedIds) {
            // busca com lock
            RegistrationRequest request = registrationRequestRepository.findByIdForUpdate(requestId).orElse(null);

            // confere novamente para confirmar que não foi enviada ou cancelada entre a busca e o lock
            if (request == null || request.getStatus() != RegistrationRequestStatus.DRAFT || request.getType() == RegistrationRequestType.INITIAL) {
                continue;
            }

            cancelRequest(request);
        }

        if (!abandonedIds.isEmpty()) {
            log.info("[cancelAbandonedDrafts] Customer {}: {} rascunho(s) abandonado(s) cancelado(s)", customerId, abandonedIds.size());
        }
    }

    // apaga as linhas agora e os arquivos após o commit
    private void deleteDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return;
        }

        List<String> storageKeys = documents.stream().map(Document::getStorageKey).toList();

        documentRepository.deleteAll(documents);
        documentRepository.flush();

        eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(storageKeys));
    }

    private OwnerBuilt buildOwner(TransportRegistration registration) {
        if (registration.getStudentId() != null) {
            return new OwnerBuilt(OwnerType.STUDENT, registration.getStudentId());
        }

        return new OwnerBuilt(OwnerType.DRIVER, registration.getDriverId());
    }

    // registro mínimo: tipo, hash, quem enviou, quem conferiu e quando
    private RegistrationDocumentAudit toAudit(Document document, UUID reviewerAccountId, RegistrationReviewOutcome outcome, Instant reviewedAt) {
        RegistrationDocumentAudit audit = new RegistrationDocumentAudit();

        audit.assignCustomer(document.getCustomerId());
        audit.setRegistrationRequestId(document.getRegistrationRequestId());
        audit.setDocumentType(document.getDocumentType());
        audit.setSha256(document.getSha256());
        audit.setUploadedBy(document.getUploadedBy());
        audit.setUploadedAt(document.getCreatedAt());
        audit.setReviewedBy(reviewerAccountId);
        audit.setReviewedAt(reviewedAt);
        audit.setOutcome(outcome);

        return audit;
    }

    // configurações do Customer usadas no cálculo das datas
    private record RegistrationSettings(int gracePeriodDays, int renewalWindowDays, int blockWarningDays) { }

    /*
     * "fotografia" de uma matrícula que representa tudo o que a decisão precisa, sem entidades
     * (as entidades deixam de ser gerenciadas quando a transação de leitura termina)
     * */
    private record RegistrationSnapshot(
            UUID registrationId,
            int reminderStage,
            boolean adminBlockWarningSent,
            LocalDate termEnd,
            RegistrationRequestType openRequestType,       // null se não houver solicitação aberta
            RegistrationRequestStatus openRequestStatus,   // null se não houver solicitação aberta
            boolean hasNextTerm
    ) { }

    // resultado da leitura de um Customer
    private record CustomerLifecycleData(RegistrationSettings settings, List<RegistrationSnapshot> snapshots, int nextTermMissingCount) { }
}

