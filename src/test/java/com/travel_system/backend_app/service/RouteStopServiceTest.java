package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.RouteStopRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.RouteStopResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopAssignmentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteStopsUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RouteStopServiceTest {

    private RouteStopService RouteStopService;

    @Mock
    private StandardRouteRepository standardRouteRepository;
    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    
    @Mock
    private MapboxAPIService mapboxAPIService;
    
    StandardRoute standardRoute;
    UserModel user;
    Customer customer;
    RouteStop routeStop;

    StandardRouteResponseDTO standardRouteResponseDTO;
    StandardRouteRequestDTO standardRouteRequestDTO;

    @BeforeEach
    void setUp() {
        RouteStopRequestMapper realRequestMapper = Mappers.getMapper(RouteStopRequestMapper.class);
        RouteStopResponseMapper realResponseMapper = Mappers.getMapper(RouteStopResponseMapper.class);

        RouteStopService = new RouteStopService(
                userRepository,
                routeStopRepository,
                studentRepository,
                mapboxAPIService,
                realResponseMapper,
                realRequestMapper
        );

        customer = new Customer();
        customer.setId(UUID.randomUUID());

        user = new UserModel(UUID.randomUUID(), "useremail@gmail.com", "123", "user", "lastname", "278382345", null, GeneralStatus.ACTIVE, LocalDateTime.now(), null, new Customer());

        Permissions perms = new Permissions("ROLE_ADMIN");
        user.setPermissions(List.of(perms));
        user.setCustomer(customer);

        standardRoute = new StandardRoute(UUID.randomUUID(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", TravelPeriod.MORNING, customer, GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));

        routeStop = new RouteStop(UUID.randomUUID(), "RouteStopName", "RouteStop Description", -45.324, -11.342, customer, GeneralStatus.ACTIVE, Instant.now(), null);

        standardRouteResponseDTO = new StandardRouteResponseDTO(standardRoute.getId(), "Rota Universitária - Linha Leste", "Trajeto diário de transporte universitário conectando pontos de embarque ao campus central.", -12.2333, -38.7500, -12.2670, -38.9670, "a~|~Fkf~vO|@_@eA_@m@g@_@y@e@...", TravelPeriod.MORNING, Set.of(new RouteStopAssignmentResponseDTO(UUID.randomUUID(), "Ponto 1 - Praça Central", 1, false), new RouteStopAssignmentResponseDTO(UUID.randomUUID(), "Ponto 2 - Biblioteca", 2, true)), UUID.randomUUID(), GeneralStatus.ACTIVE, Instant.parse("2026-08-10T12:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"));
        standardRouteRequestDTO = new StandardRouteRequestDTO(
                "Rota Coração de Maria - Feira de Santana",
                "Rota fictícia para testes saindo de Coração de Maria com destino a Feira de Santana.",
                -12.233333,
                -38.750000,
                -12.266666,
                -38.966666,
                TravelPeriod.MORNING,
                Set.of(
                        new RouteStopAssignmentRequestDTO(
                                routeStop.getId(),
                                1,
                                false
                        )
                )
        );
    }

    @Nested
    class updateRouteStopStatus {

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar o update do status do Route Stop com sucesso")
            void shouldUpdateRouteStopStatusWhenDataIsValid() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));
                when(routeStopRepository.save(routeStop)).thenReturn(routeStop);

                RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE);

                ArgumentCaptor<RouteStop> routeStopArgCaptor = ArgumentCaptor.forClass(RouteStop.class);

                verify(routeStopRepository, times(1)).save(routeStopArgCaptor.capture());

                RouteStop storageValue = routeStopArgCaptor.getValue();

                assertEquals(GeneralStatus.INACTIVE, storageValue.getStatus());

            }
        }

        @Nested
        class failureScenarios {
            @Test
            @DisplayName("Deve lançar exception quando o usuário não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenUserNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

                assertThrows(EntityNotFoundException.class, () -> RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verifyNoInteractions(standardRouteRepository, standardRouteRepository, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception se o usuário não tiver a ROLE de ADMIN ou PLATFORM_ADMIN")
            @MethodSource("invalidUserRoleProvider")
            void shouldThrowExceptionWhenUserIsNotAdmin(String permission) {
                Permissions invalidPerms = new Permissions(permission);
                user.setPermissions(List.of(invalidPerms));

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(NotAuthorizedException.class, () -> RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            public static Stream<Arguments> invalidUserRoleProvider() {
                return Stream.of(
                        Arguments.of("ROLE_DRIVER"),
                        Arguments.of("ROLE_USER")
                );
            }

            @Test
            void shouldThrowExceptionWhenAdminIsWithoutCustomer() {
                user.setCustomer(null);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(DomainValidationException.class, () -> RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            void shouldThrowExceptionWhenAdminIsInactive() {
                user.setStatus(GeneralStatus.INACTIVE);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);

                assertThrows(InactiveAccountModificationException.class, () -> RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(standardRouteRepository, routeStopRepository, mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o Customer da rota padrão for diferente do Customer do usuário autenticado")
            void shouldThrowExceptionWhenRouteBelongsToDifferentCustomer() {
                Customer differentCustomer = new Customer();
                user.setCustomer(differentCustomer);

                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(CustomerMismatchException.class, () -> RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verify(routeStopRepository, never()).save(any());

                verifyNoMoreInteractions(userRepository);

                verifyNoInteractions(mapboxAPIService);

            }

            @Test
            @DisplayName("Deve lançar exception quando o RouteStop não for encontrado")
            void shouldThrowEntityNotFoundExceptionWhenRouteStopNotFound() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.INACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));

                verifyNoMoreInteractions(routeStopRepository, userRepository);

                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve lançar exception quando o routeStop ja possuir o status do parâmetro")
            void shouldThrowDuplicateResourceExceptionWhenStatusIsSameAsCurrent() {
                when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
                when(routeStopRepository.findById(routeStop.getId())).thenReturn(Optional.of(routeStop));

                assertThrows(DuplicateResourceException.class, () -> RouteStopService.updateRouteStopStatus(routeStop.getId(), user.getEmail(), GeneralStatus.ACTIVE));

                verify(userRepository, times(1)).findUserByEmail(eq(user.getEmail()));
                verify(routeStopRepository, never()).save(any());

                verifyNoMoreInteractions(routeStopRepository, userRepository);

                verifyNoInteractions(mapboxAPIService);
            }
        }
    }
}