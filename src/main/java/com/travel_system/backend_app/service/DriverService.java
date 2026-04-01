package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
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

    public List<DriverResponseDTO> getAllActiveDrivers() {
        return getDriversByStatus(GeneralStatus.ACTIVE);
    }

    public List<DriverResponseDTO> getAllInactiveDrivers() {
        return getDriversByStatus(GeneralStatus.INACTIVE);
    }

    @Transactional
    public DriverResponseDTO createDriver(DriverRequestDTO driverRequestDTO) {
        Driver newDriver = driverMapper(driverRequestDTO);

        verifyFieldsIsNull(driverRequestDTO);

        Optional<Driver> email = repository.findByEmail(newDriver.getEmail());
        Optional<Driver> telephone = repository.findByTelephone(newDriver.getTelephone());

        if (email.isPresent()) throw new DuplicateResourceException("Email já existe");
        if (telephone.isPresent()) throw new DuplicateResourceException("Telefone já existe");

        String rawPassword = newDriver.getPassword();
        newDriver.setPassword(passwordEncoder.encode(rawPassword));

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
    public DriverResponseDTO updateLoggedDriver(String authenticatedEmail, DriverRequestDTO driverRequestDTO) {
        Driver driverLogged = repository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado, " + authenticatedEmail));

        if (driverLogged.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new InactiveAccountModificationException("Não é possível modificar dados de uma conta inativa");
        }

        Optional<Driver> existingUser = repository.findByEmailOrTelephoneAndIdNot(
                driverRequestDTO.email(),
                driverRequestDTO.telephone(),
                driverLogged.getId()
        );

        if (existingUser.isPresent()) throw new DuplicateResourceException("Email ou telefone já em uso por outro usuário.");

        driverLogged.setEmail(driverRequestDTO.email());
        driverLogged.setPassword(driverRequestDTO.password());
        driverLogged.setName(driverRequestDTO.name());
        driverLogged.setLastName(driverRequestDTO.lastName());
        driverLogged.setTelephone(driverRequestDTO.telephone());
        driverLogged.setProfilePicture(driverRequestDTO.profilePicture());
        driverLogged.setAreaOfActivity(driverRequestDTO.areaOfActivity());

        Driver savedDriver = repository.save(driverLogged);
        return driverConverted(savedDriver);
    }

    public DriverResponseDTO getLoggedInDriverProfile(String email) {
        Driver getDriverLoggedProfile = repository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado. Email: " + email));
        return driverConverted(getDriverLoggedProfile) ;
    }

    @Transactional
    public void disableDriver(UUID id) {
        Driver driver = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado, " + id));

        if (driver.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new InactiveAccountModificationException("Driver já desativado, " + id);
        }

        driver.setStatus(GeneralStatus.INACTIVE);

        repository.save(driver);
    }

    // METODOS AUXILIARES
    // METODOS AUXILIARES
    // METODOS AUXILIARES

    private List<DriverResponseDTO> getDriversByStatus(GeneralStatus status) {
        List<Driver> drivers = repository.findAllByStatus(status);

        return drivers.stream().map(this::driverConverted).toList();
    }

    private Driver driverMapper(DriverRequestDTO requestDTO) {
        Driver newDriver = new Driver();

        newDriver.setEmail(requestDTO.email());
        newDriver.setPassword(requestDTO.password());
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
                driver.getCreatedAt(),
                driver.getStatus(),
                driver.getAreaOfActivity(),
                driver.getTotalTrips()
        );
    }
}
