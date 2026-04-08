package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Permission;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionsServiceTest {

    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT) de forma com que todos os cenários sejam cobertos
     *
     */

    @InjectMocks
    private PermissionsService permissionsService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionsRepository permissionsRepository;

    private ArgumentCaptor<UserModel> userModelCaptor = ArgumentCaptor.forClass(UserModel.class);

    @Nested
    class assignPermissions {

        @Test
        @DisplayName("should assign permission with success")
        void shouldAssignPermissionWithSuccess() {
            // arrange
            UUID id = UUID.randomUUID();
            String perm = "perm-string";

            UserModel user = new UserModel();
            user.setId(id);

            Permissions permission = new Permissions(perm);

            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(permissionsRepository.findByDescription(perm)).thenReturn(Optional.of(permission));

            // act
            permissionsService.assignPermissions(id, perm);

            // assert
            verify(userRepository, times(1)).save(userModelCaptor.capture());
            UserModel savedUser = userModelCaptor.getValue();

            assertNotNull(savedUser);
            assertEquals(perm, savedUser.getPermissions().getFirst().getDescription());
        }

        @Test
        @DisplayName("throw exception when user not found from database")
        void throwExceptionWhenUserNotFound() {
            // arrange
            UUID id = UUID.randomUUID();
            String perm = "perm-string";

            when(userRepository.findById(id)).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () ->
                    permissionsService.assignPermissions(id, perm));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when permission not found from database")
        void throwExceptionWhenPermissionNotFound() {
            // arrange
            UUID id = UUID.randomUUID();
            String perm = "perm-string";

            UserModel user = new UserModel();
            user.setId(id);

            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(permissionsRepository.findByDescription(perm)).thenReturn(Optional.empty());

            // act & assert
            assertThrows(PermissionNotFoundException.class, () ->
                    permissionsService.assignPermissions(id, perm));

            verify(userRepository, never()).save(any());
        }
    }
}