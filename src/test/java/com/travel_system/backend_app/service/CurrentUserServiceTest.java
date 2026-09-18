package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.ProfilePictureNotFoundException;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {
    @InjectMocks
    @Spy
    private CurrentUserService currentUserService;

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private ImageProcessingService imageProcessingService;

    @Nested
    class isPlatformAdmin {

        @Test
        @DisplayName("Deve retornar TRUE quando o atual user logado for um Platform Admin")
        void shouldReturnTrueIfCurrentUserIsPlatformAdmin() {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN");
            List<GrantedAuthority> authorities = Collections.singletonList(authority);

            when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);
            when(securityContext.getAuthentication()).thenReturn(authentication);

            try (MockedStatic<SecurityContextHolder> mockedHolder = mockStatic(SecurityContextHolder.class)) {
                mockedHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                boolean result = currentUserService.isPlatformAdmin();

                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Deve retornar FALSE quando o atual user logado não for um Platform Admin")
        void shouldReturnFalseIfCurrentUserIsNotPlatformAdmin() {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ANY_ANOTHER_ROLE");
            List<GrantedAuthority> authorities = Collections.singletonList(authority);

            when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);
            when(securityContext.getAuthentication()).thenReturn(authentication);

            try (MockedStatic<SecurityContextHolder> mockedHolder = mockStatic(SecurityContextHolder.class)) {
                mockedHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                boolean result = currentUserService.isPlatformAdmin();

                assertFalse(result);
            }
        }
    }

/*    @Nested
    class userProfilePictureUpdate {
        UserModel userModel;
        MultipartFile file;
        byte[] bytes;

        @BeforeEach
        void setUp() {
            Customer customer = new Customer();
            customer.setId(UUID.randomUUID());

            bytes = new byte[]{120};

            userModel = new UserModel();
            userModel.setEmail("userEmail@gmail.com");
            userModel.setCustomer(customer);

            file = new MockMultipartFile("file", "profile.jpeg", "image/jpeg", bytes);
        }

        @Test
        @DisplayName("Deve atualizar com sucesso para o usuário quando for um administrador de plataforma")
        void shouldUpdateForPlatformAdminWithSuccess() throws IOException {
            String expectedKey = "platform/users/" + userModel.getId() + "/profile.jpeg";

            doReturn(true).when(currentUserService).isPlatformAdmin();

            when(userAccountRepository.findUserByEmail(userModel.getEmail())).thenReturn(userModel);
            when(imageProcessingService.convertImageToJPEG(file)).thenReturn(bytes);

            currentUserService.userProfilePictureUpdate(userModel.getEmail(), file);

            verify(userAccountRepository, times(1)).findUserByEmail(userModel.getEmail());
            verify(imageProcessingService, times(1)).convertImageToJPEG(file);
            verify(s3StorageService, times(1)).upload(bytes, expectedKey, "image/jpeg");
            verify(userAccountRepository, times(1)).updateProfilePicture(expectedKey, userModel.getEmail());
        }

        @Test
        @DisplayName("Deve atualizar com sucesso para o usuário do tipo cliente.")
        void shouldUpdateForClientWithSuccess() throws IOException {
            String expectedKey = "customers/" + userModel.getCustomer().getId() + "/users/" + userModel.getId() + "/profile.jpeg";

            doReturn(false).when(currentUserService).isPlatformAdmin();

            when(userAccountRepository.findUserByEmail(userModel.getEmail())).thenReturn(userModel);
            when(imageProcessingService.convertImageToJPEG(file)).thenReturn(bytes);

            currentUserService.userProfilePictureUpdate(userModel.getEmail(), file);

            verify(userAccountRepository, times(1)).findUserByEmail(userModel.getEmail());
            verify(imageProcessingService, times(1)).convertImageToJPEG(file);
            verify(s3StorageService, times(1)).upload(bytes, expectedKey, "image/jpeg");
            verify(userAccountRepository, times(1)).updateProfilePicture(expectedKey, userModel.getEmail());
        }

        @Test
        @DisplayName("Deve lançar exception quando o file estiver null")
        void throwExceptionWhenFileIsNull() throws IOException {
            MultipartFile nullFile = null;

            assertThrows(IllegalArgumentException.class, () -> currentUserService.userProfilePictureUpdate(userModel.getEmail(), nullFile));

            verify(userAccountRepository, never()).findUserByEmail(any());
            verify(imageProcessingService, never()).convertImageToJPEG(any());
            verify(s3StorageService, never()).upload(any(), any(), any());
            verify(userAccountRepository, never()).updateProfilePicture(any(), any());
        }

        @Test
        @DisplayName("Deve lançar exception quando user não for encontrado pelo email")
        void throwExceptionWhenUserNotFound() throws IOException {
            doReturn(false).when(currentUserService).isPlatformAdmin();

            when(userAccountRepository.findUserByEmail(userModel.getEmail())).thenReturn(null);

            assertThrows(EntityNotFoundException.class, () -> currentUserService.userProfilePictureUpdate(userModel.getEmail(), file));

            verify(userAccountRepository, times(1)).findUserByEmail(eq(userModel.getEmail()));
            verify(imageProcessingService, never()).convertImageToJPEG(any());
            verify(s3StorageService, never()).upload(any(), any(), any());
            verify(userAccountRepository, never()).updateProfilePicture(any(), any());
        }

        @Test
        @DisplayName("Deve lançar exception quando ocorrer um erro durante o processamento de imagem")
        void throwExceptionWhenErrorOccursInImageProcessing() throws IOException {
            doReturn(true).when(currentUserService).isPlatformAdmin();

            when(userAccountRepository.findUserByEmail(userModel.getEmail())).thenReturn(userModel);
            when(imageProcessingService.convertImageToJPEG(file)).thenThrow(IOException.class);

            assertThrows(IOException.class, () -> currentUserService.userProfilePictureUpdate(userModel.getEmail(), file));

            verify(userAccountRepository, times(1)).findUserByEmail(userModel.getEmail());
            verify(imageProcessingService, times(1)).convertImageToJPEG(file);

            verify(s3StorageService, never()).upload(any(), any(), any());
            verify(userAccountRepository, never()).updateProfilePicture(any(), any());
        }
    }

    @Nested
    class userProfilePictureDelete {
        UserModel userModel;

        @BeforeEach
        void setUp() {
            userModel = new UserModel();
            userModel.setEmail("userEmail@gmail.com");
            userModel.setProfilePicture("existingProfilePicture");
        }

        @Test
        @DisplayName("Deve realizar a deleção da foto de perfil com sucesso")
        void shouldDeleteProfilePictureWithSuccess() {
            when(userAccountRepository.findProfilePictureByEmail(userModel.getEmail())).thenReturn(Optional.of(userModel.getProfilePicture()));

            currentUserService.userProfilePictureDelete(userModel.getEmail());

            verify(userAccountRepository, times(1)).findProfilePictureByEmail(eq(userModel.getEmail()));
            verify(userAccountRepository, times(1)).deleteProfilePictureByEmail(eq(userModel.getProfilePicture()), eq(userModel.getEmail()));
        }

        @Test
        @DisplayName("Deve lançar exception quando o usuário não tiver foto de perfil")
        void throwExceptionWhenUserHasNoProfilePicture() {
            when(userAccountRepository.findProfilePictureByEmail(userModel.getEmail())).thenReturn(Optional.empty());

            assertThrows(ProfilePictureNotFoundException.class, () -> currentUserService.userProfilePictureDelete(userModel.getEmail()));

            verify(userAccountRepository, times(1)).findProfilePictureByEmail(eq(userModel.getEmail()));
            verify(userAccountRepository, never()).deleteProfilePictureByEmail(any(), any());
        }
    }*/

    @Nested
    class getPublicUrl {

        @Test
        @DisplayName("Deve retonar URL publica do S3 com sucesso")
        void shouldReturnPublicUrlOfS3WithSuccess() {
            String objectKey = "exemple of objectKey";

            when(s3StorageService.getPublicUrl(objectKey)).thenReturn("localhost:8080/teste");

            String result = currentUserService.getPublicUrl(objectKey);

            assertNotNull(result);

            assertEquals("localhost:8080/teste", result);
        }
    }
}