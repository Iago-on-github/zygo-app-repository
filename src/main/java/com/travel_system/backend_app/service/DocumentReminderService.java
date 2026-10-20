package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.DocumentExpiredEvent;
import com.travel_system.backend_app.events.documents.DocumentExpiringEvent;
import com.travel_system.backend_app.events.documents.HealthDeclarationOutdatedEvent;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.HealthDeclarationStatus;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.repository.*;
import org.flywaydb.core.internal.jdbc.TransactionalExecutionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

/*
* envia lembretes períodicos para usuários cujo documentos estão perto de vencer
* */

@Service
public class DocumentReminderService {
    private final Logger log = LoggerFactory.getLogger(DocumentReminderService.class);

    private final UserAccountRepository userAccountRepository;
    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final CustomerTermRepository customerTermRepository;
    private final HealthDeclarationRepository healthDeclarationRepository;

    private static final List<Integer> REMINDER_STAGES = List.of(1, 7, 30);
    private static final int MAX_REMINDER_DAYS = 30;

    private static final List<DocumentStatus> ACTIVE_STATUSES = List.of(DocumentStatus.PENDING_REVIEW, DocumentStatus.APPROVED);

    private static final String ZONE = "America/Sao_Paulo";

    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentReminderService(UserAccountRepository userAccountRepository, DocumentRepository documentRepository, CustomerRepository customerRepository, CustomerTermRepository customerTermRepository, HealthDeclarationRepository healthDeclarationRepository, TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
        this.customerTermRepository = customerTermRepository;
        this.healthDeclarationRepository = healthDeclarationRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    /*
    * envia lembretes que são processados via job para docs que estão proximos de expirar
    * */
    @Transactional
    public void sendExpiringReminders() {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));

        List<Document> documents = documentRepository.findExpiringForReminder(ACTIVE_STATUSES, today, today.plusDays(MAX_REMINDER_DAYS));

        for (Document document : documents) {
            long daysLeft = ChronoUnit.DAYS.between(today, document.getValidUntil());

            // menor estágio que ainda cobre os dias restantes
            Integer dueStage = REMINDER_STAGES.stream().filter(stage -> daysLeft <= stage).findFirst().orElse(null);

            if (dueStage == null) {
                continue;
            }

            // já enviado esse estágio (ou um mais urgente): nada a fazer
            Integer lastStage = document.getLastReminderStage();
            if (lastStage != null && lastStage <= dueStage) {
                continue;
            }

            document.setLastReminderStage(dueStage);

            UserAccount user = userAccountRepository.findUserByEmail(getAuthenticatedUserEmail());

            if (user == null) {
                throw new AccessDeniedException("User não existe");
            }

            // publica evento que só envia após o commit
            eventPublisher.publishEvent(new DocumentExpiringEvent(document.getId(), document.getCustomerId(), user.getId(), document.getValidUntil(), daysLeft));
        }
    }

    /*
    * docs que já expiraram: envia lembrete diário
    * */
    @Transactional
    public void sendExpireReminders() {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));

        List<Document> expireForReminder = documentRepository.findExpireForReminder(ACTIVE_STATUSES, today);

        for (Document expireDoc : expireForReminder) {

            expireDoc.setDocumentStatus(DocumentStatus.EXPIRED);

            // publica evento p/ notificação
            eventPublisher.publishEvent(new DocumentExpiredEvent(expireDoc.getId(), expireDoc.getCustomerId(), ownerOf(expireDoc)));
        }
    }

    /*
     * Executado diariamente pelo job.
     * Para cada Customer com declaração de saúde habilitada, marca como OUTDATED as declarações
     * que não pertencem ao período letivo vigente e notifica o titular/responsáveis.
     * Cada Customer roda na sua própria transação: a falha de um não afeta os demais.
     */
    public void markOutdatedDeclarations() {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        List<Customer> customers = customerRepository.findAllByHealthDeclarationRequestEnabledTrue();

        for (Customer customer : customers) {
            UUID customerId = customer.getId();

            try {
                TenantContext.setCurrentTenant(customerId);
                transactionTemplate.executeWithoutResult(status -> markOutdatedForCustomer(customerId, today));
            } catch (Exception e) {
                log.error("Falha ao marcar declarações desatualizadas do Customer {}", customerId, e);
            } finally {
                TenantContext.removeCurrentTenant();
            }
        }
    }

    private void markOutdatedForCustomer(UUID customerId, LocalDate today) {
        // sem período vigente (férias, sem calendário cadastrado) -> nada a fazer
        Optional<CustomerTerm> currentTerm = customerTermRepository.findCurrentByCustomerId(customerId, today);
        if (currentTerm.isEmpty()) {
            return;
        }

        List<HealthDeclaration> declarations = healthDeclarationRepository.findOutsideCurrentTerm(customerId, HealthDeclarationStatus.CURRENT, currentTerm.get().getId());

        for (HealthDeclaration declaration : declarations) {
            // a mudança de status garante que cada declaração é notificada uma única vez
            declaration.setHealthDeclarationStatus(HealthDeclarationStatus.OUTDATED);

            eventPublisher.publishEvent(new HealthDeclarationOutdatedEvent(declaration.getId(), customerId, toOwner(declaration)));
        }
    }

    private HealthOwner toOwner(HealthDeclaration declaration) {
        return declaration.getStudentId() != null
                ? new HealthOwner(OwnerType.STUDENT, declaration.getStudentId())
                : new HealthOwner(OwnerType.DRIVER, declaration.getDriverId());
    }

    // monta o dono a partir do documento
    private HealthOwner ownerOf(Document document) {
        return document.getStudentId() != null ? new HealthOwner(OwnerType.STUDENT, document.getStudentId()) : new HealthOwner(OwnerType.DRIVER, document.getDriverId());
    }


}
