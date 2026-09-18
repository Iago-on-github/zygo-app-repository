package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFoundException;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.interfaces.mappers.DriverRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.DriverResponseMapper;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final PermissionsRepository permissionsRepository;
    private final UserAccountRepository userAccountRepository;

    private final PasswordEncoder passwordEncoder;

    private final DriverResponseMapper driverResponseMapper;
    private final DriverRequestMapper driverRequestMapper;

    public DriverService(DriverRepository driverRepository, PermissionsRepository permissionsRepository, UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, DriverResponseMapper driverResponseMapper, DriverRequestMapper driverRequestMapper) {
        this.driverRepository = driverRepository;
        this.permissionsRepository = permissionsRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.driverResponseMapper = driverResponseMapper;
        this.driverRequestMapper = driverRequestMapper;
    }

    @Transactional(readOnly = true)
    public Page<DriverResponseDTO> getAllDrivers() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Driver> allDrivers = driverRepository.findAll(pageable);

        return allDrivers.map(driverResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<DriverResponseDTO> getDriversByStatus(GeneralStatus newDriverStatus) {
        if (newDriverStatus == null) newDriverStatus = GeneralStatus.ACTIVE;

        List<Driver> driverByStatus = driverRepository.findAllByStatus(newDriverStatus);

        return driverByStatus.stream().map(driverResponseMapper::toDTO).toList();
    }

    @Transactional
    public DriverResponseDTO createDriver(DriverRequestDTO driverRequestDTO) {
        verifyFieldsIsNull(driverRequestDTO);

        // validações evitando duplicação de recursos no sistema
        if (userAccountRepository.existsByEmail(driverRequestDTO.email())) {
            throw new DuplicateResourceException("Email " + driverRequestDTO.email() + " já existe");
        }
        if (driverRepository.existsByTelephone(driverRequestDTO.telephone())) {
            throw new DuplicateResourceException("Telefone " + driverRequestDTO.telephone() + " já existe");
        }

/*        final String ROLE_DRIVER = "ROLE_DRIVER";
        Permissions driverPermission = permissionsRepository.findByDescription(ROLE_DRIVER)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + ROLE_DRIVER + " não encontrada."));*/

        // cria novo UserAccount p/ o driver
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(driverRequestDTO.email());
        userAccount.setPermissions(List.of());
        userAccount.setPassword(passwordEncoder.encode(driverRequestDTO.password()));
        userAccount.setUserAccountType(UserAccountType.UNASSIGNED);

        UserAccount savedAccount = userAccountRepository.save(userAccount);

        Driver driver = driverRequestMapper.toEntity(driverRequestDTO);

        // faz o vínculo do Driver com o UserAccount
        driver.setUserAccount(savedAccount);

        if (!driverRequestDTO.driverShifts().isEmpty()) {
            driver.setDriverShifts(driverRequestDTO.driverShifts());
        }

        Driver savedDriver = driverRepository.save(driver);

        return driverResponseMapper.toDTO(savedDriver);
    }

    @Transactional
    public DriverResponseDTO updateCurrentDriver(String authenticatedEmail, DriverUpdateDTO driverUpdateDTO) {
        Driver driverLogged = driverRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado: " + authenticatedEmail));

        if (driverLogged.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountModificationException("Não é possível modificar dados de uma conta inativa");
        }

        UserAccount userAccount = driverLogged.getUserAccount();

        // validação p evitar duplicação de recursos no sistema
        if (driverUpdateDTO.email() != null && !driverUpdateDTO.email().equals(userAccount.getEmail())) {
            if (userAccountRepository.existsByEmail(driverUpdateDTO.email())) {
                throw new DuplicateResourceException("Email já em uso por outro usuário");
            }
            userAccount.setEmail(driverUpdateDTO.email());
        }

        if (driverUpdateDTO.telephone() != null && !driverUpdateDTO.telephone().equals(driverLogged.getTelephone())) {
            if (driverRepository.existsByTelephone(driverUpdateDTO.telephone())) {
                throw new DuplicateResourceException("Telefone já em uso por outro usuário ");
            }
        }

        driverRequestMapper.driverUpdateFromDTO(driverUpdateDTO, driverLogged);

        if (driverUpdateDTO.password() != null && !driverUpdateDTO.password().isBlank()) {
            userAccount.setPassword(passwordEncoder.encode(driverUpdateDTO.password()));
        }

        Driver savedDriver = driverRepository.save(driverLogged);

        return driverResponseMapper.toDTO(savedDriver);
    }

    @Transactional(readOnly = true)
    public DriverResponseDTO getCurrentDriver(String email) {
        Driver getDriverLoggedProfile = driverRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado. Email: " + email));

        return driverResponseMapper.toDTO(getDriverLoggedProfile);
    }

    @Transactional
    public void updateDriver(UUID id, UpdateEntityStatusDTO driverStatus) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado, " + id));

        if (driver.getStatus() == driverStatus.status()) {
            throw new IllegalStateException("Motorista " + id + " já com o status " + driverStatus);
        }

        driverRequestMapper.driverUpdateStatusFromDTO(driverStatus, driver);

        driverRepository.save(driver);
    }

    private void verifyFieldsIsNull(DriverRequestDTO dto) {
        if (dto.email() == null || dto.password() == null ||
                dto.name() == null || dto.telephone() == null || dto.birthdate() == null || dto.driverShifts().isEmpty()) {
            throw new EmptyMandatoryFieldsFoundException("Você deve preencher todos os campos requeridos");
        }
    }

}
