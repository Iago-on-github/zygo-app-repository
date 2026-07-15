package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RefreshTokenResponseDTO;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT)
     */

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private TokenConfig tokenConfig;

    private LoginRequestDTO loginRequestDto;
    private UserModel userEntity;
    private LoginResponseDTO mockTokenResponse;
    private RefreshTokenResponseDTO mockRefreshedToken;
    private final String refreshToken = "old_refresh_token_jwt";

    @BeforeEach
    void setUp() {
        loginRequestDto = new LoginRequestDTO("usuario@teste.com", "senhaSegura123");

        userEntity = new UserModel();
        userEntity.setEmail(loginRequestDto.email());

        mockTokenResponse = new LoginResponseDTO(
                loginRequestDto.email(),
                true,
                Instant.now(),
                Instant.now().minusSeconds(30),
                "access_token_jwt",
                "refresh_token_jwt"
        );

        mockRefreshedToken = new RefreshTokenResponseDTO(
                "new_access_token_jwt",
                "new_refresh_token_jwt",
                Instant.now().minusSeconds(30)
        );
    }

    @Nested
    class signing {

        @Test
        @DisplayName("Cenário 1.1: Deve autenticar com sucesso um usuário comum vinculado a um Customer")
        void shouldAuthenticateCommonUserSuccessfully() {
            UUID customerId = UUID.randomUUID();
            Customer customerEntity = new Customer();
            customerEntity.setId(customerId);
            userEntity.setCustomer(customerEntity);

            // Mocks para o fluxo de autenticação, busca e geração do token
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(mock(Authentication.class));
            when(userRepository.findUserByEmail(loginRequestDto.email())).thenReturn(userEntity);
            when(tokenConfig.createAccessToken(loginRequestDto.email(), userEntity.getRoles(), customerId))
                    .thenReturn(mockTokenResponse);

            LoginResponseDTO response = authService.signing(loginRequestDto);

            assertNotNull(response);
            assertEquals(loginRequestDto.email(), response.username());
            assertTrue(response.authenticated());
            assertEquals("access_token_jwt", response.accessToken());
            assertEquals("refresh_token_jwt", response.refreshToken());

            verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(userRepository, times(1)).findUserByEmail(loginRequestDto.email());
            verify(tokenConfig, times(1)).createAccessToken(loginRequestDto.email(), userEntity.getRoles(), customerId);
        }

        @Test
        @DisplayName("Cenário 1.2: Deve autenticar com sucesso um Platform Admin (sem vínculo com Customer)")
        void shouldAuthenticatePlatformAdminSuccessfully() {
            // Platform Admin não possui vínculo com Customer (customer é null)
            userEntity.setCustomer(null);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(mock(Authentication.class));
            when(userRepository.findUserByEmail(loginRequestDto.email())).thenReturn(userEntity);
            when(tokenConfig.createAccessToken(loginRequestDto.email(), userEntity.getRoles(), null))
                    .thenReturn(mockTokenResponse);

            LoginResponseDTO response = authService.signing(loginRequestDto);

            assertNotNull(response);
            assertEquals(loginRequestDto.email(), response.username());
            assertTrue(response.authenticated());
            assertEquals("access_token_jwt", response.accessToken());

            verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(userRepository, times(1)).findUserByEmail(loginRequestDto.email());
            verify(tokenConfig, times(1)).createAccessToken(loginRequestDto.email(), userEntity.getRoles(), null);
        }

        @Test
        @DisplayName("Cenário 1.3: Deve lançar BadCredentialsException quando o DTO ou seus campos obrigatórios forem nulos")
        void shouldThrowBadCredentialsExceptionWhenDtoOrFieldsAreNull() {
            // Teste com DTO inteiramente nulo
            BadCredentialsException exceptionNullDto = assertThrows(BadCredentialsException.class, () -> {
                authService.signing(null);
            });
            assertEquals("Email ou senha inválidos", exceptionNullDto.getMessage());

            // Teste com e-mail nulo
            LoginRequestDTO dtoWithNullEmail = new LoginRequestDTO(null, "senha123");
            BadCredentialsException exceptionNullEmail = assertThrows(BadCredentialsException.class, () -> {
                authService.signing(dtoWithNullEmail);
            });
            assertEquals("Email ou senha inválidos", exceptionNullEmail.getMessage());

            // Teste com senha nula
            LoginRequestDTO dtoWithNullPassword = new LoginRequestDTO("teste@teste.com", null);
            BadCredentialsException exceptionNullPassword = assertThrows(BadCredentialsException.class, () -> {
                authService.signing(dtoWithNullPassword);
            });
            assertEquals("Email ou senha inválidos", exceptionNullPassword.getMessage());

            // Garante que nenhuma dependência externa foi acionada
            verifyNoInteractions(authenticationManager, userRepository, tokenConfig);
        }

        @Test
        @DisplayName("Cenário 1.4: Deve lançar BadCredentialsException personalizada quando o AuthenticationManager falhar")
        void shouldThrowBadCredentialsExceptionWhenAuthenticationFails() {
            LoginRequestDTO loginRequestDto = new LoginRequestDTO("usuario@teste.com", "senhaIncorreta");

            // Simula o AuthenticationManager lançando uma exceção de autenticação genérica do Spring Security
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            BadCredentialsException exception = assertThrows(BadCredentialsException.class, () -> {
                authService.signing(loginRequestDto);
            });

            assertEquals("Email ou senha inválidos. Tente novamente", exception.getMessage());

            verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verifyNoInteractions(userRepository, tokenConfig);
        }

        @Test
        @DisplayName("Cenário 1.5: Deve lançar EntityNotFoundException quando o usuário autenticado não for encontrado no banco")
        void shouldThrowEntityNotFoundExceptionWhenUserNotFoundInDatabase() {
            LoginRequestDTO loginRequestDto = new LoginRequestDTO("fantasma@teste.com", "senhaCorreta123");

            // Autenticação passa com sucesso no gerenciador
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(mock(Authentication.class));

            // Porém o e-mail correspondente não retorna nenhuma entidade do banco de dados
            when(userRepository.findUserByEmail(loginRequestDto.email())).thenReturn(null);

            EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
                authService.signing(loginRequestDto);
            });

            assertEquals("Email não encontrado. Tente novamente", exception.getMessage());

            verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(userRepository, times(1)).findUserByEmail(loginRequestDto.email());
            verifyNoInteractions(tokenConfig);
        }

    }

    @Nested
    class refreshToken {
        @Test
        @DisplayName("Cenário 2.1: Deve renovar o token com sucesso para um Platform Admin")
        void shouldRefreshTokenForPlatformAdminSuccessfully() {
            // Simula que as roles extraídas do token contêm ROLE_PLATFORM_ADMIN
            when(tokenConfig.getRolesFromToken(refreshToken)).thenReturn(List.of("ROLE_PLATFORM_ADMIN"));
            when(userRepository.findByEmailAndRole(userEntity.getEmail(), "ROLE_PLATFORM_ADMIN")).thenReturn(Optional.of(userEntity));
            when(tokenConfig.refreshToken(refreshToken)).thenReturn(mockRefreshedToken);

            RefreshTokenResponseDTO response = authService.refreshToken(userEntity.getEmail(), refreshToken, null);

            assertNotNull(response);
            assertEquals("new_access_token_jwt", response.accessToken());
            assertEquals("new_refresh_token_jwt", response.refreshToken());

            verify(tokenConfig, times(1)).getRolesFromToken(refreshToken);
            verify(userRepository, times(1)).findByEmailAndRole(userEntity.getEmail(), "ROLE_PLATFORM_ADMIN");
            verify(userRepository, never()).findByEmailAndCustomerId(anyString(), any());
            verify(tokenConfig, times(1)).refreshToken(refreshToken);
        }

        @Test
        @DisplayName("Cenário 2.2: Deve renovar o token com sucesso para um usuário comum (com customerId)")
        void shouldRefreshTokenForCommonUserSuccessfully() {
            UUID customerId = UUID.randomUUID();

            // Usuário comum não possui a role ROLE_PLATFORM_ADMIN no token
            when(tokenConfig.getRolesFromToken(refreshToken)).thenReturn(List.of("ROLE_USER"));
            when(userRepository.findByEmailAndCustomerId(userEntity.getEmail(), customerId)).thenReturn(Optional.of(userEntity));
            when(tokenConfig.refreshToken(refreshToken)).thenReturn(mockRefreshedToken);

            RefreshTokenResponseDTO response = authService.refreshToken(userEntity.getEmail(), refreshToken, customerId);

            assertNotNull(response);
            assertEquals("new_access_token_jwt", response.accessToken());

            verify(tokenConfig, times(1)).getRolesFromToken(refreshToken);
            verify(userRepository, times(1)).findByEmailAndCustomerId(userEntity.getEmail(), customerId);
            verify(userRepository, never()).findByEmailAndRole(anyString(), anyString());
            verify(tokenConfig, times(1)).refreshToken(refreshToken);
        }

        @Test
        @DisplayName("Cenário 2.3: Deve lançar BadCredentialsException se um usuário comum não possuir customerId")
        void shouldThrowBadCredentialsExceptionWhenCommonUserHasNullCustomerId() {
            // Token sem ROLE_PLATFORM_ADMIN
            when(tokenConfig.getRolesFromToken(refreshToken)).thenReturn(List.of("ROLE_USER"));

            assertThrows(BadCredentialsException.class, () -> authService.refreshToken(userEntity.getEmail(), refreshToken, null));

            verify(tokenConfig, times(1)).getRolesFromToken(refreshToken);
            verifyNoInteractions(userRepository);
            verify(tokenConfig, never()).refreshToken(anyString());
        }

        @Test
        @DisplayName("Cenário 2.4: Deve lançar EntityNotFoundException se o Platform Admin correspondente não for localizado no banco")
        void shouldThrowEntityNotFoundExceptionWhenPlatformAdminNotFound() {
            when(tokenConfig.getRolesFromToken(refreshToken)).thenReturn(List.of("ROLE_PLATFORM_ADMIN"));
            when(userRepository.findByEmailAndRole(userEntity.getEmail(), "ROLE_PLATFORM_ADMIN")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> authService.refreshToken(userEntity.getEmail(), refreshToken, null));

            verify(userRepository, times(1)).findByEmailAndRole(userEntity.getEmail(), "ROLE_PLATFORM_ADMIN");
            verify(tokenConfig, never()).refreshToken(anyString());
        }

        @Test
        @DisplayName("Cenário 2.5: Deve lançar EntityNotFoundException se o usuário comum correspondente (por email e customerId) não for localizado")
        void shouldThrowEntityNotFoundExceptionWhenCommonUserNotFound() {
            UUID customerId = UUID.randomUUID();

            when(tokenConfig.getRolesFromToken(refreshToken)).thenReturn(List.of("ROLE_USER"));
            when(userRepository.findByEmailAndCustomerId(userEntity.getEmail(), customerId)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> authService.refreshToken(userEntity.getEmail(), refreshToken, customerId));

            verify(userRepository, times(1)).findByEmailAndCustomerId(userEntity.getEmail(), customerId);
            verify(tokenConfig, never()).refreshToken(anyString());
        }
    }
}