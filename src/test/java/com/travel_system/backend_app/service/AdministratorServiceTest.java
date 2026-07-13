package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.AdministratorMapper;
import com.travel_system.backend_app.interfaces.mappers.CustomerMapper;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.parameters.P;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministratorServiceTest {

    @InjectMocks
    private AdministratorService administratorService;

    @Mock
    private AdministratorRepository administratorRepository;
    @Mock
    private PermissionsRepository permissionsRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AdministratorMapper administratorMapper;
    @Mock
    private CurrentUserService currentUserService;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    ArgumentCaptor<Administrator> admCaptor = ArgumentCaptor.forClass(Administrator.class);

    private AdministratorRequestDTO validDto;
    private PlatformAdministratorRequestDTO validPlatformDto;
    private Customer customerEntity;
    private Administrator adminEntity;

    private final Pageable expectedPageable = PageRequest.of(0, 10);
    private final String authEmail = "auth_admin@test.com";

    @BeforeEach
    void setUp() {
        customerEntity = new Customer();
        customerEntity.setId(UUID.randomUUID());

        adminEntity = new Administrator();
        adminEntity.setId(UUID.randomUUID());
        adminEntity.setEmail("admin@test.com");
        adminEntity.setName("Nome");
        adminEntity.setLastName("Sobrenome");
        adminEntity.setProfilePicture("path/to/pic.jpg");
        adminEntity.setCreatedAt(LocalDateTime.now());
        adminEntity.setCustomer(customerEntity);

        validDto = new AdministratorRequestDTO(
                "new_admin@test.com",
                "securePassword123",
                "Lucas",
                "Mendes",
                "12345678901",
                "03/03/2000",
                "11988888888",
                customerEntity.getId()
        );

        validPlatformDto = new PlatformAdministratorRequestDTO(
                "platform_admin@test.com",
                "superSecurePassword123",
                "Gabriel",
                "Costa",
                "98765432100",
                LocalDate.of(1990, 1, 1).toString(),
                "11977777777"
        );
    }

    @Nested
    class getAllAdministrators {

        @Test
        @DisplayName("Deve retornar a listagem completa de administradores quando o usuário logado for admin da plataforma")
        void shouldReturnAllAdministratorsWhenLoggedUserIsPlatformAdmin() {
            Page<Administrator> pagedAdmins = new PageImpl<>(List.of(adminEntity));

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findAll(expectedPageable)).thenReturn(pagedAdmins);
            when(currentUserService.getPublicUrl(adminEntity.getProfilePicture())).thenReturn("http://s3.url/pic.jpg");

            Page<AdministratorResponseDTO> result = administratorService.getAllAdministrators();

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(adminEntity.getEmail(), result.getContent().get(0).email());
            assertEquals(customerEntity.getId(), result.getContent().get(0).customerId());

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(administratorRepository, times(1)).findAll(expectedPageable);
            verify(administratorRepository, never()).findAllWithCustomerId(any(Pageable.class));
        }

        @Test
        @DisplayName("Deve retornar a listagem filtrada de administradores quando o usuário logado for admin comum (Customer Admin)")
        void shouldReturnFilteredAdministratorsWhenLoggedUserIsCustomerAdmin() {
            Page<Administrator> pagedAdmins = new PageImpl<>(List.of(adminEntity));

            when(currentUserService.isPlatformAdmin()).thenReturn(false);
            when(administratorRepository.findAllWithCustomerId(expectedPageable)).thenReturn(pagedAdmins);
            when(currentUserService.getPublicUrl(adminEntity.getProfilePicture())).thenReturn("http://s3.url/pic.jpg");

            Page<AdministratorResponseDTO> result = administratorService.getAllAdministrators();

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(adminEntity.getEmail(), result.getContent().get(0).email());
            assertEquals(customerEntity.getId(), result.getContent().get(0).customerId());

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(administratorRepository, times(1)).findAllWithCustomerId(expectedPageable);
            verify(administratorRepository, never()).findAll(any(Pageable.class));
        }
    }

    @Nested
    class getAllAdministratorsByStatus {

        @Test
        @DisplayName("Deve filtrar por status chamando findByStatusWithCustomerId quando for administrador da plataforma")
        void shouldFilterByStatusWhenLoggedUserIsPlatformAdmin() {
            GeneralStatus targetStatus = GeneralStatus.INACTIVE;
            Page<Administrator> pagedAdmins = new PageImpl<>(List.of(adminEntity));

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByStatusWithCustomerId(targetStatus, expectedPageable)).thenReturn(pagedAdmins);
            when(currentUserService.getPublicUrl(adminEntity.getProfilePicture())).thenReturn("http://s3.url/pic.jpg");

            Page<AdministratorResponseDTO> result = administratorService.getAllAdministratorsByStatus(targetStatus);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(administratorRepository, times(1)).findByStatusWithCustomerId(targetStatus, expectedPageable);
            verify(administratorRepository, never()).findByStatus(any(), any());
        }

        @Test
        @DisplayName("Deve filtrar por status chamando findByStatus quando for administrador comum")
        void shouldFilterByStatusWhenLoggedUserIsCustomerAdmin() {
            GeneralStatus targetStatus = GeneralStatus.ACTIVE;
            Page<Administrator> pagedAdmins = new PageImpl<>(List.of(adminEntity));

            when(currentUserService.isPlatformAdmin()).thenReturn(false);
            when(administratorRepository.findByStatus(targetStatus, expectedPageable)).thenReturn(pagedAdmins);
            when(currentUserService.getPublicUrl(adminEntity.getProfilePicture())).thenReturn("http://s3.url/pic.jpg");

            Page<AdministratorResponseDTO> result = administratorService.getAllAdministratorsByStatus(targetStatus);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(administratorRepository, times(1)).findByStatus(targetStatus, expectedPageable);
            verify(administratorRepository, never()).findByStatusWithCustomerId(any(), any());
        }

        @Test
        @DisplayName("Deve usar status ACTIVE como fallback quando o parâmetro informado for nulo")
        void shouldFallbackToActiveStatusWhenParameterIsNull() {
            Page<Administrator> pagedAdmins = new PageImpl<>(List.of(adminEntity));

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByStatusWithCustomerId(GeneralStatus.ACTIVE, expectedPageable)).thenReturn(pagedAdmins);
            when(currentUserService.getPublicUrl(adminEntity.getProfilePicture())).thenReturn("http://s3.url/pic.jpg");

            // Passando explicitamente o status nulo para testar o fallback
            Page<AdministratorResponseDTO> result = administratorService.getAllAdministratorsByStatus(null);

            assertNotNull(result);

            verify(currentUserService, times(1)).isPlatformAdmin();
            // Garante que o repositório foi acionado com GeneralStatus.ACTIVE mesmo o input inicial sendo nulo
            verify(administratorRepository, times(1)).findByStatusWithCustomerId(GeneralStatus.ACTIVE, expectedPageable);
        }
    }

    @Nested
    class getCurrentAdministrator {
        @Test
        @DisplayName("Deve retornar o DTO mapeado corretamente quando o administrador for localizado pelo email")
        void shouldReturnAdministratorResponseDTOWhenAdminIsFoundByEmail() {
            String expectedPublicUrl = "https://s3.amazonaws.com/bucket/carlos.jpg";

            when(administratorRepository.findByEmail(authEmail)).thenReturn(Optional.of(adminEntity));
            when(currentUserService.getPublicUrl(adminEntity.getProfilePicture())).thenReturn(expectedPublicUrl);

            AdministratorResponseDTO result = administratorService.getCurrentAdministrator(authEmail);

            assertNotNull(result);
            assertEquals(adminEntity.getId(), result.id());
            assertEquals(adminEntity.getEmail(), result.email());
            assertEquals(adminEntity.getName(), result.name());
            assertEquals(expectedPublicUrl, result.profilePicture());
            assertEquals(customerEntity.getId(), result.customerId());

            verify(administratorRepository, times(1)).findByEmail(authEmail);
            verify(currentUserService, times(1)).getPublicUrl(adminEntity.getProfilePicture());
        }

        @Test
        @DisplayName("Deve lancar EntityNotFoundException quando o email autenticado nao for encontrado na base")
        void shouldThrowEntityNotFoundExceptionWhenAuthenticatedEmailDoesNotExist() {
            String nonExistingEmail = "ghost@test.com";

            when(administratorRepository.findByEmail(nonExistingEmail)).thenReturn(Optional.empty());

            EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
                administratorService.getCurrentAdministrator(nonExistingEmail);
            });

            assertEquals("Administrador não encontrado", exception.getMessage());

            verify(administratorRepository, times(1)).findByEmail(nonExistingEmail);
            verifyNoInteractions(currentUserService);
        }
    }

    @Nested
    class createAdministrator {

        @Test
        @DisplayName("Deve cadastrar administrador com sucesso quando todas as validações passarem")
        void shouldCreateAdministratorWithSuccess() {
            String encryptedPassword = "encrypted_pwd";

            when(administratorRepository.findByEmail(validDto.email())).thenReturn(Optional.empty());
            when(administratorRepository.findByTelephone(validDto.telephone())).thenReturn(Optional.empty());
            when(permissionsRepository.findByDescription("ROLE_ADMIN")).thenReturn(Optional.of(new Permissions()));
            when(customerRepository.findById(customerEntity.getId())).thenReturn(Optional.of(customerEntity));
            when(passwordEncoder.encode(validDto.password())).thenReturn(encryptedPassword);

            when(administratorRepository.save(any(Administrator.class))).thenAnswer(invocation -> {
                Administrator arg = invocation.getArgument(0);
                arg.setId(UUID.randomUUID());
                return arg;
            });

            AdministratorResponseDTO result = administratorService.createAdministrator(validDto);

            assertNotNull(result);
            assertNotNull(result.id());
            assertEquals(validDto.email(), result.email());
            assertEquals(GeneralStatus.ACTIVE, result.status());
            assertEquals(customerEntity.getId(), result.customerId());

            verify(passwordEncoder, times(1)).encode(validDto.password());
            verify(administratorRepository, times(1)).save(any(Administrator.class));
        }

        @Test
        @DisplayName("Deve lançar EmptyMandatoryFieldsFound quando houver campos obrigatórios ausentes")
        void shouldThrowEmptyMandatoryFieldsFoundWhenFieldsAreMissing() {
            AdministratorRequestDTO invalidDto = new AdministratorRequestDTO(
                    null, "password", "Nome", "Sobrenome", "123", LocalDate.now().toString(), "123", customerEntity.getId()
            );

            EmptyMandatoryFieldsFound exception = assertThrows(EmptyMandatoryFieldsFound.class, () -> {
                administratorService.createAdministrator(invalidDto);
            });

            assertEquals("Você deve preencher todos os campos requeridos.", exception.getMessage());
            verifyNoInteractions(administratorRepository, permissionsRepository, customerRepository, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar DuplicateResourceException quando o email informado já estiver registrado")
        void shouldThrowDuplicateResourceExceptionWhenEmailAlreadyExists() {
            when(administratorRepository.findByEmail(validDto.email())).thenReturn(Optional.of(new Administrator()));

            DuplicateResourceException exception = assertThrows(DuplicateResourceException.class, () -> {
                administratorService.createAdministrator(validDto);
            });

            assertEquals("Email já registrado", exception.getMessage());
            verify(administratorRepository, times(1)).findByEmail(validDto.email());
//            verify(administratorRepository, never()).findByTelephone(anyString());
            verify(administratorRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar DuplicateResourceException quando o telefone informado já estiver registrado")
        void shouldThrowDuplicateResourceExceptionWhenTelephoneAlreadyExists() {
            when(administratorRepository.findByEmail(validDto.email())).thenReturn(Optional.empty());
            when(administratorRepository.findByTelephone(validDto.telephone())).thenReturn(Optional.of(new Administrator()));

            DuplicateResourceException exception = assertThrows(DuplicateResourceException.class, () -> {
                administratorService.createAdministrator(validDto);
            });

            assertEquals("Telefone já registrado", exception.getMessage());
            verify(administratorRepository, times(1)).findByEmail(validDto.email());
            verify(administratorRepository, times(1)).findByTelephone(validDto.telephone());
            verify(permissionsRepository, never()).findByDescription(anyString());
        }

        @Test
        @DisplayName("Deve lançar PermissionNotFoundException quando a role padrão ROLE_ADMIN não existir no banco")
        void shouldThrowPermissionNotFoundExceptionWhenRoleAdminIsNotFound() {
            when(administratorRepository.findByEmail(validDto.email())).thenReturn(Optional.empty());
            when(administratorRepository.findByTelephone(validDto.telephone())).thenReturn(Optional.empty());
            when(permissionsRepository.findByDescription("ROLE_ADMIN")).thenReturn(Optional.empty());

            PermissionNotFoundException exception = assertThrows(PermissionNotFoundException.class, () -> {
                administratorService.createAdministrator(validDto);
            });

            assertEquals("Permissão ROLE_ADMIN não encontrada.", exception.getMessage());
            verify(customerRepository, never()).findById(any());
            verify(administratorRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar EntityNotFoundException quando a empresa (Customer) vinculada não existir")
        void shouldThrowEntityNotFoundExceptionWhenCustomerDoesNotExist() {
            when(administratorRepository.findByEmail(validDto.email())).thenReturn(Optional.empty());
            when(administratorRepository.findByTelephone(validDto.telephone())).thenReturn(Optional.empty());

            when(permissionsRepository.findByDescription("ROLE_ADMIN")).thenReturn(Optional.of(new Permissions()));

            assertThrows(EntityNotFoundException.class, () -> administratorService.createAdministrator(validDto));

            verify(administratorRepository, never()).save(any());
        }
    }

    @Nested
    class createPlatformAdministrator {

        @Test
        @DisplayName("Deve cadastrar administrador de plataforma com sucesso")
        void shouldCreatePlatformAdministratorWithSuccess() {
            String encryptedPassword = "encrypted_platform_pwd";

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByEmail(validPlatformDto.email())).thenReturn(Optional.empty());
            when(administratorRepository.findByTelephone(validPlatformDto.telephone())).thenReturn(Optional.empty());
            when(permissionsRepository.findByDescription("ROLE_PLATFORM_ADMIN")).thenReturn(Optional.of(new Permissions()));
            when(passwordEncoder.encode(validPlatformDto.password())).thenReturn(encryptedPassword);

            when(administratorRepository.save(any(Administrator.class))).thenAnswer(invocation -> {
                Administrator arg = invocation.getArgument(0);
                arg.setId(UUID.randomUUID());
                return arg;
            });

            AdministratorResponseDTO result = administratorService.createPlatformAdministrator(validPlatformDto);

            assertNotNull(result);
            assertNotNull(result.id());
            assertEquals(validPlatformDto.email(), result.email());
            assertEquals(GeneralStatus.ACTIVE, result.status());
            assertNull(result.customerId()); // deve ser nulo por se tratar de admin global

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(passwordEncoder, times(1)).encode(validPlatformDto.password());
            verify(administratorRepository, times(1)).save(any(Administrator.class));
        }

        @Test
        @DisplayName("Deve lançar NotAuthorizedException se o usuário logado não for administrador de plataforma")
        void shouldThrowNotAuthorizedExceptionWhenLoggedUserIsNotPlatformAdmin() {
            when(currentUserService.isPlatformAdmin()).thenReturn(false);

            assertThrows(NotAuthorizedException.class, () -> administratorService.createPlatformAdministrator(validPlatformDto));

            verify(currentUserService, times(1)).isPlatformAdmin();
            verifyNoInteractions(administratorRepository, permissionsRepository, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar EmptyMandatoryFieldsFound quando houver campos obrigatórios ausentes na criação da plataforma")
        void shouldThrowEmptyMandatoryFieldsFoundWhenPlatformFieldsAreMissing() {
            when(currentUserService.isPlatformAdmin()).thenReturn(true);

            PlatformAdministratorRequestDTO invalidDto = new PlatformAdministratorRequestDTO(
                    null, "pwd", "Nome", "Sobrenome", "123", LocalDate.now().toString(), "123"
            );

            EmptyMandatoryFieldsFound exception = assertThrows(EmptyMandatoryFieldsFound.class, () -> {
                administratorService.createPlatformAdministrator(invalidDto);
            });

            assertEquals("Você deve preencher todos os campos requeridos.", exception.getMessage());
            verifyNoInteractions(administratorRepository, permissionsRepository, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar DuplicateResourceException quando houver conflitos de email duplicado")
        void shouldThrowDuplicateResourceExceptionWhenPlatformEmailAlreadyExists() {
            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByEmail(validPlatformDto.email())).thenReturn(Optional.of(new Administrator()));

            assertThrows(DuplicateResourceException.class, () -> administratorService.createPlatformAdministrator(validPlatformDto));

            verify(administratorRepository, times(1)).findByEmail(validPlatformDto.email());
            verify(administratorRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar PermissionNotFoundException quando a role ROLE_PLATFORM_ADMIN não for localizada")
        void shouldThrowPermissionNotFoundExceptionWhenRolePlatformAdminIsNotFound() {
            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByEmail(validPlatformDto.email())).thenReturn(Optional.empty());
            when(administratorRepository.findByTelephone(validPlatformDto.telephone())).thenReturn(Optional.empty());
            when(permissionsRepository.findByDescription("ROLE_PLATFORM_ADMIN")).thenReturn(Optional.empty());

            assertThrows(PermissionNotFoundException.class, () -> administratorService.createPlatformAdministrator(validPlatformDto));

            verify(administratorRepository, never()).save(any());
        }
    }

    @Nested
    class updateCurrentAdministrator {
        private Administrator loggedAdmin;
        private AdministratorUpdateDTO updateDtoNoPassword;
        private AdministratorUpdateDTO updateDto;
        private final String authenticatedEmail = "admin_logado@test.com";

        @BeforeEach
        void setUp() {
            UUID adminId = UUID.randomUUID();
            Customer customerEntity = new Customer();
            customerEntity.setId(UUID.randomUUID());

            loggedAdmin = new Administrator();
            loggedAdmin.setId(adminId);
            loggedAdmin.setEmail(authenticatedEmail);
            loggedAdmin.setPassword("senha_antiga_criptografada");
            loggedAdmin.setName("Nome Original");
            loggedAdmin.setLastName("Sobrenome Original");
            loggedAdmin.setCpf("12345678901");
            loggedAdmin.setBirthDate(LocalDate.of(1990, 1, 1).toString());
            loggedAdmin.setTelephone("11999999999");
            loggedAdmin.setStatus(GeneralStatus.ACTIVE);
            loggedAdmin.setCreatedAt(LocalDateTime.now().minusMonths(1));
            loggedAdmin.setCustomer(customerEntity);

            updateDtoNoPassword = new AdministratorUpdateDTO(
                    "novo_email@test.com",
                    null,
                    "Novo Nome",
                    "Novo Sobrenome",
                    "11988888888"
            );

            updateDto = new AdministratorUpdateDTO(
                    "novo_email@test.com",
                    "novaSenha123",
                    "Novo Nome",
                    "Novo Sobrenome",
                    "11988888888"
            );
        }

        @Test
        @DisplayName("Deve atualizar o perfil do administrador logado com sucesso sem modificar a senha")
        void shouldUpdateCurrentAdministratorWithSuccessWhenPasswordIsNotProvided() {
            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByEmail(authenticatedEmail)).thenReturn(Optional.of(loggedAdmin));
            when(administratorRepository.findByEmailOrTelephoneAndIdNot(
                    updateDtoNoPassword.email(),
                    updateDtoNoPassword.telephone(),
                    loggedAdmin.getId()
            )).thenReturn(Optional.empty());

            doAnswer(invocation -> {
                AdministratorUpdateDTO dto = invocation.getArgument(0);
                Administrator entity = invocation.getArgument(1);
                entity.setEmail(dto.email());
                entity.setName(dto.name());
                entity.setLastName(dto.lastName());
                entity.setTelephone(dto.telephone());
                return null;
            }).when(administratorMapper).administratorUpdateFromDTO(updateDtoNoPassword, loggedAdmin);

            // Retorna a própria entidade mutada simulando a persistência
            when(administratorRepository.save(loggedAdmin)).thenReturn(loggedAdmin);
            when(currentUserService.getPublicUrl(loggedAdmin.getProfilePicture())).thenReturn("http://s3.url/pic.jpg");

            AdministratorResponseDTO result = administratorService.updateCurrentAdministrator(authenticatedEmail, updateDtoNoPassword);

            assertNotNull(result);
            assertEquals(loggedAdmin.getId(), result.id());
            assertEquals(updateDtoNoPassword.email(), result.email());
            assertEquals(updateDtoNoPassword.name(), result.name());
            assertEquals("senha_antiga_criptografada", loggedAdmin.getPassword()); // Garante que a senha permaneceu a mesma
            assertNotNull(loggedAdmin.getUpdatedAt()); // valida o timestamp de atualização

            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(administratorRepository, times(1)).findByEmail(authenticatedEmail);
            verify(administratorMapper, times(1)).administratorUpdateFromDTO(updateDtoNoPassword, loggedAdmin);
            verify(administratorRepository, times(1)).save(loggedAdmin);

            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar NotAuthorizedException se o usuário logado não for administrador de plataforma")
        void shouldThrowNotAuthorizedExceptionWhenLoggedUserIsNotPlatformAdmin() {
            when(currentUserService.isPlatformAdmin()).thenReturn(false);

            NotAuthorizedException exception = assertThrows(NotAuthorizedException.class, () -> {
                administratorService.updateCurrentAdministrator(authenticatedEmail, updateDto);
            });

            assertEquals("Administrador sem permissão necessária para alterar Administratores de Plataforma.", exception.getMessage());

            verify(currentUserService, times(1)).isPlatformAdmin();
            verifyNoInteractions(administratorRepository, administratorMapper, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar EntityNotFoundException quando o perfil do administrador logado não for localizado no banco")
        void shouldThrowEntityNotFoundExceptionWhenCurrentAdminProfileNotFound() {
            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByEmail(authenticatedEmail)).thenReturn(Optional.empty());

            EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
                administratorService.updateCurrentAdministrator(authenticatedEmail, updateDto);
            });

            assertEquals("Administrador não encontrado, " + authenticatedEmail, exception.getMessage());

            verify(administratorRepository, times(1)).findByEmail(authenticatedEmail);
            verifyNoInteractions(administratorMapper, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar InactiveAccountModificationException ao tentar modificar dados de uma conta desativada")
        void shouldThrowInactiveAccountModificationExceptionWhenAccountIsInactive() {
            Administrator inactiveAdmin = new Administrator();
            inactiveAdmin.setStatus(GeneralStatus.INACTIVE);

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByEmail(authenticatedEmail)).thenReturn(Optional.of(inactiveAdmin));

            InactiveAccountModificationException exception = assertThrows(InactiveAccountModificationException.class, () -> {
                administratorService.updateCurrentAdministrator(authenticatedEmail, updateDto);
            });

            assertEquals("Não é possível atualizar uma conta desativada", exception.getMessage());

            verify(administratorRepository, times(1)).findByEmail(authenticatedEmail);
            verifyNoInteractions(administratorMapper, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar DuplicateResourceException quando o novo email ou telefone já estiver em uso por outro usuário")
        void shouldThrowDuplicateResourceExceptionWhenEmailOrTelephoneIsAlreadyInUseByAnotherUser() {
            Administrator activeAdmin = new Administrator();
            activeAdmin.setId(UUID.randomUUID());
            activeAdmin.setStatus(GeneralStatus.ACTIVE);

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findByEmail(authenticatedEmail)).thenReturn(Optional.of(activeAdmin));

            // Simula que o repositório localizou OUTRO administrador (ID diferente) usando os mesmos dados informados
            when(administratorRepository.findByEmailOrTelephoneAndIdNot(
                    updateDto.email(),
                    updateDto.telephone(),
                    activeAdmin.getId()
            )).thenReturn(Optional.of(new Administrator()));

            DuplicateResourceException exception = assertThrows(DuplicateResourceException.class, () -> {
                administratorService.updateCurrentAdministrator(authenticatedEmail, updateDto);
            });

            assertEquals("Email ou telefone já em uso por outro usuário.", exception.getMessage());

            verify(administratorRepository, times(1)).findByEmail(authenticatedEmail);
            verify(administratorRepository, times(1)).findByEmailOrTelephoneAndIdNot(updateDto.email(), updateDto.telephone(), activeAdmin.getId());
            verifyNoInteractions(administratorMapper, passwordEncoder);
        }
    }

    @Nested
    class updateAdministrator {
        private UUID targetAdminId;
        private Administrator targetAdmin;

        @BeforeEach
        void setUp() {
            targetAdminId = UUID.randomUUID();

            targetAdmin = new Administrator();
            targetAdmin.setId(targetAdminId);
            targetAdmin.setStatus(GeneralStatus.ACTIVE);
        }

        @Test
        @DisplayName("Deve alterar o status do administrador alvo com sucesso")
        void shouldUpdateAdministratorStatusWithSuccess() {
            GeneralStatus newStatus = GeneralStatus.INACTIVE;

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findById(targetAdminId)).thenReturn(Optional.of(targetAdmin));

            // Simula o retorno do save devolvendo a própria entidade atualizada
            when(administratorRepository.save(targetAdmin)).thenReturn(targetAdmin);

            assertDoesNotThrow(() -> {
                administratorService.updateAdministrator(targetAdminId, newStatus);
            });

            assertEquals(newStatus, targetAdmin.getStatus());
            verify(currentUserService, times(1)).isPlatformAdmin();
            verify(administratorRepository, times(1)).findById(targetAdminId);
            verify(administratorRepository, times(1)).save(targetAdmin);
        }

        @Test
        @DisplayName("Deve lançar NotAuthorizedException se o usuário logado não for administrador de plataforma")
        void shouldThrowNotAuthorizedExceptionWhenUserIsNotPlatformAdmin() {
            GeneralStatus newStatus = GeneralStatus.INACTIVE;

            when(currentUserService.isPlatformAdmin()).thenReturn(false);

            NotAuthorizedException exception = assertThrows(NotAuthorizedException.class, () -> {
                administratorService.updateAdministrator(targetAdminId, newStatus);
            });

            assertEquals("Administrador sem permissão necessária para alterar Administratores de Plataforma.", exception.getMessage());
            verify(currentUserService, times(1)).isPlatformAdmin();
            verifyNoInteractions(administratorRepository);
        }

        @Test
        @DisplayName("Deve lançar EntityNotFoundException quando o administrador alvo não for localizado no banco")
        void shouldThrowEntityNotFoundExceptionWhenTargetAdminIsNotFound() {
            GeneralStatus newStatus = GeneralStatus.INACTIVE;

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findById(targetAdminId)).thenReturn(Optional.empty());

            EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
                administratorService.updateAdministrator(targetAdminId, newStatus);
            });

            assertEquals("Administrador não encontrado: " + targetAdminId, exception.getMessage());
            verify(administratorRepository, times(1)).findById(targetAdminId);
            verify(administratorRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar DuplicateResourceException se o status atual do administrador já for igual ao novo status solicitado")
        void shouldThrowDuplicateResourceExceptionWhenStatusChangeIsRedundant() {
            // Tentar mudar para ACTIVE um administrador que já está ACTIVE
            GeneralStatus newStatus = GeneralStatus.ACTIVE;

            when(currentUserService.isPlatformAdmin()).thenReturn(true);
            when(administratorRepository.findById(targetAdminId)).thenReturn(Optional.of(targetAdmin));

            DuplicateResourceException exception = assertThrows(DuplicateResourceException.class, () -> {
                administratorService.updateAdministrator(targetAdminId, newStatus);
            });

            assertEquals("Administrador já está com status, " + newStatus, exception.getMessage());
            verify(administratorRepository, times(1)).findById(targetAdminId);
            verify(administratorRepository, never()).save(any());
        }
    }
}
