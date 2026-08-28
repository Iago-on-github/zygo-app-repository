package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.AdministratorMapper;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdministratorService {
    private final AdministratorRepository administratorRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionsRepository permissionsRepository;
    private final AdministratorMapper administratorMapper;
    private final CurrentUserService currentUserService;

    @Autowired
    public AdministratorService(AdministratorRepository administratorRepository, CustomerRepository customerRepository, PasswordEncoder passwordEncoder, PermissionsRepository permissionsRepository, AdministratorMapper administratorMapper, CurrentUserService currentUserService) {
        this.administratorRepository = administratorRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionsRepository = permissionsRepository;
        this.administratorMapper = administratorMapper;
        this.currentUserService = currentUserService;
    }

    public Page<AdministratorResponseDTO> getAllAdministrators() {
        Pageable pageable = PageRequest.of(0, 10);

        boolean platformAdmin = currentUserService.isPlatformAdmin();

        Page<Administrator> allAdmins;

        if (platformAdmin) {
            allAdmins = administratorRepository.findAll(pageable);
        } else {
            allAdmins = administratorRepository.findAllWithCustomerId(pageable);
        }

        return allAdmins.map(this::admConverted);
    }

    public Page<AdministratorResponseDTO> getAllAdministratorsByStatus(GeneralStatus status) {
        if (status == null) status = GeneralStatus.ACTIVE;

        Pageable pageable = PageRequest.of(0, 10);

        boolean platformAdmin = currentUserService.isPlatformAdmin();

        Page<Administrator> administrators;
        if (platformAdmin) {
            administrators = administratorRepository.findByStatusWithCustomerId(status, pageable);
        } else {
            administrators = administratorRepository.findByStatus(status, pageable);
        }

        return administrators.map(this::admConverted);
    }

    public AdministratorResponseDTO getCurrentAdministrator(String authenticatedAdmEmail) {
        Administrator expectedLoggedAdmin = administratorRepository.findByEmail(authenticatedAdmEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado"));

        return admConverted(expectedLoggedAdmin);
    }

    @Transactional
    public AdministratorResponseDTO createAdministrator(AdministratorRequestDTO admRequestDTO) {
        checkFieldsIsNull(admRequestDTO);

        Optional<Administrator> existingAdministratorEmail = administratorRepository.findByEmail(admRequestDTO.email());
        Optional<Administrator> existingAdministratorTelephone = administratorRepository.findByTelephone(admRequestDTO.telephone());

        if (existingAdministratorEmail.isPresent()) throw new DuplicateResourceException("Email já registrado");
        if (existingAdministratorTelephone.isPresent()) throw new DuplicateResourceException("Telefone já registrado");

        final String ROLE_ADMIN = "ROLE_ADMIN";
        Permissions admPerm = permissionsRepository.findByDescription(ROLE_ADMIN)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + ROLE_ADMIN + " não encontrada."));

        Customer customer = customerRepository.findById(admRequestDTO.customerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer " + admRequestDTO.customerId() + " não encontrado"));

        Administrator adm = admMapper(admRequestDTO);

        adm.setPermissions(List.of(admPerm));
        adm.setCustomerId(customer.getId());

        Administrator savedAdm = administratorRepository.save(adm);
        return admConverted(savedAdm);
    }

    @Transactional
    public AdministratorResponseDTO createPlatformAdministrator(PlatformAdministratorRequestDTO platformAdmRequestDTO) {
        boolean platformAdmin = currentUserService.isPlatformAdmin();

        if (!platformAdmin) {
            throw new NotAuthorizedException("Administrador sem permissão necessária para criar Administradores de Plataforma.");
        }

        checkFieldsIsNull(platformAdmRequestDTO);

        Optional<Administrator> existingAdministratorEmail = administratorRepository.findByEmail(platformAdmRequestDTO.email());
        Optional<Administrator> existingAdministratorTelephone = administratorRepository.findByTelephone(platformAdmRequestDTO.telephone());

        if (existingAdministratorEmail.isPresent()) throw new DuplicateResourceException("Email já registrado");
        if (existingAdministratorTelephone.isPresent()) throw new DuplicateResourceException("Telefone já registrado");

        final String ROLE_PLATFORM = "ROLE_PLATFORM_ADMIN";
        Permissions admPerm = permissionsRepository.findByDescription(ROLE_PLATFORM)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + ROLE_PLATFORM + " não encontrada."));

        Administrator adm = admPlatformMapper(platformAdmRequestDTO);

        adm.setPermissions(List.of(admPerm));

        Administrator savedAdm = administratorRepository.save(adm);
        return admConverted(savedAdm);
    }

    @Transactional
    public AdministratorResponseDTO updateCurrentAdministrator(String authenticatedEmail, AdministratorUpdateDTO admRequestDTO) {
        boolean platformAdmin = currentUserService.isPlatformAdmin();

        if (!platformAdmin) {
            throw new NotAuthorizedException("Administrador sem permissão necessária para alterar Administratores de Plataforma.");
        }

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
        boolean platformAdmin = currentUserService.isPlatformAdmin();

/*        if (!platformAdmin) {
            throw new NotAuthorizedException("Administrador sem permissão necessária para alterar Administratores de Plataforma.");
        }*/

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
               admRequestDTO.name() == null || admRequestDTO.cpf() == null || admRequestDTO.telephone() == null ||
       admRequestDTO.customerId() == null)  {
           throw new EmptyMandatoryFieldsFound("Você deve preencher todos os campos requeridos.");
       }
    }

    private void checkFieldsIsNull(PlatformAdministratorRequestDTO platformAdmRequestDTO) {
        if (platformAdmRequestDTO.email() == null || platformAdmRequestDTO.password() == null ||
                platformAdmRequestDTO.name() == null || platformAdmRequestDTO.cpf() == null || platformAdmRequestDTO.telephone() == null)  {
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

        return adm;
    }

    private Administrator admPlatformMapper(PlatformAdministratorRequestDTO admRequestDto) {
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

        return adm;
    }

    private AdministratorResponseDTO admConverted(Administrator adm) {
        UUID customerId = adm.getCustomerId() != null ? adm.getCustomerId() : null;

        return new AdministratorResponseDTO(
                adm.getId(),
                adm.getEmail(),
                adm.getName(),
                adm.getLastName(),
                adm.getBirthDate(),
                adm.getTelephone(),
                currentUserService.getPublicUrl(adm.getProfilePicture()),
                adm.getStatus(),
                adm.getCreatedAt(),
                adm.getUpdatedAt(),
                customerId
        );
    }
}
