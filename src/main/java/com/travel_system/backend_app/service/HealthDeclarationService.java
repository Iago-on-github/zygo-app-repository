package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.events.documents.HealthConditionChangedEvent;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.response.HealthDeclarationResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.HealthDeclarationResponseDTO;
import com.travel_system.backend_app.model.enums.Action;
import com.travel_system.backend_app.model.enums.HealthDeclarationStatus;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.ResourceType;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

/*
 * "ficha" de saúde do aluno ou motorista
 * - cada dono possui uma única declaração, atualizada ao longo do tempo
 * - estudantes menores de idade não têm autonomia para auto-declaração, precisam do responsável
 * */

@Service
public class HealthDeclarationService {

    private final UserProfileResolverService userProfileResolverService;
    private final HealthConsentService healthConsentService;
    private final SensitiveDataAccessLogService sensitiveDataAccessLogService;

    private final UserAccountRepository userAccountRepository;
    private final CustomerTermRepository customerTermRepository;
    private final HealthDeclarationRepository healthDeclarationRepository;
    private final DocumentRepository documentRepository;

    private final HealthDeclarationResponseMapper healthDeclarationResponseMapper;

    private final ApplicationEventPublisher eventPublisher;

    public HealthDeclarationService(UserProfileResolverService userProfileResolverService, HealthConsentService healthConsentService, SensitiveDataAccessLogService sensitiveDataAccessLogService, UserAccountRepository userAccountRepository, CustomerTermRepository customerTermRepository, HealthDeclarationRepository healthDeclarationRepository, DocumentRepository documentRepository, HealthDeclarationResponseMapper healthDeclarationResponseMapper, ApplicationEventPublisher eventPublisher) {
        this.userProfileResolverService = userProfileResolverService;
        this.healthConsentService = healthConsentService;
        this.sensitiveDataAccessLogService = sensitiveDataAccessLogService;
        this.userAccountRepository = userAccountRepository;
        this.customerTermRepository = customerTermRepository;
        this.healthDeclarationRepository = healthDeclarationRepository;
        this.documentRepository = documentRepository;
        this.healthDeclarationResponseMapper = healthDeclarationResponseMapper;
        this.eventPublisher = eventPublisher;
    }

    // aluno maior ou motorista realizando a própria declaração
    @Transactional
    public HealthDeclarationResponseDTO myDeclaration(HealthDeclarationRequestDTO dto) {
        return declare(selfContext(), dto);
    }

    // responsável realizando a declaração para o estudante menor de idade
    @Transactional
    public HealthDeclarationResponseDTO minorStudentDeclaration(UUID studentId, HealthDeclarationRequestDTO dto) {
        return declare(responsibleContext(studentId), dto);
    }

    /*
     * cria a declaração de saúde do dono
     * - só é permitida se ainda não existir declaração (alterações posteriores passam pelo update)
     * - vale para o período letivo atual, para o mais próximo ou fica sem período, caso não exista nenhum
     * - "sem condições" exige lista vazia; "com condições" exige ao menos uma
     * */
    private HealthDeclarationResponseDTO declare(HealthAccessContext context, HealthDeclarationRequestDTO dto) {
        HealthOwner owner = context.dtoOwner();

        requireActiveConsent(owner);

        if (healthDeclarationRepository.existsByOwnerId(owner.id())) {
            throw new HealthDeclarationAlreadyExistsException("Já existe uma declaração de saúde. Utilize a atualização para alterá-la");
        }

        List<HealthConditionRequestDTO> conditions = dto.conditions() == null ? List.of() : dto.conditions();

        // consistência entre a flag e a lista de condições
        if (dto.hasCondition() && conditions.isEmpty()) {
            throw new ConditionsNotFoundException("Você deve inserir ao menos uma condição");
        }
        if (!dto.hasCondition() && !conditions.isEmpty()) {
            throw new ConditionsNotFoundException("Uma declaração sem condições não pode conter condições");
        }

        UUID customerId = getCurrentTenant();

        HealthDeclaration declaration = new HealthDeclaration();
        declaration.assignCustomer(customerId);

        if (owner.type() == OwnerType.STUDENT) {
            declaration.setStudentId(owner.id());
        } else {
            declaration.setDriverId(owner.id());
        }

        declaration.setHasCondition(dto.hasCondition());
        declaration.setHealthDeclarationStatus(HealthDeclarationStatus.CURRENT);
        declaration.setCustomerTerm(getCurrentOrClosestTerm(LocalDate.now()));
        declaration.setDeclaredBy(context.actorAccountId());
        declaration.setDeclaredAt(Instant.now());

        // converte e vincula cada condição à declaração
        conditions.forEach(conditionDTO -> declaration.addCondition(toCondition(conditionDTO, customerId)));

        HealthDeclaration saved;
        try {
            saved = healthDeclarationRepository.saveAndFlush(declaration);
        } catch (DataIntegrityViolationException e) {
            throw new HealthDeclarationAlreadyExistsException("Já existe uma declaração de saúde. Utilize a atualização para alterá-la");
        }

        // notifica o administrador somente quando há condições declaradas
        if (saved.isHasCondition()) {
            publishConditionChanged(saved, owner);
        }

        return healthDeclarationResponseMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public HealthDeclarationResponseDTO getMyCurrentDeclaration() {
        return getDeclaration(selfContext());
    }

    @Transactional(readOnly = true)
    public HealthDeclarationResponseDTO getMinorStudentDeclaration(UUID studentId) {
        return getDeclaration(responsibleContext(studentId));
    }

    private HealthDeclarationResponseDTO getDeclaration(HealthAccessContext context) {
        HealthDeclaration declaration = healthDeclarationRepository.findByOwnerId(context.dtoOwner().id())
                .orElseThrow(() -> new EntityNotFoundException("Declaração não encontrada"));

        return healthDeclarationResponseMapper.toDTO(declaration);
    }

    /*
     * apenas para o administrador
     * - o filtro de tenant garante que apenas declarações do próprio Customer sejam encontradas
     * - o acesso é registrado no log de dados sensíveis
     * */
    @Transactional(readOnly = true)
    public HealthDeclarationResponseDTO getDeclarationById(UUID healthDeclarationId) {
        HealthDeclaration declaration = healthDeclarationRepository.findById(healthDeclarationId)
                .orElseThrow(() -> new EntityNotFoundException("Declaração não encontrada"));

        // registra a leitura do dado sensível pelo administrador
        sensitiveDataAccessLogService.record(getAuthenticatedUserAccountId(), ownerOf(declaration),
                ResourceType.HEALTH_DECLARATION, declaration.getId(), Action.VIEW, null);

        return healthDeclarationResponseMapper.toDTO(declaration);
    }

    @Transactional
    public HealthDeclarationResponseDTO updateMyDeclaration(HealthDeclarationUpdateDTO dto) {
        return update(selfContext(), dto);
    }

    @Transactional
    public HealthDeclarationResponseDTO updateMinorStudentDeclaration(UUID studentId, HealthDeclarationUpdateDTO dto) {
        return update(responsibleContext(studentId), dto);
    }

    /*
     * atualiza a declaração existente:
     * - remove, edita e adiciona condições (nessa ordem)
     * - documentos das condições removidas são apagados do banco agora e do storage após o commit
     * - recalcula a flag hasCondition e registra quem alterou
     * - notifica o administrador quando há condições adicionadas ou editadas
     * */
    private HealthDeclarationResponseDTO update(HealthAccessContext context, HealthDeclarationUpdateDTO dto) {
        HealthOwner owner = context.dtoOwner();

        requireActiveConsent(owner);

        // busca com lock e evita alterações concorrentes na mesma declaração
        HealthDeclaration declaration = healthDeclarationRepository.findByOwnerIdForUpdate(owner.id())
                .orElseThrow(() -> new EntityNotFoundException("Declaração não encontrada"));

        List<UUID> idsToRemove = dto.conditionsIdsToRemove() == null ? List.of() : dto.conditionsIdsToRemove();
        List<HealthConditionUpdateDTO> toUpdate = dto.conditionsToUpdate() == null ? List.of() : dto.conditionsToUpdate();
        List<HealthConditionRequestDTO> toAdd = dto.conditionsToAdd() == null ? List.of() : dto.conditionsToAdd();

        // indexa as condições atuais da declaração por id
        Map<UUID, HealthCondition> currentConditions = new HashMap<>();
        declaration.getConditions().forEach(condition -> currentConditions.put(condition.getId(), condition));

        // remoção
        if (!idsToRemove.isEmpty()) {
            // todos os ids precisam pertencer a ESTA declaração
            List<HealthCondition> removedConditions = idsToRemove.stream()
                    .map(id -> Optional.ofNullable(currentConditions.get(id))
                            .orElseThrow(() -> new EntityNotFoundException("Condição não encontrada")))
                    .toList();

            /*
             * apaga primeiro os documentos vinculados às condições removidas:
             * - a FK health_condition_id impediria a exclusão das condições
             * - o flush garante que os documentos sejam removidos antes das condições
             * - os arquivos são removidos do storage somente após o commit (evento)
             * */
            List<Document> documents = documentRepository.findByHealthConditionIdIn(idsToRemove);

            if (!documents.isEmpty()) {
                List<String> storageKeysToRemove = documents.stream().map(Document::getStorageKey).toList();

                documentRepository.deleteAll(documents);
                documentRepository.flush();

                eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(storageKeysToRemove));
            }

            // as condições removidas da lista são apagadas no commit via ophanRemoval
            declaration.getConditions().removeAll(removedConditions);
        }

        // edição
        for (HealthConditionUpdateDTO conditionDTO : toUpdate) {
            HealthCondition condition = currentConditions.get(conditionDTO.id());

            if (condition == null || idsToRemove.contains(conditionDTO.id())) {
                throw new EntityNotFoundException("Condição não encontrada");
            }

            condition.setType(conditionDTO.type());
            condition.setSeverity(conditionDTO.severity());
            condition.setDescription(conditionDTO.description());
            condition.setEmergencyInstructions(conditionDTO.emergencyInstructions());
            condition.setMedication(conditionDTO.medication());
            condition.setWhereIsMedication(conditionDTO.whereIsMedication());
            condition.setEmergencyContactName(conditionDTO.emergencyContactName());
            condition.setEmergencyContactTelephone(conditionDTO.emergencyContactTelephone());
        }

        // adição
        toAdd.forEach(conditionDTO -> declaration.addCondition(toCondition(conditionDTO, declaration.getCustomerId())));

        // recalcula a flag e registra quem alterou
        declaration.setHasCondition(!declaration.getConditions().isEmpty());
        declaration.setDeclaredBy(context.actorAccountId());
        declaration.setDeclaredAt(Instant.now());

        // notifica o administrador quando houve condição adicionada ou editada
        if (!toAdd.isEmpty() || !toUpdate.isEmpty()) {
            publishConditionChanged(declaration, owner);
        }

        return healthDeclarationResponseMapper.toDTO(declaration);
    }


    // aluno maior ou motorista confirmando a própria declaração
    @Transactional
    public HealthDeclarationResponseDTO confirmUnchangedMine() {
        return confirmUnchanged(selfContext());
    }

    // responsável confirmando a declaração de um aluno menor vinculado
    @Transactional
    public HealthDeclarationResponseDTO confirmUnchangedForStudent(UUID studentId) {
        return confirmUnchanged(responsibleContext(studentId));
    }

    /*
     *  revalidação rápida da declaração ("minhas informações continuam as mesmas").
     * - atualiza a própria declaração, sem copiar condições (os documentos continuam vinculados)
     * - volta a ser CURRENT e passa a valer para o período letivo atual (ou sem período, se não houver)
     * - registra quem confirmou e quando
     * - idempotente: se já estiver vigente no período atual, apenas retorna a declaração
     * */
    private HealthDeclarationResponseDTO confirmUnchanged(HealthAccessContext context) {
        HealthOwner owner = context.dtoOwner();

        // sem consentimento ativo nenhum dado de saúde pode ser revalidado
        requireActiveConsent(owner);

        // busca a declaração com lock
        HealthDeclaration declaration = healthDeclarationRepository.findByOwnerIdForUpdate(owner.id())
                .orElseThrow(() -> new EntityNotFoundException("Nenhuma declaração de saúde encontrada para revalidar"));

        // período letivo vigente (que pode não existir)
        CustomerTerm currentTerm = customerTermRepository.findByCurrentDate(LocalDate.now()).orElse(null);

        // se já está vigente no período atual
        if (declaration.getHealthDeclarationStatus() == HealthDeclarationStatus.CURRENT
                && Objects.equals(termId(declaration.getCustomerTerm()), termId(currentTerm))) {
            return healthDeclarationResponseMapper.toDTO(declaration);
        }

        declaration.setHealthDeclarationStatus(HealthDeclarationStatus.CURRENT);
        declaration.setCustomerTerm(currentTerm);
        declaration.setDeclaredBy(context.actorAccountId());
        declaration.setDeclaredAt(Instant.now());

        return healthDeclarationResponseMapper.toDTO(declaration);
    }

    // contexto do aluno maior ou motorista agindo sobre os próprios dados
    private HealthAccessContext selfContext() {
        return new HealthAccessContext(getAuthenticatedUserAccountId(), userProfileResolverService.resolveSelf());
    }

    // contexto do responsável agindo sobre um aluno menor vinculado (vínculo e idade validados no resolver)
    private HealthAccessContext responsibleContext(UUID studentId) {
        return new HealthAccessContext(getAuthenticatedUserAccountId(), userProfileResolverService.resolveForResponsible(studentId));
    }

    // sem consentimento ativo nenhum dado de saúde pode ser criado ou alterado
    private void requireActiveConsent(HealthOwner owner) {
        if (healthConsentService.hasActiveConsent(owner.id())) {
            throw new HealthConsentRequiredException("É necessário aceitar o termo de consentimento antes de continuar");
        }
    }

    // converte o DTO em entidade (o vínculo com a declaração é feito pelo addCondition)
    private HealthCondition toCondition(HealthConditionRequestDTO dto, UUID customerId) {
        HealthCondition condition = new HealthCondition();
        condition.assignCustomer(customerId);
        condition.setType(dto.type());
        condition.setSeverity(dto.severity());
        condition.setDescription(dto.description());
        condition.setEmergencyInstructions(dto.emergencyInstructions());
        condition.setMedication(dto.medication());
        condition.setWhereIsMedication(dto.whereIsMedication());
        condition.setEmergencyContactName(dto.emergencyContactName());
        condition.setEmergencyContactTelephone(dto.emergencyContactTelephone());
        return condition;
    }

    // publica o evento para notificar o administrador sobre condições novas ou alteradas
    private void publishConditionChanged(HealthDeclaration declaration, HealthOwner owner) {
        eventPublisher.publishEvent(new HealthConditionChangedEvent(
                declaration.getId(), owner.id(), declaration.isHasCondition(),
                declaration.getDeclaredBy(), declaration.getCustomerId()));
    }

    // monta o dono a partir da declaração
    private HealthOwner ownerOf(HealthDeclaration declaration) {
        return declaration.getStudentId() != null
                ? new HealthOwner(OwnerType.STUDENT, declaration.getStudentId())
                : new HealthOwner(OwnerType.DRIVER, declaration.getDriverId());
    }

    /*
     * período em que a declaração começa a valer:
     * - o período vigente na data informada
     * - caso não haja, o período futuro mais próximo
     * - caso não haja nenhum, a declaração fica sem período (null)
     * */
    private CustomerTerm getCurrentOrClosestTerm(LocalDate now) {
        return customerTermRepository.findByCurrentDate(now)
                .or(() -> customerTermRepository.findFirstByStartTermGreaterThanEqualOrderByStartTermAsc(now))
                .orElse(null);
    }

    // compara períodos pelo id
    private UUID termId(CustomerTerm term) {
        return term == null ? null : term.getId();
    }

}