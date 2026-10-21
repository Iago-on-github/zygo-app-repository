package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.HealthPendingItemsReminderEvent;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.dtos.response.HealthPendingItemDTO;
import com.travel_system.backend_app.model.dtos.response.HealthPendingItemsResponseDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

/*
* Responsável por realizar cálculos do que falta para cada aluno ou motorista para alimentar os avisos fortes do app
* e o admin ter mais visibilidade sobre esses dados
*
* o próprio admin pode escolher se serão exigidos documentos de declração de saúde e de matrícula
* */

@Service
public class HealthPendingItemsService {
    private final Logger log = LoggerFactory.getLogger(HealthPendingItemsService.class);

    private final CustomerRepository customerRepository;
    private final CustomerTermRepository customerTermRepository;
    private final HealthDeclarationRepository healthDeclarationRepository;
    private final DocumentRepository documentRepository;
    private final HealthConsentRepository healthConsentRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;

    private final HealthConsentService healthConsentService;
    private final UserProfileResolverService userProfileResolverService;

    private final ApplicationEventPublisher eventPublisher;

    private final TransactionTemplate readOnlyTransactionTemplate;

    // status em que um documento ainda pode comprovar a condição
    private static final Set<DocumentStatus> ACTIVE_STATUSES = EnumSet.of(DocumentStatus.PENDING_REVIEW, DocumentStatus.APPROVED);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    public HealthPendingItemsService(CustomerRepository customerRepository, CustomerTermRepository customerTermRepository, PlatformTransactionManager transactionManager, HealthDeclarationRepository healthDeclarationRepository, DocumentRepository documentRepository, HealthConsentRepository healthConsentRepository, StudentRepository studentRepository, DriverRepository driverRepository, HealthConsentService healthConsentService, UserProfileResolverService userProfileResolverService, ApplicationEventPublisher eventPublisher, TransactionTemplate readOnlyTransactionTemplate) {
        this.customerRepository = customerRepository;
        this.customerTermRepository = customerTermRepository;
        this.healthDeclarationRepository = healthDeclarationRepository;
        this.documentRepository = documentRepository;
        this.healthConsentRepository = healthConsentRepository;
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.healthConsentService = healthConsentService;
        this.userProfileResolverService = userProfileResolverService;
        this.eventPublisher = eventPublisher;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public HealthPendingItemsResponseDTO getMyPendingItems() {
        HealthOwner healthOwner = userProfileResolverService.resolveSelf();

        return calculate(healthOwner, getCurrentCustomer());
    }

    // usado pelo responsável p/ ver pendências de um aluno menor vinculado
    @Transactional(readOnly = true)
    public HealthPendingItemsResponseDTO getStudentPendingItems(UUID studentId) {
        HealthOwner owner = userProfileResolverService.resolveForResponsible(studentId);
        return calculate(owner, getCurrentCustomer());
    }

    // usado pelo admin p/ ver pendências de qualquer dono do próprio Customer
    @Transactional(readOnly = true)
    public HealthPendingItemsResponseDTO getOwnerPendingItems(OwnerType ownerType, UUID ownerId) {
        boolean exists = ownerType == OwnerType.STUDENT ? studentRepository.existsById(ownerId) : driverRepository.existsById(ownerId);

        if (!exists) {
            throw new EntityNotFoundException("Dono não encontrado");
        }

        return calculate(new HealthOwner(ownerType, ownerId), getCurrentCustomer());
    }

    public void sendPendingItemsReminders() {
        List<Customer> customers = customerRepository.findAllByHealthDeclarationRequestEnabledTrue();

        for (Customer customer : customers) {
            try {
                TenantContext.setCurrentTenant(customer.getId());

                // faz com que o job rode em apenas uma transição de leitura
                readOnlyTransactionTemplate.executeWithoutResult(status -> {
                    List<HealthOwner> owners = new ArrayList<>();

                    studentRepository.findActiveIds(customer.getId()).forEach(id -> owners.add(new HealthOwner(OwnerType.STUDENT, id)));
                    driverRepository.findActiveIds(customer.getId()).forEach(id -> owners.add(new HealthOwner(OwnerType.DRIVER, id)));

                    for (HealthOwner owner : owners) {
                        HealthPendingItemsResponseDTO pending = calculate(owner, customer);

                        if (pending.hasPendingItems()) {
                            eventPublisher.publishEvent(new HealthPendingItemsReminderEvent(owner, pending.items().size(), customer.getId()));
                        }
                    }
                });

            } catch(Exception e) {
                log.error("[sendPendingItemsReminders] Falha ao processar o Customer {}", customer.getId(), e);
            } finally {
                // limpa o customerId para não vazar para o próximo Customer
                TenantContext.removeCurrentTenant();
            }
        }
    }

    /*
     *  calcula as pendências de saúde de um dono (aluno ou motorista):
     * - não depende do usuário logado: é reutilizado pelos endpoints e pelo job de lembretes
     * - consentimento e declaração ausentes encerram o cálculo (o usuário não consegue avançar sem eles)
     * - declaração desatualizada é registrada e o cálculo continua para os documentos
     * - cada condição que exige comprovação precisa de ao menos um documento válido
     * */
    private HealthPendingItemsResponseDTO calculate(HealthOwner owner, Customer customer) {
        // caso não seja obrigatório considera sem pendências
        if (!customer.isHealthDeclarationRequestEnabled()) {
            return response(owner, List.of());
        }

        // sem consentimento ativo nada mais pode ser feito
        if (!healthConsentService.hasActiveConsent(owner.id())) {
            return response(owner, List.of(item(HealthPendingItemType.CONSENT_MISSING, null, null, "Aceite o termo de consentimento para continuar")));
        }

        // sem declaração nada mais pode ser verificado
        HealthDeclaration declaration = healthDeclarationRepository.findByOwnerId(owner.id()).orElse(null);

        if (declaration == null) {
            return response(owner, List.of(item(HealthPendingItemType.DECLARATION_MISSING, null, null, "Preencha a declaração de saúde")));
        }

        List<HealthPendingItemDTO> items = new ArrayList<>();

        // declaração de um período letivo anterior: registra e continua
        if (declaration.getHealthDeclarationStatus() == HealthDeclarationStatus.OUTDATED) {
            items.add(item(HealthPendingItemType.DECLARATION_OUTDATED, null, null, "Revise a declaração de saúde para o período atual"));
        }

        // condições declaradas que exigem documento comprobatório
        List<UUID> requiredConditionIds = declaration.isHasCondition() ? declaration.getConditions().stream()
                .filter(condition -> condition.getType().isRequiresMedicalReport())
                .map(HealthCondition::getId).toList() : List.of();

        // nenhuma condição exige documento retorna direto e evita a consulta com IN vazio
        if (requiredConditionIds.isEmpty()) {
            return response(owner, items);
        }

        LocalDate today = LocalDate.now(zoneOf(customer));

        // busca os documentos de todas as condições em uma única consulta e agrupa por condição
        Map<UUID, List<Document>> documentsByCondition = documentRepository.findByHealthConditionIdIn(requiredConditionIds)
                .stream()
                .collect(Collectors.groupingBy(Document::getHealthConditionId));

        // percorre as condições exigidas (inclusive as que não têm nenhum documento)
        for (UUID conditionId : requiredConditionIds) {
            List<Document> documents = documentsByCondition.getOrDefault(conditionId, List.of());

            // basta um documento válido para a condição estar comprovada
            boolean hasValidDocument = documents.stream().anyMatch(document -> isValid(document, today));

            if (hasValidDocument) {
                continue;
            }

            // documento mais recente da condição (substituídos não contam jaq o arquivo já foi removido)
            Optional<Document> latest = documents.stream()
                    .filter(document -> document.getDocumentStatus() != DocumentStatus.REPLACED)
                    .max(Comparator.comparing(Document::getCreatedAt));

            // nenhum documento enviado
            if (latest.isEmpty()) {
                items.add(item(HealthPendingItemType.CONDITION_WITHOUT_DOCUMENT, conditionId, null, "Envie o documento comprobatório de uma condição declarada"));
                continue;
            }

            Document document = latest.get();

            // rejeitado ou vencido (EXPIRED ou ativo com validade passada ainda não marcado pelo job)
            if (document.getDocumentStatus() == DocumentStatus.REJECTED) {
                items.add(item(HealthPendingItemType.DOCUMENT_REJECTED, conditionId, document.getId(), "Um documento foi rejeitado e precisa ser reenviado"));
            } else {
                items.add(item(HealthPendingItemType.DOCUMENT_EXPIRED, conditionId, document.getId(), "Um documento está vencido e precisa ser substituído"));
            }
        }

        return response(owner, items);
    }

    // documento ativo e dentro da validade (sem validade = não vence)
    private boolean isValid(Document document, LocalDate today) {
        return ACTIVE_STATUSES.contains(document.getDocumentStatus())
                && (document.getValidUntil() == null || !document.getValidUntil().isBefore(today));
    }

    // fuso do Customer para calcular o "hoje" (fallback para o horário de Brasília)
    private ZoneId zoneOf(Customer customer) {
        try {
            return customer.getTimeZone() != null ? ZoneId.of(customer.getTimeZone()) : DEFAULT_ZONE;
        } catch (DateTimeException e) {
            return DEFAULT_ZONE;
        }
    }

    // customer do usuário logado (via TenantContext)
    private Customer getCurrentCustomer() {
        return customerRepository.findById(getCurrentTenant())
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));
    }

    private HealthPendingItemDTO item(HealthPendingItemType type, UUID conditionId, UUID documentId, String message) {
        return new HealthPendingItemDTO(type, conditionId, documentId, message);
    }

    private HealthPendingItemsResponseDTO response(HealthOwner owner, List<HealthPendingItemDTO> items) {
        return new HealthPendingItemsResponseDTO(owner.type(), owner.id(), !items.isEmpty(), items);
    }
}
