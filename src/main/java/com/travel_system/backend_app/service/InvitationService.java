package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.config.constants.GlobalAppConstants;
import com.travel_system.backend_app.events.invitations.InvitationDeclinedEvent;
import com.travel_system.backend_app.events.invitations.InvitationAcceptedEvent;
import com.travel_system.backend_app.events.invitations.InvitationCreatedEvent;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.ProfileCreator;
import com.travel_system.backend_app.interfaces.mappers.InvitationRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.InvitationResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.invitation.InvitationAcceptResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.MyInvitationResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.StudentAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.StudentInvitationDTO;
import com.travel_system.backend_app.model.dtos.response.InvitationResponseDTO;
import com.travel_system.backend_app.model.dtos.security.AuthTokens;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InvitationStatus;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.InvitationRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.service.strategies.invitation.StudentInvitationProfileStrategy;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private final InvitationRepository invitationRepository;
    private final UserAccountRepository userAccountRepository;
    private final AdministratorRepository administratorRepository;
    private final CustomerRepository customerRepository;

    private final PermissionsService permissionsService;
    private final TokenConfig tokenConfig;

    private final InvitationResponseMapper invitationResponseMapper;

    private final ObjectMapper objectMapper;

    private final StudentInvitationProfileStrategy studentInvitationProfileStrategy;

    private final ApplicationEventPublisher eventPublisher;

    public InvitationService(InvitationRepository invitationRepository, UserAccountRepository userAccountRepository, AdministratorRepository administratorRepository, CustomerRepository customerRepository, PermissionsService permissionsService, TokenConfig tokenConfig, InvitationResponseMapper invitationResponseMapper, ObjectMapper objectMapper, StudentInvitationProfileStrategy studentInvitationProfileStrategy, ApplicationEventPublisher eventPublisher) {
        this.invitationRepository = invitationRepository;
        this.userAccountRepository = userAccountRepository;
        this.administratorRepository = administratorRepository;
        this.customerRepository = customerRepository;
        this.permissionsService = permissionsService;
        this.tokenConfig = tokenConfig;
        this.invitationResponseMapper = invitationResponseMapper;
        this.objectMapper = objectMapper;
        this.studentInvitationProfileStrategy = studentInvitationProfileStrategy;
        this.eventPublisher = eventPublisher;
    }

    // delega para o método privado de enviar convites
    @Transactional
    public InvitationResponseDTO sendStudentInvitation(String email, StudentInvitationDTO data) {
        return sendInvitation(email, TargetUserType.STUDENT, data);
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponseDTO> listCustomerInvitations(InvitationStatus invitationStatus, Pageable pageable) {
        Administrator administrator = getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());

        Page<Invitation> invitations = invitationRepository.findInvitationsByCustomerIdAndStatusOptional(
                administrator.getCustomerId(), invitationStatus, pageable);

        if (invitations.isEmpty()) {
            return Page.empty(pageable);
        }

        // busca em lote os administradores que enviaram os convites da página
        Set<UUID> inviterUserAccountIds = invitations.stream()
                .map(Invitation::getInvitedBy)
                .collect(Collectors.toSet());

        Map<UUID, Administrator> invitersByUserAccountId = administratorRepository
                .findAllByUserAccountIdIn(inviterUserAccountIds).stream()
                .collect(Collectors.toMap(a -> a.getUserAccount().getId(), Function.identity()));

        Instant now = Instant.now();
        return invitations.map(inv -> {
            Administrator inviter = invitersByUserAccountId.get(inv.getInvitedBy());
            return invitationResponseMapper.toResponse(inv, inviter != null ? inviter.getName() : null, now);
        });
    }

    @Transactional
    public void revokeInvitation(UUID invitationId) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        getAuthenticatedActiveAdministrator(authenticatedUserEmail);

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation não encontrada pelo ID"));

        invitation.revoke(Instant.now());
    }

    @Transactional
    public InvitationResponseDTO resendInvitation(UUID invitationId) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        getAuthenticatedActiveAdministrator(authenticatedUserEmail);

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation não encontrada pelo ID"));

        Instant now = Instant.now();

        UUID invitedUserAccountId = invitation.getInvitedUserAccountId();

        resolveInvitableUserById(invitedUserAccountId);

        // gera um novo token puro e o hash
        String randomPureToken = HmacTokenService.generateRandomPureToken();

        String simpleHashToken = HmacTokenService.calculateSimpleHash(randomPureToken);

        // gera novo token, com nova expiração e setando status para PENDING
        invitation.renew(simpleHashToken, now.plus(GlobalAppConstants.INVITE_EXPIRES_AT));

        // salva com save and flush, lançando exception caso seja duplicado com PENDING
        Invitation savedInvitation = saveAndFlushHandlingDuplicate(invitation);

        // gera e publica evento
        InvitationCreatedEvent invitationCreatedEvent = new InvitationCreatedEvent(savedInvitation.getId(), savedInvitation.getInvitedUserAccountId(), savedInvitation.getCustomerId(), savedInvitation.getTargetUserType(), savedInvitation.getExpiresAt(), randomPureToken);

        eventPublisher.publishEvent(invitationCreatedEvent);

        // busca o administrator que fez o convite
        UserAccount userAccount = userAccountRepository.findById(savedInvitation.getInvitedBy())
                .orElseThrow(() -> new EntityNotFoundException("UserAccount não encontrado."));

        String email = userAccount.getEmail();

        Administrator administrator = administratorRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Administrator não encontrado."));

        return invitationResponseMapper.toResponse(savedInvitation, administrator.getName(), now);
    }

    @Transactional(readOnly = true)
    public List<MyInvitationResponseDTO> listMyPendingInvitations() {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        UserAccount user = userAccountRepository.findUserByEmail(authenticatedUserEmail);

        if (user == null) throw new AccessDeniedException("User não encontrado");

        // se já está em um customer retorna a lista vazia
        if (user.getUserAccountType() != UserAccountType.UNASSIGNED) {
            return List.of();
        }

        Instant now = Instant.now();

        List<Invitation> myPendingInvitations = invitationRepository.findMyPendingInvitations(user.getId(), now);

        if (myPendingInvitations.isEmpty()) {
            return List.of();
        }

        Set<UUID> customerIds = myPendingInvitations.stream().map(Invitation::getCustomerId).collect(Collectors.toSet());
        Set<UUID> inviterIds = myPendingInvitations.stream().map(Invitation::getInvitedBy).collect(Collectors.toSet());

        Map<UUID, String> customerNames  = customerRepository.findAllById(customerIds).stream().collect(Collectors.toMap(Customer::getId, Customer::getName));

        Map<UUID, String> inviterNames  = administratorRepository.findAllByUserAccountIdInIgnoringTenant(inviterIds).stream()
                .collect(Collectors.toMap(a -> a.getUserAccount().getId(), Administrator::getName));

        return myPendingInvitations.stream()
                .map(inv -> new MyInvitationResponseDTO(
                        inv.getId(),
                        inv.getCustomerId(),
                        customerNames.get(inv.getCustomerId()),
                        inv.getTargetUserType(),
                        inviterNames.get(inv.getInvitedBy()),
                        readProfileDataAsJson(inv.getProfileData()),
                        inv.getCreatedAt(),
                        inv.getExpiresAt()
                )).toList();
    }

    @Transactional
    public InvitationAcceptResponseDTO acceptStudentInvitation(UUID invitationId, StudentAcceptDTO studentAcceptDTO){
        return acceptInvitation(invitationId, TargetUserType.STUDENT,
                ((invitation, account) -> studentInvitationProfileStrategy.createProfile(invitation, account, studentAcceptDTO)));
    }

    @Transactional
    public void declineInvitation(UUID invitationId) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Instant now = Instant.now();

        UserAccount user = userAccountRepository.findUserByEmail(authenticatedUserEmail);

        if (user == null) throw new AccessDeniedException("User não encontrado");

        Invitation invitation = invitationRepository.findByIdAndInvitedUserAccountIdIgnoringTenant(invitationId, user.getId())
                .orElseThrow(() -> new InvitationNotFoundException("Nenhum convite encontrado"));

        if (!invitation.isPending()) {
            throw new InvitationNotPendingException("O convite não está pendente");
        }

        if (invitation.isExpired(now)) throw new InvitationExpiredException("Convite expirado");

        // faz o decline
        invitation.decline(now);

        // publica evento para realizar envio de notificações
        eventPublisher.publishEvent(new InvitationDeclinedEvent(invitation.getId(), invitation.getCustomerId(), invitation.getInvitedBy(), user.getId()));
    }

    @Transactional
    public int expirePendingInvitations() {
        // usa job
        return invitationRepository.expirePendingInvitationsWithoutTenantFilter(Instant.now());
    }

    private InvitationAcceptResponseDTO acceptInvitation(UUID invitationId, TargetUserType expectedType, ProfileCreator creator) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Instant now = Instant.now();

        // conta do usuário autenticado
        UserAccount userAccount = userAccountRepository.findByEmailForUpdate(authenticatedUserEmail)
                .orElseThrow(() -> new AccessDeniedException("Usuário não autenticado"));

        if (!userAccount.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email não verificado.");
        }

        // convite do próprio usuário, ignorando o filtro de tenant (TenantContext vazio)
        Invitation invitation = invitationRepository.findByIdAndInvitedUserAccountIdIgnoringTenant(invitationId, userAccount.getId())
                .orElseThrow(() -> new InvitationNotFoundException("Convite não encontrado"));

        // validações
        if (!invitation.isPending()) {
            throw new InvitationNotPendingException("O convite não está mais pendente.");
        }

        if (invitation.isExpired(now)) {
            throw new InvitationExpiredException("O convite expirou");
        }

        if (invitation.getTargetUserType() != expectedType) {
            throw new InvitationRoleMismatchException("O convite não corresponde a esse tipo de perfil");
        }

        if (userAccount.getUserAccountType() != UserAccountType.UNASSIGNED) {
            throw new UserAlreadyHasProfileException("O usuário já possui um perfil vinculado");
        }

        // cria o perfil conforme a lambda que foi recebida
        UUID profileId = creator.create(invitation, userAccount);

        // atualiza a conta: seta permissões e papel
        userAccount.setUserAccountType(expectedType.getUserAccountType());
        permissionsService.assignPermissions(userAccount, expectedType);

        // aceita o convite
        invitation.accept(now);

        // manda o contexto e tokens para o front
        String customerName = customerRepository.findById(invitation.getCustomerId())
                .map(Customer::getName)
                .orElse(null);

        AuthTokens authTokens = tokenConfig.generateBothTokens(userAccount, invitation.getCustomerId());

        // realiza o cancelamento dos demais convites pendentes p/ o usuário
        invitationRepository.cancelOtherPendingInvitations(userAccount.getId(), invitation.getId(), now);

        // publica evento para notificar aqueles quem convidou (apenas rodando after commit)
        eventPublisher.publishEvent(new InvitationAcceptedEvent(invitation.getId(), invitation.getCustomerId(), invitation.getInvitedBy(), userAccount.getId(), now));

        return new InvitationAcceptResponseDTO(
                authTokens.accessToken(),
                authTokens.refreshToken(),
                userAccount.getUserAccountType(),
                profileId,
                invitation.getCustomerId(),
                invitation.getInvitedBy(),
                customerName,
                GeneralStatus.ACTIVE
        );

    }

    private InvitationResponseDTO sendInvitation(String email, TargetUserType targetUserType, Object profileData) {
        Instant now = Instant.now();

        // admin logado, ativo e no tenant correto
        Administrator administrator = getAuthenticatedActiveAdministrator(getAuthenticatedUserEmail());
        UUID customerId = administrator.getCustomerId();

        UserAccount invitee = resolveInvitableUserByEmail(email);

        // no máximo um convite pendente por Customer + usuário
        if (invitationRepository.existsByCustomerIdAndInvitedUserAccountIdAndInvitationStatus(customerId, invitee.getId(), InvitationStatus.PENDING)) {
            throw new DuplicatePendingInvitationException("Já existe um convite pendente para este usuário");
        }

        // payload com os dados operacionais do perfil
        String payload = writeProfileData(profileData);

        // token puro (vai no e-mail) e hash (fica no banco)
        String randomPureToken = HmacTokenService.generateRandomPureToken();
        String tokenHash = HmacTokenService.calculateSimpleHash(randomPureToken);

        Invitation invitation = new Invitation();
        invitation.setInvitedUserAccountId(invitee.getId());
        invitation.assignCustomer(customerId); // seta customerId através do vínculo de convite
        invitation.setInvitedIdentifier(email);
        invitation.setInvitedBy(administrator.getUserAccount().getId());
        invitation.setTargetUserType(targetUserType);
        invitation.setProfileData(payload);
        invitation.setVerificationTokenHash(tokenHash);
        invitation.setInvitationStatus(InvitationStatus.PENDING);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(GlobalAppConstants.INVITE_EXPIRES_AT));

        // o índice único parcial cobre a corrida entre dois admins
        Invitation saved = saveAndFlushHandlingDuplicate(invitation);

        eventPublisher.publishEvent(new InvitationCreatedEvent(
                saved.getId(),
                saved.getInvitedUserAccountId(),
                saved.getCustomerId(),
                saved.getTargetUserType(),
                saved.getExpiresAt(),
                randomPureToken));

        return invitationResponseMapper.toResponse(saved, administrator.getName(), now);
    }

    private UserAccount resolveInvitableUserById(UUID invitedUserAccountId) {
        UserAccount userAccount = userAccountRepository.findById(invitedUserAccountId)
                .orElseThrow(() -> new UserNotInvitableException("UserAccount não encontrado"));

        if (!userAccount.isEmailVerified() || userAccount.getUserAccountType() != UserAccountType.UNASSIGNED) {
            throw new UserNotInvitableException("Não foi possível convidar este usuário");
        }

        return userAccount;
    }

    private UserAccount resolveInvitableUserByEmail(String email) {
        UserAccount userAccount = userAccountRepository.findUserByEmail(email);

        if (userAccount == null) {
            throw new AccessDeniedException("Usuário nao encontrado");
        }

        if (!userAccount.isEmailVerified() || userAccount.getUserAccountType() != UserAccountType.UNASSIGNED) {
            log.info("[sendInvitation] Convite bloqueado: userAccountId={}, emailVerified={}, type={}",
                    userAccount.getId(), userAccount.isEmailVerified(), userAccount.getUserAccountType());
            throw new UserNotInvitableException("Não foi possível convidar este usuário");
        }

        return userAccount;
    }

    // usa hasConstraint para verificar o possível laço de exceptions para lançar a exception correta caso exista.
    private Invitation saveAndFlushHandlingDuplicate(Invitation inv) {
        try {
            return invitationRepository.saveAndFlush(inv);
        } catch (Exception e) {
            if (hasConstraint(e)) {
                throw new DuplicatePendingInvitationException("Pending duplicado");
            }

            throw e;
        }
    }

    private String writeProfileData(Object profileData) {
        try {
            return objectMapper.writeValueAsString(profileData);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar os dados do perfil do convite", e);
        }
    }

    // converte json para String como estrutura Json manipulável
    private JsonNode readProfileDataAsJson(String profileData) {
        try {
            return profileData == null ? null : objectMapper.readTree(profileData);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("profileData inválido no convite", e);
        }
    }

    private boolean hasConstraint(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().contains("ux_invitations_pending_customer_user")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
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
