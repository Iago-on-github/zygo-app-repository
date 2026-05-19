package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.interfaces.AdministratorMapper;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdministratorService {
    private final AdministratorRepository administratorRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionsRepository permissionsRepository;
    private final AdministratorMapper administratorMapper;

    @Autowired
    public AdministratorService(AdministratorRepository administratorRepository, PasswordEncoder passwordEncoder, PermissionsRepository permissionsRepository, AdministratorMapper administratorMapper) {
        this.administratorRepository = administratorRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionsRepository = permissionsRepository;
        this.administratorMapper = administratorMapper;
    }

    public List<AdministratorResponseDTO> getAllAdministrators() {
        List<Administrator> allAdmins = administratorRepository.findAll();

        return allAdmins.stream().map(this::admConverted).toList();
    }

    public List<AdministratorResponseDTO> getAllAdministratorsByStatus(GeneralStatus status) {
        if (status == null) status = GeneralStatus.ACTIVE;

        List<Administrator> administrators = administratorRepository.findByStatus(status);

        return administrators.stream().map(this::admConverted).toList();
    }

    public AdministratorResponseDTO getCurrentAdministrator(String authenticatedAdmEmail) {
        Administrator expectedLoggedAdmin = administratorRepository.findByEmail(authenticatedAdmEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado"));

        return admConverted(expectedLoggedAdmin);
    }

    @Transactional
    public AdministratorResponseDTO createAdministrator(AdministratorRequestDTO admRequestDTO) {
        checkFieldsIsNull(admRequestDTO);

        Administrator adm = admMapper(admRequestDTO);

        Optional<Administrator> existingAdministratorEmail = administratorRepository.findByEmail(adm.getEmail());
        Optional<Administrator> existingAdministratorTelephone = administratorRepository.findByTelephone(adm.getTelephone());

        if (existingAdministratorEmail.isPresent()) throw new DuplicateResourceException("Email já registrado");
        if (existingAdministratorTelephone.isPresent()) throw new DuplicateResourceException("Telefone já registrado");

        final String ROLE_ADMIN = "ROLE_ADMIN";
        Permissions admPerm = permissionsRepository.findByDescription(ROLE_ADMIN)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + ROLE_ADMIN + " não encontrada."));

        adm.setPermissions(List.of(admPerm));

        Administrator savedAdm = administratorRepository.save(adm);
        return admConverted(savedAdm);
    }

    @Transactional
    public AdministratorResponseDTO updateCurrentAdministrator(String authenticatedEmail, AdministratorUpdateDTO admRequestDTO) {
        Administrator loggedAdm = administratorRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado, " + authenticatedEmail));

        if (loggedAdm.getStatus().equals(GeneralStatus.INACTIVE)) throw new InactiveAccountModificationException("Não é possível atualizar uma conta desativada");

        if (admRequestDTO.email() != null || admRequestDTO.telephone() != null) {
            administratorRepository.findByEmailOrTelephoneAndIdNot(
                    admRequestDTO.email(),
                    admRequestDTO.telephone(),
                    loggedAdm.getId())
                    .ifPresent(admin -> {
                        throw new DuplicateResourceException("Email ou telefone já em uso por outro usuário.");
                    });
        }

        administratorMapper.administratorUpdateFromDTO(admRequestDTO, loggedAdm);

        if (admRequestDTO.password() != null) {
            loggedAdm.setPassword(passwordEncoder.encode(admRequestDTO.password()));
        }

        loggedAdm.setUpdatedAt(LocalDateTime.now());

        Administrator savedAdmin = administratorRepository.save(loggedAdm);
        return admConverted(savedAdmin);
    }

    @Transactional
    public void updateAdministrator(UUID id, GeneralStatus newStatus) {
        Administrator expectedAdministrator = administratorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado: " + id));

        if (expectedAdministrator.getStatus() == newStatus) throw new DuplicateResourceException("Administrador já está com status, " + newStatus);

        expectedAdministrator.setStatus(newStatus);

        administratorRepository.save(expectedAdministrator);
    }

    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES

    private void checkFieldsIsNull(AdministratorRequestDTO admRequestDTO) {
       if (admRequestDTO.email() == null || admRequestDTO.password() == null ||
               admRequestDTO.name() == null || admRequestDTO.cpf() == null || admRequestDTO.telephone() == null)  {
           throw new EmptyMandatoryFieldsFound("Você deve preencher todos os campos requeridos.");
       }
    }

    private Administrator admMapper(AdministratorRequestDTO admRequestDto) {
        Administrator adm = new Administrator();

        adm.setEmail(admRequestDto.email());
        adm.setPassword(passwordEncoder.encode(admRequestDto.password()));
        adm.setName(admRequestDto.name());
        adm.setLastName(admRequestDto.lastName());
        adm.setCpf(admRequestDto.cpf());
        adm.setBirthDate(admRequestDto.birthDate());
        adm.setTelephone(admRequestDto.telephone());
        adm.setStatus(GeneralStatus.ACTIVE);
        adm.setCreatedAt(LocalDateTime.now());
        adm.setProfilePicture(admRequestDto.profilePicture());

        return adm;
    }

    private AdministratorResponseDTO admConverted(Administrator adm) {
        return new AdministratorResponseDTO(
                adm.getId(),
                adm.getEmail(),
                adm.getName(),
                adm.getLastName(),
                adm.getBirthDate(),
                adm.getTelephone(),
                adm.getProfilePicture(),
                adm.getStatus(),
                adm.getCreatedAt(),
                adm.getUpdatedAt()
        );
    }
}
