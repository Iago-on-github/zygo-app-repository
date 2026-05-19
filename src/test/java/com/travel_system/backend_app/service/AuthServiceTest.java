package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RefreshTokenResponseDTO;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Nested
    class signing {

        @Test
        @DisplayName("should return responseEntity's body with accessToken and statuscode 200")
        void shouldReturnAccessTokenAndSigningWithSuccess() {
            // arrange
            LoginRequestDTO loginRequestDto = new LoginRequestDTO("emailTest@gmail.com", "1234");

            UserModel user = new UserModel();
            user.setEmail(loginRequestDto.email());
            user.setPassword(loginRequestDto.password());

            LoginResponseDTO loginResponseDTO = new LoginResponseDTO("teste", true, Instant.now(), null, "1234", null);

            when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
            when(tokenConfig.createAccessToken(user.getEmail(), user.getRoles())).thenReturn(loginResponseDTO);

            // act
            var result = authService.signing(loginRequestDto);

            // assert
            assertNotNull(result);

            assertTrue(result.authenticated());

            assertEquals("teste", result.username());
            assertEquals("1234", result.accessToken());

            assertNotNull(result.created());

            verify(userRepository, times(1))
                    .findUserByEmail(user.getEmail());

            verify(tokenConfig, times(1))
                    .createAccessToken(user.getEmail(), user.getRoles());
        }

        @Test
        @DisplayName("throw exception with user email not found from database")
        void throwExceptionWhenUserEmailNotFound() {
            // arrange
            LoginRequestDTO loginRequestDto = new LoginRequestDTO("emailTest@gmail.com", "1234");

            UserModel user = new UserModel();
            user.setEmail(loginRequestDto.email());
            user.setPassword(loginRequestDto.password());

            when(userRepository.findUserByEmail(loginRequestDto.email())).thenReturn(null);

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> {
                authService.signing(loginRequestDto);
            });

            verify(tokenConfig, never()).createAccessToken(any(), any());
        }

        @Test
        @DisplayName("throw exception when email is invalid for signing")
        void throwExceptionWhenEmailIsInvalid() {
            // arrange
            LoginRequestDTO loginRequestDto = new LoginRequestDTO("emailTest@gmail.com", "1234");

            UserModel user = new UserModel();
            user.setEmail(loginRequestDto.email());
            user.setPassword(loginRequestDto.password());

            when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("anotherEmail@gmail.com", loginRequestDto.password())));

            // act & assert
            assertThrows(BadCredentialsException.class, () -> authService.signing(loginRequestDto));

            verify(tokenConfig, never()).createAccessToken(any(), any());
        }

        @Test
        @DisplayName("throw exception when password is invalid for signing")
        void throwExceptionWhenPasswordIsInvalid() {
            // arrange
            LoginRequestDTO loginRequestDto = new LoginRequestDTO("emailTest@gmail.com", "1234");

            UserModel user = new UserModel();
            user.setEmail(loginRequestDto.email());
            user.setPassword(loginRequestDto.password());

            when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequestDto.email(), "pass teste ")));

            // act & assert
            assertThrows(BadCredentialsException.class, () -> authService.signing(loginRequestDto));

            verify(tokenConfig, never()).createAccessToken(any(), any());
        }
    }

    @Nested
    class refreshToken {

        @Test
        @DisplayName("should generate refresh token with success")
        void shouldGenerateRefreshTokenWithSuccess() {
            // arrange
            String email = "teste@gmail.com";
            String refreshToken = "token";

            UserModel user = new UserModel();
            user.setEmail(email);

            RefreshTokenResponseDTO tokenResponseDTO =
                    new RefreshTokenResponseDTO(
                            "access-token",
                            "refresh-token",
                            Instant.now().plusSeconds(100)
                    );

            when(userRepository.findUserByEmail(email)).thenReturn(user);
            when(tokenConfig.refreshToken(refreshToken)).thenReturn(tokenResponseDTO);

            // act
            var result = authService.refreshToken(email, refreshToken);

            // assert
            assertNotNull(result);

            assertEquals("access-token", result.accessToken());
            assertEquals("refresh-token", result.refreshToken());
            assertNotNull(result.expiresAt());
        }

        @Test
        @DisplayName("throw exception when user email not found from database ")
        void throwExceptionWhenUserEmailNotFound() {
            // arrange
            String email = "teste@gmail.com";
            String refreshToken = "token";

            UserModel user = new UserModel();
            user.setEmail(email);

            when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> authService.refreshToken(email, refreshToken));

            verify(tokenConfig, never()).refreshToken(any());
        }
    }
}