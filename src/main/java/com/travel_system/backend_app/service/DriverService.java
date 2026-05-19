package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
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
    private final PasswordEncoder passwordEncoder;
    private final PermissionsRepository permissionsRepository;

    public DriverService(DriverRepository repository, PasswordEncoder passwordEncoder, PermissionsRepository permissionsRepository) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.permissionsRepository = permissionsRepository;
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
        Driver newDriver = driverMapper(driverRequestDTO);

        verifyFieldsIsNull(driverRequestDTO);

        Optional<Driver> email = repository.findByEmail(newDriver.getEmail());
        Optional<Driver> telephone = repository.findByTelephone(newDriver.getTelephone());

        if (email.isPresent()) throw new DuplicateResourceException("Email já existe");
        if (telephone.isPresent()) throw new DuplicateResourceException("Telefone já existe");

        newDriver.setCreatedAt(LocalDateTime.now());
        newDriver.setStatus(GeneralStatus.ACTIVE);

        final String ROLE_DRIVER = "ROLE_DRIVER";
        Permissions admPerm = permissionsRepository.findByDescription(ROLE_DRIVER)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + ROLE_DRIVER + " não encontrada."));

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

        // faz upgrade gradual dos campos
        updateDriverFields(driverLogged, driverUpdateDTO);

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
        newDriver.setProfilePicture(requestDTO.profilePicture());
        newDriver.setAreaOfActivity(requestDTO.areaOfActivity());

        return newDriver;
    }

    private void verifyFieldsIsNull(DriverRequestDTO dto) {
        if (dto.email() == null || dto.password() == null ||
                dto.name() == null || dto.telephone() == null || dto.areaOfActivity() == null) {
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
                driver.getProfilePicture(),
                driver.getCreatedAt(),
                driver.getStatus(),
                driver.getAreaOfActivity(),
                driver.getTotalTrips()
        );
    }

    private void updateDriverFields(Driver driverLogged, DriverUpdateDTO driverUpdateDTO) {
        // atualiza senha
        if (driverUpdateDTO.password() != null && !driverUpdateDTO.password().isBlank()) {
            driverLogged.setPassword(passwordEncoder.encode(driverUpdateDTO.password()));
        }

        // atualizações parciais das props
        if (driverUpdateDTO.name() != null) {
            driverLogged.setName(driverUpdateDTO.name());
        }

        if (driverUpdateDTO.lastName() != null) {
            driverLogged.setLastName(driverUpdateDTO.lastName());
        }

        if (driverUpdateDTO.profilePicture() != null) {
            driverLogged.setProfilePicture(driverUpdateDTO.profilePicture());
        }

        if (driverUpdateDTO.areaOfActivity() != null) {
            driverLogged.setAreaOfActivity(driverUpdateDTO.areaOfActivity());
        }
    }
}
