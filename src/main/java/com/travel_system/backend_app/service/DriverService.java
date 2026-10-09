package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.constants.GlobalAppConstants;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.DriverRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.DriverResponseMapper;
import com.travel_system.backend_app.model.Cnh;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverProfileDTO;
import com.travel_system.backend_app.model.dtos.request.CnhNumberSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CpfSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CnhResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DriverCnhExpirationDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.CnhCategory;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;
    private final CnhRepository cnhRepository;
    private final PushNotificationDeviceTokenRepository pushNotificationDeviceTokenRepository;

    private final SetupAuthenticationService setupAuthenticationService;
    private final ImageStorageService imageStorageService;
    private final CnhService cnhService;

    private final PasswordEncoder passwordEncoder;

    private final DriverResponseMapper driverResponseMapper;
    private final DriverRequestMapper driverRequestMapper;

    public DriverService(DriverRepository driverRepository, UserAccountRepository userAccountRepository, CnhRepository cnhRepository, PushNotificationDeviceTokenRepository pushNotificationDeviceTokenRepository, SetupAuthenticationService setupAuthenticationService, S3StorageService s3StorageService, CustomerRepository customerRepository, ImageStorageService imageStorageService, CnhService cnhService, PasswordEncoder passwordEncoder, DriverResponseMapper driverResponseMapper, DriverRequestMapper driverRequestMapper) {
        this.driverRepository = driverRepository;
        this.userAccountRepository = userAccountRepository;
        this.cnhRepository = cnhRepository;
        this.pushNotificationDeviceTokenRepository = pushNotificationDeviceTokenRepository;
        this.setupAuthenticationService = setupAuthenticationService;
        this.customerRepository = customerRepository;
        this.imageStorageService = imageStorageService;
        this.cnhService = cnhService;
        this.passwordEncoder = passwordEncoder;
        this.driverResponseMapper = driverResponseMapper;
        this.driverRequestMapper = driverRequestMapper;
    }

    @Transactional(readOnly = true)
    public Page<DriverResponseDTO> getAllDrivers(String email, String name, String lastName, String neighborhood, String areaOfActivity, Set<Shift> driverShifts, Pageable pageable) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Usuário sem Customer vinculado");
        }

        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);

        // conjunto vazio ou nulo sem filtro de turno
        String[] shifts = (driverShifts == null || driverShifts.isEmpty())
                ? null
                : driverShifts.stream().map(Shift::name).toArray(String[]::new);

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return driverRepository.findAllByOptionalParameters(
                customerId,
                        blankToNull(normalizedEmail),
                        blankToNull(name),
                        blankToNull(lastName),
                        blankToNull(neighborhood),
                        blankToNull(areaOfActivity),
                        shifts,
                        pageOnly)
                .map(driverResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<DriverResponseDTO> getDriversByStatus(GeneralStatus newDriverStatus, Pageable pageable) {
        if (newDriverStatus == null) newDriverStatus = GeneralStatus.ACTIVE;

        Page<Driver> driverByStatus = driverRepository.findAllByStatus(newDriverStatus, pageable);

        return driverByStatus.map(driverResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public DriverResponseDTO getDriverById(UUID driverId) {
        return driverResponseMapper.toDTO(driverRepository.findById(driverId).orElseThrow(() -> new EntityNotFoundException("Driver não encontrado")));
    }

    @Transactional(readOnly = true)
    public DriverResponseDTO getDriverByCpf(CpfSearchRequestDTO dto) {
        String normalizeCpf = dto.cpf().replaceAll("\\D", "");

        return driverResponseMapper.toDTO(driverRepository.findByCpf(normalizeCpf).orElseThrow(() -> new EntityNotFoundException("Driver não encontrado")));
    }

    @Transactional(readOnly = true)
    public DriverResponseDTO getDriverByCnhNumber(CnhNumberSearchRequestDTO dto) {
        String normalizeCnhNumber = dto.cnhNumber().replaceAll("\\D", "");

        return driverResponseMapper.toDTO(driverRepository.findByCnhNumber(normalizeCnhNumber).orElseThrow(() -> new EntityNotFoundException("Driver não encontrado")));
    }

    // retorna motoristas cujo data de expiração estão proximas do parâmetro
    @Transactional(readOnly = true)
    public List<DriverCnhExpirationDTO> getDriversWithCnhExpiringSoon(Set<CnhCategory> cnhCategories, LocalDate thresholdDate) {
        return driverRepository.findDriversWithCnhExpiringUntil(cnhCategories, thresholdDate);
    }

    @Transactional(readOnly = true)
    public DriverResponseDTO getCurrentDriver() {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Driver getDriverLoggedProfile = driverRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado para o email: " + authenticatedUserEmail));

        return driverResponseMapper.toDTO(getDriverLoggedProfile);
    }

    public Driver createForExistingAccount(UserAccount userAccount, UUID customerId, DriverProfileDTO dto) {

        if (driverRepository.existsByUserAccountIdIgnoringTenant(userAccount.getId())) {
            throw new DuplicateResourceException("Já existe esse driver cadastrado no sistema");
        }

        if (driverRepository.existsByTelephoneIgnoringTenant(dto.driverAccept().telephone())) {
            throw new DuplicateResourceException("Já existe um user com esse telefone");
        }

        long countDriverInThisCustomer = countDriversInThisCustomer(customerId);

        if (countDriverInThisCustomer >= driverRegisterLimit(customerId)) {
            throw new EntityLimitExceededException("O limite de cadastro para Motoristas no seu plano é de " + driverRegisterLimit(customerId) + ". Para mais cadastros faça um upgrade ou personalize seu plano.");
        }

        // cria a CNH do motorista
        Cnh driverCnh = cnhService.createCnh(dto.driverAccept().cnhRequest(), customerId);

        Driver driver = driverRequestMapper.toEntity(dto);

        driver.setUserAccount(userAccount);
        driver.assignCustomer(customerId);
        driver.setStatus(GeneralStatus.ACTIVE);
        driver.setCnh(driverCnh);

        return driverRepository.save(driver);
    }

    @Transactional
    public DriverResponseDTO updateCurrentDriver(DriverUpdateDTO driverUpdateDTO) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Driver driverLogged = driverRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado pelo email: " + authenticatedUserEmail));

        if (driverLogged.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountModificationException("Não é possível modificar dados de uma conta inativa");
        }

        UserAccount userAccount = driverLogged.getUserAccount();

        // validação p evitar duplicação de recursos no sistema
        if (driverUpdateDTO.email() != null && !driverUpdateDTO.email().isBlank()) {
            if (!driverUpdateDTO.email().equals(userAccount.getEmail())) {
                if (userAccountRepository.existsByEmail(driverUpdateDTO.email())) {
                    throw new DuplicateResourceException("Email já em uso por outro usuário");
                }
            }
        }

        // telefone
        if (driverUpdateDTO.telephone() != null && !driverUpdateDTO.telephone().isBlank()) {
            if (!driverUpdateDTO.telephone().equals(driverLogged.getTelephone())) {
                if (driverRepository.existsByTelephone(driverUpdateDTO.telephone())) {
                    throw new DuplicateResourceException("Telefone já em uso por outro usuário");
                }
            }
        }

        // cpf
        if (driverUpdateDTO.cpf() != null && !driverUpdateDTO.cpf().isBlank()) {
            if (!driverUpdateDTO.cpf().equals(driverLogged.getCpf())) {
                if (driverRepository.existsByCpfIgnoringTenant(driverUpdateDTO.cpf())) {
                    throw new DuplicateResourceException("CPF já em uso por outro usuário");
                }
            }
        }

        // cnh number
        if (driverUpdateDTO.cnhUpdate().cnhNumber() != null && !driverUpdateDTO.cnhUpdate().cnhNumber().isBlank()) {
            if (!driverUpdateDTO.cnhUpdate().cnhNumber().equals(driverLogged.getCnh().getCnhNumber())) {
                if (driverRepository.existsByCnhNumberIgnoringTenant(driverUpdateDTO.cnhUpdate().cnhNumber())) {
                    throw new DuplicateResourceException("CNH Number já em uso por outro usuário");
                }
            }
        }

        driverRequestMapper.driverUpdateFromDTO(driverUpdateDTO, driverLogged);

        if (driverUpdateDTO.password() != null && !driverUpdateDTO.password().isBlank()) {
            userAccount.setPassword(passwordEncoder.encode(driverUpdateDTO.password()));
        }

        if (driverUpdateDTO.cnhUpdate().cnhCategories() != null && !driverUpdateDTO.cnhUpdate().cnhCategories().isEmpty()) {
            driverLogged.getCnh().getCnhCategories().addAll(driverUpdateDTO.cnhUpdate().cnhCategories());
        }

        Driver savedDriver = driverRepository.save(driverLogged);

        return driverResponseMapper.toDTO(savedDriver);
    }

    @Transactional
    public void updateDriver(UpdateStatusDTO driverStatus) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Driver driver = driverRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado para o email: " + authenticatedUserEmail));

        if (driver.getStatus() == driverStatus.status()) {
            throw new DuplicateResourceException("Motorista já com o status " + driverStatus);
        }

        driver.setStatus(driverStatus.status());

        driverRepository.save(driver);
    }

    protected long countDriversInThisCustomer(UUID customerId) {
        return driverRepository.countDriverInThisCustomer(customerId);
    }

    private long driverRegisterLimit(UUID customerId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        return customer.getPlan().getMaxAdministrators();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
