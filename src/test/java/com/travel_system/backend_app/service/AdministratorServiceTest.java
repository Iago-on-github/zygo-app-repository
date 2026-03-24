package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.AdministratorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    * */

    @InjectMocks
    private AdministratorService administratorService;

    @Mock
    private AdministratorRepository administratorRepository;

    private PasswordEncoder passwordEncoder;

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
    class getAllActiveAdministrators {

        @Test
        @DisplayName("Should return all active administrators from database with success")
        void shouldReturnAllActiveAdministratorsWithSuccess() {
            // arrange
            Administrator adm = new Administrator();
            adm.setId(UUID.randomUUID());
            adm.setStatus(GeneralStatus.ACTIVE);
            adm.setEmail("admEmailTeste2@zygo.com");

            List<Administrator> mockList = List.of(adm);

            when(administratorRepository.findByStatus(GeneralStatus.ACTIVE)).thenReturn(mockList);

            // act
            List<AdministratorResponseDTO> result = administratorService.getAllActiveAdministrators();

            // assert
            assertNotNull(result, "must never be null");

            assertEquals(adm.getStatus(), result.getFirst().status());
            assertEquals(GeneralStatus.ACTIVE, result.getFirst().status(), "both status must be active");
            assertEquals(adm.getEmail(), result.getFirst().email());
        }

        @Test
        @DisplayName("Should return an empty list when active administrator do not exists from the database")
        void shouldReturnAnEmptyListWhenActiveAdministratorsDoNotExists() {
            // arrange
            List<Administrator> adm = new ArrayList<>();

            when(administratorRepository.findByStatus(GeneralStatus.ACTIVE)).thenReturn(adm);

            // act
            List<AdministratorResponseDTO> result = administratorService.getAllActiveAdministrators();

            assertTrue(result.isEmpty(), "must be always null");
            assertEquals(0, adm.size());
        }
    }

    @Nested
    class getAllInactiveAdministrators {

        @Test
        @DisplayName("Should return all inactive administrator from database with success")
        void shouldReturnAllInactiveAdministratorsWithSuccess() {
            // arrange
            Administrator adm = new Administrator();
            adm.setStatus(GeneralStatus.INACTIVE);
            adm.setEmail("zygoEmail@gmail.com");

            List<Administrator> mockList = List.of(adm);

            when(administratorRepository.findByStatus(GeneralStatus.INACTIVE)).thenReturn(mockList);

            // act
            List<AdministratorResponseDTO> result = administratorService.getAllInactiveAdministrators();

            // assert
            assertNotNull(result, "must never be null");

            assertEquals(result.getFirst().email(), mockList.getFirst().getEmail());
            assertEquals(result.getFirst().status(), mockList.getFirst().getStatus());
            assertEquals(GeneralStatus.INACTIVE, result.getFirst().status(), "the result list must've status inactive");
        }

        @Test
        @DisplayName("Should return an empty list when inactive administrators do not exists from the database")
        void shouldReturnAnEmptyListWhenInactiveAdministratorsDoNotExists() {
            // arrange
            List<Administrator> mockList = new ArrayList<>();

            when(administratorRepository.findByStatus(GeneralStatus.INACTIVE)).thenReturn(mockList);

            // act
            List<AdministratorResponseDTO> result = administratorService.getAllInactiveAdministrators();

            // assert
            assertTrue(result.isEmpty(), "must be always null");
        }
    }

    @Nested
    class getLoggedAdministratorInProfile {

        @Test
        @DisplayName("Should get logged administrator in profile")
        void shouldGetLoggedAdministratorInProfileWithSuccess() {
            // arrange
            Administrator adm = new Administrator();
            adm.setEmail("zygo@gmail.com");

            when(administratorRepository.findByEmail(adm.getEmail())).thenReturn(Optional.of(adm));

            // act
            AdministratorResponseDTO result = administratorService.getLoggedAdministratorInProfile(adm.getEmail());

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
            assertThrows(EntityNotFoundException.class, () -> administratorService.getLoggedAdministratorInProfile(any()));
        }
    }

    @Nested
    class createAdministrator {

        @Test
        @DisplayName("Should create a new administrator with success")
        void shouldCreateAdministratorWithSuccess() {

        }
    }
}