package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.constants.GlobalAppConstants;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.AdministratorRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.AdministratorResponseMapper;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorProfileDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.PushNotificationDeviceTokenRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class AdministratorService {

    private final AdministratorRepository administratorRepository;
    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;
    private final SetupAuthenticationService setupAuthenticationService;
    private final ImageStorageService imageStorageService;

    private final PushNotificationDeviceTokenRepository pushNotificationDeviceTokenRepository;

    private final AdministratorRequestMapper administratorRequestMapper;
    private final AdministratorResponseMapper administratorResponseMapper;

    private final PasswordEncoder passwordEncoder;

    public AdministratorService(AdministratorRepository administratorRepository, UserAccountRepository userAccountRepository, CustomerRepository customerRepository, SetupAuthenticationService setupAuthenticationService, S3StorageService s3StorageService, ImageStorageService imageStorageService, PushNotificationDeviceTokenRepository pushNotificationDeviceTokenRepository, AdministratorRequestMapper administratorRequestMapper, AdministratorResponseMapper administratorResponseMapper, PasswordEncoder passwordEncoder) {
        this.administratorRepository = administratorRepository;
        this.userAccountRepository = userAccountRepository;
        this.customerRepository = customerRepository;
        this.setupAuthenticationService = setupAuthenticationService;
        this.imageStorageService = imageStorageService;
        this.pushNotificationDeviceTokenRepository = pushNotificationDeviceTokenRepository;
        this.administratorRequestMapper = administratorRequestMapper;
        this.administratorResponseMapper = administratorResponseMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Page<AdministratorResponseDTO> getAllAdministrators(String email, String name, String lastName, String neighborhood, String jobTitle, Pageable pageable) {
        // pega o tenant da thread atual e usa para filtrar na native query
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new IllegalStateException("Entidade de tenant sem Customer definido");
        }

        String normalizeEmail = email == null ? null : email.toLowerCase(Locale.ROOT);

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return administratorRepository.findAllByOptionalFilters(
                customerId,
                blankToNull(normalizeEmail),
                blankToNull(name),
                blankToNull(lastName),
                blankToNull(neighborhood),
                blankToNull(jobTitle),
                pageOnly).map(administratorResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<AdministratorResponseDTO> getAllAdministratorsByStatus(GeneralStatus status, Pageable pageable) {
        if (status == null) status = GeneralStatus.ACTIVE;

        Page<Administrator> administrators = administratorRepository.findByStatus(status, pageable);

        return administrators.map(administratorResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public AdministratorResponseDTO getCurrentAdministrator() {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Administrator expectedLoggedAdmin = administratorRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado para o email: " + authenticatedUserEmail));

        return administratorResponseMapper.toDTO(expectedLoggedAdmin);
    }

    public Administrator createForExistingAccount(UserAccount userAccount, UUID customerId, AdministratorProfileDTO profileDTO) {

        // verifica se já não está cadastrado no sistema
        if (administratorRepository.existsByUserAccountIdIgnoringTenant(userAccount.getId())) {
            throw new DuplicateResourceException("Esse usuário já existe no sistema");
        }

        if (administratorRepository.existsByTelephoneIgnoringTenant(profileDTO.administratorAccept().telephone())) {
            throw new DuplicateResourceException("Já existe um usuário com esse telefone no sistema");
        }

        if (administratorRepository.existsByCpfIgnoringTenant(profileDTO.administratorAccept().cpf())) {
            throw new DuplicateResourceException("Já existe um usuário com esse cpf no sistema");
        }

        long countAdministrators = countAdministratorInThisCustomer(customerId);

        // plano básico: até 02 administradores no sistema
        if (countAdministrators >= administratorRegisterLimit(customerId)) {
            throw new EntityLimitExceededException("O limite de cadastro para Administradores no seu plano é de " + administratorRegisterLimit(customerId) + ". Para mais cadastros faça um upgrade ou personalize seu plano.");
        }

        Administrator administrator = administratorRequestMapper.toEntity(profileDTO);

        administrator.setUserAccount(userAccount);
        administrator.assignCustomer(customerId);
        administrator.setStatus(GeneralStatus.ACTIVE);

        return administratorRepository.save(administrator);
    }

    @Transactional
    public AdministratorResponseDTO updateCurrentAdministrator(AdministratorUpdateDTO admRequestDTO) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Administrator loggedAdm = administratorRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado para o email: " + authenticatedUserEmail));

        if (loggedAdm.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountModificationException("Não é possível atualizar uma conta desativada");
        }

        // validações de duplicação de recursos no sistema
        if (admRequestDTO.email() != null && !admRequestDTO.email().isBlank()) {
            if (!admRequestDTO.email().equals(loggedAdm.getUserAccount().getEmail())) {
                if (userAccountRepository.existsByEmail(admRequestDTO.email())) {
                    throw new DuplicateResourceException("Email já registrado no sistema");
                }
            }
        }

        if (admRequestDTO.telephone() != null && !admRequestDTO.telephone().isBlank()) {
            if (!admRequestDTO.telephone().equals(loggedAdm.getTelephone())) {
                if (administratorRepository.existsByTelephoneIgnoringTenant(admRequestDTO.telephone())) {
                    throw new DuplicateResourceException("Telefone já registrado no sistema");
                }
            }
        }

        // usa MapStruct p/ atualizar apenas os campos não nulos
        administratorRequestMapper.administratorUpdateFromDTO(admRequestDTO, loggedAdm);

        UserAccount userAccount = loggedAdm.getUserAccount();

        if (admRequestDTO.email() != null) {
            userAccount.setEmail(admRequestDTO.email());
        }

        if (admRequestDTO.password() != null && !admRequestDTO.password().isBlank()) {
            userAccount.setPassword(passwordEncoder.encode(admRequestDTO.password()));
        }

        Administrator savedAdmin = administratorRepository.save(loggedAdm);

        return administratorResponseMapper.toDTO(savedAdmin);
    }

    @Transactional
    public void updateAdministrator(GeneralStatus newStatus) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Administrator expectedAdministrator = administratorRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado para o email: " + authenticatedUserEmail));

        // bloqueia APENAS se estiver tentando DESATIVAR e for o último ativo
        if (newStatus == GeneralStatus.INACTIVE) {
            int activeAdministrators = administratorRepository.countActiveAdministrators(GeneralStatus.ACTIVE);

            if (activeAdministrators <= 1) {
                throw new IllegalStateException("Não é possível desativar o último administrador ativo do sistema.");
            }
        }

        if (expectedAdministrator.getStatus() == newStatus) throw new DuplicateResourceException("Administrador já está com status, " + newStatus);

        expectedAdministrator.setStatus(newStatus);

        administratorRepository.save(expectedAdministrator);
    }

    protected long countAdministratorInThisCustomer(UUID customerId) {
        return administratorRepository.countAdministratorsInThisCustomer(customerId);
    }

    private long administratorRegisterLimit(UUID customerId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        return customer.getPlan().getMaxAdministrators();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
