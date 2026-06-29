package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.interfaces.mappers.DriverMapper;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CityResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverService {
    private final DriverRepository repository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionsRepository permissionsRepository;
    private final DriverMapper driverMapper;
    private final CurrentUserService currentUserService;

    public DriverService(DriverRepository repository, CustomerRepository customerRepository, PasswordEncoder passwordEncoder, PermissionsRepository permissionsRepository, DriverMapper driverMapper, CurrentUserService currentUserService) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionsRepository = permissionsRepository;
        this.driverMapper = driverMapper;
        this.currentUserService = currentUserService;
    }

    public List<DriverResponseDTO> getAllDrivers() {
        List<Driver> allDrivers = repository.findAll();

        return allDrivers.stream().map(this::driverConverted).toList();
    }

    public List<DriverResponseDTO> getDriversByStatus(GeneralStatus newDriverStatus) {
        if (newDriverStatus == null) newDriverStatus = GeneralStatus.ACTIVE;

        List<Driver> driverByStatus = repository.findAllByStatus(newDriverStatus);

        return driverByStatus.stream().map(this::driverConverted).toList();
    }

    @Transactional
    public DriverResponseDTO createDriver(DriverRequestDTO driverRequestDTO) {
        verifyFieldsIsNull(driverRequestDTO);

        Optional<Driver> email = repository.findByEmail(driverRequestDTO.email());
        Optional<Driver> telephone = repository.findByTelephone(driverRequestDTO.telephone());

        if (email.isPresent()) throw new DuplicateResourceException("Email " + driverRequestDTO.email() + " já existe");
        if (telephone.isPresent()) throw new DuplicateResourceException("Telefone " + driverRequestDTO.telephone() + " já existe");

        Driver newDriver = driverMapper(driverRequestDTO);

        newDriver.setCreatedAt(LocalDateTime.now());
        newDriver.setStatus(GeneralStatus.ACTIVE);

        Customer customer = customerRepository.findById(driverRequestDTO.customerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer " + driverRequestDTO.customerId() + " não encontrado."));

        final String ROLE_DRIVER = "ROLE_DRIVER";
        Permissions admPerm = permissionsRepository.findByDescription(ROLE_DRIVER)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + ROLE_DRIVER + " não encontrada."));

        newDriver.setCustomer(customer);
        newDriver.setPermissions(List.of(admPerm));

        Driver savedDriver = repository.save(newDriver);
        return driverConverted(savedDriver);
    }

    @Transactional
    public DriverResponseDTO updateCurrentDriver(String authenticatedEmail, DriverUpdateDTO driverUpdateDTO) {
        Driver driverLogged = repository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado: " + authenticatedEmail));

        if (driverLogged.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new InactiveAccountModificationException("Não é possível modificar dados de uma conta inativa");
        }

        // verifica email duplicado
        if (driverUpdateDTO.email() != null && !driverUpdateDTO.email().equals(driverLogged.getEmail())) {
            boolean emailAlreadyExists = repository.findByEmail(driverUpdateDTO.email()).isPresent();

            if (emailAlreadyExists) {
                throw new DuplicateResourceException("Email já em uso por outro usuário.");
            }

            driverLogged.setEmail(driverUpdateDTO.email());
        }

        // verifica telefone duplicado
        if (driverUpdateDTO.telephone() != null && !driverUpdateDTO.telephone().equals(driverLogged.getTelephone())) {
            boolean telephoneAlreadyExists = repository.findByTelephone(driverUpdateDTO.telephone()).isPresent();

            if (telephoneAlreadyExists) {
                throw new DuplicateResourceException("Telefone já em uso por outro usuário.");
            }

            driverLogged.setTelephone(driverUpdateDTO.telephone());
        }

        // mapStructure para atualização parcial
        driverMapper.driverUpdateFromDTO(driverUpdateDTO, driverLogged);

        if (driverUpdateDTO.password() != null && !driverUpdateDTO.password().isBlank()) {
            driverLogged.setPassword(passwordEncoder.encode(driverUpdateDTO.password()));
        }

        Driver savedDriver = repository.save(driverLogged);

        return driverConverted(savedDriver);
    }

    public DriverResponseDTO getCurrentDriver(String email) {
        Driver getDriverLoggedProfile = repository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado. Email: " + email));
        return driverConverted(getDriverLoggedProfile) ;
    }

    @Transactional
    public void updateDriver(UUID id, UpdateEntityStatusDTO driverStatus) {
        Driver driver = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado, " + id));

        if (driver.getStatus().equals(driverStatus.status())) {
            throw new DuplicateResourceException("Motorista " + id + " já com o status " + driverStatus);
        }

        driver.setStatus(driverStatus.status());
        driver.setUpdatedAt(LocalDateTime.now());

        repository.save(driver);
    }

    // METODOS AUXILIARES
    // METODOS AUXILIARES
    // METODOS AUXILIARES

    private Driver driverMapper(DriverRequestDTO requestDTO) {
        Driver newDriver = new Driver();

        newDriver.setEmail(requestDTO.email());
        newDriver.setPassword(passwordEncoder.encode(requestDTO.password()));
        newDriver.setName(requestDTO.name());
        newDriver.setLastName(requestDTO.lastName());
        newDriver.setTelephone(requestDTO.telephone());
        newDriver.setAreaOfActivity(requestDTO.areaOfActivity());

        return newDriver;
    }

    private void verifyFieldsIsNull(DriverRequestDTO dto) {
        if (dto.email() == null || dto.password() == null ||
                dto.name() == null || dto.telephone() == null || dto.customerId() == null) {
            throw new EmptyMandatoryFieldsFound("Você deve preencher todos os campos requeridos");
        }
    }

    private DriverResponseDTO driverConverted(Driver driver) {
        return new DriverResponseDTO(
                driver.getId(),
                driver.getName(),
                driver.getLastName(),
                driver.getEmail(),
                driver.getTelephone(),
                currentUserService.getPublicUrl(driver.getProfilePicture()),
                driver.getCreatedAt(),
                driver.getStatus(),
                driver.getAreaOfActivity(),
                driver.getTotalTrips(),
                driver.getCustomer().getId()
        );
    }
}
