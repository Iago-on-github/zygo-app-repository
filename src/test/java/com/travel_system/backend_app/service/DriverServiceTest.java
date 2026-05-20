package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.interfaces.mappers.DriverMapper;
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
import org.apache.coyote.Request;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private DriverRepository repository;
    @Mock
    private PermissionsRepository permissionsRepository;

    @Mock
    private DriverMapper driverMapper;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ArgumentCaptor<Driver> driverArgumentCaptor = ArgumentCaptor.forClass(Driver.class);

    @Nested
    class getAllDrivers {

        @Test
        @DisplayName("should return all drivers from database with success")
        void shouldReturnAllDriversWithSuccess() {
            // arrange
            Driver exemple_driver = new Driver(UUID.randomUUID(), "driver@email.com", "123456", "João", "Silva", "75999999999", "https://minha-imagem.com/driver.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador - BA", 25, new ArrayList<>());

            List<Driver> drivers = List.of(exemple_driver);

            when(repository.findAll()).thenReturn(drivers);

            // act
            List<DriverResponseDTO> result = driverService.getAllDrivers();

            // arrange
            assertNotNull(result, "result must never be null");

            assertEquals(result.size(), drivers.size());
            assertEquals(result.getFirst().id(), drivers.getFirst().getId());
            assertEquals(result.getFirst().email(), drivers.getFirst().getEmail());
            assertEquals(result.getFirst().telephone(), drivers.getFirst().getTelephone());
        }

        @Test
        @DisplayName("should return an empty list when drivers not found from database")
        void shouldReturnAnEmptyListWhenDriversNotFound() {
            // arrange
            List<Driver> drivers = Collections.emptyList();
            
            when(repository.findAll()).thenReturn(drivers);
            
            // act
            List<DriverResponseDTO> result = driverService.getAllDrivers();

            // assert
            assertTrue(result.isEmpty());
        }


    }

    @Nested
    class getDriversByStatus {
        Driver driver;

        @BeforeEach
        void setUp() {
            driver = new Driver(UUID.randomUUID(), "driver2@email.com", "123", "João", "Silva", "75999999999", null, GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador - BA", 25, new ArrayList<>());

        }

        @ParameterizedTest
        @MethodSource("statusProvider")
        void shouldReturnAllDriversByStatusWithSuccess(GeneralStatus inputStatus, GeneralStatus expectedStatus) {
            when(repository.findAllByStatus(expectedStatus)).thenReturn(List.of(driver));

            List<DriverResponseDTO> result = driverService.getDriversByStatus(inputStatus);

            assertNotNull(result);

            assertEquals(1, result.size());

            verify(repository, times(1)).findAllByStatus(any());
        }

        public static Stream<Arguments> statusProvider() {
            return Stream.of(
                    Arguments.of(GeneralStatus.ACTIVE, GeneralStatus.ACTIVE),
                    Arguments.of(GeneralStatus.INACTIVE, GeneralStatus.INACTIVE),
                    Arguments.of(null, GeneralStatus.ACTIVE)
            );
        }
    }

    @Nested
    class createDriver {

        @Test
        @DisplayName("should create new driver with success")
        void shouldCreateNewDriverWithSuccess() {
            // arrange
            String encodePassword = passwordEncoder.encode("123");

            Driver exemple_driver = new Driver(UUID.randomUUID(), "driver2@email.com", encodePassword, "João", "Silva", "75999999999", null, GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador - BA", 25, new ArrayList<>());

            String role = "ROLE_DRIVER";
            Permissions perm = new Permissions(role);

            DriverRequestDTO driverRequestDTO = new DriverRequestDTO(
                    "driver2@email.com",
                    encodePassword,
                    "João",
                    "Silva",
                    "75999999999",
                    null,
                    "Salvador - BA"
            );

            when(repository.findByEmail(exemple_driver.getEmail())).thenReturn(Optional.empty());
            when(repository.findByTelephone(exemple_driver.getTelephone())).thenReturn(Optional.empty());
            when(permissionsRepository.findByDescription(role)).thenReturn(Optional.of(perm));
            when(repository.save(any(Driver.class))).thenReturn(exemple_driver);

            // act
            DriverResponseDTO result = driverService.createDriver(driverRequestDTO);

            // assert
            assertNotNull(result, "result must never be null");

            verify(repository, times(1)).findByEmail(any());
            verify(repository, times(1)).findByTelephone(any());
            verify(permissionsRepository, times(1)).findByDescription(any());

            verify(repository, times(1)).save(driverArgumentCaptor.capture());
            Driver savedDriver = driverArgumentCaptor.getValue();

            assertNotEquals("123", savedDriver.getPassword());
            assertEquals(perm.getDescription(), savedDriver.getPermissions().getFirst().getAuthority());

            assertEquals(GeneralStatus.ACTIVE, savedDriver.getStatus());
            assertNotNull(savedDriver.getCreatedAt());
            assertNotNull(savedDriver.getPassword());
        }

        @ParameterizedTest(name = "should throw exception when invalid fields: {0}")
        @DisplayName("throw exception when empty mandatory fields are found")
        @MethodSource("emptyMandatoryFieldsProvider")
        void throwExceptionWhenEmptyMandatoryFieldsFound(DriverRequestDTO driverRequestDTO) {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> driverService.createDriver(driverRequestDTO));

            verify(repository, never()).save(any(Driver.class));
        }

        public static Stream<DriverRequestDTO> emptyMandatoryFieldsProvider() {
            return Stream.of(
                    new DriverRequestDTO(null, "123", "João", "Silva", "75999999999", null, "Salvador - BA"),
                    new DriverRequestDTO("email@teste.com", null, "João", "Silva", "75999999999", null, "Salvador - BA"),
                    new DriverRequestDTO("email@teste.com", "123", null, "Silva", "75999999999", null, "Salvador - BA"),
                    new DriverRequestDTO("email@teste.com", "123", "João", "Silva", null, null, "Salvador - BA")
            );
        }

        @Test
        @DisplayName("throw exception when email already exists in the database")
        void throwExceptionWhenEmailAlreadyExists() {
            // arrange
            String encodePassword = "encoded-password-123";

            Driver exemple_driver = new Driver(UUID.randomUUID(), "driver2@email.com", encodePassword, "João", "Silva", "75999999999", null, GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador - BA", 25, new ArrayList<>());

            DriverRequestDTO driverRequestDTO = new DriverRequestDTO(
                    "driver2@email.com",
                    encodePassword,
                    "João",
                    "Silva",
                    "75999999999",
                    null,
                    "Salvador - BA"
            );

            when(repository.findByEmail(exemple_driver.getEmail())).thenReturn(Optional.of(exemple_driver));
            when(repository.findByTelephone(exemple_driver.getTelephone())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(DuplicateResourceException.class, () -> driverService.createDriver(driverRequestDTO));

            verify(permissionsRepository, never()).findByDescription(any());
            verify(repository, never()).save(any());

        }

        @Test
        @DisplayName("throw exception when telephone already exists in the database")
        void throwExceptionWhenTelephoneAlreadyExists() {
            // arrange
            String encodedPassword = "encoded-password-123";

            Driver existingDriver = new Driver(
                    UUID.randomUUID(), "driver2@email.com", encodedPassword,
                    "João", "Silva", "75999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador - BA", 25, new ArrayList<>()
            );

            DriverRequestDTO driverRequestDTO = new DriverRequestDTO(
                    "driver2@email.com", encodedPassword,
                    "João", "Silva",
                    "75999999999", null,
                    "Salvador - BA"
            );

            when(repository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.empty());

            when(repository.findByTelephone(driverRequestDTO.telephone())).thenReturn(Optional.of(existingDriver));

            // act + assert
            assertThrows(DuplicateResourceException.class, () -> driverService.createDriver(driverRequestDTO));

            verify(repository).findByEmail(driverRequestDTO.email());
            verify(repository).findByTelephone(driverRequestDTO.telephone());

            verify(permissionsRepository, never()).findByDescription(any());
            verify(repository, never()).save(any());

        }

        @Test
        @DisplayName("throw exception when ROLE_DRIVER not found from database")
        void throwExceptionWhenRoleDriverNotFound() {
            // arrange
            Permissions perm = new Permissions("ROLE_DRIVER");

            DriverRequestDTO driverRequestDTO = new DriverRequestDTO(
                    "driver2@email.com",
                    "1234",
                    "João",
                    "Silva",
                    "75999999999",
                    null,
                    "Salvador - BA"
            );

            when(repository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.empty());
            when(repository.findByTelephone(driverRequestDTO.telephone())).thenReturn(Optional.empty());
            when(permissionsRepository.findByDescription(perm.getDescription())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(PermissionNotFoundException.class, () -> driverService.createDriver(driverRequestDTO));

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class updateCurrentDriver {

        @Test
        @DisplayName("should update logged driver with success")
        void shouldUpdateCurrentDriverWithSuccess() {
            // arrange
            String authenticatedEmail = "driver2@email.com";

            Driver driver = new Driver(
                    UUID.randomUUID(),
                    authenticatedEmail,
                    "oldPassword",
                    "João",
                    "Silva",
                    "75999999999",
                    null,
                    GeneralStatus.ACTIVE,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    "Salvador - BA",
                    25,
                    new ArrayList<>()
            );

            DriverUpdateDTO driverUpdateDTO = new DriverUpdateDTO(
                    "new@email.com",
                    "newPass",
                    "NewName",
                    "NewLast",
                    "75888888888",
                    null,
                    "Feira de Santana - BA"
            );

            String encodedPassword = "encodedPassword";

            when(repository.findByEmail(authenticatedEmail))
                    .thenReturn(Optional.of(driver));

            when(repository.findByEmail(driverUpdateDTO.email()))
                    .thenReturn(Optional.empty());

            when(repository.findByTelephone(driverUpdateDTO.telephone()))
                    .thenReturn(Optional.empty());

            when(passwordEncoder.encode(driverUpdateDTO.password()))
                    .thenReturn(encodedPassword);

            doAnswer(invocation -> {
                DriverUpdateDTO dto = invocation.getArgument(0);
                Driver entity = invocation.getArgument(1);

                entity.setEmail(dto.email());
                entity.setName(dto.name());
                entity.setLastName(dto.lastName());
                entity.setTelephone(dto.telephone());
                entity.setAreaOfActivity(dto.areaOfActivity());

                return null;
            }).when(driverMapper).driverUpdateFromDTO(driverUpdateDTO, driver);

            when(repository.save(any(Driver.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // act
            DriverResponseDTO result = driverService
                    .updateCurrentDriver(authenticatedEmail, driverUpdateDTO);

            // assert
            assertNotNull(result);

            verify(repository).findByEmail(authenticatedEmail);
            verify(repository).findByEmail(driverUpdateDTO.email());
            verify(repository).findByTelephone(driverUpdateDTO.telephone());
            verify(driverMapper).driverUpdateFromDTO(driverUpdateDTO, driver);
            verify(repository).save(driverArgumentCaptor.capture());

            Driver savedDriver = driverArgumentCaptor.getValue();

            assertEquals("new@email.com", savedDriver.getEmail());
            assertEquals("NewName", savedDriver.getName());
            assertEquals("NewLast", savedDriver.getLastName());
            assertEquals("75888888888", savedDriver.getTelephone());
            assertEquals("Feira de Santana - BA", savedDriver.getAreaOfActivity());

            assertEquals(encodedPassword, savedDriver.getPassword());

            assertNotNull(result.id());
        }

        @Test
        @DisplayName("throw exception when logged driver not found from database")
        void throwExceptionWhenLoggedDriverNotFound() {
            // arrange
            DriverUpdateDTO driverUpdateDTO = new DriverUpdateDTO(
                    "new@email.com",
                    "newPass",
                    "NewName",
                    "NewLast",
                    "75888888888",
                    null,
                    "Feira de Santana - BA"
            );

            when(repository.findByEmail(driverUpdateDTO.email())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> driverService.updateCurrentDriver(driverUpdateDTO.email(), driverUpdateDTO));

            verify(repository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when driver is inactive")
        void throwExceptionWhenDriverIsInactive() {
            // arrange
            DriverUpdateDTO driverUpdateDTO = new DriverUpdateDTO(
                    "new@email.com",
                    "newPass",
                    "NewName",
                    "NewLast",
                    "75888888888",
                    null,
                    "Feira de Santana - BA"
            );

            Driver driver = new Driver();
            driver.setStatus(GeneralStatus.INACTIVE);

            when(repository.findByEmail(driverUpdateDTO.email())).thenReturn(Optional.of(driver));

            // act & assert
            assertThrows(InactiveAccountModificationException.class, () -> driverService.updateCurrentDriver(driverUpdateDTO.email(), driverUpdateDTO));

            verify(repository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(repository, never()).save(any());
        }

        @ParameterizedTest
        @DisplayName("throw exception when already exists driver with email or telephone")
        @MethodSource("filledPropsProvider")
        void throwExceptionWhenAlreadyExistingDriverWithTelephoneOrEmail(
                DriverUpdateDTO driverUpdateDTO) {

            UUID driverId = UUID.randomUUID();

            Driver loggedDriver = new Driver();
            loggedDriver.setId(driverId);
            loggedDriver.setEmail("logged@email.com");
            loggedDriver.setTelephone("71999999999");
            loggedDriver.setStatus(GeneralStatus.ACTIVE);

            Driver existingDriver = new Driver();
            existingDriver.setId(UUID.randomUUID());
            existingDriver.setEmail("filled@email.com");
            existingDriver.setTelephone("74739204403");

            String authenticatedEmail = "logged@email.com";

            when(repository.findByEmail(authenticatedEmail)).thenReturn(Optional.of(loggedDriver));

            if (driverUpdateDTO.email() != null) {
                when(repository.findByEmail(driverUpdateDTO.email())).thenReturn(Optional.of(existingDriver));
            }

            if (driverUpdateDTO.telephone() != null) {
                when(repository.findByTelephone(driverUpdateDTO.telephone())).thenReturn(Optional.of(existingDriver));
            }

            assertThrows(DuplicateResourceException.class, () -> driverService.updateCurrentDriver(authenticatedEmail, driverUpdateDTO));

            verify(repository, never()).save(any());
        }

        public static Stream<DriverUpdateDTO> filledPropsProvider() {
            return Stream.of(
                    new DriverUpdateDTO("filled@email.com",null,null,null,null,null,null),
                    new DriverUpdateDTO(null,null,null,null,"74739204403",null,null)
            );
        }

    }

    @Nested
    class getLoggedDriverInProfile {

        @Test
        @DisplayName("should return actual logged driver")
        void shouldReturnLoggedDriver() {
            // arrange
            Driver driver = new Driver();
            driver.setEmail("teste@gmail.com");

            when(repository.findByEmail(driver.getEmail())).thenReturn(Optional.of(driver));

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

            when(repository.findByEmail(driver.getEmail())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> driverService.getCurrentDriver(driver.getEmail()));
        }
    }

    @Nested
    class updateDriver {
        Driver driver;

        @BeforeEach
        void setUp() {
            driver = new Driver(UUID.randomUUID(), "driver2@email.com", "123", "João", "Silva", "75999999999", null, GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador - BA", 25, new ArrayList<>());

        }
        @Test
        void shouldUpdateDriverWithSuccess() {
            when(repository.findById(driver.getId())).thenReturn(Optional.of(driver));

            driverService.updateDriver(driver.getId(), new UpdateEntityStatusDTO(GeneralStatus.INACTIVE));

            verify(repository, times(1)).save(driverArgumentCaptor.capture());
            Driver savedValue = driverArgumentCaptor.getValue();

            assertEquals(GeneralStatus.INACTIVE, savedValue.getStatus());
        }

        @Test
        void throwExceptionWhenDriverNotFound() {
            when(repository.findById(driver.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> driverService.updateDriver(driver.getId(), new UpdateEntityStatusDTO(GeneralStatus.INACTIVE)));

            verify(repository, never()).save(any());
        }

        @Test
        void throwExceptionWhenDriverAlreadyHasStatus() {
            when(repository.findById(driver.getId())).thenReturn(Optional.of(driver));

            assertThrows(DuplicateResourceException.class, () -> driverService.updateDriver(driver.getId(), new UpdateEntityStatusDTO(GeneralStatus.ACTIVE)));

            verify(repository, never()).save(any());
        }

    }

}