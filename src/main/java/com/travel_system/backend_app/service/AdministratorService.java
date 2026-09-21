package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.constants.GlobalAppConstants;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.AdministratorRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.AdministratorResponseMapper;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
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

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class AdministratorService {

    private final AdministratorRepository administratorRepository;
    private final UserAccountRepository userAccountRepository;

    private final CurrentUserService currentUserService;

    private final AdministratorRequestMapper administratorRequestMapper;
    private final AdministratorResponseMapper administratorResponseMapper;

    private final PasswordEncoder passwordEncoder;

    public AdministratorService(AdministratorRepository administratorRepository, UserAccountRepository userAccountRepository, CurrentUserService currentUserService, AdministratorRequestMapper administratorRequestMapper, AdministratorResponseMapper administratorResponseMapper, PasswordEncoder passwordEncoder) {
        this.administratorRepository = administratorRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentUserService = currentUserService;
        this.administratorRequestMapper = administratorRequestMapper;
        this.administratorResponseMapper = administratorResponseMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<AdministratorResponseDTO> getAllAdministrators(Pageable pageable) {
        Page<Administrator> allAdmins = administratorRepository.findAll(pageable);

        return allAdmins.map(administratorResponseMapper::toDTO);
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

    @Transactional
    public AdministratorResponseDTO createAdministrator(AdministratorRequestDTO admRequestDTO) {
        checkFieldsIsNull(admRequestDTO);

        long countAdministrators = administratorRepository.count();

        // plano básico: até 02 administradores no sistema
        if (countAdministrators >= GlobalAppConstants.ADMINISTRATOR_RECORD_LIMIT) {
            throw new EntityLimitExceededException("O limite de cadastro para Administradores no seu plano é de " + GlobalAppConstants.ADMINISTRATOR_RECORD_LIMIT + ". Para mais cadastros faça um upgrade ou personalize seu plano.");
        }

        // validações de duplicação de recursos no sistema
        if (userAccountRepository.existsByEmail(admRequestDTO.email())) throw new DuplicateResourceException("Email " + admRequestDTO.email()  + "já registrado");
        if (administratorRepository.existsByTelephone(admRequestDTO.telephone())) throw new DuplicateResourceException("Telefone " + admRequestDTO.telephone() + " já registrado");
        if (administratorRepository.existsByCpf(admRequestDTO.cpf())) throw new DuplicateResourceException("CPF já registrado");

        // cria novo UserAccount p/ o admin
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(admRequestDTO.email());
        userAccount.setPassword(passwordEncoder.encode(admRequestDTO.password()));
        userAccount.setPermissions(List.of());
        userAccount.setUserAccountType(UserAccountType.UNASSIGNED);

        UserAccount savedUserAccount = userAccountRepository.save(userAccount);

        // cria entidade Administrator com MapStruct
        Administrator administrator = administratorRequestMapper.toEntity(admRequestDTO);

        // faz o vínculo da userAccount
        administrator.setUserAccount(savedUserAccount);

        Administrator savedAdm = administratorRepository.save(administrator);

        return administratorResponseMapper.toDTO(savedAdm);
    }

    @Transactional
    public AdministratorResponseDTO updateCurrentAdministrator(AdministratorUpdateDTO admRequestDTO) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Administrator loggedAdm = administratorRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado para o email: " + authenticatedUserEmail));

        if (loggedAdm.getStatus() == GeneralStatus.INACTIVE) throw new InactiveAccountModificationException("Não é possível atualizar uma conta desativada");

        // validações de duplicação de recursos no sistema
        if (userAccountRepository.existsByEmail(admRequestDTO.email())) throw new DuplicateResourceException("Email " + admRequestDTO.email()  + "já registrado");
        if (administratorRepository.existsByTelephone(admRequestDTO.telephone())) throw new DuplicateResourceException("Telefone " + admRequestDTO.telephone() + " já registrado");

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

        if (expectedAdministrator.getStatus() == newStatus) throw new DuplicateResourceException("Administrador já está com status, " + newStatus);

        expectedAdministrator.setStatus(newStatus);

        administratorRepository.save(expectedAdministrator);
    }

    private void checkFieldsIsNull(AdministratorRequestDTO admRequestDTO) {
       if (admRequestDTO.email() == null || admRequestDTO.password() == null ||
               admRequestDTO.name() == null || admRequestDTO.cpf() == null || admRequestDTO.telephone() == null)  {
           throw new EmptyMandatoryFieldsFoundException("Você deve preencher todos os campos requeridos.");
       }
    }

}
