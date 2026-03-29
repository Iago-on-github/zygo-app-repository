package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.exceptions.EmailNotFoundException;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.repository.UserRepository;
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

            LoginResponseDTO loginResponseDTO = new LoginResponseDTO("teste", null, Instant.now(), null, "1234", null);

            when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
            when(tokenConfig.createAccessToken(user.getEmail(), user.getRoles())).thenReturn(loginResponseDTO);

            // act
            var result = authService.signing(loginRequestDto);

            // assert
            assertNotNull(result);

            assertTrue(result.hasBody());
            assertEquals(result.getStatusCode(), HttpStatusCode.valueOf(200));
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
            assertThrows(EmailNotFoundException.class, () -> {
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

            LoginResponseDTO loginResponseDTO = new LoginResponseDTO("teste", null, Instant.now(), null, "1234", null);

            when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
            when(tokenConfig.refreshToken(refreshToken)).thenReturn(loginResponseDTO);

            // act
            var result = authService.refreshToken(email, refreshToken);

            assertNotNull(result);

            assertTrue(result.hasBody());
            assertEquals(result.getStatusCode(), HttpStatusCode.valueOf(200));
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
            assertThrows(EmailNotFoundException.class, () -> authService.refreshToken(email, refreshToken));

            verify(tokenConfig, never()).refreshToken(any());
        }
    }
}