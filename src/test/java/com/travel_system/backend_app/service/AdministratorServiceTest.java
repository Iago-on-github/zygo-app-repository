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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private AdministratorService administratorService;

    @Mock
    private AdministratorRepository administratorRepository;

    @Mock
    private PermissionsRepository permissionsRepository;

    @Mock
    private AdministratorMapper administratorMapper;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    ArgumentCaptor<Administrator> admCaptor = ArgumentCaptor.forClass(Administrator.class);

    @Nested
    class getAllAdministrators {

        @Test
        @DisplayName("Should return all administrators with success")
        void shouldReturnAllAdministratorsWithSuccess() {
            // arrange
            Administrator adm = new Administrator();
            adm.setId(UUID.randomUUID());
            adm.setName("Adm Teste");

            List<Administrator> mockList = List.of(adm);

            when(administratorRepository.findAll()).thenReturn(mockList);

            // act
            List<AdministratorResponseDTO> result = administratorService.getAllAdministrators();

            // assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(adm.getName(), result.getFirst().name());
        }

        @Test
        @DisplayName("Should return an empty list when database return empty for findAll")
        void shouldReturnAnEmptyListWhenNoAdministratorRegistersMatchers() {
            // assert
            List<Administrator> mockList = new ArrayList<>();

            when(administratorRepository.findAll()).thenReturn(mockList);

            // act
            List<AdministratorResponseDTO> result = administratorService.getAllAdministrators();

            assertTrue(result.isEmpty(), "must be null");
        }
    }

    @Nested
    class getAllAdministratorsByStatus {

        @ParameterizedTest
        @MethodSource("statusProvider")
        void shouldReturnAllAdministratorsByStatusWithSuccess(GeneralStatus inputStatus, GeneralStatus expectedStatus) {
            List<Administrator> admList = List.of(new Administrator(UUID.randomUUID(), "email@gmail.com", "123", "teste", "testeSobrenome", "7373723442", null, LocalDateTime.now(), null, null, null));

            when(administratorRepository.findByStatus(expectedStatus)).thenReturn(admList);

            List<AdministratorResponseDTO> result = administratorService.getAllAdministratorsByStatus(inputStatus);

            assertEquals(1, result.size());

            verify(administratorRepository)
                    .findByStatus(expectedStatus);
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
    class getCurrentAdministrator {

        @Test
        @DisplayName("Should get logged administrator in profile")
        void shouldgetCurrentAdministratorWithSuccess() {
            // arrange
            Administrator adm = new Administrator();
            adm.setEmail("zygo@gmail.com");

            when(administratorRepository.findByEmail(adm.getEmail())).thenReturn(Optional.of(adm));

            // act
            AdministratorResponseDTO result = administratorService.getCurrentAdministrator(adm.getEmail());

            // assert
            assertNotNull(result, "must never be null");

            assertEquals(result.email(), adm.getEmail(), "both email property must be equals");
        }

        @Test
        @DisplayName("Throw exception when administrator do not found from the database")
        void throwExceptionWhenAdministratorDoNotFound() {
            //arrange
            when(administratorRepository.findByEmail(any())).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> administratorService.getCurrentAdministrator(any()));
        }
    }

    @Nested
    class createAdministrator {

        @Test
        @DisplayName("Should create a new administrator with success")
        void shouldCreateAdministratorWithSuccess() {
            // arrange
            String ROLE_ADMIN = "ROLE_ADMIN";
            String passEncoded = passwordEncoder.encode("123");

            AdministratorRequestDTO admDto = new AdministratorRequestDTO("adm@email.com", passEncoded, "adm", "teste", "75981736299", null);

            Administrator admToReturn = new Administrator(UUID.randomUUID(), admDto.email(), admDto.password(), null, null, admDto.telephone(), null, null, null, "08149190473", "03.11.1992");
            Permissions perms = new Permissions(ROLE_ADMIN);

            when(administratorRepository.findByEmail(admDto.email())).thenReturn(Optional.empty());
            when(administratorRepository.findByTelephone(admDto.telephone())).thenReturn(Optional.empty());
            when(administratorRepository.save(any(Administrator.class))).thenReturn(admToReturn);

            when(permissionsRepository.findByDescription(ROLE_ADMIN)).thenReturn(Optional.of(perms));

            // act
            AdministratorResponseDTO result = administratorService.createAdministrator(admDto);

            // assert
            assertNotNull(result);
            assertNotNull(result.email());
            assertNotNull(result.telephone());
            assertNotNull(result.id());

            assertEquals(GeneralStatus.ACTIVE, result.status());
            assertEquals(admDto.email(), result.email());
            assertEquals(admDto.telephone(), result.telephone());

            // using argument captor for capturing savedAdm values
            verify(administratorRepository).save(admCaptor.capture());
            Administrator savedAdm = admCaptor.getValue();


            assertNotNull(savedAdm.getPassword());
            assertNotNull(savedAdm.getPermissions());
            assertEquals(ROLE_ADMIN, savedAdm.getPermissions().getFirst().getAuthority());

            assertNotEquals("123", savedAdm.getPassword());
        }

        @ParameterizedTest
        @DisplayName("throw exception if mandatory fields like email or telephone are null")
        @MethodSource("nullFieldsProvider")
        void throwExceptionIfMandatoryFieldsAreNull(AdministratorRequestDTO admReqDTO) {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> administratorService.createAdministrator(admReqDTO));

            verify(administratorRepository, never()).save(any(Administrator.class));
        }

        private static Stream<AdministratorRequestDTO> nullFieldsProvider() {
            return Stream.of(
                    new AdministratorRequestDTO(null, "fsdf", "adm", "teste", "75981736299", null),
                    new AdministratorRequestDTO("adm@email.com", null, "adm", "teste", "75981736299", null),
                    new AdministratorRequestDTO("adm@email.com", "fsdf", null, "teste", "75981736299", null),
                    new AdministratorRequestDTO("adm@email.com", "fsdf", "adm", "teste", null, null)
            );
        }

        @Test
        @DisplayName("throw exception when administrator already registered with this email")
        void throwExceptionWhenAdministratorAlreadyRegisteredWithEmail() {
            // arrange
            AdministratorRequestDTO admDto = new AdministratorRequestDTO("adm@email.com", "fsdf", "adm", "teste", "75981736299", null);
            Administrator admToReturn = new Administrator(UUID.randomUUID(), admDto.email(), admDto.password(), null, null, admDto.telephone(), null, null, null, "08149190473", "03.11.1992");

            when(administratorRepository.findByEmail(admDto.email())).thenReturn(Optional.of(admToReturn));

            // act & assert
            assertThrows(DuplicateResourceException.class, () -> {
                administratorService.createAdministrator(admDto);
            });

            verify(administratorRepository, never()).save(any(Administrator.class));
        }

        @Test
        @DisplayName("throw exception when administrator already registered with this telephone")
        void throwExceptionWhenAdministratorAlreadyRegisteredWithTelephone() {
            // arrange
            AdministratorRequestDTO admDto = new AdministratorRequestDTO("adm@email.com", "fsdf", "adm", "teste", "75981736299", null);
            Administrator admToReturn = new Administrator(UUID.randomUUID(), admDto.email(), admDto.password(), null, null, admDto.telephone(), null, null, null, "08149190473", "03.11.1992");

            when(administratorRepository.findByTelephone(admDto.telephone())).thenReturn(Optional.of(admToReturn));

            // act & assert
            assertThrows(DuplicateResourceException.class, () -> {
                administratorService.createAdministrator(admDto);
            });

            verify(administratorRepository, never()).save(any(Administrator.class));
        }

        @Test
        @DisplayName("throw exception when 'ROLE_ADMIN' permission not found from database")
        void throwExceptionWhenRoleAdminPermissionNotFound() {
            // arrange
            String ROLE_ADMIN = "ROLE_ADMIN";

            AdministratorRequestDTO admDto = new AdministratorRequestDTO("adm23@email.com", "fsdf", "adm", "teste", "75981736299", null);

            when(permissionsRepository.findByDescription(ROLE_ADMIN)).thenReturn(Optional.empty());

            // act & assert
            assertThrows(PermissionNotFoundException.class, () -> administratorService.createAdministrator(admDto));

            verify(administratorRepository, never()).save(any(Administrator.class));
        }
    }

    @Nested
    class updateCurrentAdministrator {

        @Test
        @DisplayName("should update logged administrator with success")
        void shouldupdateCurrentAdministratorWithSuccess() {
            // arrange
            String passEncoded = passwordEncoder.encode("123");
            AdministratorUpdateDTO admDto = new AdministratorUpdateDTO("adm@email.com", passEncoded, "adm", "teste", "75981736299", null);

            Administrator admToReturn = new Administrator(UUID.randomUUID(), admDto.email(), admDto.password(), null, null, admDto.telephone(), null, null, null, "08149190473", "03.11.1992");

            admToReturn.setId(UUID.randomUUID());
            admToReturn.setStatus(GeneralStatus.ACTIVE);

            when(administratorRepository.findByEmail(admDto.email())).thenReturn(Optional.of(admToReturn));
            when(administratorRepository.findByEmailOrTelephoneAndIdNot(admDto.email(), admDto.telephone(), admToReturn.getId())).thenReturn(Optional.empty());
            when(administratorRepository.save(admToReturn)).thenReturn(admToReturn);

            doNothing().when(administratorMapper).administratorUpdateFromDTO(admDto, admToReturn);

            // act
            AdministratorResponseDTO result = administratorService.updateCurrentAdministrator(admDto.email(), admDto);

            // assert
            assertNotNull(result, "result must never be null");

            assertNotNull(admToReturn.getUpdatedAt());
            assertNotNull(result.telephone());
            assertNotNull(result.email());
            assertNotNull(result.id());
            assertNotNull(admToReturn.getUpdatedAt());

            assertEquals(GeneralStatus.ACTIVE, result.status());

            verify(administratorRepository, times(1)).save(admToReturn);
        }

        @Test
        @DisplayName("should update without checking duplicates when email and telephone are null")
        void shouldUpdateWhenEmailAndTelephoneNotExistsFromDatabase() {
            // arrange
            AdministratorUpdateDTO admDto = new AdministratorUpdateDTO(null, passwordEncoder.encode("123"), "adm", "teste", null, null);
            Administrator adm = new Administrator();

            adm.setId(UUID.randomUUID());
            adm.setStatus(GeneralStatus.ACTIVE);
            adm.setUpdatedAt(LocalDateTime.now());

            when(administratorRepository.findByEmail(admDto.email())).thenReturn(Optional.of(adm));
            when(administratorRepository.save(adm)).thenReturn(adm);

            // act
            AdministratorResponseDTO result = administratorService.updateCurrentAdministrator(admDto.email(), admDto);

            // assert
            assertNotNull(result, "result must never be null");

            verify(administratorRepository, never()).findByEmailOrTelephoneAndIdNot(any(), any(), any());
            verify(administratorRepository, times(1)).save(adm);

        }

        @Test
        @DisplayName("should update without encoding when password is null")
        void shouldUpdateWithoutEncodingWhenPasswordIsNull() {
            // arrange
            AdministratorUpdateDTO admDto = new AdministratorUpdateDTO("emailADM@gmail.com", null, "adm", "teste", "75981736299", null);

            Administrator adm = new Administrator();
            adm.setId(UUID.randomUUID());
            adm.setEmail(admDto.email());
            adm.setStatus(GeneralStatus.ACTIVE);

            when(administratorRepository.findByEmail(adm.getEmail())).thenReturn(Optional.of(adm));
            when(administratorRepository.findByEmailOrTelephoneAndIdNot(any(), any(), any())).thenReturn(Optional.empty());
            when(administratorRepository.save(adm)).thenReturn(adm);

            // act
            AdministratorResponseDTO result = administratorService.updateCurrentAdministrator(admDto.email(), admDto);

            // assert
            assertNotNull(result, "result must never be null");

            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("throw exception adm email not exists from database")
        void throwExceptionWhenEmailNotFound() {
            // arrange
            String unauthenticatedEmail = "anyUnauthenticated@gmail.com";
            AdministratorUpdateDTO admDto = new AdministratorUpdateDTO(unauthenticatedEmail, passwordEncoder.encode("123"), "adm", "teste", "75981736299", null);

            when(administratorRepository.findByEmail(unauthenticatedEmail)).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> administratorService.updateCurrentAdministrator(admDto.email(), admDto));

            verify(administratorRepository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when administrator has inactive status")
        void throwExceptionWhenAdministratorHasInactiveStatus() {
            // arrange
            AdministratorUpdateDTO admDto = new AdministratorUpdateDTO("email@email.com", passwordEncoder.encode("123"), "adm", "teste", "75981736299", null);
            Administrator adm = new Administrator();
            adm.setStatus(GeneralStatus.INACTIVE);

            when(administratorRepository.findByEmail(admDto.email())).thenReturn(Optional.of(adm));

            // act & assert
            assertThrows(InactiveAccountModificationException.class, () -> administratorService.updateCurrentAdministrator(admDto.email(), admDto));

            verify(administratorRepository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when the props email or telephone already used by another one")
        void throwExceptionWhenEmailOrTelephoneAlreadyUsed() {
            // arrange
            AdministratorUpdateDTO admDto = new AdministratorUpdateDTO("email@email.com", passwordEncoder.encode("123"), "adm", "teste", "75981736299", null);
            Administrator adm = new Administrator();

            adm.setId(UUID.randomUUID());
            adm.setStatus(GeneralStatus.ACTIVE);

            when(administratorRepository.findByEmail(admDto.email())).thenReturn(Optional.of(adm));
            when(administratorRepository.findByEmailOrTelephoneAndIdNot(admDto.email(), admDto.telephone(), adm.getId())).thenReturn(Optional.of(adm));

            // act & assert
            assertThrows(DuplicateResourceException.class, () -> administratorService.updateCurrentAdministrator(admDto.email(), admDto));

            verify(administratorRepository, never()).save(any());
        }

    }

    @Nested


    class updateAdministrator {

    }
}
