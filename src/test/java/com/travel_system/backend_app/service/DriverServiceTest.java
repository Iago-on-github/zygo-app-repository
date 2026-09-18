package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFoundException;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.interfaces.mappers.DriverRequestMapper;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT)
     *
     */

    @InjectMocks
    private DriverService driverService;

    @Mock
    private DriverRepository driverRepository;
    @Mock
    private PermissionsRepository permissionsRepository;
    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private DriverRequestMapper driverRequestMapper;
    @Mock
    private CurrentUserService currentUserService;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final Pageable expectedPageable = PageRequest.of(0, 10);

    private Customer customerEntity;
    private Driver driverEntity;
    private Permissions permissionsEntity;
    private DriverRequestDTO driverRequestDTO;

/*    @BeforeEach
    void setUp() {
        permissionsEntity = new Permissions("ROLE_DRIVER");

        customerEntity = new Customer(UUID.randomUUID(), "Prefeitura X", "prefeitura-x", "12.345.678/0001-99", true, new City(), ClientSector.PUBLIC_CLIENT, null, Instant.now(), null);

        driverEntity = new Driver(UUID.randomUUID(), "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customerEntity, "CITY", 12);

        driverRequestDTO = new DriverRequestDTO("rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "TRANSPORTE ESCOLAR", customerEntity.getId());
    }

    @Nested
    class getAllDrivers {

        @Test
        @DisplayName("Deve retornar os Drivers cadastrados com sucesso")
        void shouldReturnDriversWithSuccess() {
            Page<Driver> pagedDrivers = new PageImpl<>(List.of(driverEntity));

            when(currentUserService.getPublicUrl(driverEntity.getProfilePicture())).thenReturn("publicUrl_exemple");
            when(driverRepository.findAll(expectedPageable)).thenReturn(pagedDrivers);

            Page<DriverResponseDTO> result = driverService.getAllDrivers();

            assertNotNull(result);

            assertEquals(1, result.getTotalElements());

            assertEquals(result.getContent().getFirst().email(), driverEntity.getEmail());
            assertEquals(result.getContent().getFirst().id(), driverEntity.getId());
        }
    }

    @Nested
    class getDriversByStatus {

        @Test
        @DisplayName("Deve filtrar os drivers pelo status com sucesso")
        void shouldFilterDriversByStatusSuccessfully() {
            Page<Driver> pagedDrivers = new PageImpl<>(List.of(driverEntity));

            when(driverRepository.findAllByStatus(GeneralStatus.ACTIVE, expectedPageable)).thenReturn(pagedDrivers);

            Page<DriverResponseDTO> result = driverService.getDriversByStatus(GeneralStatus.ACTIVE);

            assertEquals(1, result.getTotalElements());

            assertNotNull(result);
            assertEquals(result.getContent().getFirst().email(), driverEntity.getEmail());
            assertEquals(result.getContent().getFirst().id(), driverEntity.getId());
        }

        @Test
        @DisplayName("Deve filtrar os drivers pelo status com sucesso com o fallback ativo")
        void shouldDefaultToActiveStatusWhenStatusIsNull() {
            Page<Driver> pagedDrivers = new PageImpl<>(List.of(driverEntity));

            when(driverRepository.findAllByStatus(GeneralStatus.ACTIVE, expectedPageable)).thenReturn(pagedDrivers);

            // null passado direto para simular o fallback
            Page<DriverResponseDTO> result = driverService.getDriversByStatus(null);

            assertEquals(1, result.getTotalElements());

            assertNotNull(result);
            assertEquals(result.getContent().getFirst().email(), driverEntity.getEmail());
            assertEquals(result.getContent().getFirst().id(), driverEntity.getId());
        }

    }
    
    @Nested
    class createDriver {

        @Test
        @DisplayName("Deve criar um novo motorista com sucesso")
        void shouldCreateDriverWithSuccess() {
            when(driverRepository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.empty());
            when(driverRepository.findByTelephone(driverRequestDTO.telephone())).thenReturn(Optional.empty());
            when(customerRepository.findById(driverRequestDTO.customerId())).thenReturn(Optional.of(customerEntity));
            when(permissionsRepository.findByDescription("ROLE_DRIVER")).thenReturn(Optional.of(permissionsEntity));
            when(passwordEncoder.encode(anyString())).thenReturn("senha_criptografada_mock");
            when(currentUserService.getPublicUrl(any())).thenReturn("https://s3.url/profile.jpg");

            when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> {
                Driver arg = invocation.getArgument(0);
                arg.setId(UUID.randomUUID());
                return arg;
            });

            DriverResponseDTO result = driverService.createDriver(driverRequestDTO);

            assertNotNull(result);

            ArgumentCaptor<Driver> driverArgCaptor = ArgumentCaptor.forClass(Driver.class);

            verify(driverRepository, times(1)).save(driverArgCaptor.capture());

            Driver savedValue = driverArgCaptor.getValue();
            assertEquals(result.id(), savedValue.getId());
            assertEquals(result.email(), savedValue.getEmail());
            assertEquals(result.customerId(), savedValue.getCustomer().getId());

            assertNotNull(savedValue.getCreatedAt());
            assertNotNull(savedValue.getStatus());

            verify(passwordEncoder, times(1)).encode(anyString());

            verify(customerRepository, times(1)).findById(any());
            verify(driverRepository, times(1)).findByEmail(any());
            verify(driverRepository, times(1)).findByTelephone(anyString());
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exception quando algum dos campos requeridos forem null")
        @MethodSource("invalidFieldsProvider")
        void throwExceptionWhenRequireFieldsIsNull(DriverRequestDTO driverRequestDTO) {
            assertThrows(EmptyMandatoryFieldsFoundException.class, () -> driverService.createDriver(driverRequestDTO));

            verifyNoInteractions(driverRepository, customerRepository, permissionsRepository, passwordEncoder, currentUserService);
        }

        public static Stream<Arguments> invalidFieldsProvider() {
            return Stream.of(
                    Arguments.of(new DriverRequestDTO(null, "Senha@123", "Rafael", "Silva", "11999998888", "TRANSPORTE ESCOLAR", UUID.randomUUID())),
                    Arguments.of(new DriverRequestDTO("rafael.silva@test.com", null, "Rafael", "Silva", "11999998888", "TRANSPORTE ESCOLAR", UUID.randomUUID())),
                    Arguments.of(new DriverRequestDTO("rafael.silva@test.com", "Senha@123", null, "Silva", "11999998888", "TRANSPORTE ESCOLAR", UUID.randomUUID())),
                    Arguments.of(new DriverRequestDTO("rafael.silva@test.com", "Senha@123", "Rafael", "Silva", null, "TRANSPORTE ESCOLAR", UUID.randomUUID())),
                    Arguments.of(new DriverRequestDTO("rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "TRANSPORTE ESCOLAR", null))
            );
        }

        @Test
        @DisplayName("Deve lançar exception quando o email ja existir no banco de dados")
        void throwExceptionWhenEmailAlreadyExists() {
            when(driverRepository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.of(driverEntity));
            when(driverRepository.findByTelephone(driverRequestDTO.telephone())).thenReturn(Optional.empty());

            assertThrows(DuplicateResourceException.class, () -> driverService.createDriver(driverRequestDTO));

            verifyNoMoreInteractions(driverRepository);
            verifyNoInteractions(customerRepository, permissionsRepository, passwordEncoder, currentUserService);
        }

        @Test
        @DisplayName("Deve lançar exception quando o telefone ja existir no banco de dados")
        void throwExceptionWhenTelephoneAlreadyExists() {
            when(driverRepository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.empty());
            when(driverRepository.findByTelephone(driverRequestDTO.telephone())).thenReturn(Optional.of(driverEntity));

            assertThrows(DuplicateResourceException.class, () -> driverService.createDriver(driverRequestDTO));

            verifyNoMoreInteractions(driverRepository);
            verifyNoInteractions(customerRepository, permissionsRepository, passwordEncoder, currentUserService);
        }

        @Test
        @DisplayName("Deve lançar exception quando o Customer enviado não for válido")
        void throwExceptionWhenCustomerNotFound() {
            when(driverRepository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.empty());
            when(driverRepository.findByTelephone(driverRequestDTO.telephone())).thenReturn(Optional.empty());

            when(customerRepository.findById(customerEntity.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> driverService.createDriver(driverRequestDTO));

            verifyNoMoreInteractions(driverRepository, customerRepository);

            verifyNoInteractions(permissionsRepository, passwordEncoder, currentUserService);
        }

        @Test
        @DisplayName("Deve lançar exception quando a permissão não for encontrada no banco de dados")
        void throwExceptionWhenPermissionNotFound() {
            when(driverRepository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.empty());
            when(driverRepository.findByTelephone(driverRequestDTO.telephone())).thenReturn(Optional.empty());
            when(customerRepository.findById(customerEntity.getId())).thenReturn(Optional.of(customerEntity));

            when(permissionsRepository.findByDescription(anyString())).thenReturn(Optional.empty());

            assertThrows(PermissionNotFoundException.class, () -> driverService.createDriver(driverRequestDTO));

            verifyNoMoreInteractions(driverRepository, customerRepository, permissionsRepository);

            verifyNoInteractions(passwordEncoder, currentUserService);
        }
    }

    @Nested
    class updateCurrentDriver {
        DriverUpdateDTO driverUpdateDTO;

        @BeforeEach
        void setUp() {
            driverUpdateDTO = new DriverUpdateDTO("rafael.silva@test.com", null, "Rafael", "Silva", "11999998888", "CITY");
        }

        @Test
        @DisplayName("Deve realizar a atualização do driver no banco sem alterar a senha")
        void shouldUpdateDriverWithSuccessWithoutPasswordChange() {
            driverEntity.setEmail("antigo_email@teste.com");
            driverEntity.setTelephone("11911111111");

            when(driverRepository.findByEmail(driverEntity.getEmail())).thenReturn(Optional.of(driverEntity)); 
            when(driverRepository.findByEmail(driverUpdateDTO.email())).thenReturn(Optional.empty());
            when(driverRepository.findByTelephone(driverUpdateDTO.telephone())).thenReturn(Optional.empty());

            when(driverRepository.save(driverEntity)).thenReturn(driverEntity);
            when(currentUserService.getPublicUrl(any())).thenReturn("https://s3.url/foto.jpg");

            doNothing().when(driverRequestMapper).driverUpdateFromDTO(driverUpdateDTO, driverEntity);

            DriverResponseDTO result = driverService.updateCurrentDriver(driverEntity.getEmail(), driverUpdateDTO);

            assertNotNull(result);

            verify(driverRepository, times(1)).findByEmail(driverEntity.getEmail());
            verify(driverRepository, times(1)).findByEmail(driverUpdateDTO.email());
            verify(driverRepository, times(1)).findByTelephone(driverUpdateDTO.telephone());
            verify(driverRequestMapper, times(1)).driverUpdateFromDTO(driverUpdateDTO, driverEntity);
            verify(driverRepository, times(1)).save(driverEntity);

            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("Deve realizar a atualização do driver no banco alterando a senha")
        void shouldUpdateDriverWithSuccessWithPasswordChange() {
            driverEntity.setEmail("antigo_email@teste.com");
            driverEntity.setTelephone("11911111111");

            DriverUpdateDTO driverUpdatePass = new DriverUpdateDTO("rafael.silva@test.com", "123", "Rafael", "Silva", "11999998888", "CITY");

            when(driverRepository.findByEmail(driverEntity.getEmail())).thenReturn(Optional.of(driverEntity));
            when(driverRepository.findByEmail(driverUpdatePass.email())).thenReturn(Optional.empty());
            when(driverRepository.findByTelephone(driverUpdatePass.telephone())).thenReturn(Optional.empty());

            when(driverRepository.save(driverEntity)).thenReturn(driverEntity);
            when(currentUserService.getPublicUrl(any())).thenReturn("https://s3.url/foto.jpg");

            doNothing().when(driverRequestMapper).driverUpdateFromDTO(driverUpdatePass, driverEntity);
            when(passwordEncoder.encode(driverUpdatePass.password())).thenReturn("new_encoded_pass");

            DriverResponseDTO result = driverService.updateCurrentDriver(driverEntity.getEmail(), driverUpdatePass);

            assertNotNull(result);

            verify(driverRepository, times(1)).findByEmail(driverEntity.getEmail());
            verify(driverRepository, times(1)).findByEmail(driverUpdatePass.email());
            verify(driverRepository, times(1)).findByTelephone(driverUpdatePass.telephone());
            verify(driverRequestMapper, times(1)).driverUpdateFromDTO(driverUpdatePass, driverEntity);
            verify(driverRepository, times(1)).save(driverEntity);

            verify(passwordEncoder, times(1)).encode(driverUpdatePass.password());
        }

        @Test
        @DisplayName("Deve lançar exception quando driver não for encontrado")
        void throwExceptionWhenDriverNotFound() {
            when(driverRepository.findByEmail(driverEntity.getEmail())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> driverService.updateCurrentDriver(driverEntity.getEmail(), driverUpdateDTO));

            verify(driverRepository, times(1)).findByEmail(any());

            verifyNoMoreInteractions(driverRepository);

            verifyNoInteractions(driverRequestMapper, passwordEncoder);

        }

        @Test
        @DisplayName("Deve lançar exception quando o driver for inativo")
        void throwExceptionWhenDriverHasInactive() {
            driverEntity.setStatus(GeneralStatus.INACTIVE);

            when(driverRepository.findByEmail(driverEntity.getEmail())).thenReturn(Optional.of(driverEntity));

            assertThrows(InactiveAccountModificationException.class, () -> driverService.updateCurrentDriver(driverEntity.getEmail(), driverUpdateDTO));

            verify(driverRepository, times(1)).findByEmail(any());

            verifyNoMoreInteractions(driverRepository);

            verifyNoInteractions(driverRequestMapper, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar exception quando o email já existir")
        void throwExceptionWhenEmailAlreadyExists() {
            String emailLogado = "motorista_atual@teste.com";
            String novoEmailDesejado = driverUpdateDTO.email();

            driverEntity.setEmail(emailLogado);
            driverEntity.setStatus(GeneralStatus.ACTIVE);

            when(driverRepository.findByEmail(emailLogado)).thenReturn(Optional.of(driverEntity));

            when(driverRepository.findByEmail(novoEmailDesejado)).thenReturn(Optional.of(new Driver()));

            assertThrows(DuplicateResourceException.class, () -> driverService.updateCurrentDriver(emailLogado, driverUpdateDTO));

            verify(driverRepository, times(1)).findByEmail(emailLogado);
            verify(driverRepository, times(1)).findByEmail(novoEmailDesejado);

            verify(driverRepository, never()).save(any());
            verifyNoInteractions(driverRequestMapper, passwordEncoder);
        }

        @Test
        @DisplayName("Deve lançar exception quando o telephone já existir")
        void throwExceptionWhenTelephoneAlreadyExists() {
            String emailLogado = "motorista_atual@teste.com";
            String telefoneAtual = "11911111111";
            String novoTelefoneDesejado = driverUpdateDTO.telephone();

            driverEntity.setEmail(emailLogado);
            driverEntity.setTelephone(telefoneAtual);
            driverEntity.setStatus(GeneralStatus.ACTIVE);

            when(driverRepository.findByEmail(emailLogado)).thenReturn(Optional.of(driverEntity));

            when(driverRepository.findByTelephone(novoTelefoneDesejado)).thenReturn(Optional.of(new Driver()));

            assertThrows(DuplicateResourceException.class, () -> driverService.updateCurrentDriver(emailLogado, driverUpdateDTO));

            verify(driverRepository, times(1)).findByEmail(emailLogado);
            verify(driverRepository, times(1)).findByTelephone(novoTelefoneDesejado);

            verify(driverRepository, never()).save(any());
            verifyNoInteractions(driverRequestMapper, passwordEncoder);
        }
    }
    
    @Nested
    class getCurrentDriver {

        @Test
        @DisplayName("should return actual logged driver")
        void shouldReturnLoggedDriver() {
            // arrange
            Driver driver = new Driver();
            driver.setEmail("teste@gmail.com");

            when(driverRepository.findByEmail(driver.getEmail())).thenReturn(Optional.of(driver));

            // act
            DriverResponseDTO result = driverService.getCurrentDriver(driver.getEmail());

            // assert
            assertNotNull(result, "result must never be null");

            assertEquals(result.email(), driver.getEmail());
        }

        @Test
        @DisplayName("throw exception when logged driver not found from database")
        void throwExceptionWhenLoggedDriverNotFound() {
            // arrange
            Driver driver = new Driver();
            driver.setEmail("teste@gmail.com");

            when(driverRepository.findByEmail(driver.getEmail())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> driverService.getCurrentDriver(driver.getEmail()));
        }
    }*/

    @Nested
    class updateDriver {

        @Test
        @DisplayName("Deve atualizar o status do driver com sucesso")
        void shouldUpdateDriverStatusWithSuccess() {
            when(driverRepository.findById(driverEntity.getId())).thenReturn(Optional.of(driverEntity));

            driverService.updateDriver(driverEntity.getId(), new UpdateEntityStatusDTO(GeneralStatus.INACTIVE));

            ArgumentCaptor<Driver> driverArgCaptor = ArgumentCaptor.forClass(Driver.class);

            verify(driverRepository, times(1)).save(driverArgCaptor.capture());

            Driver storageValue = driverArgCaptor.getValue();
            assertNotNull(storageValue);
            assertNotNull(storageValue.getUpdatedAt());

            assertEquals(GeneralStatus.INACTIVE, storageValue.getStatus());
        }

        @Test
        void throwExceptionWhenDriverNotFound() {
            when(driverRepository.findById(driverEntity.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> driverService.updateDriver(driverEntity.getId(), new UpdateEntityStatusDTO(GeneralStatus.INACTIVE)));

            verify(driverRepository, never()).save(any(Driver.class));
        }

        @Test
        @DisplayName("Deve lançar exception quando o driver ja tiver o status passado na requisição")
        void throwExceptionWhenDriverAlreadyHasStatus() {
            when(driverRepository.findById(driverEntity.getId())).thenReturn(Optional.of(driverEntity));

            assertThrows(DuplicateResourceException.class, () -> driverService.updateDriver(driverEntity.getId(), new UpdateEntityStatusDTO(GeneralStatus.ACTIVE)));

            verify(driverRepository, never()).save(any(Driver.class));

        }
    }


}