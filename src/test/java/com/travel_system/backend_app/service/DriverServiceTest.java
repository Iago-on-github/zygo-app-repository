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
import org.apache.coyote.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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

    @InjectMocks
    private DriverService driverService;

    @Mock
    private DriverRepository repository;
    @Mock
    private PermissionsRepository permissionsRepository;

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
    class getAllActiveDrivers {

        @Test
        @DisplayName("should return all active drivers from database with success")
        void shouldReturnAllActiveDriversWithSuccess() {
            // arrange
            Driver driver = new Driver();
            driver.setStatus(GeneralStatus.ACTIVE);

            List<Driver> drivers = List.of(driver);

            when(repository.findAllByStatus(GeneralStatus.ACTIVE)).thenReturn(drivers);

            // act
            List<DriverResponseDTO> result = driverService.getAllActiveDrivers();

            // assert
            assertNotNull(result, "result must never be null");

            assertEquals(result.getFirst().status(), drivers.getFirst().getStatus());
        }

        @Test
        @DisplayName("should return an empty list when active drivers not found from database")
        void shouldReturnAnEmptyListWhenActiveDriversNotFound() {
            // arrange
            Driver driver = new Driver();
            driver.setStatus(GeneralStatus.ACTIVE);

            List<Driver> drivers = Collections.emptyList();

            when(repository.findAllByStatus(GeneralStatus.ACTIVE)).thenReturn(drivers);

            // act
            List<DriverResponseDTO> result = driverService.getAllActiveDrivers();

            // assert
            assertNotNull(result, "result must never be null");

            assertEquals(0, result.size());

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class getAllInactiveDrivers {

        @Test
        @DisplayName("should return all inactive drivers from database with success")
        void shouldReturnAllInactiveDriversWithSuccess() {
            // arrange
            Driver driver = new Driver();
            driver.setStatus(GeneralStatus.INACTIVE);

            List<Driver> drivers = List.of(driver);

            when(repository.findAllByStatus(GeneralStatus.INACTIVE)).thenReturn(drivers);

            // act
            List<DriverResponseDTO> result = driverService.getAllInactiveDrivers();

            // assert
            assertNotNull(result, "result must never be null");

            assertEquals(result.getFirst().status(), drivers.getFirst().getStatus());
        }

        @Test
        @DisplayName("should return an empty list when inactive drivers not found from database")
        void shouldReturnAnEmptyListWhenInactiveDriversNotFound() {
            // arrange
            Driver driver = new Driver();
            driver.setStatus(GeneralStatus.INACTIVE);

            List<Driver> drivers = Collections.emptyList();

            when(repository.findAllByStatus(GeneralStatus.INACTIVE)).thenReturn(drivers);

            // act
            List<DriverResponseDTO> result = driverService.getAllInactiveDrivers();

            // assert
            assertNotNull(result, "result must never be null");

            assertEquals(0, result.size());

            assertTrue(result.isEmpty());
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
                    GeneralStatus.ACTIVE,
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
                    new DriverRequestDTO(null, "123", "123", "João", "Silva", "75999999999", null, "Salvador - BA"),
                    new DriverRequestDTO("emailteste@gmail.cm", null, "123", "João", "Silva", "75999999999", null, "Salvador - BA"),
                    new DriverRequestDTO("email@gmail.com", "123", null, "João", "Silva", "75999999999", null, "Salvador - BA"),
                    new DriverRequestDTO("email1@gmail.com", "123", "123", "João", "Silva", "75999999999", null, null)
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
                    GeneralStatus.ACTIVE,
                    "Salvador - BA"
            );

            when(repository.findByEmail(exemple_driver.getEmail())).thenReturn(Optional.of(exemple_driver));
            when(repository.findByTelephone(exemple_driver.getTelephone())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(DuplicateResourceException.class, () -> driverService.createDriver(driverRequestDTO));

            verify(permissionsRepository, never()).findByDescription(any());
            verify(repository, never()).save(any());

            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("throw exception when telephone already exists in the database")
        void throwExceptionWhenTelephoneAlreadyExists() {
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
                    GeneralStatus.ACTIVE,
                    "Salvador - BA"
            );

            when(repository.findByEmail(exemple_driver.getEmail())).thenReturn(Optional.empty());
            when(repository.findByTelephone(exemple_driver.getTelephone())).thenReturn(Optional.of(exemple_driver));

            // act & assert
            assertThrows(DuplicateResourceException.class, () -> driverService.createDriver(driverRequestDTO));

            verify(permissionsRepository, never()).findByDescription(any());
            verify(repository, never()).save(any());

            verifyNoInteractions(passwordEncoder);
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
                    GeneralStatus.ACTIVE,
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
    class updateLoggedDriver {

        @Test
        @DisplayName("should update logged driver with success")
        void shouldUpdateLoggedDriverWithSuccess() {
            // arrange
            String encodePassword = "123";

            Driver driver = new Driver(UUID.randomUUID(), "driver2@email.com", encodePassword, "João", "Silva", "75999999999", null, GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador - BA", 25, new ArrayList<>());

            DriverRequestDTO driverRequestDTO = new DriverRequestDTO(
                    "new@email.com",
                    "newPass",
                    "NewName",
                    "NewLast",
                    "75888888888",
                    null,
                    GeneralStatus.ACTIVE,
                    "Feira de Santana - BA"
            );

            when(repository.findByEmail(driver.getEmail())).thenReturn(Optional.of(driver));
            when(repository.findByEmailOrTelephoneAndIdNot(driverRequestDTO.email(), driverRequestDTO.telephone(), driver.getId())).thenReturn(Optional.empty());
            when(repository.save(any(Driver.class))).thenReturn(driver);

            // act
            DriverResponseDTO result = driverService.updateLoggedDriver(driver.getEmail(), driverRequestDTO);

            // assert
            assertNotNull(result, "result must never be null");

            verify(repository, times(1)).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(repository, times(1)).save(driverArgumentCaptor.capture());
            Driver savedDriver = driverArgumentCaptor.getValue();

            assertEquals("new@email.com", savedDriver.getEmail());
            assertEquals("NewName", savedDriver.getName());
            assertEquals("NewLast", savedDriver.getLastName());
            assertEquals("75888888888", savedDriver.getTelephone());
            assertEquals("newPass", savedDriver.getPassword());
            assertEquals("Feira de Santana - BA", savedDriver.getAreaOfActivity());
            assertNotNull(result.id());
        }

        @Test
        @DisplayName("throw exception when logged driver not found from database")
        void throwExceptionWhenLoggedDriverNotFound() {
            // arrange
            DriverRequestDTO driverRequestDTO = new DriverRequestDTO(
                    "new@email.com",
                    "newPass",
                    "NewName",
                    "NewLast",
                    "75888888888",
                    null,
                    GeneralStatus.ACTIVE,
                    "Feira de Santana - BA"
            );

            when(repository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> driverService.updateLoggedDriver(driverRequestDTO.email(), driverRequestDTO));

            verify(repository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when driver is inactive")
        void throwExceptionWhenDriverIsInactive() {
            // arrange
            DriverRequestDTO driverRequestDTO = new DriverRequestDTO(
                    "new@email.com",
                    "newPass",
                    "NewName",
                    "NewLast",
                    "75888888888",
                    null,
                    GeneralStatus.INACTIVE,
                    "Feira de Santana - BA"
            );

            Driver driver = new Driver();
            driver.setStatus(GeneralStatus.INACTIVE);

            when(repository.findByEmail(driverRequestDTO.email())).thenReturn(Optional.of(driver));

            // act & assert
            assertThrows(InactiveAccountModificationException.class, () -> driverService.updateLoggedDriver(driverRequestDTO.email(), driverRequestDTO));

            verify(repository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(repository, never()).save(any());
        }

        @ParameterizedTest
        @DisplayName("throw exception when already exists driver with email or telephone")
        @MethodSource("filledPropsProvier")
        void throwExceptionWhenAlreadyExistingDriverWithTelephoneOrEmail(DriverRequestDTO driverRequestDTO) {
            Driver driver = new Driver();
            driver.setEmail("teste@gmail.com");
            driver.setStatus(GeneralStatus.ACTIVE);
            driver.setId(UUID.randomUUID());

            when(repository.findByEmail(any())).thenReturn(Optional.of(driver));
            when(repository.findByEmailOrTelephoneAndIdNot(any(), any(), any())).thenReturn(Optional.of(driver));

            assertThrows(DuplicateResourceException.class, () -> driverService.updateLoggedDriver(driverRequestDTO.email(), driverRequestDTO));

            verify(repository, never()).save(any());
        }

        public static Stream<DriverRequestDTO> filledPropsProvier() {
            return Stream.of(
                    new DriverRequestDTO("filled@email.com", null, null, null, null, null, null, null),
                    new DriverRequestDTO(null, null, null, null, "74739204403", null, null, null)
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
            DriverResponseDTO result = driverService.getLoggedInDriverProfile(driver.getEmail());

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
            assertThrows(EntityNotFoundException.class, () -> driverService.getLoggedInDriverProfile(driver.getEmail()));
        }
    }

    @Nested
    class disableDriver {

        @Test
        @DisplayName("should disable driver with success")
        void shouldDisableDriverWithSuccess() {
            // arrange
            Driver driver = new Driver();
            driver.setStatus(GeneralStatus.ACTIVE);
            driver.setId(UUID.randomUUID());

            when(repository.findById(driver.getId())).thenReturn(Optional.of(driver));

            // act
            driverService.disableDriver(driver.getId());

            verify(repository, times(1)).save(driverArgumentCaptor.capture());
            Driver savedDriver = driverArgumentCaptor.getValue();
        }
    }
}