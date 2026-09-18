package com.travel_system.backend_app.service;

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

@Service
public class AdministratorService {

    private final AdministratorRepository administratorRepository;
    private final CustomerRepository customerRepository;
    private final PermissionsRepository permissionsRepository;
    private final UserAccountRepository userAccountRepository;

    private final CurrentUserService currentUserService;

    private final AdministratorRequestMapper administratorRequestMapper;
    private final AdministratorResponseMapper administratorResponseMapper;

    private final PasswordEncoder passwordEncoder;

    public AdministratorService(AdministratorRepository administratorRepository, CustomerRepository customerRepository, PasswordEncoder passwordEncoder, PermissionsRepository permissionsRepository, UserAccountRepository userAccountRepository, AdministratorRequestMapper administratorRequestMapper, CurrentUserService currentUserService, AdministratorResponseMapper administratorResponseMapper) {
        this.administratorRepository = administratorRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionsRepository = permissionsRepository;
        this.userAccountRepository = userAccountRepository;
        this.administratorRequestMapper = administratorRequestMapper;
        this.currentUserService = currentUserService;
        this.administratorResponseMapper = administratorResponseMapper;
    }

    @Transactional(readOnly = true)
    public Page<AdministratorResponseDTO> getAllAdministrators() {

        /*
        * realiza a valdação com base em quem está fazendo a requisição
        * platformADMIN pode recuperar todos
        * admin normal somente aqueles do seu customer
        * */

        Pageable pageable = PageRequest.of(0, 10);

        boolean platformAdmin = currentUserService.isPlatformAdmin();

        Page<Administrator> allAdmins;

        if (platformAdmin) {
            allAdmins = administratorRepository.findAll(pageable);
        } else {
            allAdmins = administratorRepository.findAllWithCustomerId(pageable);
        }

        return allAdmins.map(administratorResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<AdministratorResponseDTO> getAllAdministratorsByStatus(GeneralStatus status) {

        /*
         * realiza a valdação com base em quem está fazendo a requisição
         * platformADMIN pode recuperar todos
         * admin normal somente aqueles do seu customer
         *
         * status sendo enviado como NULL = seta automaticamente para ACTIVE
         * */

        if (status == null) status = GeneralStatus.ACTIVE;

        Pageable pageable = PageRequest.of(0, 10);

        boolean platformAdmin = currentUserService.isPlatformAdmin();

        Page<Administrator> administrators;
        if (platformAdmin) {
            administrators = administratorRepository.findByStatusWithCustomerId(status, pageable);
        } else {
            administrators = administratorRepository.findByStatus(status, pageable);
        }

        return administrators.map(administratorResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public AdministratorResponseDTO getCurrentAdministrator(String authenticatedAdmEmail) {
        Administrator expectedLoggedAdmin = administratorRepository.findByEmail(authenticatedAdmEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado"));

        return administratorResponseMapper.toDTO(expectedLoggedAdmin);
    }

    @Transactional
    public AdministratorResponseDTO createAdministrator(AdministratorRequestDTO admRequestDTO) {
        checkFieldsIsNull(admRequestDTO);

        // validações de duplicação de recursos no sistema
        if (userAccountRepository.existsByEmail(admRequestDTO.email())) throw new DuplicateResourceException("Email " + admRequestDTO.email()  + "já registrado");
        if (administratorRepository.existsByTelephone(admRequestDTO.telephone())) throw new DuplicateResourceException("Telefone " + admRequestDTO.telephone() + " já registrado");
        if (administratorRepository.existsByCpf(admRequestDTO.cpf())) throw new DuplicateResourceException("CPF já registrado");

/*        final String ROLE_ADMIN = "ROLE_ADMIN";
        Permissions admPerm = permissionsRepository.findByDescription(ROLE_ADMIN)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + ROLE_ADMIN + " não encontrada."));*/

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
    public AdministratorResponseDTO updateCurrentAdministrator(String authenticatedEmail, AdministratorUpdateDTO admRequestDTO) {
        Administrator loggedAdm = administratorRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado, " + authenticatedEmail));

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
    public void updateAdministrator(UUID id, GeneralStatus newStatus) {
        Administrator expectedAdministrator = administratorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Administrador não encontrado: " + id));

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
