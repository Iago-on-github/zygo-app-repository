package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.repository.UserRepository;
import org.hibernate.annotations.DiscriminatorFormula;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Nested
    class loadUserByUsername {

        @Test
        @DisplayName("should load by username with success")
        void shouldLoadByUsernameWithSuccess() {
            String username = "username";

            when(userRepository.findUserByEmail(username)).thenReturn(new UserModel());

            UserDetails result = userService.loadUserByUsername(username);

            assertNotNull(result);
        }

        @Test
        @DisplayName("throw exception when mandatory fields is null")
        void throwExceptionWhenMandatoryFieldsIsNull() {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> userService.loadUserByUsername(null));

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throw exception when user not found from database")
        void throwExceptionWhenUserNotFoundFromDatabase() {
            String username = "username";

            when(userRepository.findUserByEmail(username)).thenReturn(null);

            assertThrows(UsernameNotFoundException.class, () -> userService.loadUserByUsername(username));

            verifyNoMoreInteractions(userRepository);
        }
    }
}